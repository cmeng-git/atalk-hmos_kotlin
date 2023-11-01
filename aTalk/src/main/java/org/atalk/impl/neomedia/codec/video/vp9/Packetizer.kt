/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.atalk.impl.neomedia.codec.video.vp9.DePacketizer.VP9PayloadDescriptor
import org.atalk.service.neomedia.codec.Constants
import timber.log.Timber
import javax.media.Buffer
import javax.media.format.VideoFormat
import kotlin.math.min

/**
 * Packetizes VP9 encoded frames in accord with
 * See []//tools.ietf.org/html/draft-ietf-payload-vp9-15"">&quot;https://tools.ietf.org/html/draft-ietf-payload-vp9-15&quot;
 *
 * Uses the simplest possible scheme, only splitting large packets. Extended
 * bits are never added, and PartID is always set to 0. The only bit that changes
 * is the Start of Partition bit, which is set only for the first packet encoding a frame.
 *
 * @author Eng Chong Meng
 */
class Packetizer : AbstractCodec2("VP9 Packetizer", VideoFormat::class.java, arrayOf(VideoFormat(Constants.VP9_RTP))) {
    /**
     * Whether this is the first packet from the frame.
     */
    private var firstPacket = true

    /**
     * Initializes a new `Packetizer` instance.
     */
    init {
        inputFormats = arrayOf(VideoFormat(Constants.VP9))
    }

    /**
     * {@inheritDoc}
     */
    override fun doClose() {}

    /**
     * {@inheritDoc}
     */
    override fun doOpen() {
        Timber.log(TimberLog.FINER, "Opened VP9 packetizer")
    }

    /**
     * {@inheritDoc}
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        var inLen = 0
        if (inBuf.isDiscard || inBuf.length.also { inLen = it } == 0) {
            // Timber.d("VP9 Packetizer: %s %s", inputBuffer.isDiscard(), inputBuffer.getLength());
            outBuf.isDiscard = true
            return BUFFER_PROCESSED_OK
        }

        // The input will fit in a single packet
        val inOff = inBuf.offset
        val len = min(inLen, MAX_SIZE)
        val output: ByteArray
        var offset = VP9PayloadDescriptor.MAX_LENGTH
        output = validateByteArraySize(outBuf, offset + len, true)
        System.arraycopy(inBuf.data as ByteArray, inOff, output, offset, len)

        // get the payload descriptor and copy it to the output
        val pd = VP9PayloadDescriptor.create(firstPacket, (inBuf.format as VideoFormat).size)
        System.arraycopy(pd, 0, output, offset - pd.size, pd.size)
        offset -= pd.size

        // set up the output buffer
        outBuf.format = VideoFormat(Constants.VP9_RTP)
        outBuf.offset = offset
        outBuf.length = len + pd.size

        // Timber.d("VP9 Packetizer: inLen: %s; pdMaxLen: %s; offset: %s;\ndata: %s",
        //        inLen, pdMaxLen, offset, bytesToHex(output, 32));
        return if (inLen <= MAX_SIZE) {
            firstPacket = true
            outBuf.flags = outBuf.flags or Buffer.FLAG_RTP_MARKER
            BUFFER_PROCESSED_OK
        } else {
            firstPacket = false
            inBuf.length = inLen - MAX_SIZE
            inBuf.offset = inOff + MAX_SIZE
            INPUT_BUFFER_NOT_CONSUMED
        }
    }

    companion object {
        /**
         * Maximum size of packets (excluding the payload descriptor and any other headers (RTP, UDP))
         */
        private const val MAX_SIZE = 1350
    }
}