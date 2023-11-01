/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * Interacts with user for received transfer request for unknown calls.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
interface TransferAuthority {
    /**
     * Checks with user for unknown transfer. Returns `true` if user accepts and we must
     * process the transfer, `false` otherwise.
     *
     * @param fromContact
     * the contact initiating the transfer.
     * @param transferTo
     * the address we will be transferred to.
     * @return `true` if transfer is allowed to process, `false` otherwise.
     */
    fun processTransfer(fromContact: Contact?, transferTo: String?): Boolean

    /**
     * Checks with user for unknown transfer. Returns `true` if user accepts and we must
     * process the transfer, `false` otherwise.
     *
     * @param fromAddress
     * the address initiating the transfer.
     * @param transferTo
     * the address we will be transferred to.
     * @return `true` if transfer is allowed to process, `false` otherwise.
     */
    fun processTransfer(fromAddress: String?, transferTo: String?): Boolean
}