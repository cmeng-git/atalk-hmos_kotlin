/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media

import org.atalk.service.neomedia.RTPExtension
import java.util.*

/**
 * RFC [RFC 5285] defines a mechanism for attaching multiple extensions to RTP packets. Part of this
 * mechanism consists in negotiating their identifiers using `extmap` attributes pretty much
 * the same way one would negotiate payload types with `rtpmap` attributes.
 *
 *
 * Mappings of extension IDs are handled with SDP. They are created for a particular session and
 * remain the same for its entire lifetime. They may however change in following sessions.
 *
 *
 *
 * We use this class as a utility for easily creating and tracking extension mappings for the
 * lifetime of a particular session. One instance of this registry is supposed to be mapped to one
 * media session and they should have the same life cycle.
 *
 * @author Emil Ivov
 */
class DynamicRTPExtensionsRegistry {
    /**
     * A field that we use to track mapping IDs.
     */
    private var nextExtensionMapping = MIN_HEADER_ID.toByte()

    /**
     * A table mapping `RTPExtension` instances to the dynamically allocated ID they have
     * obtained for the lifetime of this registry.
     */
    private val extMap: MutableMap<RTPExtension, Byte> = Hashtable<RTPExtension, Byte>()

    /**
     * Returns the ID that has been allocated for `extension`. A mapping for the specified
     * `extension` would be created even if it did not previously exist. The method is meant
     * for use primarily during generation of SDP descriptions.
     *
     * @param extension
     * the `RTPExtension` instance that we'd like to obtain a dynamic ID for.
     *
     * @return the (possibly newly allocated) ID corresponding to the specified `extension`
     * and valid for the lifetime of the media session.
     *
     * @throws IllegalStateException
     * if we have already registered more RTP extensions than allowed for by RTP.
     */
    @Throws(IllegalStateException::class)
    fun obtainExtensionMapping(extension: RTPExtension): Byte {
        var extID = extMap[extension]

        // hey, we already had this one, let's return it ;)
        if (extID == null) {
            extID = nextExtensionID()
            extMap[extension] = extID
        }
        return extID
    }

    /**
     * Returns the ID that has been allocated for `extension` or `-1` if no extension
     * exists.
     *
     * @param extension
     * the `RTPExtension` instance whose ID we'd like to find.
     *
     * @return the ID corresponding to the specified `extension` or `-1` if
     * `extension` is not registered with this registry.
     */
    fun getExtensionMapping(extension: RTPExtension): Byte {

        // hey, we already had this one, let's return it ;)
        return extMap[extension] ?: return -1
    }

    /**
     * Adds the specified `extension` to `extID` mapping to the list of mappings known
     * to this registry. The method is meant for use primarily when handling incoming media
     * descriptions, methods generating local SDP should use the `obtainExtensionMapping`
     * instead.
     *
     * @param extID
     * the extension ID that we'd like to allocated to `extension`.
     * @param extension
     * the `RTPExtension` that we'd like to create a dynamic mapping for.
     *
     * @throws IllegalArgumentException
     * in case `extID` has already been assigned to another `RTPExtension`.
     */
    @Throws(IllegalArgumentException::class)
    fun addMapping(extension: RTPExtension, extID: Byte) {
        val alreadyMappedExt: RTPExtension? = findExtension(extID)
        require(alreadyMappedExt == null) {
            (extID.toString() + " has already been allocated to "
                    + alreadyMappedExt)
        }
        require(extID >= MIN_HEADER_ID) {
            (extID.toString() + " is not a valid RTP extensino header ID."
                    + " (must be between " + MIN_HEADER_ID + " and " + MAX_TWO_BYTE_HEADER_ID)
        }
        extMap[extension] = java.lang.Byte.valueOf(extID)
    }

    /**
     * Returns a reference to the `RTPExtension` with the specified mapping or `null`
     * if the number specified by `extID` has not been allocated yet.
     *
     * @param extID
     * the ID whose `RTPExtension` we are trying to discover.
     *
     * @return the `RTPExtension` that has been mapped to `extID` in this registry or
     * `null` if it hasn't been allocated yet.
     */
    fun findExtension(extID: Byte): RTPExtension? {
        for ((key, currentExtensionID) in extMap) {
            if (currentExtensionID == extID) return key
        }
        return null
    }

    /**
     * Returns the first non-allocated dynamic extension ID number.
     *
     * @return the first non-allocated dynamic extension ID number..
     *
     * @throws IllegalStateException
     * if we have already registered more RTP extension headers than allowed for by RTP.
     */
    @Throws(IllegalStateException::class)
    private fun nextExtensionID(): Byte {
        while (true) {
            check(nextExtensionMapping >= 0) { "Impossible to map more than the 255 already mapped " + " RTP extensions" }
            val extID = nextExtensionMapping++
            if (findExtension(extID) == null) return extID

            // if we get here then that means that the number we obtained by
            // incrementing our ID counter was already occupied (probably by an
            // incoming SDP). continue bravely and get the next free one.
        }
    }

    /**
     * Returns a copy of all mappings currently registered in this registry.
     *
     * @return a copy of all mappings currently registered in this registry.
     */
    fun getMappings(): Map<RTPExtension, Byte> {
        return Hashtable<RTPExtension, Byte>(extMap)
    }

    companion object {
        /**
         * The minimum integer that is allowed for use when mapping extensions using the one-byte
         * header.
         */
        const val MIN_HEADER_ID = 1

        /**
         * The maximum integer that is allowed for use when mapping extensions using the one-byte
         * header. Note that 15 is reserved for future use by 5285
         */
        const val MAX_ONE_BYTE_HEADER_ID = 14

        /**
         * The maximum integer that is allowed for use when mapping extensions using the two-byte
         * header.
         */
        const val MAX_TWO_BYTE_HEADER_ID = 255
    }
}