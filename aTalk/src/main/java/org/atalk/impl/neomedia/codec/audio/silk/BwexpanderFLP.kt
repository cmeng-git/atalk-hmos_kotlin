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
object BwexpanderFLP {
    /**
     * Chirp (bw expand) LP AR filter.
     *
     * @param ar
     * AR filter to be expanded (without leading 1).
     * @param ar_offset
     * offset of valid data.
     * @param d
     * length of ar.
     * @param chirp
     * chirp factor (typically in range (0..1) ).
     */
    fun SKP_Silk_bwexpander_FLP(ar: FloatArray?,  /*
													 * I/O AR filter to be expanded (without leading
													 * 1)
													 */
            ar_offset: Int, d: Int,  /* I length of ar */
            chirp: Float /* I chirp factor (typically in range (0..1) ) */
    ) {
        var i: Int
        var cfac = chirp
        i = 0
        while (i < d - 1) {
            ar!![ar_offset + i] *= cfac
            cfac *= chirp
            i++
        }
        ar!![ar_offset + d - 1] *= cfac
    }
}