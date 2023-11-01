/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.pulseaudio

import org.atalk.impl.neomedia.MediaUtils
import org.atalk.impl.neomedia.NeomediaServiceUtils
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.impl.neomedia.device.PulseAudioSystem
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferCaptureDevice
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferStream
import org.atalk.impl.neomedia.pulseaudio.PA
import org.atalk.service.neomedia.BasicVolumeControl
import timber.log.Timber
import java.io.IOException
import java.util.*
import javax.media.Buffer
import javax.media.Format
import javax.media.GainControl
import javax.media.control.FormatControl
import javax.media.format.AudioFormat

/**
 * Implements `CaptureDevice` and `DataSource` using the native PulseAudio
 * API/library.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class DataSource
/**
 * Initializes a new `DataSource` instance.
 */
    : AbstractPullBufferCaptureDevice() {
    /**
     * Implements a `PullBufferStream` using the native PulseAudio API/library.
     */
    inner class PulseAudioStream(formatControl: FormatControl?) : AbstractPullBufferStream<DataSource?>(this@DataSource, formatControl) {
        /**
         * The `PulseAudioSystem` instance which provides the capture device and allows
         * creating [.stream].
         */
        private val audioSystem = PulseAudioSystem.pulseAudioSystem
        private var buffer: ByteArray? = null

        /**
         * The number of channels of audio data this `PulseAudioStream` is configured to
         * input.
         */
        private var channels = 0

        /**
         * The indicator which determines whether [.stream]'s input is paused or resumed.
         */
        private var corked = true

        /**
         * The `pa_cvolume` (structure) instance used by this `PulseAudioRenderer` to
         * set the per-channel volume of [.stream].
         */
        private var cvolume = 0L
        private var fragsize = 0

        /**
         * The `GainControl` which specifies the volume level to be applied to the audio data
         * input through this `PulseAudioStream`.
         */
        private val gainControl: GainControl?

        /**
         * The volume level specified by [.gainControl] which has been set on [.stream].
         */
        private var gainControlLevel = 0f

        /**
         * The number of bytes in [.buffer] starting at [.offset].
         */
        private var length = 0

        /**
         * The offset in [.buffer].
         */
        private var offset = 0

        /**
         * The PulseAudio callback which notifies this `PulseAudioStream` that
         * [.stream] has audio data available to input.
         */
        private val readCb = object : PA.stream_request_cb_t {
            override fun callback(s: Long, nbytes: Int) {
                readCb(s, nbytes)
            }
        }

        /**
         * The PulseAudio stream which inputs audio data from the PulseAudio source.
         */
        private var stream = 0L

        /**
         * Initializes a new `PulseAudioStream` which is to have its `Format`-related
         * information abstracted by a specific `FormatControl`.
         */
        init {
            checkNotNull(audioSystem) { "audioSystem" }
            val mediaServiceImpl = NeomediaServiceUtils.mediaServiceImpl
            gainControl = if (mediaServiceImpl == null)
                null
            else
                mediaServiceImpl.inputVolumeControl as GainControl
        }

        /**
         * Connects this `PulseAudioStream` to the configured source and prepares it to input
         * audio data in the configured FMJ `Format`.
         *
         * @throws IOException if this `PulseAudioStream` fails to connect to the configured source
         */
        @Throws(IOException::class)
        fun connect() {
            audioSystem!!.lockMainloop()
            try {
                connectWithMainloopLock()
            } finally {
                audioSystem.unlockMainloop()
            }
        }

        /**
         * Connects this `PulseAudioStream` to the configured source and prepares it to input
         * audio data in the configured FMJ `Format`. The method executes with the assumption
         * that the PulseAudio event loop object is locked by the executing thread.
         *
         * @throws IOException if this `PulseAudioStream` fails to connect to the configured source
         */
        @Throws(IOException::class)
        private fun connectWithMainloopLock() {
            if (stream != 0L) return
            val format = format as AudioFormat
            var sampleRate = format.sampleRate.toInt()
            var channels = format.channels
            var sampleSizeInBits = format.sampleSizeInBits
            if (sampleRate == Format.NOT_SPECIFIED && MediaUtils.MAX_AUDIO_SAMPLE_RATE != Format.NOT_SPECIFIED.toDouble()) sampleRate = MediaUtils.MAX_AUDIO_SAMPLE_RATE.toInt()
            if (channels == Format.NOT_SPECIFIED) channels = 1
            if (sampleSizeInBits == Format.NOT_SPECIFIED) sampleSizeInBits = 16
            var stream = 0L
            var exception: Throwable? = null
            try {
                stream = audioSystem!!.createStream(sampleRate, channels, javaClass.name,
                        PulseAudioSystem.MEDIA_ROLE_PHONE)
                this.channels = channels
            } catch (ise: IllegalStateException) {
                exception = ise
            } catch (re: RuntimeException) {
                exception = re
            }
            if (exception != null) {
                val ioe = IOException()
                ioe.initCause(exception)
                throw ioe
            }
            if (stream == 0L) throw IOException("stream")
            try {
                val bytesPerTenMillis = sampleRate / 100 * channels * (sampleSizeInBits / 8)
                fragsize = FRAGSIZE_IN_TENS_OF_MILLIS * bytesPerTenMillis
                buffer = ByteArray(BUFFER_IN_TENS_OF_MILLIS * bytesPerTenMillis)
                var attr = PA.buffer_attr_new(-1, -1, -1, -1, fragsize)
                if (attr == 0L) throw IOException("pa_buffer_attr_new")

                try {
                    val stateCallback = Runnable { audioSystem!!.signalMainloop(false) }
                    PA.stream_set_state_callback(stream, stateCallback)
                    PA.stream_connect_record(stream, locatorDev, attr,
                            PA.STREAM_ADJUST_LATENCY or PA.STREAM_START_CORKED)
                    try {
                        PA.buffer_attr_free(attr)
                        attr = 0

                        val state = audioSystem!!.waitForStreamState(stream, PA.STREAM_READY)
                        if (state != PA.STREAM_READY) throw IOException("stream.state")
                        PA.stream_set_read_callback(stream, readCb)
                        if (!SOFTWARE_GAIN && gainControl != null) {
                            cvolume = PA.cvolume_new()
                            var freeCvolume = true
                            try {
                                val gainControlLevel = gainControl.level
                                setStreamVolume(stream, gainControlLevel)
                                this.gainControlLevel = gainControlLevel
                                freeCvolume = false
                            } finally {
                                if (freeCvolume) {
                                    PA.cvolume_free(cvolume)
                                    cvolume = 0
                                }
                            }
                        }
                        this.stream = stream
                    } finally {
                        if (this.stream == 0L) PA.stream_disconnect(stream)
                    }
                } finally {
                    if (attr != 0L) PA.buffer_attr_free(attr)
                }
            } finally {
                if (this.stream == 0L) PA.stream_unref(stream)
            }
        }

        /**
         * Pauses or resumes the input of audio data through [.stream].
         *
         * @param b `true` to pause the input of audio data or `false` to resume it
         */
        @Throws(IOException::class)
        private fun cork(b: Boolean) {
            corked = try {
                PulseAudioSystem.corkStream(stream, b)
                b
            } finally {
                audioSystem!!.signalMainloop(false)
            }
        }

        /**
         * Disconnects this `PulseAudioStream` and its `DataSource` from the connected
         * capture device.
         */
        @Throws(IOException::class)
        fun disconnect() {
            audioSystem!!.lockMainloop()
            try {
                val stream = stream
                if (stream != 0L) {
                    try {
                        stopWithMainloopLock()
                    } finally {
                        val cvolume = cvolume
                        this.cvolume = 0
                        this.stream = 0
                        buffer = null
                        corked = true
                        fragsize = 0
                        length = 0
                        offset = 0
                        audioSystem.signalMainloop(false)
                        if (cvolume != 0L) PA.cvolume_free(cvolume)
                        PA.stream_disconnect(stream)
                        PA.stream_unref(stream)
                    }
                }
            } finally {
                audioSystem.unlockMainloop()
            }
        }

        /**
         * {@inheritDoc}
         */
        @Throws(IOException::class)
        override fun read(buffer: Buffer) {
            audioSystem!!.lockMainloop()
            try {
                if (stream == 0L) throw IOException("stream")
                val data = AbstractCodec2.validateByteArraySize(buffer, fragsize, false)
                var toRead = fragsize
                var offset = 0
                var length = 0
                while (toRead > 0) {
                    if (corked) break
                    if (this.length <= 0) {
                        audioSystem.waitMainloop()
                        continue
                    }
                    val toCopy = if (toRead < this.length) toRead else this.length
                    System.arraycopy(this.buffer!!, this.offset, data, offset, toCopy)
                    this.offset += toCopy
                    this.length -= toCopy
                    if (this.length <= 0) {
                        this.offset = 0
                        this.length = 0
                    }
                    toRead -= toCopy
                    offset += toCopy
                    length += toCopy
                }
                buffer.flags = Buffer.FLAG_SYSTEM_TIME
                buffer.length = length
                buffer.offset = 0
                buffer.timeStamp = System.nanoTime()
                if (gainControl != null) {
                    if (SOFTWARE_GAIN || cvolume == 0L) {
                        if (length > 0) {
                            BasicVolumeControl.applyGain(gainControl, data, 0, length)
                        }
                    } else {
                        val gainControlLevel = gainControl.level
                        if (this.gainControlLevel != gainControlLevel) {
                            this.gainControlLevel = gainControlLevel
                            setStreamVolume(stream, gainControlLevel)
                        }
                    }
                }
            } finally {
                audioSystem.unlockMainloop()
            }
        }

        private fun readCb(stream: Long, length: Int) {
            try {
                val peeked: Int
                if (corked) {
                    peeked = 0
                } else {
                    var offset: Int
                    if (buffer == null || buffer!!.size < length) {
                        buffer = ByteArray(length)
                        this.offset = 0
                        this.length = 0
                        offset = 0
                    } else {
                        offset = this.offset + this.length
                        if (offset + length > buffer!!.size) {
                            val overflow = this.length + length - buffer!!.size
                            if (overflow > 0) {
                                if (overflow >= this.length) {
                                    Timber.d("Dropping %s bytes!", this.length)
                                    this.offset = 0
                                    this.length = 0
                                    offset = 0
                                } else {
                                    Timber.d("Dropping %d bytes!", overflow)
                                    this.offset += overflow
                                    this.length -= overflow
                                }
                            }
                            if (this.length > 0) {
                                var i = 0
                                while (i < this.length) {
                                    buffer!![i] = buffer!![this.offset]
                                    i++
                                    this.offset++
                                }
                                this.offset = 0
                                offset = this.length
                            }
                        }
                    }
                    peeked = PA.stream_peek(stream, buffer, offset)
                }
                PA.stream_drop(stream)
                this.length += peeked
            } finally {
                audioSystem!!.signalMainloop(false)
            }
        }

        /**
         * Sets the volume of a specific PulseAudio `stream` to a specific `level`.
         *
         * @param stream the PulseAudio stream to set the volume of
         * @param level the volume to set on `stream`
         */
        private fun setStreamVolume(stream: Long, level: Float) {
            val volume = PA.sw_volume_from_linear(level.toDouble()
                    * (BasicVolumeControl.MAX_VOLUME_PERCENT / 100))
            PA.cvolume_set(cvolume, channels, volume)
            val o = PA.context_set_source_output_volume(audioSystem!!.getContext(),
                    PA.stream_get_index(stream), cvolume, null)
            if (o != 0L) PA.operation_unref(o)
        }

        /**
         * {@inheritDoc}
         */
        @Throws(IOException::class)
        override fun start() {
            audioSystem!!.lockMainloop()
            try {
                if (stream == 0L) connectWithMainloopLock()
                cork(false)
            } finally {
                audioSystem.unlockMainloop()
            }
            super.start()
        }

        /**
         * {@inheritDoc}
         */
        @Throws(IOException::class)
        override fun stop() {
            audioSystem!!.lockMainloop()
            try {
                stopWithMainloopLock()
            } finally {
                audioSystem.unlockMainloop()
            }
        }

        /**
         * Pauses the input of audio data performed by [.stream]. The method executes with the
         * assumption that the PulseAudio event loop object is locked by the executing thread.
         */
        @Throws(IOException::class)
        private fun stopWithMainloopLock() {
            if (stream != 0L) cork(true)
            super.stop()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun createStream(streamIndex: Int, formatControl: FormatControl?): PulseAudioStream {
        return PulseAudioStream(formatControl)
    }

    /**
     * {@inheritDoc}
     */
    override fun doDisconnect() {
        synchronized(streamSyncRoot) {
            val streams = streams()
            if (streams != null && streams.isNotEmpty()) {
                for (stream in streams) {
                    if (stream is PulseAudioStream) {
                        try {
                            stream.disconnect()
                        } catch (ioe: IOException) {
                            // Well, what can we do?
                        }
                    }
                }
            }
        }
        super.doDisconnect()
    }

    /**
     * Returns the name of the PulseAudio source that this `DataSource` is configured to
     * input audio data from.
     *
     * @return the name of the PulseAudio source that this `DataSource` is configured to
     * input audio data from
     */
    private val locatorDev: String?
        get() {
            val locator = locator
            var locatorDev: String?
            if (locator == null) {
                locatorDev = null
            } else {
                locatorDev = locator.remainder
                if (locatorDev != null && locatorDev.isEmpty()) locatorDev = null
            }
            return locatorDev
        }

    companion object {
        private const val BUFFER_IN_TENS_OF_MILLIS = 10
        private const val FRAGSIZE_IN_TENS_OF_MILLIS = 2

        /**
         * The indicator which determines whether `DataSource` instances apply audio volume
         * levels on the audio data to be renderer or leave the task to PulseAudio.
         */
        private var SOFTWARE_GAIN = false

        init {
            val softwareGain = true
            try {
                val libraryVersion = PA.get_library_version()
                if (libraryVersion != null) {
                    val st = StringTokenizer(libraryVersion, ".")
                    if (/* major */st.nextToken().toInt() >= 1
                            &&  /* minor */st.nextToken().toInt() >= 0) {
                        // FIXME The control of the volume through the native
                        // PulseAudio API has been reported to maximize the
                        // system-wide volume of the source with flat volumes i.e.
                        // https://java.net/jira/browse/JITSI-1050 (Pulseaudio
                        // changes volume to maximum values).
                        // softwareGain = false;
                        // Timber.d("Will control the volume through the native PulseAudio API.");
                    }
                }
            } catch (t: Throwable) {
                if (t is ThreadDeath) throw t
            }
            SOFTWARE_GAIN = softwareGain
        }
    }
}