/*
 *	This file is part of dhcp4java, a DHCP API for the Java language.
 * (c) 2006 Stephan Hadinger
 *
 *	This library is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU Lesser General Public
 *	License as published by the Free Software Foundation; either
 *	version 2.1 of the License, or (at your option) any later version.
 *
 *	This library is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *	Lesser General Public License for more details.
 *
 *	You should have received a copy of the GNU Lesser General Public
 *	License along with this library; if not, write to the Free Software
 *	Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.dhcp4java

import java.lang.reflect.Modifier
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.*

/**
 * Class holding all DHCP constants.
 *
 * @author Stephan Hadinger
 * @version 1.00
 */
class DHCPConstants private constructor() {
    // Suppresses default constructor, ensuring non-instantiability.
    init {
        throw UnsupportedOperationException()
    }

    companion object {
        // ========================================================================
        // DHCP Constants
        /** DHCP BOOTP CODES  */
        const val BOOTREQUEST: Byte = 1
        const val BOOTREPLY: Byte = 2

        /** DHCP HTYPE CODES  */
        const val HTYPE_ETHER: Byte = 1
        const val HTYPE_IEEE802: Byte = 6
        const val HTYPE_FDDI: Byte = 8
        const val HTYPE_IEEE1394: Byte = 24 // rfc 2855

        /** DHCP MESSAGE CODES  */
        const val DHCPDISCOVER: Byte = 1
        const val DHCPOFFER: Byte = 2
        const val DHCPREQUEST: Byte = 3
        const val DHCPDECLINE: Byte = 4
        const val DHCPACK: Byte = 5
        const val DHCPNAK: Byte = 6
        const val DHCPRELEASE: Byte = 7
        const val DHCPINFORM: Byte = 8
        const val DHCPFORCERENEW: Byte = 9
        const val DHCPLEASEQUERY: Byte = 10 // RFC 4388
        const val DHCPLEASEUNASSIGNED: Byte = 11 // RFC 4388
        const val DHCPLEASEUNKNOWN: Byte = 12 // RFC 4388
        const val DHCPLEASEACTIVE: Byte = 13 // RFC 4388

        /** DHCP OPTIONS CODE  */
        const val DHO_PAD: Byte = 0
        const val DHO_SUBNET_MASK: Byte = 1
        const val DHO_TIME_OFFSET: Byte = 2
        const val DHO_ROUTERS: Byte = 3
        const val DHO_TIME_SERVERS: Byte = 4
        const val DHO_NAME_SERVERS: Byte = 5
        const val DHO_DOMAIN_NAME_SERVERS: Byte = 6
        const val DHO_LOG_SERVERS: Byte = 7
        const val DHO_COOKIE_SERVERS: Byte = 8
        const val DHO_LPR_SERVERS: Byte = 9
        const val DHO_IMPRESS_SERVERS: Byte = 10
        const val DHO_RESOURCE_LOCATION_SERVERS: Byte = 11
        const val DHO_HOST_NAME: Byte = 12
        const val DHO_BOOT_SIZE: Byte = 13
        const val DHO_MERIT_DUMP: Byte = 14
        const val DHO_DOMAIN_NAME: Byte = 15
        const val DHO_SWAP_SERVER: Byte = 16
        const val DHO_ROOT_PATH: Byte = 17
        const val DHO_EXTENSIONS_PATH: Byte = 18
        const val DHO_IP_FORWARDING: Byte = 19
        const val DHO_NON_LOCAL_SOURCE_ROUTING: Byte = 20
        const val DHO_POLICY_FILTER: Byte = 21
        const val DHO_MAX_DGRAM_REASSEMBLY: Byte = 22
        const val DHO_DEFAULT_IP_TTL: Byte = 23
        const val DHO_PATH_MTU_AGING_TIMEOUT: Byte = 24
        const val DHO_PATH_MTU_PLATEAU_TABLE: Byte = 25
        const val DHO_INTERFACE_MTU: Byte = 26
        const val DHO_ALL_SUBNETS_LOCAL: Byte = 27
        const val DHO_BROADCAST_ADDRESS: Byte = 28
        const val DHO_PERFORM_MASK_DISCOVERY: Byte = 29
        const val DHO_MASK_SUPPLIER: Byte = 30
        const val DHO_ROUTER_DISCOVERY: Byte = 31
        const val DHO_ROUTER_SOLICITATION_ADDRESS: Byte = 32
        const val DHO_STATIC_ROUTES: Byte = 33
        const val DHO_TRAILER_ENCAPSULATION: Byte = 34
        const val DHO_ARP_CACHE_TIMEOUT: Byte = 35
        const val DHO_IEEE802_3_ENCAPSULATION: Byte = 36
        const val DHO_DEFAULT_TCP_TTL: Byte = 37
        const val DHO_TCP_KEEPALIVE_INTERVAL: Byte = 38
        const val DHO_TCP_KEEPALIVE_GARBAGE: Byte = 39
        const val DHO_NIS_SERVERS: Byte = 41
        const val DHO_NTP_SERVERS: Byte = 42
        const val DHO_VENDOR_ENCAPSULATED_OPTIONS: Byte = 43
        const val DHO_NETBIOS_NAME_SERVERS: Byte = 44
        const val DHO_NETBIOS_DD_SERVER: Byte = 45
        const val DHO_NETBIOS_NODE_TYPE: Byte = 46
        const val DHO_NETBIOS_SCOPE: Byte = 47
        const val DHO_FONT_SERVERS: Byte = 48
        const val DHO_X_DISPLAY_MANAGER: Byte = 49
        const val DHO_DHCP_REQUESTED_ADDRESS: Byte = 50
        const val DHO_DHCP_LEASE_TIME: Byte = 51
        const val DHO_DHCP_OPTION_OVERLOAD: Byte = 52
        const val DHO_DHCP_MESSAGE_TYPE: Byte = 53
        const val DHO_DHCP_SERVER_IDENTIFIER: Byte = 54
        const val DHO_DHCP_PARAMETER_REQUEST_LIST: Byte = 55
        const val DHO_DHCP_MESSAGE: Byte = 56
        const val DHO_DHCP_MAX_MESSAGE_SIZE: Byte = 57
        const val DHO_DHCP_RENEWAL_TIME: Byte = 58
        const val DHO_DHCP_REBINDING_TIME: Byte = 59
        const val DHO_VENDOR_CLASS_IDENTIFIER: Byte = 60
        const val DHO_DHCP_CLIENT_IDENTIFIER: Byte = 61
        const val DHO_NWIP_DOMAIN_NAME: Byte = 62 // rfc 2242
        const val DHO_NWIP_SUBOPTIONS: Byte = 63 // rfc 2242
        const val DHO_NISPLUS_DOMAIN: Byte = 64
        const val DHO_NISPLUS_SERVER: Byte = 65
        const val DHO_TFTP_SERVER: Byte = 66
        const val DHO_BOOTFILE: Byte = 67
        const val DHO_MOBILE_IP_HOME_AGENT: Byte = 68
        const val DHO_SMTP_SERVER: Byte = 69
        const val DHO_POP3_SERVER: Byte = 70
        const val DHO_NNTP_SERVER: Byte = 71
        const val DHO_WWW_SERVER: Byte = 72
        const val DHO_FINGER_SERVER: Byte = 73
        const val DHO_IRC_SERVER: Byte = 74
        const val DHO_STREETTALK_SERVER: Byte = 75
        const val DHO_STDA_SERVER: Byte = 76
        const val DHO_USER_CLASS: Byte = 77 // rfc 3004
        const val DHO_FQDN: Byte = 81
        const val DHO_DHCP_AGENT_OPTIONS: Byte = 82 // rfc 3046
        const val DHO_NDS_SERVERS: Byte = 85 // rfc 2241
        const val DHO_NDS_TREE_NAME: Byte = 86 // rfc 2241
        const val DHO_NDS_CONTEXT: Byte = 87 // rfc 2241
        const val DHO_CLIENT_LAST_TRANSACTION_TIME: Byte = 91 // rfc 4388
        const val DHO_ASSOCIATED_IP: Byte = 92 // rfc 4388
        const val DHO_USER_AUTHENTICATION_PROTOCOL: Byte = 98
        const val DHO_AUTO_CONFIGURE: Byte = 116
        const val DHO_NAME_SERVICE_SEARCH: Byte = 117 // rfc 2937
        const val DHO_SUBNET_SELECTION: Byte = 118 // rfc 3011
        const val DHO_DOMAIN_SEARCH: Byte = 119 // rfc 3397
        const val DHO_CLASSLESS_ROUTE: Byte = 121 // rfc 3442
        const val DHO_END: Byte = -1

        /** Any address  */
        val INADDR_ANY = inaddrAny

        /** Broadcast Address  */
        val INADDR_BROADCAST = inaddrBroadcast

        // bad luck
        private val inaddrAny: InetAddress
            get() = try {
                val rawAddr = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte())
                InetAddress.getByAddress(rawAddr)
            } catch (e: UnknownHostException) {
                // bad luck
                throw IllegalStateException("Unable to generate INADDR_ANY")
            }

