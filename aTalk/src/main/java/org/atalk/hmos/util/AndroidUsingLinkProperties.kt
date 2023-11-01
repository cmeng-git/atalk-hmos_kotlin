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
package org.atalk.hmos.util

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import org.minidns.DnsClient
import org.minidns.dnsserverlookup.AbstractDnsServerLookupMechanism
import org.minidns.dnsserverlookup.AndroidUsingExec
import java.net.Inet4Address
import java.net.InetAddress

/**
 * A DNS server lookup mechanism using Android's Link Properties method available on Android API 21 or higher.
 * Use [.setup] to setup this mechanism.
 *
 * Requires the ACCESS_NETWORK_STATE permission.
 *
 */
class AndroidUsingLinkProperties(context: Context) : AbstractDnsServerLookupMechanism(AndroidUsingLinkProperties::class.java.simpleName, AndroidUsingExec.PRIORITY - 1) {
    private val connectivityManager: ConnectivityManager

    init {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @SuppressLint("ObsoleteSdkInt")
    override fun isAvailable(): Boolean {
        return true
    }

    // ConnectivityManager.getActiveNetwork() is API 23; null if otherwise
    private val activeNetwork: Network?
        get() {

            // ConnectivityManager.getActiveNetwork() is API 23; null if otherwise
            return connectivityManager.activeNetwork
        }

    /**
     * Get DnsServerAddresses; null if unavailable so DnsClient#findDNS() will proceed with next available mechanism .
     *
     * @return servers list or null
     */
    override fun getDnsServerAddresses(): List<String>? {
        val servers = ArrayList<String>()
        val network = activeNetwork ?: return null
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return null
        val vpnOffset = 0
        val networkInfo = connectivityManager.getNetworkInfo(network)
        val isVpn = networkInfo != null && networkInfo.type == ConnectivityManager.TYPE_VPN
        val v4v6Servers = getIPv4First(linkProperties.dnsServers)
        // Timber.d("hasDefaultRoute: %s activeNetwork: %s || isVpn: %s || IP: %s",
        //        hasDefaultRoute(linkProperties), network, isVpn, toListOfStrings(linkProperties.getDnsServers()));
        if (isVpn) {
            servers.addAll(0, v4v6Servers)
            // vpnOffset += v4v6Servers.size();
        } else if (hasDefaultRoute(linkProperties)) {
            servers.addAll(vpnOffset, v4v6Servers)
        } else {
            servers.addAll(v4v6Servers)
        }

        // Timber.d("dns Server Addresses (linkProperty): %s", servers);
        return servers
    }

    companion object {
        /**
         * Setup this DNS server lookup mechanism. You need to invoke this method only once,
         * ideally before you do your first DNS lookup.
         *
         * @param context a Context instance.
         * @return the instance of the newly setup mechanism
         */
        fun setup(context: Context): AndroidUsingLinkProperties {
            val androidUsingLinkProperties = AndroidUsingLinkProperties(context)
            DnsClient.addDnsServerLookupMechanism(androidUsingLinkProperties)
            return androidUsingLinkProperties
        }

        /**
         * Sort and return the list of given InetAddress in IPv4-IPv6 order, and keeping original in order
         *
         * @param in list of unsorted InetAddress
         * @return sorted vp4 vp6 IP addresses
         */
        private fun getIPv4First(`in`: List<InetAddress>): List<String> {
            val out = ArrayList<String>()
            var i = 0
            for (addr in `in`) {
                if (addr is Inet4Address) {
                    out.add(i++, addr.getHostAddress()!!)
                } else {
                    out.add(addr.hostAddress!!)
                }
            }
            return out
        }

        private fun hasDefaultRoute(linkProperties: LinkProperties): Boolean {
            for (route in linkProperties.routes) {
                if (route.isDefaultRoute) {
                    return true
                }
            }
            return false
        }
    }
}