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

import android.os.AsyncTask
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import net.java.sip.communicator.impl.filehistory.FileHistoryServiceImpl
import net.java.sip.communicator.service.filehistory.FileRecord
import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer
import net.java.sip.communicator.service.protocol.event.FileTransferCreatedEvent
import net.java.sip.communicator.service.protocol.event.FileTransferRequestEvent
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.FileTransferStatusListener
import net.java.sip.communicator.service.protocol.event.ScFileTransferListener
import net.java.sip.communicator.util.ConfigurationUtils.isAutoAcceptFile
import net.java.sip.communicator.util.ConfigurationUtils.isSendThumbnail
import net.java.sip.communicator.util.GuiUtils.formatDateTime
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.chat.ChatFragment
import org.atalk.hmos.gui.chat.ChatFragment.MessageViewHolder
import org.atalk.hmos.gui.chat.ChatMessage
import timber.log.Timber
import java.io.File
import java.util.*

/**
 * The `ReceiveFileConversationComponent` is the component shown in the conversation area
 * of the chat window to display a incoming file transfer.
 * This UI is used by both the JingleFile and Legacy ByteStream incoming file.
 *
 * @author Eng Chong Meng
 */
class FileReceiveConversation private constructor(cPanel: ChatFragment, dir: String) : FileTransferConversation(cPanel, dir), ScFileTransferListener, FileTransferStatusListener {
    private lateinit var fileTransferRequest: IncomingFileTransferRequest
    private lateinit var fileTransferOpSet: OperationSetFileTransfer
    private var mFHS: FileHistoryServiceImpl? = null
    private var mSendTo: String? = null

