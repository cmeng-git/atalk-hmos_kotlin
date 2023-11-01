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
import net.java.sip.communicator.service.protocol.event.FileTransferCreatedEvent
import net.java.sip.communicator.service.protocol.event.FileTransferRequestEvent
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.SmackException.NoResponseException
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smackx.jet.component.JetSecurityImpl
import org.jivesoftware.smackx.jingle.component.JingleContentImpl
import org.jivesoftware.smackx.jingle_filetransfer.component.JingleFile
import org.jivesoftware.smackx.jingle_filetransfer.controller.IncomingFileOfferController
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Jabber implementation of the jingle incoming file offer
 *
 * @author Eng Chong Meng
 */
class IncomingFileOfferJingleImpl(pps: ProtocolProviderServiceJabberImpl,
        private val fileTransferOpSet: OperationSetFileTransferJabberImpl, offer: IncomingFileOfferController) : IncomingFileTransferRequest {
    private val mOffer: IncomingFileOfferController
    private val mJingleFile: JingleFile
    private var mFileTransfer: IncomingFileTransferJingleImpl? = null
    private var mFile: File? = null
    private val mConnection: XMPPConnection?
    private var mSender: Contact?
    private val mId: String

    /*
     * Transfer file encryption type based on incoming encryption detection.
     */
    protected var mEncryption: Int

    /**
     * Creates an `IncomingFileOfferJingleImpl` based on the given
     * `fileTransferRequest`, coming from the Jabber protocol.
     *
     * pps the protocol provider
     * fileTransferOpSet file transfer operation set
     * fileTransferRequest the request coming from the Jabber protocol
     */
    init {
        mConnection = pps.connection
        mOffer = offer
        mJingleFile = mOffer.metadata
        val hashElement = mJingleFile.hash
        mId = if (hashElement != null) hashElement.hashB64 else System.currentTimeMillis().toString() + hashCode()

        // Determine the incoming content encryption type.
        mEncryption = IMessage.ENCRYPTION_NONE
        val contentImpls = mOffer.jingleSession.contentImpls.values
        for (jingleContent in contentImpls) {
            if (jingleContent.security is JetSecurityImpl) {
                mEncryption = IMessage.ENCRYPTION_OMEMO
                break
            }
        }
        val remoteJid = mOffer.jingleSession.remote.asBareJid()
        val opSetPersPresence = pps.getOperationSet(OperationSetPersistentPresence::class.java) as OperationSetPersistentPresenceJabberImpl?
        mSender = opSetPersPresence!!.findContactByJid(remoteJid)
        if (mSender == null) {
            var privateContactRoom: ChatRoom? = null
            val mucOpSet = pps.getOperationSet(OperationSetMultiUserChat::class.java) as OperationSetMultiUserChatJabberImpl?
            if (mucOpSet != null) privateContactRoom = mucOpSet.getChatRoom(remoteJid)
            if (privateContactRoom != null) {
                mSender = opSetPersPresence.createVolatileContact(remoteJid, true)
                privateContactRoom.updatePrivateContactPresenceStatus(mSender)
            } else {
                // just create a volatile contact for new sender
                mSender = opSetPersPresence.createVolatileContact(remoteJid)
            }
        }
    }

    /**
     * JingleSessionImpl.addJingleSessionListener(this);
     */
    override fun onPrepare(file: File?): FileTransfer? {
        mFile = file
        mFileTransfer = IncomingFileTransferJingleImpl(this, file)
        return mFileTransfer
    }

    fun getController(): IncomingFileOfferController {
        return mOffer
    }

    /**
     * Returns the `Contact` making this request.
     *
     * @return the `Contact` making this request
     */
    override fun getSender(): Contact? {
        return mSender
    }

    /**
     * Returns the description of the file corresponding to this request.
     *
     * @return the description of the file corresponding to this request
     */
    override fun getFileDescription(): String? {
        return mJingleFile.description
    }

    override fun getMimeType(): String? {
        return mJingleFile.mediaType
    }

    /**
     * Returns the name of the file corresponding to this request.
     *
     * @return the name of the file corresponding to this request
     */
    override fun getFileName(): String? {
        return mJingleFile.name
    }

    /**
     * Returns the size of the file corresponding to this request.
     *
     * @return the size of the file corresponding to this request
     */
    override fun getFileSize(): Long {
        return mJingleFile.size.toLong()
    }

    /**
     * The file transfer unique id.
     *
     * @return the id.
     */
    override fun getID(): String {
        return mId
    }

    /**
     * Return the encryption of the incoming file corresponding to this FileTransfer.
     *
     * @return the encryption of the file corresponding to this request
     */
    override fun getEncryptionType(): Int {
        return mEncryption
    }

    /**
     * Returns the thumbnail contained in this request. Jingle file transfer does not support thumbnail.
     *
     * @return the thumbnail contained in this request
     */
    override fun getThumbnail(): ByteArray? {
        return null
    }

    /**
     * Accepts the file and starts the transfer.
     *
     * Note: If user cancels while in protocol negotiation; the accept() will return an error:
     * XMPPError: item-not-found - cancel
     */
    override fun acceptFile() {
        try {
            val event = FileTransferCreatedEvent(mFileTransfer, Date())
            fileTransferOpSet.fireFileTransferCreated(event)
            mOffer.accept(mConnection, mFile)
        } catch (e: IOException) {
            aTalkApp.showToastMessage(R.string.xFile_FILE_RECEIVE_FAILED, e.message)
            Timber.e("Receiving file failed; %s", e.message)
        } catch (e: SmackException) {
            aTalkApp.showToastMessage(R.string.xFile_FILE_RECEIVE_FAILED, e.message)
            Timber.e("Receiving file failed; %s", e.message)
        } catch (e: InterruptedException) {
            aTalkApp.showToastMessage(R.string.xFile_FILE_RECEIVE_FAILED, e.message)
            Timber.e("Receiving file failed; %s", e.message)
        } catch (e: XMPPErrorException) {
            aTalkApp.showToastMessage(R.string.xFile_FILE_RECEIVE_FAILED, e.message)
            Timber.e("Receiving file failed; %s", e.message)
        }
    }

    /**
     * Declines the incoming file offer.
     */
    @Throws(OperationFailedException::class)
    override fun declineFile() {
        try {
            mOffer.cancel(mConnection)
            mFileTransfer!!.removeIfoListener()
        } catch (e: NotConnectedException) {
            throw OperationFailedException("Could not decline the file offer", OperationFailedException.GENERAL_ERROR, e)
        } catch (e: InterruptedException) {
            throw OperationFailedException("Could not decline the file offer", OperationFailedException.GENERAL_ERROR, e)
        } catch (e: XMPPErrorException) {
            throw OperationFailedException("Could not decline the file offer", OperationFailedException.GENERAL_ERROR, e)
        } catch (e: NoResponseException) {
            throw OperationFailedException("Could not decline the file offer", OperationFailedException.GENERAL_ERROR, e)
        }
        fileTransferOpSet.fireFileTransferRequestRejected(
                FileTransferRequestEvent(fileTransferOpSet, this, Date()))
    }
}