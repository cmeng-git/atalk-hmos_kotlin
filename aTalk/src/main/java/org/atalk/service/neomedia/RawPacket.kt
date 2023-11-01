/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.service.neomedia

import net.sf.fmj.media.rtp.RTPHeader
import org.atalk.util.ByteArrayBuffer
import org.atalk.util.RTPUtils.readInt
import org.atalk.util.RTPUtils.readUint16AsInt
import org.atalk.util.RTPUtils.readUint32AsLong
import org.atalk.util.RTPUtils.writeInt
import org.atalk.util.RTPUtils.writeShort

/**
 * When using TransformConnector, a RTP/RTCP packet is represented using
 * RawPacket. RawPacket stores the buffer holding the RTP/RTCP packet, as well
 * as the inner offset and length of RTP/RTCP packet data.
 *
 * After transformation, data is also store in RawPacket objects, either the
 * original RawPacket (in place transformation), or a newly created RawPacket.
 *
 * Besides packet info storage, RawPacket also provides some other operations
 * such as readInt() to ease the development process.
 *
 * FIXME This class needs to be split/merged into RtpHeader, RTCPHeader, ByteBufferUtils, etc.
 *
 * @author Werner Dittmann (Werner.Dittmann@t-online.de)
 * @author Bing SU (nova.su@gmail.com)
 * @author Emil Ivov
 * @author Damian Minkov
 * @author Boris Grozev
 * @author Lyubomir Marinov
 * @author George Politis
 * @author Eng Chong Meng
 */
open class RawPacket : ByteArrayBuffer {
    /**
     * Byte array storing the content of this Packet
     * Note that if this instance changes, then [.headerExtensions] MUST be reinitialized.
     * It is best to use [.setBuffer] instead of accessing this field directly.
     */
    final override var buffer = ByteArray(0)
        set(buffer) {
            field = buffer
            headerExtensions = HeaderExtensions()
        }

    /**
     * The bitmap/flag mask that specifies the set of boolean attributes enabled
     * for this `RawPacket`. The value is the logical sum of all of the
     * set flags. The possible flags are defined by the `FLAG_XXX`
     * constants of FMJ's [javax.media.Buffer] class.
     */
    var flags = 0

    /**
     * Length of this packet's data
     */
    final override var length = 0

    /**
     * Start offset of the packet data inside buffer.
     * Usually this value would be 0. But in order to be compatible with
     * RTPManager we store this info. (Not assuming the offset is always zero)
     */
    final override var offset = 0

    /**
     * A [HeaderExtensions] instance, used to iterate over the RTP header extensions of this [RawPacket].
     */
    private var headerExtensions: HeaderExtensions?

    /**
     * A flag to skip packet statistics for this packet.
     */
    var isSkipStats = false

    /**
     * Initializes a new empty <tt>RawPacket</tt> instance.
     */
    constructor() {
        headerExtensions = null
    }

    /**
     * Initializes a new `RawPacket` instance with a specific `byte` array buffer.
     *
     * @param buffer the `byte` array to be the buffer of the new instance
     * @param offset the offset in `buffer` at which the actual data to
     * be represented by the new instance starts
     * @param length the number of `byte`s in `buffer` which
     * constitute the actual data to be represented by the new instance
     */
    constructor(buffer: ByteArray, offset: Int, length: Int) {
        this.buffer = buffer
        this.offset = offset
        this.length = length
        headerExtensions = HeaderExtensions()
    }

    /**
     * Adds the given buffer as a header extension of this packet
     * according the rules specified in RFC 5285. Note that this method does
     * not replace extensions so if you add the same buffer twice it would be
     * added as a separate extension.
     *
     * This method MUST NOT be called while iterating over the extensions using
     * [.getHeaderExtensions], or while manipulating the state of this [RawPacket].
     *
     * @param id the ID with which to add the extension.
     * @param data the buffer containing the extension data.
     * @param len the length of the extension.
     */
    @JvmOverloads
    fun addExtension(id: Byte, data: ByteArray?, len: Int = data!!.size) {
        require((data == null || len < 1 || len > 16 || data.size >= len)) {
            ("id=" + id + " data.length="
                    + (data?.size ?: "null")
                    + " len=" + len)
        }
        val he = addExtension(id, len)
        System.arraycopy(data!!, 0, he.buffer, he.offset + 1, len)
    }

