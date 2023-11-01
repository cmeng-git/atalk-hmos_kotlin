/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.util

import android.text.TextUtils
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.minidns.hla.ResolverApi
import org.minidns.hla.ResolverResult
import org.minidns.record.*
import org.minidns.util.InetAddressUtil
import timber.log.Timber
import java.io.IOException
import java.net.*
import java.util.*

/**
 * Utility methods and fields to use when working with network addresses.
 *
 * @author Eng Chong Meng
 */
object NetworkUtils {
    /**
     * A string containing the "any" local address for IPv6.
     */
    private const val IN6_ADDR_ANY = "::0"

    /**
     * A string containing the "any" local address for IPv4.
     */
    private const val IN4_ADDR_ANY = "0.0.0.0"

    /**
     * A string containing the "any" local address.
     */
    val IN_ADDR_ANY = determineAnyAddress()

    /**
     * The length of IPv6 addresses.
     */
    private const val IN6_ADDR_SIZE = 16

    /**
     * The size of the tokens in a `String` representation of IPv6 addresses.
     */
    private const val IN6_ADDR_TOKEN_SIZE = 2

    /**
     * The length of IPv4 addresses.
     */
    private const val IN4_ADDR_SIZE = 4

    /**
     * The maximum int value that could correspond to a port number.
     */
    const val MAX_PORT_NUMBER = 65535

    /**
     * The minimum int value that could correspond to a port number bindable by the SIP Communicator.
     */
    const val MIN_PORT_NUMBER = 1024

    /**
     * The random port number generator that we use in getRandomPortNumber()
     */
    private val portNumberGenerator = Random()

    /**
     * The name of the boolean property that defines whether all domain names
     * looked up from Jitsi should be treated as absolute.
     */
    const val PNAME_DNS_ALWAYS_ABSOLUTE = "dns.DNSSEC_ALWAYS_ABSOLUTE"

    /**
     * Default value of [.PNAME_DNS_ALWAYS_ABSOLUTE].
     */
    const val PDEFAULT_DNS_ALWAYS_ABSOLUTE = false

    /**
     * A random number generator.
     */
    private val random = Random()

    init {
        val prefer6 = System.getProperty("java.net.preferIPv6Addresses")
        val prefer4 = System.getProperty("java.net.preferIPv4Stack")
        Timber.i("java.net.preferIPv6Addresses=%s; java.net.preferIPv4Stack=%s", prefer6, prefer4)
    }

    /**
     * Determines whether the address is the result of windows auto configuration.
     * (i.e. One that is in the 169.254.0.0 network)
     *
     * @param add the address to inspect
     * @return true if the address is auto-configured by windows, false otherwise.
     */
    fun isWindowsAutoConfiguredIPv4Address(add: InetAddress): Boolean {
        return add.address[0].toInt() and 0xFF == 169 && add.address[1].toInt() and 0xFF == 254
    }

    /**
     * Returns a random local port number that user applications could bind to. (i.e. above 1024).
     *
     * @return a random int located between 1024 and 65 535.
     */
    val randomPortNumber: Int
        get() = getRandomPortNumber(MIN_PORT_NUMBER, MAX_PORT_NUMBER)

    /**
     * Returns a random local port number in the interval [min, max].
     *
     * @param min the minimum allowed value for the returned port number.
     * @param max the maximum allowed value for the returned port number.
     * @return a random int in the interval [min, max].
     */
    private fun getRandomPortNumber(min: Int, max: Int): Int {
        return portNumberGenerator.nextInt(max - min + 1) + min
    }

    /**
     * Returns array of hosts from the SRV record of the specified domain.
     * The records are ordered against the SRV record priority
     *
     * @param domain the name of the domain we'd like to resolve (_proto._tcp included).
     * @return an array of SRV containing records returned by the DNS server - address and port .
     * @throws IOException if an IO error occurs.
     */
    @Throws(IOException::class)
    fun getSRVRecords(domain: String?): Array<SRV>? {
        return try {
            val result = ResolverApi.INSTANCE.resolve(domain, SRV::class.java)
            val records = result.answersOrEmptySet
            if (records.isNotEmpty()) {
                val srvRecords = records.toTypedArray()
                // Sort the SRV RRs by priority (lower is preferred) and weight.
                sortSrvRecord(srvRecords)
                srvRecords
            } else {
                null
            }
        } catch (e: IOException) {
            Timber.e("No SRV record found for %s: %s", domain, e.message)
            throw IOException(e)
        }
    }

