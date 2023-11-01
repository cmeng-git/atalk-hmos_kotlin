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
package net.java.sip.communicator.service.netaddr

import net.java.sip.communicator.service.netaddr.event.NetworkConfigurationChangeListener
import org.ice4j.ice.Agent
import org.ice4j.ice.IceMediaStream
import org.ice4j.ice.harvest.StunCandidateHarvester
import java.io.IOException
import java.net.BindException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

/**
 * The NetworkAddressManagerService takes care of problems such as
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface NetworkAddressManagerService {
    /**
     * Returns an InetAddress instance that represents the localhost, and that a socket can bind
     * upon or distribute to peers as a contact address.
     *
     * This method tries to make for the ambiguity in the implementation of the InetAddress
     * .getLocalHost() method.
     * (see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4665037).
     *
     * To put it briefly, the issue is about choosing a local source address to bind to or to
     * distribute to peers. It is possible and even quite probable to expect that a machine may
     * dispose with multiple addresses and each of them may be valid for a specific destination.
     * Example cases include:
     *
     * 1) A dual stack IPv6/IPv4 box. <br></br>
     * 2) A double NIC box with a leg on the Internet and another one in a private LAN <br></br>
     * 3) In the presence of a virtual interface over a VPN or a MobileIP(v6) tunnel.
     *
     * In all such cases a source local address needs to be chosen according to the intended
     * destination and after consulting the local routing table.
     *
     *
     * @param intendedDestination the address of the destination that we'd like to access through
     * the local address that we are requesting.
     * @return an InetAddress instance representing the local host, and that a socket can bind
     * upon or distribute to peers as a contact address.
     */
    fun getLocalHost(intendedDestination: InetAddress?): InetAddress

    /**
     * Tries to obtain a mapped/public address for the specified port. If the STUN lib fails,
     * tries to retrieve localhost, if that fails too, returns null.
     *
     * @param intendedDestination the destination that we'd like to use this address with.
     * @param port the port whose mapping we are interested in.
     * @return a public address corresponding to the specified port or null if all attempts to
     * retrieve such an address have failed.
     * @throws IOException if an error occurs while the underlying resolver lib is using sockets.
     * @throws BindException if the port is already in use.
     */
    @Throws(IOException::class, BindException::class)
    fun getPublicAddressFor(intendedDestination: InetAddress?, port: Int): InetSocketAddress?

    /**
     * Returns the hardware address (i.e. MAC address) of the specified interface name.
     *
     * @param iface the `NetworkInterface`
     * @return array of bytes representing the layer 2 address
     */
    fun getHardwareAddress(iface: NetworkInterface?): ByteArray?

    /**
     * Creates a `DatagramSocket` and binds it to on the specified `localAddress`
     * and a port in the range specified by the `minPort` and `maxPort` parameters.
     * We first try to bind the newly created socket on the `preferredPort` port number and
     * then proceed incrementally upwards until we succeed or reach the bind retries limit. If we
     * reach the `maxPort` port number before the bind retries limit, we will then start
     * over again at `minPort` and keep going until we run out of retries.
     *
     * @param laddr the address that we'd like to bind the socket on.
     * @param preferredPort the port number that we should try to bind to first.
     * @param minPort the port number where we should first try to bind before moving to the next
     * one (i.e. `minPort + 1`)
     * @param maxPort the maximum port number where we should try binding before giving up and throwing an exception.
     * @return the newly created `DatagramSocket`.
     * @throws IllegalArgumentException if either `minPort` or `maxPort` is not a valid port number.
     * @throws IOException if an error occurs while the underlying resolver lib is using sockets.
     * @throws BindException if we couldn't find a free port between `minPort` and
     * `maxPort` before reaching the maximum allowed number of retries.
     */
    @Throws(IllegalArgumentException::class, IOException::class, BindException::class)
    fun createDatagramSocket(laddr: InetAddress?, preferredPort: Int, minPort: Int, maxPort: Int): DatagramSocket?

    /**
     * Adds new `NetworkConfigurationChangeListener` which will be informed for network configuration changes.
     *
     * @param listener the listener.
     */
    fun addNetworkConfigurationChangeListener(listener: NetworkConfigurationChangeListener?)

    /**
     * Remove `NetworkConfigurationChangeListener`.
     *
     * @param listener the listener.
     */
    fun removeNetworkConfigurationChangeListener(listener: NetworkConfigurationChangeListener?)

    /**
     * Creates and returns an ICE agent that a protocol could use for the negotiation of media
     * transport addresses. One ICE agent should only be used for a single session negotiation.
     *
     * @return the newly created ICE Agent.
     */
    fun createIceAgent(): Agent?

    /**
     * Tries to discover a TURN or a STUN server for the specified `domainName`. The
     * method would first try to discover a TURN server and then fall back to STUN only. In both
     * cases we would only care about a UDP transport.
     *
     * @param domainName the domain name that we are trying to discover a TURN server for.
     * @param userName the name of the user we'd like to use when connecting to a TURN server (we
     * won't be using credentials in case we only have a STUN server).
     * @param password the password that we'd like to try when connecting to a TURN server (we
     * won't be using credentials in case we only have a STUN server).
     * @return A [StunCandidateHarvester] corresponding to the TURN or STUN server we
     * discovered or `null` if there were no such records for the specified `domainName`
     */
    fun discoverStunServer(domainName: String?, userName: ByteArray?, password: ByteArray?): StunCandidateHarvester?

    /**
     * Creates an `IceMediaStream` and adds to it an RTP and and RTCP component, which
     * also implies running the currently installed harvesters so that they would.
     *
     * @param rtpPort the port that we should try to bind the RTP component on (the RTCP one
     * would automatically go to rtpPort + 1)
     * @param streamName the name of the stream to create
     * @param agent the `Agent` that should create the stream.
     * @return the newly created `IceMediaStream`.
     * @throws IllegalArgumentException if `rtpPort` is not a valid port number.
     * @throws IOException if an error occurs while the underlying resolver is using sockets.
     * @throws BindException if we couldn't find a free port between within the default number of retries.
     */
    @Throws(IllegalArgumentException::class, IOException::class, BindException::class)
    fun createIceStream(rtpPort: Int, streamName: String?, agent: Agent?): IceMediaStream?

    /**
     * Creates an `IceMediaStream` and adds to it one or two components, which also
     * implies running the currently installed harvesters.
     *
     * @param portBase the port that we should try to bind first component on (the second one
     * would automatically go to portBase + 1)
     * @param streamName the name of the stream to create
     * @param agent the `Agent` that should create the stream.
     * @return the newly created `IceMediaStream`.
     * @throws IllegalArgumentException if `portBase` is not a valid port number. If
     * `numComponents` is neither 1 nor 2.
     * @throws IOException if an error occurs while the underlying resolver is using sockets.
     * @throws BindException if we couldn't find a free port between within the default number of retries.
     */
    @Throws(IllegalArgumentException::class, IOException::class, BindException::class)
    fun createIceStream(numComponents: Int, portBase: Int, streamName: String?, agent: Agent?): IceMediaStream?

    companion object {
        /**
         * The default number of binds that a `NetworkAddressManagerService` implementation
         * should execute in case a port is already bound to (each retry would be on a different port).
         */
        const val BIND_RETRIES_DEFAULT_VALUE = 50

        /**
         * The name of the property containing number of binds that a
         * `NetworkAddressManagerService` implementation should execute in case a port is
         * already bound to (each retry would be on a different port).
         */
        const val BIND_RETRIES_PROPERTY_NAME = "netaddr.BIND_RETRIES"
    }
}