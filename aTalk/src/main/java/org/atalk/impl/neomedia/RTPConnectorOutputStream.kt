/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import net.sf.fmj.media.util.MediaThread
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.RawPacket
import org.atalk.util.ConfigUtils
import org.ice4j.util.QueueStatistics
import org.ice4j.util.RateStatistics
import timber.log.Timber
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import javax.media.rtp.OutputDataStream

/**
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
abstract class RTPConnectorOutputStream : OutputDataStream {
    /**
     * Whether this `RTPConnectorOutputStream` is enabled or disabled. While the stream is
     * disabled, it suppresses actually sending any packets via [.write].
     */
    private var enabled = true

    /**
     * Number of bytes sent through this stream to any of its targets.
     */
    val numberOfBytesSent = 0L

    /**
     * Number of packets sent through this stream, not taking into account the number of its targets.
     */
    private var numberOfPackets = 0L

    /**
     * The number of packets dropped because a packet was inserted while [.queue] was full.
     */
    private var numDroppedPackets = 0

    /**
     * The pool of `RawPacket` instances which reduces the number of allocations performed by packetize.
     */
    private val rawPacketPool = LinkedBlockingQueue<RawPacket>(POOL_CAPACITY)

    /**
     * Stream targets' IP addresses and ports.
     */
    protected val targets = LinkedList<InetSocketAddress>()

    /**
     * The [Queue] which will hold packets to be processed, if using a separate thread for sending is enabled.
     */
    private var queue: Queue? = null

    /**
     * Whether this [RTPConnectorOutputStream] is closed.
     */
    private var closed = false

    /**
     * The `RateStatistics` instance used to calculate the sending bitrate of this output stream.
     */
    private val rateStatistics = RateStatistics(AVERAGE_BITRATE_WINDOW_MS)

    /**
     * Initializes a new `RTPConnectorOutputStream` which is to send packet data out through a specific socket.
     */
    init {
        queue = if (USE_SEND_THREAD) {
            Queue()
        } else {
            null
        }
    }

    /**
     * Add a target to stream targets list
     *
     * @param remoteAddr target ip address
     * @param remotePort target port
     */
    fun addTarget(remoteAddr: InetAddress?, remotePort: Int) {
        val target = InetSocketAddress(remoteAddr, remotePort)
        if (!targets.contains(target)) targets.add(target)
    }

    /**
     * Close this output stream.
     */
    fun close() {
        if (!closed) {
            closed = true
            removeTargets()
        }
    }

    /**
     * Creates a `RawPacket` element from a specific `byte[]` buffer in order to have
     * this instance send its packet data through its [.write] method.
     * Returns an array of one or more elements, with the created `RawPacket` as its first
     * element (and `null` for all other elements)
     *
     * Allows extenders to intercept the array and possibly filter and/or modify it.
     *
     * @param buf the packet data to be sent to the targets of this instance. The contents of
     * `buf` starting at `off` with the specified `len` is copied into the
     * buffer of the returned `RawPacket`.
     * @param off the offset of the packet data in `buf`
     * @param len the length of the packet data in `buf`
     * @param context the `Object` provided to [.write]. The
     * implementation of `RTPConnectorOutputStream` ignores the `context`.
     * @return an array with a single `RawPacket` containing the packet data of the specified
     * `byte[]` buffer.
     */
    protected open fun packetize(buf: ByteArray, off: Int, len: Int, context: Any?): Array<RawPacket?> {
        val pkts = arrayOfNulls<RawPacket>(1)
        var pkt = rawPacketPool.poll()
        var pktBuffer: ByteArray
        if (pkt == null) {
            pktBuffer = ByteArray(len)
            pkt = RawPacket()
        } else {
            pktBuffer = pkt.buffer
        }
        if (pktBuffer.size < len) {
            /*
             * XXX It may be argued that if the buffer length is insufficient once, it will be
             * insufficient more than once. That is why we recreate it without returning a packet to
             * the pool.
             */
            pktBuffer = ByteArray(len)
        }
        pkt.buffer = pktBuffer
        pkt.flags = 0
        pkt.length = len
        pkt.offset = 0
        System.arraycopy(buf, off, pktBuffer, 0, len)
        pkts[0] = pkt
        return pkts
    }

    /**
     * Returns whether or not this `RTPConnectorOutputStream` has a valid socket.
     *
     * @return `true` if this `RTPConnectorOutputStream` has a valid socket; `false`, otherwise
     */
    protected abstract fun isSocketValid(): Boolean

    /**
     * Remove a target from stream targets list
     *
     * @param remoteAddr target ip address
     * @param remotePort target port
     * @return `true` if the target is in stream target list and can be removed; `false`, otherwise
     */
    fun removeTarget(remoteAddr: InetAddress, remotePort: Int): Boolean {
        val targetIter = targets.iterator()
        while (targetIter.hasNext()) {
            val target = targetIter.next()
            if (target.address == remoteAddr && target.port == remotePort) {
                targetIter.remove()
                return true
            }
        }
        return false
    }

    /**
     * Remove all stream targets from this session.
     */
    fun removeTargets() {
        targets.clear()
    }

    /**
     * Sends a specific RTP packet through the `DatagramSocket` of this `OutputDataSource`.
     *
     * Warning: the `RawPacket` passed to this method, and its underlying buffer will be
     * consumed and might later be reused by this `RTPConnectorOutputStream`. They should not
     * be used by the user afterwards.
     *
     * @param packet the RTP packet to be sent through the `DatagramSocket` of this `OutputDataSource`
     * @return `true` if the specified `packet` was successfully sent to all targets;
     * otherwise, `false`.
     */
    private fun send(packet: RawPacket): Boolean {
        if (!isSocketValid()) {
            rawPacketPool.offer(packet)
            return false
        }
        numberOfPackets++
        for (target in targets) {
            try {
                sendToTarget(packet, target)
            } catch (ioe: IOException) {
                rawPacketPool.offer(packet)
                // too many msg hangs the system, show only once per 100
                if (numberOfPackets % 100 == 0L) Timber.w("Failed to send 100 packets to target %s: %s", target, ioe.message)
                return false
            }
        }
        rawPacketPool.offer(packet)
        return true
    }

    /**
     * Sends a specific `RawPacket` through this `OutputDataStream` to a specific `InetSocketAddress`.
     *
     * @param packet the `RawPacket` to send through this `OutputDataStream` to the specified
     * `target`
     * @param target the `InetSocketAddress` to which the specified `packet` is to be sent
     * through this `OutputDataStream`
     * @throws IOException if anything goes wrong while sending the specified `packet` through this
     * `OutputDataStream` to the specified `target`
     */
    @Throws(IOException::class)
    protected abstract fun sendToTarget(packet: RawPacket, target: InetSocketAddress)

    /**
     * Enables or disables this `RTPConnectorOutputStream`. While the stream is disabled, it
     * suppresses actually sending any packets via [.send].
     *
     * @param enabled `true` to enable, `false` to disable.
     */
    fun setEnabled(enabled: Boolean) {
        if (this.enabled != enabled) {
            Timber.log(TimberLog.FINER, "setEnabled: %s", enabled)
            this.enabled = enabled
        }
    }

    /**
     * Sets the maximum number of RTP packets to be sent by this `OutputDataStream` through
     * its `DatagramSocket` per a specific number of milliseconds.
     *
     * @param maxPackets the maximum number of RTP packets to be sent by this `OutputDataStream` through
     * its `DatagramSocket` per the specified number of milliseconds; `-1` if no maximum is to be set
     * @param perMillis the number of milliseconds per which `maxPackets` are to be sent by this
     * `OutputDataStream` through its `DatagramSocket`
     */
    fun setMaxPacketsPerMillis(maxPackets: Int, perMillis: Long): Boolean {
        if (queue != null) {
            queue!!.setMaxPacketsPerMillis(maxPackets, perMillis)
        } else {
            Timber.e("Cannot enable pacing: send thread is not enabled.")
        }
        return queue != null
    }

    /**
     * Changes current thread priority.
     *
     * @param priority the new priority.
     */
    fun setPriority(priority: Int) {
        // currently no priority is set
    }

    /**
     * Implements [OutputDataStream.write].
     *
     * @param buf the `byte[]` to write into this `OutputDataStream`
     * @param off the offset in `buf` at which the `byte`s to be written into this
     * `OutputDataStream` start
     * @param len the number of `byte`s in `buf` starting at `off` to be written into
     * this `OutputDataStream`
     * @return the number of `byte`s read from `buf` starting at `off` and not
     * exceeding `len` and written into this `OutputDataStream`
     */
    override fun write(buf: ByteArray, off: Int, len: Int): Int {
        return write(buf, off, len,  /* context */null)
    }

    /**
     * Writes a byte[] to this [RTPConnectorOutputStream] synchronously ( even when
     * [.USE_SEND_THREAD] is enabled).
     *
     * @param buf
     * @param off
     * @param len
     * @return the number of bytes written.
     */
    fun syncWrite(buf: ByteArray, off: Int, len: Int): Int {
        return syncWrite(buf, off, len, null)
    }

    /**
     * Writes a byte[] to this [RTPConnectorOutputStream] synchronously ( even when
     * [.USE_SEND_THREAD] is enabled).
     *
     * @param buf
     * @param off
     * @param len
     * @return the number of bytes written.
     */
    private fun syncWrite(buf: ByteArray, off: Int, len: Int, context: Any?): Int {
        var result = -1
        val pkts = packetize(buf, off, len, context)
        if (pkts != null) {
            if (write(pkts)) {
                result = len
            }
        } else {
            result = len // there was nothing to send
        }
        return result
    }

    /**
     * Implements [OutputDataStream.write]. Allows extenders to provide a context
     * `Object` to invoked overridable methods such as [.packetize].
     *
     * @param buf the `byte[]` to write into this `OutputDataStream`
     * @param off the offset in `buf` at which the `byte`s to be written into this
     * `OutputDataStream` start
     * @param len the number of `byte`s in `buf` starting at `off` to be written into
     * this `OutputDataStream`
     * @param context the `Object` to provide to invoked overridable methods such as
     * [.packetize]
     * @return the number of `byte`s read from `buf` starting at `off` and not
     * exceeding `len` and written into this `OutputDataStream`
     */
    fun write(buf: ByteArray, off: Int, len: Int, context: Any?): Int {
        if (enabled) {
            // While calling write without targets can be carried out without a
            // problem, such a situation may be a symptom of a problem. For
            // example, it was discovered during testing that RTCP was
            // seemingly endlessly sent after hanging up a call.
            if (targets.isEmpty()) Timber.log(TimberLog.FINER, Throwable(), "Write called without targets!")
            if (queue != null) {
                queue!!.write(buf, off, len, context)
            } else {
                syncWrite(buf, off, len, context)
            }
        }
        return len
    }

    /**
     * Sends an array of [RawPacket]s to this [RTPConnectorOutputStream]'s targets.
     *
     * @param pkts the array of [RawPacket]s to send.
     * @return `true` if all `pkts` were written into this `OutputDataStream`; otherwise, `false`
     */
    private fun write(pkts: Array<RawPacket?>?): Boolean {
        if (closed) return false
        if (pkts == null) return true
        var success = true
        val now = System.currentTimeMillis()
        for (pkt in pkts) {
            // If we got extended, the delivery of the packet may have been canceled.
            if (pkt != null) {
                if (success) {
                    if (!send(pkt)) {
                        // Skip sending the remaining RawPackets but return them to the pool and clear pkts.
                        // The current pkt was returned to the pool by send().
                        success = false
                    } else {
                        rateStatistics.update(pkt.length, now)
                    }
                } else {
                    rawPacketPool.offer(pkt)
                }
            }
        }
        return success
    }

    /**
     * @return the current output bitrate in bits per second.
     */
    fun getOutputBitrate(): Long {
        return getOutputBitrate(System.currentTimeMillis())
    }

    /**
     * @param now the current time.
     * @return the current output bitrate in bits per second.
     */
    fun getOutputBitrate(now: Long): Long {
        return rateStatistics.getRate(now)
    }

    private inner class Queue {
        /**
         * The [java.util.Queue] which holds [Buffer]s to be processed by [.sendThread].
         */
        val queue = ArrayBlockingQueue<Buffer?>(PACKET_QUEUE_CAPACITY)

        /**
         * A pool of [RTPConnectorOutputStream.Queue.Buffer] instances.
         */
        val pool = ArrayBlockingQueue<Buffer>(15)

        /**
         * The maximum number of [Buffer]s to be processed by [.sendThread] per [.perNanos] nanoseconds.
         */
        var maxBuffers = -1

        /**
         * The time interval in nanoseconds during which no more than [.maxBuffers]
         * [Buffer]s are to be processed by [.sendThread].
         */
        var perNanos = -1L

        /**
         * The number of [Buffer]s already processed during the current `perNanos` interval.
         */
        var buffersProcessedInCurrentInterval = 0L

        /**
         * The time stamp in nanoseconds of the start of the current `perNanos` interval.
         */
        var intervalStartTimeNanos = 0L

        /**
         * The [Thread] which is to read [Buffer]s from this [Queue] and send them
         * to this [RTPConnectorOutputStream] 's targets.
         */
        val sendThread: Thread

        /**
         * The instance optionally used to gather and print statistics about this queue.
         */
        var queueStats: QueueStatistics? = null

        /**
         * Initializes a new Queue instance and starts its send thread.
         */
        init {
            if (TimberLog.isTraceEnable) {
                // queueStats = QueueStatistics.get(getClass().getSimpleName());
                queueStats = QueueStatistics(javaClass.simpleName + "-" + hashCode())
            }
            sendThread = object : Thread() {
                override fun run() {
                    runInSendThread()
                }
            }
            sendThread.setDaemon(true)
            sendThread.setName(Queue::class.java.name + ".sendThread")
            RTPConnectorInputStream.setThreadPriority(sendThread, MediaThread.getNetworkPriority())
            sendThread.start()
        }

        /**
         * Adds the given buffer (and its context) to this queue.
         *
         * @param buf
         * @param off
         * @param len
         * @param context
         */
        fun write(buf: ByteArray, off: Int, len: Int, context: Any?) {
            if (closed) return
            val buffer = getBuffer(len)
            System.arraycopy(buf, off, buffer.buf!!, 0, len)
            buffer.len = len
            buffer.context = context
            val now = System.currentTimeMillis()
            if (queue.size >= PACKET_QUEUE_CAPACITY) {
                // Drop from the head of the queue.
                val b = queue.poll()
                if (b != null) {
                    if (queueStats != null) {
                        queueStats!!.remove(now)
                    }
                    pool.offer(b)
                    numDroppedPackets++
                    if (logDroppedPacket(numDroppedPackets)) {
                        Timber.w("Packets dropped (hashCode = %s): %s", hashCode(), numDroppedPackets)
                    }
                }
            }
            //            if (queue.size() % 200 == 0) {
            //                new Exception("queue check #" + buffer.context).printStackTrace();
            //            }
            if (queue.offer(buffer) && queueStats != null) {
                queueStats!!.add(now)
            }
        }

        /**
         * Reads [Buffer]s from [.queue], "packetizes" them through
         * [RTPConnectorOutputStream.packetize] and sends the
         * resulting packets to this [RTPConnectorOutputStream]'s targets.
         *
         * If a pacing policy is configured, makes sure that it is respected. Note that this pacing
         * is done on the basis of the number of [Buffer]s read from the queue, which
         * technically could be different than the number of [RawPacket]s sent. This is done
         * in order to keep the implementation simpler, and because in the majority of the cases
         * (and in all current cases where pacing is enabled) the numbers do match.
         */
        private fun runInSendThread() {
            if (Thread.currentThread() != sendThread) {
                Timber.w(Throwable(), "runInSendThread executing in the wrong thread: %s",
                        Thread.currentThread().name)
                return
            }
            try {
                while (!closed) {
                    val buffer = try {
                        queue.poll(500, TimeUnit.MILLISECONDS)
                    } catch (iex: InterruptedException) {
                        continue
                    }

                    // The current thread has potentially waited.
                    if (closed) {
                        break
                    }
                    if (buffer == null) {
                        continue
                    }
                    if (queueStats != null) {
                        queueStats!!.remove(System.currentTimeMillis())
                    }
                    val pkts = try {
                        // We will sooner or later process the Buffer. Since this
                        // may take a non-negligible amount of time, do it
                        // before taking pacing into account.
                        packetize(buffer.buf!!, 0, buffer.len, buffer.context)
                    } catch (e: Exception) {
                        // The sending thread must not die because of a failure
                        // in the conversion to RawPacket[] or any of the
                        // transformations (because of e.g. parsing errors).
                        Timber.e(e, "Failed to handle an outgoing packet.")
                        continue
                    } finally {
                        pool.offer(buffer)
                    }
                    if (perNanos > 0 && maxBuffers > 0) {
                        val time = System.nanoTime()
                        val nanosRemainingTime = time - intervalStartTimeNanos
                        if (nanosRemainingTime >= perNanos) {
                            intervalStartTimeNanos = time
                            buffersProcessedInCurrentInterval = 0
                        } else if (buffersProcessedInCurrentInterval >= maxBuffers) {
                            LockSupport.parkNanos(nanosRemainingTime)
                        }
                    }
                    try {
                        this@RTPConnectorOutputStream.write(pkts)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to send a packet.")
                        continue
                    }
                    buffersProcessedInCurrentInterval++
                }
            } finally {
                queue.clear()
            }
        }

        fun setMaxPacketsPerMillis(maxPackets: Int, perMillis: Long) {
            if (maxPackets < 1) {
                // This doesn't make sense. Disable pacing.
                maxBuffers = -1
                perNanos = -1
            } else {
                require(perMillis >= 1) { "perMillis" }
                maxBuffers = maxPackets
                perNanos = perMillis * 1000000
            }
        }

        /**
         * @return a free [Buffer] instance with a byte array with a length of at least `len`.
         */
        private fun getBuffer(len: Int): Buffer {
            var buffer = pool.poll()
            if (buffer == null) buffer = Buffer()
            if (buffer.buf == null || buffer.buf!!.size < len) buffer.buf = ByteArray(len)
            return buffer
        }

        private inner class Buffer {
            var buf: ByteArray? = null
            var len = 0
            var context: Any? = null
        }
    }

    companion object {
        /**
         * The maximum number of packets to be sent to be kept in the queue of
         * [RTPConnectorOutputStream]. When the maximum is reached, the next attempt to write a
         * new packet in the queue will result in the first packet in the queue being dropped. Defined in order
         * to prevent `OutOfMemoryError`s which may arise if the capacity of the queue is unlimited.
         */
        var PACKET_QUEUE_CAPACITY = 0

        /**
         * The maximum size of the queues used as pools for unused objects.
         */
        var POOL_CAPACITY = 0

        /**
         * The size of the window over which average bitrate will be calculated.
         */
        private var AVERAGE_BITRATE_WINDOW_MS = 0

        /**
         * The flag which controls whether this [RTPConnectorOutputStream] should create its own
         * thread which will perform the packetization (and potential transformation) and sending of
         * packets to the targets.
         *
         * If `true`, calls to [.write] will only add the given bytes to
         * [.queue]. Otherwise, packetization (via [.packetize])
         * and output (via [.sendToTarget] will be performed by the
         * calling thread. Note that these are potentially blocking operations.
         *
         * Note: if pacing is to be
         */
        private var USE_SEND_THREAD = false

        /**
         * The name of the property which controls the value of [.USE_SEND_THREAD].
         */
        private val USE_SEND_THREAD_PNAME = RTPConnectorOutputStream::class.java.name + ".USE_SEND_THREAD"

        /**
         * The name of the `ConfigurationService` and/or `System` integer property which
         * specifies the value of [.PACKET_QUEUE_CAPACITY].
         */
        private val PACKET_QUEUE_CAPACITY_PNAME = RTPConnectorOutputStream::class.java.name + ".PACKET_QUEUE_CAPACITY"

        /**
         * The name of the property which specifies the value of [.POOL_CAPACITY].
         */
        private val POOL_CAPACITY_PNAME = RTPConnectorOutputStream::class.java.name + ".POOL_CAPACITY"

        /**
         * The name of the property which specifies the value of [.AVERAGE_BITRATE_WINDOW_MS].
         */
        private val AVERAGE_BITRATE_WINDOW_MS_PNAME = RTPConnectorOutputStream::class.java.name + ".AVERAGE_BITRATE_WINDOW_MS"

        init {
            val cfg = LibJitsi.configurationService

            // Set USE_SEND_THREAD
            USE_SEND_THREAD = ConfigUtils.getBoolean(cfg, USE_SEND_THREAD_PNAME, true)
            POOL_CAPACITY = ConfigUtils.getInt(cfg, POOL_CAPACITY_PNAME, 100)
            AVERAGE_BITRATE_WINDOW_MS = ConfigUtils.getInt(cfg, AVERAGE_BITRATE_WINDOW_MS_PNAME, 5000)

            // Set PACKET_QUEUE_CAPACITY
            var packetQueueCapacity = ConfigUtils.getInt(cfg, PACKET_QUEUE_CAPACITY_PNAME, -1)
            if (packetQueueCapacity == -1) {
                // Backward-compatibility with the old property name.
                val oldPropertyName = "MaxPacketsPerMillisPolicy.PACKET_QUEUE_CAPACITY"
                packetQueueCapacity = ConfigUtils.getInt(cfg, oldPropertyName, -1)
            }
            PACKET_QUEUE_CAPACITY = if (packetQueueCapacity >= 0) packetQueueCapacity else 1024
            Timber.log(TimberLog.FINER, "Initialized configuration. Send thread: %s. Pool capacity: %s. Queue capacity: %s. Avg bitrate window: %s",
                    USE_SEND_THREAD, POOL_CAPACITY, PACKET_QUEUE_CAPACITY, AVERAGE_BITRATE_WINDOW_MS)
        }

        /**
         * Returns true if a warning should be logged after a queue has dropped
         * `numDroppedPackets` packets.
         *
         * @param numDroppedPackets the number of dropped packets.
         * @return `true` if a warning should be logged.
         */
        fun logDroppedPacket(numDroppedPackets: Int): Boolean {
            return numDroppedPackets == 1 || numDroppedPackets <= 1000 && numDroppedPackets % 100 == 0 || numDroppedPackets % 1000 == 0
        }
    }
}