    /**
     * Returns array of SRV Record for the specified (service, proto and domain).
     * or `null` if the specified domain is of unknown host or there are no SRV records for `domain`.
     *
     * @param service the service that we are trying to get a record for e.g. xmpp.
     * @param proto the protocol that we'd like `service` on i.e. tcp or udp.
     * @param domain the name of the domain we'd like to resolve i.e. example.org.
     * @return an array of SRV containing records returned by the DNS server - address and port .
     * @throws IOException if an IO error occurs.
     */
    @Throws(IOException::class)
    fun getSRVRecords(service: String, proto: String, domain: String): Array<SRV>? {
        // verify the domain is knownHost and reachable before proceed
        try {
            val inetAddress = InetAddress.getByName(domain)
        } catch (e: UnknownHostException) {
            Exception("_$service._$proto.$domain").printStackTrace()
            return null
        }
        return getSRVRecords("_$service._$proto.$domain")
    }

    /**
     * Not use : not implemented by miniDNS
     * Makes a NAPTR query and returns the result. The returned records are an array of [Order, Service(Transport)
     * and Replacement (the srv to query for servers and ports)] this all for supplied `domain`.
     *
     * @param domain the name of the domain we'd like to resolve.
     * @return an array with the values or null if no records found.
     */
    fun getNAPTRRecords(domain: String): Array<Array<String?>>? {
        val records = try {
            val dnsQueryResult = ResolverApi.INSTANCE.client.query(domain, Record.TYPE.NAPTR)
            dnsQueryResult.query.answerSection
        } catch (tpe: IOException) {
            Timber.log(TimberLog.FINER, "No A record found for $domain")
            // throw new ParseException(tpe.getMessage(), 0);
            return null
        }
        if (records != null) {
            val recVals = ArrayList<Array<String>?>(records.size)
            for (i in records.indices) {
                val recVal = arrayOfNulls<String>(4)
                //                NAPTR r = (NAPTR) records.get(i).getPayload();
//
//                // todo - check here for broken records as missing transport
//                recVal[0] = "" + r.getOrder();
//                recVal[1] = getProtocolFromNAPTRRecords(r.getService());
//                // we don't understand this NAPTR, maybe it's not for SIP?
//                if (recVal[1] == null) {
//                    continue;
//                }
//
//                String replacement = r.getReplacement().toString();
//                if (replacement.endsWith(".")) {
//                    recVal[2] = replacement.substring(0, replacement.length() - 1);
//                }
//                else {
//                    recVal[2] = replacement;
//                }
//                recVal[3] = "" + r.getPreference();
//                recVals.add(recVal);
            }

            // sort the SRV RRs by RR value (lower is preferred)
            Collections.sort(recVals, Comparator { array1, array2 ->

                // Sorts NAPTR records by ORDER (low number first), PREFERENCE (low number first) and
                // PROTOCOL (0-TLS, 1-TCP, 2-UDP).
                // First tries to define the priority with the NAPTR order.
                val order = array1!![0].toInt() - array2!![0].toInt()
                if (order != 0) {
                    return@Comparator order
                }
                // Second tries to define the priority with the NAPTR preference.
                val preference = array1[3].toInt() - array2[3].toInt()
                if (preference != 0) {
                    preference
                } else getProtocolPriority(array1[1]) - getProtocolPriority(array2[1])
                // Finally defines the priority with the NAPTR protocol.
            })
            var arrayResult = Array<Array<String?>>(recVals.size) { arrayOfNulls(4) }
            arrayResult = recVals.toArray(arrayResult)
            Timber.log(TimberLog.FINER, "NAPTRs for " + domain + " = " + Arrays.toString(arrayResult))
            return arrayResult
        }
        return null
    }

