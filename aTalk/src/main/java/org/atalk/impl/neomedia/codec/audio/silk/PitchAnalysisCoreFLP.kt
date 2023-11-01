/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Pitch analysis.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
// TODO: float or dobule ???
object PitchAnalysisCoreFLP {
    const val SCRATCH_SIZE = 22
    const val eps = 1.192092896e-07f

    /* using log2() helps the fixed-point conversion */
    fun SKP_P_log2(x: Double): Float {
        return (3.32192809488736 * Math.log10(x)).toFloat()
    }

    /**
     * CORE PITCH ANALYSIS FUNCTION.
     *
     * @param signal
     * signal of length PITCH_EST_FRAME_LENGTH_MS*Fs_kHz
     * @param pitch_out
     * 4 pitch lag values
     * @param lagIndex
     * lag Index
     * @param contourIndex
     * pitch contour Index
     * @param LTPCorr
     * normalized correlation; input: value from previous frame
     * @param prevLag
     * last lag of previous frame; set to zero is unvoiced
     * @param search_thres1
     * first stage threshold for lag candidates 0 - 1
     * @param search_thres2
     * final threshold for lag candidates 0 - 1
     * @param Fs_kHz
     * sample frequency (kHz)
     * @param complexity
     * Complexity setting, 0-2, where 2 is highest
     * @return voicing estimate: 0 voiced, 1 unvoiced
     */
    fun SKP_Silk_pitch_analysis_core_FLP( /* O voicing estimate: 0 voiced, 1 unvoiced */
            signal: FloatArray,  /* I signal of length PITCH_EST_FRAME_LENGTH_MS*Fs_kHz */
            pitch_out: IntArray?,  /* O 4 pitch lag values */
            lagIndex: IntArray,  /* O lag Index */
            contourIndex: IntArray,  /* O pitch contour Index */
            LTPCorr: FloatArray,  /* I/O normalized correlation; input: value from previous frame */
            prevLag: Int,  /* I last lag of previous frame; set to zero is unvoiced */
            search_thres1: Float,  /* I first stage threshold for lag candidates 0 - 1 */
            search_thres2: Float,  /* I final threshold for lag candidates 0 - 1 */
            Fs_kHz: Int,  /* I sample frequency (kHz) */
            complexity: Int /* I Complexity setting, 0-2, where 2 is highest */
    ): Int {
        var prevLag = prevLag
        val signal_8kHz = FloatArray(CommonPitchEstDefines.PITCH_EST_FRAME_LENGTH_MS * 8)
        val signal_4kHz = FloatArray(CommonPitchEstDefines.PITCH_EST_FRAME_LENGTH_MS * 4)
        val scratch_mem = FloatArray(CommonPitchEstDefines.PITCH_EST_MAX_FRAME_LENGTH * 3)
        val filt_state = FloatArray(CommonPitchEstDefines.PITCH_EST_MAX_DECIMATE_STATE_LENGTH)
        var i: Int
        var k: Int
        var d: Int
        var j: Int
        var threshold: Float
        val contour_bias: Float
        val C = Array(CommonPitchEstDefines.PITCH_EST_NB_SUBFR) { FloatArray((CommonPitchEstDefines.PITCH_EST_MAX_LAG shr 1) + 5) } /*
																					 * use to be +2
																					 * but then
																					 * valgrind
																					 * reported
																					 * errors for
																					 * SWB
																					 */
        val CC = FloatArray(CommonPitchEstDefines.PITCH_EST_NB_CBKS_STAGE2_EXT)
        var basis_ptr: FloatArray
        var target_ptr_offset: Int
        var basis_ptr_offset: Int
        var cross_corr: Double
        var normalizer: Double
        var energy: Double
        var energy_tmp: Double
        val d_srch = IntArray(CommonPitchEstDefines.PITCH_EST_D_SRCH_LENGTH)
        val d_comp = ShortArray((CommonPitchEstDefines.PITCH_EST_MAX_LAG shr 1) + 5)
        var length_d_srch: Int
        val Cmax: Float
        var CCmax: Float
        var CCmax_b: Float
        var CCmax_new_b: Float
        var CCmax_new: Float
        var CBimax: Int
        var CBimax_new: Int
        var lag: Int
        val start_lag: Int
        val end_lag: Int
        var lag_new: Int
        val cbk_offset: Int
        val cbk_size: Int
        var lag_log2: Float
        val prevLag_log2: Float
        var delta_lag_log2_sqr: Float
        val energies_st3 = Array(CommonPitchEstDefines.PITCH_EST_NB_SUBFR) { Array(CommonPitchEstDefines.PITCH_EST_NB_CBKS_STAGE3_MAX) { FloatArray(CommonPitchEstDefines.PITCH_EST_NB_STAGE3_LAGS) } }
        val cross_corr_st3 = Array(CommonPitchEstDefines.PITCH_EST_NB_SUBFR) { Array(CommonPitchEstDefines.PITCH_EST_NB_CBKS_STAGE3_MAX) { FloatArray(CommonPitchEstDefines.PITCH_EST_NB_STAGE3_LAGS) } }
        var diff: Int
        var lag_counter: Int
        val sf_length: Int
        val sf_length_8kHz: Int
        val max_lag: Int
        assert(Fs_kHz == 8 || Fs_kHz == 12 || Fs_kHz == 16 || Fs_kHz == 24)
        assert(complexity >= SigProcFIXConstants.SKP_Silk_PITCH_EST_MIN_COMPLEX)
        assert(complexity <= SigProcFIXConstants.SKP_Silk_PITCH_EST_MAX_COMPLEX)
        assert(search_thres1 in 0.0f..1.0f)
        assert(search_thres2 in 0.0f..1.0f)

        /* Setup frame lengths max / min lag for the sampling frequency */
        val frame_length: Int = CommonPitchEstDefines.PITCH_EST_FRAME_LENGTH_MS * Fs_kHz
        val frame_length_4kHz: Int = CommonPitchEstDefines.PITCH_EST_FRAME_LENGTH_MS * 4
        val frame_length_8kHz: Int = CommonPitchEstDefines.PITCH_EST_FRAME_LENGTH_MS * 8
        sf_length = frame_length shr 3
        val sf_length_4kHz: Int = frame_length_4kHz shr 3
        sf_length_8kHz = frame_length_8kHz shr 3
        val min_lag: Int = CommonPitchEstDefines.PITCH_EST_MIN_LAG_MS * Fs_kHz
        val min_lag_4kHz: Int = CommonPitchEstDefines.PITCH_EST_MIN_LAG_MS * 4
        val min_lag_8kHz: Int = CommonPitchEstDefines.PITCH_EST_MIN_LAG_MS * 8
        max_lag = CommonPitchEstDefines.PITCH_EST_MAX_LAG_MS * Fs_kHz
        val max_lag_4kHz: Int = CommonPitchEstDefines.PITCH_EST_MAX_LAG_MS * 4
        val max_lag_8kHz: Int = CommonPitchEstDefines.PITCH_EST_MAX_LAG_MS * 8
        for (i_djinn in 0 until CommonPitchEstDefines.PITCH_EST_NB_SUBFR) {
            for (j_djinn in 0 until (CommonPitchEstDefines.PITCH_EST_MAX_LAG shr 1) + 5) {
                C[i_djinn][j_djinn] = 0f
            }
        }

        /* Resample from input sampled at Fs_kHz to 8 kHz */
        if (Fs_kHz == 12) {
            val signal_12 = ShortArray(12 * CommonPitchEstDefines.PITCH_EST_FRAME_LENGTH_MS)
            val signal_8 = ShortArray(8 * CommonPitchEstDefines.PITCH_EST_FRAME_LENGTH_MS)
            val R23 = IntArray(6)

            /* Resample to 12 -> 8 khz */
            for (i_djinn in 0..5) R23[i_djinn] = 0
            SigProcFLP.SKP_float2short_array(signal_12, 0, signal, 0,
                    CommonPitchEstDefines.PITCH_EST_FRAME_LENGTH_MS * 12)
            ResamplerDown23.SKP_Silk_resampler_down2_3(R23, 0, signal_8, 0, signal_12, 0,
                    CommonPitchEstDefines.PITCH_EST_FRAME_LENGTH_MS * 12)
            SigProcFLP.SKP_short2float_array(signal_8kHz, 0, signal_8, 0, frame_length_8kHz)
        } else if (Fs_kHz == 16) {
            if (complexity == SigProcFIXConstants.SKP_Silk_PITCH_EST_MAX_COMPLEX) {
                assert(4 <= CommonPitchEstDefines.PITCH_EST_MAX_DECIMATE_STATE_LENGTH)
                for (i_djinn in 0..3) filt_state[i_djinn] = 0f
                Decimate2CoarseFLP.SKP_Silk_decimate2_coarse_FLP(signal, 0, filt_state, 0,
                        signal_8kHz, 0, scratch_mem, 0, frame_length_8kHz)
            } else {
                assert(2 <= CommonPitchEstDefines.PITCH_EST_MAX_DECIMATE_STATE_LENGTH)
                for (i_djinn in 0..1) filt_state[i_djinn] = 0f
                Decimate2CoarsestFLP.SKP_Silk_decimate2_coarsest_FLP(signal, 0, filt_state, 0,
                        signal_8kHz, 0, scratch_mem, 0, frame_length_8kHz)
            }
        } else if (Fs_kHz == 24) {
            val signal_24 = ShortArray(CommonPitchEstDefines.PITCH_EST_MAX_FRAME_LENGTH)
            val signal_8 = ShortArray(8 * CommonPitchEstDefines.PITCH_EST_FRAME_LENGTH_MS)
            val filt_state_fix = IntArray(8)

            /* Resample to 24 -> 8 khz */
            SigProcFLP.SKP_float2short_array(signal_24, 0, signal, 0,
                    24 * CommonPitchEstDefines.PITCH_EST_FRAME_LENGTH_MS)
            for (i_djinn in 0..7) filt_state_fix[i_djinn] = 0
            ResamplerDown3.SKP_Silk_resampler_down3(filt_state_fix, 0, signal_8, 0, signal_24, 0,
                    24 * CommonPitchEstDefines.PITCH_EST_FRAME_LENGTH_MS)
            SigProcFLP.SKP_short2float_array(signal_8kHz, 0, signal_8, 0, frame_length_8kHz)
        } else {
            assert(Fs_kHz == 8)
            for (i_djinn in 0 until frame_length_8kHz) signal_8kHz[i_djinn] = signal[i_djinn]
        }

        /* Decimate again to 4 kHz. Set mem to zero */
        if (complexity == SigProcFIXConstants.SKP_Silk_PITCH_EST_MAX_COMPLEX) {
            assert(4 <= CommonPitchEstDefines.PITCH_EST_MAX_DECIMATE_STATE_LENGTH)
            for (i_djinn in 0..3) filt_state[i_djinn] = 0f
            Decimate2CoarseFLP.SKP_Silk_decimate2_coarse_FLP(signal_8kHz, 0, filt_state, 0,
                    signal_4kHz, 0, scratch_mem, 0, frame_length_4kHz)
        } else {
            assert(2 <= CommonPitchEstDefines.PITCH_EST_MAX_DECIMATE_STATE_LENGTH)
            for (i_djinn in 0..3) filt_state[i_djinn] = 0f
            Decimate2CoarsestFLP.SKP_Silk_decimate2_coarsest_FLP(signal_8kHz, 0, filt_state, 0,
                    signal_4kHz, 0, scratch_mem, 0, frame_length_4kHz)
        }

        /* Low-pass filter */i = frame_length_4kHz - 1
        while (i > 0) {
            signal_4kHz[i] += signal_4kHz[i - 1]
            i--
        }
        /******************************************************************************
         * FIRST STAGE, operating in 4 khz
         */
        var target_ptr: FloatArray = signal_4kHz
        target_ptr_offset = frame_length_4kHz shr 1
        k = 0
        while (k < 2) {
            assert(target_ptr_offset >= 0)
            assert(target_ptr_offset + sf_length_8kHz <= frame_length_4kHz)
            basis_ptr = target_ptr
            basis_ptr_offset = target_ptr_offset - min_lag_4kHz
            assert(basis_ptr_offset >= 0)
            assert(basis_ptr_offset + sf_length_8kHz <= frame_length_4kHz)

            /* Calculate first vector products before loop */
            cross_corr = InnerProductFLP.SKP_Silk_inner_product_FLP(target_ptr, target_ptr_offset,
                    basis_ptr, basis_ptr_offset, sf_length_8kHz)
            normalizer = EnergyFLP.SKP_Silk_energy_FLP(basis_ptr, basis_ptr_offset, sf_length_8kHz) + 1000.0f
            C[0][min_lag_4kHz] += (cross_corr / Math.sqrt(normalizer)).toFloat()

            /* From now on normalizer is computed recursively */
            d = min_lag_4kHz + 1
            while (d <= max_lag_4kHz) {
                basis_ptr_offset--
                assert(basis_ptr_offset >= 0)
                assert(basis_ptr_offset + sf_length_8kHz <= frame_length_4kHz)
                cross_corr = InnerProductFLP.SKP_Silk_inner_product_FLP(target_ptr,
                        target_ptr_offset, basis_ptr, basis_ptr_offset, sf_length_8kHz)

                /* Add contribution of new sample and remove contribution from oldest sample */
                // normalizer +=
                // basis_ptr[ 0 ] * basis_ptr[ 0 ] -
                // basis_ptr[ sf_length_8kHz ] * basis_ptr[ sf_length_8kHz ];
                normalizer += (basis_ptr[basis_ptr_offset + 0] * basis_ptr[basis_ptr_offset + 0]
                        - basis_ptr[basis_ptr_offset + sf_length_8kHz]
                        * basis_ptr[basis_ptr_offset + sf_length_8kHz]).toDouble()
                C[0][d] += (cross_corr / Math.sqrt(normalizer)).toFloat()
                d++
            }
            /* Update target pointer */
            target_ptr_offset += sf_length_8kHz
            k++
        }

        /* Apply short-lag bias */i = max_lag_4kHz
        while (i >= min_lag_4kHz) {
            C[0][i] -= C[0][i] * i / 4096.0f
            i--
        }

        /* Sort */
        length_d_srch = 5 + complexity
        assert(length_d_srch <= CommonPitchEstDefines.PITCH_EST_D_SRCH_LENGTH)
        SortFLP.SKP_Silk_insertion_sort_decreasing_FLP(C[0], min_lag_4kHz, d_srch, max_lag_4kHz
                - min_lag_4kHz + 1, length_d_srch)

        /* Escape if correlation is very low already here */
        Cmax = C[0][min_lag_4kHz]
        target_ptr = signal_4kHz
        target_ptr_offset = frame_length_4kHz shr 1
        energy = 1000.0
        i = 0
        while (i < frame_length_4kHz shr 1) {
            energy += (target_ptr[target_ptr_offset + i] * target_ptr[target_ptr_offset + i]).toDouble()
            i++
        }
        threshold = Cmax * Cmax
        if (energy / 16.0f > threshold) {
            for (i_djinn in 0 until CommonPitchEstDefines.PITCH_EST_NB_SUBFR) pitch_out!![i_djinn] = 0
            LTPCorr[0] = 0.0f
            lagIndex[0] = 0
            contourIndex[0] = 0
            return 1
        }
        threshold = search_thres1 * Cmax
        i = 0
        while (i < length_d_srch) {

            /* Convert to 8 kHz indices for the sorted correlation that exceeds the threshold */
            if (C[0][min_lag_4kHz + i] > threshold) {
                d_srch[i] = d_srch[i] + min_lag_4kHz shl 1
            } else {
                length_d_srch = i
                break
            }
            i++
        }
        assert(length_d_srch > 0)
        i = min_lag_8kHz - 5
        while (i < max_lag_8kHz + 5) {
            d_comp[i] = 0
            i++
        }
        i = 0
        while (i < length_d_srch) {
            d_comp[d_srch[i]] = 1
            i++
        }

        /* Convolution */
        i = max_lag_8kHz + 3
        while (i >= min_lag_8kHz) {
            d_comp[i] = (d_comp[i] + d_comp[i - 1] + d_comp[i - 2]).toShort()
            i--
        }
        length_d_srch = 0
        i = min_lag_8kHz
        while (i < max_lag_8kHz + 1) {
            if (d_comp[i + 1] > 0) {
                d_srch[length_d_srch] = i
                length_d_srch++
            }
            i++
        }

        /* Convolution */i = max_lag_8kHz + 3
        while (i >= min_lag_8kHz) {
            d_comp[i] = (d_comp[i] + d_comp[i - 1] + d_comp[i - 2] + d_comp[i - 3]).toShort()
            i--
        }
        var length_d_comp: Int = 0
        i = min_lag_8kHz
        while (i < max_lag_8kHz + 4) {
            if (d_comp[i] > 0) {
                d_comp[length_d_comp] = (i - 2).toShort()
                length_d_comp++
            }
            i++
        }
        /**********************************************************************************
         * SECOND STAGE, operating at 8 kHz, on lag sections with high correlation
         */
        /*********************************************************************************
         * Find energy of each subframe projected onto its history, for a range of delays
         */
        for (i_djinn in 0 until CommonPitchEstDefines.PITCH_EST_NB_SUBFR) for (j_djinn in 0 until (CommonPitchEstDefines.PITCH_EST_MAX_LAG shr 1) + 5) C[i_djinn][j_djinn] = 0f
        target_ptr = signal_8kHz /* point to middle of frame */
        target_ptr_offset = frame_length_4kHz
        k = 0
        while (k < CommonPitchEstDefines.PITCH_EST_NB_SUBFR) {
            assert(target_ptr_offset >= 0)
            assert(target_ptr_offset + sf_length_8kHz <= frame_length_8kHz)
            energy_tmp = EnergyFLP.SKP_Silk_energy_FLP(target_ptr, target_ptr_offset,
                    sf_length_8kHz)
            j = 0
            while (j < length_d_comp) {
                d = d_comp[j].toInt()
                basis_ptr = target_ptr
                basis_ptr_offset = target_ptr_offset - d
                assert(basis_ptr_offset >= 0)
                assert(basis_ptr_offset + sf_length_8kHz <= frame_length_8kHz)
                cross_corr = InnerProductFLP.SKP_Silk_inner_product_FLP(basis_ptr,
                        basis_ptr_offset, target_ptr, target_ptr_offset, sf_length_8kHz)
                energy = EnergyFLP.SKP_Silk_energy_FLP(basis_ptr, basis_ptr_offset, sf_length_8kHz)
                if (cross_corr > 0.0f) {
                    C[k][d] = (cross_corr * cross_corr / (energy * energy_tmp + eps)).toFloat()
                } else {
                    C[k][d] = 0.0f
                }
                j++
            }
            target_ptr_offset += sf_length_8kHz
            k++
        }

        /* search over lag range and lags codebook */
        /* scale factor for lag codebook, as a function of center lag */
        CCmax = 0.0f /* This value doesn't matter */
        CCmax_b = -1000.0f
        CBimax = 0 /* To avoid returning undefined lag values */
        lag = -1 /* To check if lag with strong enough correlation has been found */
        if (prevLag > 0) {
            if (Fs_kHz == 12) {
                prevLag = (prevLag shl 1) / 3
            } else if (Fs_kHz == 16) {
                prevLag = prevLag shr 1
            } else if (Fs_kHz == 24) {
                prevLag = prevLag / 3
            }
            prevLag_log2 = SKP_P_log2(prevLag.toDouble())
        } else {
            prevLag_log2 = 0f
        }

        /* If input is 8 khz use a larger codebook here because it is last stage */
        val nb_cbks_stage2: Int = if (Fs_kHz == 8 && complexity > SigProcFIXConstants.SKP_Silk_PITCH_EST_MIN_COMPLEX) {
            CommonPitchEstDefines.PITCH_EST_NB_CBKS_STAGE2_EXT
        } else {
            CommonPitchEstDefines.PITCH_EST_NB_CBKS_STAGE2
        }
        k = 0
        while (k < length_d_srch) {
            d = d_srch[k]
            j = 0
            while (j < nb_cbks_stage2) {
                CC[j] = 0.0f
                i = 0
                while (i < CommonPitchEstDefines.PITCH_EST_NB_SUBFR) {

                    /* Try all codebooks */
                    CC[j] += C[i][d + PitchEstTables.SKP_Silk_CB_lags_stage2[i]!![j]]
                    i++
                }
                j++
            }
            /* Find best codebook */
            CCmax_new = -1000.0f
            CBimax_new = 0
            i = 0
            while (i < nb_cbks_stage2) {
                if (CC[i] > CCmax_new) {
                    CCmax_new = CC[i]
                    CBimax_new = i
                }
                i++
            }
            CCmax_new = Math.max(CCmax_new, 0.0f) /*
													 * To avoid taking square root of negative
													 * number later
													 */
            CCmax_new_b = CCmax_new

            /* Bias towards shorter lags */
            lag_log2 = SKP_P_log2(d.toDouble())
            CCmax_new_b -= PitchEstDefinesFLP.PITCH_EST_FLP_SHORTLAG_BIAS * CommonPitchEstDefines.PITCH_EST_NB_SUBFR * lag_log2

            /* Bias towards previous lag */
            if (prevLag > 0) {
                delta_lag_log2_sqr = lag_log2 - prevLag_log2
                delta_lag_log2_sqr *= delta_lag_log2_sqr
                CCmax_new_b -= (PitchEstDefinesFLP.PITCH_EST_FLP_PREVLAG_BIAS * CommonPitchEstDefines.PITCH_EST_NB_SUBFR * LTPCorr[0]
                        * delta_lag_log2_sqr) / (delta_lag_log2_sqr + 0.5f)
            }
            if (CCmax_new_b > CCmax_b
                    && CCmax_new > CommonPitchEstDefines.PITCH_EST_NB_SUBFR * search_thres2 * search_thres2) {
                CCmax_b = CCmax_new_b
                CCmax = CCmax_new
                lag = d
                CBimax = CBimax_new
            }
            k++
        }
        if (lag == -1) {
            /* No suitable candidate found */
            for (i_djinn in 0 until CommonPitchEstDefines.PITCH_EST_NB_SUBFR) pitch_out!![i_djinn] = 0
            LTPCorr[0] = 0.0f
            lagIndex[0] = 0
            contourIndex[0] = 0
            return 1
        }
        if (Fs_kHz > 8) {
            /* Search in original signal */

            /* Compensate for decimation */
            assert(lag == SigProcFIX.SKP_SAT16(lag))
            lag = if (Fs_kHz == 12) {
                SigProcFIX.SKP_RSHIFT_ROUND(Macros.SKP_SMULBB(lag, 3), 1)
            } else if (Fs_kHz == 16) {
                lag shl 1
            } else {
                Macros.SKP_SMULBB(lag, 3)
            }
            lag = SigProcFIX.SKP_LIMIT_int(lag, min_lag, max_lag)
            start_lag = Math.max(lag - 2, min_lag)
            end_lag = Math.min(lag + 2, max_lag)
            lag_new = lag /* to avoid undefined lag */
            CBimax = 0 /* to avoid undefined lag */
            assert(CCmax >= 0.0f)
            LTPCorr[0] = Math.sqrt((CCmax / CommonPitchEstDefines.PITCH_EST_NB_SUBFR).toDouble()).toFloat() // Output normalized
            // correlation
            CCmax = -1000.0f

            /* Calculate the correlations and energies needed in stage 3 */
            SKP_P_Ana_calc_corr_st3(cross_corr_st3, signal, 0, start_lag, sf_length, complexity)
            SKP_P_Ana_calc_energy_st3(energies_st3, signal, 0, start_lag, sf_length, complexity)
            lag_counter = 0
            assert(lag == SigProcFIX.SKP_SAT16(lag))
            contour_bias = PitchEstDefinesFLP.PITCH_EST_FLP_FLATCONTOUR_BIAS / lag

            /* Setup cbk parameters according to complexity setting */
            cbk_size = PitchEstTables.SKP_Silk_cbk_sizes_stage3[complexity].toInt()
            cbk_offset = PitchEstTables.SKP_Silk_cbk_offsets_stage3[complexity].toInt()
            d = start_lag
            while (d <= end_lag) {
                j = cbk_offset
                while (j < cbk_offset + cbk_size) {
                    cross_corr = 0.0
                    energy = eps.toDouble()
                    k = 0
                    while (k < CommonPitchEstDefines.PITCH_EST_NB_SUBFR) {
                        energy += energies_st3[k][j][lag_counter].toDouble()
                        cross_corr += cross_corr_st3[k][j][lag_counter].toDouble()
                        k++
                    }
                    if (cross_corr > 0.0) {
                        CCmax_new = (cross_corr * cross_corr / energy).toFloat()
                        /* Reduce depending on flatness of contour */
                        diff = j - (CommonPitchEstDefines.PITCH_EST_NB_CBKS_STAGE3_MAX shr 1)
                        CCmax_new *= 1.0f - contour_bias * diff * diff
                    } else {
                        CCmax_new = 0.0f
                    }
                    if (CCmax_new > CCmax) {
                        CCmax = CCmax_new
                        lag_new = d
                        CBimax = j
                    }
                    j++
                }
                lag_counter++
                d++
            }
            k = 0
            while (k < CommonPitchEstDefines.PITCH_EST_NB_SUBFR) {
                pitch_out!![k] = lag_new + PitchEstTables.SKP_Silk_CB_lags_stage3[k]!![CBimax]
                k++
            }
            lagIndex[0] = lag_new - min_lag
            contourIndex[0] = CBimax
        } else {
            /* Save Lags and correlation */
            assert(CCmax >= 0.0f)
            LTPCorr[0] = Math.sqrt((CCmax / CommonPitchEstDefines.PITCH_EST_NB_SUBFR).toDouble()).toFloat() /*
																		 * Output normalized
																		 * correlation
																		 */
            k = 0
            while (k < CommonPitchEstDefines.PITCH_EST_NB_SUBFR) {
                pitch_out!![k] = lag + PitchEstTables.SKP_Silk_CB_lags_stage2[k]!![CBimax]
                k++
            }
            lagIndex[0] = lag - min_lag
            contourIndex[0] = CBimax
        }
        assert(lagIndex[0] >= 0)
        /* return as voiced */
        return 0
    }

