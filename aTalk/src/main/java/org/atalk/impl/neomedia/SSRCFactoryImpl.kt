/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import net.sf.fmj.media.rtp.GenerateSSRCCause
import org.atalk.service.neomedia.SSRCFactory
import java.util.*

/**
 * An `SSRCFactory` implementation which allows the first generated SSRC to be set by the
 * user.
 *
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class SSRCFactoryImpl(initialLocalSSRC: Long) : SSRCFactory {
    private var i = 0
    private var initialLocalSSRC = -1L

    /**
     * The `Random` instance used by this `SSRCFactory` to generate new
     * synchronization source (SSRC) identifiers.
     */
    private val random = Random()

    init {
        this.initialLocalSSRC = initialLocalSSRC
    }

    private fun doGenerateSSRC(): Int {
        return random.nextInt()
    }

    /**
     * {@inheritDoc}
     */
    override fun generateSSRC(cause: String): Long {
        // XXX(gp) the problem here is that if the initialLocalSSRC changes,
        // the bridge is unaware of the change. TAG(cat4-local-ssrc-hurricane).
        if (initialLocalSSRC != -1L) {
            if (i++ == 0) return initialLocalSSRC.toInt().toLong() else if (cause == GenerateSSRCCause.REMOVE_SEND_STREAM.name) return Long.MAX_VALUE
        }
        return doGenerateSSRC().toLong()
    }
}