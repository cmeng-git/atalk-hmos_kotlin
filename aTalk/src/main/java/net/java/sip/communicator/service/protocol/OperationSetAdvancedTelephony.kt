/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.OperationFailedException

/**
 * Extends `OperationSetBasicTelephony` with advanced telephony operations such as call transfer.
 *
 * @param <T> the implementation specific provider class like for example `ProtocolProviderServiceSipImpl`.
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
</T> */
interface OperationSetAdvancedTelephony<T : ProtocolProviderService> : OperationSetBasicTelephony<T> {
    /**
     * Transfers (in the sense of call transfer) a specific `CallPeer` to a specific callee
     * address which already participates in an active `Call`.
     *
     * The method is suitable for providing the implementation of 'attended' call transfer
     * (though no such requirement is imposed).
     *
     * @param peer the `CallPeer` to be transferred to the specified callee address
     * @param target the address in the form of `CallPeer` of the callee to transfer `peer` to
     * @throws OperationFailedException if something goes wrong.
     */
    @Throws(OperationFailedException::class)
    fun transfer(peer: CallPeer?, target: CallPeer?)

    /**
     * Transfers (in the sense of call transfer) a specific `CallPeer` to a specific callee
     * address which may or may not already be participating in an active `Call`.
     *
     * The method is suitable for providing the implementation of 'unattended' call transfer
     * (though no such requirement is imposed).
     *
     * @param peer the `CallPeer` to be transferred to the specified callee address
     * @param target the address of the callee to transfer `peer` to
     * @throws OperationFailedException if something goes wrong.
     */
    @Throws(OperationFailedException::class)
    fun transfer(peer: CallPeer?, target: String?)

    /**
     * Transfer authority used for interacting with user for unknown call transfer requests.
     *
     * @param authority transfer authority asks user for accepting a particular transfer request.
     */
    fun setTransferAuthority(authority: TransferAuthority?)
}