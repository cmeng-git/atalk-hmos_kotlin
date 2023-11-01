/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import java.util.*

/**
 * `GenericEvent`s indicate reception of an new generic event.
 *
 * @author Damian Minkov
 */
class GenericEvent
/**
 * Constructs a GenericEvent.
 *
 * @param sourceProtocolProvider
 * The object on which the Event initially occurred.
 * @param from
 * the contact from which this event is coming from.
 * @param eventName
 * the event name.
 * @param eventValue
 * the event value.
 * @param sourceContact
 * contact for this event.
 * @throws IllegalArgumentException
 * if source is null.
 */
(sourceProtocolProvider: ProtocolProviderService?,
        /**
         * The contact which is the source of this event.
         */
        private val from: Contact,
        /**
         * The event name.
         */
        private val eventName: String,
        /**
         * The event value.
         */
        private val eventValue: String,
        /**
         * The source contact for this event.
         */
        private val sourceContact: Contact) : EventObject(sourceProtocolProvider) {
    /**
     * The event name.
     *
     * @return the event name.
     */
    fun getEventName(): String {
        return eventName
    }

    /**
     * The event value.
     *
     * @return the event value.
     */
    fun getEventValue(): String {
        return eventValue
    }

    /**
     * The contact which is the source of this event.
     *
     * @return the contact which is the source of this event.
     */
    fun getFrom(): Contact {
        return from
    }

    /**
     * Returns the `ProtocolProviderService` which originated this event.
     *
     * @return the source `ProtocolProviderService`
     */
    fun getSourceProvider(): ProtocolProviderService {
        return getSource() as ProtocolProviderService
    }

    /**
     * Returns a String representation of this GenericEvent.
     *
     * @return A a String representation of this GenericEvent.
     */
    override fun toString(): String {
        return ("GenericEvent from:" + from + " - eventName:" + eventName + " eventValue:"
                + eventValue)
    }

    /**
     * Returns The source contact for this event.
     *
     * @return the event source contact.
     */
    fun getSourceContact(): Contact {
        return sourceContact
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}