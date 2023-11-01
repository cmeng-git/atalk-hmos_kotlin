/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
  * the License for the
 * specific language governing permissions and limitations under the License.
 */
package net.java.sip.communicator.impl.netaddr

import net.java.sip.communicator.service.netaddr.NetworkAddressManagerService
import net.java.sip.communicator.service.netaddr.event.NetworkConfigurationChangeListener
import net.java.sip.communicator.util.NetworkUtils
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.configuration.ConfigurationService
import org.atalk.util.OSUtils
import org.ice4j.Transport
import org.ice4j.TransportAddress
import org.ice4j.ice.Agent
import org.ice4j.ice.IceMediaStream
import org.ice4j.ice.harvest.StunCandidateHarvester
import org.ice4j.ice.harvest.TurnCandidateHarvester
import org.ice4j.security.LongTermCredential
import org.minidns.record.SRV
import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.io.IOException
import java.net.BindException
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.UnknownHostException

/**
 * This implementation of the Network Address Manager allows you to intelligently retrieve the
 * address of your localhost according to the destinations that you will be trying to reach. It
 * also provides an interface to the ICE implementation in ice4j.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class NetworkAddressManagerServiceImpl : NetworkAddressManagerService {
    /**
     * The socket that we use for dummy connections during selection of a local address that has
     * to be used when communicating with a specific location.
     */
    private var localHostFinderSocket: DatagramSocket? = null

    /**
     * A thread which periodically scans network interfaces and reports changes in network configuration.
     */
    private var networkConfigurationWatcher: NetworkConfigurationWatcher? = null

    /**
     * Initializes this network address manager service implementation.
     */
    fun start() {
        localHostFinderSocket = initRandomPortSocket()
    }

    /**
     * Kills all threads/processes launched by this thread (if any) and prepares it for shutdown.
     * You may use this method as a reinitialization technique (you'll have to call start afterwards)
     */
    fun stop() {
        if (networkConfigurationWatcher != null) networkConfigurationWatcher!!.stop()
    }

    /**
     * Returns an InetAddress instance that represents the localhost, and that a socket can bind
     * upon or distribute to peers as a contact address.
     *
     * @param intendedDestination the destination that we'd like to use the localhost address with.
     * @return an InetAddress instance representing the local host, and that a socket can bind
     * upon or distribute to peers as a contact address.
     */
    @Synchronized
    override fun getLocalHost(intendedDestination: InetAddress?): InetAddress {
        var localHost: InetAddress?
        Timber.log(TimberLog.FINER, "Querying for a localhost address for intended destination '%s", intendedDestination)

        /*
         * use native code (JNI) to find source address for a specific destination address on Windows XP SP1 and over.
         *
         * For other systems, we used method based on DatagramSocket.connect which will returns us source address.
         * The reason why we cannot use it on Windows is because its socket implementation returns any address...
         */

//        String osVersion;
//        if (OSUtils.IS_WINDOWS && !(osVersion = System.getProperty("os.version")).startsWith("4") /* 95/98/Me/NT */
//                && !osVersion.startsWith("5.0")) /* 2000 */ {
//            byte[] src = Win32LocalhostRetriever.getSourceForDestination(intendedDestination.getAddress());
//            if (src == null) {
//                Timber.w("Failed to get localhost ");
//            }
//            else {
//                try {
//                    localHost = InetAddress.getByAddress(src);
//                } catch (UnknownHostException uhe) {
//                    Timber.w(uhe, "Failed to get localhost");
//                }
//            }
//        }
//        else if (OSUtils.IS_MAC) {
//            try {
//                localHost = BsdLocalhostRetriever.getLocalSocketAddress(
//                        new InetSocketAddress(intendedDestination, RANDOM_ADDR_DISC_PORT));
//            } catch (IOException e) {
//                Timber.w(e, "Failed to get localhost");
//            }
//        }
//        else {
        // no point in making sure that the localHostFinderSocket is initialized.
        // better let it through a NullPointerException.
        localHostFinderSocket!!.connect(intendedDestination, RANDOM_ADDR_DISC_PORT)
        localHost = localHostFinderSocket!!.localAddress
        localHostFinderSocket!!.disconnect()
        //        }

        // windows socket implementations return the any address so we need to find something else here ...
        // InetAddress.getLocalHost seems to work better on windows so let's hope it'll do the trick.
        if (localHost == null) {
            try {
                localHost = InetAddress.getLocalHost()
            } catch (e: UnknownHostException) {
                Timber.w(e, "Failed to get localhost")
            }
        }
        if (localHost != null && localHost.isAnyLocalAddress) {
            Timber.log(TimberLog.FINER, "Socket returned the ANY local address. Trying a workaround.")
            try {
                // all that's inside the if is an ugly IPv6 hack (good ol' IPv6 - always causing more problems than it solves.)
                if (intendedDestination is Inet6Address) {
                    // return the first globally route-able ipv6 address we find on the machine (and hope it's a good one)
                    var done = false
                    val ifaces = NetworkInterface.getNetworkInterfaces()
                    while (!done && ifaces.hasMoreElements()) {
                        val addresses = ifaces.nextElement().inetAddresses
                        while (addresses.hasMoreElements()) {
                            val address = addresses.nextElement()
                            if (address is Inet6Address && !address.isAnyLocalAddress()
                                    && !address.isLinkLocalAddress() && !address.isLoopbackAddress()
                                    && !address.isSiteLocalAddress()) {
                                localHost = address
                                done = true
                                break
                            }
                        }
                    }
                } else {
                    // Make sure we got an IPv4 address.
                    if (intendedDestination is Inet4Address) {
                        // return the first non-loopback interface we find.
                        var done = false
                        val ifaces = NetworkInterface.getNetworkInterfaces()
                        while (!done && ifaces.hasMoreElements()) {
                            val addresses = ifaces.nextElement().inetAddresses
                            while (addresses.hasMoreElements()) {
                                val address = addresses.nextElement()
                                if (address is Inet4Address && !address.isLoopbackAddress()) {
                                    localHost = address
                                    done = true
                                    break
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // sigh ... ok return 0.0.0.0
                Timber.w(e, "Failed to get localhost")
            }
        }
        Timber.log(TimberLog.FINER, "Returning the localhost address '%s'", localHost)
        return localHost!!
    }

    /**
     * Returns the hardware address (i.e. MAC address) of the specified interface name.
     *
     * @param iface the `NetworkInterface`
     * @return array of bytes representing the layer 2 address or null if interface does not exist
     */
    override fun getHardwareAddress(iface: NetworkInterface?): ByteArray? {
        var hwAddress: ByteArray?

        /* try reflection */
        try {
            val method = iface!!.javaClass.getMethod("getHardwareAddress")
            hwAddress = method.invoke(iface, *arrayOf()) as ByteArray
            return hwAddress
        } catch (e: Exception) {
            Timber.e("get Hardware Address failed: %s", e.message)
        }
        /*
         * maybe getHardwareAddress not available on this JVM try with our JNI
         */
        val ifName = if (OSUtils.IS_WINDOWS) {
            iface!!.displayName
        } else {
            iface!!.name
        }
        hwAddress = HardwareAddressRetriever.getHardwareAddress(ifName)
        return hwAddress
    }

    /**
     * Tries to obtain an for the specified port.
     *
     * @param intendedDestination the destination that we'd like to use this address with.
     * @param port the port whose mapping we are interested in.
     * @return a public address corresponding to the specified port or null if all attempts to
     * retrieve such an address have failed.
     * @throws IOException if an error occurs while creating the socket.
     * @throws BindException if the port is already in use.
     */
    @Throws(IOException::class, BindException::class)
    override fun getPublicAddressFor(intendedDestination: InetAddress?, port: Int): InetSocketAddress {
        // we'll try to bind so that we could notify the caller if the port has been taken already.
        val bindTestSocket = DatagramSocket(port)
        bindTestSocket.close()

        // if we're here then the port was free.
        return InetSocketAddress(getLocalHost(intendedDestination), port)
    }

    /**
     * This method gets called when a bound property is changed.
     *
     * @param evt A PropertyChangeEvent object describing the event source and the property that has changed.
     */
    fun propertyChange(evt: PropertyChangeEvent?) {
        // there's no point in implementing this method as we have no way of knowing whether the
        // current property change event is the only event we're going to get or whether another
        // one is going to follow..

        // in the case of a STUN_SERVER_ADDRESS property change for example there's no way of
        // knowing whether a STUN_SERVER_PORT property change will follow or not.

        // Reinitialization will therefore only happen if the reinitialize() method is called.
    }

    /**
     * Initializes and binds a socket that on a random port number. The method would try to bind
     * on a random port and retry 5 times until a free port is found.
     *
     * @return the socket that we have initialized on a random port number.
     */
    private fun initRandomPortSocket(): DatagramSocket? {
        var resultSocket: DatagramSocket? = null
        val bindRetriesStr = NetaddrActivator.getConfigurationService()!!.getString(NetworkAddressManagerService.BIND_RETRIES_PROPERTY_NAME)
        var bindRetries = 5
        if (bindRetriesStr != null) {
            try {
                bindRetries = bindRetriesStr.toInt()
            } catch (ex: NumberFormatException) {
                Timber.e(ex, "%s does not appear to be an integer. Defaulting port bind retries to %s",
                        bindRetriesStr, bindRetries)
            }
        }
        var currentlyTriedPort = NetworkUtils.randomPortNumber

        // we'll first try to bind to a random port. if this fails we'll try again
        // (bindRetries times in all) until we find a free local port.
        for (i in 0 until bindRetries) {
            try {
                resultSocket = DatagramSocket(currentlyTriedPort)
                // we succeeded - break so that we don't try to bind again
                break
            } catch (exc: SocketException) {
                if (!exc.message!!.contains("Address already in use")) {
                    Timber.e(exc, "An exception occurred while trying to create a local host discovery socket.")
                    return null
                }
                // port seems to be taken. try another one.
                Timber.d("Port %d seems to be in use.", currentlyTriedPort)
                currentlyTriedPort = NetworkUtils.randomPortNumber
                Timber.d("Retrying bind on port %s", currentlyTriedPort)
            }
        }
        return resultSocket
    }

    /**
     * Creates a `DatagramSocket` and binds it to the specified `localAddress` and a
     * port in the range specified by the `minPort` and `maxPort` parameters. We
     * first try to bind the newly created socket on the `preferredPort` port number
     * (unless it is outside the `[minPort, maxPort]` range in which case we first try the
     * `minPort`) and then proceed incrementally upwards until we succeed or reach the bind
     * retries limit. If we reach the `maxPort` port number before the bind retries limit,
     * we will then start over again at `minPort` and keep going until we run out of retries.
     *
     * @param laddr the address that we'd like to bind the socket on.
     * @param preferredPort the port number that we should try to bind to first.
     * @param minPort the port number where we should first try to bind before moving to the next one
     * (i.e. `minPort + 1`)
     * @param maxPort the maximum port number where we should try binding before giving up and throwing an exception.
     * @return the newly created `DatagramSocket`.
     * @throws IllegalArgumentException if either `minPort` or `maxPort` is not a valid
     * port number or if `minPort > maxPort`.
     * @throws IOException if an error occurs while the underlying resolver lib is using sockets.
     * @throws BindException if we couldn't find a free port between `minPort` and `maxPort` before
     * reaching the maximum allowed number of retries.
     */
    @Throws(IllegalArgumentException::class, IOException::class, BindException::class)
    override fun createDatagramSocket(laddr: InetAddress?, preferredPort: Int, minPort: Int, maxPort: Int): DatagramSocket {
        // make sure port numbers are valid
        require(!(!NetworkUtils.isValidPortNumber(minPort) || !NetworkUtils.isValidPortNumber(maxPort))) {
            ("minPort (" + minPort + ") and maxPort (" + maxPort
                    + ")  should be integers between 1024 and 65535.")
        }

        // make sure minPort comes before maxPort.
        require(minPort <= maxPort) {
            ("minPort (" + minPort
                    + ") should be less than or equal to maxPort (" + maxPort + ")")
        }

        // if preferredPort is not in the allowed range, place it at min.
        require(!(minPort > preferredPort || preferredPort > maxPort)) {
            ("preferred Port (" + preferredPort
                    + ") must be between minPort (" + minPort + ") and maxPort (" + maxPort + ")")
        }
        val config = NetaddrActivator.getConfigurationService()
        val bindRetries = config!!.getInt(NetworkAddressManagerService.BIND_RETRIES_PROPERTY_NAME, NetworkAddressManagerService.BIND_RETRIES_DEFAULT_VALUE)
        var port = preferredPort
        for (i in 0 until bindRetries) {
            try {
                return DatagramSocket(port, laddr)
            } catch (se: SocketException) {
                Timber.i("Retrying a bind because of a failure to bind to address: %s and port: %d", laddr, port)
                Timber.log(TimberLog.FINER, se, "Since you seem, here's a stack")
            }
            port++
            if (port > maxPort) port = minPort
        }
        throw BindException("Could not bind to any port between " + minPort + " and " + (port - 1))
    }

    /**
     * Adds new `NetworkConfigurationChangeListener` which will be informed for network configuration changes.
     *
     * @param listener the listener.
     */
    @Synchronized
    override fun addNetworkConfigurationChangeListener(listener: NetworkConfigurationChangeListener?) {
        if (networkConfigurationWatcher == null) networkConfigurationWatcher = NetworkConfigurationWatcher()
        networkConfigurationWatcher!!.addNetworkConfigurationChangeListener(listener!!)
    }

    /**
     * Remove `NetworkConfigurationChangeListener`.
     *
     * @param listener the listener.
     */
    @Synchronized
    override fun removeNetworkConfigurationChangeListener(listener: NetworkConfigurationChangeListener?) {
        if (networkConfigurationWatcher != null) networkConfigurationWatcher!!.removeNetworkConfigurationChangeListener(listener)
    }

    /**
     * Creates and returns an ICE agent that a protocol could use for the negotiation of media
     * transport addresses. One ICE agent should only be used for a single session negotiation.
     *
     * @return the newly created ICE Agent.
     */
    override fun createIceAgent(): Agent {
        return Agent()
    }

    /**
     * Tries to discover a TURN or a STUN server for the specified `domainName`. The method
     * would first try to discover a TURN server and then fall back to STUN only. In both cases
     * we would only care about a UDP transport.
     *
     * @param domainName the domain name that we are trying to discover a TURN server for.
     * @param userName the name of the user we'd like to use when connecting to a TURN server (we won't be
     * using credentials in case we only have a STUN server).
     * @param password the password that we'd like to try when connecting to a TURN server (we won't be using
     * credentials in case we only have a STUN server).
     * @return A [StunCandidateHarvester] corresponding to the TURN or STUN server we
     * discovered or `null` if there were no such records for the specified `domainName`
     */
    override fun discoverStunServer(domainName: String?, userName: ByteArray?, password: ByteArray?): StunCandidateHarvester? {
        // cmeng - Do not proceed to check further if the domainName is not reachable, just return null
        try {
            val inetAddress = InetAddress.getByName(domainName)
        } catch (e: UnknownHostException) {
            Timber.w("Unreachable host for TURN/STUN discovery: %s", domainName)
            return null
        }
        var srvrAddress: String? = null
        var port = 0
        try {
            var srvRecords = NetworkUtils.getSRVRecords(TURN_SRV_NAME, Transport.UDP.toString(), domainName!!)
            if (srvRecords != null) {
                srvrAddress = srvRecords[0].target.toString()
            }

            // Seem to have a TURN server, so we'll be using it for both TURN and STUN harvesting.
            if (srvrAddress != null) {
                return TurnCandidateHarvester(TransportAddress(srvrAddress, srvRecords!![0].port, Transport.UDP),
                        LongTermCredential(userName, password))
            }

            // srvrAddress was null. try for a STUN only server.
            srvRecords = NetworkUtils.getSRVRecords(STUN_SRV_NAME, Transport.UDP.toString(), domainName)
            if (srvRecords != null) {
                srvrAddress = srvRecords[0].target.toString()
                port = srvRecords[0].port
            }
        } catch (e: IOException) {
            Timber.w("Failed to fetch STUN/TURN SRV RR for %s: %s", domainName, e.message)
        }
        return if (srvrAddress != null) {
            StunCandidateHarvester(TransportAddress(srvrAddress, port, Transport.UDP))
        } else null
        // srvrAddress was still null. sigh ...
    }

    /**
     * Creates an `IceMediaStream` and adds to it an RTP and and RTCP component,
     * which also implies running the currently installed harvesters so that they would.
     *
     * @param rtpPort the port that we should try to bind the RTP component on
     * (the RTCP one would automatically go to rtpPort + 1)
     * @param streamName the name of the stream to create
     * @param agent the `Agent` that should create the stream.
     * @return the newly created `IceMediaStream`.
     * @throws IllegalArgumentException if `rtpPort` is not a valid port number.
     * @throws IOException if an error occurs while the underlying resolver is using sockets.
     * @throws BindException if we couldn't find a free port between within the default number of retries.
     */
    @Throws(IllegalArgumentException::class, IOException::class, BindException::class)
    override fun createIceStream(rtpPort: Int, streamName: String?, agent: Agent?): IceMediaStream {
        return createIceStream(2, rtpPort, streamName, agent)
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IllegalArgumentException::class, IOException::class, BindException::class)
    override fun createIceStream(numComponents: Int, portBase: Int, streamName: String?, agent: Agent?): IceMediaStream {
        require(!(numComponents < 1 || numComponents > 2)) { "Invalid numComponents value: $numComponents" }
        val stream = agent!!.createMediaStream(streamName)
        agent.createComponent(stream, portBase, portBase, portBase + 100)
        if (numComponents > 1) {
            agent.createComponent(stream, portBase + 1, portBase + 1, portBase + 101)
        }
        return stream
    }

    companion object {
        /**
         * A random (unused)local port to use when trying to select a local host address to use when
         * sending messages to a specific destination.
         */
        private const val RANDOM_ADDR_DISC_PORT = 55721

        /**
         * Default STUN server port.
         */
        const val DEFAULT_STUN_SERVER_PORT = 3478

        /**
         * The service name to use when discovering TURN servers through DNS using SRV requests as per RFC 5766.
         */
        const val TURN_SRV_NAME = "turn"

        /**
         * The service name to use when discovering STUN servers through DNS using SRV requests as per RFC 5389.
         */
        const val STUN_SRV_NAME = "stun"
    }
}