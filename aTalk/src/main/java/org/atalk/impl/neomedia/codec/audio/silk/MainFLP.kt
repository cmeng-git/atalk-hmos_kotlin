/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object MainFLP {
    /**
     * using log2() helps the fixed-point conversion.
     *
     * @param x
     * @return
     */
    fun SKP_Silk_log2(x: Double): Float {
        return (3.32192809488736 * Math.log10(x)).toFloat()
    }
}