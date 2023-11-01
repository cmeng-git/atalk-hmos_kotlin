/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Encode parameters to create the payload.
 *
 * @author Dingxin Xu
 */
object EncodeParameters {
    /**
     * Encode parameters to create the payload.
     *
     * @param psEncC
     * Encoder state.
     * @param psEncCtrlC
     * Encoder control.
     * @param psRC
     * Range encoder state.
     * @param q
     * Quantization indices.
     */
    fun SKP_Silk_encode_parameters(psEncC: SKP_Silk_encoder_state?,  /* I/O Encoder state */
            psEncCtrlC: SKP_Silk_encoder_control?,  /* I/O Encoder control */
            psRC: SKP_Silk_range_coder_state?,  /* I/O Range encoder state */
            q: ByteArray? /* I Quantization indices */
    ) {
        var i: Int
        var k: Int
        val typeOffset: Int
        val psNLSF_CB: SKP_Silk_NLSF_CB_struct?
        /** */
        /* Encode sampling rate */
        /** */
        /* only done for first frame in packet */
        if (psEncC!!.nFramesInPayloadBuf == 0) {
            /* get sampling rate index */
            i = 0
            while (i < 3) {
                if (TablesOther.SKP_Silk_SamplingRates_table[i] == psEncC.fs_kHz) {
                    break
                }
                i++
            }
            RangeCoder.SKP_Silk_range_encoder(psRC, i, TablesOther.SKP_Silk_SamplingRates_CDF, 0)
        }
        /** */
        /* Encode signal type and quantizer offset */
        /** */
        typeOffset = 2 * psEncCtrlC!!.sigtype + psEncCtrlC.QuantOffsetType
        if (psEncC.nFramesInPayloadBuf == 0) {
            /* first frame in packet: independent coding */
            RangeCoder.SKP_Silk_range_encoder(psRC, typeOffset,
                    TablesTypeOffset.SKP_Silk_type_offset_CDF, 0)
        } else {
            /* condidtional coding */
            RangeCoder.SKP_Silk_range_encoder(psRC, typeOffset,
                    TablesTypeOffset.SKP_Silk_type_offset_joint_CDF[psEncC.typeOffsetPrev], 0)
        }
        psEncC.typeOffsetPrev = typeOffset
        /** */
        /* Encode gains */
        /** */
        /* first subframe */
        if (psEncC.nFramesInPayloadBuf == 0) {
            /* first frame in packet: independent coding */
            RangeCoder.SKP_Silk_range_encoder(psRC, psEncCtrlC.GainsIndices[0],
                    TablesGain.SKP_Silk_gain_CDF[psEncCtrlC.sigtype], 0)
        } else {
            /* condidtional coding */
            RangeCoder.SKP_Silk_range_encoder(psRC, psEncCtrlC.GainsIndices[0],
                    TablesGain.SKP_Silk_delta_gain_CDF, 0)
        }

        /* remaining subframes */
        i = 1
        while (i < Define.NB_SUBFR) {
            RangeCoder.SKP_Silk_range_encoder(psRC, psEncCtrlC.GainsIndices[i],
                    TablesGain.SKP_Silk_delta_gain_CDF, 0)
            i++
        }
        /** */
        /* Encode NLSFs */
        /** */
        /* Range encoding of the NLSF path */
        psNLSF_CB = psEncC.psNLSF_CB[psEncCtrlC.sigtype]
        RangeCoder.SKP_Silk_range_encoder_multi(psRC, psEncCtrlC.NLSFIndices, psNLSF_CB!!.StartPtr,
                psNLSF_CB.nStages)
        assert(psEncC.useInterpolatedNLSFs == 1 || psEncCtrlC.NLSFInterpCoef_Q2 == 1 shl 2)
        RangeCoder.SKP_Silk_range_encoder(psRC, psEncCtrlC.NLSFInterpCoef_Q2,
                TablesOther.SKP_Silk_NLSF_interpolation_factor_CDF, 0)
        if (psEncCtrlC.sigtype == Define.SIG_TYPE_VOICED) {
            /** */
            /* Encode pitch lags */
            /** */

            /* lag index */
            if (psEncC.fs_kHz == 8) {
                RangeCoder.SKP_Silk_range_encoder(psRC, psEncCtrlC.lagIndex,
                        TablesPitchLag.SKP_Silk_pitch_lag_NB_CDF, 0)
            } else if (psEncC.fs_kHz == 12) {
                RangeCoder.SKP_Silk_range_encoder(psRC, psEncCtrlC.lagIndex,
                        TablesPitchLag.SKP_Silk_pitch_lag_MB_CDF, 0)
            } else if (psEncC.fs_kHz == 16) {
                RangeCoder.SKP_Silk_range_encoder(psRC, psEncCtrlC.lagIndex,
                        TablesPitchLag.SKP_Silk_pitch_lag_WB_CDF, 0)
            } else {
                RangeCoder.SKP_Silk_range_encoder(psRC, psEncCtrlC.lagIndex,
                        TablesPitchLag.SKP_Silk_pitch_lag_SWB_CDF, 0)
            }

            /* countour index */
            if (psEncC.fs_kHz == 8) {
                /* Less codevectors used in 8 khz mode */
                RangeCoder.SKP_Silk_range_encoder(psRC, psEncCtrlC.contourIndex,
                        TablesPitchLag.SKP_Silk_pitch_contour_NB_CDF, 0)
            } else {
                /* Joint for 12, 16, 24 khz */
                RangeCoder.SKP_Silk_range_encoder(psRC, psEncCtrlC.contourIndex,
                        TablesPitchLag.SKP_Silk_pitch_contour_CDF, 0)
            }
            /** */
            /* Encode LTP gains */
            /** */

            /* PERIndex value */
            RangeCoder.SKP_Silk_range_encoder(psRC, psEncCtrlC.PERIndex,
                    TablesLTP.SKP_Silk_LTP_per_index_CDF, 0)

            /* Codebook Indices */
            k = 0
            while (k < Define.NB_SUBFR) {
                RangeCoder.SKP_Silk_range_encoder(psRC, psEncCtrlC.LTPIndex[k],
                        TablesLTP.SKP_Silk_LTP_gain_CDF_ptrs[psEncCtrlC.PERIndex], 0)
                k++
            }
            /** */
            /* Encode LTP scaling */
            /** */
            RangeCoder.SKP_Silk_range_encoder(psRC, psEncCtrlC.LTP_scaleIndex,
                    TablesOther.SKP_Silk_LTPscale_CDF, 0)
        }
        /** */
        /* Encode seed */
        /** */
        RangeCoder.SKP_Silk_range_encoder(psRC, psEncCtrlC.Seed, TablesOther.SKP_Silk_Seed_CDF, 0)
        /** */
        /* Encode quantization indices of excitation */
        /** */
        EncodePulses.SKP_Silk_encode_pulses(psRC, psEncCtrlC.sigtype, psEncCtrlC.QuantOffsetType,
                q, psEncC.frame_length)
        /** */
        /* Encode VAD flag */
        /** */
        RangeCoder.SKP_Silk_range_encoder(psRC, psEncC.vadFlag, TablesOther.SKP_Silk_vadflag_CDF, 0)
    }
}