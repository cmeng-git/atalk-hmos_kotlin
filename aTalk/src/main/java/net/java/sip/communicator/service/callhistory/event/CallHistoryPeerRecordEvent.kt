/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.service.callhistory.event

import net.java.sip.communicator.service.protocol.ProtocolProviderService
import java.util.*

/**
 * An event which is fired when a new call peer history record is added.
 *
 * @author Hristo Terezov
 */
class CallHistoryPeerRecordEvent(peerAddress: String?, startDate: Date?, provider: ProtocolProviderService?) : EventObject(peerAddress) {
    /**
     * Constructs new `CallHistoryPeerRecordEvent` event.
     *
     * @param peerAddress the address of the peer associated with the event.
     * @param startDate the date when the peer has been added.
     * @param provider the provider associated with the peer.
     */
    init {
        Companion.startDate = startDate
        Companion.provider = provider
    }

    /**
     * Returns the start date property of the event.
     *
     * @return the start date property of the event.
     */
    fun getStartDate(): Date? {
        return startDate
    }

    /**
     * Returns the peer address of the event.
     *
     * @return the peer address of the event.
     */
    fun getPeerAddress(): String {
        return getSource() as String
    }

    /**
     * Returns the protocol provider service associated with the event.
     *
     * @return the protocol provider service associated with the event.
     */
    fun getProvider(): ProtocolProviderService? {
        return provider
    }

    companion object {
        /**
         * Serial ID.
         */
        private const val serialVersionUID = 1L

        /**
         * The date when the call peer have started the conversation.
         */
        private var startDate: Date? = null

        /**
         * The provider associated with the call peer.
         */
        private var provider: ProtocolProviderService? = null
    }
}