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
package org.atalk.hmos.gui.chat.filetransfer

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl
import net.java.sip.communicator.service.filehistory.FileRecord
import net.java.sip.communicator.service.protocol.FileTransfer
import net.java.sip.communicator.service.protocol.IMessage
import net.java.sip.communicator.service.protocol.event.FileTransferProgressEvent
import net.java.sip.communicator.service.protocol.event.FileTransferProgressListener
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent
import net.java.sip.communicator.util.ByteFormat
import net.java.sip.communicator.util.GuiUtils.formatSeconds
import net.java.sip.communicator.util.UtilActivator
import org.atalk.hmos.MyGlideApp.loadImage
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.ChatActivity
import org.atalk.hmos.gui.chat.ChatFragment
import org.atalk.hmos.gui.chat.ChatFragment.MessageViewHolder
import org.atalk.hmos.plugin.audioservice.AudioBgService
import org.atalk.hmos.plugin.audioservice.AudioBgService.PlaybackState
import org.atalk.persistance.FileBackend
import org.atalk.persistance.FileBackend.getMimeType
import org.atalk.persistance.FileBackend.getUriForFile
import org.atalk.persistance.FileBackend.getaTalkStore
import org.atalk.service.osgi.OSGiFragment
import org.jivesoftware.smack.util.StringUtils
import org.jivesoftware.smackx.httpfileupload.UploadProgressListener
import timber.log.Timber
import java.io.File
import java.util.*

/**
 * The `FileTransferConversationComponent` is the parent of all
 * file conversation components - for incoming, outgoing and history file transfers.
 *
 * The smack reply timer is extended to 10 sec in file sharing info exchanges e.g. IBB takes > 5sec
 *
 * @author Eng Chong Meng
 */
abstract class FileTransferConversation protected constructor(protected var mChatFragment: ChatFragment, dir: String) : OSGiFragment(), View.OnClickListener, OnLongClickListener, FileTransferProgressListener, UploadProgressListener, OnSeekBarChangeListener {
    /**
     * @return the fileTransfer file
     */
    /**
     * The xfer file full path for saving the received file.
     */
    var xferFile: File? = null
        protected set

    protected var mUri: Uri? = null
    protected var mDate: String? = null

    /**
     * The file transfer.
     */
    protected lateinit var mFileTransfer: FileTransfer

    /**
     * The size of the file to be transferred.
     */
    protected var mTransferFileSize = 0L

    /**
     * The time of the last fileTransfer update.
     */
    private var mLastTimestamp = -1L

    /**
     * The number of bytes last transferred.
     */
    private var mLastTransferredBytes = 0L

    /**
     * The last calculated progress speed.
     */
    private var mTransferSpeedAverage = 0L

    /**
     * The last estimated time.
     */
    private var mEstimatedTimeLeft = -1L
    private var playerState = STATE_STOP
    private var mPlayerAnimate: AnimationDrawable? = null
    private var isMediaAudio = false
    private var mimeType: String? = null
    private val mDir: String
    private var isSeeking = false
    private var positionSeek = 0

    /**
     * The file transfer index/position of the message in chatListAdapter
     */
    protected var msgViewId = 0
    /**
     * The message Uuid uniquely identify the record in the message database
     *
     * @return the uid for the requested message to send file
     */
    /**
     * The message Uuid  uniquely identify the record in the message database
     */
    lateinit var msgUuid: String
        protected set

    /*
     * mEntityJid can be Contact or ChatRoom
     */
    protected var mEntityJid: Any? = null

    /*
     * Transfer file encryption type
     */
    protected var mEncryption = IMessage.ENCRYPTION_NONE
    protected var mChatActivity = mChatFragment.activity as ChatActivity
    protected var mConnection = mChatFragment.chatPanel!!.protocolProvider.connection!!
    protected lateinit var messageViewHolder: MessageViewHolder

