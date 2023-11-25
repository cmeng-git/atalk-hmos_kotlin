/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chat

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.impl.protocol.jabber.ChatRoomMemberJabberImpl
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.protocol.ChatRoomMemberRole
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.IMessage
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceChangeEvent
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceListener
import net.java.sip.communicator.util.ConfigurationUtils
import net.sf.fmj.utility.IOUtils
import org.apache.commons.lang3.StringUtils
import org.atalk.crypto.CryptoFragment
import org.atalk.hmos.MyGlideApp
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.actionbar.ActionBarUtil
import org.atalk.hmos.gui.call.AndroidCallUtil
import org.atalk.hmos.gui.call.telephony.TelephonyFragment
import org.atalk.hmos.gui.chat.conference.ChatInviteDialog
import org.atalk.hmos.gui.chat.conference.ConferenceChatSession
import org.atalk.hmos.gui.chatroomslist.ChatRoomConfiguration
import org.atalk.hmos.gui.chatroomslist.ChatRoomDestroyDialog
import org.atalk.hmos.gui.chatroomslist.ChatRoomInfoChangeDialog
import org.atalk.hmos.gui.chatroomslist.ChatRoomInfoDialog
import org.atalk.hmos.gui.contactlist.model.MetaContactRenderer
import org.atalk.hmos.gui.dialogs.AttachOptionDialog
import org.atalk.hmos.gui.dialogs.AttachOptionItem
import org.atalk.hmos.gui.share.Attachment
import org.atalk.hmos.gui.share.MediaPreviewAdapter
import org.atalk.hmos.gui.util.AndroidUtils
import org.atalk.hmos.gui.util.EntityListHelper
import org.atalk.hmos.gui.util.EntityListHelper.TaskCompleted
import org.atalk.hmos.plugin.audioservice.AudioBgService
import org.atalk.hmos.plugin.geolocation.GeoLocationActivity
import org.atalk.hmos.plugin.geolocation.GeoLocationBase
import org.atalk.hmos.plugin.mediaplayer.MediaExoPlayerFragment
import org.atalk.hmos.plugin.mediaplayer.YoutubePlayerFragment
import org.atalk.persistance.FileBackend
import org.atalk.persistance.FilePathHelper
import org.atalk.service.osgi.OSGiActivity
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager
import org.jivesoftware.smackx.iqlast.LastActivityManager
import org.json.JSONException
import org.json.JSONObject
import org.jxmpp.jid.DomainBareJid
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.*

