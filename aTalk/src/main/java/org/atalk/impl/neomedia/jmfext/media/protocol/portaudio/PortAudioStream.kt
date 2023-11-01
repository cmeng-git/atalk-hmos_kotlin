/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.portaudio

import okhttp3.internal.notifyAll
import org.atalk.impl.neomedia.NeomediaServiceUtils
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.impl.neomedia.control.DiagnosticsControl
import org.atalk.impl.neomedia.device.AudioSystem
import org.atalk.impl.neomedia.device.AudioSystem2
import org.atalk.impl.neomedia.device.DeviceConfiguration
import org.atalk.impl.neomedia.device.PortAudioSystem
import org.atalk.impl.neomedia.device.UpdateAvailableDeviceListListener
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferStream
import org.atalk.impl.neomedia.portaudio.Pa
import org.atalk.impl.neomedia.portaudio.PortAudioException
import org.atalk.service.neomedia.BasicVolumeControl
import timber.log.Timber
import java.awt.Component
import java.io.IOException
import javax.media.Buffer
import javax.media.Format
import javax.media.GainControl
import javax.media.control.FormatControl
import javax.media.format.AudioFormat

/**
 * Implements `PullBufferStream` for PortAudio.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class PortAudioStream(
        dataSource: DataSource?, formatControl: FormatControl?,
        /**
         * The indicator which determines whether audio quality improvement is enabled for this
         * `PortAudioStream` in accord with the preferences of the user.
         */
        private val audioQualityImprovement: Boolean,
) : AbstractPullBufferStream<DataSource?>(dataSource, formatControl) {
    /**
     * The number of bytes to read from a native PortAudio stream in a single invocation. Based on
     * [.framesPerBuffer].
     */
    private var bytesPerBuffer = 0

    /**
     * The device identifier (the device UID, or if not available, the device name) of the PortAudio
     * device read through this `PullBufferStream`.
     */
    private var deviceID: String? = null

    /**
     * The `DiagnosticsControl` implementation of this instance which allows the diagnosis of
     * the functional health of `Pa_ReadStream`.
     */
    private val diagnosticsControl = object : DiagnosticsControl {

        override val malfunctioningSince: Long
            get() = TODO("Not yet implemented")

        /**
         * {@inheritDoc}
         *
         * Returns the identifier of the PortAudio device read through this `PortAudioStream`
         * .
         */
        override fun toString(): String {
            val id = deviceID
            var name: String? = null
            if (deviceID != null) {
                val index = Pa.getDeviceIndex(id, 1, 0)
                if (index != Pa.paNoDevice) {
                    val info = Pa.GetDeviceInfo(index)
                    if (info != 0L) name = Pa.DeviceInfo_getName(info)
                }
            }
            return name!!
        }

        /**
         * `PortAudioStream`'s `DiagnosticsControl` implementation does not provide
         * its own user interface and always returns `null`.
         */
        override fun getControlComponent(): Component? {
            return null
        }
    }

    /**
     * The last-known `Format` of the media data made available by this
     * `PullBufferStream`.
     */
    private var mFormat: AudioFormat? = null

    /**
     * The number of frames to read from a native PortAudio stream in a single invocation.
     */
    private var framesPerBuffer = 0L

    /**
     * The `GainControl` through which the volume/gain of captured media is controlled.
     */
    private val gainControl: GainControl?

    /**
     * Native pointer to a PaStreamParameters object.
     */
    private var inputParameters = 0L
    private val paUpdateAvailableDeviceListListener = object : UpdateAvailableDeviceListListener {
        /**
         * The device ID (could be deviceUID or name but that is not really of concern to
         * PortAudioStream) used before and after (if still available) the update.
         */
        private var deviceID: String? = null
        private var start = false

        @Throws(Exception::class)
        override fun didUpdateAvailableDeviceList() {
            synchronized(this@PortAudioStream) {
                try {
                    waitWhileStreamIsBusy()
                    /*
                     * The stream should be closed. If it is not, then something else happened in
                     * the meantime and we cannot be sure that restoring the old state of this
                     * PortAudioStream is the right thing to do in its new state.
                     */
                    if (stream == 0L) {
                        setDeviceID(deviceID)
                        if (start) start()
                    }
                } finally {
                    /*
                     * If we had to attempt to restore the state of this PortAudioStream, we just
                     * did attempt to.
                     */
                    deviceID = null
                    start = false
                }
            }
        }

        @Throws(Exception::class)
        override fun willUpdateAvailableDeviceList() {
            synchronized(this@PortAudioStream) {
                waitWhileStreamIsBusy()
                if (stream == 0L) {
                    deviceID = null
                    start = false
                } else {
                    deviceID = this@PortAudioStream.deviceID
                    start = started
                    var disconnected = false
                    disconnected = try {
                        setDeviceID(null)
                        true
                    } finally {
                        /*
                         * If we failed to disconnect this PortAudioStream, we will not attempt to
                         * restore its state later on.
                         */
                        if (!disconnected) {
                            deviceID = null
                            start = false
                        }
                    }
                }
            }
        }
    }
    /**
     * {@inheritDoc}
     */
    /**
     * The time in milliseconds at which `Pa_ReadStream` has started malfunctioning. For
     * example, `Pa_ReadStream` returning `paTimedOut` and/or Windows Multimedia
     * reporting `MMSYSERR_NODRIVER` (may) indicate abnormal functioning.
     */
    var malfunctioningSince = NEVER
        private set

    /**
     * Current sequence number.
     */
    private var sequenceNumber = 0
    private var started = false

    /**
     * The input PortAudio stream represented by this instance.
     */
    private var stream = 0L

    /**
     * The indicator which determines whether [.stream] is busy and should not, for example,
     * be closed.
     */
    private var streamIsBusy = false

    /**
     * Initializes a new `PortAudioStream` instance which is to have its `Format`
     * -related information abstracted by a specific `FormatControl`.
     */
    init {
        val mediaServiceImpl = NeomediaServiceUtils.mediaServiceImpl
        gainControl = if (mediaServiceImpl == null)
            null
        else
            mediaServiceImpl.inputVolumeControl as GainControl

        /*
         * XXX We will add a UpdateAvailableDeviceListListener and will not remove it because we
         * will rely on PortAudioSystem's use of WeakReference.
         */
        (AudioSystem.getAudioSystem(AudioSystem.LOCATOR_PROTOCOL_PORTAUDIO) as AudioSystem2?)?.addUpdateAvailableDeviceListListener(paUpdateAvailableDeviceListListener)
    }

    @Throws(IOException::class)
    private fun connect() {
        val deviceIndex = Pa.getDeviceIndex(deviceID,  /* minInputChannels */
                1,  /* minOutputChannels */0)
        if (deviceIndex == Pa.paNoDevice) {
            throw IOException("The audio device $deviceID appears to be disconnected.")
        }
        val format = mFormat
        var channels = format!!.channels
        if (channels == Format.NOT_SPECIFIED) channels = 1
        val sampleSizeInBits = format.sampleSizeInBits
        val sampleFormat = Pa.getPaSampleFormat(sampleSizeInBits)
        val sampleRate = format.sampleRate
        val framesPerBuffer = (sampleRate * Pa.DEFAULT_MILLIS_PER_BUFFER / (channels * 1000)).toLong()

        try {
            inputParameters = Pa.StreamParameters_new(deviceIndex, channels, sampleFormat,
                    Pa.suggestedLatency)
            stream = Pa.OpenStream(inputParameters, 0 /* outputParameters */, sampleRate,
                    framesPerBuffer, Pa.STREAM_FLAGS_CLIP_OFF or Pa.STREAM_FLAGS_DITHER_OFF, null /* streamCallback */)
        } catch (paex: PortAudioException) {
            Timber.e(paex, "Failed to open %s", javaClass.simpleName)
            throw IOException(paex.localizedMessage, paex)
        } finally {
            if (stream == 0L && inputParameters != 0L) {
                Pa.StreamParameters_free(inputParameters)
                inputParameters = 0
            }
        }

        if (stream == 0L) throw IOException("Pa_OpenStream")
        this.framesPerBuffer = framesPerBuffer
        bytesPerBuffer = Pa.GetSampleSize(sampleFormat) * channels * framesPerBuffer.toInt()

        /*
         * Know the Format in which this PortAudioStream will output audio data so that it can
         * report it without going through its DataSource.
         */
        this.mFormat = AudioFormat(AudioFormat.LINEAR, sampleRate, sampleSizeInBits, channels,
                AudioFormat.LITTLE_ENDIAN, AudioFormat.SIGNED,
                Format.NOT_SPECIFIED /* frameSizeInBits */, Format.NOT_SPECIFIED /* frameRate */.toDouble(),
                Format.byteArray)
        var denoise = false
        var echoCancel = false
        var echoCancelFilterLengthInMillis = DeviceConfiguration.DEFAULT_AUDIO_ECHOCANCEL_FILTER_LENGTH_IN_MILLIS
        if (audioQualityImprovement) {
            val audioSystem = AudioSystem.getAudioSystem(AudioSystem.LOCATOR_PROTOCOL_PORTAUDIO)
            if (audioSystem != null) {
                denoise = audioSystem.isDenoise
                echoCancel = audioSystem.isEchoCancel
                if (echoCancel) {
                    val mediaServiceImpl = NeomediaServiceUtils.mediaServiceImpl
                    if (mediaServiceImpl != null) {
                        val devCfg = mediaServiceImpl.deviceConfiguration
                        if (devCfg != null) {
                            echoCancelFilterLengthInMillis = devCfg.echoCancelFilterLengthInMillis
                        }
                    }
                }
            }
        }
        Pa.setDenoise(stream, denoise)
        Pa.setEchoFilterLengthInMillis(stream, if (echoCancel) echoCancelFilterLengthInMillis else 0)

        // Pa_ReadStream has not been invoked yet.
        if (malfunctioningSince != NEVER) setReadIsMalfunctioning(false)
    }

    /**
     * Gets the `Format` of this `PullBufferStream` as directly known by it.
     *
     * @return the `Format` of this `PullBufferStream` as directly known by it or
     * `null` if this `PullBufferStream` does not directly know its
     * `Format` and it relies on the `PullBufferDataSource` which created it
     * to report its `Format`
     * @see AbstractPullBufferStream.doGetFormat
     */
    override fun doGetFormat(): Format? {
        return if (mFormat == null) super.doGetFormat() else mFormat
    }

    /**
     * Reads media data from this `PullBufferStream` into a specific `Buffer` with
     * blocking.
     *
     * @param buffer the `Buffer` in which media data is to be read from this
     * `PullBufferStream`
     * @throws IOException if anything goes wrong while reading media data from this `PullBufferStream`
     * into the specified `buffer`
     */
    @Throws(IOException::class)
    override fun read(buffer: Buffer) {
        var message: String?
        synchronized(this) {
            if (stream == 0L) message = javaClass.name + " is disconnected." else if (!started) message = javaClass.name + " is stopped." else {
                message = null
                streamIsBusy = true
            }
            if (message != null) {
                /*
                 * There is certainly a problem but it is other than a malfunction in Pa_ReadStream.
                 */
                if (malfunctioningSince != NEVER) setReadIsMalfunctioning(false)
            }
        }

        /*
         * The caller shouldn't call #read(Buffer) if this instance is disconnected or stopped.
         * Additionally, if she does, she may be persistent. If we do not slow her down, she may hog
         * the CPU.
         */
        if (message != null) {
            yield()
            throw IOException(message)
        }
        var errorCode = Pa.paNoError.toLong()
        var hostApiType: Pa.HostApiTypeId? = null
        try {
            /*
             * Reuse the data of buffer in order to not perform unnecessary allocations.
             */
            val data = AbstractCodec2.validateByteArraySize(buffer, bytesPerBuffer, false)
            try {
                Pa.ReadStream(stream, data, framesPerBuffer)
            } catch (pae: PortAudioException) {
                errorCode = pae.errorCode
                hostApiType = pae.hostApiType
                Timber.e(pae, "Failed to read from PortAudio stream.")
                throw IOException(pae.localizedMessage, pae)
            }

            /*
             * Take into account the user's preferences with respect to the input volume.
             */
            if (gainControl != null) {
                BasicVolumeControl.applyGain(gainControl, data, 0, bytesPerBuffer)
            }
            val bufferTimeStamp = System.nanoTime()
            buffer.flags = Buffer.FLAG_SYSTEM_TIME
            if (mFormat != null) buffer.format = mFormat
            buffer.header = null
            buffer.length = bytesPerBuffer
            buffer.offset = 0
            buffer.sequenceNumber = sequenceNumber++.toLong()
            buffer.timeStamp = bufferTimeStamp
        } finally {
            /*
             * If a timeout has occurred in the method Pa.ReadStream, give the application a little
             * time to allow it to possibly get its act together. The same treatment sounds
             * appropriate on Windows as soon as the wmme host API starts reporting that no device
             * driver is present.
             */
            var yield = false
            synchronized(this) {
                streamIsBusy = false
                notifyAll()
                if (errorCode == Pa.paNoError.toLong()) {
                    // Pa_ReadStream appears to function normally.
                    if (malfunctioningSince != NEVER) setReadIsMalfunctioning(false)
                } else if (Pa.paTimedOut == errorCode || Pa.HostApiTypeId.paMME == hostApiType && Pa.MMSYSERR_NODRIVER == errorCode) {
                    if (malfunctioningSince == NEVER) setReadIsMalfunctioning(true)
                    yield = true
                }
            }
            if (yield) yield()
        }
    }

    /**
     * Sets the device index of the PortAudio device to be read through this
     * `PullBufferStream`.
     *
     * @param deviceID The ID of the device used to be read trough this PortAudioStream. This String contains
     * the deviceUID, or if not available, the device name. If set to null, then there was no
     * device used before the update.
     * @throws IOException if input/output error occurred
     */
    @Synchronized
    @Throws(IOException::class)
    fun setDeviceID(deviceID: String?) {
        /*
         * We should better not short-circuit because the deviceID may be the same but it eventually
         * resolves to a deviceIndex and may have changed after hotplugging.
         */

        // DataSource#disconnect
        if (this.deviceID != null) {
            /*
             * Just to be on the safe side, make sure #read(Buffer) is not currently executing.
             */
            waitWhileStreamIsBusy()
            if (stream != 0L) {
                /*
                 * For the sake of completeness, attempt to stop this instance before disconnecting
                 * it.
                 */
                if (started) {
                    try {
                        stop()
                    } catch (ioe: IOException) {
                        /*
                         * The exception should have already been logged by the method #stop().
                         * Additionally and as said above, we attempted it out of courtesy.
                         */
                    }
                }
                var closed = false
                try {
                    Pa.CloseStream(stream)
                    closed = true
                } catch (pae: PortAudioException) {
                    /*
                     * The function Pa_CloseStream is not supposed to time out under normal
                     * execution. However, we have modified it to do so under exceptional
                     * circumstances on Windows at least in order to overcome endless loops related
                     * to hotplugging. In such a case, presume the native PortAudio stream closed in
                     * order to maybe avoid a crash at the risk of a memory leak.
                     */
                    val errorCode = pae.errorCode
                    if (errorCode == Pa.paTimedOut || Pa.HostApiTypeId.paMME == pae.hostApiType && errorCode == Pa.MMSYSERR_NODRIVER) {
                        closed = true
                    }
                    if (!closed) {
                        Timber.e(pae, "Failed to close %s", javaClass.simpleName)
                        throw IOException(pae.localizedMessage, pae)
                    }
                } finally {
                    if (closed) {
                        stream = 0
                        if (inputParameters != 0L) {
                            Pa.StreamParameters_free(inputParameters)
                            inputParameters = 0
                        }

                        /*
                         * Make sure this AbstractPullBufferStream asks its DataSource for the
                         * Format in which it is supposed to output audio data the next time it is
                         * opened instead of using its Format from a previous open.
                         */
                        mFormat = null
                        if (malfunctioningSince != NEVER) setReadIsMalfunctioning(false)
                    }
                }
            }
        }
        this.deviceID = deviceID
        started = false

        // DataSource#connect
        if (this.deviceID != null) {
            val audioSystem = AudioSystem.getAudioSystem(AudioSystem.LOCATOR_PROTOCOL_PORTAUDIO) as AudioSystem2?
            audioSystem?.willOpenStream()
            try {
                connect()
            } finally {
                audioSystem?.didOpenStream()
            }
        }
    }

    /**
     * Indicates whether `Pa_ReadStream` is malfunctioning.
     *
     * @param malfunctioning `true` if `Pa_ReadStream` is malfunctioning; otherwise, `false`
     */
    private fun setReadIsMalfunctioning(malfunctioning: Boolean) {
        if (malfunctioning) {
            if (malfunctioningSince == NEVER) {
                malfunctioningSince = System.currentTimeMillis()
                PortAudioSystem.monitorFunctionalHealth(diagnosticsControl)
            }
        } else malfunctioningSince = NEVER
    }

    /**
     * Starts the transfer of media data from this `PullBufferStream`.
     *
     * @throws IOException if anything goes wrong while starting the transfer of media data from this
     * `PullBufferStream`
     */
    @Synchronized
    @Throws(IOException::class)
    override fun start() {
        if (stream != 0L) {
            waitWhileStreamIsBusy()
            started = try {
                Pa.StartStream(stream)
                true
            } catch (paex: PortAudioException) {
                Timber.e(paex, "Failed to start %s", javaClass.simpleName)
                throw IOException(paex.localizedMessage, paex)
            }
        }
    }

    /**
     * Stops the transfer of media data from this `PullBufferStream`.
     *
     * @throws IOException if anything goes wrong while stopping the transfer of media data from this
     * `PullBufferStream`
     */
    @Synchronized
    @Throws(IOException::class)
    override fun stop() {
        if (stream != 0L) {
            waitWhileStreamIsBusy()
            try {
                Pa.StopStream(stream)
                started = false
                if (malfunctioningSince != NEVER) setReadIsMalfunctioning(false)
            } catch (paex: PortAudioException) {
                Timber.e(paex, "Failed to stop %s", javaClass.simpleName)
                throw IOException(paex.localizedMessage, paex)
            }
        }
    }

    /**
     * Waits on this instance while [.streamIsBusy] is equal to `true` i.e. until it
     * becomes `false`. The method should only be called by a thread that is the owner of
     * this object's monitor.
     */
    private fun waitWhileStreamIsBusy() {
        var interrupted = false
        while (stream != 0L && streamIsBusy) {
            try {
                (this as Object).wait()
            } catch (iex: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    companion object {
        /**
         * The constant which expresses a non-existent time in milliseconds for the purposes of
         * [.readIsMalfunctioningSince].
         */
        private const val NEVER = DiagnosticsControl.NEVER

        /**
         * Causes the currently executing thread to temporarily pause and allow other threads to
         * execute.
         */
        fun yield() {
            var interrupted = false
            try {
                Thread.sleep(Pa.DEFAULT_MILLIS_PER_BUFFER)
            } catch (ie: InterruptedException) {
                interrupted = true
            }
            if (interrupted) Thread.currentThread().interrupt()
        }
    }
}