    /**
     * Adds an RTP header extension with a given ID and a given length to this
     * packet. The contents of the extension are not set to anything, and the
     * caller of this method is responsible for filling them in.
     *
     * This method MUST NOT be called while iterating over the extensions using
     * [.getHeaderExtensions], or while manipulating the state of this
     * [RawPacket].
     *
     * @param id the ID of the extension to add.
     * @param len the length in bytes of the extension to add.
     * @return the header extension which was added.
     */
    fun addExtension(id: Byte, len: Int): HeaderExtension {
        require((id < 1 || id > 15 || len < 1 || len <= 16)) { "id=$id len=$len" }

        // The byte[] of a RawPacket has the following structure:
        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        // | A: unused | B: hdr + ext | C: payload | D: unused |
        // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        // And the regions have the following sizes:
        // A: this.offset
        // B: this.getHeaderLength()
        // C: this.getPayloadLength()
        // D: this.buffer.length - this.length - this.offset
        // We will try to extend the packet so that it uses A and/or D if
        // possible, in order to avoid allocating new memory.

        // We get this early, before we modify the buffer.
        val payloadLength = payloadLength
        val extensionBit = extensionBit
        val extHeaderOffset = FIXED_HEADER_SIZE + 4 * csrcCount


        // This is an upper bound on the required length for the packet after
        // the addition of the new extension. It is easier to calculate than
        // the exact number, and is relatively close (it may be off by a few
        // bytes due to padding)
        val maxRequiredLength = (length
                + (if (extensionBit) 0 else EXT_HEADER_SIZE)
                + 1 /* the 1-byte header of the extension element */
                + len
                + 3) /* padding */
        val newBuffer: ByteArray?
        val newPayloadOffset: Int
        if (buffer.size >= maxRequiredLength) {
            // We don't need a new buffer.
            newBuffer = buffer
            if (offset + headerLength >= maxRequiredLength - this.payloadLength) {
                // If region A (see above) is enough to accommodate the new
                // packet, then keep the payload where it is.
                newPayloadOffset = payloadOffset
            } else {
                // Otherwise, we have to use region D. To do so, move the
                // payload to the right.
                newPayloadOffset = buffer.size - payloadLength
                System.arraycopy(buffer, payloadOffset,
                        buffer, newPayloadOffset,
                        payloadLength)
            }
        } else {
            // We need a new buffer. We will place the payload to the very right.
            newBuffer = ByteArray(maxRequiredLength)
            newPayloadOffset = newBuffer.size - payloadLength
            System.arraycopy(buffer, payloadOffset,
                    newBuffer, newPayloadOffset,
                    payloadLength)
        }

        // By now we have the payload in a position which leaves enough space
        // for the whole new header.
        // Next, we are going to construct a new header + extensions (including
        // the one we are adding) at offset 0, and once finished, we will move
        // them to the correct offset.
        var newHeaderLength = extHeaderOffset
        // The bytes in the header extensions, excluding the (0xBEDE, length)
        // field and any padding.
        var extensionBytes = 0
        if (extensionBit) {
            // (0xBEDE, length)
            newHeaderLength += 4

            // We can't find the actual length without an iteration because
            // of padding. It is safe to iterate, because we have not yet
            // modified the header (we only might have moved the offset right)
            val hes = getHeaderExtensions()!!
            while (hes.hasNext()) {
                val he = hes.next()
                // 1 byte for id/len + data
                extensionBytes += 1 + he.extLength
            }
            newHeaderLength += extensionBytes
        }

        // Copy the header (and extensions, excluding padding, if there are any)
        System.arraycopy(buffer, offset,
                newBuffer, 0,
                newHeaderLength)
        if (!extensionBit) {
            // If the original packet didn't have any extensions, we need to
            // add the extension header (RFC 5285):
            //  0                   1                   2                   3
            //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
            // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            // |       0xBE    |    0xDE       |           length              |
            // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            writeShort(newBuffer, extHeaderOffset, 0xBEDE.toShort())
            // We will set the length field later.
            newHeaderLength += 4
        }

        // Finally we get to add our extension.
        newBuffer[newHeaderLength++] = (id.toInt() and 0x0f shl 4 or (len - 1 and 0x0f)).toByte()
        extensionBytes++

        // This is where the data of the extension that we add begins. We just
        // skip 'len' bytes, and let the caller fill them in. We have to go
        // back one byte, because newHeaderLength already moved.
        val extensionDataOffset = newHeaderLength - 1
        newHeaderLength += len
        extensionBytes += len
        val paddingBytes = 4 - extensionBytes % 4 % 4
        for (i in 0 until paddingBytes) {
            // Set the padding to 0 (we have to do this because we may be
            // reusing a buffer).
            newBuffer[newHeaderLength++] = 0
        }
        writeShort(newBuffer, extHeaderOffset + 2, ((extensionBytes + paddingBytes) / 4).toShort())

        // Now we have the new header, with the added header extension and with
        // the correct padding, in newBuffer at offset 0. Lets move it to the
        // correct place (right before the payload).
        val newOffset = newPayloadOffset - newHeaderLength
        if (newOffset != 0) {
            System.arraycopy(newBuffer, 0,
                    newBuffer, newOffset,
                    newHeaderLength)
        }

        // All that is left to do is update the RawPacket state.
        buffer = newBuffer
        offset = newOffset
        length = newHeaderLength + payloadLength

        // ... and set the extension bit.
        this.extensionBit = true

        // Setup the single HeaderExtension instance of this RawPacket and
        // return it.
        val he = getHeaderExtensions()!!.headerExtension
        he.offset = offset + extensionDataOffset
        he.length = len + 1
        return he
    }

    /**
     * Append a byte array to the end of the packet. This may change the data
     * buffer of this packet.
     *
     * @param data byte array to append
     * @param len the number of bytes to append
     */
    override fun append(data: ByteArray?, len: Int) {
        if (data == null || len == 0) {
            return
        }

        // Ensure the internal buffer is long enough to accommodate data. (The
        // method grow will re-allocate the internal buffer if it's too short.)
        grow(len)
        // Append data.
        System.arraycopy(data, 0, buffer, length + offset, len)
        length += len
    }

    /**
     * Returns a map binding CSRC IDs to audio levels as reported by the remote
     * party that sent this packet.
     *
     *buffer csrcExtID the ID of the extension that's transporting csrc audio
     * levels in the session that this `RawPacket` belongs to.
     * @return an array representing a map binding CSRC IDs to audio levels as
     * reported by the remote party that sent this packet. The entries of the
     * map are contained in consecutive elements of the returned array where
     * elements at even indices stand for CSRC IDs and elements at odd indices
     * stand for the associated audio levels
     */
    fun extractCsrcAudioLevels(csrcExtID: Byte): LongArray? {
        if (!extensionBit || extensionLength == 0) return null
        val csrcCount = csrcCount
        if (csrcCount == 0) return null

        /*
         * XXX The guideline which is also supported by Google and recommended
         * for Android is that single-dimensional arrays should be preferred to
         * multi-dimensional arrays in Java because the former take less space
         * than the latter and are thus more efficient in terms of memory and
         * garbage collection.
         */
        val csrcLevels = LongArray(csrcCount * 2)

        //first extract the csrc IDs
        var i = 0
        var csrcStartIndex = offset + FIXED_HEADER_SIZE
        while (i < csrcCount) {
            val csrcLevelsIndex = 2 * i
            csrcLevels[csrcLevelsIndex] = readUint32AsLong(csrcStartIndex)
            /*
             * The audio levels generated by Jitsi are not in accord with the
             * respective specification, they are backwards with respect to the
             * value domain. Which means that the audio level generated from a
             * muted audio source is 0/zero.
             */
            csrcLevels[csrcLevelsIndex + 1] = getCsrcAudioLevel(csrcExtID, i, 0.toByte()).toLong()
            i++
            csrcStartIndex += 4
        }
        return csrcLevels
    }

    /**
     * Returns the list of CSRC IDs, currently encapsulated in this packet.
     *
     * @return an array containing the list of CSRC IDs, currently encapsulated in this packet.
     */
    fun extractCsrcList(): LongArray {
        val csrcCount = csrcCount
        val csrcList = LongArray(csrcCount)
        var i = 0
        var csrcStartIndex = offset + FIXED_HEADER_SIZE
        while (i < csrcCount) {
            csrcList[i] = readInt(csrcStartIndex).toLong()
            i++
            csrcStartIndex += 4
        }
        return csrcList
    }

    /**
     * Extracts the source audio level reported by the remote party which sent
     * this packet and carried in this packet.
     *
     *buffer ssrcExtID the ID of the extension that's transporting ssrc audio
     * levels in the session that this `RawPacket` belongs to
     * @return the source audio level reported by the remote party which sent
     * this packet and carried in this packet or a negative value if this packet
     * contains no extension such as the specified by `ssrcExtID`
     */
    fun extractSsrcAudioLevel(ssrcExtID: Byte): Byte {
        /*
         * The method getCsrcAudioLevel(byte, int) is implemented with the
         * awareness that there may be a flag bit V with a value other than 0.
         */
        /*
         * The audio levels sent by Google Chrome are in accord with the
         * specification i.e. the audio level generated from a muted audio
         * source is 127 and the values are non-negative. If there is no source
         * audio level in this packet, return a negative value.
         */
        return getCsrcAudioLevel(ssrcExtID, 0, Byte.MIN_VALUE)
    }

