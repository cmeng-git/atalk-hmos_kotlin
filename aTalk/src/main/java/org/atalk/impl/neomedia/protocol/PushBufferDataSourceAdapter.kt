/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol

import net.sf.fmj.media.util.MediaThread
import okhttp3.internal.notifyAll
import org.atalk.impl.neomedia.jmfext.media.renderer.AbstractRenderer
import timber.log.Timber
import java.io.IOException
import javax.media.Buffer
import javax.media.Format
import javax.media.format.AudioFormat
import javax.media.format.VideoFormat
import javax.media.protocol.BufferTransferHandler
import javax.media.protocol.ContentDescriptor
import javax.media.protocol.PullBufferDataSource
import javax.media.protocol.PullBufferStream
import javax.media.protocol.PushBufferStream

/**
 * Implements `PushBufferDataSource` for a specific `PullBufferDataSource`.
 *
 * @author Lyubomir Marinov
 */
class PushBufferDataSourceAdapter
/**
 * Initializes a new `PushBufferDataSourceAdapter` which is to implement
 * `PushBufferDataSource` capabilities for a specific `PullBufferDataSource`.
 *
 * @param dataSource the `PullBufferDataSource` the new instance is to implement
 * `PushBufferDataSource` capabilities for
 */
(dataSource: PullBufferDataSource) : PushBufferDataSourceDelegate<PullBufferDataSource>(dataSource) {
    /**
     * Implements `PushBufferStream` for a specific `PullBufferStream`.
     */
    private class PushBufferStreamAdapter(stream: PullBufferStream?) : PushBufferStream {
        /**
         * The `Buffer` which contains the media data read by this instance from
         * [.stream] and to be returned by this implementation of
         * [PushBufferStream.read] by copying.
         */
        private val buffer = Buffer()

        /**
         * The indicator which determines whether [.buffer] contains media data read by this
         * instance from [.stream] and not returned by this implementation of
         * [PushBufferStream.read] yet.
         */
        private var bufferIsWritten = false

        /**
         * The indicator which determined whether [.start] has been called without a
         * subsequent call to [.stop].
         */
        private var started = false

        /**
         * The `PullBufferStream` to which this instance provides `PushBufferStream`
         * capabilities.
         */
        val stream: PullBufferStream

        /**
         * The `IOException`, if any, which has been thrown by the last call to
         * [PullBufferStream.read] on [.stream] and which still hasn't been
         * rethrown by this implementation of [PushBufferStream.read].
         */
        private var streamReadException: IOException? = null

        /**
         * The `Thread` which currently reads media data from [.stream] into
         * [.buffer].
         */
        private var streamReadThread: Thread? = null

        /**
         * The `Object` which synchronizes the access to [.streamReadThread]-related
         * members.
         */
        private val streamReadThreadSyncRoot = Any()

        /**
         * The `BufferTransferHandler` through which this `PushBufferStream` notifies
         * its user that media data is available for reading.
         */
        private var transferHandler: BufferTransferHandler? = null

        /**
         * Initializes a new `PushBufferStreamAdapter` instance which is to implement
         * `PushBufferStream` for a specific `PullBufferStream`.
         *
         * stream the `PullBufferStream` the new instance is to implement `PushBufferStream` for
         */
        init {
            if (stream == null) throw NullPointerException("stream")
            this.stream = stream
        }

        /**
         * Disposes of this `PushBufferStreamAdapter`. Afterwards, this instance is not
         * guaranteed to be operation and considered to be available for garbage collection.
         */
        fun close() {
            stop()
        }

        /**
         * Implements SourceStream.endOfStream. Delegates to the wrapped `PullBufferStream`.
         *
         * @return `true` if the wrapped `PullBufferStream` has reached the end of the
         * media data; otherwise, `false`
         */
        override fun endOfStream(): Boolean {
            return stream.endOfStream()
        }

        /**
         * Implements SourceStream.getContentDescriptor. Delegates to the wrapped PullBufferStream`.
         *
         * @return the `ContentDescriptor` of the wrapped `PullBufferStream` which
         * describes the type of the media data it gives access to
         */
        override fun getContentDescriptor(): ContentDescriptor {
            return stream.contentDescriptor
        }

        /**
         * Implements SourceStream.getContentLength. Delegates to the wrapped PullBufferStream`.
         *
         * @return the length of the content the wrapped `PullBufferStream` gives access to
         */
        override fun getContentLength(): Long {
            return stream.contentLength
        }

        /**
         * Implements [javax.media.Controls.getControl]. Delegates to the wrapped `PullBufferStream`.
         *
         * @param controlType a `String` value which specifies the type of the control of the wrapped
         * `PullBufferStream` to be retrieved
         * @return an `Object` which represents the control of the wrapped
         * `PushBufferStream` of the requested type if the wrapped
         * `PushBufferStream` has such a control; `null` if the wrapped
         * `PushBufferStream` does not have a control of the specified type
         */
        override fun getControl(controlType: String): Any {
            return stream.getControl(controlType)
        }

        /**
         * Implements [javax.media.Controls.getControls]. Delegates to the wrapped
         * `PushBufferStream`.
         *
         * @return an array of `Object`s which represent the controls available for the
         * wrapped `PushBufferStream`
         */
        override fun getControls(): Array<Any> {
            return stream.controls
        }

        /**
         * Implements [PushBufferStream.getFormat]. Delegates to the wrapped
         * `PullBufferStream`.
         *
         * @return the `Format` of the wrapped `PullBufferStream`
         */
        override fun getFormat(): Format {
            return stream.format
        }

        /**
         * Implements [PushBufferStream.read].
         *
         * @param buffer a `Buffer` in which media data is to be written by this
         * `PushBufferDataSource`
         * @throws IOException if anything wrong happens while reading media data from this
         * `PushBufferDataSource` into the specified `buffer`
         */
        @Throws(IOException::class)
        override fun read(buffer: Buffer) {
            synchronized(this.buffer) {
                /*
                 * If stream has throw an exception during its last read, rethrow it as an
                 * exception
                 * of this stream.
                 */
                if (streamReadException != null) {
                val ie = IOException()
                ie.initCause(streamReadException)
                streamReadException = null
                throw ie
            } else if (bufferIsWritten) {
                buffer.copy(this.buffer)
                bufferIsWritten = false
            } else buffer.length = 0
            }
        }

        /**
         * Executes an iteration of [.streamReadThread] i.e. reads media data from
         * [.stream] into [.buffer] and invokes
         * [BufferTransferHandler.transferData] on [.transferHandler]
         * if any.
         */
        private fun runInStreamReadThread() {
            var bufferIsWritten: Boolean
            var yield: Boolean
            synchronized(buffer) {
                try {
                    stream.read(buffer)
                    this.bufferIsWritten = !buffer.isDiscard
                    streamReadException = null
                } catch (ie: IOException) {
                    this.bufferIsWritten = false
                    streamReadException = ie
                }
                bufferIsWritten = this.bufferIsWritten
                /*
                 * If an exception has been thrown by the stream's read method, it may be better to
                 * give the stream's underlying implementation (e.g. PortAudio) a little time to
                 * possibly get its act together.
                 */
                yield = !bufferIsWritten && streamReadException != null
            }
            if (bufferIsWritten) {
                this.transferHandler?.transferData(this)
            } else if (yield) Thread.yield()
        }

        /**
         * Implements [PushBufferStream.setTransferHandler]. Sets the
         * means through which this `PushBufferStream` is to notify its user that media data
         * is available for reading.
         *
         * @param transferHandler the `BufferTransferHandler` through which `PushBufferStream` is to
         * notify its user that media data is available for reading
         */
        override fun setTransferHandler(transferHandler: BufferTransferHandler) {
            if (this.transferHandler != transferHandler) this.transferHandler = transferHandler
        }

        /**
         * Starts the reading of media data of this `PushBufferStreamAdapter` from the
         * wrapped `PullBufferStream`.
         */
        fun start() {
            synchronized(streamReadThreadSyncRoot) {
                started = true
                if (streamReadThread == null) {
                    streamReadThread = object : Thread(javaClass.name + ".streamReadThread") {
                        override fun run() {
                            try {
                                setStreamReadThreadPriority(stream)
                                while (true) {
                                    synchronized(streamReadThreadSyncRoot) {
                                    }
                                    if (!started) break
                                    if (streamReadThread != currentThread()) break

                                    runInStreamReadThread()
                                }
                            } finally {
                                synchronized(streamReadThreadSyncRoot) {
                                    if (streamReadThread === currentThread()) {
                                        streamReadThread = null
                                        streamReadThreadSyncRoot.notifyAll()
                                    }
                                }
                            }
                        }
                    }
                    streamReadThread!!.isDaemon = true
                    streamReadThread!!.start()
                }
            }
        }

        /**
         * Stops the reading of media data of this `PushBufferStreamAdapter` from the
         * wrapped `PullBufferStream`.
         */
        fun stop() {
            synchronized(streamReadThreadSyncRoot) {
                started = false
                if (STRICT_STOP) {
                    var interrupted = false
                    while (streamReadThread != null) {
                        try {
                            (streamReadThreadSyncRoot as Object).wait()
                        } catch (iex: InterruptedException) {
                            Timber.i(iex, " %s interrupted while waiting for PullBufferStream read thread to stop.",
                                    javaClass.simpleName)
                            interrupted = true
                        }
                    }
                    if (interrupted) Thread.currentThread().interrupt()
                } else streamReadThread = null
            }
        }
    }

    /**
     * The indicator which determines whether [.start] has been called on this
     * `DataSource` without a subsequent call to [.stop].
     */
    private var started = false

    /**
     * The `PushBufferStream`s through which this `PushBufferDataSource` gives access
     * to its media data.
     */
    private val streams = ArrayList<PushBufferStreamAdapter>()

    /**
     * Implements DataSource.disconnect. Disposes of the
     * `PushBufferStreamAdapter`s which wrap the `PullBufferStream`s of the
     * `PullBufferDataSource` wrapped by this instance.
     */
    override fun disconnect() {
        synchronized(streams) {
            val streamIter = streams.iterator()
            while (streamIter.hasNext()) {
                val stream = streamIter.next()
                streamIter.remove()
                stream.close()
            }
        }
        super.disconnect()
    }

    /**
     * Implements PushBufferDataSource.sreams. Gets the `PushBufferStream`s
     * through which this `PushBufferDataSource` gives access to its media data.
     *
     * @return an array of `PushBufferStream`s through which this `PushBufferDataSource` gives access to its media data
     */
    override fun getStreams(): Array<PushBufferStream> {
        synchronized(streams) {
            var dataSourceStreams = dataSource.streams
            val dataSourceStreamCount: Int

            /*
             * I don't know whether dataSource returns a copy of its internal storage so I'm not
             * sure if it's safe to modify dataSourceStreams.
             */
            if (dataSourceStreams != null) {
            dataSourceStreams = dataSourceStreams.clone()
            dataSourceStreamCount = dataSourceStreams.size
        } else dataSourceStreamCount = 0

            /*
             * Dispose of the PushBufferStreamAdapters which adapt PullBufferStreams which are no
             * longer returned by dataSource.
             */
            val streamIter = streams.iterator()
            while (streamIter.hasNext()) {
                val streamAdapter = streamIter.next()
                val stream = streamAdapter.stream
                var removeStream = true
                for (dataSourceStreamIndex in 0 until dataSourceStreamCount) if (stream == dataSourceStreams[dataSourceStreamIndex]) {
                    removeStream = false
                    dataSourceStreams[dataSourceStreamIndex] = null
                    break
                }
                if (removeStream) {
                    streamIter.remove()
                    streamAdapter.close()
                }
            }

            /*
             * Create PushBufferStreamAdapters for the PullBufferStreams returned by dataSource
             * which are not adapted yet.
             */
            for (dataSourceStreamIndex in 0 until dataSourceStreamCount) {
            val dataSourceStream = dataSourceStreams[dataSourceStreamIndex]
            if (dataSourceStream != null) {
                val stream = PushBufferStreamAdapter(dataSourceStream)
                streams.add(stream)
                if (started) stream.start()
            }
        }
            return (streams as ArrayList).toArray(EMPTY_STREAMS)
        }
    }

    /**
     * Implements DataSource.start. Starts the wrapped `PullBufferDataSource` and
     * the pushing from the `PushBufferStreamAdapter`s which wrap the
     * `PullBufferStream`s of the `PullBufferDataSource` wrapped by this instance.
     *
     * @throws IOException if anything wrong happens while starting the wrapped `PullBufferDataSource` or
     * the pushing from the `PushBufferStreamAdapter`s which wrap the
     * `PullBufferStream`s of the `PullBufferDataSource` wrapped by this
     * instance
     */
    @Throws(IOException::class)
    override fun start() {
        super.start()
        synchronized(streams) {
            started = true
            for (stream in streams) stream.start()
        }
    }

    /**
     * Implements DataSource.start. Stops the wrapped `PullBufferDataSource` and
     * the pushing from the `PushBufferStreamAdapter`s which wrap the
     * `PullBufferStream`s of the `PullBufferDataSource` wrapped by this instance.
     *
     * @throws IOException if anything wrong happens while stopping the wrapped `PullBufferDataSource` or
     * the pushing from the `PushBufferStreamAdapter`s which wrap the
     * `PullBufferStream`s of the `PullBufferDataSource` wrapped by this
     * instance
     */
    @Throws(IOException::class)
    override fun stop() {
        synchronized(streams) {
            started = false
            for (stream in streams) stream.stop()
        }
        super.stop()
    }

    companion object {
        /**
         * The indicator which determines whether the `PushBufferStreamAdapater` instances
         * should wait for their [PushBufferStreamAdapter.streamReadThread]s to exit before their
         * [PushBufferStreamAdapter.stop] returns.
         */
        private const val STRICT_STOP = false

        /**
         * Sets the priority of the `streamReadThread` of a `PushBufferStreamAdapter`
         * that adapts a specific `PullBufferStream` in accord with the `Format` of the
         * media data.
         *
         * @param stream the `PullBufferStream` adapted by a `PushBufferStreamAdapter` that is to
         * have the priority of its `streamReadThread` set
         */
        private fun setStreamReadThreadPriority(stream: PullBufferStream) {
            try {
                val format = stream.format
                val threadPriority = if (format is AudioFormat) {
                    MediaThread.getAudioPriority()
                }
                else if (format is VideoFormat) {
                    MediaThread.getVideoPriority()
                }
                else {
                    return
                }
                AbstractRenderer.useThreadPriority(threadPriority)
            } catch (t: Throwable) {
                if (t is InterruptedException) Thread.currentThread().interrupt() else if (t is ThreadDeath) throw t
                Timber.w("Failed to set the priority of streamReadThread")
            }
        }
    }
}