    /**
     * Returns the mapping from rfc3263 between service and the protocols.
     *
     * @param service the service from NAPTR record.
     * @return the protocol TCP, UDP or TLS.
     */
    private fun getProtocolFromNAPTRRecords(service: String): String? {
        return if (service.equals("SIP+D2U", ignoreCase = true)) "UDP" else if (service.equals("SIP+D2T", ignoreCase = true)) "TCP" else if (service.equals("SIPS+D2T", ignoreCase = true)) "TLS" else null
    }

    /**
     * Returns the priority of a protocol. The lowest priority is the highest: 0-TLS, 1-TCP, 2-UDP.
     *
     * @param protocol The protocol name: "TLS", "TCP" or "UDP".
     * @return The priority of a protocol. The lowest priority is the highest: 0-TLS, 1-TCP, 2-UDP.
     */
    private fun getProtocolPriority(protocol: String): Int {
        if (protocol == "TLS") return 0 else if (protocol == "TCP") return 1
        return 2 // "UDP".
    }

    /**
     * Creates an InetAddress from the specified `hostAddress`. The point of using the method rather than
     * creating the address by yourself is that it would first check whether the specified `hostAddress`
     * is indeed a valid ip address. It this is the case, the method would create the `InetAddress` using
     * the `InetAddress.getByAddress()` method so that no DNS resolution is attempted by the JRE. Otherwise
     * it would simply use `InetAddress.getByName()` so that we would an `InetAddress` instance
     * even at the cost of a potential DNS resolution.
     *
     * @param hostAddress_ the `String` representation of the address
     * that we would like to create an `InetAddress` instance for.
     * @return an `InetAddress` instance corresponding to the specified `hostAddress`.
     * @throws UnknownHostException if any of the `InetAddress` methods we are using throw an exception.
     * @throws IllegalArgumentException if the given hostAddress is not an ip4 or ip6 address.
     */
    @JvmStatic
    @Throws(UnknownHostException::class, IllegalArgumentException::class)
    fun getInetAddress(hostAddress_: String): InetAddress {
        var hostAddress = hostAddress_
        if (TextUtils.isEmpty(hostAddress)) {
            throw UnknownHostException("$hostAddress is not a valid host address")
        }

        // transform IPv6 literals into normal addresses
        if (hostAddress[0] == '[') {
            // This is supposed to be an IPv6 literal
            hostAddress = if (hostAddress.length > 2 && hostAddress[hostAddress.length - 1] == ']') {
                hostAddress.substring(1, hostAddress.length - 1)
            } else {
                // This was supposed to be a IPv6 address, but it's not!
                throw UnknownHostException(hostAddress)
            }
        }

        // if not IPv6, then parse as IPv4 address else throws
        val inetAddress = try {
            InetAddressUtil.ipv6From(hostAddress)
        } catch (e: IllegalArgumentException) {
            InetAddressUtil.ipv4From(hostAddress)
        }
        return InetAddress.getByAddress(hostAddress, inetAddress.address)
    }

