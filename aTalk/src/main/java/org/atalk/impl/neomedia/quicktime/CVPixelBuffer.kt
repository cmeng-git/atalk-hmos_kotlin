/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime

/**
 * Represents a CoreVideo `CVPixelBufferRef`.
 *
 * @author Lyubomir Marinov
 */
open class CVPixelBuffer
/**
 * Initializes a new `CVPixelBuffer` instance which is to represent a specific CoreVideo `CVPixelBufferRef`.
 *
 * @param ptr
 * the CoreVideo `CVPixelBufferRef` to be represented by the new instance
 */
(ptr: Long) : CVImageBuffer(ptr) {
    /**
     * Gets the number of bytes which represent the pixels of the associated CoreVideo `CVPixelBufferRef`.
     *
     * @return the number of bytes which represent the pixels of the associated CoreVideo
     * `CVPixelBufferRef`
     */
    val byteCount: Int
        get() = getByteCount(ptr)

    /**
     * Gets a `byte` array which represents the pixels of the associated CoreVideo `CVPixelBufferRef`.
     *
     * @return a `byte` array which represents the pixels of the associated CoreVideo `CVPixelBufferRef`
     */
    val bytes: ByteArray
        get() = getBytes(ptr)

    /**
     * Gets the bytes which represent the pixels of the associated `CVPixelBufferRef` into a
     * specific native byte buffer with a specific capacity.
     *
     * @param buf the native byte buffer to return the bytes into
     * @param bufLength the capacity in bytes of `buf`
     * @return the number of bytes written into `buf`
     */
    fun getBytes(buf: Long, bufLength: Int): Int {
        return getBytes(ptr, buf, bufLength)
    }

    /**
     * Gets the height in pixels of this `CVPixelBuffer`.
     *
     * @return the height in pixels of this `CVPixelBuffer`
     */
    val height: Int
        get() = getHeight(ptr)

    /**
     * Gets the width in pixels of this `CVPixelBuffer`.
     *
     * @return the width in pixels of this `CVPixelBuffer`
     */
    val width: Int
        get() = getWidth(ptr)

    companion object {
        /**
         * Gets the number of bytes which represent the pixels of a specific CoreVideo `CVPixelBufferRef`.
         *
         * @param ptr the `CVPixelBufferRef` to get the number of bytes which represent its pixels of
         * @return the number of bytes which represent the pixels of the specified CoreVideo `CVPixelBufferRef`
         */
        private external fun getByteCount(ptr: Long): Int

        /**
         * Gets a `byte` array which represents the pixels of a specific CoreVideo `CVPixelBufferRef`.
         *
         * @param ptr the `CVPixelBufferRef` to get the pixel bytes of
         * @return a `byte` array which represents the pixels of the specified CoreVideo `CVPixelBufferRef`
         */
        private external fun getBytes(ptr: Long): ByteArray

        /**
         * Gets the bytes which represent the pixels of a specific `CVPixelBufferRef` into a
         * specific native byte buffer with a specific capacity.
         *
         * @param ptr the `CVPixelBufferRef` to get the bytes of
         * @param buf the native byte buffer to return the bytes into
         * @param bufLength the capacity in bytes of `buf`
         * @return the number of bytes written into `buf`
         */
        private external fun getBytes(ptr: Long, buf: Long, bufLength: Int): Int

        /**
         * Gets the height in pixels of a specific CoreVideo `CVPixelBufferRef`.
         *
         * @param ptr the CoreVideo `CVPixelBufferRef` to get the height in pixels of
         * @return the height in pixels of the specified CoreVideo `CVPixelBufferRef`
         */
        private external fun getHeight(ptr: Long): Int

        /**
         * Gets the width in pixels of a specific CoreVideo `CVPixelBufferRef`.
         *
         * @param ptr the CoreVideo `CVPixelBufferRef` to get the width in pixels of
         * @return the width in pixels of the specified CoreVideo `CVPixelBufferRef`
         */
        private external fun getWidth(ptr: Long): Int

        /**
         * Native copy from native pointer `src` to byte array `dst`.
         *
         * @param dst destination array
         * @param dstOffset offset of `dst` to copy data to
         * @param dstLength length of `dst`
         * @param src native pointer source
         */
        external fun memcpy(dst: ByteArray?, dstOffset: Int, dstLength: Int, src: Long)
    }
}