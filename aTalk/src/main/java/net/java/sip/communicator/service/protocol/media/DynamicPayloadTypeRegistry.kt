/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media

import org.atalk.service.neomedia.format.MediaFormat
import timber.log.Timber

/**
 * The RTP Audio/Video Profile [RFC 3551] specifies a number of static payload types for use with
 * RTP and reserves the 96-127 field for use with dynamic payload types.
 *
 * Mappings of dynamic payload types are handled with SDP. They are created for a particular session
 * and remain the same for its entire lifetime. They may however change in following sessions.
 *
 * We use this class as a utility for easily creating and tracking dynamic payload mappings for the
 * lifetime of a particular session. One instance of this registry is supposed to be mapped to one
 * media session. They should have pretty much the same life cycle.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class DynamicPayloadTypeRegistry {
    /**
     * A field that we use to track dynamic payload numbers that we allocate.
     */
    private var nextDynamicPayloadType = MediaFormat.MIN_DYNAMIC_PAYLOAD_TYPE.toByte()

    /**
     * The mappings of `MediaFormat` instances to the dynamic payload type numbers they have
     * obtained for the lifetime of this registry.
     */
    private val payloadTypeMappings: MutableMap<MediaFormat, Byte> = HashMap()

    /**
     * Maps locally defined payload types to payload type numbers that the remote party wants to
     * use.
     */
    private val payloadTypeOverrides: MutableMap<Byte, Byte> = HashMap()

    /**
     * An override mappings of `MediaFormat` instances to the dynamic payload type numbers.
     */
    private var localPayloadTypePreferences: Map<Byte, String>? = null

    /**
     * Payload types mapping from `MediaService`.
     */
    private var mediaMappings: Map<MediaFormat, Byte>? = null

    /**
     * Sets the override payload type numbers.
     *
     * @param mappings the override payload-type mappings.
     */
    fun setLocalPayloadTypePreferences(mappings: Map<Byte, String>) {
        localPayloadTypePreferences = mappings
    }

    /**
     * Returns the dynamic payload type that has been allocated for `format`. A mapping for
     * the specified `format` would be created even if it did not previously exist. The
     * method is meant for use primarily during generation of SDP descriptions.
     *
     * @param format the `MediaFormat` instance that we'd like to obtain a payload type number for.
     * @return the (possibly newly allocated) payload type number corresponding to the specified
     * `format` instance for the lifetime of the media session.
     * @throws IllegalStateException if we have already registered more dynamic formats than allowed for by RTP.
     */
    @Throws(IllegalStateException::class)
    fun obtainPayloadTypeNumber(format: MediaFormat): Byte {
        var payloadType = getPayloadType(format)

        // seems like we haven't allocated a payload type for this format yet. lets try to do so now.
        if (payloadType == null) {
            // first, let's check whether there's a particular PT number that this format would like to have
            // (e.g. "telephone-event" generally loves to be called "101").
            val preferredPT = getPreferredDynamicPayloadType(format)

            payloadType = if (preferredPT != null && findFormat(preferredPT) == null) {
                // the format has a preference and it's free
                preferredPT
            } else {
                // the format does not have a preferred PT number or it isn't free.
                nextPayloadTypeNumber()
            }
            payloadTypeMappings[format] = payloadType
        }
        return payloadType
    }

    /**
     * Returns the dynamic payload type preferences mappings.
     *
     * @return the dynamic payload type preferences mappings.
     */
    private fun getDynamicPayloadTypePreferences(): Map<MediaFormat, Byte> {
        if (mediaMappings == null) {
            val mappings = ProtocolMediaActivator.mediaService!!.dynamicPayloadTypePreferences

            if (localPayloadTypePreferences == null) return mappings

            // If we have specific payload type preferences from CallPeerMediaHandler, replace them here.
            for ((key, fmt) in localPayloadTypePreferences!!) {
                var saveFmt: MediaFormat? = null
                var saveKey: Byte? = null
                var replaceKey: Byte? = null
                var replaceFmt: MediaFormat? = null

                for ((fmt2, key2) in mappings) {
                    if (fmt2.encoding == fmt) {
                        saveFmt = fmt2
                        saveKey = key2
                        if (replaceKey != null) break
                    }

                    if (key2 == key) {
                        replaceFmt = fmt2
                        replaceKey = key
                        if (saveKey != null) break
                    }
                }

                if (saveFmt != null) {
                    mappings.remove(saveFmt)
                    if (replaceFmt != null) mappings.remove(replaceFmt)
                    mappings[saveFmt] = key
                    if (replaceFmt != null) mappings[replaceFmt] = saveKey!!
                }
            }
            mediaMappings = mappings
        }
        return mediaMappings!!
    }

    /**
     * Returns the payload type number that `format` would like to use if possible and
     * `null` if there is no such preference.
     *
     * @param format the [MediaFormat] whose preferred dynamic PT number we are trying to obtain.
     * @return the payload type number that `format` would like to use if possible and
     * `null` if there is no such preference.
     */
    private fun getPreferredDynamicPayloadType(format: MediaFormat): Byte? {
        val ptPreferences = getDynamicPayloadTypePreferences()
        return getPayloadTypeFromMap(ptPreferences, format)
    }

    /**
     * Adds the specified `format` to `payloadType` mapping to the list of mappings
     * known to this registry. If the mapping already exists in the registry then we will use the
     * new value to create an overriding mapping. This basically means that we will expect packets
     * to be streamed to us with the original PT but we will be streaming with The method is meant
     * for use primarily when handling incoming media descriptions, methods generating local SDP
     * should use the `obtainPayloadTypeNumber` instead.
     *
     * @param payloadType the payload type number that we'd like to allocated to `format`.
     * @param format the `MediaFormat` that we'd like to create a dynamic mapping for.
     * @throws IllegalArgumentException in case `payloadType` has already been assigned to another format.
     */
    @Throws(IllegalArgumentException::class)
    fun addMapping(format: MediaFormat, payloadType: Byte) {
        val alreadyMappedFmt = findFormat(payloadType)
        if (alreadyMappedFmt != null) {
            if (alreadyMappedFmt.matches(format)) {
                // we already have the exact same mapping, so no need to
                // create a new one override the old PT number. However, there
                // might be a leftover override from a previous mapping so let's
                // remove it if this is the case.
                payloadTypeOverrides.remove(payloadType)
                return
            }
            // else:
            // welcome to hackland: the remote party is trying to re-map a
            // payload type we already use. we will try to respect their choice
            // and create an overriding mapping but we also need to make sure
            // that the format itself actually has a PT we can override.
            val newlyObtainedPT = obtainPayloadTypeNumber(format)

            Timber.w("Remote party is trying to remap payload type %s and reassign it"
                    + " from %s to %s. We'll go along but there might be issues because"
                    + " of this. We'll also expect to receive %s with PT = %s",
                    payloadType, alreadyMappedFmt, format, format, newlyObtainedPT)
        }

        require(payloadType >= MediaFormat.MIN_DYNAMIC_PAYLOAD_TYPE) {
            (payloadType.toString()
                    + " is not a valid dynamic payload type number." + " (must be between "
                    + MediaFormat.MIN_DYNAMIC_PAYLOAD_TYPE + " and "
                    + MediaFormat.MAX_DYNAMIC_PAYLOAD_TYPE)
        }

        // if the format is already mapped to a PT then we'll keep it and use
        // the new one as an override value for sending. we'd still expect to
        // receive packets with the value that we had first selected.
        val originalPayloadType = getPayloadType(format)
        if (originalPayloadType != null && originalPayloadType != payloadType) {
            payloadTypeOverrides[originalPayloadType] = payloadType
        } else {
            // we are just adding a new mapping. nothing out of the ordinary
            payloadTypeMappings[format] = payloadType
        }
    }

    /**
     * Return s a reference to the `MediaFormat` with the specified mapping or `null`
     * if the number specified by `payloadType` has not been allocated yet.
     *
     * @param payloadType the number of the payload type that we are trying to get a format for.
     * @return the `MediaFormat` that has been mapped to `payloadType` in this
     * registry or `null` if it hasn't been allocated yet.
     */
    private fun findFormat(payloadType: Byte): MediaFormat? {
        for ((key, fmtPayloadType) in payloadTypeMappings) {
            if (fmtPayloadType == payloadType) return key
        }
        return null
    }

    /**
     * Returns the first non-allocated dynamic payload type number.
     *
     * @return the first non-allocated dynamic payload type number.
     * @throws IllegalStateException if we have already registered more dynamic formats than allowed for by RTP.
     */
    @Throws(IllegalStateException::class)
    private fun nextPayloadTypeNumber(): Byte {
        while (true) {
            check(nextDynamicPayloadType >= 0) {
                ("Impossible to allocate more than the already 32 mapped dynamic payload type numbers")
            }

            val payloadType = nextDynamicPayloadType++
            if (findFormat(payloadType) == null
                    && findFormatWithPreference(payloadType) == null)
                return payloadType

            /*
             * If we get here, then that means that the number we obtained by incrementing our PT
             * counter was already occupied (probably by an incoming SDP). Continue bravely and get
             * the next free one.
             */
        }
    }

    /**
     * Returns the [MediaFormat] with the specified `payloadTypePreference` or
     * `null` if no [MediaFormat] has claimed this payload type number as preferred.
     *
     * @param payloadTypePreference the dynamic payload type number that we are trying to determine as being claimed as
     * preferred or not by a media format.
     * @return the [MediaFormat] with the null `payloadTypePreference` or `null`
     * if no [MediaFormat] has claimed this payload type number as preferred.
     */
    private fun findFormatWithPreference(payloadTypePreference: Byte): MediaFormat? {
        for ((key, value) in getDynamicPayloadTypePreferences()) {
            if (value == payloadTypePreference)
                return key
        }
        return null
    }

    /**
     * Returns a copy of all mappings currently registered in this registry.
     *
     * @return a copy of all mappings currently registered in this registry.
     */
    fun getMappings(): Map<MediaFormat, Byte> {
        return HashMap(payloadTypeMappings)
    }

    /**
     * Returns a copy of all mapping overrides currently registered in this registry.
     *
     * @return a copy of all mapping overrides currently registered in this registry.
     */
    fun getMappingOverrides(): Map<Byte, Byte> {
        return HashMap(payloadTypeOverrides)
    }

    /**
     * Returns the payload type that is currently mapped to `format` or `null` if
     * there is currently no such payload type.
     *
     * @param format the [MediaFormat] whose mapping we are looking for
     * @return the payload type that is currently mapped to `format` or `null` if
     * there is currently no such payload type.
     */
    fun getPayloadType(format: MediaFormat): Byte? {
        return getPayloadTypeFromMap(payloadTypeMappings, format)
    }

    companion object {
        /**
         * Iterates through `formatMap` and returns the payload type that it maps to
         * `format` or `null` if there is currently no such payload type.
         *
         * @param format the [MediaFormat] whose mapping we are looking for
         * @return the payload type that is currently mapped to `format` or `null` if
         * there is currently no such payload type.
         */
        private fun getPayloadTypeFromMap(formatMap: Map<MediaFormat, Byte>, format: MediaFormat): Byte? {
            for ((key, value) in formatMap) {
                if (key.matches(format)) {
                    return value
                }
            }
            return null
        }
    }
}