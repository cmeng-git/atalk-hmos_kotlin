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
object VAD {
    /**
     * Initialization of the Silk VAD.
     *
     * @param psSilk_VAD
     * Silk VAD state.
     * @return return 0 if success.
     */
    fun SKP_Silk_VAD_Init( /* O Return value, 0 if success */
            psSilk_VAD: SKP_Silk_VAD_state? /* I/O Pointer to Silk VAD state */
    ): Int {
        var b: Int
        val ret = 0

        /* reset state memory */
        // TODO: memset
        // SKP_memset( psSilk_VAD, 0, sizeof( SKP_Silk_VAD_state ) );

        /* init noise levels */
        /* Initialize array with approx pink noise levels (psd proportional to inverse of frequency) */
        b = 0
        while (b < Define.VAD_N_BANDS) {
            psSilk_VAD!!.NoiseLevelBias[b] = Math.max(Define.VAD_NOISE_LEVELS_BIAS / (b + 1), 1)
            b++
        }

        /* Initialize state */
        b = 0
        while (b < Define.VAD_N_BANDS) {
            psSilk_VAD!!.NL[b] = 100 * psSilk_VAD.NoiseLevelBias[b]
            psSilk_VAD.inv_NL[b] = Typedef.SKP_int32_MAX / psSilk_VAD.NL[b]
            b++
        }
        psSilk_VAD!!.counter = 15

        /* init smoothed energy-to-noise ratio */
        b = 0
        while (b < Define.VAD_N_BANDS) {
            psSilk_VAD.NrgRatioSmth_Q8[b] = 100 * 256 /* 100 * 256 --> 20 dB SNR */
            b++
        }
        return ret
    }

    /* Weighting factors for tilt measure */
    var tiltWeights = intArrayOf(30000, 6000, -12000, -12000)

