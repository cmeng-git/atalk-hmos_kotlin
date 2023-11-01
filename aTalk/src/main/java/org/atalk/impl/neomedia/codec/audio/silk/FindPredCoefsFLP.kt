/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

import java.util.*

/**
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object FindPredCoefsFLP {
    /************************
     * TEST for nlsf
     */
    var frame_cnt = 0
    /** */
    /**
     *
     * @param psEnc
     * Encoder state FLP.
     * @param psEncCtrl
     * Encoder control FLP.
     * @param res_pitch
     * Residual from pitch analysis.
     */
    fun SKP_Silk_find_pred_coefs_FLP(psEnc: SKP_Silk_encoder_state_FLP?,  /*
																				 * I/O Encoder state
																				 * FLP
																				 */
            psEncCtrl: SKP_Silk_encoder_control_FLP,  /* I/O Encoder control FLP */
            res_pitch: FloatArray /* I Residual from pitch analysis */
    ) {
        var i: Int
        val WLTP = FloatArray(Define.NB_SUBFR * Define.LTP_ORDER * Define.LTP_ORDER)
        val invGains = FloatArray(Define.NB_SUBFR)
        val Wght = FloatArray(Define.NB_SUBFR)
        val NLSF = FloatArray(Define.MAX_LPC_ORDER)
        val x_ptr: FloatArray?
        var x_ptr_offset: Int
        val x_pre_ptr: FloatArray
        val LPC_in_pre = FloatArray(Define.NB_SUBFR * Define.MAX_LPC_ORDER + Define.MAX_FRAME_LENGTH)
        var x_pre_ptr_offset: Int

        /* Weighting for weighted least squares */
        i = 0
        while (i < Define.NB_SUBFR) {
            assert(psEncCtrl.Gains[i] > 0.0f)
            invGains[i] = 1.0f / psEncCtrl.Gains[i]
            Wght[i] = invGains[i] * invGains[i]
            i++
        }
        if (psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_VOICED) {
            /** */
            /* VOICED */
            /** */
            assert(psEnc!!.sCmn.frame_length - psEnc.sCmn.predictLPCOrder >= psEncCtrl.sCmn.pitchL[0]
                    + Define.LTP_ORDER / 2)

            /* LTP analysis */
            val LTPredCodGain_ptr = FloatArray(1)
            LTPredCodGain_ptr[0] = psEncCtrl.LTPredCodGain
            FindLTPFLP.SKP_Silk_find_LTP_FLP(psEncCtrl.LTPCoef, WLTP, LTPredCodGain_ptr, res_pitch,
                    res_pitch, psEnc.sCmn.frame_length shr 1, psEncCtrl.sCmn.pitchL, Wght,
                    psEnc.sCmn.subfr_length, psEnc.sCmn.frame_length)
            psEncCtrl.LTPredCodGain = LTPredCodGain_ptr[0]

            /* Quantize LTP gain parameters */
            val PERIndex_ptr = IntArray(1)
            PERIndex_ptr[0] = psEncCtrl.sCmn.PERIndex
            QuantLTPGainsFLP.SKP_Silk_quant_LTP_gains_FLP(psEncCtrl.LTPCoef,
                    psEncCtrl.sCmn.LTPIndex, PERIndex_ptr, WLTP, psEnc.mu_LTP,
                    psEnc.sCmn.LTPQuantLowComplexity)
            psEncCtrl.sCmn.PERIndex = PERIndex_ptr[0]

            /* Control LTP scaling */
            LTPScaleCtrlFLP.SKP_Silk_LTP_scale_ctrl_FLP(psEnc, psEncCtrl)

            /* Create LTP residual */
            LTPAnalysisFilterFLP.SKP_Silk_LTP_analysis_filter_FLP(LPC_in_pre, psEnc.x_buf,
                    psEnc.sCmn.frame_length - psEnc.sCmn.predictLPCOrder, psEncCtrl.LTPCoef,
                    psEncCtrl.sCmn.pitchL, invGains, psEnc.sCmn.subfr_length,
                    psEnc.sCmn.predictLPCOrder)
        } else {
            /** */
            /* UNVOICED */
            /** */
            /* Create signal with prepended subframes, scaled by inverse gains */
            x_ptr = psEnc!!.x_buf
            x_ptr_offset = psEnc.sCmn.frame_length - psEnc.sCmn.predictLPCOrder
            x_pre_ptr = LPC_in_pre
            x_pre_ptr_offset = 0
            i = 0
            while (i < Define.NB_SUBFR) {
                ScaleCopyVectorFLP.SKP_Silk_scale_copy_vector_FLP(x_pre_ptr, x_pre_ptr_offset,
                        x_ptr, x_ptr_offset, invGains[i], psEnc.sCmn.subfr_length
                        + psEnc.sCmn.predictLPCOrder)
                x_pre_ptr_offset += psEnc.sCmn.subfr_length + psEnc.sCmn.predictLPCOrder
                x_ptr_offset += psEnc.sCmn.subfr_length
                i++
            }
            Arrays.fill(psEncCtrl.LTPCoef, 0, Define.NB_SUBFR * Define.LTP_ORDER, 0.0f)
            psEncCtrl.LTPredCodGain = 0.0f
        }

        /*
		 * LPC_in_pre contains the LTP-filtered input for voiced, and the unfiltered input for
		 * unvoiced
		 */
        val NLSFInterpCoef_Q2_ptr = IntArray(1)
        NLSFInterpCoef_Q2_ptr[0] = psEncCtrl.sCmn.NLSFInterpCoef_Q2
        FindLPCFLP.SKP_Silk_find_LPC_FLP(NLSF, NLSFInterpCoef_Q2_ptr, psEnc.sPred.prev_NLSFq,
                psEnc.sCmn.useInterpolatedNLSFs * (1 - psEnc.sCmn.first_frame_after_reset),
                psEnc.sCmn.predictLPCOrder, LPC_in_pre, psEnc.sCmn.subfr_length
                + psEnc.sCmn.predictLPCOrder)
        psEncCtrl.sCmn.NLSFInterpCoef_Q2 = NLSFInterpCoef_Q2_ptr[0]

        /* Quantize LSFs */
        /* TEST*********************************************************************** */
        // /**
        // * Test for NLSF
        // */
        // float[] nlsf = new float[ MAX_LPC_ORDER ];
        // String nlsf_filename = "D:/gsoc/nlsf/nlsf";
        // nlsf_filename += frame_cnt;
        // DataInputStream nlsf_datain = null;
        // try
        // {
        // nlsf_datain = new DataInputStream(
        // new FileInputStream(
        // new File(nlsf_filename)));
        // byte[] buffer = new byte[4];
        // for(int ii = 0; ii < NLSF.length; ii++ )
        // {
        // try
        // {
        //
        // int res = nlsf_datain.read(buffer);
        // if(res != 4)
        // {
        // throw new IOException("Unexpected End of Stream");
        // }
        // nlsf[ii] = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        // NLSF[ii] = nlsf[ii];
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }
        // }
        // catch (FileNotFoundException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // finally
        // {
        // if(nlsf_datain != null)
        // {
        // try
        // {
        // nlsf_datain.close();
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }
        // }
        // frame_cnt++;
        /* TEST END******************************************************************* */
        ProcessNLSFsFLP.SKP_Silk_process_NLSFs_FLP(psEnc, psEncCtrl, NLSF)

        /* Calculate residual energy using quantized LPC coefficients */
        ResidualEnergyFLP.SKP_Silk_residual_energy_FLP(psEncCtrl.ResNrg, LPC_in_pre,
                psEncCtrl.PredCoef, psEncCtrl.Gains, psEnc.sCmn.subfr_length,
                psEnc.sCmn.predictLPCOrder)

        /* Copy to prediction struct for use in next frame for fluctuation reduction */
        System.arraycopy(NLSF, 0, psEnc.sPred.prev_NLSFq, 0, psEnc.sCmn.predictLPCOrder)
    }
}