    /**
     * Returns array of hosts from the A and AAAA records of the specified domain. The records are
     * ordered against the IPv4/IPv6 protocol priority
     *
     * @param domain the name of the domain we'd like to resolve.
     * @param port the port number of the returned `InetSocketAddress`
     * @return an array of InetSocketAddress containing records returned by the DNS server - address and port .
     * @throws UnknownHostException if IP address is of illegal length
     * @throws IOException if an IO error occurs.
     */
    @Throws(IOException::class)
    fun getAandAAAARecords(domain: String?, port: Int): List<InetSocketAddress> {
        val inetSocketAddresses = ArrayList<InetSocketAddress>()
        val address = try {
            InetAddressUtil.ipv4From(domain).address
        } catch (e: IllegalArgumentException) {
            InetAddressUtil.ipv6From(domain).address
        }

        if (address != null) {
            inetSocketAddresses.add(InetSocketAddress(InetAddress.getByAddress(domain, address), port))
            return inetSocketAddresses
        } else Timber.i("Unable to create InetAddress for <%s>; Try using A/AAAA RR.", domain)

        var resultA: ResolverResult<A>
        var resultAAAA: ResolverResult<AAAA>
        var v6lookup = java.lang.Boolean.getBoolean("java.net.preferIPv6Addresses")
        for (i in 0..1) {
            if (v6lookup) {
                resultAAAA = ResolverApi.INSTANCE.resolve(domain, AAAA::class.java)
                if (!resultAAAA.wasSuccessful()) {
                    continue
                }
                val answers = resultAAAA.answers
                for (aaaa in answers) {
                    val inetAddress = aaaa.inetAddress
                    inetSocketAddresses.add(InetSocketAddress(inetAddress, port))
                }
            } else {
                resultA = ResolverApi.INSTANCE.resolve(domain, A::class.java)
                if (!resultA.wasSuccessful()) {
                    continue
                }
                val answers = resultA.answers
                for (a in answers) {
                    val inetAddress = a.inetAddress
                    inetSocketAddresses.add(InetSocketAddress(inetAddress, port))
                }
            }
            v6lookup = !v6lookup
        }
        return inetSocketAddresses
    }

    /**
     * Returns array of hosts from the A record of the specified domain.
     * The records are ordered against the A record priority
     *
     * @param domain the name of the domain we'd like to resolve.
     * @param port the port number of the returned `InetSocketAddress`
     * @return an array of InetSocketAddress containing records returned by the DNS server - address and port .
     */
    @Throws(IOException::class)
    fun getARecords(domain: String?, port: Int): List<InetSocketAddress>? {
        val inetSocketAddresses = ArrayList<InetSocketAddress>()
        try {
            val address = InetAddressUtil.ipv4From(domain).address
            inetSocketAddresses.add(InetSocketAddress(InetAddress.getByAddress(domain, address), port))
            return inetSocketAddresses
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Unable to create InetAddress for <%s>", domain)
        }
        val result = ResolverApi.INSTANCE.resolve(domain, A::class.java)
        if (!result.wasSuccessful()) {
            return null
        }
        val answers = result.answers
        for (a in answers) {
            val inetAddress = a.inetAddress
            inetSocketAddresses.add(InetSocketAddress(inetAddress, port))
        }
        return inetSocketAddresses
    }

    /**
     * Returns array of hosts from the AAAA record of the specified domain.
     * The records are ordered against the AAAA record priority
     *
     * @param domain the name of the domain we'd like to resolve.
     * @param port the port number of the returned `InetSocketAddress`
     * @return an array of InetSocketAddress containing records returned by the DNS server - address and port .
     * @throws IOException if an IO error occurs.
     */
    @Throws(IOException::class)
    fun getAAAARecords(domain: String?, port: Int): List<InetSocketAddress>? {
        val inetSocketAddresses = ArrayList<InetSocketAddress>()
        try {
            val address = InetAddressUtil.ipv6From(domain).address
            inetSocketAddresses.add(InetSocketAddress(InetAddress.getByAddress(domain, address), port))
            return inetSocketAddresses
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Unable to create InetAddress for <%s>", domain)
        }
        val result = ResolverApi.INSTANCE.resolve(domain, AAAA::class.java)
        if (!result.wasSuccessful()) {
            return null
        }
        val answers = result.answers
        for (aaaa in answers) {
            val inetAddress = aaaa.inetAddress
            inetSocketAddresses.add(InetSocketAddress(inetAddress, port))
        }
        return inetSocketAddresses
    }

