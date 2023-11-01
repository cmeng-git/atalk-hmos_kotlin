/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.dtmf

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.neomedia.RawPacket
import timber.log.Timber

/**
 * `DtmfRawPacket` represent an RTP Packet. You create your `DtmfRawPacket` by calling
 * the constructor. You specify the DTMF attributes : code=9, end=false, marker=true ... Then you
 * fill the packet using init( ... dtmf attributes ... );
 *
 * @author Romain Philibert
 * @author Emil Ivov
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class DtmfRawPacket : RawPacket, Cloneable {
    /**
     * The event code of the current packet.
     *
     * @return the code
     */
    /**
     * The event code to send.
     */
    var code = 0
        private set
    /**
     * Is this an end packet.
     *
     * @return the end
     */
    /**
     * Is this an end packet.
     */
    var isEnd = false
        private set
    /**
     * The duration of the current event.
     *
     * @return the duration
     */
    /**
     * The duration of the current packet.
     */
    var duration = 0
        private set
    /**
     * The volume of the current event.
     *
     * @return the volume
     */
    /**
     * The volume of the current packet.
     */
    var volume = 0
        private set

    /**
     * Creates a `DtmfRawPacket` using the specified buffer.
     *
     * @param buffer the `byte` array that we should use to store packet content
     * @param offset the index where we should start using the `buffer`.
     * @param length Length of the packet's data.
     * @param payload the payload that has been negotiated for telephone events by our signaling modules.
     */
    constructor(buffer: ByteArray, offset: Int, length: Int, payload: Byte) : super(buffer, offset, length) {
        payloadType = payload
    }

    /**
     * Used for incoming DTMF packets, creating `DtmfRawPacket` from RTP one.
     *
     * @param pkt the RTP packet.
     */
    constructor(pkt: RawPacket?) : super(pkt!!.buffer, pkt.offset, pkt.length) {
        var at = headerLength
        code = readByte(at++).toInt()
        val b = readByte(at++)
        isEnd = b.toInt() and 0x80 != 0
        volume = b.toInt() and 0x7f
        duration = readByte(at++).toInt() and 0xFF shl 8 or (readByte(at++).toInt() and 0xFF)
    }

    /**
     * Initializes DTMF specific values in this packet.
     *
     * @param code the DTMF code representing the digit.
     * @param end the DTMF End flag
     * @param marker the RTP Marker flag
     * @param duration the DTMF duration
     * @param timestamp the RTP timestamp
     * @param volume the DTMF volume
     */
    fun init(code: Int, end: Boolean, marker: Boolean, duration: Int, timestamp: Long, volume: Int) {
        Timber.log(TimberLog.FINER, "DTMF send on RTP, code: %s duration = %s timestamps = %s Marker = %s End = %s",
                code, duration, timestamp, marker, end)

        // Set the marker
        setMarker(marker)

        // set the Timestamp
        super.timestamp = timestamp

        // Clear any RTP header extensions
        removeExtension()

        // Create the RTP data
        setDtmfPayload(code, end, duration, volume)
    }

    /**
     * Initializes the  a DTMF raw data using event, E and duration field.
     * Event : the digits to transmit (0-15).
     * E : End field, used to mark the two last packets.
     * R always = 0.
     * Volume always = 0.
     * Duration : duration increments for each dtmf sending updates,
     * stay unchanged at the end for the 3 last packets.
     * <pre>
     * 0                   1                   2                   3
     * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |     event     |E R| volume    |          duration             |
     * |       ?       |? 0|    0      |              ?                |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    </pre> *
     *
     * @param code the digit to transmit 0-15
     * @param end boolean used to mark the two last packets
     * @param duration int increments for each dtmf sending updates, stay unchanged at the end for the 2 last
     * packets.
     * @param volume describes the power level of the tone, expressed in dBm0
     */
    private fun setDtmfPayload(code: Int, end: Boolean, duration: Int, volume: Int) {
        this.code = code
        isEnd = end
        this.duration = duration
        this.volume = volume
        var at = headerLength
        writeByte(at++, code.toByte())
        writeByte(at++, if (end) (volume or 0x80).toByte() else (volume and 0x7f).toByte())
        writeByte(at++, (duration shr 8).toByte())
        writeByte(at++, duration.toByte())

        // packet finished setting its payload, set correct length
        length = at
    }

    /**
     * Initializes a new `DtmfRawPacket` instance which has the same properties as this instance.
     *
     * @return a new `DtmfRawPacket` instance which has the same properties as this instance
     */
    public override fun clone(): Any {
        val pkt = RawPacket(buffer.clone(), offset, length)
        return DtmfRawPacket(pkt)
    }
}