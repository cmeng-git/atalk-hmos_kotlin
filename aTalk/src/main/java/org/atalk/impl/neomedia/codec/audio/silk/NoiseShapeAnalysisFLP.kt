/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

import kotlin.math.pow

/**
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object NoiseShapeAnalysisFLP {
    /**
     * Compute noise shaping coefficients and initial gain values.
     *
     * @param psEnc
     * Encoder state FLP
     * @param psEncCtrl
     * Encoder control FLP
     * @param pitch_res
     * LPC residual from pitch analysis
     * @param pitch_res_offset
     * offset of valid data.
     * @param x
     * Input signal [frame_length + la_shape]
     * @param x_offset
     * offset of valid data.
     */
    fun SKP_Silk_noise_shape_analysis_FLP(psEnc: SKP_Silk_encoder_state_FLP?,  /*
																					 * I/O Encoder
																					 * state FLP
																					 */
            psEncCtrl: SKP_Silk_encoder_control_FLP,  /* I/O Encoder control FLP */
            pitch_res: FloatArray,  /* I LPC residual from pitch analysis */
            pitch_res_offset: Int, x: FloatArray?,  /* I Input signal [frame_length + la_shape] */
            x_offset: Int) {
        val psShapeSt = psEnc!!.sShape
        var k: Int
        val nSamples: Int
        var SNR_adj_dB: Float
        var HarmBoost: Float
        var HarmShapeGain: Float
        val Tilt: Float
        var nrg: Float
        var pre_nrg = 0f
        var log_energy: Float
        var log_energy_prev: Float
        var energy_variation: Float
        val delta: Float
        var BWExp1: Float
        var BWExp2: Float
        var gain_mult: Float
        val gain_add: Float
        val strength: Float
        var b: Float
        val x_windowed = FloatArray(Define.SHAPE_LPC_WIN_MAX)
        val auto_corr = FloatArray(Define.SHAPE_LPC_ORDER_MAX + 1)
        val x_ptr: FloatArray?
        val pitch_res_ptr: FloatArray
        var x_ptr_offset: Int
        var pitch_res_ptr_offset = 0

        /* Point to start of first LPC analysis block */
        x_ptr = x
        x_ptr_offset = (x_offset + psEnc.sCmn.la_shape - Define.SHAPE_LPC_WIN_MS * psEnc.sCmn.fs_kHz
                + psEnc.sCmn.subfr_length)
        /** */
        /* CONTROL SNR */
        /** */
        /* Reduce SNR_dB values if recent bitstream has exceeded TargetRate */
        psEncCtrl.current_SNR_dB = psEnc.SNR_dB - 0.05f * psEnc.BufferedInChannel_ms

        /* Reduce SNR_dB if inband FEC used */
        if (psEnc.speech_activity > DefineFLP.LBRR_SPEECH_ACTIVITY_THRES) {
            psEncCtrl.current_SNR_dB -= psEnc.inBandFEC_SNR_comp
        }
        /** */
        /* GAIN CONTROL */
        /** */
        /* Input quality is the average of the quality in the lowest two VAD bands */
        psEncCtrl.input_quality = 0.5f * (psEncCtrl.input_quality_bands[0] + psEncCtrl.input_quality_bands[1])

        /* Coding quality level, between 0.0 and 1.0 */
        psEncCtrl.coding_quality = SigProcFLP.SKP_sigmoid(0.25f * (psEncCtrl.current_SNR_dB - 18.0f))

        /* Reduce coding SNR during low speech activity */
        b = 1.0f - psEnc.speech_activity
        SNR_adj_dB = psEncCtrl.current_SNR_dB - (PerceptualParametersFLP.BG_SNR_DECR_dB
                * psEncCtrl.coding_quality * (0.5f + 0.5f * psEncCtrl.input_quality) * b * b)
        SNR_adj_dB += if (psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_VOICED) {
            /* Reduce gains for periodic signals */
            PerceptualParametersFLP.HARM_SNR_INCR_dB * psEnc.LTPCorr
        } else {
            /*
			 * For unvoiced signals and low-quality input, adjust the quality slower than SNR_dB
			 * setting
			 */
            ((-0.4f * psEncCtrl.current_SNR_dB + 6.0f)
                    * (1.0f - psEncCtrl.input_quality))
        }
        /** */
        /* SPARSENESS PROCESSING */
        /** */
        /* Set quantizer offset */
        if (psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_VOICED) {
            /* Initally set to 0; may be overruled in process_gains(..) */
            psEncCtrl.sCmn.QuantOffsetType = 0
            psEncCtrl.sparseness = 0.0f
        } else {
            /* Sparseness measure, based on relative fluctuations of energy per 2 milliseconds */
            nSamples = 2 * psEnc.sCmn.fs_kHz
            energy_variation = 0.0f
            log_energy_prev = 0.0f
            pitch_res_ptr = pitch_res
            pitch_res_ptr_offset = pitch_res_offset
            k = 0
            while (k < Define.FRAME_LENGTH_MS / 2) {
                nrg = (nSamples
                        + EnergyFLP.SKP_Silk_energy_FLP(pitch_res_ptr, pitch_res_ptr_offset,
                        nSamples).toFloat())
                log_energy = MainFLP.SKP_Silk_log2(nrg.toDouble())
                if (k > 0) {
                    energy_variation += Math.abs(log_energy - log_energy_prev)
                }
                log_energy_prev = log_energy
                pitch_res_ptr_offset += nSamples
                k++
            }
            psEncCtrl.sparseness = SigProcFLP.SKP_sigmoid(0.4f * (energy_variation - 5.0f))

            /* Set quantization offset depending on sparseness measure */
            if (psEncCtrl.sparseness > PerceptualParametersFLP.SPARSENESS_THRESHOLD_QNT_OFFSET) {
                psEncCtrl.sCmn.QuantOffsetType = 0
            } else {
                psEncCtrl.sCmn.QuantOffsetType = 1
            }

            /* Increase coding SNR for sparse signals */
            SNR_adj_dB += (PerceptualParametersFLP.SPARSE_SNR_INCR_dB
                    * (psEncCtrl.sparseness - 0.5f))
        }
        /** */
        /* Control bandwidth expansion */
        /** */
        delta = (PerceptualParametersFLP.LOW_RATE_BANDWIDTH_EXPANSION_DELTA
                * (1.0f - 0.75f * psEncCtrl.coding_quality))
        BWExp1 = PerceptualParametersFLP.BANDWIDTH_EXPANSION - delta
        BWExp2 = PerceptualParametersFLP.BANDWIDTH_EXPANSION + delta
        if (psEnc.sCmn.fs_kHz == 24) {
            /* Less bandwidth expansion for super wideband */
            BWExp1 = 1.0f - (1.0f - BWExp1) * PerceptualParametersFLP.SWB_BANDWIDTH_EXPANSION_REDUCTION
            BWExp2 = 1.0f - (1.0f - BWExp2) * PerceptualParametersFLP.SWB_BANDWIDTH_EXPANSION_REDUCTION
        }
        /* BWExp1 will be applied after BWExp2, so make it relative */
        BWExp1 /= BWExp2
        /** */
        /* Compute noise shaping AR coefs and gains */
        /** */
        k = 0
        while (k < Define.NB_SUBFR) {

            /* Apply window */
            ApplySineWindowFLP.SKP_Silk_apply_sine_window_FLP(x_windowed, 0, x_ptr, x_ptr_offset,
                    0, Define.SHAPE_LPC_WIN_MS * psEnc.sCmn.fs_kHz)

            /* Update pointer: next LPC analysis block */
            x_ptr_offset += psEnc.sCmn.subfr_length

            /* Calculate auto correlation */
            AutocorrelationFLP.SKP_Silk_autocorrelation_FLP(auto_corr, 0, x_windowed, 0,
                    Define.SHAPE_LPC_WIN_MS * psEnc.sCmn.fs_kHz, psEnc.sCmn.shapingLPCOrder + 1)

            /* Add white noise, as a fraction of energy */
            auto_corr[0] += auto_corr[0] * PerceptualParametersFLP.SHAPE_WHITE_NOISE_FRACTION

            /* Convert correlations to prediction coefficients, and compute residual energy */
            nrg = LevinsondurbinFLP.SKP_Silk_levinsondurbin_FLP(psEncCtrl.AR2, k
                    * Define.SHAPE_LPC_ORDER_MAX, auto_corr, psEnc.sCmn.shapingLPCOrder)

            /* Bandwidth expansion for synthesis filter shaping */
            BwexpanderFLP.SKP_Silk_bwexpander_FLP(psEncCtrl.AR2, k * Define.SHAPE_LPC_ORDER_MAX,
                    psEnc.sCmn.shapingLPCOrder, BWExp2)

            /* Make sure to fit in Q13 SKP_int16 */
            LPC_fit_int16(psEncCtrl.AR2, k * Define.SHAPE_LPC_ORDER_MAX, 1.0f, psEnc.sCmn.shapingLPCOrder,
                    3.999f)

            /* Compute noise shaping filter coefficients */
            // SKP_memcpy(
            // &psEncCtrl->AR1[ k * SHAPE_LPC_ORDER_MAX ],
            // &psEncCtrl->AR2[ k * SHAPE_LPC_ORDER_MAX ],
            // psEnc->sCmn.shapingLPCOrder * sizeof( SKP_float ) );
            for (i_djinn in 0 until psEnc.sCmn.shapingLPCOrder) psEncCtrl.AR1[k * Define.SHAPE_LPC_ORDER_MAX + i_djinn] = psEncCtrl.AR2[k
                    * Define.SHAPE_LPC_ORDER_MAX + i_djinn]

            /* Bandwidth expansion for analysis filter shaping */
            BwexpanderFLP.SKP_Silk_bwexpander_FLP(psEncCtrl.AR1, k * Define.SHAPE_LPC_ORDER_MAX,
                    psEnc.sCmn.shapingLPCOrder, BWExp1)

            /* Increase residual energy */
            nrg += PerceptualParametersFLP.SHAPE_MIN_ENERGY_RATIO * auto_corr[0]
            psEncCtrl.Gains[k] = Math.sqrt(nrg.toDouble()).toFloat()

            /* Ratio of prediction gains, in energy domain */
            val pre_nrg_djinnaddress = floatArrayOf(pre_nrg)
            LPCInvPredGainFLP.SKP_Silk_LPC_inverse_pred_gain_FLP(pre_nrg_djinnaddress,
                    psEncCtrl.AR2, k * Define.SHAPE_LPC_ORDER_MAX, psEnc.sCmn.shapingLPCOrder)
            pre_nrg = pre_nrg_djinnaddress[0]
            val nrg_djinnaddress = floatArrayOf(nrg)
            LPCInvPredGainFLP.SKP_Silk_LPC_inverse_pred_gain_FLP(nrg_djinnaddress, psEncCtrl.AR1, k
                    * Define.SHAPE_LPC_ORDER_MAX, psEnc.sCmn.shapingLPCOrder)
            nrg = nrg_djinnaddress[0]
            psEncCtrl.GainsPre[k] = Math.sqrt((pre_nrg / nrg).toDouble()).toFloat()
            k++
        }
        /** */
        /* Gain tweaking */
        /** */
        /* Increase gains during low speech activity and put lower limit on gains */
        gain_mult = 2.0.pow((-0.16f * SNR_adj_dB).toDouble()).toFloat()
        gain_add = 2.0.pow((0.16f * PerceptualParametersFLP.NOISE_FLOOR_dB).toDouble()).toFloat() + 2.0.pow((0.16f * PerceptualParametersFLP.RELATIVE_MIN_GAIN_dB).toDouble()).toFloat() * psEnc.avgGain
        k = 0
        while (k < Define.NB_SUBFR) {
            psEncCtrl.Gains[k] *= gain_mult
            psEncCtrl.Gains[k] += gain_add
            psEnc.avgGain += (psEnc.speech_activity * PerceptualParametersFLP.GAIN_SMOOTHING_COEF
                    * (psEncCtrl.Gains[k] - psEnc.avgGain))
            k++
        }
        /** */
        /* Decrease level during fricatives (de-essing) */
        /** */
        gain_mult = 1.0f + PerceptualParametersFLP.INPUT_TILT + (psEncCtrl.coding_quality
                * PerceptualParametersFLP.HIGH_RATE_INPUT_TILT)
        if (psEncCtrl.input_tilt <= 0.0f && psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_UNVOICED) {
            val essStrength = (-psEncCtrl.input_tilt * psEnc.speech_activity
                    * (1.0f - psEncCtrl.sparseness))
            if (psEnc.sCmn.fs_kHz == 24) {
                gain_mult *= Math.pow(2.0, (-0.16f
                        * PerceptualParametersFLP.DE_ESSER_COEF_SWB_dB * essStrength).toDouble()).toFloat()
            } else if (psEnc.sCmn.fs_kHz == 16) {
                gain_mult *= Math.pow(2.0, (-0.16f
                        * PerceptualParametersFLP.DE_ESSER_COEF_WB_dB * essStrength).toDouble()).toFloat()
            } else {
                assert(psEnc.sCmn.fs_kHz == 12 || psEnc.sCmn.fs_kHz == 8)
            }
        }
        k = 0
        while (k < Define.NB_SUBFR) {
            psEncCtrl.GainsPre[k] *= gain_mult
            k++
        }
        /** */
        /* Control low-frequency shaping and noise tilt */
        /** */
        /* Less low frequency shaping for noisy inputs */
        strength = (PerceptualParametersFLP.LOW_FREQ_SHAPING
                * (1.0f + PerceptualParametersFLP.LOW_QUALITY_LOW_FREQ_SHAPING_DECR
                * (psEncCtrl.input_quality_bands[0] - 1.0f)))
        if (psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_VOICED) {
            /*
			 * Reduce low frequencies quantization noise for periodic signals, depending on pitch
			 * lag
			 */
            /*
			 * f = 400; freqz([1, -0.98 + 2e-4 * f], [1, -0.97 + 7e-4 * f], 2^12, Fs); axis([0,
			 * 1000, -10, 1])
			 */
            k = 0
            while (k < Define.NB_SUBFR) {
                b = 0.2f / psEnc.sCmn.fs_kHz + 3.0f / psEncCtrl.sCmn.pitchL[k]
                psEncCtrl.LF_MA_shp[k] = -1.0f + b
                psEncCtrl.LF_AR_shp[k] = 1.0f - b - b * strength
                k++
            }
            Tilt = (-PerceptualParametersFLP.HP_NOISE_COEF
                    - ((1 - PerceptualParametersFLP.HP_NOISE_COEF)
                    * PerceptualParametersFLP.HARM_HP_NOISE_COEF * psEnc.speech_activity))
        } else {
            b = 1.3f / psEnc.sCmn.fs_kHz
            psEncCtrl.LF_MA_shp[0] = -1.0f + b
            psEncCtrl.LF_AR_shp[0] = 1.0f - b - b * strength * 0.6f
            k = 1
            while (k < Define.NB_SUBFR) {
                psEncCtrl.LF_MA_shp[k] = psEncCtrl.LF_MA_shp[k - 1]
                psEncCtrl.LF_AR_shp[k] = psEncCtrl.LF_AR_shp[k - 1]
                k++
            }
            Tilt = -PerceptualParametersFLP.HP_NOISE_COEF
        }
        /** */
        /* HARMONIC SHAPING CONTROL */
        /** */
        /* Control boosting of harmonic frequencies */
        HarmBoost = (PerceptualParametersFLP.LOW_RATE_HARMONIC_BOOST
                * (1.0f - psEncCtrl.coding_quality) * psEnc.LTPCorr)

        /* More harmonic boost for noisy input signals */
        HarmBoost += (PerceptualParametersFLP.LOW_INPUT_QUALITY_HARMONIC_BOOST
                * (1.0f - psEncCtrl.input_quality))
        if (Define.USE_HARM_SHAPING != 0 && psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_VOICED) {
            /* Harmonic noise shaping */
            HarmShapeGain = PerceptualParametersFLP.HARMONIC_SHAPING

            /* More harmonic noise shaping for high bitrates or noisy input */
            HarmShapeGain += (PerceptualParametersFLP.HIGH_RATE_OR_LOW_QUALITY_HARMONIC_SHAPING
                    * (1.0f - (1.0f - psEncCtrl.coding_quality) * psEncCtrl.input_quality))

            /* Less harmonic noise shaping for less periodic signals */
            HarmShapeGain *= Math.sqrt(psEnc.LTPCorr.toDouble()).toFloat()
        } else {
            HarmShapeGain = 0.0f
        }
        /** */
        /* Smooth over subframes */
        /** */
        k = 0
        while (k < Define.NB_SUBFR) {
            psShapeSt.HarmBoost_smth += (PerceptualParametersFLP.SUBFR_SMTH_COEF
                    * (HarmBoost - psShapeSt.HarmBoost_smth))
            psEncCtrl.HarmBoost[k] = psShapeSt.HarmBoost_smth
            psShapeSt.HarmShapeGain_smth += (PerceptualParametersFLP.SUBFR_SMTH_COEF
                    * (HarmShapeGain - psShapeSt.HarmShapeGain_smth))
            psEncCtrl.HarmShapeGain[k] = psShapeSt.HarmShapeGain_smth
            psShapeSt.Tilt_smth += (PerceptualParametersFLP.SUBFR_SMTH_COEF
                    * (Tilt - psShapeSt.Tilt_smth))
            psEncCtrl.Tilt[k] = psShapeSt.Tilt_smth
            k++
        }
    }

    /**
     *
     * @param a
     * Unstable/stabilized LPC vector [L].
     * @param a_offset
     * offset of valid data.
     * @param bwe
     * Bandwidth expansion factor.
     * @param L
     * Number of LPC parameters in the input vector.
     * @param maxVal
     * Maximum value allowed.
     */
    fun LPC_fit_int16(a: FloatArray?,  /* I/O: Unstable/stabilized LPC vector [L] */
            a_offset: Int, bwe: Float,  /* I: Bandwidth expansion factor */
            L: Int,  /* I: Number of LPC parameters in the input vector */
            maxVal: Float /* I Maximum value allowed */
    ) {
        var maxabs: Float
        var absval: Float
        var sc: Float
        var i: Int
        var idx = 0
        val invGain = FloatArray(1)
        BwexpanderFLP.SKP_Silk_bwexpander_FLP(a, a_offset, L, bwe)
        /** */
        /* Limit range of the LPCs */
        /** */
        /* Limit the maximum absolute value of the prediction coefficients */
        var k: Int = 0
        while (k < 1000) {

            /* Find maximum absolute value and its index */
            maxabs = -1.0f
            i = 0
            while (i < L) {
                absval = Math.abs(a!![a_offset + i])
                if (absval > maxabs) {
                    maxabs = absval
                    idx = i
                }
                i++
            }
            if (maxabs >= maxVal) {
                /* Reduce magnitude of prediction coefficients */
                sc = 0.995f * (1.0f - (1.0f - maxVal / maxabs) / (idx + 1))
                BwexpanderFLP.SKP_Silk_bwexpander_FLP(a, a_offset, L, sc)
            } else {
                break
            }
            k++
        }
        /* Reached the last iteration */
        if (k == 1000) {
            assert(false)
        }
        /** */
        /* Ensure stable LPCs */
        /** */
        k = 0
        while (k < 1000) {
            if (LPCInvPredGainFLP.SKP_Silk_LPC_inverse_pred_gain_FLP(invGain, a, a_offset, L) == 1) {
                BwexpanderFLP.SKP_Silk_bwexpander_FLP(a, a_offset, L, 0.997f)
            } else {
                break
            }
            k++
        }

        /* Reached the last iteration */
        if (k == 1000) {
            assert(false)
            i = 0
            while (i < L) {
                a!![i] = 0.0f
                i++
            }
        }
    }
}