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

import net.java.sip.communicator.service.protocol.AdHocChatRoom
import net.java.sip.communicator.service.protocol.Contact

/**
 * The event that occurs when an ad-hoc chat room has been created.
 *
 * @author Valentin Martinet
 */
class AdHocChatRoomCreatedEvent
/**
 * Initializes an `AdHocChatRoomCreatedEvent` with the creator (`
 * by`) and the ad-hoc room `adHocChatRoom`.
 *
 * @param adHocChatRoom
 * the `AdHocChatRoom`
 * @param by
 * the `Contact` who created this ad-hoc room
 */(
        /**
         * The ad-hoc room that has been created.
         */
        private val adHocChatRoom: AdHocChatRoom,
        /**
         * The `Contact` who created the ad-hoc room.
         */
        private val by: Contact) {
    /**
     * Returns the `Contact` who created the room.
     *
     * @return `Contact`
     */
    fun getBy(): Contact {
        return by
    }

    /**
     * Returns the ad-hoc room concerned by this event.
     *
     * @return `AdHocChatRoom`
     */
    fun getAdHocCreatedRoom(): AdHocChatRoom {
        return adHocChatRoom
    }
}