    /**
     * Internally used functions.
     *
     * @param cross_corr_st3
     * 3 DIM correlation array.
     * @param signal
     * vector to correlate.
     * @param signal_offset
     * offset of valid data.
     * @param start_lag
     * start lag.
     * @param sf_length
     * sub frame length.
     * @param complexity
     * Complexity setting.
     */
    fun SKP_P_Ana_calc_corr_st3(cross_corr_st3: Array<Array<FloatArray>>, signal: FloatArray,  /*
																					 * I vector to
																					 * correlate
																					 */
            signal_offset: Int, start_lag: Int,  /* I start lag */
            sf_length: Int,  /* I sub frame length */
            complexity: Int /* I Complexity setting */
    )
            /***********************************************************************
             * Calculates the correlations used in stage 3 search. In order to cover the whole lag codebook
             * for all the searched offset lags (lag +- 2), the following correlations are needed in each
             * sub frame:
             *
             * sf1: lag range [-8,...,7] total 16 correlations sf2: lag range [-4,...,4] total 9
             * correlations sf3: lag range [-3,....4] total 8 correltions sf4: lag range [-6,....8] total 15
             * correlations
             *
             * In total 48 correlations. The direct implementation computed in worst case 4*12*5 = 240
             * correlations, but more likely around 120.
             */
    {
        var basis_ptr: FloatArray
        var basis_ptr_offset: Int
        var i: Int
        var j: Int
        var lag_counter: Int
        var delta: Int
        var idx: Int
        val scratch_mem = FloatArray(SCRATCH_SIZE)
        assert(complexity >= SigProcFIXConstants.SKP_Silk_PITCH_EST_MIN_COMPLEX)
        assert(complexity <= SigProcFIXConstants.SKP_Silk_PITCH_EST_MAX_COMPLEX)
        val cbk_offset: Int = PitchEstTables.SKP_Silk_cbk_offsets_stage3[complexity].toInt()
        val cbk_size: Int = PitchEstTables.SKP_Silk_cbk_sizes_stage3[complexity].toInt()
        val target_ptr: FloatArray = signal /* Pointer to middle of frame */
        var target_ptr_offset: Int = signal_offset + (sf_length shl 2)
        var k = 0
        while (k < CommonPitchEstDefines.PITCH_EST_NB_SUBFR) {
            lag_counter = 0

            /* Calculate the correlations for each subframe */
            j = PitchEstTables.SKP_Silk_Lag_range_stage3[complexity][k]!![0].toInt()
            while (j <= PitchEstTables.SKP_Silk_Lag_range_stage3[complexity][k]!![1]) {
                basis_ptr = target_ptr
                basis_ptr_offset = target_ptr_offset - (start_lag + j)
                assert(lag_counter < SCRATCH_SIZE)
                scratch_mem[lag_counter] = InnerProductFLP.SKP_Silk_inner_product_FLP(
                        target_ptr, target_ptr_offset, basis_ptr, basis_ptr_offset, sf_length).toFloat()
                lag_counter++
                j++
            }
            delta = PitchEstTables.SKP_Silk_Lag_range_stage3[complexity][k]!![0].toInt()
            i = cbk_offset
            while (i < cbk_offset + cbk_size) {

                /* Fill out the 3 dim array that stores the correlations for */
                /* each code_book vector for each start lag */
                idx = PitchEstTables.SKP_Silk_CB_lags_stage3[k]!![i] - delta
                j = 0
                while (j < CommonPitchEstDefines.PITCH_EST_NB_STAGE3_LAGS) {
                    assert(idx + j < SCRATCH_SIZE)
                    assert(idx + j < lag_counter)
                    cross_corr_st3[k][i][j] = scratch_mem[idx + j]
                    j++
                }
                i++
            }
            target_ptr_offset += sf_length
            k++
        }
    }

