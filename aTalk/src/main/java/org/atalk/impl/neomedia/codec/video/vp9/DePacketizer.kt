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
package org.atalk.impl.neomedia.codec.video.vp9

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.service.neomedia.codec.Constants
import org.atalk.util.ByteArrayBuffer
import org.atalk.util.RTPUtils
import org.atalk.util.RTPUtils.toHexString
import timber.log.Timber
import java.awt.Dimension
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import javax.media.Buffer
import javax.media.ResourceUnavailableException
import javax.media.format.VideoFormat
import kotlin.math.abs
import kotlin.math.min

/**
 * A depacketizer for VP9 codec which handles Constants.VP9_RTP stream data
 * See []//tools.ietf.org/html/draft-ietf-payload-vp9-15"">&quot;https://tools.ietf.org/html/draft-ietf-payload-vp9-15&quot;
 *
 * Stores the RTP payloads (VP9 payload descriptor stripped) from RTP packets belonging to a
 * single VP9 compressed frame. Maps an RTP sequence number to a buffer which contains the payload.
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
class DePacketizer : AbstractCodec2("VP9 RTP DePacketizer", VideoFormat::class.java, arrayOf(VideoFormat(Constants.VP9))) {
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
     * Stores the value of the `PictureID` field for the VP9 compressed
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
     * Whether we have stored in `data` the last RTP packet of the VP9
     * compressed frame, parts of which are currently stored in `data`.
     */
    private var haveEnd = false

    /**
     * Whether we have stored in `data` the first RTP packet of the VP9
     * compressed frame, parts of which are currently stored in `data`.
     */
    private var haveStart = false

    /**
     * Stores the sum of the lengths of the data stored in `data`, that
     * is the total length of the VP9 compressed frame to be constructed.
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
        inputFormats = arrayOf(VideoFormat(Constants.VP9_RTP))
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
        Timber.log(TimberLog.FINER, "Opened VP9 dePacketizer")
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
        if (!VP9PayloadDescriptor.isValid(inData, inOffset, inLength)) {
            Timber.w("Invalid VP9/RTP packet discarded.")
            outBuf.isDiscard = true
            return BUFFER_PROCESSED_FAILED //XXX: FAILED or OK?
        }
        val inSeq = inBuf.sequenceNumber.toInt()
        val inRtpTimestamp = inBuf.rtpTimeStamp
        val inPictureId = VP9PayloadDescriptor.getPictureId(inData, inOffset)
        val inMarker = inBuf.flags and Buffer.FLAG_RTP_MARKER != 0
        val inIsStartOfFrame = VP9PayloadDescriptor.isStartOfFrame(inData, inOffset, inLength)

        /*
         * inPdSize: inBuffer payload descriptor length, need to be stripped off in filter output
         * inPayloadLength: the actual media frame data length
         */
        val inPdSize = VP9PayloadDescriptor.getSize(inData, inOffset, inLength)
        val inPayloadLength = inLength - inPdSize


        // Timber.d("VP9: DePacketizer: %s %s %s:\nData: %s", inBuffer.getFormat(), inPdSize, inPayloadLength,
        //        bytesToHex((byte[]) inBuffer.getData(), 32));

        // Timber.d("VP9: %s", VP9PayloadDescriptor.toString(inData, inOffset, inLength));
        // Timber.d("VP9: DePacketizer: %s %s %s %s %s %s %s %s %s %s %s", bytesToHex(inData, 48), inOffset, inLength,
        //        inSeq, Integer.toHexString(inPictureId), inMarker, inIsStartOfFrame, inPdSize, inPayloadLength, empty, lastSentSeq);
        if (empty && lastSentSeq != -1 && RTPUtils.sequenceNumberComparator.compare(inSeq, lastSentSeq) <= 0) {
            Timber.d("Discarding old packet (while empty) %s <= %s", inSeq, lastSentSeq)
            // resync lastSentSeq = current inSeq
            lastSentSeq = inSeq
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
        if (container.buff == null || container.buff!!.size < inPayloadLength) container.buff = ByteArray(inPayloadLength)
        if (data[inSeq] != null) {
            Timber.i("(Probable) duplicate packet detected, discarding %s", inSeq)
            outBuf.isDiscard = true
            return BUFFER_PROCESSED_OK
        }
        System.arraycopy(inData, inOffset + inPdSize, container.buff!!, 0, inPayloadLength)
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
                System.arraycopy(b.buff!!, 0, outData, ptr, b.len)
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
     * A class that represents the VP9 Payload Descriptor structure defined
     * in []//tools.ietf.org/html/draft-ietf-payload-vp9-15"">&quot;https://tools.ietf.org/html/draft-ietf-payload-vp9-15&quot;
     */
    // VP9 format:
    //
    // Payload descriptor for F = 1 (flexible mode)
    //       0 1 2 3 4 5 6 7
    //      +-+-+-+-+-+-+-+-+
    //      |I|P|L|F|B|E|V|Z| (REQUIRED)
    //      +-+-+-+-+-+-+-+-+
    // I:   |M| PICTURE ID  | (RECOMMENDED)
    //      +-+-+-+-+-+-+-+-+
    // M:   | EXTENDED PID  | (RECOMMENDED)
    //      +-+-+-+-+-+-+-+-+
    // L:   |  T  |U|  S  |D| (CONDITIONALLY RECOMMENDED)
    //      +-+-+-+-+-+-+-+-+                             -|
    // P,F: | P_DIFF      |N| (CONDITIONALLY RECOMMENDED)  . up to 3 times
    //      +-+-+-+-+-+-+-+-+                             -|
    // V:   | SS            |
    //      | ..            |
    //      +-+-+-+-+-+-+-+-+
    //
    // Payload descriptor for F = 0 (non-flexible mode)
    //       0 1 2 3 4 5 6 7
    //      +-+-+-+-+-+-+-+-+
    //      |I|P|L|F|B|E|V|Z| (REQUIRED)
    //      +-+-+-+-+-+-+-+-+
    // I:   |M| PICTURE ID  | (RECOMMENDED)
    //      +-+-+-+-+-+-+-+-+
    // M:   | EXTENDED PID  | (RECOMMENDED)
    //      +-+-+-+-+-+-+-+-+
    // L:   |  T  |U|  S  |D| (CONDITIONALLY RECOMMENDED)
    //      +-+-+-+-+-+-+-+-+
    //      |   TL0PICIDX   | (CONDITIONALLY REQUIRED)
    //      +-+-+-+-+-+-+-+-+
    // V:   | SS            |
    //      | ..            |
    //      +-+-+-+-+-+-+-+-+
    //
    // Scalability structure (SS).
    //
    //      +-+-+-+-+-+-+-+-+
    // V:   | N_S |Y|G|-|-|-|
    //      +-+-+-+-+-+-+-+-+              -|
    // Y:   |     WIDTH     | (OPTIONAL)    .
    //      +               +               .
    //      |               | (OPTIONAL)    .
    //      +-+-+-+-+-+-+-+-+               . N_S + 1 times
    //      |     HEIGHT    | (OPTIONAL)    .
    //      +               +               .
    //      |               | (OPTIONAL)    .
    //      +-+-+-+-+-+-+-+-+              -|
    // G:   |      N_G      | (OPTIONAL)
    //      +-+-+-+-+-+-+-+-+                           -|
    // N_G: |  T  |U| R |-|-| (OPTIONAL)                 .
    //      +-+-+-+-+-+-+-+-+              -|            . N_G times
    //      |    P_DIFF     | (OPTIONAL)    . R times    .
    //      +-+-+-+-+-+-+-+-+              -|           -|
    //
    object VP9PayloadDescriptor {
        /**
         * I: Picture ID (PID) present; bit from the first byte of the Payload Descriptor.
         */
        private const val I_BIT = 0x80.toByte()

        /**
         * P: Inter-picture predicted layer frame; bit from the first byte of the Payload Descriptor.
         */
        private const val P_BIT = 0x40.toByte()

        /**
         * L: Layer indices present; bit from the first byte of the Payload Descriptor.
         */
        private const val L_BIT = 0x20.toByte()

        /**
         * F: The Flexible mode; bit from the first byte of the Payload Descriptor.
         */
        private const val F_BIT = 0x10.toByte()

        /**
         * B: Start of a layer frame; bit from the first byte of the Payload Descriptor.
         */
        private const val B_BIT = 0x08.toByte()

        /**
         * E: End of a layer frame; bit from the first byte of the Payload Descriptor.
         */
        private const val E_BIT = 0x04.toByte()

        /**
         * E: Scalability structure (SS) data present; bit from the first byte of the Payload Descriptor.
         */
        private const val V_BIT = 0x02.toByte()

        /**
         * Z: Not a reference frame for upper spatial layers.; bit from the first byte of the Payload Descriptor.
         */
        private const val Z_BIT = 0x01.toByte()

        /**
         * M: The Extended flag; bit from the PID.
         */
        private const val M_BIT = 0x80.toByte()

        /**
         * Mask for TID value from Layer Indices byte of the Payload Descriptor.
         */
        private const val TID_MASK = 0xE0.toByte()

        /**
         * Mask for SID value from Layer Indices byte of the Payload Descriptor.
         */
        private const val SID_MASK: Byte = 0x0E

        /**
         * Mask for D value from Layer Indices byte of the Payload Descriptor.
         */
        private const val D_MASK: Byte = 0x01

        /**
         * Maximum length of a VP9 Payload Descriptor pending:
         * a. VP9 Payload Description - SS = 4 + 3 = 7
         * V: Scalability structure (SS) data = V + (N_S + 1) * 4 + G + N_G * (1 + 3)
         * = 1 + (8 * 4) + 1 + (255 * (1 + 3))
         * = 34 + (255 * 4)
         */
        // public static final int MAX_LENGTH = 7 + 34 + (255 * 4); // 1061 or webric(1200)
        const val MAX_LENGTH = 23 // practical length in aTalk

        /**
         * Returns `true` if the B bit from the first byte of the payload descriptor has value 0.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         * @return `true` if the B bit from the first byte of the payload descriptor has value 0, false otherwise.
         */
        fun isStartOfFrame(buf: ByteArray, off: Int, len: Int): Boolean {
            // Check if this is the start of a VP9 layer frame in the payload descriptor.
            return isValid(buf, off, len) && buf[off].toInt() and B_BIT.toInt() != 0
        }

        /**
         * Returns `true` if the E bit from the first byte of the payload descriptor has value 0.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         * @return `true` if the E bit from the first byte of the payload descriptor has value 0, false otherwise.
         */
        fun isEndOfFrame(buf: ByteArray, off: Int, len: Int): Boolean {
            // Check if this is the end of a VP9 layer frame in the payload descriptor.
            return isValid(buf, off, len) && buf[off].toInt() and E_BIT.toInt() != 0
        }

        /**
         * Gets the temporal layer index (TID), if that's set.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         * @return the temporal layer index (TID), if that's set, -1 otherwise.
         */
        fun getTemporalLayerIndex(buf: ByteArray, off: Int, len: Int): Int {
            if (!isValid(buf, off, len) || buf[off].toInt() and L_BIT.toInt() == 0) {
                return -1
            }
            var loff = off + 1
            if (buf[off].toInt() and I_BIT.toInt() != 0) {
                loff++
                // check if it is an extended pid.
                if (buf[off + 1].toInt() and M_BIT.toInt() != 0) {
                    loff++
                }
            }
            return buf[loff].toInt() and TID_MASK.toInt() shr 5
        }

        /**
         * Gets the spatial layer index (SID), if that's set.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         * @return the spatial layer index (SID), if that's set, -1 otherwise.
         */
        fun getSpatialLayerIndex(buf: ByteArray, off: Int, len: Int): Int {
            if (!isValid(buf, off, len) || buf[off].toInt() and L_BIT.toInt() == 0) {
                return -1
            }
            var loff = off + 1
            if (buf[off].toInt() and I_BIT.toInt() != 0) {
                loff++
                // check if it is an extended pid.
                if (buf[off + 1].toInt() and M_BIT.toInt() != 0) {
                    loff++
                }
            }
            return buf[loff].toInt() and SID_MASK.toInt() shr 1
        }

        /**
         * Check if the current packet contains a key frame:
         *
         * A key picture is a picture whose base spatial layer frame is a key frame,
         * and which thus completely resets the encoder state. This packet will have:
         * a. P bit equal to zero,
         * b. SID or D bit (described below) equal to zero, and
         * c. B bit (described below) equal to 1.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         * @return true if the frame is a key frame, false otherwise.
         */
        fun isKeyFrame(buf: ByteArray, off: Int, len: Int): Boolean {
            // This packet will have its P bit equal to zero, SID or D bit (described below)
            // equal to zero, and B bit (described below) equal to 1
            if (!isValid(buf, off, len)) {
                return false
            }

            // P_BIT must be 0 and B_BIT is 1
            // L_BIT must be 1 to ensure we can do further checks for SID and D
            if (buf[off].toInt() and P_BIT.toInt() != 0 || buf[off].toInt() and B_BIT.toInt() == 0 || buf[off].toInt() and L_BIT.toInt() == 0) {
                return false
            }
            var loff = off + 1
            if (buf[off].toInt() and I_BIT.toInt() != 0) {
                loff += 1
                if (buf[off + 1].toInt() and (1 shl 7) != 0) {
                    // an extended pid.
                    loff += 1
                }
            }
            // SID or D bit equal to zero
            return buf[loff].toInt() and SID_MASK.toInt() shr 1 == 0 || buf[loff].toInt() and D_MASK.toInt() == 0
        }

        /**
         * Returns a simple Payload Descriptor, the 'start of a Frame' bit set
         * according to `startOfFrame`, and all other bits set to 0.
         *
         * @param startOfFrame create start of frame header with B-bit set and more header info
         * @return a simple Payload Descriptor, with 'start of a Frame' bit set
         * according to `startOfFrame`, and all other bits set to 0.
         */
        // SYNC_CODE /* equal to 0x498342 */
        // D/(DePacketizer.java:214)#doProcess: VP9: DePacketizer: VP9/RTP, fmtps={} 11 1032:
        //        Data: 00000000 00000000 00000000 8BCE9818 019202D0 01040182 49834200 19102CF4
        // D/(DePacketizer.java:214)#doProcess: VP9: DePacketizer: VP9/RTP, fmtps={} 3 1041:
        //        Data: 00000000 00000000 00000000 81CE98F4 531AE1CE 91275C60 5977EED3 5F205A9A
        // D/(DePacketizer.java:214)#doProcess: VP9: DePacketizer: VP9/RTP, fmtps={} 3 1041:
        //        Data: 00000000 00000000 00000000 81CE9876 68DA0B96 04CD716D FCA00918 6B855DE4
        // D/(DePacketizer.java:214)#doProcess: VP9: DePacketizer: VP9/RTP, fmtps={} 3 1041:
        //        Data: 00000000 00000000 00000000 85CE9889 CC970F97 DEF46D09 0DD8D5F8 44B2E8DC
        // D/(DePacketizer.java:214)#doProcess: VP9: DePacketizer: VP9/RTP, fmtps={} 11 1168:
        //        Data: 00000000 00000000 00000000 8BCE9918 019202D0 01040182 49834200 19102CF4
        // D/(DePacketizer.java:214)#doProcess: VP9: DePacketizer: VP9/RTP, fmtps={} 3 1176:
        //        Data: 00000000 00000000 00000000 81CE998B B67D0750 CE0C8CE9 82B77952 9C91D8E6
        // D/(DePacketizer.java:214)#doProcess: VP9: DePacketizer: VP9/RTP, fmtps={} 3 1176:
        //        Data: 00000000 00000000 00000000 81CE9904 6156411E 965433C6 8B122BBF 235A6944
        // D/(DePacketizer.java:214)#doProcess: VP9: DePacketizer: VP9/RTP, fmtps={} 3 1177:
        //        Data: 00000000 00000000 00000000 85CE99C5 A7447E9D E6AC11B1 3E7E75A7 6A0E68B2
        fun create(startOfFrame: Boolean, size: Dimension): ByteArray {
            val pd: ByteArray
            if (startOfFrame) {
                pid += 1
                pd = byteArrayOf(0x8B.toByte(), 0x00, 0x00, 0x18.toByte(), 0x00, 0x00, 0x00, 0x00, 0x01.toByte(), 0x04.toByte(), 0x01.toByte())
                pd[4] = (size.width and 0xFF00 shr 8).toByte()
                pd[5] = (size.width and 0xFF).toByte()
                pd[6] = (size.height and 0xFF00 shr 8).toByte()
                pd[7] = (size.height and 0xFF).toByte()
            } else {
                pd = byteArrayOf(0x81.toByte(), 0x00, 0x00)
            }
            pd[1] = (0x80 or (pid and 0x7F00 shr 8)).toByte()
            pd[2] = (pid and 0xFF).toByte()
            return pd
        }

        /**
         * The size in bytes of the Payload Descriptor at offset `offset` in `input`.
         *
         * @param baBuffer the `ByteArrayBuffer` that holds the VP9 payload descriptor.
         * @return The size in bytes of the Payload Descriptor at offset
         * `offset` in `input`, or -1 if the input is not a valid VP9 Payload Descriptor.
         */
        fun getSize(baBuffer: ByteArrayBuffer?): Int {
            return if (baBuffer == null) {
                -1
            } else getSize(baBuffer.buffer, baBuffer.offset, baBuffer.length)
        }

        /**
         * The size in bytes of the Payload Descriptor at offset `off` in `buf`.
         *
         * @param buf the byte buffer that contains the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload in the byte buffer.
         * @return The size in bytes of the Payload Descriptor at offset
         * `offset` in `input`, or -1 if the input is not a valid VP9 Payload Descriptor.
         */
        fun getSize(buf: ByteArray?, off: Int, len: Int): Int {
            // Y-bit from the Scalability Structure (SS)_header: spatial layer's frame resolution present flag.
            val Y_BIT = 0x10.toByte() // Each spatial layer's frame resolution present

            // Y bit from the ss_header: GOF description present flag.
            val G_BIT = 0x08.toByte()

            // Value N_S mask: the number of spatial layers in SS group.
            val N_S_MASK = 0xE0.toByte()
            if (!isValid(buf, off, len)) return -1
            var size = 1
            // Picture ID (PID) present
            if (buf!![off].toInt() and I_BIT.toInt() != 0) {
                size += if (buf[off + 1].toInt() and M_BIT.toInt() != 0) 2 else 1
            }

            // Layer indices size with F_BIT
            if (buf[off].toInt() and L_BIT.toInt() != 0) {
                size += if (buf[off].toInt() and F_BIT.toInt() != 0) 1 else 2
            }

            // Scalability structure (SS) data present
            if (buf[off].toInt() and V_BIT.toInt() != 0) {
                val ss_header = buf[off + size]

                // number of spatial layers present in the VP9 stream i.e. N_S + 1
                val ns_size = ss_header.toInt() and N_S_MASK.toInt() shr 5
                // Timber.d("ss_header: %s %s %s", size, Integer.toHexString(ss_header), ns_size);

                // frame resolution: width x height
                val y_size = if (ss_header.toInt() and Y_BIT.toInt() != 0) 4 else 0
                size += (ns_size + 1) * y_size + 1 // + V-Byte

                // PG description
                if (ss_header.toInt() and G_BIT.toInt() != 0) {
                    val ng_size = buf[off + size].toInt()
                    size += ng_size * 2 + 1 // N_G-Byte
                }
            }
            return size
        }

        /**
         * Determines whether the VP9 payload specified in the buffer that is
         * passed as an argument has a picture ID or not.
         *
         * @param buf the byte buffer that contains the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload in the byte buffer.
         * @return true if the VP9 payload contains a picture ID, false otherwise.
         */
        private fun hasPictureId(buf: ByteArray, off: Int, len: Int): Boolean {
            return isValid(buf, off, len) && buf[off].toInt() and I_BIT.toInt() != 0
        }

        /**
         * Determines whether the VP9 payload specified in the buffer that is
         * passed as an argument has an extended picture ID or not.
         *
         * @param buf the byte buffer that contains the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload in the byte buffer.
         * @return true if the VP9 payload contains an extended picture ID, false otherwise.
         */
        private fun hasExtendedPictureId(buf: ByteArray, off: Int, len: Int): Boolean {
            return hasPictureId(buf, off, len) && buf[off + 1].toInt() and M_BIT.toInt() != 0
        }

        /**
         * Gets the value of the PictureID field of a VP9 Payload Descriptor.
         *
         * @param buf the byte buffer that contains the VP9 payload.
         * @param off he offset in the byte buffer where the VP9 payload starts.
         * @return the value of the PictureID field of a VP9 Payload Descriptor,
         * or -1 if the fields is not present.
         */
        fun getPictureId(buf: ByteArray?, off: Int): Int {
            if (buf == null
                    || !hasPictureId(buf, off, buf.size - off)) {
                return -1
            }
            val isLong = buf[off + 1].toInt() and M_BIT.toInt() != 0
            return if (isLong) buf[off + 1].toInt() and 0x7f shl 8 or (buf[off + 2].toInt() and 0xff) else buf[off + 1].toInt() and 0x7f
        }

        /**
         * Sets the extended picture ID for the VP9 payload specified in the
         * buffer that is passed as an argument. (cmeng: Need to update for VP9)
         *
         * @param buf the byte buffer that contains the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload in the byte buffer.
         * @return true if the operation succeeded, false otherwise.
         */
        fun setExtendedPictureId(buf: ByteArray, off: Int, len: Int, `val`: Int): Boolean {
            if (!hasExtendedPictureId(buf, off, len)) {
                return false
            }
            buf[off + 1] = (0x80 or (`val` shr 8 and 0x7F)).toByte()
            buf[off + 2] = (`val` and 0xFF).toByte()
            return true
        }

        /**
         * Sets the TL0PICIDX field for the VP9 payload specified in the buffer that is passed as an argument.
         *
         * @param buf the byte buffer that contains the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload in the byte buffer.
         * @return true if the operation succeeded, false otherwise.
         */
        fun setTL0PICIDX(buf: ByteArray, off: Int, len: Int, `val`: Int): Boolean {
            if (!isValid(buf, off, len) || buf[off].toInt() and F_BIT.toInt() != 0 || buf[off].toInt() and L_BIT.toInt() == 0) {
                return false
            }
            var offTL0PICIDX = 2
            if (buf[off + 1].toInt() and I_BIT.toInt() != 0) {
                offTL0PICIDX++
                if (buf[off + 1].toInt() and M_BIT.toInt() != 0) {
                    offTL0PICIDX++
                }
            }
            buf[off + offTL0PICIDX] = `val`.toByte()
            return true
        }

        /**
         * Returns `true` if the arguments specify a valid non-empty buffer.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         * @return `true` if the arguments specify a valid non-empty buffer.
         */
        fun isValid(buf: ByteArray?, off: Int, len: Int): Boolean {
            return buf != null && buf.size >= off + len && off > -1 && len > 0
        }

        /**
         * Return boolean indicates whether the non-reference bit is set.
         *
         * @param buf the byte buffer that holds the VP9 payload descriptor.
         * @param off the offset in the byte buffer where the payload descriptor starts.
         * @param len the length of the payload descriptor in the byte buffer.
         * @return true if the non-reference bit is NOT set, false otherwise.
         */
        fun isReference(buf: ByteArray, off: Int, len: Int): Boolean {
            return buf[off].toInt() and P_BIT.toInt() != 0 && buf[off].toInt() and F_BIT.toInt() != 0
        }

        /**
         * Gets the TL0PICIDX from the payload descriptor.
         *
         * @param buf the byte buffer that holds the VP9 payload descriptor.
         * @param off the offset in the byte buffer where the payload descriptor starts.
         * @param len the length of the payload descriptor in the byte buffer.
         * @return the TL0PICIDX from the payload descriptor.
         */
        private fun getTL0PICIDX(buf: ByteArray, off: Int, len: Int): Int {
            if (!isValid(buf, off, len) || buf[off].toInt() and F_BIT.toInt() != 0 || buf[off].toInt() and L_BIT.toInt() == 0) {
                return -1
            }
            var offTL0PICIDX = 2
            if (buf[off + 1].toInt() and I_BIT.toInt() != 0) {
                offTL0PICIDX++
                if (buf[off + 1].toInt() and M_BIT.toInt() != 0) {
                    offTL0PICIDX++
                }
            }
            return buf[off + offTL0PICIDX].toInt()
        }

        /**
         * Provides a string description of the VP9 descriptor that can be used for debugging purposes.
         *
         * @param buf the byte buffer that holds the VP9 payload descriptor.
         * @param off the offset in the byte buffer where the payload descriptor starts.
         * @param len the length of the payload descriptor in the byte buffer.
         * @return Descriptive string of the VP9 info
         */
        fun toString(buf: ByteArray, off: Int, len: Int): String {
            return "VP9PayloadDescriptor" +
                    "[size=" + getSize(buf, off, len) +
                    ", tid=" + getTemporalLayerIndex(buf, off, len) +
                    ", tl0picidx=" + getTL0PICIDX(buf, off, len) +
                    ", pid=" + Integer.toHexString(getPictureId(buf, off)) +
                    ", isExtended=" + hasExtendedPictureId(buf, off, len) +
                    ", hex=" + toHexString(buf, off, min(len, MAX_LENGTH), false) +
                    "]"
        }
    }

    /**
     * A class that represents the VP9 Payload Header structure defined in []//tools.ietf.org/html/rfc7741"">&quot;https://tools.ietf.org/html/rfc7741&quot;
     */
    object VP9PayloadHeader {
        /**
         * P bit of the Payload Descriptor.
         */
        private const val P_BIT = 0x01.toByte()

        /**
         * Returns true if the `P` (inverse key frame flag) field of the
         * VP9 Payload Header at offset `offset` in `input` is 0.
         *
         * @return true if the `P` (inverse key frame flag) field of the
         * VP9 Payload Header at offset `offset` in `input` is 0, false otherwise.
         */
        fun isKeyFrame(input: ByteArray, offset: Int): Boolean {
            // When set to 0 the current frame is a key frame.  When set to 1
            // the current frame is an inter-frame. Defined in [RFC6386]
            return input[offset].toInt() and P_BIT.toInt() == 0
        }
    }

    /**
     * A class represents a keyframe header structure (see RFC 6386, paragraph 9.1).
     */
    object VP9KeyframeHeader {
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
        var buff: ByteArray? = null

        /**
         * Length used.
         */
        var len = 0
    }

    companion object {
        private var pid = abs(Random().nextInt())

        /**
         * Returns true if the buffer contains a VP9 key frame at offset `offset`.
         *
         * @param buf the byte buffer to check
         * @param off the offset in the byte buffer where the actual data starts
         * @param len the length of the data in the byte buffer
         * @return true if the buffer contains a VP9 key frame at offset `offset`.
         */
        fun isKeyFrame(buf: ByteArray, off: Int, len: Int): Boolean {
            return VP9PayloadDescriptor.isKeyFrame(buf, off, len)
        }
    }
}