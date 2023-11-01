/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.translator

import org.atalk.impl.neomedia.rtp.StreamRTPManager
import java.util.*
import javax.media.Format
import javax.media.rtp.ReceiveStreamListener

/**
 * Describes additional information about a `StreamRTPManager` for the purposes of
 * `RTPTranslatorImpl`.
 *
 * @author Lyubomir Marinov
 */
class StreamRTPManagerDesc
/**
 * Initializes a new `StreamRTPManagerDesc` instance which is to describe a specific
 * `StreamRTPManager`.
 *
 * @param streamRTPManager
 * the `StreamRTPManager` to be described by the new instance
 */
(val streamRTPManager: StreamRTPManager) {
    var connectorDesc: RTPConnectorDesc? = null
    private val formats: MutableMap<Int, Format> = HashMap()

    /**
     * The list of synchronization source (SSRC) identifiers received by [.streamRTPManager]
     * (as `ReceiveStream`s).
     */
    private var receiveSSRCs = EMPTY_INT_ARRAY
    val receiveStreamListeners: MutableList<ReceiveStreamListener?> = LinkedList()
    fun addFormat(format: Format, payloadType: Int) {
        synchronized(formats) { formats.put(payloadType, format) }
    }

    /**
     * Adds a new synchronization source (SSRC) identifier to the list of SSRC received by the
     * associated `StreamRTPManager`.
     *
     * @param receiveSSRC
     * the new SSRC to add to the list of SSRC received by the associated
     * `StreamRTPManager`
     */
    @Synchronized
    fun addReceiveSSRC(receiveSSRC: Int) {
        if (!containsReceiveSSRC(receiveSSRC)) {
            val receiveSSRCCount = receiveSSRCs.size
            val newReceiveSSRCs = IntArray(receiveSSRCCount + 1)
            System.arraycopy(receiveSSRCs, 0, newReceiveSSRCs, 0, receiveSSRCCount)
            newReceiveSSRCs[receiveSSRCCount] = receiveSSRC
            receiveSSRCs = newReceiveSSRCs
        }
    }

    fun addReceiveStreamListener(listener: ReceiveStreamListener?) {
        synchronized(receiveStreamListeners) { if (!receiveStreamListeners.contains(listener)) receiveStreamListeners.add(listener) }
    }

    /**
     * Determines whether the list of synchronization source (SSRC) identifiers received by the
     * associated `StreamRTPManager` contains a specific SSRC.
     *
     * @param receiveSSRC
     * the SSRC to check whether it is contained in the list of SSRC received by the
     * associated `StreamRTPManager`
     * @return `true` if the specified `receiveSSRC` is contained in the list of SSRC
     * received by the associated `StreamRTPManager`; otherwise, `false`
     */
    @Synchronized
    fun containsReceiveSSRC(receiveSSRC: Int): Boolean {
        for (i in receiveSSRCs.indices) {
            if (receiveSSRCs[i] == receiveSSRC) return true
        }
        return false
    }

    fun getFormat(payloadType: Int): Format? {
        synchronized(formats) { return formats[payloadType] }
    }

    fun getFormats(): Array<Format> {
        synchronized(formats) {
            val formats: Collection<Format> = formats.values
            return formats.toTypedArray()
        }
    }

    fun getPayloadType(format: Format): Int? {
        synchronized(formats) {
            for ((key, entryFormat) in formats) {
                if (entryFormat.matches(format) || format.matches(entryFormat)) return key
            }
        }
        return null
    }

    fun getReceiveStreamListeners(): Array<ReceiveStreamListener?> {
        synchronized(receiveStreamListeners) { return receiveStreamListeners.toTypedArray() }
    }

    fun removeReceiveStreamListener(listener: ReceiveStreamListener?) {
        synchronized(receiveStreamListeners) { receiveStreamListeners.remove(listener) }
    }

    companion object {
        /**
         * An array with `int` element type and no elements explicitly defined to reduce
         * unnecessary allocations.
         */
        private val EMPTY_INT_ARRAY = IntArray(0)
    }
}