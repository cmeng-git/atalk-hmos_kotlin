/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Chirp (bandwidth expand) LP AR filter
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object Bwexpander {
    /**
     * Chirp (bandwidth expand) LP AR filter.
     *
     * @param ar
     * AR filter to be expanded (without leading 1).
     * @param d
     * Length of ar.
     * @param chirp_Q16
     * Chirp factor (typically in the range 0 to 1).
     */
    fun SKP_Silk_bwexpander(ar: ShortArray?,  /* I/O AR filter to be expanded (without leading 1) */
            d: Int,  /* I Length of ar */
            chirp_Q16: Int /* I Chirp factor (typically in the range 0 to 1) */
    ) {
        var chirp_Q16 = chirp_Q16
        var i: Int
        val chirp_minus_one_Q16: Int
        chirp_minus_one_Q16 = chirp_Q16 - 65536

        /* NB: Dont use SKP_SMULWB, instead of SKP_RSHIFT_ROUND( SKP_MUL() , 16 ), below. */
        /* Bias in SKP_SMULWB can lead to unstable filters */
        i = 0
        while (i < d - 1) {
            ar!![i] = SigProcFIX.SKP_RSHIFT_ROUND(chirp_Q16 * ar[i], 16).toShort()
            chirp_Q16 += SigProcFIX.SKP_RSHIFT_ROUND(chirp_Q16 * chirp_minus_one_Q16, 16)
            i++
        }
        ar!![d - 1] = SigProcFIX.SKP_RSHIFT_ROUND(chirp_Q16 * ar[d - 1], 16).toShort()
    }
}