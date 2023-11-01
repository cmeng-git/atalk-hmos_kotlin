/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime

/**
 * Represents an Objective-C `NSError` object.
 *
 * @author Lyubomir Marinov
 */
class NSError
/**
 * Initializes a new `NSError` instance which is to represent a specific Objective-C
 * `NSError` object.
 *
 * @param ptr
 * the pointer to the Objective-C `NSError` object to be represented by the new
 * instance
 */
(ptr: Long) : NSObject(ptr) {
    /**
     * Called by the garbage collector to release system resources and perform other cleanup.
     *
     * @see Object.finalize
     */
    protected fun finalize() {
        release()
    }
}