        // bad luck
        private val inaddrBroadcast: InetAddress
            get() = try {
                val rawAddr = byteArrayOf((-1.toByte()).toByte(), (-1.toByte()).toByte(), (-1.toByte()).toByte(), (-1.toByte()).toByte())
                InetAddress.getByAddress(rawAddr)
            } catch (e: UnknownHostException) {
                // bad luck
                throw IllegalStateException("Unable to generate INADDR_BROADCAST")
            }

        /**
         * Converts a DHCP option name into the option code.
         * @param name user-readable option name
         * @return the option code
         * @throws NullPointerException name is `null.
        ` */
        fun getDhoNamesReverse(name: String?): Byte? {
            if (name == null) {
                throw NullPointerException()
            }
            return dhoNamesReverseMap!![name]
        }

        /**
         * Converts a DHCP code into a user-readable DHCP option name.
         * @param code DHCP option code
         * @return user-readable DHCP option name
         */
        fun getDhoName(code: Byte): String? {
            return dhoNamesMap!![code]
        }

        // sanity check values
        const val _DHCP_MIN_LEN = 548
        const val _DHCP_DEFAULT_MAX_LEN = 576 // max default size for client
        const val _BOOTP_ABSOLUTE_MIN_LEN = 236
        const val _DHCP_MAX_MTU = 1500
        const val _DHCP_UDP_OVERHEAD = 14 + 20 + 8
        const val _BOOTP_VEND_SIZE = 64

