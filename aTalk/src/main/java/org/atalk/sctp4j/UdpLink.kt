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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Class used in code samples to send SCTP packets through UDP sockets.
 *
 *
 * FIXME: fix receiving loop
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class UdpLink(
        /**
         * `SctpSocket` instance that is used in this connection.
         */
        private val sctpSocket: SctpSocket?,
        localIp: String?, localPort: Int,
        remoteIp: String?, remotePort: Int) : NetworkLink {
    /**
     * Udp socket used for transport.
     */
    private val udpSocket: DatagramSocket

    /**
     * Destination UDP port.
     */
    private val remotePort: Int

    /**
     * Destination `InetAddress`.
     */
    private val remoteIp: InetAddress

    /**
     * Creates new instance of `UdpConnection`.
     *
     * @param sctpSocket SCTP socket instance used by this connection.
     * @param localIp local IP address.
     * @param localPort local UDP port.
     * @param remoteIp remote address.
     * @param remotePort destination UDP port.
     * @throws IOException when we fail to resolve any of addresses
     * or when opening UDP socket.
     */
    init {
        udpSocket = DatagramSocket(localPort, InetAddress.getByName(localIp))
        this.remotePort = remotePort
        this.remoteIp = InetAddress.getByName(remoteIp)

        // Listening thread
        Thread {
            try {
                val buff = ByteArray(2048)
                val p = DatagramPacket(buff, 2048)
                while (true) {
                    udpSocket.receive(p)
                    sctpSocket!!.onConnIn(p.data, p.offset, p.length)
                }
            } catch (e: IOException) {
                Timber.e(e)
            }
        }.start()
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun onConnOut(s: SctpSocket, packetData: ByteArray) {
        val packet = DatagramPacket(packetData,
                packetData.size, remoteIp, remotePort)
        udpSocket.send(packet)
    }
}