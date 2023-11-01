/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media

import org.atalk.service.neomedia.SrtpControl
import org.atalk.service.neomedia.SrtpControlType
import org.atalk.util.MediaType

/**
 * Represents a sorted set of `SrtpControl` implementations.
 *
 * @author Lyubomir Marinov
 * @author MilanKral
 */
class SrtpControls
/**
 * Initializes a new `SrtpControls` instance.
 */
{
    /**
     * The `SrtpControl` implementations which are the elements of this sorted set.
     */
    private val elements = Array<Array<SrtpControl?>>(MediaType.values().size) {
        arrayOfNulls(SrtpControlType.values().size)
    }

    fun findFirst(mediaType: MediaType): SrtpControl? {
        var element: SrtpControl? = null
        for (srtpControlType in SORTED_SRTP_CONTROL_TYPES) {
            element = get(mediaType, srtpControlType)
            if (element != null) break
        }
        return element
    }

    operator fun get(mediaType: MediaType, srtpControlType: SrtpControlType): SrtpControl? {
        return elements[mediaType.ordinal][srtpControlType.ordinal]
    }

    fun getOrCreate(mediaType: MediaType, srtpControlType: SrtpControlType, myZid: ByteArray?): SrtpControl? {
        val elements = elements[mediaType.ordinal]
        val index = srtpControlType.ordinal
        var element = elements[index]
        if (element == null) {
            element = ProtocolMediaActivator.mediaService!!.createSrtpControl(srtpControlType, myZid)
            if (element != null) elements[index] = element
        }
        return element
    }

    fun remove(mediaType: MediaType, srtpControlType: SrtpControlType): SrtpControl? {
        val elements = elements[mediaType.ordinal]
        val index = srtpControlType.ordinal
        val element = elements[index]
        elements[index] = null
        return element
    }

    operator fun set(mediaType: MediaType, element: SrtpControl?) {
        val srtpControlType = element!!.srtpControlType
        elements[mediaType.ordinal][srtpControlType.ordinal] = element
    }

    companion object {
        private val SORTED_SRTP_CONTROL_TYPES = arrayOf(
                SrtpControlType.ZRTP,
                SrtpControlType.DTLS_SRTP,
                SrtpControlType.MIKEY,
                SrtpControlType.SDES)
    }
}