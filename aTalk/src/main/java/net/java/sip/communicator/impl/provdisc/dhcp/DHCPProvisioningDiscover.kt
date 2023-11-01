/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
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
package net.java.sip.communicator.impl.provdisc.dhcp

import net.java.sip.communicator.service.provdisc.event.DiscoveryEvent
import net.java.sip.communicator.service.provdisc.event.DiscoveryListener
import org.dhcp4java.DHCPConstants
import org.dhcp4java.DHCPOption
import org.dhcp4java.DHCPPacket
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.*

/**
 * Class that will perform DHCP provisioning discovery.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class DHCPProvisioningDiscover constructor(
        /**
         * Listening port of the client. Note that the socket will send packet to DHCP server on port - 1.
         */
        private val port: Int, option: Byte) : Runnable {
    /**
     * UDP socket.
     */
    private val socket: DatagramSocket

    /**
     * DHCP transaction number.
     */
    private val xid: Int

    /**
     * Option code of the specific provisioning option.
     */
    private var option = 224.toByte()

    /**
     * List of `ProvisioningListener` that will be notified when a provisioning URL is retrieved.
     */
    private val listeners = ArrayList<DiscoveryListener>()

    /**
     * Constructor.
     *
     * port port on which we will bound and listen for DHCP response
     * option code of the specific provisioning option
     * Exception if anything goes wrong during initialization
     */
    init {
        this.option = option
        socket = DatagramSocket(port)
        xid = Random().nextInt()

        /*
         * set timeout so that we will not blocked forever if we have no response from DHCP server
         */
        socket.soTimeout = DHCP_TIMEOUT
    }

    /**
     * It sends a DHCPINFORM message from all interfaces and wait for a response. Thread stops
     * after first successful answer that contains specific option and thus the provisioning URL.
     *
     * @return provisioning URL
     */
    fun discoverProvisioningURL(): String? {
        val inform = DHCPPacket()
        var macAddress: ByteArray?
        val zeroIPAddress = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        val broadcastIPAddr = byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte())
        val dhcpOpts = arrayOfNulls<DHCPOption>(1)
        val transactions = ArrayList<DHCPTransaction>()
        try {
            inform.op = DHCPConstants.BOOTREQUEST
            inform.htype = DHCPConstants.HTYPE_ETHER
            inform.hlen = 6.toByte()
            inform.hops = 0.toByte()
            inform.xid = xid
            inform.secs = 0.toShort()
            inform.flags = 0.toShort()
            inform.setYiaddr(InetAddress.getByAddress(zeroIPAddress))
            inform.setSiaddr(InetAddress.getByAddress(zeroIPAddress))
            inform.setGiaddr(InetAddress.getByAddress(zeroIPAddress))
            //inform.setChaddr(macAddress);
            inform.isDhcp = true
            inform.setDHCPMessageType(DHCPConstants.DHCPINFORM)
            dhcpOpts[0] = DHCPOption(DHCPConstants.DHO_DHCP_PARAMETER_REQUEST_LIST, byteArrayOf(option))
            inform.setOptions(dhcpOpts)
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val iface = en.nextElement()
                val enAddr = iface.inetAddresses
                while (enAddr.hasMoreElements()) {
                    val addr = enAddr.nextElement()

                    /* just take IPv4 address */
                    if (addr is Inet4Address) {
                        val netaddr = ProvisioningDiscoveryDHCPActivator.networkAddressManagerService
                        if (!addr.isLoopbackAddress()) {
                            macAddress = netaddr!!.getHardwareAddress(iface)
                            val p = inform.clone()
                            p.setCiaddr(addr)
                            p.chaddr = macAddress
                            val msg = p.serialize()
                            val pkt = DatagramPacket(msg,
                                    msg.size, InetAddress.getByAddress(broadcastIPAddr), port - 1)
                            val transaction = DHCPTransaction(socket, pkt)
                            transaction.schedule()
                            transactions.add(transaction)
                        }
                    }
                }
            }

            /*
             * now see if we receive DHCP ACK response and if it contains our custom option
             */
            var found = false
            try {
                val pkt2 = DatagramPacket(ByteArray(1500), 1500)
                while (!found) {
                    /* we timeout after some seconds if no DHCP response are received
                     */
                    socket.receive(pkt2)
                    val dhcp = DHCPPacket.getPacket(pkt2)
                    if (dhcp.xid != xid) {
                        continue
                    }

                    val optProvisioning = dhcp.getOption(option)
                    /* notify */
                    if (optProvisioning != null) {
                        found = true
                        for (t: DHCPTransaction in transactions) {
                            t.cancel()
                        }
                        return String(optProvisioning.getValue()!!)
                    }
                }
            } catch (est: SocketTimeoutException) {
                Timber.w(est, "Timeout, no DHCP answer received")
            }
        } catch (e: Exception) {
            Timber.w(e, "Exception occurred during DHCP discover")
        }
        for (t: DHCPTransaction in transactions) {
            t.cancel()
        }
        return null
    }

    /**
     * Thread entry point. It runs `discoverProvisioningURL` in a separate thread.
     */
    override fun run() {
        val url = discoverProvisioningURL()
        if (url != null) {
            /* as we run in an asynchronous manner, notify the listener */
            val evt = DiscoveryEvent(this, url)
            for (listener: DiscoveryListener in listeners) {
                listener.notifyProvisioningURL(evt)
            }
        }
    }

    /**
     * Add a listener that will be notified when the `discoverProvisioningURL` has finished.
     *
     * @param listener `ProvisioningListener` to add
     */
    fun addDiscoveryListener(listener: DiscoveryListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    /**
     * Add a listener that will be notified when the `discoverProvisioningURL` has finished.
     *
     * @param listener `ProvisioningListener` to add
     */
    fun removeDiscoveryListener(listener: DiscoveryListener) {
        listeners.remove(listener)
    }

    companion object {
        /**
         * DHCP socket timeout (in milliseconds).
         */
        private const val DHCP_TIMEOUT = 10000
    }
}