    protected fun inflateViewForFileTransfer(inflater: LayoutInflater, msgViewHolder: MessageViewHolder,
            container: ViewGroup?, init: Boolean): View? {
        messageViewHolder = msgViewHolder
        var convertView: View? = null
        if (init) {
            convertView = if (FileRecord.IN == mDir) inflater.inflate(R.layout.chat_file_transfer_in_row, container, false) else inflater.inflate(R.layout.chat_file_transfer_out_row, container, false)
            messageViewHolder.fileIcon = convertView.findViewById(R.id.button_file)
            messageViewHolder.stickerView = convertView.findViewById(R.id.sticker)
            messageViewHolder.playerView = convertView.findViewById(R.id.playerView)
            messageViewHolder.fileAudio = convertView.findViewById(R.id.filename_audio)
            messageViewHolder.playbackPlay = convertView.findViewById(R.id.playback_play)
            messageViewHolder.playbackPosition = convertView.findViewById(R.id.playback_position)
            messageViewHolder.playbackDuration = convertView.findViewById(R.id.playback_duration)
            messageViewHolder.playbackSeekBar = convertView.findViewById(R.id.playback_seekbar)
            messageViewHolder.fileLabel = convertView.findViewById(R.id.filexferFileNameView)
            messageViewHolder.fileStatus = convertView.findViewById(R.id.filexferStatusView)
            messageViewHolder.fileXferError = convertView.findViewById(R.id.errorView)
            messageViewHolder.encStateView = convertView.findViewById(R.id.encFileStateView)
            messageViewHolder.timeView = convertView.findViewById(R.id.xferTimeView)
            messageViewHolder.fileXferSpeed = convertView.findViewById(R.id.file_progressSpeed)
            messageViewHolder.estTimeRemain = convertView.findViewById(R.id.file_estTime)
            messageViewHolder.progressBar = convertView.findViewById(R.id.file_progressbar)
            messageViewHolder.cancelButton = convertView.findViewById(R.id.buttonCancel)
            messageViewHolder.retryButton = convertView.findViewById(R.id.button_retry)
            messageViewHolder.acceptButton = convertView.findViewById(R.id.button_accept)
            messageViewHolder.declineButton = convertView.findViewById(R.id.button_decline)
        }
        hideProgressRelatedComponents()

        // Note-5: playbackSeekBar is not visible and thumb partially clipped with xml default settings.
        // So increase the playbackSeekBar height to 16dp
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val scale = mChatActivity.resources.displayMetrics.density
            val dpPadding = (16 * scale + 0.5f).toInt()
            messageViewHolder.playbackSeekBar.requestLayout()
            messageViewHolder.playbackSeekBar.layoutParams.height = dpPadding
        }