    /**
     * Returns the index of the element in this packet's buffer where the
     * content of the header with the specified `extensionID` starts.
     *
     * @param extensionID the ID of the extension whose content we are looking for.
     * @return the index of the first byte of the content of the extension
     * with the specified `extensionID` or -1 if no such extension was found.
     */
    private fun findExtension(extensionID: Int): Int {
        if (!extensionBit || extensionLength == 0) return 0
        var extOffset = offset + FIXED_HEADER_SIZE + csrcCount * 4 + EXT_HEADER_SIZE
        val extensionEnd = extOffset + extensionLength
        val extHdrLen = extensionHeaderLength
        if (extHdrLen != 1 && extHdrLen != 2) {
            return -1
        }
        while (extOffset < extensionEnd) {
            var currType: Int
            var currLen: Int
            if (extHdrLen == 1) {
                //short header. type is in the lefter 4 bits and length is on
                //the right; like this:
                //      0
                //      0 1 2 3 4 5 6 7
                //      +-+-+-+-+-+-+-+-+
                //      |  ID   |  len  |
                //      +-+-+-+-+-+-+-+-+
                currType = buffer[extOffset].toInt() shr 4
                currLen = (buffer[extOffset].toInt() and 0x0F) + 1 //add one as per 5285

                //now skip the header
                extOffset++
            } else {
                //long header. type is in the first byte and length is in the
                //second
                //       0                   1
                //       0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
                //      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                //      |       ID      |     length    |
                //      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                currType = buffer[extOffset].toInt()
                currLen = buffer[extOffset + 1].toInt()

                //now skip the header
                extOffset += 2
            }
            if (currType == extensionID) {
                return extOffset
            }
            extOffset += currLen
        }
        return -1
    }

    /**
     * Returns the CSRC level at the specified index or `defaultValue`
     * if there was no level at that index.
     *
     * csrcExtID the ID of the extension that's transporting csrc audio
     * levels in the session that this `RawPacket` belongs to.
     * index the sequence number of the CSRC audio level extension to
     * return.
     * @return the CSRC audio level at the specified index of the csrc audio
     * level option or `0` if there was no level at that index.
     */
    private fun getCsrcAudioLevel(csrcExtID: Byte, index: Int, defaultValue: Byte): Byte {
        var level = defaultValue
        try {
            if (extensionBit && extensionLength != 0) {
                val levelsStart = findExtension(csrcExtID.toInt())
                if (levelsStart != -1) {
                    val levelsCount = getLengthForExtension(levelsStart)
                    if (levelsCount < index) {
                        //apparently the remote side sent more CSRCs than levels.
                        // ... yeah remote sides do that now and then ...
                    } else {
                        level = (0x7F and buffer[levelsStart + index].toInt()).toByte()
                    }
                }
            }
        } catch (e: ArrayIndexOutOfBoundsException) {
            // While ideally we should check the bounds everywhere and not
            // attempt to access the packet's buffer at invalid indexes, there
            // are too many places where it could inadvertently happen. It's
            // safer to return the default value than to risk killing a thread
            // which may not expect this.
            level = defaultValue
        }
        return level
    }

    /**
     * Returns the number of CSRC identifiers currently included in this packet.
     *
     * @return the CSRC count for this `RawPacket`.
     */
    private val csrcCount: Int
        get() = getCsrcCount(buffer, offset, length)

    /**
     * Raises the extension bit of this packet is `extBit` is `true`
     * or set it to `0` if `extBit` is `false`.
     *
     * extBit the flag that indicates whether we are to set or clear
     * the extension bit of this packet.
     */
    var extensionBit: Boolean
        get() = getExtensionBit(buffer, offset, length)
        private set(extBit) {
            if (extBit) buffer[offset] = (buffer[offset].toInt() or 0x10).toByte() else buffer[offset] = (buffer[offset].toInt() and 0xEF).toByte()
        }

    //the type of the extension header comes right after the RTP header and the CSRC list.
    //0xBEDE means short extension header.

    //0x100 means a two-byte extension header.
    /**
     * Returns the length of the extension header being used in this packet or
     * `-1` in case there were no extension headers here or we didn't
     * understand the kind of extension being used.
     *
     * @return the length of the extension header being used in this packet or
     * `-1` in case there were no extension headers here or we didn't
     * understand the kind of extension being used.
     */
    private val extensionHeaderLength: Int
        get() {
            if (!extensionBit) return -1

            //the type of the extension header comes right after the RTP header and
            //the CSRC list.
            val extLenIndex = offset + FIXED_HEADER_SIZE + csrcCount * 4

            //0xBEDE means short extension header.
            if (buffer[extLenIndex] == 0xBE.toByte()
                    && buffer[extLenIndex + 1] == 0xDE.toByte()) return 1

            //0x100 means a two-byte extension header.
            return if (buffer[extLenIndex] == 0x10.toByte()
                    && buffer[extLenIndex + 1].toInt() shr 4 == 0) 2 else -1
        }

    /**
     * Returns the length of the extensions currently added to this packet.
     *
     * @return the length of the extensions currently added to this packet.
     */
    val extensionLength: Int
        get() = getExtensionLength(buffer, offset, length)

    /**
     * @return the iterator over this [RawPacket]'s RTP header extensions.
     */
    private fun getHeaderExtensions(): HeaderExtensions? {
        if (headerExtensions == null) {
            headerExtensions = HeaderExtensions()
        }
        headerExtensions!!.reset()
        return headerExtensions
    }

    /**
     * Return the define by profile part of the extension header.
     *
     * @return the starting two bytes of extension header.
     */
    val headerExtensionType: Int
        get() = if (!extensionBit) 0 else readUint16AsInt(offset + FIXED_HEADER_SIZE + csrcCount * 4)

    /**
     * Get RTP header length from a RTP packet
     *
     * @return RTP header length from source RTP packet
     */
    val headerLength: Int
        get() = getHeaderLength(buffer, offset, length)

    /**
     * Returns the length of the header extension that is carrying the content
     * starting at `contentStart`. In other words this method checks the
     * size of extension headers in this packet and then either returns the
     * value of the byte right before `contentStart` or its lower 4 bits.
     * This is a very basic method so if you are using it - make sure u know
     * what you are doing.
     *
     * @param contentStart the index of the first element of the content of
     * the extension whose size we are trying to obtain.
     * @return the length of the extension carrying the content starting at
     * `contentStart`.
     */
    private fun getLengthForExtension(contentStart: Int): Int {
        val hdrLen = extensionHeaderLength
        return if (hdrLen == 1) (buffer[contentStart - 1].toInt() and 0x0F) + 1 else buffer[contentStart - 1].toInt()
    }

    /**
     * Gets the value of the "version" field of an RTP packet.
     *
     * @return the value of the RTP "version" field.
     */
    val version: Int
        get() = getVersion(buffer, offset, length)

