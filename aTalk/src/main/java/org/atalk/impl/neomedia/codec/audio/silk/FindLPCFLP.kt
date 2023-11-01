/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 *
 * @author Dingxin Xu
 */
object FindLPCFLP {
    /**
     *
     * @param NLSF
     * NLSFs.
     * @param interpIndex
     * NLSF interp. index for NLSF interp.
     * @param prev_NLSFq
     * Previous NLSFs, for NLSF interpolation.
     * @param useInterpNLSFs
     * Flag.
     * @param LPC_order
     * LPC order.
     * @param x
     * Input signal.
     * @param subfr_length
     * Subframe length incl preceeding samples.
     */
    fun SKP_Silk_find_LPC_FLP(NLSF: FloatArray,  /* O NLSFs */
            interpIndex: IntArray,  /* O NLSF interp. index for NLSF interp. */
            prev_NLSFq: FloatArray?,  /* I Previous NLSFs, for NLSF interpolation */
            useInterpNLSFs: Int,  /* I Flag */
            LPC_order: Int,  /* I LPC order */
            x: FloatArray,  /* I Input signal */
            subfr_length: Int /* I Subframe length incl preceeding samples */
    ) {
        var k: Int
        val a = FloatArray(Define.MAX_LPC_ORDER)

        /* Used only for NLSF interpolation */
        var res_nrg: Double
        var res_nrg_2nd: Double
        var res_nrg_interp: Double
        val a_tmp = FloatArray(Define.MAX_LPC_ORDER)
        val NLSF0 = FloatArray(Define.MAX_LPC_ORDER)
        val LPC_res = FloatArray((Define.MAX_FRAME_LENGTH + Define.NB_SUBFR * Define.MAX_LPC_ORDER) / 2)

        /* Default: No interpolation */
        interpIndex[0] = 4

        /* Burg AR analysis for the full frame */
        res_nrg = BurgModifiedFLP.SKP_Silk_burg_modified_FLP(a, x, 0, subfr_length, Define.NB_SUBFR,
                DefineFLP.FIND_LPC_COND_FAC, LPC_order).toDouble()
        if (useInterpNLSFs == 1) {

            /* Optimal solution for last 10 ms; subtract residual energy here, as that's easier than */
            /*
			 * adding it to the residual energy of the first 10 ms in each iteration of the search
			 * below
			 */
            res_nrg -= BurgModifiedFLP.SKP_Silk_burg_modified_FLP(a_tmp, x, Define.NB_SUBFR / 2
                    * subfr_length, subfr_length, Define.NB_SUBFR / 2, DefineFLP.FIND_LPC_COND_FAC, LPC_order).toDouble()

            /* Convert to NLSFs */
            WrappersFLP.SKP_Silk_A2NLSF_FLP(NLSF, a_tmp, LPC_order)

            /* Search over interpolation indices to find the one with lowest residual energy */
            res_nrg_2nd = Float.MAX_VALUE.toDouble()
            k = 3
            while (k >= 0) {

                /* Interpolate NLSFs for first half */
                WrappersFLP.SKP_Silk_interpolate_wrapper_FLP(NLSF0, prev_NLSFq, NLSF, 0.25f * k,
                        LPC_order)

                /* Convert to LPC for residual energy evaluation */
                WrappersFLP.SKP_Silk_NLSF2A_stable_FLP(a_tmp, NLSF0, LPC_order)

                /* Calculate residual energy with LSF interpolation */
                LPCAnalysisFilterFLP.SKP_Silk_LPC_analysis_filter_FLP(LPC_res, a_tmp, x, 0,
                        2 * subfr_length, LPC_order)
                res_nrg_interp = (EnergyFLP.SKP_Silk_energy_FLP(LPC_res, LPC_order, subfr_length
                        - LPC_order)
                        + EnergyFLP.SKP_Silk_energy_FLP(LPC_res, LPC_order + subfr_length, subfr_length
                        - LPC_order))

                /* Determine whether current interpolated NLSFs are best so far */
                if (res_nrg_interp < res_nrg) {
                    /* Interpolation has lower residual energy */
                    res_nrg = res_nrg_interp
                    interpIndex[0] = k
                } else if (res_nrg_interp > res_nrg_2nd) {
                    /* No reason to continue iterating - residual energies will continue to climb */
                    break
                }
                res_nrg_2nd = res_nrg_interp
                k--
            }
        }
        if (interpIndex[0] == 4) {
            /*
			 * NLSF interpolation is currently inactive, calculate NLSFs from full frame AR
			 * coefficients
			 */
            WrappersFLP.SKP_Silk_A2NLSF_FLP(NLSF, a, LPC_order)
        }
    }
}