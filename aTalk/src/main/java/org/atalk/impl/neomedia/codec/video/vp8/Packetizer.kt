/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video.vp8

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.impl.neomedia.codec.video.vp8.DePacketizer.VP8PayloadDescriptor
import org.atalk.service.neomedia.codec.Constants
import timber.log.Timber
import javax.media.Buffer
import javax.media.format.VideoFormat
import kotlin.math.min

/**
 * Packetizes VP8 encoded frames in accord with
 * See []//tools.ietf.org/html/draft-ietf-payload-vp8-17"">&quot;https://tools.ietf.org/html/draft-ietf-payload-vp8-17&quot;
 *
 * Uses the simplest possible scheme, only splitting large packets. Extended
 * bits are never added, and PartID is always set to 0. The only bit that
 * changes is the Start of Partition bit, which is set only for the first packet
 * encoding a frame.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class Packetizer : AbstractCodec2("VP8 Packetizer", VideoFormat::class.java, arrayOf(VideoFormat(Constants.VP8_RTP))) {
    /**
     * Whether this is the first packet from the frame.
     */
    private var firstPacket = true

    /**
     * Initializes a new `Packetizer` instance.
     */
    init {
        inputFormats = arrayOf(VideoFormat(Constants.VP8))
    }

    /**
     * {@inheritDoc}
     */
    override fun doClose() {}

    /**
     * {@inheritDoc}
     */
    override fun doOpen() {
        Timber.log(TimberLog.FINER, "Opened VP8 packetizer")
    }

    /**
     * {@inheritDoc}
     */
    override fun doProcess(inputBuffer: Buffer, outputBuffer: Buffer): Int {
        var inLen = 0
        if (inputBuffer.isDiscard || inputBuffer.length.also { inLen = it } == 0) {
            outputBuffer.isDiscard = true
            return BUFFER_PROCESSED_OK
        }

        //The input will fit in a single packet
        val inOff = inputBuffer.offset
        val len = min(inLen, MAX_SIZE)
        val output: ByteArray
        var offset = VP8PayloadDescriptor.MAX_LENGTH
        output = validateByteArraySize(outputBuffer, offset + len, true)
        System.arraycopy(inputBuffer.data as ByteArray, inOff, output, offset, len)

        //get the payload descriptor and copy it to the output
        val pd = VP8PayloadDescriptor.create(firstPacket)
        System.arraycopy(pd, 0, output, offset - pd.size, pd.size)
        offset -= pd.size

        //set up the output buffer
        outputBuffer.format = VideoFormat(Constants.VP8_RTP)
        outputBuffer.offset = offset
        outputBuffer.length = len + pd.size
        return if (inLen <= MAX_SIZE) {
            firstPacket = true
            outputBuffer.flags = outputBuffer.flags or Buffer.FLAG_RTP_MARKER
            BUFFER_PROCESSED_OK
        } else {
            firstPacket = false
            inputBuffer.length = inLen - MAX_SIZE
            inputBuffer.offset = inOff + MAX_SIZE
            INPUT_BUFFER_NOT_CONSUMED
        }
    }

    companion object {
        /**
         * Maximum size of packets (excluding the payload descriptor and any other
         * headers (RTP, UDP))
         */
        private const val MAX_SIZE = 1350
    }
}