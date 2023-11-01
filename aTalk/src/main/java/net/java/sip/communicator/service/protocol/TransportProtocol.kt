/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * Enumerates the "transport" (OSI transport/application) protocols used to carry the control
 * channel for protocol services like xmmp, sip, etc.
 *
 * @author Vincent Lucas
 * @author Eng Chong Meng
 */
enum class TransportProtocol {
    /**
     * The "transport" protocol is unknown.
     */
    UNKNOWN,

    /**
     * The "transport" protocol is UDP.
     */
    UDP,

    /**
     * The "transport" protocol is TCP.
     */
    TCP,

    /**
     * The "transport" protocol is TLS.
     */
    TLS;

    companion object {
        /**
         * Parses a `String` and returns the appropriate `TransportProtocol`.
         *
         * @param transportProtocol
         * string
         * @return appropriate `TransportProtocol`
         * @throws IllegalArgumentException
         * if string is not a transport protocol valid name
         */
        @Throws(IllegalArgumentException::class)
        fun parse(transportProtocol: String): TransportProtocol {
            if (UNKNOWN.toString().equals(transportProtocol, ignoreCase = true)) return UNKNOWN else if (UDP.toString().equals(transportProtocol, ignoreCase = true)) return UDP else if (TCP.toString().equals(transportProtocol, ignoreCase = true)) return TCP else if (TLS.toString().equals(transportProtocol, ignoreCase = true)) return TLS
            throw IllegalArgumentException(transportProtocol
                    + "is not a currently supported TransportProtocolEnum")
        }
    }
}