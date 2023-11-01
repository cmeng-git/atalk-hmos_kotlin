/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * compute inverse of LPC prediction gain, and test if LPC coefficients are stable (all poles within
 * unit circle)
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object LPCInvPredGainFLP {
    const val RC_THRESHOLD = 0.9999f

    /**
     * compute inverse of LPC prediction gain, and test if LPC coefficients are stable (all poles
     * within unit circle) this code is based on SKP_Silk_a2k_FLP().
     *
     * @param invGain
     * inverse prediction gain, energy domain
     * @param A
     * prediction coefficients [order]
     * @param A_offset
     * offset of valid data.
     * @param order
     * prediction order
     * @return returns 1 if unstable, otherwise 0
     */
    fun SKP_Silk_LPC_inverse_pred_gain_FLP( /* O: returns 1 if unstable, otherwise 0 */
            invGain: FloatArray,  /* O: inverse prediction gain, energy domain */
            A: FloatArray?,  /* I: prediction coefficients [order] */
            A_offset: Int, order: Int /* I: prediction order */
    ): Int {
        var k: Int
        var n: Int
        var rc: Double
        var rc_mult1: Double
        var rc_mult2: Double
        val Atmp = Array(2) { FloatArray(SigProcFIXConstants.SKP_Silk_MAX_ORDER_LPC) }
        var Aold: FloatArray
        var Anew: FloatArray
        Anew = Atmp[order and 1]
        for (i_djinn in 0 until order) Anew[i_djinn] = A!![A_offset + i_djinn]
        invGain[0] = 1.0f
        k = order - 1
        while (k > 0) {
            rc = -Anew[k].toDouble()
            if (rc > RC_THRESHOLD || rc < -RC_THRESHOLD) {
                return 1
            }
            rc_mult1 = 1.0f - rc * rc
            rc_mult2 = 1.0f / rc_mult1
            invGain[0] *= rc_mult1.toFloat()
            /* swap pointers */
            Aold = Anew
            Anew = Atmp[k and 1]
            n = 0
            while (n < k) {
                Anew[n] = ((Aold[n] - Aold[k - n - 1] * rc) * rc_mult2).toFloat()
                n++
            }
            k--
        }
        rc = -Anew[0].toDouble()
        if (rc > RC_THRESHOLD || rc < -RC_THRESHOLD) {
            return 1
        }
        rc_mult1 = 1.0f - rc * rc
        invGain[0] *= rc_mult1.toFloat()
        return 0
    }
}