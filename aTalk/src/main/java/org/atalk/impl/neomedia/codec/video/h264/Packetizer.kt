/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video.h264

import net.sf.fmj.media.AbstractPacketizer
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl
import org.atalk.service.neomedia.codec.Constants
import java.awt.Dimension
import java.util.*
import javax.media.Buffer
import javax.media.Format
import javax.media.PlugIn
import javax.media.ResourceUnavailableException
import javax.media.format.VideoFormat

/**
 * Packetizes H.264 encoded data/NAL units into RTP packets in accord with RFC 3984
 * "RTP Payload Format for H.264 Video".
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
class Packetizer : AbstractPacketizer() {
    /**
     * The list of NAL units to be sent as payload in RTP packets.
     */
    private val nals: MutableList<ByteArray> = LinkedList()

    /**
     * The timeStamp of the RTP packets in which `nals` are to be sent.
     */
    private var nalsTimeStamp: Long = 0

    /**
     * The sequence number of the next RTP packet to be output by this `Packetizer`.
     */
    private var sequenceNumber = 0

    /**
     * Initializes a new `Packetizer` instance which is to packetize H.264 encoded data/NAL
     * units into RTP packets in accord with RFC 3984 "RTP Payload Format for H.264 Video".
     */
    init {
        inputFormats = JNIEncoder.SUPPORTED_OUTPUT_FORMATS
        inputFormat = null
        outputFormat = null
    }

    /**
     * Close this `Packetizer`.
     */
    @Synchronized
    override fun close() {
        if (opened) {
            opened = false
            super.close()
        }
    }

    /**
     * Gets the output formats matching a specific input format.
     *
     * @param input
     * the input format to get the matching output formats for
     * @return an array of output formats matching the specified input format
     */
    private fun getMatchingOutputFormats(input: Format): Array<Format> {
        val videoInput = input as VideoFormat
        val size = videoInput.size
        val frameRate = videoInput.frameRate
        val packetizationMode = getPacketizationMode(input)

        return arrayOf(ParameterizedVideoFormat(
            Constants.H264_RTP,
            size,
            Format.NOT_SPECIFIED,
            Format.byteArray,
            frameRate,
            ParameterizedVideoFormat.toMap(
                VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP,
                packetizationMode))
        )
    }

    /**
     * Gets the value of the `packetization-mode` format parameter assigned by a specific
     * `Format`.
     *
     * @param format
     * the `Format` which assigns a value to the `packetization-mode` format
     * parameter
     * @return the value of the `packetization-mode` format parameter assigned by the
     * specified `format`
     */
    private fun getPacketizationMode(format: Format): String {
        var packetizationMode: String? = null
        if (format is ParameterizedVideoFormat) packetizationMode = format.getFormatParameter(
            VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP)
        if (packetizationMode == null) packetizationMode = "0"
        return packetizationMode
    }

    /**
     * Return the list of formats supported at the output.
     *
     * @param `in`
     * input `Format` to determine corresponding output `Format/code>s
     * @return array of formats supported at output
    ` */
    override fun getSupportedOutputFormats(format: Format?): Array<Format> {
        // null input format
        if (format == null) return SUPPORTED_OUTPUT_FORMATS

        // mismatch input format
        return when {
            format !is VideoFormat || (null == AbstractCodec2.matches(format, inputFormats)) -> ArrayList<Format>().toTypedArray()
            else -> getMatchingOutputFormats(format)
        }
    }

    /**
     * Open this `Packetizer`.
     *
     * @throws ResourceUnavailableException
     * if something goes wrong during initialization of the Packetizer.
     */
    @Synchronized
    @Throws(ResourceUnavailableException::class)
    override fun open() {
        if (!opened) {
            nals.clear()
            sequenceNumber = 0
            super.open()
            opened = true
        }
    }

    /**
     * Packetizes a specific NAL unit of H.264 encoded data so that it becomes ready to be sent as
     * the payload of RTP packets. If the specified NAL unit does not fit into a single RTP
     * packet i.e. will not become a "Single NAL Unit Packet", splits it into "Fragmentation
     * Units (FUs)" of type FU-A.
     *
     * @param nal
     * the bytes which contain the NAL unit of H.264 encoded data to be packetized
     * @param nalOfs
     * the offset in `nal` at which the NAL unit of H.264 encoded data to be
     * packetized begins
     * @param nalLen
     * the length in `nal` beginning at `nalOffset` of the NAL unit of H.264
     * encoded data to be packetized
     * @return `true` if at least one RTP packet payload has been packetized i.e. prepared
     * for sending; otherwise, `false`
     */
    private fun packetizeNAL(nal: ByteArray, nalOfs: Int, nalLen: Int): Boolean {
        /*
		 * If the NAL fits into a "Single NAL Unit Packet", it's already packetized.
		 */
        var nalOffset = nalOfs
        var nalLength = nalLen
        if (nalLength <= MAX_PAYLOAD_SIZE) {
            val singleNALUnitPacket = ByteArray(nalLength)
            System.arraycopy(nal, nalOffset, singleNALUnitPacket, 0, nalLength)
            return nals.add(singleNALUnitPacket)
        }

        // Otherwise, split it into "Fragmentation Units (FUs)".
        val octet = nal[nalOffset]
        val forbidden_zero_bit = octet.toInt() and 0x80
        val nri = octet.toInt() and 0x60
        val nal_unit_type = octet.toInt() and 0x1F
        val fuIndicator = (0xFF and (forbidden_zero_bit or nri
                or 28 /* nal_unit_type FU-A */)).toByte()
        var fuHeader = (0xFF and (0x80 /* Start bit */
                or 0 /* End bit */
                or 0 /* Reserved bit */
                or nal_unit_type)).toByte()
        nalOffset++
        nalLength--
        val maxFUPayloadLength = MAX_PAYLOAD_SIZE - 2 /* FU indicator & FU header */
        var nalsAdded = false
        while (nalLength > 0) {
            var fuPayloadLength: Int
            if (nalLength > maxFUPayloadLength) fuPayloadLength = maxFUPayloadLength
            else {
                fuPayloadLength = nalLength
                fuHeader = (fuHeader.toInt() or 0x40).toByte() // Turn on the End bit.
            }

            /*
			 * Tests with Asterisk suggest that the fragments of a fragmented NAL unit must be
			 * with one and the same size. There is also a similar question on the x264-devel
			 * mailing list but, unfortunately, it is unanswered.
			 */
            val fua = ByteArray(2 /* FU indicator & FU header */ + maxFUPayloadLength)
            fua[0] = fuIndicator
            fua[1] = fuHeader
            System.arraycopy(nal, nalOffset, fua, 2, fuPayloadLength)
            nalOffset += fuPayloadLength
            nalLength -= fuPayloadLength
            nalsAdded = nals.add(fua) || nalsAdded
            fuHeader = (fuHeader.toInt() and 0x80.inv()).toByte() // Turn off the Start bit.
        }
        return nalsAdded
    }

    /**
     * Processes (packetize) a buffer.
     *
     * @param inBuffer
     * input buffer
     * @param outBuffer
     * output buffer
     * @return `BUFFER_PROCESSED_OK` if buffer has been successfully processed
     */
    override fun process(inBuffer: Buffer, outBuffer: Buffer): Int {
        // if there are some nals we check and send them
        if (nals.size > 0) {
            val nal = nals.removeAt(0)

            // Send the NAL.
            outBuffer.data = nal
            outBuffer.length = nal.size
            outBuffer.offset = 0
            outBuffer.timeStamp = nalsTimeStamp
            outBuffer.sequenceNumber = sequenceNumber++.toLong()

            // If there are other NALs, send them as well.
            return if (nals.size > 0) PlugIn.BUFFER_PROCESSED_OK or PlugIn.INPUT_BUFFER_NOT_CONSUMED
            else {
                var flags = outBuffer.flags or Buffer.FLAG_RTP_MARKER

                /*
				 * It's the last NAL of the current frame so mark it. In order to (at least
				 * partially) support feeding this Packetizer one NAL at a time, do NOT always
				 * mark it i.e. the NALs with a value for nal_unit_type which signals that they
				 * cannot be the last NALs in an access unit should probably NOT be marked anyway.
				 */
                if (nal.isNotEmpty()) {
                    var nal_unit_type = nal[0].toInt() and 0x1F
                    if (nal_unit_type == 28 && nal.size > 1) {
                        val fuHeader = nal[1]
                        if (fuHeader.toInt() and 0x40 /* End bit */ == 0) {
                            /*
							 * A FU-A without the End bit cannot possibly be the last NAL unit of
							 * an access unit.
							 */
                            flags = flags and Buffer.FLAG_RTP_MARKER.inv()
                        }
                        else nal_unit_type = fuHeader.toInt() and 0x1F
                    }
                    when (nal_unit_type) {
                        6, 7, 8, 9 -> flags = flags and Buffer.FLAG_RTP_MARKER.inv()
                    }
                }
                outBuffer.flags = flags
                PlugIn.BUFFER_PROCESSED_OK
            }
        }
        if (isEOM(inBuffer)) {
            propagateEOM(outBuffer)
            reset()
            return PlugIn.BUFFER_PROCESSED_OK
        }
        if (inBuffer.isDiscard) {
            outBuffer.isDiscard = true
            reset()
            return PlugIn.BUFFER_PROCESSED_OK
        }
        val inFormat = inBuffer.format
        if (inFormat != inputFormat && !inFormat.matches(inputFormat)) setInputFormat(inFormat)
        val inLength = inBuffer.length

        /*
		 * We need 3 bytes for start_code_prefix_one_3bytes and at least 1 byte for the NAL unit
		 * i.e. its octet serving as the payload header.
		 */
        if (inLength < 4) {
            outBuffer.isDiscard = true
            reset()
            return PlugIn.BUFFER_PROCESSED_OK
        }
        val inData = inBuffer.data as ByteArray
        val inOffset = inBuffer.offset
        var nalsAdded = false

        /*
		 * Split the H.264 encoded data into NAL units. Each NAL unit begins with
		 * start_code_prefix_one_3bytes. Refer to "B.1 Byte stream NAL unit syntax and semantics"
		 * of "ITU-T Rec. H.264 Advanced video coding for generic audiovisual services" for
		 * further details.
		 */
        val endIndex = inOffset + inLength
        var beginIndex = ff_avc_find_startcode(inData, inOffset, endIndex)
        if (beginIndex < endIndex) {
            beginIndex += 3
            var nextBeginIndex = 0
            while (beginIndex < endIndex && ff_avc_find_startcode(
                        inData, beginIndex,
                        endIndex).also { nextBeginIndex = it } <= endIndex) {
                var nalLength = nextBeginIndex - beginIndex

                // Discard any trailing_zero_8bits.
                while (nalLength > 0 && inData[beginIndex + nalLength - 1].toInt() == 0) {
                    nalLength--
                }
                if (nalLength > 0) nalsAdded = packetizeNAL(inData, beginIndex, nalLength) || nalsAdded
                beginIndex = nextBeginIndex + 3
            }
        }
        nalsTimeStamp = inBuffer.timeStamp
        return if (nalsAdded) process(inBuffer, outBuffer) else PlugIn.OUTPUT_BUFFER_NOT_FILLED
    }

    /**
     * Sets the input format.
     *
     * @param `in`
     * format to set
     * @return format
     */
    override fun setInputFormat(format: Format): Format? {
        /*
		 * Return null if the specified input Format is incompatible with this Packetizer.
		 */
        if (format !is VideoFormat
                || null == AbstractCodec2.matches(format, inputFormats)) return null
        inputFormat = format
        return format
    }

    /**
     * Sets the `Format` in which this `Codec` is to output media data.
     *
     * @param format
     * the `Format` in which this `Codec` is to output media data
     * @return the `Format` in which this `Codec` is currently configured to output
     * media data or `null` if `format` was found to be incompatible with this
     * `Codec`
     */
    override fun setOutputFormat(format: Format): Format? {
        /*
		 * Return null if the specified output Format is incompatible with this Packetizer.
		 */
        if (format !is VideoFormat || null == AbstractCodec2.matches(format, getMatchingOutputFormats(inputFormat))) return null
        /*
		 * A Packetizer translates raw media data in RTP payloads. Consequently, the size of the
		 * output is equal to the size of the input.
		 */
        var size: Dimension? = null
        if (inputFormat != null) size = (inputFormat as VideoFormat).size
        if (size == null && format.matches(outputFormat)) size = (outputFormat as VideoFormat).size
        var fmtps: MutableMap<String, String>? = null
        if (format is ParameterizedVideoFormat) fmtps = format.formatParameters
        if (fmtps == null) fmtps = HashMap()
        if (fmtps[VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP] == null) {
            fmtps[VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP] = getPacketizationMode(inputFormat)
        }
        outputFormat = ParameterizedVideoFormat(
            format.encoding,
            size,
            Format.NOT_SPECIFIED,  /* maxDataLength */
            Format.byteArray,
            format.frameRate,
            fmtps)

        // Return the outputFormat which is actually set.
        return outputFormat
    }

    companion object {
        /**
         * Maximum payload size without the headers.
         */
        const val MAX_PAYLOAD_SIZE = 1024

        /**
         * Name of the plugin.
         */
        const val name = "H264 Packetizer"

        /**
         * The `Formats` supported by `Packetizer` instances as output.
         */
        val SUPPORTED_OUTPUT_FORMATS = arrayOf<Format>(
            ParameterizedVideoFormat(
                Constants.H264_RTP,
                VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP,
                "0"),

            ParameterizedVideoFormat(
                Constants.H264_RTP,
                VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP,
                "1")
        )

        /**
         * Finds the index in `byteStream` at which the start_code_prefix_one_3bytes of a NAL
         * unit begins.
         *
         * @param byteStream
         * the H.264 encoded byte stream composed of NAL units in which the index of the
         * beginning of the start_code_prefix_one_3bytes of a NAL unit is to be found
         * @param beginId
         * the inclusive index in `byteStream` at which the search is to begin
         * @param endIndex
         * the exclusive index in `byteStream` at which the search is to end
         * @return the index in `byteStream` at which the start_code_prefix_one_3bytes of a NAL
         * unit begins if it is found; otherwise, `endIndex`
         */
        private fun ff_avc_find_startcode(byteStream: ByteArray, beginId: Int, endIndex: Int): Int {
            var beginIndex = beginId
            while (beginIndex < endIndex - 3) {
                if (byteStream[beginIndex].toInt() == 0 && byteStream[beginIndex + 1].toInt() == 0 && byteStream[beginIndex + 2].toInt() == 1) {
                    return beginIndex
                }
                beginIndex++
            }
            return endIndex
        }
    }
}