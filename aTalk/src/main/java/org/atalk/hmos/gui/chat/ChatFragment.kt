/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.hmos.gui.chat

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.SparseBooleanArray
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AbsListView.MultiChoiceModeListener
import android.widget.AdapterView
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import net.java.sip.communicator.impl.protocol.jabber.HttpFileDownloadJabberImpl
import net.java.sip.communicator.impl.protocol.jabber.OperationSetPersistentPresenceJabberImpl
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.filehistory.FileRecord
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.protocol.ChatRoomMember
import net.java.sip.communicator.service.protocol.ChatRoomMemberRole
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.FileTransfer
import net.java.sip.communicator.service.protocol.IMessage
import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.ChatStateNotificationEvent
import net.java.sip.communicator.service.protocol.event.ChatStateNotificationsListener
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.FileTransferStatusListener
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.MessageDeliveryFailedEvent
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent
import net.java.sip.communicator.util.ByteFormat
import net.java.sip.communicator.util.GuiUtils.formatDateTime
import net.java.sip.communicator.util.StatusUtil.getContactStatusIcon
import org.atalk.crypto.CryptoFragment
import org.atalk.crypto.listener.CryptoModeChangeListener
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.chat.ChatPanel.ChatSessionListener
import org.atalk.hmos.gui.chat.ChatSessionManager.CurrentChatListener
import org.atalk.hmos.gui.chat.ChatSessionManager.addCurrentChatListener
import org.atalk.hmos.gui.chat.ChatSessionManager.getActiveChat
import org.atalk.hmos.gui.chat.ChatSessionManager.getChatIntent
import org.atalk.hmos.gui.chat.ChatSessionManager.removeCurrentChatListener
import org.atalk.hmos.gui.chat.ChatStateNotificationHandler.handleChatStateNotificationReceived
import org.atalk.hmos.gui.chat.filetransfer.FileHistoryConversation
import org.atalk.hmos.gui.chat.filetransfer.FileHttpDownloadConversation
import org.atalk.hmos.gui.chat.filetransfer.FileReceiveConversation
import org.atalk.hmos.gui.chat.filetransfer.FileSendConversation
import org.atalk.hmos.gui.contactlist.model.MetaContactRenderer
import org.atalk.hmos.gui.share.ShareActivity
import org.atalk.hmos.gui.share.ShareUtil.share
import org.atalk.hmos.gui.share.ShareUtil.shareLocal
import org.atalk.hmos.gui.util.EntityListHelper.eraseEntityChatHistory
import org.atalk.hmos.gui.util.HtmlImageGetter
import org.atalk.hmos.gui.util.XhtmlImageParser
import org.atalk.hmos.gui.util.event.EventListener
import org.atalk.hmos.plugin.geolocation.SvpApiImpl
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.persistance.FileBackend.getUriForFile
import org.atalk.persistance.FileBackend.isHttpFileDnLink
import org.atalk.service.osgi.OSGiFragment
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.util.StringUtils
import org.jivesoftware.smackx.chatstates.ChatState
import org.jivesoftware.smackx.omemo_media_sharing.AesgcmUrl
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager
import org.jivesoftware.smackx.receipts.ReceiptReceivedListener
import org.jxmpp.jid.Jid
import org.jxmpp.util.XmppStringUtils
import timber.log.Timber
import java.io.File
import java.util.*
import java.util.regex.Pattern

