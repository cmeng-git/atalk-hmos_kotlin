/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.renderer.audio

import okhttp3.internal.notifyAll
import org.atalk.impl.neomedia.control.DiagnosticsControl
import org.atalk.impl.neomedia.device.*
import org.atalk.impl.neomedia.device.AudioSystem.DataFlow
import org.atalk.impl.neomedia.jmfext.media.protocol.portaudio.DataSource.Companion.getDeviceID
import org.atalk.impl.neomedia.jmfext.media.protocol.portaudio.PortAudioStream
import org.atalk.impl.neomedia.portaudio.Pa
import org.atalk.impl.neomedia.portaudio.Pa.CloseStream
import org.atalk.impl.neomedia.portaudio.Pa.DeviceInfo_getMaxOutputChannels
import org.atalk.impl.neomedia.portaudio.Pa.DeviceInfo_getName
import org.atalk.impl.neomedia.portaudio.Pa.GetDeviceInfo
import org.atalk.impl.neomedia.portaudio.Pa.GetSampleSize
import org.atalk.impl.neomedia.portaudio.Pa.HostApiTypeId
import org.atalk.impl.neomedia.portaudio.Pa.IsFormatSupported
import org.atalk.impl.neomedia.portaudio.Pa.OpenStream
import org.atalk.impl.neomedia.portaudio.Pa.StartStream
import org.atalk.impl.neomedia.portaudio.Pa.StopStream
import org.atalk.impl.neomedia.portaudio.Pa.StreamParameters_free
import org.atalk.impl.neomedia.portaudio.Pa.StreamParameters_new
import org.atalk.impl.neomedia.portaudio.Pa.WriteStream
import org.atalk.impl.neomedia.portaudio.Pa.getDeviceIndex
import org.atalk.impl.neomedia.portaudio.Pa.getPaSampleFormat
import org.atalk.impl.neomedia.portaudio.Pa.suggestedLatency
import org.atalk.impl.neomedia.portaudio.PortAudioException
import org.atalk.service.neomedia.BasicVolumeControl
import timber.log.Timber
import java.awt.Component
import java.beans.PropertyChangeEvent
import java.lang.reflect.UndeclaredThrowableException
import javax.media.*
import javax.media.format.AudioFormat
import kotlin.math.min

