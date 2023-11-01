/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import net.sf.fmj.media.rtp.SSRCCache
import org.atalk.impl.neomedia.rtp.StreamRTPManager

/**
 * Represents an RTP translator which forwards RTP and RTCP traffic between multiple
 * `MediaStream`s.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface RTPTranslator {
    /**
     * Finds the `StreamRTPManager` which receives a specific SSRC.
     *
     * @param receiveSSRC
     * the SSRC of the RTP stream received by the `StreamRTPManager` to be returned
     * @return the `StreamRTPManager` which receives `receiveSSRC` of `null`
     */
    fun findStreamRTPManagerByReceiveSSRC(receiveSSRC: Int): StreamRTPManager?

    /**
     * Returns a list of `StreamRTPManager`s currently attached to this
     * `RTPTranslator`. This is admittedly wrong, to expose the bare `SSRCCache` to
     * the use of of the `StreamRTPManager`. We should find a better way of exposing this
     * information. Currently it is necessary for RTCP termination.
     *
     * @return a list of `StreamRTPManager`s currently attached to this
     * `RTPTranslator`.
     */
    fun getStreamRTPManagers(): List<StreamRTPManager>

    /**
     * Provides access to the underlying `SSRCCache` that holds statistics information about
     * each SSRC that we receive.
     *
     * @return the underlying `SSRCCache` that holds statistics information about each SSRC
     * that we receive.
     */
    val sSRCCache: SSRCCache?

    /**
     * Defines a packet filter which allows an observer of an `RTPTranslator` to disallow the
     * writing of specific packets into a specific destination identified by a `MediaStream`.
     */
    interface WriteFilter {
        fun accept(
                source: MediaStream?, pkt: RawPacket?,
                destination: MediaStream?, data: Boolean,
        ): Boolean
    }

    /**
     * Adds a `WriteFilter` to this `RTPTranslator`.
     *
     * @param writeFilter
     * the `WriteFilter` to add to this `RTPTranslator`
     */
    fun addWriteFilter(writeFilter: WriteFilter?)

    /**
     * Releases the resources allocated by this instance in the course of its execution and prepares
     * it to be garbage collected.
     */
    fun dispose()

    /**
     * Removes a `WriteFilter` from this `RTPTranslator`.
     *
     * @param writeFilter
     * the `WriteFilter` to remove from this `RTPTranslator`
     */
    fun removeWriteFilter(writeFilter: WriteFilter?)
}