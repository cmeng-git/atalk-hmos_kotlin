/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video.vp8

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.service.neomedia.codec.Constants
import org.atalk.util.ByteArrayBuffer
import org.atalk.util.RTPUtils
import org.atalk.util.RTPUtils.toHexString
import timber.log.Timber
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import javax.media.Buffer
import javax.media.ResourceUnavailableException
import javax.media.format.VideoFormat
import kotlin.math.min

/**
 * A depacketizer from VP8 codec.
 * See []//tools.ietf.org/html/rfc7741"">&quot;https://tools.ietf.org/html/rfc7741&quot;
 * See []//tools.ietf.org/html/draft-ietf-payload-vp8-17"">&quot;https://tools.ietf.org/html/draft-ietf-payload-vp8-17&quot;
 *
 * Stores the RTP payloads (VP8 payload descriptor stripped) from RTP packets belonging to a
 * single VP8 compressed frame. Maps an RTP sequence number to a buffer which contains the payload.
 *
 * @author Boris Grozev
 * @author George Politis
 * @author Eng Chong Meng
 */
class DePacketizer : AbstractCodec2("VP8 RTP DePacketizer", VideoFormat::class.java, arrayOf(VideoFormat(Constants.VP8))) {
    private val data: SortedMap<Int, Container?> = TreeMap(RTPUtils.sequenceNumberComparator)

    /**
     * Stores unused `Container`'s.
     */
    private val free: Queue<Container?> = ArrayBlockingQueue(100)

    /**
     * Stores the first (earliest) sequence number stored in `data`, or -1 if `data` is empty.
     */
    private var firstSeq = -1

    /**
     * Stores the last (latest) sequence number stored in `data`, or -1 if `data` is empty.
     */
    private var lastSeq = -1

    /**
     * Stores the value of the `PictureID` field for the VP8 compressed
     * frame, parts of which are currently stored in `data`, or -1 if
     * the `PictureID` field is not in use or `data` is empty.
     */
    private var pictureId = -1

    /**
     * Stores the RTP timestamp of the packets stored in `data`, or -1 if they don't have a timestamp set.
     */
    private var timestamp = -1L

    /**
     * Whether we have stored any packets in `data`. Equivalent to `data.isEmpty()`.
     */
    private var empty = true

    /**
     * Whether we have stored in `data` the last RTP packet of the VP8
     * compressed frame, parts of which are currently stored in `data`.
     */
    private var haveEnd = false

    /**
     * Whether we have stored in `data` the first RTP packet of the VP8
     * compressed frame, parts of which are currently stored in `data`.
     */
    private var haveStart = false

    /**
     * Stores the sum of the lengths of the data stored in `data`, that
     * is the total length of the VP8 compressed frame to be constructed.
     */
    private var frameLength = 0

    /**
     * The sequence number of the last RTP packet, which was included in the output.
     */
    private var lastSentSeq = -1

    /**
     * Initializes a new `AbstractCodec2` instance with a specific `PlugIn` name, a
     * specific `Class` of input and output `Format`s, and a specific list of
     * `Format`s supported as output.
     *
     * name: the `PlugIn` name of the new instance
     * VideoFormat.class: the `Class` of input and output `Format`s supported by the new instance
     * VideoFormat: the list of `Format`s supported by the new instance as output @Super parameters
     */
    init {
        inputFormats = arrayOf(VideoFormat(Constants.VP8_RTP))
    }

    /**
     * {@inheritDoc}
     */
    override fun doClose() {}

