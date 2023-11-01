/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.ChatRoomMember
import net.java.sip.communicator.service.protocol.IMessage
import java.util.*

/**
 * `ChatRoomMessageDeliveredEvent`s confirm successful delivery of an instant message.
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class ChatRoomMessageDeliveryFailedEvent
/**
 * Creates a `ChatRoomMessageDeliveryFailedEvent` indicating failure of delivery of a
 * message to the specified `ChatRoomMember` in the specified `ChatRoom`.
 *
 * @param source the `ChatRoom` in which the message was sent
 * @param to the `ChatRoomMember` that this message was sent to.
 * @param errorCode an errorCode indicating the reason of the failure.
 * @param timestamp the exact timestamp when it was determined that delivery had failed.
 * @param reason a human readable message indicating the reason for the failure or null if the reason is unknown.
 * @param message the received `IMessage`.
 */
(source: ChatRoom?,
        /**
         * The chat room member that this message has been sent to.
         */
        private val to: ChatRoomMember?,
        /**
         * An error code indicating the reason for the failure of this delivery.
         */
        private val errorCode: Int,
        /**
         * A timestamp indicating the exact date when the event occurred.
         */
        private val timestamp: Long,
        /**
         * Contains a human readable message indicating the reason for the failure or null if the reason is unknown.
         */
        private val reason: String,
        /**
         * The received `IMessage`.
         */
        private val message: IMessage) : EventObject(source) {
    /**
     * Returns a reference to the `ChatRoomMember` that the source (failed) `IMessage`
     * was sent to.
     *
     * @return a reference to the `ChatRoomMember` that the source failed `IMessage`
     * was sent to.
     */
    fun getDestinationChatRoomMember(): ChatRoomMember?
    {
        return to
    }

    /**
     * Returns the received message.
     *
     * @return the `IMessage` that triggered this event.
     */
    fun getMessage(): IMessage {
        return message
    }

    /**
     * Returns an error code describing the reason for the failure of the message delivery.
     *
     * @return an error code describing the reason for the failure of the message delivery.
     */
    fun getErrorCode(): Int {
        return errorCode
    }

    /**
     * Returns a human readable message indicating the reason for the failure or null if the reason is unknown.
     *
     * @return a human readable message indicating the reason for the failure or null if the reason is unknown.
     */
    fun getReason(): String {
        return reason
    }

    /**
     * A timestamp indicating the exact date when the event occurred (in this case it is the moment
     * when it was determined that message delivery has failed).
     *
     * @return a long indicating when the event occurred in the form of date timestamp.
     */
    fun getTimestamp(): Long {
        return timestamp
    }

    /**
     * Returns the `ChatRoom` that triggered this event.
     *
     * @return the `ChatRoom` that triggered this event.
     */
    fun getSourceChatRoom(): ChatRoom {
        return getSource() as ChatRoom
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}