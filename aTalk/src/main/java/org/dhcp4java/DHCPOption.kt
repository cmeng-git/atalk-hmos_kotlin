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

import org.dhcp4java.DHCPPacket.Companion.appendHex
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.Serializable
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.*

/**
 * Class for manipulating DHCP options (used internally).
 *
 * @author Stephan Hadinger
 * @author Eng Chong Meng
 * @version 1.00
 *
 * Immutable object.
 */
class DHCPOption @JvmOverloads constructor(code: Byte, value: ByteArray?, mirror: Boolean = false) : Serializable {
    /**
     * Return the `code` field (byte).
     *
     * @return code field
     */
    /**
     * The code of the option. 0 is reserved for padding, -1 for end of options.
     */
    val code: Byte
    /**
     * @return option value, never `null`. Minimal value is `byte[0]`.
     */
    /**
     * Raw bytes value of the option. Some methods are provided for higher
     * level of data structures, depending on the `code`.
     */
    val valueFast: ByteArray?
    /**
     * Returns whether the option is marked as "mirror", meaning it should mirror
     * the option value in the client request.
     *
     *
     * To be used only in servers.
     *
     * @return is the option marked is mirror?
     */
    /**
     * Used to mark an option as having a mirroring behaviour. This means that
     * this option if used by a server will first mirror the option the client sent
     * then provide a default value if this option was not present in the request.
     *
     *
     * This is only meant to be used by servers through the `getMirrorValue`
     * method.
     */
    val isMirror: Boolean

