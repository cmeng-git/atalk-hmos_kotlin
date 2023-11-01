/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.event.*
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum
import net.java.sip.communicator.util.ConfigurationUtils.isAutoAcceptFile
import net.java.sip.communicator.util.ConfigurationUtils.isSendThumbnail
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.SmackException.NoResponseException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPConnectionRegistry
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smack.packet.StanzaError
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smackx.filetransfer.*
import org.jivesoftware.smackx.filetransfer.FileTransfer
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer.NegotiationProgress
import org.jivesoftware.smackx.jingle_filetransfer.JingleFileTransferManager
import org.jivesoftware.smackx.jingle_filetransfer.controller.IncomingFileOfferController
import org.jivesoftware.smackx.jingle_filetransfer.listener.IncomingFileOfferListener
import org.jivesoftware.smackx.si.packet.StreamInitiation
import org.jivesoftware.smackx.thumbnail.ThumbnailFile
import org.jxmpp.jid.EntityFullJid
import timber.log.Timber
import java.io.File
import java.io.OutputStream
import java.lang.reflect.Method
import java.util.*

/**
 * The Jabber implementation of the `OperationSetFileTransfer` interface.
 *
 * @author Gregory Bande
 * @author Nicolas Riegel
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class OperationSetFileTransferJabberImpl(provider: ProtocolProviderServiceJabberImpl) : OperationSetFileTransfer {
    /**
     * The provider that created us.
     */
    private val mPPS: ProtocolProviderServiceJabberImpl?

    /**
     * An active instance of the opSetPersPresence operation set.
     */
    private var opSetPersPresence: OperationSetPersistentPresenceJabberImpl? = null
    private var mOGFileTransfer: OutgoingFileTransferJabberImpl? = null

    /**
     * The Smack file transfer request Listener for legacy IBB and Sock5.
     */
    private var ftrListener: FileTransferRequestListener? = null

    /**
     * The Smack Jingle file transfer request Listener for legacy IBB and SOCK5.
     */
    private var ifoListener: IncomingFileOfferListener? = null

    /**
     * Flag indicates and ByteStream file transfer exception has occurred.
     */
    private var byteStreamError = false

    /**
     * A list of listeners registered for file transfer events.
     */
    private val fileTransferListeners = Vector<ScFileTransferListener?>()

    /**
     * Sends a file transfer request to the given `contact`.
     *
     * toContact the contact that should receive the file
     * file file to send
     * msgUuid the id that uniquely identifies this file transfer and saved DB record
     *
     * @return the transfer object
     */
    @Throws(IllegalStateException::class, IllegalArgumentException::class, OperationNotSupportedException::class)
    override fun sendFile(toContact: Contact, file: File, msgUuid: String): net.java.sip.communicator.service.protocol.FileTransfer {
        assertConnected()
        require(file.length() <= getMaximumFileLength()) { aTalkApp.getResString(R.string.service_gui_FILE_TOO_BIG, mPPS!!.ourJID) }

        // null if the contact is offline, or file transfer is not supported by this contact;
        // Then throws OperationNotSupportedException for caller to try alternative method
        val mContactJid = getFullJid(toContact,
                StreamInitiation.NAMESPACE, StreamInitiation.NAMESPACE + "/profile/file-transfer")
                ?: throw OperationNotSupportedException(aTalkApp.getResString(R.string.service_gui_FILE_TRANSFER_NOT_SUPPORTED))

        /* Must init to the correct ftManager at time of sending file with current mPPS; Otherwise
         * the ftManager is the last registered PPS and may not be correct in multiple user accounts env.
         */
        val ftManager = FileTransferManager.getInstanceFor(mPPS!!.connection)
        val transfer = ftManager.createOutgoingFileTransfer(mContactJid)
        mOGFileTransfer = OutgoingFileTransferJabberImpl(toContact, file, transfer, mPPS, msgUuid)

        // Notify all interested listeners that a file transfer has been created.
        val event = FileTransferCreatedEvent(mOGFileTransfer, Date())
        fireFileTransferCreated(event)

        // cmeng: start file transferring with callback on status changes;
        // Start FileTransferNegotiator to support both sock5 and IBB; fallback to IBB if sock5 failed on retry
        FileTransferNegotiator.IBB_ONLY = byteStreamError
        try {
            // Start smack handle everything and start status and progress thread.
            transfer.setCallback(negotiationProgress)
            transfer.sendFile(file, "Sending file")
            FileTransferProgressThread(transfer, mOGFileTransfer!!).start()
        } catch (e: SmackException) {
            Timber.e("Failed to send file: %s", e.message)
            throw OperationNotSupportedException(
                    aTalkApp.getResString(R.string.xFile_FILE_UNABLE_TO_SEND, mContactJid))
        }
        return mOGFileTransfer!!
    }

    /**
     * A callback class to retrieve the status of an outgoing transfer negotiation process
     * for legacy IBB/SOCK5 Bytestream file transfer.
     */
    private val negotiationProgress = object : NegotiationProgress {
        /**
         * Called when the status changes.
         *
         * oldStatus the previous status of the file transfer.
         * newStatus the new status of the file transfer.
         */
        override fun statusUpdated(oldStatus: FileTransfer.Status, newStatus: FileTransfer.Status) {
            // Timber.d("NegotiationProgress status change: %s => %s", oldStatus, newStatus);
            when (newStatus) {
                FileTransfer.Status.complete, FileTransfer.Status.cancelled, FileTransfer.Status.refused -> {
                    byteStreamError = false
                    mOGFileTransfer!!.removeThumbnailHandler()
                }
                FileTransfer.Status.error -> {
                    mOGFileTransfer!!.removeThumbnailHandler()
                    if (FileTransfer.Status.negotiating_stream == oldStatus) {
                        byteStreamError = !FileTransferNegotiator.IBB_ONLY
                    }
                }
                else -> {}
            }
            val reason = newStatus.toString()
            // Timber.d("Error message: %s", reason);
            mOGFileTransfer!!.fireStatusChangeEvent(parseJabberStatus(newStatus), reason)
        }

        /**
         * Once the negotiation process is completed the output stream can be retrieved.
         * Valid for SOCK5ByteStream protocol only.
         *
         * stream the established stream which can be used to transfer the file to the remote entity
         */
        override fun outputStreamEstablished(stream: OutputStream) {
            // Timber.d("NegotiationProgress outputStreamEstablished: %s", stream);
            byteStreamError = false
        }

        /**
         * Called when an exception occurs during the negotiation progress.
         * Set byteStreamError flag to true only if IBB_ONLY is not set.
         *
         * ex the exception that occurred.
         */
        override fun errorEstablishingStream(ex: Exception) {
            var errMsg = ex.message
            if (errMsg != null && ex is NoResponseException) {
                errMsg = errMsg.substring(0, errMsg.indexOf(". StanzaCollector"))
            }
            mOGFileTransfer!!.fireStatusChangeEvent(FileTransferStatusChangeEvent.CANCELED, errMsg!!)
        }
    }

    /**
     * Constructor
     *
     * provider is the provider that created us
     */
    init {
        mPPS = provider
        provider.addRegistrationStateChangeListener(RegistrationStateListener())
    }

    /**
     * Find the EntityFullJid of an ONLINE contact with the highest priority if more than one found,
     * and supports the given file transfer features; if we have equals priorities choose the one more available.
     *
     * contact The Contact
     * features The desired features' namespace
     *
     * @return the filtered contact EntityFullJid if found, or null otherwise
     */
    fun getFullJid(contact: Contact?, vararg features: String?): EntityFullJid? {
        var contactJid = contact!!.contactJid // bareJid from the roster unless is volatile contact
        val mucOpSet = mPPS!!.getOperationSet(OperationSetMultiUserChat::class.java)
        if (mucOpSet == null || !mucOpSet.isPrivateMessagingContact(contactJid)) {
            val presences = Roster.getInstanceFor(mPPS.connection).getPresences(contactJid!!.asBareJid())
            var bestPriority = -128
            var pStatus: PresenceStatus? = null
            for (presence in presences) {
                // Proceed only for presence with Type.available
                if (presence.isAvailable && mPPS.isFeatureListSupported(presence.from, *features)) {
                    val priority = presence.priority // return priority range: -128~127
                    if (priority > bestPriority) {
                        bestPriority = priority
                        contactJid = presence.from
                        pStatus = OperationSetPersistentPresenceJabberImpl.jabberStatusToPresenceStatus(presence, mPPS)
                    } else if (priority == bestPriority && pStatus != null) {
                        val tmpStatus = OperationSetPersistentPresenceJabberImpl.jabberStatusToPresenceStatus(presence, mPPS)
                        if (tmpStatus > pStatus) {
                            contactJid = presence.from
                            pStatus = tmpStatus
                        }
                    }
                }
            }
        }
        // Force to null if contact is offline i.e does not resolved to an EntityFullJid.
        return if (contactJid is EntityFullJid) contactJid else null
    }

    /**
     * Adds the given `ScFileTransferListener` that would listen for file transfer requests
     * created file transfers.
     *
     * listener the `ScFileTransferListener` to add
     */
    override fun addFileTransferListener(listener: ScFileTransferListener) {
        synchronized(fileTransferListeners) {
            if (!fileTransferListeners.contains(listener)) {
                fileTransferListeners.add(listener)
            }
        }
    }

    /**
     * Removes the given `ScFileTransferListener` that listens for file transfer requests and
     * created file transfers.
     *
     * listener the `ScFileTransferListener` to remove
     */
    override fun removeFileTransferListener(listener: ScFileTransferListener) {
        synchronized(fileTransferListeners) { fileTransferListeners.remove(listener) }
    }

    /**
     * Utility method throwing an exception if the stack is not properly initialized.
     *
     * @throws java.lang.IllegalStateException if the underlying stack is not registered and initialized.
     */
    @Throws(IllegalStateException::class)
    private fun assertConnected() {
        // if we are not registered but the current status is online change the current status
        checkNotNull(mPPS) { "The provider must be non-null and signed in before being able to send a file." }
        if (!mPPS.isRegistered) {
            // if we are not registered but the current status is online change the current status
            if (opSetPersPresence!!.getPresenceStatus()!!.isOnline) {
                opSetPersPresence!!.fireProviderStatusChangeEvent(opSetPersPresence!!.getPresenceStatus(),
                        mPPS.jabberStatusEnum!!.getStatus(JabberStatusEnum.OFFLINE))
            }
            throw IllegalStateException("The provider must be signed in before being able to send a file.")
        }
    }

    /**
     * Returns the maximum file length supported by the protocol in bytes. Supports up to 2GB.
     *
     * @return the file length that is supported.
     */
    override fun getMaximumFileLength(): Long {
        return MAX_FILE_LENGTH
    }

    /**
     * Our listener that will tell us when we're registered to
     */
    private inner class RegistrationStateListener : RegistrationStateChangeListener {
        /**
         * The method is called by a ProtocolProvider implementation whenever a change in the
         * registration state of the corresponding provider had occurred.
         *
         * evt ProviderStatusChangeEvent the event describing the status change.
         */
        override fun registrationStateChanged(evt: RegistrationStateChangeEvent) {
            var ftManager: FileTransferManager? = null
            var jftManager: JingleFileTransferManager? = null
            val connection = mPPS!!.connection
            if (connection != null) {
                ftManager = FileTransferManager.getInstanceFor(connection)
                jftManager = JingleFileTransferManager.getInstanceFor(connection)
            }
            if (evt.getNewState() === RegistrationState.REGISTERED) {
                opSetPersPresence = mPPS.getOperationSet(OperationSetPersistentPresence::class.java) as OperationSetPersistentPresenceJabberImpl?

                // cmeng: Registered only once - otherwise multiple triggers on single file request
                if (ftrListener == null && ftManager != null) {
                    ftrListener = FileTransferRequestListener()
                    ftManager.addFileTransferListener(ftrListener)
                }
                if ((ifoListener == null) && jftManager != null) {
                    ifoListener = JingleIFOListener()
                    jftManager.addIncomingFileOfferListener(ifoListener)
                }
            } else if (evt.getNewState() === RegistrationState.UNREGISTERING) {
                // Must unregistered ftrListener on protocolProvider UNREGISTERING to avoid any ghost listener
                // check ftManager to ensure it is still valid i..e not null
                if (ftrListener != null && ftManager != null) {
                    // Timber.w("Remove FileTransferListener: %s", ftrListener);
                    ftManager.removeFileTransferListener(ftrListener)
                    ftrListener = null
                }
                if ((ifoListener != null) && jftManager != null) {
                    jftManager.removeIncomingFileOfferListener(ifoListener)
                    ifoListener = null
                }
            }
        }
    }

    /**
     * Listener for Jingle IBB/SOCK5 ByteStream incoming file offer.
     */
    private inner class JingleIFOListener : IncomingFileOfferListener {
        /**
         * Listens for file transfer packets.
         *
         * request fileTransfer request from smack FileTransferListener
         */
        override fun onIncomingFileOffer(offer: IncomingFileOfferController) {
            // Timber.d("Received jingle incoming file offer.");

            // Create a global incoming file transfer request.
            val ifoJingle = IncomingFileOfferJingleImpl(mPPS!!, this@OperationSetFileTransferJabberImpl, offer)
            fireFileTransferRequest(ifoJingle)
        }
    }

    /**
     * Listener for Jabber legacy IBB/SOCK5 incoming file transfer requests.
     */
    private inner class FileTransferRequestListener : FileTransferListener {
        private fun getStreamInitiation(request: FileTransferRequest): StreamInitiation? {
            val gsi: Method
            return try {
                gsi = request.javaClass.getDeclaredMethod("getStreamInitiation")
                gsi.isAccessible = true
                gsi.invoke(request) as StreamInitiation
            } catch (e: Exception) {
                Timber.e("Cannot invoke getStreamInitiation: %s", e.message)
                null
            }
        }

        /**
         * Listens for incoming file transfer stanza.
         *
         * request fileTransfer request from smack FileTransferListener
         */
        override fun fileTransferRequest(request: FileTransferRequest) {
            // Timber.d("Received incoming Jabber file transfer request.");

            // Create a global incoming file transfer request.
            val incomingFileTransferRequest = IncomingFileTransferRequestJabberImpl(mPPS!!, this@OperationSetFileTransferJabberImpl, request)
            val si = getStreamInitiation(request)

            // Request thumbnail if advertised in streamInitiation stanza, no autoAccept, and the feature is enabled
            var thumbnailRequest = false
            if (si !== null && si.file is ThumbnailFile) {
                // Proceed to request for the available thumbnail if auto accept file not permitted
                val isAutoAccept = isAutoAcceptFile(request.fileSize)
                val thumbnailElement = (si.file as ThumbnailFile).thumbnail
                if (!isAutoAccept && isSendThumbnail() && thumbnailElement != null) {
                    thumbnailRequest = true
                    incomingFileTransferRequest.fetchThumbnailAndNotify(thumbnailElement.cid)
                }
            }
            // No thumbnail request, then proceed to notify the global listener that a request has arrived
            if (!thumbnailRequest) {
                fireFileTransferRequest(incomingFileTransferRequest)
            }
        }
    }

    /**
     * Delivers the specified event to all registered file transfer listeners.
     *
     * request the `IncomingFileTransferRequestJabberImpl` that we'd like delivered to all
     * registered file transfer listeners.
     */
    fun fireFileTransferRequest(request: IncomingFileTransferRequest?) {
        // Create an event associated to this global request.
        val event = FileTransferRequestEvent(this, request!!, Date())
        var listeners: Iterator<ScFileTransferListener?>
        synchronized(fileTransferListeners) { listeners = ArrayList(fileTransferListeners).iterator() }
        while (listeners.hasNext()) {
            val listener = listeners.next()
            listener!!.fileTransferRequestReceived(event)
        }
    }

    /**
     * When the remote declines an incoming file offer;
     * delivers the specified event to all registered file transfer listeners.
     *
     * event the `EventObject` that we'd like delivered to all registered file transfer listeners.
     */
    fun fireFileTransferRequestRejected(event: FileTransferRequestEvent) {
        var listeners: Iterator<ScFileTransferListener?>
        synchronized(fileTransferListeners) { listeners = ArrayList(fileTransferListeners).iterator() }
        while (listeners.hasNext()) {
            val listener = listeners.next()
            listener!!.fileTransferRequestRejected(event)
        }
    }

    /**
     * When the remote user cancels the file transfer/offer;
     * delivers the specified event to all registered file transfer listeners.
     * Note: Legacy XMPP IBB/SOCK5 protocol does reverts this info to sender.
     *
     * event the `EventObject` that we'd like delivered to all
     * registered file transfer listeners.
     */
    fun fireFileTransferRequestCanceled(event: FileTransferRequestEvent) {
        var listeners: Iterator<ScFileTransferListener?>
        synchronized(fileTransferListeners) { listeners = ArrayList(fileTransferListeners).iterator() }
        while (listeners.hasNext()) {
            val listener = listeners.next()
            listener!!.fileTransferRequestCanceled(event)
        }
    }

    /**
     * Delivers the file transfer to all registered listeners.
     *
     * event the `FileTransferEvent` that we'd like delivered to all registered file transfer listeners.
     */
    fun fireFileTransferCreated(event: FileTransferCreatedEvent?) {
        var listeners: Iterator<ScFileTransferListener?>
        synchronized(fileTransferListeners) { listeners = ArrayList(fileTransferListeners).iterator() }
        while (listeners.hasNext()) {
            val listener = listeners.next()
            listener!!.fileTransferCreated(event)
        }
    }

    /**
     * Updates file transfer progress and status while sending or receiving a file.
     */
    class FileTransferProgressThread : Thread {
        private val jabberTransfer: FileTransfer
        private val fileTransfer: AbstractFileTransfer
        private var initialFileSize: Long = 0

        constructor(jabberTransfer: FileTransfer,
                transfer: AbstractFileTransfer, initialFileSize: Long) {
            this.jabberTransfer = jabberTransfer
            fileTransfer = transfer
            this.initialFileSize = initialFileSize
        }

        constructor(jabberTransfer: FileTransfer,
                transfer: AbstractFileTransfer) {
            this.jabberTransfer = jabberTransfer
            fileTransfer = transfer
        }

        /**
         * Thread entry point.
         */
        override fun run() {
            var status: Int
            var statusReason: String? = ""
            while (true) {
                try {
                    sleep(100)
                } catch (e: InterruptedException) {
                    Timber.d("Unable to thread sleep.")
                }

                // OutgoingFileTransfer has its own callback to handle status change
                if (fileTransfer is IncomingFileTransferJabberImpl) {
                    status = parseJabberStatus(jabberTransfer.status)
                    // if (count++ % 250 == 0) {
                    //     Timber.d("FileTransferProgressThread: %s (%s) <= %s",
                    //             jabberTransfer.getStatus().toString(), status, jabberTransfer);
                    // }
                    if (jabberTransfer.error != null) {
                        Timber.e("An error occurred while transferring file: %s", jabberTransfer.error.message)
                    }
                    val transferException = jabberTransfer.exception
                    if (transferException != null) {
                        statusReason = transferException.message
                        Timber.e("An exception occurred while transferring file: %s", statusReason)
                        if (transferException is XMPPErrorException) {
                            val error = transferException.stanzaError
                            // get more specific reason for failure if available
                            if (error != null) {
                                statusReason = error.descriptiveText
                                if ((error.condition == StanzaError.Condition.not_acceptable) || error.condition == StanzaError.Condition.forbidden) status = FileTransferStatusChangeEvent.DECLINED
                            }
                        }
                    }

                    // Only partial file is received
                    if ((initialFileSize > 0) && status == FileTransferStatusChangeEvent.COMPLETED && fileTransfer.getTransferredBytes() < initialFileSize) {
                        status = FileTransferStatusChangeEvent.CANCELED
                    }
                    fileTransfer.fireStatusChangeEvent(status, statusReason!!)
                }

                // cmeng - use actual transfer bytes at time of fireProgressChangeEvent
                // for both the outgoing and incoming legacy file transfer.
                fileTransfer.fireProgressChangeEvent(System.currentTimeMillis(), fileTransfer.getTransferredBytes())

                // stop the FileTransferProgressThread thread once everything isDone()
                if (jabberTransfer.isDone) {
                    break
                }
            }
        }
    }

    companion object {
        // Change max to 20 MBytes. Original max 2GBytes i.e. 2147483648l = 2048*1024*1024;
        private const val MAX_FILE_LENGTH = 2147483647L

        // Register file transfer features on every established connection to make sure we register
        // them before creating our ServiceDiscoveryManager
        init {
            XMPPConnectionRegistry.addConnectionCreationListener { connection: XMPPConnection? -> FileTransferNegotiator.getInstanceFor(connection) }
        }

        /**
         * Parses the given Jabber status to a `FileTransfer` interface status.
         *
         * smackFTStatus the smack file transfer status to parse
         *
         * @return the parsed status
         * @see org.jivesoftware.smackx.filetransfer.FileTransfer
         */
        private fun parseJabberStatus(smackFTStatus: FileTransfer.Status): Int {
            return when (smackFTStatus) {
                FileTransfer.Status.complete -> FileTransferStatusChangeEvent.COMPLETED
                FileTransfer.Status.cancelled -> FileTransferStatusChangeEvent.CANCELED
                FileTransfer.Status.refused -> FileTransferStatusChangeEvent.DECLINED
                FileTransfer.Status.error -> FileTransferStatusChangeEvent.FAILED
                FileTransfer.Status.initial -> FileTransferStatusChangeEvent.PREPARING
                FileTransfer.Status.negotiating_transfer -> FileTransferStatusChangeEvent.WAITING
                FileTransfer.Status.negotiating_stream, FileTransfer.Status.negotiated, FileTransfer.Status.in_progress -> FileTransferStatusChangeEvent.IN_PROGRESS
                else -> FileTransferStatusChangeEvent.UNKNOWN
            }
        }
    }
}