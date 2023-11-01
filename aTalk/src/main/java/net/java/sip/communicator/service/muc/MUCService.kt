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
package net.java.sip.communicator.service.muc

import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.contactsource.SourceContact
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.ChatRoomInvitation
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.util.ConfigurationUtils.getChatRoomProperty
import net.java.sip.communicator.util.ConfigurationUtils.updateChatRoomProperty
import org.jxmpp.jid.EntityBareJid

/**
 * The MUC service provides interface for the chat rooms. It connects the GUI with the protocol.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
abstract class MUCService {
    /**
     * Fires a `ChatRoomListChangedEvent` event.
     *
     * @param chatRoomWrapper the chat room.
     * @param eventID the id of the event.
     */
    abstract fun fireChatRoomListChangedEvent(chatRoomWrapper: ChatRoomWrapper, eventID: Int)

    /**
     * Joins the given chat room with the given password and manages all the
     * exceptions that could occur during the join process.
     *
     * @param chatRoomWrapper the chat room to join.
     * @param nickName the nickname we choose for the given chat room.
     * @param password the password.
     * @param subject the subject which will be set to the room after the user join successful.
     */
    abstract fun joinChatRoom(chatRoomWrapper: ChatRoomWrapper, nickName: String, password: ByteArray?, subject: String?)

    /**
     * Creates a private chat room, by specifying the parent protocol provider and eventually,
     * the contacts invited to participate in this chat room.
     *
     * @param protocolProvider the parent protocol provider.
     * @param contacts the contacts invited when creating the chat room.
     * @param reason the reason to create
     * @param persistent is the room persistent
     * @return the `ChatRoomWrapper` corresponding to the created room
     */
    abstract fun createPrivateChatRoom(protocolProvider: ProtocolProviderService,
            contacts: Collection<String?>?, reason: String?, persistent: Boolean): ChatRoomWrapper?

    /**
     * Creates a chat room, by specifying the chat room name, the parent protocol provider and
     * eventually, the contacts invited to participate in this chat room.
     *
     * @param roomName the name of the room
     * @param protocolProvider the parent protocol provider.
     * @param contacts the contacts invited when creating the chat room.
     * @param reason the reason for the muc creation
     * @param join whether we should join the room after creating it.
     * @param persistent whether the newly created room will be persistent.
     * @param isPrivate whether the room will be private or public.
     * @param onServerRoom whether the room is already in the server room list.
     * @return the `ChatRoomWrapper` corresponding to the created room or
     * `null` if the protocol failed to create the chat room
     */
    abstract fun createChatRoom(roomName: String?, protocolProvider: ProtocolProviderService,
            contacts: Collection<String?>?, reason: String?, join: Boolean, persistent: Boolean, isPrivate: Boolean,
            onServerRoom: Boolean): ChatRoomWrapper?

    /**
     * Joins the room with the given name though the given chat room provider.
     *
     * @param chatRoomName the name of the room to join.
     * @param chatRoomProvider the chat room provider to join through.
     */
    abstract fun joinChatRoom(chatRoomName: String, chatRoomProvider: ChatRoomProviderWrapper)

    /**
     * Returns existing chat rooms for the given `chatRoomProvider`.
     *
     * @param chatRoomProvider the `ChatRoomProviderWrapper`, which chat rooms we're looking for
     * @return existing chat rooms for the given `chatRoomProvider`
     */
    abstract fun getExistingChatRooms(chatRoomProvider: ChatRoomProviderWrapper): List<String?>?

    /**
     * Returns existing chatRooms in store for the given `ProtocolProviderService`.
     *
     * @param pps the `ProtocolProviderService`, whom chatRooms we're looking for
     * @return existing chatRooms in store for the given `ProtocolProviderService`
     */
    abstract fun getExistingChatRooms(pps: ProtocolProviderService): List<String>

    /**
     * Called to accept an incoming invitation. Adds the invitation chat room
     * to the list of chat rooms and joins it.
     *
     * @param invitation the invitation to accept.
     */
    abstract fun acceptInvitation(invitation: ChatRoomInvitation)

    /**
     * Rejects the given invitation with the specified reason.
     *
     * @param multiUserChatOpSet the operation set to use for rejecting the
     * invitation
     * @param invitation the invitation to reject
     * @param reason the reason for the rejection
     */
    @Throws(OperationFailedException::class)
    abstract fun rejectInvitation(multiUserChatOpSet: OperationSetMultiUserChat,
            invitation: ChatRoomInvitation, reason: String?)

    /**
     * Leaves the given chat room.
     *
     * @param chatRoomWrapper the chat room to leave.
     * @return `ChatRoomWrapper` instance associated with the chat room.
     */
    abstract fun leaveChatRoom(chatRoomWrapper: ChatRoomWrapper): ChatRoomWrapper?

    /**
     * Finds `ChatRoomWrapper` instance associated with the given source contact.
     *
     * @param contact the contact.
     * @return `ChatRoomWrapper` instance associated with the given source contact.
     */
    abstract fun findChatRoomWrapperFromSourceContact(contact: SourceContact): ChatRoomWrapper?

    /**
     * Searches for chat room wrapper in chat room list by chat room.
     *
     * @param chatRoom the chat room.
     * @param create if `true` and the chat room wrapper is not found new chatRoomWrapper is created.
     * @return found chat room wrapper or the created chat room wrapper.
     */
    abstract fun getChatRoomWrapperByChatRoom(chatRoom: ChatRoom, create: Boolean): ChatRoomWrapper?

    /**
     * Finds the `ChatRoomWrapper` instance associated with the
     * chat room.
     *
     * @param chatRoomID the id of the chat room.
     * @param pps the provider of the chat room.
     * @return the `ChatRoomWrapper` instance.
     */
    abstract fun findChatRoomWrapperFromChatRoomID(chatRoomID: String, pps: ProtocolProviderService?): ChatRoomWrapper?

    /**
     * Goes through the locally stored chat rooms list and for each
     * [ChatRoomWrapper] tries to find the corresponding server stored
     * [ChatRoom] in the specified operation set. Joins automatically all found chat rooms.
     *
     * @param protocolProvider the protocol provider for the account to synchronize
     * @param opSet the multi user chat operation set, which give us access to
     * chat room server
     */
    abstract fun synchronizeOpSetWithLocalContactList(
            protocolProvider: ProtocolProviderService, opSet: OperationSetMultiUserChat)

    /**
     * Returns an iterator to the list of chat room providers.
     *
     * @return an iterator to the list of chat room providers.
     */
    abstract val chatRoomProviders: List<ChatRoomProviderWrapper?>?

    /**
     * Removes the given `ChatRoom` from the list of all chat rooms.
     *
     * @param chatRoomWrapper the `ChatRoomWrapper` to remove
     */
    abstract fun removeChatRoom(chatRoomWrapper: ChatRoomWrapper?)

    /**
     * Adds a ChatRoomProviderWrapperListener to the listener list.
     *
     * @param listener the ChatRoomProviderWrapperListener to be added
     */
    abstract fun addChatRoomProviderWrapperListener(listener: ChatRoomProviderWrapperListener)

    /**
     * Removes the ChatRoomProviderWrapperListener to the listener list.
     *
     * @param listener the ChatRoomProviderWrapperListener to be removed
     */
    abstract fun removeChatRoomProviderWrapperListener(listener: ChatRoomProviderWrapperListener)

    /**
     * Destroys the given `ChatRoom` from the list of all chat rooms.
     *
     * @param chatRoomWrapper the `ChatRoomWrapper` to be destroyed.
     * @param reason the reason for destroying.
     * @param alternateAddress the entityBareJid of the chatRoom.
     */
    abstract fun destroyChatRoom(chatRoomWrapper: ChatRoomWrapper, reason: String?, alternateAddress: EntityBareJid?)

    /**
     * Returns the `ChatRoomProviderWrapper` that correspond to the
     * given `ProtocolProviderService`. If the list doesn't contain a
     * corresponding wrapper - returns null.
     *
     * @param protocolProvider the protocol provider that we're looking for
     * @return the `ChatRoomProvider` object corresponding to
     * the given `ProtocolProviderService`
     */
    abstract fun findServerWrapperFromProvider(protocolProvider: ProtocolProviderService): ChatRoomProviderWrapper?

    /**
     * Returns the `ChatRoomWrapper` that correspond to the given `ChatRoom`. If the list
     * of chat rooms doesn't contain a corresponding wrapper - returns null.
     *
     * @param chatRoom the `ChatRoom` that we're looking for
     * @return the `ChatRoomWrapper` object corresponding to the given `ChatRoom`
     */
    abstract fun findChatRoomWrapperFromChatRoom(chatRoom: ChatRoom): ChatRoomWrapper?

    /**
     * Opens a chat window for the chat room.
     *
     * @param chatRoomWrapper the chat room.
     */
    abstract fun openChatRoom(chatRoomWrapper: ChatRoomWrapper?)

    /**
     * Returns instance of the `ServerChatRoomContactSourceService` contact source.
     *
     * @return instance of the `ServerChatRoomContactSourceService` contact source.
     */
    abstract fun getServerChatRoomsContactSourceForProvider(pps: ChatRoomProviderWrapper?): ContactSourceService?

    /**
     * Returns `true` if the contact is `ChatRoomSourceContact`
     *
     * @param contact the contact
     * @return `true` if the contact is `ChatRoomSourceContact`
     */
    abstract fun isMUCSourceContact(contact: SourceContact): Boolean

    companion object {
        /**
         * The configuration property to disable
         */
        const val DISABLED_PROPERTY = "muc.MUC_SERVICE_DISABLED"

        /**
         * Key for auto-open configuration entry.
         */
        var AUTO_OPEN_CONFIG_KEY = "openAutomatically"

        /**
         * The value for chat room configuration property to open automatically on activity
         */
        var OPEN_ON_ACTIVITY = "on_activity"

        /**
         * The value for chat room configuration property to open automatically on message
         */
        var OPEN_ON_MESSAGE = "on_message"

        /**
         * The value for chat room configuration property to open automatically on important messages.
         */
        var OPEN_ON_IMPORTANT_MESSAGE = "on_important_message"

        /**
         * The default for chat room auto-open behaviour.
         */
        var DEFAULT_AUTO_OPEN_BEHAVIOUR = OPEN_ON_MESSAGE

        /**
         * Remove the newly create room if failed to join
         */
        const val REMOVE_ROOM_ON_FIRST_JOIN_FAILED = "gui.chatroomslist.REMOVE_ROOM_ON_FIRST_JOIN_FAILED"

        /**
         * Map for the auto open configuration values and their text representation
         */
        var autoOpenConfigValuesTexts = HashMap<String, String>()

        init {
            autoOpenConfigValuesTexts[OPEN_ON_ACTIVITY] = "service.gui.OPEN_ON_ACTIVITY"
            autoOpenConfigValuesTexts[OPEN_ON_MESSAGE] = "service.gui.OPEN_ON_MESSAGE"
            autoOpenConfigValuesTexts[OPEN_ON_IMPORTANT_MESSAGE] = "service.gui.OPEN_ON_IMPORTANT_MESSAGE"
        }

        /**
         * Sets chat room open automatically property
         *
         * @param pps the provider
         * @param chatRoomId the chat room id
         * @param value the new value for the property
         */
        fun setChatRoomAutoOpenOption(pps: ProtocolProviderService, chatRoomId: String, value: String?) {
            updateChatRoomProperty(pps, chatRoomId, AUTO_OPEN_CONFIG_KEY, value)
        }

        /**
         * Returns the value of the chat room open automatically property
         *
         * @param pps the provider
         * @param chatRoomId the chat room id
         * @return the value of the chat room open automatically property
         */
        fun getChatRoomAutoOpenOption(pps: ProtocolProviderService, chatRoomId: String): String? {
            return getChatRoomProperty(pps, chatRoomId, AUTO_OPEN_CONFIG_KEY)
        }

        /**
         * Determines whether a specific `ChatRoom` is private i.e.
         * represents a one-to-one conversation which is not a channel. Since the
         * interface [ChatRoom] does not expose the private property, an
         * heuristic is used as a workaround: (1) a system `ChatRoom` is
         * obviously not private and (2) a `ChatRoom` is private if it
         * has only one `ChatRoomMember` who is not the local user.
         *
         * @param chatRoom the `ChatRoom` to be determined as private or not
         * @return `true` if the specified `ChatRoom` is private; otherwise, `false`
         */
        fun isPrivate(chatRoom: ChatRoom): Boolean {
            if (!chatRoom.isSystem() && chatRoom.isJoined() && chatRoom.getMembersCount() == 1) {
                val nickResource = chatRoom.getUserNickname()
                if (nickResource != null) {
                    val nickName = nickResource.toString()
                    for (member in chatRoom.getMembers()) if (nickName == member.getNickName()) return false
                    return true
                }
            }
            return false
        }

        /**
         * Returns the multi user chat operation set for the given protocol provider.
         *
         * @param protocolProvider The protocol provider for which the multi user chat operation set is about.
         * @return OperationSetMultiUserChat The telephony operation set for the given protocol provider.
         */
        fun getMultiUserChatOpSet(protocolProvider: ProtocolProviderService): OperationSetMultiUserChat? {
            val opSet = protocolProvider.getOperationSet(OperationSetMultiUserChat::class.java)
            return if (opSet != null) opSet else null
        }
    }
}