    /**
     * @param energies_st3
     * 3 DIM correlation array.
     * @param signal
     * vector to correlate.
     * @param signal_offset
     * offset of valid data.
     * @param start_lag
     * start lag.
     * @param sf_length
     * sub frame length.
     * @param complexity
     * Complexity setting.
     */
    fun SKP_P_Ana_calc_energy_st3(energies_st3: Array<Array<FloatArray>>, signal: FloatArray,  /*
																					 * I vector to
																					 * correlate
																					 */
            signal_offset: Int, start_lag: Int,  /* I start lag */
            sf_length: Int,  /* I sub frame length */
            complexity: Int /* I Complexity setting */
    )
            /****************************************************************
             * Calculate the energies for first two subframes. The energies are calculated recursively.
             */
    {
        val target_ptr: FloatArray
        var basis_ptr: FloatArray
        var target_ptr_offset: Int
        var basis_ptr_offset: Int
        var energy: Double
        var k: Int
        var i: Int
        var j: Int
        var lag_counter: Int
        val cbk_offset: Int
        val cbk_size: Int
        var delta: Int
        var idx: Int
        val scratch_mem = FloatArray(SCRATCH_SIZE)
        assert(complexity >= SigProcFIXConstants.SKP_Silk_PITCH_EST_MIN_COMPLEX)
        assert(complexity <= SigProcFIXConstants.SKP_Silk_PITCH_EST_MAX_COMPLEX)
        cbk_offset = PitchEstTables.SKP_Silk_cbk_offsets_stage3[complexity].toInt()
        cbk_size = PitchEstTables.SKP_Silk_cbk_sizes_stage3[complexity].toInt()
        target_ptr = signal
        target_ptr_offset = signal_offset + (sf_length shl 2)
        k = 0
        while (k < CommonPitchEstDefines.PITCH_EST_NB_SUBFR) {
            lag_counter = 0

            /* Calculate the energy for first lag */basis_ptr = target_ptr
            basis_ptr_offset = (target_ptr_offset
                    - (start_lag + PitchEstTables.SKP_Silk_Lag_range_stage3[complexity][k]!![0]))
            energy = EnergyFLP.SKP_Silk_energy_FLP(basis_ptr, basis_ptr_offset, sf_length) + 1e-3
            assert(energy >= 0.0)
            scratch_mem[lag_counter] = energy.toFloat()
            lag_counter++
            i = 1
            while (i < (PitchEstTables.SKP_Silk_Lag_range_stage3[complexity][k]!![1]
                            - PitchEstTables.SKP_Silk_Lag_range_stage3[complexity][k]!![0] + 1)) {

                /* remove part outside new window */
                energy -= (basis_ptr[basis_ptr_offset + sf_length - i]
                        * basis_ptr[basis_ptr_offset + sf_length - i]).toDouble()
                assert(energy >= 0.0)

                /* add part that comes into window */
                energy += (basis_ptr[basis_ptr_offset - i] * basis_ptr[basis_ptr_offset - i]).toDouble()
                assert(energy >= 0.0)
                assert(lag_counter < SCRATCH_SIZE)
                scratch_mem[lag_counter] = energy.toFloat()
                lag_counter++
                i++
            }
            delta = PitchEstTables.SKP_Silk_Lag_range_stage3[complexity][k]!![0].toInt()
            i = cbk_offset
            while (i < cbk_offset + cbk_size) {

                /* Fill out the 3 dim array that stores the correlations for */
                /* each code_book vector for each start lag */idx = PitchEstTables.SKP_Silk_CB_lags_stage3[k]!![i] - delta
                j = 0
                while (j < CommonPitchEstDefines.PITCH_EST_NB_STAGE3_LAGS) {
                    assert(idx + j < SCRATCH_SIZE)
                    assert(idx + j < lag_counter)
                    energies_st3[k][i][j] = scratch_mem[idx + j]
                    assert(energies_st3[k][i][j] >= 0.0f)
                    j++
                }
                i++
            }
            target_ptr_offset += sf_length
            k++
        }
    }
}