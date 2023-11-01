/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Calculates the reflection coefficients from the input vector Input vector contains nb_subfr sub
 * vectors of length L_sub + D
 *
 * @author Dingxin Xu
 */
object BurgModifiedFLP {
    const val MAX_FRAME_SIZE = 544 // subfr_length * nb_subfr = ( 0.005 * 24000 + 16 ) * 4 =

    // 544
    const val MAX_NB_SUBFR = 4

    /**
     * Compute reflection coefficients from input signal.
     *
     * @param A
     * prediction coefficients (length order).
     * @param x
     * input signal, length: nb_subfr*(D+L_sub).
     * @param x_offset
     * offset of valid data.
     * @param subfr_length
     * input signal subframe length (including D preceeding samples).
     * @param nb_subfr
     * number of subframes stacked in x.
     * @param WhiteNoiseFrac
     * fraction added to zero-lag autocorrelation.
     * @param D
     * order.
     * @return
     */
    fun SKP_Silk_burg_modified_FLP( /* O returns residual energy */
            A: FloatArray,  /* O prediction coefficients (length order) */
            x: FloatArray,  /* I input signal, length: nb_subfr*(D+L_sub) */
            x_offset: Int, subfr_length: Int,  /*
											 * I input signal subframe length (including D
											 * preceeding samples)
											 */
            nb_subfr: Int,  /* I number of subframes stacked in x */
            WhiteNoiseFrac: Float,  /* I fraction added to zero-lag autocorrelation */
            D: Int /* I order */
    ): Float {
        var k: Int
        var n: Int
        var s: Int
        val C0: Double
        var num: Double
        var nrg_f: Double
        var nrg_b: Double
        var rc: Double
        var Atmp: Double
        var tmp1: Double
        var tmp2: Double
        var x_ptr: FloatArray
        var x_ptr_offset: Int
        val C_first_row = DoubleArray(SigProcFIXConstants.SKP_Silk_MAX_ORDER_LPC)
        val C_last_row = DoubleArray(SigProcFIXConstants.SKP_Silk_MAX_ORDER_LPC)
        val CAf = DoubleArray(SigProcFIXConstants.SKP_Silk_MAX_ORDER_LPC + 1)
        val CAb = DoubleArray(SigProcFIXConstants.SKP_Silk_MAX_ORDER_LPC + 1)
        val Af = DoubleArray(SigProcFIXConstants.SKP_Silk_MAX_ORDER_LPC)
        assert(subfr_length * nb_subfr <= MAX_FRAME_SIZE)
        assert(nb_subfr <= MAX_NB_SUBFR)

        /* Compute autocorrelations, added over subframes */
        C0 = EnergyFLP.SKP_Silk_energy_FLP(x, x_offset, nb_subfr * subfr_length)
        s = 0
        while (s < nb_subfr) {
            x_ptr = x
            x_ptr_offset = x_offset + s * subfr_length
            n = 1
            while (n < D + 1) {
                C_first_row[n - 1] += InnerProductFLP.SKP_Silk_inner_product_FLP(x_ptr,
                        x_ptr_offset, x_ptr, x_ptr_offset + n, subfr_length - n)
                n++
            }
            s++
        }
        System.arraycopy(C_first_row, 0, C_last_row, 0, SigProcFIXConstants.SKP_Silk_MAX_ORDER_LPC)

        /* Initialize */
        CAf[0] = C0 + WhiteNoiseFrac * C0 + 1e-9f
        CAb[0] = CAf[0]
        n = 0
        while (n < D) {

            /* Update first row of correlation matrix (without first element) */
            /*
			 * Update last row of correlation matrix (without last element, stored in reversed
			 * order)
			 */
            /* Update C * Af */
            /* Update C * flipud(Af) (stored in reversed order) */
            s = 0
            while (s < nb_subfr) {
                x_ptr = x
                x_ptr_offset = x_offset + s * subfr_length
                tmp1 = x_ptr[x_ptr_offset + n].toDouble()
                tmp2 = x_ptr[x_ptr_offset + subfr_length - n - 1].toDouble()
                k = 0
                while (k < n) {
                    C_first_row[k] -= (x_ptr[x_ptr_offset + n] * x_ptr[x_ptr_offset + n - k - 1]).toDouble()
                    C_last_row[k] -= (x_ptr[x_ptr_offset + subfr_length - n - 1]
                            * x_ptr[x_ptr_offset + subfr_length - n + k]).toDouble()
                    Atmp = Af[k]
                    tmp1 += x_ptr[x_ptr_offset + n - k - 1] * Atmp
                    tmp2 += x_ptr[x_ptr_offset + subfr_length - n + k] * Atmp
                    k++
                }
                k = 0
                while (k <= n) {
                    CAf[k] -= tmp1 * x_ptr[x_ptr_offset + n - k]
                    CAb[k] -= tmp2 * x_ptr[x_ptr_offset + subfr_length - n + k - 1]
                    k++
                }
                s++
            }
            tmp1 = C_first_row[n]
            tmp2 = C_last_row[n]
            k = 0
            while (k < n) {
                Atmp = Af[k]
                tmp1 += C_last_row[n - k - 1] * Atmp
                tmp2 += C_first_row[n - k - 1] * Atmp
                k++
            }
            CAf[n + 1] = tmp1
            CAb[n + 1] = tmp2

            /*
			 * Calculate nominator and denominator for the next order reflection (parcor)
			 * coefficient
			 */
            num = CAb[n + 1]
            nrg_b = CAb[0]
            nrg_f = CAf[0]
            k = 0
            while (k < n) {
                Atmp = Af[k]
                num += CAb[n - k] * Atmp
                nrg_b += CAb[k + 1] * Atmp
                nrg_f += CAf[k + 1] * Atmp
                k++
            }
            assert(nrg_f > 0.0)
            assert(nrg_b > 0.0)

            /* Calculate the next order reflection (parcor) coefficient */
            rc = -2.0 * num / (nrg_f + nrg_b)
            assert(rc > -1.0 && rc < 1.0)

            /* Update the AR coefficients */
            k = 0
            while (k < n + 1 shr 1) {
                tmp1 = Af[k]
                tmp2 = Af[n - k - 1]
                Af[k] = tmp1 + rc * tmp2
                Af[n - k - 1] = tmp2 + rc * tmp1
                k++
            }
            Af[n] = rc

            /* Update C * Af and C * Ab */
            k = 0
            while (k <= n + 1) {
                tmp1 = CAf[k]
                CAf[k] += rc * CAb[n - k + 1]
                CAb[n - k + 1] += rc * tmp1
                k++
            }
            n++
        }

        /* Return residual energy */
        nrg_f = CAf[0]
        tmp1 = 1.0
        k = 0
        while (k < D) {
            Atmp = Af[k]
            nrg_f += CAf[k + 1] * Atmp
            tmp1 += Atmp * Atmp
            A[k] = (-Atmp).toFloat()
            k++
        }
        nrg_f -= WhiteNoiseFrac * C0 * tmp1
        return nrg_f.toFloat()
    }
}