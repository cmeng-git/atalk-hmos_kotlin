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
import java.util.concurrent.ConcurrentHashMap

/**
 * Class encapsulates native SCTP counterpart.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
object Sctp {
    /**
     * FIXME Remove once usrsctp_finish is fixed
     */
    private var initialized = false

    /**
     * SCTP notification
     */
    const val MSG_NOTIFICATION = 0x2000

    /**
     * Track the number of currently running SCTP engines.
     * Each engine calls [.init] on startup and [.finish] on shutdown. We want
     * [.init] to be effectively called only when there are 0 engines currently running
     * and [.finish] when the last one is performing a shutdown.
     */
    private const val sctpEngineCount = 0

    /**
     * List of instantiated `SctpSockets` mapped by native pointer.
     */
    private val sockets = ConcurrentHashMap<Long, SctpSocket>()

    init {
        val lib = "jnsctp"
        try {
            System.loadLibrary(lib)
        } catch (t: Throwable) {
            Timber.e("Failed to load native library %s: %s", lib, t.message)
            if (t is Error) throw t else throw (t as RuntimeException)
        }
    }

    /**
     * Closes SCTP socket addressed by given native pointer.
     *
     * @param ptr native socket pointer.
     */
    fun closeSocket(ptr: Long) {
        usrsctp_close(ptr)
        sockets.remove(ptr)
    }

    /**
     * Creates new `SctpSocket` for given SCTP port. Allocates native resources bound to
     * the socket.
     *
     * @param localPort local SCTP socket port.
     * @return new `SctpSocket` for given SCTP port.
     */
    fun createSocket(localPort: Int): SctpSocket? {
        val ptr = usrsctp_socket(localPort)
        val socket: SctpSocket?
        if (ptr == 0L) {
            socket = null
        } else {
            socket = SctpSocket(ptr, localPort)
            sockets[ptr] = socket
        }
        return socket
    }

    /**
     * Disposes of the resources held by native counterpart.
     *
     * @throws IOException if usrsctp stack has failed to shutdown.
     */
    @Synchronized
    @Throws(IOException::class)
    fun finish() {
        // Skip if we're not the last one
        //if(--sctpEngineCount > 0)
        //  return;

        //try
        //{
        // FIXME fix this loop?
        // it comes from SCTP samples written in C

        // Retry limited amount of times
        /*
			  FIXME usrsctp issue:
              SCTP stack is now never deinitialized in order to prevent deadlock
              in usrsctp_finish.
              https://code.google.com/p/webrtc/issues/detail?id=2749

            final int CLOSE_RETRY_COUNT = 20;

            for(int i=0; i < CLOSE_RETRY_COUNT; i++)
            {
                if(usrsctp_finish())
                    return;

                Thread.sleep(50);
            }*/

        //FIXME after throwing we might end up with other SCTP users broken
        // (or stack not disposed) at this point because engine count will
        // be out of sync for the purpose of calling init() and finish()
        // methods.
        //    throw new IOException("Failed to shutdown usrsctp stack" +
        //                              " after 20 retries");
        //}
        //catch(InterruptedException e)
        //{
        //    Timber.e(e, "Finish interrupted");
        //    Thread.currentThread().interrupt();
        //}
    }

    /**
     * Initializes native SCTP counterpart.
     */
    @Synchronized
    fun init() {
        // Skip if we're not the first one
        //if(sctpEngineCount++ > 0)
        //    return;
        if (!initialized) {
            Timber.e("Init'ing brian's patched usrsctp")
            usrsctp_init(0)
            initialized = true
        }
    }

    /**
     * Passes network packet to native SCTP stack counterpart.
     *
     * @param ptr native socket pointer.
     * @param pkt buffer holding network packet data.
     * @param off the position in the buffer where packet data starts.
     * @param len packet data length.
     */
    private external fun on_network_in(ptr: Long, pkt: ByteArray, off: Int, len: Int)

    /**
     * Used by [SctpSocket] to pass received network packet to native
     * counterpart.
     *
     * @param socketPtr native socket pointer.
     * @param packet network packet data.
     * @param offset position in the buffer where packet data starts.
     * @param len length of packet data in the buffer.
     */
    fun onConnIn(socketPtr: Long, packet: ByteArray, offset: Int, len: Int) {
        on_network_in(socketPtr, packet, offset, len)
    }

    /**
     * Method fired by native counterpart to notify about incoming data.
     *
     * @param socketAddr native socket pointer
     * @param data buffer holding received data
     * @param sid stream id
     * @param ssn
     * @param tsn
     * @param ppid payload protocol identifier
     * @param context
     * @param flags
     */
    fun onSctpInboundPacket(
            socketAddr: Long, data: ByteArray, sid: Int, ssn: Int, tsn: Int, ppid: Long,
            context: Int, flags: Int) {
        val socket = sockets[socketAddr]
        if (socket == null) {
            Timber.e("No SctpSocket found for ptr: %s", socketAddr)
        } else {
            socket.onSctpInboundPacket(data, sid, ssn, tsn, ppid, context, flags)
        }
    }

    /**
     * Method fired by native counterpart when SCTP stack wants to send network packet.
     *
     * @param socketAddr native socket pointer
     * @param data buffer holding packet data
     * @param tos type of service???
     * @param set_df use IP don't fragment option
     * @return 0 if the packet has been successfully sent or -1 otherwise.
     */
    fun onSctpOutboundPacket(
            socketAddr: Long, data: ByteArray, tos: Int, set_df: Int): Int {
        // FIXME handle tos and set_df
        val socket = sockets[socketAddr]
        val ret: Int
        if (socket == null) {
            ret = -1
            Timber.e("No SctpSocket found for ptr: %s", socketAddr)
        } else {
            ret = socket.onSctpOut(data, tos, set_df)
        }
        return ret
    }

    /**
     * Waits for incoming connection.
     *
     * @param ptr native socket pointer.
     */
    external fun usrsctp_accept(ptr: Long): Boolean

    /**
     * Closes SCTP socket.
     *
     * @param ptr native socket pointer.
     */
    private external fun usrsctp_close(ptr: Long)

    /**
     * Connects SCTP socket to remote socket on given SCTP port.
     *
     * @param ptr native socket pointer.
     * @param remotePort remote SCTP port.
     * @return `true` if the socket has been successfully connected.
     */
    external fun usrsctp_connect(ptr: Long, remotePort: Int): Boolean

    /**
     * Disposes of the resources held by native counterpart.
     *
     * @return `true` if stack successfully released resources.
     */
    private external fun usrsctp_finish(): Boolean

    /**
     * Initializes native SCTP counterpart.
     *
     * @param port UDP encapsulation port.
     * @return `true` on success.
     */
    private external fun usrsctp_init(port: Int): Boolean

    /**
     * Makes socket passive.
     *
     * @param ptr native socket pointer.
     */
    external fun usrsctp_listen(ptr: Long)

    /**
     * Sends given `data` on selected SCTP stream using given payload
     * protocol identifier.
     * FIXME add offset and length buffer parameters.
     *
     * @param ptr native socket pointer.
     * @param data the data to send.
     * @param off the position of the data inside the buffer
     * @param len data length.
     * @param ordered should we care about message order ?
     * @param sid SCTP stream identifier
     * @param ppid payload protocol identifier
     * @return sent bytes count or `-1` in case of an error.
     */
    external fun usrsctp_send(
            ptr: Long, data: ByteArray?, off: Int, len: Int, ordered: Boolean, sid: Int, ppid: Int): Int

    /**
     * Creates native SCTP socket and returns pointer to it.
     *
     * @param localPort local SCTP socket port.
     * @return native socket pointer or 0 if operation failed.
     */
    private external fun usrsctp_socket(localPort: Int): Long /*
    FIXME to be added?
    int usrsctp_shutdown(struct socket *so, int how);
    */
}