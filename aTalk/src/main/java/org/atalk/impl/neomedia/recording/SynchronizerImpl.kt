/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.recording

import org.atalk.service.neomedia.RawPacket
import org.atalk.service.neomedia.recording.Synchronizer
import org.atalk.util.RTPUtils.rtpTimestampDiff
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong

/**
 * @author Boris Grozev
 */
class SynchronizerImpl : Synchronizer {
    /**
     * Maps an SSRC to the `SSRCDesc` structure containing information
     * about it.
     */
    private val ssrcs = ConcurrentHashMap<Long, SSRCDesc>()

    /**
     * Maps an endpoint identifier to an `Endpoint` structure containing
     * information about the endpoint.
     */
    private val endpoints = ConcurrentHashMap<String?, Endpoint>()

    /**
     * {@inheritDoc}
     */
    override fun setRtpClockRate(ssrc: Long, clockRate: Long) {
        val ssrcDesc = getSSRCDesc(ssrc)
        if (ssrcDesc.clockRate == -1L) {
            synchronized(ssrcDesc) {
                if (ssrcDesc.clockRate == -1L) ssrcDesc.clockRate = clockRate else if (ssrcDesc.clockRate != clockRate) {
                    // this shouldn't happen...but if the clock rate really
                    // changed for some reason, out timings are now irrelevant.
                    ssrcDesc.clockRate = clockRate
                    ssrcDesc.ntpTime = -1.0
                    ssrcDesc.rtpTime = -1
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun setEndpoint(ssrc: Long, endpointId: String?) {
        val ssrcDesc = getSSRCDesc(ssrc)
        synchronized(ssrcDesc) { ssrcDesc.endpointId = endpointId }
    }

    /**
     * {@inheritDoc}
     */
    override fun mapRtpToNtp(ssrc: Long, rtpTime: Long, ntpTime: Double) {
        val ssrcDesc = getSSRCDesc(ssrc)
        if (rtpTime != -1L && ntpTime != -1.0) // have valid values to update
        {
            if (ssrcDesc.rtpTime == -1L || ssrcDesc.ntpTime == -1.0) {
                synchronized(ssrcDesc) {
                    if (ssrcDesc.rtpTime == -1L || ssrcDesc.ntpTime == -1.0) {
                        ssrcDesc.rtpTime = rtpTime
                        ssrcDesc.ntpTime = ntpTime
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun mapLocalToNtp(ssrc: Long, localTime: Long, ntpTime: Double) {
        val ssrcDesc = getSSRCDesc(ssrc)
        if (localTime != -1L && ntpTime != -1.0 && ssrcDesc.endpointId != null) {
            val endpoint = getEndpoint(ssrcDesc.endpointId)
            if (endpoint.localTime == -1L || endpoint.ntpTime == -1.0) {
                synchronized(endpoint) {
                    if (endpoint.localTime == -1L || endpoint.ntpTime == -1.0) {
                        endpoint.localTime = localTime
                        endpoint.ntpTime = ntpTime
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun getLocalTime(ssrc: Long, rtpTime: Long): Long {
        // don't use getSSRCDesc, because we don't want to create an instance
        val ssrcDesc = ssrcs[ssrc] ?: return -1

        // get all required times
        var clockRate: Long // the clock rate for the RTP clock for the given SSRC
        var rtp1: Long // some time X in the RTP clock for the given SSRC
        var ntp1: Double // the same time X in the source's wallclock
        var endpointId: String?
        synchronized(ssrcDesc) {
            clockRate = ssrcDesc.clockRate
            rtp1 = ssrcDesc.rtpTime
            ntp1 = ssrcDesc.ntpTime
            endpointId = ssrcDesc.endpointId
        }

        // if something is missing, we can't calculate the time
        if (clockRate == -1L || rtp1 == -1L || ntp1 == -1.0 || endpointId == null) {
            return -1
        }
        val endpoint = endpoints[ssrcDesc.endpointId] ?: return -1
        var ntp2: Double // some time Y in the source's wallclock (same clock as for
        // ntp1)
        var local2: Long // the same time Y in the local clock
        synchronized(endpoint) {
            ntp2 = endpoint.ntpTime
            local2 = endpoint.localTime
        }
        if (ntp2 == -1.0 || local2 == -1L) {
            return -1
        }

        // crunch the numbers. we're looking for 'local0',
        // the local time corresponding to 'rtp0'
        val local0: Long
        val diff1S = ntp1 - ntp2
        val diff2S = rtpTimestampDiff(rtpTime, rtp1).toDouble() / clockRate
        val diffMs = ((diff1S + diff2S) * 1000).roundToLong()
        local0 = local2 + diffMs
        return local0
    }

    /**
     * Adds an RTCP packet to this instance. Time mappings are extracted and used by this instance.
     *
     * @param pkt the packet to add.
     * @param localTime the local time of reception of the packet.
     */
    @JvmOverloads
    fun addRTCPPacket(pkt: RawPacket?, localTime: Long = System.currentTimeMillis()) {
        if (!isValidRTCP(pkt)) {
            return
        }
        when (getPacketType(pkt)) {
            PT_SENDER_REPORT -> addSR(pkt, localTime)
            PT_SDES -> addSDES(pkt)
        }
    }

    /**
     * Handles and RTCP SDES packet.
     *
     * @param pkt
     * the packet to handle.
     */
    private fun addSDES(pkt: RawPacket?) {
        if (USE_CNAME_AS_ENDPOINT_ID) {
            for (item in getCnameItems(pkt)) {
                val ssrc = getSSRCDesc(item.ssrc)
                if (ssrc.endpointId == null) {
                    synchronized(ssrc) { if (ssrc.endpointId == null) ssrc.endpointId = item.cname }
                }
            }
        }
    }

    /**
     * Handles an RTCP Sender Report packet.
     *
     * @param pkt
     * the packet to handle.
     * @param localTime
     * the local time of reception of the packet.
     */
    private fun addSR(pkt: RawPacket?, localTime: Long) {
        val ssrc = pkt!!.rtcpSSRC
        val rtpTime = pkt.readUint32AsLong(16)
        val sec = pkt.readUint32AsLong(8)
        val fract = pkt.readUint32AsLong(12)
        val ntpTime = sec + fract.toDouble() / (1L shl 32)
        if (localTime != -1L && ntpTime != -1.0) mapLocalToNtp(ssrc, localTime, ntpTime)
        if (rtpTime != -1L && ntpTime != -1.0) mapRtpToNtp(ssrc, rtpTime, ntpTime)
    }

    /**
     * Returns the `SSRCDesc` instance mapped to the SSRC `ssrc`. If no instance is
     * mapped to `ssrc`, create one and inserts it in the map. Always returns non-null.
     *
     * @param ssrc
     * the ssrc to get the `SSRCDesc` for.
     * @return the `SSRCDesc` instance mapped to the SSRC `ssrc`.
     */
    private fun getSSRCDesc(ssrc: Long): SSRCDesc {
        synchronized(ssrcs) {
            var ssrcDesc = ssrcs[ssrc]
            if (ssrcDesc == null) {
                ssrcDesc = SSRCDesc()
                ssrcs[ssrc] = ssrcDesc
            }
            return ssrcDesc
        }
    }

    /**
     * Returns the `Endpoint` with id `endpointId`. Creates an `Endpoint` if
     * necessary. Always returns non-null.
     *
     * @param endpointId
     * the string identifying the endpoint.
     * @return the `Endpoint` with id `endpointId`. Creates an `Endpoint` if
     * necessary.
     */
    private fun getEndpoint(endpointId: String?): Endpoint {
        synchronized(endpoints) {
            var endpoint = endpoints[endpointId]
            if (endpoint == null) {
                endpoint = Endpoint()
                endpoints[endpointId] = endpoint
            }
            return endpoint
        }
    }

    /**
     * Return a set of all items with type CNAME from the RTCP SDES packet `pkt`.
     *
     * @param pkt
     * the packet to parse for CNAME items.
     * @retur a set of all items with type CNAME from the RTCP SDES packet `pkt`.
     */
    private fun getCnameItems(pkt: RawPacket?): Set<CNAMEItem> {
        val ret = HashSet<CNAMEItem>()
        val buf = pkt!!.buffer
        val off = pkt.offset
        val len = pkt.length

        // first item
        var ptr = 4
        while (ptr + 6 < len) // an item is at least 6B: 4B ssrc, 1B type, 1B len
        {
            val type = buf[off + ptr + 4].toInt()
            val len2 = buf[off + ptr + 5].toInt()
            if (ptr + 6 + len2 >= len) // not enough buffer for the whole item
                break
            if (type == 1) // CNAME
            {
                val item = CNAMEItem()
                item.ssrc = readUnsignedIntAsLong(buf, off + ptr)
                item.cname = readString(buf, off + ptr + 6, len2)
                ret.add(item)
            }
            ptr += 6 + len2
        }
        return ret
    }

    /**
     * Reads a portion of a byte array as a string.
     *
     * @return the string with length `len`read from `buf` at offset `off`.
     */
    private fun readString(buf: ByteArray?, off: Int, len: Int): String {
        var ret = ""
        for (i in off until off + len) {
            ret += Char(buf!![i].toUShort())
        }
        return ret
    }

    /**
     * Read an unsigned integer as long at specified offset
     *
     * @param off
     * start offset of this unsigned integer
     * @return unsigned integer as long at offset
     */
    private fun readUnsignedIntAsLong(buf: ByteArray?, off: Int): Long {
        val b0 = 0x000000FF and buf!![off + 0].toInt()
        val b1 = 0x000000FF and buf[off + 1].toInt()
        val b2 = 0x000000FF and buf[off + 2].toInt()
        val b3 = 0x000000FF and buf[off + 3].toInt()
        return (b0 shl 24 or (b1 shl 16) or (b2 shl 8) or b3).toLong() and 0xFFFFFFFFL
    }

    /**
     * Checks whether `pkt` looks like a valid RTCP packet.
     *
     * @param pkt
     * the packet to check.
     * @return `true` if `pkt` seems to be a valid RTCP packet.
     */
    private fun isValidRTCP(pkt: RawPacket?): Boolean {
        val buf = pkt!!.buffer
        val off = pkt.offset
        val len = pkt.length
        if (len < 4) return false
        val v = buf[off].toInt() and 0xc0 ushr 6
        if (v != 2) return false
        val lengthInWords = buf[off + 2].toInt() and 0xFF shl 8 or (buf[off + 3].toInt() and 0xFF)
        val lengthInBytes = (lengthInWords + 1) * 4
        return len >= lengthInBytes
    }

    /**
     * Returns the value of the packet type field from the RTCP header of `pkt`. Assumes that
     * `pkt` is a valid RTCP packet (at least as reported by [.isValidRTCP]).
     *
     * @param pkt
     * the packet to get the packet type of.
     * @return the value of the packet type field from the RTCP header of `pkt`.
     */
    private fun getPacketType(pkt: RawPacket?): Int {
        return pkt!!.buffer[pkt.offset + 1].toInt() and 0xff
    }

    /**
     * Removes the RTP-NTP mapping for a given SSRC.
     *
     * @param ssrc
     * the SSRC for which to remove the RTP-NTP mapping
     */
    fun removeMapping(ssrc: Long) {
        val ssrcDesc = ssrcs[ssrc]
        if (ssrcDesc != null) {
            synchronized(ssrcDesc) {
                ssrcDesc.ntpTime = -1.0
                ssrcDesc.rtpTime = -1
            }
        }
    }

    /**
     * Represents an SSRC for the purpose of this `Synchronizer`.
     */
    private class SSRCDesc {
        /**
         * The string identifying the endpoint associated with this SSRC.
         */
        var endpointId: String? = null

        /**
         * The RTP clock rate for this SSRC.
         */
        var clockRate = -1L
        var ntpTime = -1.0
        var rtpTime = -1L
    }

    /**
     * A class used to identify an "endpoint" or "source". Contains a mapping between a wallclock at
     * the endpoint and a time we chose on the local system clock to correcpond to it.
     */
    private class Endpoint {
        /**
         * The time in seconds on the "endpoint"'s clock.
         */
        var ntpTime = -1.0

        /**
         * The local time.
         */
        var localTime = -1L
    }

    /**
     * Represents an item of type CNAME from an RTCP SDES packet.
     */
    private class CNAMEItem {
        /**
         * The SSRC of the item.
         */
        var ssrc = 0L

        /**
         * The CNAME value.
         */
        var cname: String? = null
    }

    companion object {
        /**
         * Whether the CNAME identifier from RTCP SDES packets should be use as an endpoint identifier.
         *
         * If set to true, RTCP SDES packets fed through [.addRTCPPacket] will be
         * searched for CNAME items, and the values will be used as endpoint identifiers.
         */
        private const val USE_CNAME_AS_ENDPOINT_ID = false

        /**
         * The RTCP Sender Report packet type.
         */
        private const val PT_SENDER_REPORT = 200

        /**
         * The RTCP SDES packet type.
         */
        private const val PT_SDES = 202
    }
}