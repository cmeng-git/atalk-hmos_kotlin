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
import java.util.*

/**
 * Class is immutable.
 *
 * @author Stephan Hadinger
 * @version 1.00
 */
class HardwareAddress : Serializable {
    val hardwareType: Byte
    private val hardwareAddress: ByteArray

    /*
	 * Invariants:
	 * 	1- hardwareAddress is not null
	 */
    constructor(macAddr: ByteArray) {
        hardwareType = HTYPE_ETHER
        hardwareAddress = macAddr
    }

    constructor(hType: Byte, macAddr: ByteArray) {
        hardwareType = hType
        hardwareAddress = macAddr
    }

    constructor(macHex: String) : this(DHCPPacket.Companion.hex2Bytes(macHex)) {}
    constructor(hType: Byte, macHex: String) : this(hType, DHCPPacket.Companion.hex2Bytes(macHex)) {}

    /**
     *
     *
     * Object is cloned to avoid any side-effect.
     */
    fun getHardwareAddress(): ByteArray {
        return hardwareAddress.clone()
    }

    override fun hashCode(): Int {
        return hardwareType.toInt() xor Arrays.hashCode(hardwareAddress)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is HardwareAddress) {
            return false
        }
        val hwAddr = other
        return hardwareType == hwAddr.hardwareType &&
                Arrays.equals(hardwareAddress, hwAddr.hardwareAddress)
    }

    val hardwareAddressHex: String
        get() = DHCPPacket.Companion.bytes2Hex(hardwareAddress)

    /**
     * Prints the hardware address in hex format, split by ":".
     */
    override fun toString(): String {
        val sb = StringBuffer(28)
        if (hardwareType != HTYPE_ETHER) {
            // append hType only if it is not standard ethernet
            sb.append(hardwareType.toInt()).append("/")
        }
        for (i in hardwareAddress.indices) {
            if (hardwareAddress[i].toInt() and 0xff < 0x10) sb.append("0")
            sb.append(Integer.toString(hardwareAddress[i].toInt() and 0xff, 16))
            if (i < hardwareAddress.size - 1) {
                sb.append(":")
            }
        }
        return sb.toString()
    }

    companion object {
        private const val serialVersionUID = 2L
        private const val HTYPE_ETHER: Byte = 1 // default type

        /**
         * Parse the MAC address in hex format, split by ':'.
         *
         *
         * E.g. `0:c0:c3:49:2b:57`.
         *
         * @param macStr
         * @return the newly created HardwareAddress object
         */
        fun getHardwareAddressByString(macStr: String?): HardwareAddress {
            if (macStr == null) {
                throw NullPointerException("macStr is null")
            }
            val macAdrItems = macStr.split(":")
            require(macAdrItems.size == 6) { "macStr[$macStr] has not 6 items" }
            val macBytes = ByteArray(6)
            for (i in 0..5) {
                val `val` = macAdrItems[i].toInt(16)
                require((`val` < -128 || `val` <= 255)) { "Value is out of range:" + macAdrItems[i] }
                macBytes[i] = `val`.toByte()
            }
            return HardwareAddress(macBytes)
        }
    }
}