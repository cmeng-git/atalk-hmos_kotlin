/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.account

import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.SecurityAuthority

/**
 * The `LoginRenderer` is the renderer of all login related operations.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface LoginRenderer {
    /**
     * Adds the user interface related to the given protocol provider.
     *
     * @param protocolProvider the protocol provider for which we add the user interface
     */
    fun addProtocolProviderUI(protocolProvider: ProtocolProviderService)

    /**
     * Removes the user interface related to the given protocol provider.
     *
     * @param protocolProvider the protocol provider to remove
     */
    fun removeProtocolProviderUI(protocolProvider: ProtocolProviderService)

    /**
     * Starts the connecting user interface for the given protocol provider.
     *
     * @param protocolProvider the protocol provider for which we add the connecting user interface
     */
    fun startConnectingUI(protocolProvider: ProtocolProviderService)

    /**
     * Stops the connecting user interface for the given protocol provider.
     *
     * @param protocolProvider the protocol provider for which we remove the connecting user interface
     */
    fun stopConnectingUI(protocolProvider: ProtocolProviderService)

    /**
     * Indicates that the given protocol provider is now connected.
     *
     * @param protocolProvider the `ProtocolProviderService` that is connected
     * @param date the date on which the event occured
     */
    fun protocolProviderConnected(protocolProvider: ProtocolProviderService, date: Long)

    /**
     * Indicates that a protocol provider connection has failed.
     *
     * @param protocolProvider the `ProtocolProviderService`, which connection failed
     * @param loginManagerCallback the `LoginManager` implementation, which is managing the process
     */
    fun protocolProviderConnectionFailed(protocolProvider: ProtocolProviderService,
            loginManagerCallback: LoginManager)

    /**
     * Returns the `SecurityAuthority` implementation related to this login renderer.
     *
     * @param protocolProvider the specific `ProtocolProviderService`, for which we're
     * obtaining a security authority
     * @return the `SecurityAuthority` implementation related to this login renderer
     */
    fun getSecurityAuthorityImpl(protocolProvider: ProtocolProviderService): SecurityAuthority?

    /**
     * Indicates if the given `protocolProvider` related user interface is already rendered.
     *
     * @param protocolProvider the `ProtocolProviderService`, which related user interface
     * we're looking for
     * @return `true` if the given `protocolProvider` related user interface is already rendered
     */
    fun containsProtocolProviderUI(protocolProvider: ProtocolProviderService): Boolean
}