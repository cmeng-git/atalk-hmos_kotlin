/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

import java.util.*

/**
 *
 * @author Dingxin Xu
 */
object NSQ {
    /**
     *
     * @param psEncC
     * Encoder State
     * @param psEncCtrlC
     * Encoder Control
     * @param NSQ
     * NSQ state
     * @param x
     * prefiltered input signal
     * @param q
     * quantized qulse signal
     * @param LSFInterpFactor_Q2
     * LSF interpolation factor in Q2
     * @param PredCoef_Q12
     * Short term prediction coefficients
     * @param LTPCoef_Q14
     * Long term prediction coefficients
     * @param AR2_Q13
     * @param HarmShapeGain_Q14
     * @param Tilt_Q14
     * Spectral tilt
     * @param LF_shp_Q14
     * @param Gains_Q16
     * @param Lambda_Q10
     * @param LTP_scale_Q14
     * LTP state scaling
     */
    fun SKP_Silk_NSQ(psEncC: SKP_Silk_encoder_state?,  /* I/O Encoder State */
            psEncCtrlC: SKP_Silk_encoder_control?,  /* I Encoder Control */
            NSQ: SKP_Silk_nsq_state?,  /* I/O NSQ state */
            x: ShortArray,  /* I prefiltered input signal */
            q: ByteArray?,  /* O quantized qulse signal */
            LSFInterpFactor_Q2: Int,  /* I LSF interpolation factor in Q2 */
            PredCoef_Q12: ShortArray,  /* I Short term prediction coefficients */
            LTPCoef_Q14: ShortArray,  /* I Long term prediction coefficients */
            AR2_Q13: ShortArray,  /* I */
            HarmShapeGain_Q14: IntArray,  /* I */
            Tilt_Q14: IntArray,  /* I Spectral tilt */
            LF_shp_Q14: IntArray,  /* I */
            Gains_Q16: IntArray,  /* I */
            Lambda_Q10: Int,  /* I */
            LTP_scale_Q14: Int /* I LTP state scaling */
    ) {
        var lag: Int
        var start_idx: Int
        var A_Q12: ShortArray
        var B_Q14: ShortArray
        var AR_shp_Q13: ShortArray
        var A_Q12_offset: Int
        var B_Q14_offset: Int
        var AR_shp_Q13_offset: Int
        val pxq: ShortArray?
        val sLTP_Q16 = IntArray(2 * Define.MAX_FRAME_LENGTH)
        val sLTP = ShortArray(2 * Define.MAX_FRAME_LENGTH)
        var HarmShapeFIRPacked_Q14: Int
        val FiltState = IntArray(Define.MAX_LPC_ORDER)
        val x_sc_Q10 = IntArray(Define.MAX_FRAME_LENGTH / Define.NB_SUBFR)
        val subfr_length = psEncC!!.frame_length / Define.NB_SUBFR
        NSQ!!.rand_seed = psEncCtrlC!!.Seed
        /* Set unvoiced lag to the previous one, overwrite later for voiced */
        lag = NSQ.lagPrev
        assert(NSQ.prev_inv_gain_Q16 != 0)
        val offset_Q10 = TablesOther.SKP_Silk_Quantization_Offsets_Q10[psEncCtrlC.sigtype]!![psEncCtrlC.QuantOffsetType].toInt()
        val LSF_interpolation_flag = if (LSFInterpFactor_Q2 == 1 shl 2) {
            0
        } else {
            1
        }

        /* Setup pointers to start of sub frame */
        NSQ.sLTP_shp_buf_idx = psEncC.frame_length
        NSQ.sLTP_buf_idx = psEncC.frame_length
        pxq = NSQ.xq
        var pxq_offset = psEncC.frame_length
        // TODO: use a local copy of the parameter short x[], which is supposed to be input;
        val x_tmp = x.clone()
        var x_tmp_offset = 0
        // TODO: use a local copy of the parameter byte[] q, which is supposed to be output;
        val q_tmp = q!!.clone()
        var q_tmp_offset = 0
        var k = 0
        while (k < Define.NB_SUBFR) {
            A_Q12 = PredCoef_Q12
            A_Q12_offset = (k shr 1 or 1 - LSF_interpolation_flag) * Define.MAX_LPC_ORDER
            B_Q14 = LTPCoef_Q14
            B_Q14_offset = k * Define.LTP_ORDER
            AR_shp_Q13 = AR2_Q13
            AR_shp_Q13_offset = k * Define.SHAPE_LPC_ORDER_MAX
            assert(HarmShapeGain_Q14[k] >= 0)
            HarmShapeFIRPacked_Q14 = HarmShapeGain_Q14[k] shr 2
            HarmShapeFIRPacked_Q14 = HarmShapeFIRPacked_Q14 or (HarmShapeGain_Q14[k] shr 1 shl 16)
            if (psEncCtrlC.sigtype == Define.SIG_TYPE_VOICED) {
                /* Voiced */
                lag = psEncCtrlC.pitchL[k]
                NSQ.rewhite_flag = 0
                /* Re-whitening */
                if (k and 3 - (LSF_interpolation_flag shl 1) == 0) {
                    /* Rewhiten with new A coefs */
                    start_idx = psEncC.frame_length - lag - psEncC.predictLPCOrder - Define.LTP_ORDER / 2
                    start_idx = SigProcFIX.SKP_LIMIT_int(start_idx, 0, psEncC.frame_length
                            - psEncC.predictLPCOrder) /* Limit */
                    Arrays.fill(FiltState, 0, psEncC.predictLPCOrder, 0)
                    MA.SKP_Silk_MA_Prediction(NSQ.xq, start_idx + k * (psEncC.frame_length shr 2),
                            A_Q12, A_Q12_offset, FiltState, sLTP, start_idx, psEncC.frame_length
                            - start_idx, psEncC.predictLPCOrder)
                    NSQ.rewhite_flag = 1
                    NSQ.sLTP_buf_idx = psEncC.frame_length
                }
            }
            SKP_Silk_nsq_scale_states(NSQ, x_tmp, x_tmp_offset, x_sc_Q10, psEncC.subfr_length,
                    sLTP, sLTP_Q16, k, LTP_scale_Q14, Gains_Q16, psEncCtrlC.pitchL)
            SKP_Silk_noise_shape_quantizer(NSQ, psEncCtrlC.sigtype, x_sc_Q10, q_tmp, q_tmp_offset,
                    pxq, pxq_offset, sLTP_Q16, A_Q12, A_Q12_offset, B_Q14, B_Q14_offset, AR_shp_Q13,
                    AR_shp_Q13_offset, lag, HarmShapeFIRPacked_Q14, Tilt_Q14[k], LF_shp_Q14[k],
                    Gains_Q16[k], Lambda_Q10, offset_Q10, psEncC.subfr_length, psEncC.shapingLPCOrder,
                    psEncC.predictLPCOrder)
            x_tmp_offset += psEncC.subfr_length
            q_tmp_offset += psEncC.subfr_length
            pxq_offset += psEncC.subfr_length
            k++
        }

        /* Save scalars for this layer */
        NSQ.sLF_AR_shp_Q12 = NSQ.sLF_AR_shp_Q12
        NSQ.prev_inv_gain_Q16 = NSQ.prev_inv_gain_Q16
        NSQ.lagPrev = psEncCtrlC.pitchL[Define.NB_SUBFR - 1]
        /* Save quantized speech and noise shaping signals */
        System.arraycopy(NSQ.xq, psEncC.frame_length, NSQ.xq, 0, psEncC.frame_length)
        System.arraycopy(NSQ.sLTP_shp_Q10, psEncC.frame_length, NSQ.sLTP_shp_Q10, 0,
                psEncC.frame_length)

        // TODO: copy back the q_tmp to the output parameter q;
        System.arraycopy(q_tmp, 0, q, 0, q.size)
    }

