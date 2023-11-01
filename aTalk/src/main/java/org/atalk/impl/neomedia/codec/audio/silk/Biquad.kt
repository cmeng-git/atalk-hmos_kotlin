/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Second order ARMA filter Can handle slowly varying filter coefficients
 *
 * @author Jing Dai
 */
object Biquad {
    /**
     * Second order ARMA filter Can handle slowly varying filter coefficients
     *
     * @param in
     * input signal
     * @param in_offset
     * offset of valid data.
     * @param B
     * MA coefficients, Q13 [3]
     * @param A
     * AR coefficients, Q13 [2]
     * @param S
     * state vector [2]
     * @param out
     * output signal
     * @param out_offset
     * offset of valid data.
     * @param len
     * signal length
     */
    fun SKP_Silk_biquad(`in`: ShortArray,  /* I: input signal */
            in_offset: Int, B: ShortArray?,  /* I: MA coefficients, Q13 [3] */
            A: ShortArray?,  /* I: AR coefficients, Q13 [2] */
            S: IntArray?,  /* I/O: state vector [2] */
            out: ShortArray,  /* O: output signal */
            out_offset: Int, len: Int /* I: signal length */
    ) {
        var k: Int
        var in16: Int
        val A0_neg: Int
        val A1_neg: Int
        var S0: Int
        var S1: Int
        var out32: Int
        var tmp32: Int
        S0 = S!![0]
        S1 = S[1]
        A0_neg = -A!![0]
        A1_neg = -A[1]
        k = 0
        while (k < len) {

            /* S[ 0 ], S[ 1 ]: Q13 */
            in16 = `in`[in_offset + k].toInt()
            out32 = Macros.SKP_SMLABB(S0, in16, B!![0].toInt())
            S0 = Macros.SKP_SMLABB(S1, in16, B[1].toInt())
            S0 += Macros.SKP_SMULWB(out32, A0_neg) shl 3
            S1 = Macros.SKP_SMULWB(out32, A1_neg) shl 3
            S1 = Macros.SKP_SMLABB(S1, in16, B[2].toInt())
            tmp32 = SigProcFIX.SKP_RSHIFT_ROUND(out32, 13) + 1
            out[out_offset + k] = SigProcFIX.SKP_SAT16(tmp32).toShort()
            k++
        }
        S[0] = S0
        S[1] = S1
    }
}