/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.argdelegation

/**
 * This interface is meant to be implemented by all bundles that wish to handle
 * URIs passed as invocation arguments.
 *
 * @author Emil Ivov <emcho at sip-communicator.org>
</emcho> */
interface UriHandler {
    /**
     * Returns the protocols that this handler is responsible for.
     *
     * @return protocols that this handler is responsible for
     */
    val protocol: Array<String>?

    /**
     * Handles/opens the URI.
     *
     * @param uri the URI that the handler has to open.
     */
    fun handleUri(uri: String?)

    companion object {
        /**
         * The name of the property that we use in the service registration
         * properties to store a protocol name when registering `UriHandler`s
         */
        const val PROTOCOL_PROPERTY = "ProtocolName"
    }
}