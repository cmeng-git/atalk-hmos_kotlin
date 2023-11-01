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
import java.net.InetSocketAddress

/**
 * This class provides some standard factories for DHCP responses.
 *
 *
 * This simplifies DHCP Server development as basic behaviour is already usable
 * as-is.
 *
 * @author Stephan Hadinger
 * @author Eng Chong Meng
 *
 * @version 1.00
 */
class DHCPResponseFactory private constructor() {
    // Suppresses default constructor, ensuring non-instantiability.
    init {
        throw UnsupportedOperationException()
    }

    companion object {
        /**
         * Create a populated DHCPOFFER response.
         *
         *
         * Reponse is populated according to the DHCP request received (must be
         * DHCPDISCOVER), the proposed client address and a set of pre-set options.
         *
         *
         * Note: `getDefaultSocketAddress` is called internally to populate
         * address and port number to which response should be sent.
         *
         * @param request
         * @param offeredAddress
         * @param options
         * @return the newly created OFFER Packet
         */
        fun makeDHCPOffer(
                request: DHCPPacket?,
                offeredAddress: InetAddress?,
                leaseTime: Int,
                serverIdentifier: InetAddress?,
                message: String?,
                options: Array<DHCPOption>?): DHCPPacket {
            // check request
            if (request == null) {
                throw NullPointerException("request is null")
            }
            if (!request.isDhcp) {
                throw DHCPBadPacketException("request is BOOTP")
            }
            val requestMessageType = request.dHCPMessageType
                    ?: throw DHCPBadPacketException("request has no message type")
            if (requestMessageType != DHCPConstants.DHCPDISCOVER) {
                throw DHCPBadPacketException("request is not DHCPDISCOVER")
            }
            // check offeredAddress
            requireNotNull(offeredAddress) { "offeredAddress must not be null" }
            require(offeredAddress is Inet4Address) { "offeredAddress must be IPv4" }
            val resp = DHCPPacket()
            resp.op = DHCPConstants.BOOTREPLY
            resp.htype = request.htype
            resp.hlen = request.hlen
            // Hops is left to 0
            resp.xid = request.xid
            // Secs is left to 0
            resp.flags = request.flags
            // Ciaddr is left to 0.0.0.0
            resp.setYiaddr(offeredAddress)
            // Siaddr ?
            resp.giaddrRaw = request.giaddrRaw
            resp.chaddr = request.chaddr
            // sname left empty
            // file left empty

            // we set the DHCPOFFER type
            resp.setDHCPMessageType(DHCPConstants.DHCPOFFER)

            // set standard options
            resp.setOptionAsInt(DHCPConstants.DHO_DHCP_LEASE_TIME, leaseTime)
            resp.setOptionAsInetAddress(DHCPConstants.DHO_DHCP_SERVER_IDENTIFIER, serverIdentifier)
            resp.setOptionAsString(DHCPConstants.DHO_DHCP_MESSAGE, message) // if null, it is removed
            if (options != null) {
                for (opt in options) {
                    resp.setOption(opt.applyOption(request))
                }
            }

            // we set address/port according to rfc
            resp.addrPort = getDefaultSocketAddress(request, DHCPConstants.DHCPOFFER)
            return resp
        }

        /**
         * Create a populated DHCPACK response.
         *
         *
         * Reponse is populated according to the DHCP request received (must be
         * DHCPREQUEST), the proposed client address and a set of pre-set options.
         *
         *
         * Note: `getDefaultSocketAddress` is called internally to populate
         * address and port number to which response should be sent.
         *
         * @param request
         * @param offeredAddress
         * @param options
         * @return the newly created ACK Packet
         */
        fun makeDHCPAck(
                request: DHCPPacket?,
                offeredAddress: InetAddress?,
                leaseTime: Int,
                serverIdentifier: InetAddress?,
                message: String?,
                options: Array<DHCPOption>?): DHCPPacket {
            // check request
            if (request == null) {
                throw NullPointerException("request is null")
            }
            if (!request.isDhcp) {
                throw DHCPBadPacketException("request is BOOTP")
            }
            val requestMessageType = request.dHCPMessageType
                    ?: throw DHCPBadPacketException("request has no message type")
            if (requestMessageType != DHCPConstants.DHCPREQUEST && requestMessageType != DHCPConstants.DHCPINFORM) {
                throw DHCPBadPacketException("request is not DHCPREQUEST/DHCPINFORM")
            }
            // check offered address
            requireNotNull(offeredAddress) { "offeredAddress must not be null" }
            require(offeredAddress is Inet4Address) { "offeredAddress must be IPv4" }
            val resp = DHCPPacket()
            resp.op = DHCPConstants.BOOTREPLY
            resp.htype = request.htype
            resp.hlen = request.hlen
            // Hops is left to 0
            resp.xid = request.xid
            // Secs is left to 0
            resp.flags = request.flags
            resp.ciaddrRaw = request.ciaddrRaw
            if (requestMessageType != DHCPConstants.DHCPINFORM) {
                resp.setYiaddr(offeredAddress)
            }
            // Siaddr ?
            resp.giaddrRaw = request.giaddrRaw
            resp.chaddr = request.chaddr
            // sname left empty
            // file left empty

            // we set the DHCPOFFER type
            resp.setDHCPMessageType(DHCPConstants.DHCPACK)

            // set standard options
            if (requestMessageType == DHCPConstants.DHCPREQUEST) {            // rfc 2131
                resp.setOptionAsInt(DHCPConstants.DHO_DHCP_LEASE_TIME, leaseTime)
            }
            resp.setOptionAsInetAddress(DHCPConstants.DHO_DHCP_SERVER_IDENTIFIER, serverIdentifier)
            resp.setOptionAsString(DHCPConstants.DHO_DHCP_MESSAGE, message) // if null, it is removed
            if (options != null) {
                for (opt in options) {
                    resp.setOption(opt.applyOption(request))
                }
            }

            // we set address/port according to rfc
            resp.addrPort = getDefaultSocketAddress(request, DHCPConstants.DHCPACK)
            return resp
        }

        /**
         * Create a populated DHCPNAK response.
         *
         *
         * Reponse is populated according to the DHCP request received (must be
         * DHCPREQUEST), the proposed client address and a set of pre-set options.
         *
         *
         * Note: `getDefaultSocketAddress` is called internally to populate
         * address and port number to which response should be sent.
         *
         * @param request
         * @param serverIdentifier
         * @param message
         * @return the newly created NAK Packet
         */
        fun makeDHCPNak(
                request: DHCPPacket?,
                serverIdentifier: InetAddress?,
                message: String?): DHCPPacket {
            // check request
            if (request == null) {
                throw NullPointerException("request is null")
            }
            if (!request.isDhcp) {
                throw DHCPBadPacketException("request is BOOTP")
            }
            val requestMessageType = request.dHCPMessageType
                    ?: throw DHCPBadPacketException("request has no message type")
            if (requestMessageType != DHCPConstants.DHCPREQUEST) {
                throw DHCPBadPacketException("request is not DHCPREQUEST")
            }
            val resp = DHCPPacket()
            resp.op = DHCPConstants.BOOTREPLY
            resp.htype = request.htype
            resp.hlen = request.hlen
            // Hops is left to 0
            resp.xid = request.xid
            // Secs is left to 0
            resp.flags = request.flags
            // ciaddr left to 0
            // yiaddr left to 0
            // Siaddr ?
            resp.giaddrRaw = request.giaddrRaw
            resp.chaddr = request.chaddr
            // sname left empty
            // file left empty

            // we set the DHCPOFFER type
            resp.setDHCPMessageType(DHCPConstants.DHCPNAK)

            // set standard options
            resp.setOptionAsInetAddress(DHCPConstants.DHO_DHCP_SERVER_IDENTIFIER, serverIdentifier)
            resp.setOptionAsString(DHCPConstants.DHO_DHCP_MESSAGE, message) // if null, it is removed

            // we do not set other options for this type of message

            // we set address/port according to rfc
            resp.addrPort = getDefaultSocketAddress(request, DHCPConstants.DHCPNAK)
            return resp
        }

        /**
         * Calculates the addres/port to which the response must be sent, according to
         * rfc 2131, section 4.1.
         *
         *
         * This is a method ready to use for *standard* behaviour for any RFC
         * compliant DHCP Server.
         *
         *
         * If `giaddr` is null, it is the client's addres/68, otherwise
         * giaddr/67.
         *
         *
         * Standard behaviour is to set the response packet as follows:
         * <pre>
         * response.setAddrPort(getDefaultSocketAddress(request), response.getOp());
        </pre> *
         *
         * @param request the client DHCP request
         * @param responseType the DHCP Message Type the servers wants to send (DHCPOFFER,
         * DHCPACK, DHCPNAK)
         * @return the ip/port to send back the response
         * @throws IllegalArgumentException if request is `null`.
         * @throws IllegalArgumentException if responseType is not valid.
         */
        fun getDefaultSocketAddress(request: DHCPPacket?, responseType: Byte): InetSocketAddress {
            requireNotNull(request) { "request is null" }
            val sockAdr: InetSocketAddress
            val giaddr = request.address
            val ciaddr = request.getCiaddr()
            when (responseType) {
                DHCPConstants.DHCPOFFER, DHCPConstants.DHCPACK -> if (DHCPConstants.INADDR_ANY == giaddr) {
                    if (DHCPConstants.INADDR_ANY == ciaddr) {    // broadcast to LAN
                        sockAdr = InetSocketAddress(DHCPConstants.INADDR_BROADCAST, 68)
                    } else {
                        sockAdr = InetSocketAddress(ciaddr, 68)
                    }
                } else { // unicast to relay
                    sockAdr = InetSocketAddress(giaddr, 67)
                }
                DHCPConstants.DHCPNAK -> if (DHCPConstants.INADDR_ANY == giaddr) {    // always broadcast
                    sockAdr = InetSocketAddress(DHCPConstants.INADDR_BROADCAST, 68)
                } else { // unicast to relay
                    sockAdr = InetSocketAddress(giaddr, 67)
                }
                else -> throw IllegalArgumentException("responseType not valid")
            }
            return sockAdr
        }
    }
}