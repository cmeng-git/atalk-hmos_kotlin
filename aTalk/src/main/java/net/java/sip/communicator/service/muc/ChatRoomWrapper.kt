/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.service.muc

import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.parts.Resourcepart
import java.beans.PropertyChangeListener

/**
 * The `ChatRoomWrapper` is the representation of the `ChatRoom` in the GUI. It
 * stores the information for the chat room even when the corresponding protocol provider is not connected.
 *
 * @author Yana Stamcheva
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
interface ChatRoomWrapper : Comparable<ChatRoomWrapper?> {
    /**
     * Returns the `ChatRoom` that this wrapper represents.
     *
     * @return the `ChatRoom` that this wrapper represents.
     */
    /**
     * Sets the `ChatRoom` that this wrapper represents.
     *
     * @param chatRoom the chat room
     */
    var chatRoom: ChatRoom?

    /**
     * Returns the chat room EntityBareJid.
     *
     * @return the chat room EntityBareJid
     */
    val entityBareJid: EntityBareJid?

    /**
     * Returns the chat room name.
     *
     * @return the chat room name
     */
    val chatRoomName: String?

    /**
     * Get the account User BareJid
     *
     * @return the account user BareJid
     */
    val user: BareJid?
    /**
     * Returns the unread message count for this chat room
     *
     * @return the unread message count
     */
    /**
     * Set the unread message count for this wrapper represent
     *
     * @param count unread message count
     */
    var unreadCount: Int

    /**
     * Returns the identifier of the chat room.
     *
     * @return the identifier of the chat room
     */
    val chatRoomID: String

    /**
     * Returns the parent protocol provider.
     *
     * @return the parent protocol provider
     */
    val parentProvider: ChatRoomProviderWrapper

    /**
     * Returns the protocol provider service corresponding to this  wrapper.
     *
     * @return the protocol provider service corresponding to this server wrapper.
     */
    val protocolProvider: ProtocolProviderService?
    /**
     * Returns `true` if the chat room is persistent, otherwise - returns `false `.
     *
     * @return `true` if the chat room is persistent, otherwise - returns `false`.
     */
    /**
     * Change persistence of this room.
     *
     * @param value set persistent state.
     */
    var isPersistent: Boolean?

    /**
     * Returns the user nickName as ResourcePart.
     *
     * @return the user nickName ResourcePart
     */
    val nickResource: Resourcepart?
    /**
     * Returns the user nickName.
     *
     * @return the user nickName
     */
    /**
     * Stores the nickName for the user.
     *
     * @param nickName the nickName to save
     */
    var nickName: String?
    /**
     * Returns the bookmark name.
     *
     * @return the bookmark name
     */
    /**
     * set the bookmark name.
     *
     * @param name the bookmark name to set
     */
    var bookmarkName: String?

    /**
     * Stores the password for the chat room.
     *
     * @param password the password to store
     */
    fun savePassword(password: String?)

    /**
     * Returns the password for the chat room.
     *
     * @return the password
     */
    fun loadPassword(): String?

    /**
     * Removes the saved password for the chat room.
     */
    fun removePassword()
    /**
     * Is room set to auto join on start-up.
     *
     * @return is auto joining enabled.
     */
    /**
     * Changes auto join value in configuration service.
     *
     * @param value change of auto join property.
     */
    var isAutoJoin: Boolean

    /**
     * Is access on start-up (return bookmarked may be null).
     *
     * @return if the charRoomWrapper is bookmarked.
     */
    val isBookmarked: Boolean

    /**
     * Changes bookmark value in configuration service.
     *
     * @param value change of bookmark property.
     */
    fun setBookmark(value: Boolean)
    /**
     * When access on start-up, return ttsEnable may be null. Null value in DB is considered as false
     *
     * @return true if charRoomWrapper tts is enabled.
     */
    /**
     * Change charRoomWrapper tts enable value in configuration service.
     *
     * @param value change of tts enable property.
     */
    var isTtsEnable: Boolean
    /**
     * When access on start-up, return value may be null. Null value in DB is considered as true
     *
     * @return true if charRoomWrapper Subject or Member Presence Status Change notification is enabled.
     */
    /**
     * Change charRoomWrapper subject or member status notification enable value in configuration service.
     *
     * @param value change value of property.
     */
    var isRoomStatusEnable: Boolean

    /**
     * Removes the listeners.
     */
    fun removeListeners()

    /**
     * Property changes for the room wrapper. Like join status changes.
     *
     * @param listener the listener to be notified.
     */
    fun addPropertyChangeListener(listener: PropertyChangeListener)

    /**
     * Removes property change listener.
     *
     * @param listener the listener to be notified.
     */
    fun removePropertyChangeListener(listener: PropertyChangeListener)

    companion object {
        /**
         * Property to be fired when successfully joined to chat room.
         */
        const val JOIN_SUCCESS_PROP = "Success"

        /**
         * Property to be fired when authentication failed while joining a chat room.
         */
        const val JOIN_AUTHENTICATION_FAILED_PROP = "AuthenticationFailed"

        /**
         * Property to be fired when chat room captcha verification failed.
         */
        const val JOIN_CAPTCHA_VERIFICATION_PROP = "CaptchaVerification"

        /**
         * Property to be fired when chat room requires registration and we failed while joining the chat room.
         */
        const val JOIN_REGISTRATION_REQUIRED_PROP = "RegistrationRequired"

        /**
         * Property to be fired when provider is not registered while joining a chat room.
         */
        const val JOIN_PROVIDER_NOT_REGISTERED_PROP = "ProviderNotRegistered"

        /**
         * Property to be fired when we try to join twice the same chat room.
         */
        const val JOIN_SUBSCRIPTION_ALREADY_EXISTS_PROP = "SubscriptionAlreadyExists"

        /**
         * Property to be fired when we do not have enough privileges to perform certain task
         * e.g. Only moderators are allowed to change the subject in this room
         */
        const val NOT_ENOUGH_PRIVILEGES = "NotEnoughPrivileges"

        /**
         * Property to be fired when unknown error occurred while joining a chat room.
         */
        const val JOIN_UNKNOWN_ERROR_PROP = "UnknownError"
    }
}