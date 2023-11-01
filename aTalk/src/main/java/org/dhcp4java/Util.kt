/*
 *	This file is part of dhcp4java, a DHCP API for the Java language.
 *	(c) 2006 Stephan Hadinger
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

import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * @author Stephan Hadinger
 * @version 1.00
 */
class Util private constructor() {
    // Suppresses default constructor, ensuring non-instantiability.
    init {
        throw UnsupportedOperationException()
    }

    companion object {
        /**
         * Converts 32 bits int to IPv4 `InetAddress`.
         *
         * @param val int representation of IPv4 address
         * @return the address object
         */
        fun int2InetAddress(`val`: Int): InetAddress? {
            val value = byteArrayOf((`val` and -0x1000000 ushr 24).toByte(), (`val` and 0X00FF0000 ushr 16).toByte(), (`val` and 0x0000FF00 ushr 8).toByte(), (`val` and 0x000000FF).toByte())
            return try {
                InetAddress.getByAddress(value)
            } catch (e: UnknownHostException) {
                null
            }
        }

        /**
         * Converts 32 bits int packaged into a 64bits long to IPv4 `InetAddress`.
         *
         * @param val int representation of IPv4 address
         * @return the address object
         */
        fun long2InetAddress(`val`: Long): InetAddress? {
            if (`val` < 0 || `val` > 0xFFFFFFFFL) {
                // TODO exception ???
            }
            return int2InetAddress(`val`.toInt())
        }

        /**
         * Converts IPv4 `InetAddress` to 32 bits int.
         *
         * @param addr IPv4 address object
         * @return 32 bits int
         * @throws NullPointerException `addr` is `null`.
         * @throws IllegalArgumentException the address is not IPv4 (Inet4Address).
         */
        fun inetAddress2Int(addr: InetAddress): Int {
            require(addr is Inet4Address) { "Only IPv4 supported" }
            val addrBytes = addr.getAddress()
            return addrBytes[0].toInt() and 0xFF shl 24 or
                    (addrBytes[1].toInt() and 0xFF shl 16) or
                    (addrBytes[2].toInt() and 0xFF shl 8) or
                    (addrBytes[3].toInt() and 0xFF)
        }

        /**
         * Converts IPv4 `InetAddress` to 32 bits int, packages into a 64 bits `long`.
         *
         * @param addr IPv4 address object
         * @return 32 bits int
         * @throws NullPointerException `addr` is `null`.
         * @throws IllegalArgumentException the address is not IPv4 (Inet4Address).
         */
        fun inetAddress2Long(addr: InetAddress): Long {
            return inetAddress2Int(addr).toLong() and 0xFFFFFFFFL
        }
    }
}