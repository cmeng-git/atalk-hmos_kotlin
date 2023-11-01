/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.translator

import okhttp3.internal.notifyAll
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.RTPConnectorOutputStream
import org.ice4j.util.QueueStatistics
import timber.log.Timber
import java.io.IOException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import javax.media.Buffer
import javax.media.protocol.*

/**
 * Implements `PushSourceStream` for an `RTPTranslatorImpl`. Reads packets from
 * endpoint `PushSourceStream`s and pushes them to an `RTPTranslatorImpl` to be
 * translated.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
internal class PushSourceStreamImpl(private val connector: RTPConnectorImpl, private val data: Boolean) : PushSourceStream, Runnable, SourceTransferHandler {
    /**
     * The indicator which determines whether [.close] has been invoked on this instance.
     */
    private var closed = false

    /**
     * The indicator which determines whether [.read] read a
     * `SourcePacket` from [.readQ] after a `SourcePacket` was written there.
     */
    private var _read = false

    /**
     * The `Queue` of `SourcePacket`s to be read out of this instance via
     * [.read].
     */
    private val readQ: Queue<SourcePacket>

    /**
     * The capacity of [.readQ].
     */
    private val readQCapacity: Int
    private var readQStats: QueueStatistics? = null

    /**
     * The number of packets dropped because a packet was inserted while
     * [.readQ] was full.
     */
    private var numDroppedPackets = 0

    /**
     * The pool of `SourcePacket` instances to reduce their allocations and garbage collection.
     */
    private val sourcePacketPool: Queue<SourcePacket?> = LinkedBlockingQueue(RTPConnectorOutputStream.POOL_CAPACITY)
    private val streams: MutableList<PushSourceStreamDesc> = LinkedList()

    /**
     * The `Thread` which invokes
     * [SourceTransferHandler.transferData] on [._transferHandler].
     */
    private var transferDataThread: Thread?
    private var _transferHandler: SourceTransferHandler? = null

    init {
        readQCapacity = RTPConnectorOutputStream.PACKET_QUEUE_CAPACITY
        readQ = ArrayBlockingQueue(readQCapacity)
        readQStats = if (TimberLog.isTraceEnable) {
            // readQStats = QueueStatistics.get(getClass().getSimpleName()); // ice4j 2.0
            QueueStatistics(javaClass.simpleName + "-" + hashCode())
        } else {
            null
        }
        transferDataThread = Thread(this, javaClass.name)
        transferDataThread!!.isDaemon = true
        transferDataThread!!.start()
    }

    @Synchronized
    fun addStream(connectorDesc: RTPConnectorDesc, stream: PushSourceStream) {
        for (streamDesc in streams) {
            if (streamDesc.connectorDesc === connectorDesc && streamDesc.stream === stream) {
                return
            }
        }
        streams.add(PushSourceStreamDesc(connectorDesc, stream, data))
        stream.setTransferHandler(this)
    }

    fun close() {
        closed = true
        sourcePacketPool.clear()
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    override fun endOfStream(): Boolean {
        // TODO Auto-generated method stub
        return false
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    override fun getContentDescriptor(): ContentDescriptor? {
        return null
    }

    override fun getContentLength(): Long {
        return SourceStream.LENGTH_UNKNOWN
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    override fun getControl(controlType: String): Any? {
        return null
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    override fun getControls(): Array<Any?>? {
        return null
    }

    @Synchronized
    override fun getMinimumTransferSize(): Int {
        var minimumTransferSize = 0
        for (streamDesc in streams) {
            val streamMinimumTransferSize = streamDesc.stream.minimumTransferSize
            if (minimumTransferSize < streamMinimumTransferSize) minimumTransferSize = streamMinimumTransferSize
        }
        return minimumTransferSize
    }

    private val translator: RTPTranslatorImpl
        get() = connector.translator

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (closed) return -1
        var pkt: SourcePacket?
        var pktLength: Int

        synchronized(readQ) {
            pkt = readQ.peek()
            if (pkt == null) return 0

            pktLength = pkt!!.length
            if (length < pktLength) {
                throw IOException("Length $length is insufficient. Must be at least $pktLength.")
            }

            readQ.remove()
            if (readQStats != null) {
                readQStats!!.remove(System.currentTimeMillis())
            }
            _read = true
            (readQ as Object).notifyAll()
        }

        System.arraycopy(pkt!!.buffer!!, pkt!!.offset, buffer, offset, pktLength)
        val streamDesc = pkt!!.streamDesc
        var read = pktLength
        val flags = pkt!!.flags
        pkt!!.streamDesc = null
        sourcePacketPool.offer(pkt)
        if (read > 0) {
            val translator = translator
            if (translator != null) {
                read = translator.didRead(streamDesc, buffer, offset, read, flags)
            }
        }
        return read
    }

    @Synchronized
    fun removeStreams(connectorDesc: RTPConnectorDesc) {
        val streamIter = streams.iterator()
        while (streamIter.hasNext()) {
            val streamDesc = streamIter.next()
            if (streamDesc.connectorDesc === connectorDesc) {
                streamDesc.stream.setTransferHandler(null)
                streamIter.remove()
            }
        }
    }

    /**
     * Runs in [.transferDataThread] and invokes
     * [SourceTransferHandler.transferData] on [._transferHandler].
     */
    override fun run() {
        try {
            while (!closed) {
                val transferHandler = _transferHandler
                var proceed = true

                synchronized(readQ) {
                    if (readQ.isEmpty() || transferHandler == null) {
                        try {
                            (readQ as Object).wait(100)
                        } catch (ignore: InterruptedException) {
                        }
                        proceed = false
                    }
                }

                if (proceed) {
                    try {
                        transferHandler!!.transferData(this)
                    } catch (t: Throwable) {
                        if (t is ThreadDeath) {
                            throw t
                        } else {
                            Timber.w(t, "An RTP packet may have not been fully handled.")
                        }
                    }
                }
            }
        } finally {
            if (Thread.currentThread() == transferDataThread) transferDataThread = null
        }
    }

    @Synchronized
    override fun setTransferHandler(transferHandler: SourceTransferHandler) {
        if (_transferHandler != transferHandler) {
            _transferHandler = transferHandler
            for (streamDesc in streams) streamDesc.stream.setTransferHandler(this)
        }
    }

    /**
     * {@inheritDoc}
     *
     *
     * Implements [SourceTransferHandler.transferData]. This instance sets
     * itself as the `transferHandler` of all `PushSourceStream`s that get added to it
     * (i.e. [.streams]). When either one of these pushes media data, this instance pushes
     * that media data.
     */
    override fun transferData(stream: PushSourceStream) {
        if (closed) return
        var streamDesc: PushSourceStreamDesc? = null
        synchronized(this) {
            for (aStreamDesc in streams) {
                if (aStreamDesc.stream === stream) {
                    streamDesc = aStreamDesc
                    break
                }
            }
        }
        if (streamDesc == null) return
        var len = stream.minimumTransferSize
        if (len < 1) len = 2 * 1024
        var pkt = sourcePacketPool.poll()
        var buf: ByteArray? = null

        if (pkt == null || pkt.buffer.also { buf = it }!!.size < len) {
            buf = ByteArray(len)
            pkt = SourcePacket(buf!!, 0, 0)
        } else {
            len = buf!!.size
            pkt.flags = 0
            pkt.length = 0
            pkt.offset = 0
        }
        var read = 0
        try {
            val streamAsPushBufferStream = streamDesc!!.streamAsPushBufferStream
            if (streamAsPushBufferStream == null) {
                read = stream.read(buf, 0, len)
            } else {
                streamAsPushBufferStream.read(pkt)
                if (pkt.isDiscard) {
                    read = 0
                } else {
                    read = pkt.length
                    if (read < 1 && pkt.flags and Buffer.FLAG_EOM == Buffer.FLAG_EOM) {
                        read = -1
                    }
                }
            }
        } catch (ioe: IOException) {
            Timber.e(ioe, "Failed to read from an RTP stream!")
        } finally {
            if (read > 0) {
                pkt.length = read
                pkt.streamDesc = streamDesc
                var yield: Boolean
                synchronized(readQ) {
                    val readQSize = readQ.size
                    if (readQSize < 1) yield = false else yield = readQSize >= readQCapacity || !_read
                    if (yield) readQ.notifyAll()
                }
                if (yield) Thread.yield()

                synchronized(readQ) {
                    val now = System.currentTimeMillis()
                    if (readQ.size >= readQCapacity) {
                        readQ.remove()
                        if (readQStats != null) {
                            readQStats!!.remove(now)
                        }
                        numDroppedPackets++
                        if (RTPConnectorOutputStream.logDroppedPacket(numDroppedPackets)) {
                            Timber.w("Dropped %s packets hashCode = %s", numDroppedPackets, hashCode())
                        }
                    }
                    if (readQ.offer(pkt)) {
                        if (readQStats != null) {
                            readQStats!!.add(now)
                        }
                        // TODO It appears that it is better to not yield based
                        // on whether the read method has read after the last write.
                        // this.read = false;
                    }
                    readQ.notifyAll()
                }
            } else {
                pkt.streamDesc = null
                sourcePacketPool.offer(pkt)
            }
        }
    }
}