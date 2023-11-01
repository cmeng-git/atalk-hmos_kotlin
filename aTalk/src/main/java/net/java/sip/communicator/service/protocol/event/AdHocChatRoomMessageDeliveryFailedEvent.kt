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
import net.java.sip.communicator.service.protocol.IMessage
import java.util.*

/**
 * `AdHocChatRoomMessageDeliveredEvent`s confirm successful delivery of an instant message.
 *
 * @author Valentin Martinet
 */
class AdHocChatRoomMessageDeliveryFailedEvent(source: AdHocChatRoom?, to: Contact?, errorCode: Int,
        timestamp: Date?, message: IMessage?) : EventObject(source) {
    /**
     * Returns a reference to the `Contact` that the source (failed) `IMessage` was
     * sent to.
     *
     * @return a reference to the `Contact` that the source failed `IMessage` was sent
     * to.
     */
    /**
     * The ad-hoc chat room participant that this message has been sent to.
     */
    var destinationParticipant: Contact? = null
    /**
     * Returns an error code descibing the reason for the failure of the message delivery.
     *
     * @return an error code descibing the reason for the failure of the message delivery.
     */
    /**
     * An error code indicating the reason for the failure of this delivery.
     */
    var errorCode = UNKNOWN_ERROR
    /**
     * A timestamp indicating the exact date when the event ocurred (in this case it is the moment
     * when it was determined that message delivery has failed).
     *
     * @return a Date indicating when the event ocurred.
     */
    /**
     * A timestamp indicating the exact date when the event occurred.
     */
    var timestamp: Date? = null
    /**
     * Returns the received message.
     *
     * @return the `IMessage` that triggered this event.
     */
    /**
     * The received `IMessage`.
     */
    var message: IMessage? = null

    /**
     * Creates a `AdHocChatRoomMessageDeliveryFailedEvent` indicating failure of delivery of
     * a message to the specified `Contact` in the specified `AdHocChatRoom`.
     *
     * @param source
     * the `AdHocChatRoom` in which the message was sent
     * @param to
     * the `Contact` that this message was sent to.
     * @param errorCode
     * an errorCode indicating the reason of the failure.
     * @param timestamp
     * the exact Date when it was determined that delivery had failed.
     * @param message
     * the received `IMessage`.
     */
    init {
        destinationParticipant = to
        this.errorCode = errorCode
        this.timestamp = timestamp
        this.message = message
    }

    /**
     * Returns the `AdHocChatRoom` that triggered this event.
     *
     * @return the `AdHocChatRoom` that triggered this event.
     */
    val sourceChatRoom: AdHocChatRoom
        get() = getSource() as AdHocChatRoom

    companion object {
        /**
         * Set when no other error code can describe the exception that occurred.
         */
        const val UNKNOWN_ERROR = 1

        /**
         * Set when delivery fails due to a failure in network communications or a transport error.
         */
        const val NETWORK_FAILURE = 2

        /**
         * Set to indicate that delivery has failed because the provider was not registered.
         */
        const val PROVIDER_NOT_REGISTERED = 3

        /**
         * Set when delivery fails for implementation specific reasons.
         */
        const val INTERNAL_ERROR = 4

        /**
         * Set when delivery fails because we're trying to send a message to a contact that is currently
         * offline and the server does not support offline messages.
         */
        const val OFFLINE_MESSAGES_NOT_SUPPORTED = 5
    }
}