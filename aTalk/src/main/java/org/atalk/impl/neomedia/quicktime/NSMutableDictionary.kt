/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime

/**
 * Represents an Objective-C `NSMutableDictionary` object.
 *
 * @author Lyubomir Marinov
 */
class NSMutableDictionary
/**
 * Initializes a new `NSMutableDictionary` instance which is to represent a specific
 * Objective-C `NSMutableDictionary` object.
 *
 * @param ptr
 * the pointer to the Objective-C `NSMutableDictionary` object to be represented
 * by the new instance
 */
/**
 * Initializes a new `NSMutableDictionary` instance which is to represent a new
 * Objective-C `NSMutableDictionary` object.
 */
@JvmOverloads constructor(ptr: Long = allocAndInit()) : NSDictionary(ptr) {
    fun setIntForKey(value: Int, key: Long) {
        setIntForKey(ptr, value, key)
    }

    companion object {
        private external fun allocAndInit(): Long
        private external fun setIntForKey(ptr: Long, value: Int, key: Long)
    }
}