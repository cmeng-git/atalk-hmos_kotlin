/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * conversion between prediction filter coefficients and LSFs order should be even a piecewise
 * linear approximation maps LSF <-> cos(LSF) therefore the result is not accurate LSFs, but the two
 * function are accurate inverses of each other.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object NLSF2A {
    /**
     * helper function for NLSF2A(..).
     *
     * @param out
     * intermediate polynomial, Q20.
     * @param cLSF
     * vector of interleaved 2*cos(LSFs), Q20.
     * @param cLSF_offset
     * offset of valid data.
     * @param dd
     * polynomial order (= 1/2 * filter order).
     */
    fun SKP_Silk_NLSF2A_find_poly(out: IntArray,  /* o intermediate polynomial, Q20 */
            cLSF: IntArray,  /* i vector of interleaved 2*cos(LSFs), Q20 */
            cLSF_offset: Int, dd: Int /* i polynomial order (= 1/2 * filter order) */
    ) {
        var k: Int
        var n: Int
        var ftmp: Int
        out[0] = 1 shl 20
        out[1] = -cLSF[cLSF_offset + 0]
        k = 1
        while (k < dd) {
            ftmp = cLSF[cLSF_offset + 2 * k] // Q20
            val test = ftmp * out[k]
            val test2 = SigProcFIX.SKP_SMULL(ftmp, out[k])
            out[k + 1] = ((out[k - 1] shl 1)
                    - SigProcFIX.SKP_RSHIFT_ROUND64(SigProcFIX.SKP_SMULL(ftmp, out[k]), 20).toInt())
            n = k
            while (n > 1) {
                out[n] += (out[n - 2]
                        - SigProcFIX.SKP_RSHIFT_ROUND64(SigProcFIX.SKP_SMULL(ftmp, out[n - 1]),
                        20).toInt())
                n--
            }
            out[1] -= ftmp
            k++
        }
    }

    /**
     * compute whitening filter coefficients from normalized line spectral frequencies.
     *
     * @param a
     * monic whitening filter coefficients in Q12, [d].
     * @param NLSF
     * normalized line spectral frequencies in Q15, [d].
     * @param d
     * filter order (should be even).
     */
    fun SKP_Silk_NLSF2A(a: ShortArray?,  /* o monic whitening filter coefficients in Q12, [d] */
            NLSF: IntArray?,  /* i normalized line spectral frequencies in Q15, [d] */
            d: Int /* i filter order (should be even) */
    ) {
        var k: Int
        var i: Int
        val dd: Int
        val cos_LSF_Q20 = IntArray(SigProcFIXConstants.SKP_Silk_MAX_ORDER_LPC)
        val P = IntArray(SigProcFIXConstants.SKP_Silk_MAX_ORDER_LPC / 2 + 1)
        val Q = IntArray(SigProcFIXConstants.SKP_Silk_MAX_ORDER_LPC / 2 + 1)
        var Ptmp: Int
        var Qtmp: Int
        var f_int: Int
        var f_frac: Int
        var cos_val: Int
        var delta: Int
        val a_int32 = IntArray(SigProcFIXConstants.SKP_Silk_MAX_ORDER_LPC)
        var maxabs: Int
        var absval: Int
        var idx = 0
        var sc_Q16: Int
        Typedef.SKP_assert(SigProcFIXConstants.LSF_COS_TAB_SZ_FIX == 128)

        /* convert LSFs to 2*cos(LSF(i)), using piecewise linear curve from table */
        k = 0
        while (k < d) {
            Typedef.SKP_assert(NLSF!![k] >= 0)
            Typedef.SKP_assert(NLSF[k] <= 32767)

            /* f_int on a scale 0-127 (rounded down) */
            f_int = NLSF[k] shr (15 - 7)

            /* f_frac, range: 0..255 */
            f_frac = NLSF[k] - (f_int shl 15 - 7)
            Typedef.SKP_assert(f_int >= 0)
            Typedef.SKP_assert(f_int < SigProcFIXConstants.LSF_COS_TAB_SZ_FIX)

            /* Read start and end value from table */
            cos_val = LSFCosTable.SKP_Silk_LSFCosTab_FIX_Q12[f_int] /* Q12 */
            delta = LSFCosTable.SKP_Silk_LSFCosTab_FIX_Q12[f_int + 1] - cos_val
            /*
             * Q12, with a range of 0..200
             */

            /* Linear interpolation */
            cos_LSF_Q20[k] = (cos_val shl 8) + delta * f_frac /* Q20 */
            k++
        }
        dd = d shr 1

        /* generate even and odd polynomials using convolution */
        SKP_Silk_NLSF2A_find_poly(P, cos_LSF_Q20, 0, dd)
        SKP_Silk_NLSF2A_find_poly(Q, cos_LSF_Q20, 1, dd)

        /* convert even and odd polynomials to int Q12 filter coefs */
        k = 0
        while (k < dd) {
            Ptmp = P[k + 1] + P[k]
            Qtmp = Q[k + 1] - Q[k]

            /* the Ptmp and Qtmp values at this stage need to fit in int32 */
            a_int32[k] = -SigProcFIX.SKP_RSHIFT_ROUND(Ptmp + Qtmp, 9) /* Q20 -> Q12 */
            a_int32[d - k - 1] = SigProcFIX.SKP_RSHIFT_ROUND(Qtmp - Ptmp, 9) /* Q20 -> Q12 */
            k++
        }

        /* Limit the maximum absolute value of the prediction coefficients */
        i = 0
        while (i < 10) {

            /* Find maximum absolute value and its index */
            maxabs = 0
            k = 0
            while (k < d) {
                absval = SigProcFIX.SKP_abs(a_int32[k])
                if (absval > maxabs) {
                    maxabs = absval
                    idx = k
                }
                k++
            }
            if (maxabs > Typedef.SKP_int16_MAX) {
                /* Reduce magnitude of prediction coefficients */
                maxabs = SigProcFIX.SKP_min(maxabs, 98369) // ( SKP_int32_MAX / ( 65470 >> 2 ) ) +
                // SKP_int16_MAX =
                // 98369
                sc_Q16 = 65470 - (65470 shr 2) * (maxabs - Typedef.SKP_int16_MAX) / (maxabs * (idx + 1) shr 2)
                Bwexpander32.SKP_Silk_bwexpander_32(a_int32, d, sc_Q16)
            } else {
                break
            }
            i++
        }

        /* Reached the last iteration */
        if (i == 10) {
            Typedef.SKP_assert(false)
            k = 0
            while (k < d) {
                a_int32[k] = SigProcFIX.SKP_SAT16(a_int32[k])
                k++
            }
        }

        /* Return as SKP_int16 Q12 coefficients */
        k = 0
        while (k < d) {
            a!![k] = a_int32[k].toShort()
            k++
        }
    }
}