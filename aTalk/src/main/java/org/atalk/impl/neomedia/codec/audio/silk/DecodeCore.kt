/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

import java.util.*

/**
 * Core decoder. Performs inverse NSQ operation LTP + LPC
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object DecodeCore {
    /**
     * Core decoder. Performs inverse NSQ operation LTP + LPC.
     *
     * @param psDec
     * Decoder state.
     * @param psDecCtrl
     * Decoder control.
     * @param xq
     * Decoded speech.
     * @param xq_offset
     * offset of the valid data.
     * @param q
     * Pulse signal.
     */
    fun SKP_Silk_decode_core(psDec: SKP_Silk_decoder_state?,  /* I/O Decoder state */
            psDecCtrl: SKP_Silk_decoder_control,  /* I Decoder control */
            xq: ShortArray?,  /* O Decoded speech */
            xq_offset: Int, q: IntArray /* I Pulse signal */
    ) {
        var i: Int
        var k: Int
        var lag = 0
        var start_idx: Int
        var sLTP_buf_idx: Int
        val NLSF_interpolation_flag: Int
        var sigtype: Int
        var A_Q12: ShortArray?
        var B_Q14: ShortArray?
        var B_Q14_offset: Int
        val pxq: ShortArray?
        var pxq_offset: Int
        val A_Q12_tmp = ShortArray(Define.MAX_LPC_ORDER)
        val sLTP = ShortArray(Define.MAX_FRAME_LENGTH)
        var Gain_Q16: Int
        var pred_lag_ptr: IntArray?
        var pred_lag_ptr_offset: Int
        val pexc_Q10: IntArray?
        var pexc_Q10_offset: Int
        val pres_Q10: IntArray?
        var pres_Q10_offset: Int
        var LTP_pred_Q14: Int
        var LPC_pred_Q10: Int
        var rand_seed: Int
        val offset_Q10: Int
        var dither: Int
        val vec_Q10 = IntArray(Define.MAX_FRAME_LENGTH / Define.NB_SUBFR)
        var inv_gain_Q16: Int
        var inv_gain_Q32: Int
        var gain_adj_Q16: Int
        val FiltState = IntArray(Define.MAX_LPC_ORDER)
        var j: Int
        Typedef.SKP_assert(psDec!!.prev_inv_gain_Q16 != 0)
        offset_Q10 = TablesOther.SKP_Silk_Quantization_Offsets_Q10[psDecCtrl.sigtype]!![psDecCtrl.QuantOffsetType].toInt()
        NLSF_interpolation_flag = if (psDecCtrl.NLSFInterpCoef_Q2 < 1 shl 2) {
            1
        } else {
            0
        }

        /* Decode excitation */
        rand_seed = psDecCtrl.Seed
        i = 0
        while (i < psDec.frame_length) {
            rand_seed = SigProcFIX.SKP_RAND(rand_seed)
            /* dither = rand_seed < 0 ? 0xFFFFFFFF : 0; */
            dither = rand_seed shr 31
            psDec.exc_Q10[i] = (q[i] shl 10) + offset_Q10
            psDec.exc_Q10[i] = (psDec.exc_Q10[i] xor dither) - dither
            rand_seed += q[i]
            i++
        }
        pexc_Q10 = psDec.exc_Q10
        pexc_Q10_offset = 0
        pres_Q10 = psDec.res_Q10
        pres_Q10_offset = 0
        pxq = psDec.outBuf
        pxq_offset = psDec.frame_length
        sLTP_buf_idx = psDec.frame_length
        /* Loop over subframes */
        k = 0
        while (k < Define.NB_SUBFR) {
            A_Q12 = psDecCtrl.PredCoef_Q12[k shr 1]

            /* Preload LPC coeficients to array on stack. Gives small performance gain */
            System.arraycopy(A_Q12, 0, A_Q12_tmp, 0, psDec.LPC_order)
            B_Q14 = psDecCtrl.LTPCoef_Q14
            B_Q14_offset = k * Define.LTP_ORDER
            Gain_Q16 = psDecCtrl.Gains_Q16[k]
            sigtype = psDecCtrl.sigtype
            inv_gain_Q16 = Typedef.SKP_int32_MAX / (Gain_Q16 shr 1)
            inv_gain_Q16 = Math.min(inv_gain_Q16, Typedef.SKP_int16_MAX.toInt())

            /* Calculate Gain adjustment factor */
            gain_adj_Q16 = 1 shl 16
            if (inv_gain_Q16 != psDec.prev_inv_gain_Q16) {
                gain_adj_Q16 = Inlines.SKP_DIV32_varQ(inv_gain_Q16, psDec.prev_inv_gain_Q16, 16)
            }

            /* Avoid abrupt transition from voiced PLC to unvoiced normal decoding */
            if (psDec.lossCnt != 0 && psDec.prev_sigtype == Define.SIG_TYPE_VOICED && psDecCtrl.sigtype == Define.SIG_TYPE_UNVOICED && k < Define.NB_SUBFR shr 1) {
                Arrays.fill(B_Q14, B_Q14_offset, B_Q14_offset + Define.LTP_ORDER, 0.toShort())
                B_Q14[B_Q14_offset + Define.LTP_ORDER / 2] = (1.toShort().toInt() shl 12).toShort() /* 0.25 */
                sigtype = Define.SIG_TYPE_VOICED
                psDecCtrl.pitchL[k] = psDec.lagPrev
            }
            if (sigtype == Define.SIG_TYPE_VOICED) {
                /* Voiced */
                lag = psDecCtrl.pitchL[k]
                /* Re-whitening */
                if (k and 3 - (NLSF_interpolation_flag shl 1) == 0) {
                    /* Rewhiten with new A coefs */
                    start_idx = psDec.frame_length - lag - psDec.LPC_order - Define.LTP_ORDER / 2
                    Typedef.SKP_assert(start_idx >= 0)
                    Typedef.SKP_assert(start_idx <= psDec.frame_length - psDec.LPC_order)
                    Arrays.fill(FiltState, 0, psDec.LPC_order, 0)
                    /*
                     * Not really necessary, but
                     * Valgrind and Coverity will
                     * complain otherwise
                     */
                    MA.SKP_Silk_MA_Prediction(psDec.outBuf, start_idx + k
                            * (psDec.frame_length shr 2), A_Q12, 0, FiltState, sLTP, start_idx,
                            psDec.frame_length - start_idx, psDec.LPC_order)

                    /* After rewhitening the LTP state is unscaled */
                    inv_gain_Q32 = inv_gain_Q16 shl 16
                    if (k == 0) {
                        /* Do LTP downscaling */
                        inv_gain_Q32 = Macros.SKP_SMULWB(inv_gain_Q32, psDecCtrl.LTP_scale_Q14) shl 2
                    }
                    i = 0
                    while (i < lag + Define.LTP_ORDER / 2) {
                        psDec.sLTP_Q16[sLTP_buf_idx - i - 1] = Macros.SKP_SMULWB(inv_gain_Q32,
                                sLTP[psDec.frame_length - i - 1].toInt())
                        i++
                    }
                } else {
                    /* Update LTP state when Gain changes */
                    if (gain_adj_Q16 != 1 shl 16) {
                        i = 0
                        while (i < lag + Define.LTP_ORDER / 2) {
                            psDec.sLTP_Q16[sLTP_buf_idx - i - 1] = Macros.SKP_SMULWW(gain_adj_Q16,
                                    psDec.sLTP_Q16[sLTP_buf_idx - i - 1])
                            i++
                        }
                    }
                }
            }

            /* Scale short term state */
            i = 0
            while (i < Define.MAX_LPC_ORDER) {
                psDec.sLPC_Q14[i] = Macros.SKP_SMULWW(gain_adj_Q16, psDec.sLPC_Q14[i])
                i++
            }

            /* Save inv_gain */
            Typedef.SKP_assert(inv_gain_Q16 != 0)
            psDec.prev_inv_gain_Q16 = inv_gain_Q16

            /* Long-term prediction */
            if (sigtype == Define.SIG_TYPE_VOICED) {
                /* Setup pointer */
                pred_lag_ptr = psDec.sLTP_Q16
                pred_lag_ptr_offset = sLTP_buf_idx - lag + Define.LTP_ORDER / 2
                i = 0
                while (i < psDec.subfr_length) {

                    /* Unrolled loop */
                    LTP_pred_Q14 = Macros.SKP_SMULWB(pred_lag_ptr[pred_lag_ptr_offset + 0],
                            B_Q14[B_Q14_offset + 0].toInt())
                    LTP_pred_Q14 = Macros.SKP_SMLAWB(LTP_pred_Q14, pred_lag_ptr[pred_lag_ptr_offset - 1],
                            B_Q14[B_Q14_offset + 1].toInt())
                    LTP_pred_Q14 = Macros.SKP_SMLAWB(LTP_pred_Q14, pred_lag_ptr[pred_lag_ptr_offset - 2],
                            B_Q14[B_Q14_offset + 2].toInt())
                    LTP_pred_Q14 = Macros.SKP_SMLAWB(LTP_pred_Q14, pred_lag_ptr[pred_lag_ptr_offset - 3],
                            B_Q14[B_Q14_offset + 3].toInt())
                    LTP_pred_Q14 = Macros.SKP_SMLAWB(LTP_pred_Q14, pred_lag_ptr[pred_lag_ptr_offset - 4],
                            B_Q14[B_Q14_offset + 4].toInt())
                    pred_lag_ptr_offset++

                    /* Generate LPC residual */
                    pres_Q10[pres_Q10_offset + i] = pexc_Q10[pexc_Q10_offset + i] + SigProcFIX.SKP_RSHIFT_ROUND(LTP_pred_Q14, 4)

                    /* Update states */
                    psDec.sLTP_Q16[sLTP_buf_idx] = pres_Q10[pres_Q10_offset + i] shl 6
                    sLTP_buf_idx++
                    i++
                }
            } else {
                System.arraycopy(pexc_Q10, pexc_Q10_offset, pres_Q10, pres_Q10_offset,
                        psDec.subfr_length)
            }
            SKP_Silk_decode_short_term_prediction(vec_Q10, pres_Q10, pres_Q10_offset,
                    psDec.sLPC_Q14, A_Q12_tmp, psDec.LPC_order, psDec.subfr_length)

            /* Scale with Gain */
            i = 0
            while (i < psDec.subfr_length) {
                pxq[pxq_offset + i] = SigProcFIX.SKP_SAT16(SigProcFIX.SKP_RSHIFT_ROUND(
                        Macros.SKP_SMULWW(vec_Q10[i], Gain_Q16), 10)).toShort()
                i++
            }

            /* Update LPC filter state */
            System.arraycopy(psDec.sLPC_Q14, psDec.subfr_length, psDec.sLPC_Q14, 0, Define.MAX_LPC_ORDER)
            pexc_Q10_offset += psDec.subfr_length
            pres_Q10_offset += psDec.subfr_length
            pxq_offset += psDec.subfr_length
            k++
        }

        /* Copy to output */
        System.arraycopy(psDec.outBuf, psDec.frame_length, xq, xq_offset, psDec.frame_length)
    }

    private fun SKP_Silk_decode_short_term_prediction(vec_Q10: IntArray, pres_Q10: IntArray?,
            pres_Q10_offset: Int, sLPC_Q14: IntArray?, A_Q12_tmp: ShortArray, LPC_order: Int, subfr_length: Int) {
        var i: Int
        var LPC_pred_Q10: Int
        var j: Int
        i = 0
        while (i < subfr_length) {

            /* Partially unrolled */
            LPC_pred_Q10 = Macros.SKP_SMULWB(sLPC_Q14!![Define.MAX_LPC_ORDER + i - 1], A_Q12_tmp[0].toInt())
            LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, sLPC_Q14[Define.MAX_LPC_ORDER + i - 2], A_Q12_tmp[1].toInt())
            LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, sLPC_Q14[Define.MAX_LPC_ORDER + i - 3], A_Q12_tmp[2].toInt())
            LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, sLPC_Q14[Define.MAX_LPC_ORDER + i - 4], A_Q12_tmp[3].toInt())
            LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, sLPC_Q14[Define.MAX_LPC_ORDER + i - 5], A_Q12_tmp[4].toInt())
            LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, sLPC_Q14[Define.MAX_LPC_ORDER + i - 6], A_Q12_tmp[5].toInt())
            LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, sLPC_Q14[Define.MAX_LPC_ORDER + i - 7], A_Q12_tmp[6].toInt())
            LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, sLPC_Q14[Define.MAX_LPC_ORDER + i - 8], A_Q12_tmp[7].toInt())
            LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, sLPC_Q14[Define.MAX_LPC_ORDER + i - 9], A_Q12_tmp[8].toInt())
            LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, sLPC_Q14[Define.MAX_LPC_ORDER + i - 10], A_Q12_tmp[9].toInt())
            j = 10
            while (j < LPC_order) {
                LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, sLPC_Q14[Define.MAX_LPC_ORDER + i - j - 1],
                        A_Q12_tmp[j].toInt())
                j++
            }

            /* Add prediction to LPC residual */
            vec_Q10[i] = pres_Q10!![pres_Q10_offset + i] + LPC_pred_Q10

            /* Update states */
            sLPC_Q14[Define.MAX_LPC_ORDER + i] = vec_Q10[i] shl 4
            i++
        }
    }
}