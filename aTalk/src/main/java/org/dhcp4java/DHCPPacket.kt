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

import org.atalk.hmos.plugin.timberlog.TimberLog
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.Serializable
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.util.*

/**
 * The basic class for manipulating DHCP packets.
 *
 * @author Stephan Hadinger
 * @version 1.00
 *
 *
 * There are two basic ways to build a new DHCPPacket object.
 *
 * First one is to build an object from scratch using the constructor and setters.
 * If you need to set repeatedly the same set of parameters and options,
 * you can create a "master" object and clone it many times.
 *
 * <pre>
 * DHCPPacket discover = new DHCPPacket();
 * discover.setOp(DHCPPacket.BOOTREQUEST);
 * discover.setHtype(DHCPPacket.HTYPE_ETHER);
 * discover.setHlen((byte) 6);
 * discover.setHops((byte) 0);
 * discover.setXid( (new Random()).nextInt() );
 * ...
</pre> *
 * Second is to decode a DHCP datagram received from the network.
 * In this case, the object is created through a factory.
 *
 *
 * Example: simple DHCP sniffer
 * <pre>
 * DatagramSocket socket = new DatagramSocket(67);
 * while (true) {
 * DatagramPacket pac = new DatagramPacket(new byte[1500], 1500);
 * socket.receive(pac);
 * DHCPPacket dhcp = DHCPPacket.getPacket(pac);
 * System.out.println(dhcp.toString());
 * }
</pre> *
 * In this second way, beware that a `BadPacketExpcetion` is thrown
 * if the datagram contains invalid DHCP data.
 *
 *
 *
 * **Getters and Setters**: methods are provided with high-level data structures
 * wherever it is possible (String, InetAddress...). However there are also low-overhead
 * version (suffix `Raw`) dealing directly with `byte[]` for maximum performance.
 * They are useful in servers for copying parameters in a servers from a request to a response without
 * any type conversion. All parameters are copies, you may modify them as you like without
 * any side-effect on the `DHCPPacket` object.
 *
 * <h4>DHCP datagram format description:</h4>
 * <blockquote><table cellspacing=2>
 * <tr><th>Field</th><th>Octets</th><th>Description</th></tr>
 * <tr><td valign=top>`op`</td><td valign=top>1</td>
 * <td>Message op code / message type.<br></br>
 * use constants
 * `BOOTREQUEST`,
 * `BOOTREPLY`</td></tr>
 * <tr><td valign=top>`htype`</td>
 * <td valign=top>1</td><td>Hardware address type, see ARP section in
 * "Assigned Numbers" RFC<br></br>
 * use constants
 * `HTYPE_ETHER`,
 * `HTYPE_IEEE802`,
 * `HTYPE_FDDI`</td></tr>
 * <tr><td valign=top>`hlen`</td><td>1</td><td>Hardware address length
 * (e.g.  '6' for ethernet).</td></tr>
 * <tr><td valign=top>`hops`</td><td valign=top>1</td><td>Client sets to zero, optionally used
 * by relay agents when booting via a relay agent.</td></tr>
 * <tr><td valign=top>`xid`</td><td valign=top>4</td>
 * <td>Transaction ID, a random number chosen by the
 * client, used by the client and server to associate
 * messages and responses between a client and a
 * server.</td></tr>
 * <tr><td valign=top>`secs`</td><td valign=top>2</td>
 * <td>Filled in by client, seconds elapsed since client
 * began address acquisition or renewal process.</td></tr>
 * <tr><td valign=top>`flags`</td><td valign=top>2</td>
 * <td>Flags (see below).</td></tr>
 * <tr><td valign=top>`ciaddr`</td><td valign=top>4</td>
 * <td>Client IP address; only filled in if client is in
 * BOUND, RENEW or REBINDING state and can respond
 * to ARP requests.</td></tr>
 * <tr><td valign=top>`yiaddr`</td><td valign=top>4</td>
 * <td>'your' (client) IP address.</td></tr>
 * <tr><td valign=top>`siaddr`</td><td valign=top>4</td>
 * <td>IP address of next server to use in bootstrap;
 * returned in DHCPOFFER, DHCPACK by server.</td></tr>
 * <tr><td valign=top>`giaddr`</td><td valign=top>4</td>
 * <td>Relay agent IP address, used in booting via a
 * relay agent.</td></tr>
 * <tr><td valign=top>`chaddr`</td><td valign=top>16</td>
 * <td>Client hardware address.</td></tr>
 * <tr><td valign=top>`sname`</td><td valign=top>64</td>
 * <td>Optional server host name, null terminated string.</td></tr>
 * <tr><td valign=top>`file`</td><td valign=top>128</td>
 * <td>Boot file name, null terminated string; "generic"
 * name or null in DHCPDISCOVER, fully qualified
 * directory-path name in DHCPOFFER.</td></tr>
 * <tr><td valign=top>`isDhcp`</td><td valign=top>4</td>
 * <td>Controls whether the packet is BOOTP or DHCP.
 * DHCP contains the "magic cookie" of 4 bytes.
 * 0x63 0x82 0x53 0x63.</td></tr>
 * <tr><td valign=top>`DHO_*code*`</td><td valign=top>*</td>
 * <td>Optional parameters field.  See the options
 * documents for a list of defined options. See below.</td></tr>
 * <tr><td valign=top>`padding`</td><td valign=top>*</td>
 * <td>Optional padding at the end of the packet.</td></tr>
</table></blockquote> *
 *
 * <h4>DHCP Option</h4>
 *
 * The following options are codes are supported:
 * <pre>
 * DHO_SUBNET_MASK(1)
 * DHO_TIME_OFFSET(2)
 * DHO_ROUTERS(3)
 * DHO_TIME_SERVERS(4)
 * DHO_NAME_SERVERS(5)
 * DHO_DOMAIN_NAME_SERVERS(6)
 * DHO_LOG_SERVERS(7)
 * DHO_COOKIE_SERVERS(8)
 * DHO_LPR_SERVERS(9)
 * DHO_IMPRESS_SERVERS(10)
 * DHO_RESOURCE_LOCATION_SERVERS(11)
 * DHO_HOST_NAME(12)
 * DHO_BOOT_SIZE(13)
 * DHO_MERIT_DUMP(14)
 * DHO_DOMAIN_NAME(15)
 * DHO_SWAP_SERVER(16)
 * DHO_ROOT_PATH(17)
 * DHO_EXTENSIONS_PATH(18)
 * DHO_IP_FORWARDING(19)
 * DHO_NON_LOCAL_SOURCE_ROUTING(20)
 * DHO_POLICY_FILTER(21)
 * DHO_MAX_DGRAM_REASSEMBLY(22)
 * DHO_DEFAULT_IP_TTL(23)
 * DHO_PATH_MTU_AGING_TIMEOUT(24)
 * DHO_PATH_MTU_PLATEAU_TABLE(25)
 * DHO_INTERFACE_MTU(26)
 * DHO_ALL_SUBNETS_LOCAL(27)
 * DHO_BROADCAST_ADDRESS(28)
 * DHO_PERFORM_MASK_DISCOVERY(29)
 * DHO_MASK_SUPPLIER(30)
 * DHO_ROUTER_DISCOVERY(31)
 * DHO_ROUTER_SOLICITATION_ADDRESS(32)
 * DHO_STATIC_ROUTES(33)
 * DHO_TRAILER_ENCAPSULATION(34)
 * DHO_ARP_CACHE_TIMEOUT(35)
 * DHO_IEEE802_3_ENCAPSULATION(36)
 * DHO_DEFAULT_TCP_TTL(37)
 * DHO_TCP_KEEPALIVE_INTERVAL(38)
 * DHO_TCP_KEEPALIVE_GARBAGE(39)
 * DHO_NIS_SERVERS(41)
 * DHO_NTP_SERVERS(42)
 * DHO_VENDOR_ENCAPSULATED_OPTIONS(43)
 * DHO_NETBIOS_NAME_SERVERS(44)
 * DHO_NETBIOS_DD_SERVER(45)
 * DHO_NETBIOS_NODE_TYPE(46)
 * DHO_NETBIOS_SCOPE(47)
 * DHO_FONT_SERVERS(48)
 * DHO_X_DISPLAY_MANAGER(49)
 * DHO_DHCP_REQUESTED_ADDRESS(50)
 * DHO_DHCP_LEASE_TIME(51)
 * DHO_DHCP_OPTION_OVERLOAD(52)
 * DHO_DHCP_MESSAGE_TYPE(53)
 * DHO_DHCP_SERVER_IDENTIFIER(54)
 * DHO_DHCP_PARAMETER_REQUEST_LIST(55)
 * DHO_DHCP_MESSAGE(56)
 * DHO_DHCP_MAX_MESSAGE_SIZE(57)
 * DHO_DHCP_RENEWAL_TIME(58)
 * DHO_DHCP_REBINDING_TIME(59)
 * DHO_VENDOR_CLASS_IDENTIFIER(60)
 * DHO_DHCP_CLIENT_IDENTIFIER(61)
 * DHO_NWIP_DOMAIN_NAME(62)
 * DHO_NWIP_SUBOPTIONS(63)
 * DHO_NIS_DOMAIN(64)
 * DHO_NIS_SERVER(65)
 * DHO_TFTP_SERVER(66)
 * DHO_BOOTFILE(67)
 * DHO_MOBILE_IP_HOME_AGENT(68)
 * DHO_SMTP_SERVER(69)
 * DHO_POP3_SERVER(70)
 * DHO_NNTP_SERVER(71)
 * DHO_WWW_SERVER(72)
 * DHO_FINGER_SERVER(73)
 * DHO_IRC_SERVER(74)
 * DHO_STREETTALK_SERVER(75)
 * DHO_STDA_SERVER(76)
 * DHO_USER_CLASS(77)
 * DHO_FQDN(81)
 * DHO_DHCP_AGENT_OPTIONS(82)
 * DHO_NDS_SERVERS(85)
 * DHO_NDS_TREE_NAME(86)
 * DHO_USER_AUTHENTICATION_PROTOCOL(98)
 * DHO_AUTO_CONFIGURE(116)
 * DHO_NAME_SERVICE_SEARCH(117)
 * DHO_SUBNET_SELECTION(118)
</pre> *
 *
 *
 * These options can be set and get through basic low-level `getOptionRaw` and
 * `setOptionRaw` passing `byte[]` structures. Using these functions, data formats
 * are under your responsibility. Arrays are always passed by copies (clones) so you can modify
 * them freely without side-effects. These functions allow maximum performance, especially
 * when copying options from a request datagram to a response datagram.
 *
 * <h4>Special case: DHO_DHCP_MESSAGE_TYPE</h4>
 * The DHCP Message Type (option 53) is supported for the following values
 * <pre>
 * DHCPDISCOVER(1)
 * DHCPOFFER(2)
 * DHCPREQUEST(3)
 * DHCPDECLINE(4)
 * DHCPACK(5)
 * DHCPNAK(6)
 * DHCPRELEASE(7)
 * DHCPINFORM(8)
 * DHCPFORCERENEW(9)
 * DHCPLEASEQUERY(13)
</pre> *
 *
 * <h4>DHCP option formats</h4>
 *
 * A limited set of higher level data-structures are supported. Type checking is enforced
 * according to rfc 2132. Check corresponding methods for a list of option codes allowed for
 * each datatype.
 *
 * <blockquote>
 * <br></br>Inet (4 bytes - IPv4 address)
 * <br></br>Inets (X*4 bytes - list of IPv4 addresses)
 * <br></br>Short (2 bytes - short)
 * <br></br>Shorts (X*2 bytes - list of shorts)
 * <br></br>Byte (1 byte)
 * <br></br>Bytes (X bytes - list of 1 byte parameters)
 * <br></br>String (X bytes - ASCII string)
 * <br></br>
</blockquote> *
 *
 *
 *
 * **Note**: this class is not synchronized for maximum performance.
 * However, it is unlikely that the same `DHCPPacket` is used in two different
 * threads in real life DHPC servers or clients. Multi-threading acces
 * to an instance of this class is at your own risk.
 *
 *
 * **Limitations**: this class doesn't support spanned options or options longer than 256 bytes.
 * It does not support options stored in `sname` or `file` fields.
 *
 *
 * This API is originally a port from my PERL
 * `[Net::DHCP](http://search.cpan.org/~shadinger/)` api.
 *
 *
 * **Future extensions**: IPv6 support, extended data structure TODO...
 */
