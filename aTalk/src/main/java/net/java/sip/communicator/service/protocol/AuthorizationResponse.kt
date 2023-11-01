/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * This class is used to represent both incoming and outgoing AuthorizationResponse-s
 *
 *
 * An outgoing Authorization Response is to be created by the user interface when an authorization
 * request has been received from the network. The user interface or any other bundle responsible of
 * handling such responses is to implement the AuthorizationHandler interface and register itself
 * with a protocol provider. Whenever a response needs to be sent the protocol provider would ask
 * the the AuthorizationHandler to create one through the processAuthorizationRequest() method.
 *
 *
 * Incoming Authorization responses are delivered to the AuthorizationHandler implementation through
 * the AuthorizationHandler.processAuthorizationResponse() method.
 *
 * @author Emil Ivov
 */
class AuthorizationResponse(responseCode: AuthorizationResponseCode?, reason: String?) {
    /**
     * A reason indication that the source user may or may not add to explaining the response.
     */
    private var reason: String? = null

    /**
     * Authorization response codes represent unambiguous indication of the way a user or a remote
     * party have acted upon an authorization request.
     */
    private var responseCode: AuthorizationResponseCode? = null

    /**
     * Creates an instance of an AuthorizationResponse with the specified responseCode and reason.
     *
     * @param responseCode
     * AuthorizationResponseCode
     * @param reason
     * String
     */
    init {
        this.reason = reason
        this.responseCode = responseCode
    }

    /**
     * Returns a String containing additional explanations (might also be empty) of this response.
     *
     * @return the reason the source user has given to explain this response and null or an empty
     * string if no reason has been specified.
     */
    fun getReason(): String? {
        return reason
    }

    /**
     * Returns the response code that unambiguously represents the sense of this response.
     *
     * @return an AuthorizationResponseResponseCode instance determining the nature of the
     * response.
     */
    fun getResponseCode(): AuthorizationResponseCode? {
        return responseCode
    }

    /**
     * Authorization response codes represent unambiguous indication of the way a user or a remote
     * party have acted upon an authorization request.
     */
    class AuthorizationResponseCode(private val code: String) {
        /**
         * Returns the string contents representing this code.
         *
         * @return a String representing the code.
         */
        fun getCode(): String {
            return code
        }
    }

    companion object {
        /**
         * Indicates that the source authorization request which this response is about has been
         * accepted and that the requester may now proceed to adding the user to their contact list.
         */
        val ACCEPT = AuthorizationResponseCode("service.gui.ACCEPT")

        /**
         * Indicates that source authorization request which this response is about has been
         * rejected. A reason may also have been specified.
         */
        val REJECT = AuthorizationResponseCode("Reject")

        /**
         * Indicates that source authorization request which this response is about has been ignored
         * and that no other indication will be sent to the requester.
         */
        val IGNORE = AuthorizationResponseCode("Ignore")
    }
}