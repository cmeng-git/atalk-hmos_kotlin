/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.impl.protocol.jabber.OperationSetFileTransferJabberImpl.FileTransferProgressThread
import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.event.FileTransferCreatedEvent
import net.java.sip.communicator.service.protocol.event.FileTransferRequestEvent
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.SmackException.*
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smackx.bob.BoBManager
import org.jivesoftware.smackx.bob.ContentId
import org.jivesoftware.smackx.filetransfer.FileTransferRequest
import org.jivesoftware.smackx.filetransfer.IncomingFileTransfer
import org.jxmpp.jid.Jid
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors

/**
 * Jabber implementation of the incoming file transfer request
 *
 * @author Nicolas Riegel
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class IncomingFileTransferRequestJabberImpl(private val jabberProvider: ProtocolProviderServiceJabberImpl,
        private val fileTransferOpSet: OperationSetFileTransferJabberImpl,
        private val fileTransferRequest: FileTransferRequest) : IncomingFileTransferRequest {
    /**
     * The Jabber file transfer request.
     */
    private val id: String
    private val remoteJid = fileTransferRequest.requestor
    private var mIncomingFileTransfer: IncomingFileTransfer? = null
    private var mFileTransfer: IncomingFileTransferJabberImpl? = null
    private var mFile: File? = null
    private var sender: Contact?
    private var thumbnail: ByteArray? = null

    /*
     * Transfer file encryption type.
     */
    private var mEncryption = IMessage.ENCRYPTION_NONE

    /**
     * Creates an `IncomingFileTransferRequestJabberImpl` based on the given
     * `fileTransferRequest`, coming from the Jabber protocol.
     *
     * pps the protocol provider
     * fileTransferOpSet file transfer operation set
     * fileTransferRequest the request coming from the Jabber protocol
     */
    init {

        // Legacy ByteStream transfer supports only ENCRYPTION_NONE
        val opSetPersPresence = jabberProvider.getOperationSet(OperationSetPersistentPresence::class.java) as OperationSetPersistentPresenceJabberImpl?
        sender = opSetPersPresence!!.findContactByJid(remoteJid)
        if (sender == null) {
            var privateContactRoom: ChatRoom? = null
            val mucOpSet = jabberProvider.getOperationSet(OperationSetMultiUserChat::class.java) as OperationSetMultiUserChatJabberImpl?
            if (mucOpSet != null) privateContactRoom = mucOpSet.getChatRoom(remoteJid.asBareJid())
            if (privateContactRoom != null) {
                sender = opSetPersPresence.createVolatileContact(remoteJid, true)
                privateContactRoom.updatePrivateContactPresenceStatus(sender)
            } else {
                sender = opSetPersPresence.createVolatileContact(remoteJid)
            }
        }
        id = System.currentTimeMillis().toString() + hashCode()
    }

    override fun onPrepare(file: File?): FileTransfer? {
        mFile = file
        mIncomingFileTransfer = fileTransferRequest.accept()
        mFileTransfer = IncomingFileTransferJabberImpl(id, sender, file, mIncomingFileTransfer)
        return mFileTransfer
    }

    /**
     * Returns the `Contact` making this request.
     *
     * @return the `Contact` making this request
     */
    override fun getSender(): Contact? {
        return sender
    }

    /**
     * Returns the description of the file corresponding to this request.
     *
     * @return the description of the file corresponding to this request
     */
    override fun getFileDescription(): String? {
        return fileTransferRequest.description
    }

    override fun getMimeType(): String? {
        return fileTransferRequest.mimeType
    }

    /**
     * Returns the name of the file corresponding to this request.
     *
     * @return the name of the file corresponding to this request
     */
    override fun getFileName(): String? {
        return fileTransferRequest.fileName
    }

    /**
     * Returns the size of the file corresponding to this request.
     *
     * @return the size of the file corresponding to this request
     */
    override fun getFileSize(): Long {
        return fileTransferRequest.fileSize
    }

    /**
     * The unique id.
     *
     * @return the id.
     */
    override fun getID(): String {
        return id
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
     * Returns the thumbnail contained in this request.
     *
     * @return the thumbnail contained in this request
     */
    override fun getThumbnail(): ByteArray? {
        return thumbnail
    }

    /**
     * Accepts the file and starts the transfer.
     */
    override fun acceptFile() {
        try {
            val event = FileTransferCreatedEvent(mFileTransfer, Date())
            fileTransferOpSet.fireFileTransferCreated(event)
            mIncomingFileTransfer!!.receiveFile(mFile)
            FileTransferProgressThread(mIncomingFileTransfer!!, mFileTransfer!!, getFileSize()).start()
        } catch (e: IOException) {
            Timber.e(e, "Receiving file failed.")
        } catch (e: SmackException) {
            Timber.e(e, "Receiving file failed.")
        }
    }

    /**
     * Declines the incoming file transfer request.
     */
    @Throws(OperationFailedException::class)
    override fun declineFile() {
        try {
            fileTransferRequest.reject()
        } catch (e: NotConnectedException) {
            throw OperationFailedException("Could not reject file transfer",
                    OperationFailedException.GENERAL_ERROR, e)
        } catch (e: InterruptedException) {
            throw OperationFailedException("Could not reject file transfer",
                    OperationFailedException.GENERAL_ERROR, e)
        }
        fileTransferOpSet.fireFileTransferRequestRejected(
                FileTransferRequestEvent(fileTransferOpSet, this, Date()))
    }

    /**
     * Request the thumbnail from the peer, allow extended smack timeout on thumbnail request.
     * Then fire the incoming transfer request event to start the actual incoming file transfer.
     *
     * cid the thumbnail content-Id
     */
    fun fetchThumbnailAndNotify(cid: ContentId?) {
        val connection = jabberProvider.connection
        val bobManager = BoBManager.getInstanceFor(connection)
        thumbnailCollector.submit {
            try {
                connection!!.replyTimeout = ProtocolProviderServiceJabberImpl.SMACK_REPLY_EXTENDED_TIMEOUT_10
                thumbnail = bobManager.requestBoB(remoteJid, cid).content
            } catch (e: NotLoggedInException) {
                Timber.e("Error in requesting for thumbnail: %s", e.message)
            } catch (e: NoResponseException) {
                Timber.e("Error in requesting for thumbnail: %s", e.message)
            } catch (e: XMPPErrorException) {
                Timber.e("Error in requesting for thumbnail: %s", e.message)
            } catch (e: NotConnectedException) {
                Timber.e("Error in requesting for thumbnail: %s", e.message)
            } catch (e: InterruptedException) {
                Timber.e("Error in requesting for thumbnail: %s", e.message)
            } finally {
                connection!!.replyTimeout = ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT
                // Notify the global listener that a request has arrived.
                fileTransferOpSet.fireFileTransferRequest(this@IncomingFileTransferRequestJabberImpl)
            }
        }
    }

    companion object {
        /**
         * Thread to fetch thumbnails in the background, one at a time
         */
        private val thumbnailCollector = Executors.newSingleThreadExecutor()
    }
}