    /**
     * Tries to determine if this host supports IPv6 addresses (i.e. has at
     * least one IPv6 address) and returns IN6_ADDR_ANY or IN4_ADDR_ANY
     * accordingly. This method is only used to initialize IN_ADDR_ANY so that
     * it could be used when binding sockets. The reason we need it is because
     * on mac (contrary to lin or win) binding a socket on 0.0.0.0 would make
     * it deaf to IPv6 traffic. Binding on ::0 does the trick but that would
     * fail on hosts that have no IPv6 support. Using the result of this method
     * provides an easy way to bind sockets in cases where we simply want any
     * IP packets coming on the port we are listening on (regardless of IP version).
     *
     * @return IN6_ADDR_ANY or IN4_ADDR_ANY if this host supports or not IPv6.
     */
    private fun determineAnyAddress(): String {
        val ifaces = try {
            NetworkInterface.getNetworkInterfaces()
        } catch (e: SocketException) {
            Timber.d(e, "Couldn't retrieve local interfaces.")
            return IN4_ADDR_ANY
        }

        while (ifaces.hasMoreElements()) {
            val addrs = ifaces.nextElement().inetAddresses
            while (addrs.hasMoreElements()) {
                if (addrs.nextElement() is Inet6Address) return IN6_ADDR_ANY
            }
        }
        return IN4_ADDR_ANY
    }

    /**
     * Checks whether `address` is a valid IP address string.
     *
     * @param address_ the address that we'd like to check
     * @return true if address is an IPv4 or IPv6 address and false otherwise.
     */
    fun isValidIPAddress(address_: String): Boolean {
        var address = address_
        if (TextUtils.isEmpty(address)) {
            return false
        }
        // look for IPv6 brackets and remove brackets for parsing
        if (address[0] == '[') {
            // This is supposed to be an IPv6 literal
            address = if (address.length > 2 && address[address.length - 1] == ']') {
                // remove brackets from IPv6
                address.substring(1, address.length - 1)
            } else {
                return false
            }
        }

        // look for IP addresses valid pattern i.e. start with digit or ":"
        if (((address[0].digitToIntOrNull(16) ?: -1) != -1) || (address[0] == ':')) {
            // see if it is IPv4 address; if not, see if it is IPv6 address
            var inetAddress: InetAddress?
            return try {
                // if IPv6is found as expected
                inetAddress = InetAddressUtil.ipv6From(address)
                inetAddress != null
            } catch (e6: IllegalArgumentException) {
                try {
                    inetAddress = InetAddressUtil.ipv4From(address)
                    inetAddress != null
                } catch (e4: IllegalArgumentException) {
                    Timber.w("The given IP address is an unknownHost: %s", address)
                    false
                }
            }
        }
        return false
    }

    /**
     * Determines whether `port` is a valid port number bindable by an
     * application (i.e. an integer between 1024 and 65535).
     *
     * @param port the port number that we'd like verified.
     * @return `true` if port is a valid and bindable port number and `alse` otherwise.
     */
    fun isValidPortNumber(port: Int): Boolean {
        return port in MIN_PORT_NUMBER..MAX_PORT_NUMBER
    }

    /**
     * Returns an IPv4 address matching the one mapped in the IPv6
     * `addr`. Both input and returned value are in network order.
     *
     * @param addr a String representing an IPv4-Mapped address in textual format
     * @return a byte array numerically representing the IPv4 address
     */
    fun mappedIPv4ToRealIPv4(addr: ByteArray): ByteArray? {
        if (isMappedIPv4Addr(addr)) {
            val newAddr = ByteArray(IN4_ADDR_SIZE)
            System.arraycopy(addr, 12, newAddr, 0, IN6_ADDR_SIZE)
            return newAddr
        }
        return null
    }

    /**
     * Utility method to check if the specified `address` is an IPv4 mapped IPv6 address.
     *
     * @param address the address that we'd like to determine as an IPv4 mapped one or not.
     * @return `true` if address is an IPv4 mapped IPv6 address and `false` otherwise.
     */
    private fun isMappedIPv4Addr(address: ByteArray): Boolean {
        if (address.size < IN6_ADDR_SIZE) {
            return false
        }
        return address[0].toInt() == 0x00 && address[1].toInt() == 0x00 && address[2].toInt() == 0x00 && address[3].toInt() == 0x00 && address[4].toInt() == 0x00 && address[5].toInt() == 0x00 && address[6].toInt() == 0x00 && address[7].toInt() == 0x00 && address[8].toInt() == 0x00 && address[9].toInt() == 0x00 && address[10] == 0xff.toByte() && address[11] == 0xff.toByte()
    }

