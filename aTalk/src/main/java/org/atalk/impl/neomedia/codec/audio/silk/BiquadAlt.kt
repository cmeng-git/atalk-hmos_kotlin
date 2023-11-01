/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Second order ARMA filter, alternative implementation.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object BiquadAlt {
    /**
     * Second order ARMA filter, alternative implementation.
     *
     * @param in
     * Input signal.
     * @param in_offset
     * offset of valid data.
     * @param B_Q28
     * MA coefficients [3].
     * @param A_Q28
     * AR coefficients [2].
     * @param S
     * State vector [2].
     * @param out
     * Output signal.
     * @param out_offset
     * offset of valid data.
     * @param len
     * Signal length (must be even).
     */
    fun SKP_Silk_biquad_alt(`in`: ShortArray?,  /* I: Input signal */
            in_offset: Int, B_Q28: IntArray,  /* I: MA coefficients [3] */
            A_Q28: IntArray,  /* I: AR coefficients [2] */
            S: IntArray?,  /* I/O: State vector [2] */
            out: ShortArray,  /* O: Output signal */
            out_offset: Int, len: Int /* I: Signal length (must be even) */
    ) {
        /* DIRECT FORM II TRANSPOSED (uses 2 element state vector) */
        var inval: Int
        var out32_Q14: Int

        /* Negate A_Q28 values and split in two parts */
        val A0_L_Q28 = -A_Q28[0] and 0x00003FFF /* lower part */
        val A0_U_Q28 = -A_Q28[0] shr 14 /* upper part */
        val A1_L_Q28 = -A_Q28[1] and 0x00003FFF /* lower part */
        val A1_U_Q28 = -A_Q28[1] shr 14 /* upper part */
        var k = 0
        while (k < len) {

            /* S[ 0 ], S[ 1 ]: Q12 */
            inval = `in`!![in_offset + k].toInt()
            out32_Q14 = Macros.SKP_SMLAWB(S!![0], B_Q28[0], inval) shl 2
            S[0] = S[1] + (Macros.SKP_SMULWB(out32_Q14, A0_L_Q28) shr 14)
            S[0] = Macros.SKP_SMLAWB(S[0], out32_Q14, A0_U_Q28)
            S[0] = Macros.SKP_SMLAWB(S[0], B_Q28[1], inval)
            S[1] = Macros.SKP_SMULWB(out32_Q14, A1_L_Q28) shr 14
            S[1] = Macros.SKP_SMLAWB(S[1], out32_Q14, A1_U_Q28)
            S[1] = Macros.SKP_SMLAWB(S[1], B_Q28[2], inval)

            /* Scale back to Q0 and saturate */
            out[out_offset + k] = SigProcFIX.SKP_SAT16((out32_Q14 shr 14) + 2).toShort()
            k++
        }
    }
}