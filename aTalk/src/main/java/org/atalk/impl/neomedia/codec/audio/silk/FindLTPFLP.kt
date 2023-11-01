/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object FindLTPFLP {
    /**
     *
     * @param b
     * LTP coefs.
     * @param WLTP
     * Weight for LTP quantization.
     * @param LTPredCodGain
     * LTP coding gain.
     * @param r_first
     * LPC residual, signal + state for 10 ms.
     * @param r_last
     * LPC residual, signal + state for 10 ms.
     * @param r_last_offset
     * offset of valid data.
     * @param lag
     * LTP lags.
     * @param Wght
     * Weights.
     * @param subfr_length
     * Subframe length.
     * @param mem_offset
     * Number of samples in LTP memory.
     */
    fun SKP_Silk_find_LTP_FLP(b: FloatArray?,  /* O LTP coefs */
            WLTP: FloatArray,  /* O Weight for LTP quantization */
            LTPredCodGain: FloatArray?,  /* O LTP coding gain */
            r_first: FloatArray,  /* I LPC residual, signal + state for 10 ms */
            r_last: FloatArray,  /* I LPC residual, signal + state for 10 ms */
            r_last_offset: Int, lag: IntArray?,  /* I LTP lags */
            Wght: FloatArray,  /* I Weights */
            subfr_length: Int,  /* I Subframe length */
            mem_offset: Int /* I Number of samples in LTP memory */
    ) {
        var i: Int
        var k: Int
        var b_ptr: FloatArray?
        var temp: Float
        val WLTP_ptr: FloatArray
        var LPC_res_nrg: Float
        var LPC_LTP_res_nrg: Float
        val d = FloatArray(Define.NB_SUBFR)
        var m: Float
        var g: Float
        val delta_b = FloatArray(Define.LTP_ORDER)
        val w = FloatArray(Define.NB_SUBFR)
        val nrg = FloatArray(Define.NB_SUBFR)
        var regu: Float
        val Rr = FloatArray(Define.LTP_ORDER)
        val rr = FloatArray(Define.NB_SUBFR)
        var r_ptr: FloatArray
        var lag_ptr: FloatArray
        var r_ptr_offset: Int
        var lag_ptr_offset: Int
        b_ptr = b
        var b_ptr_offset = 0
        WLTP_ptr = WLTP
        var WLTP_ptr_offset = 0
        r_ptr = r_first
        r_ptr_offset = mem_offset
        k = 0
        while (k < Define.NB_SUBFR) {
            if (k == Define.NB_SUBFR shr 1) { /* Shift residual for last 10 ms */
                r_ptr = r_last
                r_ptr_offset = r_last_offset + mem_offset
            }
            lag_ptr = r_ptr
            lag_ptr_offset = r_ptr_offset - (lag!![k] + Define.LTP_ORDER / 2)
            CorrMatrixFLP.SKP_Silk_corrMatrix_FLP(lag_ptr, lag_ptr_offset, subfr_length, Define.LTP_ORDER,
                    WLTP_ptr, WLTP_ptr_offset)
            CorrMatrixFLP.SKP_Silk_corrVector_FLP(lag_ptr, lag_ptr_offset, r_ptr, r_ptr_offset,
                    subfr_length, Define.LTP_ORDER, Rr)
            rr[k] = EnergyFLP.SKP_Silk_energy_FLP(r_ptr, r_ptr_offset, subfr_length).toFloat()
            regu = DefineFLP.LTP_DAMPING * (rr[k] + 1.0f)
            RegularizeCorrelationsFLP.SKP_Silk_regularize_correlations_FLP(WLTP_ptr,
                    WLTP_ptr_offset, rr, k, regu, Define.LTP_ORDER)
            SolveLSFLP.SKP_Silk_solve_LDL_FLP(WLTP_ptr, WLTP_ptr_offset, Define.LTP_ORDER, Rr, b_ptr,
                    b_ptr_offset)

            /* Calculate residual energy */
            nrg[k] = ResidualEnergyFLP.SKP_Silk_residual_energy_covar_FLP(b_ptr, b_ptr_offset,
                    WLTP_ptr, WLTP_ptr_offset, Rr, rr[k], Define.LTP_ORDER)
            temp = Wght[k] / (nrg[k] * Wght[k] + 0.01f * subfr_length)
            ScaleVectorFLP.SKP_Silk_scale_vector_FLP(WLTP_ptr, WLTP_ptr_offset, temp, Define.LTP_ORDER
                    * Define.LTP_ORDER)
            // w[ k ] = matrix_ptr( WLTP_ptr, LTP_ORDER / 2, LTP_ORDER / 2, LTP_ORDER );
            w[k] = WLTP_ptr[WLTP_ptr_offset + (Define.LTP_ORDER / 2 * Define.LTP_ORDER + Define.LTP_ORDER / 2)]
            r_ptr_offset += subfr_length
            b_ptr_offset += Define.LTP_ORDER
            WLTP_ptr_offset += Define.LTP_ORDER * Define.LTP_ORDER
            k++
        }

        /* Compute LTP coding gain */
        if (LTPredCodGain != null) {
            LPC_LTP_res_nrg = 1e-6f
            LPC_res_nrg = 0.0f
            k = 0
            while (k < Define.NB_SUBFR) {
                LPC_res_nrg += rr[k] * Wght[k]
                LPC_LTP_res_nrg += nrg[k] * Wght[k]
                k++
            }
            assert(LPC_LTP_res_nrg > 0)
            LTPredCodGain[0] = 3.0f * MainFLP.SKP_Silk_log2((LPC_res_nrg / LPC_LTP_res_nrg).toDouble())
        }

        /* Smoothing */
        /* d = sum( B, 1 ); */
        b_ptr = b
        b_ptr_offset = 0
        k = 0
        while (k < Define.NB_SUBFR) {
            d[k] = 0f
            i = 0
            while (i < Define.LTP_ORDER) {
                d[k] += b_ptr!![b_ptr_offset + i]
                i++
            }
            b_ptr_offset += Define.LTP_ORDER
            k++
        }
        /* m = ( w * d' ) / ( sum( w ) + 1e-3 ); */
        temp = 1e-3f
        k = 0
        while (k < Define.NB_SUBFR) {
            temp += w[k]
            k++
        }
        m = 0f
        k = 0
        while (k < Define.NB_SUBFR) {
            m += d[k] * w[k]
            k++
        }
        m = m / temp
        b_ptr = b
        b_ptr_offset = 0
        k = 0
        while (k < Define.NB_SUBFR) {
            g = DefineFLP.LTP_SMOOTHING / (DefineFLP.LTP_SMOOTHING + w[k]) * (m - d[k])
            temp = 0f
            i = 0
            while (i < Define.LTP_ORDER) {
                delta_b[i] = Math.max(b_ptr!![i], 0.1f)
                temp += delta_b[i]
                i++
            }
            temp = g / temp
            i = 0
            while (i < Define.LTP_ORDER) {
                b_ptr!![i] = b_ptr[i] + delta_b[i] * temp
                i++
            }
            b_ptr_offset += Define.LTP_ORDER
            k++
        }
    }
}