/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime

/**
 * Defines an `Exception` which reports an `NSError`.
 *
 * @author Lyubomir Marinov
 */
class NSErrorException
/**
 * Initializes a new `NSErrorException` instance which is to report a specific
 * `NSError`.
 *
 * @param error
 * the `NSError` to be reported by the new instance
 */
(
        /**
         * The `NSError` reported by this instance.
         */
        val error: NSError) : Exception() {
    /**
     * Gets the `NSError` reported by this instance.
     *
     * @return the `NSError` reported by this instance
     */

    /**
     * Initializes a new `NSErrorException` instance which is to report a specific
     * Objective-C `NSError`.
     *
     * @param errorPtr
     * the pointer to the Objective-C `NSError` object to be reported by the new
     * instance
     */
    constructor(errorPtr: Long) : this(NSError(errorPtr)) {}
}