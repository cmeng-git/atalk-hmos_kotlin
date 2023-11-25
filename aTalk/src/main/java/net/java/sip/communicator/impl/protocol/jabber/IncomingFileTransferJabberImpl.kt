/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.*
import org.jivesoftware.smackx.filetransfer.IncomingFileTransfer
import java.io.File

/**
 * The Jabber protocol extension of the `AbstractFileTransfer`.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class IncomingFileTransferJabberImpl
/**
 * Creates an `IncomingFileTransferJabberImpl`.
 *
 * @param id the identifier of this transfer
 * @param sender the sender of the file
 * @param file the file
 * @param jabberTransfer the Jabber file transfer object
 */
(private val mId: String, private val mSender: Contact?, private val mFile: File?,
        /**
         * The Jabber incoming file transfer.
         */
        private val mJabberTransfer: IncomingFileTransfer?) : AbstractFileTransfer() {
    /**
     * User declines the incoming file transfer/offer.
     */
    override fun cancel() {
        mJabberTransfer!!.cancel()
    }

    /**
     * Returns the number of bytes already received from the recipient.
     *
     * @return the number of bytes already received from the recipient
     */
    override fun getTransferredBytes(): Long {
        return mJabberTransfer!!.amountWritten
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
    override fun getID(): String {
        return mId
    }

    /**
     * Returns the local file that is being transferred or to which we transfer.
     *
     * @return the file
     */
    override fun getLocalFile(): File? {
        return mFile
    }
}