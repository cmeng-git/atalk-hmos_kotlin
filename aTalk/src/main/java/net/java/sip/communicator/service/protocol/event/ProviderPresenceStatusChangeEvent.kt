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

import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import java.beans.PropertyChangeEvent

/**
 * Instances of this class represent a change in the status of the provider that triggered them.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class ProviderPresenceStatusChangeEvent
/**
 * Creates an event instance indicating a change of the property specified by
 * `eventType` from `oldValue` to `newValue`.
 *
 * @param source the provider that generated the event
 * @param oldValue the status the source provider was int before entering the new state.
 * @param newValue the status the source provider is currently in.
 */
(source: ProtocolProviderService?,
 oldValue: PresenceStatus?, newValue: PresenceStatus?) : PropertyChangeEvent(source, ProviderPresenceStatusChangeEvent::class.java.name, oldValue, newValue) {
    /**
     * Returns the provider that has generated this event
     *
     * @return the provider that generated the event.
     */
    fun getProvider(): ProtocolProviderService {
        return getSource() as ProtocolProviderService
    }

    /**
     * Returns the status of the provider before this event took place.
     *
     * @return a PresenceStatus instance indicating the event the source provider was in before it
     * entered its new state.
     */
    fun getOldStatus(): PresenceStatus {
        return super.getOldValue() as PresenceStatus
    }

    /**
     * Returns the status of the provider after this event took place. (i.e. at the time the event
     * is being dispatched).
     *
     * @return a PresenceStatus instance indicating the event the source provider is in after the
     * status change occurred.
     */
    fun getNewStatus(): PresenceStatus {
        return super.getNewValue() as PresenceStatus
    }

    /**
     * Returns a String representation of this ProviderPresenceStatusChangeEvent
     *
     * @return A a String representation of this ProviderPresenceStatusChangeEvent.
     */
    override fun toString(): String {
        return ("ProviderPresenceStatusChangeEvent-[" + "OldStatus=" + getOldStatus()
                + ",  NewStatus=" + getNewStatus() + "]")
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}