    /**
     * SKP_Silk_noise_shape_quantizer.
     *
     * @param NSQ
     * NSQ state
     * @param sigtype
     * Signal type
     * @param x_sc_Q10
     * @param q
     * @param q_offset
     * @param xq
     * @param xq_offset
     * @param sLTP_Q16
     * LTP state
     * @param a_Q12
     * Short term prediction coefs
     * @param a_Q12_offset
     * @param b_Q14
     * Long term prediction coefs
     * @param b_Q14_offset
     * @param AR_shp_Q13
     * Noise shaping AR coefs
     * @param AR_shp_Q13_offset
     * @param lag
     * Pitch lag
     * @param HarmShapeFIRPacked_Q14
     * @param Tilt_Q14
     * Spectral tilt
     * @param LF_shp_Q14
     * @param Gain_Q16
     * @param Lambda_Q10
     * @param offset_Q10
     * @param length
     * Input length
     * @param shapingLPCOrder
     * Noise shaping AR filter order
     * @param predictLPCOrder
     * Prediction filter order
     */
    fun SKP_Silk_noise_shape_quantizer(NSQ: SKP_Silk_nsq_state,  /* I/O NSQ state */
            sigtype: Int,  /* I Signal type */
            x_sc_Q10: IntArray,  /* I */
            q: ByteArray,  /* O */
            q_offset: Int, xq: ShortArray,  /* O */
            xq_offset: Int, sLTP_Q16: IntArray,  /* I/O LTP state */
            a_Q12: ShortArray,  /* I Short term prediction coefs */
            a_Q12_offset: Int, b_Q14: ShortArray,  /* I Long term prediction coefs */
            b_Q14_offset: Int, AR_shp_Q13: ShortArray,  /* I Noise shaping AR coefs */
            AR_shp_Q13_offset: Int, lag: Int,  /* I Pitch lag */
            HarmShapeFIRPacked_Q14: Int,  /* I */
            Tilt_Q14: Int,  /* I Spectral tilt */
            LF_shp_Q14: Int,  /* I */
            Gain_Q16: Int,  /* I */
            Lambda_Q10: Int,  /* I */
            offset_Q10: Int,  /* I */
            length: Int,  /* I Input length */
            shapingLPCOrder: Int,  /* I Noise shaping AR filter order */
            predictLPCOrder: Int /* I Prediction filter order */
    ) {
        var j: Int
        var LTP_pred_Q14: Int
        var LPC_pred_Q10: Int
        var n_AR_Q10: Int
        var n_LTP_Q14: Int
        var n_LF_Q10: Int
        var r_Q10: Int
        var q_Q0: Int
        var q_Q10: Int
        var dither: Int
        var exc_Q10: Int
        var LPC_exc_Q10: Int
        var xq_Q10: Int
        var tmp: Int
        var sLF_AR_shp_Q10: Int
        val psLPC_Q14: IntArray?
        val shp_lag_ptr: IntArray?
        shp_lag_ptr = NSQ.sLTP_shp_Q10
        var shp_lag_ptr_offset = NSQ.sLTP_shp_buf_idx - lag + Define.HARM_SHAPE_FIR_TAPS / 2
        val pred_lag_ptr = sLTP_Q16
        var pred_lag_ptr_offset = NSQ.sLTP_buf_idx - lag + Define.LTP_ORDER / 2

        /* Setup short term AR state */
        psLPC_Q14 = NSQ.sLPC_Q14
        var psLPC_Q14_offset = Define.MAX_LPC_ORDER - 1

        /* Quantization thresholds */
        val thr1_Q10 = -1536 - (Lambda_Q10 shr 1)
        var thr2_Q10 = -512 - (Lambda_Q10 shr 1)
        thr2_Q10 += (Macros.SKP_SMULBB(offset_Q10, Lambda_Q10) shr 10)
        val thr3_Q10 = 512 + (Lambda_Q10 shr 1)
        var i = 0
        while (i < length) {

            /* Generate dither */
            NSQ.rand_seed = SigProcFIX.SKP_RAND(NSQ.rand_seed)

            /* dither = rand_seed < 0 ? 0xFFFFFFFF : 0; */
            dither = NSQ.rand_seed shr 31
            assert(predictLPCOrder and 1 == 0 /* check that order is even */)
            assert(predictLPCOrder >= 10 /* check that unrolling works */)
            /* Partially unrolled */
            LPC_pred_Q10 = Macros.SKP_SMULWB(psLPC_Q14[psLPC_Q14_offset + 0], a_Q12[a_Q12_offset + 0].toInt())
            LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psLPC_Q14[psLPC_Q14_offset - 1],
                    a_Q12[a_Q12_offset + 1].toInt())
            LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psLPC_Q14[psLPC_Q14_offset - 2],
                    a_Q12[a_Q12_offset + 2].toInt())
            LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psLPC_Q14[psLPC_Q14_offset - 3],
                    a_Q12[a_Q12_offset + 3].toInt())
            LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psLPC_Q14[psLPC_Q14_offset - 4],
                    a_Q12[a_Q12_offset + 4].toInt())
            LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psLPC_Q14[psLPC_Q14_offset - 5],
                    a_Q12[a_Q12_offset + 5].toInt())
            LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psLPC_Q14[psLPC_Q14_offset - 6],
                    a_Q12[a_Q12_offset + 6].toInt())
            LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psLPC_Q14[psLPC_Q14_offset - 7],
                    a_Q12[a_Q12_offset + 7].toInt())
            LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psLPC_Q14[psLPC_Q14_offset - 8],
                    a_Q12[a_Q12_offset + 8].toInt())
            LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psLPC_Q14[psLPC_Q14_offset - 9],
                    a_Q12[a_Q12_offset + 9].toInt())
            j = 10
            while (j < predictLPCOrder) {
                LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psLPC_Q14[psLPC_Q14_offset - j],
                        a_Q12[a_Q12_offset + j].toInt())
                j++
            }
            /* Long-term prediction */
            if (sigtype == Define.SIG_TYPE_VOICED) {
                /* Unrolled loop */
                LTP_pred_Q14 = Macros.SKP_SMULWB(pred_lag_ptr[pred_lag_ptr_offset + 0],
                        b_Q14[b_Q14_offset + 0].toInt())
                LTP_pred_Q14 = Macros.SKP_SMLAWB(LTP_pred_Q14, pred_lag_ptr[pred_lag_ptr_offset - 1],
                        b_Q14[b_Q14_offset + 1].toInt())
                LTP_pred_Q14 = Macros.SKP_SMLAWB(LTP_pred_Q14, pred_lag_ptr[pred_lag_ptr_offset - 2],
                        b_Q14[b_Q14_offset + 2].toInt())
                LTP_pred_Q14 = Macros.SKP_SMLAWB(LTP_pred_Q14, pred_lag_ptr[pred_lag_ptr_offset - 3],
                        b_Q14[b_Q14_offset + 3].toInt())
                LTP_pred_Q14 = Macros.SKP_SMLAWB(LTP_pred_Q14, pred_lag_ptr[pred_lag_ptr_offset - 4],
                        b_Q14[b_Q14_offset + 4].toInt())
                pred_lag_ptr_offset++
            } else {
                LTP_pred_Q14 = 0
            }
            assert(shapingLPCOrder and 1 == 0 /* check that order is even */)
            assert(shapingLPCOrder >= 12 /* check that unrolling works */)
            /* Partially unrolled */
            n_AR_Q10 = Macros.SKP_SMULWB(psLPC_Q14[psLPC_Q14_offset + 0],
                    AR_shp_Q13[AR_shp_Q13_offset + 0].toInt())
            n_AR_Q10 = Macros.SKP_SMLAWB(n_AR_Q10, psLPC_Q14[psLPC_Q14_offset - 1],
                    AR_shp_Q13[AR_shp_Q13_offset + 1].toInt())
            n_AR_Q10 = Macros.SKP_SMLAWB(n_AR_Q10, psLPC_Q14[psLPC_Q14_offset - 2],
                    AR_shp_Q13[AR_shp_Q13_offset + 2].toInt())
            n_AR_Q10 = Macros.SKP_SMLAWB(n_AR_Q10, psLPC_Q14[psLPC_Q14_offset - 3],
                    AR_shp_Q13[AR_shp_Q13_offset + 3].toInt())
            n_AR_Q10 = Macros.SKP_SMLAWB(n_AR_Q10, psLPC_Q14[psLPC_Q14_offset - 4],
                    AR_shp_Q13[AR_shp_Q13_offset + 4].toInt())
            n_AR_Q10 = Macros.SKP_SMLAWB(n_AR_Q10, psLPC_Q14[psLPC_Q14_offset - 5],
                    AR_shp_Q13[AR_shp_Q13_offset + 5].toInt())
            n_AR_Q10 = Macros.SKP_SMLAWB(n_AR_Q10, psLPC_Q14[psLPC_Q14_offset - 6],
                    AR_shp_Q13[AR_shp_Q13_offset + 6].toInt())
            n_AR_Q10 = Macros.SKP_SMLAWB(n_AR_Q10, psLPC_Q14[psLPC_Q14_offset - 7],
                    AR_shp_Q13[AR_shp_Q13_offset + 7].toInt())
            n_AR_Q10 = Macros.SKP_SMLAWB(n_AR_Q10, psLPC_Q14[psLPC_Q14_offset - 8],
                    AR_shp_Q13[AR_shp_Q13_offset + 8].toInt())
            n_AR_Q10 = Macros.SKP_SMLAWB(n_AR_Q10, psLPC_Q14[psLPC_Q14_offset - 9],
                    AR_shp_Q13[AR_shp_Q13_offset + 9].toInt())
            n_AR_Q10 = Macros.SKP_SMLAWB(n_AR_Q10, psLPC_Q14[psLPC_Q14_offset - 10],
                    AR_shp_Q13[AR_shp_Q13_offset + 10].toInt())
            n_AR_Q10 = Macros.SKP_SMLAWB(n_AR_Q10, psLPC_Q14[psLPC_Q14_offset - 11],
                    AR_shp_Q13[AR_shp_Q13_offset + 11].toInt())
            j = 12
            while (j < shapingLPCOrder) {
                n_AR_Q10 = Macros.SKP_SMLAWB(n_AR_Q10, psLPC_Q14[psLPC_Q14_offset - j],
                        AR_shp_Q13[AR_shp_Q13_offset + j].toInt())
                j++
            }
            n_AR_Q10 = n_AR_Q10 shr 1 /* Q11 -> Q10 */
            n_AR_Q10 = Macros.SKP_SMLAWB(n_AR_Q10, NSQ.sLF_AR_shp_Q12, Tilt_Q14)
            n_LF_Q10 = Macros.SKP_SMULWB(NSQ.sLTP_shp_Q10[NSQ.sLTP_shp_buf_idx - 1], LF_shp_Q14) shl 2
            n_LF_Q10 = Macros.SKP_SMLAWT(n_LF_Q10, NSQ.sLF_AR_shp_Q12, LF_shp_Q14)
            assert(lag > 0 || sigtype == Define.SIG_TYPE_UNVOICED)

            /* Long-term shaping */
            if (lag > 0) {
                /* Symmetric, packed FIR coefficients */
                n_LTP_Q14 = Macros.SKP_SMULWB(
                        shp_lag_ptr[shp_lag_ptr_offset + 0] + shp_lag_ptr[shp_lag_ptr_offset - 2],
                        HarmShapeFIRPacked_Q14)
                n_LTP_Q14 = Macros.SKP_SMLAWT(n_LTP_Q14, shp_lag_ptr[shp_lag_ptr_offset - 1],
                        HarmShapeFIRPacked_Q14)
                shp_lag_ptr_offset++
                n_LTP_Q14 = n_LTP_Q14 shl 6
            } else {
                n_LTP_Q14 = 0
            }

            /* Input minus prediction plus noise feedback */
            // r = x[ i ] - LTP_pred - LPC_pred + n_AR + n_Tilt + n_LF + n_LTP;
            tmp = LTP_pred_Q14 - n_LTP_Q14 /* Add Q14 stuff */
            tmp = SigProcFIX.SKP_RSHIFT_ROUND(tmp, 4) /* round to Q10 */
            tmp += LPC_pred_Q10 /* add Q10 stuff */
            tmp -= n_AR_Q10 /* subtract Q10 stuff */
            tmp -= n_LF_Q10 /* subtract Q10 stuff */
            r_Q10 = x_sc_Q10[i] - tmp

            /* Flip sign depending on dither */
            r_Q10 = (r_Q10 xor dither) - dither
            r_Q10 -= offset_Q10
            r_Q10 = SigProcFIX.SKP_LIMIT_32(r_Q10, -64 shl 10, 64 shl 10)

            /* Quantize */
            if (r_Q10 < thr1_Q10) {
                q_Q0 = SigProcFIX.SKP_RSHIFT_ROUND(r_Q10 + (Lambda_Q10 shr 1), 10)
                q_Q10 = q_Q0 shl 10
            } else if (r_Q10 < thr2_Q10) {
                q_Q0 = -1
                q_Q10 = -1024
            } else if (r_Q10 > thr3_Q10) {
                q_Q0 = SigProcFIX.SKP_RSHIFT_ROUND(r_Q10 - (Lambda_Q10 shr 1), 10)
                q_Q10 = q_Q0 shl 10
            } else {
                q_Q0 = 0
                q_Q10 = 0
            }
            q[q_offset + i] = q_Q0.toByte() /* No saturation needed because max is 64 */

            /* Excitation */
            exc_Q10 = q_Q10 + offset_Q10
            exc_Q10 = (exc_Q10 xor dither) - dither

            /* Add predictions */
            LPC_exc_Q10 = exc_Q10 + SigProcFIX.SKP_RSHIFT_ROUND(LTP_pred_Q14, 4)
            xq_Q10 = LPC_exc_Q10 + LPC_pred_Q10

            /* Scale XQ back to normal level before saving */
            xq[xq_offset + i] = SigProcFIX.SKP_SAT16(SigProcFIX.SKP_RSHIFT_ROUND(
                    Macros.SKP_SMULWW(xq_Q10, Gain_Q16), 10)).toShort()

            /* Update states */
            psLPC_Q14_offset++
            psLPC_Q14[psLPC_Q14_offset] = xq_Q10 shl 4
            sLF_AR_shp_Q10 = xq_Q10 - n_AR_Q10
            NSQ.sLF_AR_shp_Q12 = sLF_AR_shp_Q10 shl 2
            NSQ.sLTP_shp_Q10[NSQ.sLTP_shp_buf_idx] = sLF_AR_shp_Q10 - n_LF_Q10
            sLTP_Q16[NSQ.sLTP_buf_idx] = LPC_exc_Q10 shl 6
            NSQ.sLTP_shp_buf_idx++
            NSQ.sLTP_buf_idx++

            /* Make dither dependent on quantized signal */
            NSQ.rand_seed += q[q_offset + i].toInt()
            i++
        }
        /* Update LPC synth buffer */
        System.arraycopy(NSQ.sLPC_Q14, length, NSQ.sLPC_Q14, 0, Define.MAX_LPC_ORDER)
    }

    /**
     *
     * @param NSQ
     * NSQ state
     * @param x
     * input in Q0
     * @param x_offset
     * @param x_sc_Q10
     * input scaled with 1/Gain
     * @param length
     * length of input
     * @param sLTP
     * re-whitened LTP state in Q0
     * @param sLTP_Q16
     * LTP state matching scaled input
     * @param subfr
     * subframe number
     * @param LTP_scale_Q14
     * @param Gains_Q16
     * @param pitchL
     */
    private fun SKP_Silk_nsq_scale_states(NSQ: SKP_Silk_nsq_state,  /* I/O NSQ state */
            x: ShortArray,  /* I input in Q0 */
            x_offset: Int, x_sc_Q10: IntArray,  /* O input scaled with 1/Gain */
            length: Int,  /* I length of input */
            sLTP: ShortArray,  /* I re-whitened LTP state in Q0 */
            sLTP_Q16: IntArray,  /* O LTP state matching scaled input */
            subfr: Int,  /* I subframe number */
            LTP_scale_Q14: Int,  /* I */
            Gains_Q16: IntArray,  /* I */
            pitchL: IntArray /* I */
    ) {
        var i: Int
        val gain_adj_Q16: Int
        var inv_gain_Q32: Int
        var inv_gain_Q16 = Int.MAX_VALUE / (Gains_Q16[subfr] shr 1)
        inv_gain_Q16 = if (inv_gain_Q16 < Short.MAX_VALUE) inv_gain_Q16 else Short.MAX_VALUE.toInt()
        val lag: Int = pitchL[subfr]

        /* After rewhitening the LTP state is un-scaled */
        if (NSQ.rewhite_flag != 0) {
            inv_gain_Q32 = inv_gain_Q16 shl 16
            if (subfr == 0) {
                /* Do LTP downscaling */
                inv_gain_Q32 = Macros.SKP_SMULWB(inv_gain_Q32, LTP_scale_Q14) shl 2
            }
            i = NSQ.sLTP_buf_idx - lag - Define.LTP_ORDER / 2
            while (i < NSQ.sLTP_buf_idx) {
                sLTP_Q16[i] = Macros.SKP_SMULWB(inv_gain_Q32, sLTP[i].toInt())
                i++
            }
        }

        /* Prepare for Worst case. Next frame starts with max lag voiced */
        var scale_length = length * Define.NB_SUBFR /* approx max lag */
        scale_length -= Macros.SKP_SMULBB(Define.NB_SUBFR - (subfr + 1), length) /*
																				 * subtract samples
																				 * that will be too
																				 * old in next frame
																				 */
        scale_length = SigProcFIX.SKP_max_int(scale_length, lag + Define.LTP_ORDER) /*
																			 * make sure to scale
																			 * whole pitch period if
																			 * voiced
																			 */

        /* Adjust for changing gain */
        if (inv_gain_Q16 != NSQ.prev_inv_gain_Q16) {
            gain_adj_Q16 = Inlines.SKP_DIV32_varQ(inv_gain_Q16, NSQ.prev_inv_gain_Q16, 16)
            i = NSQ.sLTP_shp_buf_idx - scale_length
            while (i < NSQ.sLTP_shp_buf_idx) {
                NSQ.sLTP_shp_Q10[i] = Macros.SKP_SMULWW(gain_adj_Q16, NSQ.sLTP_shp_Q10[i])
                i++
            }

            /* Scale LTP predict state */
            if (NSQ.rewhite_flag == 0) {
                i = NSQ.sLTP_buf_idx - lag - Define.LTP_ORDER / 2
                while (i < NSQ.sLTP_buf_idx) {
                    sLTP_Q16[i] = Macros.SKP_SMULWW(gain_adj_Q16, sLTP_Q16[i])
                    i++
                }
            }
            NSQ.sLF_AR_shp_Q12 = Macros.SKP_SMULWW(gain_adj_Q16, NSQ.sLF_AR_shp_Q12)

            /* scale short term state */
            i = 0
            while (i < Define.MAX_LPC_ORDER) {
                NSQ.sLPC_Q14[i] = Macros.SKP_SMULWW(gain_adj_Q16, NSQ.sLPC_Q14[i])
                i++
            }
        }

        /* Scale input */
        i = 0
        while (i < length) {
            x_sc_Q10[i] = Macros.SKP_SMULBB(x[x_offset + i].toInt(), inv_gain_Q16.toShort().toInt()) shr 6
            i++
        }
        assert(inv_gain_Q16 != 0)
        NSQ.prev_inv_gain_Q16 = inv_gain_Q16
    }
}