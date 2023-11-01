/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.fileaccess

import org.atalk.service.fileaccess.FailSafeTransaction
import java.io.*
import java.lang.NullPointerException

/**
 * A failsafe transaction class. By failsafe we mean here that the file concerned
 * always stays in a coherent state. This class use the transactional model.
 *
 * @author Benoit Pradelle
 * @author Eng Chong Meng
 */
class FailSafeTransactionImpl(file: File?) : FailSafeTransaction {
    /**
     * Original file used by the transaction
     */
    private val file: File

    /**
     * Backup file used by the transaction
     */
    private var backup: File?

    /**
     * Creates a new transaction.
     *
     * @param file The file associated with this transaction
     * @throws NullPointerException if the file is null
     */
    init {
        if (file == null) {
            throw NullPointerException("null file provided")
        }
        this.file = file
        backup = null
    }

    /**
     * Ensure that the file accessed is in a coherent state. This function is useful to do a failsafe read without
     * starting a transaction.
     *
     * @throws IllegalStateException if the file doesn't exists anymore
     * @throws IOException if an IOException occurs during the file restoration
     */
    @Throws(IllegalStateException::class, IOException::class)
    override fun restoreFile() {
        val back = File(file.absolutePath + BAK_EXT)

        // if a backup copy is still present, simply restore it
        if (back.exists()) {
            failsafeCopy(back, file)
            back.delete()
        }
    }

    /**
     * Begins a new transaction. If a transaction is already active, commits the changes and begin a new transaction. A
     * transaction can be closed by a commit or rollback operation. When the transaction begins, the file is restored to
     * a coherent state if needed.
     *
     * @throws IllegalStateException if the file doesn't exists anymore
     * @throws IOException if an IOException occurs during the transaction creation
     */
    @Throws(IllegalStateException::class, IOException::class)
    override fun beginTransaction() {
        // if the last transaction hasn't been closed, commit it
        if (backup != null) {
            commit()
        }

        // if needed, restore the file in its previous state
        restoreFile()
        backup = File(file.absolutePath + BAK_EXT)

        // else backup the current file
        failsafeCopy(file, backup)
    }

    /**
     * Closes the transaction and commit the changes. Everything written in the file during the transaction is saved.
     *
     * @throws IllegalStateException if the file doesn't exists anymore
     */
    @Throws(IllegalStateException::class)
    override fun commit() {
        if (backup == null) {
            return
        }

        // simply delete the backup file
        backup!!.delete()
        backup = null
    }

    /**
     * Closes the transaction and cancels the changes. Everything written in the file during the transaction is NOT
     * saved.
     *
     * @throws IllegalStateException if the file doesn't exists anymore
     * @throws IOException if an IOException occurs during the operation
     */
    @Throws(IllegalStateException::class, IOException::class)
    override fun rollback() {
        if (backup == null) {
            return
        }

        // restore the backup and delete it
        failsafeCopy(backup, file)
        backup!!.delete()
        backup = null
    }

    /**
     * Copy a file in a fail-safe way. The destination is created in an atomic way.
     *
     * @param from The file to copy
     * @param to The copy to create
     * @throws IllegalStateException if the file doesn't exists anymore
     * @throws IOException if an IOException occurs during the operation
     */
    @Throws(IllegalStateException::class, IOException::class)
    private fun failsafeCopy(from: File?, to: File?) {
        val `in`: FileInputStream
        val out: FileOutputStream

        // to ensure a perfect copy, delete the destination if it exists
        if (to!!.exists()) {
            if (!to.delete()) throw IOException("Failed to delete destination file: " + to.name)
        }
        val ptoF = File(to.absolutePath + PART_EXT)
        if (ptoF.exists()) {
            if (!ptoF.delete()) throw IOException("Failed to delete partial file: " + ptoF.name)
        }
        try {
            `in` = FileInputStream(from)
            out = FileOutputStream(to.toString() + PART_EXT)
        } catch (e: FileNotFoundException) {
            throw IllegalStateException(e.message)
        }

        // actually copy the file
        val buf = ByteArray(1024)
        var len: Int
        while (`in`.read(buf).also { len = it } > 0) {
            out.write(buf, 0, len)
        }
        `in`.close()
        out.close()

        // once done, rename the partial file to the final copy
        if (!ptoF.renameTo(to)) throw IOException("Failed to rename " + ptoF.name + " to " + to.name)
    }

    companion object {
        /**
         * Extension of a partial file
         */
        private const val PART_EXT = ".part"

        /**
         * Extension of a backup copy
         */
        private const val BAK_EXT = ".bak"
    }
}