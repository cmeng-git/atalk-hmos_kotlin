/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

import java.util.*

/**
 * Decode parameters from payload
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object DecodeParameters {
    /**
     * Decode parameters from payload.
     *
     * @param psDec
     * decoder state.
     * @param psDecCtrl
     * Decoder control.
     * @param q
     * Excitation signal
     * @param fullDecoding
     * Flag to tell if only arithmetic decoding
     */
    fun SKP_Silk_decode_parameters(
            psDec: SKP_Silk_decoder_state?,  /* I/O State */
            psDecCtrl: SKP_Silk_decoder_control,  /* I/O Decoder control */
            q: IntArray,  /* O Excitation signal */
            fullDecoding: Int, /* I Flag to tell if only arithmetic decoding */
    ) {
        var i: Int
        var k: Int
        var Ix: Int
        val fs_kHz_dec: Int
        val nBytesUsed: Int
        val Ix_ptr = IntArray(1)
        val Ixs = IntArray(Define.NB_SUBFR)
        val GainsIndices = IntArray(Define.NB_SUBFR)
        val NLSFIndices = IntArray(Define.NLSF_MSVQ_MAX_CB_STAGES)
        val pNLSF_Q15 = IntArray(Define.MAX_LPC_ORDER)
        val pNLSF0_Q15 = IntArray(Define.MAX_LPC_ORDER)
        val cbk_ptr_Q14: ShortArray?
        val psRC = psDec!!.sRC
        /** */
        /* Decode sampling rate */
        /** */
        /* only done for first frame of packet */
        if (psDec.nFramesDecoded == 0) {
            RangeCoder.SKP_Silk_range_decoder(Ix_ptr, 0, psRC,
                    TablesOther.SKP_Silk_SamplingRates_CDF, 0,
                    TablesOther.SKP_Silk_SamplingRates_offset)
            Ix = Ix_ptr[0]

            /* check that sampling rate is supported */
            if (Ix < 0 || Ix > 3) {
                psRC.error = Define.RANGE_CODER_ILLEGAL_SAMPLING_RATE
                return
            }
            fs_kHz_dec = TablesOther.SKP_Silk_SamplingRates_table[Ix]
            DecoderSetFs.SKP_Silk_decoder_set_fs(psDec, fs_kHz_dec)
        }
        /** */
        /* Decode signal type and quantizer offset */
        /** */
        Ix = if (psDec.nFramesDecoded == 0) {
            /* first frame in packet: independent coding */
            RangeCoder.SKP_Silk_range_decoder(Ix_ptr, 0, psRC,
                    TablesTypeOffset.SKP_Silk_type_offset_CDF, 0,
                    TablesTypeOffset.SKP_Silk_type_offset_CDF_offset)
            Ix_ptr[0]
        } else {
            /* condidtional coding */
            RangeCoder.SKP_Silk_range_decoder(Ix_ptr, 0, psRC,
                    TablesTypeOffset.SKP_Silk_type_offset_joint_CDF[psDec.typeOffsetPrev], 0,
                    TablesTypeOffset.SKP_Silk_type_offset_CDF_offset)
            Ix_ptr[0]
        }
        psDecCtrl.sigtype = Ix shr 1
        psDecCtrl.QuantOffsetType = Ix and 1
        psDec.typeOffsetPrev = Ix
        /** */
        /* Decode gains */
        /** */
        /* first subframe */
        if (psDec.nFramesDecoded == 0) {
            /* first frame in packet: independent coding */
            RangeCoder.SKP_Silk_range_decoder(GainsIndices, 0, psRC,
                    TablesGain.SKP_Silk_gain_CDF[psDecCtrl.sigtype], 0,
                    TablesGain.SKP_Silk_gain_CDF_offset)
        } else {
            /* condidtional coding */
            RangeCoder.SKP_Silk_range_decoder(GainsIndices, 0, psRC,
                    TablesGain.SKP_Silk_delta_gain_CDF, 0, TablesGain.SKP_Silk_delta_gain_CDF_offset)
        }

        /* remaining subframes */
        i = 1
        while (i < Define.NB_SUBFR) {
            RangeCoder.SKP_Silk_range_decoder(GainsIndices, i, psRC,
                    TablesGain.SKP_Silk_delta_gain_CDF, 0, TablesGain.SKP_Silk_delta_gain_CDF_offset)
            i++
        }

        /* Dequant Gains */
        val LastGainIndex_ptr = IntArray(1)
        LastGainIndex_ptr[0] = psDec.LastGainIndex
        GainQuant.SKP_Silk_gains_dequant(psDecCtrl.Gains_Q16, GainsIndices, LastGainIndex_ptr,
                psDec.nFramesDecoded)
        psDec.LastGainIndex = LastGainIndex_ptr[0]
        /** */
        /* Decode NLSFs */
        /** */
        /* Set pointer to NLSF VQ CB for the current signal type */
        val psNLSF_CB = psDec.psNLSF_CB[psDecCtrl.sigtype]

        /* Arithmetically decode NLSF path */
        RangeCoder.SKP_Silk_range_decoder_multi(NLSFIndices, psRC, psNLSF_CB!!.StartPtr,
                psNLSF_CB.MiddleIx, psNLSF_CB.nStages)

        /* From the NLSF path, decode an NLSF vector */
        NLSFMSVQDecode.SKP_Silk_NLSF_MSVQ_decode(pNLSF_Q15, psNLSF_CB, NLSFIndices, psDec.LPC_order)

        /** */
        /* Decode NLSF interpolation factor */
        /** */
        val NLSFInterpCoef_Q2_ptr = IntArray(1)
        NLSFInterpCoef_Q2_ptr[0] = psDecCtrl.NLSFInterpCoef_Q2
        RangeCoder.SKP_Silk_range_decoder(NLSFInterpCoef_Q2_ptr, 0, psRC,
                TablesOther.SKP_Silk_NLSF_interpolation_factor_CDF, 0,
                TablesOther.SKP_Silk_NLSF_interpolation_factor_offset)
        psDecCtrl.NLSFInterpCoef_Q2 = NLSFInterpCoef_Q2_ptr[0]

        /* If just reset, e.g., because internal Fs changed, do not allow interpolation */
        /* improves the case of packet loss in the first frame after a switch */
        if (psDec.first_frame_after_reset == 1) {
            psDecCtrl.NLSFInterpCoef_Q2 = 4
        }
        if (fullDecoding != 0) {
            /* Convert NLSF parameters to AR prediction filter coefficients */
            NLSF2AStable.SKP_Silk_NLSF2A_stable(psDecCtrl.PredCoef_Q12[1], pNLSF_Q15,
                    psDec.LPC_order)
            if (psDecCtrl.NLSFInterpCoef_Q2 < 4) {
                /* Calculation of the interpolated NLSF0 vector from the interpolation factor, */
                /* the previous NLSF1, and the current NLSF1 */
                i = 0
                while (i < psDec.LPC_order) {
                    pNLSF0_Q15[i] = (psDec.prevNLSF_Q15[i]
                            + (psDecCtrl.NLSFInterpCoef_Q2 * (pNLSF_Q15[i] - psDec.prevNLSF_Q15[i]) shr 2))
                    i++
                }

                /* Convert NLSF parameters to AR prediction filter coefficients */
                NLSF2AStable.SKP_Silk_NLSF2A_stable(psDecCtrl.PredCoef_Q12[0], pNLSF0_Q15,
                        psDec.LPC_order)
            } else {
                /* Copy LPC coefficients for first half from second half */
                System.arraycopy(psDecCtrl.PredCoef_Q12[1]!!, 0, psDecCtrl.PredCoef_Q12[0]!!, 0,
                        psDec.LPC_order)
            }
        }
        System.arraycopy(pNLSF_Q15, 0, psDec.prevNLSF_Q15, 0, psDec.LPC_order)

        /* After a packet loss do BWE of LPC coefs */
        if (psDec.lossCnt != 0) {
            Bwexpander.SKP_Silk_bwexpander(psDecCtrl.PredCoef_Q12[0], psDec.LPC_order,
                    Define.BWE_AFTER_LOSS_Q16)
            Bwexpander.SKP_Silk_bwexpander(psDecCtrl.PredCoef_Q12[1], psDec.LPC_order,
                    Define.BWE_AFTER_LOSS_Q16)
        }
        if (psDecCtrl.sigtype == Define.SIG_TYPE_VOICED) {
            /** */
            /* Decode pitch lags */
            /** */
            /* Get lag index */
            when (psDec.fs_kHz) {
                8 -> {
                    RangeCoder.SKP_Silk_range_decoder(Ixs, 0, psRC,
                            TablesPitchLag.SKP_Silk_pitch_lag_NB_CDF, 0,
                            TablesPitchLag.SKP_Silk_pitch_lag_NB_CDF_offset)
                }
                12 -> {
                    RangeCoder.SKP_Silk_range_decoder(Ixs, 0, psRC,
                            TablesPitchLag.SKP_Silk_pitch_lag_MB_CDF, 0,
                            TablesPitchLag.SKP_Silk_pitch_lag_MB_CDF_offset)
                }
                16 -> {
                    RangeCoder.SKP_Silk_range_decoder(Ixs, 0, psRC,
                            TablesPitchLag.SKP_Silk_pitch_lag_WB_CDF, 0,
                            TablesPitchLag.SKP_Silk_pitch_lag_WB_CDF_offset)
                }
                else -> {
                    RangeCoder.SKP_Silk_range_decoder(Ixs, 0, psRC,
                            TablesPitchLag.SKP_Silk_pitch_lag_SWB_CDF, 0,
                            TablesPitchLag.SKP_Silk_pitch_lag_SWB_CDF_offset)
                }
            }

            /* Get countour index */
            if (psDec.fs_kHz == 8) {
                /* Less codevectors used in 8 khz mode */
                RangeCoder.SKP_Silk_range_decoder(Ixs, 1, psRC,
                        TablesPitchLag.SKP_Silk_pitch_contour_NB_CDF, 0,
                        TablesPitchLag.SKP_Silk_pitch_contour_NB_CDF_offset)
            } else {
                /* Joint for 12, 16, and 24 khz */
                // SKP_Silk_range_decoder( &Ixs[ 1 ], psRC, SKP_Silk_pitch_contour_CDF,
                // SKP_Silk_pitch_contour_CDF_offset );
                RangeCoder.SKP_Silk_range_decoder(Ixs, 1, psRC,
                        TablesPitchLag.SKP_Silk_pitch_contour_CDF, 0,
                        TablesPitchLag.SKP_Silk_pitch_contour_CDF_offset)
            }

            /* Decode pitch values */
            DecodePitch.SKP_Silk_decode_pitch(Ixs[0], Ixs[1], psDecCtrl.pitchL, psDec.fs_kHz)
            /** */
            /* Decode LTP gains */
            /** */
            /* Decode PERIndex value */  val PERIndex_ptr = IntArray(1)
            PERIndex_ptr[0] = psDecCtrl.PERIndex
            RangeCoder.SKP_Silk_range_decoder(PERIndex_ptr, 0, psRC,
                    TablesLTP.SKP_Silk_LTP_per_index_CDF, 0,
                    TablesLTP.SKP_Silk_LTP_per_index_CDF_offset)
            psDecCtrl.PERIndex = PERIndex_ptr[0]

            /* Decode Codebook Index */
            cbk_ptr_Q14 = TablesLTP.SKP_Silk_LTP_vq_ptrs_Q14[psDecCtrl.PERIndex] // set pointer to
            // start of
            // codebook
            k = 0
            while (k < Define.NB_SUBFR) {
                RangeCoder.SKP_Silk_range_decoder(Ix_ptr, 0, psRC,
                        TablesLTP.SKP_Silk_LTP_gain_CDF_ptrs[psDecCtrl.PERIndex], 0,
                        TablesLTP.SKP_Silk_LTP_gain_CDF_offsets[psDecCtrl.PERIndex])
                Ix = Ix_ptr[0]
                i = 0
                while (i < Define.LTP_ORDER) {
                    psDecCtrl.LTPCoef_Q14[Macros.SKP_SMULBB(k, Define.LTP_ORDER) + i] = cbk_ptr_Q14!![Macros.SKP_SMULBB(
                            Ix, Define.LTP_ORDER) + i]
                    i++
                }
                k++
            }
            /** */
            /* Decode LTP scaling */
            /** */
            RangeCoder.SKP_Silk_range_decoder(Ix_ptr, 0, psRC, TablesOther.SKP_Silk_LTPscale_CDF,
                    0, TablesOther.SKP_Silk_LTPscale_offset)
            Ix = Ix_ptr[0]
            psDecCtrl.LTP_scale_Q14 = TablesOther.SKP_Silk_LTPScales_table_Q14[Ix].toInt()
        } else {
            Arrays.fill(psDecCtrl.pitchL, 0, Define.NB_SUBFR, 0)
            Arrays.fill(psDecCtrl.LTPCoef_Q14, 0, Define.NB_SUBFR, 0.toShort())
            psDecCtrl.PERIndex = 0
            psDecCtrl.LTP_scale_Q14 = 0
        }
        /** */
        /* Decode seed */
        /** */
        RangeCoder.SKP_Silk_range_decoder(Ix_ptr, 0, psRC, TablesOther.SKP_Silk_Seed_CDF, 0,
                TablesOther.SKP_Silk_Seed_offset)
        Ix = Ix_ptr[0]
        psDecCtrl.Seed = Ix
        /** */
        /* Decode quantization indices of excitation */
        /** */
        DecodePulses.SKP_Silk_decode_pulses(psRC, psDecCtrl, q, psDec.frame_length)
        /** */
        /* Decode VAD flag */
        /** */
        val vadFlag_ptr = IntArray(1)
        vadFlag_ptr[0] = psDec.vadFlag
        RangeCoder.SKP_Silk_range_decoder(vadFlag_ptr, 0, psRC, TablesOther.SKP_Silk_vadflag_CDF,
                0, TablesOther.SKP_Silk_vadflag_offset)
        psDec.vadFlag = vadFlag_ptr[0]
        /** */
        /* Decode Frame termination indicator */
        /** */
        val FrameTermination_ptr = IntArray(1)
        FrameTermination_ptr[0] = psDec.FrameTermination
        RangeCoder.SKP_Silk_range_decoder(FrameTermination_ptr, 0, psRC,
                TablesOther.SKP_Silk_FrameTermination_CDF, 0,
                TablesOther.SKP_Silk_FrameTermination_offset)
        psDec.FrameTermination = FrameTermination_ptr[0]
        /** */
        /* get number of bytes used so far */
        /** */
        val nBytesUsed_ptr = IntArray(1)
        RangeCoder.SKP_Silk_range_coder_get_length(psRC, nBytesUsed_ptr)
        nBytesUsed = nBytesUsed_ptr[0]
        psDec.nBytesLeft = psRC.bufferLength - nBytesUsed
        if (psDec.nBytesLeft < 0) {
            psRC.error = Define.RANGE_CODER_READ_BEYOND_BUFFER
        }
        /** */
        /* check remaining bits in last byte */
        /** */
        if (psDec.nBytesLeft == 0) {
            RangeCoder.SKP_Silk_range_coder_check_after_decoding(psRC)
        }
    }
}