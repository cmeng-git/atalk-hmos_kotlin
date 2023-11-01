/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import org.apache.commons.lang3.StringUtils
import java.io.Serializable
import java.nio.charset.StandardCharsets

/**
 * A `StunServerDescriptor` stores information necessary to create a STUN or TURN candidate
 * harvester that we could use with ICE4J. Descriptors are normally initialized by protocol wizards.
 * They are then used to convert the data into a [String] form suitable for storage in an
 * accounts properties Map.
 * @see https://github.com/MilanKral/atalk-android/commit/d61d5165dda4d290280ebb3e93075e8846e255ad
 * Enhance TURN with TCP, TLS, DTLS transport
 *
 *
 * @author Yana Stamcheva
 * @author Emil Ivov
 * @author Eng Chong Meng
 * @author MilanKral
 */
class StunServerDescriptor(
        /**
         * The address (IP or FQDN) of the server.
         */
        var address: String,

        /**
         * The port of the server.
         */
        var port: Int,

        /**
         * Indicates if the server can also act as a TURN server (as opposed to a STUN only server).
         */
        var isTurnSupported: Boolean,

        username: String?, password: String?, protocol: String?) : Serializable {

    /**
     * The username that we need to use with the server or `null` if this server does not require a user name.
     */
    private var username: ByteArray?

    /**
     * The password that we need to use when authenticating with the server or `null` if no password is necessary.
     */
    private var password: ByteArray?

    /**
     * Transport protocol used.
     */
    var protocol: String?

    /**
     * Creates an instance of `StunServer` by specifying all parameters.
     *
     * address the IP address or FQDN of the STUN server
     * port the port of the server
     * supportTurn indicates if this STUN server supports TURN
     * username the user name for authenticating
     * password the password
     */
    init {
        this.username = username?.toByteArray(StandardCharsets.UTF_8) ?: byteArrayOf()
        this.password = password?.toByteArray(StandardCharsets.UTF_8) ?: byteArrayOf()
        this.protocol = protocol
    }

    /**
     * Returns the username associated to this server.
     *
     * @return the username associated to this server
     */
    fun getUsername(): ByteArray? {
        return username
    }

    /**
     * Sets the username associated to this server. Empty byte array if null
     *
     * @param username the username to set
     */
    fun setUsername(username: String?) {
        this.username = username?.toByteArray(StandardCharsets.UTF_8) ?: byteArrayOf()
    }

    /**
     * Returns the password associated to this server username.
     *
     * @return the password associated to this server username
     */
    fun getPassword(): ByteArray? {
        return password
    }

    /**
     * Sets the password associated to this server username. Empty byte array if null
     *
     * @param password the password to set
     */
    fun setPassword(password: String?) {
        this.password = password?.toByteArray(StandardCharsets.UTF_8) ?: byteArrayOf()
    }

    /**
     * Stores this descriptor into the specified [Map].The method is meant for use with
     * account property maps. It also allows prepending an account prefix to all property names so
     * that multiple descriptors can be stored in a single [Map].
     *
     * @param props the account properties [Map] that we'd like to store this descriptor in.
     * @param namePrefix_ the prefix that we should prepend to every property name.
     */
    fun storeDescriptor(props: MutableMap<String, String?>, namePrefix_: String?) {
        var namePrefix = namePrefix_
        if (namePrefix == null) namePrefix = ProtocolProviderFactory.STUN_PREFIX
        props[namePrefix + ProtocolProviderFactory.STUN_ADDRESS] = address
        if (port != -1) props[namePrefix + ProtocolProviderFactory.STUN_PORT] = port.toString()
        if (getUsername() != null && getUsername()!!.isNotEmpty()) {
            props[namePrefix + ProtocolProviderFactory.STUN_USERNAME] = StringUtils.toEncodedString(getUsername(), StandardCharsets.UTF_8)
        }
        if (getPassword() != null && getPassword()!!.isNotEmpty()) {
            props[namePrefix + ProtocolProviderFactory.STUN_PASSWORD] = String(getPassword()!!)
        }
        props[namePrefix + ProtocolProviderFactory.STUN_IS_TURN_SUPPORTED] = java.lang.Boolean.toString(isTurnSupported)
        props[namePrefix + ProtocolProviderFactory.STUN_TURN_PROTOCOL] = protocol
    }

    /**
     * Returns a `String` representation of this descriptor
     *
     * @return a `String` representation of this descriptor.
     */
    override fun toString(): String {
        return "StunServerDesc: $address/$port turnSupport=$isTurnSupported"
    }

    companion object {
        /**
         * The maximum number of stun servers that we would allow.
         */
        const val MAX_STUN_SERVER_COUNT = 100

        /**
         * UDP protocol.
         */
        const val PROTOCOL_UDP = "udp"

        /**
         * TCP protocol.
         */
        const val PROTOCOL_TCP = "tcp"

        /**
         * UDP with DTLS protocol.
         */
        const val PROTOCOL_DTLS = "dtls"

        /**
         * TCP with TLS protocol.
         */
        const val PROTOCOL_TLS = "tls"

        /**
         * TCP with SSL protocol (only for Google Talk TURN server).
         */
        const val PROTOCOL_SSLTCP = "ssltcp"

        /**
         * Loads this descriptor from the specified [Map].The method is meant for use with account
         * property maps. It also allows prepending an account prefix to all property names so that
         * multiple descriptors can be read in a single [Map].
         *
         * @param props the account properties [Map] that we'd like to load this descriptor from.
         * @param namePrefix_ the prefix that we should prepend to every property name.
         * @return the newly created descriptor or null if no descriptor was found.
         */
        fun loadDescriptor(props: Map<String, String?>, namePrefix_: String?): StunServerDescriptor? {
            var namePrefix = namePrefix_
            if (namePrefix == null) namePrefix = ProtocolProviderFactory.STUN_PREFIX

            // there doesn't seem to be a stun server with the specified prefix
            val stunAddress = props[namePrefix + ProtocolProviderFactory.STUN_ADDRESS]
                    ?: return null
            val stunPortStr = props[namePrefix + ProtocolProviderFactory.STUN_PORT]
            var stunPort = -1
            try {
                stunPort = stunPortStr!!.toInt()
            } catch (t: Throwable) {
                // if the port value was wrong we just keep the default: -1
            }
            val stunUsername = props[namePrefix + ProtocolProviderFactory.STUN_USERNAME]
            val stunPassword = props[namePrefix + ProtocolProviderFactory.STUN_PASSWORD]
            val stunIsTurnSupported = java.lang.Boolean.parseBoolean(props[namePrefix + ProtocolProviderFactory.STUN_IS_TURN_SUPPORTED])
            var stunTURNprotocol = props[namePrefix + ProtocolProviderFactory.STUN_TURN_PROTOCOL]
            if (stunTURNprotocol == null) {
                stunTURNprotocol = PROTOCOL_UDP
            }
            return StunServerDescriptor(stunAddress, stunPort, stunIsTurnSupported, stunUsername,
                    stunPassword, stunTURNprotocol)
        }
    }
}