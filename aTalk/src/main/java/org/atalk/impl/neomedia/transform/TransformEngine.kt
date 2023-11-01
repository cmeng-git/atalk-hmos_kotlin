/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform

/**
 * Defines how to get `PacketTransformer`s for RTP and RTCP packets. A single
 * `PacketTransformer` can be used for both RTP and RTCP packets or there can be two separate
 * `PacketTransformer`s.
 *
 * @author Bing SU (nova.su@gmail.com)
 */
interface TransformEngine {
    /**
     * Gets the `PacketTransformer` for RTP packets.
     *
     * @return the `PacketTransformer` for RTP packets
     */
    val rtpTransformer: PacketTransformer?

    /**
     * Gets the `PacketTransformer` for RTCP packets.
     *
     * @return the `PacketTransformer` for RTCP packets
     */
    val rtcpTransformer: PacketTransformer?
}