/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Convert input to a linear scale.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object Log2lin {
    /**
     * Approximation of 2^() (very close inverse of Silk_lin2log.SKP_Silk_lin2log()) Convert input
     * to a linear scale.
     *
     * @param inLog_Q7
     * Input on log scale
     * @return
     */
    fun SKP_Silk_log2lin(inLog_Q7: Int): Int /* I: Input on log scale */ {
        var out: Int
        val frac_Q7: Int
        if (inLog_Q7 < 0) {
            return 0
        }
        out = 1 shl (inLog_Q7 shr 7)
        frac_Q7 = inLog_Q7 and 0x7F
        out = if (inLog_Q7 < 2048) {
            /* Piece-wise parabolic approximation */
            SigProcFIX.SKP_ADD_RSHIFT(
                    out,
                    SigProcFIX.SKP_MUL(out,
                            Macros.SKP_SMLAWB(frac_Q7, SigProcFIX.SKP_MUL(frac_Q7, 128 - frac_Q7), -174)), 7)
        } else {
            /* Piece-wise parabolic approximation */
            SigProcFIX.SKP_MLA(out, SigProcFIX.SKP_RSHIFT(out, 7),
                    Macros.SKP_SMLAWB(frac_Q7, SigProcFIX.SKP_MUL(frac_Q7, 128 - frac_Q7), -174))
        }
        return out
    }
}