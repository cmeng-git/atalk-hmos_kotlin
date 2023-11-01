/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol

import okhttp3.internal.notifyAll
import java.awt.Component
import java.io.IOException
import java.util.*
import javax.media.*
import javax.media.control.BufferControl
import javax.media.format.AudioFormat
import javax.media.protocol.*

/**
 * Enables reading from a `PushBufferStream` a certain maximum number of data units (e.g.
 * bytes, shorts, ints) even if the `PushBufferStream` itself pushes a larger number of data
 * units.
 *
 *
 * An example use of this functionality is pacing a `PushBufferStream` which pushes more data
 * units in a single step than a `CaptureDevice`. When these two undergo audio mixing, the
 * different numbers of per-push data units will cause the `PushBufferStream` "play" itself
 * faster than the `CaptureDevice`.
 *
 *
 * @author Lyubomir Marinov
 */
class CachingPushBufferStream
/**
 * Initializes a new `CachingPushBufferStream` instance which is to pace the number of
 * per-push data units a specific `PushBufferStream` provides.
 *
 * @param stream
 * the `PushBufferStream` to be paced with respect to the number of per-push data
 * units it provides
 */
(
        /**
         * The `PushBufferStream` being paced by this instance with respect to the maximum
         * number of data units it provides in a single push.
         */
        val stream: PushBufferStream) : PushBufferStream {
    /**
     * The `BufferControl` of this `PushBufferStream` which allows the adjustment of
     * the size of the buffering it performs.
     */
    private var bufferControl: BufferControl? = null

    /**
     * The `Object` which synchronizes the access to [.bufferControl].
     */
    private val bufferControlSyncRoot = Any()

    /**
     * The list of `Buffer`s in which this instance stores the data it reads from the
     * wrapped `PushBufferStream` and from which it reads in chunks later on when its
     * [.read] method is called.
     */
    private val cache = LinkedList<Buffer>()

    /**
     * The length of the media in milliseconds currently available in [.cache].
     */
    private var cacheLengthInMillis = 0L

    /**
     * The last `IOException` this stream has received from the `#read(Buffer)`
     * method of the wrapped stream and to be thrown by this stream on the earliest call of its
     * `#read(Buffer)` method.
     */
    private var readException: IOException? = null
    /**
     * Gets the `PushBufferStream` wrapped by this instance.
     *
     * @return the `PushBufferStream` wrapped by this instance
     */

    /**
     * The `BufferTransferHandler` set on [.stream].
     */
    private var transferHandler: BufferTransferHandler? = null

    /**
     * Determines whether adding a new `Buffer` to [.cache] is acceptable given the
     * maximum size of the `cache` and the length of the media currently available in it.
     *
     * @return `true` if adding a new `Buffer` to `cache` is acceptable;
     * otherwise, `false` which means that the reading from the wrapped
     * `PushBufferStream` should be blocked until `true` is returned
     */
    private fun canWriteInCache(): Boolean {
        synchronized(cache) {
            val cacheSize = cache.size

            /*
			 * Obviously, if there's nothing in the cache, we desperately want something to be
			 * written into it.
			 */
            if (cacheSize < 1) return true
            /*
			 * For the sake of not running out of memory, don't let the sky be the limit.
			 */
            if (cacheSize >= MAX_CACHE_SIZE) return false
            val bufferLength = bufferLength

            /*
			 * There is no bufferLength specified by a BufferControl so don't buffer anything.
			 */
            if (bufferLength < 1) return false
            /*
			 * Having Buffers in the cache and yet not having their length in milliseconds is weird
			 * so don't buffer anything.
			 */
            return if (cacheLengthInMillis < 1) false else cacheLengthInMillis < bufferLength
            /*
			 * Of course, if the media in the cache hasn't reached the specified buffer length,
			 * write more to the cache.
			 */
        }
    }

    /**
     * Implements [SourceStream.endOfStream]. Delegates to the wrapped
     * `PushBufferStream` when the cache of this instance is fully read; otherwise, returns false`.
     *
     * @return `true` if this `PushBufferStream` has reached the end of the
     * content it makes available; otherwise, `false`
     */
    override fun endOfStream(): Boolean {
        /*
		 * TODO If the cache is still not exhausted, don't report the end of this stream even if
		  * the
		 * wrapped stream has reached its end.
		 */
        return stream.endOfStream()
    }

    /**
     * Gets the `BufferControl` of this `PushBufferStream` which allows the
     * adjustment of the size of the buffering it performs. If it does not exist yet, it is
     * created.
     *
     * @return the `BufferControl` of this `PushBufferStream` which allows the
     * adjustment of the size of the buffering it performs
     */
    private fun getBufferControl(): BufferControl {
        synchronized(bufferControlSyncRoot) {
            if (bufferControl == null) bufferControl = BufferControlImpl()
            return bufferControl!!
        }
    }

    /**
     * Gets the length in milliseconds of the buffering performed by this
     * `PushBufferStream`.
     *
     * @return the length in milliseconds of the buffering performed by this
     * `PushBufferStream` if such a value has been set; otherwise,
     * [BufferControl.DEFAULT_VALUE]
     */
    private val bufferLength: Long
        get() {
            synchronized(bufferControlSyncRoot) { return if (bufferControl == null) BufferControl.DEFAULT_VALUE else bufferControl!!.bufferLength }
        }

    /**
     * Implements [SourceStream.getContentDescriptor]. Delegates to the wrapped
     * `PushBufferStream`.
     *
     * @return a `ContentDescriptor` which describes the type of the content made available
     * by the wrapped `PushBufferStream`
     */
    override fun getContentDescriptor(): ContentDescriptor {
        return stream.contentDescriptor
    }

    /**
     * Implements [SourceStream.getContentLength]. Delegates to the wrapped
     * `PushBufferStream`.
     *
     * @return the length of the content made available by the wrapped `PushBufferStream`
     */
    override fun getContentLength(): Long {
        return stream.contentLength
    }

    /**
     * Implements [javax.media.Controls.getControl]. Delegates to the wrapped
     * `PushBufferStream` and gives access to the `BufferControl` of this instance if
     * such a `controlType` is specified and the wrapped `PushBufferStream` does not
     * have such a control available.
     *
     * @param controlType
     * a `String` value which names the type of the control of the wrapped
     * `PushBufferStream` to be retrieved
     * @return an `Object` which represents the control of the wrapped
     * `PushBufferStream` with the specified type if such a control is available;
     * otherwise, `null`
     */
    override fun getControl(controlType: String): Any {
        var control = stream.getControl(controlType)
        if (control == null && BufferControl::class.java.name == controlType) {
            control = getBufferControl()
        }
        return control
    }

    /**
     * Implements [javax.media.Controls.getControls]. Delegates to the wrapped
     * `PushBufferStream` and adds the `BufferControl` of this instance if the
     * wrapped `PushBufferStream` does not have a control of such type available.
     *
     * @return an array of `Object`s which represent the control available for the wrapped
     * `PushBufferStream`
     */
    override fun getControls(): Array<Any> {
        var controls = stream.controls
        if (controls == null) {
            val bufferControl = getBufferControl()
            if (bufferControl != null) controls = arrayOf<Any>(bufferControl)
        } else {
            var bufferControlExists = false
            for (control in controls) {
                if (control is BufferControl) {
                    bufferControlExists = true
                    break
                }
            }
            if (!bufferControlExists) {
                val bufferControl = getBufferControl()
                if (bufferControl != null) {
                    val newControls = arrayOfNulls<Any>(controls.size + 1)
                    newControls[0] = bufferControl
                    System.arraycopy(controls, 0, newControls, 1, controls.size)
                }
            }
        }
        return controls
    }

    /**
     * Implements [PushBufferStream.getFormat]. Delegates to the wrapped
     * `PushBufferStream`.
     *
     * @return the `Format` of the media data available for reading in this
     * `PushBufferStream`
     */
    override fun getFormat(): Format {
        return stream.format
    }

    /**
     * Gets the length in milliseconds of the media in a specific `Buffer` (often
     * referred to as duration).
     *
     * @param buffer
     * the `Buffer` which contains media the length in milliseconds of which is to be
     * calculated
     * @return the length in milliseconds of the media in `buffer` if there actually is
     * media in `buffer` and its length in milliseconds can be calculated; otherwise,
     * `0`
     */
    private fun getLengthInMillis(buffer: Buffer): Long {
        val length = buffer.length
        if (length < 1) return 0
        var format = buffer.format
        if (format == null) {
            format = getFormat()
            if (format == null) return 0
        }
        if (format !is AudioFormat) return 0
        val duration = format.computeDuration(length.toLong())
        return if (duration < 1) 0 else duration / 1000000
    }

    /**
     * Implements [PushBufferStream.read]. If an `IOException` has been thrown
     * by the wrapped stream when data was last read from it, re-throws it. If there has been no
     * such exception, reads from the cache of this instance.
     *
     * @param buffer
     * the `Buffer` to receive the read media data
     * @throws IOException
     * if the wrapped stream has thrown such an exception when data was last read from it
     */
    @Throws(IOException::class)
    override fun read(buffer: Buffer) {
        synchronized(cache) {
            if (readException != null) {
                val ioe = IOException()
                ioe.initCause(readException)
                readException = null
                throw ioe
            }
            buffer.length = 0
            if (cache.isNotEmpty()) {
                var bufferOffset = buffer.offset
                while (cache.isNotEmpty()) {
                    val cacheBuffer = cache[0]
                    val nextBufferOffset = read(cacheBuffer, buffer, bufferOffset)
                    if (cacheBuffer.length <= 0 || cacheBuffer.data == null) cache.removeAt(0)
                    bufferOffset = if (nextBufferOffset < 0) break else nextBufferOffset
                }
                cacheLengthInMillis -= getLengthInMillis(buffer)
                if (cacheLengthInMillis < 0) cacheLengthInMillis = 0
                if (canWriteInCache()) (cache as Object).notify()
            }
        }
    }

    /**
     * Reads data from a specific input `Buffer` (if such data is available) and writes the
     * read data into a specific output `Buffer`. The input `Buffer` will be modified
     * to reflect the number of read data units. If the output `Buffer` has allocated an
     * array for storing the read data and the type of this array matches that of the input
     * `Buffer`, it will be used and thus the output `Buffer` may control the maximum
     * number of data units to be read into it.
     *
     * @param in
     * the `Buffer` to read data from
     * @param out
     * the `Buffer` into which to write the data read from the specified `in`
     * @param outOffset
     * the offset in `out` at which the data read from `in` is to be written
     * @return the offset in `out` at which a next round of writing is to continue;
     * `-1` if no more writing in `out` is to be performed and `out` is
     * to be returned to the caller
     * @throws IOException
     * if reading from `in` into `out` fails including if either of the
     * formats of `in` and `out` are not supported
     */
    @Throws(IOException::class)
    private fun read(`in`: Buffer, out: Buffer, outOffset: Int): Int {
        val outData = out.data
        if (outData != null) {
            val inData = `in`.data
            if (inData == null) {
                out.format = `in`.format
                // There was nothing to read so continue reading and concatenating.
                return outOffset
            }
            val dataType: Class<*> = outData.javaClass
            if (inData.javaClass == dataType && dataType == ByteArray::class.java) {
                val inOffset = `in`.offset
                val inLength = `in`.length
                val outBytes = outData as ByteArray
                var outLength = outBytes.size - outOffset

                // Where is it supposed to be written?
                if (outLength < 1) return -1
                if (inLength < outLength) outLength = inLength
                System.arraycopy(inData, inOffset, outBytes, outOffset, outLength)
                out.data = outBytes
                out.length = out.length + outLength

                /*
				 * If we're currently continuing a concatenation, the parameters of the first read
				 * from input are left as the parameters of output. Mostly done at least for
				 * timeStamp.
				 */
                if (out.offset == outOffset) {
                    out.format = `in`.format
                    out.isDiscard = `in`.isDiscard
                    out.isEOM = `in`.isEOM
                    out.flags = `in`.flags
                    out.header = `in`.header
                    out.sequenceNumber = `in`.sequenceNumber
                    out.timeStamp = `in`.timeStamp
                    out.rtpTimeStamp = `in`.rtpTimeStamp
                    out.headerExtension = `in`.headerExtension

                    /*
					 * It's possible that we've split the input into multiple outputs so the output
					 * duration may be different than the input duration. An alternative to
					 * Buffer.TIME_UNKNOWN is possibly the calculation of the output duration as
					  * the input duration multiplied by the ratio between the current output
					  * length and the initial input length.
					 */
                    out.duration = Buffer.TIME_UNKNOWN
                }
                `in`.length = inLength - outLength
                `in`.offset = inOffset + outLength
                // Continue reading and concatenating.
                return outOffset + outLength
            }
        }

        /*
		 * If we were supposed to continue a concatenation and we discovered that it could not be
		 * continued, flush whatever has already been written to the caller.
		 */
        if (out.offset == outOffset) {
            out.copy(`in`)
            val outputLength = out.length
            `in`.length = `in`.length - outputLength
            `in`.offset = `in`.offset + outputLength
        }
        /*
		 * We didn't know how to concatenate the media so return it to the caller.
		 */
        return -1
    }

    /**
     * Implements [PushBufferStream.setTransferHandler]. Delegates to
     * the wrapped `PushBufferStream` but wraps the specified
     * BufferTransferHandler in order to intercept the calls to
     * [BufferTransferHandler.transferData] and read
     * data from the wrapped `PushBufferStream` into the cache during the calls in question.
     *
     * @param transferHandler
     * the `BufferTransferHandler` to be notified by this `PushBufferStream`
     * when media data is available for reading
    `` */
    override fun setTransferHandler(transferHandler: BufferTransferHandler) {
        val substituteTransferHandler = if (transferHandler == null) null else object : StreamSubstituteBufferTransferHandler(transferHandler, stream, this) {
            override fun transferData(stream: PushBufferStream) {
                if (this@CachingPushBufferStream.stream === stream) this@CachingPushBufferStream.transferData(this)
                super.transferData(stream)
            }
        }
        synchronized(cache) {
            stream.setTransferHandler(substituteTransferHandler)
            this.transferHandler = substituteTransferHandler
            cache.notifyAll()
        }
    }

    /**
     * Reads data from the wrapped/input `PushBufferStream` into the cache of this stream if
     * the cache accepts it. If the cache does not accept a new read, blocks the calling thread
     * until the cache accepts a new read and data is read from the wrapped
     * `PushBufferStream` into the cache.
     *
     * @param transferHandler
     * the `BufferTransferHandler` which has been notified
     */
    private fun transferData(transferHandler: BufferTransferHandler) {
        /*
		 * Obviously, we cannot cache every Buffer because we will run out of memory. So wait for
		 * room to appear within cache (or for this instance to be stopped, of course).
		 */
        var interrupted = false
        var canWriteInCache: Boolean
        synchronized(cache) {
            while (true) {
                if (this.transferHandler != transferHandler) {
                    /*
					 * The specified transferHandler has already been obsoleted/replaced so it does
					 * not have the right to cause a read or a write.
					 */
                    canWriteInCache = false
                    break
                } else if (canWriteInCache()) {
                    canWriteInCache = true
                    break
                } else {
                    try {
                        (cache as Object).wait(DEFAULT_BUFFER_LENGTH / 2)
                    } catch (iex: InterruptedException) {
                        interrupted = true
                    }
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt()
        } else if (canWriteInCache) {
            /*
			 * The protocol of PushBufferStream's #read(Buffer) method is that it does not block.
			 * The underlying implementation may be flawed though so we would better not take any
			 * chances. Besides, we have a report at the time of this writing which suggests
			 * that we may really be hitting a rogue implementation in a real-world scenario.
			 */
            val buffer = Buffer()
            val readException: IOException?
            readException = try {
                stream.read(buffer)
                null
            } catch (ioe: IOException) {
                ioe
            }
            if (readException == null) {
                if (!buffer.isDiscard && buffer.length != 0 && buffer.data != null) {
                    /*
					 * Well, we risk disagreeing with #canWriteInCache() because we have
					 * temporarily released the cache but we have read a Buffer from the stream
					 * so it is probably better to not throw it away.
					 */
                    synchronized(cache) {
                        cache.add(buffer)
                        cacheLengthInMillis += getLengthInMillis(buffer)
                    }
                }
            } else {
                synchronized(cache) { this.readException = readException }
            }
        }
    }

    /**
     * Implements a `BufferControl` which enables the adjustment of the length of the
     * buffering performed by a `CachingPushBufferStream`.
     */
    private class BufferControlImpl : BufferControl {
        /**
         * The length of the buffering to be performed by the owner of this instance.
         */
        private var bufferLength = BufferControl.DEFAULT_VALUE

        /**
         * The indicator which determines whether threshold calculations are enabled.
         *
         * @see BufferControl.setEnabledThreshold
         */
        private var enabledThreshold = false

        /**
         * The minimum threshold in milliseconds for the buffering performed by the owner of this
         * instance.
         *
         * @see BufferControl.getMinimumThreshold
         */
        private val minimumThreshold = BufferControl.DEFAULT_VALUE

        /**
         * Implements [BufferControl.getBufferLength]. Gets the length in milliseconds of
         * the buffering performed by the owner of this instance.
         *
         * @return the length in milliseconds of the buffering performed by the owner of this
         * instance; [BufferControl.DEFAULT_VALUE] if it is up to the owner of this
         * instance to decide the length in milliseconds of the buffering to perform if any
         */
        override fun getBufferLength(): Long {
            return bufferLength
        }

        /**
         * Implements [Control.getControlComponent]. Gets the UI `Component`
         * representing this instance and exported by the owner of this instance. Returns
         * `null`.
         *
         * @return the UI `Component` representing this instance and exported by the
         * owner of this instance if such a `Component` is available; otherwise,
         * `null`
         */
        override fun getControlComponent(): Component? {
            return null
        }

        /**
         * Implements [BufferControl.getEnabledThreshold]. Gets the indicator which
         * determines whether threshold calculations are enabled.
         *
         * @return `true` if threshold calculations are enabled; otherwise, `false`
         */
        override fun getEnabledThreshold(): Boolean {
            return enabledThreshold
        }

        /**
         * Implements [BufferControl.getMinimumThreshold]. Gets the minimum threshold in
         * milliseconds for the buffering performed by the owner of this instance.
         *
         * @return the minimum threshold in milliseconds for the buffering performed by the
         * owner of this instance
         */
        override fun getMinimumThreshold(): Long {
            return minimumThreshold
        }

        /**
         * Implements [BufferControl.setBufferLength]. Sets the length in milliseconds
         * of the buffering to be performed by the owner of this instance and returns the value
         * actually in effect after attempting to set it to the specified value.
         *
         * @param bufferLength
         * the length in milliseconds of the buffering to be performed by the owner of this
         * instance
         * @return the length in milliseconds of the buffering performed by the owner of this
         * instance that is actually in effect after the attempt to set it to the specified
         * `bufferLength`
         */
        override fun setBufferLength(bufferLength: Long): Long {
            if (bufferLength == BufferControl.DEFAULT_VALUE || bufferLength > 0) this.bufferLength = bufferLength
            // Returns the current value as specified by the javadoc.
            return getBufferLength()
        }

        /**
         * Implements [BufferControl.setEnabledThreshold]. Sets the indicator which
         * determines whether threshold calculations are enabled.
         *
         * @param enabledThreshold
         * `true` if threshold calculations are to be enabled; otherwise,
         * `false`
         */
        override fun setEnabledThreshold(enabledThreshold: Boolean) {
            this.enabledThreshold = enabledThreshold
        }

        /**
         * Implements [BufferControl.setMinimumThreshold]. Sets the minimum
         * threshold in milliseconds for the buffering to be performed by the owner of this
         * instance and returns the value actually in effect after attempting to set it to the
         * specified value.
         *
         * @param minimumThreshold
         * the minimum threshold in milliseconds for the buffering to be performed by the
         * owner of this instance
         * @return the minimum threshold in milliseconds for the buffering performed by the
         * owner of this instance that is actually in effect after the attempt to set it to the
         * specified `minimumThreshold`
         */
        override fun setMinimumThreshold(minimumThreshold: Long): Long {
            /*
			 * The minimumThreshold property is not supported in any way at the time of this
			 * writing so returns the current value as specified by the javadoc.
			 */
            return getMinimumThreshold()
        }
    }

    companion object {
        /**
         * The default length in milliseconds of the buffering to be performed by
         * `CachePushBufferStream`s.
         */
        const val DEFAULT_BUFFER_LENGTH = 20L

        /**
         * The maximum number of `Buffer`s to be cached in a `CachingPushBufferStream`.
         * Generally, defined to a relatively large value which allows large buffering and yet tries to
         * prevent `OutOfMemoryError`.
         */
        private const val MAX_CACHE_SIZE = 1024
    }
}