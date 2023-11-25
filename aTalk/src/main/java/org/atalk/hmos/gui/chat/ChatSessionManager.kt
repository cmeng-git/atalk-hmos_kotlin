/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chat

import android.content.Intent
import android.os.Handler
import android.os.Looper
import net.java.sip.communicator.impl.contactlist.MclStorageManager
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.gui.Chat
import net.java.sip.communicator.service.gui.ChatLinkClickedListener
import net.java.sip.communicator.service.gui.event.ChatListener
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.protocol.AdHocChatRoom
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ContactResource
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging
import net.java.sip.communicator.service.protocol.OperationSetSmsMessaging
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.chat.conference.AdHocChatRoomWrapper
import org.atalk.hmos.gui.chat.conference.AdHocConferenceChatSession
import org.atalk.hmos.gui.chat.conference.ConferenceChatSession
import timber.log.Timber
import java.net.URI
import java.util.*

/**
 * The `ChatSessionManager` managing active chat sessions.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
object ChatSessionManager {
    /**
     * The chat identifier property. It corresponds to chat's meta contact UID.
     */
    const val CHAT_IDENTIFIER = "ChatIdentifier"
    const val CHAT_MODE = "ChatMode"
    const val CHAT_MSGTYPE = "ChatMessageTypeIdentifier"

    // Chat Mode variables
    const val MC_CHAT = 0
    private const val MUC_CC = 1
    private const val MUC_ADHOC = 2

    /**
     * A map of all active chats. The stored key is unique either be MetaContactID, ChatRoomID.
     * This list should be referred to as master reference for other chat associated classes
     */
    private val activeChats = LinkedHashMap<String?, ChatPanel>()

    /**
     * The list of chat listeners.
     */
    private val chatListeners = ArrayList<ChatListener>()
    private val chatSyncRoot = Any()

    /**
     * The list of active CurrentChatListener.
     */
    private val currentChatListeners = ArrayList<CurrentChatListener>()

    /**
     * The list of chat link listeners.
     */
    private val chatLinkListeners = ArrayList<ChatLinkClickedListener?>()

    /**
     * The currently selected chat identifier. It's equal to chat's `MetaContact` UID in
     * the childContact table.
     */
    private var currentChatId: String? = null

    /**
     * Last ChatTransport of the ChatSession
     */
    private var lastDescriptor: Any? = null

    /**
     * Adds an active chat.
     *
     * @param chatPanel the `ChatPanel` corresponding to the active chat
     */
    @Synchronized
    private fun addActiveChat(chatPanel: ChatPanel) {
        val chatId = chatPanel.chatSession!!.chatId
        activeChats[chatId] = chatPanel
        fireChatCreated(chatPanel as Chat)
    }

    /**
     * Removes an active chat.
     *
     * @param chatPanel the `ChatPanel` corresponding to the active chat to remove
     */
    @Synchronized
    fun removeActiveChat(chatPanel: ChatPanel?) {
        // FFR: v2.1.5 NPE for chatPanel
        if (chatPanel != null) {
            activeChats.remove(chatPanel.chatSession!!.chatId)
            fireChatClosed(chatPanel)
            chatPanel.dispose()
        }
    }

    /**
     * Removes all active chats.
     */
    @Synchronized
    fun removeAllActiveChats() {
        val chatPanels = ArrayList(activeChats.values)
        for (chatPanel in chatPanels) {
            removeActiveChat(chatPanel)
        }
    }

    /**
     * Returns the `ChatPanel` corresponding to the given chat identifier.
     *
     * @param chatKey the chat identifier
     *
     * @return the `ChatPanel` corresponding to the given chat identifier
     */
    @Synchronized
    fun getActiveChat(chatKey: String?): ChatPanel? {
        return activeChats[chatKey]
    }

    /**
     * Returns the `ChatPanel` corresponding to the given `MetaContact`.
     *
     * @param metaContact the `MetaContact` corresponding to the `ChatPanel` we're looking for
     *
     * @return the `ChatPanel` corresponding to the given chat identifier
     */
    @Synchronized
    fun getActiveChat(metaContact: MetaContact?): ChatPanel? {
        return if (metaContact != null) activeChats[metaContact.getMetaUID()] else null
    }

    /**
     * Returns the list of active chats' identifiers.
     *
     * @return the list of active chats' identifiers
     */
    @get:Synchronized
    val activeChatsIDs: MutableList<String?>
        get() = LinkedList(activeChats.keys)

    /**
     * Returns the list of active chats.
     *
     * @return the list of active chats.
     */
    @Synchronized
    fun getActiveChats(): List<Chat> {
        return LinkedList(activeChats.values)
    }

    /**
     * Sets the current chat session identifier i.e chat is focused and message pop-up is disabled.
     *
     * @param chatId the identifier of the current chat session
     */
    @Synchronized
    fun setCurrentChatId(chatId: String?) {
        // cmeng: chatId set to null when chat session end
        currentChatId = null
        lastDescriptor = null
        if (chatId != null) {
            currentChatId = chatId
            val currChat = getActiveChat(chatId)
            if (currChat != null) {
                lastDescriptor = currChat.chatSession!!.descriptor
                // Timber.d("Current chat descriptor: %s = %s", chatId, lastDescriptor);
            }

            // Notifies about new current chat session
            for (l in currentChatListeners) {
                l.onCurrentChatChanged(currentChatId!!)
            }
        }
    }

    /**
     * Return the current chat session identifier.
     *
     * @return the identifier of the current chat session
     */
    @Synchronized
    fun getCurrentChatId(): String? {
        return currentChatId
    }

    /**
     * Returns currently active `ChatPanel`.
     *
     * @return currently active `ChatPanel`.
     */
    @get:Synchronized
    val currentChatPanel: ChatPanel?
        get() = getActiveChat(currentChatId)

    /**
     * Registers new chat listener.
     *
     * @param listener the chat listener to add.
     */
    @Synchronized
    fun addChatListener(listener: ChatListener) {
        if (!chatListeners.contains(listener)) chatListeners.add(listener)
    }

    /**
     * Unregisters chat listener.
     *
     * @param listener the chat listener to remove.
     */
    @Synchronized
    fun removeChatListener(listener: ChatListener) {
        chatListeners.remove(listener)
    }

    /**
     * Adds given listener to current chat listeners list.
     *
     * @param l the listener to add to current chat listeners list.
     */
    @Synchronized
    fun addCurrentChatListener(l: CurrentChatListener) {
        if (!currentChatListeners.contains(l)) currentChatListeners.add(l)
    }

    /**
     * Removes given listener form current chat listeners list.
     *
     * @param l the listener to remove from current chat listeners list.
     */
    @Synchronized
    fun removeCurrentChatListener(l: CurrentChatListener) {
        currentChatListeners.remove(l)
    }

    /**
     * Adds `ChatLinkClickedListener`.
     *
     * @param chatLinkClickedListener the `ChatLinkClickedListener` to add.
     */
    @Synchronized
    fun addChatLinkListener(chatLinkClickedListener: ChatLinkClickedListener?) {
        if (!chatLinkListeners.contains(chatLinkClickedListener)) chatLinkListeners.add(chatLinkClickedListener)
    }

    /**
     * Removes given `ChatLinkClickedListener`.
     *
     * @param chatLinkClickedListener the `ChatLinkClickedListener` to remove.
     */
    @Synchronized
    fun removeChatLinkListener(chatLinkClickedListener: ChatLinkClickedListener?) {
        chatLinkListeners.remove(chatLinkClickedListener)
    }

    /**
     * Notifies currently registers `ChatLinkClickedListener` when the link is clicked.
     *
     * @param uri clicked link `URI`
     */
    @Synchronized
    fun notifyChatLinkClicked(uri: URI) {
        for (l in chatLinkListeners) {
            l!!.chatLinkClicked(uri)
        }
    }

    /**
     * Creates the `Intent` for starting new chat with given `MetaContact`.
     *
     * @param descriptor the contact we want to start new chat with.
     *
     * @return the `Intent` for starting new chat with given `MetaContact`.
     */
    fun getChatIntent(descriptor: Any?): Intent? {
        // A string identifier that uniquely represents this descriptor in the containing chat session database
        val chatId: String?
        val chatMode: Int

        // childContacts table = mcUid
        if (descriptor is MetaContact) {
            chatId = descriptor.getMetaUID()
            chatMode = MC_CHAT
        } else if (descriptor is Contact) {
            val accountUuid = descriptor.protocolProvider.accountID.accountUuid!!
            val contactJid = descriptor.address
            val metaUuid = MclStorageManager.getMetaUuid(accountUuid, contactJid)
            if (metaUuid != null) {
                chatId = metaUuid
                chatMode = MC_CHAT
            }
            else {
                return null
            }
        } else if (descriptor is ChatRoomWrapper) {
            chatId = descriptor.chatRoomID
            chatMode = MUC_CC
        } else if (descriptor is AdHocChatRoomWrapper) {
            chatId = descriptor.adHocChatRoomID
            chatMode = MUC_ADHOC
        } else {
            return null
        }
        val chatIntent = Intent(aTalkApp.globalContext, ChatActivity::class.java)
        chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        chatIntent.putExtra(CHAT_IDENTIFIER, chatId)
        chatIntent.putExtra(CHAT_MODE, chatMode)
        return chatIntent
    }

    /**
     * @return the Intent of the chat creation
     */
    val lastChatIntent: Intent?
        get() = if (lastDescriptor == null) null else getChatIntent(lastDescriptor)

    /**
     * Disposes of static resources held by this instance.
     */
    @Synchronized
    fun dispose() {
        chatLinkListeners.clear()
        chatListeners.clear()
        currentChatListeners.clear()
        activeChats.clear()
    }

    /**
     * Removes all active chat sessions for the given `protocolProvider`.
     *
     * @param protocolProvider protocol provider for which all chat sessions to be removed.
     */
    @Synchronized
    fun removeAllChatsForProvider(protocolProvider: ProtocolProviderService) {
        val toBeRemoved = ArrayList<ChatPanel>()
        for (chat in activeChats.values) {
            if (protocolProvider === chat.protocolProvider) {
                toBeRemoved.add(chat)
            }
        }
        for (chat in toBeRemoved) removeActiveChat(chat)
    }
    // ###########################################################
    /**
     * Finds the chat for given `Contact`.
     *
     * @param contact the contact for which active chat will be returned.
     *
     * @return active chat for given contact.
     */
    // public synchronized static ChatPanel findChatForContact(Contact contact, boolean createIfNotExists)
    @Synchronized
    fun createChatForContact(contact: Contact?): ChatPanel? {
        val newChat: ChatPanel?
        val metaContact: MetaContact?
        if (contact == null) {
            Timber.e("Failed to obtain chat instance for null contact")
            return null
        } else {
            metaContact = AndroidGUIActivator.contactListService.findMetaContactByContact(contact)
            if (metaContact == null) {
                Timber.w("No meta contact found for %s", contact)
                return null
            }
        }
        val chatId = metaContact.getMetaUID()
        newChat = createChatForChatId(chatId, MC_CHAT)
        return newChat
    }

    /**
     * Return the `ChatPanel` for the given chatId if exists; Otherwise create and return
     * new and saves it in the list of created `ChatPanel` by the called routine.
     *
     * @param chatId A string identifier that uniquely represents the caller in the containing chat
     * session database
     * @param chatMode can have one of the value as shown in below code
     *
     * @return An existing `ChatPanel` or newly created.
     */
    @Synchronized
    fun createChatForChatId(chatId: String?, chatMode: Int): ChatPanel? {
        if (chatId == null) throw NullPointerException()
        var chatPanel: ChatPanel? = null
        if (activeChats.containsKey(chatId)) {
            chatPanel = activeChats[chatId]
        } else if (chatMode == MC_CHAT) {
            val metaContact = AndroidGUIActivator.contactListService.findMetaContactByMetaUID(chatId)
            if (metaContact != null) {
                chatPanel = createChat(metaContact)
            }
        } else if (chatMode == MUC_CC) {
            val chatRoomWrapper = MUCActivator.mucService.findChatRoomWrapperFromChatRoomID(chatId, null)
            if (chatRoomWrapper != null) {
                chatPanel = createChat(chatRoomWrapper)
            }
        } else if (chatMode == MUC_ADHOC) {
            val chatRoomWrapper = MUCActivator.mucService.findChatRoomWrapperFromChatRoomID(chatId, null)
            if (chatRoomWrapper != null) {
                chatPanel = createChat(chatRoomWrapper as AdHocChatRoomWrapper)
            }
        }
        return chatPanel
    }
    // ############### Multi-User Chat Methods ############################
    /**
     * Gets the `ChatPanel` corresponding to the specified `ChatRoomWrapper` and
     * optionally creates it if it does not exist yet. Must be executed on the event dispatch thread.
     *
     * @param chatRoomWrapper the `ChatRoomWrapper` to get the corresponding `ChatPanel` of
     * @param create `true` to create a new `ChatPanel` for the specified
     * `ChatRoomWrapper` if no such `ChatPanel` exists already; otherwise, `false`
     *
     * @return the `ChatPanel` corresponding to the specified `ChatRoomWrapper` or
     * `null` if no such `ChatPanel` exists and `create` is `false`
     */
    fun getMultiChat(chatRoomWrapper: ChatRoomWrapper, create: Boolean): ChatPanel? {
        return getMultiChatInternal(chatRoomWrapper, create)
    }

    /**
     * Gets the `ChatPanel` corresponding to the specified `ChatRoomWrapper` and
     * optionally creates it if it does not exist yet.
     *
     * @param chatRoomWrapper the `ChatRoomWrapper` to get the corresponding `ChatPanel` of
     * @param create `true` to create a new `ChatPanel` for the specified
     * `ChatRoomWrapper` if no such `ChatPanel` exists already; otherwise, `false`
     *
     * @return the `ChatPanel` corresponding to the specified `ChatRoomWrapper` or
     * `null` if no such `ChatPanel` exists and `create` is `false`
     */
    private fun getMultiChatInternal(chatRoomWrapper: ChatRoomWrapper, create: Boolean): ChatPanel? {
        synchronized(chatSyncRoot) {
            var chatPanel = findChatPanelForDescriptor(chatRoomWrapper)
            if (chatPanel == null && create) chatPanel = createChat(chatRoomWrapper)
            return chatPanel
        }
    }

    /**
     * Gets the `ChatPanel` corresponding to the specified `AdHocChatRoomWrapper`
     * and optionally creates it if it does not exist yet. Must be executed on the event dispatch thread.
     *
     * @param chatRoomWrapper the `AdHocChatRoomWrapper` to get the corresponding `ChatPanel` of
     * @param create `true` to create a new `ChatPanel` for the specified
     * `AdHocChatRoomWrapper` if no such `ChatPanel` exists already; otherwise, `false`
     *
     * @return the `ChatPanel` corresponding to the specified `AdHocChatRoomWrapper`
     * or `null` if no such `ChatPanel` exists and `create` is `false`
     */
    fun getMultiChat(chatRoomWrapper: AdHocChatRoomWrapper, create: Boolean): ChatPanel? {
        return getMultiChatInternal(chatRoomWrapper, create)
    }

    /**
     * Gets the `ChatPanel` corresponding to the specified `AdHocChatRoomWrapper`
     * and optionally creates it if it does not exist yet.
     *
     * @param chatRoomWrapper the `AdHocChatRoomWrapper` to get the corresponding `ChatPanel` of
     * @param create `true` to create a new `ChatPanel` for the specified
     * `AdHocChatRoomWrapper` if no such `ChatPanel` exists already; otherwise, `false`
     *
     * @return the `ChatPanel` corresponding to the specified `AdHocChatRoomWrapper`
     * or `null` if no such `ChatPanel` exists and `create` is `false`
     */
    private fun getMultiChatInternal(chatRoomWrapper: AdHocChatRoomWrapper, create: Boolean): ChatPanel? {
        synchronized(chatSyncRoot) {
            var chatPanel = findChatPanelForDescriptor(chatRoomWrapper)
            if (chatPanel == null && create) chatPanel = createChat(chatRoomWrapper)
            return chatPanel
        }
    }

    /**
     * Gets the `ChatPanel` corresponding to the specified `ChatRoom` and optionally
     * creates it if it does not exist.
     *
     * @param chatRoom the `ChatRoom` to get the corresponding `ChatPanel` of
     * @param create `true` to create a `ChatPanel` corresponding to the specified
     * `ChatRoom` if such `ChatPanel` does not exist yet
     * @param escapedMessageID the message ID of the message that should be excluded from the history when the last
     * one is loaded in the chat
     *
     * @return the `ChatPanel` corresponding to the specified `ChatRoom`;
     * `null` if there is no such `ChatPanel` and `create` is `false`
     */
    private fun getMultiChatInternal(chatRoom: ChatRoom, create: Boolean, escapedMessageID: String?): ChatPanel? {
        synchronized(chatSyncRoot) {
            var chatPanel: ChatPanel? = null
            val chatRoomWrapper = MUCActivator.mucService.getChatRoomWrapperByChatRoom(chatRoom, create)
            if (chatRoomWrapper != null) {
                chatPanel = findChatPanelForDescriptor(chatRoomWrapper)
                if (chatPanel == null && create) chatPanel = createChat(chatRoomWrapper, escapedMessageID)
            }
            return chatPanel
        }
    }

    /**
     * Gets the `ChatPanel` corresponding to the specified `ChatRoom` and optionally
     * creates it if it does not exist. Must be executed on the event dispatch thread.
     *
     * @param chatRoom the `ChatRoom` to get the corresponding `ChatPanel` of
     * @param create `true` to create a `ChatPanel` corresponding to the specified
     * `ChatRoom` if such `ChatPanel` does not exist yet
     * @param escapedMessageID the message ID of the message that should be excluded from the history when the last
     * one is loaded in the chat
     *
     * @return the `ChatPanel` corresponding to the specified `ChatRoom`;
     * `null` if there is no such `ChatPanel` and `create` is `false`
     */
    fun getMultiChat(chatRoom: ChatRoom, create: Boolean, escapedMessageID: String?): ChatPanel? {
        return getMultiChatInternal(chatRoom, create, escapedMessageID)
    }

    /**
     * Gets the `ChatPanel` corresponding to the specified `ChatRoom` and optionally
     * creates it if it does not exist.
     *
     * @param chatRoom the `ChatRoom` to get the corresponding `ChatPanel` of
     * @param create `true` to create a `ChatPanel` corresponding to the specified
     * `ChatRoom` if such `ChatPanel` does not exist yet
     *
     * @return the `ChatPanel` corresponding to the specified `ChatRoom`;
     * `null` if there is no such `ChatPanel` and `create` is `false`
     */
    fun getMultiChat(chatRoom: ChatRoom, create: Boolean): ChatPanel? {
        return getMultiChat(chatRoom, create, null)
    }

    /**
     * Gets the `ChatPanel` corresponding to the specified `AdHocChatRoom` and
     * optionally creates it if it does not exist. Must be executed on the event dispatch thread.
     *
     * @param adHocChatRoom the `AdHocChatRoom` to get the corresponding `ChatPanel` of
     * @param create `true` to create a `ChatPanel` corresponding to the specified
     * `AdHocChatRoom` if such `ChatPanel` does not exist yet
     * @param escapedMessageID the message ID of the message that should be excluded from the history when the last
     * one is loaded in the chat
     *
     * @return the `ChatPanel` corresponding to the specified `AdHocChatRoom`;
     * `null` if there is no such `ChatPanel` and `create` is `false`
     */
    private fun getMultiChatInternal(adHocChatRoom: AdHocChatRoom, create: Boolean, escapedMessageID: String?): ChatPanel? {
        synchronized(chatSyncRoot) {
            val chatRoomList = AndroidGUIActivator.uIService.conferenceChatManager.getAdHocChatRoomList()

            // Search in the chat room's list for a chat room that correspond to the given one.
            var chatRoomWrapper = chatRoomList.findChatRoomWrapperFromAdHocChatRoom(adHocChatRoom)
            if (chatRoomWrapper == null && create) {
                val parentProvider = chatRoomList.findServerWrapperFromProvider(adHocChatRoom.getParentProvider())!!
                chatRoomWrapper = AdHocChatRoomWrapper(parentProvider, adHocChatRoom)
                chatRoomList.addAdHocChatRoom(chatRoomWrapper)
            }
            var chatPanel: ChatPanel? = null
            if (chatRoomWrapper != null) {
                chatPanel = findChatPanelForDescriptor(chatRoomWrapper)
                if (chatPanel == null && create) chatPanel = createChat(chatRoomWrapper, escapedMessageID)
            }
            return chatPanel
        }
    }

    /**
     * Gets the `ChatPanel` corresponding to the specified `AdHocChatRoom` and
     * optionally creates it if it does not exist. Must be executed on the event dispatch thread.
     *
     * @param adHocChatRoom the `AdHocChatRoom` to get the corresponding `ChatPanel` of
     * @param create `true` to create a `ChatPanel` corresponding to the specified
     * `AdHocChatRoom` if such `ChatPanel` does not exist yet
     * @param escapedMessageID the message ID of the message that should be excluded from the history when the last
     * one is loaded in the chat
     *
     * @return the `ChatPanel` corresponding to the specified `AdHocChatRoom`;
     * `null` if there is no such `ChatPanel` and `create` is `false`
     */
    fun getMultiChat(adHocChatRoom: AdHocChatRoom, create: Boolean, escapedMessageID: String?): ChatPanel? {
        return getMultiChatInternal(adHocChatRoom, create, escapedMessageID)
    }

    /**
     * Gets the `ChatPanel` corresponding to the specified `AdHocChatRoom` and
     * optionally creates it if it does not exist.
     *
     * @param adHocChatRoom the `AdHocChatRoom` to get the corresponding `ChatPanel` of
     * @param create `true` to create a `ChatPanel` corresponding to the specified
     * `AdHocChatRoom` if such `ChatPanel` does not exist yet
     *
     * @return the `ChatPanel` corresponding to the specified `AdHocChatRoom`;
     * `null` if there is no such `ChatPanel` and `create` is `false`
     */
    fun getMultiChat(adHocChatRoom: AdHocChatRoom, create: Boolean): ChatPanel? {
        return getMultiChat(adHocChatRoom, create, null)
    }
    // ==============================================
    /**
     * Gets the default `Contact` of the specified `MetaContact` if it is online;
     * otherwise, gets one of its `Contact`s which supports offline messaging.
     *
     * @param metaContact the `MetaContact` to get the default `Contact` of
     *
     * @return the default `Contact` of the specified `MetaContact` if it is online;
     * otherwise, gets one of its `Contact`s which supports offline messaging
     */
    private fun getDefaultContact(metaContact: MetaContact): Contact? {
        var defaultContact = metaContact.getDefaultContact(OperationSetBasicInstantMessaging::class.java)
        if (defaultContact == null) {
            defaultContact = metaContact.getDefaultContact(OperationSetSmsMessaging::class.java)
            if (defaultContact == null) return null
        }
        val defaultProvider = defaultContact.protocolProvider
        val defaultIM = defaultProvider.getOperationSet(OperationSetBasicInstantMessaging::class.java)
        if (defaultContact.presenceStatus.status < 1
                && (!defaultIM!!.isOfflineMessagingSupported() || !defaultProvider.isRegistered)) {
            val protoContacts = metaContact.getContacts()
            while (protoContacts.hasNext()) {
                val contact = protoContacts.next()!!
                val protoContactProvider = contact.protocolProvider
                val protoContactIM = protoContactProvider.getOperationSet(OperationSetBasicInstantMessaging::class.java)
                if (protoContactIM != null && protoContactIM.isOfflineMessagingSupported()
                        && protoContactProvider.isRegistered) {
                    defaultContact = contact
                }
            }
        }
        return defaultContact
    }

    /**
     * Creates a `ChatPanel` for the given contact and saves it in the list of created `ChatPanel`s.
     *
     * @param metaContact the `MetaContact` to create a `ChatPanel` for
     *
     * @return The `ChatPanel` newly created.
     */
    private fun createChat(metaContact: MetaContact): ChatPanel? {
        /*
         * The Contact, respectively its ChatTransport to be selected in the newly created ChatPanel; select the
         * default Contact of  metaContact if it is online or one of its Contacts which supports offline messaging
         */
        val protocolContact = getDefaultContact(metaContact) ?: return null
        val chatPanel = ChatPanel(metaContact)
        // The ContactResource to be selected in the newly created ChatPanel
        var contactResource = ContactResource.BASE_RESOURCE
        val resources = metaContact.getDefaultContact()!!.getResources()
        // cmeng: resources == null if user account not registered with server
        if (resources != null) {
            for (res in resources) {
                if (res != null) {
                    contactResource = res
                    break
                }
            }
        }
        val chatSession = MetaContactChatSession(chatPanel, metaContact, protocolContact, contactResource)
        chatPanel.chatSession = chatSession
        addActiveChat(chatPanel)
        // chatPanel.loadHistory(escapedMessageID);
        return chatPanel
    }
    /**
     * Creates a `ChatPanel` for the given `ChatRoom` and saves it in the list of
     * created `ChatPanel`s.
     *
     * @param chatRoomWrapper the `ChatRoom`, for which the chat will be created
     * @param escapedMessageID the message ID of the message that should be excluded from the history when the last
     * one is loaded in the chat.
     *
     * @return The `ChatPanel` newly created.
     */
    private fun createChat(chatRoomWrapper: ChatRoomWrapper, escapedMessageID: String? = null): ChatPanel {
        val chatPanel = ChatPanel(chatRoomWrapper)
        val chatSession = ConferenceChatSession(chatPanel, chatRoomWrapper)
        chatPanel.chatSession = chatSession
        addActiveChat(chatPanel)
        return chatPanel
    }
    /**
     * Creates a `ChatPanel` for the given `AdHocChatRoom` and saves it in the list
     * of created `ChatPanel`s.
     *
     * @param chatRoomWrapper the `AdHocChatRoom`, for which the chat will be created
     * @param escapedMessageID the message ID of the message that should be excluded from the history when the last
     * one is loaded in the chat.
     *
     * @return The `ChatPanel` newly created.
     */
    private fun createChat(chatRoomWrapper: AdHocChatRoomWrapper, escapedMessageID: String? = null): ChatPanel {
        val chatPanel = ChatPanel(chatRoomWrapper)
        val chatSession = AdHocConferenceChatSession(chatPanel, chatRoomWrapper)
        chatPanel.chatSession = chatSession
        addActiveChat(chatPanel)
        return chatPanel
    }

    /**
     * Finds the `ChatPanel` corresponding to the given chat descriptor.
     *
     * @param descriptor the chat descriptor.
     *
     * @return the `ChatPanel` corresponding to the given chat descriptor if any; otherwise, `null`
     */
    private fun findChatPanelForDescriptor(descriptor: Any): ChatPanel? {
        for (chatPanel in activeChats.values) {
            if (chatPanel.chatSession!!.descriptor == descriptor) return chatPanel
        }
        return null
    }

    /**
     * Notifies the `ChatListener`s registered with this instance that a specific `Chat` has been closed.
     *
     * @param chat the `Chat` which has been closed and which the `ChatListener`s
     * registered with this instance are to be notified about
     */
    private fun fireChatClosed(chat: Chat) {
        for (l in chatListeners) {
            l.chatClosed(chat)
        }
    }

    /**
     * Notifies the `ChatListener`s registered with this instance that a specific
     * `Chat` has been created.
     *
     * @param chat the `Chat` which has been created and which the `ChatListener`s
     * registered with this instance are to be notified about
     */
    private fun fireChatCreated(chat: Chat) {
        for (l in chatListeners) {
            l.chatCreated(chat)
        }
    }

    /**
     * Interface used to listen for currently visible chat session changes.
     */
    interface CurrentChatListener {
        /**
         * Fired when currently visible chat session changes
         *
         * @param chatId id of current chat session or `null` if there is no chat currently
         * displayed.
         */
        fun onCurrentChatChanged(chatId: String)
    }
    // ******************************************************* //
    /**
     * Runnable used as base for all that creates chat panels.
     */
    abstract class AbstractChatPanelCreateRunnable {
        /**
         * Returns the result chat panel.
         *
         * @return the result chat panel.
         */
        /**
         * The result panel.
         */
        var chatPanel: ChatPanel? = null
            get() {
                Handler(Looper.getMainLooper()).post(Runnable { field = createChatPanel() })
                return field
            }
            private set

        /**
         * The method that will create the panel.
         *
         * @return the result chat panel.
         */
        protected abstract fun createChatPanel(): ChatPanel?
    }

    /**
     * Creates chat room wrapper in event dispatch thread.
     */
    private class CreateChatRoomWrapperRunner private constructor(chatRoomWrapper: ChatRoomWrapper) : AbstractChatPanelCreateRunnable() {
        /**
         * The source chat room.
         */
        private val chatRoomWrapper: ChatRoomWrapper

        /**
         * Constructs.
         * chatRoomWrapper the `ChatRoomWrapper` to use for creating a panel.
         */
        init {
            this.chatRoomWrapper = chatRoomWrapper
        }

        /**
         * Runs on event dispatch thread.
         */
        override fun createChatPanel(): ChatPanel? {
            return getMultiChatInternal(chatRoomWrapper, true)
        }
    }

    /**
     * Creates chat room wrapper in event dispatch thread.
     */
    class CreateAdHocChatRoomWrapperRunner private constructor(chatRoomWrapper: AdHocChatRoomWrapper) : AbstractChatPanelCreateRunnable() {
        /**
         * The source chat room.
         */
        private val chatRoomWrapper: AdHocChatRoomWrapper

        /**
         * Constructs.
         *
         * chatRoomWrapper the `AdHocChatRoom`, for which the chat will be created.
         */
        init {
            this.chatRoomWrapper = chatRoomWrapper
        }

        /**
         * Runs on event dispatch thread.
         */
        override fun createChatPanel(): ChatPanel? {
            return getMultiChatInternal(chatRoomWrapper, true)
        }
    }

    /**
     * Creates chat room in event dispatch thread.
     */
    private class CreateChatRoomRunner private constructor(chatRoom: ChatRoom, escapedMessageID: String) : AbstractChatPanelCreateRunnable() {
        /**
         * The source chat room.
         */
        private val chatRoom: ChatRoom
        private val escapedMessageID: String

        /**
         * Constructs.
         *
         * chatRoom the `ChatRoom` used to create the corresponding `ChatPanel`.
         */
        init {
            this.chatRoom = chatRoom
            this.escapedMessageID = escapedMessageID
        }

        /**
         * Runs on event dispatch thread.
         */
        override fun createChatPanel(): ChatPanel? {
            return getMultiChatInternal(chatRoom, true, escapedMessageID)
        }
    }

    /**
     * Creates chat room in event dispatch thread.
     */
    private class CreateAdHocChatRoomRunner private constructor(adHocChatRoom: AdHocChatRoom, escapedMessageID: String) : AbstractChatPanelCreateRunnable() {
        /**
         * The source chat room.
         */
        private val adHocChatRoom: AdHocChatRoom
        private val escapedMessageID: String

        /**
         * Constructs.
         * adHocChatRoom the `AdHocChatRoom` used to create the corresponding `ChatPanel`.
         */
        init {
            this.adHocChatRoom = adHocChatRoom
            this.escapedMessageID = escapedMessageID
        }

        /**
         * Runs on event dispatch thread.
         */
        override fun createChatPanel(): ChatPanel? {
            return getMultiChatInternal(adHocChatRoom, true, escapedMessageID)
        }
    }
}