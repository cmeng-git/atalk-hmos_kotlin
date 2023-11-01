/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import org.jivesoftware.smackx.jingle.element.JingleContent

/**
 * Represents functionality which allows a `TransportManagerJabberImpl` implementation to
 * send `transport-info` [Jingle]s for the purposes of expediting candidate negotiation.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface TransportInfoSender {
    /**
     * Sends specific [JingleContent]s in a `transport-info` [Jingle]
     * from the local peer to the remote peer.
     *
     * @param contents the `JingleContent`s to be sent in a `transport-info`
     * `Jingle` from the local peer to the remote peer
     */
    fun sendTransportInfo(contents: Iterable<JingleContent?>?)
}