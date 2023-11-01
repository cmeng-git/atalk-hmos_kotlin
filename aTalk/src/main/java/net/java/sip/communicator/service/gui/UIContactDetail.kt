/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui

import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService

/**
 * The `UIContactDetail` corresponds to a particular contact detail,
 * phone number, IM identifier, email, etc. which has it's preferred mode of
 * transport `ProtocolProviderService`.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
abstract class UIContactDetail
/**
 * Creates a `UIContactDetail` by specifying the contact
 * `address`, the `displayName` and `preferredProvider`.
 *
 * @param address the contact address
 * @param displayName the contact display name
 * @param category the category of the underlying contact detail
 * @param labels the collection of labels associated with this detail
 * @param preferredProviders the preferred protocol provider
 * @param preferredProtocols the preferred protocol if no protocol provider is set
 * @param descriptor the underlying object that this class is wrapping
 */
(
        /**
         * The address of this detail.
         */
        address: String,
        /**
         * The display name of this detail.
         */
        val displayName: String?,
        /**
         * The category of the underlying contact detail.
         */
        val category: String?,
        /**
         * The collection of labels associated with this detail.
         */
        private val labels: Collection<String?>?,
        /**
         * The `ProtocolProviderService` corresponding to this detail.
         */
        private var preferredProviders: MutableMap<Class<out OperationSet?>, ProtocolProviderService>?,
        /**
         * The protocol to be used for this contact detail if no protocol provider is set.
         */
        private var preferredProtocols: MutableMap<Class<out OperationSet?>, String>?,
        /**
         * The underlying object that this class is wrapping
         */
        val descriptor: Any?) {
    /**
     * Returns the prefix to be used when calling this contact detail.
     *
     * @return the prefix to be used when calling this contact detail
     */
    /**
     * Sets the prefix to be used when calling this contact detail.
     *
     * @param prefix the prefix to be used when calling this contact detail
     */
    /**
     * The prefix to be used when calling this contact detail.
     */
    var prefix: String? = null

    /**
     * Returns the display name of this detail.
     *
     * @return the display name of this detail
     */

    /**
     * Returns the category of the underlying detail.
     *
     * @return the category of the underlying detail
     */
    /**
     * Returns the underlying object that this class is wrapping
     *
     * @return the underlying object that this class is wrapping
     */

    /**
     * Creates a `UIContactDetail` by specifying the contact
     * `address`, the `displayName` and `preferredProvider`.
     *
     * @param address the contact address
     * @param displayName the contact display name
     * @param descriptor the underlying object that this class is wrapping
     */
    constructor(
            address: String,
            displayName: String,
            descriptor: Any?) : this(address,
            displayName,
            null,
            null,
            null,
            null,
            descriptor) {
    }

    /**
     * The address of this detail.
     */
    val address: String = ""
        get() {
            return if (prefix != null && prefix!!.trim { it <= ' ' }.length >= 0) {
                prefix + field
            } else field
        }

    /**
     * Returns an iterator over the collection of labels associated with this detail.
     *
     * @return an iterator over the collection of labels associated with this detail
     */
    fun getLabels(): Iterator<String?>? {
        return labels?.iterator()
    }

    /**
     * Returns the protocol provider preferred for contacting this detail for
     * the given `OperationSet` class.
     *
     * @param opSetClass the `OperationSet` class for which we're looking for provider
     * @return the protocol provider preferred for contacting this detail
     */
    fun getPreferredProtocolProvider(opSetClass: Class<out OperationSet?>): ProtocolProviderService? {
        return if (preferredProviders != null) preferredProviders!![opSetClass] else null
    }

    /**
     * Adds a preferred protocol provider for a given OperationSet class.
     *
     * @param opSetClass the `OperationSet` class for which we're looking for protocol
     * @param protocolProvider the preferred protocol provider to add
     */
    fun addPreferredProtocolProvider(
            opSetClass: Class<out OperationSet?>, protocolProvider: ProtocolProviderService) {
        if (preferredProviders == null) preferredProviders = HashMap()
        preferredProviders!![opSetClass] = protocolProvider
    }

    /**
     * Returns the name of the protocol preferred for contacting this detail for
     * the given `OperationSet` class if no preferred protocol provider
     * is set.
     *
     * @param opSetClass the `OperationSet` class for which we're looking
     * for protocol
     * @return the name of the protocol preferred for contacting this detail
     */
    fun getPreferredProtocol(opSetClass: Class<out OperationSet?>): String? {
        return if (preferredProtocols != null) preferredProtocols!![opSetClass] else null
    }

    /**
     * Adds a preferred protocol for a given OperationSet class.
     *
     * @param opSetClass the `OperationSet` class for which we're looking for protocol
     * @param protocol the preferred protocol to add
     */
    fun addPreferredProtocol(opSetClass: Class<out OperationSet?>, protocol: String) {
        if (preferredProtocols == null) preferredProtocols = HashMap()
        preferredProtocols!![opSetClass] = protocol
    }

    /**
     * Returns the `PresenceStatus` of this `ContactDetail` or
     * null if the detail doesn't support presence.
     *
     * @return the `PresenceStatus` of this `ContactDetail` or
     * null if the detail doesn't support presence
     */
    abstract val presenceStatus: PresenceStatus?
}