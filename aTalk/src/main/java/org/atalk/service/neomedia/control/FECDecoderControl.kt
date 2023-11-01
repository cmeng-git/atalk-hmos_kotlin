/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.control

import javax.media.Control

/**
 * An interface used to communicate with a decoder that supports decoding FEC
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
interface FECDecoderControl : Control {
    /**
     * Returns the number of packets for which FEC was decoded
     *
     * @return the number of packets for which FEC was decoded
     */
    fun fecPacketsDecoded(): Int
}