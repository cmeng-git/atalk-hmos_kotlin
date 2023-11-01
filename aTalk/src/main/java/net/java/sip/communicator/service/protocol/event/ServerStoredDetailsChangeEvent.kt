/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.ProtocolProviderService

import java.util.*

/**
 * Instances of this class represent a change in the server stored details change that triggered
 * them.
 *
 * @author Damian Minkov
 */
class ServerStoredDetailsChangeEvent
/**
 * Constructs a ServerStoredDetailsChangeEvent.
 *
 * @param source
 * The object on which the Event initially occurred.
 * @param eventID
 * the event ID
 * @param oldValue
 * old value
 * @param newValue
 * new value
 * @throws IllegalArgumentException
 * if source is null.
 */(source: ProtocolProviderService?,
        /**
         * The event type id.
         */
        private val eventID: Int,
        /**
         * Previous value for property. May be null if not known.
         *
         * @serial
         */
        private val oldValue: Any?,
        /**
         * New value for property. May be null if not known.
         *
         * @serial
         */
        private val newValue: Any) : EventObject(source) {
    /**
     * Returns the provider that has generated this event
     *
     * @return the provider that generated the event.
     */
    fun getProvider(): ProtocolProviderService {
        return getSource() as ProtocolProviderService
    }

    /**
     * Gets the new value for the event, expressed as an Object.
     *
     * @return The new value for the event, expressed as an Object.
     */
    fun getNewValue(): Any {
        return newValue
    }

    /**
     * Gets the old value for the event, expressed as an Object.
     *
     * @return The old value for the event, expressed as an Object.
     */
    fun getOldValue(): Any {
        return oldValue!!
    }

    /**
     * Returns the event type id.
     *
     * @return the event ID
     */
    fun getEventID(): Int {
        return eventID
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Indicates that the ServerStoredDetailsChangeEvent instance was triggered by adding a new
         * detail.
         */
        const val DETAIL_ADDED = 1

        /**
         * Indicates that the ServerStoredDetailsChangeEvent instance was triggered by the removal of an
         * existing detail.
         */
        const val DETAIL_REMOVED = 2

        /**
         * Indicates that the ServerStoredDetailsChangeEvent instance was triggered by the fact a detail
         * was replaced with new value.
         */
        const val DETAIL_REPLACED = 3
    }
}