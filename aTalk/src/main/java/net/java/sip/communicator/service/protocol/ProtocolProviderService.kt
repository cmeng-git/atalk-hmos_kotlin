/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.impl.protocol.jabber.ProtocolIconJabberImpl
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener
import net.java.sip.communicator.service.protocol.jabber.JabberAccountID
import org.jivesoftware.smack.AbstractXMPPConnection

/**
 * The ProtocolProvider interface should be implemented by bundles that wrap Instant Messaging and
 * telephony protocol stacks. It gives the user interface a way to plug into these stacks and
 * receive notifications on status change and incoming calls, as well as deliver user requests for
 * establishing or ending calls, putting peers on hold and etc.
 *
 *
 * An instance of a ProtocolProviderService corresponds to a particular user account and all
 * operations performed through a provider (sending messages, modifying contact lists, receiving
 * calls)would pertain to this particular user account.
 *
 *
 * ProtocolProviderService instances are created through the provider factory. Each protocol
 * provider is assigned a unique AccountID instance that uniquely identifies it. Account id's for
 * different accounts are guaranteed to be different and in the same time the ID of a particular
 * account against a given service over any protocol will always be the same (so that we detect
 * attempts for creating the same account twice.)
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface ProtocolProviderService {
    /**
     * Starts the registration process. Connection details such as registration server, user
     * name/number are provided through the configuration service through implementation specific
     * properties.
     *
     * @param authority the security authority that will be used for resolving any security
     * challenges that may be returned during the registration or at any moment while we're
     * registered.
     * @throws OperationFailedException with the corresponding code it the registration fails
     * for some reason (e.g. a networking error or an implementation problem).
     */
    @Throws(OperationFailedException::class)
    fun register(authority: SecurityAuthority?)

    /**
     * Ends the registration of this protocol provider with the current registration service.
     *
     * @throws OperationFailedException with the corresponding code it the registration fails
     * for some reason (e.g. a networking error or an implementation problem).
     */
    @Throws(OperationFailedException::class)
    fun unregister()

    /**
     * Ends the registration of this protocol provider with the current registration service.
     *
     * @param userRequest is the unregister by user request.
     * @throws OperationFailedException with the corresponding code it the registration fails
     * for some reason (e.g. a networking error or an implementation problem).
     */
    @Throws(OperationFailedException::class)
    fun unregister(userRequest: Boolean)

    /**
     * Indicates whether or not this provider is registered
     *
     * @return true if the provider is currently registered and false otherwise.
     */
    val isRegistered: Boolean

    /**
     * Returns the `XMPPConnection`opened by this provider
     *
     * @return a reference to the `XMPPConnection` last opened by this provider.
     */
    var connection: AbstractXMPPConnection?

    /**
     * Indicates whether or not this provider must registered when placing outgoing calls.
     *
     * @return true if the provider must be registered when placing a call.
     */
    val isRegistrationRequiredForCalling: Boolean

    /**
     * Returns the state of the registration of this protocol provider with the corresponding
     * registration service.
     *
     * @return ProviderRegistrationState
     */
    val registrationState: RegistrationState

    /**
     * Returns the short name of the protocol that the implementation of this provider is based
     * upon (like SIP, Jabber, ICQ/AIM, or others for example). If the name of the protocol has
     * been enumerated in ProtocolNames then the value returned by this method must be the same
     * as the one in ProtocolNames.
     *
     * @return a String containing the short name of the protocol this service is implementing
     * (most often that would be a name in ProtocolNames).
     */
    val protocolName: String

    /**
     * Returns the protocol display name. This is the name that would be used by the GUI to display
     * the protocol name.
     *
     * @return a String containing the display name of the protocol this service is implementing
     */
    val protocolDisplayName: String

    /**
     * Returns the protocol logo icon.
     *
     * @return the protocol logo icon
     */
    var protocolIcon: ProtocolIconJabberImpl

    /**
     * Registers the specified listener with this provider so that it would receive notifications
     * on changes of its state or other properties such as its local address and display name.
     *
     * @param listener the listener to register.
     */
    fun addRegistrationStateChangeListener(listener: RegistrationStateChangeListener?)

    /**
     * Removes the specified listener.
     *
     * @param listener the listener to remove.
     */
    fun removeRegistrationStateChangeListener(listener: RegistrationStateChangeListener)

    /**
     * Returns an array containing all operation sets supported by the current implementation. When
     * querying this method users must be prepared to receive any subset of the OperationSet-s
     * defined by this service. They MUST ignore any OperationSet-s that they are not aware of and
     * that may be defined by future versions of this service. Such "unknown" OperationSet-s though
     * not encouraged, may also be defined by service implementors.
     *
     * @return a [Map] containing instances of all supported operation sets mapped against
     * their class names (e.g. `OperationSetPresence.class.getName()` associated with
     * a `OperationSetPresence` instance).
     */
    fun getSupportedOperationSets(): Map<String, OperationSet>

    /**
     * Returns a collection containing all operation sets classes supported by the current
     * implementation. When querying this method users must be prepared to receive any subset of the
     * OperationSet-s defined by this service. They MUST ignore any OperationSet-s that they are not
     * aware of and that may be defined by future versions of this service. Such "unknown"
     * OperationSet-s though not encouraged, may also be defined by service implementors.
     *
     * @return a [Collection] containing instances of all supported operation set classes
     * (e.g. `OperationSetPresence.class`.
     */
    fun getSupportedOperationSetClasses(): Collection<Class<out OperationSet>>

    /**
     * Returns the operation set corresponding to the specified class or `null` if this
     * operation set is not supported by the provider implementation.
     *
     * @param <T> the type which extends `OperationSet` and which is to be retrieved
     * @param opsetClass the `Class` of the operation set that we're looking for.
     * @return returns an OperationSet of the specified `Class` if the underlying </T>
     */
    fun <T : OperationSet?> getOperationSet(opsetClass: Class<T>): T?

    /**
     * Makes the service implementation close all open sockets and release any resources that it
     * might have taken and prepare for shutdown/garbage collection.
     */
    fun shutdown()

    /**
     * Returns the AccountID that uniquely identifies the account represented by this instance of
     * the ProtocolProviderService.
     *
     * @return the id of the account represented by this provider.
     */
    var accountID: JabberAccountID

    /**
     * Validates the given protocol specific contact identifier and returns an
     * error message if applicable and a suggested correction.
     *
     * @param contactId the contact identifier to validate
     * @param result Must be supplied as an empty a list. Implementors add items:
     *
     *  1. is the error message if applicable
     *  1. a suggested correction. Index 1 is optional and can only
     * be present if there was a validation failure.
     *
     * @return true if the contact id is valid, false otherwise
     */
    fun validateContactAddress(contactId: String?, result: MutableList<String?>?): Boolean

    /**
     * Indicate if the signaling transport of this protocol instance uses a secure (e.g. via TLS) connection.
     *
     * @return True when the connection is secured, false otherwise.
     */
    val isSignalingTransportSecure: Boolean

    /**
     * Returns the "transport" protocol of this instance used to carry the control channel for the
     * current protocol service.
     *
     * @return The "transport" protocol of this instance: UDP, TCP, TLS or UNKNOWN.
     */
    val transportProtocol: TransportProtocol

    companion object {
        /**
         * The name of the property containing the number of binds that a Protocol Provider Service
         * Implementation should execute in case a port is already bound to (each retry would be on a
         * new random port).
         */
        const val BIND_RETRIES_PROPERTY_NAME = "protocol.BIND_RETRIES"

        /**
         * The default number of binds that a Protocol Provider Service Implementation should execute
         * in case a port is already bound to (each retry would be on a new random port).
         */
        const val BIND_RETRIES_DEFAULT_VALUE = 50
    }
}