    /**
     * Get the speech activity level in Q8.
     *
     * @param psSilk_VAD
     * Silk VAD state.
     * @param pSA_Q8
     * Speech activity level in Q8.
     * @param pSNR_dB_Q7
     * SNR for current frame in Q7.
     * @param pQuality_Q15
     * Smoothed SNR for each band.
     * @param pTilt_Q15
     * Smoothed SNR for each band.
     * @param pIn
     * PCM input[framelength].
     * @param pIn_offset
     * offset of valid data.
     * @param framelength
     * Input frame length.
     * @return Return value, 0 if success.
     */
    fun SKP_Silk_VAD_GetSA_Q8( /* O Return value, 0 if success */
            psSilk_VAD: SKP_Silk_VAD_state?,  /* I/O Silk VAD state */
            pSA_Q8: IntArray,  /* O Speech activity level in Q8 */
            pSNR_dB_Q7: IntArray,  /* O SNR for current frame in Q7 */
            pQuality_Q15: IntArray,  /* O Smoothed SNR for each band */
            pTilt_Q15: IntArray,  /* O current frame's frequency tilt */
            pIn: ShortArray?,  /* I PCM input [framelength] */
            pIn_offset: Int, framelength: Int /* I Input frame length */
    ): Int {
        var SA_Q15: Int
        var input_tilt: Int
        val scratch = IntArray(3 * Define.MAX_FRAME_LENGTH / 2)
        var decimated_framelength: Int
        var dec_subframe_length: Int
        var dec_subframe_offset: Int
        var SNR_Q7: Int
        var i: Int
        var b: Int
        var s: Int
        var sumSquared = 0
        val smooth_coef_Q16: Int
        val HPstateTmp: Short
        val X = Array(Define.VAD_N_BANDS) { ShortArray(Define.MAX_FRAME_LENGTH / 2) }
        val Xnrg = IntArray(Define.VAD_N_BANDS)
        val NrgToNoiseRatio_Q8 = IntArray(Define.VAD_N_BANDS)
        var speech_nrg: Int
        var x_tmp: Int
        val ret = 0
        assert(Define.VAD_N_BANDS == 4)
        assert(Define.MAX_FRAME_LENGTH >= framelength)
        assert(framelength <= 512)
        /** */
        /* Filter and Decimate */
        /** */
        /* 0-8 kHz to 0-4 kHz and 4-8 kHz */
        AnaFiltBank1.SKP_Silk_ana_filt_bank_1(pIn, pIn_offset, psSilk_VAD!!.AnaState, 0, X[0], 0,
                X[3], 0, scratch, framelength)

        /* 0-4 kHz to 0-2 kHz and 2-4 kHz */
        AnaFiltBank1.SKP_Silk_ana_filt_bank_1(X[0], 0, psSilk_VAD.AnaState1, 0, X[0], 0, X[2], 0,
                scratch, framelength shr 1)

        /* 0-2 kHz to 0-1 kHz and 1-2 kHz */
        AnaFiltBank1.SKP_Silk_ana_filt_bank_1(X[0], 0, psSilk_VAD.AnaState2, 0, X[0], 0, X[1], 0,
                scratch, framelength shr 2)
        /** */
        /* HP filter on lowest band (differentiator) */
        /** */
        decimated_framelength = framelength shr 3
        X[0][decimated_framelength - 1] = (X[0][decimated_framelength - 1].toInt() shr 1).toShort()
        HPstateTmp = X[0][decimated_framelength - 1]
        i = decimated_framelength - 1
        while (i > 0) {
            X[0][i - 1] = (X[0][i - 1].toInt() shr 1).toShort()
            X[0][i] = (X[0][i] - X[0][i - 1]).toShort()
            i--
        }
        X[0][0] = (X[0][0] - psSilk_VAD.HPstate).toShort()
        psSilk_VAD.HPstate = HPstateTmp
        /** */
        /* Calculate the energy in each band */
        /** */
        b = 0
        while (b < Define.VAD_N_BANDS) {

            /* Find the decimated framelength in the non-uniformly divided bands */
            decimated_framelength = framelength shr Math.min(Define.VAD_N_BANDS - b, Define.VAD_N_BANDS - 1)

            /* Split length into subframe lengths */
            dec_subframe_length = decimated_framelength shr Define.VAD_INTERNAL_SUBFRAMES_LOG2
            dec_subframe_offset = 0

            /* Compute energy per sub-frame */
            /* initialize with summed energy of last subframe */
            Xnrg[b] = psSilk_VAD.XnrgSubfr[b]
            s = 0
            while (s < Define.VAD_INTERNAL_SUBFRAMES) {
                sumSquared = 0
                i = 0
                while (i < dec_subframe_length) {

                    /* The energy will be less than dec_subframe_length * ( SKP_int16_MIN / 8 )^2. */
                    /*
					 * Therefore we can accumulate with no risk of overflow (unless
					 * dec_subframe_length > 128)
					 */
                    x_tmp = X[b][i + dec_subframe_offset].toInt() shr 3
                    sumSquared = Macros.SKP_SMLABB(sumSquared, x_tmp, x_tmp)
                    assert(sumSquared >= 0)
                    i++
                }

                /* add/saturate summed energy of current subframe */
                if (s < Define.VAD_INTERNAL_SUBFRAMES - 1) {
                    Xnrg[b] = SigProcFIX.SKP_ADD_POS_SAT32(Xnrg[b], sumSquared)
                } else {
                    /* look-ahead subframe */
                    Xnrg[b] = SigProcFIX.SKP_ADD_POS_SAT32(Xnrg[b], sumSquared shr 1)
                }
                dec_subframe_offset += dec_subframe_length
                s++
            }
            psSilk_VAD.XnrgSubfr[b] = sumSquared
            b++
        }
        /** */
        /* Noise estimation */
        /** */
        SKP_Silk_VAD_GetNoiseLevels(Xnrg, psSilk_VAD)

        /* Signal-plus-noise to noise ratio estimation */
        sumSquared = 0
        input_tilt = 0
        b = 0
        while (b < Define.VAD_N_BANDS) {
            speech_nrg = Xnrg[b] - psSilk_VAD.NL[b]
            if (speech_nrg > 0) {
                /* Divide, with sufficient resolution */
                if (Xnrg[b] and -0x800000 == 0) {
                    NrgToNoiseRatio_Q8[b] = (Xnrg[b] shl 8) / (psSilk_VAD.NL[b] + 1)
                } else {
                    NrgToNoiseRatio_Q8[b] = Xnrg[b] / ((psSilk_VAD.NL[b] shr 8) + 1)
                }

                /* Convert to log domain */
                SNR_Q7 = Lin2log.SKP_Silk_lin2log(NrgToNoiseRatio_Q8[b]) - 8 * 128

                /* Sum-of-squares */
                sumSquared = Macros.SKP_SMLABB(sumSquared, SNR_Q7, SNR_Q7) /* Q14 */

                /* Tilt measure */
                if (speech_nrg < 1 shl 20) {
                    /* Scale down SNR value for small subband speech energies */
                    SNR_Q7 = Macros.SKP_SMULWB(Inlines.SKP_Silk_SQRT_APPROX(speech_nrg) shl 6, SNR_Q7)
                }
                input_tilt = Macros.SKP_SMLAWB(input_tilt, tiltWeights[b], SNR_Q7)
            } else {
                NrgToNoiseRatio_Q8[b] = 256
            }
            b++
        }

        /* Mean-of-squares */
        sumSquared /= Define.VAD_N_BANDS /* Q14 */

        /* Root-mean-square approximation, scale to dBs, and write to output pointer */
        pSNR_dB_Q7[0] = (3 * Inlines.SKP_Silk_SQRT_APPROX(sumSquared)).toShort().toInt() /* Q7 */
        /** */
        /* Speech Probability Estimation */
        /** */
        SA_Q15 = SigmQ15.SKP_Silk_sigm_Q15(Macros.SKP_SMULWB(Define.VAD_SNR_FACTOR_Q16, pSNR_dB_Q7[0])
                - Define.VAD_NEGATIVE_OFFSET_Q5)
        /** */
        /* Frequency Tilt Measure */
        /** */
        pTilt_Q15[0] = SigmQ15.SKP_Silk_sigm_Q15(input_tilt) - 16384 shl 1
        /** */
        /* Scale the sigmoid output based on power levels */
        /** */
        speech_nrg = 0
        b = 0
        while (b < Define.VAD_N_BANDS) {

            /* Accumulate signal-without-noise energies, higher frequency bands have more weight */
            speech_nrg += (b + 1) * (Xnrg[b] - psSilk_VAD.NL[b] shr 4)
            b++
        }

        /* Power scaling */
        if (speech_nrg <= 0) {
            SA_Q15 = SA_Q15 shr 1
        } else if (speech_nrg < 32768) {
            /* square-root */
            speech_nrg = Inlines.SKP_Silk_SQRT_APPROX(speech_nrg shl 15)
            SA_Q15 = Macros.SKP_SMULWB(32768 + speech_nrg, SA_Q15)
        }

        /* Copy the resulting speech activity in Q8 to *pSA_Q8 */
        pSA_Q8[0] = Math.min(SA_Q15 shr 7, Typedef.SKP_uint8_MAX.toInt())
        /** */
        /* Energy Level and SNR estimation */
        /** */
        /* smoothing coefficient */
        smooth_coef_Q16 = Macros.SKP_SMULWB(Define.VAD_SNR_SMOOTH_COEF_Q18, Macros.SKP_SMULWB(SA_Q15, SA_Q15))
        b = 0
        while (b < Define.VAD_N_BANDS) {

            /* compute smoothed energy-to-noise ratio per band */
            psSilk_VAD.NrgRatioSmth_Q8[b] = Macros.SKP_SMLAWB(psSilk_VAD.NrgRatioSmth_Q8[b],
                    NrgToNoiseRatio_Q8[b] - psSilk_VAD.NrgRatioSmth_Q8[b], smooth_coef_Q16)

            /* signal to noise ratio in dB per band */
            SNR_Q7 = 3 * (Lin2log.SKP_Silk_lin2log(psSilk_VAD.NrgRatioSmth_Q8[b]) - 8 * 128)
            /* quality = sigmoid( 0.25 * ( SNR_dB - 16 ) ); */
            pQuality_Q15[b] = SigmQ15.SKP_Silk_sigm_Q15(SNR_Q7 - 16 * 128 shr 4)
            b++
        }
        return ret
    }

