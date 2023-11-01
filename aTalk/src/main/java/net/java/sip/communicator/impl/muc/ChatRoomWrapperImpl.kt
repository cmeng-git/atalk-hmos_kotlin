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

import net.java.sip.communicator.service.msghistory.MessageHistoryService
import net.java.sip.communicator.service.muc.ChatRoomListChangeEvent
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.muc.MUCService
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.util.ConfigurationUtils
import net.java.sip.communicator.util.ConfigurationUtils.getChatRoomProperty
import net.java.sip.communicator.util.ConfigurationUtils.saveChatRoom
import net.java.sip.communicator.util.ConfigurationUtils.updateChatRoomProperty
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.util.event.PropertyChangeNotifier
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart
import org.jxmpp.stringprep.XmppStringprepException
import org.jxmpp.util.XmppStringUtils
import timber.log.Timber
import java.beans.PropertyChangeListener

/**
 * The `ChatRoomWrapper` is the representation of the `ChatRoom` in the GUI. It
 * stores the information for the chat room even when the corresponding protocol provider is not connected.
 *
 * @author Yana Stamcheva
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class ChatRoomWrapperImpl(
        /**
         * The protocol provider to which the corresponding chat room belongs.
         */
        override val parentProvider: ChatRoomProviderWrapper,
        /**
         * The room Name.
         */
        override val chatRoomID: String,
) : PropertyChangeNotifier(), ChatRoomWrapper {
    /**
     * The protocol provider service.
     */
    override val protocolProvider = parentProvider.protocolProvider

    /**
     * The prefix needed by the credential storage service to store the password of the chatRoom.
     * cmeng: passwordPrefix = sessionUuid
     */
    private lateinit var passwordPrefix: String

    /**
     * The property change listener for the message service.
     */
    private val propertyListener = PropertyChangeListener {
        val mhs = MUCActivator.messageHistoryService
        if (!mhs!!.isHistoryLoggingEnabled || !mhs.isHistoryLoggingEnabled(chatRoomID)) {
            MUCService.setChatRoomAutoOpenOption(protocolProvider, chatRoomID, MUCService.OPEN_ON_ACTIVITY)
        }
    }

    /**
     * Creates a `ChatRoomWrapper` by specifying the protocol provider and the name of the chatRoom.
     *
     * parentProvider the protocol provider to which the corresponding chat room belongs
     * chatRoomID the identifier of the corresponding chat room
     */
    init {
        // Request for passwordPrefix only if chatRoomID is not a serviceName
        if (chatRoomID.contains("@")) {
            passwordPrefix = ConfigurationUtils.getChatRoomPrefix(protocolProvider, chatRoomID) + ".password"
        }

        MUCActivator.configurationService!!.addPropertyChangeListener(
                MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED, propertyListener)
        MUCActivator.configurationService!!.addPropertyChangeListener(
                MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_PER_CONTACT_ENABLED_PREFIX
                        + "." + chatRoomID, propertyListener)
    }

    /**
     * Creates a `ChatRoomWrapper` by specifying the corresponding chat room.
     *
     * @param parentProvider the protocol provider to which the corresponding chat room belongs
     * @param chatRoom the chat room to which this wrapper corresponds.
     */
    constructor(parentProvider: ChatRoomProviderWrapper, chatRoom: ChatRoom) : this(parentProvider, chatRoom.getIdentifier().toString()) {
        this.chatRoom = chatRoom
    }

    /**
     * Set the unread message count for this wrapper represent
     */
    /**
     * The number of unread messages
     */
    override var unreadCount = 0

    /**
     * Returns the `ChatRoom` that this wrapper represents.
     */
    /**
     * Sets the `ChatRoom` that this wrapper represents.
     */
    /**
     * The room that is wrapped.
     */
    override var chatRoom: ChatRoom? = null

    /**
     * The participant nickName.
     */
    private var mNickName: String? = null
    /**
     * Return the bookmark name; default to conference local part if null.
     *
     * @return the bookmark name
     */
    /**
     * set the bookmark name.
     */
    override var bookmarkName: String? = null
        get() {
            return if (StringUtils.isEmpty(field)) chatRoomID.split("@")[0] else field
        }
        set(value) {
            field = value
            if (!isPersistent!!) {
                isPersistent = true
                saveChatRoom(protocolProvider, chatRoomID, chatRoomID)
            }
            updateChatRoomProperty(protocolProvider, chatRoomID, ChatRoom.CHATROOM_NAME, null)
        }
    /**
     * When access on start-up, return ttsEnable may be null.
     *
     * @return true if chatroom tts is enabled.
     */
    /**
     * Change chatroom tts enable value in configuration service.
     * Null value in DB is considered as false.
     */
    override var isTtsEnable = getChatRoomProperty(protocolProvider, chatRoomID, TTS_ENABLE).toBoolean()
        set(value) {
            if (isTtsEnable == value) return
            field = value
            if (value) {
                updateChatRoomProperty(protocolProvider, chatRoomID, TTS_ENABLE, java.lang.Boolean.toString(isTtsEnable))
            }
            else {
                updateChatRoomProperty(protocolProvider, chatRoomID, TTS_ENABLE, null)
            }
        }
    /**
     * When access on start-up, return roomStatusEnable may be null.
     *
     * @return true if chatroom tts is enabled.
     */
    /**
     * Change chatroom status enable value in configuration service. Null value in DB is considered as true.
     */
    override var isRoomStatusEnable = getChatRoomProperty(protocolProvider, chatRoomID, ROOM_STATUS_ENABLE).toBoolean()
        set(value) {
            if (isRoomStatusEnable == value) return
            field = value
            if (value) {
                updateChatRoomProperty(protocolProvider, chatRoomID, ROOM_STATUS_ENABLE, null)
            }
            else {
                updateChatRoomProperty(protocolProvider, chatRoomID, ROOM_STATUS_ENABLE,
                        java.lang.Boolean.toString(isRoomStatusEnable))
            }
        }
    /**
     * Is room set to auto join on start-up (return autoJoin may be null).
     *
     * @return is auto joining enabled.
     */
    // as the user wants to autoJoin this room and it maybe already created as non-persistent
    // we must set it persistent and store it
    /**
     * Changes auto join value in configuration service.
     */
    /**
     * As isAutoJoin can be called from GUI many times we store its value once retrieved to
     * minimize calls to configuration service. Set to null to indicate not initialize
     */
    override var isAutoJoin = getChatRoomProperty(protocolProvider, chatRoomID, AUTOJOIN_PROPERTY_NAME).toBoolean()
        set(value) {
            if (isAutoJoin == value) return
            field = value
            // as the user wants to autoJoin this room and it maybe already created as non-persistent
            // we must set it persistent and store it
            if (!isPersistent!!) {
                isPersistent = true
                saveChatRoom(protocolProvider, chatRoomID, chatRoomID)
            }
            if (value) {
                updateChatRoomProperty(protocolProvider, chatRoomID, AUTOJOIN_PROPERTY_NAME, java.lang.Boolean.toString(isAutoJoin))
            }
            else {
                updateChatRoomProperty(protocolProvider, chatRoomID, AUTOJOIN_PROPERTY_NAME, null)
            }
            MUCActivator.mucService.fireChatRoomListChangedEvent(this, ChatRoomListChangeEvent.CHAT_ROOM_CHANGED)
        }

    private var bookmark: Boolean? = null

    /**
     * By default all chat rooms are persistent from UI point of view. But we can override this
     * and force not saving it. If not overridden we query the wrapped room.
     */
    override var isPersistent: Boolean? = null
        get() {
            if (field == null) {
                if (chatRoom != null) {
                    field = chatRoom!!.isPersistent()
                }
                else
                    field = true
            }
            return field
        }

    /**
     * cmeng: Get the chatRoom name - current same as chatRoomID.
     *
     * @return the chat room name
     */
    override val chatRoomName: String
        get() = chatRoom!!.getName()

    /**
     * Get the account User BareJid
     *
     * @return the account user BareJid
     */
    override val user: BareJid
        get() = protocolProvider.connection!!.user.asBareJid()

    /**
     * Returns the chat room EntityBareJid.
     *
     * @return the chat room EntityBareJid
     */
    override val entityBareJid: EntityBareJid?
        get() {
            if (chatRoom != null) return chatRoom!!.getIdentifier()
            else {
                try {
                    return JidCreate.entityBareFrom(chatRoomID)
                } catch (e: XmppStringprepException) {
                    Timber.w("Failed to get Room EntityBareJid: %s", e.message)
                }
            }
            return null
        }

    /**
     * Stores the password for the chat room.
     *
     * @param password the password to store
     */
    override fun savePassword(password: String?) {
        MUCActivator.credentialsStorageService!!.storePassword(passwordPrefix, password)
    }

    /**
     * Returns the password for the chat room.
     *
     * @return the password
     */
    override fun loadPassword(): String? {
        return MUCActivator.credentialsStorageService!!.loadPassword(passwordPrefix)
    }

    /**
     * Removes the saved password for the chat room.
     */
    override fun removePassword() {
        MUCActivator.credentialsStorageService!!.removePassword(passwordPrefix)
    }

    /**
     * Returns the user nickName as ResourcePart.
     *
     * @return the user nickName ResourcePart
     */
    override val nickResource: Resourcepart?
        get() {
            val nickName = nickName
            try {
                return Resourcepart.from(nickName)
            } catch (e: XmppStringprepException) {
                Timber.w("Failed to get Nick resourcePart: %s", e.message)
            }
            return null
        }
    /**
     * Returns the member nickName.
     *
     * @return the member nickName
     */
    /**
     * Stores the nickName for the member.
     */
    override var nickName: String?
        get() {
            if (StringUtils.isEmpty(mNickName)) {
                mNickName = getChatRoomProperty(protocolProvider, chatRoomID, ChatRoom.USER_NICK_NAME)
                if (StringUtils.isEmpty(mNickName)) mNickName = getDefaultNickname(protocolProvider)
            }
            return mNickName
        }
        set(nickName) {
            mNickName = nickName
            if (!isPersistent!!) {
                isPersistent = true
                saveChatRoom(protocolProvider, chatRoomID, chatRoomID)
            }
            updateChatRoomProperty(protocolProvider, chatRoomID, ChatRoom.USER_NICK_NAME, nickName)
        }

    /**
     * Sets the default value in the nickname field based on pps.
     *
     * @param pps the ProtocolProviderService
     */
    private fun getDefaultNickname(pps: ProtocolProviderService?): String? {
        var nickName = AndroidGUIActivator.globalDisplayDetailsService?.getDisplayName(pps)
        if (nickName == null || nickName.contains("@")) nickName = XmppStringUtils.parseLocalpart(pps!!.accountID.accountJid)
        return nickName
    }

    /**
     * Is access on start-up (return bookmarked may be null).
     *
     * @return if the charRoomWrapper is bookmarked.
     */
    override val isBookmarked: Boolean
        get() {
            if (bookmark == null) {
                val value = getChatRoomProperty(protocolProvider, chatRoomID, BOOKMARK_PROPERTY_NAME)
                bookmark = !StringUtils.isEmpty(value) && java.lang.Boolean.parseBoolean(value)
            }
            return bookmark!!
        }

    /**
     * Changes bookmark value in configuration service.
     *
     * @param value change of bookmark property.
     */
    override fun setBookmark(value: Boolean) {
        if (isBookmarked == value) return
        bookmark = value
        // as the user wants to bookmark this room and it maybe already created as non persistent
        // we must set it persistent and store it
        if (!isPersistent!!) {
            isPersistent = true
            saveChatRoom(protocolProvider, chatRoomID, chatRoomID)
        }
        if (value) {
            updateChatRoomProperty(protocolProvider, chatRoomID, BOOKMARK_PROPERTY_NAME, java.lang.Boolean.toString(bookmark!!))
        }
        else {
            updateChatRoomProperty(protocolProvider, chatRoomID, BOOKMARK_PROPERTY_NAME, null)
        }
    }

    /**
     * Removes the listeners.
     */
    override fun removeListeners() {
        MUCActivator.configurationService!!.removePropertyChangeListener(
                MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_ENABLED, propertyListener)
        MUCActivator.configurationService!!.removePropertyChangeListener(
                MessageHistoryService.PNAME_IS_MESSAGE_HISTORY_PER_CONTACT_ENABLED_PREFIX + "."
                        + chatRoomID, propertyListener)
    }

    /**
     * Fire property change.
     *
     * @param property chatRoom property that has been changed
     */
    fun firePropertyChange(property: String?) {
        super.firePropertyChange(property, null, null)
    }

    override fun compareTo(other: ChatRoomWrapper?): Int {
        val target = other as ChatRoomWrapperImpl
        return chatRoomID.compareTo(target.chatRoomID, ignoreCase = true)
    }

    companion object {
        /**
         * The property we use to store values in configuration service.
         */
        private const val AUTOJOIN_PROPERTY_NAME = "autoJoin"

        /**
         * The property we use to store values in configuration service.
         */
        private const val BOOKMARK_PROPERTY_NAME = "bookmark"
        private const val TTS_ENABLE = "tts_Enable"

        /**
         * ChatRoom member presence status change
         */
        private const val ROOM_STATUS_ENABLE = "room_status_Enable"
    }
}