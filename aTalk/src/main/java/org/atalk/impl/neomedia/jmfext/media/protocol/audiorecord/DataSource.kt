/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.audiorecord

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import androidx.core.app.ActivityCompat
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.util.AndroidUtils
import org.atalk.impl.neomedia.NeomediaActivator
import org.atalk.impl.neomedia.device.AudioSystem
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractBufferStream
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferCaptureDevice
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferStream
import org.atalk.service.neomedia.BasicVolumeControl
import timber.log.Timber
import java.io.IOException
import javax.media.Buffer
import javax.media.Format
import javax.media.GainControl
import javax.media.MediaLocator
import javax.media.control.FormatControl
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Implements an audio `CaptureDevice` using [AudioRecord].
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class DataSource : AbstractPullBufferCaptureDevice {
    /**
     * Initializes a new `DataSource` instance.
     */
    constructor()

    /**
     * Initializes a new `DataSource` from a specific `MediaLocator`.
     *
     * @param locator the `MediaLocator` to create the new instance from
     */
    constructor(locator: MediaLocator?) : super(locator)

    /**
     * Creates a new `PullBufferStream` which is to be at a specific zero-based index in the
     * list of streams of this `PullBufferDataSource`. The `Format`-related
     * information of the new instance is to be abstracted by a specific `FormatControl`.
     *
     * @param streamIndex the zero-based index of the `PullBufferStream` in the list of streams of this
     * `PullBufferDataSource`
     * @param formatControl the `FormatControl` which is to abstract the `Format`-related
     * information of the new instance
     * @return a new `PullBufferStream` which is to be at the specified `streamIndex`
     * in the list of streams of this `PullBufferDataSource` and which has its
     * `Format`-related information abstracted by the specified `formatControl`
     * @see AbstractPullBufferCaptureDevice.createStream
     */
    override fun createStream(streamIndex: Int, formatControl: FormatControl?): AbstractPullBufferStream<*> {
        return AudioRecordStream(this, formatControl)
    }

    /**
     * Opens a connection to the media source specified by the `MediaLocator` of this `DataSource`.
     *
     * @throws IOException if anything goes wrong while opening the connection to the media source specified by
     * the `MediaLocator` of this `DataSource`
     * @see AbstractPullBufferCaptureDevice.doConnect
     */
    @Throws(IOException::class)
    override fun doConnect() {
        super.doConnect()
        /*
         * XXX The AudioRecordStream will connect upon start in order to be able to respect requests
         * to set its format.
         */
    }

    /**
     * Closes the connection to the media source specified by the `MediaLocator` of this `DataSource`.
     *
     * @see AbstractPullBufferCaptureDevice.doDisconnect
     */
    override fun doDisconnect() {
        synchronized(streamSyncRoot) {
            val streams_ = streams
            if (streams_ != null) for (stream in streams_) (stream as AudioRecordStream?)!!.disconnect()
        }
        aTalkApp.audioManager.mode = AudioManager.MODE_NORMAL
        super.doDisconnect()
    }

    /**
     * Attempts to set the `Format` to be reported by the `FormatControl` of a
     * `PullBufferStream` at a specific zero-based index in the list of streams of this
     * `PullBufferDataSource`. The `PullBufferStream` does not exist at the time of
     * the attempt to set its `Format`. Override the default behavior which is to not attempt
     * to set the specified `Format` so that they can enable setting the `Format`
     * prior to creating the `PullBufferStream`.
     *
     * @param streamIndex the zero-based index of the `PullBufferStream` the `Format` of which is
     * to be set
     * @param oldValue the last-known `Format` for the `PullBufferStream` at the specified `streamIndex`
     * @param newValue the `Format` which is to be set
     * @return the `Format` to be reported by the `FormatControl` of the
     * `PullBufferStream` at the specified `streamIndex` in the list of
     * streams of this `PullBufferStream` or `null` if the attempt to set the
     * `Format` did not success and any last-known `Format` is to be left in effect
     * @see AbstractPullBufferCaptureDevice.setFormat
     */
    override fun setFormat(streamIndex: Int, oldValue: Format?, newValue: Format?): Format? {
        /*
         * Accept format specifications prior to the initialization of AudioRecordStream.
         * Afterwards, AudioRecordStream will decide whether to accept further format specifications.
         */
        return newValue
    }

    /**
     * Implements an audio `PullBufferStream` using [AudioRecord].
     */
    private class AudioRecordStream(dataSource: DataSource?, formatControl: FormatControl?)
        : AbstractPullBufferStream<DataSource?>(dataSource, formatControl), AudioEffect.OnEnableStatusChangeListener {

        /**
         * The `android.media.AudioRecord` which does the actual capturing of audio.
         */
        private var audioRecord: AudioRecord? = null

        /**
         * The `GainControl` through which the volume/gain of captured media is controlled.
         */
        private val gainControl: GainControl?

        /**
         * The length in bytes of the media data read into a `Buffer` via a call to [.read].
         */
        private var length = 0

        /**
         * The indicator which determines whether this `AudioRecordStream` is to set the
         * priority of the thread in which its [.read] method is executed.
         */
        private var setThreadPriority = true

        /**
         * Initializes a new `OpenSLESStream` instance which is to have its `Format`
         * -related information abstracted by a specific `FormatControl`.
         *
         * dataSource the `DataSource` which is creating the new instance so that it becomes one
         * of its `streams`
         * ormatControl the `FormatControl` which is to abstract the `Format`-related
         * information of the new instance
         */
        init {
            val mediaServiceImpl = NeomediaActivator.getMediaServiceImpl()
            gainControl = if (mediaServiceImpl == null) null else mediaServiceImpl.inputVolumeControl as GainControl?
        }

        /**
         * Opens a connection to the media source of the associated `DataSource`.
         *
         * @throws IOException if anything goes wrong while opening a connection to the media source of the
         * associated `DataSource`
         */
        @Synchronized
        @Throws(IOException::class)
        fun connect() {
            val af = format as javax.media.format.AudioFormat
            val channels = af.channels
            val channelConfig = when (channels) {
                Format.NOT_SPECIFIED, 1 -> AudioFormat.CHANNEL_IN_MONO

                2 -> AudioFormat.CHANNEL_IN_STEREO

                else -> throw IOException("channels")
            }

            val sampleSizeInBits = af.sampleSizeInBits
            val audioFormat = when (sampleSizeInBits) {
                8 -> AudioFormat.ENCODING_PCM_8BIT

                16 -> AudioFormat.ENCODING_PCM_16BIT

                else -> throw IOException("sampleSizeInBits")
            }

            val sampleRate = af.sampleRate
            /* 20 in milliseconds */
            length = (20 * (sampleRate / 1000) * channels * (sampleSizeInBits / 8.0)).roundToInt()

            /*
             * Apart from the thread in which #read(Buffer) is executed, use the thread priority for
             * the thread which will create the AudioRecord.
             */
            setThreadPriority()
            try {
                val minBufferSize = AudioRecord.getMinBufferSize(sampleRate.toInt(), channelConfig, audioFormat)

                if (ActivityCompat.checkSelfPermission(aTalkApp.globalContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    return
                }

                audioRecord = AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate.toInt(),
                        channelConfig, audioFormat, max(length, minBufferSize))

                // tries to configure audio effects if available
                configureEffects()
            } catch (iae: IllegalArgumentException) {
                throw IOException(iae)
            }
            setThreadPriority = true
        }

        /**
         * Configures echo cancellation and noise suppression effects.
         */
        private fun configureEffects() {
            if (!AndroidUtils.hasAPI(16)) return

            // Must enable to improve AEC to avoid audio howling on speaker phone enabled
            aTalkApp.audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            val audioSystem = AudioSystem.getAudioSystem(AudioSystem.LOCATOR_PROTOCOL_AUDIORECORD)

            // Creates echo canceler if available
            if (AcousticEchoCanceler.isAvailable()) {
                val echoCanceller = AcousticEchoCanceler.create(audioRecord!!.audioSessionId)
                if (echoCanceller != null) {
                    echoCanceller.setEnableStatusListener(this)
                    echoCanceller.enabled = audioSystem!!.isEchoCancel
                    Timber.i("Echo cancellation: %s", echoCanceller.enabled)
                }
            }

            // Automatic gain control
            if (AutomaticGainControl.isAvailable()) {
                val agc = AutomaticGainControl.create(audioRecord!!.audioSessionId)
                if (agc != null) {
                    agc.setEnableStatusListener(this)
                    agc.enabled = audioSystem!!.isAutomaticGainControl
                    Timber.i("Auto gain control: %s", agc.enabled)
                }
            }

            // Creates noise suppressor if available
            if (NoiseSuppressor.isAvailable()) {
                val noiseSuppressor = NoiseSuppressor.create(audioRecord!!.audioSessionId)
                if (noiseSuppressor != null) {
                    noiseSuppressor.setEnableStatusListener(this)
                    noiseSuppressor.enabled = audioSystem!!.isDenoise
                    Timber.i("Noise suppressor: %s", noiseSuppressor.enabled)
                }
            }
        }

        /**
         * Closes the connection to the media source of the associated `DataSource`.
         */
        @Synchronized
        fun disconnect() {
            if (audioRecord != null) {
                audioRecord!!.release()
                audioRecord = null
                setThreadPriority = true
            }
        }

        /**
         * Attempts to set the `Format` of this `AbstractBufferStream`.
         *
         * @param format the `Format` to be set as the format of this `AbstractBufferStream`
         * @return the `Format` of this `AbstractBufferStream` or `null` if the attempt to set
         * the `Format` did not succeed and any last-known `Format` is to be left in effect
         * @see AbstractPullBufferStream.doSetFormat
         */
        @Synchronized
        override fun doSetFormat(format: Format): Format? {
            return if (audioRecord == null) format else null
        }

        /**
         * Reads media data from this `PullBufferStream` into a specific `Buffer` with blocking.
         *
         * @param buffer the `Buffer` in which media data is to be read from this `PullBufferStream`
         * @throws IOException if anything goes wrong while reading media data from this `PullBufferStream`
         * into the specified `buffer`  @see javax.media.protocol.PullBufferStream#read(javax.media.Buffer)
         */
        @Throws(IOException::class)
        override fun read(buffer: Buffer) {
            if (setThreadPriority) {
                setThreadPriority = false
                setThreadPriority()
            }
            var data = buffer.data
            val length = length
            if (data is ByteArray) {
                if (data.size < length) data = null
            } else data = null
            if (data == null) {
                data = ByteArray(length)
                buffer.data = data
            }
            var toRead = length
            val bytes = data as ByteArray
            var offset = 0
            buffer.length = 0
            while (toRead > 0) {

                var read = -1
                var loopEnd = false
                synchronized (this) {
                    if (audioRecord!!.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                        read = audioRecord!!.read(bytes, offset, toRead);
                    else
                        loopEnd = true
                }
                if (loopEnd) break

                if (read < 0) {
                    throw IOException(AudioRecord::class.java.name + "#read(byte[], int, int) returned " + read)
                } else {
                    buffer.length = buffer.length + read
                    offset += read
                    toRead -= read
                }
            }

            buffer.offset = 0

            // Apply software gain.
            if (gainControl != null) {
                BasicVolumeControl.applyGain(gainControl, bytes, buffer.offset, buffer.length)
            }
        }

        /**
         * Starts the transfer of media data from this `AbstractBufferStream`.
         * WIll not proceed if mState == STATE_UNINITIALIZED (when mic is disabled)
         *
         * @throws IOException if anything goes wrong while starting the transfer of media data from this
         * `AbstractBufferStream`
         * @see AbstractBufferStream.start
         */
        @Throws(IOException::class)
        override fun start() {
            /*
             * Connect upon start because the connect has been delayed to allow this
             * AudioRecordStream to respect requests to set its format.
             */
            synchronized(this) { if (audioRecord == null) connect() }
            super.start()
            synchronized(this) {
                if (audioRecord != null && audioRecord!!.state == AudioRecord.STATE_INITIALIZED) {
                    setThreadPriority = true
                    audioRecord!!.startRecording()
                }
            }
        }

        /**
         * Stops the transfer of media data from this `AbstractBufferStream`.
         *
         * @throws IOException if anything goes wrong while stopping the transfer of media data from this
         * `AbstractBufferStream`
         * @see AbstractBufferStream.stop
         */
        @Throws(IOException::class)
        override fun stop() {
            synchronized(this) {
                if (audioRecord != null && audioRecord!!.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord!!.stop()
                    setThreadPriority = true
                }
            }
            super.stop()
        }

        override fun onEnableStatusChange(effect: AudioEffect, enabled: Boolean) {
            Timber.i("%s: %s", effect.descriptor, enabled)
        }
    }

    companion object {
        /**
         * The priority to be set to the thread executing the [AudioRecordStream.read]
         * method of a given `AudioRecordStream`.
         */
        private const val THREAD_PRIORITY = Process.THREAD_PRIORITY_URGENT_AUDIO

        /**
         * Sets the priority of the calling thread to [.THREAD_PRIORITY].
         */
        fun setThreadPriority() {
            setThreadPriority(THREAD_PRIORITY)
        }

        /**
         * Sets the priority of the calling thread to a specific value.
         *
         * @param threadPriority the priority to be set on the calling thread
         */
        fun setThreadPriority(threadPriority: Int) {
            try {
                Process.setThreadPriority(threadPriority)

            } catch (ex: Exception) {
                when (ex) {
                    is IllegalArgumentException,
                    is SecurityException,
                    -> {
                        Timber.w("Failed to set thread priority: %s", ex.message)
                    }
                }
            }
        }
    }
}