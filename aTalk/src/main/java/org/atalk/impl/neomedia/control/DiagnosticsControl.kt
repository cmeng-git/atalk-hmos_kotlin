/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.control

import javax.media.Control

/**
 * Defines an FMJ `Control` which allows the diagnosis of the functional health of a
 * procedure/process.
 *
 * @author Lyubomir Marinov
 */
interface DiagnosticsControl : Control {
    /**
     * Gets the time in milliseconds at which the associated procedure/process has started
     * malfunctioning.
     *
     * @return the time in milliseconds at which the associated procedure/process has started
     * malfunctioning or `NEVER` if the associated procedure/process is functioning
     * normally
     */
    val malfunctioningSince: Long

    /**
     * Returns a human-readable `String` representation of the associated procedure/process.
     *
     * @return a human-readable `String` representation of the associated procedure/process
     */
    override fun toString(): String

    companion object {
        /**
         * The constant which expresses a non-existent time in milliseconds for the purposes of
         * [.getMalfunctioningSince]. Explicitly chosen to be `0` rather than `-1`
         * in the name of efficiency.
         */
        const val NEVER = 0L
    }
}