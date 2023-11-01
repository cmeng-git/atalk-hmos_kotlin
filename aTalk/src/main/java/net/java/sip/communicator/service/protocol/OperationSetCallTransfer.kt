/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * An `OperationSet` defining operations that allow transferring calls to a new location.
 *
 * @author Emil Ivov
 */
interface OperationSetCallTransfer : OperationSet {
    /**
     * Indicates a user request to transfer the specified call participant to a new (target) uri.
     *
     * @param peer
     * the call peer we'd like to transfer
     * @param targetURI
     * the uri that we'd like this call peer to be transferred to.
     */
    fun transferCallPeer(peer: CallPeer?, targetURI: String?)
}