/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

object LPCStabilize {
    private val LPC_STABILIZE_LPC_MAX_ABS_VALUE_Q16 = Short.MAX_VALUE.toInt() shl 4

    /**
     * LPC stabilizer, for a single input data vector.
     *
     * @param a_Q12
     * stabilized LPC vector [L]
     * @param a_Q16
     * LPC vector [L]
     * @param bwe_Q16
     * Bandwidth expansion factor
     * @param L
     * Number of LPC parameters in the input vector
     */
    fun SKP_Silk_LPC_stabilize(
            a_Q12: ShortArray,  /* O stabilized LPC vector [L] */
            a_Q16: IntArray,  /* I LPC vector [L] */
            bwe_Q16: Int,  /* I Bandwidth expansion factor */
            L: Int, /* I Number of LPC parameters in the input vector */
    ) {
        var maxabs: Int
        var absval: Int
        var sc_Q16: Int
        var i: Int
        var idx = 0
        var invGain_Q30 = 0
        Bwexpander32.SKP_Silk_bwexpander_32(a_Q16, L, bwe_Q16)
        /** */
        /* Limit range of the LPCs */
        /** */
        /* Limit the maximum absolute value of the prediction coefficients */
        while (true) {
            /* Find maximum absolute value and its index */
            maxabs = Int.MIN_VALUE
            i = 0
            while (i < L) {
                absval = Math.abs(a_Q16[i])
                if (absval > maxabs) {
                    maxabs = absval
                    idx = i
                }
                i++
            }
            if (maxabs >= LPC_STABILIZE_LPC_MAX_ABS_VALUE_Q16) {
                /* Reduce magnitude of prediction coefficients */
                sc_Q16 = Int.MAX_VALUE / (maxabs shr 4)
                sc_Q16 = 65536 - sc_Q16
                sc_Q16 = sc_Q16 / (idx + 1)
                sc_Q16 = 65536 - sc_Q16
                sc_Q16 = Macros.SKP_SMULWB(sc_Q16, 32604) shl 1 // 0.995 in Q16
                Bwexpander32.SKP_Silk_bwexpander_32(a_Q16, L, sc_Q16)
            } else {
                break
            }
        }

        /* Convert to 16 bit Q12 */
        i = 0
        while (i < L) {
            a_Q12[i] = SigProcFIX.SKP_RSHIFT_ROUND(a_Q16[i], 4).toShort()
            i++
        }
        /** */
        /* Ensure stable LPCs */
        /** */
        val invGain_Q30_ptr = IntArray(1)
        invGain_Q30_ptr[0] = invGain_Q30
        while (LPCInvPredGain.SKP_Silk_LPC_inverse_pred_gain(invGain_Q30_ptr, a_Q12, L) == 1) {
            invGain_Q30 = invGain_Q30_ptr[0]
            Bwexpander.SKP_Silk_bwexpander(a_Q12, L, 65339) // 0.997 in Q16
        }
    }

    /**
     *
     * @param a_QQ
     * Stabilized LPC vector, Q(24-rshift) [L]
     * @param a_Q24
     * LPC vector [L]
     * @param QQ
     * Q domain of output LPC vector
     * @param L
     * Number of LPC parameters in the input vector
     */
    fun SKP_Silk_LPC_fit(
            a_QQ: ShortArray,  /* O Stabilized LPC vector, Q(24-rshift) [L] */
            a_Q24: IntArray,  /* I LPC vector [L] */
            QQ: Int,  /* I Q domain of output LPC vector */
            L: Int, /* I Number of LPC parameters in the input vector */
    ) {
        var i: Int
        val rshift: Int
        var idx = 0
        var maxabs: Int
        var absval: Int
        var sc_Q16: Int
        rshift = 24 - QQ
        /** */
        /* Limit range of the LPCs */
        /** */
        /* Limit the maximum absolute value of the prediction coefficients */
        while (true) {
            /* Find maximum absolute value and its index */
            maxabs = Int.MIN_VALUE
            i = 0
            while (i < L) {
                absval = Math.abs(a_Q24[i])
                if (absval > maxabs) {
                    maxabs = absval
                    idx = i
                }
                i++
            }
            maxabs = maxabs shr rshift
            if (maxabs >= Short.MAX_VALUE) {
                /* Reduce magnitude of prediction coefficients */
                maxabs = Math.min(maxabs, 98369) // ( SKP_int32_MAX / ( 65470 >> 2 ) ) +
                // SKP_int16_MAX = 98369
                sc_Q16 = 65470 - (65470 shr 2) * (maxabs - Short.MAX_VALUE) / SigProcFIX.SKP_RSHIFT32(maxabs * (idx + 1), 2)
                Bwexpander32.SKP_Silk_bwexpander_32(a_Q24, L, sc_Q16)
            } else {
                break
            }
        }
        assert(rshift > 0)
        assert(rshift < 31)
        i = 0
        while (i < L) {
            a_QQ[i] = SigProcFIX.SKP_RSHIFT_ROUND(a_Q24[i], rshift).toShort()
            i++
        }
    }
}