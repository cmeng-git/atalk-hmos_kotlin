/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * The `OperationSetCusaxUtils` provides utility methods related to a CUSAX implementation.
 *
 * @author Yana Stamcheva
 */
interface OperationSetCusaxUtils : OperationSet {
    /**
     * Checks if the given `detailAddress` exists in the given `contact` details.
     *
     * @param contact the `Contact`, which details to check
     * @param detailAddress the detail address we're looking for
     * @return `true` if the given `detailAdress` exists in the details of the given `contact`
     */
    fun doesDetailBelong(contact: Contact?, detailAddress: String?): Boolean

    /**
     * Returns the linked CUSAX provider for this protocol provider.
     *
     * @return the linked CUSAX provider for this protocol provider or null if such isn't specified
     */
    fun getLinkedCusaxProvider(): ProtocolProviderService?
}