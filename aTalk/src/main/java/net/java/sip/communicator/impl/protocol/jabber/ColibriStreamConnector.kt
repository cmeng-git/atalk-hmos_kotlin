/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import org.atalk.service.neomedia.StreamConnector
import org.atalk.service.neomedia.StreamConnectorDelegate

/**
 * Implements a `StreamConnector` which allows sharing a specific `StreamConnector`
 * instance among multiple `TransportManager`s for the purposes of the Jitsi Videobridge.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class ColibriStreamConnector
/**
 * Initializes a new `ColibriStreamConnector` instance which is to share a specific
 * `StreamConnector` instance among multiple `TransportManager`s for the purposes
 * of the Jitsi Videobridge.
 *
 * @param streamConnector the `StreamConnector` instance to be shared by the new instance among multiple
 * `TransportManager`s for the purposes of the Jitsi Videobridge
 */
(streamConnector: StreamConnector?) : StreamConnectorDelegate<StreamConnector?>(streamConnector) {
    /**
     * {@inheritDoc}
     *
     * Overrides [StreamConnectorDelegate.close] in order to prevent the closing of the
     * `StreamConnector` wrapped by this instance because the latter is shared and it is not
     * clear whether no `TransportManager` is using it.
     */
    override fun close() {
        /*
         * Do not close the shared StreamConnector because it is not clear whether no
         * TransportManager is using it.
         */
    }

    /**
     * {@inheritDoc}
     *
     * Invokes [.close] on this instance when it is clear that no `TransportManager`
     * is using it in order to release the resources allocated by this instance throughout its life
     * time (that need explicit disposal).
     */
    @Throws(Throwable::class)
    protected fun finalize() {
        try {
            /*
             * Close the shared StreamConnector because it is clear that no TrasportManager is using it.
             */
            super.close()
        } finally {
            this.finalize()
        }
    }
}