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
package org.atalk.sctp4j

import timber.log.Timber
import java.io.IOException

/**
 * SCTP socket implemented using "usrsctp" lib.
 *
 * @author Pawel Domas
 * @author George Politis
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class SctpSocket internal constructor(ptr: Long, localPort: Int) {
    /**
     * The indicator which determines whether [.close] has been invoked
     * on this `SctpSocket`. It does NOT indicate whether
     * [Sctp.closeSocket] has been invoked with [.ptr].
     */
    private var closed = false

    /**
     * Callback used to notify about received data.
     */
    private var dataCallback: SctpDataCallback? = null

    /**
     * The link used to send network packets.
     */
    private var link: NetworkLink? = null
    /**
     * Returns SCTP port used by this socket.
     *
     * @return SCTP port used by this socket.
     */
    /**
     * Local SCTP port.
     */
    val port: Int

    /**
     * SCTP notification listener.
     */
    private var notificationListener = object : NotificationListener {
        /**
         * {@inheritDoc}
         */
        override fun onSctpNotification(socket: SctpSocket?, notification: SctpNotification?) {
            Timber.d("SctpSocket %08x notification: %s", ptr, notification)
        }
    }

    /**
     * Pointer to native socket counterpart.
     */
    private var ptr: Long

    /**
     * The number of current readers of [.ptr] which are preventing the
     * writer (i.e. [.close]) from invoking
     * [Sctp.closeSocket].
     */
    private var ptrLockCount = 0

    /**
     * Creates new instance of `SctpSocket`.
     *
     * ptr native socket pointer.
     * localPort local SCTP port on which this socket is bound.
     */
    init {
        if (ptr == 0L) throw NullPointerException("ptr")
        this.ptr = ptr
        port = localPort

        // We slightly changed the synchronization scheme used in this class in
        // order to avoid a deadlock.
        //
        // What happened is that both A and B must have selected a participant
        // at about the same time (it's not important, but, for the shake of
        // example, suppose that A selected B and that B selected A). That
        // resulted in a data channel message being fired from both endpoints
        // notifying the bridge who's the selected participant at each endpoint
        // and locking 0x0000000775ec1010 and 0x0000000775e123b8.
        //
        // Upon reception of the selection notification from A, his simulcast
        // manager decided to request from B (by sending a data channel message)
        // to start its high quality stream (waiting to lock 0x0000000775ec1010)
        // and visa-versa, i.e. B's simulcast manager decided to request from
        // his endpoint to start A's high quality stream (waiting to lock
        // 0x0000000775e123b8). Boom!
        //
        // Possible solutions are:
        //
        // 1. use an incoming SCTP packets queue and a single processing thread.
        // 2. use an outgoing SCTP packets queue and a single sending thread.
        // 3. when there are incoming SCTP packets, execute each data callback
        //    in its own thread without queuing
        // 4. when there are outgoing SCTP packets, execute the send in its own
        //    thread without queuing (I'm not sure whether the underlying native
        //    SCTP socket is thread safe though, so this could be a little
        //    risky)
        // 5. a combination of the above
        // 6. change the synchronization scheme
        //
        // However, usrsctp seems to already be queuing packets and having
        // sending/processing threads so there's no need to duplicate this
        // functionality here. We implement a readers-writers scheme that
        // protects the native socket pointer instead.
    }

    /**
     * Accepts incoming SCTP connection.
     * FIXME:
     * Usrscp is currently configured to work in non blocking mode thus this
     * method should be polled in intervals.
     *
     * @return `true` if we have accepted incoming connection
     * successfully.
     */
    @Throws(IOException::class)
    fun accept(): Boolean {
        val ptr = lockPtr()
        val r = try {
            Sctp.usrsctp_accept(ptr)
        } finally {
            unlockPtr()
        }
        return r
    }

    /**
     * Closes this socket. After call to this method this instance MUST NOT be
     * used.
     */
    fun close() {
        // The value of the field closed only ever changes from false to true.
        // Additionally, its reading is always synchronized and combined with
        // access to the field ptrLockCount governed by logic which binds the
        // meanings of the two values together. Consequently, the
        // synchronization with respect to closed is considered consistent.
        // Allowing the writing outside the synchronized block expedites the
        // actual closing of ptr.
        closed = true
        var ptr: Long
        synchronized(this) {
            if (ptrLockCount == 0) {
                // The actual closing of ptr will not be deferred.
                ptr = this.ptr
                this.ptr = 0
            } else {
                // The actual closing of ptr will be deferred.
                ptr = 0
            }
        }
        if (ptr != 0L) Sctp.closeSocket(ptr)
    }

    /**
     * Initializes SCTP connection by sending INIT message.
     *
     * @param remotePort remote SCTP port.
     * @throws java.io.IOException if this socket is closed or an error occurs
     * while trying to connect the socket.
     */
    @Throws(IOException::class)
    fun connect(remotePort: Int) {
        val ptr = lockPtr()
        try {
            if (!Sctp.usrsctp_connect(ptr, remotePort)) throw IOException("Failed to connect SCTP")
        } finally {
            unlockPtr()
        }
    }

    /**
     * Makes SCTP socket passive.
     */
    @Throws(IOException::class)
    fun listen() {
        val ptr = lockPtr()
        try {
            Sctp.usrsctp_listen(ptr)
        } finally {
            unlockPtr()
        }
    }

    /**
     * Locks [.ptr] for reading and returns its value if this
     * `SctpSocket` has not been closed (yet). Each `lockPtr`
     * method invocation must be balanced with a subsequent `unlockPtr`
     * method invocation.
     *
     * @return `ptr`
     * @throws IOException if this `SctpSocket` has (already) been closed
     */
    @Throws(IOException::class)
    private fun lockPtr(): Long {
        var ptr: Long
        synchronized(this) {
            // It may seem that the synchronization with respect to the field
            // closed is inconsistent because there is no synchronization upon
            // writing its value. It is consistent though.
            if (closed) {
                throw IOException("SctpSocket is closed!")
            } else {
                ptr = this.ptr
                if (ptr == 0L) throw IOException("SctpSocket is closed!") else ++ptrLockCount
            }
        }
        return ptr
    }

    /**
     * Call this method to pass network packets received on the link.
     *
     * @param packet network packet received.
     * @param offset the position in the packet buffer where actual data starts
     * @param len length of packet data in the buffer.
     */
    @Throws(IOException::class)
    fun onConnIn(packet: ByteArray?, offset: Int, len: Int) {
        if (packet == null) {
            throw NullPointerException("packet")
        }
        require((offset < 0 || len <= 0 || offset + len <= packet.size)) { "o: " + offset + " l: " + len + " packet l: " + packet.size }
        val ptr = lockPtr()
        try {
            Sctp.onConnIn(ptr, packet, offset, len)
        } finally {
            unlockPtr()
        }
    }

    /**
     * Fired when usrsctp stack sends notification.
     *
     * @param notification the `SctpNotification` triggered.
     */
    private fun onNotification(notification: SctpNotification) {
        if (notificationListener != null) {
            notificationListener.onSctpNotification(this, notification)
        }
    }

    /**
     * Method fired by SCTP stack to notify about incoming data.
     *
     * @param data buffer holding received data
     * @param sid stream id
     * @param ssn
     * @param tsn
     * @param ppid payload protocol identifier
     * @param context
     * @param flags
     */
    private fun onSctpIn(
            data: ByteArray, sid: Int, ssn: Int, tsn: Int, ppid: Long, context: Int,
            flags: Int) {
        if (dataCallback != null) {
            dataCallback!!.onSctpPacket(
                    data, sid, ssn, tsn, ppid, context, flags)
        } else {
            Timber.w("No dataCallback set, dropping a message from usrsctp")
        }
    }

    /**
     * Notifies this `SctpSocket` about incoming data.
     *
     * @param data buffer holding received data
     * @param sid stream id
     * @param ssn
     * @param tsn
     * @param ppid payload protocol identifier
     * @param context
     * @param flags
     */
    fun onSctpInboundPacket(data: ByteArray, sid: Int, ssn: Int, tsn: Int, ppid: Long,
            context: Int, flags: Int) {
        if (flags and Sctp.MSG_NOTIFICATION != 0) {
            onNotification(SctpNotification.parse(data))
        } else {
            onSctpIn(data, sid, ssn, tsn, ppid, context, flags)
        }
    }

    /**
     * Callback triggered by Sctp stack whenever it wants to send some network
     * packet.
     *
     * @param packet network packet buffer.
     * @param tos type of service???
     * @param set_df use IP don't fragment option
     * @return 0 if the packet was successfully sent or -1 otherwise.
     */
    fun onSctpOut(packet: ByteArray, tos: Int, set_df: Int): Int {
        val link = link
        var ret = -1
        if (link != null) {
            try {
                link.onConnOut(this, packet)
                ret = 0
            } catch (e: IOException) {
                Timber.e(e, "Error while sending packet trough the link: %s,", link)
            }
        }
        return ret
    }

    /**
     * Sends given `data` on selected SCTP stream using given payload
     * protocol identifier.
     *
     * @param data the data to send.
     * @param ordered should we care about message order ?
     * @param sid SCTP stream identifier
     * @param ppid payload protocol identifier
     * @return sent bytes count or `-1` in case of an error.
     */
    @Throws(IOException::class)
    fun send(data: ByteArray, ordered: Boolean, sid: Int, ppid: Int): Int {
        return send(data, 0, data.size, ordered, sid, ppid)
    }

    /**
     * Sends given `data` on selected SCTP stream using given payload
     * protocol identifier.
     *
     * @param data the data to send.
     * @param offset position of the data inside the buffer
     * @param len data length
     * @param ordered should we care about message order ?
     * @param sid SCTP stream identifier
     * @param ppid payload protocol identifier
     * @return sent bytes count or `-1` in case of an error.
     */
    @Throws(IOException::class)
    fun send(
            data: ByteArray?, offset: Int, len: Int,
            ordered: Boolean,
            sid: Int, ppid: Int): Int {
        if (data == null) {
            throw NullPointerException("data")
        }
        require((offset < 0 || len <= 0 || offset + len <= data.size)) { "o: " + offset + " l: " + len + " data l: " + data.size }
        val ptr = lockPtr()
        val r = try {
            Sctp.usrsctp_send(ptr, data, offset, len, ordered, sid, ppid)
        } finally {
            unlockPtr()
        }
        return r
    }

    /**
     * Sets the callback that will be fired when new data is received.
     *
     * @param callback the callback that will be fired when new data is
     * received.
     */
    fun setDataCallback(callback: SctpDataCallback?) {
        dataCallback = callback
    }

    /**
     * Sets the link that will be used to send network packets.
     *
     * @param link `NetworkLink` that will be used by this instance to
     * send network packets.
     */
    fun setLink(link: NetworkLink?) {
        this.link = link
    }

