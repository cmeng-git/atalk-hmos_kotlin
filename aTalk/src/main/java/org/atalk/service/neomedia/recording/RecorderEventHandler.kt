/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.recording

/**
 * An interface that allows handling of `RecorderEvent` instances, such as writing them to
 * disk in some format.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
interface RecorderEventHandler {
    /**
     * Handle a specific `RecorderEvent`
     *
     * @param ev
     * the event to handle.
     * @return
     */
    fun handleEvent(ev: RecorderEvent?): Boolean

    /**
     * Closes the `RecorderEventHandler`.
     */
    fun close()
}