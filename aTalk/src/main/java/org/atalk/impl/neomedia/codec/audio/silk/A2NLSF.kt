/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

open class A2NLSF_constants {
    companion object {
        /* Number of binary divisions, when not in low complexity mode */
        const val BIN_DIV_STEPS_A2NLSF_FIX = 2 /*
													 * must be no higher than 16 - log2(
													 * LSF_COS_TAB_SZ_FIX )
													 */
        const val QPoly = 16
        const val MAX_ITERATIONS_A2NLSF_FIX = 50

        /* Flag for using 2x as many cosine sampling points, reduces the risk of missing a root */
        const val OVERSAMPLE_COSINE_TABLE = false
    }
}

/**
 * Conversion between prediction filter coefficients and NLSFs. Requires the order to be an even
 * number. A piecewise linear approximation maps LSF <-> cos(LSF). Therefore the result is not
 * accurate NLSFs, but the two. function are accurate inverses of each other.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object A2NLSF : A2NLSF_constants() {
    /**
     * Helper function for A2NLSF(..). Transforms polynomials from cos(n*f) to cos(f)^n.
     *
     * @param p
     * Polynomial
     * @param dd
     * Polynomial order (= filter order / 2 )
     */
    fun SKP_Silk_A2NLSF_trans_poly(p: IntArray,  /* I/O Polynomial */
            dd: Int /* I Polynomial order (= filter order / 2 ) */
    ) {
        var k: Int
        var n: Int
        k = 2
        while (k <= dd) {
            n = dd
            while (n > k) {
                p[n - 2] -= p[n]
                n--
            }
            p[k - 2] -= p[k] shl 1
            k++
        }
    }

    /**
     * Helper function for A2NLSF(..). Polynomial evaluation.
     *
     * @param p
     * Polynomial, QPoly
     * @param x
     * Evaluation point, Q12
     * @param dd
     * Order
     * @return return the polynomial evaluation, in QPoly
     */
    fun SKP_Silk_A2NLSF_eval_poly( /* return the polynomial evaluation, in QPoly */
            p: IntArray?,  /* I Polynomial, QPoly */
            x: Int,  /* I Evaluation point, Q12 */
            dd: Int /* I Order */
    ): Int {
        var n: Int
        val x_Q16: Int
        var y32: Int
        y32 = p!![dd] /* QPoly */
        x_Q16 = x shl 4
        n = dd - 1
        while (n >= 0) {
            y32 = Macros.SKP_SMLAWW(p[n], y32, x_Q16) /* QPoly */
            n--
        }
        return y32
    }

    fun SKP_Silk_A2NLSF_init(a_Q16: IntArray, P: IntArray, Q: IntArray, dd: Int) {
        var k: Int

        /* Convert filter coefs to even and odd polynomials */
        P[dd] = 1 shl QPoly
        Q[dd] = 1 shl QPoly
        k = 0
        while (k < dd) {
            if (QPoly < 16) {
                P[k] = SigProcFIX.SKP_RSHIFT_ROUND(-a_Q16[dd - k - 1] - a_Q16[dd + k], 16 - QPoly) /* QPoly */
                Q[k] = SigProcFIX.SKP_RSHIFT_ROUND(-a_Q16[dd - k - 1] + a_Q16[dd + k], 16 - QPoly) /* QPoly */
            } else if (QPoly == 16) {
                P[k] = -a_Q16[dd - k - 1] - a_Q16[dd + k] // QPoly
                Q[k] = -a_Q16[dd - k - 1] + a_Q16[dd + k] // QPoly
            } else {
                P[k] = -a_Q16[dd - k - 1] - a_Q16[dd + k] shl QPoly - 16 /* QPoly */
                Q[k] = -a_Q16[dd - k - 1] + a_Q16[dd + k] shl QPoly - 16 /* QPoly */
            }
            k++
        }

        /* Divide out zeros as we have that for even filter orders, */
        /* z = 1 is always a root in Q, and */
        /* z = -1 is always a root in P */
        k = dd
        while (k > 0) {
            P[k - 1] -= P[k]
            Q[k - 1] += Q[k]
            k--
        }

        /* Transform polynomials from cos(n*f) to cos(f)^n */
        SKP_Silk_A2NLSF_trans_poly(P, dd)
        SKP_Silk_A2NLSF_trans_poly(Q, dd)
    }

    /**
     * Compute Normalized Line Spectral Frequencies (NLSFs) from whitening filter coefficients. If
     * not all roots are found, the a_Q16 coefficients are bandwidth expanded until convergence.
     *
     * @param NLSF
     * Normalized Line Spectral Frequencies, Q15 (0 - (2^15-1)), [d]
     * @param a_Q16
     * Monic whitening filter coefficients in Q16 [d]
     * @param d
     * Filter order (must be even)
     */
    fun SKP_Silk_A2NLSF(NLSF: IntArray,  /*
											 * O Normalized Line Spectral Frequencies, Q15 (0 -
											 * (2^15-1)), [d]
											 */
            a_Q16: IntArray,  /* I/O Monic whitening filter coefficients in Q16 [d] */
            d: Int /* I Filter order (must be even) */
    ) {
        var i: Int
        var k: Int
        var m: Int
        val dd: Int
        var root_ix: Int
        var ffrac: Int
        var xlo: Int
        var xhi: Int
        var xmid: Int
        var ylo: Int
        var yhi: Int
        var ymid: Int
        var nom: Int
        var den: Int
        val P = IntArray(SigProcFIXConstants.SKP_Silk_MAX_ORDER_LPC / 2 + 1)
        val Q = IntArray(SigProcFIXConstants.SKP_Silk_MAX_ORDER_LPC / 2 + 1)
        val PQ = arrayOfNulls<IntArray>(2)
        var p: IntArray?

        /* Store pointers to array */
        PQ[0] = P
        PQ[1] = Q
        dd = d shr 1
        SKP_Silk_A2NLSF_init(a_Q16, P, Q, dd)

        /* Find roots, alternating between P and Q */
        p = P /* Pointer to polynomial */
        xlo = LSFCosTable.SKP_Silk_LSFCosTab_FIX_Q12[0] // Q12
        ylo = SKP_Silk_A2NLSF_eval_poly(p, xlo, dd)
        if (ylo < 0) {
            /* Set the first NLSF to zero and move on to the next */
            NLSF[0] = 0
            p = Q /* Pointer to polynomial */
            ylo = SKP_Silk_A2NLSF_eval_poly(p, xlo, dd)
            root_ix = 1 /* Index of current root */
        } else {
            root_ix = 0 /* Index of current root */
        }
        k = 1 /* Loop counter */
        i = 0 /* Counter for bandwidth expansions applied */
        while (true) {
            /* Evaluate polynomial */
            xhi = if (OVERSAMPLE_COSINE_TABLE) {
                (LSFCosTable.SKP_Silk_LSFCosTab_FIX_Q12[k shr 1]
                        + (LSFCosTable.SKP_Silk_LSFCosTab_FIX_Q12[k + 1 shr 1] - LSFCosTable.SKP_Silk_LSFCosTab_FIX_Q12[k shr 1] shr 1)) /* Q12 */
            } else {
                LSFCosTable.SKP_Silk_LSFCosTab_FIX_Q12[k] /* Q12 */
            }
            yhi = SKP_Silk_A2NLSF_eval_poly(p, xhi, dd)

            /* Detect zero crossing */
            if (ylo <= 0 && yhi >= 0 || ylo >= 0 && yhi <= 0) {
                /* Binary division */
                ffrac = if (OVERSAMPLE_COSINE_TABLE) -128 else -256
                m = 0
                while (m < BIN_DIV_STEPS_A2NLSF_FIX) {

                    /* Evaluate polynomial */
                    xmid = SigProcFIX.SKP_RSHIFT_ROUND(xlo + xhi, 1)
                    ymid = SKP_Silk_A2NLSF_eval_poly(p, xmid, dd)

                    /* Detect zero crossing */
                    if ((ylo <= 0 && ymid >= 0 || ylo >= 0) && ymid <= 0) {
                        /* Reduce frequency */
                        xhi = xmid
                        yhi = ymid
                    } else {
                        /* Increase frequency */
                        xlo = xmid
                        ylo = ymid
                        ffrac = if (OVERSAMPLE_COSINE_TABLE) ffrac + (64 shr m) else ffrac + (128 shr m)
                    }
                    m++
                }

                /* Interpolate */
                if (Math.abs(ylo) < 65536) {
                    /* Avoid dividing by zero */
                    den = ylo - yhi
                    nom = ylo shl 8 - BIN_DIV_STEPS_A2NLSF_FIX + (den shr 1)
                    if (den != 0) {
                        ffrac += nom / den
                    }
                } else {
                    /* No risk of dividing by zero because abs(ylo - yhi) >= abs(ylo) >= 65536 */
                    ffrac += ylo / (ylo - yhi shr 8 - BIN_DIV_STEPS_A2NLSF_FIX)
                }
                if (OVERSAMPLE_COSINE_TABLE) NLSF[root_ix] = Math.min((k shl 7) + ffrac, Typedef.SKP_int16_MAX.toInt()) else NLSF[root_ix] = Math.min((k shl 8) + ffrac, Typedef.SKP_int16_MAX.toInt())
                assert(NLSF[root_ix] >= 0)
                assert(NLSF[root_ix] <= 32767)
                root_ix++ /* Next root */
                if (root_ix >= d) {
                    /* Found all roots */
                    break
                }
                /* Alternate pointer to polynomial */
                p = PQ[root_ix and 1]

                /* Evaluate polynomial */
                xlo = if (OVERSAMPLE_COSINE_TABLE) (LSFCosTable.SKP_Silk_LSFCosTab_FIX_Q12[k - 1 shr 1]
                        + (LSFCosTable.SKP_Silk_LSFCosTab_FIX_Q12[k shr 1] - LSFCosTable.SKP_Silk_LSFCosTab_FIX_Q12[k - 1 shr 1] shr 1)) // Q12
                else LSFCosTable.SKP_Silk_LSFCosTab_FIX_Q12[k - 1] // Q12
                ylo = 1 - (root_ix and 2) shl 12
            } else {
                /* Increment loop counter */
                k++
                xlo = xhi
                ylo = yhi
                var b: Boolean
                if (OVERSAMPLE_COSINE_TABLE) b = k > 2 * SigProcFIXConstants.LSF_COS_TAB_SZ_FIX else b = k > SigProcFIXConstants.LSF_COS_TAB_SZ_FIX
                if (b) {
                    i++
                    if (i > MAX_ITERATIONS_A2NLSF_FIX) {
                        /* Set NLSFs to white spectrum and exit */
                        NLSF[0] = (1 shl 15) / (d + 1)
                        k = 1
                        while (k < d) {
                            NLSF[k] = Macros.SKP_SMULBB(k + 1, NLSF[0])
                            k++
                        }
                        return
                    }

                    /* Error: Apply progressively more bandwidth expansion and run again */
                    Bwexpander32.SKP_Silk_bwexpander_32(a_Q16, d, 65536 - Macros.SKP_SMULBB(10 + i, i)) // 10_Q16
                    // =
                    // 0.00015
                    SKP_Silk_A2NLSF_init(a_Q16, P, Q, dd)
                    p = P /* Pointer to polynomial */
                    xlo = LSFCosTable.SKP_Silk_LSFCosTab_FIX_Q12[0] // Q12
                    ylo = SKP_Silk_A2NLSF_eval_poly(p, xlo, dd)
                    if (ylo < 0) {
                        /* Set the first NLSF to zero and move on to the next */
                        NLSF[0] = 0
                        p = Q /* Pointer to polynomial */
                        ylo = SKP_Silk_A2NLSF_eval_poly(p, xlo, dd)
                        root_ix = 1 /* Index of current root */
                    } else {
                        root_ix = 0 /* Index of current root */
                    }
                    k = 1 /* Reset loop counter */
                }
            }
        }
    }
}