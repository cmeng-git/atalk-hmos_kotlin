/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.muc

import android.text.TextUtils
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import net.java.sip.communicator.service.protocol.ProtocolIcon
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import java.util.*

/**
 * @author Yana Stamcheva
 * @author Damian Minkov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class ChatRoomProviderWrapperImpl(
        /**
         * Returns the protocol provider service corresponding to this server wrapper.
         *
         * @return the protocol provider service corresponding to this server wrapper.
         */
        override val protocolProvider: ProtocolProviderService) : ChatRoomProviderWrapper {

    /**
     * Returns the system room wrapper corresponding to this server.
     *
     * @return the system room wrapper corresponding to this server.
     */
    override val systemRoomWrapper: ChatRoomWrapper
    private val chatRoomsOrderedCopy = LinkedList<ChatRoomWrapper>()

    /**
     * The user-specific key-value associations stored in this instance.
     *
     *
     * Like the Widget implementation of Eclipse SWT, the storage type takes into account that
     * there are likely to be many `MetaContactGroupImpl` instances and
     * `Map`s are thus likely to impose increased memory use. While an array may
     * very well perform worse than a `Map` with respect to search, the mechanism of
     * user-defined key-value associations explicitly states that it is not guaranteed to be
     * optimized for any particular use and only covers the most basic cases and
     * performance-savvy code will likely implement a more optimized solution anyway.
     *
     */
    private var data: Array<Any?>? = null

    /**
     * Creates an instance of `ChatRoomProviderWrapper` by specifying the protocol
     * provider, corresponding to the multi user chat account.
     *
     * protocolProvider protocol provider, corresponding to the multi user chat account.
     */
    init {
        val accountIdService = protocolProvider.accountID.service!!
        systemRoomWrapper = ChatRoomWrapperImpl(this, accountIdService)
    }

    /**
     * Returns the name of this chat room provider.
     *
     * @return the name of this chat room provider.
     */
    override val name: String
        get() = protocolProvider.protocolDisplayName

    override val icon: ByteArray?
        get() = protocolProvider.protocolIcon.getIcon(ProtocolIcon.ICON_SIZE_64x64)

    override val image: ByteArray?
        get() {
            var logoImage: ByteArray? = null
            val protocolIcon = protocolProvider.protocolIcon
            if (protocolIcon.isSizeSupported(ProtocolIcon.ICON_SIZE_64x64)) {
                logoImage = protocolIcon.getIcon(ProtocolIcon.ICON_SIZE_64x64)
            }
            else {
                if (protocolIcon.isSizeSupported(ProtocolIcon.ICON_SIZE_48x48)) logoImage = protocolIcon.getIcon(ProtocolIcon.ICON_SIZE_48x48)
            }
            return logoImage
        }

    /**
     * Sets the system room corresponding to this server.
     *
     * @param systemRoom the system room to set
     */
    override fun setSystemRoom(systemRoom: ChatRoom) {
        systemRoomWrapper.chatRoom = systemRoom
    }

    /**
     * Adds the given chat room to this chat room provider.
     *
     * @param chatRoom the chat room to add.
     */
    override fun addChatRoom(chatRoom: ChatRoomWrapper) {
        chatRoomsOrderedCopy.add(chatRoom)
    }

    /**
     * Removes the given chat room from this provider.
     *
     * @param chatRoom the chat room to remove.
     */
    override fun removeChatRoom(chatRoom: ChatRoomWrapper) {
        chatRoomsOrderedCopy.remove(chatRoom)
    }

    /**
     * Returns `true` if the given chat room is contained in this provider, otherwise - returns `false`.
     *
     * @param chatRoom the chat room to search for.
     * @return `true` if the given chat room is contained in this provider, otherwise - returns `false`.
     */
    override fun containsChatRoom(chatRoom: ChatRoomWrapper): Boolean {
        synchronized(chatRoomsOrderedCopy) { return chatRoomsOrderedCopy.contains(chatRoom) }
    }

    /**
     * Returns the chat room wrapper contained in this provider that corresponds to the given chat room.
     *
     * @param chatRoom the chat room we're looking for.
     * @return the chat room wrapper contained in this provider that corresponds to the given chat room.
     */
    override fun findChatRoomWrapperForChatRoom(chatRoom: ChatRoom): ChatRoomWrapper? {
        return findChatRoomWrapperForChatRoomID(chatRoom.getName())
    }

    /**
     * Returns the chat room wrapper contained in this provider that corresponds to the chat room with the given id.
     *
     * @param chatRoomID the id of the chat room we're looking for.
     * @return the chat room wrapper contained in this provider that corresponds to the given chat room id.
     */
    override fun findChatRoomWrapperForChatRoomID(chatRoomID: String): ChatRoomWrapper? {
        // Compare ids, cause saved chatRooms don't have ChatRoom object but Id's are the same.
        for (chatRoomWrapper in chatRoomsOrderedCopy) {
            if (chatRoomWrapper.chatRoomID == chatRoomID) {
                return chatRoomWrapper
            }
        }
        return null
    }

    override val chatRooms: MutableList<ChatRoomWrapper>
        get() = chatRoomsOrderedCopy

    /**
     * Returns the number of chat rooms contained in this provider.
     *
     * @return the number of chat rooms contained in this provider.
     */
    override fun countChatRooms(): Int {
        return chatRoomsOrderedCopy.size
    }

    override fun getChatRoom(index: Int): ChatRoomWrapper {
        return chatRoomsOrderedCopy[index]
    }

    /**
     * Returns the index of the given chat room in this provider.
     *
     * @param chatRoomWrapper the chat room to search for.
     * @return the index of the given chat room in this provider.
     */
    override fun indexOf(chatRoomWrapper: ChatRoomWrapper): Int {
        return chatRoomsOrderedCopy.indexOf(chatRoomWrapper)
    }

    /**
     * Implements [ChatRoomProviderWrapper.getData].
     *
     * @return the data value corresponding to the given key
     */
    override fun getData(key: Any?): Any? {
        if (key == null) throw NullPointerException("key")
        val index = dataIndexOf(key)
        return if (index == -1) null else data!![index + 1]
    }

    /**
     * Implements [ChatRoomProviderWrapper.setData].
     *
     * @param key the of the data
     * @param value the value of the data
     */
    override fun setData(key: Any?, value: Any?) {
        if (key == null) throw NullPointerException("key")
        val index = dataIndexOf(key)
        if (index == -1) {
            /*
             * If value is null, remove the association with key (or just don't add it).
             */
            if (data == null) {
                if (value != null) data = arrayOf(key, value)
            }
            else if (value == null) {
                val length = data!!.size - 2
                data = if (length > 0) {
                    val newData = arrayOfNulls<Any>(length)
                    System.arraycopy(data!!, 0, newData, 0, index)
                    System.arraycopy(data!!, index + 2, newData, index, length - index)
                    newData
                }
                else null
            }
            else {
                var length = data!!.size
                val newData = arrayOfNulls<Any>(length + 2)
                System.arraycopy(data!!, 0, newData, 0, length)
                data = newData
                data!![length++] = key
                data!![length] = value
            }
        }
        else data!![index + 1] = value
    }

    /**
     * Determines the index in `#data` of a specific key.
     *
     * @param key the key to retrieve the index in `#data` of
     * @return the index in `#data` of the specified `key` if it is contained;
     * `-1` if `key` is not contained in `#data`
     */
    private fun dataIndexOf(key: Any): Int {
        if (data != null) {
            var index = 0
            while (index < data!!.size) {
                if (key == data!![index]) return index
                index += 2
            }
        }
        return -1
    }

    /**
     * Goes through the locally stored chat rooms list and for each [ChatRoomWrapper]
     * tries to find the corresponding server stored [ChatRoom] in the specified operation set.
     * Joins automatically if enabled for all found chat rooms.
     */
    override fun synchronizeProvider() {
        val groupChatOpSet = protocolProvider.getOperationSet(OperationSetMultiUserChat::class.java)
        for (chatRoomWrapperCopy in chatRoomsOrderedCopy) {
            var chatRoomWrapper = chatRoomWrapperCopy
            val chatRoom = groupChatOpSet!!.findRoom(chatRoomWrapper.entityBareJid)
            if (chatRoom != null) {
                chatRoomWrapper.chatRoom = chatRoom
            }

            if (chatRoomWrapper.isAutoJoin) {
                // For non-existent chat room, we must create it before joining
                if (chatRoom == null) {
                    chatRoomWrapper = MUCActivator.mucService.createChatRoom(chatRoomWrapper,
                            "auto joined", false, persistent = false, isPrivate = true)
                }
                val nickName = chatRoomWrapper.nickName
                val pwd = chatRoomWrapper.loadPassword()
                val password = if (TextUtils.isEmpty(pwd)) null else pwd!!.toByteArray()
                MUCActivator.mucService.joinChatRoom(chatRoomWrapper, nickName, password)
            }
        }
    }
}