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

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import net.java.sip.communicator.impl.filehistory.FileHistoryServiceImpl
import net.java.sip.communicator.impl.protocol.jabber.OutgoingFileSendEntityImpl
import net.java.sip.communicator.impl.protocol.jabber.OutgoingFileTransferJabberImpl
import net.java.sip.communicator.service.filehistory.FileRecord
import net.java.sip.communicator.service.protocol.FileTransfer
import net.java.sip.communicator.service.protocol.IMessage
import net.java.sip.communicator.service.protocol.event.FileTransferCreatedEvent
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.FileTransferStatusListener
import net.java.sip.communicator.util.ConfigurationUtils
import net.java.sip.communicator.util.GuiUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.chat.ChatFragment
import org.atalk.hmos.gui.chat.ChatMessage
import org.atalk.persistance.FileBackend
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*

/**
 * The `SendFileConversationComponent` is the component added in the chat conversation
 * when user sends a file either via legacy file transfer or httpFileUpload protocol.
 *
 * @author Eng Chong Meng
 */
open class FileSendConversation private constructor(cPanel: ChatFragment, dir: String) : FileTransferConversation(cPanel, dir), FileTransferStatusListener {
    private var mSendTo: String? = null

    /**
     * Check to see if the file transfer is sending sticker
     *
     * @return true if sending sticker
     */
    var isStickerMode = false
        private set
    private var mFHS: FileHistoryServiceImpl? = null
    private var mChatType = 0
    var fileThumbnail: ByteArray? = null
        private set

