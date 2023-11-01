/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.control

import javax.media.Control

/**
 * An interface which allows to flush a buffer.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
interface FlushableControl : Control {
    /**
     * Flushes the buffer.
     */
    fun flush()
}