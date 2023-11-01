/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime

/**
 * Represents an Objective-C `NSDictionary` object.
 *
 * @author Lyubomir Marinov
 */
open class NSDictionary
/**
 * Initializes a new `NSDictionary` instance which is to represent a specific Objective-C
 * `NSDictionary` object.
 *
 * @param ptr
 * the pointer to the Objective-C `NSDictionary` object to be represented by the
 * new instance
 */
(ptr: Long) : NSObject(ptr) {
    /**
     * Called by the garbage collector to release system resources and perform other cleanup.
     *
     * @see Object.finalize
     */
    protected open fun finalize() {
        release()
    }

    fun intForKey(key: Long): Int {
        return intForKey(ptr, key)
    }

    companion object {
        private external fun intForKey(ptr: Long, key: Long): Int
    }
}