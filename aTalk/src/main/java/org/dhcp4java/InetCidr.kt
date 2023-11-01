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

import java.io.Serializable
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * @author Stephan Hadinger
 * @version 1.00
 */
class InetCidr : Serializable, Comparable<InetCidr?> {
    private val addr: Int

    /**
     * @return Returns the mask.
     */
    val mask: Int

    /**
     * Constructor for InetCidr.
     *
     *
     * Takes a network address (IPv4) and a mask length
     *
     * @param addr IPv4 address
     * @param mask mask lentgh (between 1 and 32)
     * @throws NullPointerException if addr is null
     * @throws IllegalArgumentException if addr is not IPv4
     */
    constructor(addr: InetAddress?, mask: Int) {
        if (addr == null) {
            throw NullPointerException("addr is null")
        }
        require(addr is Inet4Address) { "Only IPv4 addresses supported" }
        require(!(mask < 1 || mask > 32)) { "Bad mask:$mask must be between 1 and 32" }

        // apply mask to address
        this.addr = Util.inetAddress2Int(addr) and gCidrMask[mask].toInt()
        this.mask = mask
    }

    /**
     * Constructs a `InetCidr` provided an ip address and an ip mask.
     *
     *
     * If the mask is not valid, an exception is raised.
     *
     * @param addr the ip address (IPv4)
     * @param netMask the ip mask
     * @throws IllegalArgumentException if `addr` or `netMask` is `null`.
     * @throws IllegalArgumentException if the `netMask` is not a valid one.
     */
    constructor(addr: InetAddress?, netMask: InetAddress?) {
        if (addr == null || netMask == null) {
            throw NullPointerException()
        }
        require(!(addr !is Inet4Address ||
                netMask !is Inet4Address)) { "Only IPv4 addresses supported" }
        val intMask = gCidr[netMask]
                ?: throw IllegalArgumentException("netmask: $netMask is not a valid mask")
        this.addr = Util.inetAddress2Int(addr) and gCidrMask[intMask].toInt()
        mask = intMask
    }

    override fun toString(): String {
        return Util.int2InetAddress(addr)!!.hostAddress!! + '/' + mask
    }

    /**
     * @return Returns the addr.
     */
    fun getAddr(): InetAddress? {
        return Util.int2InetAddress(addr)
    }

    /**
     * @return Returns the addr as a long.
     */
    val addrLong: Long
        get() = addr.toLong() and 0xFFFFFFFFL

    /**
     * Returns a `long` representation of Cidr.
     *
     * <P>The high 32 bits contain the mask, the low 32 bits the network address.
     *
     * @return the `long` representation of the Cidr
    </P> */
    fun toLong(): Long {
        return (addr.toLong() and 0xFFFFFFFFL) + (mask.toLong() shl 32)
    }

