/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Convert NLSF parameters to stable AR prediction filter coefficients.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object NLSF2AStable {
    /**
     * Convert NLSF parameters to stable AR prediction filter coefficients.
     *
     * @param pAR_Q12
     * Stabilized AR coefs [LPC_order].
     * @param pNLSF
     * NLSF vector [LPC_order].
     * @param LPC_order
     * LPC/LSF order.
     */
    fun SKP_Silk_NLSF2A_stable(pAR_Q12: ShortArray?,  /* O Stabilized AR coefs [LPC_order] */
            pNLSF: IntArray?,  /* I NLSF vector [LPC_order] */
            LPC_order: Int /* I LPC/LSF order */
    ) {
        var i: Int
        var invGain_Q30: Int
        val invGain_Q30_ptr = IntArray(1)
        NLSF2A.SKP_Silk_NLSF2A(pAR_Q12, pNLSF, LPC_order)

        /* Ensure stable LPCs */
        i = 0
        while (i < Define.MAX_LPC_STABILIZE_ITERATIONS) {
            if (LPCInvPredGain.SKP_Silk_LPC_inverse_pred_gain(invGain_Q30_ptr, pAR_Q12, LPC_order) == 1) {
                invGain_Q30 = invGain_Q30_ptr[0]
                Bwexpander.SKP_Silk_bwexpander(pAR_Q12, LPC_order, 65536 - Macros.SKP_SMULBB(66, i))
            /*
             * 66_Q16 = 0.001
             */
            } else {
                invGain_Q30 = invGain_Q30_ptr[0]
                break
            }
            i++
        }

        /* Reached the last iteration */
        if (i == Define.MAX_LPC_STABILIZE_ITERATIONS) {
            i = 0
            while (i < LPC_order) {
                pAR_Q12!![i] = 0
                i++
            }
        }
    }
}