/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.opensles

import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferCaptureDevice
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferStream
import java.io.IOException
import javax.media.Buffer
import javax.media.Format
import javax.media.MediaLocator
import javax.media.control.FormatControl
import javax.media.format.AudioFormat
import javax.media.protocol.PullBufferDataSource

/**
 * Implements an audio `CaptureDevice` using OpenSL ES.
 *
 * @author Lyubomir Marinov
 */
class DataSource : AbstractPullBufferCaptureDevice {
    /**
     * Initializes a new `DataSource` instance.
     */
    constructor()

    /**
     * Initializes a new `DataSource` from a specific `MediaLocator`.
     *
     * @param locator
     * the `MediaLocator` to create the new instance from
     */
    constructor(locator: MediaLocator?) : super(locator)

    /**
     * Creates a new `PullBufferStream` which is to be at a specific zero-based index in the
     * list of streams of this `PullBufferDataSource`. The `Format`-related
     * information of the new instance is to be abstracted by a specific `FormatControl`.
     *
     * @param streamIndex
     * the zero-based index of the `PullBufferStream` in the list of streams of this
     * `PullBufferDataSource`
     * @param formatControl
     * the `FormatControl` which is to abstract the `Format`-related
     * information of the new instance
     * @return a new `PullBufferStream` which is to be at the specified `streamIndex`
     * in the list of streams of this `PullBufferDataSource` and which has its
     * `Format`-related information abstracted by the specified
     * `formatControl`
     * @see AbstractPullBufferCaptureDevice.createStream
     */
    override fun createStream(streamIndex: Int, formatControl: FormatControl?): AbstractPullBufferStream<*> {
        return OpenSLESStream(this, formatControl)
    }

    /**
     * Opens a connection to the media source specified by the `MediaLocator` of this
     * `DataSource`.
     *
     * @throws IOException
     * if anything goes wrong while opening the connection to the media source specified by
     * the `MediaLocator` of this `DataSource`
     * @see AbstractPullBufferCaptureDevice.doConnect
     */
    @Throws(IOException::class)
    override fun doConnect() {
        super.doConnect()

        /*
		 * XXX The OpenSLESStream will connect upon start in order to be able to respect requests to
		 * set its format.
		 */
    }

    /**
     * Closes the connection to the media source specified by the `MediaLocator` of this
     * `DataSource`.
     *
     * @see AbstractPullBufferCaptureDevice.doDisconnect
     */
    override fun doDisconnect() {
        synchronized(streamSyncRoot) {
            val streams = streams()
            if (streams != null) for (stream in streams) (stream as OpenSLESStream?)!!.disconnect()
        }
        super.doDisconnect()
    }

    /**
     * Attempts to set the `Format` to be reported by the `FormatControl` of a
     * `PullBufferStream` at a specific zero-based index in the list of streams of this
     * `PullBufferDataSource`. The `PullBufferStream` does not exist at the time of
     * the attempt to set its `Format`. Allows extenders to override the default behavior
     * which is to not attempt to set the specified `Format` so that they can enable setting
     * the `Format` prior to creating the `PullBufferStream`. If setting the
     * `Format` of an existing `PullBufferStream` is desired,
     * `AbstractPullBufferStream#doSetFormat(Format)` should be overridden instead.
     *
     * @param streamIndex
     * the zero-based index of the `PullBufferStream` the `Format` of which is to be set
     * @param oldValue the last-known `Format` for the `PullBufferStream` at the specified
     * `streamIndex`
     * @param newValue the `Format` which is to be set
     * @return the `Format` to be reported by the `FormatControl` of the
     * `PullBufferStream` at the specified `streamIndex` in the list of
     * streams of this `PullBufferStream` or `null` if the attempt to set the
     * `Format` did not success and any last-known `Format` is to be left in effect
     * @see AbstractPullBufferCaptureDevice.setFormat
     */
    override fun setFormat(streamIndex: Int, oldValue: Format?, newValue: Format?): Format? {
        /*
		 * Accept format specifications prior to the initialization of OpenSLESStream. Afterwards,
		 * OpenSLESStream will decide whether to accept further format specifications.
		 */
        return newValue
    }

