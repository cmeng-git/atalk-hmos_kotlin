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
object Bwexpander32 {
    /**
     * Chirp (bandwidth expand) LP AR filter.
     *
     * @param ar
     * AR filter to be expanded (without leading 1).
     * @param d
     * Length of ar.
     * @param chirp_Q16
     * Chirp factor in Q16.
     */
    fun SKP_Silk_bwexpander_32(ar: IntArray,  /* I/O AR filter to be expanded (without leading 1) */
            d: Int,  /* I Length of ar */
            chirp_Q16: Int /* I Chirp factor in Q16 */
    ) {
        var i: Int
        var tmp_chirp_Q16: Int
        tmp_chirp_Q16 = chirp_Q16
        i = 0
        while (i < d - 1) {
            ar[i] = Macros.SKP_SMULWW(ar[i], tmp_chirp_Q16)
            tmp_chirp_Q16 = Macros.SKP_SMULWW(chirp_Q16, tmp_chirp_Q16)
            i++
        }
        ar[d - 1] = Macros.SKP_SMULWW(ar[d - 1], tmp_chirp_Q16)
    }
}