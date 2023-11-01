/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * even order AR filter. Coefficients are in Q12
 *
 * @author Jing Dai
 */
object LPCSynthesisFilter {
    /**
     * even order AR filter.
     *
     * @param in
     * excitation signal
     * @param A_Q12
     * AR coefficients [Order], between -8_Q0 and 8_Q0
     * @param Gain_Q26
     * gain
     * @param S
     * state vector [Order]
     * @param out
     * output signal
     * @param len
     * signal length
     * @param Order
     * filter order, must be even
     */
    fun SKP_Silk_LPC_synthesis_filter(`in`: ShortArray,  /* I: excitation signal */
            A_Q12: ShortArray,  /* I: AR coefficients [Order], between -8_Q0 and 8_Q0 */
            Gain_Q26: Int,  /* I: gain */
            S: IntArray?,  /* I/O: state vector [Order] */
            out: ShortArray,  /* O: output signal */
            len: Int,  /* I: signal length */
            Order: Int /* I: filter order, must be even */
    ) {
        var k: Int
        var j: Int
        var idx: Int
        val Order_half = Order shr 1
        var SA: Int
        var SB: Int
        var out32_Q10: Int
        var out32: Int

        /* Order must be even */
        Typedef.SKP_assert(2 * Order_half == Order)

        /* S[] values are in Q14 */
        k = 0
        while (k < len) {
            SA = S!![Order - 1]
            out32_Q10 = 0
            j = 0
            while (j < Order_half - 1) {
                idx = Macros.SKP_SMULBB(2, j) + 1
                SB = S[Order - 1 - idx]
                S[Order - 1 - idx] = SA
                out32_Q10 = Macros.SKP_SMLAWB(out32_Q10, SA, A_Q12[j shl 1].toInt())
                out32_Q10 = Macros.SKP_SMLAWB(out32_Q10, SB, A_Q12[(j shl 1) + 1].toInt())
                SA = S[Order - 2 - idx]
                S[Order - 2 - idx] = SB
                j++
            }

            /* unrolled loop: epilog */
            SB = S[0]
            S[0] = SA
            out32_Q10 = Macros.SKP_SMLAWB(out32_Q10, SA, A_Q12[Order - 2].toInt())
            out32_Q10 = Macros.SKP_SMLAWB(out32_Q10, SB, A_Q12[Order - 1].toInt())
            /* apply gain to excitation signal and add to prediction */
            out32_Q10 = Macros.SKP_ADD_SAT32(out32_Q10, Macros.SKP_SMULWB(Gain_Q26, `in`[k].toInt()))

            /* scale to Q0 */
            out32 = SigProcFIX.SKP_RSHIFT_ROUND(out32_Q10, 10)

            /* saturate output */
            out[k] = SigProcFIX.SKP_SAT16(out32).toShort()

            /* move result into delay line */
            S[Order - 1] = SigProcFIX.SKP_LSHIFT_SAT32(out32_Q10, 4)
            k++
        }
    }
}