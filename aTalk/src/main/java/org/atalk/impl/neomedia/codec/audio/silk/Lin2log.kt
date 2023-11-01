/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Convert input to a log scale
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object Lin2log {
    /**
     * Approximation of 128 * log2() (very close inverse of approx 2^() below) Convert input to a
     * log scale.
     *
     * @param inLin
     * Input in linear scale
     * @return
     */
    fun SKP_Silk_lin2log(inLin: Int): Int /* I: Input in linear scale */ {
        val lz: Int
        val frac_Q7: Int
        val lz_ptr = IntArray(1)
        val frac_Q7_ptr = IntArray(1)
        Inlines.SKP_Silk_CLZ_FRAC(inLin, lz_ptr, frac_Q7_ptr)
        lz = lz_ptr[0]
        frac_Q7 = frac_Q7_ptr[0]

        /* Piece-wise parabolic approximation */
        return SigProcFIX.SKP_LSHIFT(31 - lz, 7) + Macros.SKP_SMLAWB(frac_Q7,
                SigProcFIX.SKP_MUL(frac_Q7, 128 - frac_Q7), 179)
    }
}