    /**
     * Noise level estimation.
     *
     * @param pX
     * subband energies.
     * @param psSilk_VAD
     * Silk VAD state.
     */
    fun SKP_Silk_VAD_GetNoiseLevels(pX: IntArray,  /* I subband energies */
            psSilk_VAD: SKP_Silk_VAD_state? /* I/O Pointer to Silk VAD state */
    ) {
        var k: Int
        var nl: Int
        var nrg: Int
        var inv_nrg: Int
        var coef: Int
        val min_coef: Int

        /* Initially faster smoothing */
        min_coef = if (psSilk_VAD!!.counter < 1000) { /* 1000 = 20 sec */
            Typedef.SKP_int16_MAX / ((psSilk_VAD.counter shr 4) + 1)
        } else {
            0
        }
        k = 0
        while (k < Define.VAD_N_BANDS) {

            /* Get old noise level estimate for current band */
            nl = psSilk_VAD.NL[k]
            assert(nl >= 0)

            /* Add bias */
            nrg = SigProcFIX.SKP_ADD_POS_SAT32(pX[k], psSilk_VAD.NoiseLevelBias[k])
            assert(nrg > 0)

            /* Invert energies */
            inv_nrg = Typedef.SKP_int32_MAX / nrg
            assert(inv_nrg >= 0)

            /* Less update when subband energy is high */
            coef = if (nrg > nl shl 3) {
                Define.VAD_NOISE_LEVEL_SMOOTH_COEF_Q16 shr 3
            } else if (nrg < nl) {
                Define.VAD_NOISE_LEVEL_SMOOTH_COEF_Q16
            } else {
                Macros.SKP_SMULWB(Macros.SKP_SMULWW(inv_nrg, nl), Define.VAD_NOISE_LEVEL_SMOOTH_COEF_Q16 shl 1)
            }

            /* Initially faster smoothing */
            coef = Math.max(coef, min_coef)

            /* Smooth inverse energies */
            psSilk_VAD.inv_NL[k] = Macros.SKP_SMLAWB(psSilk_VAD.inv_NL[k], inv_nrg - psSilk_VAD.inv_NL[k],
                    coef)
            assert(psSilk_VAD.inv_NL[k] >= 0)

            /* Compute noise level by inverting again */
            nl = Typedef.SKP_int32_MAX / psSilk_VAD.inv_NL[k]
            assert(nl >= 0)

            /* Limit noise levels (guarantee 7 bits of head room) */
            nl = Math.min(nl, 0x00FFFFFF)

            /* Store as part of state */
            psSilk_VAD.NL[k] = nl
            k++
        }

        /* Increment frame counter */
        psSilk_VAD.counter++
    }
}