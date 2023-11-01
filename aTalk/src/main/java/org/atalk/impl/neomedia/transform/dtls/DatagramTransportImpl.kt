/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.dtls

import okhttp3.internal.notifyAll
import org.atalk.impl.neomedia.AbstractRTPConnector
import org.atalk.impl.neomedia.RTPConnectorInputStream
import org.atalk.impl.neomedia.RTPConnectorOutputStream
import org.atalk.impl.neomedia.codec.video.h264.Packetizer
import org.atalk.service.neomedia.RawPacket
import org.bouncycastle.tls.*
import timber.log.Timber
import java.io.IOException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.min

/**
 * Implements [DatagramTransport] in order to integrate the Bouncy Castle Crypto APIs in
 * libjitsi for the purposes of implementing DTLS-SRTP.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class DatagramTransportImpl(componentID: Int) : DatagramTransport {
    /**
     * The ID of the component which this instance works for/is associated with.
     */
    private var componentID = 0

    /**
     * The `RTPConnector` which represents and implements the actual `DatagramSocket`
     * adapted by this instance.
     */
    private var connector: AbstractRTPConnector? = null

    /**
     * The pool of `RawPacket`s instances to reduce their allocations and garbage collection.
     */
    private val rawPacketPool: Queue<RawPacket> = LinkedBlockingQueue(RTPConnectorOutputStream.POOL_CAPACITY)

    /**
     * The queue of `RawPacket`s which have been received from the network are awaiting to be
     * received by the application through this `DatagramTransport`.
     */
    private val receiveQ: ArrayBlockingQueue<RawPacket>

    /**
     * The capacity of [.receiveQ].
     */
    private val receiveQCapacity: Int

    /**
     * The `byte` buffer which represents a datagram to be sent. It may consist of multiple
     * DTLS records which are simple encoded consecutively.
     */
    private var sendBuf: ByteArray? = null

    /**
     * The length in `byte`s of [.sendBuf] i.e. the number of `sendBuf` elements
     * which constitute actual DTLS records.
     */
    private var sendBufLength = 0

    /**
     * The `Object` that synchronizes the access to [.sendBuf], [.sendBufLength].
     */
    private val sendBufSyncRoot = Any()

    /**
     * Initializes a new `DatagramTransportImpl`.
     *
     * componentID Component.RTP if the new instance is to work on data/RTP packets or
     * Component.RTCP if the new instance is to work on control/RTCP packets
     */
    init {
        when (componentID) {
            DtlsTransformEngine.COMPONENT_RTCP,
            DtlsTransformEngine.COMPONENT_RTP,
            ->
                this.componentID = componentID
            else ->
                throw IllegalArgumentException("componentID")
        }

        receiveQCapacity = RTPConnectorOutputStream.PACKET_QUEUE_CAPACITY
        receiveQ = ArrayBlockingQueue(receiveQCapacity)
    }

    @Throws(IOException::class)
    private fun assertNotClosed(): AbstractRTPConnector {
        val connector = connector
        return connector ?: throw IOException(javaClass.name + " is closed!")
    }

    /**
     * {@inheritDoc}
     */
    override fun close() {
        setConnector(null)
    }

    @Throws(IOException::class)
    private fun doSend(buf: ByteArray, off: Int, len: Int) {
        // Do preserve the sequence of sends.
        flush()

        val connector = assertNotClosed()
        val outputStream = when (componentID) {
            DtlsTransformEngine.COMPONENT_RTCP ->
                connector.controlOutputStream
            DtlsTransformEngine.COMPONENT_RTP ->
                connector.dataOutputStream
            else -> {
                val msg = "componentID"
                val ise = IllegalStateException(msg)
                Timber.e(ise, "%s", msg)
                throw ise
            }
        }

        // Write synchronously in order to avoid our packet getting stuck in the
        // write queue (in case it is blocked waiting for DTLS to finish, for example).
        outputStream?.syncWrite(buf, off, len)
    }

    @Throws(IOException::class)
    private fun flush() {
        assertNotClosed()
        var buf: ByteArray?
        var len: Int

        synchronized(sendBufSyncRoot) {
            if (sendBuf != null && sendBufLength != 0) {
                buf = sendBuf
                sendBuf = null
                len = sendBufLength
                sendBufLength = 0
            }
            else {
                buf = null
                len = 0
            }
        }
        if (buf != null) {
            doSend(buf!!, 0, len)

            // Attempt to reduce allocations and garbage collection.
            synchronized(sendBufSyncRoot) {
                if (sendBuf == null)
                    sendBuf = buf
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun getReceiveLimit(): Int {
        val connector = connector
        var receiveLimit = connector?.receiveBufferSize ?: -1

        if (receiveLimit <= 0)
            receiveLimit = RTPConnectorInputStream.PACKET_RECEIVE_BUFFER_LENGTH
        return receiveLimit
    }

    /**
     * {@inheritDoc}
     */
    override fun getSendLimit(): Int {
        val connector = connector
        var sendLimit = connector?.sendBufferSize ?: -1
        if (sendLimit <= 0) {
            /*
             * XXX The estimation bellow is wildly inaccurate and hardly related but we have to start somewhere.
             */
            sendLimit = DtlsPacketTransformer.DTLS_RECORD_HEADER_LENGTH + Packetizer.MAX_PAYLOAD_SIZE
        }
        return sendLimit
    }

    /**
     * Queues a packet received from the network to be received by the application through this `DatagramTransport`.
     *
     * @param buf the array of `byte`s which contains the packet to be queued
     * @param off the offset within `buf` at which the packet to be queued starts
     * @param len the length within `buf` starting at `off` of the packet to be queued
     */
    fun queueReceive(buf: ByteArray, off: Int, len: Int) {
        if (len > 0) {
            synchronized(receiveQ) {
                try {
                    assertNotClosed()
                } catch (ioe: IOException) {
                    throw IllegalStateException(ioe)
                }

                var pkt = rawPacketPool.poll()
                val pktBuf: ByteArray
                if (pkt == null || pkt.buffer.size < len) {
                    pktBuf = ByteArray(len)
                    pkt = RawPacket(pktBuf, 0, len)
                }
                else {
                    pktBuf = pkt.buffer
                    pkt.length = len
                    pkt.offset = 0
                }
                System.arraycopy(buf, off, pktBuf, 0, len)

                if (receiveQ.size == receiveQCapacity) {
                    val oldPkt = receiveQ.remove()
                    rawPacketPool.offer(oldPkt)
                }
                receiveQ.add(pkt)
                receiveQ.notifyAll()
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun receive(buf: ByteArray, off: Int, len: Int, waitMillis: Int): Int {
        val enterTime = System.currentTimeMillis()

        /*
         * If this DatagramTransportImpl is to be received from, then what is to be received may be
         * a response to a request that was earlier scheduled for send.
         */
        /*
         * XXX However, it may unnecessarily break up a flight into multiple datagrams. Since we have
         * implemented the recognition of the end of flights, it should be fairly safe to rely on it alone.
         */
        // flush();

        /*
         * If no datagram is received at all and the specified waitMillis expires, a negative value
         * is to be returned in order to have the outbound flight retransmitted.
         */
        var received = -1
        var interrupted = false

        while (received < len) {
            var timeout: Long

            if (waitMillis > 0) {
                timeout = waitMillis - System.currentTimeMillis() + enterTime
                if (timeout == 0L /* wait forever */)
                    timeout = -1 /* do not wait */
            }
            else {
                timeout = waitMillis.toLong()
            }

            synchronized(receiveQ) {
                assertNotClosed()
                val pkt = receiveQ.peek()
                if (pkt != null) {
                    /*
                     * If a datagram has been received and even if it carries no/zero bytes, a
                     * non-negative value is to be returned in order to distinguish the case with
                     * that of no received datagram. If the received bytes do not represent a DTLS
                     * record, the record layer may still not retransmit the outbound flight. But
                     * that should not be much of a concern because we queue DTLS records into
                     * DatagramTransportImpl.
                     */
                    if (received < 0)
                        received = 0

                    var toReceive = len - received
                    var toReceiveIsPositive = (toReceive > 0)

                    if (toReceiveIsPositive) {
                        val pktLength = pkt.length
                        val pktOffset = pkt.offset

                        if (toReceive > pktLength) {
                            toReceive = pktLength
                            toReceiveIsPositive = (toReceive > 0)
                        }

                        if (toReceiveIsPositive) {
                            System.arraycopy(pkt.buffer, pktOffset, buf, off + received, toReceive)
                            received += toReceive
                        }

                        if (toReceive == pktLength) {
                            receiveQ.remove()
                            rawPacketPool.offer(pkt)
                        }
                        else {
                            pkt.length = pktLength - toReceive
                            pkt.offset = pktOffset + toReceive
                        }

                        if (toReceiveIsPositive) {
                            /*
                             * The specified buf has received toReceive bytes and we do not concatenate RawPackets.
                             * Set while -> break condition in Sync block
                             */
                            received = len
                        }
                    }
                    else {
                        // The specified buf has received at least len bytes.
                        // Set while -> break condition in Sync block
                        received = len
                    }
                }

                if (received < len && receiveQ.isEmpty()) {
                    if (timeout >= 0) {
                        try {
                            (receiveQ as Object).wait(timeout)
                        } catch (ie: InterruptedException) {
                            interrupted = true
                        }
                    }
                    else {
                        // The specified waitMillis has been exceeded.
                        // Set while -> break condition in Sync block
                        received = len
                    }
                }
            }

            // Execute while -> break condition outside Sync block
            if (received >= len)
                break
        }
        if (interrupted)
            Thread.currentThread().interrupt()

        return received
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun send(buf: ByteArray, offset: Int, len: Int) {
        assertNotClosed()

        // If possible, construct a single datagram from multiple DTLS records.
        if (len >= DtlsPacketTransformer.DTLS_RECORD_HEADER_LENGTH) {
            val type = TlsUtils.readUint8(buf, offset)
            var endOfFlight = false

            when (type) {
                ContentType.handshake -> {
                    // message_type is the first byte of the record layer fragment (encrypted after 'change_cipher_spec')
                    val msg_type = TlsUtils.readUint8(buf, offset + DtlsPacketTransformer.DTLS_RECORD_HEADER_LENGTH)

                    endOfFlight = when (msg_type) {
                        HandshakeType.certificate,
                        HandshakeType.certificate_request,
                        HandshakeType.certificate_verify,
                        HandshakeType.client_key_exchange,
                        HandshakeType.server_hello,
                        HandshakeType.server_key_exchange,
                        HandshakeType.new_session_ticket,
                        HandshakeType.supplemental_data,
                        -> false

                        HandshakeType.client_hello,
                        HandshakeType.finished,
                        HandshakeType.hello_request,
                        HandshakeType.hello_verify_request,
                        HandshakeType.server_hello_done,
                        -> true

                        else -> {
                            /*
                             * See DTLSRecordLayer#sendRecord(): When handling HandshakeType.finished message,
                             * it uses TlsAEADCipher#encodePlaintext() which seems not copy the sent buffer info;
                             * hence the msg_type will be random value
                             */
                            Timber.w("Received DTLS 'HandshakeType.finished' or unknown message type: %s", msg_type)
                            true
                        }
                    }

                    // Kotlin does not allow fallthrough, so have to repeat the code here
                    synchronized(sendBufSyncRoot) {
                        val newSendBufLength = sendBufLength + len
                        val sendLimit = sendLimit
                        if (newSendBufLength <= sendLimit) {
                            if (sendBuf == null) {
                                sendBuf = ByteArray(sendLimit)
                                sendBufLength = 0
                            }
                            else if (sendBuf!!.size < sendLimit) {
                                val oldSendBuf = sendBuf
                                sendBuf = ByteArray(sendLimit)
                                System.arraycopy(oldSendBuf!!, 0, sendBuf!!, 0,
                                    min(sendBufLength, sendBuf!!.size))
                            }
                            System.arraycopy(buf, offset, sendBuf!!, sendBufLength, len)
                            sendBufLength = newSendBufLength
                            if (endOfFlight) flush()
                        }
                        else {
                            if (endOfFlight) {
                                doSend(buf, offset, len)
                            }
                            else {
                                flush()
                                send(buf, offset, len)
                            }
                        }
                    }
                }

                ContentType.change_cipher_spec ->
                    synchronized(sendBufSyncRoot) {
                        val newSendBufLength = sendBufLength + len
                        val sendLimit = sendLimit
                        if (newSendBufLength <= sendLimit) {
                            if (sendBuf == null) {
                                sendBuf = ByteArray(sendLimit)
                                sendBufLength = 0
                            }
                            else if (sendBuf!!.size < sendLimit) {
                                val oldSendBuf = sendBuf
                                sendBuf = ByteArray(sendLimit)
                                System.arraycopy(oldSendBuf!!, 0, sendBuf!!, 0,
                                    min(sendBufLength, sendBuf!!.size))
                            }
                            System.arraycopy(buf, offset, sendBuf!!, sendBufLength, len)
                            sendBufLength = newSendBufLength
                        }
                        else {
                            flush()
                            send(buf, offset, len)
                        }
                    }

                ContentType.alert,
                ContentType.application_data, ->
                    doSend(buf, offset, len)

                else ->
                    doSend(buf, offset, len)
            }
        }
        else {
            doSend(buf, offset, len)
        }
    }

    /**
     * Sets the `RTPConnector` which represents and implements the actual
     * `DatagramSocket` to be adapted by this instance.
     *
     * @param connector the `RTPConnector` which represents and implements the actual
     * `DatagramSocket` to be adapted by this instance
     */
    fun setConnector(connector: AbstractRTPConnector?) {
        synchronized(receiveQ) {
            this.connector = connector
            (receiveQ as Object).notifyAll()
        }
    }
}