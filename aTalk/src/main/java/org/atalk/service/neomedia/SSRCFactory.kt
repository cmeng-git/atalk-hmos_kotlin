/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

/**
 * Declares a factory of synchronization source (SSRC) identifiers.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface SSRCFactory {
    /**
     * Generates a new synchronization source (SSRC) identifier. If the returned synchronization
     * source (SSRC) identifier is found to not be globally unique within the associated RTP
     * session, the method will be invoked again.
     *
     * @param cause
     * a `String` which specified the cause of the invocation of the method
     * @return a randomly chosen `int` value which is to be utilized as a new synchronization
     * source (SSRC) identifier should it be found to be globally unique within the
     * associated RTP session or `Long.MAX_VALUE` if this `SSRCFactory` has
     * cancelled the operation
     */
    fun generateSSRC(cause: String): Long
}