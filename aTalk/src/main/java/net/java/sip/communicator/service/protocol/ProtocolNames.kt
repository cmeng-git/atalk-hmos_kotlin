/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * Specifies the names of some of the most popular instant messaging protocols. These names should
 * be used when registering an implementation of a protocol with the osgi framework. These names
 * must be set in the properties dictionary that one specifies when registering an OSGI service.
 * When setting one of these names, a protocol implementor must map it against the
 * ProtocolProviderFactory.PROTOCOL_PROPERTY_NAME key.
 *
 * @author Emil Ivov
 */
interface ProtocolNames {
    companion object {
        /**
         * The SIP (and SIMPLE) protocols.
         */
        const val SIP = "SIP"

        /**
         * Returns the protocol name as listed in "ProtocolNames" or just the name of the service.
         *
         * @return the protocol name
         */
        /**
         * The Jabber protocol.
         */
        const val JABBER = "Jabber"

        /**
         * The IRC protocol.
         */
        const val IRC = "IRC"

        /**
         * The ICQ service protocol.
         */
        const val ICQ = "ICQ"

        /**
         * The AIM service protocol.
         */
        const val AIM = "AIM"

        /**
         * The SIP Communicator MOCK protocol.
         */
        const val SIP_COMMUNICATOR_MOCK = "sip-communicator-mock"

        /**
         * The SSH protocol.
         */
        const val SSH = "SSH"

        /**
         * The Gibberish protocol.
         */
        const val GIBBERISH = "Gibberish"
    }
}