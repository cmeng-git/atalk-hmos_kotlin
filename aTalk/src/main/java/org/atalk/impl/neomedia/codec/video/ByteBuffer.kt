/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video

import org.atalk.impl.neomedia.codec.FFmpeg.av_free
import org.atalk.impl.neomedia.codec.FFmpeg.av_malloc

/**
 * Represents a buffer of native memory with a specific size/capacity which may
 * contains a specific number of bytes of valid data. If the memory represented
 * by a `ByteBuffer` instance has been allocated by the
 * `ByteBuffer` instance itself, the native memory will automatically be
 * freed upon finalization.
 *
 * @author Lyubomir Marinov
 */
open class ByteBuffer {
    /**
     * Gets the maximum number of bytes which may be written into the native
     * memory represented by this instance. If `0`, this instance has
     * been initialized to provide read-only access to the native memory it
     * represents and will not deallocate it upon finalization.
     *
     * @return the maximum number of bytes which may be written into the native
     * memory represented by this instance
     */
    /**
     * The maximum number of bytes which may be written into the native memory
     * represented by this instance. If `0`, this instance has been
     * initialized to provide read-only access to the native memory it
     * represents and will not deallocate it upon finalization.
     */
    @get:Synchronized
    var capacity: Int
        private set

    /**
     * The number of bytes of valid data that the native memory represented by
     * this instance contains.
     */
    private var length: Int
    /**
     * Gets the pointer to the native memory represented by this instance.
     *
     * @return the pointer to the native memory represented by this instance
     */
    /**
     * The pointer to the native memory represented by this instance.
     */
    @get:Synchronized
    var ptr: Long
        private set

    /**
     * Initializes a new `ByteBuffer` instance with a specific
     * `capacity` of native memory. The new instance allocates the native
     * memory and automatically frees it upon finalization.
     *
     * @param capacity the maximum number of bytes which can be written into the
     * native memory represented by the new instance
     */
    constructor(capacity: Int) {
        require(capacity >= 1) { "capacity" }
        ptr = av_malloc(capacity)
        if (ptr == 0L) throw OutOfMemoryError("av_malloc($capacity)")
        this.capacity = capacity
        length = 0
    }

    /**
     * Initializes a new `ByteBuffer` instance which is to represent a
     * specific block of native memory. Since the specified native memory has
     * been allocated outside the new instance, the new instance will not
     * automatically free it.
     *
     * @param ptr a pointer to the block of native memory to be represented by
     * the new instance
     */
    constructor(ptr: Long) {
        this.ptr = ptr

        capacity = 0
        length = 0
    }

    /**
     * {@inheritDoc}
     *
     * Frees the native memory represented by this instance if the native memory
     * has been allocated by this instance and has not been freed yet i.e.
     * ensures that [.free] is invoked on this instance.
     *
     * @see Object.finalize
     */
    @Throws(Throwable::class)
    protected open fun finalize() {
        try {
            free()
        } finally {
            // super.finalize()
        }
    }

    /**
     * Frees the native memory represented by this instance if the native memory
     * has been allocated by this instance and has not been freed yet.
     */
    @Synchronized
    open fun free() {
        if (capacity != 0 && ptr != 0L) {
            av_free(ptr)
            capacity = 0
            ptr = 0
        }
    }

    /**
     * Gets the number of bytes of valid data that the native memory represented
     * by this instance contains.
     *
     * @return the number of bytes of valid data that the native memory
     * represented by this instance contains
     */
    fun getLength(): Int {
        return length
    }

    /**
     * Sets the number of bytes of valid data that the native memory represented
     * by this instance contains.
     *
     * @param length the number of bytes of valid data that the native memory
     * represented by this instance contains
     * @throws IllegalArgumentException if `length` is a negative value
     */
    fun setLength(length: Int) {
        require(length >= 0) { "length" }
        this.length = length
    }
}