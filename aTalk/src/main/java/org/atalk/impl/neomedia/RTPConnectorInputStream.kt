/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import net.sf.fmj.media.util.MediaThread
import okhttp3.internal.notifyAll
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream
import org.atalk.impl.neomedia.protocol.PushBufferStreamAdapter
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.RawPacket
import org.atalk.util.ArrayUtils
import org.atalk.util.concurrent.MonotonicAtomicLong
import org.ice4j.socket.DatagramPacketFilter
import timber.log.Timber
import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.SocketTimeoutException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import javax.media.Buffer
import javax.media.protocol.ContentDescriptor
import javax.media.protocol.PushBufferStream
import javax.media.protocol.PushSourceStream
import javax.media.protocol.SourceStream
import javax.media.protocol.SourceTransferHandler

/**
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
abstract class RTPConnectorInputStream<T : Closeable?> protected constructor(socket: T) : PushSourceStream, Closeable {
    /**
     * Packet receive buffer
     */
    private val buffer = ByteArray(PACKET_RECEIVE_BUFFER_LENGTH)

    /**
     * Whether this stream is closed. Used to control the termination of worker thread.
     */
    private var closed = false

    /**
     * The `DatagramPacketFilter`s which allow dropping `DatagramPacket`s before they
     * are converted into `RawPacket`s.
     */
    private var datagramPacketFilters: Array<DatagramPacketFilter>? = null

    /**
     * Whether this `RTPConnectorInputStream` is enabled or disabled.
     * While disabled, the stream does not accept any packets.
     */
    private var enabled = true

    /**
     * Caught an IO exception during read from socket
     */
    private var ioError = false

    /**
     * Number of received bytes.
     */
    var numberOfReceivedBytes = 0L

    /**
     * The packet data to be read out of this instance through its [.read] method.
     */
    private var pkt: RawPacket? = null

    /**
     * The `Object` which synchronizes the access to [.pkt].
     */
    private val pktSyncRoot = Any()

    /**
     * The adapter of this `PushSourceStream` to the `PushBufferStream` interface.
     */
    private val pushBufferStream: PushBufferStream

    /**
     * The pool of `RawPacket` instances to reduce their allocations and garbage collection.
     */
    private val rawPacketPool = ArrayBlockingQueue<RawPacket?>(RTPConnectorOutputStream.POOL_CAPACITY)

    /**
     * The background/daemon `Thread` which invokes [.receive].
     */
    private var receiveThread: Thread? = null
    protected val socket: T?

    /**
     * SourceTransferHandler object which is used to read packets.
     */
    private var transferHandler: SourceTransferHandler? = null

    /**
     * The time in milliseconds of the last activity related to this `RTPConnectorInputStream`.
     */
    val lastActivityTime = MonotonicAtomicLong()

    /**
     * Initializes a new `RTPConnectorInputStream` which is to receive packet data from a specific UDP socket.
     */
    init {
        this.socket = socket
        if (this.socket == null) {
            closed = true
        } else {
            closed = false
            try {
                val receiveBufferSize = LibJitsi.configurationService.getInt(SO_RCVBUF_PNAME, 65535)
                setReceiveBufferSize(receiveBufferSize)
            } catch (t: Throwable) {
                if (t is InterruptedException) Thread.currentThread().interrupt() else if (t is ThreadDeath) throw t
            }
        }
        addDatagramPacketFilter { p: DatagramPacket? ->
            lastActivityTime.increase(System.currentTimeMillis())
            true
        }

        /*
         * Adapt this PushSourceStream to the PushBufferStream interface in order to make it
         * possible to read the Buffer flags of RawPacket.
         */
        pushBufferStream = object : PushBufferStreamAdapter(this, null) {
            @Throws(IOException::class)
            override fun doRead(buffer: Buffer?, data: ByteArray?, offset: Int, length: Int): Int {
                return this@RTPConnectorInputStream.read(buffer, data, offset, length)
            }
        }
        maybeStartReceiveThread()
    }

    /**
     * Gets the time in milliseconds of the last activity related to this `RTPConnectorInputStream`.
     *
     * @return the time in milliseconds of the last activity related to this `RTPConnectorInputStream`
     */
    fun getLastActivityTime(): Long {
        return lastActivityTime.get()
    }

    /**
     * Determines whether all [.datagramPacketFilters] accept a received
     * `DatagramPacket` for pushing out of this `PushSourceStream` . In other words,
     * determines whether `p` is to be discarded/dropped/ignored.
     *
     * @param p the `DatagramPacket` to be considered for acceptance by all `datagramPacketFilters`
     * @return `true` if all `datagramPacketFilters` accept `p`; otherwise, `false`
     */
    private fun accept(p: DatagramPacket): Boolean {
        var accept: Boolean
        if (enabled) {
            val filters = getDatagramPacketFilters()
            if (filters == null) {
                accept = true
            } else {
                accept = true
                for (filter in filters) {
                    try {
                        if (!filter.accept(p)) {
                            accept = false
                            break
                        }
                    } catch (t: Throwable) {
                        if (t is InterruptedException) Thread.currentThread().interrupt() else if (t is ThreadDeath) throw t
                    }
                }
            }
        } else {
            accept = false
            if (!closed) {
                Timber.log(TimberLog.FINER, "Will drop received packet because this is disabled: " + p.length + " bytes.")
            }
        }
        return accept
    }

    /**
     * Adds a `DatagramPacketFilter` which allows dropping `DatagramPacket`s before
     * they are converted into `RawPacket` s.
     *
     * @param datagramPacketFilter the `DatagramPacketFilter` which allows dropping `DatagramPacket`s
     * before they are converted into `RawPacket`s
     */
    @Synchronized
    fun addDatagramPacketFilter(datagramPacketFilter: DatagramPacketFilter?) {
        datagramPacketFilters = ArrayUtils.add(datagramPacketFilters, DatagramPacketFilter::class.java, datagramPacketFilter)
    }

    /**
     * Close this stream, stops the worker thread.
     */
    @Synchronized
    override fun close() {
        closed = true
        if (socket != null) {
            /*
             * The classes DatagramSocket and Socket implement the interface
             * Closeable since Java Runtime Environment 7.
             */
            try {
                if (socket is Closeable) {
                    socket.close()
                }
            } catch (ex: IOException) {
                // ignore
            }
        }
    }

    /**
     * Creates a new `RawPacket` from a specific `DatagramPacket` in order to have
     * this instance receive its packet data through its [.read] method.
     * Returns an array of `RawPacket` with the created packet as its first element (and
     * `null` for the other elements).
     *
     *
     * Allows extenders to intercept the packet data and possibly filter and/or modify it.
     *
     * @param datagramPacket the `DatagramPacket` containing the packet data
     * @return an array of `RawPacket` containing the `RawPacket` which contains the
     * packet data of the specified `DatagramPacket` as its first element.
     */
    protected open fun createRawPacket(datagramPacket: DatagramPacket): Array<RawPacket?> {
        val pkts = arrayOfNulls<RawPacket>(1)
        var pkt = rawPacketPool.poll()
        if (pkt == null) pkt = RawPacket()
        var buffer = pkt.buffer
        val length = datagramPacket.length
        if (buffer == null || buffer.size < length) {
            buffer = ByteArray(length)
            pkt.buffer = buffer
        }
        System.arraycopy(datagramPacket.data, datagramPacket.offset, buffer, 0, length)
        pkt.buffer = buffer
        pkt.offset = 0
        pkt.length = length
        pkt.flags = 0
        pkts[0] = pkt
        return pkts
    }

    /**
     * Provides a dummy implementation to [RTPConnectorInputStream.endOfStream] that always
     * returns `false`.
     *
     * @return `false`, no matter what.
     */
    override fun endOfStream(): Boolean {
        return false
    }

    /**
     * Provides a dummy implementation to [RTPConnectorInputStream.getContentDescriptor]
     * that always returns `null`.
     *
     * @return `null`, no matter what.
     */
    override fun getContentDescriptor(): ContentDescriptor? {
        return null
    }

    /**
     * Provides a dummy implementation to [RTPConnectorInputStream.getContentLength] that
     * always returns `LENGTH_UNKNOWN`.
     *
     * @return `LENGTH_UNKNOWN`, no matter what.
     */
    override fun getContentLength(): Long {
        return SourceStream.LENGTH_UNKNOWN
    }

    /**
     * Provides a dummy implementation of [RTPConnectorInputStream.getControl] that
     * always returns `null`.
     *
     * @param controlType ignored.
     * @return `null`, no matter what.
     */
    override fun getControl(controlType: String): Any? {
        return if (AbstractPushBufferStream.PUSH_BUFFER_STREAM_CLASS_NAME.equals(controlType)) {
            pushBufferStream
        } else {
            null
        }
    }

    /**
     * Provides a dummy implementation of [RTPConnectorInputStream.getControls] that always
     * returns `EMPTY_CONTROLS`.
     *
     * @return `EMPTY_CONTROLS`, no matter what.
     */
    override fun getControls(): Array<Any?> {
        return EMPTY_CONTROLS
    }

    /**
     * Gets the `DatagramPacketFilter`s which allow dropping `DatagramPacket`s before
     * they are converted into `RawPacket`s.
     *
     * @return the `DatagramPacketFilter`s which allow dropping `DatagramPacket`s
     * before they are converted into `RawPacket`s.
     */
    @Synchronized
    protected fun getDatagramPacketFilters(): Array<DatagramPacketFilter>? {
        return datagramPacketFilters
    }

    /**
     * Provides a dummy implementation of [ ][PushSourceStream.getMinimumTransferSize] that always returns
     * `2 * 1024`.
     *
     * @return `2 * 1024`, no matter what.
     */
    override fun getMinimumTransferSize(): Int {
        return 2 * 1024 // twice the MTU size, just to be safe.
    }

    @Synchronized
    private fun maybeStartReceiveThread() {
        if (receiveThread == null) {
            if (socket != null && !closed && transferHandler != null) {
                receiveThread = object : Thread() {
                    override fun run() {
                        runInReceiveThread()
                    }
                }
                receiveThread!!.isDaemon = true
                receiveThread!!.name = RTPConnectorInputStream::class.java.name + ".receiveThread"
                setThreadPriority(receiveThread!!, MediaThread.getNetworkPriority())
                receiveThread!!.start()
            }
        } else {
            notifyAll()
        }
    }

    /**
     * Pools the specified `RawPacket` in order to avoid future allocations and to reduce
     * the
     * effects of garbage collection.
     *
     * @param pkt the `RawPacket` to be offered to [.rawPacketPool]
     */
    private fun poolRawPacket(pkt: RawPacket) {
        pkt.flags = 0
        pkt.length = 0
        pkt.offset = 0
        rawPacketPool.offer(pkt)
    }

    /**
     * Copies the content of the most recently received packet into `data`.
     *
     * @param buffer an optional `Buffer` instance associated with the specified `data`,
     * `offset` and `length` and provided to the method in case the
     * implementation would like to provide additional `Buffer` properties such as
     * `flags`
     * @param data the `byte[]` that we'd like to copy the content of the packet to.
     * @param offset the position where we are supposed to start writing in `data`.
     * @param length the number of `byte`s available for writing in `data`.
     * @return the number of bytes read
     * @throws IOException if `length` is less than the size of the packet.
     */
    @Throws(IOException::class)
    protected open fun read(buffer: Buffer?, data: ByteArray?, offset: Int, length: Int): Int {
        if (data == null) throw NullPointerException("data")
        if (ioError) return -1
        var pkt: RawPacket?

        synchronized(pktSyncRoot) {
            pkt = this.pkt
            this.pkt = null
        }

        val pktLength: Int
        if (pkt == null) {
            pktLength = 0
        } else {
            // By default, pkt will be returned to the pool after it was read.
            var poolPkt = true
            try {
                pktLength = pkt!!.length
                if (length < pktLength) {
                    /*
                     * If pkt is still the latest RawPacket made available to reading, reinstate it
                     * for the next invocation of read; otherwise, return it to the pool.
                     */
                    poolPkt = false
                    throw IOException("Input buffer not big enough for $pktLength")
                } else {
                    val pktBuffer = pkt!!.buffer
                    if (pktBuffer == null) {
                        throw NullPointerException("pkt.buffer null, pkt.length " + pktLength
                                + ", pkt.offset " + pkt!!.offset)
                    } else {
                        System.arraycopy(pkt!!.buffer, pkt!!.offset, data, offset, pktLength)
                        if (buffer != null) buffer.flags = pkt!!.flags
                    }
                }
            } finally {
                if (!poolPkt) {
                    synchronized(pktSyncRoot) { if (this.pkt == null) this.pkt = pkt else poolPkt = true }
                }
                if (poolPkt) {
                    // Return pkt to the pool because it was successfully read.
                    poolRawPacket(pkt!!)
                }
            }
        }
        return pktLength
    }

    /**
     * Copies the content of the most recently received packet into `buffer`.
     *
     * @param buffer the `byte[]` that we'd like to copy the content of the packet to.
     * @param offset the position where we are supposed to start writing in `buffer`.
     * @param length the number of `byte`s available for writing in `buffer`.
     * @return the number of bytes read
     * @throws IOException if `length` is less than the size of the packet.
     */
    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return read(null, buffer, offset, length)
    }

    /**
     * Receive packet.
     *
     * @param p packet for receiving
     * @throws IOException if something goes wrong during receiving
     */
    @Throws(IOException::class)
    protected abstract fun receive(p: DatagramPacket)

    /**
     * Listens for incoming datagram packets, stores them for reading by the `read` method
     * and notifies the local `transferHandler` that there's data to be read.
     */
    private fun runInReceiveThread() {
        val p = DatagramPacket(buffer, 0, PACKET_RECEIVE_BUFFER_LENGTH)
        while (!closed) {
            // Reset the buffer, because the previous call to receive() might
            // have bumped the offset or even changed the byte[].
            p.setData(buffer, 0, buffer.size)
            try {
                receive(p)
            } catch (ste: SocketTimeoutException) {
                // We need to handle these, because some of our implementations
                // of DatagramSocket#receive are unable to throw a SocketClosed exception.
                Timber.log(TimberLog.FINER, "Socket timeout, closed = %s", closed)
                continue
            } catch (e: IOException) {
                ioError = true
                break
            }
            numberOfReceivedBytes += p.length.toLong()
            try {
                // Do the DatagramPacketFilters accept the received DatagramPacket?
                if (accept(p)) {
                    val pkts = createRawPacket(p)
                    transferData(pkts)
                }
            } catch (e: Exception) {
                // The receive thread should not die as a result of a failure in
                // the packetization (converting to RawPacket[] and transforming)
                // or a failure in any of the DatagramPacketFilters.
                Timber.e(e, "Failed to receive a packet: ")
            }
        }
    }

    /**
     * Enables or disables this `RTPConnectorInputStream`. While the stream is disabled, it
     * does not accept any packets.
     *
     * @param enabled `true` to enable, `false` to disable.
     */
    fun setEnabled(enabled: Boolean) {
        if (this.enabled != enabled) {
            Timber.log(TimberLog.FINER, "setEnabled: $enabled")
            this.enabled = enabled
        }
    }

    /**
     * Changes current thread priority.
     *
     * @param priority the new priority.
     */
    fun setPriority(priority: Int) {
        // if (receiverThread != null)
        // receiverThread.setPriority(priority);
    }

    @Throws(IOException::class)
    protected abstract fun setReceiveBufferSize(receiveBufferSize: Int)

    /**
     * Sets the `transferHandler` that this connector should be notifying when new data is
     * available for reading.
     *
     * @param transferHandler the `transferHandler` that this connector should be notifying when new data is
     * available for reading.
     */
    @Synchronized
    override fun setTransferHandler(transferHandler: SourceTransferHandler) {
        if (this.transferHandler != transferHandler) {
            this.transferHandler = transferHandler
            maybeStartReceiveThread()
        }
    }

    /**
     * Invokes [SourceTransferHandler.transferData] on
     * [.transferHandler] for each of `pkts` in order to consecutively push them out
     * of/make them available outside this `PushSourceStream`.
     *
     * @param pkts the set of `RawPacket`s to push out of this `PushSourceStream`
     */
    private fun transferData(pkts: Array<RawPacket?>) {
        for (i in pkts.indices) {
            val pkt = pkts[i]
            pkts[i] = null
            if (pkt != null) {
                if (pkt.isInvalid) {
                    /*
                     * Return pkt to the pool because it is invalid and, consequently, will not be
                     * made available to reading.
                     */
                    poolRawPacket(pkt)
                } else {
                    var oldPkt: RawPacket?
                    synchronized(pktSyncRoot) {
                        oldPkt = this.pkt
                        this.pkt = pkt
                    }
                    if (oldPkt != null) {
                        /*
                         * Return oldPkt to the pool because it was made available to reading
                         * and it
                         * was not read.
                         */
                        poolRawPacket(oldPkt!!)
                    }
                    if (transferHandler != null && !closed) {
                        try {
                            transferHandler!!.transferData(this)
                        } catch (t: Throwable) {
                            // XXX We cannot allow transferHandler to kill us.
                            when (t) {
                                is InterruptedException -> {
                                    Thread.currentThread().interrupt()
                                }
                                is ThreadDeath -> {
                                    throw t
                                }
                                else -> {
                                    Timber.w(t, "An RTP packet may have not been fully handled.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        /**
         * The value of the property `controls` of `RTPConnectorInputStream` when there
         * are no controls. Explicitly defined in order to reduce unnecessary allocations.
         */
        private val EMPTY_CONTROLS = arrayOfNulls<Any>(0)

        /**
         * The length in bytes of the buffers of `RTPConnectorInputStream` receiving packets from the network.
         */
        const val PACKET_RECEIVE_BUFFER_LENGTH = 4 * 1024

        /**
         * The name of the property which controls the size of the receive buffer
         * which [RTPConnectorInputStream] will request for the sockets that it uses.
         */
        val SO_RCVBUF_PNAME = RTPConnectorInputStream::class.java.name + ".SO_RCVBUF"

        /**
         * Sets a specific priority on a specific `Thread`.
         *
         * @param thread the `Thread` to set the specified `priority` on
         * @param priority the priority to set on the specified `thread`
         */
        fun setThreadPriority(thread: Thread, priority: Int) {
            val oldPriority = thread.priority
            if (priority != oldPriority) {
                var throwable: Throwable? = null
                try {
                    thread.priority = priority
                } catch (iae: IllegalArgumentException) {
                    throwable = iae
                } catch (iae: SecurityException) {
                    throwable = iae
                }
                if (throwable != null) {
                    Timber.w("Failed to use Thread priority: %s", priority)
                }
                val newPriority = thread.priority
                if (priority != newPriority) {
                    Timber.d("Did not change Thread priority from %s => %s; keep %s instead.",
                            oldPriority, priority, newPriority)
                }
            }
        }
    }
}