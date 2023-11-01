/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Compute inverse of LPC prediction gain, and test if LPC coefficients are stable (all poles within
 * unit circle)
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object LPCInvPredGain {
    const val QA = 16
    const val A_LIMIT = 65520

    /**
     * Compute inverse of LPC prediction gain, and test if LPC coefficients are stable (all poles
     * within unit circle).
     *
     * @param invGain_Q30
     * Inverse prediction gain, Q30 energy domain
     * @param A_Q12
     * Prediction coefficients, Q12 [order]
     * @param order
     * Prediction order
     * @return Returns 1 if unstable, otherwise 0
     */
    fun SKP_Silk_LPC_inverse_pred_gain(
            /* O: Returns 1 if unstable, otherwise 0 */
            invGain_Q30: IntArray,  /* O: Inverse prediction gain, Q30 energy domain */
            A_Q12: ShortArray?,  /* I: Prediction coefficients, Q12 [order] */
            order: Int, /* I: Prediction order */
    ): Int {
        var k: Int
        var n: Int
        var headrm: Int
        var rc_Q31: Int
        var rc_mult1_Q30: Int
        var rc_mult2_Q16: Int
        val Atmp_QA = Array<IntArray>(2) { IntArray(SigProcFIXConstants.SKP_Silk_MAX_ORDER_LPC) }
        var tmp_QA: Int
        var Aold_QA: IntArray
        var Anew_QA: IntArray
        Anew_QA = Atmp_QA[order and 1]
        /* Increase Q domain of the AR coefficients */
        k = 0
        while (k < order) {
            Anew_QA[k] = A_Q12!![k].toInt() shl (QA - 12)
            k++
        }

        invGain_Q30[0] = 1 shl 30
        k = order - 1
        while (k > 0) {

            /* Check for stability */
            if (Anew_QA[k] > A_LIMIT || Anew_QA[k] < -A_LIMIT) {
                return 1
            }

            /* Set RC equal to negated AR coef */
            rc_Q31 = -(Anew_QA[k] shl 31 - QA)

            /* rc_mult1_Q30 range: [ 1 : 2^30-1 ] */
            rc_mult1_Q30 = (Typedef.SKP_int32_MAX shr 1) - SigProcFIX.SKP_SMMUL(rc_Q31, rc_Q31)
            Typedef.SKP_assert(rc_mult1_Q30 > 1 shl 15) /* reduce A_LIMIT if fails */
            Typedef.SKP_assert(rc_mult1_Q30 < 1 shl 30)

            /* rc_mult2_Q16 range: [ 2^16 : SKP_int32_MAX ] */
            rc_mult2_Q16 = Inlines.SKP_INVERSE32_varQ(rc_mult1_Q30, 46) /* 16 = 46 - 30 */

            /* Update inverse gain */
            /* invGain_Q30 range: [ 0 : 2^30 ] */
            invGain_Q30[0] = SigProcFIX.SKP_SMMUL(invGain_Q30[0], rc_mult1_Q30) shl 2
            Typedef.SKP_assert(invGain_Q30[0] >= 0)
            Typedef.SKP_assert(invGain_Q30[0] <= 1 shl 30)

            /* Swap pointers */
            Aold_QA = Anew_QA
            Anew_QA = Atmp_QA[k and 1]

            /* Update AR coefficient */
            headrm = Macros.SKP_Silk_CLZ32(rc_mult2_Q16) - 1
            rc_mult2_Q16 = rc_mult2_Q16 shl headrm /* Q: 16 + headrm */
            n = 0
            while (n < k) {
                tmp_QA = Aold_QA[n] - (SigProcFIX.SKP_SMMUL(Aold_QA[k - n - 1], rc_Q31) shl 1)
                Anew_QA[n] = SigProcFIX.SKP_SMMUL(tmp_QA, rc_mult2_Q16) shl 16 - headrm
                n++
            }
            k--
        }

        /* Check for stability */
        if (Anew_QA[0] > A_LIMIT || Anew_QA[0] < -A_LIMIT) {
            return 1
        }

        /* Set RC equal to negated AR coef */
        rc_Q31 = -(Anew_QA[0] shl 31 - QA)

        /* Range: [ 1 : 2^30 ] */
        rc_mult1_Q30 = (Typedef.SKP_int32_MAX shr 1) - SigProcFIX.SKP_SMMUL(rc_Q31, rc_Q31)

        /* Update inverse gain */
        /* Range: [ 0 : 2^30 ] */
        invGain_Q30[0] = SigProcFIX.SKP_SMMUL(invGain_Q30[0], rc_mult1_Q30) shl 2
        Typedef.SKP_assert(invGain_Q30[0] >= 0)
        Typedef.SKP_assert(invGain_Q30[0] <= 1 shl 30)
        return 0
    }

    /**
     * For input in Q13 domain.
     *
     * @param invGain_Q30
     * Inverse prediction gain, Q30 energy domain.
     * @param A_Q13
     * Prediction coefficients, Q13 [order].
     * @param order
     * Prediction order.
     * @return Returns 1 if unstable, otherwise 0.
     */
    fun SKP_Silk_LPC_inverse_pred_gain_Q13(
            /* O: Returns 1 if unstable, otherwise 0 */
            invGain_Q30: IntArray,  /* O: Inverse prediction gain, Q30 energy domain */
            A_Q13: ShortArray,  /* I: Prediction coefficients, Q13 [order] */
            order: Int, /* I: Prediction order */
    ): Int {
        var k: Int
        var n: Int
        var headrm: Int
        var rc_Q31: Int
        var rc_mult1_Q30: Int
        var rc_mult2_Q16: Int
        val Atmp_QA = Array<IntArray>(2) { IntArray(SigProcFIXConstants.SKP_Silk_MAX_ORDER_LPC) }
        var tmp_QA: Int
        var Aold_QA: IntArray
        var Anew_QA: IntArray
        Anew_QA = Atmp_QA[order and 1]
        /* Increase Q domain of the AR coefficients */
        k = 0
        while (k < order) {
            Anew_QA[k] = A_Q13[k].toInt() shl (QA - 13)
            k++
        }
        invGain_Q30[0] = 1 shl 30
        k = order - 1
        while (k > 0) {

            /* Check for stability */
            if (Anew_QA[k] > A_LIMIT || Anew_QA[k] < -A_LIMIT) {
                return 1
            }

            /* Set RC equal to negated AR coef */
            rc_Q31 = -(Anew_QA[k] shl 31 - QA)

            /* rc_mult1_Q30 range: [ 1 : 2^30-1 ] */
            rc_mult1_Q30 = (Typedef.SKP_int32_MAX shr 1) - SigProcFIX.SKP_SMMUL(rc_Q31, rc_Q31)
            assert(rc_mult1_Q30 > 1 shl 15 /* reduce A_LIMIT if fails */)
            assert(rc_mult1_Q30 < 1 shl 30)

            /* rc_mult2_Q16 range: [ 2^16 : SKP_int32_MAX ] */
            rc_mult2_Q16 = Inlines.SKP_INVERSE32_varQ(rc_mult1_Q30, 46) /* 16 = 46 - 30 */

            /* Update inverse gain */
            /* invGain_Q30 range: [ 0 : 2^30 ] */
            invGain_Q30[0] = SigProcFIX.SKP_SMMUL(invGain_Q30[0], rc_mult1_Q30) shl 2
            Typedef.SKP_assert(invGain_Q30[0] >= 0)
            Typedef.SKP_assert(invGain_Q30[0] <= 1 shl 30)

            /* Swap pointers */
            Aold_QA = Anew_QA
            Anew_QA = Atmp_QA[k and 1]

            /* Update AR coefficient */
            headrm = Macros.SKP_Silk_CLZ32(rc_mult2_Q16) - 1
            rc_mult2_Q16 = rc_mult2_Q16 shl headrm /* Q: 16 + headrm */
            n = 0
            while (n < k) {
                tmp_QA = Aold_QA[n] - (SigProcFIX.SKP_SMMUL(Aold_QA[k - n - 1], rc_Q31) shl 1)
                Anew_QA[n] = SigProcFIX.SKP_SMMUL(tmp_QA, rc_mult2_Q16) shl 16 - headrm
                n++
            }
            k--
        }

        /* Check for stability */
        if (Anew_QA[0] > A_LIMIT || Anew_QA[0] < -A_LIMIT) {
            return 1
        }

        /* Set RC equal to negated AR coef */
        rc_Q31 = -(Anew_QA[0] shl 31 - QA)

        /* Range: [ 1 : 2^30 ] */
        rc_mult1_Q30 = (Typedef.SKP_int32_MAX shr 1) - SigProcFIX.SKP_SMMUL(rc_Q31, rc_Q31)

        /* Update inverse gain */
        /* Range: [ 0 : 2^30 ] */
        invGain_Q30[0] = SigProcFIX.SKP_SMMUL(invGain_Q30[0], rc_mult1_Q30) shl 2
        Typedef.SKP_assert(invGain_Q30[0] >= 0)
        Typedef.SKP_assert(invGain_Q30[0] <= 1 shl 30)
        return 0
    }
}