        // set to viewHolder default state
        messageViewHolder.playerView.visibility = View.GONE
        messageViewHolder.stickerView.visibility = View.GONE
        messageViewHolder.fileLabel.visibility = View.VISIBLE
        messageViewHolder.playbackSeekBar.setOnSeekBarChangeListener(this)
        messageViewHolder.cancelButton.setOnClickListener(this)
        messageViewHolder.stickerView.setOnClickListener(this)
        messageViewHolder.playbackPlay.setOnClickListener(this)
        messageViewHolder.playbackPlay.setOnLongClickListener(this)
        mPlayerAnimate = messageViewHolder.playbackPlay.background as AnimationDrawable
        messageViewHolder.fileStatus.setTextColor(UtilActivator.resources.getColor("black"))
        return convertView
    }

    /**
     * A common routine to update the file transfer view component states
     *
     * @param status FileTransferStatusChangeEvent status
     * @param statusText the status text for update
     */
    protected fun updateXferFileViewState(status: Int, statusText: String?) {
        messageViewHolder.acceptButton.visibility = View.GONE
        messageViewHolder.declineButton.visibility = View.GONE
        messageViewHolder.cancelButton.visibility = View.GONE
        messageViewHolder.retryButton.visibility = View.GONE
        messageViewHolder.fileStatus.setTextColor(Color.BLACK)
        when (status) {
            FileTransferStatusChangeEvent.WAITING -> if (this is FileSendConversation) {
                messageViewHolder.cancelButton.visibility = View.VISIBLE
            }
            else {
                messageViewHolder.acceptButton.visibility = View.VISIBLE
                messageViewHolder.declineButton.visibility = View.VISIBLE
            }
            FileTransferStatusChangeEvent.PREPARING ->                 // Preserve the cancel button view height, avoid being partially hidden by android when it is enabled
                messageViewHolder.cancelButton.visibility = View.GONE
            FileTransferStatusChangeEvent.IN_PROGRESS -> {
                messageViewHolder.cancelButton.visibility = View.VISIBLE
                mConnection.replyTimeout = ProtocolProviderServiceJabberImpl.SMACK_REPLY_EXTENDED_TIMEOUT_10
            }

            FileTransferStatusChangeEvent.COMPLETED -> {
                if (xferFile != null) {
                    // Update file label and image for incoming file
                    if (FileRecord.IN == mDir) {
                        updateFileViewInfo(xferFile, false)
                    }
                    // set to full for progressBar on file transfer completed
                    var fileSize = xferFile!!.length()
                    // found http file download fileSize == 0; so fake to 100.
                    if (fileSize == 0L) {
                        fileSize = 100
                    }
                    onUploadProgress(fileSize, fileSize)
                    mConnection.replyTimeout = ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT
                }
            }

            FileTransferStatusChangeEvent.FAILED, FileTransferStatusChangeEvent.CANCELED -> {
                // Allow user retries only if sender cancels the file transfer
                if (FileRecord.OUT == mDir) {
                    messageViewHolder.retryButton.visibility = View.VISIBLE
                } // fall through
                messageViewHolder.fileStatus.setTextColor(Color.RED)
                mConnection.replyTimeout = ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT
            }
            FileTransferStatusChangeEvent.DECLINED -> {
                messageViewHolder.fileStatus.setTextColor(Color.RED)
                mConnection.replyTimeout = ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT
            }
        }
        if (!TextUtils.isEmpty(statusText)) {
            messageViewHolder.fileStatus.text = statusText
        }
        messageViewHolder.timeView.text = mDate
    }

    /**
     * Shows the given error message in the error area of this component.
     *
     * @param resId the message to show
     */
    protected fun showErrorMessage(resId: Int) {
        val message = resources.getString(resId)
        messageViewHolder.fileXferError.text = message
        messageViewHolder.fileXferError.visibility = TextView.VISIBLE
    }

    /**
     * Shows file thumbnail.
     *
     * @param thumbnail the thumbnail to show
     */
    protected fun showThumbnail(thumbnail: ByteArray?) {
        if (thumbnail != null && thumbnail.isNotEmpty()) {
            val thumbnailIcon = BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.size)
            val mWidth = thumbnailIcon.width
            val mHeight = thumbnailIcon.height
            if (mWidth > IMAGE_WIDTH || mHeight > IMAGE_HEIGHT) {
                messageViewHolder.fileIcon.scaleType = ScaleType.FIT_CENTER
            }
            else {
                messageViewHolder.fileIcon.scaleType = ScaleType.CENTER
            }
            messageViewHolder.fileIcon.setImageBitmap(thumbnailIcon)
            messageViewHolder.stickerView.visibility = View.VISIBLE
            messageViewHolder.stickerView.setImageBitmap(thumbnailIcon)
        }
    }

    /**
     * Initialize all the local parameters i.e. mXferFile, mUri, mimeType and isMediaAudio
     * Update the file transfer view display info in thumbnail or audio player UI accordingly.
     *
     * @param file the file that has been downloaded/received or sent
     * @param isHistory true if the view file is history, so show small image size
     */
    protected fun updateFileViewInfo(file: File?, isHistory: Boolean) {
        // File length = 0 will cause Glade to throw errors
        if (file == null || !file.exists() || file.length() == 0L)
            return

        xferFile = file
        mUri = getUriForFile(mChatActivity, file)
        mimeType = checkMimeType(file)
        isMediaAudio = mimeType != null && (mimeType!!.contains("audio") || mimeType!!.contains("3gp"))
        messageViewHolder.fileLabel.text = getFileLabel(file)
        if (isMediaAudio && playerInit()) {
            messageViewHolder.playerView.visibility = View.VISIBLE
            messageViewHolder.stickerView.visibility = View.GONE
            messageViewHolder.fileLabel.visibility = View.GONE
            messageViewHolder.fileAudio.text = getFileLabel(file)
        }
        else {
            messageViewHolder.playerView.visibility = View.GONE
            messageViewHolder.stickerView.visibility = View.VISIBLE
            messageViewHolder.fileLabel.visibility = View.VISIBLE
            updateImageView(isHistory)
        }
        val toolTip = aTalkApp.getResString(R.string.service_gui_OPEN_FILE_FROM_IMAGE)
        messageViewHolder.fileIcon.contentDescription = toolTip
        messageViewHolder.fileIcon.setOnClickListener(this)
        messageViewHolder.fileIcon.setOnLongClickListener { v: View ->
            Toast.makeText(v.context, toolTip, Toast.LENGTH_SHORT).show()
            true
        }
    }

    /**
     * Load the received media file image into the stickerView.
     * Ensure the loaded image view is fully visible after resource is ready.
     */
    private fun updateImageView(isHistory: Boolean) {
        if (isHistory || FileRecord.OUT == mDir) {
            loadImage(messageViewHolder.stickerView, xferFile!!, isHistory)
            return
        }
        Glide.with(aTalkApp.globalContext)
                .asDrawable()
                .load(Uri.fromFile(xferFile))
                .override(1280, 608)
                .into(object : CustomViewTarget<ImageView?, Drawable?>(messageViewHolder.stickerView) {
                    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable?>?) {
                        messageViewHolder.stickerView.setImageDrawable(resource)
                        if (resource is GifDrawable) {
                            resource.start()
                        }
                        mChatFragment.scrollToBottom()
                    }

                    override fun onResourceCleared(placeholder: Drawable?) {
                        Timber.d("Glide onResourceCleared received!!!")
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        messageViewHolder.stickerView.setImageResource(R.drawable.ic_file_open)
                        mChatFragment.scrollToBottom()
                    }

                }
                )
    }

    /**
     * Sets the file transfer and addProgressListener().
     * Note: HttpFileUpload adds ProgressListener in httpFileUploadManager.uploadFile()
     *
     * @param fileTransfer the file transfer
     * @param transferFileSize the size of the transferred file Running in thread, not UI here
     */
    protected fun setFileTransfer(fileTransfer: FileTransfer, transferFileSize: Long) {
        mFileTransfer = fileTransfer
        mTransferFileSize = transferFileSize
        fileTransfer.addProgressListener(this)
    }

    /**
     * Hides all progress related components.
     */
    protected fun hideProgressRelatedComponents() {
        messageViewHolder.progressBar.visibility = View.GONE
        messageViewHolder.fileXferSpeed.visibility = View.GONE
        messageViewHolder.estTimeRemain.visibility = View.GONE
    }

    /**
     * Remove file transfer progress listener
     */
    protected fun removeProgressListener() {
        mFileTransfer.removeProgressListener(this)
    }

    /**
     * Updates progress bar progress line every time a progress event has been received file transport
     * Note: total size of event.getProgress() is always lag behind event.getFileTransfer().getTransferredBytes();
     *
     * @param event the `FileTransferProgressEvent` that notifies us
     */
    override fun progressChanged(event: FileTransferProgressEvent?) {
        val transferredBytes = event!!.getFileTransfer().getTransferredBytes()
        val progressTimestamp = event.getTimestamp()
        updateProgress(transferredBytes, progressTimestamp)
    }

    /**
     * Callback for displaying http file upload, and file transfer progress status.
     *
     * @param uploadedBytes the number of bytes uploaded at the moment
     * @param totalBytes the total number of bytes to be uploaded
     */
    override fun onUploadProgress(uploadedBytes: Long, totalBytes: Long) {
        updateProgress(uploadedBytes, System.currentTimeMillis())
    }

    private fun updateProgress(transferredBytes: Long, progressTimestamp: Long) {
        val SMOOTHING_FACTOR = 100L

        // before file transfer start is -1
        if (transferredBytes < 0) return
        val bytesString = ByteFormat.format(transferredBytes)
        val byteTransferDelta = if (transferredBytes == 0L) 0 else transferredBytes - mLastTransferredBytes

        // Calculate running average transfer speed in bytes/sec and time left, with the given SMOOTHING_FACTOR
        if (mLastTimestamp > 0) {
            val timeElapsed = progressTimestamp - mLastTimestamp
            val transferSpeedCurrent = if (timeElapsed > 0) byteTransferDelta * 1000 / timeElapsed else 0
            mTransferSpeedAverage = if (mTransferSpeedAverage != 0L) {
                (transferSpeedCurrent + (SMOOTHING_FACTOR - 1) * mTransferSpeedAverage) / SMOOTHING_FACTOR
            }
            else {
                transferSpeedCurrent
            }
        }
        else {
            mEstimatedTimeLeft = -1
        }

        // Calculate  running average time left in sec
        if (mTransferSpeedAverage > 0) mEstimatedTimeLeft = (mTransferFileSize - transferredBytes) / mTransferSpeedAverage
        mLastTimestamp = progressTimestamp
        mLastTransferredBytes = transferredBytes
        runOnUiThread {

            // Need to do it here as it was found that Http File Upload completed before the progress Bar is even visible
            if (!messageViewHolder.progressBar.isShown) {
                messageViewHolder.progressBar.visibility = View.VISIBLE
                messageViewHolder.progressBar.max = mTransferFileSize.toInt()
                mChatFragment.scrollToBottom()
            }
            // Note: progress bar can only handle int size (4-bytes: 2,147,483, 647);
            messageViewHolder.progressBar.progress = transferredBytes.toInt()
            if (mTransferSpeedAverage > 0) {
                messageViewHolder.fileXferSpeed.visibility = View.VISIBLE
                messageViewHolder.fileXferSpeed.text = aTalkApp.getResString(R.string.service_gui_SPEED, ByteFormat.format(mTransferSpeedAverage), bytesString)
            }
            if (transferredBytes >= mTransferFileSize) {
                messageViewHolder.estTimeRemain.visibility = View.GONE
            }
            else if (mEstimatedTimeLeft > 0) {
                messageViewHolder.estTimeRemain.visibility = View.VISIBLE
                messageViewHolder.estTimeRemain.text = aTalkApp.getResString(R.string.service_gui_ESTIMATED_TIME,
                        formatSeconds(mEstimatedTimeLeft * 1000))
            }
        }
    }

    /**
     * Returns a string showing information for the given file.
     *
     * @param file the file
     *
     * @return the name and size of the given file
     */
    private fun getFileLabel(file: File?): String {
        if (file != null && file.exists()) {
            val fileName = file.name
            val fileSize = file.length()
            return getFileLabel(fileName, fileSize)
        }
        return if (file == null) "" else file.name
    }

    /**
     * Returns the string, showing information for the given file.
     *
     * @param fileName the name of the file
     * @param fileSize the size of the file
     *
     * @return the name of the given file
     */
    protected fun getFileLabel(fileName: String?, fileSize: Long): String {
        val text = ByteFormat.format(fileSize)
        return "$fileName ($text)"
    }

    /**
     * Returns the label to show on the progress bar.
     *
     * @param bytesString the bytes that have been transferred
     *
     * @return the label to show on the progress bar
     */
    protected abstract fun getProgressLabel(bytesString: Long): String

    // abstract updateView for class extension implementation
    protected abstract fun updateView(status: Int, reason: String?)

    /**
     * Init some of the file transfer parameters. Mainly call by sendFile and File History.
     *
     * @param status File transfer send status
     * @param jid Contact or ChatRoom for Http file upload service
     * @param encType File encryption type
     * @param reason Contact or ChatRoom for Http file upload service
     */
    fun setStatus(status: Int, jid: Any?, encType: Int, reason: String?) {
        mEntityJid = jid
        mEncryption = encType
        // Must execute in UiThread to Update UI information
        runOnUiThread {
            setEncState(mEncryption)
            updateView(status, reason)
        }
    }

    /**
     * Set the file encryption status icon.
     * Access directly by file receive constructor; sendFile via setStatus().
     *
     * @param encType the encryption
     */
    protected fun setEncState(encType: Int) {
        if (IMessage.ENCRYPTION_OMEMO == encType) messageViewHolder.encStateView.setImageResource(R.drawable.encryption_omemo) else messageViewHolder.encStateView.setImageResource(R.drawable.encryption_none)
    }

    /**
     * Get the current status fo the file transfer
     *
     * @return the current status of the file transfer
     */
    protected open val xferStatus: Int
        get() = mChatFragment.chatListAdapter!!.getXferStatus(msgViewId)

    /**
     * Handles buttons click action events.
     */
    override fun onClick(view: View) {
        when (view.id) {
            R.id.button_file, R.id.sticker -> mChatActivity.openDownloadable(xferFile, view)
            R.id.playback_play -> playStart()
            R.id.buttonCancel -> {
                messageViewHolder.retryButton.visibility = View.GONE
                messageViewHolder.cancelButton.visibility = View.GONE
                // Let file transport event call back to handle
                // updateView(FileTransferStatusChangeEvent.CANCELED, null);
                mFileTransfer.cancel()
            }
        }
    }

    /**
     * Handles buttons long press action events
     * mainly use to stop and release player
     */
    override fun onLongClick(v: View): Boolean {
        if (v.id == R.id.playback_play) {
            playerStop()
            return true
        }
        return false
    }

    /**
     * Initialize the broadcast receiver for the media player (uri).
     * Keep the active bc receiver instance in bcRegisters list to ensure only one bc is registered
     *
     * @param file the media file
     *
     * @return true if init is successful
     */
    private fun bcReceiverInit(file: File?): Boolean {
        val mimeType = checkMimeType(file)
        if (mimeType != null && (mimeType.contains("audio") || mimeType.contains("3gp"))) {
            if (playerState == STATE_STOP) {
                var bcReceiver: BroadcastReceiver?
                if (bcRegisters[mUri].also { bcReceiver = it } != null) {
                    LocalBroadcastManager.getInstance(mChatActivity).unregisterReceiver(bcReceiver!!)
                }
                val filter = IntentFilter()
                filter.addAction(AudioBgService.PLAYBACK_STATE)
                filter.addAction(AudioBgService.PLAYBACK_STATUS)
                LocalBroadcastManager.getInstance(mChatActivity).registerReceiver(mReceiver, filter)
                bcRegisters[mUri] = mReceiver
            }
            return true
        }
        return false
    }

    /**
     * Get the active media player status or just media info for the view display;
     * update the view holder content via Broadcast receiver
     */
    private fun playerInit(): Boolean {
        if (isMediaAudio) {
            if (playerState == STATE_STOP) {
                if (!bcReceiverInit(xferFile)) return false
                val intent = Intent(mChatActivity, AudioBgService::class.java)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.setDataAndType(mUri, mimeType)
                intent.action = AudioBgService.ACTION_PLAYER_INIT
                mChatActivity.startService(intent)
            }
            return true
        }
        return false
    }

    /**
     * Stop the current active media player playback
     */
    private fun playerStop() {
        if (isMediaAudio) {
            if (playerState == STATE_PAUSE || playerState == STATE_PLAY) {
                val intent = Intent(mChatActivity, AudioBgService::class.java)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.setDataAndType(mUri, mimeType)
                intent.action = AudioBgService.ACTION_PLAYER_STOP
                mChatActivity.startService(intent)
            }
        }
    }

    /**
     * Toggle audio file playback states:
     * STOP -> PLAY -> PAUSE -> PLAY;
     * long press play button to STOP
     *
     * Proceed to open the file for VIEW if this is not an audio file
     */
    private fun playStart() {
        var intent = Intent(mChatActivity, AudioBgService::class.java)
        if (isMediaAudio) {
            if (playerState == STATE_PLAY) {
                intent.data = mUri
                intent.action = AudioBgService.ACTION_PLAYER_PAUSE
                mChatActivity.startService(intent)
                return
            }
            else if (playerState == STATE_STOP) {
                if (!bcReceiverInit(xferFile)) return
            }
            intent.action = AudioBgService.ACTION_PLAYER_START
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.setDataAndType(mUri, mimeType)
            mChatActivity.startService(intent)
            return
        }
        intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.setDataAndType(mUri, mimeType)
        val manager = mChatActivity.packageManager
        val info = manager.queryIntentActivities(intent, 0)
        if (info.size == 0) {
            intent.setDataAndType(mUri, "*/*")
        }
        try {
            mChatActivity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            aTalkApp.showToastMessage(R.string.service_gui_FILE_OPEN_NO_APPLICATION)
        }
    }

    /**
     * SeekTo player new start play position
     *
     * @param position seek time position
     */
    private fun playerSeek(position: Int) {
        if (isMediaAudio) {
            if (!bcReceiverInit(xferFile)) return
            val intent = Intent(mChatActivity, AudioBgService::class.java)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.setDataAndType(mUri, mimeType)
            intent.putExtra(AudioBgService.PLAYBACK_POSITION, position)
            intent.action = AudioBgService.ACTION_PLAYER_SEEK
            mChatActivity.startService(intent)
        }
    }

    /**
     * Media player BroadcastReceiver to animate and update player view holder info
     */
    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // proceed only if it is the playback of the current mUri
            if (mUri != intent.getParcelableExtra(AudioBgService.PLAYBACK_URI)) return
            val position = intent.getIntExtra(AudioBgService.PLAYBACK_POSITION, 0)
            val audioDuration = intent.getIntExtra(AudioBgService.PLAYBACK_DURATION, 0)
            if (playerState == STATE_PLAY && AudioBgService.PLAYBACK_STATUS == intent.action) {
                if (!isSeeking) messageViewHolder.playbackPosition.text = formatTime(position)
                messageViewHolder.playbackDuration.text = formatTime(audioDuration - position)
                messageViewHolder.playbackSeekBar.max = audioDuration
                messageViewHolder.playbackSeekBar.progress = position
            }
            else if (AudioBgService.PLAYBACK_STATE == intent.action) {
                val playbackState = intent.getSerializableExtra(AudioBgService.PLAYBACK_STATE)
                Timber.d("Audio playback state: %s (%s/%s): %s", playbackState, position, audioDuration, mUri!!.path)
                when (playbackState) {
                    PlaybackState.INITIATE -> {
                        playerState = STATE_IDLE
                        messageViewHolder.playbackDuration.text = formatTime(audioDuration)
                        messageViewHolder.playbackPosition.text = formatTime(0)
                        messageViewHolder.playbackSeekBar.max = audioDuration
                        messageViewHolder.playbackSeekBar.progress = 0
                        messageViewHolder.playbackPlay.setImageResource(R.drawable.ic_player_stop)
                        mPlayerAnimate!!.stop()
                    }
                    PlaybackState.PLAY -> {
                        playerState = STATE_PLAY
                        messageViewHolder.playbackSeekBar.max = audioDuration
                        messageViewHolder.playerView.clearAnimation()
                        messageViewHolder.playbackPlay.setImageDrawable(null)
                        mPlayerAnimate!!.start()
                    }
                    PlaybackState.STOP -> {
                        playerState = STATE_STOP
                        bcRegisters.remove(mUri)
                        LocalBroadcastManager.getInstance(mChatActivity).unregisterReceiver(this)
                        if (playerState != STATE_STOP) {
                            playerState = STATE_PAUSE
                        }
                        messageViewHolder.playbackPosition.text = formatTime(position)
                        messageViewHolder.playbackDuration.text = formatTime(audioDuration - position)
                        messageViewHolder.playbackSeekBar.max = audioDuration
                        messageViewHolder.playbackSeekBar.progress = position
                        mPlayerAnimate!!.stop()
                        messageViewHolder.playbackPlay.setImageResource(if (playerState == STATE_PAUSE) R.drawable.ic_player_pause else R.drawable.ic_player_stop)
                    }
                    PlaybackState.PAUSE -> {
                        if (playerState != STATE_STOP) {
                            playerState = STATE_PAUSE
                        }
                        messageViewHolder.playbackPosition.text = formatTime(position)
                        messageViewHolder.playbackDuration.text = formatTime(audioDuration - position)
                        messageViewHolder.playbackSeekBar.max = audioDuration
                        messageViewHolder.playbackSeekBar.progress = position
                        mPlayerAnimate!!.stop()
                        messageViewHolder.playbackPlay.setImageResource(if (playerState == STATE_PAUSE) R.drawable.ic_player_pause else R.drawable.ic_player_stop)
                    }
                }
            }
        }
    }

    init {
        mDir = dir
    }

    /**
     * OnSeekBarChangeListener callback interface during multimedia playback
     *
     * A SeekBar callback that notifies clients when the progress level has been
     * changed. This includes changes that were initiated by the user through a
     * touch gesture or arrow key/trackball as well as changes that were initiated
     * programmatically.
     */
    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (fromUser && messageViewHolder.playbackSeekBar === seekBar) {
            positionSeek = progress
            messageViewHolder.playbackPosition.text = formatTime(progress)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        if (messageViewHolder.playbackSeekBar === seekBar) {
            isSeeking = true
        }
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        if (messageViewHolder.playbackSeekBar === seekBar) {
            playerSeek(positionSeek)
            isSeeking = false
        }
    }

    /**
     * Format the given time to mm:ss
     *
     * @param time time is ms
     *
     * @return the formatted time string in mm:ss
     */
    private fun formatTime(time: Int): String {
        // int ms = (time % 1000) / 10;
        var seconds = time / 1000
        val minutes = seconds / 60
        seconds %= 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    /**
     * Determine the mimeType of the given file
     *
     * @param file the media file to check
     *
     * @return mimeType or null if undetermined
     */
    private fun checkMimeType(file: File?): String? {
        return if (!file!!.exists()) {
            // aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST);
            null
        }
        else try {
            val uri = getUriForFile(mChatActivity, file)
            var mimeType = getMimeType(mChatActivity, uri)
            if (mimeType == null || mimeType.contains("application")) {
                mimeType = "*/*"
            }
            mimeType
        } catch (e: SecurityException) {
            Timber.i("No permission to access %s: %s", file.absolutePath, e.message)
            aTalkApp.showToastMessage(R.string.service_gui_FILE_OPEN_NO_PERMISSION)
            null
        }
    }

    /**
     * Generate the mXferFile full filePath based on the given fileName and mimeType
     *
     * @param fileName the incoming xfer fileName
     * @param mimeType the incoming file mimeType
     */
    protected fun setTransferFilePath(fileName: String, mimeType: String?) {
        var downloadPath = FileBackend.MEDIA_DOCUMENT
        if (fileName.contains("voice-"))
            downloadPath = FileBackend.MEDIA_VOICE_RECEIVE
        else if (StringUtils.isNotEmpty(mimeType) && !mimeType!!.startsWith("*")) {
            downloadPath = FileBackend.MEDIA + File.separator + mimeType.split("/")[0]
        }

        val downloadDir = getaTalkStore(downloadPath, true)
        xferFile = File(downloadDir, fileName)

        // If a file with the given name already exists, add an index to the file name.
        var index = 0
        var filenameLength = fileName.lastIndexOf(".")
        if (filenameLength == -1) {
            filenameLength = fileName.length
        }

        while (xferFile != null && xferFile!!.exists()) {
            val newFileName = (fileName.substring(0, filenameLength) + "-"
                    + ++index + fileName.substring(filenameLength))
            xferFile = File(downloadDir, newFileName)
        }
    }

    companion object {
        /**
         * Image default width / height.
         */
        private const val IMAGE_WIDTH = 64
        private const val IMAGE_HEIGHT = 64

        /**
         * The state of a player where playback is stopped
         */
        private const val STATE_STOP = 0

        /**
         * The state of a player when it's created
         */
        private const val STATE_IDLE = 1

        /**
         * The state of a player where playback is paused
         */
        private const val STATE_PAUSE = 2

        /**
         * The state of a player that is actively playing
         */
        private const val STATE_PLAY = 3
        private val bcRegisters = HashMap<Uri?, BroadcastReceiver>()
    }
}