        // Magic cookie
        const val _MAGIC_COOKIE = 0x63825363
        const val BOOTP_REQUEST_PORT = 67
        const val BOOTP_REPLY_PORT = 68

        /**
         * Returns a map associating a BootCode and the user-readable name.
         *
         * <P>Currently:<br></br>
         * 1=BOOTREQUEST<br></br>
         * 2=BOOTREPLY
         * @return the map
         */
        // Maps for "code" to "string" conversion
        var bootNamesMap: Map<Byte, String>? = null

        /**
         * Returns a map associating a HType and the user-readable name.
         *
         *
         * Ex: 1=HTYPE_ETHER
         * @return the map
         */
        var htypesMap: Map<Byte, String>? = null

        /**
         * Returns a map associating a DHCP code and the user-readable name.
         *
         *
         * ex: 1=DHCPDISCOVER
         * @return the map
         */
        var dhcpCodesMap: Map<Byte, String>? = null

        /**
         * Returns a map associating a DHCP option code and the user-readable name.
         *
         *
         * ex: 1=DHO_SUBNET_MASK, 51=DHO_DHCP_LEASE_TIME,
         * @return the map
         */
        var dhoNamesMap: Map<Byte, String>? = null

        /**
         * Returns a map associating a user-readable DHCP option name and the option code.
         *
         *
         * ex: "DHO_SUBNET_MASK"=1, "DHO_DHCP_LEASE_TIME"=51
         * @return the map
         */
        var dhoNamesReverseMap: Map<String, Byte>? = null

        /*
       * preload at startup Maps with constants
       * allowing reverse lookup
       */
        init {
            val bootNames: MutableMap<Byte, String> = LinkedHashMap()
            val htypeNames: MutableMap<Byte, String> = LinkedHashMap()
            val dhcpCodes: MutableMap<Byte, String> = LinkedHashMap()
            val dhoNames: MutableMap<Byte, String> = LinkedHashMap()
            val dhoNamesRev: MutableMap<String, Byte> = LinkedHashMap()

            // do some introspection to list constants
            val fields = DHCPConstants::class.java.declaredFields

            // parse internal fields
            try {
                for (field in fields) {
                    val mod: Int = field.modifiers
                    val name: String = field.name

                    // parse only "public final static byte"
                    if (Modifier.isFinal(mod) && Modifier.isPublic(mod) && Modifier.isStatic(mod) && field.type == Byte::class.javaPrimitiveType) {
                        val code: Byte = field.getByte(null)
                        if (name.startsWith("BOOT")) {
                            bootNames.put(code, name)
                        } else if (name.startsWith("HTYPE_")) {
                            htypeNames.put(code, name)
                        } else if (name.startsWith("DHCP")) {
                            dhcpCodes.put(code, name)
                        } else if (name.startsWith("DHO_")) {
                            dhoNames.put(code, name)
                            dhoNamesRev.put(name, code)
                        }
                    }
                }
            } catch (e: IllegalAccessException) {
                // we have a problem
                throw IllegalStateException("Fatal error while parsing internal fields")
            }

            bootNamesMap = Collections.unmodifiableMap(bootNames)
            htypesMap = Collections.unmodifiableMap(htypeNames)
            dhcpCodesMap = Collections.unmodifiableMap(dhcpCodes)
            dhoNamesMap = Collections.unmodifiableMap(dhoNames)
            dhoNamesReverseMap = Collections.unmodifiableMap(dhoNamesRev)
        }
    }
}