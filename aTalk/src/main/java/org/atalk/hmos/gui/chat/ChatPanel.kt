/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chat

import android.content.Intent
import android.text.TextUtils
import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.impl.protocol.jabber.ChatRoomMemberJabberImpl
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.filehistory.FileRecord
import net.java.sip.communicator.service.gui.Chat
import net.java.sip.communicator.service.gui.ChatLinkClickedListener
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.IMessage
import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest
import net.java.sip.communicator.service.protocol.OperationSetAdHocMultiUserChat
import net.java.sip.communicator.service.protocol.OperationSetChatStateNotifications
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageReceivedEvent
import net.java.sip.communicator.service.protocol.event.ChatStateNotificationsListener
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.MessageDeliveryFailedEvent
import net.java.sip.communicator.service.protocol.event.MessageListener
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent
import net.java.sip.communicator.util.ConfigurationUtils
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.actionbar.ActionBarUtil
import org.atalk.hmos.gui.chat.conference.ConferenceChatSession
import org.atalk.hmos.plugin.textspeech.TTSService
import org.atalk.persistance.FileBackend
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smackx.mam.MamManager
import org.jivesoftware.smackx.mam.MamManager.*
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import timber.log.Timber
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The `ChatPanel`, `ChatActivity`, `ChatController` and `ChatFragment`
 * together formed the frontend interface to the user, where users can write and send messages
 * (ChatController), view received and sent messages (ChatFragment). A ChatPanel is created for a
 * contact or for a group of contacts in case of a chat conference. There is always one default
 * contact for the chat, which is the first contact which was added to the chat. Each "Chat GUI'
 * constitutes a fragment page access/scroll to view via the ChatPagerAdapter.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
