/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Split signal into two decimated bands using first-order allpass filters.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object AnaFiltBank1 {
    /* Coefficients for 2-band filter bank based on first-order allpass filters */ // old
    private val A_fb1_20 = shortArrayOf((5394 shl 1).toShort())
    private val A_fb1_21 = shortArrayOf((20623 shl 1).toShort()) /*
																	 * wrap-around to negative
																	 * number is intentional
																	 */

    /**
     * Split signal into two decimated bands using first-order allpass filters.
     *
     * @param in
     * Input signal [N].
     * @param in_offset
     * offset of valid data.
     * @param S
     * State vector [2].
     * @param S_offset
     * offset of valid data.
     * @param outL
     * Low band [N/2].
     * @param outL_offset
     * offset of valid data.
     * @param outH
     * High band [N/2].
     * @param outH_offset
     * offset of valid data.
     * @param scratch
     * Scratch memory [3*N/2].
     * @param N
     * Number of input samples.
     */
    fun SKP_Silk_ana_filt_bank_1(`in`: ShortArray?,  /* I: Input signal [N] */
            in_offset: Int, S: IntArray?,  /* I/O: State vector [2] */
            S_offset: Int, outL: ShortArray,  /* O: Low band [N/2] */
            outL_offset: Int, outH: ShortArray,  /* O: High band [N/2] */
            outH_offset: Int, scratch: IntArray?,  /* I: Scratch memory [3*N/2] */ // todo: remove - no longer
            // used
            N: Int /* I: Number of input samples */
    ) {
        val N2 = N shr 1
        var in32: Int
        var X: Int
        var Y: Int
        var out_1: Int
        var out_2: Int

        /* Internal variables and state are in Q10 format */
        var k: Int = 0
        while (k < N2) {

            /* Convert to Q10 */
            in32 = `in`!![in_offset + 2 * k].toInt() shl 10

            /* All-pass section for even input sample */
            Y = in32 - S!![S_offset + 0]
            X = Macros.SKP_SMLAWB(Y, Y, A_fb1_21[0].toInt())
            out_1 = S[S_offset + 0] + X
            S[S_offset + 0] = in32 + X

            /* Convert to Q10 */
            in32 = `in`[in_offset + 2 * k + 1].toInt() shl 10

            /* All-pass section for odd input sample, and add to output of previous section */
            Y = in32 - S[S_offset + 1]
            X = Macros.SKP_SMULWB(Y, A_fb1_20[0].toInt())
            out_2 = S[S_offset + 1] + X
            S[S_offset + 1] = in32 + X

            /* Add/subtract, convert back to int16 and store to output */
            outL[outL_offset + k] = SigProcFIX.SKP_SAT16(SigProcFIX.SKP_RSHIFT_ROUND(out_2
                    + out_1, 11)).toShort()
            outH[outH_offset + k] = SigProcFIX.SKP_SAT16(SigProcFIX.SKP_RSHIFT_ROUND(out_2
                    - out_1, 11)).toShort()
            k++
        }
    }
}