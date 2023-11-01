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

import net.java.sip.communicator.service.protocol.AccountManager
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import java.util.*

/**
 * Represents a notifying event fired by a specific [AccountManager].
 *
 * @author Lubomir Marinov
 */
class AccountManagerEvent
/**
 * Initializes a new `AccountManagerEvent` instance fired by a specific
 * `AccountManager` in order to notify of an event of a specific type occurring while
 * working on a specific `ProtocolProviderFactory`.
 *
 * @param accountManager
 * the `AccountManager` issuing the notification i.e. the source of the event
 * @param type
 * the type of the event which is one of [.STORED_ACCOUNTS_LOADED]
 * @param factory
 * the `ProtocolProviderFactory` being worked on at the time this event has
 * been fired
 */
(accountManager: AccountManager?,
        /**
         * The (detail) type of this event which is one of [.STORED_ACCOUNTS_LOADED].
         */
        val type: Int,
        /**
         * The `ProtocolProviderFactory` being worked on at the time this event has been
         * fired.
         */
        val factory: ProtocolProviderFactory?) : EventObject(accountManager) {
    /**
     * Gets the `ProtocolProviderFactory` being worked on at the time this event has been
     * fired.
     *
     * @return the `ProtocolProviderFactory` being worked on at the time this event has
     * been fired
     */
    /**
     * Gets the (detail) type of this event which is one of `STORED_ACCOUNTS_LOADED`.
     *
     * @return the (detail) type of this event which is one of `STORED_ACCOUNTS_LOADED`
     */

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * The type of event notifying that the loading of the stored accounts of a specific
         * `ProtocolProviderFactory` has finished.
         */
        const val STORED_ACCOUNTS_LOADED = 1
    }
}