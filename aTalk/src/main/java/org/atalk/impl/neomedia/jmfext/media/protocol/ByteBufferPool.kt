/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol

import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.codec.video.ByteBuffer
import java.lang.ref.WeakReference

/**
 * Represents a pool of `ByteBuffer`s which reduces the allocations and deallocations of
 * `ByteBuffer`s in the Java heap and of native memory in the native heap.
 *
 * @author Lyubomir Marinov
 */
class ByteBufferPool {
    /**
     * The `ByteBuffer`s which are managed by this `ByteBufferPool`.
     */
    private val buffers = ArrayList<PooledByteBuffer>()

    /**
     * Drains this `ByteBufferPool` i.e. frees the `ByteBuffer`s that it contains.
     */
    @Synchronized
    fun drain() {
        val i = buffers.iterator()
        while (i.hasNext()) {
            val buffer = i.next()
            i.remove()
            buffer.doFree()
        }
    }

    /**
     * Gets a `ByteBuffer` out of this pool of `ByteBuffer`s which is capable to
     * receiving at least `capacity` number of bytes.
     *
     * @param capacity
     * the minimal number of bytes that the returned `ByteBuffer` is to be capable of
     * receiving
     * @return a `ByteBuffer` which is ready for writing captured media data into and which
     * is capable of receiving at least `capacity` number of bytes
     */
    @Synchronized
    fun getBuffer(capacity: Int): ByteBuffer {
        // XXX Pad with FF_INPUT_BUFFER_PADDING_SIZE or hell will break loose.
        var capacity = capacity
        capacity += FFmpeg.FF_INPUT_BUFFER_PADDING_SIZE
        var buffer: ByteBuffer? = null
        val i = buffers.iterator()
        while (i.hasNext()) {
            val aBuffer = i.next()
            if (aBuffer.capacity >= capacity) {
                i.remove()
                buffer = aBuffer
                break
            }
        }
        if (buffer == null) buffer = PooledByteBuffer(capacity, this)
        return buffer
    }

    /**
     * Returns a specific `ByteBuffer` into this pool of `ByteBuffer`s.
     *
     * @param buffer
     * the `ByteBuffer` to be returned into this pool of `ByteBuffer`s
     */
    @Synchronized
    private fun returnBuffer(buffer: PooledByteBuffer) {
        if (!buffers.contains(buffer)) buffers.add(buffer)
    }

    /**
     * Implements a `ByteBuffer` which is pooled in a `ByteBufferPool` in order to
     * reduce the numbers of allocations and deallocations of `ByteBuffer`s and their
     * respective native memory.
     */
    private class PooledByteBuffer(capacity: Int, pool: ByteBufferPool) : ByteBuffer(capacity) {
        /**
         * The `ByteBufferPool` in which this instance is pooled and in which it should
         * returns upon [.free].
         */
        private val pool: WeakReference<ByteBufferPool>

        init {
            this.pool = WeakReference(pool)
        }

        /**
         * Invokes [ByteBuffer.free] i.e. does not make any attempt to return this instance
         * to the associated `ByteBufferPool` and frees the native memory represented by
         * this instance.
         */
        fun doFree() {
            super.free()
        }

        /**
         * {@inheritDoc}
         *
         *
         * Returns this `ByteBuffer` and, respectively, the native memory that it represents
         * to the associated `ByteBufferPool`. If the `ByteBufferPool` has already
         * been finalized by the garbage collector, frees the native memory represented by this
         * instance.
         */
        override fun free() {
            val pool = pool.get()
            if (pool == null) doFree() else pool.returnBuffer(this)
        }
    }
}