class DHCPPacket : Cloneable, Serializable {
    /**
     * Returns the comment associated to this packet.
     *
     *
     * This field can be used freely and has no influence on the real network datagram.
     * It can be used to store a transaction number or any other information
     *
     * @return the _comment field.
     */
    /**
     * Sets the comment associated to this packet.
     *
     *
     * This field can be used freely and has no influence on the real network datagram.
     * It can be used to store a transaction number or any other information
     *
     * @param comment The comment to set.
     */
    // ----------------------------------------------------------------------
    // user defined comment
    // Free user-defined comment
    var comment = ""
    /**
     * Returns the op field (Message op code).
     *
     *
     * Predefined values are:
     * <pre>
     * BOOTREQUEST (1)
     * BOOTREPLY (2)
    </pre> *
     *
     * @return the op field.
     */
    /**
     * Sets the op field (Message op code).
     *
     *
     * Predefined values are:
     * <pre>
     * BOOTREQUEST (1)
     * BOOTREPLY (2)
    </pre> *
     *
     *
     * Default value is `BOOTREPLY`, suitable for server replies.
     *
     * @param op The op to set.
     */
    // ----------------------------------------------------------------------
    // static structure of the packet
    // Op code
    var op: Byte
    /**
     * Returns the htype field (Hardware address length).
     *
     *
     * Predefined values are:
     * <pre>
     * HTYPE_ETHER (1)
     * HTYPE_IEEE802 (6)
     * HTYPE_FDDI (8)
    </pre> *
     *
     *
     * Typical value is `HTYPE_ETHER`.
     *
     * @return the htype field.
     */
    /**
     * Sets the htype field (Hardware address length).
     *
     *
     * Predefined values are:
     * <pre>
     * HTYPE_ETHER (1)
     * HTYPE_IEEE802 (6)
     * HTYPE_FDDI (8)
    </pre> *
     *
     *
     * Typical value is `HTYPE_ETHER`.
     *
     * @param htype The htype to set.
     */
    // HW address Type
    var htype: Byte
    /**
     * Returns the hlen field (Hardware address length).
     *
     *
     * Typical value is 6 for ethernet - 6 bytes MAC address.
     *
     * @return the hlen field.
     */
    /**
     * Sets the hlen field (Hardware address length).
     *
     *
     * Typical value is 6 for ethernet - 6 bytes MAC address.
     *
     *
     * hlen value should be between 0 and 16, but no control is done here.
     *
     * @param hlen The hlen to set.
     */
    // hardware address length
    var hlen: Byte
    /**
     * Returns the hops field.
     *
     * @return the hops field.
     */
    /**
     * Sets the hops field.
     *
     * @param hops The hops to set.
     */
    // Hw options
    var hops: Byte = 0
    /**
     * Returns the xid field (Transaction ID).
     *
     * @return Returns the xid.
     */
    /**
     * Sets the xid field (Transaction ID).
     *
     *
     * This field is random generated by the client, and used by the client and
     * server to associate requests and responses for the same transaction.
     *
     * @param xid The xid to set.
     */
    // transaction id
    var xid = 0
    /**
     * Returns the secs field (seconds elapsed).
     *
     * @return the secs field.
     */
    /**
     * Sets the secs field (seconds elapsed).
     *
     * @param secs The secs to set.
     */
    // elapsed time from trying to boot
    var secs: Short = 0
    /**
     * Returns the flags field.
     *
     * @return the flags field.
     */

    /**
     * The flags field.
     */
    var flags: Short = 0

    // client IP
    private var ciaddr: ByteArray?

    // your client IP
    private var yiaddr: ByteArray?

    // Server IP
    private var siaddr: ByteArray?

    // relay agent IP
    private var giaddr: ByteArray?

