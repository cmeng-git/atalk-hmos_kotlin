/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.fileaccess

import java.io.IOException

/**
 * A failsafe transaction interface. By failsafe we mean here that the file concerned always
 * stays in a coherent state.
 *
 * @author Benoit Pradelle
 * @author Eng Chong Meng
 */
interface FailSafeTransaction {
    /**
     * Ensure that the file accessed is in a coherent state. This function is useful to do a
     * failsafe read without starting a transaction.
     *
     * @throws IllegalStateException
     * if the file doesn't exists anymore
     * @throws IOException
     * if an IOException occurs during the file restoration
     */
    @Throws(IllegalStateException::class, IOException::class)
    fun restoreFile()

    /**
     * Begins a new transaction. If a transaction is already active, commits the changes and begin
     * a new transaction. A transaction can be closed by a commit or rollback operation. When the
     * transaction begins, the file is restored to a coherent state if needed.
     *
     * @throws IllegalStateException
     * if the file doesn't exists anymore
     * @throws IOException
     * if an IOException occurs during the transaction creation
     */
    @Throws(IllegalStateException::class, IOException::class)
    fun beginTransaction()

    /**
     * Closes the transaction and commit the changes. Everything written in the file during the
     * transaction is saved.
     *
     * @throws IllegalStateException
     * if the file doesn't exists anymore
     * @throws IOException
     * if an IOException occurs during the operation
     */
    @Throws(IllegalStateException::class, IOException::class)
    fun commit()

    /**
     * Closes the transation and cancel the changes. Everything written in the file during the
     * transaction is NOT saved.
     *
     * @throws IllegalStateException
     * if the file doesn't exists anymore
     * @throws IOException
     * if an IOException occurs during the operation
     */
    @Throws(IllegalStateException::class, IOException::class)
    fun rollback()
}