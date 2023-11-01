/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.control

import javax.media.Control

/**
 * An interface used to notify encoders about the packet loss which is expected.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
interface PacketLossAwareEncoder : Control {
    /**
     * Tells the encoder to expect `percentage` percent packet loss.
     *
     * @return the percentage of expected packet loss
     */
    fun setExpectedPacketLoss(percentage: Int)
}