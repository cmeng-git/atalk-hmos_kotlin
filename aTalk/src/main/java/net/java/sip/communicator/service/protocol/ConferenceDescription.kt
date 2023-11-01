/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * A description of a conference call that can be dialed into. Contains an URI and additional
 * parameters to use.
 *
 * @author Boris Grozev
 */
class ConferenceDescription
/**
 * Creates a new instance with the specified `uri` and `callId`
 *
 * @param uri
 * the `uri` to set.
 * @param callId
 * the `callId` to set.
 */
@JvmOverloads
constructor(
        /**
         * The URI of the conference.
         */
        private var uri: String? = null,
        /**
         * The call ID to use to call into the conference.
         */
        private var callId: String? = null,
        /**
         * The password to use to call into the conference.
         */
        private var password: String? = null) {
    /**
     * The subject of the conference.
     */
    private var subject: String? = null

    /**
     * The name of the conference.
     */
    private var displayName: String? = null

    /**
     * Whether the conference is available or not.
     */
    private var available = true

    /**
     * The transport methods supported for calling into the conference.
     *
     * If the set is empty, the intended interpretation is that it is up to the caller to chose an
     * appropriate transport.
     */
    private val transports = HashSet<String>()
    /**
     * Creates a new instance with the specified `uri`, `callId` and `password`
     * .
     *
     * @param uri
     * the `uri` to set.
     * @param callId
     * the `callId` to set.
     * @param password
     * the `auth` to set.
     */
    /**
     * Creates a new instance.
     */
    /**
     * Creates a new instance with the specified `uri`.
     *
     * @param uri
     * the `uri` to set.
     */
    /**
     * Returns the display name of the conference.
     *
     * @return the display name
     */
    fun getDisplayName(): String? {
        return displayName
    }

    /**
     * Sets the display name of the conference.
     *
     * @param displayName
     * the display name to set
     */
    fun setDisplayName(displayName: String?) {
        this.displayName = displayName
    }

    /**
     * Gets the uri of this `ConferenceDescription`.
     *
     * @return the uri of this `ConferenceDescription`.
     */
    fun getUri(): String? {
        return uri
    }

    /**
     * Sets the uri of this `ConferenceDescription`.
     *
     * @param uri
     * the value to set
     */
    fun setUri(uri: String?) {
        this.uri = uri
    }

    /**
     * Gets the subject of this `ConferenceDescription`.
     *
     * @return the subject of this `ConferenceDescription`.
     */
    fun getSubject(): String? {
        return subject
    }

    /**
     * Sets the subject of this `ConferenceDescription`.
     *
     * @param subject
     * the value to set
     */
    fun setSubject(subject: String?) {
        this.subject = subject
    }

    /**
     * Gets the call ID of this `ConferenceDescription`
     *
     * @return the call ID of this `ConferenceDescription`
     */
    fun getCallId(): String? {
        return callId
    }

    /**
     * Sets the call ID of this `ConferenceDescription`.
     *
     * @param callId
     * the value to set
     */
    fun setCallId(callId: String?) {
        this.callId = callId
    }

    /**
     * Gets the password of this `ConferenceDescription`
     *
     * @return the password of this `ConferenceDescription`
     */
    fun getPassword(): String? {
        return password
    }

    /**
     * Sets the auth of this `ConferenceDescription`.
     *
     * @param password
     * the value to set
     */
    fun setPassword(password: String?) {
        this.password = password
    }

    /**
     * Checks if the conference is available.
     *
     * @return `true` iff the conference is available.
     */
    fun isAvailable(): Boolean {
        return available
    }

    /**
     * Sets the availability of this `ConferenceDescription`.
     *
     * @param available
     * the value to set
     */
    fun setAvailable(available: Boolean) {
        this.available = available
    }

    /**
     * Adds a `Transport` to the set of `Transport`s supported by the conference.
     *
     * @param transport
     * the `Transport` to add.
     */
    fun addTransport(transport: String) {
        transports.add(transport)
    }

    /**
     * Checks whether `transport` is supported by this `ConferenceDescription`. If the
     * set of transports for this `ConferenceDescription` is empty, always returns true.
     *
     * @param transport
     * the `Transport` to check.
     * @return `true` if `transport` is supported by this
     * `ConferenceDescription`
     */
    fun supportsTransport(transport: String): Boolean {
        /*
		 * An empty list means that all transports are supported.
		 */
        return if (transports.isEmpty()) true else transports.contains(transport)
    }

    /**
     * Returns the transports supported by this `ConferenceDescription`
     *
     * @return the supported by this `ConferenceDescription`
     */
    fun getSupportedTransports(): Set<String> {
        return HashSet(transports)
    }

    /**
     * {@inheritDoc}
     */
    override fun toString(): String {
        return "ConferenceDescription(uri=$uri; callid=$callId)"
    }

    /**
     * Checks if two `ConferenceDescription` instances have the same call id, URI and supported transports.
     *
     * cd1 the first `ConferenceDescription` instance.
     * cd2 the second `ConferenceDescription` instance.
     * @return `true` if the `ConferenceDescription` instances have the same call id,
     * URI and supported transports. Otherwise `false` is returned.
     */
    fun compareConferenceDescription(cd: ConferenceDescription): Boolean {
        return getCallId() == cd.getCallId() && getUri() == cd.getUri() && (getSupportedTransports()
                == cd.getSupportedTransports())
    }
}