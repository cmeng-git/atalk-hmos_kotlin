/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * Implemented by the user interface, this interface allows a protocol provider to asynchronously
 * demand passwords necessary for authentication against various realms.
 *
 *
 * Or in other (simpler words) this is a callback or a hook that the UI would give a protocol
 * provider so that the protocol provider could requestCredentials() when necessary (when a password
 * is not available for a server, or once it has changed, or re-demand one after a faulty authentication)
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 */
interface SecurityAuthority {
    /**
     * Returns a UserCredentials object associated with the specified realm, by specifying the
     * reason of this operation.
     *
     *
     *
     * @param accountID The realm that the credentials are needed for.
     * @param credentials the values to propose the user by default
     * @param reasonCode indicates the reason for which we're obtaining the credentials.
     * @param isShowServerOption true is to request user for server option
     * @return The credentials associated with the specified realm or null if none could be obtained.
     */
    fun obtainCredentials(accountID: AccountID, credentials: UserCredentials,
            reasonCode: Int, isShowServerOption: Boolean): UserCredentials

    /**
     * Returns a UserCredentials object associated with the specified realm, by specifying the
     * reason of this operation.
     *
     *
     *
     * @param accountID The realm that the credentials are needed for.
     * @param defaultValues the values to propose the user by default
     * @param isShowServerOption true is to request user for server option
     * @return The credentials associated with the specified realm or null if none could be obtained.
     */
    fun obtainCredentials(accountID: AccountID, defaultValues: UserCredentials, isShowServerOption: Boolean): UserCredentials

    /**
     * Sets the userNameEditable property, which should indicate to the implementations of this
     * interface if the user name could be changed by user or not.
     *
     * @param isUserNameEditable indicates if the user name could be changed by user in the implementation of this
     * interface.
     */
    fun setUserNameEditable(isUserNameEditable: Boolean)

    /**
     * Indicates if the user name is currently editable, i.e. could be changed by user or not.
     *
     * @return `true` if the user name could be changed, `false` - otherwise.
     */
    fun isUserNameEditable(): Boolean

    companion object {
        /**
         * Indicates that reason is unknown for authentication failure.
         */
        const val REASON_UNKNOWN = -1

        /**
         * Indicates that the reason for obtaining credentials is that an authentication is required.
         */
        const val AUTHENTICATION_REQUIRED = 0

        /**
         * Indicates that the reason for obtaining credentials is that the last time a wrong password
         * has been provided.
         */
        const val WRONG_PASSWORD = 1

        /**
         * Indicates that the reason for obtaining credentials is that the last time a wrong user name
         * has been provided.
         */
        const val WRONG_USERNAME = 2

        /**
         * Indicates that the reason for obtaining credentials is that the last login has an invalid authorization
         */
        const val INVALID_AUTHORIZATION = 3

        /**
         * Indicates that the reason for obtaining credentials is that the last connection has failed
         */
        const val CONNECTION_FAILED = 4

        /**
         * Indicates that the reason for obtaining credentials is that the last server name provided
         * cannot be resolved to any server ip
         */
        const val NO_SERVER_FOUND = 5

        /**
         * Indicates that the requested service is not available: sasl, incompatible authentication mechanism etc
         */
        const val AUTHENTICATION_FAILED = 6

        /**
         * Indicates that the reason for obtaining credentials is that the last login has failed server account registration
         */
        const val AUTHENTICATION_FORBIDDEN = 7

        /**
         * Indicates that the reason for obtaining credentials is that the last login is not authorized
         * - IBR registration required.
         */
        const val NOT_AUTHORIZED = 8

        /**
         * SASLError using EXTERNAL: not-authorized: Failed to get peer certificate
         */
        const val SASL_ERROR_EXTERNAL = 9

        /**
         * Indicates that reason for login failure is due to a policy violation.
         */
        const val POLICY_VIOLATION = 10

        /**
         * Indicates that reason for login failure is due to a policy violation.
         */
        const val DNSSEC_NOT_ALLOWED = 11

        /**
         * Indicates that reason for login failure is due to conflict e.g. multiple login instances
         */
        const val CONFLICT = 12

        /**
         * Indicates that reason for login failure is due to security exception: ssl connection failed
         */
        const val SECURITY_EXCEPTION = 13
    }
}