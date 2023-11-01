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
package org.atalk.impl.neomedia.codec.video.h264

import okhttp3.internal.notifyAll
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.impl.neomedia.codec.FFmpeg
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl
import org.atalk.service.neomedia.codec.Constants
import org.atalk.service.neomedia.control.KeyFrameControl
import timber.log.Timber
import java.util.*
import javax.media.Buffer
import javax.media.Format
import javax.media.PlugIn
import javax.media.ResourceUnavailableException
import javax.media.format.VideoFormat
import kotlin.collections.ArrayList
import kotlin.math.max

/**
 * Implements `Codec` to represent a depacketizer of H.264 RTP packets into
 * Network Abstraction Layer (NAL) units.
 *
 * @author Lyubomir Marinov
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class DePacketizer : AbstractCodec2("H264 DePacketizer", VideoFormat::class.java, arrayOf(VideoFormat(Constants.H264))) {
    /**
     * The indicator which determines whether this `DePacketizer` has successfully
     * processed an RTP packet with payload representing a "Fragmentation Unit (FU)" with its
     * Start bit set and has not encountered one with its End bit set.
     */
    private var fuaStartedAndNotEnded = false

    /**
     * The `KeyFrameControl` used by this `DePacketizer` to control its key frame-related logic.
     */
    private var keyFrameControl: KeyFrameControl? = null

    /**
     * The time stamp of the last received key frame.
     */
    private var lastKeyFrameTime: Long = -1

    /**
     * The time of the last request for a key frame from the remote peer associated with
     * [.keyFrameControl] performed by this `DePacketizer`.
     */
    private var lastRequestKeyFrameTime: Long = -1

    /**
     * Keeps track of last (input) sequence number in order to avoid inconsistent data.
     */
    private var lastSequenceNumber: Long = -1

    /**
     * The `nal_unit_type` as defined by the ITU-T Recommendation for H.264 of the last
     * NAL unit given to this `DePacketizer` for processing. In the case of processing a
     * fragmentation unit, the value is equal to the `nal_unit_type` of the fragmented NAL unit.
     */
    private var nal_unit_type = 0

    /**
     * The size of the padding at the end of the output data of this `DePacketizer`
     * expected by the H.264 decoder.
     */
    private val outputPaddingSize: Int = FFmpeg.FF_INPUT_BUFFER_PADDING_SIZE

    /**
     * The indicator which determines whether this `DePacketizer` is to request a key
     * frame from the remote peer associated with [.keyFrameControl].
     */
    private var requestKeyFrame = false

    /**
     * The `Thread` which is to asynchronously request key frames frozm
     * the remote peer associated with [.keyFrameControl] on behalf of
     * this `DePacketizer` and in accord with [.requestKeyFrame].
     */
    private var requestKeyFrameThread: Thread? = null

    /**
     * Initializes a new `DePacketizer` instance which is to depacketize H.264 RTP packets into NAL units.
     */
    init {
        val inputFormats = ArrayList<Format>()
        inputFormats.add(VideoFormat(Constants.H264_RTP))
        /*
         * Apart from the generic Constants.H264_RTP VideoFormat, add the possible respective
         * ParameterizedVideoFormats because ParameterizedVideoFormat will not match every
         * VideoFormat due to the fact that a missing packetization-mode format parameter is
         * interpreted as having a value of 0.
         */
        Collections.addAll(inputFormats, *Packetizer.SUPPORTED_OUTPUT_FORMATS)
        this.inputFormats = inputFormats.toTypedArray()
    }

    /**
     * Extracts a fragment of a NAL unit from a specific FU-A RTP packet payload.
     *
     * @param in the payload of the RTP packet from which a FU-A fragment of a NAL unit is to be extracted
     * @param inOff the offset in `in` at which the payload begins
     * @param inLen the length of the payload in `in` beginning at `inOffset`
     * @param outBuffer the `Buffer` which is to receive the extracted FU-A fragment of a NAL unit
     * @return the flags such as `BUFFER_PROCESSED_OK` and
     * `OUTPUT_BUFFER_NOT_FILLED` to be returned by [.process]
     */
    private fun dePacketizeFUA(`in`: ByteArray, inOff: Int, inLen: Int, outBuffer: Buffer): Int {
        var inOffset = inOff
        var inLength = inLen
        val fu_indicator = `in`[inOffset]
        inOffset++
        inLength--
        val fu_header = `in`[inOffset]
        inOffset++
        inLength--
        val nal_unit_type = fu_header.toInt() and 0x1F
        this.nal_unit_type = nal_unit_type
        val start_bit = fu_header.toInt() and 0x80 != 0
        val end_bit = fu_header.toInt() and 0x40 != 0
        var outOffset = outBuffer.offset
        var newOutLength = inLength
        val octet: Int
        if (start_bit) {
            /*
             * The Start bit and End bit MUST NOT both be set in the same FU header.
             */
            if (end_bit) {
                outBuffer.isDiscard = true
                return PlugIn.BUFFER_PROCESSED_OK
            }
            fuaStartedAndNotEnded = true
            newOutLength += H264.NAL_PREFIX.size + 1 /* octet */
            octet = (fu_indicator.toInt() and 0xE0 /* forbidden_zero_bit & NRI */
                    or nal_unit_type)
        }
        else if (!fuaStartedAndNotEnded) {
            outBuffer.isDiscard = true
            return PlugIn.BUFFER_PROCESSED_OK
        }
        else {
            val outLength = outBuffer.length
            outOffset += outLength
            newOutLength += outLength
            octet = 0 // Ignored later on.
        }
        val out: ByteArray = validateByteArraySize(
            outBuffer, outBuffer.offset + newOutLength + outputPaddingSize, true)
        if (start_bit) {
            // Copy in the NAL start sequence and the (reconstructed) octet.
            System.arraycopy(H264.NAL_PREFIX, 0, out, outOffset, H264.NAL_PREFIX.size)
            outOffset += H264.NAL_PREFIX.size
            out[outOffset] = (octet and 0xFF).toByte()
            outOffset++
        }
        System.arraycopy(`in`, inOffset, out, outOffset, inLength)
        outOffset += inLength
        padOutput(out, outOffset)
        outBuffer.length = newOutLength
        return if (end_bit) {
            fuaStartedAndNotEnded = false
            PlugIn.BUFFER_PROCESSED_OK
        }
        else PlugIn.OUTPUT_BUFFER_NOT_FILLED
    }

    /**
     * Extract a single (complete) NAL unit from RTP payload.
     *
     * @param nal_unit_type unit type of NAL
     * @param in the payload of the RTP packet
     * @param inOffset the offset in `in` at which the payload begins
     * @param inLength the length of the payload in `in` beginning at `inOffset`
     * @param outBuffer the `Buffer` which is to receive the extracted NAL unit
     * @return the flags such as `BUFFER_PROCESSED_OK` and
     * `OUTPUT_BUFFER_NOT_FILLED` to be returned by [.process]
     */
    private fun dePacketizeSingleNALUnitPacket(
            nal_unit_type: Int, `in`: ByteArray, inOffset: Int, inLength: Int, outBuffer: Buffer,
    ): Int {
        this.nal_unit_type = nal_unit_type
        var outOffset = outBuffer.offset
        val newOutLength = H264.NAL_PREFIX.size + inLength
        val out: ByteArray = validateByteArraySize(
            outBuffer, outOffset + newOutLength + outputPaddingSize, true)
        System.arraycopy(H264.NAL_PREFIX, 0, out, outOffset, H264.NAL_PREFIX.size)
        outOffset += H264.NAL_PREFIX.size
        System.arraycopy(`in`, inOffset, out, outOffset, inLength)
        outOffset += inLength
        padOutput(out, outOffset)
        outBuffer.length = newOutLength
        return PlugIn.BUFFER_PROCESSED_OK
    }

    /**
     * Close the `Codec`.
     */
    @Synchronized
    override fun doClose() {
        // If requestKeyFrameThread is running, tell it to perish.
        requestKeyFrameThread = null
        notifyAll()
    }

    /**
     * Opens this `Codec` and acquires the resources that it needs to
     * operate. A call to [PlugIn.open] on this instance will result in
     * a call to `doOpen` only if AbstractCodec.opened is
     * `false`. All required input and/or output formats are assumed to
     * have been set on this `Codec` before `doOpen` is called.
     *
     * @throws ResourceUnavailableException if any of the resources that this
     * `Codec` needs to operate cannot be acquired
     */
    @Synchronized
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        fuaStartedAndNotEnded = false
        lastKeyFrameTime = -1
        lastRequestKeyFrameTime = -1
        lastSequenceNumber = -1
        nal_unit_type = UNSPECIFIED_NAL_UNIT_TYPE
        requestKeyFrame = false
        requestKeyFrameThread = null
    }

    /**
     * Processes (depacketizes) a buffer.
     *
     * @param inBuf input buffer
     * @param outBuf output buffer
     * @return `BUFFER_PROCESSED_OK` if buffer has been successfully processed
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        /*
         * We'll only be depacketizing, we'll not act as an H.264 parser. Consequently, we'll
         * only care about the rules of packetizing/depacketizing. For example, we'll have to
         * make sure that no packets are lost and no other packets are received when
         * depacketizing FU-A Fragmentation Units (FUs).
         */
        val sequenceNumber = inBuf.sequenceNumber
        var ret: Int
        var requestKeyFrame = lastKeyFrameTime == -1L
        if (lastSequenceNumber != -1L && sequenceNumber - lastSequenceNumber != 1L) {
            /*
             * Even if (the new) sequenceNumber is less than lastSequenceNumber,
             * we have to use it because the received sequence numbers may have
             * reached their maximum value and wrapped around starting from
             * their minimum value again.
             */
            Timber.log(TimberLog.FINER, "Dropped RTP packets up to sequenceNumber %s and continuing with sequenceNumber %s",
                lastSequenceNumber, sequenceNumber)

            /*
             * If a frame has been lost, then we may be in a need of a key frame.
             */
            requestKeyFrame = true
            ret = reset(outBuf)
            if (ret and PlugIn.OUTPUT_BUFFER_NOT_FILLED == 0) {
                /*
                 * TODO Do we have to reset the nal_unit_type field of this
                 * DePacketizer to UNSPECIFIED_NAL_UNIT_TYPE here? If ret contains
                 * INPUT_BUFFER_NOT_CONSUMED, it seems safe to not reset it because the input
                 * Buffer will be returned for processing during the next call.
                 */
                setRequestKeyFrame(requestKeyFrame)
                return ret
            }
        }

        /*
         * Ignore the RTP time stamp reported by JMF because it is not the actual RTP packet time
         * stamp send by the remote peer but some locally calculated JMF value.
         */
        lastSequenceNumber = sequenceNumber
        val `in` = inBuf.data as ByteArray
        val inOffset = inBuf.offset
        val octet = `in`[inOffset]

        /*
         * NRI equal to the binary value 00 indicates that the content of the NAL unit is not
         * used to reconstruct reference pictures for inter picture prediction. Such NAL units
         * can be discarded without risking the integrity of the reference pictures. However, it
         * is not the place of the DePacketizer to take the decision to discard them but of the
         * H.264 decoder.
         */

        /*
         * The nal_unit_type of the NAL unit given to this DePacketizer for processing. In the
         * case of processing a fragmentation unit, the value is equal to the nal_unit_type of
         * the fragmentation unit, not the fragmented NAL unit and is thus in contrast with the
         * value of the nal_unit_type field of this DePacketizer.
         */
        val nal_unit_type = octet.toInt() and 0x1F

        // Single NAL Unit Packet
        if (nal_unit_type in 1..23) {
            fuaStartedAndNotEnded = false
            ret = dePacketizeSingleNALUnitPacket(nal_unit_type, `in`, inOffset, inBuf.length, outBuf)
        }
        else if (nal_unit_type == 28) { // FU-A Fragmentation unit (FU)
            ret = dePacketizeFUA(`in`, inOffset, inBuf.length, outBuf)
            if (outBuf.isDiscard) fuaStartedAndNotEnded = false
        }
        else {
            Timber.w("Dropping NAL unit of unsupported type %s", nal_unit_type)
            this.nal_unit_type = nal_unit_type
            fuaStartedAndNotEnded = false
            outBuf.isDiscard = true
            ret = PlugIn.BUFFER_PROCESSED_OK
        }
        outBuf.sequenceNumber = sequenceNumber

        /*
         * The RTP marker bit is set for the very last packet of the access unit indicated by the
         * RTP time stamp to allow an efficient playout buffer handling. Consequently, we have
         * to output it as well.
         */
        if (inBuf.flags and Buffer.FLAG_RTP_MARKER != 0) outBuf.flags = outBuf.flags or Buffer.FLAG_RTP_MARKER
        when (this.nal_unit_type) {
            5 -> {
                lastKeyFrameTime = System.currentTimeMillis()
                requestKeyFrame = false
            }
            7, 8 -> requestKeyFrame = false
            else -> {}
        }
        setRequestKeyFrame(requestKeyFrame)
        return ret
    }

    /**
     * {@inheritDoc}
     *
     *
     * Makes sure that the format parameters of a `ParameterizedVideoFormat` input which
     * are of no concern to this `DePacketizer` get passed on through the output to the next
     * `Codec` in the codec chain (i.e. `JNIDecoder`).
     */
    override fun getMatchingOutputFormats(inputFormat: Format): Array<Format> {
        val matchingOutputFormats = super.getMatchingOutputFormats(inputFormat) as Array<Format>

        if (matchingOutputFormats.isNotEmpty() && inputFormat is ParameterizedVideoFormat) {
            val fmtps = inputFormat.formatParameters

            if (fmtps.isNotEmpty()) {
                fmtps.remove(VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP)
                for (i in matchingOutputFormats.indices) {
                    matchingOutputFormats[i] = ParameterizedVideoFormat(Constants.H264, fmtps)
                }
            }
        }
        return matchingOutputFormats
    }

    /**
     * Appends [.outputPaddingSize] number of bytes to `out`
     * beginning at index `outOffset`. The specified `out` is
     * expected to be large enough to accommodate the mentioned number of bytes.
     *
     * @param out the buffer in which `outputPaddingSize` number of bytes are to be written
     * @param outOffset the index in `outOffset` at which the writing of
     * `outputPaddingSize` number of bytes is to begin
     */
    private fun padOutput(out: ByteArray, outOffset: Int) {
        Arrays.fill(out, outOffset, outOffset + outputPaddingSize, 0.toByte())
    }

    /**
     * Requests a key frame from the remote peer associated with this
     * `DePacketizer` using the logic of `DePacketizer`.
     *
     * @param urgent `true` if the caller has determined that the need
     * for a key frame is urgent and should not obey all constraints with
     * respect to time between two subsequent requests for key frames
     * @return `true` if a key frame was indeed requested in response to
     * the call; otherwise, `false`
     */
    @Synchronized
    fun requestKeyFrame(urgent: Boolean): Boolean {
        lastKeyFrameTime = -1
        setRequestKeyFrame(true)
        return true
    }

    /**
     * Resets the states of this `DePacketizer` and a specific output
     * `Buffer` so that they are ready to have this `DePacketizer`
     * process input RTP payloads. If the specified output `Buffer`
     * contains an incomplete NAL unit, its forbidden_zero_bit will be turned on
     * and the NAL unit in question will be output by this `DePacketizer`.
     *
     * @param outBuffer the output `Buffer` to be reset
     * @return the flags such as `BUFFER_PROCESSED_OK` and
     * `OUTPUT_BUFFER_NOT_FILLED` to be returned by
     * [.process]
     */
    private fun reset(outBuffer: Buffer): Int {
        /*
         * We need the octet at the very least. Additionally, it does not make
         * sense to output a NAL unit with zero payload because such NAL units
         * are only given meaning for the purposes of the network and not the H.264 decoder.
         */
        if (((OUTPUT_INCOMPLETE_NAL_UNITS
                        && fuaStartedAndNotEnded)
                        && outBuffer.length >= H264.NAL_PREFIX.size + 1 + 1)) {
            val outData = outBuffer.data
            if (outData is ByteArray) {
                val octetIndex = outBuffer.offset + H264.NAL_PREFIX.size
                outData[octetIndex] = (outData[octetIndex].toInt() or 0x80).toByte() // Turn on the forbidden_zero_bit.
                fuaStartedAndNotEnded = false
                return PlugIn.BUFFER_PROCESSED_OK or PlugIn.INPUT_BUFFER_NOT_CONSUMED
            }
        }
        fuaStartedAndNotEnded = false
        outBuffer.length = 0
        return PlugIn.OUTPUT_BUFFER_NOT_FILLED
    }

    /**
     * Requests key frames from the remote peer associated with
     * [.keyFrameControl] in [.requestKeyFrameThread].
     */
    private fun runInRequestKeyFrameThread() {
        while (true) {
            synchronized(this) {
                if (requestKeyFrameThread == Thread.currentThread()) {
                    val now = System.currentTimeMillis()

                    val timeout = when {
                        requestKeyFrame -> {
                            /*
                         * If we have received at least one key frame, we may
                         * receive a new one later. So allow a certain amount of
                         * time for the new key frame to arrive without DePacketizer
                         * requesting it.
                         */
                            val nextKeyFrameTime = lastKeyFrameTime + TIME_FROM_KEY_FRAME_TO_REQUEST_KEY_FRAME

                            if (now >= nextKeyFrameTime) {
                                /*
                             * In order to not have the requests for key frames
                             * overwhelm the remote peer, make sure two consecutive
                             * requests are separated by a certain amount of time.
                             */
                                val nextRequestKeyFrameTime = lastRequestKeyFrameTime + TIME_BETWEEN_REQUEST_KEY_FRAME

                                if (now >= nextRequestKeyFrameTime) {
                                    // Request a key frame from the remote peer now.
                                    -1
                                }
                                else {
                                    /*
                                 * Too little time has passed from our last attempt
                                 * to request a key frame from the remote peer. If
                                 * we do not wait, we risk intruding.
                                 */
                                    nextRequestKeyFrameTime - now
                                }
                            }
                            else {
                                /*
                             * Too little time has passed from the last receipt of a
                             * key frame to make us think that the remote peer will
                             * not send a key frame without us requesting it.
                             */
                                nextKeyFrameTime - now
                            }
                        }
                        else -> {
                            /*
                         * This DePacketizer has not expressed its desire to request
                         * a key frame from the remote peer so we will have to wait
                         * until it expresses the desire in question.
                         */
                            0L
                        }
                    }

                    if (timeout >= 0) {
                        try {
                            (this as Object).wait(timeout)
                        } catch (ignore: InterruptedException) {
                        }
                    }
                }
            }

            val keyFrameControl = this.keyFrameControl
            if (keyFrameControl != null) {
                val keyFrameRequesters = keyFrameControl.getKeyFrameRequesters()

                if (keyFrameRequesters != null) {
                    for (keyFrameRequester in keyFrameRequesters) {
                        try {
                            if (keyFrameRequester.requestKeyFrame())
                                break
                        } catch (e: Exception) {
                            // A KeyFrameRequester has malfunctioned, do not let it interfere with the others.
                        }
                    }
                }
            }
            lastRequestKeyFrameTime = System.currentTimeMillis()
        }
    }

    /**
     * Sets the `KeyFrameControl` to be used by this `DePacketizer` as a means of
     * control over its key frame-related logic.
     *
     * @param keyFrameControl the `KeyFrameControl` to be used by this `DePacketizer`
     * as a means of control over its key frame-related logic
     */
    fun setKeyFrameControl(keyFrameControl: KeyFrameControl) {
        this.keyFrameControl = keyFrameControl
    }

    /**
     * Sets the indicator which determines whether this `DePacketizer` is to request a key
     * frame from the remote peer associated with [.keyFrameControl].
     *
     * @param requestKeyFrame `true` if this `DePacketizer` is to request a key frame
     * from the remote peer associated with [.keyFrameControl]
     */
    @Synchronized
    private fun setRequestKeyFrame(requestKeyFrame: Boolean) {
        if (this.requestKeyFrame != requestKeyFrame) {
            this.requestKeyFrame = requestKeyFrame

            if (this.requestKeyFrame && requestKeyFrameThread == null) {
                requestKeyFrameThread = object : Thread() {
                    override fun run() {
                        try {
                            runInRequestKeyFrameThread()
                        } finally {
                            synchronized(this@DePacketizer) {
                                if (requestKeyFrameThread == currentThread())
                                    requestKeyFrameThread = null
                            }
                        }
                    }
                }
                requestKeyFrameThread!!.start()
            }
            notifyAll()
        }
    }

    companion object {
        /**
         * The indicator which determines whether incomplete NAL units are output
         * from the H.264 `DePacketizer` to the decoder. It is advisable to
         * output incomplete NAL units because the FFmpeg H.264 decoder is able to
         * decode them. If `false`, incomplete NAL units will be discarded
         * and, consequently, the video quality will be worse (e.g. if the last RTP
         * packet of a fragmented NAL unit carrying a keyframe does not arrive from
         * the network, the whole keyframe will be discarded and thus all NAL units
         * up to the next keyframe will be useless).
         */
        private const val OUTPUT_INCOMPLETE_NAL_UNITS = true

        /**
         * The interval of time in milliseconds between two consecutive requests for a key frame from
         * the remote peer associated with the [.keyFrameControl] of `DePacketizer`.
         */
        private const val TIME_BETWEEN_REQUEST_KEY_FRAME: Long = 500

        /**
         * The interval of time in milliseconds from the time of the last received
         * key frame to the time at which a key frame will be requested from the
         * remote peer associated with the [.keyFrameControl] of
         * `DePacketizer`. The value at the time of this writing is the
         * default time between two consecutive key frames generated by
         * [JNIDecoder] with an addition of a certain fraction of that time in
         * the role of a leeway to prevent `DePacketizer` from requesting key
         * frames from `JNIEncoder` in the scenario of perfect transmission.
         */
        private const val TIME_FROM_KEY_FRAME_TO_REQUEST_KEY_FRAME = JNIEncoder.DEFAULT_KEYINT * 4L / (JNIEncoder.DEFAULT_FRAME_RATE * 3L) * 1000L

        /**
         * The Unspecified `nal_unit_type` as defined by the ITU-T Recommendation for H.264.
         */
        private const val UNSPECIFIED_NAL_UNIT_TYPE = 0

        /**
         * Returns true if the buffer contains a H264 key frame at offset `offset`.
         *
         * @param buf the byte buffer to check
         * @param off the offset in the byte buffer where the actual data starts
         * @param len the length of the data in the byte buffer
         * @return true if the buffer contains a H264 key frame at offset `offset`.
         */
        fun isKeyFrame(buf: ByteArray?, off: Int, len: Int): Boolean {
            if (buf == null || buf.size < off + max(len, 1)) {
                return false
            }
            val nalType = buf[off].toInt() and H264.kTypeMask.toInt()
            // Single NAL Unit Packet
            return if (nalType == H264.kFuA.toInt()) {
                // Fragmented NAL units (FU-A).
                parseFuaNaluForKeyFrame(buf, off, len)
            }
            else {
                parseSingleNaluForKeyFrame(buf, off, len)
            }
        }

        /**
         * Checks if a a fragment of a NAL unit from a specific FU-A RTP packet payload is keyframe or not
         */
        private fun parseFuaNaluForKeyFrame(buf: ByteArray, off: Int, len: Int): Boolean {
            return if (len < H264.kFuAHeaderSize) {
                false
            }
            else buf[off + 1].toInt() and H264.kTypeMask.toInt() == H264.kIdr.toInt()
        }

        /**
         * Checks if a a fragment of a NAL unit from a specific FU-A RTP packet payload is keyframe or not
         */
        private fun parseSingleNaluForKeyFrame(buf: ByteArray, off: Int, len: Int): Boolean {
            val naluStart = off + H264.kNalHeaderSize
            val naluLength = len - H264.kNalHeaderSize
            var nalType = buf[off].toInt() and H264.kTypeMask.toInt()
            if (nalType == H264.kStapA.toInt()) {
                // Skip the StapA header (StapA nal type + length).
                if (len <= H264.kStapAHeaderSize) {
                    Timber.e("StapA header truncated.")
                    return false
                }
                if (!H264.verifyStapANaluLengths(buf, naluStart, naluLength)) {
                    Timber.e("StapA packet with incorrect NALU packet lengths.")
                    return false
                }
                nalType = buf[off + H264.kStapAHeaderSize].toInt() and H264.kTypeMask.toInt()
            }
            return nalType == H264.kIdr.toInt() || nalType == H264.kSps.toInt() || nalType == H264.kPps.toInt() || nalType == H264.kSei.toInt()
        }
    }
}