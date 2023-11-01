/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform

import org.atalk.impl.neomedia.RTCPConnectorInputStream
import org.atalk.impl.neomedia.RTPConnectorUDPInputStream
import org.atalk.service.neomedia.event.RTCPFeedbackMessageListener
import java.io.IOException
import java.net.DatagramSocket
import java.util.*
import javax.media.Buffer

/**
 * Implement control channel (RTCP) for `TransformInputStream` which notify listeners when
 * RTCP feedback messages are received.
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
/**
 * Initializes a new `ControlTransformInputStream` which is to receive packet data from a specific UDP socket.
 *
 * @param socket the UDP socket the new instance is to receive data from
 */
class ControlTransformInputStream
    (socket: DatagramSocket) : RTPConnectorUDPInputStream(socket) {
    /**
     * The list of `RTCPFeedbackMessageListener`s.
     */
    private val listeners: MutableList<RTCPFeedbackMessageListener> = LinkedList()

    /**
     * Adds an `RTCPFeedbackMessageListener`.
     *
     * @param listener the `RTCPFeedbackMessageListener` to add
     */
    fun addRTCPFeedbackMessageListener(listener: RTCPFeedbackMessageListener) {
        // if (listener == null) throw NullPointerException("listener")

        synchronized(listeners) {
            if (!listeners.contains(listener))
                listeners.add(listener)
        }
    }

    /**
     * Removes an `RTCPFeedbackMessageListener`.
     *
     * @param listener the `RTCPFeedbackMessageListener` to remove
     */
    fun removeRTCPFeedbackMessageListener(listener: RTCPFeedbackMessageListener?) {
        if (listener != null) {
            synchronized(listeners) {
                listeners.remove(listener)
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun read(buffer: Buffer?, data: ByteArray?, offset: Int, length: Int): Int {
        val pktLength = super.read(buffer, data, offset, length)
        RTCPConnectorInputStream.fireRTCPFeedbackMessageReceived(this, data, offset, pktLength, listeners)
        return pktLength
    }
}