    /**
     * Get RTP padding size from a RTP packet
     *
     * @return RTP padding size from source RTP packet
     */
    val paddingSize: Int
        get() = getPaddingSize(buffer, offset, length)// FIXME The payload includes the padding at the end. Do we really want
    // it though? We are currently keeping the implementation as it is for
    // compatibility with existing code.
    /**
     * Get the RTP payload (bytes) of this RTP packet.
     *
     * @return an array of `byte`s which represents the RTP payload of
     * this RTP packet
     */
    val payload: ByteArray?
        get() =// FIXME The payload includes the padding at the end. Do we really want
        // it though? We are currently keeping the implementation as it is for
                // compatibility with existing code.
            readRegion(headerLength, payloadLength)

    /**
     * Get RTP payload length from a RTP packet
     *
     * @return RTP payload length from source RTP packet
     */
    fun getPayloadLength(removePadding: Boolean): Int {
        return getPayloadLength(buffer, offset, length, removePadding)
    }

    /**
     * Get RTP payload length from a RTP packet
     *
     * @return RTP payload length from source RTP packet
     */
    val payloadLength: Int
        get() = getPayloadLength(buffer, offset, length)

    /**
     * Get the RTP payload offset of an RTP packet.
     *
     * @return the RTP payload offset of an RTP packet.
     */
    val payloadOffset: Int
        get() = getPayloadOffset(buffer, offset, length)
    /**
     * Get RTP payload type from a RTP packet
     *
     * @return RTP payload type of source RTP packet
     */
    //this is supposed to be a 7bit payload so make sure that the leftmost
    //bit is 0 so that we don't accidentally overwrite the marker.
    /**
     * Sets the payload type of this packet.
     *
     * payload the RTP payload type describing the content of this packet.
     */
    open var payloadType: Byte
        get() = getPayloadType(buffer, offset, length).toByte()
        set(payload_) {
            //this is supposed to be a 7bit payload so make sure that the leftmost
            //bit is 0 so that we don't accidentally overwrite the marker.
            var payload = payload_
            payload = (payload.toInt() and 0x7F.toByte().toInt()).toByte()
            buffer[offset + 1] = (buffer[offset + 1].toInt() and 0x80 or payload.toInt()).toByte()
        }

    /**
     * Get RTCP SSRC from a RTCP packet
     *
     * @return RTP SSRC from source RTP packet
     */
    val rtcpSSRC: Long
        get() = getRTCPSSRC(this)

    /**
     * Gets the packet type of this RTCP packet.
     *
     * @return the packet type of this RTCP packet.
     */
    val rtcpPacketType: Int
        get() = 0xff and buffer[offset + 1].toInt()
    /**
     * Get RTP sequence number from a RTP packet
     *
     * @return RTP sequence num from source packet
     */
    /**
     * Set the RTP sequence number of an RTP packet
     *
     * seq the sequence number to set (only the least-significant 16bits are used)
     */
    var sequenceNumber: Int
        get() = getSequenceNumber(buffer, offset, length)
        set(seq) {
            setSequenceNumber(buffer, offset, seq)
        }

    /**
     * Get SRTCP sequence number from a SRTCP packet
     *
     * @param authTagLen authentication tag length
     * @return SRTCP sequence num from source packet
     */
    fun getSRTCPIndex(authTagLen: Int): Int {
        return getSRTCPIndex(this, authTagLen)
    }

    /**
     * Get RTP SSRC from a RTP packet
     *
     * @return RTP SSRC from source RTP packet
     */
    open fun getSSRC(): Int {
        return getSSRC(buffer, offset, length)
    }

    /**
     * Returns a `long` representation of the SSRC of this RTP packet.
     *
     * @return a `long` representation of the SSRC of this RTP packet.
     */
    fun getSSRCAsLong(): Long {
        return getSSRCAsLong(buffer, offset, length)
    }

    /**
     * Returns the timestamp for this RTP `RawPacket`.
     *
     * @return the timestamp for this RTP `RawPacket`.
     */
    /**
     * Set the timestamp value of the RTP Packet
     *
     * timestamp : the RTP Timestamp
     */
    var timestamp
        get() = getTimestamp(buffer, offset, length)
        set(timestamp) {
            setTimestamp(buffer, offset, length, timestamp)
        }

    /**
     * Grows the internal buffer of this `RawPacket`.
     *
     * This will change the data buffer of this packet but not the length of the
     * valid data. Use this to grow the internal buffer to avoid buffer
     * re-allocations when appending data.
     *
     * @param howMuch the number of bytes by which this `RawPacket` is to grow
     */
    override fun grow(howMuch: Int) {
        require(howMuch >= 0) { "howMuch" }
        val newLength = length + howMuch
        if (newLength > buffer.size - offset) {
            val newBuffer = ByteArray(newLength)
            System.arraycopy(buffer, offset, newBuffer, 0, length)
            offset = 0
            buffer = newBuffer
        }
    }

    /**
     * Perform checks on the packet represented by this instance and
     * return `true` if it is found to be invalid. A return value of
     * `false` does not necessarily mean that the packet is valid.
     *
     * @return `true` if the RTP/RTCP packet represented by this
     * instance is found to be invalid, `false` otherwise.
     */
    override val isInvalid: Boolean
        get() = isInvalid(buffer, offset, length)

    /**
     * Test whether the RTP Marker bit is set
     *
     * @return whether the RTP Marker bit is set
     */
    val isPacketMarked: Boolean
        get() = isPacketMarked(buffer, offset, length)

    /**
     * Read a byte from this packet at specified offset
     *
     * @param off start offset of the byte
     * @return byte at offset
     */
    fun readByte(off: Int): Byte {
        return buffer[offset + off]
    }

    /**
     * Read a integer from this packet at specified offset
     *
     * @param off start offset of the integer to be read
     * @return the integer to be read
     */
    fun readInt(off: Int): Int {
        return readInt(buffer, offset + off)
    }

    /**
     * Read a 32-bit unsigned integer from this packet at the specified offset.
     *
     * @param off start offset of the integer to be read.
     * @return the integer to be read
     */
    fun readUint32AsLong(off: Int): Long {
        return readUint32AsLong(buffer, offset + off)
    }

    /**
     * Read a byte region from specified offset with specified length
     *
     * @param off start offset of the region to be read
     * @param len length of the region to be read
     * @return byte array of [offset, offset + length)
     */
    fun readRegion(off: Int, len: Int): ByteArray? {
        val startOffset = offset + off
        if (off < 0 || len <= 0 || startOffset + len > buffer.size) return null
        val region = ByteArray(len)
        System.arraycopy(buffer, startOffset, region, 0, len)
        return region
    }

    /**
     * Read a byte region from specified offset with specified length in given buffer
     *
     * @param off start offset of the region to be read
     * @param len length of the region to be read
     * @param outBuf output buffer
     */
    override fun readRegionToBuff(off: Int, len: Int, outBuf: ByteArray?) {
        val startOffset = offset + off
        if (off < 0 || len <= 0 || startOffset + len > buffer.size) return
        if (outBuf!!.size < len) return
        System.arraycopy(buffer, startOffset, outBuf, 0, len)
    }