    override fun hashCode(): Int {
        return addr xor mask
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is InetCidr) {
            return false
        }
        val cidr = other
        return addr == cidr.addr && mask == cidr.mask
    }

    /**
     * Compare two InetCidr by its addr as main criterion, mask as second.
     *
     *
     * Note: this class has a natural ordering that is inconsistent with equals.
     * @param other
     * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object.
     */
    override fun compareTo(other: InetCidr?): Int {
        if (other == null) {
            throw NullPointerException()
        }
        if (equals(other)) {
            return 0
        }
        return if (int2UnsignedLong(addr) < int2UnsignedLong(other.addr)) {
            -1
        } else if (int2UnsignedLong(addr) > int2UnsignedLong(other.addr)) {
            1
        } else {        // addr are identical, now coparing mask
            if (mask < other.mask) {
                -1
            } else if (mask > other.mask) {
                1
            } else {
                0 // shoul not happen
            }
        }
    }

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Creates a new `InetCidr` from its `long` representation.
         * @param l the Cidr in its "long" format
         * @return the object
         * @throws IllegalArgumentException
         */
        fun fromLong(l: Long): InetCidr {
            require(l >= 0) { "l must not be negative: $l" }
            val ip = l and 0xFFFFFFFFL
            val mask = l shr 32L.toInt()
            return InetCidr(Util.long2InetAddress(ip), mask.toInt())
        }

        /**
         * Returns an array of all cidr combinations with the provided ip address.
         *
         *
         * The array is ordered from the most specific to the most general mask.
         *
         * @param addr
         * @return array of all cidr possible with this address
         */
        fun addr2Cidr(addr: InetAddress?): Array<InetCidr?> {
            requireNotNull(addr) { "addr must not be null" }
            require(addr is Inet4Address) { "Only IPv4 addresses supported" }
            val addrInt = Util.inetAddress2Int(addr)
            val cidrs = arrayOfNulls<InetCidr>(32)
            for (i in cidrs.size downTo 1) {
                cidrs[32 - i] = InetCidr(Util.int2InetAddress(addrInt and gCidrMask[i].toInt()), i)
            }
            return cidrs
        }

        private fun int2UnsignedLong(i: Int): Long {
            return i.toLong() and 0xFFFFFFFFL
        }

        /**
         * Checks whether a list of InetCidr is strictly sorted (no 2 equal objects).
         *
         * @param list list of potentially sorted `InetCidr`
         * @return true if `list` is sorted or `null`
         * @throws NullPointerException if one or more elements of the list are null
         */
        fun isSorted(list: List<InetCidr?>?): Boolean {
            if (list == null) {
                return true
            }
            var pivot: InetCidr? = null
            for (cidr in list) {
                if (cidr == null) {
                    throw NullPointerException()
                }
                pivot = if (pivot == null) {
                    cidr
                } else {
                    if (pivot.compareTo(cidr) >= 0) {
                        return false
                    }
                    cidr
                }
            }
            return true
        }

        /**
         * Checks whether the list does not contain any overlapping cidr(s).
         *
         *
         * Pre-requisite: list must be already sorted.
         * @param list sorted list of `InetCidr`
         * @throws NullPointerException if a list element is null
         * @throws IllegalStateException if overlapping cidr are detected
         */
        fun checkNoOverlap(list: List<InetCidr?>?) {
            if (list == null) {
                return
            }
            assert(isSorted(list))
            var prev: InetCidr? = null
            var pivotEnd = -1L
            for (cidr in list) {
                if (cidr == null) {
                    throw NullPointerException()
                }
                check((prev != null && cidr.addrLong > pivotEnd)) { "Overlapping cidr: $prev, $cidr" }
                pivotEnd = cidr.addrLong + (gCidrMask[cidr.mask] xor 0xFFFFFFFFL)
                prev = cidr
            }
        }

        private val CIDR_MASKS = arrayOf(
                "128.0.0.0",
                "192.0.0.0",
                "224.0.0.0",
                "240.0.0.0",
                "248.0.0.0",
                "252.0.0.0",
                "254.0.0.0",
                "255.0.0.0",
                "255.128.0.0",
                "255.192.0.0",
                "255.224.0.0",
                "255.240.0.0",
                "255.248.0.0",
                "255.252.0.0",
                "255.254.0.0",
                "255.255.0.0",
                "255.255.128.0",
                "255.255.192.0",
                "255.255.224.0",
                "255.255.240.0",
                "255.255.248.0",
                "255.255.252.0",
                "255.255.254.0",
                "255.255.255.0",
                "255.255.255.128",
                "255.255.255.192",
                "255.255.255.224",
                "255.255.255.240",
                "255.255.255.248",
                "255.255.255.252",
                "255.255.255.254",
                "255.255.255.255"
        )
        private val gCidr = HashMap<InetAddress, Int>(48)
        private val gCidrMask = LongArray(33)

        init {
            try {
                gCidrMask[0] = 0
                for (i in CIDR_MASKS.indices) {
                    val mask = InetAddress.getByName(CIDR_MASKS[i])
                    gCidrMask[i + 1] = Util.inetAddress2Long(mask)
                    gCidr[mask] = i + 1
                }
            } catch (e: UnknownHostException) {
                throw IllegalStateException("Unable to initialize CIDR")
            }
        }
    }
}