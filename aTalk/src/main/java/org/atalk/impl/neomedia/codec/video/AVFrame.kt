/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video

import org.atalk.impl.neomedia.codec.FFmpeg.avcodec_alloc_frame
import org.atalk.impl.neomedia.codec.FFmpeg.avcodec_free_frame
import org.atalk.impl.neomedia.codec.FFmpeg.avpicture_fill
import javax.media.Buffer
import javax.media.Format

/**
 * Represents a pointer to a native FFmpeg `AVFrame` object.
 *
 * @author Lyubomir Marinov
 */
class AVFrame {
    /**
     * Gets the `ByteBuffer` whose native memory is set on the native counterpart of this instance/
     * `AVFrame`.
     *
     * @return the `ByteBuffer` whose native memory is set on the native counterpart of this instance/
     * `AVFrame`.
     */
    /**
     * The `ByteBuffer` whose native memory is set on the native counterpart of this instance/`AVFrame`.
     */
    @get:Synchronized
    var data: ByteBuffer? = null
        private set

    /**
     * The indicator which determines whether the native memory represented by this instance is to be freed upon
     * finalization.
     */
    private var free: Boolean
    /**
     * Gets the pointer to the native FFmpeg `AVFrame` object represented by this instance.
     *
     * @return the pointer to the native FFmpeg `AVFrame` object represented by this instance
     */
    /**
     * The pointer to the native FFmpeg `AVFrame` object represented by this instance.
     */
    @get:Synchronized
    var ptr: Long
        private set

    /**
     * Initializes a new `FinalizableAVFrame` instance which is to allocate a new native FFmpeg `AVFrame`
     * and represent it.
     */
    constructor() {
        ptr = avcodec_alloc_frame()
        if (ptr == 0L) throw OutOfMemoryError("avcodec_alloc_frame()")
        free = true
    }

    /**
     * Initializes a new `AVFrame` instance which is to represent a specific pointer to a native FFmpeg
     * `AVFrame` object. Because the native memory/`AVFrame` has been allocated outside the new instance,
     * the new instance does not automatically free it upon finalization.
     *
     * @param ptr
     * the pointer to the native FFmpeg `AVFrame` object to be represented by the new instance
     */
    constructor(ptr: Long) {
        require(ptr != 0L) { "ptr" }
        this.ptr = ptr
        free = false
    }

    @Synchronized
    fun avpicture_fill(data: ByteBuffer, format: AVFrameFormat): Int {
        val size = format.size
        val ret = avpicture_fill(ptr, data.ptr, format.pixFmt, size.width, size.height)
        if (ret >= 0) {
            if (this.data != null) this.data!!.free()
            this.data = data
        }
        return ret
    }

    /**
     * Deallocates the native memory/FFmpeg `AVFrame` object represented by this instance if this instance has
     * allocated it upon initialization and it has not been deallocated yet i.e. ensures that [.free] is invoked
     * on this instance.
     *
     * @see Object.finalize
     */
    @Throws(Throwable::class)
    protected fun finalize() {
        try {
            free()
        } finally {
            // super.finalize()
        }
    }

    /**
     * Deallocates the native memory/FFmpeg `AVFrame` object represented by this instance if this instance has
     * allocated it upon initialization and it has not been deallocated yet.
     */
    @Synchronized
    fun free() {
        if (free && ptr != 0L) {
            avcodec_free_frame(ptr)
            free = false
            ptr = 0
        }
        if (data != null) {
            data!!.free()
            data = null
        }
    }

    companion object {
        fun read(buffer: Buffer, format: Format, data: ByteBuffer): Int {
            val frameFormat = format as AVFrameFormat
            val o = buffer.data
            val frame: AVFrame
            if (o is AVFrame) frame = o else {
                frame = AVFrame()
                buffer.data = frame
            }
            return frame.avpicture_fill(data, frameFormat)
        }
    }
}