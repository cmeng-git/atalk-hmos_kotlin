/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.filehistory

import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.Contact
import java.io.File
import java.util.*

/**
 * Structure used for encapsulating data when writing or reading File History Data.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class FileRecord
/**
 * Constructs new FileRecord
 *
 * @param id File record Uuid
 * @param entityJid The entityJid  // Contact or ChatRoom
 * @param direction File received or send
 * @param date the timeStamp
 * @param file the file name
 * @param encType the file encryption (plain or OMEMO)
 * @param status Status of the fileTransfer
 */
(val id: String, val entityJid: Any, val direction: String, val date: Date, val file: File,
        val encType: Int, val status: Int) : EventObject(file) {

    /**
     * The contact.
     *
     * @return the contact
     */
    fun getJidAddress(): String {
        return if (entityJid is Contact) entityJid.address else (entityJid as ChatRoom).getName()
    }

    companion object {
        /**
         * Direction of the transfer: out
         */
        const val OUT = "out"

        /**
         * Direction of the transfer: in
         */
        const val IN = "in"

        /* ===============================================================
        File transfer status - save in message status for file transfer
        ===============================================================*/
        /**
         * Status indicating that the file transfer has been completed.
         */
        const val STATUS_COMPLETED = 10

        /**
         * Status indicating that the file transfer has failed.
         */
        const val STATUS_FAILED = 11

        /**
         * Status indicating that the file transfer has been canceled.
         */
        const val STATUS_CANCELED = 12

        /**
         * Status indicating that the file transfer has been refused.
         */
        const val STATUS_DECLINED = 13

        /**
         * Status indicating that the file transfer was in active state.
         */
        const val STATUS_WAITING = 14

        /**
         * Status indicating that the file transfer is preparing state.
         */
        const val STATUS_PREPARING = 15

        /**
         * Status indicating that the file transfer is in-progress state.
         */
        const val STATUS_IN_PROGRESS = 16

        /**
         * Status indicating that the file transfer state is unknown.
         */
        const val STATUS_UNKNOWN = -1

        // Special case where downloaded file cannot be found
        const val FILE_NOT_FOUND = -1

        /**
         * A map between File transfer status to status descriptive text
         */
        val statusMap = object : HashMap<Int?, String?>() {
            init {
                put(STATUS_COMPLETED, "completed")
                put(STATUS_FAILED, "failed")
                put(STATUS_CANCELED, "canceled")
                put(STATUS_DECLINED, "declined")
                put(STATUS_PREPARING, "preparing")
                put(STATUS_WAITING, "waiting")
                put(STATUS_IN_PROGRESS, "in_progress")
            }
        }
    }
}