    /**
     * Write a short to this packet at the specified offset.
     *
     * @param off
     * @param val
     */
    fun writeShort(off: Int, `val`: Short) {
        writeShort(buffer, offset + off, `val`)
    }

    /**
     * Read an unsigned short at specified offset as a int
     *
     * @param off start offset of the unsigned short
     * @return the int value of the unsigned short at offset
     */
    fun readUint16AsInt(off: Int): Int {
        return readUint16AsInt(buffer, offset + off)
    }

    /**
     * Removes the extension from the packet and its header.
     */
    fun removeExtension() {
        if (!extensionBit) return
        val payloadOffset = offset + headerLength
        val extHeaderLen = extensionLength + EXT_HEADER_SIZE
        System.arraycopy(buffer, payloadOffset, buffer, payloadOffset - extHeaderLen, payloadLength)
        length -= extHeaderLen
        extensionBit = false
    }

    /**
     * Replaces the existing CSRC list (even if empty) with `newCsrcList`
     * and updates the CC (CSRC count) field of this `RawPacket` accordingly.
     *
     * newCsrcList the list of CSRC identifiers that we'd like to set for
     * this `RawPacket`.
     */
    fun setCsrcList(newCsrcList: LongArray) {
        val newCsrcCount = newCsrcList.size
        val csrcBuff = ByteArray(newCsrcCount * 4)
        var csrcOffset = 0
        for (csrc in newCsrcList) {
            writeInt(csrcBuff, csrcOffset, csrc.toInt())
            csrcOffset += 4
        }
        val oldCsrcCount = csrcCount
        val oldBuffer = buffer

        //the new buffer needs to be bigger than the new one in order to
        //accommodate the list of CSRC IDs (unless there were more of them
        //previously than after setting the new list).
        val newBuffer = ByteArray(length + offset + csrcBuff.size - oldCsrcCount * 4)

        //copy the part up to the CSRC list
        System.arraycopy(oldBuffer, 0, newBuffer, 0, offset + FIXED_HEADER_SIZE)

        //copy the new CSRC list
        System.arraycopy(csrcBuff, 0, newBuffer, offset + FIXED_HEADER_SIZE, csrcBuff.size)

        //now copy the payload from the old buff and make sure we don't copy
        //the CSRC list if there was one in the old packet
        val payloadOffsetForOldBuff = offset + FIXED_HEADER_SIZE + oldCsrcCount * 4
        val payloadOffsetForNewBuff = offset + FIXED_HEADER_SIZE + newCsrcCount * 4
        System.arraycopy(oldBuffer, payloadOffsetForOldBuff,
                newBuffer, payloadOffsetForNewBuff,
                length - payloadOffsetForOldBuff)

        //set the new CSRC count
        newBuffer[offset] = (newBuffer[offset].toInt() and 0xF0 or newCsrcCount).toByte()
        buffer = newBuffer
        length = payloadOffsetForNewBuff + length - payloadOffsetForOldBuff - offset
    }

    /**
     * Sets or resets the marker bit of this packet according to the `marker` parameter.
     *
     * @param marker `true` if we are to raise the marker bit and `false` otherwise.
     */
    fun setMarker(marker: Boolean) {
        if (marker) {
            buffer[offset + 1] = (buffer[offset + 1].toInt() or 0x80.toByte().toInt()).toByte()
        } else {
            buffer[offset + 1] = (buffer[offset + 1].toInt() and 0x7F.toByte().toInt()).toByte()
        }
    }

    /**
     * Set the SSRC of this packet
     *
     * @param ssrc SSRC to set
     */
    fun setSSRC(ssrc: Int) {
        writeInt(8, ssrc)
    }

    /**
     * Shrink the buffer of this packet by specified length
     *
     * @param len length to shrink
     */
    override fun shrink(len: Int) {
        if (len <= 0) return
        length -= len
        if (length < 0) length = 0
    }

    /**
     * Write a byte to this packet at specified offset
     *
     * @param off start offset of the byte
     * @param b byte to write
     */
    fun writeByte(off: Int, b: Byte) {
        buffer[offset + off] = b
    }

    /**
     * Set an integer at specified offset in network order.
     *
     * @param off Offset into the buffer
     * @param data The integer to store in the packet
     */
    fun writeInt(off: Int, data: Int) {
        writeInt(buffer, offset + off, data)
    }
    /**
     * Gets the OSN value of an RTX packet.
     *
     * @return the OSN value of an RTX packet.
     */
    /**
     * Sets the OSN value of an RTX packet.
     *
     * sequenceNumber the new OSN value of this RTX packet.
     */
    var originalSequenceNumber: Int
        get() = readUint16AsInt(buffer, offset + headerLength)
        set(sequenceNumber) {
            writeShort(headerLength, sequenceNumber.toShort())
        }

    /**
     * Sets the padding length for this RTP packet.
     *
     * @param len the padding length.
     * @return the number of bytes that were written, or -1 in case of an error.
     */
    fun setPaddingSize(len: Int): Boolean {
        if (buffer == null || buffer.size < offset + FIXED_HEADER_SIZE + len || len < 0 || len > 0xFF) {
            return false
        }

        // Set the padding bit.
        buffer[offset] = (buffer[offset].toInt() or 0x20).toByte()
        buffer[offset + length - 1] = len.toByte()
        return true
    }

    /**
     * Sets the RTP version in this RTP packet.
     *
     * @return the number of bytes that were written, or -1 in case of an error.
     */
    fun setVersion(): Boolean {
        if (isInvalid) {
            return false
        }
        buffer[offset] = (buffer[offset].toInt() or 0x80).toByte()
        return true
    }

    /**
     * {@inheritDoc}
     */
    override fun toString(): String {
        // Note: this will not print meaningful values unless the packet is an
        // RTP packet.
        val sb = StringBuilder("RawPacket[off=").append(offset)
                .append(", len=").append(length)
                .append(", PT=").append(payloadType.toInt())
                .append(", SSRC=").append(getSSRCAsLong())
                .append(", seq=").append(sequenceNumber)
                .append(", M=").append(isPacketMarked)
                .append(", X=").append(extensionBit)
                .append(", TS=").append(timestamp)
                .append(", hdrLen=").append(headerLength)
                .append(", payloadLen=").append(payloadLength)
                .append(", paddingLen=").append(paddingSize)
                .append(", extLen=").append(extensionLength)
                .append(']')
        return sb.toString()
    }

