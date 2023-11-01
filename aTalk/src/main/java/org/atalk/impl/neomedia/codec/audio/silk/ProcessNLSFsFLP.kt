/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Limit, stabilize, convert and quantize NLSFs.
 *
 * @author Dingxin Xu
 */
object ProcessNLSFsFLP {
    /**
     * Limit, stabilize, convert and quantize NLSFs.
     *
     * @param psEnc
     * Encoder state FLP
     * @param psEncCtrl
     * Encoder control FLP
     * @param pNLSF
     * NLSFs (quantized output)
     */
    fun SKP_Silk_process_NLSFs_FLP(psEnc: SKP_Silk_encoder_state_FLP?,  /* I/O Encoder state FLP */
            psEncCtrl: SKP_Silk_encoder_control_FLP,  /* I/O Encoder control FLP */
            pNLSF: FloatArray /* I/O NLSFs (quantized output) */
    ) {
        val doInterpolate: Boolean
        val pNLSFW = FloatArray(Define.MAX_LPC_ORDER)
        val NLSF_mu: Float
        val NLSF_mu_fluc_red: Float
        val i_sqr: Float
        var NLSF_interpolation_factor = 0.0f
        val psNLSF_CB_FLP: SKP_Silk_NLSF_CB_FLP?

        /* Used only for NLSF interpolation */
        val pNLSF0_temp = FloatArray(Define.MAX_LPC_ORDER)
        val pNLSFW0_temp = FloatArray(Define.MAX_LPC_ORDER)
        var i: Int
        assert(psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_VOICED || psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_UNVOICED)
        /** */
        /* Calculate mu values */
        /** */
        if (psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_VOICED) {
            NLSF_mu = 0.002f - 0.001f * psEnc!!.speech_activity
            NLSF_mu_fluc_red = 0.1f - 0.05f * psEnc.speech_activity
        } else {
            NLSF_mu = 0.005f - 0.004f * psEnc!!.speech_activity
            NLSF_mu_fluc_red = 0.2f - 0.1f * (psEnc.speech_activity + psEncCtrl.sparseness)
        }

        /* Calculate NLSF weights */
        NLSFVQWeightsLaroiaFLP.SKP_Silk_NLSF_VQ_weights_laroia_FLP(pNLSFW, pNLSF,
                psEnc.sCmn.predictLPCOrder)

        /* Update NLSF weights for interpolated NLSFs */
        doInterpolate = psEnc.sCmn.useInterpolatedNLSFs == 1 && psEncCtrl.sCmn.NLSFInterpCoef_Q2 < (1 shl 2)
        if (doInterpolate) {

            /* Calculate the interpolated NLSF vector for the first half */
            NLSF_interpolation_factor = 0.25f * psEncCtrl.sCmn.NLSFInterpCoef_Q2
            WrappersFLP.SKP_Silk_interpolate_wrapper_FLP(pNLSF0_temp, psEnc.sPred.prev_NLSFq,
                    pNLSF, NLSF_interpolation_factor, psEnc.sCmn.predictLPCOrder)

            /* Calculate first half NLSF weights for the interpolated NLSFs */
            NLSFVQWeightsLaroiaFLP.SKP_Silk_NLSF_VQ_weights_laroia_FLP(pNLSFW0_temp, pNLSF0_temp,
                    psEnc.sCmn.predictLPCOrder)

            /* Update NLSF weights with contribution from first half */
            i_sqr = NLSF_interpolation_factor * NLSF_interpolation_factor
            i = 0
            while (i < psEnc.sCmn.predictLPCOrder) {
                pNLSFW[i] = 0.5f * (pNLSFW[i] + i_sqr * pNLSFW0_temp[i])
                i++
            }
        }

        /* Set pointer to the NLSF codebook for the current signal type and LPC order */
        psNLSF_CB_FLP = psEnc.psNLSF_CB_FLP[psEncCtrl.sCmn.sigtype]

        /* Quantize NLSF parameters given the trained NLSF codebooks */
        NLSFMSVQEncodeFLP.SKP_Silk_NLSF_MSVQ_encode_FLP(psEncCtrl.sCmn.NLSFIndices, pNLSF,
                psNLSF_CB_FLP, psEnc.sPred.prev_NLSFq, pNLSFW, NLSF_mu, NLSF_mu_fluc_red,
                psEnc.sCmn.NLSF_MSVQ_Survivors, psEnc.sCmn.predictLPCOrder,
                psEnc.sCmn.first_frame_after_reset)

        /* Convert quantized NLSFs back to LPC coefficients */
        WrappersFLP.SKP_Silk_NLSF2A_stable_FLP(psEncCtrl.PredCoef[1], pNLSF,
                psEnc.sCmn.predictLPCOrder)
        if (doInterpolate) {
            /* Calculate the interpolated, quantized NLSF vector for the first half */
            WrappersFLP.SKP_Silk_interpolate_wrapper_FLP(pNLSF0_temp, psEnc.sPred.prev_NLSFq,
                    pNLSF, NLSF_interpolation_factor, psEnc.sCmn.predictLPCOrder)

            /* Convert back to LPC coefficients */
            WrappersFLP.SKP_Silk_NLSF2A_stable_FLP(psEncCtrl.PredCoef[0], pNLSF0_temp,
                    psEnc.sCmn.predictLPCOrder)
        } else {
            /* Copy LPC coefficients for first half from second half */
            System.arraycopy(psEncCtrl.PredCoef[1]!!, 0, psEncCtrl.PredCoef[0]!!, 0,
                    psEnc.sCmn.predictLPCOrder)
        }
    }
}