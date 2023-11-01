/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import java.io.Serializable

/**
 * A `JingleNodesDescriptor` stores information necessary to create a JingleNodes tracker or
 * relay candidate harvester that we could use with ICE4J. Descriptors are normally initialized by
 * protocol wizards. They are then used to convert the data into a [String] form suitable for
 * storage in an accounts properties Map.
 *
 * @author Yana Stamcheva
 * @author Emil Ivov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class JingleNodeDescriptor
/**
 * Creates an instance of `JingleNodes` by specifying all parameters.
 *
 * @param address address of the JingleNodes
 * @param relaySupported if the JingleNodes supports relay
 */
(
        /**
         * The address of the JingleNodes (JID).
         */
        private var address: Jid,
        /**
         * If the relay is supported by this JingleNodes.
         */
        private var relaySupported: Boolean) : Serializable {
    /**
     * Returns the address of the JingleNodes
     *
     * @return the address of the JingleNodes
     */
    fun getJID(): Jid {
        return address
    }

    /**
     * Sets the address of the JingleNodes.
     *
     * @param address the JID of the JingleNodes
     */
    fun setAddress(address: Jid) {
        this.address = address
    }

    /**
     * Returns if the JID has relay support.
     *
     * @return `true` if relay is supported, `false` otherwise
     */
    fun isRelaySupported(): Boolean {
        return relaySupported
    }

    /**
     * Sets the relay support corresponding to this JID.
     *
     * @param relaySupported relay value to set
     */
    fun setRelay(relaySupported: Boolean) {
        this.relaySupported = relaySupported
    }

    /**
     * Stores this descriptor into the specified [Map].The method is meant for use with
     * account property maps. It also allows prepending an account prefix to all property names so
     * that multiple descriptors can be stored in a single [Map].
     *
     * @param props the account properties [Map] that we'd like to store this descriptor in.
     * namePrefix the prefix that we should prepend to every property name.
     */
    fun storeDescriptor(props: MutableMap<String, String?>, namePrefix_: String?) {
        var namePrefix = namePrefix_
        if (namePrefix == null) namePrefix = JN_PREFIX
        props[namePrefix + JN_ADDRESS] = getJID().toString()
        props[namePrefix + JN_IS_RELAY_SUPPORTED] = java.lang.Boolean.toString(isRelaySupported())
    }

    /**
     * Returns a `String` representation of this descriptor
     *
     * @return a `String` representation of this descriptor.
     */
    override fun toString(): String {
        return "JingleNodesDesc: " + getJID() + " relay:" + isRelaySupported()
    }

    companion object {
        private const val serialVersionUID = 1L

        /**
         * JingleNodes prefix to store configuration.
         */
        const val JN_PREFIX = "JINGLENODES"

        /**
         * JingleNodes prefix to store server address in configuration.
         */
        const val JN_ADDRESS = "ADDRESS"

        /**
         * JingleNodes prefix to store the relay capabilities in configuration.
         */
        const val JN_IS_RELAY_SUPPORTED = "IS_RELAY_SUPPORTED"

        /**
         * The maximum number of stun servers that we would allow.
         */
        const val MAX_JN_RELAY_COUNT = 100

        /**
         * Loads this descriptor from the specified [Map].The method is meant for use with account
         * property maps. It also allows prepending an account prefix to all property names so that
         * multiple descriptors can be read in a single [Map].
         *
         * @param props the account properties [Map] that we'd like to load this descriptor from.
         * @param namePrefix_ the prefix that we should prepend to every property name.
         * @return the newly created descriptor or null if no descriptor was found.
         */
        fun loadDescriptor(props: Map<String, String?>?, namePrefix_: String?): JingleNodeDescriptor? {
            var namePrefix = namePrefix_
            if (namePrefix == null) namePrefix = JN_PREFIX
            val relayAddress = props!![namePrefix + JN_ADDRESS]
                    ?: return null
            val relayStr = props[namePrefix + JN_IS_RELAY_SUPPORTED]
            return try {
                val relay = java.lang.Boolean.parseBoolean(relayStr)
                JingleNodeDescriptor(JidCreate.from(relayAddress), relay)
            } catch (t: Throwable) {
                null
            }
        }
    }
}