    /**
     * @param id the byte id
     * @return the header extension of this [RawPacket] with the given ID,
     * or null if the packet doesn't have one.
     * WARNING: This method should not be used while iterating over the
     * extensions with [.getHeaderExtensions], because it uses the same
     * iterator.
     */
    fun getHeaderExtension(id: Byte): HeaderExtension? {
        val hes = getHeaderExtensions()!!
        while (hes.hasNext()) {
            val he = hes.next()
            if (he.extId == id.toInt()) {
                return he
            }
        }
        return null
    }

    /**
     * Represents an RTP header extension with the RFC5285 one-byte header:
     * <pre>`0
     * 0 1 2 3 4 5 6 7
     * +-+-+-+-+-+-+-+-+
     * |  ID   |  len  |
     * +-+-+-+-+-+-+-+-+
    `</pre> *
     */
    inner class HeaderExtension internal constructor() : ByteArrayBufferImpl(buffer, 0, 0) {
        /**
         * @return the ID field of this extension.
         */
        val extId: Int
            get() = if (super.length <= 0) -1 else buffer[super.offset].toInt() and 0xf0 ushr 4// "The 4-bit length is the number minus one of data bytes of this
        // header extension element following the one-byte header.
        // Therefore, the value zero in this field indicates that one byte
        // of data follows, and a value of 15 (the maximum) indicates
        // element data of 16 bytes."
        /**
         * @return the number of bytes of data in this header extension.
         */
        val extLength: Int
            get() =// "The 4-bit length is the number minus one of data bytes of this
            // header extension element following the one-byte header.
            // Therefore, the value zero in this field indicates that one byte
            // of data follows, and a value of 15 (the maximum) indicates
                    // element data of 16 bytes."
                (buffer[super.offset].toInt() and 0x0f) + 1
    }

    /**
     * Implements an iterator over the RTP header extensions of a
     * [RawPacket].
     */
    inner class HeaderExtensions : Iterator<HeaderExtension> {
        /**
         * The offset of the next extension.
         */
        private var nextOff = 0

        /**
         * The remaining length of the extensions headers.
         */
        private var remainingLen = 0

        /**
         * The single [HeaderExtension] instance which will be updates with each iteration.
         */
        val headerExtension = HeaderExtension()

        /**
         * Resets the iterator to the beginning of the header extensions of the
         * [RawPacket].
         */
        fun reset() {
            val len = extensionLength
            if (len <= 0) {
                // No extensions.
                nextOff = -1
                remainingLen = -1
                return
            }
            nextOff = (offset
                    + FIXED_HEADER_SIZE) + getCsrcCount(buffer, offset, length) * 4 + EXT_HEADER_SIZE
            remainingLen = len
        }

        /**
         * {@inheritDoc}
         *
         * Returns true if this {@RawPacket} contains another header extension.
         */
        override fun hasNext(): Boolean {
            if (remainingLen <= 0 || nextOff < 0) {
                return false
            }
            val len = getExtLength(buffer, nextOff, remainingLen)
            return len > 0
        }

        /**
         * @return the length in bytes of an RTP header extension with an
         * RFC5285 one-byte header. This is slightly different from
         * [HeaderExtension.extLength] in that it includes the header byte and checks the boundaries.
         */
        private fun getExtLength(buf: ByteArray?, off: Int, len: Int): Int {
            if (len <= 2) {
                return -1
            }

            // len=0 indicates 1 byte of data; add 1 more byte for the id/len field itself.
            val extLen = (buf!![off].toInt() and 0x0f) + 2
            return if (extLen > len) {
                -1
            } else extLen
        }

        /**
         * @return the next header extension of this [RawPacket]. Note
         * that it reuses the same object and only update its state.
         */
        override fun next(): HeaderExtension {
            // Prepare this.headerExtension
            val extLen = getExtLength(buffer, nextOff, remainingLen)
            check(extLen > 0) { "Invalid extension length. Did hasNext() return true?" }
            headerExtension.setOffsetLength(nextOff, extLen)

            // Advance "next"
            nextOff += extLen
            remainingLen -= extLen
            return headerExtension
        }
    }