// An object reference containing either a MetaContact or ChatRoomWrapper
class ChatPanel(
        val descriptor: Any,
) : Chat, MessageListener {
    /**
     * The underlying `MetaContact`, we're chatting with.
     */
    private var mMetaContact: MetaContact? = null
    /**
     * Stores current chatType.
     *
     *chatType selected chatType e.g. MSGTYPE_NORMAL.
     */
    /**
     * The chatType for which the message will be send for method not using Transform process
     * i.e. OTR. This is also the master copy where other will update or refer to.
     * The state may also be change by the under lying signal protocol based on the current
     * signalling condition.
     */
    var chatType = 0

    /**
     * The current chat transport.
     */
    private lateinit var mCurrentChatTransport: ChatTransport

    /**
     * The chat history filter for retrieving history messages.
     */
    private val chatHistoryFilter = ChatSession.chatHistoryFilter

    /**
     * msgCache: Messages cache used by this session; to cache any received message when the session
     * chatFragment has not yet opened once. msgCache is the mirror image of the DisplayMessages show
     * in ChatSession UI, and get updated with history messages retrieved by user. This msgCache is
     * always return when user resume the chatSession (chatListAdapter is empty). There the contents
     * must kept up to date with the ChatSession UI messages.
     *
     * Important: when historyLog is disabled i.e. all messages exchanges are only saved in msgCache.
     *
     * Use CopyOnWriteArrayList instead to avoid ChatFragment#prependMessages ConcurrentModificationException
     * private List<ChatMessage> msgCache = new LinkedList<>();
    </ChatMessage> */
    private var msgCache: MutableList<ChatMessage> = CopyOnWriteArrayList()

    /**
     * Synchronization root for messages cache.
     */
    private val cacheLock = Any()
    private var mLastMsgFetchDate: Date? = null

    // Chat identifier is the same as SessionUuid in DB; uniquely identify this chat session.
    private lateinit var mChatId: String

    /**
     * Flag indicates if the history has been loaded (it must be done only once;
     * and all the next messages are cached through the listeners mechanism).
     */
    private var historyLoaded = false

    /**
     * Flag indicates that mam access has been attempted when chat session if first launched.
     * Flag is set to true when user is registered and mam retrieval is attempted.
     */
    private var mamChecked = false

    /**
     * Blocked caching of the next new message if sent via normal sendMessage().
     * Otherwise there will have duplicated display messages
     */
    private var cacheBlocked = false
    private var cacheUpdated: Boolean? = null

    /**
     * Registered chatFragment to be informed of any messageReceived event
     */
    private val msgListeners = ArrayList<ChatSessionListener?>()

    /**
     * Current chatSession TTS is active if true
     */
    var isChatTtsEnable = false
        private set

    private var ttsDelay = 1200
    /**
     * Returns recently edited message text.
     *
     * @return recently edited message text.
     */
    /**
     * Stores recently edited message text.
     *
     *editedText recently edited message text.
     */
    /**
     * Field used by the `ChatController` to keep track of last edited message content.
     */
    var editedText: String? = null
    /**
     * Gets the UID of recently corrected message.
     *
     * @return the UID of recently corrected message.
     */
    /**
     * Stores the UID of recently corrected message.
     *
     *correctionUID the UID of recently corrected message.
     */
    /**
     * Field used by the `ChatController` (input text) to remember if user was
     * making correction to the earlier sent message.
     */
    var correctionUID: String? = null

    /**
     * Creates a chat session with the given MetaContact or ChatRoomWrapper.
     *
     *descriptor the transport object we're chatting with
     */
    init {
        mMetaContact = if (descriptor is MetaContact) {
            descriptor
        } else {
            null
        }
    }

    /**
     * Current chat session type: mChatSession can either be one of the following:
     * MetaContactChatSession, ConferenceChatSession or AdHocConferenceChatSession
     */
    var chatSession: ChatSession? = null
        /**
         * Sets the chat session to associate to this chat panel.
         */
        set(chatSession) {
            if (field != null) {
                // remove any old listener if present.
                mCurrentChatTransport.removeInstantMessageListener(this)
                mCurrentChatTransport.removeSmsMessageListener(this)
            }

            field = chatSession
            mChatId = chatSession!!.chatId
            mCurrentChatTransport = chatSession.currentChatTransport!!
            mCurrentChatTransport.addInstantMessageListener(this)
            mCurrentChatTransport.addSmsMessageListener(this)
            updateChatTtsOption()
        }

    /**
     * Returns the protocolProvider of the user associated with this chat panel.
     *
     * @return the protocolProvider associated with this chat panel.
     */
    val protocolProvider: ProtocolProviderService
        get() = mCurrentChatTransport.protocolProvider

    /**
     * Returns the underlying `MetaContact`, we're chatting with in metaContactChatSession
     *
     * @return the underlying `MetaContact`, we're chatting with
     */
    val metaContact: MetaContact?
        get() = mMetaContact

    /**
     * Check if current chat is set to OMEMO crypto mode
     *
     * @return return `true` if OMEMO crypto chat is selected.
     */
    val isOmemoChat: Boolean
        get() = chatType == ChatFragment.MSGTYPE_OMEMO || chatType == ChatFragment.MSGTYPE_OMEMO_UA || chatType == ChatFragment.MSGTYPE_OMEMO_UT

    /**
     * Check if current chat is set to OTR crypto mode
     *
     * @return return `true` if OMEMO crypto chat is selected
     */
    val isOTRChat: Boolean
        get() = chatType == ChatFragment.MSGTYPE_OTR || chatType == ChatFragment.MSGTYPE_OTR_UA

    /**
     * Runs clean-up for associated resources which need explicit disposal (e.g.
     * listeners keeping this instance alive because they were added to the
     * model which operationally outlives this instance).
     */
    fun dispose() {
        mCurrentChatTransport.removeInstantMessageListener(this)
        mCurrentChatTransport.removeSmsMessageListener(this)
        chatSession!!.dispose()
    }

    /**
     * Adds the given `ChatSessionListener` to listen for message events in this chat session.
     *
     *msgListener the `ChatSessionListener` to add
     */
    fun addMessageListener(msgListener: ChatSessionListener) {
        if (!msgListeners.contains(msgListener)) msgListeners.add(msgListener)
    }

    /**
     * Removes the given `ChatSessionListener` from this chat session.
     *
     *msgListener the `ChatSessionListener` to remove
     */
    fun removeMessageListener(msgListener: ChatSessionListener) {
        msgListeners.remove(msgListener)
    }

    /**
     * Adds the given `ChatStateNotificationsListener` to listen for chat state events
     * in this chat session (Contact or ChatRoom).
     *
     *l the `ChatStateNotificationsListener` to add
     */
    fun addChatStateListener(l: ChatStateNotificationsListener) {
        mCurrentChatTransport.protocolProvider.getOperationSet(OperationSetChatStateNotifications::class.java)?.addChatStateNotificationsListener(l)
    }

    /**
     * Removes the given `ChatStateNotificationsListener` from this chat session (Contact or ChatRoom)..
     *
     *l the `ChatStateNotificationsListener` to remove
     */
    fun removeChatStateListener(l: ChatStateNotificationsListener) {
        mCurrentChatTransport.protocolProvider.getOperationSet(OperationSetChatStateNotifications::class.java)?.removeChatStateNotificationsListener(l)
    }

    /**
     * Adds the given `ContactPresenceStatusListener` to listen for message events
     * in this chat session.
     *
     *l the `ContactPresenceStatusListener` to add
     */
    fun addContactStatusListener(l: ContactPresenceStatusListener) {
        if (mMetaContact == null) return
        val protoContacts = mMetaContact!!.getContacts()
        while (protoContacts.hasNext()) {
            val protoContact = protoContacts.next()!!
            protoContact.protocolProvider.getOperationSet(OperationSetPresence::class.java)?.addContactPresenceStatusListener(l)
        }
    }

    /**
     * Removes the given `ContactPresenceStatusListener` from this chat session.
     *
     *l the `ContactPresenceStatusListener` to remove
     */
    fun removeContactStatusListener(l: ContactPresenceStatusListener) {
        if (mMetaContact == null) return
        val protoContacts = mMetaContact!!.getContacts()
        while (protoContacts.hasNext()) {
            val protoContact = protoContacts.next()!!
            protoContact.protocolProvider.getOperationSet(OperationSetPresence::class.java)?.removeContactPresenceStatusListener(l)
        }
    }

    /**
     * Returns a collection of newly fetched last messages from store; merged with msgCache.
     *
     * @return a collection of last messages.
     */
    fun getHistory(init: Boolean): List<ChatMessage> {
        // If chatFragment is initializing (or onResume) AND we have already cached the messages
        // i.e. (historyLoaded == true), then just return the current msgCache content.
        if (init && historyLoaded) {
            return msgCache
        }

        // If the MetaHistoryService is not registered we have nothing to do here.
        // The history store could be "disabled" by the user via Chat History Logging option.
        val metaHistory = AndroidGUIActivator.metaHistoryService
                ?: return msgCache

        // descriptor can either be metaContact or chatRoomWrapper=>ChatRoom, from whom the history to be loaded
        var descriptor = descriptor
        if (descriptor is ChatRoomWrapper) {
            descriptor = descriptor.chatRoom!!
        }
        val history: Collection<Any>
        // First time access: mamQuery the server mam records and save them into sql database;
        // only then read in last HISTORY_CHUNK_SIZE of history messages from database
        if (msgCache.isEmpty()) {
            mamChecked = mamQuery(descriptor)
            history = metaHistory.findLast(chatHistoryFilter, descriptor, HISTORY_CHUNK_SIZE)
        } else {
            // Update Message History database if mamRetrieved is fase and user is now registered
            // Note: this only update the DB but not the chat session UI.
            if (!mamChecked) {
                mamChecked = mamQuery(descriptor)
            }
            if (mLastMsgFetchDate == null) {
                mLastMsgFetchDate = msgCache[0].date
            }
            history = metaHistory.findLastMessagesBefore(chatHistoryFilter, descriptor, mLastMsgFetchDate!!, HISTORY_CHUNK_SIZE)

            // cmeng (20221229): was introduced in v.2.6; msgCache should have been properly updated now,
            // so omit and simplify mergeCachedMessage process.
            // retrieve the history records from DB again; need to take care when:
            // a. any history record is deleted;
            // b. Message delivery receipt status;
            // All currently are handle in updateCacheMessage(); do this just in case implementation not complete
            // history.addAll(metaHistory.findByStartDate(chatHistoryFilter, descriptor, lastMsgCacheDate));
        }
        val msgHistory = ArrayList<ChatMessage>()
        if (!history.isEmpty()) {
            // Convert events into messages for display in chat
            for (o in history) {
                when (o) {
                    is MessageDeliveredEvent -> {
                        msgHistory.add(ChatMessageImpl.getMsgForEvent(o))
                    }
                    is MessageReceivedEvent -> {
                        msgHistory.add(ChatMessageImpl.getMsgForEvent(o))
                    }
                    is ChatRoomMessageDeliveredEvent -> {
                        msgHistory.add(ChatMessageImpl.getMsgForEvent(o))
                    }
                    is ChatRoomMessageReceivedEvent -> {
                        msgHistory.add(ChatMessageImpl.getMsgForEvent(o))
                    }
                    is FileRecord -> {
                        msgHistory.add(ChatMessageImpl.getMsgForEvent(o))
                    }
                    else -> {
                        Timber.e("Unexpected event in history: %s", o)
                    }
                }
            }
        }
        return if (init) {
            synchronized(cacheLock) {
                // We have something cached and we want to merge it with the history.
                if (!historyLoaded) {
                    // Do this only when we haven't merged it yet (ever).
                    msgCache = mergeMsgLists(msgHistory, msgCache)
                    historyLoaded = true
                } else {
                    // Otherwise just prepend the history records.
                    msgCache.addAll(0, msgHistory)
                }
            }
            if (msgCache.isNotEmpty()) {
                mLastMsgFetchDate = msgCache[0].date
            }
            msgCache
        } else {
            if (msgHistory.isNotEmpty()) {
                mLastMsgFetchDate = msgHistory[0].date
            }
            msgHistory
        }
    }

    /**
     * Merges given lists of messages. Output list is ordered by received date.
     *
     *msgHistory first list to merge.
     *msgCache the second list to merge.
     *
     * @return merged list of messages contained in the given lists ordered by the date.
     */
    private fun mergeMsgLists(msgHistory: List<ChatMessage>, msgCache: List<ChatMessage>): MutableList<ChatMessage> {
        val mergedList: MutableList<ChatMessage> = LinkedList()
        var historyIdx = msgHistory.size - 1
        var cacheIdx = msgCache.size - 1
        while (historyIdx >= 0 && cacheIdx >= 0) {
            val historyMsg = msgHistory[historyIdx]
            val cacheMsg = msgCache[cacheIdx]
            if (historyMsg.date.after(cacheMsg.date)) {
                mergedList.add(0, historyMsg)
                historyIdx--
            } else {
                mergedList.add(0, cacheMsg)
                cacheIdx--
            }
        }

        // Input remaining history messages
        while (historyIdx >= 0) mergedList.add(0, msgHistory[historyIdx--])

        // Input remaining cache messages
        while (cacheIdx >= 0) mergedList.add(0, msgCache[cacheIdx--])
        return mergedList
    }

    /**
     * Fetch the server mam message and merged into the history database if new;
     * This method is accessed only after the user has registered with the network,
     *
     *descriptor can either be metaContact or chatRoomWrapper=>ChatRoom, from whom the mam are to be loaded
     */
    private fun mamQuery(descriptor: Any): Boolean {
        if (!protocolProvider.isRegistered) {
            aTalkApp.showToastMessage(R.string.service_gui_HISTORY_WARNING)
            return false
        }
        val mamManager: MamManager
        val connection = protocolProvider.connection!!
        val jid: BareJid
        if (descriptor is ChatRoom) {
            jid = descriptor.getIdentifier()
            mamManager = getInstanceFor(descriptor.getMultiUserChat())
        } else {
            jid = (descriptor as MetaContact).getDefaultContact()!!.contactJid!!.asBareJid()
            mamManager = getInstanceFor(connection, null)
        }

        // Retrieve the mamData from the last message sent/received in this chatSession
        val mMHS = MessageHistoryActivator.messageHistoryService
        var mamDate = mMHS.getLastMessageDateForSessionUuid(mChatId)
        try {
            if (mamManager.isSupported) {
                // Prevent omemoManager from automatically decrypting MAM messages.
                val omemoManager = OmemoManager.getInstanceFor(connection)
                omemoManager.stopStanzaAndPEPListeners()

                // Must always use a valid mamDate in memQuery
                mamDate = mMHS.getMamDate(mChatId)
                if (mamDate == null) {
                    val c = Calendar.getInstance(TimeZone.getDefault())
                    c[Calendar.DAY_OF_MONTH] = -30
                    mamDate = c.time
                }
                val mamQueryArgs = MamQueryArgs.builder()
                        .limitResultsToJid(jid)
                        .limitResultsSince(mamDate)
                        .setResultPageSizeTo(MAM_PAGE_SIZE)
                        .build()
                val query = mamManager.queryArchive(mamQueryArgs)
                val forwardedList = query.page.forwarded
                if (forwardedList.isNotEmpty()) {
                    mMHS.saveMamIfNotExit(omemoManager, this, forwardedList)
                }
                omemoManager.resumeStanzaAndPEPListeners()
            } else {
                if (mamDate == null) {
                    val c = Calendar.getInstance(TimeZone.getDefault())
                    mamDate = c.time
                }
                mMHS.setMamDate(mChatId, mamDate!!)
            }
        } catch (e: SmackException.NoResponseException) {
            Timber.e("MAM query: %s", e.message)
        } catch (e: XMPPException.XMPPErrorException) {
            Timber.e("MAM query: %s", e.message)
        } catch (e: SmackException.NotConnectedException) {
            Timber.e("MAM query: %s", e.message)
        } catch (e: InterruptedException) {
            Timber.e("MAM query: %s", e.message)
        } catch (e: SmackException.NotLoggedInException) {
            Timber.e("MAM query: %s", e.message)
        }
        return true
    }

    /**
     * Update the file transfer status in the msgCache; must do this else file transfer will be
     * reactivated onResume chat. Also important if historyLog is disabled.
     *
     *msgUuid ChatMessage uuid
     *status File transfer status
     *fileName the downloaded fileName
     *recordType File record type see ChatMessage MESSAGE_FILE_
     */
    fun updateCacheFTRecord(msgUuid: String, status: Int, fileName: String?, encType: Int, recordType: Int) {
        var cacheIdx = msgCache.size - 1
        while (cacheIdx >= 0) {
            val cacheMsg = msgCache[cacheIdx] as ChatMessageImpl
            // 20220709: cacheMsg.getMessageUID() can be null
            if (msgUuid == cacheMsg.messageUID) {
                cacheMsg.updateFTStatus(descriptor, msgUuid, status, fileName!!, encType, recordType, cacheMsg.messageDir!!)
                // Timber.d("updateCacheFTRecord msgUid: %s => %s (%s)", msgUuid, status, recordType );
                break
            }
            cacheIdx--
        }
    }

    /**
     * Remove user deleted messages from msgCache if receiptStatus is null;
     * or update receiptStatus cached message of the given msgUuid
     *
     *msgUuid ChatMessage uuid
     *receiptStatus message receipt status to update; null is to delete message
     */
    fun updateCacheMessage(msgUuid: String, receiptStatus: Int?) {
        var cacheIdx = msgCache.size - 1
        while (cacheIdx >= 0) {
            val cacheMsg = msgCache[cacheIdx] as ChatMessageImpl
            if (msgUuid == cacheMsg.messageUID) {
                // Timber.d("updateCacheMessage msgUid: %s => %s", msgUuid, receiptStatus);
                if (receiptStatus == null) {
                    msgCache.removeAt(cacheIdx)
                } else {
                    cacheMsg.receiptStatus = receiptStatus
                }
                break
            }
            cacheIdx--
        }
    }

    /**
     * Implements the `Chat.isChatFocused` method. Returns TRUE if this chat is
     * the currently selected and if the chat window, where it's contained is active.
     *
     * @return true if this chat has the focus and false otherwise.
     */
    override val isChatFocused: Boolean
        get() = mChatId != null && mChatId == ChatSessionManager.getCurrentChatId()
    /**
     * Returns the message written by user in the chat write area.
     *
     * @return the message written by user in the chat write area
     *///??? chatController.msgEdit.setText(message);
    /**
     * Sets the given message as a message in the chat write area.
     *
     *message the text that would be set to the chat write area
     */
    override var message: String
        get() {
            throw RuntimeException("Not supported yet")
        }
        set(message) {
            throw RuntimeException("Not supported yet")
            //??? chatController.msgEdit.setText(message);
        }

    /**
     * Bring this chat to front if `b` is true, hide it otherwise.
     *
     *isVisible tells if the chat will be made visible or not.
     */
    override fun setChatVisible(isVisible: Boolean) {
        throw RuntimeException("Not supported yet")
    }

    /**
     * Sends the message and blocked message caching for this message; otherwise the single send message
     * will appear twice in the chat fragment i.e. inserted and cached e.g. from share link
     *
     *message the text string to be sent
     *encType The encType of the message to be sent: RemoteOnly | 1=text/html or 0=text/plain.
     */
    fun sendMessage(message: String, encType: Int) {
        cacheBlocked = true
        var encryption = IMessage.ENCRYPTION_NONE
        if (isOmemoChat) encryption = IMessage.ENCRYPTION_OMEMO else if (isOTRChat) encryption = IMessage.ENCRYPTION_OTR
        try {
            mCurrentChatTransport.sendInstantMessage(message, encryption or encType)
        } catch (ex: Exception) {
            aTalkApp.showToastMessage(ex.message)
            cacheBlocked = false
        }
    }

    /**
     * Add a message to this `Chat`. Mainly use for System messages for internal generated messages
     *
     *contactName the name of the contact sending the message
     *date the time at which the message is sent or received
     *messageType the type of the message
     *encType the content encode type i.e plain or html
     *content the message text
     */
    override fun addMessage(contactName: String, date: Date, messageType: Int, mimeType: Int, message: String) {
        addMessage(ChatMessageImpl(contactName, contactName, date, messageType, mimeType, message, null, ChatMessage.DIR_IN))
    }

    /**
     * Add a message to this `Chat`.
     *
     *contactName the name of the contact sending the message
     *displayName the display name of the contact
     *date the time at which the message is sent or received
     *chatMsgType the type of the message. See ChatMessage
     *message the IMessage.
     */
    fun addMessage(
            contactName: String, displayName: String?, date: Date, chatMsgType: Int,
            message: IMessage, correctedMessageUID: String?,
    ) {
        addMessage(ChatMessageImpl(contactName, displayName, date, chatMsgType, message, correctedMessageUID, ChatMessage.DIR_IN))
    }

    /**
     * Adds a chat message to this `Chat` panel.
     *
     *chatMessage the ChatMessage.
     */
    fun addMessage(chatMessage: ChatMessageImpl) {
        // Must always cache the chatMsg as chatFragment has not registered to handle incoming
        // message on first onAttach or when it is not in focus.
        if (!cacheNextMsg(chatMessage)) {
            Timber.e("Failed adding to msgCache (updated: %s): %s", cacheUpdated, chatMessage.messageUID)
        }
        messageSpeak(chatMessage, 2 * ttsDelay) // for chatRoom

        // Just show a ToastMessage if no ChatSessionListener to display the messages usually ChatMessage.MESSAGE_ERROR
        if (msgListeners.isEmpty()) {
            aTalkApp.showToastMessage(chatMessage.message)
        } else {
            for (l in msgListeners) {
                l!!.messageAdded(chatMessage)
            }
        }
    }

    /**
     * Caches next message when chat is not in focus and it is not being blocked via sendMessage().
     * Otherwise duplicated messages when share link
     *
     *newMsg the next message to cache.
     *
     * @return true if newMsg added successfully to the msgCache
     */
    fun cacheNextMsg(newMsg: ChatMessageImpl): Boolean {
        // Timber.d("Cache blocked is %s for: %s", cacheBlocked, newMsg.getMessage());
        if (!cacheBlocked) {
            // FFR: ANR synchronized (cacheLock); fixed with new msgCache merging optimization (20221229)
            synchronized(cacheLock) { return msgCache.add(newMsg).also { cacheUpdated = it } }
        } else {
            cacheBlocked = false
            cacheUpdated = null
        }
        return false
    }

    fun updateChatTtsOption() {
        isChatTtsEnable = ConfigurationUtils.isTtsEnable()
        if (isChatTtsEnable) {
            // Object mDescriptor = mChatSession.getDescriptor();
            isChatTtsEnable = if (descriptor is MetaContact) {
                descriptor.getDefaultContact()!!.isTtsEnable!!
            } else {
                (descriptor as ChatRoomWrapper).isTtsEnable
            }
        }
        // refresh the tts delay time
        ttsDelay = ConfigurationUtils.getTtsDelay()
    }

    private fun messageSpeak(msg: ChatMessage, delay: Int) {
        Timber.d("Chat TTS message speak: %s = %s", isChatTtsEnable, msg.message)
        if (!isChatTtsEnable) return

        // ChatRoomMessageReceivedEvent from conference room
        if (ChatMessage.MESSAGE_IN == msg.messageType
                || ChatMessage.MESSAGE_MUC_IN == msg.messageType) {
            try {
                Thread.sleep(delay.toLong())
            } catch (e: InterruptedException) {
                Timber.w("TTS speak wait exception: %s", e.message)
            }
            ttsSpeak(msg)
        }
    }

    /**
     * call TTS to speak the text given in chatMessage if it is not HttpDownloadLink
     *
     *chatMessage ChatMessage for TTS
     */
    fun ttsSpeak(chatMessage: ChatMessage) {
        val textBody = chatMessage.message
        if (!TextUtils.isEmpty(textBody) && !FileBackend.isHttpFileDnLink(textBody)) {
            val spkIntent = Intent(aTalkApp.instance, TTSService::class.java)
            spkIntent.putExtra(TTSService.EXTRA_MESSAGE, textBody)
            spkIntent.putExtra(TTSService.EXTRA_QMODE, false)
            aTalkApp.instance!!.startService(spkIntent)
        }
    }

    /**
     * Send an outgoing file message to chatFragment for it to start the file send process
     * The recipient can be contact or chatRoom
     *
     *filePath as message content of the file to be sent
     *messageType indicate which File transfer message is for
     */
    fun addFTSendRequest(filePath: String?, messageType: Int) {
        val sendTo: String
        val date = Calendar.getInstance().time

        // Create the new msg Uuid for record saved in dB
        val msgUuid = System.currentTimeMillis().toString() + hashCode()
        val sender = mCurrentChatTransport.descriptor
        sendTo = if (sender is Contact) {
            sender.address
        } else {
            (sender as ChatRoom).getName()
        }

        // Do not use addMessage to avoid TTS activation for outgoing file message
        val chatMsg = ChatMessageImpl(sendTo, sendTo, date, messageType,
                IMessage.ENCODE_PLAIN, filePath, msgUuid, ChatMessage.DIR_OUT)
        if (!cacheNextMsg(chatMsg)) {
            Timber.e("Failed adding to msgCache (updated: %s): %s", cacheUpdated, msgUuid)
        }
        for (l in msgListeners) {
            l!!.messageAdded(chatMsg)
        }
    }

    /**
     * ChatMessage for IncomingFileTransferRequest
     *
     *
     * Adds the given `IncomingFileTransferRequest` to the conversation panel in order to
     * notify the user of an incoming file transfer request.
     *
     *opSet the file transfer operation set
     *request the request to display in the conversation panel
     *date the date on which the request has been received
     *
     * @see FileTransferActivator.fileTransferRequestReceived
     */
    fun addFTReceiveRequest(opSet: OperationSetFileTransfer?, request: IncomingFileTransferRequest, date: Date) {
        val sender = request.getSender()
        val senderName = sender!!.address
        val msgUuid = request.getID()
        val msgContent = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_REQUEST_RECEIVED, date.toString(), senderName)
        val msgType = ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE
        val encType = IMessage.ENCODE_PLAIN
        val chatMsg = ChatMessageImpl(senderName, date, msgType, encType,
                msgContent, msgUuid, ChatMessage.DIR_IN, opSet, request, null)

        // Do not use addMessage to avoid TTS activation for incoming file message
        if (!cacheNextMsg(chatMsg)) {
            Timber.e("Failed adding to msgCache (updated: %s): %s", cacheUpdated, msgUuid)
        }
        for (l in msgListeners) {
            l!!.messageAdded(chatMsg)
        }
    }

    /**
     * Adds a new ChatLinkClickedListener. The callback is called for every link whose scheme is
     * `jitsi`. It is the callback's responsibility to filter the action based on the URI.
     *
     *
     * Example:<br></br>
     * `jitsi://classname/action?query`<br></br>
     * Use the name of the registering class as the host, the action to execute as the path and
     * any parameters as the query.
     *
     *chatLinkClickedListener callback that is notified when a link was clicked.
     */
    override fun addChatLinkClickedListener(listener: ChatLinkClickedListener) {
        ChatSessionManager.addChatLinkListener(listener)
    }

    override fun messageReceived(evt: MessageReceivedEvent) {
        // cmeng: only handle messageReceivedEvent belongs to this.metaContact
        if (mMetaContact != null && mMetaContact!!.containsContact(evt.getSourceContact())) {
            // Must cache chatMsg as chatFragment has not registered to handle incoming
            // message on first onAttach or not in focus
            val chatMessage = ChatMessageImpl.getMsgForEvent(evt)
            if (!cacheNextMsg(chatMessage)) {
                Timber.e("Failed adding to msgCache (updated: %s): %s", cacheUpdated, chatMessage.messageUID)
            }
            for (l in msgListeners) {
                l!!.messageReceived(evt)
            }
            messageSpeak(chatMessage, ttsDelay)
        }
    }

    override fun messageDelivered(evt: MessageDeliveredEvent) {
        /*
         * (metaContact == null) for ConferenceChatTransport. Check just in case the listener is not properly
         * removed when the chat is closed. Only handle messageReceivedEvent belongs to this.metaContact
         */
        if (mMetaContact != null && mMetaContact!!.containsContact(evt.getContact())) {

            // return if delivered message does not required local display in chatWindow nor cached
            if (evt.getSourceMessage().isRemoteOnly()) return
            val chatMessage = ChatMessageImpl.getMsgForEvent(evt)
            if (!cacheNextMsg(chatMessage)) {
                Timber.e("Failed adding to msgCache (updated: %s): %s", cacheUpdated, chatMessage.messageUID)
            }
            for (l in msgListeners) {
                l!!.messageDelivered(evt)
            }
        }
    }

    override fun messageDeliveryFailed(evt: MessageDeliveryFailedEvent) {
        for (l in msgListeners) {
            l!!.messageDeliveryFailed(evt)
        }

        // Insert error message
        Timber.d("%s", evt.reason)

        // Just show the pass in error message if false
        var mergeMessage = true
        var errorMsg: String
        val srcMessage = evt.source as IMessage

        // contactJid cannot be nick name, otherwise message will not be displayed
        val contactJid = evt.destinationContact.address
        when (evt.errorCode) {
            MessageDeliveryFailedEvent.OFFLINE_MESSAGES_NOT_SUPPORTED -> errorMsg = aTalkApp.getResString(
                    R.string.service_gui_MSG_DELIVERY_NOT_SUPPORTED, contactJid)
            MessageDeliveryFailedEvent.NETWORK_FAILURE -> errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_NOT_DELIVERED)
            MessageDeliveryFailedEvent.PROVIDER_NOT_REGISTERED -> errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_SEND_CONNECTION_PROBLEM)
            MessageDeliveryFailedEvent.INTERNAL_ERROR -> errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_INTERNAL_ERROR)
            MessageDeliveryFailedEvent.OMEMO_SEND_ERROR -> {
                errorMsg = evt.reason!!
                mergeMessage = false
            }
            else -> errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_ERROR)
        }
        val reason = evt.reason
        if (!TextUtils.isEmpty(reason) && mergeMessage) {
            errorMsg += " " + aTalkApp.getResString(R.string.service_gui_ERROR_WAS, reason)
        }
        addMessage(contactJid, Date(), ChatMessage.MESSAGE_OUT, srcMessage.getMimeType(), srcMessage.getContent()!!)
        addMessage(contactJid, Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN, errorMsg)
    }

    /**
     * Extends `MessageListener` interface in order to provide notifications about injected
     * messages without the need of event objects.
     *
     * @author Pawel Domas
     */
    interface ChatSessionListener : MessageListener {
        fun messageAdded(msg: ChatMessage)
    }

    /**
     * Updates the status of the given chat transport in the send via selector box and notifies
     * the user for the status change.
     *
     *chatTransport the `chatTransport` to update
     */
    fun updateChatTransportStatus(chatTransport: ChatTransport) {
        if (isChatFocused) {
            val activity = aTalkApp.getCurrentActivity()
            activity?.runOnUiThread {
                val presenceStatus = chatTransport.status
                ActionBarUtil.setSubtitle(activity, presenceStatus!!.statusName)
                ActionBarUtil.setStatusIcon(activity, presenceStatus.statusIcon)
            }
        }
        val contactName = (chatTransport as MetaContactChatTransport).contact.address
        if (ConfigurationUtils.isShowStatusChangedInChat) {
            // Show a status message to the user.
            // addMessage(contactName, chatTransport.getName(), new Date(), ChatMessage.MESSAGE_STATUS, IMessage.ENCODE_PLAIN,
            //        aTalkApp.getResString(R.string.service_gui_STATUS_CHANGED_CHAT_MESSAGE, chatTransport.getStatus().getStatusName()),
            //        IMessage.ENCRYPTION_NONE, null, null);
            addMessage(contactName, Date(), ChatMessage.MESSAGE_STATUS, IMessage.ENCODE_PLAIN,
                    aTalkApp.getResString(R.string.service_gui_STATUS_CHANGED_CHAT_MESSAGE, chatTransport.status.statusName))
        }
    }

    /**
     * Renames all occurrences of the given `chatContact` in this chat panel.
     *
     * chatContact the contact to rename name the new name
     */
    fun setContactName(chatContact: ChatContact<*>?, name: String?) {
        if (isChatFocused) {
            val activity = aTalkApp.getCurrentActivity()
            activity!!.runOnUiThread {
                if (chatSession is MetaContactChatSession) {
                    ActionBarUtil.setTitle(activity, name)
                }
            }
        }
    }

    /**
     * Sets the given `subject` to this chat.
     *
     *subject the subject to set
     */
    fun setChatSubject(subject: String?, oldSubject: String?) {
        if (subject != null && subject != chatSubject) {
            chatSubject = subject
            if (isChatFocused) {
                val activity = aTalkApp.getCurrentActivity()
                activity?.runOnUiThread {
                    // cmeng: check instanceof just in case user change chat session
                    if (chatSession is ConferenceChatSession) {
                        ActionBarUtil.setSubtitle(activity, subject)
                    }
                }
            }
            // Do not display change subject message if this is the original subject
            if (!TextUtils.isEmpty(oldSubject)) this.addMessage(chatSession!!.chatEntity, Date(), ChatMessage.MESSAGE_STATUS, IMessage.ENCODE_PLAIN,
                    aTalkApp.getResString(R.string.service_gui_CHATROOM_SUBJECT_CHANGED, oldSubject, subject))
        }
    }

    /**
     * Updates the contact status - call from conference only.
     *
     *chatContact the chat contact of the conference to update
     *statusMessage the status message to show
     */
    fun updateChatContactStatus(chatContact: ChatContact<*>, statusMessage: String) {
        if (StringUtils.isNotEmpty(statusMessage)) {
            val contactName = (chatContact.descriptor as ChatRoomMemberJabberImpl).getContactAddress()
            addMessage(contactName, Date(), ChatMessage.MESSAGE_STATUS, IMessage.ENCODE_PLAIN, statusMessage)
        }
    }

    /**
     * Returns the first chat transport for the current chat session that supports group chat.
     *
     * @return the first chat transport for the current chat session that supports group chat.
     */
    fun findInviteChatTransport(): ChatTransport? {
        val protocolProvider = mCurrentChatTransport.protocolProvider

        // We choose between OpSets for multi user chat...
        if (protocolProvider.getOperationSet(OperationSetMultiUserChat::class.java) != null
                || protocolProvider.getOperationSet(OperationSetAdHocMultiUserChat::class.java) != null) {
            return mCurrentChatTransport
        } else {
            val chatTransportsIter = chatSession!!.getChatTransports()
            while (chatTransportsIter.hasNext()) {
                val chatTransport = chatTransportsIter.next()
                val groupChatOpSet = chatTransport.protocolProvider.getOperationSet(OperationSetMultiUserChat::class.java)
                if (groupChatOpSet != null) return chatTransport
            }
        }
        return null
    }

    /**
     * Invites the given `chatContacts` to this chat.
     *
     *inviteChatTransport the chat transport to use to send the invite
     *chatContacts the contacts to invite
     *reason the reason of the invitation
     */
    fun inviteContacts(inviteChatTransport: ChatTransport, chatContacts: Collection<String>, reason: String?) {
        val pps = inviteChatTransport.protocolProvider
        if (chatSession is MetaContactChatSession) {
            val conferenceChatManager = AndroidGUIActivator.uIService.conferenceChatManager

            // the chat session is set regarding to which OpSet is used for MUC
            if (pps.getOperationSet(OperationSetMultiUserChat::class.java) != null) {
                val chatRoomWrapper = MUCActivator.mucService.createPrivateChatRoom(pps, chatContacts, reason, false)
                if (chatRoomWrapper != null) {
                    // conferenceChatSession = new ConferenceChatSession(this, chatRoomWrapper);
                    val chatIntent = ChatSessionManager.getChatIntent(chatRoomWrapper)
                    aTalkApp.globalContext.startActivity(chatIntent)
                } else {
                    Timber.e("Failed to create chatroom")
                }
            } else if (pps.getOperationSet(OperationSetAdHocMultiUserChat::class.java) != null) {
                val chatRoomWrapper = conferenceChatManager.createAdHocChatRoom(pps, chatContacts, reason)
                // conferenceChatSession = new AdHocConferenceChatSession(this, chatRoomWrapper);
                val chatIntent = ChatSessionManager.getChatIntent(chatRoomWrapper)
                aTalkApp.globalContext.startActivity(chatIntent)
            }
            // if (conferenceChatSession != null) {
            //   this.setChatSession(conferenceChatSession);
            // }
        } else {
            for (contactAddress in chatContacts) {
                try {
                    mCurrentChatTransport.inviteChatContact(JidCreate.entityBareFrom(contactAddress), reason)
                } catch (e: XmppStringprepException) {
                    Timber.w("Group chat invitees Jid create error: %s, %s", contactAddress, e.message)
                }
            }
        }
    }

    companion object {
        /**
         * Number of history messages to be returned from loadHistory call.
         * Limits the amount of messages being loaded at one time.
         */
        private const val HISTORY_CHUNK_SIZE = 30
        private const val MAM_PAGE_SIZE = 50

        /**
         * ConferenceChatSession Subject - inform user if changed
         */
        private var chatSubject = ""
    }
}