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

import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.ChatRoomMember
import net.java.sip.communicator.service.protocol.ConferenceDescription
import java.util.*

/**
 * Dispatched to notify interested parties that a `ChatRoomMember` has published a conference
 * description.
 *
 * @author Boris Grozev
 */
class ChatRoomConferencePublishedEvent
/**
 * Creates a new instance.
 *
 * @param chatRoom
 * The `ChatRoom` which is the source of this event.
 * @param member
 * The `ChatRoomMember` who published a `ConferenceDescription`
 * @param conferenceDescription
 * The `ConferenceDescription` that was published.
 */(
        /**
         * The type of the event. It can be `CONFERENCE_DESCRIPTION_SENT` or
         * `CONFERENCE_DESCRIPTION_RECEIVED`.
         */
        private val eventType: Int,
        /**
         * The `ChatRoom` which is the source of this event.
         */
        private val chatRoom: ChatRoom,
        /**
         * The `ChatRoomMember` who published a `ConferenceDescription`
         */
        private val member: ChatRoomMember,
        /**
         * The `ConferenceDescription` that was published.
         */
        private val conferenceDescription: ConferenceDescription) : EventObject(chatRoom) {
    /**
     * Returns the `ChatRoom` which is the source of this event.
     *
     * @return the `ChatRoom` which is the source of this event.
     */
    fun getChatRoom(): ChatRoom {
        return chatRoom
    }

    /**
     * Returns the `ChatRoomMember` who published a `ConferenceDescription`
     *
     * @return the `ChatRoomMember` who published a `ConferenceDescription`
     */
    fun getMember(): ChatRoomMember {
        return member
    }

    /**
     * Returns the `ConferenceDescription` that was published.
     *
     * @return the `ConferenceDescription` that was published.
     */
    fun getConferenceDescription(): ConferenceDescription {
        return conferenceDescription
    }

    /**
     * Returns the event type.
     *
     * @return the event type.
     */
    fun getType(): Int {
        return eventType
    }

    companion object {
        /**
         * Event type that indicates sending of conference description by the local user.
         */
        const val CONFERENCE_DESCRIPTION_SENT = 0

        /**
         * Event type that indicates receiving conference description.
         */
        const val CONFERENCE_DESCRIPTION_RECEIVED = 1
    }
}