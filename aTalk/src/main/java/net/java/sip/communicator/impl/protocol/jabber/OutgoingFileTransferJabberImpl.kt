/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.AbstractFileTransfer
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.util.ConfigurationUtils.isSendThumbnail
import org.jivesoftware.smack.StanzaListener
import org.jivesoftware.smack.filter.AndFilter
import org.jivesoftware.smack.filter.IQTypeFilter
import org.jivesoftware.smack.filter.StanzaTypeFilter
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smackx.bob.BoBData
import org.jivesoftware.smackx.bob.BoBInfo
import org.jivesoftware.smackx.bob.BoBManager
import org.jivesoftware.smackx.bob.element.BoBIQ
import org.jivesoftware.smackx.filetransfer.FileTransfer
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer
import org.jivesoftware.smackx.si.packet.StreamInitiation
import org.jivesoftware.smackx.thumbnail.Thumbnail
import org.jivesoftware.smackx.thumbnail.ThumbnailFile
import timber.log.Timber
import java.io.File

/**
 * The Jabber protocol extension of the `AbstractFileTransfer`.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class OutgoingFileTransferJabberImpl(private val mContact: Contact, private val mFile: File,
        /**
         * The jabber outgoing file transfer.
         */
        private val mJabberFileTransfer: OutgoingFileTransfer?,
        private val mPPS: ProtocolProviderServiceJabberImpl,   // Message Uuid also used as file transfer id.
        private val msgUuid: String) : AbstractFileTransfer(), StanzaListener {
    private var bobInfo: BoBInfo? = null

    /**
     * Creates an `OutgoingFileTransferJabberImpl` by specifying the `receiver`
     * contact, the `file` , the `jabberTransfer`, that would be used to send the file
     * through Jabber and the `protocolProvider`.
     *
     * recipient the destination contact
     * file the file to send
     * jabberTransfer the Jabber transfer object, containing all transfer information
     * protocolProvider the parent protocol provider
     * id the id that uniquely identifies this file transfer and saved DB record
     */
    init {
        // Create the identifier of this file transfer that is used from the history and the user
        // interface to track this transfer. Use pass in value if available (cmeng 20220206: true always)
        // this.id = (TextUtils.isEmpty(msgUuid)) ? String.valueOf(System.currentTimeMillis()) + hashCode() : msgUuid;
        // Timber.e("OutgoingFileTransferJabberImpl msgUid: %s", id);

        // jabberTransfer is null for http file upload
        if (mJabberFileTransfer != null) {
            // Add this outgoing transfer as a packet interceptor in order to manage thumbnails.
            if (isSendThumbnail() && mFile is ThumbnailedFile && mFile.thumbnailData.isNotEmpty()) {
                if (mPPS.isFeatureListSupported(mPPS.getFullJidIfPossible(mContact),
                                Thumbnail.NAMESPACE, BoBIQ.NAMESPACE)) {
                    mPPS.connection!!.addStanzaInterceptor(this,
                            AndFilter(IQTypeFilter.SET, StanzaTypeFilter(StreamInitiation::class.java)))
                }
            }
        }
    }

    /**
     * Cancels the file transfer.
     */
    override fun cancel() {
        mJabberFileTransfer!!.cancel()
    }

    /**
     * Get the transfer error
     * @return FileTransfer.Error
     */
    val transferError: FileTransfer.Error
        get() = mJabberFileTransfer!!.error

    /**
     * Returns the number of bytes already sent to the recipient.
     *
     * @return the number of bytes already sent to the recipient.
     */
    override fun getTransferredBytes(): Long {
        return mJabberFileTransfer!!.bytesSent
    }

    /**
     * The direction is outgoing.
     *
     * @return OUT.
     */
    override fun getDirection(): Int {
        return net.java.sip.communicator.service.protocol.FileTransfer.OUT
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

    /**
     * Removes previously added thumbnail request listener.
     */
    fun removeThumbnailHandler() {
        if (bobInfo == null) {
            return
        }
        val bobManager = BoBManager.getInstanceFor(mPPS.connection)
        for (hash in bobInfo!!.hashes) {
            bobManager.removeBoB(hash)
        }
    }

    /**
     * Listen for all `Si` stanzas and adds a thumbnail element to it if a thumbnail preview is enabled.
     *
     * @see StanzaListener.processStanza
     */
    override fun processStanza(stanza: Stanza) {
        if (!isSendThumbnail() || stanza !is StreamInitiation) return

        // If our file is not a thumbnail file we have nothing to do here.
        if (mFile !is ThumbnailedFile) return
        val connection = mPPS.connection
        val fileTransferPacket = stanza as StreamInitiation
        val thumbnailedFile = mFile
        if (mJabberFileTransfer!!.streamID == fileTransferPacket.sessionID) {
            val file = fileTransferPacket.file
            val bobData = BoBData(
                    thumbnailedFile.thumbnailMimeType,
                    thumbnailedFile.thumbnailData,
                    maxAge)
            val bobManager = BoBManager.getInstanceFor(connection)
            bobInfo = bobManager.addBoB(bobData)
            val thumbnail = Thumbnail(
                    thumbnailedFile.thumbnailData,
                    thumbnailedFile.thumbnailMimeType,
                    thumbnailedFile.thumbnailWidth,
                    thumbnailedFile.thumbnailHeight)
            val fileElement = ThumbnailFile(file, thumbnail)
            fileTransferPacket.file = fileElement
            Timber.d("File transfer packet intercepted to add thumbnail element.")
            // Timber.d("The file transfer packet with thumbnail: %s", fileTransferPacket.toXML(XmlEnvironment.EMPTY));
        }

        // Remove this packet interceptor after we're done.
        connection?.removeStanzaInterceptor(this)
    }

    companion object {
        // must include this attribute in bobData; else smack 4.4.0 throws NPE
        private const val maxAge = 86400
    }
}