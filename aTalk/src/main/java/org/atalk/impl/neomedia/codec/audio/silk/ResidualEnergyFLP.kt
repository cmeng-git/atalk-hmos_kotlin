/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Residual energy.
 *
 * @author Dingxin Xu
 */
object ResidualEnergyFLP {
    const val MAX_ITERATIONS_RESIDUAL_NRG = 10
    const val REGULARIZATION_FACTOR = 1e-8f

    /**
     * Residual energy: nrg = wxx - 2 * wXx * c + c' * wXX * c.
     *
     * @param c
     * Filter coefficients
     * @param c_offset
     * offset of valid data.
     * @param wXX
     * Weighted correlation matrix, reg. out
     * @param wXX_offset
     * offset of valid data.
     * @param wXx
     * Weighted correlation vector
     * @param wxx
     * Weighted correlation value
     * @param D
     * Dimension
     * @return Weighted residual energy
     */
    fun SKP_Silk_residual_energy_covar_FLP( /* O Weighted residual energy */
            c: FloatArray?,  /* I Filter coefficients */
            c_offset: Int, wXX: FloatArray,  /* I/O Weighted correlation matrix, reg. out */
            wXX_offset: Int, wXx: FloatArray,  /* I Weighted correlation vector */
            wxx: Float,  /* I Weighted correlation value */
            D: Int /* I Dimension */
    ): Float {
        var i: Int
        var j: Int
        var k: Int
        var tmp: Float
        var nrg = 0f
        var regularization: Float
        assert(D >= 0)
        regularization = (REGULARIZATION_FACTOR
                * (wXX[wXX_offset + 0] + wXX[wXX_offset + D * D - 1]))
        k = 0
        while (k < MAX_ITERATIONS_RESIDUAL_NRG) {
            nrg = wxx
            tmp = 0.0f
            i = 0
            while (i < D) {
                tmp += wXx[i] * c!![c_offset + i]
                i++
            }
            nrg -= 2.0f * tmp

            /* compute c' * wXX * c, assuming wXX is symmetric */
            i = 0
            while (i < D) {
                tmp = 0.0f
                j = i + 1
                while (j < D) {

                    // tmp += matrix_c_ptr( wXX, i, j, D ) * c[ j ];
                    tmp += wXX[wXX_offset + i + j * D] * c!![c_offset + j]
                    j++
                }
                // nrg += c[ i ] * ( 2.0f * tmp + matrix_c_ptr( wXX, i, i, D ) * c[ i ] );
                nrg += (c!![c_offset + i]
                        * (2.0f * tmp + wXX[wXX_offset + i + D * i] * c[c_offset + i]))
                i++
            }
            if (nrg > 0) {
                break
            } else {
                /* Add white noise */
                i = 0
                while (i < D) {

                    // matrix_c_ptr( wXX, i, i, D ) += regularization;
                    wXX[wXX_offset + i + D * i] += regularization
                    i++
                }
                /* Increase noise for next run */
                regularization *= 2.0f
            }
            k++
        }
        if (k == MAX_ITERATIONS_RESIDUAL_NRG) {
            assert(nrg == 0f)
            nrg = 1.0f
        }
        return nrg
    }

    /**
     * Calculates residual energies of input subframes where all subframes have LPC_order of
     * preceeding samples
     *
     * @param nrgs
     * Residual energy per subframe
     * @param x
     * Input signal
     * @param a
     * AR coefs for each frame half
     * @param gains
     * Quantization gains
     * @param subfr_length
     * Subframe length
     * @param LPC_order
     * LPC order
     */
    fun SKP_Silk_residual_energy_FLP(nrgs: FloatArray?,  /* O Residual energy per subframe */
            x: FloatArray?,  /* I Input signal */
            a: Array<FloatArray?>?,  /* I AR coefs for each frame half */
            gains: FloatArray?,  /* I Quantization gains */
            subfr_length: Int,  /* I Subframe length */
            LPC_order: Int /* I LPC order */
    ) {
        val shift: Int
        // SKP_float *LPC_res_ptr, LPC_res[ ( MAX_FRAME_LENGTH + NB_SUBFR * MAX_LPC_ORDER ) / 2 ];
        val LPC_res_ptr: FloatArray
        val LPC_res = FloatArray((Define.MAX_FRAME_LENGTH + Define.NB_SUBFR * Define.MAX_LPC_ORDER) / 2)

        // LPC_res_ptr = LPC_res + LPC_order;
        LPC_res_ptr = LPC_res
        shift = LPC_order + subfr_length

        /*
		 * Filter input to create the LPC residual for each frame half, and measure subframe
		 * energies
		 */
        LPCAnalysisFilterFLP.SKP_Silk_LPC_analysis_filter_FLP(LPC_res, a!![0], x, 0 + 0 * shift,
                2 * shift, LPC_order)
        nrgs!![0] = (gains!![0] * gains[0] * EnergyFLP.SKP_Silk_energy_FLP(LPC_res_ptr,
                LPC_order + 0 * shift, subfr_length)).toFloat()
        nrgs[1] = (gains[1] * gains[1] * EnergyFLP.SKP_Silk_energy_FLP(LPC_res_ptr,
                LPC_order + 1 * shift, subfr_length)).toFloat()
        LPCAnalysisFilterFLP.SKP_Silk_LPC_analysis_filter_FLP(LPC_res, a[1], x, 0 + 2 * shift,
                2 * shift, LPC_order)
        nrgs[2] = (gains[2] * gains[2] * EnergyFLP.SKP_Silk_energy_FLP(LPC_res_ptr,
                LPC_order + 0 * shift, subfr_length)).toFloat()
        nrgs[3] = (gains[3] * gains[3] * EnergyFLP.SKP_Silk_energy_FLP(LPC_res_ptr,
                LPC_order + 1 * shift, subfr_length)).toFloat()
    }
}