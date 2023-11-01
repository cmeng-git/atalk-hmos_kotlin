/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Fourth order ARMA filter. Internally operates as two biquad filters in sequence. Coeffients are
 * stored in a packed format: { B1_Q14[1], B2_Q14[1], -A1_Q14[1], -A1_Q14[2], -A2_Q14[1],
 * -A2_Q14[2], gain_Q16 } where it is assumed that B*_Q14[0], B*_Q14[2], A*_Q14[0] are all 16384.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object ResamplerPrivateARMA4 {
    /**
     *
     * @param S
     * State vector [ 4 ].
     * @param S_offset
     * offset of valid data.
     * @param out
     * Output signal.
     * @param out_offset
     * offset of valid data.
     * @param in
     * Input signal.
     * @param in_offset
     * offset of valid data.
     * @param Coef
     * ARMA coefficients [ 7 ].
     * @param Coef_offset
     * offset of valid data.
     * @param len
     * Signal length.
     */
    fun SKP_Silk_resampler_private_ARMA4(S: IntArray?,  /* I/O: State vector [ 4 ] */
            S_offset: Int, out: ShortArray,  /* O: Output signal */
            out_offset: Int, `in`: ShortArray,  /* I: Input signal */
            in_offset: Int, Coef: ShortArray?,  /* I: ARMA coefficients [ 7 ] */
            Coef_offset: Int, len: Int /* I: Signal length */
    ) {
        var k: Int
        var in_Q8: Int
        var out1_Q8: Int
        var out2_Q8: Int
        var X: Int
        k = 0
        while (k < len) {
            in_Q8 = `in`[in_offset + k].toInt() shl 8

            /* Outputs of first and second biquad */
            out1_Q8 = in_Q8 + (S!![S_offset] shl 2)
            out2_Q8 = out1_Q8 + (S[S_offset + 2] shl 2)

            /* Update states, which are stored in Q6. Coefficients are in Q14 here */
            X = Macros.SKP_SMLAWB(S[S_offset + 1], in_Q8, Coef!![Coef_offset].toInt())
            S[S_offset] = Macros.SKP_SMLAWB(X, out1_Q8, Coef[Coef_offset + 2].toInt())
            X = Macros.SKP_SMLAWB(S[S_offset + 3], out1_Q8, Coef[Coef_offset + 1].toInt())
            S[S_offset + 2] = Macros.SKP_SMLAWB(X, out2_Q8, Coef[Coef_offset + 4].toInt())
            S[S_offset + 1] = Macros.SKP_SMLAWB(in_Q8 shr 2, out1_Q8, Coef[Coef_offset + 3].toInt())
            S[S_offset + 3] = Macros.SKP_SMLAWB(out1_Q8 shr 2, out2_Q8, Coef[Coef_offset + 5].toInt())

            /* Apply gain and store to output. The coefficient is in Q16 */
            out[out_offset + k] = SigProcFIX.SKP_SAT16(Macros.SKP_SMLAWB(128, out2_Q8,
                    Coef[Coef_offset + 6].toInt()) shr 8).toShort()
            k++
        }
    }
}