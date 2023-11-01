/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.rtpdumpfile

import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractVideoPullBufferStream
import timber.log.Timber
import java.io.IOException
import javax.media.Buffer
import javax.media.control.FormatControl
import javax.media.format.AudioFormat
import javax.media.format.VideoFormat

/**
 * Implements a `PullBufferStream` which read an rtpdump file to generate a RTP stream from
 * the payloads recorded in a rtpdump file.
 *
 * @author Thomas Kuntz
 */
class RtpdumpStream internal constructor(dataSource: DataSource, formatControl: FormatControl?) : AbstractVideoPullBufferStream<DataSource?>(dataSource, formatControl) {
    /**
     * The `RawPacketScheduler` responsible for throttling our RTP packet reading.
     */
    private val rawPacketScheduler: RawPacketScheduler

    /**
     * Boolean indicating if the last call to `doRead` return a marked rtp packet (to
     * know if `timestamp` needs to be updated).
     */
    private var lastReadWasMarked = true

    /**
     * The `RtpdumpFileReader` used by this stream to get the rtp payload.
     */
    private val rtpFileReader: RtpdumpFileReader

    /**
     * The timestamp to use for the timestamp of the next `Buffer` filled in
     * [.doRead]
     */
    private var timestamp = 0L

    /**
     * Initializes a new `RtpdumpStream` instance
     *
     *dataSource the `DataSource` which is creating the new instance so that it becomes one of its `streams`
     * formatControl the `FormatControl` of the new instance which is to specify the format in which
     * it is to provide its media data
     */
    init {
        /*
         * NOTE: We use the sampleRate or frameRate field of the format to piggyback the RTP clock
         * rate. See RtpdumpMediaDevice#createRtpdumpMediaDevice.
         */
        val clockRate = when (val format = format!!) {
            is AudioFormat -> {
                format.sampleRate.toLong()
            }
            is VideoFormat -> {
                format.frameRate.toLong()
            }
            else -> {
                Timber.w("Unknown format. Creating RtpdumpStream with clock rate 1 000 000 000.")
                (1000 * 1000 * 1000).toLong()
            }
        }
        rawPacketScheduler = RawPacketScheduler(clockRate)
        val rtpdumpFilePath = dataSource.locator.remainder
        rtpFileReader = RtpdumpFileReader(rtpdumpFilePath)
    }

    /**
     * Reads available media data from this instance into a specific `Buffer`.
     *
     * @param buffer the `Buffer` to write the available media data into
     * @throws IOException if an I/O error has prevented the reading of available media data from this instance
     * into the specified `Buffer`
     */
    @Throws(IOException::class)
    override fun doRead(buffer: Buffer) {
        var format = buffer.format
        if (format == null) {
            format = this.format
            if (format != null) buffer.format = format
        }
        val rtpPacket = rtpFileReader.getNextPacket(true)
        val data = rtpPacket.payload
        buffer.data = data
        buffer.offset = rtpPacket.offset
        buffer.length = rtpPacket.payloadLength
        buffer.flags = Buffer.FLAG_SYSTEM_TIME or Buffer.FLAG_LIVE_DATA
        if (lastReadWasMarked) {
            timestamp = System.nanoTime()
        }
        lastReadWasMarked = rtpPacket.isPacketMarked
        if (lastReadWasMarked) {
            buffer.flags = buffer.flags or Buffer.FLAG_RTP_MARKER
        }
        buffer.timeStamp = timestamp
        try {
            rawPacketScheduler.schedule(rtpPacket)
        } catch (ignore: InterruptedException) {
        }
    }
}