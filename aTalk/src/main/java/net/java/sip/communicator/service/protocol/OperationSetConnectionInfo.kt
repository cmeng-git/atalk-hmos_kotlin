/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import java.net.InetSocketAddress

/**
 * An `OperationSet` that allows access to connection information used by the protocol provider.
 *
 * @author Markus Kilas
 * @author Eng Chong Meng
 */
interface OperationSetConnectionInfo : OperationSet {
    /**
     * @return The address of the server.
     */
    fun getServerAddress(): InetSocketAddress?
}