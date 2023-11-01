/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import java.util.*

/**
 * Parent class for SecurityOn and SecurityOff events.
 *
 * @author Yana Stamcheva
 */
abstract class CallPeerSecurityStatusEvent
/**
 * Constructor required by the EventObject.
 *
 * @param source the source object for this event.
 * @param sessionType either `AUDIO_SESSION` or `VIDEO_SESSION` to indicate the type
 * of the session
 */(source: Any?,
        /**
         * Session type of the event [.AUDIO_SESSION] or [.VIDEO_SESSION].
         */
        private val sessionType: Int) : EventObject(source) {
    /**
     * Returns the type of the session, either AUDIO_SESSION or VIDEO_SESSION.
     *
     * @return the type of the session, either AUDIO_SESSION or VIDEO_SESSION.
     */
    fun getSessionType(): Int {
        return sessionType
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Constant value defining that security is enabled.
         */
        const val AUDIO_SESSION = 1

        /**
         * Constant value defining that security is disabled.
         */
        const val VIDEO_SESSION = 2
    }
}