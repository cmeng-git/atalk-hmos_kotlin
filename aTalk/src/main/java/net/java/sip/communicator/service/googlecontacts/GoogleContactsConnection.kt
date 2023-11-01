/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.googlecontacts

/**
 * Interface that define a Google Contacts connection.
 *
 * @author Sebastien Vincent
 */
interface GoogleContactsConnection {
    /**
     * Enumeration for connection status.
     */
    enum class ConnectionStatus {
        /**
         * Connection has failed due to invalid credentials.
         */
        ERROR_INVALID_CREDENTIALS,

        /**
         * Connection has failed due to unknown reason.
         */
        ERROR_UNKNOWN,

        /**
         * Connection has succeed.
         */
        SUCCESS
    }

    /**
     * Get login.
     *
     * @return login to connect to the service
     */
    fun getLogin(): String?

    /**
     * Set login.
     *
     * @param login login to connect to the service
     */
    fun setLogin(login: String?)

    /**
     * Get password.
     *
     * @return password to connect to the service
     */
    fun getPassword(): String?

    /**
     * Set password.
     *
     * @param password password to connect to the service
     */
    fun setPassword(password: String?)

    /**
     * Initialize connection.
     *
     * @return connection status
     */
    fun connect(): ConnectionStatus?

    /**
     * Returns the google contacts prefix.
     *
     * @return the google contacts prefix
     */
    fun getPrefix(): String?
}