    /**
    // Client HW address
     * The chaddr field (Client hardware address - typically MAC address).
     * The byte[16] raw buffer. Only the first `hlen` bytes are valid.
     */
    var chaddr: ByteArray? = null
        get() {
            return field!!.clone()
        }
        /**
         * Sets the chaddr field (Client hardware address - typically MAC address).
         * The buffer length should be between 0 and 16, otherwise an `IllegalArgumentException` is thrown.
         * If chaddr is null, the field is filled with zeros.
         */
        set(chaddr) {
            if (chaddr != null) {
                require(chaddr.size <= field!!.size) {
                    "chaddr is too long: " + chaddr.size +
                            ", max is: " + this.chaddr!!.size
                }
                Arrays.fill(field!!, 0.toByte())
                System.arraycopy(chaddr, 0, field, 0, chaddr.size)
            } else {
                Arrays.fill(field, 0.toByte())
            }
        }

    // Optional server host name
    private var sname: ByteArray?

    // Boot file name
    private var file: ByteArray?

    // ----------------------------------------------------------------------
    // options part of the packet
    // DHCP options
    // Invariant 1: K is identical to V.getCode()
    // Invariant 2: V.value is never <code>null</code>
    // Invariant 3; K is not 0 (PAD) and not -1 (END)
    private var options: MutableMap<Byte, DHCPOption>?
    /**
     * Returns whether the packet is DHCP or BOOTP.
     *
     *
     * It indicates the presence of the DHCP Magic Cookie at the end
     * of the BOOTP portion.
     *
     *
     * Default is `true` for a brand-new object.
     *
     * @return Returns the isDhcp.
     */
    /**
     * Sets the isDhcp flag.
     *
     *
     * Indicates whether to generate a DHCP or a BOOTP packet. If `true`
     * the DHCP Magic Cookie is added after the BOOTP portion and before the
     * DHCP Options.
     *
     *
     * If `isDhcp` if false, all DHCP options are ignored when calling
     * `serialize()`.
     *
     *
     * Default value is `true`.
     *
     * @param isDhcp The isDhcp to set.
     */
    var isDhcp // well-formed DHCP Packet ?
            : Boolean

    /**
     * Indicates that the DHCP packet has been truncated and did not finished
     * with a 0xFF option. This parameter is set only when parsing packets in
     * non-strict mode (which is not the default behaviour).
     *
     *
     * This field is read-only and can be `true` only with objects created
     * by parsing a Datagram - getPacket() methods.
     *
     *
     * This field is cleared if the object is cloned.
     *
     * @return the truncated field.
     */
    var isTruncated // are the option truncated
            = false
        private set

    // ----------------------------------------------------------------------
    // extra bytes for padding
    private var padding // end of packet padding
            : ByteArray?

    // ----------------------------------------------------------------------
    // Address/port address of the machine, which this datagram is being sent to or received from.
    /**
     * Returns the IP address of the machine to which this datagram is being sent
     * or from which the datagram was received.
     */
    var address: InetAddress? = null
        /**
         * Sets the IP address of the machine to which this datagram is being sent.
         * @throws IllegalArgumentException address is not of `Inet4Address` class.
         */
        set(address) {
            if (address == null) {
                field = null
            } else require(address is Inet4Address) { "only IPv4 addresses accepted" }
            field = address
        }

    /**
     * Returns the port number on the remote host to which this datagram is being sent
     * or from which the datagram was received.
     *
     * @return the port number on the remote host to which this datagram is being sent
     * or from which the datagram was received.
     */
    /**
     * Sets the port number on the remote host to which this datagram is being sent.
     *
     * @param port the port number.
     */
    var port = 0

    /**
     * Returns a copy of this `DHCPPacket`.
     *
     *
     * The `truncated` flag is reset.
     *
     * @return a copy of the `DHCPPacket` instance.
     */
    public override fun clone(): DHCPPacket {
        return try {
            val p = super.clone() as DHCPPacket

            // specifically cloning arrays to avoid side-effects
            p.ciaddr = ciaddr!!.clone()
            p.yiaddr = yiaddr!!.clone()
            p.siaddr = siaddr!!.clone()
            p.giaddr = giaddr!!.clone()
            p.chaddr = chaddr!!.clone()
            p.sname = sname!!.clone()
            p.file = file!!.clone()
            //p.options = this.options.clone();
            p.options = LinkedHashMap(options)
            p.padding = padding!!.clone()
            p.isTruncated = false // freshly new object, it is not considered as corrupt
            p
        } catch (e: CloneNotSupportedException) {
            // this shouldn't happen, since we are Cloneable
            throw InternalError()
        }
    }

