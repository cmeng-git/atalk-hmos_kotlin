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

import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent
import org.jivesoftware.smack.SmackException.NoResponseException
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smackx.jingle.component.JingleSessionImpl
import org.jivesoftware.smackx.jingle.component.JingleSessionImpl.JingleSessionListener
import org.jivesoftware.smackx.jingle.element.JingleReason
import org.jivesoftware.smackx.jingle_filetransfer.controller.OutgoingFileOfferController
import org.jivesoftware.smackx.jingle_filetransfer.listener.ProgressListener
import timber.log.Timber
import java.io.File

/**
 * Jabber implementation of the jingle incoming file offer
 *
 * @author Eng Chong Meng
 */
class OutgoingFileOfferJingleImpl(private val mContact: Contact, private val mFile: File, private val msgUuid: String,
        /**
         * The Jingle outgoing file offer.
         */
        private val mOfoJingle: OutgoingFileOfferController, private val mConnection: XMPPConnection) : AbstractFileTransfer() {
    private var byteWrite = 0

    /**
     * Cancel the file transfer.
     */
    override fun cancel() {
        try {
            mOfoJingle.cancel(mConnection)
        } catch (e: NotConnectedException) {
            Timber.e("File send cancel exception: %s", e.message)
        } catch (e: InterruptedException) {
            Timber.e("File send cancel exception: %s", e.message)
        } catch (e: XMPPErrorException) {
            Timber.e("File send cancel exception: %s", e.message)
        } catch (e: NoResponseException) {
            Timber.e("File send cancel exception: %s", e.message)
        }

        // Must perform the following even if cancel failed due to remote: XMPPError: item-not-found - cancel
        removeOfoListener()
        val oldState = mOfoJingle.jingleSession.sessionState
        fireStatusChangeEvent(FileTransferStatusChangeEvent.CANCELED, oldState.toString())
    }

    /**
     * Remove OFO Listener:
     * a. When sender cancel file transfer (FileTransferConversation); nothing returns from remote.
     * b. onSessionTerminated() received from remote (uer declines or cancels during active transfer)
     */
    private fun removeOfoListener() {
        // Timber.d("Remove Ofo Listener");
        mOfoJingle.removeProgressListener(inFileProgressListener)
        JingleSessionImpl.removeJingleSessionListener(jingleSessionListener)
    }

    /**
     * Returns the number of bytes already sent to the recipient.
     *
     * @return the number of bytes already sent to the recipient.
     */
    override fun getTransferredBytes(): Long {
        return byteWrite.toLong()
    }

    /**
     * The direction is outgoing.
     *
     * @return OUT.
     */
    override fun getDirection(): Int {
        return FileTransfer.OUT
    }

    /**
     * Returns the local file that is being transferred or to which we transfer.
     *
     * @return the file
     */
    override fun getLocalFile(): File {
        return mFile
    }

    /**
     * The contact we are sending the file.
     *
     * @return the receiver.
     */
    override fun getContact(): Contact {
        return mContact
    }

    /**
     * The unique id that uniquely identity the record and in DB.
     *
     * @return the id.
     */
    override fun getID(): String {
        return msgUuid
    }

    var inFileProgressListener = object : ProgressListener {
        override fun onStarted() {
            fireStatusChangeEvent(FileTransferStatusChangeEvent.IN_PROGRESS, "Byte sending started")
        }

        override fun progress(rwBytes: Int) {
            byteWrite = rwBytes
            fireProgressChangeEvent(System.currentTimeMillis(), rwBytes.toLong())
        }

        override fun onFinished() {
            fireStatusChangeEvent(FileTransferStatusChangeEvent.COMPLETED, "Byte sent completed")
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
                JingleSessionImpl.SessionState.pending -> fireStatusChangeEvent(FileTransferStatusChangeEvent.WAITING, sessionState)
                JingleSessionImpl.SessionState.active -> {}
                JingleSessionImpl.SessionState.cancelled -> fireStatusChangeEvent(FileTransferStatusChangeEvent.DECLINED, sessionState)
                JingleSessionImpl.SessionState.ended ->                     // This is triggered only on session terminate; while onFinished() is triggered
                    // upon end of stream sending. hence superseded the formal event.
                    // So "ended" event is not triggered, rely onFinished() instead.
                    fireStatusChangeEvent(FileTransferStatusChangeEvent.COMPLETED, sessionState)
            }
        }

        fun onSessionInit() {
            // Waiting for remote to accept
            fireStatusChangeEvent(FileTransferStatusChangeEvent.WAITING, "In waiting")
        }

        override fun onSessionAccepted() {
            fireStatusChangeEvent(FileTransferStatusChangeEvent.IN_PROGRESS, "Session accepted")
        }

        override fun onSessionTerminated(reason: JingleReason) {
            if (JingleReason.Reason.security_error == reason.asEnum()) {
                mSecurityErrorTimer[mContact] = defaultErrorTimer
            }
            fireStatusChangeEvent(reason)
            removeOfoListener()
        }
    }

    /**
     * Creates an `OutgoingFileTransferJabberImpl` by specifying the `receiver`
     * contact, the `file` , the `jabberTransfer`, that would be used to send the file
     * through Jabber and the `protocolProvider`.
     *
     * @param recipient the destination contact
     * @param file the file to send
     * @param jabberTransfer the Jabber transfer object, containing all transfer information
     * @param protocolProvider the parent protocol provider
     * @param mUuid the id that uniquely identifies this file transfer and saved DB record
     */
    init {
        mOfoJingle.addProgressListener(inFileProgressListener)
        JingleSessionImpl.addJingleSessionListener(jingleSessionListener)
    }

    companion object {
        /**
         * Default number of fallback to use HttpFileUpload if previously has securityError
         */
        private const val defaultErrorTimer = 10

        /**
         * Fallback to use HttpFileUpload file transfer if previously has securityError i.e. not zero
         */
        private val mSecurityErrorTimer = HashMap<Contact, Int>()

        /**
         * Avoid use of Jet for file transfer if it is still within the securityErrorTimber count.
         *
         * @return true if the timer is not zero.
         */
        fun hasSecurityError(contact: Contact): Boolean {
            var errorTimer = mSecurityErrorTimer[contact]
            if (errorTimer != null && --errorTimer > 0) {
                mSecurityErrorTimer[contact] = errorTimer
                return true
            }
            return false
        }
    }
}