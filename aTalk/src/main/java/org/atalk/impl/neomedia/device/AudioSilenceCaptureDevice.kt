/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.device

import okhttp3.internal.notifyAll
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferCaptureDevice
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream
import org.atalk.impl.neomedia.jmfext.media.renderer.audio.AbstractAudioRenderer
import java.io.IOException
import java.util.*
import javax.media.Buffer
import javax.media.Format
import javax.media.control.FormatControl
import javax.media.format.AudioFormat

/**
 * Implements a `CaptureDevice` which provides silence in the form of
 * audio media.
 *
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
internal class AudioSilenceCaptureDevice
/**
 * Initializes a new [AudioSilenceCaptureDevice].
 * @param clockOnly whether the [ ]s created by this instance
 * are to be used only for the purpose of ticking the clock which makes
 * [org.atalk.impl.neomedia.conference.AudioMixer] run.
 */
(
    /**
     * The flag which determines whether `AudioSilenceCaptureDevice` and,
     * more specifically, `AudioSilenceStream` are to be used by
     * `AudioMixer` for the mere purposes of ticking the clock which
     * makes `AudioMixer` read media from its inputs, mix it, and write
     * it to its outputs. The preferred value is `true` because it
     * causes the `AudioMixer` to not push media unless at least one
     * `Channel` is receiving actual media.
     */
    private val clockOnly: Boolean) : AbstractPushBufferCaptureDevice() {

    /**
     * {@inheritDoc}
     *
     * Implements
     * [AbstractPushBufferCaptureDevice.createStream].
     */
    override fun createStream(streamIndex: Int, formatControl: FormatControl
    ): AudioSilenceStream {
        return AudioSilenceStream(this, formatControl, clockOnly)
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation in order to return the list of
     * `Format`s hardcoded as supported in
     * `AudioSilenceCaptureDevice` because the super looks them up by
     * `CaptureDeviceInfo` and this instance does not have one.
     */
    override fun getSupportedFormats(streamIndex: Int): Array<Format> {
        return SUPPORTED_FORMATS.clone()
    }

    /**
     * Implements a `PushBufferStream` which provides silence in the form
     * of audio media.
     */
    class AudioSilenceStream
    /**
     * Initializes a new `AudioSilenceStream` which is to be exposed
     * by a specific `AudioSilenceCaptureDevice` and which is to have
     * its `Format`-related information abstracted by a specific `FormatControl`.
     *
     * @param dataSource the `AudioSilenceCaptureDevice` which is
     * initializing the new instance and which is to expose it in its array of `PushBufferStream`s
     * @param formatControl the `FormatControl` which is to abstract
     * the `Format`-related information of the new instance
     */
    (
        dataSource: AudioSilenceCaptureDevice?,
        formatControl: FormatControl?,
        /**
         * The flag which determines whether this `AudioSilenceStream`
         * is to be used by `AudioMixer` for the mere purposes of ticking
         * the clock which makes `AudioMixer` read media from its inputs, mix it, and write it to its outputs.
         */
        private val clockOnly: Boolean) : AbstractPushBufferStream<AudioSilenceCaptureDevice?>(dataSource, formatControl), Runnable {

        /**
         * The indicator which determines whether [.start] has been
         * invoked on this instance without an intervening [.stop].
         */
        private var started = false

        /**
         * The `Thread` which pushes available media data out of this
         * instance to its consumer i.e. `BufferTransferHandler`.
         */
        private var thread: Thread? = null

        /**
         * Reads available media data from this instance into a specific `Buffer`.
         *
         * @param buffer the `Buffer` to write the available media data into
         * @throws IOException if an I/O error has prevented the reading of
         * available media data from this instance into the specified `buffer`
         */
        @Throws(IOException::class)
        override fun read(buffer: Buffer) {
            if (clockOnly) {
                buffer.length = 0
            } else {
                val format = getFormat() as AudioFormat
                val frameSizeInBytes = (format.channels
                        * (format.sampleRate.toInt() / 50)
                        * (format.sampleSizeInBits / 8))
                val data = AbstractCodec2.validateByteArraySize(
                        buffer, frameSizeInBytes, false)
                Arrays.fill(data, 0, frameSizeInBytes, 0.toByte())
                buffer.format = format
                buffer.length = frameSizeInBytes
                buffer.offset = 0
            }
        }

        /**
         * Runs in [.thread] and pushes available media data out of this
         * instance to its consumer i.e. `BufferTransferHandler`.
         */
        override fun run() {
            try {
                /*
                 * Make sure that the current thread which implements the actual
                 * ticking of the clock implemented by this instance uses a
                 * thread priority considered appropriate for audio processing.
                 */
                AbstractAudioRenderer.useAudioThreadPriority()

                /*
                 * The method implements a clock which ticks at a certain and
                 * regular interval of time which is not affected by the
                 * duration of the execution of, for example, the invocation of
                 * BufferTransferHandler.transferData(PushBufferStream).
                 *
                 * XXX The implementation utilizes System.currentTimeMillis() and,
                 * consequently, may be broken by run-time adjustments to the system time.
                 */
                var tickTime = System.currentTimeMillis()
                while (true) {
                    val sleepInterval = tickTime - System.currentTimeMillis()
                    val tick = sleepInterval <= 0
                    if (tick) {
                        /*
                         * The current thread has woken up just in time or too
                         * late for the next scheduled clock tick and,
                         * consequently, the clock should tick right now.
                         */
                        tickTime += CLOCK_TICK_INTERVAL
                    } else {
                        /*
                         * The current thread has woken up too early for the
                         * next scheduled clock tick and, consequently, it
                         * should sleep until the time of the next scheduled
                         * clock tick comes.
                         */
                        try {
                            Thread.sleep(sleepInterval)
                        } catch (ignore: InterruptedException) {
                        }
                        /*
                         * The clock will not tick and spurious wakeups will be
                         * handled. However, the current thread will first check
                         * whether it is still utilized by this
                         * AudioSilenceStream in order to not delay stop
                         * requests.
                         */
                    }
                    synchronized(this) {
                        /*
                         * If the current Thread is no longer utilized by this
                         * AudioSilenceStream, it no longer has the right to
                         * touch it. If this AudioSilenceStream has been
                         * stopped, the current Thread should stop as well. 
                         */
                        if (thread != Thread.currentThread() || !started) return //break
                    }
                    if (tick) {
                        val transferHandler = mTransferHandler
                        if (transferHandler != null) {
                            try {
                                transferHandler.transferData(this)
                            } catch (t: Throwable) {
                                if (t is ThreadDeath) throw t else {
                                    // TODO Auto-generated method stub
                                }
                            }
                        }
                    }
                }
            } finally {
                synchronized(this) {
                    if (thread === Thread.currentThread()) {
                        thread = null
                        started = false
                        (this as Object).notifyAll()
                    }
                }
            }
        }

        /**
         * Starts the transfer of media data from this instance.
         *
         * @throws IOException if an error has prevented the start of the
         * transfer of media from this instance
         */
        @Synchronized
        @Throws(IOException::class)
        override fun start() {
            if (thread == null) {
                val className = javaClass.name
                thread = Thread(this, className)
                thread!!.isDaemon = true
                var started = false

                try {
                    thread!!.start()
                    started = true
                } finally {
                    this.started = started
                    if (!started) {
                        thread = null
                        notifyAll()
                        throw IOException("Failed to start $className")
                    }
                }
            }
        }

        /**
         * Stops the transfer of media data from this instance.
         *
         * @throws IOException if an error has prevented the stopping of the
         * transfer of media from this instance
         */
        @Synchronized
        @Throws(IOException::class)
        override fun stop() {
            started = false
            (this as Object).notifyAll()
            var interrupted = false
            while (thread != null) {
                try {
                    (this as Object).wait()
                } catch (ie: InterruptedException) {
                    interrupted = true
                }
            }
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    companion object {
        /**
         * The interval of time in milliseconds between two consecutive ticks of the
         * clock used by `AudioSilenceCaptureDevice` and, more specifically,
         * `AudioSilenceStream`.
         */
        private const val CLOCK_TICK_INTERVAL = 20L

        /**
         * The list of `Format`s supported by the
         * `AudioSilenceCaptureDevice` instances.
         */
        private val SUPPORTED_FORMATS = arrayOf<Format>(
                AudioFormat(
                        AudioFormat.LINEAR,
                        48000.0,
                        16,
                        1,
                        AudioFormat.LITTLE_ENDIAN,
                        AudioFormat.SIGNED,
                        Format.NOT_SPECIFIED,
                        Format.NOT_SPECIFIED.toDouble(),
                        Format.byteArray)
        )
    }
}