    /**
     * Returns true if 2 instances of `DHCPPacket` represent the same DHCP packet.
     *
     *
     * This is a field by field comparison, except `truncated` which is ignored.
     */
    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is DHCPPacket) {
            return false
        }
        var b: Boolean
        b = comment == other.comment
        b = b and (op == other.op)
        b = b and (htype == other.htype)
        b = b and (hlen == other.hlen)
        b = b and (hops == other.hops)
        b = b and (xid == other.xid)
        b = b and (secs == other.secs)
        b = b and (flags == other.flags)
        b = b and Arrays.equals(ciaddr, other.ciaddr)
        b = b and Arrays.equals(yiaddr, other.yiaddr)
        b = b and Arrays.equals(siaddr, other.siaddr)
        b = b and Arrays.equals(giaddr, other.giaddr)
        b = b and Arrays.equals(chaddr, other.chaddr)
        b = b and Arrays.equals(sname, other.sname)
        b = b and Arrays.equals(file, other.file)
        b = b and (options == other.options)
        b = b and (isDhcp == other.isDhcp)
        // we deliberately ignore "truncated" since it is reset when cloning
        b = b and Arrays.equals(padding, other.padding)
        b = b and equalsStatic(address, other.address)
        b = b and (port == other.port)
        return b
    }

    /**
     * Returns a hash code value for the object.
     */
    override fun hashCode(): Int {
        var h = -1
        h = h xor comment.hashCode()
        h += op.toInt()
        h += htype.toInt()
        h += hlen.toInt()
        h += hops.toInt()
        h += xid
        h += secs.toInt()
        h = h xor flags.toInt()
        h = h xor Arrays.hashCode(ciaddr)
        h = h xor Arrays.hashCode(yiaddr)
        h = h xor Arrays.hashCode(siaddr)
        h = h xor Arrays.hashCode(giaddr)
        h = h xor Arrays.hashCode(chaddr)
        h = h xor Arrays.hashCode(sname)
        h = h xor Arrays.hashCode(file)
        h = h xor options.hashCode()
        h += if (isDhcp) 1 else 0
        //		h += this.truncated ? 1 : 0;
        h = h xor Arrays.hashCode(padding)
        h = h xor if (address != null) address.hashCode() else 0
        h += port
        return h
    }

    /**
     * Assert all the invariants of the object. For debug purpose only.
     */
    private fun assertInvariants() {
        assert(comment != null)
        assert(ciaddr != null)
        assert(ciaddr!!.size == 4)
        assert(yiaddr != null)
        assert(yiaddr!!.size == 4)
        assert(siaddr != null)
        assert(siaddr!!.size == 4)
        assert(giaddr != null)
        assert(giaddr!!.size == 4)
        assert(chaddr != null)
        assert(chaddr!!.size == 16)
        assert(sname != null)
        assert(sname!!.size == 64)
        assert(file != null)
        assert(file!!.size == 128)
        assert(padding != null // length is free for padding
        )
        assert(options != null)
        for ((key, opt) in options!!) {
            assert(key != DHCPConstants.DHO_PAD)
            assert(key != DHCPConstants.DHO_END)
            assert(opt.code == key)
            assert(opt.valueFast != null)
        }
    }

    /**
     * Convert a specified byte array containing a DHCP message into a
     * DHCPMessage object.
     *
     * @param buffer byte array to convert to a DHCPMessage object
     * @param offset starting offset for the buffer
     * @param length length of the buffer
     * @param address0 the address from which the packet was sent, or `null`
     * @param port0 the port from which the packet was sent
     * @param strict do we read in strict mode?
     * @return a DHCPMessage object with information from byte array.
     * @throws IllegalArgumentException if buffer is `null`...
     * @throws IndexOutOfBoundsException offset..offset+length is out of buffer bounds
     * @throws DHCPBadPacketException datagram is malformed
     */
    protected fun marshall(
            buffer: ByteArray?, offset: Int, length: Int,
            address0: InetAddress?, port0: Int, strict: Boolean,
    ): DHCPPacket {
        // do some basic sanity checks
        // ibuff, offset & length are valid?
        requireNotNull(buffer) { "null buffer not allowed" }
        if (offset < 0) {
            throw IndexOutOfBoundsException("negative offset not allowed")
        }
        require(length >= 0) { "negative length not allowed" }
        if (buffer.size < offset + length) {
            throw IndexOutOfBoundsException("offset+length exceeds buffer length")
        }

        // absolute minimum size for a valid packet
        if (length < DHCPConstants._BOOTP_ABSOLUTE_MIN_LEN) {
            throw DHCPBadPacketException("DHCP Packet too small (" + length +
                    ") absolute minimum is " + DHCPConstants._BOOTP_ABSOLUTE_MIN_LEN)
        }
        // maximum size for a valid DHCP packet
        if (length > DHCPConstants._DHCP_MAX_MTU) {
            throw DHCPBadPacketException("DHCP Packet too big (" + length +
                    ") max MTU is " + DHCPConstants._DHCP_MAX_MTU)
        }

        // copy address and port
        address = address0 // no need to clone, InetAddress is immutable
        port = port0
        return try {
            // turn buffer into a readable stream
            val inBStream = ByteArrayInputStream(buffer, offset, length)
            val inStream = DataInputStream(inBStream)

            // parse static part of packet
            op = inStream.readByte()
            htype = inStream.readByte()
            hlen = inStream.readByte()
            hops = inStream.readByte()
            xid = inStream.readInt()
            secs = inStream.readShort()
            flags = inStream.readShort()
            inStream.readFully(ciaddr, 0, 4)
            inStream.readFully(yiaddr, 0, 4)
            inStream.readFully(siaddr, 0, 4)
            inStream.readFully(giaddr, 0, 4)
            inStream.readFully(chaddr, 0, 16)
            inStream.readFully(sname, 0, 64)
            inStream.readFully(file, 0, 128)

            // check for DHCP MAGIC_COOKIE
            isDhcp = true
            inBStream.mark(4) // read ahead 4 bytes
            if (inStream.readInt() != DHCPConstants._MAGIC_COOKIE) {
                isDhcp = false
                inBStream.reset() // re-read the 4 bytes
            }
            if (isDhcp) {    // is it a full DHCP packet or a simple BOOTP?
                // DHCP Packet: parsing options
                var type = 0
                while (true) {
                    var r = inBStream.read()
                    if (r < 0) {
                        break
                    } // EOF
                    type = r.toByte().toInt()
                    if (type == DHCPConstants.DHO_PAD.toInt()) {
                        continue
                    } // skip Padding
                    if (type == DHCPConstants.DHO_END.toInt()) {
                        break
                    } // break if end of options
                    r = inBStream.read()
                    if (r < 0) {
                        break
                    } // EOF
                    val len = Math.min(r, inBStream.available())
                    val unit_opt = ByteArray(len)
                    inBStream.read(unit_opt)
                    setOption(DHCPOption(type.toByte(), unit_opt)) // store option
                }
                isTruncated = type != DHCPConstants.DHO_END.toInt() // truncated options?
                if (strict && isTruncated) {
                    throw DHCPBadPacketException("Packet seams to be truncated")
                }
            }

            // put the remaining in padding
            padding = ByteArray(inBStream.available())
            inBStream.read(padding)
            // final verifications (if assertions are activated)
            assertInvariants()
            this
        } catch (e: IOException) {
            // unlikely with ByteArrayInputStream
            throw DHCPBadPacketException("IOException: $e", e)
        }
    }

    /**
     * Converts the object to a byte array ready to be sent on the wire.
     *
     *
     * Default max size of resulting packet is 576, which is the maximum
     * size a client can accept without explicit notice (option XXX)
     *
     * @return a byte array with information from DHCPMessage object.
     * @throws DHCPBadPacketException the datagram would be malformed (too small, too big...)
     */
    fun serialize(): ByteArray {
        var minLen = DHCPConstants._BOOTP_ABSOLUTE_MIN_LEN
        if (isDhcp) {
            // most other DHCP software seems to ensure that the BOOTP 'vend'
            // field is padded to at least 64 bytes
            minLen += DHCPConstants._BOOTP_VEND_SIZE
        }
        return serialize(minLen, DHCPConstants._DHCP_DEFAULT_MAX_LEN)
    }

    /**
     * Converts the object to a byte array ready to be sent on the wire.
     *
     * @param maxSize the maximum buffer size in bytes
     * @return a byte array with information from DHCPMessage object.
     * @throws DHCPBadPacketException the datagram would be malformed (too small, too big...)
     */
    fun serialize(minSize: Int, maxSize: Int): ByteArray {
        assertInvariants()
        // prepare output buffer, pre-sized to maximum buffer length
        // default buffer is half the maximum size of possible packet
        // (this seams reasonable for most uses, worst case only doubles the buffer size once
        val outBStream = ByteArrayOutputStream(DHCPConstants._DHCP_MAX_MTU / 2)
        val outStream = DataOutputStream(outBStream)
        return try {
            outStream.writeByte(op.toInt())
            outStream.writeByte(htype.toInt())
            outStream.writeByte(hlen.toInt())
            outStream.writeByte(hops.toInt())
            outStream.writeInt(xid)
            outStream.writeShort(secs.toInt())
            outStream.writeShort(flags.toInt())
            outStream.write(ciaddr, 0, 4)
            outStream.write(yiaddr, 0, 4)
            outStream.write(siaddr, 0, 4)
            outStream.write(giaddr, 0, 4)
            outStream.write(chaddr, 0, 16)
            outStream.write(sname, 0, 64)
            outStream.write(file, 0, 128)
            if (isDhcp) {
                // DHCP and not BOOTP -> magic cookie required
                outStream.writeInt(DHCPConstants._MAGIC_COOKIE)

                // parse output options in creation order (LinkedHashMap)
                for (opt in optionsCollection) {
                    assert(opt.code != DHCPConstants.DHO_PAD)
                    assert(opt.code != DHCPConstants.DHO_END)
                    assert(opt.valueFast != null)
                    val size = opt.valueFast!!.size
                    assert(size >= 0)
                    if (size > 255) {
                        throw DHCPBadPacketException("Options larger than 255 bytes are not yet supported")
                    }
                    outStream.writeByte(opt.code.toInt()) // output option code
                    outStream.writeByte(size) // output option length
                    outStream.write(opt.valueFast) // output option data
                }
                // mark end of options
                outStream.writeByte(DHCPConstants.DHO_END.toInt())
            }

            // write padding
            outStream.write(padding)

            // add padding if the packet is too small
            val min_padding = minSize - outBStream.size()
            if (min_padding > 0) {
                val add_padding = ByteArray(min_padding)
                outStream.write(add_padding)
            }

            // final packet is here
            val data = outBStream.toByteArray()

            // do some post sanity checks
            if (data.size > DHCPConstants._DHCP_MAX_MTU) {
                throw DHCPBadPacketException("serialize: packet too big (" + data.size + " greater than max MAX_MTU (" + DHCPConstants._DHCP_MAX_MTU + ')')
            }
            data
        } catch (e: IOException) {
            // nomrally impossible with ByteArrayOutputStream
            Timber.e(e, "Unexpected Exception")
            throw DHCPBadPacketException("IOException raised: $e")
        }
    }
    // ========================================================================
    // debug functions
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
        val buffer = StringBuilder() // output buffer
        try {
            val append = buffer.append(if (isDhcp) "DHCP Packet" else "BOOTP Packet")
                    .append("\ncomment=")
                    .append(comment)
                    .append("\naddress=")
                    .append(if (address != null) address!!.hostAddress else "")
                    .append('(')
                    .append(port)
                    .append(')')
                    .append("\nop=")
            val bootName = DHCPConstants.bootNamesMap!![op]
            if (bootName != null) {
                buffer.append(bootName)
                        .append('(')
                        .append(op.toInt())
                        .append(')')
            } else {
                buffer.append(op.toInt())
            }
            buffer.append("\nhtype=")
            val htypeName = DHCPConstants.htypesMap!![htype]
            if (htypeName != null) {
                buffer.append(htypeName)
                        .append('(')
                        .append(htype.toInt())
                        .append(')')
            } else {
                buffer.append(htype.toInt())
            }
            buffer.append("\nhlen=")
                    .append(hlen.toInt())
                    .append("\nhops=")
                    .append(hops.toInt())
                    .append("\nxid=0x")
            appendHex(buffer, xid)
            buffer.append("\nsecs=")
                    .append(secs.toInt())
                    .append("\nflags=0x")
                    .append(Integer.toHexString(flags.toInt()))
                    .append("\nciaddr=")
            appendHostAddress(buffer, InetAddress.getByAddress(ciaddr))
            buffer.append("\nyiaddr=")
            appendHostAddress(buffer, InetAddress.getByAddress(yiaddr))
            buffer.append("\nsiaddr=")
            appendHostAddress(buffer, InetAddress.getByAddress(siaddr))
            buffer.append("\ngiaddr=")
            appendHostAddress(buffer, InetAddress.getByAddress(giaddr))
            buffer.append("\nchaddr=0x")
            appendChaddrAsHex(buffer)
            buffer.append("\nsname=")
                    .append(getSname())
                    .append("\nfile=")
                    .append(getFile())
            if (isDhcp) {
                buffer.append("\nOptions follows:")

                // parse options in creation order (LinkedHashMap)
                for (opt in optionsCollection) {
                    buffer.append('\n')
                    opt.append(buffer)
                }
            }

            // padding
            buffer.append("\npadding[")
                    .append(padding!!.size)
                    .append("]=")
            appendHex(buffer, padding)
        } catch (e: Exception) {
            // what to do ???
        }
        return buffer.toString()
    }
    // ========================================================================
    // getters and setters

    /**
     * Appends the chaddr field (Client hardware address - typically MAC address) as
     * a hex string to this string buffer.
     *
     *
     * Only first `hlen` bytes are appended, as uppercase hex string.
     *
     * @param buffer this string buffer
     * @return the string buffer.
     */
    private fun appendChaddrAsHex(buffer: StringBuilder): StringBuilder {
        appendHex(buffer, chaddr, 0, hlen.toInt() and 0xFF)
        return buffer
    }

    /**
     * Return the hardware address (@MAC) as an `HardwareAddress` object.
     *
     * @return the `HardwareAddress` object
     */
    val hardwareAddress: HardwareAddress
        get() {
            var len = hlen.toInt() and 0xff
            if (len > 16) {
                len = 16
            }
            val buf = ByteArray(len)
            System.arraycopy(chaddr!!, 0, buf, 0, len)
            return HardwareAddress(htype, buf)
        }

    /**
     * Returns the chaddr field (Client hardware address - typically MAC address) as
     * a hex string.
     *
     *
     * Only first `hlen` bytes are printed, as uppercase hex string.
     *
     * @return the chaddr field as hex string.
     */
    val chaddrAsHex: String
        get() = appendChaddrAsHex(StringBuilder(hlen.toInt() and 0xFF)).toString()

    /**
     * Sets the chaddr field - from an hex String.
     *
     * @param hex the chaddr in hex format
     */
    fun setChaddrHex(hex: String) {
        chaddr = hex2Bytes(hex)
    }

    /**
     * Returns the ciaddr field (Client IP Address).
     *
     * @return the ciaddr field converted to `InetAddress` object.
     */
    fun getCiaddr(): InetAddress? {
        return try {
            InetAddress.getByAddress(ciaddrRaw)
        } catch (e: UnknownHostException) {
            Timber.e(e, "Unexpected UnknownHostException")
            null // normaly impossible
        }
    }
    /**
     * Returns the ciaddr field (Client IP Address).
     *
     *
     * This is the low-level maximum performance getter for this field.
     *
     * @return Returns the ciaddr as raw byte[4].
     */
    /**
     * Sets the ciaddr field (Client IP Address).
     *
     *
     * `ciaddr` must be a 4 bytes array, or an `IllegalArgumentException`
     * is thrown.
     *
     *
     * This is the low-level maximum performance setter for this field.
     * The array is internally copied so any further modification to `ciaddr`
     * parameter has no side effect.
     *
     * @param ciaddr The ciaddr to set.
     */
    var ciaddrRaw: ByteArray?
        get() = ciaddr!!.clone()
        set(ciaddr) {
            require(ciaddr!!.size == 4) { "4-byte array required" }
            System.arraycopy(ciaddr, 0, this.ciaddr!!, 0, 4)
        }

    /**
     * Sets the ciaddr field (Client IP Address).
     *
     *
     * Ths `ciaddr` field must be of `Inet4Address` class or
     * an `IllegalArgumentException` is thrown.
     *
     * @param ciaddr The ciaddr to set.
     */
    fun setCiaddr(ciaddr: InetAddress) {
        require(ciaddr is Inet4Address) { "Inet4Address required" }
        ciaddrRaw = ciaddr.getAddress()
    }

    /**
     * Sets the ciaddr field (Client IP Address).
     *
     * @param ciaddr The ciaddr to set.
     * @throws UnknownHostException
     */
    @Throws(UnknownHostException::class)
    fun setCiaddr(ciaddr: String?) {
        this.setCiaddr(InetAddress.getByName(ciaddr))
    }
    /**
     * Returns the file field (Boot File Name).
     *
     *
     * Returns the raw byte[128] buffer, containing a null terminated string.
     *
     *
     * This is the low-level maximum performance getter for this field.
     *
     * @return the file field.
     */
    /**
     * Sets the file field (Boot File Name) as String.
     *
     *
     * If the buffer size is > 128, an `IllegalArgumentException`
     * is thrown.
     *
     *
     * If `file` parameter is null, the buffer is filled with zeros.
     *
     *
     * This is the low-level maximum performance setter for this field.
     *
     * @param file The file field to set.
     * @throws IllegalArgumentException string too long
     */
    var fileRaw: ByteArray?
        get() = file!!.clone()
        set(file) {
            if (file != null) {
                require(file.size <= this.file!!.size) { "File is too long:" + file.size + " max is:" + this.file!!.size }
                Arrays.fill(this.file!!, 0.toByte())
                System.arraycopy(file, 0, this.file!!, 0, file.size)
            } else {
                Arrays.fill(this.file!!, 0.toByte())
            }
        }

    /**
     * Returns the file field (Boot File Name) as String.
     *
     * @return the file converted to a String (transparent encoding).
     */
    fun getFile(): String {
        return bytesToString(fileRaw)
    }

    /**
     * Sets the file field (Boot File Name) as String.
     *
     *
     * The string is first converted to a byte[] array using transparent
     * encoding. If the resulting buffer size is > 128, an `IllegalArgumentException`
     * is thrown.
     *
     *
     * If `file` parameter is null, the buffer is filled with zeros.
     *
     * @param file The file field to set.
     * @throws IllegalArgumentException string too long
     */
    fun setFile(file: String?) {
        fileRaw = stringToBytes(file)
    }

    /**
     * Returns the giaddr field (Relay agent IP address).
     *
     * @return the giaddr field converted to `InetAddress` object.
     */
    fun getGiaddr(): InetAddress? {
        return try {
            InetAddress.getByAddress(giaddrRaw)
        } catch (e: UnknownHostException) {
            Timber.e(e, "Unexpected UnknownHostException")
            null // normally impossible
        }
    }
    /**
     * Returns the giaddr field (Relay agent IP address).
     *
     *
     * This is the low-level maximum performance getter for this field.
     *
     * @return Returns the giaddr as raw byte[4].
     */
    /**
     * Sets the giaddr field (Relay agent IP address).
     *
     *
     * `giaddr` must be a 4 bytes array, or an `IllegalArgumentException`
     * is thrown.
     *
     *
     * This is the low-level maximum performance setter for this field.
     * The array is internally copied so any further modification to `ciaddr`
     * parameter has no side effect.
     *
     * @param giaddr The giaddr to set.
     */
    var giaddrRaw: ByteArray?
        get() = giaddr!!.clone()
        set(giaddr) {
            require(giaddr!!.size == 4) { "4-byte array required" }
            System.arraycopy(giaddr, 0, this.giaddr, 0, 4)
        }

    /**
     * Sets the giaddr field (Relay agent IP address).
     *
     *
     * Ths `giaddr` field must be of `Inet4Address` class or
     * an `IllegalArgumentException` is thrown.
     *
     * @param giaddr The giaddr to set.
     */
    fun setGiaddr(giaddr: InetAddress) {
        require(giaddr is Inet4Address) { "Inet4Address required" }
        giaddrRaw = giaddr.getAddress()
    }

    /**
     * Sets the giaddr field (Relay agent IP address).
     *
     * @param giaddr The giaddr to set.
     * @throws UnknownHostException
     */
    @Throws(UnknownHostException::class)
    fun setGiaddr(giaddr: String?) {
        this.setGiaddr(InetAddress.getByName(giaddr))
    }

    /**
     * Returns the padding portion of the packet.
     *
     *
     * This byte array follows the DHCP Options.
     * Normally, its content is irrelevant.
     *
     * @return Returns the padding.
     */
    fun getPadding(): ByteArray {
        return padding!!.clone()
    }

    /**
     * Sets the padding buffer.
     *
     *
     * This byte array follows the DHCP Options.
     * Normally, its content is irrelevant.
     *
     *
     * If `paddig` is null, it is set to an empty buffer.
     *
     *
     * Padding is automatically added at the end of the datagram when calling
     * `serialize()` to match DHCP minimal packet size.
     *
     * @param padding The padding to set.
     */
    private fun setPadding(padding: ByteArray?) {
        this.padding = padding?.clone() ?: ByteArray(0)
    }

    /**
     * Sets the padding buffer with `length` zero bytes.
     *
     *
     * This is a short cut for `setPadding(new byte[length])`.
     *
     * @param length size of the padding buffer
     */
    fun setPaddingWithZeroes(length_: Int) {
        var length = length_
        if (length < 0) {
            length = 0
        }
        require(length <= DHCPConstants._DHCP_MAX_MTU) { "length is > " + DHCPConstants._DHCP_MAX_MTU }
        setPadding(ByteArray(length))
    }

    /**
     * Returns the siaddr field (IP address of next server).
     *
     * @return the siaddr field converted to `InetAddress` object.
     */
    fun getSiaddr(): InetAddress? {
        return try {
            InetAddress.getByAddress(siaddrRaw)
        } catch (e: UnknownHostException) {
            Timber.log(TimberLog.FINER, e, "Unexpected UnknownHostException")
            null // normaly impossible
        }
    }
    /**
     * Returns the siaddr field (IP address of next server).
     *
     *
     * This is the low-level maximum performance getter for this field.
     *
     * @return Returns the siaddr as raw byte[4].
     */
    /**
     * Sets the siaddr field (IP address of next server).
     *
     *
     * `siaddr` must be a 4 bytes array, or an `IllegalArgumentException`
     * is thrown.
     *
     *
     * This is the low-level maximum performance setter for this field.
     * The array is internally copied so any further modification to `ciaddr`
     * parameter has no side effect.
     *
     * @param siaddr The siaddr to set.
     */
    private var siaddrRaw: ByteArray
        get() = siaddr!!.clone()
        set(siaddr) {
            require(siaddr.size == 4) { "4-byte array required" }
            System.arraycopy(siaddr, 0, this.siaddr!!, 0, 4)
        }

    /**
     * Sets the siaddr field (IP address of next server).
     *
     *
     * Ths `siaddr` field must be of `Inet4Address` class or
     * an `IllegalArgumentException` is thrown.
     *
     * @param siaddr The siaddr to set.
     */
    fun setSiaddr(siaddr: InetAddress) {
        require(siaddr is Inet4Address) { "Inet4Address required" }
        siaddrRaw = siaddr.getAddress()
    }

    /**
     * Sets the siaddr field (IP address of next server).
     *
     * @param siaddr The siaddr to set.
     * @throws UnknownHostException
     */
    @Throws(UnknownHostException::class)
    fun setSiaddr(siaddr: String?) {
        this.setSiaddr(InetAddress.getByName(siaddr))
    }
    /**
     * Returns the sname field (Optional server host name).
     *
     *
     * Returns the raw byte[64] buffer, containing a null terminated string.
     *
     *
     * This is the low-level maximum performance getter for this field.
     *
     * @return the sname field.
     */
    /**
     * Sets the sname field (Optional server host name) as String.
     *
     *
     * If the buffer size is > 64, an `IllegalArgumentException`
     * is thrown.
     *
     *
     * If `sname` parameter is null, the buffer is filled with zeros.
     *
     *
     * This is the low-level maximum performance setter for this field.
     *
     * @param sname The sname field to set.
     * @throws IllegalArgumentException string too long
     */
    private var snameRaw: ByteArray?
        get() = sname!!.clone()
        set(sname) {
            if (sname != null) {
                require(sname.size <= this.sname!!.size) { "Sname is too long:" + sname.size + " max is:" + this.sname!!.size }
                Arrays.fill(this.sname!!, 0.toByte())
                System.arraycopy(sname, 0, this.sname!!, 0, sname.size)
            } else {
                Arrays.fill(this.sname!!, 0.toByte())
            }
        }

    /**
     * Returns the sname field (Optional server host name) as String.
     *
     * @return the sname converted to a String (transparent encoding).
     */
    private fun getSname(): String {
        return bytesToString(snameRaw)
    }

    /**
     * Sets the sname field (Optional server host name) as String.
     *
     *
     * The string is first converted to a byte[] array using transparent
     * encoding. If the resulting buffer size is > 64, an `IllegalArgumentException`
     * is thrown.
     *
     *
     * If `sname` parameter is null, the buffer is filled with zeros.
     *
     * @param sname The sname field to set.
     * @throws IllegalArgumentException string too long
     */
    fun setSname(sname: String?) {
        snameRaw = stringToBytes(sname)
    }

    /**
     * Returns the yiaddr field ('your' IP address).
     *
     * @return the yiaddr field converted to `InetAddress` object.
     */
    fun getYiaddr(): InetAddress? {
        return try {
            InetAddress.getByAddress(yiaddrRaw)
        } catch (e: UnknownHostException) {
            Timber.e(e, "Unexpected UnknownHostException")
            null // normaly impossible
        }
    }
    /**
     * Returns the yiaddr field ('your' IP address).
     *
     *
     * This is the low-level maximum performance getter for this field.
     *
     * @return Returns the yiaddr as raw byte[4].
     */
    /**
     * Sets the yiaddr field ('your' IP address).
     *
     *
     * `yiaddr` must be a 4 bytes array, or an `IllegalArgumentException`
     * is thrown.
     *
     *
     * This is the low-level maximum performance setter for this field.
     * The array is internally copied so any further modification to `ciaddr`
     * parameter has no side effect.
     *
     * @param yiaddr The yiaddr to set.
     */
    private var yiaddrRaw: ByteArray
        get() = yiaddr!!.clone()
        set(yiaddr) {
            require(yiaddr.size == 4) { "4-byte array required" }
            System.arraycopy(yiaddr, 0, this.yiaddr!!, 0, 4)
        }

    /**
     * Sets the yiaddr field ('your' IP address).
     *
     *
     * Ths `yiaddr` field must be of `Inet4Address` class or
     * an `IllegalArgumentException` is thrown.
     *
     * @param yiaddr The yiaddr to set.
     */
    fun setYiaddr(yiaddr: InetAddress) {
        require(yiaddr is Inet4Address) { "Inet4Address required" }
        yiaddrRaw = yiaddr.getAddress()
    }

    /**
     * Sets the yiaddr field ('your' IP address).
     *
     * @param yiaddr The yiaddr to set.
     * @throws UnknownHostException
     */
    @Throws(UnknownHostException::class)
    fun setYiaddr(yiaddr: String?) {
        this.setYiaddr(InetAddress.getByName(yiaddr))
    }

    /**
     * Return the DHCP Option Type.
     *
     *
     * This is a short-cut for `getOptionAsByte(DHO_DHCP_MESSAGE_TYPE)`.
     *
     * @return option type, of `null` if not present.
     */
    val dHCPMessageType: Byte?
        get() = getOptionAsByte(DHCPConstants.DHO_DHCP_MESSAGE_TYPE)

    /**
     * Sets the DHCP Option Type.
     *
     *
     * This is a short-cur for `setOptionAsByte(DHO_DHCP_MESSAGE_TYPE, optionType);`.
     *
     * @param optionType
     */
    fun setDHCPMessageType(optionType: Byte) {
        setOptionAsByte(DHCPConstants.DHO_DHCP_MESSAGE_TYPE, optionType)
    }

    /**
     * Wrapper function for getValueAsNum() in DHCPOption. Returns a numerical option: int, short or byte.
     *
     * @param code DHCP option code
     * @return Integer object or `null`
     */
    fun getOptionAsNum(code: Byte): Int? {
        val opt = getOption(code)
        return opt?.valueAsNum
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
     * @param code the option code.
     * @return the option value, `null` if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    @Throws(IllegalArgumentException::class)
    fun getOptionAsByte(code: Byte): Byte? {
        val opt = getOption(code)
        return opt?.valueAsByte
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
     * @param code the option code.
     * @return the option value, `null` if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    @Throws(IllegalArgumentException::class)
    fun getOptionAsShort(code: Byte): Short? {
        val opt = getOption(code)
        return opt?.valueAsShort
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
     * @param code the option code.
     * @return the option value, `null` if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    @Throws(IllegalArgumentException::class)
    fun getOptionAsInteger(code: Byte): Int? {
        val opt = getOption(code)
        return opt?.valueAsInt
    }

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
     * @param code the option code.
     * @return the option value, `null` if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    @Throws(IllegalArgumentException::class)
    fun getOptionAsInetAddr(code: Byte): InetAddress? {
        val opt = getOption(code)
        return opt?.valueAsInetAddr
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
     * @param code the option code.
     * @return the option value, `null` if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    @Throws(IllegalArgumentException::class)
    fun getOptionAsString(code: Byte): String? {
        val opt = getOption(code)
        return opt?.valueAsString
    }

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
     * @param code the option code.
     * @return the option value array, `null` if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    @Throws(IllegalArgumentException::class)
    fun getOptionAsShorts(code: Byte): ShortArray? {
        val opt = getOption(code)
        return opt?.valueAsShorts
    }

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
     * @param code the option code.
     * @return the option value array, `null` if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     * @throws DHCPBadPacketException the option value in packet is of wrong size.
     */
    @Throws(IllegalArgumentException::class)
    fun getOptionAsInetAddrs(code: Byte): Array<InetAddress?>? {
        val opt = getOption(code)
        return opt?.valueAsInetAddrs
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
     * @param code the option code.
     * @return the option value array, `null` if option is not present.
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    @Throws(IllegalArgumentException::class)
    fun getOptionAsBytes(code: Byte): ByteArray? {
        val opt = getOption(code)
        return opt?.valueAsBytes
    }

    /**
     * Sets a DHCP Option as Byte format.
     *
     *
     * See `DHCPOption` for allowed option codes.
     *
     * @param code the option code.
     * @param val the value
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    fun setOptionAsByte(code: Byte, `val`: Byte) {
        setOption(DHCPOption.newOptionAsByte(code, `val`))
    }

    /**
     * Sets a DHCP Option as Short format.
     *
     *
     * See `DHCPOption` for allowed option codes.
     *
     * @param code the option code.
     * @param val the value
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    fun setOptionAsShort(code: Byte, `val`: Short) {
        setOption(DHCPOption.newOptionAsShort(code, `val`))
    }

    /**
     * Sets a DHCP Option as Integer format.
     *
     *
     * See `DHCPOption` for allowed option codes.
     *
     * @param code the option code.
     * @param val the value
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    fun setOptionAsInt(code: Byte, `val`: Int) {
        setOption(DHCPOption.newOptionAsInt(code, `val`))
    }

    /**
     * Sets a DHCP Option as InetAddress format.
     *
     *
     * See `DHCPOption` for allowed option codes.
     *
     * @param code the option code.
     * @param val the value
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    fun setOptionAsInetAddress(code: Byte, `val`: InetAddress?) {
        setOption(DHCPOption.newOptionAsInetAddress(code, `val`))
    }

    /**
     * Sets a DHCP Option as InetAddress format.
     *
     *
     * See `DHCPOption` for allowed option codes.
     *
     * @param code the option code in String format.
     * @param val the value
     * @throws UnknownHostException cannot find the address
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    @Throws(UnknownHostException::class)
    fun setOptionAsInetAddress(code: Byte, `val`: String?) {
        setOption(DHCPOption.newOptionAsInetAddress(code, InetAddress.getByName(`val`)))
    }

    /**
     * Sets a DHCP Option as InetAddress array format.
     *
     *
     * See `DHCPOption` for allowed option codes.
     *
     * @param code the option code.
     * @param val the value array
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    fun setOptionAsInetAddresses(code: Byte, `val`: Array<InetAddress?>?) {
        setOption(DHCPOption.newOptionAsInetAddresses(code, `val`))
    }

    /**
     * Sets a DHCP Option as String format.
     *
     *
     * See `DHCPOption` for allowed option codes.
     *
     * @param code the option code.
     * @param val the value
     * @throws IllegalArgumentException the option code is not in the list above.
     */
    fun setOptionAsString(code: Byte, `val`: String?) {
        setOption(DHCPOption.newOptionAsString(code, `val`))
    }

    /**
     * Returns the option as raw byte[] buffer.
     *
     *
     * This is the low-level maximum performance getter for options.
     * No byte[] copy is completed to increase performance.
     *
     * @param code option code
     * @return Returns the option as raw `byte[]`, or `null` if
     * the option is not present.
     */
    fun getOptionRaw(code: Byte): ByteArray? {
        val opt = getOption(code)
        return opt?.valueFast
    }

    /**
     * Returns the option as DHCPOption object.
     *
     *
     * This is the low-level maximum performance getter for options.
     * This method is used by every option getter in this object.
     *
     * @param code option code
     * @return Returns the option as `DHCPOption`, or `null` if
     * the option is not present.
     */
    fun getOption(code: Byte): DHCPOption? {
        val opt = options!![code] ?: return null
        // Sanity checks
        assert(opt.code == code)
        assert(opt.valueFast != null)
        return opt
    }

    /**
     * Tests whether an option code is present in the packet.
     *
     * @param code DHCP option code
     * @return true if option is present
     */
    fun containsOption(code: Byte): Boolean {
        return options!!.containsKey(code)
    }// read only

    /**
     * Return an ordered list/collection of all options.
     *
     *
     * The Collection is read-only.
     *
     * @return collection of `DHCPOption`.
     */
    val optionsCollection: Collection<DHCPOption>
        get() = Collections.unmodifiableCollection(options!!.values) // read only

    /**
     * Return an array of all DHCP options.
     *
     * @return the options array
     */
    val optionsArray: Array<DHCPOption>
        get() = options!!.values.toTypedArray()

    /**
     * Sets the option specified for the option.
     *
     *
     * If `buf` is `null`, the option is cleared.
     *
     *
     * Options are sorted in creation order. Previous values are replaced.
     *
     *
     * This is the low-level maximum performance setter for options.
     *
     * @param code opt    option code, use `DHO_*` for predefined values.
     * @param buf raw buffer value (cloned). If null, the option is removed.
     */
    fun setOptionRaw(code: Byte, buf: ByteArray?) {
        if (buf == null) {        // clear parameter
            removeOption(code)
        } else {
            setOption(DHCPOption(code, buf)) // exception here if code=0 or code=-1
        }
    }

    /**
     * Sets the option specified for the option.
     *
     *
     * If `buf` is `null`, the option is cleared.
     *
     *
     * Options are sorted in creation order. Previous values are replaced, but their
     * previous position is retained.
     *
     *
     * This is the low-level maximum performance setter for options.
     * This method is called by all setter methods in this class.
     *
     * @param opt option code, use `DHO_*` for predefined values.
     */
    fun setOption(opt: DHCPOption?) {
        if (opt != null) {
            if (opt.valueFast == null) {
                removeOption(opt.code)
            } else {
                options!![opt.code] = opt
            }
        }
    }

    /**
     * Sets an array of options. Calles repeatedly setOption on each element of the array.
     *
     * @param opts array of options.
     */
    fun setOptions(opts: Array<DHCPOption?>?) {
        if (opts != null) {
            for (opt in opts) {
                setOption(opt)
            }
        }
    }

    /**
     * Sets a Collection of options. Calles repeatedly setOption on each element of the List.
     *
     * @param opts List of options.
     */
    fun setOptions(opts: Collection<DHCPOption?>?) {
        if (opts != null) {
            for (opt in opts) {
                setOption(opt)
            }
        }
    }

    /**
     * Remove this option from the options list.
     *
     * @param opt the option code to remove.
     */
    fun removeOption(opt: Byte) {
        options!!.remove(opt)
    }

    /**
     * Remove all options.
     */
    fun removeAllOptions() {
        options!!.clear()
    }

    /**
     * Syntactic sugar for getAddress/getPort.
     *
     * @return address + port.
     */
    /**
     * Syntactic sugar for setAddress/setPort.
     *
     * @param addrPort address and port, if `null address is set to null and port to 0
    ` */
    var addrPort: InetSocketAddress?
        get() = InetSocketAddress(address, port)
        set(addrPort) {
            port = if (addrPort == null) {
                address = null
                0
            } else {
                address = addrPort.address
                addrPort.port
            }
        }

    /**
     * Constructor for the `DHCPPacket` class.
     *
     * This creates an empty `DHCPPacket` datagram.
     * All data is default values and the packet is still lacking key data to be sent on the wire.
     */
    init {
        op = DHCPConstants.BOOTREPLY
        htype = DHCPConstants.HTYPE_ETHER
        hlen = 6
        ciaddr = ByteArray(4)
        yiaddr = ByteArray(4)
        siaddr = ByteArray(4)
        giaddr = ByteArray(4)
        chaddr = ByteArray(16)
        sname = ByteArray(64)
        file = ByteArray(128)
        padding = ByteArray(0)
        isDhcp = true
        options = LinkedHashMap()
    }

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Factory for creating `DHCPPacket` objects by parsing a
         * `DatagramPacket` object.
         *
         * @param datagram the UDP datagram received to be parsed
         * @return the newly create `DHCPPacket` instance
         * @throws DHCPBadPacketException the datagram is malformed and cannot be parsed properly.
         * @throws IllegalArgumentException datagram is `null`
         * @throws IOException
         */
        @Throws(DHCPBadPacketException::class)
        fun getPacket(datagram: DatagramPacket?): DHCPPacket {
            requireNotNull(datagram) { "datagram is null" }
            val packet = DHCPPacket()
            // all parameters are checked in marshall()
            packet.marshall(datagram.data, datagram.offset, datagram.length,
                    datagram.address, datagram.port,
                    true) // strict mode by default
            return packet
        }

        /**
         * Factory for creating `DHCPPacket` objects by parsing a
         * `byte[]` e.g. from a datagram.
         *
         *
         * This method allows you to specify non-strict mode which is much more
         * tolerant for packet options. By default, any problem seen during DHCP option
         * parsing causes a DHCPBadPacketException to be thrown.
         *
         * @param buf buffer for holding the incoming datagram.
         * @param offset the offset for the buffer.
         * @param length the number of bytes to read.
         * @param strict do we parse in strict mode?
         * @return the newly create `DHCPPacket` instance
         * @throws DHCPBadPacketException the datagram is malformed.
         */
        @Throws(DHCPBadPacketException::class)
        fun getPacket(buf: ByteArray?, offset: Int, length: Int, strict: Boolean): DHCPPacket {
            val packet = DHCPPacket()
            // all parameters are checked in marshall()
            packet.marshall(buf, offset, length, null, 0, strict)
            return packet
        }

        private fun equalsStatic(a: Any?, b: Any?): Boolean {
            return if (a == null) b == null else a == b
        }
        // ========================================================================
        // utility functions
        /**
         * Converts a null terminated byte[] string to a String object,
         * with a transparent conversion.
         *
         * Faster version than String.getBytes()
         */
        fun bytesToString(buf: ByteArray?): String {
            return if (buf == null) {
                ""
            } else bytesToString(buf, 0, buf.size)
        }

        fun bytesToString(buf: ByteArray?, src: Int, len: Int): String {
            var src = src
            var len = len
            if (buf == null) {
                return ""
            }
            if (src < 0) {
                len += src // reduce length
                src = 0
            }
            if (len <= 0) {
                return ""
            }
            if (src >= buf.size) {
                return ""
            }
            if (src + len > buf.size) {
                len = buf.size - src
            }
            // string should be null terminated or whole buffer
            // first find the real lentgh
            for (i in src until src + len) {
                if (buf[i].toInt() == 0) {
                    len = i - src
                    break
                }
            }
            val chars = CharArray(len)
            for (i in src until src + len) {
                chars[i - src] = Char(buf[i].toUShort())
            }
            return String(chars)
        }

        /**
         * Converts byte to hex string (2 chars) (uppercase)
         */
        private val hex = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
        fun appendHex(sbuf: StringBuilder, b: Byte) {
            val i = b.toInt() and 0xFF
            sbuf.append(hex[i and 0xF0 shr 4])
                    .append(hex[i and 0x0F])
        }
        /**
         * Converts a byte[] to a sequence of hex chars (uppercase), limited to `len` bytes
         * and appends them to a string buffer
         */
        /**
         * Convert plain byte[] to hex string (uppercase)
         */
        @JvmOverloads
        fun appendHex(sbuf: StringBuilder, buf: ByteArray?, src_: Int = 0, len_: Int = buf!!.size) {
            var src = src_
            var len = len_
            if (buf == null) {
                return
            }
            if (src < 0) {
                len += src // reduce length
                src = 0
            }
            if (len <= 0 || src >= buf.size) {
                return
            }
            if (src + len > buf.size) {
                len = buf.size - src
            }
            for (i in src until src + len) {
                appendHex(sbuf, buf[i])
            }
        }

        /**
         * Convert bytes to hex string.
         *
         * @param buf
         * @return hex string (lowercase) or "" if buf is `null`
         */
        fun bytes2Hex(buf: ByteArray?): String {
            if (buf == null) {
                return ""
            }
            val sb = StringBuilder(buf.size * 2)
            appendHex(sb, buf)
            return sb.toString()
        }

        /**
         * Convert hes String to byte[]
         */
        fun hex2Bytes(s: String): ByteArray {
            require(s.length and 1 == 0) { "String length must be even: " + s.length }
            val buf = ByteArray(s.length / 2)
            for (index in buf.indices) {
                val stringIndex = index shl 1
                buf[index] = s.substring(stringIndex, stringIndex + 2).toInt(16).toByte()
            }
            return buf
        }

        /**
         * Convert integer to hex chars (uppercase) and appends them to a string builder
         */
        private fun appendHex(sbuf: StringBuilder, i: Int) {
            appendHex(sbuf, (i and -0x1000000 ushr 24).toByte())
            appendHex(sbuf, (i and 0x00ff0000 ushr 16).toByte())
            appendHex(sbuf, (i and 0x0000ff00 ushr 8).toByte())
            appendHex(sbuf, (i and 0x000000ff).toByte())
        }

        fun stringToBytes(str: String?): ByteArray? {
            if (str == null) {
                return null
            }
            val chars = str.toCharArray()
            val len = chars.size
            val buf = ByteArray(len)
            for (i in 0 until len) {
                buf[i] = chars[i].code.toByte()
            }
            return buf
        }

        /**
         * Even faster version than [.getHostAddress] when the address is not
         * the only piece of information put in the string.
         *
         * @param sbuf the string builder
         * @param addr the Internet address
         */
        fun appendHostAddress(sbuf: StringBuilder, addr: InetAddress?) {
            requireNotNull(addr) { "addr must not be null" }
            require(addr is Inet4Address) { "addr must be an instance of Inet4Address" }
            val src = addr.getAddress()
            sbuf.append(src[0].toInt() and 0xFF)
                    .append('.')
                    .append(src[1].toInt() and 0xFF)
                    .append('.')
                    .append(src[2].toInt() and 0xFF)
                    .append('.')
                    .append(src[3].toInt() and 0xFF)
        }

        /**
         * Faster version than `InetAddress.getHostAddress()`.
         *
         * @return String representation of address.
         */
        fun getHostAddress(addr: InetAddress?): String {
            val sbuf = StringBuilder(15)
            appendHostAddress(sbuf, addr)
            return sbuf.toString()
        }
    }
}