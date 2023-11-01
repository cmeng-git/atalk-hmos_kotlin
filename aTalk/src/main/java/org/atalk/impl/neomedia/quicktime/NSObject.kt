/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime

/**
 * Represents the root of most Objective-C class hierarchies which which objects inherit a basic
 * interface to the runtime system and the ability to behave as Objective-C objects.
 *
 * @author Lyubomir Marinov
 */
open class NSObject(ptr: Long) {
    /**
     * The pointer to the Objective-C object represented by this instance.
     */
    var ptr = 0L
    set(ptr) {
        require(ptr != 0L) { "ptr" }
        field = ptr
    }

    /**
     * Decrements the reference count of the Objective-C object represented by this instance. It is
     * sent a `dealloc` message when its reference count reaches `0`.
     */
    open fun release() {
        release(ptr)
    }

    /**
     * Increments the reference count of the Objective-C object represented by this instance.
     */
    fun retain() {
        retain(ptr)
    }

    companion object {
        init {
            System.loadLibrary("jnquicktime")
        }
        /**
         * Decrements the reference count of a specific Objective-C object. It is sent a
         * `dealloc` message when its reference count reaches `0`.
         *
         * @param ptr
         * the pointer to the Objective-C object to decrement the reference count of
         */
        /**
         * Decrements the reference count of the Objective-C object represented by this instance. It is
         * sent a `dealloc` message when its reference count reaches `0`.
         */
        @JvmOverloads
        external fun release(ptr: Long)

        /**
         * Increments the reference count of a specific Objective-C object.
         *
         * @param ptr
         * the pointer to be Objective-C object to increment the reference count of
         */
        external fun retain(ptr: Long)
    }
}