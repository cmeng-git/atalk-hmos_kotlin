/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import java.util.*

/**
 * The `CallPeerSecurityFailedEvent` is triggered whenever a problem has occurred during call security process.
 *
 * @author Yana Stamcheva
 * @author Werner Dittmann
 */
class CallPeerSecurityMessageEvent
/**
 * Creates a `CallPeerSecurityFailedEvent` by specifying the call peer, event type and
 * message associated with this event.
 *
 * @param source the object on which the event initially occurred
 * @param eventMessage the message associated with this event.
 * @param i18nMessage the internationalized message associated with this event that could be shown to the user.
 * @param eventSeverity severity level.
 */(source: Any?,
        /**
         * The message associated with this event.
         */
        private val eventMessage: String,
        /**
         * The internationalized message associated with this event.
         */
        private val eventI18nMessage: String,
        /**
         * The severity of the security message event.
         */
        private val eventSeverity: Int) : EventObject(source) {
    /**
     * Returns the message associated with this event.
     *
     * @return the message associated with this event.
     */
    fun getMessage(): String {
        return eventMessage
    }

    /**
     * Returns the internationalized message associated with this event.
     *
     * @return the internationalized message associated with this event.
     */
    fun getI18nMessage(): String {
        return eventI18nMessage
    }

    /**
     * Returns the event severity.
     *
     * @return the eventSeverity
     */
    fun getEventSeverity(): Int {
        return eventSeverity
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}