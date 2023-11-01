/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * 16th order AR filter. Coefficients are in Q12.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object LPCSynthesisOrder16 {
    /**
     * 16th order AR filter
     *
     * @param in
     * excitation signal
     * @param A_Q12
     * AR coefficients [16], between -8_Q0 and 8_Q0
     * @param Gain_Q26
     * gain
     * @param S
     * state vector [16]
     * @param out
     * output signal
     * @param len
     * signal length, must be multiple of 16
     */
    fun SKP_Silk_LPC_synthesis_order16(`in`: ShortArray,  /* I: excitation signal */
            A_Q12: ShortArray,  /* I: AR coefficients [16], between -8_Q0 and 8_Q0 */
            Gain_Q26: Int,  /* I: gain */
            S: IntArray?,  /* I/O: state vector [16] */
            out: ShortArray,  /* O: output signal */
            len: Int /* I: signal length, must be multiple of 16 */
    ) {
        var k: Int
        var SA: Int
        var SB: Int
        var out32_Q10: Int
        var out32: Int
        k = 0
        while (k < len) {

            /* unrolled loop: prolog */
            /* multiply-add two prediction coefficients per iteration */
            SA = S!![15]
            SB = S[14]
            S[14] = SA
            out32_Q10 = Macros.SKP_SMULWB(SA, A_Q12[0].toInt())
            out32_Q10 = SigProcFIX.SKP_SMLAWB_ovflw(out32_Q10, SB, A_Q12[1].toInt())
            SA = S[13]
            S[13] = SB

            /* unrolled loop: main loop */
            SB = S[12]
            S[12] = SA
            out32_Q10 = SigProcFIX.SKP_SMLAWB_ovflw(out32_Q10, SA, A_Q12[2].toInt())
            out32_Q10 = SigProcFIX.SKP_SMLAWB_ovflw(out32_Q10, SB, A_Q12[3].toInt())
            SA = S[11]
            S[11] = SB
            SB = S[10]
            S[10] = SA
            out32_Q10 = SigProcFIX.SKP_SMLAWB_ovflw(out32_Q10, SA, A_Q12[4].toInt())
            out32_Q10 = SigProcFIX.SKP_SMLAWB_ovflw(out32_Q10, SB, A_Q12[5].toInt())
            SA = S[9]
            S[9] = SB
            SB = S[8]
            S[8] = SA
            out32_Q10 = SigProcFIX.SKP_SMLAWB_ovflw(out32_Q10, SA, A_Q12[6].toInt())
            out32_Q10 = SigProcFIX.SKP_SMLAWB_ovflw(out32_Q10, SB, A_Q12[7].toInt())
            SA = S[7]
            S[7] = SB
            SB = S[6]
            S[6] = SA
            out32_Q10 = SigProcFIX.SKP_SMLAWB_ovflw(out32_Q10, SA, A_Q12[8].toInt())
            out32_Q10 = SigProcFIX.SKP_SMLAWB_ovflw(out32_Q10, SB, A_Q12[9].toInt())
            SA = S[5]
            S[5] = SB
            SB = S[4]
            S[4] = SA
            out32_Q10 = SigProcFIX.SKP_SMLAWB_ovflw(out32_Q10, SA, A_Q12[10].toInt())
            out32_Q10 = SigProcFIX.SKP_SMLAWB_ovflw(out32_Q10, SB, A_Q12[11].toInt())
            SA = S[3]
            S[3] = SB
            SB = S[2]
            S[2] = SA
            out32_Q10 = SigProcFIX.SKP_SMLAWB_ovflw(out32_Q10, SA, A_Q12[12].toInt())
            out32_Q10 = SigProcFIX.SKP_SMLAWB_ovflw(out32_Q10, SB, A_Q12[13].toInt())
            SA = S[1]
            S[1] = SB

            /* unrolled loop: epilog */
            SB = S[0]
            S[0] = SA
            out32_Q10 = SigProcFIX.SKP_SMLAWB_ovflw(out32_Q10, SA, A_Q12[14].toInt())
            out32_Q10 = SigProcFIX.SKP_SMLAWB_ovflw(out32_Q10, SB, A_Q12[15].toInt())

            /* unrolled loop: end */
            /* apply gain to excitation signal and add to prediction */
            out32_Q10 = Macros.SKP_ADD_SAT32(out32_Q10, Macros.SKP_SMULWB(Gain_Q26, `in`[k].toInt()))

            /* scale to Q0 */
            out32 = SigProcFIX.SKP_RSHIFT_ROUND(out32_Q10, 10)

            /* saturate output */
            out[k] = SigProcFIX.SKP_SAT16(out32).toShort()

            /* move result into delay line */
            S[15] = SigProcFIX.SKP_LSHIFT_SAT32(out32_Q10, 4)
            k++
        }
    }
}