/**
 * The `ChatActivity` is a singleTask activity containing chat related interface.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ChatActivity : OSGiActivity(), OnPageChangeListener, TaskCompleted, GeoLocationBase.LocationListener, ChatRoomConfiguration.ChatRoomConfigListener, LocalUserChatRoomPresenceListener {
    /**
     * The pager widget, which handles animation and allows swiping horizontally to access
     * previous and next wizard steps.
     */
    private var chatPager: ViewPager? = null

    /**
     * The pager adapter, which provides the pages to the view pager widget.
     */
    private var chatPagerAdapter: ChatPagerAdapter? = null

    /**
     * The media preview adapter, which provides views of all attachments.
     */
    private var mediaPreviewAdapter: MediaPreviewAdapter? = null

    /**
     * Caches last index to prevent from propagating too many events.
     */
    private var lastSelectedIdx = -1
    private var mPlayerContainer: FrameLayout? = null
    private var mExoPlayer: MediaExoPlayerFragment? = null
    private var mYoutubePlayer: YoutubePlayerFragment? = null

    /**
     * ChatActivity menu & menuItem
     */
    private var mMenu: Menu? = null
    private lateinit var mHistoryErase: MenuItem
    private var mCallAudioContact: MenuItem? = null
    private var mCallVideoContact: MenuItem? = null
    private var mSendFile: MenuItem? = null
    private var mSendLocation: MenuItem? = null
    private var mTtsEnable: MenuItem? = null
    private var mStatusEnable: MenuItem? = null
    private var mRoomInvite: MenuItem? = null
    private var mLeaveChatRoom: MenuItem? = null
    private var mDestroyChatRoom: MenuItem? = null
    private var mChatRoomInfo: MenuItem? = null
    private var mChatRoomMember: MenuItem? = null
    private var mChatRoomConfig: MenuItem? = null
    private var mChatRoomNickSubject: MenuItem? = null

    /**
     * Holds chatId that is currently handled by this Activity.
     */
    private var currentChatId: String? = null

    // Current chatMode see ChatSessionManager ChatMode variables
    private var currentChatMode = 0

    // Not implemented currently
    private var mCurrentChatType = 0
    private var selectedChatPanel: ChatPanel? = null
    private var chatRoomConfig: ChatRoomConfiguration? = null
    private var cryptoFragment: CryptoFragment? = null
    private var mGetContents: ActivityResultLauncher<String>? = null
    private var mTakePhoto: ActivityResultLauncher<Uri>? = null
    private var mTakeVideo: ActivityResultLauncher<Uri>? = null

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this
     * Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle). Note: Otherwise it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Use SOFT_INPUT_ADJUST_PAN mode only in horizontal orientation, which doesn't provide
        // enough space to write messages comfortably. Adjust pan is causing copy-paste options
        // not being displayed as well as the action bar which contains few useful options.
        val rotation = windowManager.defaultDisplay.rotation
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chat_main)

        // If chat notification has been clicked and OSGi service has been killed in the meantime,
        // then we have to start it and restore this activity
        if (postRestoreIntent()) {
            return
        }
        // Add fragment for crypto padLock for OMEMO before start pager
        cryptoFragment = CryptoFragment()
        supportFragmentManager.beginTransaction().add(cryptoFragment!!, CRYPTO_FRAGMENT).commit()

        // Instantiate a ViewPager and a PagerAdapter.
        chatPager = findViewById(R.id.chatPager)
        chatPagerAdapter = ChatPagerAdapter(supportFragmentManager, this)
        chatPager!!.adapter = chatPagerAdapter
        chatPager!!.offscreenPageLimit = CHAT_PAGER_SIZE
        chatPager!!.addOnPageChangeListener(this)

        /*
         * Media Preview display area for user confirmation before sending
         */
        val imagePreview = findViewById<ImageView>(R.id.imagePreview)
        val mediaPreview = findViewById<RecyclerView>(R.id.media_preview)
        mediaPreviewAdapter = MediaPreviewAdapter(this, imagePreview)
        mediaPreview.adapter = mediaPreviewAdapter
        mPlayerContainer = findViewById(R.id.player_container)
        mPlayerContainer!!.visibility = View.GONE

        // Must do this in onCreate cycle else IllegalStateException if do it in onNewIntent->handleIntent:
        // attempting to register while current state is STARTED. LifecycleOwners must call register before they are STARTED.
        mGetContents = getAttachments()
        mTakePhoto = takePhoto()
        mTakeVideo = takeVideo()

        // Registered location listener - only use by playStore version
        GeoLocationBase.registeredLocationListener(this)
        handleIntent(intent, savedInstanceState)
    }

    /**
     * {@inheritDoc}
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent, null)
    }

    private fun handleIntent(intent: Intent, savedInstanceState: Bundle?) {
        val chatId: String?

        // resume chat using previous setup conditions
        if (savedInstanceState != null) {
            chatId = savedInstanceState.getString(ChatSessionManager.CHAT_IDENTIFIER)
            currentChatMode = savedInstanceState.getInt(ChatSessionManager.CHAT_MODE)
            mCurrentChatType = savedInstanceState.getInt(ChatSessionManager.CHAT_MSGTYPE)
        }
        else {
            chatId = intent.getStringExtra(ChatSessionManager.CHAT_IDENTIFIER)
            currentChatMode = intent.getIntExtra(ChatSessionManager.CHAT_MODE, ChatSessionManager.MC_CHAT)
            mCurrentChatType = intent.getIntExtra(ChatSessionManager.CHAT_MSGTYPE, ChatFragment.MSGTYPE_OMEMO)
        }
        if (chatId == null) throw RuntimeException("Missing chat identifier extra")
        val chatPanel = ChatSessionManager.createChatForChatId(chatId, currentChatMode)
        if (chatPanel == null) {
            Timber.e("Failed to create chat session for %s: %s", currentChatMode, chatId)
            return
        }
        // Synchronize ChatActivity & ChatPager
        // setCurrentChatId(chatPanel.getChatSession().getChatId())
        setCurrentChatId(chatId)
        chatPager!!.currentItem = chatPagerAdapter!!.getChatIdx(chatId)
        if (intent.clipData != null) {
            if (intent.categories != null)
                onActivityResult(REQUEST_CODE_FORWARD, RESULT_OK, intent)
            else
                onActivityResult(REQUEST_CODE_SHARE_WITH, RESULT_OK, intent)
        }
    }

    /**
     * Called when the fragment is visible to the user and actively running. This is generally
     * tied to [Activity.onResume][android.app.Activity.onResume] of the containing Activity's lifecycle.
     *
     * Set lastSelectedIdx = -1 so [.updateSelectedChatInfo] is always executed on onResume
     */
    override fun onResume() {
        super.onResume()
        if (currentChatId != null) {
            lastSelectedIdx = -1 // always force update on resume
            updateSelectedChatInfo(chatPager!!.currentItem)
        }
        else {
            Timber.w("ChatId can't be null - finishing & exist ChatActivity")
            finish()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onPause() {
        // Must reset unread message counter on chatSession closed
        // Otherwise, value not clear when user enter and exit chatSession without page slide
        if (selectedChatPanel != null) {
            val descriptor = selectedChatPanel!!.chatSession!!.descriptor
            if (descriptor is MetaContact) {
                descriptor.setUnreadCount(0)
            }
            else if (descriptor is ChatRoomWrapper) {
                descriptor.unreadCount = 0
            }
        }
        ChatSessionManager.setCurrentChatId(null)
        super.onPause()
    }

    /**
     * {@inheritDoc}
     */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(ChatSessionManager.CHAT_IDENTIFIER, currentChatId)
        outState.putInt(ChatSessionManager.CHAT_MODE, currentChatMode)
        outState.putInt(ChatSessionManager.CHAT_MSGTYPE, mCurrentChatType)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (chatPagerAdapter != null) {
            chatPagerAdapter!!.dispose()
        }

        // Clear last chat intent
        AndroidUtils.clearGeneralNotification(aTalkApp.globalContext)
    }

    /**
     * Must check chatFragment for non-null before proceed
     * User by ShareUtil to toggle media preview if any
     */
    fun toggleInputMethod() {
        var chatFragment: ChatFragment?
        if (chatPagerAdapter!!.currentChatFragment.also { chatFragment = it } != null) chatFragment!!.chatController!!.updateSendModeState()
    }

    /**
     * Set current chat id handled for this instance.
     *
     * @param chatId the id of the chat to set.
     */
    private fun setCurrentChatId(chatId: String?) {
        currentChatId = chatId
        ChatSessionManager.setCurrentChatId(chatId)
        selectedChatPanel = ChatSessionManager.getActiveChat(chatId)
        // field feedback = can have null?
        if (selectedChatPanel == null) return
        val chatSession = selectedChatPanel!!.chatSession
        if (chatSession is MetaContactChatSession) {
            mRecipient = selectedChatPanel!!.metaContact!!.getDefaultContact()
        }
        else {
            // register for LocalUserChatRoomPresenceChangeEvent to update optionItem onJoin
            selectedChatPanel!!.protocolProvider.getOperationSet(OperationSetMultiUserChat::class.java)?.addPresenceListener(this)
        }

        // Leave last chat intent by updating general notification
        AndroidUtils.clearGeneralNotification(aTalkApp.globalContext)
    }

    /**
     * {@inheritDoc}
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Close the activity when back button is pressed
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (chatRoomConfig != null) {
                chatRoomConfig!!.onBackPressed()
            }
            else if (mPlayerContainer!!.visibility == View.VISIBLE) {
                mPlayerContainer!!.visibility = View.GONE
                releasePlayer()
            }
            else {
                finish()
            }
            return true
        }
        else {
            // Pass to ChatController to handle reference may be null on event triggered => NPE. so must check
            var chatFragment: ChatFragment?
            var chatController: ChatController?
            if (chatPagerAdapter!!.currentChatFragment.also { chatFragment = it } != null) {
                if (chatFragment!!.chatController.also { chatController = it } != null) {
                    if (chatController!!.onKeyUp(keyCode, event)) return true
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Invoked when the options menu is created. Creates our own options menu from the corresponding xml.
     *
     * @param menu the options menu
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        mMenu = menu
        val inflater = menuInflater
        inflater.inflate(R.menu.chat_menu, menu)
        mCallAudioContact = mMenu!!.findItem(R.id.call_contact_audio)
        mCallVideoContact = mMenu!!.findItem(R.id.call_contact_video)
        mSendFile = mMenu!!.findItem(R.id.send_file)
        mSendLocation = mMenu!!.findItem(R.id.share_location)
        mTtsEnable = mMenu!!.findItem(R.id.chat_tts_enable)
        mStatusEnable = mMenu!!.findItem(R.id.room_status_enable)
        mHistoryErase = mMenu!!.findItem(R.id.erase_chat_history)
        mRoomInvite = mMenu!!.findItem(R.id.muc_invite)
        mLeaveChatRoom = mMenu!!.findItem(R.id.leave_chat_room)
        mDestroyChatRoom = mMenu!!.findItem(R.id.destroy_chat_room)
        mChatRoomInfo = mMenu!!.findItem(R.id.chatroom_info)
        mChatRoomMember = mMenu!!.findItem(R.id.show_chatroom_occupant)
        mChatRoomConfig = mMenu!!.findItem(R.id.chatroom_config)
        mChatRoomNickSubject = mMenu!!.findItem(R.id.chatroom_info_change)
        setOptionItem()
        return true
    }

    private fun hasUploadService(): Boolean {
        val connection: XMPPConnection? = selectedChatPanel!!.protocolProvider.connection
        if (connection != null) {
            val httpFileUploadManager = HttpFileUploadManager.getInstanceFor(connection)
            return httpFileUploadManager.isUploadServiceDiscovered
        }
        return false
    }

    // Enable option items only applicable to the specific chatSession
    private fun setOptionItem() {
        if (mMenu != null && selectedChatPanel != null) {
            // Enable/disable certain menu items based on current transport type
            val chatSession = selectedChatPanel!!.chatSession
            val contactSession = chatSession is MetaContactChatSession
            if (contactSession) {
                mLeaveChatRoom!!.isVisible = false
                mDestroyChatRoom!!.isVisible = false
                mHistoryErase.setTitle(R.string.service_gui_HISTORY_ERASE_PER_CONTACT)
                val isDomainJid = mRecipient == null || mRecipient!!.contactJid is DomainBareJid

                // check if to show call buttons.
                val metaContact = chatSession!!.descriptor
                val contactRenderer = MetaContactRenderer()
                val isShowCall = contactRenderer.isShowCallBtn(metaContact)
                val isShowVideoCall = contactRenderer.isShowVideoCallBtn(metaContact!!)
                mCallAudioContact!!.isVisible = isShowCall
                mCallVideoContact!!.isVisible = isShowVideoCall
                val isShowFileSend = (!isDomainJid
                        && (contactRenderer.isShowFileSendBtn(metaContact) || hasUploadService()))
                mSendFile!!.isVisible = isShowFileSend
                mSendLocation!!.isVisible = !isDomainJid
                mTtsEnable!!.isVisible = !isDomainJid
                mTtsEnable!!.setTitle(if (mRecipient != null && mRecipient!!.isTtsEnable!!) R.string.service_gui_TTS_DISABLE else R.string.service_gui_TTS_ENABLE)
                mStatusEnable!!.isVisible = false
                mRoomInvite!!.isVisible = !isDomainJid
                mChatRoomInfo!!.isVisible = false
                mChatRoomMember!!.isVisible = false
                mChatRoomConfig!!.isVisible = false
                mChatRoomNickSubject!!.isVisible = false
            }
            else {
                setupChatRoomOptionItem()
            }
            // Show the TTS enable option only if global TTS option is enabled.
            mTtsEnable!!.isVisible = ConfigurationUtils.isTtsEnable()
        }
    }

    private fun setupChatRoomOptionItem() {
        if (mMenu != null && selectedChatPanel != null) {
            val chatSession = selectedChatPanel!!.chatSession as? ConferenceChatSession ?: return
            // Proceed only if it is an instance of ConferenceChatSession
            // Only room owner is allowed to destroy chatRoom - role should not be null for joined room
            val chatRoomWrapper = chatSession.descriptor as ChatRoomWrapper
            val role = chatRoomWrapper.chatRoom!!.getUserRole()
            mDestroyChatRoom!!.isVisible = ChatRoomMemberRole.OWNER == role
            mChatRoomConfig!!.isVisible = ChatRoomMemberRole.OWNER == role
            val isJoined = chatRoomWrapper.chatRoom!!.isJoined()
            mLeaveChatRoom!!.isVisible = isJoined
            mSendFile!!.isVisible = isJoined && hasUploadService()
            mSendLocation!!.isVisible = isJoined
            mTtsEnable!!.isVisible = isJoined
            mTtsEnable!!.setTitle(if (chatRoomWrapper.isTtsEnable) R.string.service_gui_TTS_DISABLE else R.string.service_gui_TTS_ENABLE)
            mStatusEnable!!.isVisible = true
            mStatusEnable!!.setTitle(if (chatRoomWrapper.isRoomStatusEnable) R.string.service_gui_CHATROOM_STATUS_OFF else R.string.service_gui_CHATROOM_STATUS_ON)
            mChatRoomNickSubject!!.isVisible = isJoined
            mHistoryErase.setTitle(R.string.service_gui_CHATROOM_HISTORY_ERASE_PER)
            mChatRoomInfo!!.isVisible = true
            mChatRoomMember!!.isVisible = true

            // not available in chatRoom
            mCallAudioContact!!.isVisible = false
            mCallVideoContact!!.isVisible = false
        }
    }

    override fun localUserPresenceChanged(evt: LocalUserChatRoomPresenceChangeEvent) {
        runOnUiThread { setupChatRoomOptionItem() }
    }

    /**
     * Invoked when an options item has been selected.
     *
     * @param item the item that has been selected
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // NPE from field
        if (selectedChatPanel == null || selectedChatPanel!!.chatSession == null) return super.onOptionsItemSelected(item)
        val descriptor = selectedChatPanel!!.chatSession!!.descriptor

        when (item.itemId) {
            R.id.send_file -> {
                val attachOptionDialog = AttachOptionDialog(this)
                attachOptionDialog.show()
                return true
            }

            R.id.muc_invite -> {
                val inviteDialog = ChatInviteDialog(this, selectedChatPanel!!)
                inviteDialog.show()
                return true
            }

            R.id.erase_chat_history -> {
                EntityListHelper.eraseEntityChatHistory(this, descriptor!!, null, null)
                return true
            }

            R.id.share_location -> {
                val intent = Intent(this, GeoLocationActivity::class.java)
                intent.putExtra(GeoLocationBase.SHARE_ALLOW, true)
                startActivity(intent)
                return true
            }
        }

        if (descriptor is ChatRoomWrapper) {
            val chatRoom = descriptor.chatRoom
            val ft = supportFragmentManager.beginTransaction()
            ft.addToBackStack(null)

            when (item.itemId) {
                R.id.chat_tts_enable -> {
                    if (descriptor.isTtsEnable) {
                        descriptor.isTtsEnable = false
                        mTtsEnable!!.setTitle(R.string.service_gui_TTS_ENABLE)
                    }
                    else {
                        descriptor.isTtsEnable = true
                        mTtsEnable!!.setTitle(R.string.service_gui_TTS_DISABLE)
                    }
                    selectedChatPanel!!.updateChatTtsOption()
                    return true
                }

                R.id.leave_chat_room -> {
                    if (chatRoom != null) {
                        val leavedRoomWrapped = MUCActivator.mucService.leaveChatRoom(descriptor)
                        if (leavedRoomWrapped != null) {
                            MUCActivator.uIService!!.closeChatRoomWindow(leavedRoomWrapped)
                        }
                    }
                    ChatSessionManager.removeActiveChat(selectedChatPanel)
                    MUCActivator.uIService!!.closeChatRoomWindow(descriptor)
                    MUCActivator.mucService.removeChatRoom(descriptor)
                    finish()
                    return true
                }

                R.id.destroy_chat_room -> {
                    ChatRoomDestroyDialog().show(this, descriptor, selectedChatPanel!!)
                    // It is safer to just finish. see case R.id.close_chat:
                    finish()
                    return true
                }

                R.id.chatroom_info -> {
                    val chatRoomInfoDialog = ChatRoomInfoDialog.newInstance(descriptor)
                    chatRoomInfoDialog.show(ft, "infoDialog")
                    return true
                }

                R.id.chatroom_info_change -> {
                    ChatRoomInfoChangeDialog().show(this, descriptor)
                    return true
                }

                R.id.chatroom_config -> {
                    chatRoomConfig = ChatRoomConfiguration.getInstance(descriptor, this)
                    ft.replace(android.R.id.content, chatRoomConfig!!).commit()
                    return true
                }

                R.id.room_status_enable -> {
                    if (descriptor.isRoomStatusEnable) {
                        descriptor.isRoomStatusEnable = false
                        mStatusEnable!!.setTitle(R.string.service_gui_CHATROOM_STATUS_ON)
                    }
                    else {
                        descriptor.isRoomStatusEnable = true
                        mStatusEnable!!.setTitle(R.string.service_gui_CHATROOM_STATUS_OFF)
                    }
                    return true
                }

                R.id.show_chatroom_occupant -> {
                    val memberList = StringBuilder()
                    val occupants = chatRoom!!.getMembers()
                    if (occupants.isNotEmpty()) {
                        for (member in occupants) {
                            val occupant = member as ChatRoomMemberJabberImpl
                            memberList.append(occupant.getNickName())
                                .append(" - ")
                                .append(occupant.getJabberId())
                                .append(" (")
                                .append(member.getRole()!!.roleName)
                                .append(")")
                                .append("<br/>")
                        }
                    }
                    else {
                        memberList.append(getString(R.string.service_gui_LIST_NONE))
                    }
                    val user = descriptor.protocolProvider!!.accountID.mUserID!!
                    selectedChatPanel!!.addMessage(user, Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_HTML,
                        memberList.toString())
                    return true
                }
            }
        }
        else if (mRecipient != null) {
            when (item.itemId) {
                R.id.chat_tts_enable -> {
                    if (mRecipient!!.isTtsEnable!!) {
                        mRecipient!!.isTtsEnable = false
                        mTtsEnable!!.setTitle(R.string.service_gui_TTS_ENABLE)
                    }
                    else {
                        mRecipient!!.isTtsEnable = true
                        mTtsEnable!!.setTitle(R.string.service_gui_TTS_DISABLE)
                    }
                    selectedChatPanel!!.updateChatTtsOption()
                    return true
                }

                R.id.call_contact_audio -> {
                    val jid = mRecipient!!.contactJid
                    if (jid is DomainBareJid) {
                        val extPhone = TelephonyFragment.newInstance(jid.toString())
                        supportFragmentManager.beginTransaction().replace(android.R.id.content, extPhone).commit()
                        return true
                    }
                    AndroidCallUtil.createCall(this, selectedChatPanel!!.metaContact!!,
                        false, null)
                    return true
                }

                R.id.call_contact_video -> {
                    AndroidCallUtil.createCall(this, selectedChatPanel!!.metaContact!!,
                        true, null)
                    return true
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onTaskComplete(result: Int, deletedUUIDs: List<String>?) {
        when (result) {
            EntityListHelper.CURRENT_ENTITY -> {
                chatPagerAdapter!!.currentChatFragment!!.onClearCurrentEntityChatHistory(deletedUUIDs)
            }
            EntityListHelper.ALL_ENTITIES -> {
                onOptionsItemSelected(mMenu!!.findItem(R.id.close_all_chatrooms))
                // selectedSession.msgListeners.notifyDataSetChanged() // all registered contact chart
            }
            else -> {
                showToastMessage(R.string.service_gui_HISTORY_REMOVE_ERROR)
            }
        }
    }

    override fun onPageScrollStateChanged(state: Int) {}

    /**
     * Indicates a page has been scrolled. Sets the current chat.
     *
     * @param pos the new selected position
     * @param posOffset the offset of the newly selected position
     * @param posOffsetPixels the offset of the newly selected position in pixels
     */
    override fun onPageScrolled(pos: Int, posOffset: Float, posOffsetPixels: Int) {}
    override fun onPageSelected(pos: Int) {
        updateSelectedChatInfo(pos)
    }

    /**
     * Update the selected chat fragment actionBar info when user changes chat session.
     */
    private fun updateSelectedChatInfo(newIdx: Int) {
        // Updates only when newIdx value changes, as there are too many notifications fired when the page is scrolled
        if (lastSelectedIdx != newIdx) {
            lastSelectedIdx = newIdx
            setCurrentChatId(chatPagerAdapter!!.getChatId(newIdx))
            setOptionItem()
            var chatSession: ChatSession? = null
            val chatPanel = ChatSessionManager.currentChatPanel
            if (chatPanel != null) {
                chatSession = chatPanel.chatSession
            }
            if (chatSession?.currentChatTransport == null) {
                Timber.e("Cannot continue without the default chatSession")
                return
            }

            // Update the actionBar Title with the entity name
            ActionBarUtil.setTitle(this, chatSession.currentChatTransport!!.displayName)
            if (chatSession is MetaContactChatSession) {
                // Reset unread message count when user slides to view this chat session
                (chatSession.descriptor as MetaContact).setUnreadCount(0)
                ActionBarUtil.setAvatar(this, chatSession.chatAvatar)
                val status = chatSession.currentChatTransport!!.status
                if (status != null) {
                    ActionBarUtil.setStatusIcon(this, status.statusIcon)
                    if (!status.isOnline) {
                        getLastSeen(status)
                    }
                    else {
                        // Reset elapse time to fetch new again when contact goes offline again
                        mRecipient!!.lastActiveTime = -1
                        ActionBarUtil.setSubtitle(this, status.statusName)
                    }
                }
            }
            else if (chatSession is ConferenceChatSession) {
                // Reset unread message count when user slides to view this chat session
                (chatSession.descriptor as ChatRoomWrapper).unreadCount = 0
                val ccSession = chatSession
                ActionBarUtil.setAvatar(this, R.drawable.ic_chatroom)
                ActionBarUtil.setStatusIcon(this, ccSession.chatStatusIcon)
                ActionBarUtil.setSubtitle(this, ccSession.chatSubject)
            }
        }
    }

    /**
     * Fetch and display the contact lastSeen elapsed Time run in new thread to avoid ANR
     */
    private fun getLastSeen(status: PresenceStatus?) {
        // a. happen if the contact remove presence subscription while still in chat session
        // b. LastActivity does not apply to DomainBareJid
        if (mRecipient != null && mRecipient!!.contactJid !is DomainBareJid) {
            val connection = mRecipient!!.protocolProvider.connection

            // Proceed only if user is online and registered
            if (connection != null && connection.isAuthenticated) {
                Thread {
                    val lastSeen: String
                    val mContact = mRecipient

                    // Retrieve from server if this is the first access
                    var lastActiveTime = mRecipient!!.lastActiveTime
                    if (lastActiveTime == -1L) {
                        val jid = mRecipient!!.contactJid
                        val lastActivityManager: LastActivityManager = LastActivityManager.getInstanceFor(connection)
                        try {
                            val elapseTime: Long = lastActivityManager.getLastActivity(jid).idleTime
                            lastActiveTime = System.currentTimeMillis() - elapseTime * 1000L
                            mRecipient!!.lastActiveTime = lastActiveTime
                        } catch (e: SmackException.NoResponseException) {
                            Timber.w("Exception in getLastSeen %s", e.message)
                        } catch (e: XMPPException.XMPPErrorException) {
                            Timber.w("Exception in getLastSeen %s", e.message)
                        } catch (e: SmackException.NotConnectedException) {
                            Timber.w("Exception in getLastSeen %s", e.message)
                        } catch (e: InterruptedException) {
                            Timber.w("Exception in getLastSeen %s", e.message)
                        } catch (e: IllegalArgumentException) {
                            Timber.w("Exception in getLastSeen %s", e.message)
                        }
                    }
                    lastSeen = if (lastActiveTime != -1L) {
                        if (DateUtils.isToday(lastActiveTime)) {
                            val df = DateFormat.getTimeInstance(DateFormat.MEDIUM)
                            getString(R.string.service_gui_LAST_SEEN, df.format(Date(lastActiveTime)))
                        }
                        else {
                            // lastSeen = DateUtils.getRelativeTimeSpanString(dateTime, timeNow, DateUtils.DAY_IN_MILLIS)
                            val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            df.format(Date(lastActiveTime))
                        }
                    }
                    else {
                        status!!.statusName
                    }
                    // Update display only if the result is for the intended mContact
                    // user may have slide to new chatSession if server has slow response
                    if (mContact == mRecipient) runOnUiThread { ActionBarUtil.setSubtitle(this@ChatActivity, lastSeen) }
                }.start()
            }
            return
        }

        // Reset elapse time to fetch new again when contact goes offline again and just update with contact old status
        // mRecipient.setLastActiveTime(-1)
        ActionBarUtil.setSubtitle(this, status!!.statusName)
    }

    fun sendAttachment(attachOptionItem: AttachOptionItem?) {
        val fileUri: Uri
        when (attachOptionItem) {
            AttachOptionItem.PICTURE -> {
                val contentType = "image/*"
                mGetContents!!.launch(contentType)
            }
            AttachOptionItem.VIDEO -> {
                val contentType = "video/*"
                mGetContents!!.launch(contentType)
            }
            AttachOptionItem.SHARE_FILE -> {
                val contentType = "*/*"
                mGetContents!!.launch(contentType)
            }
            AttachOptionItem.CAMERA ->                 // Take a photo and save to fileUri then return control to the calling application
                try {
                    // create a image file to save the photo
                    mCameraFilePath = FileBackend.getOutputMediaFile(FileBackend.MEDIA_TYPE_IMAGE)
                    fileUri = FileBackend.getUriForFile(this, mCameraFilePath)
                    mTakePhoto!!.launch(fileUri)
                } catch (e: SecurityException) {
                    aTalkApp.showToastMessage(R.string.camera_permission_denied_feedback)
                }
            AttachOptionItem.VIDEO_RECORD -> try {
                // create a mp4 file to save the video
                mCameraFilePath = FileBackend.getOutputMediaFile(FileBackend.MEDIA_TYPE_VIDEO)
                fileUri = FileBackend.getUriForFile(this, mCameraFilePath)
                mTakeVideo!!.launch(fileUri)
            } catch (e: SecurityException) {
                aTalkApp.showToastMessage(R.string.camera_permission_denied_feedback)
            }
            else -> {}
        }
    }

    /**
     * Opens a FileChooserDialog to let the user pick attachments; Add the selected items into the mediaPreviewAdapter
     */
    private fun getAttachments(): ActivityResultLauncher<String> {
        return registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri>? ->
            if (uris != null) {
                val attachments = Attachment.of(this, uris)
                mediaPreviewAdapter!!.addMediaPreviews(attachments)
            }
            else {
                aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST)
            }
        }
    }

    /**
     * Callback from camera capture a photo with success status true or false
     */
    private fun takePhoto(): ActivityResultLauncher<Uri> {
        return registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
            if (success) {
                val uri = FileBackend.getUriForFile(this, mCameraFilePath)
                val attachments = Attachment.of(this, uri, Attachment.Type.IMAGE)
                mediaPreviewAdapter!!.addMediaPreviews(attachments)
            }
            else {
                aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST)
            }
        }
    }

    /**
     * Callback from camera capture a video with return thumbnail
     */
    private fun takeVideo(): ActivityResultLauncher<Uri> {
        return registerForActivityResult<Uri, Bitmap>(ActivityResultContracts.TakeVideo()) {
            if (mCameraFilePath!!.length() != 0L) {
                val uri = FileBackend.getUriForFile(this, mCameraFilePath)
                val attachments = Attachment.of(this, uri, Attachment.Type.IMAGE)
                mediaPreviewAdapter!!.addMediaPreviews(attachments)
            }
            else {
                aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (resultCode == RESULT_OK) {
            val filePath: String
            val attachments: List<Attachment>
            when (requestCode) {
                REQUEST_CODE_OPEN_FILE -> if (intent != null) {
                    val uri = intent.data
                    if (uri != null) {
                        filePath = FilePathHelper.getFilePath(this, uri)!!
                        if (StringUtils.isNotEmpty(filePath))
                            openDownloadable(File(filePath), null)
                        else
                            aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST)
                    }
                }

                REQUEST_CODE_SHARE_WITH -> {
                    Timber.d("Share Intent with: REQUEST_CODE_SHARE_WITH")
                    selectedChatPanel!!.editedText = null
                    when (intent!!.type) {
                        "text/plain" -> {
                            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                            if (!TextUtils.isEmpty(text)) {
                                if (FileBackend.isHttpFileDnLink(text)) {
                                    val msTask = MediaShareAsynTask()
                                    msTask.execute(text)
                                }
                                else {
                                    selectedChatPanel!!.editedText = text
                                }
                            }
                        }
                        else -> {
                            attachments = Attachment.extractAttachments(this, intent, Attachment.Type.IMAGE)
                            mediaPreviewAdapter!!.addMediaPreviews(attachments)
                        }
                    }

                    // Switch to active chat fragment and update the chatController entry
                    chatPagerAdapter!!.notifyDataSetChanged()
                    toggleInputMethod()
                }

                REQUEST_CODE_FORWARD -> {
                    Timber.d("Share Intent with: REQUEST_CODE_FORWARD")
                    selectedChatPanel!!.editedText = null
                    val text = if (intent!!.categories == null)
                        null
                    else
                        intent.categories.toString()

                    if (!TextUtils.isEmpty(text)) {
                        selectedChatPanel!!.editedText = text
                    }
                    attachments = Attachment.extractAttachments(this, intent, Attachment.Type.IMAGE)
                    mediaPreviewAdapter!!.addMediaPreviews(attachments)

                    // Switch to active chat fragment and update the chatController entry
                    chatPagerAdapter!!.notifyDataSetChanged()
                    toggleInputMethod()
                }
            }
        }
    }

    /**
     * callBack for GeoLocationActivity onResult received
     *
     * @param location Geo Location information
     * @param locAddress Geo Location Address
     */
    override fun onResult(location: Location?, locAddress: String?) {
        val msg = String.format(Locale.US, "%s\ngeo: %s,%s,%.03fm", locAddress,
            location!!.latitude, location.longitude, location.altitude)
        selectedChatPanel!!.sendMessage(msg, IMessage.ENCODE_PLAIN)
    }

    /**
     * Opens the given file through the `DesktopService`.
     * TargetSdkVersion 24 (or higher) and you’re passing a file:/// URI outside your package domain
     * through an Intent, then what you’ll get FileUriExposedException
     *
     * @param file the file to open
     */
    fun openDownloadable(file: File?, view: View?) {
        if ((file == null) || !file.exists()) {
            showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST)
            return
        }

        val uri = try {
            FileBackend.getUriForFile(this, file)
        } catch (e: SecurityException) {
            Timber.i("No permission to access %s: %s", file.absolutePath, e.message)
            showToastMessage(R.string.service_gui_FILE_OPEN_NO_PERMISSION)
            return
        }

        var mimeType = FileBackend.getMimeType(this, uri)
        if (mimeType == null || mimeType.contains("application")) {
            mimeType = "*/*"
        }
        if (mimeType.contains("audio") || mimeType.contains("3gp")) {
            val openIntent = Intent(this, AudioBgService::class.java)
            openIntent.action = AudioBgService.ACTION_PLAYBACK_PLAY
            openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            openIntent.setDataAndType(uri, mimeType)
            startService(openIntent)
        }
        else if (mimeType.contains("image") && view !is ImageButton) {
            MyGlideApp.loadImage(view as ImageView, file, false)
        }
        else {
            playMediaOrActionView(uri)
        }
    }

    /**
     * Start playback if it is a video file or youtube link else start android ACTION_VIEW activity
     *
     * @param videoUrl the video url link
     */
    fun playMediaOrActionView(videoUrl: Uri) {
        val mediaUrl = videoUrl.toString()
        val mimeType = FileBackend.getMimeType(this, videoUrl)
        if (!TextUtils.isEmpty(mimeType) && (mimeType!!.contains("video") || mimeType.contains("audio"))
                || mediaUrl.matches(YoutubePlayerFragment.URL_YOUTUBE.toRegex())) {
            playEmbeddedExo(mediaUrl)
        }
        else {
            val openIntent = Intent(Intent.ACTION_VIEW)
            openIntent.setDataAndType(videoUrl, mimeType)
            openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val manager = packageManager
            val info = manager.queryIntentActivities(openIntent, 0)
            if (info.isEmpty()) {
                openIntent.setDataAndType(videoUrl, "*/*")
            }
            try {
                startActivity(openIntent)
            } catch (e: ActivityNotFoundException) {
                aTalkApp.showToastMessage(R.string.service_gui_FILE_OPEN_NO_APPLICATION)
            }
        }
    }

    /**
     * / **
     * Playback video in embedded fragment for lyrics coexistence
     *
     * @param videoUrl url for playback
     */
    private fun playEmbeddedExo(videoUrl: String) {
        val bundle = Bundle()
        bundle.putString(MediaExoPlayerFragment.ATTR_MEDIA_URL, videoUrl)
        mPlayerContainer!!.visibility = View.VISIBLE
        if (videoUrl.matches(YoutubePlayerFragment.URL_YOUTUBE.toRegex())) {
            mYoutubePlayer = YoutubePlayerFragment.getInstance(bundle)
            supportFragmentManager.beginTransaction()
                .replace(R.id.player_container, mYoutubePlayer!!)
                .addToBackStack(null)
                .commit()
        }
        else {
            mExoPlayer = MediaExoPlayerFragment.getInstance(bundle)
            supportFragmentManager.beginTransaction()
                .replace(R.id.player_container, mExoPlayer!!)
                .addToBackStack(null)
                .commit()
        }
    }

    /**
     * Release the exoPlayer resource on end
     */
    private fun releasePlayer() {
        // remove the existing player view
        val playerView = supportFragmentManager.findFragmentById(R.id.player_container)
        if (playerView != null) supportFragmentManager.beginTransaction().remove(playerView).commit()
        if (mExoPlayer != null) {
            mExoPlayer!!.releasePlayer()
            mExoPlayer = null
        }
        if (mYoutubePlayer != null) {
            mYoutubePlayer!!.release()
            mYoutubePlayer = null
        }
    }

    /**
     * Call back from ChatRoomConfiguration when it has completed the task.
     * 1. Stop all future onBackPressed call to ChatRoomConfiguration
     * 2. Re-init OMEMO support option after room properties changed.
     *
     * @param configUpdates room configuration user selected fields for update
     */
    override fun onConfigComplete(configUpdates: Map<String, Any>?) {
        chatRoomConfig = null
        cryptoFragment!!.updateOmemoSupport()
    }

    /**
     * Construct media url share with thumbnail and title via URL_EMBBED which supports with JSONObject:
     *
     * {"width":480,"provider_name":"YouTube","url":"https://www.youtube.com/watch?v=dQw4w9WgXcQ",
     * "title":"Rick Astley - Never Gonna Give You Up (Video)","author_name":"RickAstleyVEVO",
     * "thumbnail_width":480,"height":270,"thumbnail_url":"https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
     * "author_url":"https://www.youtube.com/user/RickAstleyVEVO","type":"video","provider_url":"https://www.youtube.com/",
     * "thumbnail_height":360,"version":"1.0","html":"\n<iframe width=\" 480\></iframe>" height=\"270\"
     * src=\"https://www.youtube.com/embed/dQw4w9WgXcQ?feature=oembed\" frameborder=\"0\" allowfullscreen=\"allowfullscreen\">\n"}
     */
    private open inner class MediaShareAsynTask : AsyncTask<String?, Void?, String?>() {
        private lateinit var mUrl: String
        override fun doInBackground(vararg params: String?): String? {
            mUrl = params[0]!!
            // mUrl = "https://vimeo.com/45196609"  // invalid link
            return getUrlInfo(mUrl)!!
        }

        override fun onPostExecute(result: String?) {
            var urlInfo: String? = null
            if (!TextUtils.isEmpty(result)) {
                try {
                    val attributes = JSONObject(result!!)
                    val title = attributes.getString("title")
                    val imageUrl = attributes.getString("thumbnail_url")
                    urlInfo = getString(R.string.service_gui_URL_MEDIA_SHARE, imageUrl, title, mUrl)
                    selectedChatPanel!!.sendMessage(urlInfo, IMessage.ENCODE_HTML)
                } catch (e: JSONException) {
                    Timber.w("Exception in JSONObject access: %s", result)
                }
            }

            // send mUrl instead fetch urlInfo failed
            if (urlInfo == null) {
                // selectedChatPanel.setEditedText(mUrl) too late as controller msgEdit is already initialized
                selectedChatPanel!!.sendMessage(mUrl, IMessage.ENCODE_PLAIN)
            }
        }

        /***
         * Get the Drawable from the given URL (change to secure https if necessary)
         * aTalk/android supports only secure https connection
         * https://noembed.com/embed?url=https://www.youtube.com/watch?v=dQw4w9WgXcQ
         *
         * @param urlString_ url string
         * @return Jason String
         */
        private fun getUrlInfo(urlString_: String?): String? {
            // Server that provides the media info for the supported services
            var urlString = urlString_
            val URL_EMBBED = "https://noembed.com/embed?url="
            try {
                urlString = URL_EMBBED + urlString!!.replace("http:", "https:")
                val mUrl = URL(urlString)
                val httpConnection = mUrl.openConnection() as HttpURLConnection
                httpConnection.requestMethod = "GET"
                httpConnection.setRequestProperty("Content-length", "0")
                httpConnection.useCaches = false
                httpConnection.allowUserInteraction = false
                httpConnection.connectTimeout = 3000
                httpConnection.readTimeout = 3000
                httpConnection.connect()
                val responseCode = httpConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = httpConnection.inputStream
                    return IOUtils.readAllToString(inputStream)
                }
            } catch (e: IOException) {
                Timber.w("Exception in get URL info: %s", e.message)
            }
            return null
        }
    }

    /**
     * Shows the given error message in the error area of this component.
     *
     * @param resId the Id of the message to show
     */
    private fun showToastMessage(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    /*
     * This method handles the display of Youtube Player when screen orientation is rotated
     * Set to fullscreen mode when in landscape, else otherwise.
     * Not working well - disabled
     */
    //    @Override
    //    public void onConfigurationChanged(@NotNull Configuration newConfig)
    //    {
    //        super.onConfigurationChanged(newConfig)
    //        if ((mPlayerContainer.getVisibility() == View.VISIBLE) && (mYoutubePlayer != null)) {
    //            if (aTalkApp.isPortrait) {
    //                mYoutubePlayer.getFullScreenHelper().exitFullScreen()
    //            }
    //            else {
    //                mYoutubePlayer.getFullScreenHelper().enterFullScreen()
    //            }
    //        }
    //    }

    companion object {
        private const val REQUEST_CODE_OPEN_FILE = 105
        private const val REQUEST_CODE_SHARE_WITH = 200

        /*
        * Share of both text and images in a single intent for local forward only in aTalk;
        * msgContent is saved intent.categories if both types are required;
        * Otherwise follow standard share method i.e. REQUEST_CODE_SHARE_WITH
        */
        private const val REQUEST_CODE_FORWARD = 201

        const val CRYPTO_FRAGMENT = "crypto_fragment"

        /**
         * Set the number of pages that should be retained to either side of the current page in the
         * view hierarchy in an idle state. Pages beyond this limit will be recreated from the adapter when needed.
         * Note: this is not the max fragments that user is allowed to have
         */
        private const val CHAT_PAGER_SIZE = 4
        private var mRecipient: Contact? = null

        /**
         * file for camera picture or video capture
         */
        private var mCameraFilePath: File? = null
    }
}