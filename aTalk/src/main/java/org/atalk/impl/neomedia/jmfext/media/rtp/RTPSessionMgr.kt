/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.rtp

import net.sf.fmj.media.rtp.GenerateSSRCCause
import net.sf.fmj.media.rtp.RTPSessionMgr
import org.atalk.service.neomedia.SSRCFactory

/**
 * Implements [javax.media.rtp.RTPManager] for the purposes of the libjitsi library in general
 * and the neomedia package in particular.
 *
 *
 * Allows `MediaStream` to optionally utilize [SSRCFactory].
 *
 *
 * @author Lyubomir Marinov
 */
class RTPSessionMgr
/**
 * Initializes a new `RTPSessionMgr` instance.
 */
    : RTPSessionMgr() {
    /**
     * Gets the `SSRCFactory` utilized by this instance to generate new synchronization
     * source (SSRC) identifiers.
     *
     * @return the `SSRCFactory` utilized by this instance or `null` if this instance
     * employs internal logic to generate new synchronization source (SSRC) identifiers
     */
    /**
     * Sets the `SSRCFactory` to be utilized by this instance to generate new
     * synchronization source (SSRC) identifiers.
     *
     * @param ssrcFactory
     * the `SSRCFactory` to be utilized by this instance to generate new
     * synchronization source (SSRC) identifiers or `null` if this instance is to
     * employ internal logic to generate new synchronization source (SSRC) identifiers
     */
    /**
     * The `SSRCFactory` to be utilized by this instance to generate new synchronization
     * source (SSRC) identifiers. If `null`, this instance will employ internal logic to
     * generate new synchronization source (SSRC) identifiers.
     */
    var sSRCFactory: SSRCFactory? = null

    /**
     * {@inheritDoc}
     */
    override fun generateSSRC(cause: GenerateSSRCCause): Long {
        val ssrcFactory = sSRCFactory
        return ssrcFactory?.generateSSRC(cause.name) ?: super.generateSSRC(cause)
    }
}