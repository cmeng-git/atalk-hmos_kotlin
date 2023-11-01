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
 * Notifies `UserSearchProviderListener` that a provider that supports user search is added
 * or removed.
 *
 * @author Hristo Terezov
 */
class UserSearchProviderEvent
/**
 * Constructs new `UserSearchProviderEvent` event.
 *
 * @param provider
 * the provider.
 * @param type
 * the type of the event.
 */(provider: ProtocolProviderService?,
        /**
         * The type of the event.
         */
        private val type: Int) : EventObject(provider) {
    /**
     * Returns the provider associated with the event.
     *
     * @return the provider associated with the event.
     */
    fun getProvider(): ProtocolProviderService {
        return getSource() as ProtocolProviderService
    }

    /**
     * Returns the type of the event.
     *
     * @return the type of the event.
     */
    fun getType(): Int {
        return type
    }

    companion object {
        /**
         * The serial ID.
         */
        private const val serialVersionUID = -1285649707213476360L

        /**
         * A type that indicates that the provider is added.
         */
        var PROVIDER_ADDED = 0

        /**
         * A type that indicates that the provider is removed.
         */
        var PROVIDER_REMOVED = 1
    }
}