    /**
     * {@inheritDoc}
     */
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        Timber.log(TimberLog.FINER, "Opened VP8 dePacketizer")
    }

    /**
     * Re-initializes the fields which store information about the currently held data. Empties `data`.
     */
    private fun reinit() {
        lastSeq = -1
        firstSeq = lastSeq
        timestamp = -1L
        pictureId = -1
        empty = true
        haveStart = false
        haveEnd = haveStart
        frameLength = 0
        val it: MutableIterator<Map.Entry<Int, Container?>> = data.entries.iterator()
        var e: Map.Entry<Int, Container?>
        while (it.hasNext()) {
            e = it.next()
            free.offer(e.value)
            it.remove()
        }
    }

    /**
     * Checks whether the currently held VP8 compressed frame is complete (e.g all its packets
     * are stored in `data`).
     *
     * @return `true` if the currently help VP8 compressed frame is complete, `false` otherwise.
     */
    private fun frameComplete(): Boolean {
        return haveStart && haveEnd && !haveMissing()
    }

    /**
     * Checks whether there are packets with sequence numbers between `firstSeq` and
     * `lastSeq` which are *not* stored in `data`.
     *
     * @return `true` if there are packets with sequence numbers between
     * `firstSeq` and `lastSeq` which are *not* stored in `data`.
     */
    private fun haveMissing(): Boolean {
        val seqs: Set<Int> = data.keys
        var s = firstSeq
        while (s != lastSeq) {
            if (!seqs.contains(s)) return true
            s = s + 1 and 0xffff
        }
        return false
    }

    /**
     * {@inheritDoc}
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        val inData = inBuf.data as ByteArray
        val inOffset = inBuf.offset
        val inLength = inBuf.length
        if (!VP8PayloadDescriptor.isValid(inData, inOffset, inLength)) {
            Timber.w("Invalid VP8/RTP packet discarded.")
            outBuf.isDiscard = true
            return BUFFER_PROCESSED_FAILED //XXX: FAILED or OK?
        }
        val inSeq = inBuf.sequenceNumber.toInt()
        val inRtpTimestamp = inBuf.rtpTimeStamp
        val inPictureId = VP8PayloadDescriptor.getPictureId(inData, inOffset)
        val inMarker = inBuf.flags and Buffer.FLAG_RTP_MARKER != 0
        val inIsStartOfFrame = VP8PayloadDescriptor.isStartOfFrame(inData, inOffset)
        val inPdSize = VP8PayloadDescriptor.getSize(inData, inOffset, inLength)
        val inPayloadLength = inLength - inPdSize
        if (empty && lastSentSeq != -1 && RTPUtils.sequenceNumberComparator.compare(inSeq, lastSentSeq) <= 0) {
            Timber.d("Discarding old packet (while empty) %s", inSeq)
            outBuf.isDiscard = true
            return BUFFER_PROCESSED_OK
        }
        if (!empty) {
            // if the incoming packet has a different PictureID or timestamp than those of
            // the current frame, then it belongs to a different frame.
            if ((inPictureId != -1 && pictureId != -1 && inPictureId != pictureId)
                    or (timestamp != -1L && inRtpTimestamp != -1L && inRtpTimestamp != timestamp)) {
                //inSeq <= firstSeq
                if (RTPUtils.sequenceNumberComparator.compare(inSeq, firstSeq) <= 0) {
                    // the packet belongs to a previous frame. discard it
                    Timber.i("Discarding old packet %s", inSeq)
                    outBuf.isDiscard = true
                    return BUFFER_PROCESSED_OK
                } else if (RTPUtils.sequenceNumberComparator.compare(inSeq, firstSeq) > 0) {
                    firstSeq = inSeq
                } else {
                    // the packet belongs to a subsequent frame (to the one currently being held). Drop the current frame.
                    Timber.i("Discarding saved packets on arrival of a packet for a subsequent frame: %s; %s", inSeq, firstSeq)
                    reinit()
                }
            }
        }

        // a whole frame in a single packet. avoid the extra copy to this.data and output it immediately.
        if (empty && inMarker && inIsStartOfFrame) {
            val outData = validateByteArraySize(outBuf, inPayloadLength, false)
            System.arraycopy(inData, inOffset + inPdSize, outData, 0, inPayloadLength)
            outBuf.offset = 0
            outBuf.length = inPayloadLength
            outBuf.rtpTimeStamp = inBuf.rtpTimeStamp
            Timber.log(TimberLog.FINER, "Out PictureID = %s", inPictureId)
            lastSentSeq = inSeq
            return BUFFER_PROCESSED_OK
        }

        // add to this.data
        var container = free.poll()
        if (container == null) container = Container()
        if (container.buf == null || container.buf!!.size < inPayloadLength) container.buf = ByteArray(inPayloadLength)
        if (data[inSeq] != null) {
            Timber.i("(Probable) duplicate packet detected, discarding %s", inSeq)
            outBuf.isDiscard = true
            return BUFFER_PROCESSED_OK
        }
        System.arraycopy(inData, inOffset + inPdSize, container.buf!!, 0, inPayloadLength)
        container.len = inPayloadLength
        data[inSeq] = container

        // update fields
        frameLength += inPayloadLength
        if (firstSeq == -1 || RTPUtils.sequenceNumberComparator.compare(firstSeq, inSeq) > 0) firstSeq = inSeq
        if (lastSeq == -1 || RTPUtils.sequenceNumberComparator.compare(inSeq, lastSeq) > 0) lastSeq = inSeq
        if (empty) {
            // the first received packet for the current frame was just added
            empty = false
            timestamp = inRtpTimestamp
            pictureId = inPictureId
        }
        if (inMarker) haveEnd = true
        if (inIsStartOfFrame) haveStart = true

        // check if we have a full frame
        return if (frameComplete()) {
            val outData = validateByteArraySize(outBuf, frameLength, false)
            var ptr = 0
            var b: Container
            for ((_, value) in data) {
                b = value!!
                System.arraycopy(b.buf!!, 0, outData, ptr, b.len)
                ptr += b.len
            }
            outBuf.offset = 0
            outBuf.length = frameLength
            outBuf.rtpTimeStamp = inBuf.rtpTimeStamp
            Timber.log(TimberLog.FINER, "Out PictureID = %s", inPictureId)
            lastSentSeq = lastSeq

            // prepare for the next frame
            reinit()
            BUFFER_PROCESSED_OK
        } else {
            // frame not complete yet
            outBuf.isDiscard = true
            OUTPUT_BUFFER_NOT_FILLED
        }
    }

    /**
     * A class that represents the VP8 Payload Descriptor structure defined
     * in []//tools.ietf.org/html/rfc7741"">&quot;https://tools.ietf.org/html/rfc7741&quot;
     */
    object VP8PayloadDescriptor {
        /**
         * The bitmask for the TL0PICIDX field.
         */
        const val TL0PICIDX_MASK = 0xff

        /**
         * The bitmask for the extended picture id field.
         */
        const val EXTENDED_PICTURE_ID_MASK = 0x7fff

        /**
         * I bit from the X byte of the Payload Descriptor.
         */
        private const val I_BIT = 0x80.toByte()

        /**
         * K bit from the X byte of the Payload Descriptor.
         */
        private const val K_BIT = 0x10.toByte()

        /**
         * L bit from the X byte of the Payload Descriptor.
         */
        private const val L_BIT = 0x40.toByte()

        /**
         * I bit from the I byte of the Payload Descriptor.
         */
        private const val M_BIT = 0x80.toByte()

        /**
         * S bit from the first byte of the Payload Descriptor.
         */
        private const val S_BIT = 0x10.toByte()

        /**
         * T bit from the X byte of the Payload Descriptor.
         */
        private const val T_BIT = 0x20.toByte()

        /**
         * X bit from the first byte of the Payload Descriptor.
         */
        private const val X_BIT = 0x80.toByte()

        /**
         * N bit from the first byte of the Payload Descriptor.
         */
        private const val N_BIT = 0x20.toByte()

        /**
         * The bitmask for the temporal-layer index
         */
        private const val TID_MASK = 0xC0.toByte()

        /**
         * Y bit from the TID/Y/KEYIDX extension byte.
         */
        private const val Y_BIT = 0x20.toByte()

        /**
         * The bitmask for the temporal key frame index
         */
        private const val KEYIDX_MASK = 0x1F.toByte()

        /**
         * Maximum length of a VP8 Payload Descriptor.
         */
        const val MAX_LENGTH = 6

        /**
         * Gets the TID/Y/KEYIDX extension byte if available
         *
         * @param buf the byte buffer that holds the VP8 packet.
         * @param off the offset in the byte buffer where the VP8 packet starts.
         * @param len the length of the VP8 packet.
         * @return the TID/Y/KEYIDX extension byte, if that's set, -1 otherwise.
         */
        private fun getTidYKeyIdxExtensionByte(buf: ByteArray?, off: Int, len: Int): Byte {
            if (buf == null || buf.size < off + len || len < 2) {
                return -1
            }
            if (buf[off].toInt() and X_BIT.toInt() == 0 || buf[off + 1].toInt() and (T_BIT.toInt() or K_BIT.toInt()) == 0) {
                return -1
            }
            val sz = getSize(buf, off, len)
            return if (buf.size < off + sz || sz < 1) {
                -1
            } else (buf[off + sz - 1].toInt() and 0xFF).toByte()
        }

        /**
         * Gets the temporal layer index (TID), if that's set.
         *
         * @param buf the byte buffer that holds the VP8 packet.
         * @param off the offset in the byte buffer where the VP8 packet starts.
         * @param len the length of the VP8 packet.
         * @return the temporal layer index (TID), if that's set, -1 otherwise.
         */
        fun getTemporalLayerIndex(buf: ByteArray, off: Int, len: Int): Int {
            val tidYKeyIdxByte = getTidYKeyIdxExtensionByte(buf, off, len)
            return if (tidYKeyIdxByte.toInt() != -1 && buf[off + 1].toInt() and T_BIT.toInt() != 0) tidYKeyIdxByte.toInt() and TID_MASK.toInt() shr 6 else tidYKeyIdxByte.toInt()
        }

        /**
         * Gets the 1 layer sync bit (Y BIT), if that's set.
         *
         * @param buf the byte buffer that holds the VP8 packet.
         * @param off the offset in the byte buffer where the VP8 packet starts.
         * @param len the length of the VP8 packet.
         * @return the 1 layer sync bit (Y BIT), if that's set, -1 otherwise.
         */
        fun getFirstLayerSyncBit(buf: ByteArray?, off: Int, len: Int): Int {
            val tidYKeyIdxByte = getTidYKeyIdxExtensionByte(buf, off, len)
            return if (tidYKeyIdxByte.toInt() != -1) tidYKeyIdxByte.toInt() and Y_BIT.toInt() shr 5 else tidYKeyIdxByte.toInt()
        }

        /**
         * Gets the temporal key frame index (KEYIDX), if that's set.
         *
         * @param buf the byte buffer that holds the VP8 packet.
         * @param off the offset in the byte buffer where the VP8 packet starts.
         * @param len the length of the VP8 packet.
         * @return the temporal key frame index (KEYIDX), if that's set, -1 otherwise.
         */
        fun getTemporalKeyFrameIndex(buf: ByteArray, off: Int, len: Int): Int {
            val tidYKeyIdxByte = getTidYKeyIdxExtensionByte(buf, off, len)
            return if (tidYKeyIdxByte.toInt() != -1 && buf[off + 1].toInt() and K_BIT.toInt() != 0) tidYKeyIdxByte.toInt() and KEYIDX_MASK.toInt() else tidYKeyIdxByte.toInt()
        }

        /**
         * Returns a simple Payload Descriptor, with PartID = 0, the 'start of partition' bit set
         * according to `startOfPartition`, and all other bits set to 0.
         *
         * @param startOfPartition whether to 'start of partition' bit should be set
         * @return a simple Payload Descriptor, with PartID = 0, the 'start of partition' bit set
         * according to `startOfPartition`, and all other bits set to 0.
         */
        fun create(startOfPartition: Boolean): ByteArray {
            val pd = ByteArray(1)
            pd[0] = if (startOfPartition) 0x10.toByte() else 0
            return pd
        }

        /**
         * The size in bytes of the Payload Descriptor at offset
         * `offset` in `input`. The size is between 1 and 6.
         *
         * @param baf the `ByteArrayBuffer` that holds the VP8 payload descriptor.
         * @return The size in bytes of the Payload Descriptor at offset
         * `offset` in `input`, or -1 if the input is not a valid
         * VP8 Payload Descriptor. The size is between 1 and 6.
         */
        fun getSize(baf: ByteArrayBuffer?): Int {
            return if (baf == null) {
                -1
            } else getSize(baf.buffer, baf.offset, baf.length)
        }

        /**
         * The size in bytes of the Payload Descriptor at offset
         * `offset` in `input`. The size is between 1 and 6.
         *
         * @param input input
         * @param offset offset
         * @param length length
         * @return The size in bytes of the Payload Descriptor at offset
         * `offset` in `input`, or -1 if the input is not a valid
         * VP8 Payload Descriptor. The size is between 1 and 6.
         */
        fun getSize(input: ByteArray?, offset: Int, length: Int): Int {
            if (!isValid(input, offset, length)) return -1
            if (input!![offset].toInt() and X_BIT.toInt() == 0) return 1
            var size = 2
            if (input[offset + 1].toInt() and I_BIT.toInt() != 0) {
                size++
                if (input[offset + 2].toInt() and M_BIT.toInt() != 0) size++
            }
            if (input[offset + 1].toInt() and (L_BIT.toInt() or T_BIT.toInt()) != 0) size++
            if (input[offset + 1].toInt() and (T_BIT.toInt() or K_BIT.toInt()) != 0) size++
            return size
        }

        /**
         * Determines whether the VP8 payload specified in the buffer that is
         * passed as an argument has a picture ID or not.
         *
         * @param buf the byte buffer that contains the VP8 payload.
         * @param off the offset in the byte buffer where the VP8 payload starts.
         * @param len the length of the VP8 payload in the byte buffer.
         * @return true if the VP8 payload contains a picture ID, false otherwise.
         */
        private fun hasPictureId(buf: ByteArray, off: Int, len: Int): Boolean {
            return isValid(buf, off, len) && buf[off].toInt() and X_BIT.toInt() != 0 && buf[off + 1].toInt() and I_BIT.toInt() != 0
        }

        /**
         * Determines whether the VP8 payload specified in the buffer that is
         * passed as an argument has an extended picture ID or not.
         *
         * @param buf the byte buffer that contains the VP8 payload.
         * @param off the offset in the byte buffer where the VP8 payload starts.
         * @param len the length of the VP8 payload in the byte buffer.
         * @return true if the VP8 payload contains an extended picture ID, false otherwise.
         */
        private fun hasExtendedPictureId(buf: ByteArray, off: Int, len: Int): Boolean {
            return hasPictureId(buf, off, len) && buf[off + 2].toInt() and M_BIT.toInt() != 0
        }

        /**
         * Gets the value of the PictureID field of a VP8 Payload Descriptor.
         *
         * @param input
         * @param offset
         * @return the value of the PictureID field of a VP8 Payload Descriptor,
         * or -1 if the fields is not present.
         */
        fun getPictureId(input: ByteArray?, offset: Int): Int {
            if (input == null
                    || !hasPictureId(input, offset, input.size - offset)) {
                return -1
            }
            val isLong = input[offset + 2].toInt() and M_BIT.toInt() != 0
            return if (isLong) input[offset + 2].toInt() and 0x7f shl 8 or (input[offset + 3].toInt() and 0xff) else input[offset + 2].toInt() and 0x7f
        }

        /**
         * Sets the extended picture ID for the VP8 payload specified in the
         * buffer that is passed as an argument.
         *
         * @param buf the byte buffer that contains the VP8 payload.
         * @param off the offset in the byte buffer where the VP8 payload starts.
         * @param len the length of the VP8 payload in the byte buffer.
         * @return true if the operation succeeded, false otherwise.
         */
        fun setExtendedPictureId(
                buf: ByteArray, off: Int, len: Int, `val`: Int): Boolean {
            if (!hasExtendedPictureId(buf, off, len)) {
                return false
            }
            buf[off + 2] = (0x80 or (`val` shr 8 and 0x7F)).toByte()
            buf[off + 3] = (`val` and 0xFF).toByte()
            return true
        }

        /**
         * Sets the TL0PICIDX field for the VP8 payload specified in the buffer that is passed as an argument.
         *
         * @param buf the byte buffer that contains the VP8 payload.
         * @param off the offset in the byte buffer where the VP8 payload starts.
         * @param len the length of the VP8 payload in the byte buffer.
         * @return true if the operation succeeded, false otherwise.
         */
        fun setTL0PICIDX(buf: ByteArray, off: Int, len: Int, `val`: Int): Boolean {
            if (!isValid(buf, off, len) || buf[off].toInt() and X_BIT.toInt() == 0 || buf[off + 1].toInt() and L_BIT.toInt() == 0) {
                return false
            }
            var offTL0PICIDX = 2
            if (buf[off + 1].toInt() and I_BIT.toInt() != 0) {
                offTL0PICIDX++
                if (buf[off + 2].toInt() and M_BIT.toInt() != 0) {
                    offTL0PICIDX++
                }
            }
            buf[off + offTL0PICIDX] = `val`.toByte()
            return true
        }

        /**
         * Checks whether the arguments specify a valid buffer.
         *
         * @param buf the byte buffer that contains the VP8 payload.
         * @param off the offset in the byte buffer where the VP8 payload starts.
         * @param len the length of the VP8 payload in the byte buffer.
         * @return true if the arguments specify a valid buffer, false
         * otherwise.
         */
        fun isValid(buf: ByteArray?, off: Int, len: Int): Boolean {
            return buf != null && buf.size >= off + len && off > -1 && len > 0
        }

        /**
         * Checks whether the '`start of partition`' bit is set in the
         * VP8 Payload Descriptor at offset `offset` in `input`.
         *
         * @param input input
         * @param offset offset
         * @return `true` if the '`start of partition`' bit is set,
         * `false` otherwise.
         */
        private fun isStartOfPartition(input: ByteArray, offset: Int): Boolean {
            return input[offset].toInt() and S_BIT.toInt() != 0
        }

        /**
         * Returns `true` if both the '`start of partition`' bit
         * is set and the `PID` fields has value 0 in the VP8 Payload
         * Descriptor at offset `offset` in `input`.
         *
         * @param input input
         * @param offset offset
         * @return `true` if both the '`start of partition`' bit
         * is set and the `PID` fields has value 0 in the VP8 Payload
         * Descriptor at offset `offset` in `input`.
         */
        fun isStartOfFrame(input: ByteArray, offset: Int): Boolean {
            return (isStartOfPartition(input, offset)
                    && getPartitionId(input, offset) == 0)
        }

        /**
         * Returns the value of the `PID` (partition ID) field of the
         * VP8 Payload Descriptor at offset `offset` in `input`.
         *
         * @param input input
         * @param offset offset
         * @return the value of the `PID` (partition ID) field of the
         * VP8 Payload Descriptor at offset `offset` in `input`.
         */
        private fun getPartitionId(input: ByteArray, offset: Int): Int {
            return input[offset].toInt() and 0x07
        }

        /**
         * Gets a boolean indicating if the non-reference bit is set.
         *
         * @param buf the byte buffer that holds the VP8 payload descriptor.
         * @param off the offset in the byte buffer where the payload descriptor starts.
         * @param len the length of the payload descriptor in the byte buffer.
         * @return true if the non-reference bit is NOT set, false otherwise.
         */
        fun isReference(buf: ByteArray, off: Int, len: Int): Boolean {
            return buf[off].toInt() and N_BIT.toInt() == 0
        }

        /**
         * Gets the TL0PICIDX from the payload descriptor.
         *
         * @param buf the byte buffer that holds the VP8 payload descriptor.
         * @param off the offset in the byte buffer where the payload descriptor starts.
         * @param len the length of the payload descriptor in the byte buffer.
         * @return the TL0PICIDX from the payload descriptor.
         */
        private fun getTL0PICIDX(buf: ByteArray, off: Int, len: Int): Int {
            val sz = getSize(buf, off, len)
            return if (sz < 1) {
                -1
            } else buf[off + sz - 2].toInt() and 0xff
        }

        /**
         * Provides a string description of the VP8 descriptor that can be used
         * for debugging purposes.
         *
         * @param buf the byte buffer that holds the VP8 payload descriptor.
         * @param off the offset in the byte buffer where the payload descriptor starts.
         * @param len the length of the payload descriptor in the byte buffer.
         * @return Descriptive string of the vp8 info
         */
        fun toString(buf: ByteArray, off: Int, len: Int): String {
            return "VP8PayloadDescriptor" +
                    "[size=" + getSize(buf, off, len) +
                    ", tid=" + getTemporalLayerIndex(buf, off, len) +
                    ", tl0picidx=" + getTL0PICIDX(buf, off, len) +
                    ", pid=" + getPictureId(buf, off) +
                    ", isExtended=" + hasExtendedPictureId(buf, off, len) +
                    ", hex=" + toHexString(buf, off, min(len, MAX_LENGTH), false) +
                    "]"
        }
    }

    /**
     * A class that represents the VP8 Payload Header structure defined
     * in []//tools.ietf.org/html/rfc7741"">&quot;https://tools.ietf.org/html/rfc7741&quot;
     */
    object VP8PayloadHeader {
        /**
         * P bit of the Payload Descriptor.
         */
        private const val P_BIT = 0x01.toByte()

        /**
         * Returns true if the `P` (inverse key frame flag) field of the
         * VP8 Payload Header at offset `offset` in `input` is 0.
         *
         * @return true if the `P` (inverse key frame flag) field of the
         * VP8 Payload Header at offset `offset` in `input` is 0, false otherwise.
         */
        fun isKeyFrame(input: ByteArray, offset: Int): Boolean {
            // When set to 0 the current frame is a key frame.  When set to 1
            // the current frame is an interframe. Defined in [RFC6386]
            return input[offset].toInt() and P_BIT.toInt() == 0
        }
    }

    /**
     * A class that represents a keyframe header structure (see RFC 6386, paragraph 9.1).
     *
     * @author George Politis
     */
    object VP8KeyframeHeader {
        /*
         * From RFC 6386, the keyframe header has this format.
         *
         * Start code byte 0: 0x9d
         * Start code byte 1: 0x01
         * Start code byte 2: 0x2a
         *
         * 16 bits : (2 bits Horizontal Scale << 14) | Width (14 bits)
         * 16 bits : (2 bits Vertical Scale << 14) | Height (14 bits)
         */
        /**
         * @return the height of this instance.
         */
        fun getHeight(buf: ByteArray, off: Int): Int {
            return buf[off + 6].toInt() and 0xff shl 8 or (buf[off + 5].toInt() and 0xff) and 0x3fff
        }
    }

    /**
     * A simple container for a `byte[]` and an integer.
     */
    private class Container {
        /**
         * This `Container`'s data.
         */
        var buf: ByteArray? = null

        /**
         * Length used.
         */
        var len = 0
    }

    companion object {
        /**
         * Returns true if the buffer contains a VP8 key frame at offset `offset`.
         *
         * @param buf the byte buffer to check
         * @param off the offset in the byte buffer where the actual data starts
         * @param len the length of the data in the byte buffer
         * @return true if the buffer contains a VP8 key frame at offset `offset`.
         */
        fun isKeyFrame(buf: ByteArray, off: Int, len: Int): Boolean {
            // Check if this is the start of a VP8 partition in the payload descriptor.
            if (!VP8PayloadDescriptor.isValid(buf, off, len)) {
                return false
            }
            if (!VP8PayloadDescriptor.isStartOfFrame(buf, off)) {
                return false
            }
            val szVP8PayloadDescriptor = VP8PayloadDescriptor.getSize(buf, off, len)
            return VP8PayloadHeader.isKeyFrame(buf, off + szVP8PayloadDescriptor)
        }
    }
}