    /**
     * For Http file Upload must set to true to update the message in the DB
     */
    private var mUpdateDB = false
    fun sendFileConversationForm(inflater: LayoutInflater, msgViewHolder: ChatFragment.MessageViewHolder,
            container: ViewGroup?, id: Int, init: Boolean): View? {
        val convertView = inflateViewForFileTransfer(inflater, msgViewHolder, container, init)
        msgViewId = id
        updateFileViewInfo(xferFile, false)
        messageViewHolder.retryButton.setOnClickListener { v: View? ->
            messageViewHolder.retryButton.visibility = View.GONE
            messageViewHolder.cancelButton.visibility = View.GONE
            sendFileTransferRequest(fileThumbnail)
        }

        /* Must track file transfer status as Android will redraw on listView scrolling, new message send or received */
        val status = xferStatus
        if (status != FileTransferStatusChangeEvent.CANCELED
                && status != FileTransferStatusChangeEvent.COMPLETED) {
            updateXferFileViewState(FileTransferStatusChangeEvent.PREPARING,
                    aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_PREPARING, mSendTo))
            sendFileWithThumbnail()
        }
        else {
            updateView(status, null)
        }
        return convertView
    }

    /**
     * Handles file transfer status changes. Updates the interface to reflect the changes.
     */
    override fun updateView(status: Int, reason: String?) {
        var statusText: String? = null
        updateFTStatus(status)
        when (status) {
            FileTransferStatusChangeEvent.PREPARING -> statusText = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_PREPARING, mSendTo)
            FileTransferStatusChangeEvent.WAITING -> statusText = aTalkApp.getResString(R.string.xFile_FILE_WAITING_TO_ACCEPT, mSendTo)
            FileTransferStatusChangeEvent.IN_PROGRESS -> {
                statusText = aTalkApp.getResString(R.string.xFile_FILE_SENDING_TO, mSendTo)
                // Transfer file record creation only after mEntityJid is known.
                if (mEntityJid != null && !mUpdateDB) {
                    createFileSendRecord()
                    mUpdateDB = true
                    updateFTStatus(status)
                }
            }
            FileTransferStatusChangeEvent.COMPLETED -> statusText = aTalkApp.getResString(R.string.xFile_FILE_SEND_COMPLETED, mSendTo)
            FileTransferStatusChangeEvent.DECLINED -> statusText = aTalkApp.getResString(R.string.xFile_FILE_SEND_DECLINED, mSendTo)
            FileTransferStatusChangeEvent.FAILED -> {
                statusText = aTalkApp.getResString(R.string.xFile_FILE_UNABLE_TO_SEND, mSendTo)
                if (!TextUtils.isEmpty(reason)) {
                    statusText += "\n$reason"
                }
            }
            FileTransferStatusChangeEvent.CANCELED -> {
                // Inform remote user if sender canceled; not in standard legacy file xfer protocol event
                statusText = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED)
                if (!TextUtils.isEmpty(reason)) {
                    statusText += "\n$reason"
                }
                if (mFileTransfer is OutgoingFileTransferJabberImpl) {
                    mChatFragment.chatPanel!!.sendMessage(statusText,
                            IMessage.FLAG_REMOTE_ONLY or IMessage.ENCODE_PLAIN)
                }
            }
        }
        updateXferFileViewState(status, statusText)
        mChatFragment.scrollToBottom()
    }

    /**
     * Create a new File send message/record for file transfer status tracking; File transport used is undefined.
     * Use OutgoingFileSendEntityImpl class, as recipient entityJid can either be contact or chatRoom
     */
    private fun createFileSendRecord() {
        val fileTransfer = OutgoingFileSendEntityImpl(mEntityJid!!, msgUuid, xferFile!!.path)
        val event = FileTransferCreatedEvent(fileTransfer, Date())
        mFHS!!.fileTransferCreated(event)
    }

    /**
     * Update the file transfer status into the DB if the file record has been created i.e. mUpdateDB
     * is true; update also the msgCache (and ChatSession UI) to ensure the file send request will not
     * get trigger again. The msgCache record will be used for view display on chat session resume.
     *
     *  msgUuid The message UUID
     *  status File transfer status
     */
    private fun updateFTStatus(status: Int) {
        val fileName = xferFile!!.path
        if (mUpdateDB) {
            Timber.e("updateFTStatusToDB on status: %s; row count: %s", status,
                    mFHS!!.updateFTStatusToDB(msgUuid!!, status, fileName, mEncryption, ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY))
        }
        mChatFragment.updateFTStatus(msgUuid, status, fileName, mEncryption, ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY)
    }

    /**
     * Handles file transfer status changes. Updates the interface to reflect the changes.
     * Listens for changes in file transfers and update the DB record status if known.
     * Translate FileTransfer status to FileRecord status before updateFTStatus()
     *
     * @param event FileTransferStatusChangeEvent
     */
    override fun statusChanged(event: FileTransferStatusChangeEvent) {
        val fileTransfer = event.getFileTransfer() ?: return
        val status = event.getNewStatus()
        val reason = event.getReason()
        // Timber.e(new Exception(), "StatusChanged: %s => %s", status, reason);

        // Must execute in UiThread to Update UI information
        runOnUiThread {
            updateView(status, reason)
            if (status == FileTransferStatusChangeEvent.COMPLETED || status == FileTransferStatusChangeEvent.CANCELED || status == FileTransferStatusChangeEvent.FAILED || status == FileTransferStatusChangeEvent.DECLINED) {
                // must update this in UI, otherwise the status is not being updated to FileRecord
                fileTransfer.removeStatusListener(this@FileSendConversation)
            }
        }
    }

    /**
     * Sets the `FileTransfer` object received, associated with the file transfer
     * process in this panel. Registered callback to receive all file transfer events.
     * Note: HttpFileUpload adds ProgressListener in httpFileUploadManager.uploadFile()
     *
     * @param fileTransfer the `FileTransfer` object associated with this panel
     */
    fun setTransportFileTransfer(fileTransfer: FileTransfer) {
        // activate File History service to keep track of the progress - need more work if want to keep sending history.
        // fileTransfer.addStatusListener(new FileHistoryServiceImpl());
        mFileTransfer = fileTransfer
        fileTransfer.addStatusListener(this)
        setFileTransfer(fileTransfer, xferFile!!.length())
        runOnUiThread { updateView(FileTransferStatusChangeEvent.WAITING, null) }
    }

    /**
     * Returns the label to show on the progress bar.
     *
     * @param bytesString the bytes that have been transferred
     *
     * @return the label to show on the progress bar
     */
    override fun getProgressLabel(bytesString: Long): String {
        return aTalkApp.getResString(R.string.xFile_FILE_BYTE_SENT, bytesString)
    }

    /**
     * Get the file thumbnail if applicable and start the file transfer process.
     * use sBitmap() to retrieve the thumbnail for smallest size;
     * The .as(byte[].class) returns a scale of the gif animation file (large size)
     */
    private fun sendFileWithThumbnail() {
        if (ConfigurationUtils.isSendThumbnail() && ChatFragment.MSGTYPE_OMEMO != mChatType
                && !isStickerMode && FileBackend.isMediaFile(xferFile!!)) {
            Glide.with(aTalkApp.globalContext)
                    .asBitmap()
                    .load(Uri.fromFile(xferFile))
                    .into(object : CustomTarget<Bitmap?>(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT) {
                        override fun onResourceReady(bitmap: Bitmap,
                                transition: Transition<in Bitmap?>?) {
                            val stream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                            val byteData = stream.toByteArray()
                            Timber.d("ByteData Glide byteData: %s", byteData.size)
                            sendFileTransferRequest(byteData)
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            Timber.d("Glide onLoadCleared received!!!")
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            // load failed due to some reason, notify callers here about the same
                            sendFileTransferRequest(null)
                        }
                    }
                    )
        }
        else {
            sendFileTransferRequest(null)
        }
    }

    /**
     * Send the file transfer offer to remote. Need to update view to WAITING here after
     * sendFile() step; setTransportFileTransfer#fileTransfer.addStatusListener()
     * setup is only call after the file offer initiated event.
     *
     * @param thumbnail file thumbnail or null (not video media file)
     */
    fun sendFileTransferRequest(thumbnail: ByteArray?) {
        fileThumbnail = thumbnail
        mChatFragment.SendFile(this, msgViewId).execute()
    }

    companion object {
        /**
         * The thumbnail default width.
         */
        private const val THUMBNAIL_WIDTH = 64

        /**
         * The thumbnail default height.
         */
        private const val THUMBNAIL_HEIGHT = 64

        /**
         * Creates a `SendFileConversationComponent` by specifying the parent chat panel, where
         * this component is added, the destination contact of the transfer and file to transfer.
         *
         * @param cPanel the parent chat panel, where this view component is added
         * @param sendTo the name of the destination contact
         * @param fileName the file to transfer
         */
        fun newInstance(cPanel: ChatFragment, msgUuid: String?, sendTo: String?,
                fileName: String, chatType: Int, stickerMode: Boolean): FileSendConversation {
            val fragmentSFC = FileSendConversation(cPanel, FileRecord.OUT)
            fragmentSFC.msgUuid = msgUuid
            fragmentSFC.mSendTo = sendTo
            fragmentSFC.xferFile = File(fileName)
            fragmentSFC.mTransferFileSize = fragmentSFC.xferFile!!.length()
            fragmentSFC.mDate = GuiUtils.formatDateTime(null)
            fragmentSFC.mChatType = chatType
            fragmentSFC.isStickerMode = stickerMode
            fragmentSFC.mFHS = AndroidGUIActivator.fileHistoryService as FileHistoryServiceImpl
            return fragmentSFC
        }
    }
}