    /**
     * Sorts the SRV record list by priority and weight.
     *
     * @param srvRecords The list of SRV records.
     */
    private fun sortSrvRecord(srvRecords: Array<SRV>) {
        // Sort the SRV RRs by priority (lower is preferred).
        Arrays.sort(srvRecords) { obj1: SRV, obj2: SRV -> obj1.priority - obj2.priority }

        // Sort the SRV RRs by weight (larger weight has a proportionately higher probability of being selected).
        sortSrvRecordByWeight(srvRecords)
    }

    /**
     * Sorts each priority of the SRV record list. Each priority is sorted with
     * the probability given by the weight attribute.
     *
     * @param srvRecords The list of SRV records already sorted by priority.
     */
    private fun sortSrvRecordByWeight(srvRecords: Array<SRV>) {
        var currentPriority = srvRecords[0].priority
        var startIndex = 0
        for (i in srvRecords.indices) {
            if (currentPriority != srvRecords[i].priority) {
                // Sort the current priority.
                sortSrvRecordPriorityByWeight(srvRecords, startIndex, i)
                // Reinit variables for the next priority.
                startIndex = i
                currentPriority = srvRecords[i].priority
            }
        }
    }

    /**
     * Sorts SRV record list for a given priority: this priority is sorted with
     * the probability given by the weight attribute.
     *
     * @param srvRecords The list of SRV records already sorted by priority.
     * @param startIndex_ The first index (included) for the current priority.
     * @param endIndex The last index (excluded) for the current priority.
     */
    private fun sortSrvRecordPriorityByWeight(srvRecords: Array<SRV>, startIndex_: Int, endIndex: Int) {
        var startIndex = startIndex_
        var randomWeight: Int

        // Loops over the items of the current priority.
        while (startIndex < endIndex) {
            // Compute a random number in [0...totalPriorityWeight].
            randomWeight = getRandomWeight(srvRecords, startIndex, endIndex)

            // Move the selected item on top of the unsorted items for this priority.
            moveSelectedSRVRecord(srvRecords, startIndex, endIndex, randomWeight)

            // Move to next index.
            ++startIndex
        }
    }

    /**
     * Compute a random number in [0...totalPriorityWeight] with
     * totalPriorityWeight the sum of all weight for the current priority.
     *
     * @param srvRecords The list of SRV records already sorted by priority.
     * @param startIndex The first index (included) for the current priority.
     * @param endIndex The last index (excluded) for the current priority.
     * @return A random number in [0...totalPriorityWeight] with
     * totalPriorityWeight the sum of all weight for the current priority.
     */
    private fun getRandomWeight(srvRecords: Array<SRV>, startIndex: Int, endIndex: Int): Int {
        var totalPriorityWeight = 0

        // Compute the max born.
        for (i in startIndex until endIndex) {
            totalPriorityWeight += srvRecords[i].weight
        }
        // Compute a random number in [0...totalPriorityWeight].
        return random.nextInt(totalPriorityWeight + 1)
    }

    /**
     * Moves the selected SRV record in top of the unsorted items for this priority.
     *
     * @param srvRecords The list of SRV records already sorted by priority.
     * @param startIndex The first unsorted index (included) for the current priority.
     * @param endIndex The last unsorted index (excluded) for the current priority.
     * @param selectedWeight The selected weight used to design the selected item to move.
     */
    private fun moveSelectedSRVRecord(srvRecords: Array<SRV>, startIndex: Int, endIndex: Int, selectedWeight: Int) {
        val tmpSrvRecord: SRV
        var totalPriorityWeight = 0
        for (i in startIndex until endIndex) {
            totalPriorityWeight += srvRecords[i].weight

            // If we found the selecting record.
            if (totalPriorityWeight >= selectedWeight) {
                // Switch between startIndex and j.
                tmpSrvRecord = srvRecords[startIndex]
                srvRecords[startIndex] = srvRecords[i]
                srvRecords[i] = tmpSrvRecord
                // Break the loop;
                return
            }
        }
    }
}