    companion object {
        /**
         * The size of the extension header as defined by RFC 3550.
         */
        const val EXT_HEADER_SIZE = 4

        /**
         * The size of the fixed part of the RTP header as defined by RFC 3550.
         */
        const val FIXED_HEADER_SIZE = 12

        /**
         * The minimum size in bytes of a valid RTCP packet. An empty Receiver Report is 8 bytes long.
         */
        private const val RTCP_MIN_SIZE = 8

        /**
         * The bitmask for the RTP sequence number field.
         */
        const val SEQUENCE_NUMBER_MASK = 0xffff

        /**
         * The bitmask for the RTP timestamp field.
         */
        const val TIMESTAMP_MASK = 0xFFFFFFFFL

        /**
         * Makes a new RTP `RawPacket` filled with padding with the specified
         * parameters. Note that because we're creating a packet filled with
         * padding, the length must not exceed 12 + 0xFF.
         *
         * ssrc the SSRC of the RTP packet to make.
         * @param pt the payload type of the RTP packet to make.
         * @param seqNum the sequence number of the RTP packet to make.
         * @param ts the RTP timestamp of the RTP packet to make.
         * @param len the length of the RTP packet to make.
         * @return the RTP `RawPacket` that was created.
         */
        fun makeRTP(ssrc: Long, pt: Int, seqNum: Int, ts: Long, len: Int): RawPacket {
            val buf = ByteArray(len)
            val pkt = RawPacket(buf, 0, buf.size)

            pkt.setVersion()
            pkt.payloadType = pt.toByte()
            pkt.setSSRC(ssrc.toInt())
            pkt.timestamp = ts
            pkt.sequenceNumber = seqNum
            pkt.setPaddingSize(len - FIXED_HEADER_SIZE)
            return pkt
        }

        /**
         * Gets the value of the "version" field of an RTP packet.
         *
         * @return the value of the RTP "version" field.
         */
        fun getVersion(baf: ByteArrayBuffer?): Int {
            return if (baf == null) {
                -1
            } else getVersion(baf.buffer, baf.offset, baf.length)
        }

        /**
         * Gets the value of the "version" field of an RTP packet.
         *
         * @return the value of the RTP "version" field.
         */
        fun getVersion(buffer: ByteArray, offset: Int, length: Int): Int {
            return buffer[offset].toInt() and 0xC0 ushr 6
        }

        /**
         * Test whether the RTP Marker bit is set
         *
         * @param baf byte array buffer
         * @return true if the RTP Marker bit is set, false otherwise.
         */
        fun isPacketMarked(baf: ByteArrayBuffer?): Boolean {
            return if (baf == null) {
                false
            } else isPacketMarked(baf.buffer, baf.offset, baf.length)
        }

        /**
         * Test whether the RTP Marker bit is set
         *
         * @param buffer the `byte` array that holds the RTP packet.
         * @param offset the offset in `buffer` at which the actual RTP data begins.
         * @param length the number of `byte`s in `buffer` which
         * @return true if the RTP Marker bit is set, false otherwise.
         */
        fun isPacketMarked(buffer: ByteArray?, offset: Int, length: Int): Boolean {
            return if (buffer == null || buffer.size < offset + length || length < 2) {
                false
            } else buffer[offset + 1].toInt() and 0x80 != 0
        }

        /**
         * Perform checks on the packet represented by this instance and
         * return `true` if it is found to be invalid. A return value of
         * `false` does not necessarily mean that the packet is valid.
         *
         * @return `true` if the RTP/RTCP packet represented by this
         * instance is found to be invalid, `false` otherwise.
         */
        fun isInvalid(buffer: ByteArray?, offset: Int, length: Int): Boolean {
            // RTP packets are at least 12 bytes long, RTCP packets can be 8.
            if (buffer == null || buffer.size < offset + length || length < RTCP_MIN_SIZE) {
                return true
            }
            val pt = buffer[offset + 1].toInt() and 0xff
            return if (pt < 200 || pt > 211) {
                // This is an RTP packet.
                length < FIXED_HEADER_SIZE
            } else false
        }

        /**
         * Get RTCP SSRC from a RTCP packet
         *
         * @return RTP SSRC from source RTP packet in a `long`.
         */
        fun getRTCPSSRC(baf: ByteArrayBuffer?): Long {
            return if (baf == null || baf.isInvalid) {
                -1
            } else getRTCPSSRC(baf.buffer, baf.offset, baf.length)
        }

        /**
         * Get RTCP SSRC from a RTCP packet
         *
         * @return RTP SSRC from source RTP packet
         */
        fun getRTCPSSRC(buf: ByteArray?, off: Int, len: Int): Long {
            return if (buf == null || buf.size < off + len || len < 8) {
                -1
            } else readUint32AsLong(buf, off + 4)
        }

        /**
         * Checks whether the RTP/RTCP header is valid or not (note that a valid
         * header does not necessarily imply a valid packet). It does so by checking
         * the RTP/RTCP header version and makes sure the buffer is at least 8 bytes
         * long for RTCP and 12 bytes long for RTP.
         *
         * @param buf the byte buffer that contains the RTCP header.
         * @param off the offset in the byte buffer where the RTCP header starts.
         * @param len the number of bytes in buffer which constitute the actual
         * data.
         * @return true if the RTP/RTCP packet is valid, false otherwise.
         */
        fun isRtpRtcp(buf: ByteArray, off: Int, len: Int): Boolean {
            if (isInvalid(buf, off, len)) {
                return false
            }
            val version = getVersion(buf, off, len)
            return version == RTPHeader.VERSION
        }

        /**
         * Returns the number of CSRC identifiers currently included in this packet.
         *
         * @param buffer the `byte` array that holds the RTP packet.
         * @param offset the offset in `buffer` at which the actual RTP data begins.
         * @param length the number of `byte`s in `buffer` which
         * @return the CSRC count for this `RawPacket`.
         */
        fun getCsrcCount(buffer: ByteArray, offset: Int, length: Int): Int {
            var cc = buffer[offset].toInt() and 0x0f
            if (FIXED_HEADER_SIZE + cc * 4 > length) cc = 0
            return cc
        }

        /**
         * Returns `true` if the extension bit of this packet has been set and `false` otherwise.
         *
         * @param buffer the `byte` array that holds the RTP packet.
         * @param offset the offset in `buffer` at which the actual RTP data begins.
         * @param length the number of `byte`s in `buffer` which
         * @return `true` if the extension bit of this packet has been set and `false` otherwise.
         */
        fun getExtensionBit(buffer: ByteArray, offset: Int, length: Int): Boolean {
            return buffer[offset].toInt() and 0x10 == 0x10
        }

        /**
         * Returns the length of the extensions currently added to this packet.
         *
         * @param buffer the `byte` array that holds the RTP packet.
         * @param offset the offset in `buffer` at which the actual RTP data begins.
         * @param length the number of `byte`s in `buffer` which
         * @return the length of the extensions currently added to this packet.
         */
        fun getExtensionLength(buffer: ByteArray, offset: Int, length: Int): Int {
            if (!getExtensionBit(buffer, offset, length)) return 0

            // TODO should we verify the "defined by profile" field here (0xBEDE)?

            // The extension length comes after the RTP header, the CSRC list, and
            // two bytes in the extension header called "defined by profile".
            val extLenIndex = offset + FIXED_HEADER_SIZE + getCsrcCount(buffer, offset, length) * 4 + 2
            var len = ((buffer[extLenIndex].toInt() shl 8 or (buffer[extLenIndex + 1].toInt() and 0xFF))
                    * 4)
            if (len < 0 || len > length - FIXED_HEADER_SIZE - EXT_HEADER_SIZE - getCsrcCount(buffer, offset, length) * 4) {
                // This is not a valid length. Together with the rest of the
                // header it exceeds the packet length. So be safe and assume
                // that there is no extension.
                len = 0
            }
            return len
        }

        /**
         * Get RTP header length from a RTP packet
         *
         * @param buffer the `byte` array that holds the RTP packet.
         * @param offset the offset in `buffer` at which the actual RTP data begins.
         * @param length the number of `byte`s in `buffer` which
         * @return RTP header length from source RTP packet
         */
        fun getHeaderLength(buffer: ByteArray, offset: Int, length: Int): Int {
            var headerLength = FIXED_HEADER_SIZE + 4 * getCsrcCount(buffer, offset, length)

            // Make sure that the header length doesn't exceed the packet length.
            if (headerLength > length) {
                headerLength = length
            }
            if (getExtensionBit(buffer, offset, length)) {
                // Make sure that the header length doesn't exceed the packet
                // length.
                if (headerLength + EXT_HEADER_SIZE <= length) {
                    headerLength += (EXT_HEADER_SIZE
                            + getExtensionLength(buffer, offset, length))
                }
            }
            return headerLength
        }

        /**
         * Get RTP padding size from a RTP packet
         *
         * @return RTP padding size from source RTP packet
         */
        fun getPaddingSize(buf: ByteArray, off: Int, len: Int): Int {
            return if (buf[off].toInt() and 0x20 == 0) {
                0
            } else {
                // The last octet of the padding contains a count of how many
                // padding octets should be ignored, including itself.

                // XXX It's an 8-bit unsigned number.
                0xFF and buf[off + len - 1].toInt()
            }
        }

        /**
         * Get RTP payload length from a RTP packet
         *
         * @param buffer the `byte` array that holds the RTP packet.
         * @param offset the offset in `buffer` at which the actual RTP data begins.
         * @param length the number of `byte`s in `buffer` which
         * @return RTP payload length from source RTP packet
         */
        fun getPayloadLength(buffer: ByteArray, offset: Int, length: Int): Int {
            return getPayloadLength(buffer, offset, length, false)
        }

        /**
         * Get RTP payload length from a RTP packet
         *
         * @param buffer the `byte` array that holds the RTP packet.
         * @param offset the offset in `buffer` at which the actual RTP data begins.
         * @param length the number of `byte`s in `buffer` which
         * @param removePadding remove padding
         * @return RTP payload length from source RTP packet
         */
        fun getPayloadLength(
                buffer: ByteArray, offset: Int, length: Int, removePadding: Boolean,
        ): Int {
            val lenHeader = getHeaderLength(buffer, offset, length)
            if (lenHeader < 0) {
                return -1
            }
            var len = length - lenHeader
            if (removePadding) {
                val szPadding = getPaddingSize(buffer, offset, length)
                if (szPadding < 0) {
                    return -1
                }
                len -= szPadding
            }
            return len
        }

        /**
         * Get the RTP payload offset of an RTP packet.
         *
         * @param buffer the `byte` array that holds the RTP packet.
         * @param offset the offset in `buffer` at which the actual RTP data begins.
         * @param length the number of `byte`s in `buffer` which
         * @return the RTP payload offset of an RTP packet.
         */
        fun getPayloadOffset(buffer: ByteArray, offset: Int, length: Int): Int {
            return offset + getHeaderLength(buffer, offset, length)
        }

        /**
         * Get RTP payload type from a RTP packet
         *
         * @return RTP payload type of source RTP packet, or -1 in case of an error.
         */
        fun getPayloadType(buf: ByteArray?, off: Int, len: Int): Int {
            return if (buf == null || buf.size < off + len || len < 2) {
                -1
            } else buf[off + 1].toInt() and 0x7F
        }

        /**
         * Get RTP payload type from a RTP packet
         *
         * @return RTP payload type of source RTP packet, or -1 in case of an error.
         */
        fun getPayloadType(pkt: RawPacket?): Int {
            return if (pkt == null) {
                -1
            } else getPayloadType(pkt.buffer, pkt.offset, pkt.length)
        }

        /**
         * Get RTP sequence number from a RTP packet
         *
         * @param buffer the `byte` array that holds the RTP packet.
         * @param offset the offset in `buffer` at which the actual RTP data begins.
         * @param length the number of `byte`s in `buffer` which
         * @return RTP sequence num from source packet
         */
        fun getSequenceNumber(buffer: ByteArray, offset: Int, length: Int): Int {
            return readUint16AsInt(buffer, offset + 2)
        }

        /**
         * Gets the RTP sequence number from a RTP packet.
         *
         * @param baf the [ByteArrayBuffer] that contains the RTP packet.
         * @return the RTP sequence number from a RTP packet.
         */
        fun getSequenceNumber(baf: ByteArrayBuffer?): Int {
            return if (baf == null) {
                -1
            } else getSequenceNumber(baf.buffer, baf.offset, baf.length)
        }

        /**
         * Set sequence number for an RTP buffer
         *
         * @param buffer the `byte` array that holds the RTP packet.
         * @param offset the offset in `buffer` at which the actual RTP data begins.
         * @param seq the buffer sequence number
         */
        fun setSequenceNumber(buffer: ByteArray, offset: Int, seq: Int) {
            writeShort(buffer, offset + 2, seq.toShort())
        }

        /**
         * Sets the sequence number of an RTP packet.
         *
         * @param baf the [ByteArrayBuffer] that contains the RTP packet.
         * @param dstSeqNum the sequence number to set in the RTP packet.
         */
        fun setSequenceNumber(baf: ByteArrayBuffer?, dstSeqNum: Int) {
            if (baf == null) {
                return
            }
            setSequenceNumber(baf.buffer, baf.offset, dstSeqNum)
        }

        /**
         * Set the RTP timestamp for an RTP buffer.
         *
         * @param buf the `byte` array that holds the RTP packet.
         * @param off the offset in `buffer` at which the actual RTP data begins.
         * @param len the number of `byte`s in `buffer` which
         * constitute the actual RTP data.
         * @param ts the timestamp to set in the RTP buffer.
         */
        fun setTimestamp(buf: ByteArray?, off: Int, len: Int, ts: Long) {
            writeInt(buf, off + 4, ts.toInt())
        }

        /**
         * Sets the RTP timestamp of an RTP packet.
         *
         * param baaf the [ByteArrayBuffer] that contains the RTP packet.
         *
         * @param ts the timestamp to set in the RTP packet.
         */
        fun setTimestamp(baf: ByteArrayBuffer?, ts: Long) {
            if (baf == null) {
                return
            }
            setTimestamp(baf.buffer, baf.offset, baf.length, ts)
        }

        /**
         * Get SRTCP sequence number from a SRTCP packet
         *
         * @param authTagLen authentication tag length
         * @return SRTCP sequence num from source packet
         */
        fun getSRTCPIndex(baf: ByteArrayBuffer, authTagLen: Int): Int {
            val authTagOffset = baf.length - (4 + authTagLen)
            return readInt(baf.buffer, baf.offset + authTagOffset)
        }

        /**
         * Get RTP SSRC from a RTP packet
         *
         * @param buffer the `byte` array that holds the RTP packet.
         * @param offset the offset in `buffer` at which the actual RTP data begins.
         * @param length the number of `byte`s in `buffer` which
         * @return RTP SSRC from source RTP packet
         */
        fun getSSRC(buffer: ByteArray, offset: Int, length: Int): Int {
            return readInt(buffer, offset + 8)
        }

        /**
         * Get RTP SSRC from a RTP packet
         */
        fun getSSRC(baf: ByteArrayBuffer): Int {
            return getSSRC(baf.buffer, baf.offset, baf.length)
        }

        /**
         * Returns a `long` representation of the SSRC of this RTP packet.
         *
         * @param buffer the `byte` array that holds the RTP packet.
         * @param offset the offset in `buffer` at which the actual RTP data begins.
         * @param length the number of `byte`s in `buffer` which
         * @return a `long` representation of the SSRC of this RTP packet.
         */
        fun getSSRCAsLong(buffer: ByteArray, offset: Int, length: Int): Long {
            return getSSRC(buffer, offset, length).toLong() and 0xffffffffL
        }

        /**
         * Gets the RTP timestamp for an RTP buffer.
         *
         * @param buf the `byte` array that holds the RTP packet.
         * @param off the offset in `buffer` at which the actual RTP data begins.
         * @param len the number of `byte`s in `buffer` which constitute the actual RTP data.
         * @return the timestamp in the RTP buffer.
         */
        fun getTimestamp(buf: ByteArray, off: Int, len: Int): Long {
            return readUint32AsLong(buf, off + 4)
        }

        /**
         * Gets the RTP timestamp for an RTP buffer.
         *
         * @param baf the [ByteArrayBuffer] that contains the RTP packet.
         * @return the timestamp in the RTP buffer.
         */
        fun getTimestamp(baf: ByteArrayBuffer?): Long {
            return if (baf == null) {
                -1
            } else getTimestamp(baf.buffer, baf.offset, baf.length)
        }
    }
}