    /**
     * Implements `PullBufferStream` using OpenSL ES.
     */
    private class OpenSLESStream
    /**
     * Initializes a new `OpenSLESStream` instance which is to have its `Format`
     * -related information abstracted by a specific `FormatControl`.
     *
     * @param dataSource
     * the `DataSource` which is creating the new instance so that it becomes one
     * of its `streams`
     * @param formatControl
     * the `FormatControl` which is to abstract the `Format`-related
     * information of the new instance
     */
    (dataSource: DataSource?, formatControl: FormatControl?) : AbstractPullBufferStream<PullBufferDataSource?>(dataSource, formatControl) {
        private var length = 0
        private var ptr = 0L

        /**
         * The indicator which determines whether this `OpenSLESStream` is to set the
         * priority of the thread in which its [.read] method is executed.
         */
        private var setThreadPriority = true

        /**
         * The indicator which determines whether this `OpenSLESStream` is started i.e.
         * whether [javax.media.protocol.PullBufferStream.read] should really attempt to read from [.ptr].
         */
        private var started = false

        /**
         * Opens a connection to the media source of the associated `DataSource`.
         *
         * @throws IOException if anything goes wrong while opening a connection to the media source of the
         * associated `DataSource`
         */
        @Synchronized
        @Throws(IOException::class)
        fun connect() {
            if (ptr == 0L) {
                val format = format as AudioFormat
                val sampleRate = format.sampleRate
                val sampleSizeInBits = format.sampleSizeInBits
                var channels = format.channels
                if (channels == Format.NOT_SPECIFIED) channels = 1

                /*
				 * Apart from the thread in which #read(Buffer) is executed, use the thread priority
				 * for the thread which will create the OpenSL ES Audio Recorder.
				 */
                org.atalk.impl.neomedia.jmfext.media.protocol.audiorecord.DataSource.setThreadPriority()
                ptr = connect(format.encoding, sampleRate, sampleSizeInBits,
                        channels, format.endian, format.signed, format.dataType)
                if (ptr == 0L) throw IOException()
                else {
                    length = (20 /* milliseconds */
                            * (sampleRate / 1000) * channels * (sampleSizeInBits / 8)).toInt()
                    setThreadPriority = true
                }
            }
        }

        /**
         * Closes the connection to the media source of the associated `DataSource`.
         */
        @Synchronized
        fun disconnect() {
            if (ptr != 0L) {
                disconnect(ptr)
                ptr = 0
                setThreadPriority = true
            }
        }

        /**
         * Reads media data from this `PullBufferStream` into a specific `Buffer` with
         * blocking.
         *
         * @param buffer the `Buffer` in which media data is to be read from this
         * `PullBufferStream`
         * @throws IOException
         * if anything goes wrong while reading media data from this `PullBufferStream` into the specified `buffer`
         * @see javax.media.protocol.PullBufferStream.read
         */
        @Throws(IOException::class)
        override fun read(buffer: Buffer) {
            if (setThreadPriority) {
                setThreadPriority = false
                org.atalk.impl.neomedia.jmfext.media.protocol.audiorecord.DataSource.setThreadPriority()
            }
            var data = buffer.data
            var length = length
            if (data is ByteArray) {
                if (data.size < length) data = null
            }
            else data = null
            if (data == null) {
                data = ByteArray(length)
                buffer.data = data
            }
            var read = 0
            val offset = 0

            while (read < 1) {
                synchronized(this) {
                    read = if (started) {
                        if (ptr == 0L) throw IOException("ptr") else read(ptr, data, offset, length)
                    }
                    else 2 // force to break
                }
            }

            length = read
            buffer.length = length
            buffer.offset = offset
        }

        /**
         * Starts the transfer of media data from this `AbstractBufferStream`.
         *
         * @throws IOException
         * if anything goes wrong while starting the transfer of media data from this
         * `AbstractBufferStream`
         * @see AbstractBufferStream.start
         */
        @Throws(IOException::class)
        override fun start() {
            /*
			 * Connect upon start because the connect has been delayed to allow this OpenSLESStream
			 * to respect requests to set its format.
			 */
            synchronized(this) { if (ptr == 0L) connect() }
            super.start()
            synchronized(this) {
                if (ptr != 0L) {
                    setThreadPriority = true
                    start(ptr)
                    started = true
                }
            }
        }

        /**
         * Stops the transfer of media data from this `AbstractBufferStream`.
         *
         * @throws IOException if anything goes wrong while stopping the transfer of media
         * data from this `AbstractBufferStream`
         * @see AbstractBufferStream.stop
         */
        @Throws(IOException::class)
        override fun stop() {
            synchronized(this) {
                if (ptr != 0L) {
                    stop(ptr)
                    setThreadPriority = true
                    started = false
                }
            }
            super.stop()
        }
    }

    companion object {
        init {
            System.loadLibrary("jnopensles")
        }

        @Throws(IOException::class)
        private external fun connect(
                encoding: String, sampleRate: Double, sampleSizeInBits: Int,
                channels: Int, endian: Int, signed: Int, dataType: Class<*>,
        ): Long

        private external fun disconnect(ptr: Long)

        @Throws(IOException::class)
        private external fun read(ptr: Long, data: Any, offset: Int, length: Int): Int

        @Throws(IOException::class)
        private external fun start(ptr: Long)

        @Throws(IOException::class)
        private external fun stop(ptr: Long)
    }
}