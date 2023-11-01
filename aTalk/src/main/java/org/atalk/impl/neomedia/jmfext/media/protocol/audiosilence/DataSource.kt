/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.audiosilence

import okhttp3.internal.notifyAll
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferCaptureDevice
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream
import org.atalk.impl.neomedia.jmfext.media.renderer.audio.AbstractAudioRenderer
import timber.log.Timber
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import javax.media.Buffer
import javax.media.Format
import javax.media.control.FormatControl
import javax.media.format.AudioFormat

/**
 * Implements a `CaptureDevice` which provides silence in the form of audio media.
 *
 * @author Lyubomir Marinov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class DataSource : AbstractPushBufferCaptureDevice() {
    /**
     * {@inheritDoc}
     *
     * Implements [AbstractPushBufferCaptureDevice.createStream].
     */
    override fun createStream(streamIndex: Int, formatControl: FormatControl): AudioSilenceStream {
        return AudioSilenceStream(
                this,
                formatControl,
                !NO_TRANSFER_DATA.equals(locator.remainder, ignoreCase = true))
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation in order to return the list of `Format`s hardcoded
     * as supported in `AudioSilenceCaptureDevice` because the super looks them up by
     * `CaptureDeviceInfo` and this instance does not have one.
     */
    override fun getSupportedFormats(streamIndex: Int): Array<Format> {
        return SUPPORTED_FORMATS.clone()
    }

    /**
     * Implements a `PushBufferStream` which provides silence in the form of audio media.
     */
    class AudioSilenceStream
    /**
     * Initializes a new `AudioSilenceStream` which is to be exposed
     * by a specific `AudioSilenceCaptureDevice` and which is to have
     * its `Format`-related information abstracted by a specific `FormatControl`.
     *
     * @param dataSource the `AudioSilenceCaptureDevice` which is
     * initializing the new instance and which is to expose it in its array
     * of `PushBufferStream`s
     * @param formatControl the `FormatControl` which is to abstract
     * the `Format`-related information of the new instance
     * @param transferData `true` if the new instance is to invoke
     * `BufferTransferHandler.transferData(PushBufferStream)` and,
     * thus, tick the media clock that it represents; otherwise, `false`.
     */(dataSource: DataSource?, formatControl: FormatControl?,
            /**
             * The indicator which determined whether this instance is to invoke
             * `BufferTransferHandler.transferData(PushBufferStream)` and,
             * thus, tick the media clock that it represents. If `false`, then
             * it is a dummy suitable for scenarios in which an audio capture device
             * is required but no audio samples from it are necessary such as
             * negotiating signaling for audio but actually RTP translating other
             * participants/peers' audio.
             */
            private val transferData: Boolean) : AbstractPushBufferStream<DataSource?>(dataSource, formatControl), Runnable {
        /**
         * The indicator which determines whether [.start] has been invoked on this instance
         * without an intervening [.stop].
         */
        private var started = false

        /**
         * The `Thread` which pushes available media data out of this instance to its
         * consumer i.e. `BufferTransferHandler`.
         */
        private var thread: Thread? = null

        /**
         * Reads available media data from this instance into a specific `Buffer`.
         *
         * @param buffer the `Buffer` to write the available media data into
         * @throws IOException if an I/O error has prevented the reading of available media data from this
         * instance into the specified `buffer`
         */
        @Throws(IOException::class)
        override fun read(buffer: Buffer) {
            if (CLOCK_ONLY) {
                buffer.length = 0
            } else {
                val format = format as AudioFormat
                val frameSizeInBytes = (format.channels * (format.sampleRate.toInt() / 50)
                        * (format.sampleSizeInBits / 8))
                val data = AbstractCodec2.validateByteArraySize(buffer, frameSizeInBytes, false)
                Arrays.fill(data, 0, frameSizeInBytes, 0.toByte())
                buffer.format = format
                buffer.length = frameSizeInBytes
                buffer.offset = 0
            }
        }

        /**
         * Runs in [.thread] and pushes available media data out of this instance to its
         * consumer i.e. `BufferTransferHandler`.
         */
        override fun run() {
            try {
                /*
                 * Make sure that the current thread which implements the actual ticking of the
                 * clock implemented by this instance uses a thread priority considered appropriate
                 * for audio processing.
                 */
                AbstractAudioRenderer.useAudioThreadPriority()

                /*
                 * The method implements a clock which ticks at a certain and regular interval of
                 * time which is not affected by the duration of the execution of, for example, the
                 * invocation of BufferTransferHandler.transferData(PushBufferStream).
                 *
                 * XXX The implementation utilizes System.currentTimeMillis() and, consequently, may
                 * be broken by run-time adjustments to the system time.
                 */
                var tickTime = System.currentTimeMillis()
                while (true) {
                    val sleepInterval = tickTime - System.currentTimeMillis()
                    val tick = sleepInterval <= 0
                    if (tick) {
                        /*
                         * The current thread has woken up just in time or too late for the next
                         * scheduled clock tick and, consequently, the clock should tick right now.
                         */
                        tickTime += CLOCK_TICK_INTERVAL
                    } else {
                        /*
                         * The current thread has woken up too early for the next scheduled clock
                         * tick and, consequently, it should sleep until the time of the next
                         * scheduled clock tick comes.
                         */
                        try {
                            Thread.sleep(sleepInterval)
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                        /*
                         * The clock will not tick and spurious wakeups will be handled. However,
                         * the current thread will first check whether it is still utilized by this
                         * AudioSilenceStream in order to not delay stop requests.
                         */
                    }

                    if (thread != Thread.currentThread() || !started)
                        break

                    synchronized(this) {
                        /*
                         * If the current Thread is no longer utilized by this AudioSilenceStream,
                         * it no longer has the right to touch it. If this AudioSilenceStream has
                         * been stopped, the current Thread should stop as well.
                         */
                    }

                    if (tick) {
                        val transferHandler = this.mTransferHandler
                        if (transferHandler != null) {
                            try {
                                transferHandler.transferData(this)
                            } catch (t: Throwable) {
                                if (t is ThreadDeath) throw t else {
                                    Timber.e("%s", t.message)
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
                        notifyAll()
                    }
                }
            }
        }

        /**
         * Starts the transfer of media data from this instance.
         *
         * @throws IOException if an error has prevented the start of the transfer of media from this instance
         */
        @Synchronized
        @Throws(IOException::class)
        override fun start() {
            if (!transferData) {
                // Skip creating the thread that will invoke transferData.
                started = true
                return
            }
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
         * @throws IOException if an error has prevented the stopping of the transfer of media from this instance
         */
        @Synchronized
        @Throws(IOException::class)
        override fun stop() {
            started = false
            notifyAll()
            var interrupted = false

            // a workaround for an issue we see where we cannot stop this stream as the thread waiting to
            // transfer data is waiting for data that never comes. So we timeout after short period and
            // we interrupt the thread to clean it
            val WAIT_TIMEOUT = 100L // ms.
            var waited = false
            val started = System.nanoTime()
            while (thread != null) {
                if (waited) {
                    // our stop had timed out, so let's interrupt the thread
                    thread!!.interrupt()
                    break
                }
                try {
                    (this as Object).wait(WAIT_TIMEOUT)
                    waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= WAIT_TIMEOUT
                } catch (ie: InterruptedException) {
                    interrupted = true
                }
            }
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    companion object {
        /**
         * The compile-time flag which determines whether `AudioSilenceCaptureDevice` and, more
         * specifically, `AudioSilenceStream` are to be used by `AudioMixer` for the mere
         * purposes of ticking the clock which makes `AudioMixer` read media from its inputs, mix
         * it, and write it to its outputs. The preferred value is `true` because it causes the
         * `AudioMixer` to not push media unless at least one `Channel` is receiving actual media.
         */
        private const val CLOCK_ONLY = true

        /**
         * The interval of time in milliseconds between two consecutive ticks of the clock used by
         * `AudioSilenceCaptureDevice` and, more specifically, `AudioSilenceStream`.
         */
        private const val CLOCK_TICK_INTERVAL: Long = 20

        /**
         * The optional MediaLocator remainder to indicate to
         * `DataSource` and its associated `AudioSilenceStream` that
         * `BufferTransferHandler.transferData(PushBufferStream)` is to not be
         * invoked. If `true`, then the `DataSource` is a dummy suitable
         * for scenarios in which an capture device is required but no audio
         * samples from it are necessary such as negotiating signaling for audio but
         * actually RTP translating other participants/peers' audio.
         */
        const val NO_TRANSFER_DATA = "noTransferData"

        /**
         * The list of `Format`s supported by the `AudioSilenceCaptureDevice` instances.
         */
        val SUPPORTED_FORMATS = arrayOf<Format>(
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