/**
 * The `ChatFragment` working in conjunction with ChatActivity, ChatPanel, ChatController
 * etc is providing the UI for all the chat messages/info receive and display.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ChatFragment : OSGiFragment(), CurrentChatListener, FileTransferStatusListener, CryptoModeChangeListener, ReceiptReceivedListener {
    /**
     * Returns the underlying chat list view.
     *
     * @return the underlying chat list view
     */
    /**
     * The session adapter for the contained `ChatPanel`.
     */
    var chatListAdapter: ChatListAdapter? = null
        private set
    /**
     * Returns the corresponding `ChatPanel`.
     *
     * @return the corresponding `ChatPanel`
     */
    /**
     * The corresponding `ChatPanel`.
     */
    var chatPanel: ChatPanel? = null
        private set

    /**
     * chat MetaContact associated with the chatFragment
     */
    private var mChatMetaContact: MetaContact? = null
    /**
     * Returns the underlying chat list view.
     *
     * @return the underlying chat list view
     */
    /**
     * The chat list view representing the chat.
     */
    lateinit var chatListView: ListView
        private set

    /**
     * List header used to display progress bar when history is being loaded.
     */
    private var header: View? = null

    /**
     * Remembers first visible view to scroll the list after new portion of history messages is added.
     */
    private var scrollFirstVisible = 0

    /**
     * Remembers top position to add to the scrolling offset after new portion of history messages is added.
     */
    private var scrollTopOffset = 0

    /**
     * the top of the last deleted message group to scroll to after deletion
     */
    private var lastDeletedMessageDate: Date? = null

    /**
     * The chat state view.
     */
    private var chatStateView: LinearLayout? = null

    /**
     * The task that loads history.
     */
    private var loadHistoryTask: LoadHistoryTask? = null

    /**
     * Stores all active file transfer requests and effective transfers with the identifier of the transfer.
     */
    private val activeFileTransfers = Hashtable<String?, Any>()

    /**
     * Stores all active file transfer requests and effective DisplayMessage position.
     */
    private val activeMsgTransfers = Hashtable<String?, Int>()

    /**
     * Indicates that this fragment is the currently selected primary page. A primary page can
     * have both onShow and onHide (overlay with other dialog) state. This setting is important,
     * because of PagerAdapter is being used on phone layouts.
     *
     * @see .setPrimarySelected
     * @see .onResume
     */
    private var primarySelected = false

    /*
     * Current chatType that is in use.
     */
    private var mChatType = MSGTYPE_UNKNOWN
    private var mCFView: View? = null

    /**
     * flag indicates fragment is in multi-selection ActionMode; use to temporary disable
     * last msg correction access etc.
     */
    private var isMultiChoiceMode = false

    /**
     * The chat controller used to handle operations like editing and sending messages associated with this fragment.
     */
    var chatController: ChatController? = null
        private set

    /**
     * The current chat transport.
     */
    private var currentChatTransport: ChatTransport? = null
    private var mProvider: ProtocolProviderService? = null

    /**
     * The current chatFragment.
     */
    private lateinit var currentChatFragment: ChatFragment

    /**
     * The current cryptoFragment.
     */
    private var mCryptoFragment: CryptoFragment? = null
    // private static int COUNTDOWN_INTERVAL = 1000; // ms for stealth
    /**
     * Flag indicates that we have loaded the history for the first time.
     */
    private var historyLoaded = false
    private var mContext: Context? = null
    private var mChatActivity: ChatActivity? = null
    private var mSVPStarted = false
    private var mSVP: Any? = null
    private var svpApi: SvpApiImpl? = null

    /**
     * {@inheritDoc}
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        chatController = ChatController(activity!!, this)
        mContext = context
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mCFView = inflater.inflate(R.layout.chat_conversation, container, false)
        chatListAdapter = ChatListAdapter()
        chatListView = mCFView!!.findViewById(R.id.chatListView)

        // Inflates and adds the header, hidden by default
        header = inflater.inflate(R.layout.progressbar, chatListView, false)
        header!!.visibility = GONE
        chatListView.addHeaderView(header)
        chatStateView = mCFView!!.findViewById(R.id.chatStateView)
        chatListView.adapter = chatListAdapter
        chatListView.setSelector(R.drawable.list_selector_state)
        initListViewListeners()

        // Chat intent handling - chatId should not be null
        var chatId: String? = null
        val arguments = arguments
        if (arguments != null) chatId = arguments.getString(ChatSessionManager.CHAT_IDENTIFIER)
        requireNotNull(chatId)
        chatPanel = getActiveChat(chatId)
        if (chatPanel == null) {
            Timber.e("Chat for given id: %s does not exist", chatId)
            return null
        }

        // mChatMetaContact is null for conference
        mChatMetaContact = chatPanel!!.metaContact
        chatPanel!!.addMessageListener(chatListAdapter!!)
        currentChatTransport = chatPanel!!.chatSession!!.currentChatTransport
        currentChatFragment = this
        mProvider = currentChatTransport!!.protocolProvider
        if (mChatMetaContact != null && mProvider!!.isRegistered) {
            val deliveryReceiptManager = DeliveryReceiptManager.getInstanceFor(mProvider!!.connection)
            deliveryReceiptManager.addReceiptReceivedListener(this)
        }
        mChatActivity = activity as ChatActivity?
        if (mChatActivity != null) {
            val fragmentMgr = mChatActivity!!.supportFragmentManager
            mCryptoFragment = fragmentMgr.findFragmentByTag(ChatActivity.CRYPTO_FRAGMENT) as CryptoFragment?
        }
        return mCFView
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initListViewListeners() {
        chatListView.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
                /*
                 * Detects event when user scrolls to the top of the list, to add more history messages.
                 * Proceed only if there is no active file transfer in progress; and not in
                 * MultiChoiceMode as this will destroy selected items, and reused view may get selected
                 */
                val childFirst = chatListView.getChildAt(0)
                if (!isMultiChoiceMode && childFirst != null && scrollState == 0 && activeFileTransfers.size == 0) {
                    if (childFirst.top == 0) {
                        // Loads some more history if there's no loading task in progress
                        if (loadHistoryTask == null) {
                            loadHistoryTask = LoadHistoryTask(false)
                            loadHistoryTask!!.execute()
                        }
                    }
                }
            }

            override fun onScroll(
                    view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int,
                    totalItemCount: Int,
            ) {
                // Remembers scrolling position to restore after new history messages are loaded
                scrollFirstVisible = firstVisibleItem
                val firstVisible = view.getChildAt(0)
                scrollTopOffset = firstVisible?.top ?: 0
                // Timber.d("Last scroll position: %s: %s", scrollFirstVisible, scrollTopOffset);
            }
        })
        chatListView.setOnTouchListener { v: View?, event: MotionEvent ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                chatController!!.onTouchAction()
            }
            false
        }

        // Using the contextual action mode with multi-selection
        chatListView.choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
        chatListView.setMultiChoiceModeListener(mMultiChoiceListener)
        chatListView.onItemLongClickListener = OnItemLongClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
            Toast.makeText(context, R.string.chat_message_long_press_hint, Toast.LENGTH_SHORT).show()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        if (svpApi == null) svpApi = SvpApiImpl()

        /*
         * If this chatFragment is added to the pager adapter for the first time it is required to
         * check again, because it's marked visible when the Views are not created yet
         *
         * cmeng - seeing problem as it includes other non-focus chatFragment causing non-sync
         * between chatFragment and chatController i.e. msg sent to wrong chatFragment
         * - resolved with initChatController() passing focus state as parameter, taking
         * appropriate actions pending the focus state;
         *
         *  Perform chatTransport changes only if and only if this chatFragment is the
         *  selected primary page - notified by chatPager (Check at ChatController)
         */
        if (primarySelected) {
            initChatController(true)

            // Invoke the listener valid only of metaContactChatSession
            if (chatPanel!!.chatSession is MetaContactChatSession) {
                chatPanel!!.addContactStatusListener(chatListAdapter!!)
            }
            chatPanel!!.addChatStateListener(chatListAdapter!!)
            addCurrentChatListener(this)
            mSVPStarted = false
            mSVP = null
        }
    }

    /**
     * Stop all chat listeners onPause, can only upadte on UI thread
     */
    override fun onPause() {
        // Remove the listener valid only for metaContactChatSession
        if (chatPanel!!.chatSession is MetaContactChatSession) {
            chatPanel!!.removeContactStatusListener(chatListAdapter!!)
        }
        chatPanel!!.removeChatStateListener(chatListAdapter!!)

        // Not required - implemented as static map
        // cryptoFragment.removeCryptoModeListener(this);
        removeCurrentChatListener(this)

        /*
         * Indicates that this fragment is no longer in focus, because of this call parent
         * <code>Activities don't have to call it in onPause().
         */initChatController(false)
        super.onPause()
    }

    override fun onStop() {
        chatController!!.onChatCloseAction()
        super.onStop()
    }

    override fun onDetach() {
        Timber.d("Detach chatFragment: %s", this)
        super.onDetach()
        chatController = null
        if (chatPanel != null) {
            chatPanel!!.removeMessageListener(chatListAdapter!!)
        }
        chatListAdapter = null
        if (loadHistoryTask != null) {
            loadHistoryTask!!.cancel(true)
            loadHistoryTask = null
        }
    }

    /**
     * This method must be called by parent `Activity` or `Fragment` in order to
     * register the ChatController. Setting of primarySelected must solely be performed by
     * chatPagerAdapter only to ensure both the chatFragment and chatController are in sync.
     *
     * @param isSelected `true` if the fragment is now the primary selected page.
     *
     * @see ChatController .initChatController
     */
    fun setPrimarySelected(isSelected: Boolean) {
        // Timber.d("Primary page selected: %s %s", hashCode(), isSelected);
        primarySelected = isSelected
        initChatController(isSelected)
    }

    /**
     * Checks for `ChatController` initialization. To init/activate the controller fragment
     * must be visible and its View must be created.
     *
     * Non-focus chatFragment causing non-sync between chatFragment and chatController i.e.
     * sending & received messages sent to wrong chatFragment - resolved with initChatController()
     * passing in focus state as parameter; taking the appropriate actions pending the focus state;
     */
    private fun initChatController(inFocus: Boolean) {
        // chatController => NPE from field
        if (chatController != null) {
            if (!inFocus) {
                chatController!!.onHide()
                // Also remove global status listener
                AndroidGUIActivator.loginRenderer?.removeGlobalStatusListener(globalStatusListener)
            }
            else if (chatListView != null) {
                // Timber.d("Init controller: %s", hashCode());
                chatController!!.onShow()
                // Also register global status listener
                AndroidGUIActivator.loginRenderer?.addGlobalStatusListener(globalStatusListener)

                // Init the history & ChatType background color
                chatStateView!!.visibility = INVISIBLE
                initAdapter()

                // Seem mCFView changes on re-entry into chatFragment, so update the listener
                mCryptoFragment!!.addCryptoModeListener(currentChatTransport!!.descriptor, this)
                // initBackgroundColor();
                changeBackground(mCFView, chatPanel!!.chatType)
            }
        }
        else {
            Timber.d("Skipping null controller init...")
        }
    }

    /**
     * Initializes the chat list adapter.
     */
    private fun initAdapter() {
        /*
         * Initial history load is delayed until the chat is displayed to the user. We previously
         * relayed on onCreate, but it will be called too early on phone layouts where
         * ChatPagerAdapter is used. It creates ChatFragment too early that is before the first
         * message is added to the history and we are unable to retrieve it without hacks.
         */
        if (!historyLoaded) {
            /*
             * chatListAdapter.isEmpty() is used as initActive flag, chatPanel.msgCache must be
             * cleared. Otherwise it will cause chatPanel.getHistory to return old data when the
             * underlying data changed or adapter has been cleared
             */
            loadHistoryTask = LoadHistoryTask(chatListAdapter!!.isEmpty)
            loadHistoryTask!!.execute()
            historyLoaded = true
        }
    }

    /**
     * Tasks that need to be performed for the current chatFragment when chat history are deleted.
     * - Cancel any file transfer in progress
     * - Clean up all the display messages that are in the chatList Adapter
     * - Clear all the message that has been previously stored in the msgCache
     * - Force to reload history by clearing historyLoad = false;
     * - Refresh the display
     */
    fun onClearCurrentEntityChatHistory(deletedUUIDs: List<String>?) {
        cancelActiveFileTransfers()

        // check to ensure chatListAdapter has not been destroyed before proceed (NPE from field)
        if (chatListAdapter != null) {
            chatListAdapter!!.onClearMessage(deletedUUIDs)
        }

        // scroll to the top of last deleted message group; post delayed 500ms after android
        // has refreshed the listView and auto onScroll(); Too earlier access cause deleted
        // viewHolders still appear in chat view.
        if (lastDeletedMessageDate != null) {
            Handler().postDelayed({
                val deletedTop = chatListAdapter!!.getMessagePosFromDate(lastDeletedMessageDate)
                Timber.d("Last deleted message position: %s; %s", deletedTop, lastDeletedMessageDate)
                lastDeletedMessageDate = null
                if (deletedTop >= 0) chatListView.setSelection(deletedTop)
            }, 500)
        }
    }

    fun updateFTStatus(msgUuid: String, status: Int, fileName: String?, encType: Int, msgType: Int) {
        if (chatListAdapter != null) {
            chatListAdapter!!.updateMessageFTStatus(msgUuid, status, fileName, encType, msgType)
        }
    }

    /**
     * ActionMode with multi-selection implementation for chatListView
     */
    private val mMultiChoiceListener = object : MultiChoiceModeListener {
        var cPos = 0
        var headerCount = 0
        var checkListSize = 0
        var mEdit: MenuItem? = null
        var mQuote: MenuItem? = null
        var mForward: MenuItem? = null
        var mCopy: MenuItem? = null
        var mSelectAll: MenuItem? = null
        var checkedList: SparseBooleanArray? = null
        override fun onItemCheckedStateChanged(mode: ActionMode, position_: Int, id: Long, checked: Boolean) {
            // Here you can do something when items are selected/de-selected
            var position = position_
            checkedList = chatListView.checkedItemPositions
            checkListSize = checkedList!!.size()
            val checkedItemCount = chatListView.checkedItemCount

            // Checked item position is of interest when single item remains selected
            val isSingleItemSelected = checkedItemCount == 1
            if (isSingleItemSelected && checkListSize > 1) {
                position = checkedList!!.keyAt(checkedList!!.indexOfValue(true))
            }

            // Position must be aligned to the number of header views included
            cPos = position - headerCount
            val cType = chatListAdapter!!.getItemViewType(cPos)
            val isFileRecord = cType == FILE_TRANSFER_IN_MESSAGE_VIEW || cType == FILE_TRANSFER_OUT_MESSAGE_VIEW

            // Allow max of 5 actions including the overflow icon to be shown
            if (isSingleItemSelected && !isFileRecord) {
                if (currentChatTransport is MetaContactChatTransport && cType == OUTGOING_MESSAGE_VIEW) {
                    // ensure the selected view is the last MESSAGE_OUT for edit action
                    mEdit!!.isVisible = true
                    if (cPos != chatListAdapter!!.count - 1) {
                        for (i in cPos + 1 until chatListAdapter!!.count) {
                            if (chatListAdapter!!.getItemViewType(i) == OUTGOING_MESSAGE_VIEW) {
                                mEdit!!.isVisible = false
                                mCopy!!.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                                break
                            }
                        }
                    }
                }
                else {
                    mEdit!!.isVisible = false
                }
                mQuote!!.isVisible = true
                if (mEdit!!.isVisible) mForward!!.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM) else mForward!!.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                mCopy!!.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                mSelectAll!!.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }
            else {
                mEdit!!.isVisible = false
                mQuote!!.isVisible = false
                mForward!!.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                mCopy!!.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                mSelectAll!!.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            mSelectAll!!.isVisible = chatListAdapter!!.count > 1
            mode.invalidate()
            chatListView.setSelection(position)
            mode.title = checkedItemCount.toString()
        }

        // Called when the user selects a menu item. On action picked, close the CAB i.e. mode.finish();
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            var cType: Int
            var file: File?
            val sBuilder = StringBuilder()
            var chatMsg = chatListAdapter!!.getMessage(cPos)
            val imageUris = ArrayList<Uri>()

            return when (item.itemId) {
                R.id.chat_message_edit -> {
                    if (chatController != null && chatMsg != null) {
                        chatController!!.editText(chatListView, chatMsg, cPos)

                        // Clear the selected Item highlight
                        // chatListView.clearChoices();
                    }
                    true
                }
                R.id.select_all -> {
                    val size = chatListAdapter!!.count
                    if (size < 2) return true
                    var i = 0
                    while (i < size) {
                        cPos = i + headerCount
                        checkedList!!.put(cPos, true)
                        chatListView.setSelection(cPos)
                        i++
                    }
                    checkListSize = size
                    mode.invalidate()
                    mode.title = size.toString()
                    true
                }
                R.id.chat_message_copy -> {
                    // Get clicked message text and copy it to ClipBoard
                    var i = 0
                    while (i < checkListSize) {
                        if (checkedList!!.valueAt(i)) {
                            cPos = checkedList!!.keyAt(i) - headerCount
                            chatMsg = chatListAdapter!!.getMessage(cPos)
                            if (chatMsg != null) {
                                if (i > 0) sBuilder.append("\n").append(chatMsg.getContentForClipboard()) else sBuilder.append(chatMsg.getContentForClipboard())
                            }
                        }
                        i++
                    }
                    val cmgr = mChatActivity!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                    cmgr?.setPrimaryClip(ClipData.newPlainText(null, sBuilder))
                    mode.finish()
                    true
                }
                R.id.chat_message_quote -> {
                    if (chatMsg != null) chatController!!.setQuoteMessage(chatMsg)
                    mode.finish()
                    true
                }
                R.id.chat_message_forward, R.id.chat_message_share -> {
                    var i = 0
                    while (i < checkListSize) {
                        if (checkedList!!.valueAt(i)) {
                            cPos = checkedList!!.keyAt(i) - headerCount
                            cType = chatListAdapter!!.getItemViewType(cPos)
                            chatMsg = chatListAdapter!!.getMessage(cPos)
                            if (chatMsg != null) {
                                if (cType == INCOMING_MESSAGE_VIEW || cType == OUTGOING_MESSAGE_VIEW) {
                                    if (sBuilder.isNotEmpty()) sBuilder.append("\n").append(chatMsg.getContentForClipboard()) else sBuilder.append(chatMsg.getContentForClipboard())
                                }
                                else if (cType == FILE_TRANSFER_IN_MESSAGE_VIEW || cType == FILE_TRANSFER_OUT_MESSAGE_VIEW) {
                                    if (chatMsg.fileRecord != null) {
                                        file = chatMsg.fileRecord!!.file
                                        if (file.exists()) {
                                            imageUris.add(getUriForFile(mChatActivity!!, file))
                                        }
                                    }
                                }
                            }
                        }
                        i++
                    }
                    if (R.id.chat_message_forward == item.itemId) {
                        var shareIntent = Intent(mChatActivity, ShareActivity::class.java)
                        shareIntent = shareLocal(mContext, shareIntent, sBuilder.toString(), imageUris)
                        startActivity(shareIntent)
                        mode.finish()
                        // close current chat and show contact/chatRoom list view for content forward
                        mChatActivity!!.onBackPressed()
                    }
                    else {
                        share(mChatActivity, sBuilder.toString(), imageUris)
                        mode.finish()
                    }
                    true
                }
                R.id.chat_message_del -> {
                    val msgUidDel = ArrayList<String>()
                    val msgFilesDel = ArrayList<File>()
                    var i = 0
                    while (i < checkListSize) {
                        if (checkedList!!.valueAt(i)) {
                            cPos = checkedList!!.keyAt(i) - headerCount
                            cType = chatListAdapter!!.getItemViewType(cPos)
                            if (cType == INCOMING_MESSAGE_VIEW || cType == OUTGOING_MESSAGE_VIEW || cType == SYSTEM_MESSAGE_VIEW || cType == FILE_TRANSFER_IN_MESSAGE_VIEW || cType == FILE_TRANSFER_OUT_MESSAGE_VIEW) {
                                chatMsg = chatListAdapter!!.getMessage(cPos)
                                if (chatMsg != null) {
                                    if (i == 0) {
                                        // keep a reference for return to the top of last deleted messages group
                                        lastDeletedMessageDate = chatMsg.date
                                    }

                                    // merged messages do not have file contents
                                    if (chatMsg is MergedMessage) {
                                        msgUidDel.addAll(chatMsg.messageUIDs)
                                    }
                                    else {
                                        msgUidDel.add(chatMsg.getMessageUID())

                                        /*
                                         * Include only the incoming received media or aTalk created outgoing tmp files
                                         * OR all voice file for deletion
                                         */
                                        if (cType == FILE_TRANSFER_IN_MESSAGE_VIEW || cType == FILE_TRANSFER_OUT_MESSAGE_VIEW) {
                                            val chatMsgType = chatMsg.messageType
                                            var isSafeDel = ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE == chatMsgType

                                            // Received or Sent file is in chatHistory fileRecord
                                            /*
                                             * Last received file does not get updated into the FileRecord if delete performed immediately after received.
                                            // if (isSafeDel)
                                            //    file = ((FileHttpDownloadConversation) chatListAdapter.getFileXfer(cPos)).getXferFile();
                                            */
                                            if (chatMsg.fileRecord != null) {
                                                file = chatMsg.fileRecord!!.file
                                                isSafeDel = FileRecord.IN == chatMsg.fileRecord!!.direction
                                                // Not safe, the tmp file may be used for multiple send instances
                                                // (file.getPath().contains("/tmp/"):
                                            }
                                            else if (chatListAdapter!!.getFileName(cPos).also { file = it } == null) {
                                                file = File(chatMsg.message!!)
                                            }

                                            // always include any in/out "voice-" file to be deleted
                                            if (file!!.exists() && (isSafeDel || file!!.name.startsWith("voice-"))) {
                                                msgFilesDel.add(file!!)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        i++
                    }
                    // Timber.d("Transfer file message delete msgUid: %s; files: %s", msgUidDel, msgFilesDel);
                    eraseEntityChatHistory(mChatActivity!!,
                        chatPanel!!.chatSession!!.descriptor!!, msgUidDel, msgFilesDel)
                    mode.finish()
                    true
                }
                else -> false
            }
        }

        // Called when the action mActionMode is created; startActionMode() was called
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            // Inflate the menu for the CAB
            val inflater = mode.menuInflater
            inflater.inflate(R.menu.chat_msg_share_menu, menu)
            headerCount = chatListView.headerViewsCount
            mEdit = menu.findItem(R.id.chat_message_edit)
            mQuote = menu.findItem(R.id.chat_message_quote)
            mForward = menu.findItem(R.id.chat_message_forward)
            mCopy = menu.findItem(R.id.chat_message_copy)
            mSelectAll = menu.findItem(R.id.select_all)
            isMultiChoiceMode = true
            return true
        }

        // Called each time the action mActionMode is shown. Always called after onCreateActionMode,
        // but may be called multiple times if the mActionMode is invalidated.
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            // Here you can perform updates to the CAB due to an invalidate() request
            // Return false if nothing is done
            return false
        }

        // Called when the user exits the action mActionMode
        override fun onDestroyActionMode(mode: ActionMode) {
            // Here you can make any necessary updates to the activity when
            // the CAB is removed. By default, selected items are deselected/unchecked.
            // ActionMode mActionMode = null;
            isMultiChoiceMode = false
        }
    }

    /**
     * Refresh avatar and globals status display on change.
     */
    private val globalStatusListener = object : EventListener<PresenceStatus?> {
        override fun onChangeEvent(eventObject: PresenceStatus?) {
            if (chatListAdapter != null) chatListAdapter!!.localAvatarOrStatusChanged()
        }
    }

    private fun getContact(sender: String?): Contact? {
        if (StringUtils.isEmpty(sender)) return null
        val presenceOpSet = mProvider!!.getOperationSet(OperationSetPersistentPresence::class.java) as OperationSetPersistentPresenceJabberImpl?
        return presenceOpSet!!.findContactByID(XmppStringUtils.parseBareJid(sender))
    }

    /**
     * Call from file transfer UI to ensure the full viewHolder is visiable after an update
     */
    fun scrollToBottom() {
        if (chatListAdapter != null) {
            val lastItem = chatListAdapter!!.count - 1
            chatListView.setSelection(lastItem)
        }
    }

    /**
     * The ChatListAdapter is a container with rows of send and received messages, file transfer
     * status information and system information.
     */
    inner class ChatListAdapter : BaseAdapter(), ChatSessionListener, ContactPresenceStatusListener, ChatStateNotificationsListener {
        /**
         * The list of chat message displays. All access and modification of this list must be
         * done on the UI thread.
         */
        private val messages = ArrayList<MessageDisplay>()

        // A mop reference of DisplayMessage position (index) to the viewHolder
        private val viewHolders = Hashtable<Int, MessageViewHolder>()

        // A map reference of msgUuid to the DisplayMessage position.
        // Continue get updated when messages are deleted/refresh in getView().
        private val msgUuid2Idx = Hashtable<String?, Int?>()

        /**
         * Counter used to generate row ids.
         */
        private var idGenerator = 0

        /**
         * HTML image getter.
         */
        private val imageGetter = HtmlImageGetter()

        /**
         * Note: addMessageImpl method must only be processed on UI thread.
         * Pass the message to the `ChatListAdapter` for processing;
         * appends it at the end or merge it with the last consecutive message.
         *
         * It creates a new message view holder if this is first message or if this is a new
         * message sent/received i.e. non-consecutive.
         */
        private fun addMessageImpl(newMessage: ChatMessage) {
            runOnUiThread {
                if (chatListAdapter == null) {
                    Timber.w("Add message handled, when there's no adapter - possibly after onDetach()")
                    return@runOnUiThread
                }

                // Auto enable Omemo option on receive omemo encrypted messages and view is in focus
                if (primarySelected && IMessage.ENCRYPTION_OMEMO == newMessage.encryptionType
                        && !chatPanel!!.isOmemoChat) {
                    mCryptoFragment!!.setChatType(MSGTYPE_OMEMO)
                }

                // Create a new message view holder only if message is non-consecutive i.e non-merged message
                val msgDisplay: MessageDisplay
                var lastMsgIdx = getLastMessageIdx(newMessage)
                val lastMsg = if (lastMsgIdx != -1) chatListAdapter!!.getMessage(lastMsgIdx) else null
                if (lastMsg == null || !lastMsg.isConsecutiveMessage(newMessage)) {
                    msgDisplay = MessageDisplay(newMessage)
                    messages.add(msgDisplay)
                    lastMsgIdx++

                    // Update street view map location if the view is in focus.
                    if (mSVPStarted && msgDisplay.hasLatLng) {
                        mSVP = svpApi!!.svpHandler(mSVP!!, msgDisplay.mLocation!!)
                    }
                }
                else {
                    // Consecutive message (including corrected message); proceed to update the viewHolder only
                    msgDisplay = messages[lastMsgIdx]
                    msgDisplay.update(lastMsg.mergeMessage(newMessage))
                    val viewHolder = viewHolders[lastMsgIdx]
                    if (viewHolder != null) {
                        msgDisplay.getBody(viewHolder.messageView)
                    }
                }
                // Timber.e("add Message Impl#: %s %s (%s)", lastMsgIdx, newMessage.getMessageUID(), msgDisplay.getChatMessage().getMessageUID());
                /*
                 * List must be scrolled manually, when android:transcriptMode="normal" is set
                 * Must notifyDataSetChanged to invalidate/refresh display contents; else new content may partial be hidden.
                 */
                chatListAdapter!!.notifyDataSetChanged()
                chatListView.setSelection(lastMsgIdx + chatListView.headerViewsCount)
            }
        }

        /**
         * Inserts given `CopyOnWriteArrayList` of `ChatMessage` at the beginning of the list.
         * synchronized to avoid java.util.ConcurrentModificationException on receive history messages
         * - seems still happen so use CopyOnWriteArrayList at ChanPanel#LoadHistory()
         *
         * List<ChatMessage> chatMessages = new CopyOnWriteArrayList<>() to avoid ConcurrentModificationException
         *
         * @param chatMessages the CopyOnWriteArrayList of `ChatMessage` to prepend.
        </ChatMessage> */
        @Synchronized
        fun prependMessages(chatMessages: List<ChatMessage>) {
            if (chatMessages.isEmpty()) {
                return
            }
            val newMessageList = ArrayList<MessageDisplay>()
            var previous: MessageDisplay? = null
            for (next in chatMessages) {
                if (previous == null || !previous.chatMessage.isConsecutiveMessage(next)) {
                    previous = MessageDisplay(next)
                    newMessageList.add(previous)
                }
                else {
                    // Merge the message and update the object in the list
                    previous.update(previous.chatMessage.mergeMessage(next))
                }
            }
            messages.addAll(0, newMessageList)
        }

        /**
         * Finds index of the message that will handle `newMessage` merging process (usually just the last one).
         * If the `newMessage` is a correction message, then the last message of the same type will be returned.
         *
         * @param newMessage the next message to be merged into the adapter.
         *
         * @return index of the message that will handle `newMessage` merging process. If
         * `newMessage` is a correction message, then the last message of the same type will be returned.
         */
        private fun getLastMessageIdx(newMessage: ChatMessage): Int {
            // If it's not a correction message then just return the last one
            if (newMessage.correctedMessageUID == null) return chatListAdapter!!.count - 1

            // Search for the same type
            val msgType = newMessage.messageType
            for (i in count - 1 downTo 0) {
                val candidate = getMessage(i)
                if (candidate != null && candidate.messageType == msgType) {
                    return i
                }
            }
            return -1
        }

        /**
         * {@inheritDoc}
         */
        override fun getCount(): Int {
            return messages.size
        }

        /**
         * Call after the user selected messages are deleted from the DB. Both the ChatFragment UI
         * and the ChatPanel#msgCache are updated So they are all in sync. Remove the deleted
         * messages in reverse order to retain the list index for subsequence reference.
         *
         * @param deletedUUIDs List of message UUID to be deleted.
         */
        fun onClearMessage(deletedUUIDs: List<String>?) {
            // Null signify doEraseAllEntityHistory has been performed i.e. erase all history messages
            if (deletedUUIDs == null) {
                messages.clear()
                msgUuid2Idx.clear()
            }
            else {
                // int msgSize = messages.size();
                var idx = deletedUUIDs.size
                while (idx-- > 0) {
                    val msgUuid = deletedUUIDs[idx]
                    // Remove deleted message from ChatPanel#msgCache
                    chatPanel!!.updateCacheMessage(msgUuid, null)

                    // Remove deleted message from display messages UI; merged messages may return null.
                    // Merged messages view is deleted using the root msgUuid.
                    val row = msgUuid2Idx[msgUuid]
                    if (row != null) {
                        messages.remove(getMessageDisplay(row))
                        msgUuid2Idx.remove(msgUuid)
                    }
                    else {
                        Timber.e("No message for delete: %s => %s", idx, msgUuid)
                    }
                }
                // Timber.d("Clear Message: %s => %s (%s)", msgSize, messages.size(), deletedUUIDs.size());
            }
            notifyDataSetChanged()
        }

        /**
         * {@inheritDoc}
         */
        override fun getItem(pos: Int): Any {
            return (if (pos in 0 until count) messages[pos] else null)!!
        }

        /*
         * java.lang.ArrayIndexOutOfBoundsException:
         * at java.util.ArrayList.get (ArrayList.java:439)
         * at org.atalk.hmos.gui.chat.ChatFragment$ChatListAdapter.getItem (ChatFragment.java:1118)
         */
        fun getMessage(pos: Int): ChatMessage? {
            return if (getItem(pos) is MessageDisplay) {
                (getItem(pos) as MessageDisplay).chatMessage
            }
            else {
                null
            }
        }

        private fun getMessageDisplay(pos: Int): MessageDisplay {
            return getItem(pos) as MessageDisplay
        }

        val messageDisplays: List<MessageDisplay>
            get() = messages

        /**
         * {@inheritDoc}
         */
        override fun getItemId(pos: Int): Long {
            return messages[pos].id.toLong()
        }

        fun getMessagePosFromDate(mDate: Date?): Int {
            var pos = -1
            if (mDate != null) {
                var i = 0
                while (i < messages.size) {
                    val chatMessage = getMessage(i)
                    if (chatMessage != null) {
                        val msgDate = chatMessage.date
                        if (msgDate.after(mDate) || msgDate == mDate) {
                            pos = if (i > 0) --i else 0
                            break
                        }
                    }
                    i++
                }
            }
            return pos
        }

        /**
         * Must update both the DisplayMessage and ChatPanel#msgCache delivery status;
         * to ensure onResume the cacheMessages have the latest delivery status.
         *
         * @param msgId the associated chat message UUid
         * @param receiptStatus Delivery status to be updated
         *
         * @return the viewHolder of which the content being affected (not use currently).
         */
        fun updateMessageDeliveryStatusForId(msgId: String?, receiptStatus: Int): MessageViewHolder? {
            if (TextUtils.isEmpty(msgId)) return null
            var index = messages.size
            while (index-- > 0) {
                val message = messages[index]
                val msgIds = message.serverMsgId
                if (msgIds != null && msgIds.contains(msgId!!)) {
                    // Update MessageDisplay to take care when view is refresh e.g. new message arrived or scroll
                    val chatMessage = message.updateDeliveryStatus(msgId, receiptStatus)

                    // Update ChatMessage in msgCache as well
                    chatPanel!!.updateCacheMessage(msgId, receiptStatus)
                    val viewHolder = viewHolders[index]
                    if (viewHolder != null) {
                        // Need to update merged messages new receipt statuses
                        if (chatMessage is MergedMessage) {
                            message.getBody(viewHolder.messageView)
                        }
                        setMessageReceiptStatus(viewHolder.msgReceiptView, receiptStatus)
                    }
                    return viewHolder
                }
            }
            return null
        }

        /**
         * Update the file transfer status in the msgCache; must do this else file transfer will be
         * reactivated onResume chat. Also important if historyLog is disabled.
         *
         * @param msgUuid ChatMessage uuid
         * @param status File transfer status
         * @param fileName the downloaded fileName
         * @param msgType File transfer type see ChatMessage MESSAGE_FILE_
         */
        fun updateMessageFTStatus(msgUuid: String, status: Int, fileName: String?, encType: Int, msgType: Int) {
            // Remove deleted message from display messages; merged messages may return null
            val row = msgUuid2Idx[msgUuid]
            if (row != null) {
                val chatMessage = messages[row].chatMessage as ChatMessageImpl
                chatMessage.updateFTStatus(chatPanel!!.descriptor, msgUuid, status, fileName,
                    encType, msgType, chatMessage.messageDir)
                // Timber.e("File record updated for %s => %s", msgUuid, status);

                // Update FT Record in ChatPanel#msgCache as well
                chatPanel!!.updateCacheFTRecord(msgUuid, status, fileName, encType, msgType)
            }
            else {
                Timber.e("File record not found: %s", msgUuid)
            }
        }

        // Not use currently
        fun updateMessageFTStatus(msgUuid: String, status: Int) {
            // Remove deleted message from display messages; merged messages may return null
            val row = msgUuid2Idx[msgUuid]
            if (row != null) {
                val chatMessage = messages[row].chatMessage as ChatMessageImpl
                chatMessage.updateFTStatus(msgUuid, status)
            }
        }

        fun getXferStatus(pos: Int): Int {
            return if (pos < messages.size) {
                messages[pos].chatMessage.xferStatus
            }
            else FileTransferStatusChangeEvent.CANCELED

            // assuming CANCELED if not found
        }

        fun setFileXfer(pos: Int, mFileXfer: Any?) {
            messages[pos].fileXfer = mFileXfer
        }

        fun getFileName(pos: Int): File? {
            return messages[pos].sFile
        }

        fun setFileName(pos: Int, file: File?) {
            messages[pos].sFile = file
        }

        private fun getFileXfer(pos: Int): Any? {
            return messages[pos].fileXfer
        }

        override fun getViewTypeCount(): Int {
            return VIEW_TYPE_MAX
        }

        /*
         * return the view Type of the give position
         */
        override fun getItemViewType(position: Int): Int {
            val chatMessage = getMessage(position)
            return when (chatMessage!!.messageType) {
                ChatMessage.MESSAGE_IN, ChatMessage.MESSAGE_MUC_IN, ChatMessage.MESSAGE_LOCATION_IN, ChatMessage.MESSAGE_STATUS -> INCOMING_MESSAGE_VIEW
                ChatMessage.MESSAGE_OUT, ChatMessage.MESSAGE_LOCATION_OUT, ChatMessage.MESSAGE_MUC_OUT -> {
                    val sessionCorrUID = chatPanel!!.correctionUID
                    val msgCorrUID = chatMessage.getUidForCorrection()
                    if (sessionCorrUID != null && sessionCorrUID == msgCorrUID) {
                        CORRECTED_MESSAGE_VIEW
                    }
                    else {
                        OUTGOING_MESSAGE_VIEW
                    }
                }
                ChatMessage.MESSAGE_SYSTEM -> SYSTEM_MESSAGE_VIEW
                ChatMessage.MESSAGE_ERROR -> ERROR_MESSAGE_VIEW
                ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE, ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD -> FILE_TRANSFER_IN_MESSAGE_VIEW
                ChatMessage.MESSAGE_FILE_TRANSFER_SEND, ChatMessage.MESSAGE_STICKER_SEND -> FILE_TRANSFER_OUT_MESSAGE_VIEW
                ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY -> {
                    val fileRecord = chatMessage.fileRecord
                    if (fileRecord == null || FileRecord.IN == fileRecord.direction) {
                        FILE_TRANSFER_IN_MESSAGE_VIEW
                    }
                    else {
                        FILE_TRANSFER_OUT_MESSAGE_VIEW
                    }
                }
                else -> INCOMING_MESSAGE_VIEW
            }
        }

        /**
         * Hack required to capture TextView(message body) clicks, when `LinkMovementMethod` is set.
         */
        private val msgClickAdapter = OnClickListener { v ->
            if (chatController != null && v.tag is Int) {
                val pos = v.tag as Int
                if (!isMultiChoiceMode && !checkHttpDownloadLink(pos)) {
                    if (chatPanel!!.isChatTtsEnable) {
                        val chatMessage = getMessage(pos - chatListView.headerViewsCount)
                        chatPanel!!.ttsSpeak(chatMessage!!)
                    }
                    else chatController!!.onItemClick(chatListView, v, pos, -1 /* id not used */)
                }
            }
        }

        /**
         * Method to check for Http download file link
         */
        private fun checkHttpDownloadLink(position: Int): Boolean {
            // Position must be aligned to the number of header views included
            val cPos = position - chatListView.headerViewsCount
            val isMsgIn = INCOMING_MESSAGE_VIEW == getItemViewType(cPos)
            val chatMessage = chatListAdapter!!.getMessage(cPos)
            if (chatMessage != null) {
                val body = chatMessage.message
                if (isMsgIn && isHttpFileDnLink(body)) {
                    // Local cache update
                    (chatMessage as ChatMessageImpl).messageType = ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD
                    notifyDataSetChanged()
                    return true
                }
            }
            return false
        }

        /**
         * {@inheritDoc}
         */
        override fun getView(position: Int, convertView_: View?, parent: ViewGroup): View {
            var convertView = convertView_
            val viewType = getItemViewType(position)
            val clickedPos = position + chatListView.headerViewsCount
            var messageViewHolder: MessageViewHolder
            val msgDisplay = getMessageDisplay(position)
            val chatMessage = msgDisplay.chatMessage
            val msgUuid = chatMessage.getMessageUID()
            // Update pos changed due to deletions; must not have any new entry added here => error
            if (msgUuid != null && msgUuid2Idx.put(msgUuid, position) == null) {
                Timber.e("Failed updating msgUuid2Idx with msgUuid: %s = %s", position, msgUuid)
            }

            // File Transfer convertView creation
            if (viewType == FILE_TRANSFER_IN_MESSAGE_VIEW || viewType == FILE_TRANSFER_OUT_MESSAGE_VIEW) {
                // Reuse convert view if available and valid
                var init = false
                if (convertView == null) {
                    messageViewHolder = MessageViewHolder()
                    init = true
                }
                else {
                    messageViewHolder = convertView.tag as MessageViewHolder
                    if (messageViewHolder.viewType != viewType) {
                        messageViewHolder = MessageViewHolder()
                        init = true
                    }
                }
                val opSet: OperationSetFileTransfer?
                val fileRecord: FileRecord?
                val request: IncomingFileTransferRequest?
                val fileName: String?
                val sendFrom: String?
                val sendTo: String?
                val date: Date
                var viewTemp: View? = null
                val inflater = mChatActivity!!.layoutInflater
                when (val msgType = chatMessage.messageType) {
                    ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE -> {
                        opSet = chatMessage.opSetFT
                        request = chatMessage.ftRequest
                        sendFrom = chatMessage.sender
                        date = chatMessage.date
                        var fileXferR = getFileXfer(position) as FileReceiveConversation?
                        if (fileXferR == null) {
                            fileXferR = FileReceiveConversation.newInstance(currentChatFragment, sendFrom,
                                opSet!!, request!!, date)
                            setFileXfer(position, fileXferR)
                        }
                        viewTemp = fileXferR.receiveFileConversionForm(inflater, messageViewHolder, parent, position, init)
                    }
                    ChatMessage.MESSAGE_FILE_TRANSFER_SEND, ChatMessage.MESSAGE_STICKER_SEND -> {
                        fileName = chatMessage.message!!
                        sendTo = chatMessage.sender
                        var fileXferS = getFileXfer(position) as FileSendConversation?
                        if (fileXferS == null) {
                            fileXferS = FileSendConversation.newInstance(currentChatFragment, msgUuid, sendTo, fileName,
                                mChatType, msgType == ChatMessage.MESSAGE_STICKER_SEND)
                            setFileXfer(position, fileXferS)
                        }
                        viewTemp = fileXferS.sendFileConversationForm(inflater, messageViewHolder, parent, position, init)
                    }
                    ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY -> {
                        fileRecord = msgDisplay.fileRecord
                        val fileXferH = FileHistoryConversation.newInstance(currentChatFragment,
                            fileRecord!!, chatMessage)
                        viewTemp = fileXferH.fileHistoryConversationForm(inflater, messageViewHolder, parent, init)
                    }
                    ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD -> {
                        sendFrom = chatMessage.sender
                        date = chatMessage.date
                        var httpFileTransfer = chatMessage.httpFileTransfer
                        if (httpFileTransfer == null) {
                            val dnLink = chatMessage.message
                            if (isHttpFileDnLink(dnLink)) {
                                val contact = getContact(sendFrom)
                                httpFileTransfer = HttpFileDownloadJabberImpl(contact!!, msgUuid, dnLink!!)

                                // Save a copy into the chatMessage for later retrieval
                                (chatMessage as ChatMessageImpl).httpFileTransfer = httpFileTransfer
                            }
                        }
                        var fileDownloadForm = getFileXfer(position) as FileHttpDownloadConversation?
                        if (fileDownloadForm == null && httpFileTransfer != null) {
                            fileDownloadForm = FileHttpDownloadConversation.newInstance(currentChatFragment,
                                sendFrom, httpFileTransfer, date)
                        }
                        if (fileDownloadForm != null) {
                            setFileXfer(position, fileDownloadForm)
                            viewTemp = fileDownloadForm.httpFileDownloadConversionForm(inflater, messageViewHolder,
                                parent, position, init)
                        }
                    }
                }
                if (init) {
                    convertView = viewTemp
                    if (convertView != null) convertView.tag = messageViewHolder
                }
                messageViewHolder.viewType = viewType
            }
            else {
                if (convertView == null) {
                    messageViewHolder = MessageViewHolder()
                    convertView = inflateViewForType(viewType, messageViewHolder, parent)
                }
                else {
                    // Convert between OUTGOING and CORRECTED
                    messageViewHolder = convertView.tag as MessageViewHolder
                    val vType = messageViewHolder.viewType
                    if ((vType == CORRECTED_MESSAGE_VIEW || vType == OUTGOING_MESSAGE_VIEW) && vType != viewType) {
                        messageViewHolder = MessageViewHolder()
                        convertView = inflateViewForType(viewType, messageViewHolder, parent)
                    }
                }
                // Set position used for click handling from click adapter
                // int clickedPos = position + chatListView.getHeaderViewsCount();
                messageViewHolder.messageView.tag = clickedPos
                if (messageViewHolder.outgoingMessageHolder != null) {
                    messageViewHolder.outgoingMessageHolder!!.tag = clickedPos
                }

                if (messageViewHolder.viewType == INCOMING_MESSAGE_VIEW || messageViewHolder.viewType == OUTGOING_MESSAGE_VIEW || messageViewHolder.viewType == CORRECTED_MESSAGE_VIEW) {
                    val sender = chatMessage.senderName
                    messageViewHolder.mSender = sender!!
                    if (messageViewHolder.viewType == INCOMING_MESSAGE_VIEW) {
                        messageViewHolder.jidView.text = "$sender:"
                        setEncState(messageViewHolder.encStateView, msgDisplay.encryption)
                    }
                    if (messageViewHolder.viewType == OUTGOING_MESSAGE_VIEW
                            || messageViewHolder.viewType == CORRECTED_MESSAGE_VIEW) {
                        setEncState(messageViewHolder.encStateView, msgDisplay.encryption)
                        setMessageReceiptStatus(messageViewHolder.msgReceiptView, msgDisplay.receiptStatus)
                    }
                    updateStatusAndAvatarView(messageViewHolder, sender)
                    if (msgDisplay.hasLatLng()) {
                        messageViewHolder.showMapButton.visibility = VISIBLE
                        messageViewHolder.showMapButton.setOnClickListener(msgDisplay)
                        messageViewHolder.showMapButton.setOnLongClickListener(msgDisplay)
                    }
                    else {
                        messageViewHolder.showMapButton.visibility = GONE
                    }
                    messageViewHolder.timeView.text = msgDisplay.getDateStr()
                }

                // check and make link clickable if it is not an HTTP file link
                val body = msgDisplay.getBody(messageViewHolder.messageView) as Spannable?

                // System messages must use setMovementMethod to make the link clickable
                if (messageViewHolder.viewType == SYSTEM_MESSAGE_VIEW) {
                    messageViewHolder.messageView.movementMethod = LinkMovementMethod.getInstance()
                }
                else if (!TextUtils.isEmpty(body) && !body.toString().matches(Regex("(?s)^aesgcm:.*"))) {
                    // Set up link movement method i.e. make all links in TextView clickable
                    messageViewHolder.messageView.movementMethod = LinkMovementMethod.getInstance()
                }

                // getBody() will return null if there is img src tag to be updated via async
                // if (body != null)
                //     messageViewHolder.messageView.setText(body);

                // Set clicks adapter for re-edit last outgoing message OR HTTP link download support
                messageViewHolder.messageView.setOnClickListener(msgClickAdapter)
            }
            viewHolders[position] = messageViewHolder
            return convertView!!
        }

        private fun inflateViewForType(viewType: Int, messageViewHolder: MessageViewHolder, parent: ViewGroup): View {
            messageViewHolder.viewType = viewType
            val inflater = mChatActivity!!.layoutInflater
            val convertView: View
            if (viewType == INCOMING_MESSAGE_VIEW) {
                convertView = inflater.inflate(R.layout.chat_incoming_row, parent, false)
                messageViewHolder.avatarView = convertView.findViewById(R.id.incomingAvatarIcon)
                messageViewHolder.statusView = convertView.findViewById(R.id.incomingStatusIcon)
                messageViewHolder.jidView = convertView.findViewById(R.id.incomingJidView)
                messageViewHolder.messageView = convertView.findViewById(R.id.incomingMessageView)
                messageViewHolder.encStateView = convertView.findViewById(R.id.encStateView)
                messageViewHolder.timeView = convertView.findViewById(R.id.incomingTimeView)
                messageViewHolder.chatStateView = convertView.findViewById(R.id.chatStateImageView)
                messageViewHolder.showMapButton = convertView.findViewById(R.id.showMapButton)

                // Option available for conference session
                if (mChatMetaContact == null) {
                    messageViewHolder.avatarView.setOnClickListener { chatController!!.insertTo(messageViewHolder.mSender) }
                    messageViewHolder.avatarView.setOnLongClickListener { v: View ->
                        showPopupMenuForContact(v, messageViewHolder.mSender)
                        true
                    }
                }
            }
            else if (viewType == OUTGOING_MESSAGE_VIEW || viewType == CORRECTED_MESSAGE_VIEW) {
                convertView = if (viewType == OUTGOING_MESSAGE_VIEW) {
                    inflater.inflate(R.layout.chat_outgoing_row, parent, false)
                }
                else {
                    inflater.inflate(R.layout.chat_corrected_row, parent, false)
                }
                messageViewHolder.avatarView = convertView.findViewById(R.id.outgoingAvatarIcon)
                messageViewHolder.statusView = convertView.findViewById(R.id.outgoingStatusIcon)
                messageViewHolder.messageView = convertView.findViewById(R.id.outgoingMessageView)
                messageViewHolder.msgReceiptView = convertView.findViewById(R.id.msg_delivery_status)
                messageViewHolder.encStateView = convertView.findViewById(R.id.encStateView)
                messageViewHolder.timeView = convertView.findViewById(R.id.outgoingTimeView)
                messageViewHolder.outgoingMessageHolder = convertView.findViewById(R.id.outgoingMessageHolder)
                messageViewHolder.showMapButton = convertView.findViewById(R.id.showMapButton)
            }
            else {
                // System or error view
                convertView = inflater.inflate(if (viewType == SYSTEM_MESSAGE_VIEW) R.layout.chat_system_row else R.layout.chat_error_row, parent, false)
                messageViewHolder.messageView = convertView.findViewById(R.id.messageView)
            }
            convertView.tag = messageViewHolder
            return convertView
        }

        /**
         * Inflates chatRoom Item popup menu.
         * Avoid using android contextMenu (in fragment) - truncated menu list
         *
         * @param avatar click view.
         * @param contactJid an instance of ChatRoomWrapper.
         */
        private fun showPopupMenuForContact(avatar: View, contactJid: String?) {
            if (contactJid == null) return
            val nickName = contactJid.replace("(\\w+)[:|@].*".toRegex(), "$1")
            val chatRoom = (chatPanel!!.descriptor as ChatRoomWrapper).chatRoom!!
            var mOccupant: ChatRoomMember? = null
            val mUserRole = chatRoom.getUserRole()
            val occupants = chatRoom.getMembers()
            for (occupant in occupants) {
                if (nickName == occupant.getNickName()) {
                    mOccupant = occupant
                    break
                }
            }
            if (mOccupant == null) return
            val mMemberRole = mOccupant.getRole()

            // Inflate chatRoom list popup menu
            val popup = PopupMenu(mContext, avatar)
            val menu = popup.menu
            popup.menuInflater.inflate(R.menu.chatroom_member_ctx_menu, menu)
            val menuManage = menu.findItem(R.id.chatroom_manage_privilege)
            val menuKick = menu.findItem(R.id.chatroom_kick)
            menuManage.isVisible = ChatRoomMemberRole.OWNER == mUserRole
            menuKick.isVisible = (ChatRoomMemberRole.OWNER == mUserRole
                    || ChatRoomMemberRole.ADMINISTRATOR == mUserRole)
                    && ChatRoomMemberRole.OWNER != mMemberRole
                    && ChatRoomMemberRole.ADMINISTRATOR != mMemberRole

            if (menuManage.isVisible) {
                menuManage.setTitle(
                    if (ChatRoomMemberRole.OWNER == mMemberRole)
                        R.string.service_gui_CR_MEMBER_REVOKE_OWNER_PRIVILEGE
                    else
                        R.string.service_gui_CR_MEMBER_GRANT_OWNER_PRIVILEGE)
            }

            val finalOccupant = mOccupant
            popup.setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.chatroom_start_im -> {
                        val contact = getContact(contactJid)
                        val chatIntent = getChatIntent(contact)
                        if (chatIntent != null) {
                            startActivity(chatIntent)
                        }
                        else {
                            aTalkApp.showToastMessage(R.string.service_gui_SEND_MESSAGE_NOT_SUPPORTED, contactJid)
                            // Show ContactList UI for user selection if groupChat contact is anonymous.
                            val intent = Intent(mContext, aTalk::class.java)
                            intent.action = Intent.ACTION_SENDTO
                            startActivity(intent)
                        }
                    }
                    R.id.chatroom_manage_privilege -> if (ChatRoomMemberRole.OWNER === finalOccupant.getRole()) {
                        chatRoom.revokeAdmin(contactJid)
                    }
                    else {
                        chatRoom.grantOwnership(contactJid)
                    }
                    R.id.chatroom_kick -> try {
                        chatRoom.kickParticipant(finalOccupant, "")
                    } catch (e: OperationFailedException) {
                        // throw new RuntimeException(e);
                        aTalkApp.showToastMessage(e.message)
                    }
                }
                true
            }
            popup.show()
        }

        /**
         * Updates status and avatar views on given `MessageViewHolder`.
         *
         * @param viewHolder the `MessageViewHolder` to update.
         * @param sender the `ChatMessage` sender.
         */
        private fun updateStatusAndAvatarView(viewHolder: MessageViewHolder, sender: String?) {
            var avatar: Drawable? = null
            var status: Drawable? = null
            if (viewHolder.viewType == INCOMING_MESSAGE_VIEW) {
                // FFR: NPE
                if (chatPanel == null) return
                val descriptor = chatPanel!!.chatSession!!.descriptor
                if (descriptor is MetaContact) {
                    avatar = MetaContactRenderer.getAvatarDrawable(descriptor)
                    status = MetaContactRenderer.getStatusDrawable(descriptor)
                }
                else {
                    if (sender != null) {
                        val contact = getContact(sender)
                        // If we have found a contact the we set also its avatar and status.
                        if (contact != null) {
                            avatar = MetaContactRenderer.getCachedAvatarFromBytes(contact.image)
                            val pStatus = contact.presenceStatus
                            status = MetaContactRenderer.getCachedAvatarFromBytes(getContactStatusIcon(pStatus))
                        }
                    }
                }
            }
            else if (viewHolder.viewType == Companion.OUTGOING_MESSAGE_VIEW
                    || viewHolder.viewType == CORRECTED_MESSAGE_VIEW) {
                val loginRenderer = AndroidGUIActivator.loginRenderer
                avatar = loginRenderer?.getLocalAvatarDrawable(mProvider)
                status = loginRenderer?.getLocalStatusDrawable()
            }
            else {
                // Avatar and status are present only in outgoing or incoming message views
                return
            }
            setAvatar(viewHolder.avatarView, avatar)
            setStatus(viewHolder.statusView, status)
        }

        override fun messageDelivered(evt: MessageDeliveredEvent) {
            val contact = evt.getContact()
            val metaContact = AndroidGUIActivator.contactListService.findMetaContactByContact(contact)
            val msg = ChatMessageImpl.getMsgForEvent(evt)
            Timber.log(TimberLog.FINER, "MESSAGE DELIVERED to contact: %s", contact.address)
            if (metaContact != null && metaContact == chatPanel!!.metaContact) {
                Timber.log(TimberLog.FINER, "MESSAGE DELIVERED: process message to chat for contact: %s MESSAGE: %s",
                    contact.address, msg.mMessage)
                addMessageImpl(msg)
            }
        }

        override fun messageDeliveryFailed(evt: MessageDeliveryFailedEvent) {
            // Do nothing, handled in ChatPanel
        }

        override fun messageReceived(evt: MessageReceivedEvent) {
            // ChatPanel broadcasts all received messages to all listeners. Must filter and display
            // messages only intended for this chatFragment.
            val protocolContact = evt.getSourceContact()
            if (mChatMetaContact!!.containsContact(protocolContact)) {
                val msg = ChatMessageImpl.getMsgForEvent(evt)
                addMessageImpl(msg)
            }
            else {
                Timber.log(TimberLog.FINER, "MetaContact not found for protocol contact: %s", protocolContact)
            }
        }

        // Add a new message directly without an event triggered.
        override fun messageAdded(msg: ChatMessage) {
            if (ChatMessage.MESSAGE_STATUS == msg.messageType) {
                val descriptor = chatPanel!!.chatSession!!.descriptor
                if (descriptor is ChatRoomWrapper &&
                        descriptor.isRoomStatusEnable) addMessageImpl(msg)
            }
            else {
                addMessageImpl(msg)
            }
        }

        /**
         * Indicates a contact has changed its status.
         */
        override fun contactPresenceStatusChanged(evt: ContactPresenceStatusChangeEvent) {
            val sourceContact = evt.getSourceContact()
            Timber.d("Contact presence status changed: %s", sourceContact.address)
            if (chatPanel!!.metaContact != null && chatPanel!!.metaContact!!.containsContact(sourceContact)) {
                UpdateStatusTask().execute()
            }
        }

        override fun chatStateNotificationDeliveryFailed(event: ChatStateNotificationEvent) {}
        override fun chatStateNotificationReceived(event: ChatStateNotificationEvent) {
            // Timber.d("Chat state notification received: %s", evt.getChatDescriptor().toString());
            handleChatStateNotificationReceived(event, this@ChatFragment)
        }

        /**
         * Updates all avatar and status on outgoing messages rows.
         */
        fun localAvatarOrStatusChanged() {
            runOnUiThread {
                for (i in 0 until chatListView.childCount) {
                    val row = chatListView.getChildAt(i)
                    val viewHolder = row.tag as MessageViewHolder?
                    if (viewHolder != null) updateStatusAndAvatarView(viewHolder, null)
                }
            }
        }

        /**
         * Class used to cache processed message contents. Prevents from re-processing on each View display.
         */
        inner class MessageDisplay internal constructor(msg: ChatMessage) : OnClickListener, OnLongClickListener {
            /**
             * Row identifier.
             */
            val id = idGenerator++

            /**
             * Message Receipt Status.
             */
            var receiptStatus: Int
                private set

            /**
             * Message Encryption Type.
             */
            val encryption: Int
            var serverMsgId: String?
                private set

            /**
             * Incoming or outgoing File Transfer object
             */
            var fileXfer: Any?

            /**
             * Save File name
             */
            var sFile: File? = null

            /**
             * Displayed `ChatMessage`
             */
            var chatMessage: ChatMessage

            /**
             * Date string cache
             */
            private var dateStr: String? = null

            /**
             * Message body cache
             */
            private var msgBody: Spanned?

            /**
             * Incoming message has LatLng info
             */
            var hasLatLng = false

            /**
             * double[] values containing Latitude, Longitude and Altitude extract from ChatMessage
             */
            var mLocation: DoubleArray? = null

            /**
             * Creates a new instance of `MessageDisplay` that will be used for displaying given `ChatMessage`.
             *
             * msg the `ChatMessage` that will be displayed by this instance.
             */
            init {
                chatMessage = msg
                msgBody = null
                fileXfer = null
                receiptStatus = msg.receiptStatus
                encryption = msg.encryptionType
                serverMsgId = msg.serverMsgId
                // All system messages do not have UUID i.e. null
                if (msg.getMessageUID() != null) msgUuid2Idx[msg.getMessageUID()] = id
                checkLatLng()
            }

            /**
             * check if the incoming message contain geo "LatLng:" information. Only the first
             * LatLng will be returned if there are multiple LatLng in consecutive messages
             */
            private fun checkLatLng() {
                val str = chatMessage.message
                val msgTye = chatMessage.messageType
                if (!TextUtils.isEmpty(str) && (msgTye == ChatMessage.MESSAGE_IN || msgTye == ChatMessage.MESSAGE_OUT || msgTye == ChatMessage.MESSAGE_MUC_IN || msgTye == ChatMessage.MESSAGE_MUC_OUT)) {
                    var mLoc: String? = null
                    var startIndex = str!!.indexOf("LatLng:")
                    if (startIndex != -1) {
                        str.substring(startIndex + 7).also { mLoc = it }
                    }
                    else {
                        str.indexOf("geo:").also { startIndex = it }
                        if (startIndex != -1) {
                            mLoc = str.split(";")[0].substring(startIndex + 4)
                        }
                    }
                    if (mLoc != null) {
                        try {
                            val location = mLoc!!.split(",")
                            var sLat = location[0].lowercase()
                            sLat = if (sLat.contains("s")) "-" + sLat.replace("[^\\d.]+".toRegex(), "") else sLat.replace("[^\\d.]+".toRegex(), "")
                            var sLng = location[1].lowercase()
                            sLng = if (sLng.contains("w")) "-" + sLng.replace("[^\\d.]+".toRegex(), "") else sLng.replace("[^\\d.]+".toRegex(), "")
                            var sAlt = "0.0"
                            if (location.size == 3) {
                                sAlt = location[2].replace("[^\\d.]+".toRegex(), "")
                            }
                            hasLatLng = true
                            mLocation = doubleArrayOf(sLat.toDouble(), sLng.toDouble(), sAlt.toDouble())
                        } catch (ex: NumberFormatException) {
                            Timber.w("GeoLocationActivity Number Format Exception %s: ", ex.message)
                        }
                    }
                }
            }

            /**
             * Show street map view when user clicks the show map button
             *
             * @param view view
             */
            override fun onClick(view: View) {
                mSVPStarted = true
                svpApi!!.onSVPClick(mChatActivity!!, mLocation!!)
            }

            /**
             * Perform google street and map view playback when user longClick the show map button
             *
             * @param v View
             */
            override fun onLongClick(v: View): Boolean {
                val location = ArrayList<DoubleArray>()
                val smt = messageType
                val displayMessages = messageDisplays
                for (dm in displayMessages) {
                    if (dm.hasLatLng && smt == dm.messageType) {
                        location.add(dm.mLocation!!)
                    }
                }
                if (location.isNotEmpty()) {
                    mSVPStarted = true
                    svpApi!!.onSVPLongClick(mChatActivity!!, location)
                }
                return true
            }

            /**
             * @return `true` if the message has LatLng information
             */
            fun hasLatLng(): Boolean {
                return hasLatLng
            }

            /**
             * Returns formatted date string for the `ChatMessage`.
             *
             * @return formatted date string for the `ChatMessage`.
             */
            fun getDateStr(): String {
                if (dateStr == null) {
                    dateStr = formatDateTime(chatMessage.date)
                }
                return dateStr!!
            }

            val messageType: Int
                get() = chatMessage.messageType
            val fileRecord: FileRecord?
                get() = chatMessage.fileRecord

            /**
             * Process HTML tags with image src as async task, populate the given msgView and return null;
             * Else Returns `Spanned` message body processed for HTML tags.
             *
             * @param msgView the message view container to be populated
             *
             * @return `Spanned` message body if contains no "<img></img>" tag.
             */
            fun getBody(msgView: TextView?): Spanned? {
                var body = chatMessage.message
                if (msgBody == null && !TextUtils.isEmpty(body)) {
                    val hasHtmlTag = body!!.matches(ChatMessage.HTML_MARKUP)
                    val hasImgSrcTag = hasHtmlTag && body.contains("<img")

                    // Convert to Spanned body to support text mark up display
                    // need to replace '\n' with <br/> to avoid stripped off by fromHtml()
                    body = body.replace("\n", "<br/>")
                    if (hasImgSrcTag && msgView != null) {
                        msgView.text = Html.fromHtml(body, XhtmlImageParser(msgView, body), null)
                        // Async will update the text view, so just return null to caller.
                        return null
                    }
                    else {
                        msgBody = Html.fromHtml(body, imageGetter, null)
                    }

                    // Proceed with Linkify process if msgBody contains no HTML tags
                    if (!hasHtmlTag) {
                        try {
                            val urlMatcher = Pattern.compile("\\b[A-Za-z]+://[A-Za-z\\d:./?=]+\\b")
                            Linkify.addLinks((msgBody as Spannable?)!!, urlMatcher, null)

                            // second level of adding links if not aesgcm link
                            if (!msgBody.toString().matches(Regex("(?s)^aesgcm:.*"))) {
                                Linkify.addLinks((msgBody as Spannable?)!!, Linkify.ALL)
                            }
                        } catch (ex: Exception) {
                            Timber.w("Error in Linkify process: %s", msgBody)
                        }
                    }
                    val strBuilder = SpannableStringBuilder(msgBody)
                    val urls = strBuilder.getSpans(0, msgBody!!.length, URLSpan::class.java)
                    for (span in urls) {
                        makeLinkClickable(strBuilder, span)
                    }
                    if (msgView != null) msgView.text = strBuilder
                }
                else {
                    if (msgView != null) msgView.text = msgBody
                }
                return msgBody
            }

            private fun makeLinkClickable(strBuilder: SpannableStringBuilder, urlSpan: URLSpan) {
                val start = strBuilder.getSpanStart(urlSpan)
                val end = strBuilder.getSpanEnd(urlSpan)
                val flags = strBuilder.getSpanFlags(urlSpan)
                val clickable = object : ClickableSpan() {
                    override fun onClick(view: View) {
                        mChatActivity!!.playMediaOrActionView(Uri.parse(urlSpan.url))
                    }
                }
                strBuilder.setSpan(clickable, start, end, flags)
                strBuilder.removeSpan(urlSpan)
            }

            /**
             * Updates this display instance with a new message.
             * Both receiptStatus and serverMsgId of the message will use the new chatMessage
             *
             * @param chatMessage new message content
             */
            fun update(chatMessage: ChatMessage) {
                this.chatMessage = chatMessage
                initDMessageStatus()
                receiptStatus = chatMessage.receiptStatus
                serverMsgId = chatMessage.serverMsgId
            }

            /**
             * Update this display instance for the delivery status for both single and merged messages
             *
             * @param msgId the message Id for which the delivery status has been updated
             * @param deliveryStatus delivery status
             *
             * @return the updated ChatMessage instance
             */
            fun updateDeliveryStatus(msgId: String?, deliveryStatus: Int): ChatMessage {
                if (chatMessage is MergedMessage) {
                    chatMessage = (chatMessage as MergedMessage).updateDeliveryStatus(msgId!!, deliveryStatus)
                }
                else {
                    (chatMessage as ChatMessageImpl).receiptStatus = deliveryStatus
                }
                initDMessageStatus()
                receiptStatus = deliveryStatus
                return chatMessage
            }

            /**
             * Following parameters must be set to null for any update to the ChatMessage.
             * This is to allow rebuild for msgBody and dateStr for view holder display update
             */
            private fun initDMessageStatus() {
                msgBody = null
                dateStr = null
            }
        }
    }

    class MessageViewHolder {
        var viewType = 0
        var outgoingMessageHolder: View? = null
        var chatStateView: ImageView? = null

        lateinit var avatarView: ImageView
        lateinit var statusView: ImageView
        lateinit var jidView: TextView
        lateinit var messageView: TextView
        lateinit var encStateView: ImageView
        lateinit var msgReceiptView: ImageView
        lateinit var timeView: TextView

        // public ImageView arrowDir = null;
        lateinit var stickerView: ImageView
        lateinit var fileIcon: ImageButton
        lateinit var progressBar: ProgressBar
        lateinit var playerView: View
        lateinit var playbackPlay: ImageView
        lateinit var fileAudio: TextView
        lateinit var playbackPosition: TextView
        lateinit var playbackDuration: TextView
        lateinit var playbackSeekBar: SeekBar
        lateinit var showMapButton: Button
        lateinit var cancelButton: Button
        lateinit var retryButton: Button
        lateinit var acceptButton: Button
        lateinit var declineButton: Button
        lateinit var fileLabel: TextView
        lateinit var fileStatus: TextView
        lateinit var fileXferError: TextView
        lateinit var fileXferSpeed: TextView
        lateinit var estTimeRemain: TextView
        var mSender: String = ""
    }
    //    class IdRow2 // need to include in MessageViewHolder for stealth support
    //    {
    //        public int mId;
    //        public View mRow;
    //        public int mCountDownValue;
    //        public boolean deleteFlag;
    //        public boolean mStartCountDown;
    //        public boolean mFileIsOpened;
    //
    //        public IdRow2(int id, View row, int startValue)
    //        {
    //            mId = id;
    //            mRow = row;
    //            mCountDownValue = startValue;
    //            deleteFlag = false;
    //            mStartCountDown = false;
    //            mFileIsOpened = false;
    //        }
    //    }
    /**
     * Loads the history in an asynchronous thread and then adds the history messages to the user interface.
     */
    private open inner class LoadHistoryTask(
            /**
             * Indicates that history is being loaded for the first time.
             */
            private val init: Boolean,
    ) : AsyncTask<Void?, Void?, List<ChatMessage>>() {
        /**
         * Remembers adapter size before new messages were added.
         */
        private var preSize = 0
        override fun onPreExecute() {
            super.onPreExecute()
            header!!.visibility = VISIBLE
            preSize = chatListAdapter!!.count
        }

        protected override fun doInBackground(vararg params: Void?): List<ChatMessage> {
            return chatPanel!!.getHistory(init)
        }

        override fun onPostExecute(result: List<ChatMessage>) {
            super.onPostExecute(result)
            chatListAdapter!!.prependMessages(result)
            header!!.visibility = GONE
            chatListAdapter!!.notifyDataSetChanged()
            loadHistoryTask = null
            val loaded = chatListAdapter!!.count - preSize
            val scrollTo = loaded + scrollFirstVisible
            chatListView.setSelectionFromTop(scrollTo, scrollTopOffset)
        }
    }

    /**
     * Updates the status user interface.
     */
    private open inner class UpdateStatusTask : AsyncTask<Void?, Void?, Void?>() {
        override fun doInBackground(vararg params: Void?): Void? {
            return null
        }

        override fun onPostExecute(result: Void?) {
            super.onPostExecute(result)
            if (chatListView == null || chatPanel == null) {
                return
            }
            for (i in 0 until chatListView.childCount) {
                val chatRowView = chatListView.getChildAt(i)
                val viewHolder = chatRowView.tag as MessageViewHolder?
                if (viewHolder != null && viewHolder.viewType == INCOMING_MESSAGE_VIEW) {
                    val status = MetaContactRenderer.getStatusDrawable(chatPanel!!.metaContact!!)
                    val statusView = viewHolder.statusView
                    setStatus(statusView, status)
                }
            }
        }
    }

    /**
     * Sets the status of the given view.
     *
     * @param statusView the status icon view
     * @param statusDrawable the status drawable
     */
    fun setStatus(statusView: ImageView?, statusDrawable: Drawable?) {
        // File Transfer messageHolder does not have a statusView for update;
        // cmeng - server shut down causing null pointer, why???
        statusView?.setImageDrawable(statusDrawable)
    }

    /**
     * Callback invoked when a new receipt got received. receiptId correspondents to the message ID
     *
     * @param fromJid – the jid that send this receipt
     * @param toJid – the jid which received this receipt
     * @param receiptId – the message ID of the stanza which has been received and this receipt is for. This might be null.
     * @param receipt – the receipt stanza
     */
    override fun onReceiptReceived(fromJid: Jid, toJid: Jid, receiptId: String, receipt: Stanza) {
        runOnUiThread {
            if (chatListAdapter != null) {
                chatListAdapter!!.updateMessageDeliveryStatusForId(receiptId, ChatMessage.MESSAGE_DELIVERY_RECEIPT)
            }
        }
    }

    /**
     * Sets the status of the given view.
     *
     * @param receiptStatusView the encryption state view
     * @param deliveryStatus the encryption
     */
    private fun setMessageReceiptStatus(receiptStatusView: ImageView?, deliveryStatus: Int) {
        runOnUiThread {
            if (receiptStatusView != null) {
                when (deliveryStatus) {
                    ChatMessage.MESSAGE_DELIVERY_NONE -> receiptStatusView.setImageResource(R.drawable.ic_msg_delivery_queued)
                    ChatMessage.MESSAGE_DELIVERY_RECEIPT -> receiptStatusView.setImageResource(R.drawable.ic_msg_delivery_read)
                    ChatMessage.MESSAGE_DELIVERY_CLIENT_SENT -> receiptStatusView.setImageResource(R.drawable.ic_msg_delivery_sent_client)
                    ChatMessage.MESSAGE_DELIVERY_SERVER_SENT -> receiptStatusView.setImageResource(R.drawable.ic_msg_delivery_sent_server)
                }
            }
        }
    }

    /**
     * Sets the status of the given view.
     *
     * @param encStateView the encryption state view
     * @param encType the encryption
     */
    private fun setEncState(encStateView: ImageView?, encType: Int) {
        runOnUiThread {
            when (encType) {
                IMessage.ENCRYPTION_NONE ->
                    encStateView!!.setImageResource(R.drawable.encryption_none)
                IMessage.ENCRYPTION_OMEMO ->
                    encStateView!!.setImageResource(R.drawable.encryption_omemo)
            }
        }
    }

    /**
     * Sets the appropriate chat state notification interface.
     *
     * @param chatState the chat state that should be represented in the view
     */
    fun setChatState(chatState: ChatState?, sender: String?) {
        if (chatStateView == null) {
            return
        }
        runOnUiThread {
            if (chatState != null) {
                val chatStateTextView = chatStateView!!.findViewById<TextView>(R.id.chatStateTextView)
                val chatStateImgView = chatStateView!!.findViewById<ImageView>(R.id.chatStateImageView)
                when (chatState) {
                    ChatState.composing -> {
                        var chatStateDrawable = chatStateImgView.drawable
                        if (chatStateDrawable !is AnimationDrawable) {
                            chatStateImgView.setImageResource(R.drawable.chat_state_drawable)
                            chatStateDrawable = chatStateImgView.drawable
                        }
                        if (!(chatStateDrawable as AnimationDrawable).isRunning) {
                            val animatedDrawable = chatStateDrawable
                            animatedDrawable.isOneShot = false
                            animatedDrawable.start()
                        }
                        chatStateTextView.text = aTalkApp.getResString(R.string.service_gui_CONTACT_COMPOSING, sender)
                    }
                    ChatState.paused -> {
                        chatStateImgView.setImageResource(R.drawable.typing1)
                        chatStateTextView.text = aTalkApp.getResString(R.string.service_gui_CONTACT_PAUSED_TYPING, sender)
                    }
                    ChatState.active -> {
                        chatStateImgView.setImageResource(R.drawable.global_ffc)
                        chatStateTextView.text = aTalkApp.getResString(R.string.service_gui_CONTACT_ACTIVE, sender)
                    }
                    ChatState.inactive -> {
                        chatStateImgView.setImageResource(R.drawable.global_away)
                        chatStateTextView.text = aTalkApp.getResString(R.string.service_gui_CONTACT_INACTIVE, sender)
                    }
                    ChatState.gone -> {
                        chatStateImgView.setImageResource(R.drawable.global_extended_away)
                        chatStateTextView.text = aTalkApp.getResString(R.string.service_gui_CONTACT_GONE, sender)
                    }
                }
                chatStateImgView.layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
                chatStateImgView.setPadding(7, 0, 7, 7)
                chatStateView!!.visibility = VISIBLE
            }
            else {
                chatStateView!!.visibility = INVISIBLE
            }
        }
    }
    // ********************************************************************************************//
    // Routines supporting File Transfer
    /**
     * Cancels all active file transfers.
     */
    private fun cancelActiveFileTransfers() {
        val activeKeys = activeFileTransfers.keys()
        var key: String? = null
        while (activeKeys.hasMoreElements()) {
            // catch all so if anything happens we still will close the chatFragment / chatPanel
            try {
                key = activeKeys.nextElement()
                val descriptor = activeFileTransfers[key]
                if (descriptor is IncomingFileTransferRequest) {
                    descriptor.declineFile()
                }
                else if (descriptor is FileTransfer) {
                    descriptor.cancel()
                }
            } catch (t: Throwable) {
                Timber.e(t, "Error in cancel active file transfer: %s", key)
            }
        }
    }

    /**
     * Adds the given file transfer `id` to the list of active file transfers.
     *
     * @param id the identifier of the file transfer to add
     * @param fileTransfer the descriptor of the file transfer
     */
    fun addActiveFileTransfer(id: String?, fileTransfer: FileTransfer, msgId: Int) {
        synchronized(activeFileTransfers) {
            // Both must be removed from chatFragment activeFileTransfers components when 'Done!'.
            if (!activeFileTransfers.contains(id)) {
                activeFileTransfers[id] = fileTransfer

                // Add status listener for chatFragment to track when the sendFile transfer has completed.
                fileTransfer.addStatusListener(currentChatFragment)
            }
        }
        synchronized(activeMsgTransfers) { if (!activeMsgTransfers.contains(id)) activeMsgTransfers[id] = msgId }
    }

    /**
     * Removes the given file transfer `id` from the list of active file transfers.
     *
     * @param fileTransfer the identifier of the file transfer to remove
     */
    private fun removeActiveFileTransfer(fileTransfer: FileTransfer) {
        val id = fileTransfer.getID()
        synchronized(activeFileTransfers) {
            activeFileTransfers.remove(id)

            // if (activeFileTransfers.size() == 0) ?? is one per file transfer, so must remove
            fileTransfer.removeStatusListener(currentChatFragment)
        }
        val msgId = activeMsgTransfers[id]
        synchronized(activeMsgTransfers) {
            if (msgId != null) chatListAdapter!!.setFileXfer(msgId, null)
            activeMsgTransfers.remove(id)
        }
    }

    /**
     * Handles file transfer status changed in order to remove completed file transfers from the
     * list of active transfers.
     *
     * @param event the file transfer status change event the notified us for the change
     */
    override fun statusChanged(event: FileTransferStatusChangeEvent) {
        val fileTransfer = event.getFileTransfer()
        val newStatus = event.getNewStatus()
        val msgPos = activeMsgTransfers[fileTransfer.getID()]

        // if chatFragment is still active and msgPos not null
        if (chatListAdapter != null && msgPos != null) {
            // Send an initActive message to recipient if file transfer is initActive while in preparing
            // state. Currently protocol did not broadcast status change under this condition.
            if (newStatus == FileTransferStatusChangeEvent.CANCELED && chatListAdapter!!.getXferStatus(msgPos) == FileTransferStatusChangeEvent.PREPARING) {
                val msg = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED)
                try {
                    chatPanel!!.chatSession!!.currentChatTransport!!.sendInstantMessage(msg,
                        IMessage.ENCRYPTION_NONE or IMessage.ENCODE_PLAIN)
                } catch (e: Exception) {
                    aTalkApp.showToastMessage(e.message)
                }
            }
            if (newStatus == FileTransferStatusChangeEvent.COMPLETED || newStatus == FileTransferStatusChangeEvent.CANCELED || newStatus == FileTransferStatusChangeEvent.FAILED || newStatus == FileTransferStatusChangeEvent.DECLINED) {
                removeActiveFileTransfer(fileTransfer)
            }
        }
    }

    /**
     * Sends the given file through the currently selected chat transport by using the given
     * fileComponent to visualize the transfer process in the chatFragment view.
     *
     * // @param mFile the file to send
     * // @param FileSendConversation send file component to use for file transfer & visualization
     * // @param msgId he view position on chatFragment
     */
    inner class SendFile(
            private val sendFTConversion: FileSendConversation,
            private val msgViewId: Int,
    ) : AsyncTask<Void?, Void?, Exception?>() {
        private val mFile = sendFTConversion.xferFile
        private val entityJid = currentChatTransport!!.descriptor
        private val mStickerMode = sendFTConversion.isStickerMode
        private var chkMaxSizeOK = true
        val mEncryption = if (MSGTYPE_OMEMO == mChatType) IMessage.ENCRYPTION_OMEMO
        else IMessage.ENCRYPTION_NONE

        public override fun onPreExecute() {
            val maxFileLength = currentChatTransport!!.maximumFileLength
            if (mFile!!.length() > maxFileLength) {
                val reason = aTalkApp.getResString(R.string.service_gui_FILE_TOO_BIG, ByteFormat.format(maxFileLength))
                // Remove below message allows the file view fileStatus text display properly.(reason now display in view holder)
                // chatPanel.addMessage(currentChatTransport.getName(), new Date(), ChatMessage.MESSAGE_ERROR,
                //        IMessage.ENCODE_PLAIN, reason);

                // stop background task to proceed and update status
                chkMaxSizeOK = false
                // chatListAdapter.setXferStatus(msgViewId, FileTransferStatusChangeEvent.CANCELED);
                sendFTConversion.setStatus(FileTransferStatusChangeEvent.FAILED, entityJid, mEncryption, reason)
            }
            else {
                // must reset status here as background task cannot catch up with Android redraw
                // request? causing double send requests in slow Android devices.
                // chatListAdapter.setXferStatus(msgViewId, FileTransferStatusChangeEvent.PREPARING);
                sendFTConversion.setStatus(FileTransferStatusChangeEvent.PREPARING, entityJid, mEncryption, null)
            }
        }

        override fun doInBackground(vararg param: Void?): Exception? {
            if (!chkMaxSizeOK) return null

            // return can either be FileTransfer, URL when httpFileUpload or an Exception
            val fileXfer: Any?
            val urlLink: String
            var result: Exception? = null
            try {
                fileXfer = if (mStickerMode) // mStickerMode does not attempt to send image thumbnail preview
                    currentChatTransport!!.sendSticker(mFile!!, mChatType, sendFTConversion)
                else currentChatTransport!!.sendFile(mFile!!, mChatType, sendFTConversion)

                // For both Jingle/Legacy file transfer i.e. OutgoingFileOfferJingleImpl / FileTransfer
                if (fileXfer is FileTransfer) {

                    // Trigger FileSendConversation to add statusListener as well
                    sendFTConversion.setTransportFileTransfer(fileXfer)

                    // Will be removed on file transfer completion
                    addActiveFileTransfer(fileXfer.getID(), fileXfer, msgViewId)
                }
                else {
                    urlLink = if (fileXfer is AesgcmUrl) {
                        fileXfer.aesgcmUrl
                    }
                    else {
                        fileXfer.toString()
                    }
                    // Timber.w("HTTP link: %s: %s", mFile.getName(), urlLink);
                    if (TextUtils.isEmpty(urlLink)) {
                        sendFTConversion.setStatus(FileTransferStatusChangeEvent.FAILED, entityJid, mEncryption,
                            aTalkApp.getResString(R.string.service_gui_FILE_SEND_FAILED, "HttpFileUpload"))
                    }
                    else {
                        sendFTConversion.setStatus(FileTransferStatusChangeEvent.COMPLETED, entityJid, mEncryption, "")
                        chatController!!.sendMessage(urlLink, IMessage.FLAG_REMOTE_ONLY or IMessage.ENCODE_PLAIN)
                    }
                }
            } catch (e: Exception) {
                result = e
                sendFTConversion.setStatus(FileTransferStatusChangeEvent.FAILED, entityJid, mEncryption, e.message)
            }
            return result
        }

        override fun onPostExecute(ex: Exception?) {
            if (ex != null) {
                Timber.e("Failed to send file: %s", ex.message)
                chatPanel!!.addMessage(currentChatTransport!!.name, Date(), ChatMessage.MESSAGE_ERROR,
                    IMessage.ENCODE_PLAIN, aTalkApp.getResString(R.string.service_gui_FILE_DELIVERY_ERROR, ex.message))
            }
        }

        override fun onCancelled() {}
    }

    override fun onCurrentChatChanged(chatId: String) {
        chatPanel = getActiveChat(chatId)
    }

    /*********************************************************************************************
     * Routines supporting changing chatFragment background color based on chat state and chatType
     * ChatFragment background colour is being updated when:
     * - User launches a chatSession
     * - User scroll the chatFragment pages
     * - User changes cryptoMode for the current chatSession
     */
    override fun onCryptoModeChange(cryptoMode: Int) {
        chatPanel!!.chatType = cryptoMode
        changeBackground(mCFView, cryptoMode)
    }

    /**
     * Change chatFragment background in response to initial chat session launch or event
     * triggered from omemoAuthentication and omemo mode changes in cryptoChatFragment
     *
     * @param chatType Change chat fragment view background color based on chatType
     */
    private fun changeBackground(focusView: View?, chatType: Int) {
        if (mChatType == chatType) return
        mChatType = chatType
        runOnUiThread {
            when (chatType) {
                MSGTYPE_OMEMO ->
                    focusView!!.setBackgroundResource(R.color.chat_background_omemo)

                MSGTYPE_OMEMO_UA, MSGTYPE_OMEMO_UT ->
                    focusView!!.setBackgroundResource(R.color.chat_background_omemo_ua)

                MSGTYPE_NORMAL ->
                    focusView!!.setBackgroundResource(R.color.chat_background_normal)

                MSGTYPE_MUC_NORMAL ->
                    focusView!!.setBackgroundResource(R.color.chat_background_muc)

                else ->
                    focusView!!.setBackgroundResource(R.color.chat_background_normal)
            }
        }
    }

    companion object {
        /**
         * The type of the incoming message view.
         */
        const val INCOMING_MESSAGE_VIEW = 0

        /**
         * The type of the outgoing message view.
         */
        const val OUTGOING_MESSAGE_VIEW = 1

        /**
         * The type of the system message view.
         */
        const val SYSTEM_MESSAGE_VIEW = 2

        /**
         * The type of the error message view.
         */
        private const val ERROR_MESSAGE_VIEW = 3

        /**
         * The type for corrected message view.
         */
        private const val CORRECTED_MESSAGE_VIEW = 4

        /**
         * The type for Receive File message view.
         */
        const val FILE_TRANSFER_IN_MESSAGE_VIEW = 5

        /**
         * The type for Send File message view.
         */
        const val FILE_TRANSFER_OUT_MESSAGE_VIEW = 6

        /**
         * Maximum number of message view types support
         */
        const val VIEW_TYPE_MAX = 7

        // Message chatType definitions - persistent storage constants
        const val MSGTYPE_UNKNOWN = 0x0
        const val MSGTYPE_NORMAL = 0x1
        const val MSGTYPE_OMEMO = 0x02
        const val MSGTYPE_OMEMO_UT = 0x12
        const val MSGTYPE_OMEMO_UA = 0x22
        const val MSGTYPE_MUC_NORMAL = 0x04

        /**
         * bit-7 is used to hide session record from the UI if set.
         *
         * @see ChatSessionFragment.SESSION_HIDDEN
         */
        const val MSGTYPE_MASK = 0x3F

        /**
         * Creates new parametrized instance of `ChatFragment`.
         *
         * @param chatId optional phone number that will be filled.
         *
         * @return new parametrized instance of `ChatFragment`.
         */
        fun newInstance(chatId: String?): ChatFragment {
            val chatFragment = ChatFragment()
            val args = Bundle()
            args.putString(ChatSessionManager.CHAT_IDENTIFIER, chatId)
            chatFragment.arguments = args
            return chatFragment
        }

        /**
         * Sets the avatar icon for the given avatar view.
         *
         * @param avatarView the avatar image view
         * @param drawable the avatar drawable to set
         */
        fun setAvatar(avatarView: ImageView?, drawable: Drawable?) {
            var avatarDrawable = drawable
            if (avatarDrawable == null) {
                // avatarDrawable = aTalkApp.getAppResources().getDrawable(R.drawable.contact_avatar);
                avatarDrawable = ContextCompat.getDrawable(aTalkApp.globalContext, R.drawable.contact_avatar)
            }
            avatarView?.setImageDrawable(avatarDrawable)
        }
    }
}
