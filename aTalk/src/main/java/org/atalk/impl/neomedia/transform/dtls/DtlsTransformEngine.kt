/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.dtls

import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.service.neomedia.SrtpControl

/**
 * Implements [SrtpControl.TransformEngine] (and, respectively, [TransformEngine]) for DTLS-SRTP.
 *
 * @author Lyubomir Marinov
 */
/**
 * Initializes a new `DtlsTransformEngine` instance.
 */
class DtlsTransformEngine
(
        /**
         * The `DtlsControl` which has initialized this instance.
         */
        val dtlsControl: DtlsControlImpl) : SrtpControl.TransformEngine {

    /**
     * The indicator which determines whether [SrtpControl.TransformEngine.cleanup] has been
     * invoked on this instance to prepare it for garbage collection.
     */
    private var disposed = false
    /**
     * Gets the `DtlsControl` which has initialized this instance.
     *
     * @return the `DtlsControl` which has initialized this instance
     */

    /**
     * The `PacketTransformer`s of this `TransformEngine` for data/RTP and control/RTCP packets.
     */
    private val packetTransformers = arrayOfNulls<DtlsPacketTransformer>(2)

    /**
     * {@inheritDoc}
     */
    override fun cleanup() {
        disposed = true

        for (i in packetTransformers.indices) {
            val packetTransformer = packetTransformers[i]
            if (packetTransformer != null) {
                packetTransformer.close()
                packetTransformers[i] = null
            }
        }
    }

    /**
     * Initializes a new `DtlsPacketTransformer` instance which is to work on control/RTCP or data/RTP packets.
     *
     * @param componentID the ID of the component for which the new instance is to work
     * @return a new `DtlsPacketTransformer` instance which is to work on control/RTCP or
     * data/RTP packets (in accord with `data`)
     */
    private fun createPacketTransformer(componentID: Int): DtlsPacketTransformer {
        return DtlsPacketTransformer(this, componentID)
    }

    /**
     * Gets the `PacketTransformer` of this `TransformEngine` which is to work or
     * works for the component with a specific ID.
     *
     * @param componentID the ID of the component for which the returned `PacketTransformer` is to work or works
     * @return the `PacketTransformer`, if any, which is to work or works for the component
     * with the specified `componentID`
     */
    private fun getPacketTransformer(componentID: Int): DtlsPacketTransformer? {
        var packetTransformer = packetTransformers[componentID]

        if (packetTransformer == null && !disposed) {
            packetTransformer = createPacketTransformer(componentID)
            if (packetTransformer != null)
                packetTransformers[componentID] = packetTransformer
        }
        return packetTransformer
    }

    /**
     * Gets the properties of `DtlsControlImpl` and their values which
     * [.dtlsControl] shares with this instance and [DtlsPacketTransformer].
     *
     * @return the properties of `DtlsControlImpl` and their values which
     * `dtlsControl` shares with this instance and `DtlsPacketTransformer`
     */
    val properties: Properties
        get() = dtlsControl.properties

    /**
     * {@inheritDoc}
     */
    override val rtcpTransformer: PacketTransformer?
        get() = getPacketTransformer(COMPONENT_RTCP)

    /**
     * {@inheritDoc}
     */
    override val rtpTransformer: PacketTransformer?
        get() = getPacketTransformer(COMPONENT_RTP)

    companion object {
        /**
         * The index of the RTP component.
         */
        const val COMPONENT_RTP = 0

        /**
         * The index of the RTCP component.
         */
        const val COMPONENT_RTCP = 1
    }
}