//    /**
//     * Sets the listener that will be notified about SCTP event.
//     *
//     * @param listener the [NotificationListener] to set.
//     */
//    fun setNotificationListener(listener: NotificationListener) {
//        notificationListener = listener
//    }

    /**
     * Unlocks [.ptr] for reading. If this `SctpSocket` has been
     * closed while `ptr` was locked for reading and there are no other
     * readers at the time of the method invocation, closes `ptr`. Each
     * `unlockPtr` method invocation must be balanced with a previous
     * `lockPtr` method invocation.
     */
    private fun unlockPtr() {
        var ptr: Long
        synchronized(this) {
            val ptrLockCount = ptrLockCount - 1
            if (ptrLockCount < 0) {
                throw RuntimeException(
                        "Unbalanced SctpSocket#unlockPtr() method invocation!")
            } else {
                this.ptrLockCount = ptrLockCount
                if (closed && ptrLockCount == 0) {
                    // The actual closing of ptr was deferred until now.
                    ptr = this.ptr
                    this.ptr = 0
                } else {
                    // The actual closing of ptr may not have been requested or
                    // will be deferred.
                    ptr = 0
                }
            }
        }
        if (ptr != 0L) Sctp.closeSocket(ptr)
    }

    /**
     * Interface used to listen for SCTP notifications on specific socket.
     */
    interface NotificationListener {
        /**
         * Fired when usrsctp stack sends notification.
         *
         * @param socket the [SctpSocket] notification source.
         * @param notification the `SctpNotification` triggered.
         */
        fun onSctpNotification(
                socket: SctpSocket?,
                notification: SctpNotification?)
    }

    companion object {
        /**
         * Reads 32 bit unsigned int from the buffer at specified offset
         *
         * @param buffer
         * @param offset
         * @return 32 bit unsigned value
         */
        private fun bytes_to_long(buffer: ByteArray, offset: Int): Long {
            val fByte = 0x000000FF and buffer[offset].toInt()
            val sByte = 0x000000FF and buffer[offset + 1].toInt()
            val tByte = 0x000000FF and buffer[offset + 2].toInt()
            val foByte = 0x000000FF and buffer[offset + 3].toInt()
            return ((fByte shl 24 or (sByte shl 16
                    ) or (tByte shl 8
                    ) or foByte).toLong()
                    and 0xFFFFFFFFL)
        }

        /**
         * Reads 16 bit unsigned int from the buffer at specified offset
         *
         * @param buffer
         * @param offset
         * @return 16 bit unsigned int
         */
        private fun bytes_to_short(buffer: ByteArray, offset: Int): Int {
            val fByte = 0x000000FF and buffer[offset].toInt()
            val sByte = 0x000000FF and buffer[offset + 1].toInt()
            return fByte shl 8 or sByte and 0xFFFF
        }

        private fun debugChunks(packet: ByteArray) {
            var offset = 12 // After common header
            while (packet.size - offset >= 4) {
                val chunkType = packet[offset++].toInt() and 0xFF
                val chunkFlags = packet[offset++].toInt() and 0xFF
                val chunkLength = bytes_to_short(packet, offset)
                offset += 2
                Timber.d("CH: %d; FL: %d; L: %d", chunkType, chunkFlags, chunkLength)
                when (chunkType) {
                    1 -> {
                        //Init chunk info
                        val initTag = bytes_to_long(packet, offset)
                        offset += 4
                        val a_rwnd = bytes_to_long(packet, offset)
                        offset += 4
                        val nOutStream = bytes_to_short(packet, offset)
                        offset += 2
                        val nInStream = bytes_to_short(packet, offset)
                        offset += 2
                        val initTSN = bytes_to_long(packet, offset)
                        offset += 4
                        Timber.d("ITAG: %08x; a_rwnd: %d; nOutStream: %d; nInStream: %d; initTSN: %08x",
                                initTag, a_rwnd, nOutStream, nInStream, initTSN)

                        // Parse Type-Length-Value chunks
                        /*while(offset < chunkLength)
                        {
                            //System.out.println(packet[offset++]&0xFF);
                            int type = bytes_to_short(packet, offset);
                            offset += 2;

                            int length = bytes_to_short(packet, offset);
                            offset += 2;

                            // value
                            offset += (length-4);
                            System.out.println(
                                "T: "+type+" L: "+length+" left: "+(chunkLength-offset));
                        }*/
                        offset += chunkLength - 4 - 16
                    }
                    0 -> {
                        // Payload
                        val U = chunkFlags and 0x4 > 0
                        val B = chunkFlags and 0x2 > 0
                        val E = chunkFlags and 0x1 > 0
                        val TSN = bytes_to_long(packet, offset)
                        offset += 4
                        val streamIdS = bytes_to_short(packet, offset)
                        offset += 2
                        val streamSeq = bytes_to_short(packet, offset)
                        offset += 2
                        val PPID = bytes_to_long(packet, offset)
                        offset += 4
                        Timber.d("U: %s B: %s E: %s TSN: %08x; SID: %08x; SSEQ: %08x; PPID: %08x;",
                                U, B, E, TSN, streamIdS, streamSeq, PPID)
                        offset += chunkLength - 4 - 12
                    }
                    6 -> {
                        // Abort
                        Timber.d("We have abort!!!")
                        if (offset >= chunkLength) Timber.d("No abort CAUSE!!!")
                        while (offset < chunkLength) {
                            val causeCode = bytes_to_short(packet, offset)
                            offset += 2
                            val causeLength = bytes_to_short(packet, offset)
                            offset += 2
                            Timber.d("Cause: %d; L: %d ", causeCode, causeLength)
                        }
                    }
                    else -> {
                        offset += chunkLength - 4
                    }
                }
            }
        }

        fun debugSctpPacket(packet: ByteArray, id: String?) {
            println(id)
            if (packet.size >= 12) {
                //Common header
                val srcPort = bytes_to_short(packet, 0)
                val dstPort = bytes_to_short(packet, 2)
                val verificationTag = bytes_to_long(packet, 4)
                val checksum = bytes_to_long(packet, 8)
                Timber.d("SRC P: %d; DST P:%d;  VTAG: %08x; CHK: %08x",
                        srcPort, dstPort, verificationTag, checksum)
                debugChunks(packet)
            }
        }
    }
}