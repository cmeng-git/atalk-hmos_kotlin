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

import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.ProtocolProviderService

/**
 * @author Yana Stamcheva
 * @author Damian Minkov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
interface ChatRoomProviderWrapper {
    /**
     * Returns the name of this chat room provider.
     *
     * @return the name of this chat room provider.
     */
    val name: String?
    val icon: ByteArray?
    val image: ByteArray?

    /**
     * Returns the system room wrapper corresponding to this server.
     *
     * @return the system room wrapper corresponding to this server.
     */
    val systemRoomWrapper: ChatRoomWrapper?

    /**
     * Sets the system room corresponding to this server.
     *
     * @param systemRoom the system room to set
     */
    fun setSystemRoom(systemRoom: ChatRoom)

    /**
     * Returns the protocol provider service corresponding to this  wrapper.
     *
     * @return the protocol provider service corresponding to this server wrapper.
     */
    val protocolProvider: ProtocolProviderService

    /**
     * Adds the given chat room to this chat room provider.
     *
     * @param chatRoom the chat room to add.
     */
    fun addChatRoom(chatRoom: ChatRoomWrapper)

    /**
     * Removes the given chat room from this provider.
     *
     * @param chatRoom the chat room to remove.
     */
    fun removeChatRoom(chatRoom: ChatRoomWrapper)

    /**
     * Returns `true` if the given chat room is contained in this
     * provider, otherwise - returns `false`.
     *
     * @param chatRoom the chat room to search for.
     * @return `true` if the given chat room is contained in this
     * provider, otherwise - returns `false`.
     */
    fun containsChatRoom(chatRoom: ChatRoomWrapper): Boolean

    /**
     * Returns the chat room wrapper contained in this provider that corresponds to the given chat room.
     *
     * @param chatRoom the chat room we're looking for.
     * @return the chat room wrapper contained in this provider that corresponds to the given chat room.
     */
    fun findChatRoomWrapperForChatRoom(chatRoom: ChatRoom): ChatRoomWrapper?

    /**
     * Returns the chat room wrapper contained in this provider that corresponds to the chat room with the given id.
     *
     * @param chatRoomID the id of the chat room we're looking for.
     * @return the chat room wrapper contained in this provider that corresponds to the given chat room id.
     */
    fun findChatRoomWrapperForChatRoomID(chatRoomID: String): ChatRoomWrapper?
    val chatRooms: MutableList<ChatRoomWrapper>

    /**
     * Returns the number of chat rooms contained in this provider.
     *
     * @return the number of chat rooms contained in this provider.
     */
    fun countChatRooms(): Int
    fun getChatRoom(index: Int): ChatRoomWrapper?

    /**
     * Returns the index of the given chat room in this provider.
     *
     * @param chatRoomWrapper the chat room to search for.
     * @return the index of the given chat room in this provider.
     */
    fun indexOf(chatRoomWrapper: ChatRoomWrapper): Int

    /**
     * Goes through the locally stored chat rooms list and for each
     * [ChatRoomWrapper] tries to find the corresponding server stored
     * [ChatRoom] in the specified operation set. Joins automatically all found chat rooms.
     */
    fun synchronizeProvider()

    /**
     * Gets the user data associated with this instance and a specific key.
     *
     * @param key the key of the user data associated with this instance to be retrieved
     * @return an `Object` which represents the value associated with
     * this instance and the specified `key`; `null`
     * if no association with the specified `key` exists in this instance
     */
    fun getData(key: Any?): Any?

    /**
     * Sets a user-specific association in this instance in the form of a
     * key-value pair. If the specified `key` is already associated
     * in this instance with a value, the existing value is overwritten with the specified `value`.
     *
     *
     * The user-defined association created by this method and stored in this
     * instance is not serialized by this instance and is thus only meant for runtime use.
     *
     *
     *
     * The storage of the user data is implementation-specific and is thus not
     * guaranteed to be optimized for execution time and memory use.
     *
     *
     * @param key the key to associate in this instance with the specified value
     * @param value the value to be associated in this instance with the specified `key`
     */
    fun setData(key: Any?, value: Any?)
}