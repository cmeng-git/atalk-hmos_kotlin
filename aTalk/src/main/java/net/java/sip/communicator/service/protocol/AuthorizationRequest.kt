/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * This class is used to represent both incoming and outgoing AuthorizationRequests
 *
 *
 * An outgoing Authorization Request is to be created by the user interface when an authorization
 * error/challenge has been received by the underlying protocol. The user interface or any other
 * bundle responsible of handling such requests is to implement the AuthorizationHandler interface
 * and register itself as an authorization handler of a protocol provider. Whenever a request needs
 * to be sent the protocol provider would ask the the AuthorizationHandler to create one through the
 * createAuthorizationRequest() method.
 *
 *
 * Incoming Authorization requests are delivered to the ProtocolProviderService implementation
 * through the AuthorizationHandler.processAuthorizationRequest() method.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
/**
 * Creates an empty authorization request with no reason or any other properties.
 */
class AuthorizationRequest
{
    /**
     * The reason phrase that should be sent to the user we're demanding for authorization.
     */
    var reason = ""
}