    /**
     * returns true if two `DHCPOption` objects are equal, i.e. have same `code`
     * and same `value`.
     */
    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is DHCPOption) {
            return false
        }
        val opt = other
        return opt.code == this.code && opt.isMirror == isMirror &&
                Arrays.equals(opt.valueFast, valueFast)
    }

    /**
     * Returns hashcode.
     *
     * @see Object.hashCode
     */
    override fun hashCode(): Int {
        return this.code.toInt() xor Arrays.hashCode(valueFast) xor
                if (isMirror) -0x80000000 else 0
    }

    /**
     * @return option value, can be null.
     */
    fun getValue(): ByteArray? {
        return if (valueFast == null) null else valueFast.clone()
    }

    /**
     * Returns a DHCP Option as Byte format.
     *
     * This method is only allowed for the following option codes:
     * <pre>
     * DHO_IP_FORWARDING(19)
     * DHO_NON_LOCAL_SOURCE_ROUTING(20)
     * DHO_DEFAULT_IP_TTL(23)
     * DHO_ALL_SUBNETS_LOCAL(27)
     * DHO_PERFORM_MASK_DISCOVERY(29)
     * DHO_MASK_SUPPLIER(30)
     * DHO_ROUTER_DISCOVERY(31)
     * DHO_TRAILER_ENCAPSULATION(34)
     * DHO_IEEE802_3_ENCAPSULATION(36)
     * DHO_DEFAULT_TCP_TTL(37)
     * DHO_TCP_KEEPALIVE_GARBAGE(39)
     * DHO_NETBIOS_NODE_TYPE(46)
     * DHO_DHCP_OPTION_OVERLOAD(52)
     * DHO_DHCP_MESSAGE_TYPE(53)
     * DHO_AUTO_CONFIGURE(116)
    </pre> *
     *
     * @return the option value, `null` if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    @get:Throws(IllegalArgumentException::class)
    val valueAsByte: Byte
        get() {
            require(isOptionAsByte(code)) { "DHCP option type (" + this.code + ") is not byte" }
            checkNotNull(valueFast) { "value is null" }
            if (valueFast.size != 1) {
                throw DHCPBadPacketException("option " + this.code + " is wrong size:" + valueFast.size + " should be 1")
            }
            return valueFast[0]
        }

    /**
     * Returns a DHCP Option as Short format.
     *
     *
     * This method is only allowed for the following option codes:
     * <pre>
     * DHO_BOOT_SIZE(13)
     * DHO_MAX_DGRAM_REASSEMBLY(22)
     * DHO_INTERFACE_MTU(26)
     * DHO_DHCP_MAX_MESSAGE_SIZE(57)
    </pre> *
     *
     * @return the option value, `null` if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    @get:Throws(IllegalArgumentException::class)
    val valueAsShort: Short
        get() {
            require(isOptionAsShort(code)) { "DHCP option type (" + this.code + ") is not short" }
            checkNotNull(valueFast) { "value is null" }
            if (valueFast.size != 2) {
                throw DHCPBadPacketException("option " + this.code + " is wrong size:" + valueFast.size + " should be 2")
            }
            return (valueFast[0].toInt() and 0xff shl 8 or (valueFast[1].toInt() and 0xFF)).toShort()
        }

    /**
     * Returns a DHCP Option as Integer format.
     *
     *
     * This method is only allowed for the following option codes:
     * <pre>
     * DHO_TIME_OFFSET(2)
     * DHO_PATH_MTU_AGING_TIMEOUT(24)
     * DHO_ARP_CACHE_TIMEOUT(35)
     * DHO_TCP_KEEPALIVE_INTERVAL(38)
     * DHO_DHCP_LEASE_TIME(51)
     * DHO_DHCP_RENEWAL_TIME(58)
     * DHO_DHCP_REBINDING_TIME(59)
    </pre> *
     *
     * @return the option value, `null` if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    @get:Throws(IllegalArgumentException::class)
    val valueAsInt: Int
        get() {
            require(isOptionAsInt(code)) { "DHCP option type (" + this.code + ") is not int" }
            checkNotNull(valueFast) { "value is null" }
            if (valueFast.size != 4) {
                throw DHCPBadPacketException("option " + this.code + " is wrong size:" + valueFast.size + " should be 4")
            }
            return valueFast[0].toInt() and 0xFF shl 24 or (
                    valueFast[1].toInt() and 0xFF shl 16) or (
                    valueFast[2].toInt() and 0xFF shl 8) or
                    (valueFast[3].toInt() and 0xFF)
        }
    // TODO// short// byte
    /**
     * Returns a DHCP Option as Integer format, but is usable for any numerical type: int, short or byte.
     *
     *
     * There is no check on the option
     *
     * @return the option value `null` if option is not present, or wrong number of bytes.
     */
    @get:Throws(IllegalArgumentException::class)
    val valueAsNum: Int?
        get() {
            if (valueFast == null) {
                return null
            }
            return if (valueFast.size == 1) {            // byte
                valueFast[0].toInt() and 0xFF
            } else if (valueFast.size == 2) {        // short
                valueFast[0].toInt() and 0xff shl 8 or (valueFast[1].toInt() and 0xFF)
            } else if (valueFast.size == 4) {
                valueFast[0].toInt() and 0xFF shl 24 or (
                        valueFast[1].toInt() and 0xFF shl 16) or (
                        valueFast[2].toInt() and 0xFF shl 8) or
                        (valueFast[3].toInt() and 0xFF)
            } else {
                null
            }
        }// normally impossible

    /**
     * Returns a DHCP Option as InetAddress format.
     *
     *
     * This method is only allowed for the following option codes:
     * <pre>
     * DHO_SUBNET_MASK(1)
     * DHO_SWAP_SERVER(16)
     * DHO_BROADCAST_ADDRESS(28)
     * DHO_ROUTER_SOLICITATION_ADDRESS(32)
     * DHO_DHCP_REQUESTED_ADDRESS(50)
     * DHO_DHCP_SERVER_IDENTIFIER(54)
     * DHO_SUBNET_SELECTION(118)
    </pre> *
     *
     * @return the option value, `null` if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    @get:Throws(IllegalArgumentException::class)
    val valueAsInetAddr: InetAddress?
        get() {
            require(isOptionAsInetAddr(code)) { "DHCP option type (" + this.code + ") is not InetAddr" }
            checkNotNull(valueFast) { "value is null" }
            if (valueFast.size != 4) {
                throw DHCPBadPacketException("option " + this.code + " is wrong size:" + valueFast.size + " should be 4")
            }
            return try {
                InetAddress.getByAddress(valueFast)
            } catch (e: UnknownHostException) {
                Timber.e(e, "Unexpected UnknownHostException")
                null // normally impossible
            }
        }

    /**
     * Returns a DHCP Option as String format.
     *
     *
     * This method is only allowed for the following option codes:
     * <pre>
     * DHO_HOST_NAME(12)
     * DHO_MERIT_DUMP(14)
     * DHO_DOMAIN_NAME(15)
     * DHO_ROOT_PATH(17)
     * DHO_EXTENSIONS_PATH(18)
     * DHO_NETBIOS_SCOPE(47)
     * DHO_DHCP_MESSAGE(56)
     * DHO_VENDOR_CLASS_IDENTIFIER(60)
     * DHO_NWIP_DOMAIN_NAME(62)
     * DHO_NIS_DOMAIN(64)
     * DHO_NIS_SERVER(65)
     * DHO_TFTP_SERVER(66)
     * DHO_BOOTFILE(67)
     * DHO_NDS_TREE_NAME(86)
     * DHO_USER_AUTHENTICATION_PROTOCOL(98)
    </pre> *
     *
     * @return the option value, `null` if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    @get:Throws(IllegalArgumentException::class)
    val valueAsString: String
        get() {
            require(isOptionAsString(code)) { "DHCP option type (" + this.code + ") is not String" }
            checkNotNull(valueFast) { "value is null" }
            return DHCPPacket.bytesToString(valueFast)
        }// multiple of 2

    /**
     * Returns a DHCP Option as Short array format.
     *
     *
     * This method is only allowed for the following option codes:
     * <pre>
     * DHO_PATH_MTU_PLATEAU_TABLE(25)
     * DHO_NAME_SERVICE_SEARCH(117)
    </pre> *
     *
     * @return the option value array, `null` if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    @get:Throws(IllegalArgumentException::class)
    val valueAsShorts: ShortArray
        get() {
            require(isOptionAsShorts(code)) { "DHCP option type (" + this.code + ") is not short[]" }
            checkNotNull(valueFast) { "value is null" }
            if (valueFast.size % 2 != 0) // multiple of 2
            {
                throw DHCPBadPacketException("option " + this.code + " is wrong size:" + valueFast.size + " should be 2*X")
            }
            val shorts = ShortArray(valueFast.size / 2)
            var i = 0
            var a = 0
            while (a < valueFast.size) {
                shorts[i] = (valueFast[a].toInt() and 0xFF shl 8 or (valueFast[a + 1].toInt() and 0xFF)).toShort()
                i++
                a += 2
            }
            return shorts
        }// normally impossible// multiple of 4

    /**
     * Returns a DHCP Option as InetAddress array format.
     *
     *
     * This method is only allowed for the following option codes:
     * <pre>
     * DHO_ROUTERS(3)
     * DHO_TIME_SERVERS(4)
     * DHO_NAME_SERVERS(5)
     * DHO_DOMAIN_NAME_SERVERS(6)
     * DHO_LOG_SERVERS(7)
     * DHO_COOKIE_SERVERS(8)
     * DHO_LPR_SERVERS(9)
     * DHO_IMPRESS_SERVERS(10)
     * DHO_RESOURCE_LOCATION_SERVERS(11)
     * DHO_POLICY_FILTER(21)
     * DHO_STATIC_ROUTES(33)
     * DHO_NIS_SERVERS(41)
     * DHO_NTP_SERVERS(42)
     * DHO_NETBIOS_NAME_SERVERS(44)
     * DHO_NETBIOS_DD_SERVER(45)
     * DHO_FONT_SERVERS(48)
     * DHO_X_DISPLAY_MANAGER(49)
     * DHO_MOBILE_IP_HOME_AGENT(68)
     * DHO_SMTP_SERVER(69)
     * DHO_POP3_SERVER(70)
     * DHO_NNTP_SERVER(71)
     * DHO_WWW_SERVER(72)
     * DHO_FINGER_SERVER(73)
     * DHO_IRC_SERVER(74)
     * DHO_STREETTALK_SERVER(75)
     * DHO_STDA_SERVER(76)
     * DHO_NDS_SERVERS(85)
    </pre> *
     *
     * @return the option value array, `null` if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    @get:Throws(IllegalArgumentException::class)
    val valueAsInetAddrs: Array<InetAddress?>?
        get() {
            require(isOptionAsInetAddrs(code)) { "DHCP option type (" + this.code + ") is not InetAddr[]" }
            checkNotNull(valueFast) { "value is null" }
            if (valueFast.size % 4 != 0) // multiple of 4
            {
                throw DHCPBadPacketException("option " + this.code + " is wrong size:" + valueFast.size + " should be 4*X")
            }
            return try {
                val addr = ByteArray(4)
                val addrs = arrayOfNulls<InetAddress>(valueFast.size / 4)
                var i = 0
                var a = 0
                while (a < valueFast.size) {
                    addr[0] = valueFast[a]
                    addr[1] = valueFast[a + 1]
                    addr[2] = valueFast[a + 2]
                    addr[3] = valueFast[a + 3]
                    addrs[i] = InetAddress.getByAddress(addr)
                    i++
                    a += 4
                }
                addrs
            } catch (e: UnknownHostException) {
                Timber.e(e, "Unexpected UnknownHostException")
                null // normally impossible
            }
        }

    /**
     * Returns a DHCP Option as Byte array format.
     *
     *
     * This method is only allowed for the following option codes:
     * <pre>
     * DHO_DHCP_PARAMETER_REQUEST_LIST(55)
    </pre> *
     *
     *
     * Note: this mehtod is similar to getOptionRaw, only with option type checking.
     *
     * @return the option value array, `null` if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    @get:Throws(IllegalArgumentException::class)
    val valueAsBytes: ByteArray?
        get() {
            require(isOptionAsBytes(code)) { "DHCP option type (" + this.code + ") is not bytes" }
            checkNotNull(valueFast) { "value is null" }
            return this.getValue()
        }

    /**
     * Get the option value based on the context, i.e. the client's request.
     *
     *
     * This should be the only method used with this class to get relevant values.
     *
     * @param request the client's DHCP requets
     * @return the value of the specific option in the client request
     * @throws NullPointerException if `request` is `null`.
     */
    fun applyOption(request: DHCPPacket?): DHCPOption {
        if (request == null) {
            throw NullPointerException("request is null")
        }
        return if (isMirror) {
            val res = request.getOption(this.code)
            res ?: this // return res or this
        } else {
            this
        }
    }

    /**
     * Appends to this string builder a detailed string representation of the DHCP datagram.
     *
     *
     * This multi-line string details: the static, options and padding parts
     * of the object. This is useful for debugging, but not efficient.
     *
     * @param buffer the string builder the string representation of this object should be appended.
     */
    fun append(buffer: StringBuilder) {
        // check for readable option name
        if (DHCPConstants.dhoNamesMap!!.containsKey(this.code)) {
            buffer.append(DHCPConstants.dhoNamesMap!!.get(this.code))
        }
        buffer.append('(')
                .append(unsignedByte(this.code))
                .append(")=")
        if (isMirror) {
            buffer.append("<mirror>")
        }

        // check for value printing
        if (valueFast == null) {
            buffer.append("<null>")
        } else if (this.code == DHCPConstants.DHO_DHCP_MESSAGE_TYPE) {
            val cmd = valueAsByte
            if (DHCPConstants.dhcpCodesMap!!.containsKey(cmd)) {
                buffer.append(DHCPConstants.dhcpCodesMap!!.get(cmd))
            } else {
                buffer.append(cmd)
            }
        } else if (this.code == DHCPConstants.DHO_USER_CLASS) {
            buffer.append(userClassToString(valueFast))
        } else if (this.code == DHCPConstants.DHO_DHCP_AGENT_OPTIONS) {
            buffer.append(agentOptionsToString(valueFast))
        } else if (_DHO_FORMATS.containsKey(this.code)) {
            // formatted output
            try {    // catch malformed values
                when (_DHO_FORMATS[this.code]) {
                    OptionFormat.INET -> DHCPPacket.appendHostAddress(buffer, valueAsInetAddr)
                    OptionFormat.INETS -> for (addr in valueAsInetAddrs!!) {
                        DHCPPacket.appendHostAddress(buffer, addr)
                        buffer.append(' ')
                    }
                    OptionFormat.INT -> buffer.append(valueAsInt)
                    OptionFormat.SHORT -> buffer.append(valueAsShort.toInt())
                    OptionFormat.SHORTS -> for (aShort in valueAsShorts) {
                        buffer.append(aShort.toInt())
                                .append(' ')
                    }
                    OptionFormat.BYTE -> buffer.append(valueAsByte.toInt())
                    OptionFormat.STRING -> buffer.append('"')
                            .append(valueAsString)
                            .append('"')
                    OptionFormat.BYTES -> for (aValue in valueFast) {
                        buffer.append(unsignedByte(aValue))
                                .append(' ')
                    }
                    else -> {
                        buffer.append("0x")
                        appendHex(buffer, valueFast)
                    }
                }
            } catch (e: IllegalArgumentException) {
                // fallback to bytes
                buffer.append("0x")
                appendHex(buffer, valueFast)
            }
        } else {
            // unformatted raw output
            buffer.append("0x")
            appendHex(buffer, valueFast)
        }
    }

    /**
     * Returns a detailed string representation of the DHCP datagram.
     *
     *
     * This multi-line string details: the static, options and padding parts
     * of the object. This is useful for debugging, but not efficient.
     *
     * @return a string representation of the object.
     */
    override fun toString(): String {
        val s = StringBuilder()
        this.append(s)
        return s.toString()
    }

    // ----------------------------------------------------------------------
    // Internal constants for high-level option type conversions.
    //
    // formats of options
    //
    enum class OptionFormat {
        INET,  // 4 bytes IP,				size = 4
        INETS,  // list of 4 bytes IP,		size = 4*n
        INT,  // 4 bytes integer,			size = 4
        SHORT,  // 2 bytes short,			size = 2
        SHORTS,  // list of 2 bytes shorts,	size = 2*n
        BYTE,  // 1 byte,					size = 1
        BYTES,  // list of bytes,			size = n
        STRING
        // string,					size = n
        //RELAYS	= 9;	// DHCP sub-options (rfc 3046)
        //ID		= 10;	// client identifier : byte (htype) + string (chaddr)
    }
    /**
     * Constructor for `DHCPOption`.
     *
     *
     * Note: you must not prefix the value by a length-byte. The length prefix
     * will be added automatically by the API.
     *
     *
     * If value is `null` it is considered as an empty option.
     * If you add an empty option to a DHCPPacket, it removes the option from the packet.
     *
     *
     * This constructor adds a parameter to mark the option as "mirror". See comments above.
     *
     * @param code DHCP option code
     * @param value DHCP option value as a byte array.
     */
    /**
     * Constructor for `DHCPOption`. This is the default constructor.
     *
     *
     * Note: you must not prefix the value by a length-byte. The length prefix
     * will be added automatically by the API.
     *
     *
     * If value is `null` it is considered as an empty option.
     * If you add an empty option to a DHCPPacket, it removes the option from the packet.
     *
     * @param code DHCP option code
     * @param value DHCP option value as a byte array.
     */
    init {
        require(code != DHCPConstants.DHO_PAD) { "code=0 is not allowed (reserved for padding" }
        require(code != DHCPConstants.DHO_END) { "code=-1 is not allowed (reserved for End Of Options)" }
        this.code = code
        valueFast = value?.clone()
        isMirror = mirror
    }

    companion object {
        private const val serialVersionUID = 2L
        fun isOptionAsByte(code: Byte): Boolean {
            return OptionFormat.BYTE == _DHO_FORMATS[code]
        }

        /**
         * Creates a DHCP Option as Byte format.
         *
         *
         * This method is only allowed for the following option codes:
         * <pre>
         * DHO_IP_FORWARDING(19)
         * DHO_NON_LOCAL_SOURCE_ROUTING(20)
         * DHO_DEFAULT_IP_TTL(23)
         * DHO_ALL_SUBNETS_LOCAL(27)
         * DHO_PERFORM_MASK_DISCOVERY(29)
         * DHO_MASK_SUPPLIER(30)
         * DHO_ROUTER_DISCOVERY(31)
         * DHO_TRAILER_ENCAPSULATION(34)
         * DHO_IEEE802_3_ENCAPSULATION(36)
         * DHO_DEFAULT_TCP_TTL(37)
         * DHO_TCP_KEEPALIVE_GARBAGE(39)
         * DHO_NETBIOS_NODE_TYPE(46)
         * DHO_DHCP_OPTION_OVERLOAD(52)
         * DHO_DHCP_MESSAGE_TYPE(53)
         * DHO_AUTO_CONFIGURE(116)
        </pre> *
         *
         * @param code the option code.
         * @param val the value
         * @throws IllegalArgumentException the option code is not in the list above.
         */
        fun newOptionAsByte(code: Byte, `val`: Byte): DHCPOption {
            require(isOptionAsByte(code)) { "DHCP option type ($code) is not byte" }
            return DHCPOption(code, byte2Bytes(`val`))
        }

        fun isOptionAsShort(code: Byte): Boolean {
            return OptionFormat.SHORT == _DHO_FORMATS[code]
        }

        fun isOptionAsInt(code: Byte): Boolean {
            return OptionFormat.INT == _DHO_FORMATS[code]
        }

        fun isOptionAsInetAddr(code: Byte): Boolean {
            return OptionFormat.INET == _DHO_FORMATS[code]
        }

        fun isOptionAsString(code: Byte): Boolean {
            return OptionFormat.STRING == _DHO_FORMATS[code]
        }

        fun isOptionAsShorts(code: Byte): Boolean {
            return OptionFormat.SHORTS == _DHO_FORMATS[code]
        }

        fun isOptionAsInetAddrs(code: Byte): Boolean {
            return OptionFormat.INETS == _DHO_FORMATS[code]
        }

        fun isOptionAsBytes(code: Byte): Boolean {
            return OptionFormat.BYTES == _DHO_FORMATS[code]
        }

        /**
         * Creates a DHCP Option as Short format.
         *
         *
         * This method is only allowed for the following option codes:
         * <pre>
         * DHO_BOOT_SIZE(13)
         * DHO_MAX_DGRAM_REASSEMBLY(22)
         * DHO_INTERFACE_MTU(26)
         * DHO_DHCP_MAX_MESSAGE_SIZE(57)
        </pre> *
         *
         * @param code the option code.
         * @param val the value
         * @throws IllegalArgumentException the option code is not in the list above.
         */
        fun newOptionAsShort(code: Byte, `val`: Short): DHCPOption {
            require(isOptionAsShort(code)) { "DHCP option type ($code) is not short" }
            return DHCPOption(code, short2Bytes(`val`))
        }

        /**
         * Creates a DHCP Options as Short[] format.
         *
         *
         * This method is only allowed for the following option codes:
         * <pre>
         * DHO_PATH_MTU_PLATEAU_TABLE(25)
         * DHO_NAME_SERVICE_SEARCH(117)
        </pre> *
         *
         * @param code the option code.
         * @param arr the array of shorts
         * @throws IllegalArgumentException the option code is not in the list above.
         */
        fun newOptionAsShorts(code: Byte, arr: ShortArray?): DHCPOption {
            require(isOptionAsShorts(code)) { "DHCP option type ($code) is not shorts" }
            var buf: ByteArray? = null
            if (arr != null) {
                buf = ByteArray(arr.size * 2)
                for (i in arr.indices) {
                    val `val` = arr[i]
                    buf[i * 2] = (`val`.toInt() and 0xFF00 ushr 8).toByte()
                    buf[i * 2 + 1] = (`val`.toInt() and 0XFF).toByte()
                }
            }
            return DHCPOption(code, buf)
        }

        /**
         * Creates a DHCP Option as Integer format.
         *
         *
         * This method is only allowed for the following option codes:
         * <pre>
         * DHO_TIME_OFFSET(2)
         * DHO_PATH_MTU_AGING_TIMEOUT(24)
         * DHO_ARP_CACHE_TIMEOUT(35)
         * DHO_TCP_KEEPALIVE_INTERVAL(38)
         * DHO_DHCP_LEASE_TIME(51)
         * DHO_DHCP_RENEWAL_TIME(58)
         * DHO_DHCP_REBINDING_TIME(59)
        </pre> *
         *
         * @param code the option code.
         * @param val the value
         * @throws IllegalArgumentException the option code is not in the list above.
         */
        fun newOptionAsInt(code: Byte, `val`: Int): DHCPOption {
            require(isOptionAsInt(code)) { "DHCP option type ($code) is not int" }
            return DHCPOption(code, int2Bytes(`val`))
        }

        /**
         * Sets a DHCP Option as InetAddress format.
         *
         *
         * This method is only allowed for the following option codes:
         * <pre>
         * DHO_SUBNET_MASK(1)
         * DHO_SWAP_SERVER(16)
         * DHO_BROADCAST_ADDRESS(28)
         * DHO_ROUTER_SOLICITATION_ADDRESS(32)
         * DHO_DHCP_REQUESTED_ADDRESS(50)
         * DHO_DHCP_SERVER_IDENTIFIER(54)
         * DHO_SUBNET_SELECTION(118)
        </pre> *
         * and also as a simplified version for setOptionAsInetAddresses
         * <pre>
         * DHO_ROUTERS(3)
         * DHO_TIME_SERVERS(4)
         * DHO_NAME_SERVERS(5)
         * DHO_DOMAIN_NAME_SERVERS(6)
         * DHO_LOG_SERVERS(7)
         * DHO_COOKIE_SERVERS(8)
         * DHO_LPR_SERVERS(9)
         * DHO_IMPRESS_SERVERS(10)
         * DHO_RESOURCE_LOCATION_SERVERS(11)
         * DHO_POLICY_FILTER(21)
         * DHO_STATIC_ROUTES(33)
         * DHO_NIS_SERVERS(41)
         * DHO_NTP_SERVERS(42)
         * DHO_NETBIOS_NAME_SERVERS(44)
         * DHO_NETBIOS_DD_SERVER(45)
         * DHO_FONT_SERVERS(48)
         * DHO_X_DISPLAY_MANAGER(49)
         * DHO_MOBILE_IP_HOME_AGENT(68)
         * DHO_SMTP_SERVER(69)
         * DHO_POP3_SERVER(70)
         * DHO_NNTP_SERVER(71)
         * DHO_WWW_SERVER(72)
         * DHO_FINGER_SERVER(73)
         * DHO_IRC_SERVER(74)
         * DHO_STREETTALK_SERVER(75)
         * DHO_STDA_SERVER(76)
         * DHO_NDS_SERVERS(85)
        </pre> *
         *
         * @param code the option code.
         * @param val the value
         * @throws IllegalArgumentException the option code is not in the list above.
         */
        fun newOptionAsInetAddress(code: Byte, `val`: InetAddress?): DHCPOption {
            require(!(!isOptionAsInetAddr(code) &&
                    !isOptionAsInetAddrs(code))) { "DHCP option type ($code) is not InetAddress" }
            return DHCPOption(code, inetAddress2Bytes(`val`))
        }

        /**
         * Creates a DHCP Option as InetAddress array format.
         *
         *
         * This method is only allowed for the following option codes:
         * <pre>
         * DHO_ROUTERS(3)
         * DHO_TIME_SERVERS(4)
         * DHO_NAME_SERVERS(5)
         * DHO_DOMAIN_NAME_SERVERS(6)
         * DHO_LOG_SERVERS(7)
         * DHO_COOKIE_SERVERS(8)
         * DHO_LPR_SERVERS(9)
         * DHO_IMPRESS_SERVERS(10)
         * DHO_RESOURCE_LOCATION_SERVERS(11)
         * DHO_POLICY_FILTER(21)
         * DHO_STATIC_ROUTES(33)
         * DHO_NIS_SERVERS(41)
         * DHO_NTP_SERVERS(42)
         * DHO_NETBIOS_NAME_SERVERS(44)
         * DHO_NETBIOS_DD_SERVER(45)
         * DHO_FONT_SERVERS(48)
         * DHO_X_DISPLAY_MANAGER(49)
         * DHO_MOBILE_IP_HOME_AGENT(68)
         * DHO_SMTP_SERVER(69)
         * DHO_POP3_SERVER(70)
         * DHO_NNTP_SERVER(71)
         * DHO_WWW_SERVER(72)
         * DHO_FINGER_SERVER(73)
         * DHO_IRC_SERVER(74)
         * DHO_STREETTALK_SERVER(75)
         * DHO_STDA_SERVER(76)
         * DHO_NDS_SERVERS(85)
        </pre> *
         *
         * @param code the option code.
         * @param val the value array
         * @throws IllegalArgumentException the option code is not in the list above.
         */
        fun newOptionAsInetAddresses(code: Byte, `val`: Array<InetAddress?>?): DHCPOption {
            require(isOptionAsInetAddrs(code)) { "DHCP option type ($code) is not InetAddresses" }
            return DHCPOption(code, inetAddresses2Bytes(`val`))
        }

        /**
         * Creates a DHCP Option as String format.
         *
         *
         * This method is only allowed for the following option codes:
         * <pre>
         * DHO_HOST_NAME(12)
         * DHO_MERIT_DUMP(14)
         * DHO_DOMAIN_NAME(15)
         * DHO_ROOT_PATH(17)
         * DHO_EXTENSIONS_PATH(18)
         * DHO_NETBIOS_SCOPE(47)
         * DHO_DHCP_MESSAGE(56)
         * DHO_VENDOR_CLASS_IDENTIFIER(60)
         * DHO_NWIP_DOMAIN_NAME(62)
         * DHO_NIS_DOMAIN(64)
         * DHO_NIS_SERVER(65)
         * DHO_TFTP_SERVER(66)
         * DHO_BOOTFILE(67)
         * DHO_NDS_TREE_NAME(86)
         * DHO_USER_AUTHENTICATION_PROTOCOL(98)
        </pre> *
         *
         * @param code the option code.
         * @param val the value
         * @throws IllegalArgumentException the option code is not in the list above.
         */
        fun newOptionAsString(code: Byte, `val`: String?): DHCPOption {
            require(isOptionAsString(code)) { "DHCP option type ($code) is not string" }
            return DHCPOption(code, DHCPPacket.stringToBytes(`val`))
        }

        /**
         * Convert unsigned byte to int
         */
        private fun unsignedByte(b: Byte): Int {
            return b.toInt() and 0xFF
        }

        /**************************************************************************
         *
         * Type converters.
         *
         */
        fun byte2Bytes(`val`: Byte): ByteArray {
            return byteArrayOf(`val`)
        }

        fun short2Bytes(`val`: Short): ByteArray {
            return byteArrayOf((`val`.toInt() and 0xFF00 ushr 8).toByte(), (`val`.toInt() and 0XFF).toByte())
        }

        fun int2Bytes(`val`: Int): ByteArray {
            return byteArrayOf((`val` and -0x1000000 ushr 24).toByte(), (`val` and 0X00FF0000 ushr 16).toByte(), (`val` and 0x0000FF00 ushr 8).toByte(), (`val` and 0x000000FF).toByte())
        }

        fun inetAddress2Bytes(`val`: InetAddress?): ByteArray? {
            if (`val` == null) {
                return null
            }
            require(`val` is Inet4Address) { "Adress must be of subclass Inet4Address" }
            return `val`.getAddress()
        }

        fun inetAddresses2Bytes(`val`: Array<InetAddress?>?): ByteArray? {
            if (`val` == null) {
                return null
            }
            val buf = ByteArray(`val`.size * 4)
            for (i in `val`.indices) {
                val addr = `val`[i]
                require(addr is Inet4Address) { "Adress must be of subclass Inet4Address" }
                System.arraycopy(addr.getAddress(), 0, buf, i * 4, 4)
            }
            return buf
        }

        /**
         * Convert DHO_USER_CLASS (77) option to a List.
         *
         * @param buf option value of type User Class.
         * @return List of String values.
         */
        fun userClassToList(buf: ByteArray?): List<String>? {
            if (buf == null) {
                return null
            }
            val list = LinkedList<String>()
            var i = 0
            while (i < buf.size) {
                var size = unsignedByte(buf[i++])
                val instock = buf.size - i
                if (size > instock) {
                    size = instock
                }
                list.add(DHCPPacket.bytesToString(buf, i, size))
                i += size
            }
            return list
        }

        /**
         * Converts DHO_USER_CLASS (77) option to a printable string
         *
         * @param buf option value of type User Class.
         * @return printable string.
         */
        fun userClassToString(buf: ByteArray?): String? {
            if (buf == null) {
                return null
            }
            val list = userClassToList(buf)
            val it = list!!.iterator()
            val s = StringBuffer()
            while (it.hasNext()) {
                s.append('"').append(it.next() as String?).append('"')
                if (it.hasNext()) {
                    s.append(',')
                }
            }
            return s.toString()
        }

        /**
         * Converts this list of strings to a DHO_USER_CLASS (77) option.
         *
         * @param list the list of strings
         * @return byte[] buffer to use with `setOptionRaw`, `null` if list is null
         * @throws IllegalArgumentException if List contains anything else than String
         */
        fun stringListToUserClass(list: List<String?>?): ByteArray? {
            if (list == null) {
                return null
            }
            val buf = ByteArrayOutputStream(32)
            val out = DataOutputStream(buf)
            return try {
                for (s in list) {
                    val bytes = DHCPPacket.stringToBytes(s)
                    var size = bytes!!.size
                    if (size > 255) {
                        size = 255
                    }
                    out.writeByte(size)
                    out.write(bytes, 0, size)
                }
                buf.toByteArray()
            } catch (e: IOException) {
                Timber.e(e, "Unexpected IOException")
                buf.toByteArray()
            }
        }

        /**
         * Converts DHO_DHCP_AGENT_OPTIONS (82) option type to a printable string
         *
         * @param buf option value of type Agent Option.
         * @return printable string.
         */
        fun agentOptionsToString(buf: ByteArray?): String? {
            if (buf == null) {
                return null
            }
            val map = agentOptionsToMap(buf)
            val s = StringBuffer()
            for ((key, value) in map!!) {
                s.append('{').append(unsignedByte(key)).append("}\"")
                s.append(value).append('\"')
                s.append(',')
            }
            if (s.length > 0) {
                s.setLength(s.length - 1)
            }
            return s.toString()
        }

        /**
         * Converts Map<Byte></Byte>,String> to DHO_DHCP_AGENT_OPTIONS (82) option.
         *
         *
         * LinkedHashMap are preferred as they preserve insertion order. Regular
         * HashMap order is randon.
         *
         * @param map Map<Byte></Byte>,String> couples
         * @return byte[] buffer to use with `setOptionRaw`
         * @throws IllegalArgumentException if List contains anything else than String
         */
        fun agentOptionToRaw(map: Map<Byte, String?>?): ByteArray? {
            if (map == null) {
                return null
            }
            val buf = ByteArrayOutputStream(64)
            val out = DataOutputStream(buf)
            return try {
                for ((key, value) in map) {
                    val bufTemp = DHCPPacket.stringToBytes(value)
                    val size = bufTemp!!.size
                    assert(size >= 0)
                    require(size <= 255) { "Value size is greater then 255 bytes" }
                    out.writeByte(key.toInt())
                    out.writeByte(size)
                    out.write(bufTemp, 0, size)
                }
                buf.toByteArray()
            } catch (e: IOException) {
                Timber.e(e, "Unexpected IOException")
                buf.toByteArray()
            }
        }

        /**
         * Converts DHO_DHCP_AGENT_OPTIONS (82) option type to a LinkedMap.
         *
         *
         * Order of parameters is preserved (use avc `LinkedHashmap<).
         * Keys are of type `Byte`, values are of type `String`.
         *
         * @param buf byte[] buffer returned by `getOptionRaw
         * @return the LinkedHashmap of values, `null` if buf is `null`
         */
        fun agentOptionsToMap(buf: ByteArray?): Map<Byte, String>? {
            if (buf == null) {
                return null
            }
            val map = LinkedHashMap<Byte, String>()
            var i = 0
            while (i < buf.size) {
                if (buf.size - i < 2) {
                    break // not enough data left
                }
                val key = buf[i++]
                var size = unsignedByte(buf[i++])
                val instock = buf.size - i
                if (size > instock) {
                    size = instock
                }
                map[key] = DHCPPacket.bytesToString(buf, i, size)
                i += size
            }
            return map
        }

        /**
         * Returns the type of the option based on the option code.
         *
         *
         * The type is returned as a `Class` object:
         *
         *  * `InetAddress.class`
         *  * `InetAddress[].class`
         *  * `int.class`
         *  * `short.class`
         *  * `short[].class`
         *  * `byte.class`
         *  * `byte[].class`
         *  * `String.class`
         *
         *
         *
         * Please use `getSimpleName()` methode of `Class` object for the String representation.
         *
         * @param code the DHCP option code
         * @return the Class object representing accepted types
         */
        fun getOptionFormat(code: Byte): Class<*>? {
            val format = _DHO_FORMATS[code] ?: return null
            return when (format) {
                OptionFormat.INET -> InetAddress::class.java
                OptionFormat.INETS -> Array<InetAddress>::class.java
                OptionFormat.INT -> Int::class.javaPrimitiveType
                OptionFormat.SHORT -> Short::class.javaPrimitiveType
                OptionFormat.SHORTS -> ShortArray::class.java
                OptionFormat.BYTE -> Byte::class.javaPrimitiveType
                OptionFormat.BYTES -> ByteArray::class.java
                OptionFormat.STRING -> String::class.java
                else -> null
            }
        }

        /**
         * Simple method for converting from string to supported class format.
         *
         *
         * Support values are:
         *
         *  * InetAddress, inet
         *  * InetAddress[], inets
         *  * int
         *  * short
         *  * short[], shorts
         *  * byte
         *  * byte[], bytes
         *  * String, string
         *
         *
         * @param className name of the data format (see above)
         * @return `Class` or `null` if not supported
         */
        fun string2Class(className: String): Class<*>? {
            if ("InetAddress" == className) return InetAddress::class.java
            if ("inet" == className) return InetAddress::class.java
            if ("InetAddress[]" == className) return Array<InetAddress>::class.java
            if ("inets" == className) return Array<InetAddress>::class.java
            if ("int" == className) return Int::class.javaPrimitiveType
            if ("short" == className) return Short::class.javaPrimitiveType
            if ("short[]" == className) return ShortArray::class.java
            if ("shorts" == className) return ShortArray::class.java
            if ("byte" == className) return Byte::class.javaPrimitiveType
            if ("byte[]" == className) return ByteArray::class.java
            if ("bytes" == className) return ByteArray::class.java
            if ("String" == className) return String::class.java
            return if ("string" == className) String::class.java else null
        }

        /**
         * Parse an option from a pure string representation.
         *
         * <P>The expected class is passed as a parameter, and can be provided by the
         * `string2Class()` method from a string representation of the class.
         *
        </P> * <P>TODO examples
         *
         * @param code DHCP option code
         * @param format expected Java Class after conversion
         * @param value string representation of the value
         * @return the DHCPOption object
        </P> */
        fun parseNewOption(code: Byte, format: Class<*>?, value_: String?): DHCPOption? {
            var value = value_
            if (format == null || value == null) {
                throw NullPointerException()
            }
            if (Short::class.javaPrimitiveType == format) {                                // short
                return newOptionAsShort(code, value.toInt().toShort())
            } else if (ShortArray::class.java == format) {                    // short[]
                val listVal = value.split(" ")
                val listShort = ShortArray(listVal.size)
                for (i in listVal.indices) {
                    listShort[i] = listVal[i].toInt().toShort()
                }
                return newOptionAsShorts(code, listShort)
            } else if (Int::class.javaPrimitiveType == format) {                        // int
                return newOptionAsInt(code, value.toInt())
            } else if (String::class.java == format) {                        // String
                return newOptionAsString(code, value)
            } else if (Byte::class.javaPrimitiveType == format) {                        // byte
                return newOptionAsByte(code, value.toInt().toByte())
                // TODO be explicit about BYTE allowed from -128 to 255 (unsigned int support)
            } else if (ByteArray::class.java == format) {                        // byte[]
                value = value.replace(".", " ")
                val listVal = value.split(" ")
                val listBytes = ByteArray(listVal.size)
                for (i in listVal.indices) {
                    listBytes[i] = listVal[i].toInt().toByte()
                }
                return DHCPOption(code, listBytes)
            } else if (InetAddress::class.java == format) {                    // InetAddress
                return try {
                    newOptionAsInetAddress(code, InetAddress.getByName(value))
                } catch (e: UnknownHostException) {
                    Timber.e(e, "Invalid address:%s", value)
                    null
                }
            } else if (Array<InetAddress>::class.java == format) {                // InetAddress[]
                val listVal = value.split(" ")
                val listInet = arrayOfNulls<InetAddress>(listVal.size)
                try {
                    for (i in listVal.indices) {
                        listInet[i] = InetAddress.getByName(listVal[i])
                    }
                } catch (e: UnknownHostException) {
                    Timber.e(e, "Invalid address")
                    return null
                }
                return newOptionAsInetAddresses(code, listInet)
            }
            return null
        }

        //
        // list of formats by options
        //
        private val _OPTION_FORMATS = arrayOf<Any>(
                DHCPConstants.DHO_SUBNET_MASK, OptionFormat.INET,
                DHCPConstants.DHO_TIME_OFFSET, OptionFormat.INT,
                DHCPConstants.DHO_ROUTERS, OptionFormat.INETS,
                DHCPConstants.DHO_TIME_SERVERS, OptionFormat.INETS,
                DHCPConstants.DHO_NAME_SERVERS, OptionFormat.INETS,
                DHCPConstants.DHO_DOMAIN_NAME_SERVERS, OptionFormat.INETS,
                DHCPConstants.DHO_LOG_SERVERS, OptionFormat.INETS,
                DHCPConstants.DHO_COOKIE_SERVERS, OptionFormat.INETS,
                DHCPConstants.DHO_LPR_SERVERS, OptionFormat.INETS,
                DHCPConstants.DHO_IMPRESS_SERVERS, OptionFormat.INETS,
                DHCPConstants.DHO_RESOURCE_LOCATION_SERVERS, OptionFormat.INETS,
                DHCPConstants.DHO_HOST_NAME, OptionFormat.STRING,
                DHCPConstants.DHO_BOOT_SIZE, OptionFormat.SHORT,
                DHCPConstants.DHO_MERIT_DUMP, OptionFormat.STRING,
                DHCPConstants.DHO_DOMAIN_NAME, OptionFormat.STRING,
                DHCPConstants.DHO_SWAP_SERVER, OptionFormat.INET,
                DHCPConstants.DHO_ROOT_PATH, OptionFormat.STRING,
                DHCPConstants.DHO_EXTENSIONS_PATH, OptionFormat.STRING,
                DHCPConstants.DHO_IP_FORWARDING, OptionFormat.BYTE,
                DHCPConstants.DHO_NON_LOCAL_SOURCE_ROUTING, OptionFormat.BYTE,
                DHCPConstants.DHO_POLICY_FILTER, OptionFormat.INETS,
                DHCPConstants.DHO_MAX_DGRAM_REASSEMBLY, OptionFormat.SHORT,
                DHCPConstants.DHO_DEFAULT_IP_TTL, OptionFormat.BYTE,
                DHCPConstants.DHO_PATH_MTU_AGING_TIMEOUT, OptionFormat.INT,
                DHCPConstants.DHO_PATH_MTU_PLATEAU_TABLE, OptionFormat.SHORTS,
                DHCPConstants.DHO_INTERFACE_MTU, OptionFormat.SHORT,
                DHCPConstants.DHO_ALL_SUBNETS_LOCAL, OptionFormat.BYTE,
                DHCPConstants.DHO_BROADCAST_ADDRESS, OptionFormat.INET,
                DHCPConstants.DHO_PERFORM_MASK_DISCOVERY, OptionFormat.BYTE,
                DHCPConstants.DHO_MASK_SUPPLIER, OptionFormat.BYTE,
                DHCPConstants.DHO_ROUTER_DISCOVERY, OptionFormat.BYTE,
                DHCPConstants.DHO_ROUTER_SOLICITATION_ADDRESS, OptionFormat.INET,
                DHCPConstants.DHO_STATIC_ROUTES, OptionFormat.INETS,
                DHCPConstants.DHO_TRAILER_ENCAPSULATION, OptionFormat.BYTE,
                DHCPConstants.DHO_ARP_CACHE_TIMEOUT, OptionFormat.INT,
                DHCPConstants.DHO_IEEE802_3_ENCAPSULATION, OptionFormat.BYTE,
                DHCPConstants.DHO_DEFAULT_TCP_TTL, OptionFormat.BYTE,
                DHCPConstants.DHO_TCP_KEEPALIVE_INTERVAL, OptionFormat.INT,
                DHCPConstants.DHO_TCP_KEEPALIVE_GARBAGE, OptionFormat.BYTE,
                DHCPConstants.DHO_NIS_SERVERS, OptionFormat.INETS,
                DHCPConstants.DHO_NTP_SERVERS, OptionFormat.INETS,
                DHCPConstants.DHO_NETBIOS_NAME_SERVERS, OptionFormat.INETS,
                DHCPConstants.DHO_NETBIOS_DD_SERVER, OptionFormat.INETS,
                DHCPConstants.DHO_NETBIOS_NODE_TYPE, OptionFormat.BYTE,
                DHCPConstants.DHO_NETBIOS_SCOPE, OptionFormat.STRING,
                DHCPConstants.DHO_FONT_SERVERS, OptionFormat.INETS,
                DHCPConstants.DHO_X_DISPLAY_MANAGER, OptionFormat.INETS,
                DHCPConstants.DHO_DHCP_REQUESTED_ADDRESS, OptionFormat.INET,
                DHCPConstants.DHO_DHCP_LEASE_TIME, OptionFormat.INT,
                DHCPConstants.DHO_DHCP_OPTION_OVERLOAD, OptionFormat.BYTE,
                DHCPConstants.DHO_DHCP_MESSAGE_TYPE, OptionFormat.BYTE,
                DHCPConstants.DHO_DHCP_SERVER_IDENTIFIER, OptionFormat.INET,
                DHCPConstants.DHO_DHCP_PARAMETER_REQUEST_LIST, OptionFormat.BYTES,
                DHCPConstants.DHO_DHCP_MESSAGE, OptionFormat.STRING,
                DHCPConstants.DHO_DHCP_MAX_MESSAGE_SIZE, OptionFormat.SHORT,
                DHCPConstants.DHO_DHCP_RENEWAL_TIME, OptionFormat.INT,
                DHCPConstants.DHO_DHCP_REBINDING_TIME, OptionFormat.INT,
                DHCPConstants.DHO_VENDOR_CLASS_IDENTIFIER, OptionFormat.STRING,
                DHCPConstants.DHO_NWIP_DOMAIN_NAME, OptionFormat.STRING,
                DHCPConstants.DHO_NISPLUS_DOMAIN, OptionFormat.STRING,
                DHCPConstants.DHO_NISPLUS_SERVER, OptionFormat.STRING,
                DHCPConstants.DHO_TFTP_SERVER, OptionFormat.STRING,
                DHCPConstants.DHO_BOOTFILE, OptionFormat.STRING,
                DHCPConstants.DHO_MOBILE_IP_HOME_AGENT, OptionFormat.INETS,
                DHCPConstants.DHO_SMTP_SERVER, OptionFormat.INETS,
                DHCPConstants.DHO_POP3_SERVER, OptionFormat.INETS,
                DHCPConstants.DHO_NNTP_SERVER, OptionFormat.INETS,
                DHCPConstants.DHO_WWW_SERVER, OptionFormat.INETS,
                DHCPConstants.DHO_FINGER_SERVER, OptionFormat.INETS,
                DHCPConstants.DHO_IRC_SERVER, OptionFormat.INETS,
                DHCPConstants.DHO_STREETTALK_SERVER, OptionFormat.INETS,
                DHCPConstants.DHO_STDA_SERVER, OptionFormat.INETS,
                DHCPConstants.DHO_NDS_SERVERS, OptionFormat.INETS,
                DHCPConstants.DHO_NDS_TREE_NAME, OptionFormat.STRING,
                DHCPConstants.DHO_NDS_CONTEXT, OptionFormat.STRING,
                DHCPConstants.DHO_CLIENT_LAST_TRANSACTION_TIME, OptionFormat.INT,
                DHCPConstants.DHO_ASSOCIATED_IP, OptionFormat.INETS,
                DHCPConstants.DHO_USER_AUTHENTICATION_PROTOCOL, OptionFormat.STRING,
                DHCPConstants.DHO_AUTO_CONFIGURE, OptionFormat.BYTE,
                DHCPConstants.DHO_NAME_SERVICE_SEARCH, OptionFormat.SHORTS,
                DHCPConstants.DHO_SUBNET_SELECTION, OptionFormat.INET,
                DHCPConstants.DHO_DOMAIN_SEARCH, OptionFormat.STRING)
        val _DHO_FORMATS = LinkedHashMap<Byte, OptionFormat>()

        /*
     * preload at startup Maps with constants
     * allowing reverse lookup
     */
        init {
            // construct map of formats
            for (i in 0 until _OPTION_FORMATS.size / 2) {
                _DHO_FORMATS[_OPTION_FORMATS[i * 2] as Byte] = _OPTION_FORMATS[i * 2 + 1] as OptionFormat
            }
        }

        // ========================================================================
        // main: print DHCP options for Javadoc
        @JvmStatic
        fun main(args: Array<String>) {
            var all = ""
            var inet1 = ""
            var inets = ""
            var int1 = ""
            var short1 = ""
            var shorts = ""
            var byte1 = ""
            var bytes = ""
            var string1 = ""
            for (codeByte in DHCPConstants.dhoNamesMap!!.keys) {
                var s = ""
                if (codeByte != DHCPConstants.DHO_PAD && codeByte != DHCPConstants.DHO_END) {
                    s = " * ${DHCPConstants.dhoNamesMap!![codeByte]}(${codeByte.toInt() and 0xFF})\n"
                }
                all += s
                if (_DHO_FORMATS.containsKey(codeByte)) {
                    when (_DHO_FORMATS[codeByte]) {
                        OptionFormat.INET -> inet1 += s
                        OptionFormat.INETS -> inets += s
                        OptionFormat.INT -> int1 += s
                        OptionFormat.SHORT -> short1 += s
                        OptionFormat.SHORTS -> shorts += s
                        OptionFormat.BYTE -> byte1 += s
                        OptionFormat.BYTES -> bytes += s
                        OptionFormat.STRING -> string1 += s
                        else -> {}
                    }
                }
            }
            println("---All codes---")
            println(all)
            println("---INET---")
            println(inet1)
            println("---INETS---")
            println(inets)
            println("---INT---")
            println(int1)
            println("---SHORT---")
            println(short1)
            println("---SHORTS---")
            println(shorts)
            println("---BYTE---")
            println(byte1)
            println("---BYTES---")
            println(bytes)
            println("---STRING---")
            println(string1)
        }
    }
}