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
object LBRRReset {
    /**
     * Resets LBRR buffer, used if packet size changes.
     *
     * @param psEncC
     * state
     */
    fun SKP_Silk_LBRR_reset(psEncC: SKP_Silk_encoder_state? /* I/O state */
    ) {
        var i: Int
        i = 0
        while (i < Define.MAX_LBRR_DELAY) {
            psEncC!!.LBRR_buffer[i]!!.usage = Define.SKP_SILK_NO_LBRR
            i++
        }
    }
}