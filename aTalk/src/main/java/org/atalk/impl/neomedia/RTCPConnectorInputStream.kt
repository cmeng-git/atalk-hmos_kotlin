/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import org.atalk.service.neomedia.event.RTCPFeedbackMessageEvent
import org.atalk.service.neomedia.event.RTCPFeedbackMessageListener
import java.io.IOException
import java.net.DatagramSocket
import javax.media.*

/**
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class RTCPConnectorInputStream
/**
 * Initializes a new `RTCPConnectorInputStream` which is to receive packet data from a
 * specific UDP socket.
 *
 * @param socket
 * the UDP socket the new instance is to receive data from
 */
(socket: DatagramSocket?) : RTPConnectorUDPInputStream(socket) {
    /**
     * List of RTCP feedback message listeners;
     */
    private val listeners = ArrayList<RTCPFeedbackMessageListener>()

    /**
     * Add an `RTCPFeedbackMessageListener`.
     *
     * @param listener
     * object that will listen to incoming RTCP feedback messages.
     */
    fun addRTCPFeedbackMessageListener(listener: RTCPFeedbackMessageListener?) {
        if (listener == null) throw NullPointerException("listener")
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    /**
     * Remove an `RTCPFeedbackMessageListener`.
     *
     * @param listener
     * object to remove from listening RTCP feedback messages.
     */
    fun removeRTCPFeedbackMessageListener(listener: RTCPFeedbackMessageListener) {
        listeners.remove(listener)
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun read(buffer: Buffer?, data: ByteArray?, offset: Int, length: Int): Int {
        val pktLength = super.read(buffer, data, offset, length)
        fireRTCPFeedbackMessageReceived(this, data, offset, pktLength, listeners)
        return pktLength
    }

    companion object {
        /**
         * Notifies a specific list of `RTCPFeedbackMessageListener`s about a specific RTCP
         * feedback message if such a message can be parsed out of a specific `byte` buffer.
         *
         * @param source
         * the object to be reported as the source of the `RTCPFeedbackMessageEvent` to be
         * fired
         * @param buffer
         * the `byte` buffer which may specific an RTCP feedback message
         * @param offset
         * the offset in `buffer` at which the reading of bytes is to begin
         * @param length
         * the number of bytes in `buffer` to be read for the purposes of parsing an RTCP
         * feedback message and firing an `RTPCFeedbackEvent`
         * @param listeners
         * the list of `RTCPFeedbackMessageListener`s to be notified about the specified
         * RTCP feedback message if such a message can be parsed out of the specified
         * `buffer`
         */
        fun fireRTCPFeedbackMessageReceived(source: Any?, buffer: ByteArray?, offset: Int,
                                            length: Int, listeners: List<RTCPFeedbackMessageListener>) {
            /*
         * RTCP feedback message length is minimum 12 bytes:
         * 1. Version/Padding/Feedback message type: 1 byte
         * 2. Payload type: 1 byte
         * 3. Length: 2 bytes
         * 4. SSRC of packet sender: 4 bytes
         * 5. SSRC of media source: 4 bytes
		 */
            if (length >= 12 && !listeners.isEmpty()) {
                val pt = buffer!![offset + 1].toInt() and 0xFF
                if (pt == RTCPFeedbackMessageEvent.PT_PS || pt == RTCPFeedbackMessageEvent.PT_TL) {
                    val fmt = buffer[offset].toInt() and 0x1F
                    val ev = RTCPFeedbackMessageEvent(source, fmt, pt)
                    for (l in listeners) l.rtcpFeedbackMessageReceived(ev)
                }
            }
        }
    }
}