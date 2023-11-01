/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime

/**
 * Represents a CoreVideo `CVImageBufferRef`.
 *
 * @author Lyubomir Marinov
 */
open class CVImageBuffer(ptr: Long) {
    /**
     * Gets the CoreVideo `CVImageBufferRef` represented by this instance.
     *
     * @return the CoreVideo `CVImageBufferRef` represented by this instance
     */
    /**
     * Sets the CoreVideo `CVImageBufferRef` represented by this instance.
     *
     * @param ptr
     * the CoreVideo `CVImageBufferRef` to be represented by this instance
     */
    /**
     * The CoreVideo `CVImageBufferRef` represented by this instance.
     */
    open var ptr = 0L
        set(ptr) {
            require(ptr != 0L) { "ptr" }
            field = ptr
        }

    /**
     * Initializes a new `CVImageBuffer` instance which is to represent a specific CoreVideo
     * `CVImageBufferRef`.
     *
     * @param ptr
     * the CoreVideo `CVImageBufferRef` to be represented by the new instance
     */
    init {
        this.ptr = ptr
    }

    companion object {
        init {
            System.loadLibrary("jnquicktime")
        }
    }
}