    fun receiveFileConversionForm(inflater: LayoutInflater, msgViewHolder: MessageViewHolder,
            container: ViewGroup?, id: Int, init: Boolean): View? {
        msgViewId = id
        val convertView = inflateViewForFileTransfer(inflater, msgViewHolder, container, init)
        messageViewHolder.stickerView.setImageDrawable(null)
        xferFile = createOutFile(fileTransferRequest)
        mFileTransfer = fileTransferRequest.onPrepare(xferFile)
        mFileTransfer!!.addStatusListener(this)
        mEncryption = fileTransferRequest.getEncryptionType()
        setEncState(mEncryption)
        val downloadFileSize = fileTransferRequest.getFileSize()
        val fileLabel = getFileLabel(xferFile!!.name, downloadFileSize)
        messageViewHolder.fileLabel.text = fileLabel

        /* Must keep track of file transfer status as Android always request view redraw on
		listView scrolling, new message send or received */
        val status = xferStatus
        if (status != FileTransferStatusChangeEvent.DECLINED
                && status != FileTransferStatusChangeEvent.COMPLETED) {
            // Must reset button image to fileIcon on new(); else reused view may contain an old thumbnail image
            messageViewHolder.fileIcon.setImageResource(R.drawable.file_icon)
            if (isSendThumbnail()) {
                val thumbnail = fileTransferRequest.getThumbnail()
                showThumbnail(thumbnail)
            }
            messageViewHolder.acceptButton.setOnClickListener { v: View? ->
                updateXferFileViewState(FileTransferStatusChangeEvent.PREPARING,
                        aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_PREPARING, mSendTo))

                // set the download for global display parameter
                mChatFragment.chatListAdapter!!.setFileName(msgViewId, xferFile)
                AcceptFile().execute()
            }
            messageViewHolder.declineButton.setOnClickListener { v: View? ->
                updateXferFileViewState(FileTransferStatusChangeEvent.DECLINED,
                        aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_DECLINED))
                hideProgressRelatedComponents()
                try {
                    fileTransferRequest.declineFile()
                } catch (e: OperationFailedException) {
                    Timber.e("Decline file exception: %s", e.message)
                }
                // need to update status here as chatFragment statusListener is enabled for
                // fileTransfer and only after accept
                updateFTStatus(msgUuid, FileTransferStatusChangeEvent.CANCELED)
            }
            updateXferFileViewState(FileTransferStatusChangeEvent.WAITING,
                    aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_REQUEST_RECEIVED, mSendTo))
            if (isAutoAcceptFile(downloadFileSize)) {
                messageViewHolder.acceptButton.performClick()
            }
        }
        else {
            updateView(status, null)
        }
        return convertView
    }

    /**
     * Handles file transfer status changes. Updates the interface to reflect the changes.
     * Presently the file receive statusChanged event is only trigger by non-encrypted file transfer protocol
     * i.e. mEncryption = IMessage.ENCRYPTION_NONE
     */
    override fun updateView(status: Int, reason: String?) {
        var statusText: String? = null
        updateFTStatus(msgUuid, status)
        when (status) {
            FileTransferStatusChangeEvent.PREPARING ->                 // hideProgressRelatedComponents();
                statusText = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_PREPARING, mSendTo)
            FileTransferStatusChangeEvent.IN_PROGRESS -> statusText = aTalkApp.getResString(R.string.xFile_FILE_RECEIVING_FROM, mSendTo)
            FileTransferStatusChangeEvent.COMPLETED -> {
                statusText = aTalkApp.getResString(R.string.xFile_FILE_RECEIVE_COMPLETED, mSendTo)
                if (xferFile == null) { // Android view redraw happen
                    xferFile = mChatFragment.chatListAdapter!!.getFileName(msgViewId)
                }
            }
            FileTransferStatusChangeEvent.FAILED -> {
                // hideProgressRelatedComponents(); keep the status info for user view
                statusText = aTalkApp.getResString(R.string.xFile_FILE_RECEIVE_FAILED, mSendTo)
                if (!TextUtils.isEmpty(reason)) {
                    statusText += "\n$reason "
                }
            }
            FileTransferStatusChangeEvent.CANCELED -> statusText = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED)
            FileTransferStatusChangeEvent.DECLINED ->                 // hideProgressRelatedComponents();
                statusText = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_DECLINED)
        }
        updateXferFileViewState(status, statusText)
        mChatFragment.scrollToBottom()
    }

    /**
     * Creates the local file to download.
     *
     * @return the local created file to download.
     */
    private fun createOutFile(fileTransferRequest: IncomingFileTransferRequest): File {
        val fileName = fileTransferRequest.getFileName()
        val mimeType = fileTransferRequest.getMimeType()
        setTransferFilePath(fileName!!, mimeType!!)

        // Timber.d("Create Output File: %s (%s)", xferFile, fileName);
        // Change the file name to the name we would use on the local file system.
        if (xferFile!!.name != fileName) {
            val label = getFileLabel(xferFile!!.name, fileTransferRequest.getFileSize())
            messageViewHolder.fileLabel.text = label
        }
        return xferFile!!
    }

    /**
     * Accepts the file in a new thread.
     */
    private inner class AcceptFile : AsyncTask<Void?, Void?, Boolean>() {
        public override fun onPreExecute() {}
        override fun doInBackground(vararg params: Void?): Boolean {
            fileTransferRequest.acceptFile()
            // Remove previously added listener (no further required), that notify for request cancellations if any.
            fileTransferOpSet.removeFileTransferListener(this@FileReceiveConversation)
            if (mFileTransfer != null) {
                mChatFragment.addActiveFileTransfer(mFileTransfer!!.getID(), mFileTransfer!!, msgViewId)
            }
            return true
        }

        override fun onPostExecute(result: Boolean) {
            if (mFileTransfer != null) {
                setFileTransfer(mFileTransfer!!, fileTransferRequest.getFileSize())
            }
        }
    }

    /**
     * Update the file transfer status into the DB, also the msgCache to ensure the file send request
     * will not get trigger again. The msgCache record will be used for view display on chat session resume.
     * Delete file with zero length; not to cluster directory with invalid files
     *
     * @param msgUuid The message UUID
     * @param status File transfer status
     */
    private fun updateFTStatus(msgUuid: String?, status: Int) {
        val fileName = if (xferFile == null) "" else xferFile!!.path
        if (status == FileTransferStatusChangeEvent.CANCELED || status == FileTransferStatusChangeEvent.DECLINED) {
            if (xferFile != null && xferFile!!.exists() && xferFile!!.length() == 0L && xferFile!!.delete()) {
                Timber.d("Deleted file with zero length: %s", xferFile)
            }
        }
        mFHS!!.updateFTStatusToDB(msgUuid!!, status, fileName, mEncryption, ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY)
        mChatFragment.updateFTStatus(msgUuid, status, fileName, mEncryption, ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY)
    }

    /**
     * Handles file transfer status changes. Updates the interface to reflect the changes.
     * Listens for changes in file transfers and update the DB record status if known.
     * Translate FileTransfer status to FileRecord status before updateFTStatus()
     *
     * @param event the event containing information about the change
     */
    override fun statusChanged(event: FileTransferStatusChangeEvent) {
        val fileTransfer = event.getFileTransfer()
        val status = event.getNewStatus()
        val reason = event.getReason()
        Timber.d("File receive status change: %s: %s", status, xferFile)

        // Event thread - Must execute in UiThread to Update UI information
        runOnUiThread {
            updateView(status, reason)
            if (status == FileTransferStatusChangeEvent.COMPLETED || status == FileTransferStatusChangeEvent.CANCELED || status == FileTransferStatusChangeEvent.FAILED || status == FileTransferStatusChangeEvent.DECLINED) {
                // must update this in UI, otherwise the status is not being updated to FileRecord
                fileTransfer.removeStatusListener(this)
            }
        }
    }
    /* ========== ScFileTransferListener class method implementation ========== */
    /**
     * Called when a new `IncomingFileTransferRequest` has been received. Too late to handle here.
     *
     * @param event the `FileTransferRequestEvent` containing the newly received request and other details.
     *
     * @see FileTransferActivator.fileTransferRequestReceived
     * @see FileHistoryServiceImpl.fileTransferRequestReceived
     */
    override fun fileTransferRequestReceived(event: FileTransferRequestEvent) {
        // Event is being handled by FileTransferActivator and FileHistoryServiceImpl
        // ScFileTransferListener is only being added after this event - nothing can do here.
    }

    /**
     * Called when a `FileTransferCreatedEvent` has been received from sendFile.
     *
     * @param event the `FileTransferCreatedEvent` containing the newly received
     * file transfer and other details.
     *
     * @see FileHistoryServiceImpl.fileTransferCreated
     */
    override fun fileTransferCreated(event: FileTransferCreatedEvent?) {
        // Event is being handled by FileHistoryServiceImpl for both incoming, outgoing and
        // used by FileSendConversion#createHttpFileUploadRecord - so not doing anything here
    }

    /**
     * Called when an `IncomingFileTransferRequest` has been rejected.
     *
     * @param event the `FileTransferRequestEvent` containing the received request which was rejected.
     */
    override fun fileTransferRequestRejected(event: FileTransferRequestEvent) {
        val request = event.getRequest()
        updateFTStatus(request.getID(), FileTransferStatusChangeEvent.DECLINED)

        // Event triggered - Must execute in UiThread to Update UI information
        runOnUiThread {
            if (request == fileTransferRequest) {
                updateXferFileViewState(FileTransferStatusChangeEvent.DECLINED,
                        aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_DECLINED))
                fileTransferOpSet.removeFileTransferListener(this@FileReceiveConversation)
                hideProgressRelatedComponents()
            }
        }
    }

    /**
     * Called when an `IncomingFileTransferRequest` has been canceled from the remote sender.
     * Note: This is not a standard XMPP Legacy FileTransfer protocol - aTalk yet to implement this
     *
     * @param event the `FileTransferRequestEvent` containing the request which was canceled.
     */
    override fun fileTransferRequestCanceled(event: FileTransferRequestEvent) {
        val request = event.getRequest()
        updateFTStatus(request.getID(), FileTransferStatusChangeEvent.CANCELED)

        // Event triggered - Must execute in UiThread to Update UI information
        runOnUiThread {
            if (request == fileTransferRequest) {
                updateXferFileViewState(FileTransferStatusChangeEvent.DECLINED,
                        aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED))
                fileTransferOpSet.removeFileTransferListener(this@FileReceiveConversation)
            }
        }
    }

    /**
     * Returns the label to show on the progress bar.
     *
     * @param bytesString the bytes that have been transferred
     *
     * @return the label to show on the progress bar
     */
    override fun getProgressLabel(bytesString: Long): String {
        return aTalkApp.getResString(R.string.service_gui_RECEIVED, bytesString)
    }

    companion object {
        /**
         * Creates a `ReceiveFileConversationComponent`.
         *
         * @param cPanel the chat panel
         * @param opSet the `OperationSetFileTransfer`
         * @param request the `IncomingFileTransferRequest` associated with this component
         * @param date the received file date
         */
        // Constructor used by ChatFragment to start handle ReceiveFileTransferRequest
        fun newInstance(cPanel: ChatFragment, sendTo: String?,
                opSet: OperationSetFileTransfer, request: IncomingFileTransferRequest, date: Date?): FileReceiveConversation {
            val fragmentRFC = FileReceiveConversation(cPanel, FileRecord.IN)
            fragmentRFC.mSendTo = sendTo
            fragmentRFC.fileTransferOpSet = opSet
            fragmentRFC.fileTransferRequest = request
            fragmentRFC.msgUuid = request.getID()
            fragmentRFC.mDate = formatDateTime(date)
            fragmentRFC.mFHS = AndroidGUIActivator.fileHistoryService as FileHistoryServiceImpl?

            // need to enable ScFileTransferListener for FileReceiveConversation reject/cancellation.
            opSet.addFileTransferListener(fragmentRFC)
            return fragmentRFC
        }
    }
}