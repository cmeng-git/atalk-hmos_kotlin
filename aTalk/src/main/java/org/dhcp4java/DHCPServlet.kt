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
import java.net.DatagramPacket
import java.util.*

/**
 * General Interface for a "DHCP Servlet"
 *
 *
 * Normal use is to override the `doXXX()` or `service()` method
 * to provide your own application logic.
 *
 *
 * For simple servers or test purpose, it as also a good idea to provide
 * a `main()` method so you can easily launch the server by running the servlet.
 *
 * @author Stephan Hadinger
 * @author Eng Chong Meng
 * @version 1.00
 */
class DHCPServlet {
    /**
     * @return Returns the server.
     */
    /**
     * @param server The server to set.
     */
    /**
     * the server instance running this servlet
     */
    var server: DHCPCoreServer? = null

    /**
     * Initialize servlet. Override this method to implement any initialization you may need.
     *
     *
     * This method is called once at stratup, before any request is passed to the servlet.
     * A properties is passed to the servlet to read whatever parameters it needs.
     *
     *
     * There is no default behaviour.
     *
     * @param props a Properties containing parameters, as passed to `DHCPCoreServer`
     */
    fun init(props: Properties?) {
        // read whatever parameters you need
    }

    /**
     * Low-level method for receiving a UDP Daragram and sending one back.
     *
     *
     * This methode normally does not need to be overriden and passes control
     * to `service()` for DHCP packets handling. Howerever the `service()`
     * method is not called if the DHCP request is invalid (i.e. could not be parsed).
     * So overriding this method gives you control on every datagram received, not
     * only valid DHCP packets.
     *
     * @param requestDatagram the datagram received from the client
     * @return response the datagram to send back, or `null` if no answer
     */
    fun serviceDatagram(requestDatagram: DatagramPacket?): DatagramPacket? {
        val responseDatagram: DatagramPacket
        if (requestDatagram == null) {
            return null
        }
        try {
            // parse DHCP request
            val request = DHCPPacket.getPacket(requestDatagram) ?: return null
            // nothing much we can do
            Timber.log(TimberLog.FINER, "%s", request.toString())

            // do the real work
            val response = service(request) // call service function
            // done
            Timber.log(TimberLog.FINER, "service() done")
            if (response == null) {
                return null
            }

            // check address/port
            val address = response.address
            if (address == null) {
                Timber.w("Address needed in response")
                return null
            }
            val port = response.port

            // we have something to send back
            val responseBuf = response.serialize()
            Timber.log(TimberLog.FINER, "Buffer is %d bytes long", responseBuf.size)
            responseDatagram = DatagramPacket(responseBuf, responseBuf.size, address, port)
            Timber.log(TimberLog.FINER, "Sending back to %s (%s)", address.hostAddress, port)
            postProcess(requestDatagram, responseDatagram)
            return responseDatagram
        } catch (e: DHCPBadPacketException) {
            Timber.w("Invalid DHCP packet received: %s", e.message)
        } catch (e: Exception) {
            Timber.w("Unexpected Exception: %s", e.message)
        }

        // general fallback, we do nothing
        return null
    }

    /**
     * General method for parsing a DHCP request.
     *
     *
     * Returns the DHCPPacket to send back to the client, or null if we
     * silently ignore the request.
     *
     *
     * Default behaviour: ignore BOOTP packets, and dispatch to `doXXX()` methods.
     *
     * @param request DHCP request from the client
     * @return response DHCP response to send back to client, `null` if no response
     */
    protected fun service(request: DHCPPacket?): DHCPPacket? {
        val dhcpMessageType: Byte?
        if (request == null) {
            return null
        }
        if (!request.isDhcp) {
            Timber.i("BOOTP packet rejected")
            return null // skipping old BOOTP
        }
        dhcpMessageType = request.dHCPMessageType
        if (dhcpMessageType == null) {
            Timber.i("No DHCP message type")
            return null
        }
        return if (request.op == DHCPConstants.Companion.BOOTREQUEST) {
            when (dhcpMessageType) {
                DHCPConstants.Companion.DHCPDISCOVER -> doDiscover(request)
                DHCPConstants.Companion.DHCPREQUEST -> doRequest(request)
                DHCPConstants.Companion.DHCPINFORM -> doInform(request)
                DHCPConstants.Companion.DHCPDECLINE -> doDecline(request)
                DHCPConstants.Companion.DHCPRELEASE -> doRelease(request)
                else -> {
                    Timber.i("Unsupported message type %s", dhcpMessageType)
                    null
                }
            }
        } else if (request.op == DHCPConstants.Companion.BOOTREPLY) {
            // receiving a BOOTREPLY from a client is not normal
            Timber.i("BOOTREPLY received from client")
            null
        } else {
            Timber.w("Unknown Op: %s", request.op)
            null // ignore
        }
    }

    /**
     * Process DISCOVER request.
     *
     * @param request DHCP request received from client
     * @return DHCP response to send back, or `null` if no response.
     */
    private fun doDiscover(request: DHCPPacket?): DHCPPacket? {
        Timber.log(TimberLog.FINER, "DISCOVER packet received")
        return null
    }

    /**
     * Process REQUEST request.
     *
     * @param request DHCP request received from client
     * @return DHCP response to send back, or `null` if no response.
     */
    private fun doRequest(request: DHCPPacket?): DHCPPacket? {
        Timber.log(TimberLog.FINER, "REQUEST packet received")
        return null
    }

    /**
     * Process INFORM request.
     *
     * @param request DHCP request received from client
     * @return DHCP response to send back, or `null` if no response.
     */
    private fun doInform(request: DHCPPacket?): DHCPPacket? {
        Timber.log(TimberLog.FINER, "INFORM packet received")
        return null
    }

    /**
     * Process DECLINE request.
     *
     * @param request DHCP request received from client
     * @return DHCP response to send back, or `null` if no response.
     */
    private fun doDecline(request: DHCPPacket?): DHCPPacket? {
        Timber.log(TimberLog.FINER, "DECLINE packet received")
        return null
    }

    /**
     * Process RELEASE request.
     *
     * @param request DHCP request received from client
     * @return DHCP response to send back, or `null` if no response.
     */
    private fun doRelease(request: DHCPPacket?): DHCPPacket? {
        Timber.log(TimberLog.FINER, "RELEASE packet received")
        return null
    }

    /**
     * You have a chance to catch response before it is sent back to client.
     *
     *
     * This allows for example for last minute modification (who knows?)
     * or for specific logging.
     *
     *
     * Default behaviour is to do nothing.
     *
     *
     * The only way to block the response from being sent is to raise an exception.
     *
     * @param requestDatagram datagram received from client
     * @param responseDatagram datagram sent back to client
     */
    private fun postProcess(requestDatagram: DatagramPacket?, responseDatagram: DatagramPacket?) {
        // default is nop
    }
}