/**
 * Implements an audio `Renderer` which uses Pa.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class PortAudioRenderer @JvmOverloads constructor(
        enableVolumeControl: Boolean = true,
) : AbstractAudioRenderer<PortAudioSystem>(AudioSystem.LOCATOR_PROTOCOL_PORTAUDIO,
    if (enableVolumeControl) DataFlow.PLAYBACK else DataFlow.NOTIFY) {
    /**
     * The audio samples left unwritten by a previous call to [.process]. As
     * [.bytesPerBuffer] number of bytes are always written, the number of the unwritten
     * audio samples is always less than that.
     */
    private var bufferLeft: ByteArray? = null

    /**
     * The number of bytes in [.bufferLeft] representing unwritten audio samples.
     */
    private var bufferLeftLength = 0

    /**
     * The number of bytes to write to the native PortAudio stream represented by this instance
     * with a single invocation. Based on [.framesPerBuffer].
     */
    private var bytesPerBuffer = 0

    /**
     * The `DiagnosticsControl` implementation of this instance which allows the
     * diagnosis of the functional health of `Pa_WriteStream`.
     */
    private val diagnosticsControl: DiagnosticsControl = object : DiagnosticsControl {
        /**
         * {@inheritDoc}
         *
         * `PortAudioRenderer`'s `DiagnosticsControl` implementation does not provide
         * its own user interface and always returns `null`.
         */
        override fun getControlComponent(): Component? {
            return null
        }

        override val malfunctioningSince: Long
            get() = writeIsMalfunctioningSince

        /**
         * {@inheritDoc}
         *
         * Returns the identifier of the PortAudio device written through this
         * `PortAudioRenderer`.
         */
        override fun toString(): String {
            val locator = getLocator()
            var name: String? = null
            if (locator != null) {
                val id = getDeviceID(locator)
                if (id != null) {
                    val index = getDeviceIndex(id,
                        0 /* minInputChannels */,
                        1 /* minOutputChannels */)
                    if (index != Pa.paNoDevice) {
                        val info = GetDeviceInfo(index)
                        if (info != 0L) name = DeviceInfo_getName(info)
                    }
                }
            }
            return name!!
        }
    }

    /**
     * The flags which represent certain state of this `PortAudioRenderer`. Acceptable
     * values
     * are among the `FLAG_XXX` constants defined by the `PortAudioRenderer` class.
     * For example, [.FLAG_OPEN] indicates that from the public point of view [.open]
     * has been invoked on this `Renderer` without an intervening [.close].
     */
    private var flags: Byte = 0

    /**
     * The number of frames to write to the native PortAudio stream represented by this instance
     * with a single invocation.
     */
    private var framesPerBuffer = 0
    private var outputParameters = 0L

    /**
     * The `PaUpdateAvailableDeviceListListener` which is to be notified before and after
     * PortAudio's native function `Pa_UpdateAvailableDeviceList()` is invoked. It will
     * close
     * [.stream] before the invocation in order to mitigate memory corruption afterwards and
     * it will attempt to restore the state of this `Renderer` after the invocation.
     */
    private val paUpdateAvailableDeviceListListener = object : UpdateAvailableDeviceListListener {
        @Throws(Exception::class)
        override fun didUpdateAvailableDeviceList() {
            synchronized(this@PortAudioRenderer) {
                waitWhileStreamIsBusy()

                /*
                 * PortAudioRenderer's field flags represents its open and started state from the
                 * public point of view. We will automatically open and start this Renderer i.e. we
                 * will be modifying the state from the private point of view only and,
                 * consequently, we have to make sure that we will not modify it from the public
                 * point of view.
                 */
                val flags = flags
                try {
                    if (FLAG_OPEN and flags.toInt() == FLAG_OPEN) {
                        open()
                        if (FLAG_STARTED and flags.toInt() == FLAG_STARTED) start()
                    }
                } finally {
                    this@PortAudioRenderer.flags = flags
                }
            }
        }

        @Throws(Exception::class)
        override fun willUpdateAvailableDeviceList() {
            synchronized(this@PortAudioRenderer) {
                waitWhileStreamIsBusy()

                /*
                 * PortAudioRenderer's field flags represents its open and started state from the
                 * public point of view. We will automatically close this Renderer i.e. we will be
                 * modifying the state from the private point of view only and, consequently, we
                 * have to make sure that we will not modify it from the public point of view.
                 */
                val flags = flags
                try {
                    if (stream != 0L) close()
                } finally {
                    this@PortAudioRenderer.flags = flags
                }
            }
        }
    }

    /**
     * The indicator which determines whether this `Renderer` is started.
     */
    private var started = false

    /**
     * The output PortAudio stream represented by this instance.
     */
    private var stream = 0L

    /**
     * The indicator which determines whether [.stream] is busy and should not, for example,
     * be closed.
     */
    private var streamIsBusy = false

    /**
     * Array of supported input formats.
     */
    private var supportedInputFormats: Array<Format>? = null
    /**
     * {@inheritDoc}
     */
    /**
     * The time in milliseconds at which `Pa_WriteStream` has started malfunctioning. For
     * example, `Pa_WriteStream` returning `paTimedOut` and/or Windows Multimedia
     * reporting `MMSYSERR_NODRIVER` (may) indicate abnormal functioning.
     */
    var writeIsMalfunctioningSince = DiagnosticsControl.NEVER
        private set

    /**
     * Initializes a new `PortAudioRenderer` instance which is to either perform playback or
     * sound a notification.
     *
     * playback `true` if the new instance is to perform playback or `false` if the new
     * instance is to sound a notification
     */
    /**
     * Initializes a new `PortAudioRenderer` instance.
     */
    init {

        /*
         * XXX We will add a PaUpdateAvailableDeviceListListener and will not remove it because we
         * will rely on PortAudioSystem's use of WeakReference.
         */
        audioSystem?.addUpdateAvailableDeviceListListener(paUpdateAvailableDeviceListListener)
    }

    /**
     * Closes this `PlugIn`.
     */
    @Synchronized
    override fun close() {
        try {
            stop()
        } finally {
            if (stream != 0L) {
                try {
                    CloseStream(stream)
                    stream = 0
                    started = false
                    flags = (flags.toInt() and (FLAG_OPEN or FLAG_STARTED).inv()).toByte()
                    if (writeIsMalfunctioningSince != DiagnosticsControl.NEVER) setWriteIsMalfunctioning(false)
                } catch (paex: PortAudioException) {
                    Timber.e("paex. Failed to close PortAudio stream.")
                }
            }
            if (stream == 0L && outputParameters != 0L) {
                StreamParameters_free(outputParameters)
                outputParameters = 0
            }
            super.close()
        }
    }

    /**
     * Gets the descriptive/human-readable name of this JMF plug-in.
     *
     * @return the descriptive/human-readable name of this JMF plug-in
     */
    override fun getName(): String {
        return PLUGIN_NAME
    }

    /**
     * Gets the list of JMF `Format`s of audio data which this `Renderer` is capable
     * of rendering.
     *
     * @return an array of JMF `Format`s of audio data which this `Renderer` is
     * capable of rendering
     */
    override fun getSupportedInputFormats(): Array<Format> {
        if (supportedInputFormats == null) {
            val locator = getLocator()
            var deviceID = ""
            var deviceIndex = 0
            var deviceInfo = 0L

            when {
                locator == null || getDeviceID(locator).also { deviceID = it } == null || deviceID.isEmpty() || getDeviceIndex(deviceID,
                    0 /* minInputChannels */,
                    1 /* minOutputChannels */).also { deviceIndex = it } == Pa.paNoDevice || GetDeviceInfo(deviceIndex).also { deviceInfo = it } == 0L -> {
                    supportedInputFormats = SUPPORTED_INPUT_FORMATS
                }

                getDeviceID(locator).also { deviceID = it } == null || deviceID.isEmpty() || getDeviceIndex(deviceID,
                    0,
                    1).also { deviceIndex = it } == Pa.paNoDevice || GetDeviceInfo(deviceIndex).also { deviceInfo = it } == 0L -> {
                    supportedInputFormats = SUPPORTED_INPUT_FORMATS
                }

                else -> {
                    val minOutputChannels = 1
                    /*
                     * The maximum output channels may be a lot and checking all of them will take a
                     * lot of time. Besides, we currently support at most 2.
                     */
                    val maxOutputChannels = min(DeviceInfo_getMaxOutputChannels(deviceInfo), 2)
                    val supportedInputFormats = arrayOf<Format>()
                    for (supportedInputFormat in SUPPORTED_INPUT_FORMATS) {
                        getSupportedInputFormats(supportedInputFormat, deviceIndex, minOutputChannels,
                            maxOutputChannels, supportedInputFormats)
                    }
                    this.supportedInputFormats = if (supportedInputFormats.isEmpty()) EMPTY_SUPPORTED_INPUT_FORMATS else supportedInputFormats
                }
            }
        }
        return if (supportedInputFormats!!.isEmpty()) EMPTY_SUPPORTED_INPUT_FORMATS else supportedInputFormats!!.clone()
    }

    private fun getSupportedInputFormats(
            format: Format?, deviceIndex: Int, minOutputChannels: Int,
            maxOutputChannels: Int, supportedInputFormats: Array<Format>,
    ) {
        val audioFormat = format as AudioFormat?
        val sampleSizeInBits = audioFormat!!.sampleSizeInBits
        val sampleFormat = getPaSampleFormat(sampleSizeInBits)
        val sampleRate = audioFormat.sampleRate
        var i = 0
        for (channels in minOutputChannels..maxOutputChannels) {
            val outputParameters = StreamParameters_new(deviceIndex, channels, sampleFormat,
                Pa.LATENCY_UNSPECIFIED)
            if (outputParameters != 0L) {
                try {
                    if (IsFormatSupported(0, outputParameters, sampleRate)) {
                        supportedInputFormats[i++] = AudioFormat(
                            audioFormat.encoding,
                            sampleRate,
                            sampleSizeInBits,
                            channels,
                            audioFormat.endian,
                            audioFormat.signed,
                            Format.NOT_SPECIFIED /* frameSizeInBits */,
                            NOT_SPECIFIED_DOUBLE /* frameRate */,
                            audioFormat.dataType)
                    }
                } finally {
                    StreamParameters_free(outputParameters)
                }
            }
        }
    }

    /**
     * Opens the PortAudio device and output stream represented by this instance which are to be
     * used to render audio.
     *
     * @throws ResourceUnavailableException if the PortAudio device or output stream cannot be created or opened
     */
    @Synchronized
    @Throws(ResourceUnavailableException::class)
    override fun open() {
        try {
            audioSystem!!.willOpenStream()
            try {
                doOpen()
            } finally {
                audioSystem.didOpenStream()
            }
        } catch (t: Throwable) {
            /*
             * Log the problem because FMJ may swallow it and thus make debugging harder than necessary.
             */
            Timber.d(t, "Failed to open PortAudioRenderer")
            when (t) {
                is ThreadDeath -> throw t
                is ResourceUnavailableException -> throw t
                else -> {
                    val rue = ResourceUnavailableException()
                    rue.initCause(t)
                    throw rue
                }
            }
        }
        super.open()
    }

    /**
     * Opens the PortAudio device and output stream represented by this instance which are to be
     * used to render audio.
     *
     * @throws ResourceUnavailableException if the PortAudio device or output stream cannot be created or opened
     */
    @Throws(ResourceUnavailableException::class)
    private fun doOpen() {
        if (stream == 0L) {
            val locator = getLocator() ?: throw ResourceUnavailableException("No locator/MediaLocator is set.")

            val deviceID = getDeviceID(locator)
            val deviceIndex = getDeviceIndex(deviceID,
                0 /* minInputChannels */,
                1 /* minOutputChannels */)
            if (deviceIndex == Pa.paNoDevice) {
                throw ResourceUnavailableException("The audio device " + deviceID
                        + " appears to be disconnected.")
            }
            val inputFormat = inputFormat
                    ?: throw ResourceUnavailableException("inputFormat not set")
            var channels = inputFormat.channels
            if (channels == Format.NOT_SPECIFIED) channels = 1
            val sampleFormat = getPaSampleFormat(inputFormat.sampleSizeInBits)
            val sampleRate = inputFormat.sampleRate
            framesPerBuffer = (sampleRate * Pa.DEFAULT_MILLIS_PER_BUFFER
                    / (channels * 1000)).toInt()
            try {
                outputParameters = StreamParameters_new(deviceIndex, channels, sampleFormat,
                    suggestedLatency)
                stream = OpenStream(0 /* inputParameters */,
                    outputParameters,
                    sampleRate, framesPerBuffer.toLong(),
                    Pa.STREAM_FLAGS_CLIP_OFF or Pa.STREAM_FLAGS_DITHER_OFF,
                    null /* streamCallback */)
            } catch (paex: PortAudioException) {
                Timber.e(paex, "Failed to open PortAudio stream.")
                throw ResourceUnavailableException(paex.message)
            } finally {
                started = false
                if (stream == 0L) {
                    flags = (flags.toInt() and (FLAG_OPEN or FLAG_STARTED).inv()).toByte()
                    if (outputParameters != 0L) {
                        StreamParameters_free(outputParameters)
                        outputParameters = 0
                    }
                }
                else {
                    flags = (flags.toInt() or (FLAG_OPEN or FLAG_STARTED)).toByte()
                }
            }
            if (stream == 0L) throw ResourceUnavailableException("Pa_OpenStream")
            bytesPerBuffer = GetSampleSize(sampleFormat) * channels * framesPerBuffer

            // Pa_WriteStream has not been invoked yet.
            if (writeIsMalfunctioningSince != DiagnosticsControl.NEVER) setWriteIsMalfunctioning(false)
        }
    }

    /**
     * Notifies this instance that the value of the AudioSystem.PROP_PLAYBACK_DEVICE
     * property of its associated `AudioSystem` has changed.
     *
     * @param ev a `PropertyChangeEvent` which specifies details about the change such as the
     * name of the property and its old and new values
     */
    @Synchronized
    override fun playbackDevicePropertyChange(ev: PropertyChangeEvent?) {
        /*
         * Stop, close, re-open and re-start this Renderer (performing whichever of these in order
         * to bring it into the same state) in order to reflect the change in the selection with
         * respect to the playback device.
         */
        waitWhileStreamIsBusy()

        /*
         * From the public point of view, the state of this PortAudioRenderer remains the same.
         */
        val flags = flags
        try {
            if (FLAG_OPEN and flags.toInt() == FLAG_OPEN) {
                close()
                try {
                    open()
                } catch (rue: ResourceUnavailableException) {
                    throw UndeclaredThrowableException(rue)
                }
                if (FLAG_STARTED and flags.toInt() == FLAG_STARTED) start()
            }
        } finally {
            this.flags = flags
        }
    }

    /**
     * Renders the audio data contained in a specific `Buffer` onto the PortAudio device
     * represented by this `Renderer`.
     *
     * @param buffer the `Buffer` which contains the audio data to be rendered
     * @return `PlugIn.BUFFER_PROCESSED_OK` if the specified `buffer` has been successfully
     * processed
     */
    override fun process(buffer: Buffer): Int {
        synchronized(this) {
            streamIsBusy = if (!started || stream == 0L) {
                /*
                 * The execution is somewhat abnormal but it is not because of a malfunction in
                 * Pa_WriteStream.
                 */
                if (writeIsMalfunctioningSince != DiagnosticsControl.NEVER) setWriteIsMalfunctioning(false)
                return PlugIn.BUFFER_PROCESSED_OK
            }
            else true
        }
        var errorCode = Pa.paNoError.toLong()
        var hostApiType: HostApiTypeId? = null
        try {
            process(buffer.data as ByteArray, buffer.offset, buffer.length)
        } catch (pae: PortAudioException) {
            errorCode = pae.errorCode
            hostApiType = pae.hostApiType
            Timber.e(pae, "Failed to process Buffer.")
        } finally {
            /*
             * If a timeout has occurred in the method Pa.WriteStream, give the application a
             * little time to allow it to possibly get its act together. The same treatment sounds
             * appropriate on Windows as soon as the wmme host API starts reporting that no device
             * driver is present.
             */
            var yield = false
            synchronized(this) {
                streamIsBusy = false
                notifyAll()
                if (errorCode == Pa.paNoError.toLong()) {
                    // Pa_WriteStream appears to function normally.
                    if (writeIsMalfunctioningSince != DiagnosticsControl.NEVER) setWriteIsMalfunctioning(false)
                }
                else if (Pa.paTimedOut == errorCode || HostApiTypeId.paMME ==
                        hostApiType && Pa.MMSYSERR_NODRIVER == errorCode) {
                    if (writeIsMalfunctioningSince == DiagnosticsControl.NEVER) setWriteIsMalfunctioning(true)
                    yield = true
                }
            }
            if (yield) PortAudioStream.yield()
        }
        return PlugIn.BUFFER_PROCESSED_OK
    }

    @Throws(PortAudioException::class)
    private fun process(buffer: ByteArray, ofs: Int, len: Int) {

        /*
         * If there are audio samples left unwritten from a previous write, prepend them to the
         * specified buffer. If it's possible to write them now, do it.
         */
        var offset = ofs
        var length = len
        if (bufferLeft != null && bufferLeftLength > 0) {
            val numberOfBytesInBufferLeftToBytesPerBuffer = bytesPerBuffer - bufferLeftLength
            val numberOfBytesToCopyToBufferLeft = if (numberOfBytesInBufferLeftToBytesPerBuffer < length) numberOfBytesInBufferLeftToBytesPerBuffer else length
            System.arraycopy(buffer, offset, bufferLeft!!, bufferLeftLength,
                numberOfBytesToCopyToBufferLeft)
            offset += numberOfBytesToCopyToBufferLeft
            length -= numberOfBytesToCopyToBufferLeft
            bufferLeftLength += numberOfBytesToCopyToBufferLeft
            if (bufferLeftLength == bytesPerBuffer) {
                WriteStream(stream, bufferLeft, framesPerBuffer.toLong())
                bufferLeftLength = 0
            }
        }

        // Write the audio samples from the specified buffer.
        val numberOfWrites = length / bytesPerBuffer
        if (numberOfWrites > 0) {
            /*
             * Take into account the user's preferences with respect to the output volume.
             */
            val gainControl = getGainControl()
            if (gainControl != null) {
                BasicVolumeControl.applyGain(gainControl, buffer, offset, length)
            }
            WriteStream(stream, buffer, offset, framesPerBuffer.toLong(), numberOfWrites)
            val bytesWritten = numberOfWrites * bytesPerBuffer
            offset += bytesWritten
            length -= bytesWritten
        }

        // If anything was left unwritten, remember it for next time.
        if (length > 0) {
            if (bufferLeft == null) bufferLeft = ByteArray(bytesPerBuffer)
            System.arraycopy(buffer, offset, bufferLeft!!, 0, length)
            bufferLeftLength = length
        }
    }

    /**
     * Sets the `MediaLocator` which specifies the device index of the PortAudio device
     * to be used by this instance for rendering.
     *
     * @param locator a `MediaLocator` which specifies the device index of the PortAudio device to be
     * used by this instance for rendering
     */
    override fun setLocator(locator: MediaLocator?) {
        super.setLocator(locator)
        supportedInputFormats = null
    }

    /**
     * Indicates whether `Pa_WriteStream` is malfunctioning.
     *
     * @param writeIsMalfunctioning `true` if `Pa_WriteStream` is malfunctioning; otherwise, `false`
     */
    private fun setWriteIsMalfunctioning(writeIsMalfunctioning: Boolean) {
        if (writeIsMalfunctioning) {
            if (writeIsMalfunctioningSince == DiagnosticsControl.NEVER) {
                writeIsMalfunctioningSince = System.currentTimeMillis()
                PortAudioSystem.monitorFunctionalHealth(diagnosticsControl)
            }
        }
        else writeIsMalfunctioningSince = DiagnosticsControl.NEVER
    }

    /**
     * Starts the rendering process. Any audio data available in the internal resources associated
     * with this `PortAudioRenderer` will begin being rendered.
     */
    @Synchronized
    override fun start() {
        if (!started && stream != 0L) {
            try {
                StartStream(stream)
                started = true
                flags = (flags.toInt() or FLAG_STARTED).toByte()
            } catch (paex: PortAudioException) {
                Timber.e(paex, "Failed to start PortAudio stream.")
            }
        }
    }

    /**
     * Stops the rendering process.
     */
    @Synchronized
    override fun stop() {
        waitWhileStreamIsBusy()
        if (started && stream != 0L) {
            try {
                StopStream(stream)
                started = false
                flags = (flags.toInt() and FLAG_STARTED.inv()).toByte()
                bufferLeft = null
                if (writeIsMalfunctioningSince != DiagnosticsControl.NEVER) setWriteIsMalfunctioning(false)
            } catch (paex: PortAudioException) {
                Timber.e(paex, "Failed to close PortAudio stream.")
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
        while (streamIsBusy) {
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
         * The constant which represents an empty array with `Format` element type. Explicitly
         * defined in order to reduce unnecessary allocations.
         */
        private val EMPTY_SUPPORTED_INPUT_FORMATS = arrayOf<Format>()

        /**
         * The flag which indicates that [.open] has been called on a
         * `PortAudioRenderer` without an intervening [.close]. The state it
         * represents is from the public point of view. The private point of view is represented by
         * [.stream].
         */
        private const val FLAG_OPEN = 1

        /**
         * The flag which indicates that [.start] has been called on a
         * `PortAudioRenderer` without an intervening [.stop]. The state it
         * represents is from the public point of view. The private point of view is represented by
         * [.started].
         */
        private const val FLAG_STARTED = 2

        /**
         * The human-readable name of the `PortAudioRenderer` JMF plug-in.
         */
        private const val PLUGIN_NAME = "PortAudio Renderer"

        /**
         * The list of JMF `Format`s of audio data which `PortAudioRenderer` instances
         * are capable of rendering.
         */
        private val SUPPORTED_INPUT_FORMATS: Array<Format>

        /**
         * The list of the sample rates supported by `PortAudioRenderer` as input.
         */
        private val SUPPORTED_INPUT_SAMPLE_RATES = doubleArrayOf(8000.0, 11025.0, 16000.0, 22050.0, 32000.0, 44100.0, 48000.0)

        init {
            val count = SUPPORTED_INPUT_SAMPLE_RATES.size
            SUPPORTED_INPUT_FORMATS = emptyArray()

            for (i in 0 until count) {
                SUPPORTED_INPUT_FORMATS[i] = AudioFormat(
                    AudioFormat.LINEAR,
                    SUPPORTED_INPUT_SAMPLE_RATES[i],
                    16,
                    Format.NOT_SPECIFIED /* channels */,
                    AudioFormat.LITTLE_ENDIAN,
                    AudioFormat.SIGNED,
                    Format.NOT_SPECIFIED /* frameSizeInBits */,
                    Format.NOT_SPECIFIED.toDouble() /* frameRate */,
                    Format.byteArray)
            }
        }
    }
}