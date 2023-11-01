/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import java.util.*

/**
 * Dispatched to notify interested parties that a change in our presence in the source chat room has
 * occurred. Changes may include us being kicked, join, left, etc.
 *
 * @author Emil Ivov
 * @author Stephane Remy
 */
class LocalUserChatRoomPresenceChangeEvent @JvmOverloads constructor(source: OperationSetMultiUserChat,
        chatRoom: ChatRoom, eventType: String, reason: String?, alternateAddress: String? = null) : EventObject(source) {
    /**
     * The `ChatRoom` to which the change is related.
     */
    private var chatRoom: ChatRoom

    /**
     * The type of this event.
     */
    private var eventType: String

    /**
     * An optional String indicating a possible reason as to why the event might have occurred.
     */
    private var reason: String?

    /**
     * An optional String indicating new address for the room, normally send when room is destroyed.
     */
    private var alternateAddress: String? = null

    /**
     * Creates a `ChatRoomLocalUserPresenceChangeEvent` representing that a change in local
     * participant presence in the source chat room has occurred.
     *
     * source the `OperationSetMultiUserChat`, which produced this event
     * chatRoom the `ChatRoom` that this event is about
     * eventType the type of this event.
     * reason the reason explaining why this event might have occurred
     */
    init {
        this.chatRoom = chatRoom
        this.eventType = eventType
        this.reason = reason
        this.alternateAddress = alternateAddress
    }

    /**
     * Returns the `OperationSetMultiUserChat`, where this event has occurred.
     *
     * @return the `OperationSetMultiUserChat`, where this event has occurred
     */
    fun getMultiUserChatOpSet(): OperationSetMultiUserChat {
        return getSource() as OperationSetMultiUserChat
    }

    /**
     * Returns the `ChatRoom`, that this event is about.
     *
     * @return the `ChatRoom`, that this event is about
     */
    fun getChatRoom(): ChatRoom {
        return chatRoom
    }

    /**
     * A reason string indicating a human readable reason for this event.
     *
     * @return a human readable String containing the reason for this event, or null if no
     * particular reason was specified
     */
    fun getReason(): String? {
        return reason
    }

    /**
     * Returns the type of this event which could be one of the LOCAL_USER_XXX member fields.
     *
     * @return one of the LOCAL_USER_XXX fields indicating the type of this event.
     */
    fun getEventType(): String {
        return eventType
    }

    /**
     * An optional String indicating new address for the room, normally send when room is destroyed.
     *
     * @return alternate address for the destroyed room.
     */
    fun getAlternateAddress(): String? {
        return alternateAddress
    }

    /**
     * Returns a String representation of this event.
     *
     * @return String representation of this event
     */
    override fun toString(): String {
        return "ChatRoomLocalUserPresenceChangeEvent[type=" + getEventType() + " sourceRoom=" + getChatRoom() + "]"
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Indicates that this event was triggered as a result of the local participant joining a chat room.
         */
        const val LOCAL_USER_JOINED = "LocalUserJoined"

        /**
         * Indicates that this event was triggered as a result of the local participant failed to join a chat room.
         */
        const val LOCAL_USER_JOIN_FAILED = "LocalUserJoinFailed"

        /**
         * Indicates that this event was triggered as a result of the local participant leaving a chat room.
         */
        const val LOCAL_USER_LEFT = "LocalUserLeft"

        /**
         * Indicates that this event was triggered as a result of the local participant being kicked
         * from a chat room.
         */
        const val LOCAL_USER_KICKED = "LocalUserKicked"

        /**
         * Indicates that this event was triggered as a result of the local participant being
         * disconnected from the server brutally, or ping timeout.
         */
        const val LOCAL_USER_DROPPED = "LocalUserDropped"
    }
}