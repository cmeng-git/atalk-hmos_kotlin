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
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.AbstractFileTransfer
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.FileTransfer
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.jivesoftware.smackx.jingle.component.JingleSessionImpl
import org.jivesoftware.smackx.jingle.component.JingleSessionImpl.JingleSessionListener
import org.jivesoftware.smackx.jingle.element.JingleReason
import org.jivesoftware.smackx.jingle_filetransfer.listener.ProgressListener
import timber.log.Timber
import java.io.File

/**
 * The Jabber protocol extension of the `AbstractFileTransfer` to handle Jingle File Offer.
 *
 * @author Eng Chong Meng
 */
class IncomingFileTransferJingleImpl(inFileOffer: IncomingFileOfferJingleImpl, file: File?) : AbstractFileTransfer() {
    private val mId: String?
    private val mSender: Contact?
    private val mFile: File?
    private var mByteRead: Int

    // progress last update time.
    private var mLastUpdateTime = System.currentTimeMillis()

    /**
     * The Jingle incoming file offer.
     */
    private val mIfoJingle: IncomingFileOfferJingleImpl

    /**
     * User declines or cancels an incoming file transfer request during initial or during the active state.
     */
    override fun cancel() {
        try {
            onCanceled()
            mIfoJingle.declineFile()
        } catch (e: OperationFailedException) {
            Timber.e("Exception: %s", e.message)
        }
    }

    private fun onCanceled() {
        val reason = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED)
        fireStatusChangeEvent(FileTransferStatusChangeEvent.CANCELED, reason)
    }

    /**
     * Remove IFO Listener:
     * a. Own IncomingFileOfferJingleImpl#declineFile(); nothing returns from remote
     * b. onSessionTerminated() received from remote; user cancels prior to accept or while in active transfer
     */
    fun removeIfoListener() {
        // Timber.d("Remove Ifo Listener");
        mIfoJingle.getController().removeProgressListener(outFileProgressListener)
        JingleSessionImpl.removeJingleSessionListener(jingleSessionListener)
    }

    /**
     * Returns the number of bytes already received from the recipient.
     *
     * @return the number of bytes already received from the recipient
     */
    override fun getTransferredBytes(): Long {
        return mByteRead.toLong()
    }

    /**
     * The direction is incoming.
     *
     * @return IN
     */
    override fun getDirection(): Int {
        return FileTransfer.IN
    }

    /**
     * Returns the sender of the file.
     *
     * @return the sender of the file
     */
    override fun getContact(): Contact? {
        return mSender
    }

    /**
     * Returns the identifier of this file transfer.
     *
     * @return the identifier of this file transfer
     */
    override fun getID(): String? {
        return mId
    }

    /**
     * Returns the local file that is being transferred.
     *
     * @return the file
     */
    override fun getLocalFile(): File? {
        return mFile
    }

    private var outFileProgressListener = object : ProgressListener {
        override fun onStarted() {
            fireStatusChangeEvent(FileTransferStatusChangeEvent.IN_PROGRESS, "Byte sending started")
        }

        /**
         * OperationSetFileTransferJabberImp#FileTransferProgressThread is not enabled for JingleFile
         * Transfer; so fireProgressChangeEvent to update display incoming file progressBar in UI
         *
         * rwBytes progressive byte count for byte-stream sent/received
         */
        override fun progress(rwBytes: Int) {
            mByteRead = rwBytes
            val cTime = System.currentTimeMillis()
            if (cTime - mLastUpdateTime > UPDATE_INTERVAL) {
                mLastUpdateTime = cTime
                fireProgressChangeEvent(cTime, rwBytes.toLong())
            }
        }

        override fun onFinished() {
            fireStatusChangeEvent(FileTransferStatusChangeEvent.COMPLETED, "File received completed")
        }

        override fun onError(reason: JingleReason) {
            jingleSessionListener.onSessionTerminated(reason)
        }
    }
    var jingleSessionListener = object : JingleSessionListener {
        override fun sessionStateUpdated(oldState: JingleSessionImpl.SessionState, newState: JingleSessionImpl.SessionState) {
            val sessionState = newState.toString()
            Timber.d("Jingle session state: %s => %s", oldState, newState)
            when (newState) {
                JingleSessionImpl.SessionState.fresh -> fireStatusChangeEvent(FileTransferStatusChangeEvent.PREPARING, sessionState)
                JingleSessionImpl.SessionState.pending ->                     // Currently not use in FileReceiveConversation
                    fireStatusChangeEvent(FileTransferStatusChangeEvent.WAITING, sessionState)
                JingleSessionImpl.SessionState.active -> fireStatusChangeEvent(FileTransferStatusChangeEvent.IN_PROGRESS, sessionState)
                JingleSessionImpl.SessionState.cancelled -> fireStatusChangeEvent(FileTransferStatusChangeEvent.CANCELED, sessionState)
                JingleSessionImpl.SessionState.ended -> {}
            }
        }

        override fun onSessionAccepted() {
            // For sender only, nothing to do here for jingle responder;
            // both accept and decline actions are handled in IncomingFileOfferJingleImpl
        }

        override fun onSessionTerminated(reason: JingleReason) {
            fireStatusChangeEvent(reason)
            removeIfoListener()
        }
    }

    /**
     * Creates an `IncomingFileTransferJingleImpl`.
     *
     * inFileOffer the Jingle incoming file offer
     * file the file
     */
    init {
        mId = inFileOffer.getID()
        mSender = inFileOffer.getSender()
        mFile = file
        mIfoJingle = inFileOffer
        mByteRead = 0
        mIfoJingle.getController().addProgressListener(outFileProgressListener)
        JingleSessionImpl.addJingleSessionListener(jingleSessionListener)
    }

    companion object {
        // progress update event is triggered every UPDATE_INTERVAL (ms).
        private const val UPDATE_INTERVAL = 10
    }
}