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
object FindPitchLagsFLP {
    /**
     *
     * @param psEnc
     * Encoder state FLP.
     * @param psEncCtrl
     * Encoder control FLP.
     * @param res
     * Residual.
     * @param x
     * Speech signal.
     * @param x_offset
     * offset of valid data.
     */
    fun SKP_Silk_find_pitch_lags_FLP(psEnc: SKP_Silk_encoder_state_FLP?,  /*
																				 * I/O Encoder state
																				 * FLP
																				 */
            psEncCtrl: SKP_Silk_encoder_control_FLP,  /* I/O Encoder control FLP */
            res: FloatArray,  /* O Residual */
            x: FloatArray?,  /* I Speech signal */
            x_offset: Int) {
        val psPredSt = psEnc!!.sPred
        // const SKP_float *x_buf_ptr, *x_buf;
        val x_buf_ptr: FloatArray?
        val x_buf: FloatArray?
        var x_buf_ptr_offset: Int
        val x_buf_offset: Int
        val auto_corr = FloatArray(Define.FIND_PITCH_LPC_ORDER_MAX + 1)
        val A = FloatArray(Define.FIND_PITCH_LPC_ORDER_MAX)
        val refl_coef = FloatArray(Define.FIND_PITCH_LPC_ORDER_MAX)
        val Wsig = FloatArray(Define.FIND_PITCH_LPC_WIN_MAX)
        var thrhld: Float
        val Wsig_ptr: FloatArray
        var Wsig_ptr_offset: Int
        val buf_len: Int
        /** */
        /* Setup buffer lengths etc based of Fs */
        /** */
        buf_len = 2 * psEnc.sCmn.frame_length + psEnc.sCmn.la_pitch
        assert(buf_len >= psPredSt!!.pitch_LPC_win_length)

        // x_buf = x - psEnc->sCmn.frame_length;
        x_buf = x
        x_buf_offset = x_offset - psEnc.sCmn.frame_length
        /** */
        /* Estimate LPC AR coeficients */
        /** */

        /* Calculate windowed signal */

        /* First LA_LTP samples */
        // x_buf_ptr = x_buf + buf_len - psPredSt->pitch_LPC_win_length;
        x_buf_ptr = x_buf
        x_buf_ptr_offset = x_buf_offset + buf_len - psPredSt.pitch_LPC_win_length
        Wsig_ptr = Wsig
        Wsig_ptr_offset = 0
        ApplySineWindowFLP.SKP_Silk_apply_sine_window_FLP(Wsig_ptr, Wsig_ptr_offset, x_buf_ptr,
                x_buf_ptr_offset, 1, psEnc.sCmn.la_pitch)

        /* Middle non-windowed samples */
        Wsig_ptr_offset += psEnc.sCmn.la_pitch
        x_buf_ptr_offset += psEnc.sCmn.la_pitch
        // SKP_memcpy( Wsig_ptr, x_buf_ptr, ( psPredSt->pitch_LPC_win_length - (
        // psEnc->sCmn.la_pitch << 1 ) ) * sizeof(
        // SKP_float ) );
        for (i_djinn in 0 until psPredSt.pitch_LPC_win_length - (psEnc.sCmn.la_pitch shl 1)) Wsig_ptr[Wsig_ptr_offset + i_djinn] = x_buf_ptr!![x_buf_ptr_offset + i_djinn]

        /* Last LA_LTP samples */
        Wsig_ptr_offset += psPredSt.pitch_LPC_win_length - (psEnc.sCmn.la_pitch shl 1)
        x_buf_ptr_offset += psPredSt.pitch_LPC_win_length - (psEnc.sCmn.la_pitch shl 1)
        ApplySineWindowFLP.SKP_Silk_apply_sine_window_FLP(Wsig_ptr, Wsig_ptr_offset, x_buf_ptr,
                x_buf_ptr_offset, 2, psEnc.sCmn.la_pitch)

        /* Calculate autocorrelation sequence */
        AutocorrelationFLP.SKP_Silk_autocorrelation_FLP(auto_corr, 0, Wsig, 0,
                psPredSt.pitch_LPC_win_length, psEnc.sCmn.pitchEstimationLPCOrder + 1)

        /* Add white noise, as a fraction of the energy */
        auto_corr[0] += auto_corr[0] * DefineFLP.FIND_PITCH_WHITE_NOISE_FRACTION

        /* Calculate the reflection coefficients using Schur */
        SchurFLP.SKP_Silk_schur_FLP(refl_coef, 0, auto_corr, 0, psEnc.sCmn.pitchEstimationLPCOrder)

        /* Convert reflection coefficients to prediction coefficients */
        K2aFLP.SKP_Silk_k2a_FLP(A, refl_coef, psEnc.sCmn.pitchEstimationLPCOrder)

        /* Bandwidth expansion */
        BwexpanderFLP.SKP_Silk_bwexpander_FLP(A, 0, psEnc.sCmn.pitchEstimationLPCOrder,
                DefineFLP.FIND_PITCH_BANDWITH_EXPANSION)
        /**
         * LPC analysis filtering
        */
        LPCAnalysisFilterFLP.SKP_Silk_LPC_analysis_filter_FLP(res, A, x_buf, x_buf_offset, buf_len,
                psEnc.sCmn.pitchEstimationLPCOrder)
        // SKP_memset( res, 0, psEnc->sCmn.pitchEstimationLPCOrder * sizeof( SKP_float ) );
        for (i_djinn in 0 until psEnc.sCmn.pitchEstimationLPCOrder) res[i_djinn] = 0f

        /* Threshold for pitch estimator */
        thrhld = 0.5f
        thrhld -= 0.004f * psEnc.sCmn.pitchEstimationLPCOrder
        thrhld -= 0.1f * Math.sqrt(psEnc.speech_activity.toDouble()).toFloat()
        thrhld += 0.14f * psEnc.sCmn.prev_sigtype
        thrhld -= 0.12f * psEncCtrl.input_tilt
        /** */
        /* Call Pitch estimator */
        /** */
        val lagIndex_djinnaddress = intArrayOf(psEncCtrl.sCmn.lagIndex)
        val contourIndex_djinnaddress = intArrayOf(psEncCtrl.sCmn.contourIndex)
        val LTPCorr_djinnaddress = floatArrayOf(psEnc.LTPCorr)
        psEncCtrl.sCmn.sigtype = PitchAnalysisCoreFLP.SKP_Silk_pitch_analysis_core_FLP(res,
                psEncCtrl.sCmn.pitchL, lagIndex_djinnaddress, contourIndex_djinnaddress,
                LTPCorr_djinnaddress, psEnc.sCmn.prevLag, psEnc.pitchEstimationThreshold, thrhld,
                psEnc.sCmn.fs_kHz, psEnc.sCmn.pitchEstimationComplexity)
        psEncCtrl.sCmn.lagIndex = lagIndex_djinnaddress[0]
        psEncCtrl.sCmn.contourIndex = contourIndex_djinnaddress[0]
        psEnc.LTPCorr = LTPCorr_djinnaddress[0]
    }
}