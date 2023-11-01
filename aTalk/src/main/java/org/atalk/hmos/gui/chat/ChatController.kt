/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.AnimationDrawable
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.Html
import android.text.Html.*
import android.text.TextUtils
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import net.java.sip.communicator.impl.protocol.jabber.CallJabberImpl
import net.java.sip.communicator.impl.protocol.jabber.CallPeerJabberImpl
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.gui.UIService
import net.java.sip.communicator.service.protocol.IMessage
import net.java.sip.communicator.util.ConfigurationUtils
import okhttp3.internal.notify
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.call.CallManager
import org.atalk.hmos.gui.call.notification.CallNotificationManager
import org.atalk.hmos.gui.chat.ChatFragment.*
import org.atalk.hmos.gui.share.Attachment
import org.atalk.hmos.gui.share.MediaPreviewAdapter
import org.atalk.hmos.gui.util.ContentEditText
import org.atalk.hmos.gui.util.HtmlImageGetter
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.hmos.plugin.audioservice.AudioBgService
import org.atalk.hmos.plugin.audioservice.SoundMeter
import org.atalk.persistance.FilePathHelper
import org.jivesoftware.smackx.chatstates.ChatState
import timber.log.Timber
import java.io.File
import java.util.*
import kotlin.math.abs

/**
 * Class is used to separate the logic of send message editing process from `ChatFragment`.
 * It handles last messages correction, editing, sending messages and chat state notifications.
 * It also restores edit state when the chat fragment is scrolled in view again.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ChatController(activity: Activity, fragment: ChatFragment?) : View.OnClickListener, View.OnLongClickListener, View.OnTouchListener, TextWatcher, ContentEditText.CommitListener {
    /**
     * The chat fragment used by this instance.
     */
    private val mChatFragment: ChatFragment?

    /**
     * Parent activity: ChatActivity pass in from ChatFragment.
     */
    private lateinit var parent: Activity

    /**
     * Indicates that this controller is attached to the views.
     */
    private var isAttached = false

    /**
     * Correction indicator / cancel button.
     */
    private var cancelCorrectionBtn: View? = null

    /**
     * Send button's View.
     */
    private var sendBtn: View? = null

    /**
     * media call button's View.
     */
    private var callBtn: View? = null

    /**
     * Audio recording button.
     */
    private var audioBtn: View? = null

    /**
     * Message `EditText`.
     */
    private lateinit var msgEdit: ContentEditText

    /**
     * Message editing area background.
     */
    private var msgEditBg: View? = null
    private var mediaPreview: RecyclerView? = null
    private var imagePreview: ImageView? = null
    private var chatReplyCancel: View? = null
    private var chatMessageReply: TextView? = null
    private var quotedMessage: String? = null

    /**
     * Chat chatPanel used by this controller and its parent chat fragment.
     */
    private var chatPanel: ChatPanel? = null

    /**
     * Current Chat Transport associates with this Chat Controller.
     */
    private var mChatTransport: ChatTransport? = null

    /**
     * Typing state control thread that goes from composing to stopped state.
     */
    private var chatStateCtrlThread: ChatStateControl? = null

    /**
     * Current chat state.
     */
    private var mChatState = ChatState.gone

    /**
     * Indicate whether sending chat state notifications to the contact is allowed:
     * 1. contact must support XEP-0085: Chat State Notifications
     * 2. User enable the chat state notifications sending option
     */
    private var allowsChatStateNotifications = false

    /**
     * Audio recording variables
     */
    private val isAudioAllowed: Boolean
    private var isRecording = false
    private var msgRecordView: View? = null
    private var mRecordTimer: TextView? = null
    private var mdBTextView: TextView? = null
    private var mTrash: ImageView? = null
    private var mSoundMeter: SoundMeter? = null
    private var mTtsAnimate: AnimationDrawable? = null
    private var mTrashAnimate: AnimationDrawable? = null
    private var animBlink: Animation? = null
    private var animZoomOut: Animation? = null
    private var animSlideUp: Animation? = null
    private var downX = 0f

    /**
     * Method called by the `ChatFragment` when it is displayed to the user and its `View` is created.
     */
    fun onShow() {
        if (!isAttached) {
            isAttached = true

            // Timber.d("ChatController attached to %s", chatFragment.hashCode())
            chatPanel = mChatFragment!!.chatPanel

            // Gets message edit view
            msgEdit = parent.findViewById(R.id.chatWriteText)
            msgEdit.setCommitListener(this)
            msgEdit.isFocusableInTouchMode = true

            // Restore edited text
            msgEdit.setText(chatPanel!!.editedText)
            msgEdit.addTextChangedListener(this)

            // Message typing area background
            msgEditBg = parent.findViewById(R.id.chatTypingArea)
            msgRecordView = parent.findViewById(R.id.recordView)

            // Gets the cancel correction button and hooks on click action
            cancelCorrectionBtn = parent.findViewById(R.id.cancelCorrectionBtn)
            cancelCorrectionBtn!!.setOnClickListener(this)

            // Quoted reply message view
            chatMessageReply = parent.findViewById(R.id.chatMsgReply)
            chatMessageReply!!.visibility = View.GONE
            chatReplyCancel = parent.findViewById(R.id.chatReplyCancel)
            chatReplyCancel!!.visibility = View.GONE
            chatReplyCancel!!.setOnClickListener(this)
            imagePreview = parent.findViewById(R.id.imagePreview)
            mediaPreview = parent.findViewById(R.id.media_preview)

            // Gets the send message button and hooks on click action
            sendBtn = parent.findViewById(R.id.sendMessageButton)
            sendBtn!!.setOnClickListener(this)

            // Gets the send audio button and hooks on click action if permission allowed
            audioBtn = parent.findViewById(R.id.audioMicButton)
            if (isAudioAllowed) {
                audioBtn!!.setOnClickListener(this)
                audioBtn!!.setOnLongClickListener(this)
                audioBtn!!.setOnTouchListener(this)
            }
            else {
                Timber.w("Audio recording is not allowed - permission denied!")
            }

            // Gets the call switch button
            callBtn = parent.findViewById(R.id.chatBackToCallButton)
            callBtn!!.setOnClickListener(this)
            mSoundMeter = parent.findViewById(R.id.sound_meter)
            mRecordTimer = parent.findViewById(R.id.recordTimer)
            mdBTextView = parent.findViewById(R.id.dBTextView)
            mTrash = parent.findViewById(R.id.ic_mic_trash)
            mTrashAnimate = mTrash!!.background as AnimationDrawable
            animBlink = AnimationUtils.loadAnimation(parent, R.anim.blink)
            animZoomOut = AnimationUtils.loadAnimation(parent, R.anim.zoom_out)
            animZoomOut!!.duration = 1000
            animSlideUp = AnimationUtils.loadAnimation(parent, R.anim.slide_up)
            animSlideUp!!.duration = 1000
            updateCorrectionState()
            initChatController()
            updateSendModeState()
        }
    }

    /**
     * Init to correct mChatTransport if chatTransPort allows, then enable chatState
     * notifications thread. Perform only if the chatFragment is really visible to user
     *
     * Otherwise the non-focus chatFragment will cause out-of-sync between chatFragment and
     * chatController i.e. entered msg display in wrong chatFragment
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun initChatController() {
        if (!mChatFragment!!.isVisible) {
            Timber.w("Skip init current Chat Transport to: %s with visible State: %s",
                    mChatTransport, mChatFragment.isVisible)
            return
        }
        mChatTransport = chatPanel!!.chatSession!!.currentChatTransport
        allowsChatStateNotifications = (mChatTransport!!.allowsChatStateNotifications()
                && ConfigurationUtils.isSendChatStateNotifications())
        if (allowsChatStateNotifications) {
            msgEdit.setOnTouchListener { v: View?, event: MotionEvent ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    onTouchAction()
                }
                false
            }

            // Start chat state control thread and give 500mS before sending ChatState.active
            // to take care the fast scrolling of fragment by user.
            if (chatStateCtrlThread == null) {
                mChatState = ChatState.gone
                chatStateCtrlThread = ChatStateControl()
                chatStateCtrlThread!!.start()
            }
        }
    }

    /**
     * Method called by `ChatFragment` when it's no longer displayed to the user.
     * This happens when user scroll pagerAdapter, and the chat window is out of view
     */
    fun onHide() {
        if (isAttached) {
            isAttached = false

            // Remove text listener
            msgEdit.removeTextChangedListener(this)
            // Store edited text in chatPanel
            if (chatPanel != null && msgEdit.text != null) chatPanel!!.editedText = msgEdit.text.toString()
            mediaPreview!!.visibility = View.GONE
        }
    }

    /**
     * Sends the chat message or corrects the last message if the chatPanel has correction UID set.
     *
     * @param message the text string to be sent
     * @param encType The encType of the message to be sent: RemoteOnly | 1=text/html or 0=text/plain.
     */
    fun sendMessage(message: String, encType: Int) {
        // Sometimes it seems the chatPanel is not inSync with the chatSession or initialized,
        // i.e Conference instead of MetaContact and may also be null, so check to ensure
        if (chatPanel == null) chatPanel = mChatFragment!!.chatPanel
        val correctionUID = chatPanel!!.correctionUID
        var encryption = IMessage.ENCRYPTION_NONE
        if (chatPanel!!.isOmemoChat) encryption = IMessage.ENCRYPTION_OMEMO else if (chatPanel!!.isOTRChat) encryption = IMessage.ENCRYPTION_OTR
        if (correctionUID == null) {
            try {
                mChatTransport!!.sendInstantMessage(message, encryption or encType)
            } catch (ex: Exception) {
                Timber.e("Send instant message exception: %s", ex.message)
                aTalkApp.showToastMessage(ex.message)
            }
        }
        else {
            mChatTransport!!.sendInstantMessage(message, encryption or encType, correctionUID)
            // Clears correction UI state
            chatPanel!!.correctionUID = null
            updateCorrectionState()
        }

        // must run on UiThread when access view
        parent.runOnUiThread {

            // Clears edit text field
            if (msgEdit != null) msgEdit.setText("")

            // just update chat state to active but not sending notifications
            mChatState = ChatState.active
            if (chatStateCtrlThread == null) {
                chatStateCtrlThread = ChatStateControl()
                chatStateCtrlThread!!.start()
            }
            chatStateCtrlThread!!.initChatState()
        }
    }

    /**
     * Method fired when the chat message is clicked. {@inheritDoc}.
     * Trigger from @see ChatFragment#
     */
    fun onItemClick(adapter: AdapterView<*>?, view: View, position: Int, id: Long) {
        // Detect outgoing message area
        if (view.id != R.id.outgoingMessageView && view.id != R.id.outgoingMessageHolder) {
            cancelCorrection()
            return
        }
        val chatListAdapter = mChatFragment!!.chatListAdapter

        // Position must be aligned to the number of header views included
        val headersCount = (adapter as ListView).headerViewsCount
        val cPos = position - headersCount
        val chatMessage = chatListAdapter!!.getMessage(cPos)

        // Ensure the selected message is really the last outgoing message
        if (cPos != chatListAdapter.count - 1) {
            for (i in cPos + 1 until chatListAdapter.count) {
                if (chatListAdapter.getItemViewType(i) == ChatFragment.OUTGOING_MESSAGE_VIEW) {
                    cancelCorrection()
                    return
                }
            }
        }
        if (mChatTransport is MetaContactChatTransport) {
            if (!chatMessage!!.message!!.matches(ChatMessage.HTML_MARKUP)) editText(adapter, chatMessage, position)
        }
        else {
            msgEdit.setText(chatMessage!!.contentForCorrection)
        }
    }

    fun editText(adapter: AdapterView<*>?, chatMessage: ChatMessage, position: Int) {
        // ListView cListView = chatFragment.getChatListView()
        val uidToCorrect = chatMessage.uidForCorrection
        val content = chatMessage.contentForCorrection
        if (!TextUtils.isEmpty(content)) {
            // Sets corrected message content and show the keyboard
            msgEdit.setText(content)
            msgEdit.requestFocus()

            // Not send message - uidToCorrect is null
            if (!TextUtils.isEmpty(uidToCorrect)) {
                // Change edit text bg colors and show cancel button
                chatPanel!!.correctionUID = uidToCorrect
                updateCorrectionState()
                val inputMethodManager = parent.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showSoftInput(msgEdit, InputMethodManager.SHOW_IMPLICIT)

                // Select corrected message
                // TODO: it doesn't work when keyboard is displayed for the first time
                adapter!!.setSelection(position)
            }
        }
    }

    fun setQuoteMessage(replyMessage: ChatMessage?) {
        if (replyMessage != null) {
            chatMessageReply!!.visibility = View.VISIBLE
            chatReplyCancel!!.visibility = View.VISIBLE
            val imageGetter = HtmlImageGetter()
            var body = replyMessage.message
            if (!body!!.matches(ChatMessage.HTML_MARKUP)) {
                body = body.replace("\n", "<br/>")
            }
            quotedMessage = aTalkApp.getResString(R.string.service_gui_CHAT_REPLY,
                    replyMessage.sender, body)
            chatMessageReply!!.text = fromHtml(quotedMessage, imageGetter, null)
        }
        else {
            quotedMessage = null
            chatMessageReply!!.visibility = View.GONE
            chatReplyCancel!!.visibility = View.GONE
        }
    }

    /**
     * Method fired when send a message or cancel correction button is clicked.
     *
     *
     * {@inheritDoc}
     */
    override fun onClick(v: View) {
        when (v.id) {
            R.id.sendMessageButton -> {
                if (chatPanel!!.protocolProvider.isRegistered) {
                    if (mediaPreview!!.visibility == View.VISIBLE) {
                        val mpAdapter = mediaPreview!!.adapter as MediaPreviewAdapter?
                        if (mpAdapter != null) {
                            val mediaPreviews = mpAdapter.attachments
                            if (mediaPreviews.isNotEmpty()) {
                                for (attachment in mediaPreviews) {
                                    val filePath = FilePathHelper.getFilePath(parent, attachment)
                                    if (StringUtils.isNotEmpty(filePath)) {
                                        if (File(filePath!!).exists()) {
                                            chatPanel!!.addFTSendRequest(filePath, ChatMessage.MESSAGE_FILE_TRANSFER_SEND)
                                        }
                                        else {
                                            aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST)
                                        }
                                    }
                                }
                                mpAdapter.clearPreviews()
                            }
                        }
                    }
                    else {
                        // allow last message correction to send empty string to clear last sent text
                        val correctionUID = chatPanel!!.correctionUID
                        var textEdit = ViewUtil.toString(msgEdit)
                        if (textEdit == null && correctionUID != null) {
                            textEdit = " "
                        }
                        if (textEdit == null && quotedMessage == null) {
                            return
                        }
                        if (quotedMessage != null) {
                            textEdit = quotedMessage + textEdit
                        }
                        else if (textEdit!!.matches(Regex("(?s)^http[s]:.*")) && !textEdit.contains("\\s")) {
                            textEdit = aTalkApp.getResString(R.string.service_gui_CHAT_LINK, textEdit, textEdit)
                        }

                        // if text contains markup tag then send message as ENCODE_HTML mode
                        if (textEdit.matches(ChatMessage.HTML_MARKUP)) {
                            Timber.d("HTML text entry detected: %s", textEdit)
                            msgEdit.setText(textEdit)
                            sendMessage(textEdit, IMessage.ENCODE_HTML)
                        }
                        else sendMessage(textEdit, IMessage.ENCODE_PLAIN)
                    }
                    updateSendModeState()
                }
                else {
                    aTalkApp.showToastMessage(R.string.service_gui_MSG_SEND_CONNECTION_PROBLEM)
                }
                if (quotedMessage != null) {
                    quotedMessage = null
                    chatMessageReply!!.visibility = View.GONE
                    chatReplyCancel!!.visibility = View.GONE
                }
            }
            R.id.chatReplyCancel -> {
                quotedMessage = null
                chatMessageReply!!.visibility = View.GONE
                chatReplyCancel!!.visibility = View.GONE
            }
            R.id.cancelCorrectionBtn -> {
                cancelCorrection()
                // Clear last message text
                msgEdit.setText("")
            }
            R.id.chatBackToCallButton -> if (CallManager.getActiveCallsCount() > 0) {
                var callId: String? = null
                for (call in CallManager.getActiveCalls()) {
                    callId = call!!.callId
                    val callPeer = (call as CallJabberImpl).getPeerBySid(callId)
                    val metaContact = chatPanel!!.metaContact
                    if (metaContact != null && metaContact.getDefaultContact() == callPeer!!.getContact()) {
                        break
                    }
                }
                if (callId != null) CallNotificationManager.getInstanceFor(callId).backToCall()
            }
            else updateSendModeState()
            R.id.audioMicButton -> if (chatPanel!!.isChatTtsEnable) {
                speechToText()
            }
        }
    }

    /**
     * Audio sending is disabled if permission.RECORD_AUDIO is denied.
     * Audio chat message is allowed even for offline contact and in conference
     */
    override fun onLongClick(v: View): Boolean {
        if (v.id == R.id.audioMicButton) {
            Timber.d("Current Chat Transport for audio: %s", mChatTransport.toString())
            Timber.d("Audio recording started!!!")
            isRecording = true
            // Hide normal edit text view
            msgEdit.visibility = View.GONE

            // Show audio record information
            msgRecordView!!.visibility = View.VISIBLE
            mTrash!!.setImageResource(R.drawable.ic_record)
            mTrash!!.startAnimation(animBlink)

            // Set up audio background service and receiver
            val filter = IntentFilter()
            filter.addAction(AudioBgService.ACTION_AUDIO_RECORD)
            filter.addAction(AudioBgService.ACTION_SMI)
            LocalBroadcastManager.getInstance(parent).registerReceiver(mReceiver, filter)
            startAudioService(AudioBgService.ACTION_RECORDING)
            return true
        }
        return false
    }

    /**
     * onTouch is disabled if permission.RECORD_AUDIO is denied
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        var done = false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                return false // to allow long press detection
            }
            MotionEvent.ACTION_UP -> {
                val upX = event.x
                val deltaX = downX - upX

                //Swipe horizontal detected
                if (abs(deltaX) > min_distance) {
                    if (isRecording && deltaX > 0) { // right to left
                        Timber.d("Audio recording cancelled!!!")
                        isRecording = false
                        audioBtn!!.isEnabled = false // disable while in animation
                        LocalBroadcastManager.getInstance(parent).unregisterReceiver(mReceiver)
                        startAudioService(AudioBgService.ACTION_CANCEL)

                        // Start audio sending cancel animation
                        mSoundMeter!!.startAnimation(animZoomOut)
                        mdBTextView!!.startAnimation(animSlideUp)
                        mRecordTimer!!.startAnimation(animSlideUp)
                        mTrash!!.clearAnimation()
                        mTrash!!.setImageDrawable(null)
                        mTrashAnimate!!.start()
                        onAnimationEnd(1200)
                        done = true
                    }
                }
                else {
                    if (isRecording) {
                        Timber.d("Audio recording sending!!!")
                        isRecording = false
                        startAudioService(AudioBgService.ACTION_SEND)
                        onAnimationEnd(10)
                        done = true
                    }
                }
            }
        }
        return done
    }

    /**
     * Handling of KeyCode in ChatController, called from ChatActivity
     * Note: KeyEvent.Callback is only available in Activity
     */
    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            if (mChatFragment != null) {
                sendBtn!!.performClick()
            }
            return true
        }
        return false
    }

    // Need to wait on a new thread for animation to end
    private fun onAnimationEnd(wait: Int) {
        Thread {
            try {
                Thread.sleep(wait.toLong())
                parent.runOnUiThread {
                    mTrashAnimate!!.stop()
                    mTrashAnimate!!.selectDrawable(0)
                    msgEdit.visibility = View.VISIBLE
                    msgRecordView!!.visibility = View.GONE
                    mSoundMeter!!.clearAnimation()
                    mdBTextView!!.clearAnimation()
                    mRecordTimer!!.clearAnimation()
                    audioBtn!!.isEnabled = true
                }
            } catch (ex: Exception) {
                Timber.e("Exception: %s", ex.message)
            }
        }.start()
    }

    private fun startAudioService(mAction: String) {
        val intent = Intent(parent, AudioBgService::class.java)
        intent.action = mAction
        parent.startService(intent)
    }

    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isRecording && AudioBgService.ACTION_SMI == intent.action) {
                val mDuration = intent.getStringExtra(AudioBgService.RECORD_TIMER)
                val mdBSpl = intent.getDoubleExtra(AudioBgService.SPL_LEVEL, 1.0)
                val dBspl = mdBSpl * AudioBgService.mDBRange
                val sdBSpl = String.format(Locale.US, "%.02f", dBspl) + "dB"
                mSoundMeter!!.level = mdBSpl
                mdBTextView!!.text = sdBSpl
                mRecordTimer!!.text = mDuration
            }
            else if (AudioBgService.ACTION_AUDIO_RECORD == intent.action) {
                Timber.i("Sending audio recorded file!!!")
                LocalBroadcastManager.getInstance(parent).unregisterReceiver(this)
                val filePath = intent.getStringExtra(AudioBgService.URI)
                if (StringUtils.isNotEmpty(filePath)) {
                    chatPanel!!.addFTSendRequest(filePath, ChatMessage.MESSAGE_FILE_TRANSFER_SEND)
                }
                parent.stopService(Intent(parent, AudioBgService::class.java))
            }
        }
    }

    /**
     * Creates new instance of `ChatController`.
     *
     * activity the parent `Activity`.
     * fragment the parent `ChatFragment`.
     */
    init {
        parent = activity
        mChatFragment = fragment
        // Do not use aTalk.getInstance, may not have initialized
        isAudioAllowed = aTalk.hasPermission(parent, false,
                aTalk.PRC_RECORD_AUDIO, Manifest.permission.RECORD_AUDIO)
    }

    /**
     * Built-in speech to text recognition without a soft keyboard popup.
     * To use the soft keyboard mic, click on text entry and then click on mic.
     */
    private fun speechToText() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        val recognizer = SpeechRecognizer.createSpeechRecognizer(parent)
        val listener = object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val voiceResults = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (voiceResults == null) {
                    Timber.w("No voice results")
                    updateSendModeState()
                }
                else {
                    // Contains multiple text strings for selection
                    // StringBuffer spkText = new StringBuffer()
                    // for (String match : voiceResults) {
                    //    spkText.append(match).append("\n")
                    // }
                    msgEdit.setText(voiceResults[0])
                }
            }

            override fun onReadyForSpeech(params: Bundle) {
                Timber.d("Ready for speech")
                audioBtn!!.setBackgroundResource(R.drawable.ic_tts_mic_play)
                mTtsAnimate = audioBtn!!.background as AnimationDrawable
                mTtsAnimate!!.start()
            }

            /**
             * ERROR_NETWORK_TIMEOUT = 1
             * ERROR_NETWORK = 2
             * ERROR_AUDIO = 3
             * ERROR_SERVER = 4
             * ERROR_CLIENT = 5
             * ERROR_SPEECH_TIMEOUT = 6
             * ERROR_NO_MATCH = 7
             * ERROR_RECOGNIZER_BUSY = 8
             * ERROR_INSUFFICIENT_PERMISSIONS = 9
             *
             * @param error code is defined in
             * @see SpeechRecognizer
             */
            override fun onError(error: Int) {
                Timber.e("Error listening for speech: %s ", error)
                updateSendModeState()
            }

            override fun onBeginningOfSpeech() {
                Timber.d("Speech starting")
            }

            override fun onBufferReceived(buffer: ByteArray) {
                // TODO Auto-generated method stub
            }

            override fun onEndOfSpeech() {
                mTtsAnimate!!.stop()
                mTtsAnimate!!.selectDrawable(0)
            }

            override fun onEvent(eventType: Int, params: Bundle) {
                // TODO Auto-generated method stub
            }

            override fun onPartialResults(partialResults: Bundle) {
                // TODO Auto-generated method stub
            }

            override fun onRmsChanged(rmsdB: Float) {
                // TODO Auto-generated method stub
            }
        }
        recognizer.setRecognitionListener(listener)
        recognizer.startListening(intent)
    }

    /**
     * Cancels last message correction mode.
     */
    private fun cancelCorrection() {
        // Reset correction status
        if (chatPanel!!.correctionUID != null) {
            chatPanel!!.correctionUID = null
            updateCorrectionState()
            msgEdit.setText("")
        }
    }

    /**
     * Insert/remove the buddy nickname into the sending text.
     * @param buddy occupant jid
     */
    fun insertTo(buddy: String?) {
        if (buddy != null) {
            var nickName = buddy.replace("(\\w+)[:|@].*".toRegex(), "$1")
            val editText = ViewUtil.toString(msgEdit)
            if (editText == null) {
                nickName += ": "
            }
            else if (editText.contains(nickName)) {
                nickName = editText.replace(nickName, "")
                        .replace(", ", "")
                if (nickName.length == 1) nickName = ""
            }
            else if (editText.contains(":")) {
                nickName = editText.replace(":", ", $nickName: ")
            }
            else {
                nickName = "$editText $nickName"
            }
            msgEdit.setText(nickName)
        }
    }

    /**
     * Updates visibility state of cancel correction button and toggles bg color of the message edit field.
     */
    private fun updateCorrectionState() {
        val correctionMode = chatPanel!!.correctionUID != null
        val bgColorId = if (correctionMode) R.color.msg_input_correction_bg else R.color.msg_input_bar_bg
        msgEditBg!!.setBackgroundColor(parent.resources.getColor(bgColorId, null))
        cancelCorrectionBtn!!.visibility = if (correctionMode) View.VISIBLE else View.GONE
        mChatFragment!!.chatListView.invalidateViews()
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
    override fun afterTextChanged(s: Editable) {}

    /**
     * Updates chat state.
     *
     * {@inheritDoc}
     */
    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        if (allowsChatStateNotifications) {
            if (s.isNotEmpty()) {
                // Start or refreshComposing chat state control thread
                if (chatStateCtrlThread == null) {
                    setNewChatState(ChatState.active)
                    setNewChatState(ChatState.composing)
                    chatStateCtrlThread = ChatStateControl()
                    chatStateCtrlThread!!.start()
                }
                else chatStateCtrlThread!!.refreshChatState()
            }
        }
        updateSendModeState()
    }

    /**
     * Update the view states of all send buttons based on the current available send contents.
     * Send text button has higher priority over attachment if msgEdit is not empty
     */
    fun updateSendModeState() {
        val hasAttachments = (mediaPreview!!.adapter != null
                && (mediaPreview!!.adapter as MediaPreviewAdapter?)!!.hasAttachments())
        mediaPreview!!.visibility = View.GONE
        imagePreview!!.visibility = View.GONE
        imagePreview!!.setImageDrawable(null)
        callBtn!!.visibility = View.INVISIBLE
        audioBtn!!.visibility = View.INVISIBLE
        msgEdit.visibility = View.VISIBLE

        // Enabled send text button if text entry box contains text or in correction mode
        // Sending Text before attachment
        if (!TextUtils.isEmpty(msgEdit.text) || chatPanel!!.correctionUID != null) {
            sendBtn!!.visibility = View.VISIBLE
        }
        else if (hasAttachments) {
            msgEdit.visibility = View.GONE
            mediaPreview!!.visibility = View.VISIBLE
            imagePreview!!.visibility = View.VISIBLE
            sendBtn!!.visibility = View.VISIBLE
        }
        else {
            sendBtn!!.visibility = View.INVISIBLE
            if (CallManager.getActiveCallsCount() > 0) {
                callBtn!!.visibility = View.VISIBLE
            }
            else if (isAudioAllowed) {
                audioBtn!!.setBackgroundResource(R.drawable.ic_voice_mic)
                audioBtn!!.visibility = View.VISIBLE
            }
            else {
                sendBtn!!.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Method called by `ChatFragment` and `ChatController`. when user touches the
     * display. Re-init chat state to active when user return to chat session
     */
    fun onTouchAction() {
        if (mChatState == ChatState.inactive) {
            setNewChatState(ChatState.active)
            if (chatStateCtrlThread == null) {
                chatStateCtrlThread = ChatStateControl()
                chatStateCtrlThread!!.start()
            }
        }
    }

    /**
     * Method called by `ChatFragment` when user closes the chat window.
     * Update that user is no longer in this chat session and end state ctrl thread
     */
    fun onChatCloseAction() {
        setNewChatState(ChatState.gone)
    }

    /**
     * Sets new chat state and send notification is enabled.
     *
     * @param newState new chat state to set.
     */
    private fun setNewChatState(newState: ChatState) {
        // Timber.w("Chat state changes from: " + mChatState + " => " + newState)
        if (mChatState != newState) {
            mChatState = newState
            if (allowsChatStateNotifications) mChatTransport!!.sendChatStateNotification(newState)
        }
    }

    override fun onCommitContent(info: InputContentInfoCompat?) {
        if (chatPanel!!.protocolProvider.isRegistered) {
            val contentUri = info!!.contentUri
            val filePath = FilePathHelper.getFilePath(parent, contentUri)
            if (StringUtils.isNotEmpty(filePath)) {
                sendSticker(filePath!!)
            }
            else aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST)
        }
        else {
            aTalkApp.showToastMessage(R.string.service_gui_MSG_SEND_CONNECTION_PROBLEM)
        }
    }

    private fun sendSticker(filePath: String) {
        val uiService = AndroidGUIActivator.uIService
        if (uiService != null) {
            chatPanel!!.addFTSendRequest(filePath, ChatMessage.MESSAGE_STICKER_SEND)
        }
    }

    /**
     * The thread lowers chat state from composing to inactive state. When
     * `refreshChatState` is called checks for eventual chat state refreshComposing.
     */
    internal inner class ChatStateControl : Thread() {
        private var refreshComposing = false
        private var initActive = false
        var cancel = false
        override fun run() {
            while (mChatState != ChatState.inactive) {
                refreshComposing = false
                initActive = false
                var newState: ChatState
                val delay: Long
                when (mChatState) {
                    ChatState.gone -> {
                        delay = 500
                        newState = ChatState.active
                    }
                    ChatState.composing -> {
                        delay = 10000
                        newState = ChatState.paused
                    }
                    ChatState.paused -> {
                        delay = 15000
                        newState = ChatState.inactive
                    }
                    else -> {
                        delay = 30000
                        newState = ChatState.inactive
                    }
                }
                synchronized(this) {
                    try {
                        // Waits the delay to enter newState
                        (this as Object).wait(delay)
                    } catch (e: InterruptedException) {
                        throw RuntimeException(e)
                    }
                }
                if (refreshComposing) {
                    newState = ChatState.composing
                }
                else if (initActive) {
                    newState = ChatState.active
                }
                else if (cancel) {
                    newState = ChatState.gone
                }

                // Timber.d("Chat State changes %s (%s)", newState, mChatState)
                // Post new chat state
                setNewChatState(newState)
            }
            chatStateCtrlThread = null
        }

        /**
         * Refresh the thread's control loop to ChatState.composing.
         */
        fun refreshChatState() {
            synchronized(this) {
                refreshComposing = true
                this.notify()
            }
        }

        /**
         * Initialize the thread' control loop to ChatState.active
         */
        fun initChatState() {
            synchronized(this) {
                initActive = true
                this.notify()
            }
        }

        /**
         * Cancels (ChatState.gone) and joins the thread.
         */
        fun cancel() {
            synchronized(this) {
                cancel = true
                this.notify()
            }
            try {
                this.join()
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }
    }

    fun getParent(): Activity {
        return parent
    }

    companion object {
        // Constant to detect slide left to cancel audio recording
        private const val min_distance = 100
    }
}