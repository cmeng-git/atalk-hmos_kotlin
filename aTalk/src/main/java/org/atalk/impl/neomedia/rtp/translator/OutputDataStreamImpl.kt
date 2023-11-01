/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.translator

import net.sf.fmj.media.rtp.RTCPHeader
import net.sf.fmj.media.rtp.RTPHeader
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.RTPConnectorOutputStream
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.RawPacket
import org.atalk.util.ConfigUtils.getBoolean
import org.atalk.util.RTPUtils.readInt
import org.atalk.util.RTPUtils.readUint16AsInt
import org.ice4j.util.QueueStatistics
import timber.log.Timber
import javax.media.Format
import javax.media.rtp.OutputDataStream

/**
 * Implements `OutputDataStream` for an `RTPTranslatorImpl`. The packets written into
 * `OutputDataStreamImpl` are copied into multiple endpoint `OutputDataStream`s.
 *
 * @author Lyubomir Marinov
 * @author Maryam Daneshi
 * @author George Politis
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
internal class OutputDataStreamImpl(
        private val connector: RTPConnectorImpl,
        /**
         * The indicator which determines whether RTP data (`true`) is written into this
         * `OutputDataStreamImpl` or RTP control i.e. RTCP (`false`).
         */
        private val _data: Boolean,
) : OutputDataStream, Runnable {
    private var closed = false

    /**
     * The indicator which determines whether the RTP header extension(s) are to be removed from
     * received RTP packets prior to relaying them. The default value is `false`.
     */
    private val _removeRTPHeaderExtensions = getBoolean(
            LibJitsi.configurationService, REMOVE_RTP_HEADER_EXTENSIONS_PNAME, false)

    /**
     * The `List` of `OutputDataStream`s into which this `OutputDataStream`
     * copies written data/packets. Implemented as a copy-on-write storage in order to reduce
     * synchronized blocks and deadlock risks. I do NOT want to use `CopyOnWriteArrayList`
     * because I want to
     * (1) avoid `Iterator`s and
     * (2) reduce synchronization. The access to
     * [._streams] is synchronized by [._streamsSyncRoot].
     */
    private var _streams = emptyList<OutputDataStreamDesc>()

    /**
     * The `Object` which synchronizes the access to [._streams].
     */
    private val _streamsSyncRoot = Any()
    private val writeQ = arrayOfNulls<RTPTranslatorBuffer>(WRITE_Q_CAPACITY)
    private var writeQHead = 0
    private var writeQLength = 0
    private var writeQStats: QueueStatistics? = null

    /**
     * The number of packets dropped because a packet was inserted while [.writeQ] was full.
     */
    private var numDroppedPackets = 0
    private var writeThread: Thread? = null

    init {
        writeQStats = if (TimberLog.isTraceEnable) {
            // writeQStats = QueueStatistics.get(getClass().getSimpleName()); // ice4j 2.0
            QueueStatistics(javaClass.simpleName + "-" + hashCode())
        } else {
            null
        }
    }

    /**
     * Adds a new `OutputDataStream` to the list of `OutputDataStream`s into which this
     * `OutputDataStream` copies written data/packets. If this instance contains the
     * specified `stream` already, does nothing.
     *
     * @param connectorDesc the endpoint `RTPConnector` which owns `stream`
     * @param stream the `OutputDataStream` to add to this instance
     */
    fun addStream(connectorDesc: RTPConnectorDesc, stream: OutputDataStream) {
        synchronized(_streamsSyncRoot) {

            // Prevent repetitions.
            for (streamDesc in _streams) {
                if (streamDesc.connectorDesc === connectorDesc && streamDesc.stream === stream) {
                    return
                }
            }

            // Add. Copy on write.
            val newStreams: MutableList<OutputDataStreamDesc> = ArrayList(_streams.size * 3 / 2 + 1)
            newStreams.addAll(_streams)
            newStreams.add(OutputDataStreamDesc(connectorDesc, stream))
            _streams = newStreams
        }
    }

    @Synchronized
    fun close() {
        closed = true
        writeThread = null
        (this as Object).notify()
    }

    @Synchronized
    private fun createWriteThread() {
        writeThread = Thread(this, javaClass.name)
        writeThread!!.isDaemon = true
        writeThread!!.start()
    }

    private fun doWrite(buf: ByteArray, off: Int, len: Int, format: Format?, exclusion: StreamRTPManagerDesc?): Int {
        var length = len
        val translator = translator ?: return 0

        // XXX The field _streams is explicitly implemented as a copy-on-write
        // storage in order to avoid synchronization and, especially, here where
        // I'm to invoke writes on multiple other OutputDataStreams.
        val streams = _streams
        var removeRTPHeaderExtensions = _removeRTPHeaderExtensions
        var written = 0

        // XXX I do NOT want to use an Iterator.
        var i = 0
        val end = streams.size
        while (i < end) {
            val s = streams[i]
            val streamRTPManager = s.connectorDesc.streamRTPManagerDesc
            if (streamRTPManager === exclusion) {
                ++i
                continue
            }
            var write: Boolean
            if (_data) {
                // TODO The removal of the RTP header extensions is an
                // experiment inspired by https://code.google.com/p/webrtc/issues/detail?id=1095
                // "Chrom WebRTC VP8 RTP packet retransmission does not follow RFC 4588"
                if (removeRTPHeaderExtensions) {
                    removeRTPHeaderExtensions = false
                    length = removeRTPHeaderExtensions(buf, off, length)
                }
                write = willWriteData(streamRTPManager, buf, off, length, format, exclusion)
            } else {
                write = willWriteControl(streamRTPManager, buf, off, length, format, exclusion)
            }
            if (write) {
                // Allow the RTPTranslatorImpl a final chance to filter out the
                // packet on a source-destination basis.
                write = translator.willWrite(exclusion, RawPacket(buf, off, length), streamRTPManager, _data)
            }
            if (write) {
                val w = s.stream.write(buf, off, length)
                if (written < w) written = w
            }
            ++i
        }
        return written
    }

    private val translator: RTPTranslatorImpl
        get() = connector.translator

    /**
     * Removes the `OutputDataStream`s owned by a specific `RTPConnector` from the list
     * of `OutputDataStream`s into which this `OutputDataStream` copies written data/packets.
     *
     * @param connectorDesc the `RTPConnector` that is the owner of the `OutputDataStream`s to remove
     * from this instance.
     */
    fun removeStreams(connectorDesc: RTPConnectorDesc) {
        synchronized(_streamsSyncRoot) {

            // Copy on write. Well, we aren't sure yet whether a write is going
            // to happen but it's the caller's fault if they ask this instance
            // to remove an RTPConnector which this instance doesn't contain.
            val newStreams: MutableList<OutputDataStreamDesc> = ArrayList(_streams)
            val i = newStreams.iterator()
            while (i.hasNext()) {
                if (i.next().connectorDesc === connectorDesc) i.remove()
            }
            _streams = newStreams
        }
    }

    override fun run() {
        try {
            do {
                var writeIndex: Int
                var buffer: ByteArray
                var exclusion: StreamRTPManagerDesc?
                var format: Format?
                var length: Int

                if (closed || Thread.currentThread() != writeThread) break
                synchronized(this) {
                    if (writeQLength < 1) {
                        var interrupted = false
                        try {
                            (this as Object).wait()
                        } catch (ie: InterruptedException) {
                            interrupted = true
                        }

                        if (interrupted) {
                            Thread.currentThread().interrupt()
                        } else {
                            // Do nothing
                        }

                    } else {
                        writeIndex = writeQHead
                        val write = writeQ[writeIndex]
                        buffer = write!!.data!!
                        write.data = null
                        exclusion = write.exclusion
                        write.exclusion = null
                        format = write.format
                        write.format = null
                        length = write.length
                        write.length = 0
                        writeQHead++
                        if (writeQHead >= writeQ.size) writeQHead = 0
                        writeQLength--
                        if (writeQStats != null) {
                            writeQStats!!.remove(System.currentTimeMillis())
                        }

                        try {
                            doWrite(buffer, 0, length, format, exclusion)
                        } finally {
                            val write = writeQ[writeIndex]
                            if (write != null && write.data == null) write.data = buffer
                        }
                    }
                }
            }
            while (true)
        } catch (t: Throwable) {
            Timber.e(t, "Failed to translate RTP packet")
            if (t is ThreadDeath) throw t
        } finally {
            synchronized(this) {
                if (Thread.currentThread() == writeThread) writeThread = null
                if (!closed && writeThread == null && writeQLength > 0) createWriteThread()
            }
        }
    }

    /**
     * Notifies this instance that a specific `byte` buffer will be written into the control
     * `OutputDataStream` of a specific `StreamRTPManagerDesc`.
     *
     * @param destination the `StreamRTPManagerDesc` which is the destination of the write
     * @param buffer the data to be written into `destination`
     * @param offset the offset in `buffer` at which the data to be written into `destination` starts
     * @param length the number of `byte`s in `buffer` beginning at `offset` which
     * constitute the data to the written into `destination`
     * @param format the FMJ `Format` of the data to be written into `destination`
     * @param exclusion the `StreamRTPManagerDesc` which is exclude from the write batch, possibly
     * because it is the cause of the write batch in the first place
     * @return `true` to write the specified data into the specified `destination` or
     * `false` to not write the specified data into the specified `destination`
     */
    private fun willWriteControl(
            destination: StreamRTPManagerDesc?, buffer: ByteArray?, offset: Int,
            length: Int, format: Format?, exclusion: StreamRTPManagerDesc?,
    ): Boolean {
        var write = true

        // Do the bytes in the specified buffer resemble (the header of) an RTCP packet?
        if (length >= 12 /* FB */) {
            val b0 = buffer!![offset]
            val v = b0.toInt() and 0xc0 ushr 6 /* version */
            if (v == RTCPHeader.VERSION) {
                val b1 = buffer[offset + 1]
                val pt = b1.toInt() and 0xff /* payload type */
                val fmt = b0.toInt() and 0x1f /* feedback message type */
                if (pt == 205 || pt == 206 /* PSFB */) {
                    // Verify the length field.
                    val rtcpLength = (readUint16AsInt(buffer, offset + 2) + 1) * 4
                    if (rtcpLength <= length) {
                        var ssrcOfMediaSource = 0
                        if (pt == 206 && fmt == 4) // FIR
                        {
                            if (rtcpLength < 20) {
                                // FIR messages are at least 20 bytes long
                                write = false
                            } else {
                                // FIR messages don't have a valid 'media source' field, use the
                                // SSRC from the first FCI entry instead
                                ssrcOfMediaSource = readInt(buffer, offset + 12)
                            }
                        } else {
                            ssrcOfMediaSource = readInt(buffer, offset + 8)
                        }
                        if (destination!!.containsReceiveSSRC(ssrcOfMediaSource)) {
                            if (TimberLog.isTraceEnable) {
                                val ssrcOfPacketSender = readInt(buffer, offset + 4)
                                val message = (javaClass.name + ".willWriteControl: FMT "
                                        + fmt + ", PT " + pt + ", SSRC of packet sender "
                                        + (ssrcOfPacketSender.toLong() and 0xffffffffL)
                                        + ", SSRC of media source "
                                        + (ssrcOfMediaSource.toLong() and 0xffffffffL))
                                Timber.log(TimberLog.FINER, "%s", message)
                            }
                        } else {
                            write = false
                        }
                    }
                }
            }
        }
        if (write && TimberLog.isTraceEnable) RTPTranslatorImpl.logRTCP(this, "doWrite", buffer, offset, length)
        return write
    }

    /**
     * Notifies this instance that a specific `byte` buffer will be written into the data
     * `OutputDataStream` of a specific `StreamRTPManagerDesc`.
     *
     * @param destination the `StreamRTPManagerDesc` which is the destination of the write
     * @param buf the data to be written into `destination`
     * @param off the offset in `buf` at which the data to be written into `destination` starts
     * @param len the number of `byte`s in `buf` beginning at `off` which
     * constitute the data to the written into `destination`
     * @param format the FMJ `Format` of the data to be written into `destination`
     * @param exclusion the `StreamRTPManagerDesc` which is exclude from the write batch, possibly
     * because it is the cause of the write batch in the first place
     * @return `true` to write the specified data into the specified `destination` or
     * `false` to not write the specified data into the specified `destination`
     */
    private fun willWriteData(
            destination: StreamRTPManagerDesc?, buf: ByteArray?, off: Int, len: Int,
            format: Format?, exclusion: StreamRTPManagerDesc?,
    ): Boolean {
        // Only write data packets to OutputDataStreams for which the associated MediaStream allows sending.
        if (!destination!!.streamRTPManager.mediaStream.direction!!.allowsSending()) {
            return false
        }
        if (format != null && len > 0) {
            var pt = destination.getPayloadType(format)
            if (pt == null && exclusion != null) {
                pt = exclusion.getPayloadType(format)
            }
            if (pt != null) {
                val ptByteIndex = off + 1
                buf!![ptByteIndex] = (buf[ptByteIndex].toInt() and 0x80 or (pt and 0x7f)).toByte()
            }
        }
        return true
    }

    override fun write(buf: ByteArray, off: Int, len: Int): Int {
        // FIXME It's unclear at the time of this writing why the method doWrite
        // is being invoked here and not the overloaded method write.
        return doWrite(buf, off, len,  /* format */null,  /* exclusion */null)
    }

    @Synchronized
    fun write(buf: ByteArray?, off: Int, len: Int, format: Format?, exclusion: StreamRTPManagerDesc?) {
        if (closed) return
        val writeIndex: Int

        if (writeQLength < writeQ.size) {
            writeIndex = (writeQHead + writeQLength) % writeQ.size
        } else {
            writeIndex = writeQHead
            writeQHead++
            if (writeQHead >= writeQ.size) writeQHead = 0
            writeQLength--
            if (writeQStats != null) {
                writeQStats!!.remove(System.currentTimeMillis())
            }
            numDroppedPackets++
            if (RTPConnectorOutputStream.logDroppedPacket(numDroppedPackets)) {
                Timber.w("Dropped %s packets hashCode = %s", numDroppedPackets, hashCode())
            }
        }

        var write = writeQ[writeIndex]
        if (write == null) {
            write = RTPTranslatorBuffer()
            writeQ[writeIndex] = write
        }
        var data = write.data
        if (data == null || data.size < len) {
            data = ByteArray(len)
            write.data = data
        }
        System.arraycopy(buf!!, off, data, 0, len)
        write.exclusion = exclusion
        write.format = format
        write.length = len
        writeQLength++
        if (writeQStats != null) {
            writeQStats!!.add(System.currentTimeMillis())
        }
        if (writeThread == null) createWriteThread() else (this as Object).notify()
    }

    /**
     * Writes an `RTCPFeedbackMessage` into a destination identified by specific `MediaStream`.
     *
     * @param controlPayload
     * @param destination
     * @return `true` if the `controlPayload` was written
     * into the `destination`; otherwise, `false`
     */
    fun writeControlPayload(controlPayload: Payload, destination: MediaStream): Boolean {
        // XXX The field _streams is explicitly implemented as a copy-on-write
        // storage in order to avoid synchronization.
        val streams = _streams

        // XXX I do NOT want to use an Iterator.
        var i = 0
        val end = streams.size
        while (i < end) {
            val s = streams[i]
            if (destination === s.connectorDesc.streamRTPManagerDesc!!.streamRTPManager.mediaStream) {
                controlPayload.writeTo(s.stream)
                return true
            }
            ++i
        }
        return false
    }

    companion object {
        /**
         * The name of the `boolean` `ConfigurationService` property which indicates
         * whether the RTP header extension(s) are to be removed from received RTP packets prior to
         * relaying them. The default value is `false`.
         */
        private val REMOVE_RTP_HEADER_EXTENSIONS_PNAME = RTPTranslatorImpl::class.java.name + ".removeRTPHeaderExtensions"
        private val WRITE_Q_CAPACITY = RTPConnectorOutputStream.PACKET_QUEUE_CAPACITY

        /**
         * Removes the RTP header extension(s) from an RTP packet.
         *
         * @param buf the `byte`s of a datagram packet which may contain an RTP packet
         * @param off the offset in `buf` at which the actual data in `buf` starts
         * @param len the number of `byte`s in `buf` starting at `off` comprising the actual data
         * @return the number of `byte`s in `buf` starting at `off` comprising the
         * actual data after the possible removal of the RTP header extension(s)
         */
        private fun removeRTPHeaderExtensions(buf: ByteArray?, off: Int, len: Int): Int {
            // Do the bytes in the specified buffer resemble (the header of) an RTP packet?
            var length = len
            if (length >= RTPHeader.SIZE) {
                val b0 = buf!![off]
                val v = b0.toInt() and 0xC0 ushr 6 /* version */
                if (v == RTPHeader.VERSION) {
                    val x = b0.toInt() and 0x10 == 0x10 /* extension */
                    if (x) {
                        val cc = b0.toInt() and 0x0F /* CSRC count */
                        val xBegin = off + RTPHeader.SIZE + 4 * cc
                        var xLen = 2 /* defined by profile */ + 2 /* length */
                        val end = off + length
                        if (xBegin + xLen < end) {
                            xLen += readUint16AsInt(buf, xBegin + 2 /* defined by profile */) * 4
                            val xEnd = xBegin + xLen
                            if (xEnd <= end) {
                                // Remove the RTP header extension bytes.
                                var src = xEnd
                                var dst = xBegin
                                while (src < end) {
                                    buf[dst++] = buf[src++]
                                }
                                length -= xLen
                                // Switch off the extension bit.
                                buf[off] = (b0.toInt() and 0xEF).toByte()
                            }
                        }
                    }
                }
            }
            return length
        }
    }
}