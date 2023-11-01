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

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import net.java.sip.communicator.service.filehistory.FileRecord
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent
import net.java.sip.communicator.util.GuiUtils.formatDateTime
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.ChatFragment
import org.atalk.hmos.gui.chat.ChatFragment.MessageViewHolder
import org.atalk.hmos.gui.chat.ChatMessage

/**
 * The component used to show a file transfer history record in the chat window.
 *
 * @author Eng Chong Meng
 */
class FileHistoryConversation private constructor(cPanel: ChatFragment, dir: String) : FileTransferConversation(cPanel, dir) {
    private var fileRecord: FileRecord? = null
    private var chatMessage: ChatMessage? = null

    fun fileHistoryConversationForm(inflater: LayoutInflater?, msgViewHolder: MessageViewHolder,
            container: ViewGroup?, init: Boolean): View? {
        val convertView = inflateViewForFileTransfer(inflater!!, msgViewHolder, container, init)
        // Assume history file transfer is completed with all button hidden
        updateXferFileViewState(FileTransferStatusChangeEvent.COMPLETED, null)
        if (fileRecord == null) {
            if (chatMessage != null) {
                val date = formatDateTime(chatMessage!!.date)
                messageViewHolder.timeView.text = date
                messageViewHolder.fileStatus.setText(R.string.xFile_FILE_TRANSFER_CANCELED)
            }
            return convertView
        }
        val entityJid = fileRecord!!.getJidAddress()
        val dir = fileRecord!!.direction
        val filePath = fileRecord!!.file
        var status = fileRecord!!.status
        var bgAlert = FileRecord.STATUS_COMPLETED != status
        if (!bgAlert && !filePath.exists()) {
            bgAlert = true
            status = FileRecord.FILE_NOT_FOUND
        }
        updateFileViewInfo(filePath, true)
        mEncryption = fileRecord!!.encType
        setEncState(mEncryption)
        val date = formatDateTime(fileRecord!!.date)
        messageViewHolder.timeView.text = date
        val statusMessage = getStatusMessage(entityJid, dir, status)
        messageViewHolder.fileStatus.text = statusMessage
        if (bgAlert) {
            messageViewHolder.fileStatus.setTextColor(Color.RED)
        }
        return convertView
    }

    /**
     * Generate the correct display message based on fileTransfer status and direction
     *
     * @param entityJid file transfer initiator
     * @param dir file send or received
     * @param status file transfer status
     * @return the status message to display
     */
    fun getStatusMessage(entityJid: String?, dir: String, status: Int): String {
        var statusMsg = ""
        val statusText = FileRecord.statusMap[status]
        if (FileRecord.IN == dir) {
            statusMsg = when (status) {
                FileRecord.STATUS_COMPLETED -> aTalkApp.getResString(R.string.xFile_FILE_RECEIVE_COMPLETED, entityJid)
                FileRecord.STATUS_FAILED -> aTalkApp.getResString(R.string.xFile_FILE_RECEIVE_FAILED, entityJid)
                FileRecord.STATUS_CANCELED -> aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED)
                FileRecord.STATUS_DECLINED -> aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_DECLINED)
                FileRecord.STATUS_WAITING, FileRecord.STATUS_PREPARING, FileRecord.STATUS_IN_PROGRESS -> aTalkApp.getResString(R.string.service_gui_FILE_TRANSFER_ACTIVE, statusText)
                FileRecord.FILE_NOT_FOUND -> aTalkApp.getResString(R.string.service_gui_FILE_DOES_NOT_EXIST)
                else -> aTalkApp.getResString(R.string.service_gui_FILE_TRANSFER_ACTIVE, statusText)
            }
        } else {
            when (status) {
                FileRecord.STATUS_COMPLETED -> statusMsg = aTalkApp.getResString(R.string.xFile_FILE_SEND_COMPLETED, entityJid)
                FileRecord.STATUS_FAILED -> statusMsg = aTalkApp.getResString(R.string.xFile_FILE_UNABLE_TO_SEND, entityJid)
                FileRecord.STATUS_CANCELED -> statusMsg = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED)
                FileRecord.STATUS_DECLINED -> statusMsg = aTalkApp.getResString(R.string.xFile_FILE_SEND_DECLINED, entityJid)
                FileRecord.STATUS_WAITING, FileRecord.STATUS_PREPARING, FileRecord.STATUS_IN_PROGRESS -> statusMsg = aTalkApp.getResString(R.string.service_gui_FILE_TRANSFER_ACTIVE, statusText)
                FileRecord.FILE_NOT_FOUND -> statusMsg = aTalkApp.getResString(R.string.service_gui_FILE_DOES_NOT_EXIST)
            }
        }
        return statusMsg
    }

    /**
     * We don't have progress label in history.
     *
     * @return empty string
     */
    override fun getProgressLabel(bytesString: Long): String {
        return ""
    }

    override fun updateView(status: Int, reason: String?) {
        // No view update process is called for file history
    }

    companion object {
        fun newInstance(cPanel: ChatFragment, fileRecord: FileRecord, msg: ChatMessage?): FileHistoryConversation {
            val fragmentFHC = FileHistoryConversation(cPanel, fileRecord.direction)
            fragmentFHC.fileRecord = fileRecord
            fragmentFHC.chatMessage = msg
            return fragmentFHC
        }
    }
}