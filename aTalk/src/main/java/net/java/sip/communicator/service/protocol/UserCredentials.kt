/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * The class is used whenever user credentials for a particular realm (site server or service) are
 * necessary
 *
 * @author Emil Ivov <emcho></emcho>@dev.java.net>
 * @author Eng Chong Meng
 */
class UserCredentials {
    /**
     * The user name.
     */
    var userName: String? = null

    /**
     * The user passWord.
     */
    var password: CharArray? = null

    /**
     * If we will store the passWord persistently.
     */
    var isPasswordPersistent = true

    /**
     * InBand Registration
     */
    var isIbRegistration = false

    /**
     * User login server parameter "is server overridden" value.
     */
    var isServerOverridden = false

    /**
     * `true` when user cancel the Credential request.
     */
    var isUserCancel = false

    /**
     * User login server parameter "server address" value.
     */
    var serverAddress: String? = null

    /**
     * User login server parameter "server port" value.
     */
    var serverPort: String? = null

    /**
     * Reason for login / reLogin.
     */
    var loginReason: String? = null

    /**
     * Reason for login / reLogin.
     */
    var dnssecMode: String? = null

    /**
     * Returns a String containing the passWord associated with this set of credentials.
     *
     * @return a String containing the passWord associated with this set of credentials.
     */
    fun getPasswordAsString(): String {
        return String(password!!)
    }
}