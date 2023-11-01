/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.ulaw

import com.sun.media.codec.audio.ulaw.Packetizer

/**
 * Overrides the ULaw Packetizer with a different packet size.
 *
 * @author Thomas Hofer
 */
class Packetizer : Packetizer() {
    /**
     * Constructs a new ULaw `Packetizer`.
     */
    init {
        // RFC 3551 4.5 Audio Encodings default ms/packet is 20
        packetSize = 160
        setPacketSize(packetSize)
        PLUGIN_NAME = "ULaw Packetizer"
    }
}