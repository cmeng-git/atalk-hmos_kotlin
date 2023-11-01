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
class NSQDelDecStruct {
    var RandState = IntArray(Define.DECISION_DELAY)
    var Q_Q10 = IntArray(Define.DECISION_DELAY)
    var Xq_Q10 = IntArray(Define.DECISION_DELAY)
    var Pred_Q16 = IntArray(Define.DECISION_DELAY)
    var Shape_Q10 = IntArray(Define.DECISION_DELAY)
    var Gain_Q16 = IntArray(Define.DECISION_DELAY)
    var sLPC_Q14 = IntArray(Define.MAX_FRAME_LENGTH / Define.NB_SUBFR + Define.NSQ_LPC_BUF_LENGTH())
    var LF_AR_Q12 = 0
    var Seed = 0
    var SeedInit = 0
    var RD_Q10 = 0
    fun FieldsInit() {
        Arrays.fill(RandState, 0)
        Arrays.fill(Q_Q10, 0)
        Arrays.fill(Xq_Q10, 0)
        Arrays.fill(Pred_Q16, 0)
        Arrays.fill(Shape_Q10, 0)
        Arrays.fill(Gain_Q16, 0)
        Arrays.fill(sLPC_Q14, 0)
        LF_AR_Q12 = 0
        Seed = 0
        SeedInit = 0
        RD_Q10 = 0
    }
}

/**
 *
 * @author Dingxin Xu
 */
internal class NSQ_sample_struct : Cloneable {
    var Q_Q10 = 0
    var RD_Q10 = 0
    var xq_Q14 = 0
    var LF_AR_Q12 = 0
    var sLTP_shp_Q10 = 0
    var LPC_exc_Q16 = 0
    public override fun clone(): Any {
        var clone: NSQ_sample_struct? = null
        try {
            clone = super.clone() as NSQ_sample_struct?
        } catch (e: CloneNotSupportedException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        return clone!!
    }
}

/**
 *
 * @author Dingxin Xu
 */
object NSQDelDec {
    fun SKP_Silk_NSQ_del_dec(psEncC: SKP_Silk_encoder_state?,  /* I/O Encoder State */
            psEncCtrlC: SKP_Silk_encoder_control?,  /* I Encoder Control */
            NSQ: SKP_Silk_nsq_state?,  /* I/O NSQ state */
            x: ShortArray,  /* I Prefiltered input signal */
            q: ByteArray?,  /* O Quantized pulse signal */
            LSFInterpFactor_Q2: Int,  /* I LSF interpolation factor in Q2 */
            PredCoef_Q12: ShortArray,  /* I Prediction coefs */
            LTPCoef_Q14: ShortArray,  /* I LT prediction coefs */
            AR2_Q13: ShortArray,  /* I */
            HarmShapeGain_Q14: IntArray,  /* I */
            Tilt_Q14: IntArray,  /* I Spectral tilt */
            LF_shp_Q14: IntArray,  /* I */
            Gains_Q16: IntArray,  /* I */
            Lambda_Q10: Int,  /* I */
            LTP_scale_Q14: Int /* I LTP state scaling */
    ) {
        var i: Int
        var k: Int
        var lag: Int
        var start_idx: Int
        val LSF_interpolation_flag: Int
        var Winner_ind: Int
        var subfr: Int
        var last_smple_idx: Int
        var smpl_buf_idx: Int
        var decisionDelay: Int
        val subfr_length: Int
        var A_Q12: ShortArray
        var B_Q14: ShortArray
        var AR_shp_Q13: ShortArray
        var A_Q12_offset: Int
        var B_Q14_offset: Int
        var AR_shp_Q13_offset: Int
        val pxq: ShortArray?
        var pxq_offset: Int
        val sLTP_Q16 = IntArray(2 * Define.MAX_FRAME_LENGTH)
        val sLTP = ShortArray(2 * Define.MAX_FRAME_LENGTH)
        var HarmShapeFIRPacked_Q14: Int
        val offset_Q10: Int
        val FiltState = IntArray(Define.MAX_LPC_ORDER)
        var RDmin_Q10: Int
        val x_sc_Q10 = IntArray(Define.MAX_FRAME_LENGTH / Define.NB_SUBFR)
        val psDelDec = arrayOfNulls<NSQDelDecStruct>(Define.DEL_DEC_STATES_MAX)
        /*
		 * psDelDec is an array of references, which has to be created manually.
		 */
        run {
            for (psDelDecIni_i in 0 until Define.DEL_DEC_STATES_MAX) {
                psDelDec[psDelDecIni_i] = NSQDelDecStruct()
            }
        }
        var psDD: NSQDelDecStruct?
        subfr_length = psEncC!!.frame_length / Define.NB_SUBFR

        /* Set unvoiced lag to the previous one, overwrite later for voiced */
        lag = NSQ!!.lagPrev
        assert(NSQ.prev_inv_gain_Q16 != 0)

        // TODO: use a local copy of the parameter short x[], which is supposed to be input;
        val x_tmp = x.clone()
        var x_tmp_offset = 0
        // TODO: use a local copy of the parameter byte[] q, which is supposed to be output;
        val q_tmp = q!!.clone()
        var q_tmp_offset = 0

        /* Initialize delayed decision states */
        // SKP_memset( psDelDec, 0, psEncC.nStatesDelayedDecision * sizeof( NSQ_del_dec_struct ) );
        // TODO:
        for (inx in 0 until psEncC.nStatesDelayedDecision) {
            psDelDec[inx]!!.FieldsInit()
        }
        k = 0
        while (k < psEncC.nStatesDelayedDecision) {
            psDD = psDelDec[k]
            psDD!!.Seed = k + psEncCtrlC!!.Seed and 3
            psDD.SeedInit = psDD.Seed
            psDD.RD_Q10 = 0
            psDD.LF_AR_Q12 = NSQ.sLF_AR_shp_Q12
            psDD.Shape_Q10[0] = NSQ.sLTP_shp_Q10[psEncC.frame_length - 1]
            // SKP_memcpy( psDD.sLPC_Q14, NSQ.sLPC_Q14, NSQ_LPC_BUF_LENGTH * sizeof( SKP_int32 ) );
            System.arraycopy(NSQ.sLPC_Q14, 0, psDD.sLPC_Q14, 0, Define.NSQ_LPC_BUF_LENGTH())
            k++
        }
        offset_Q10 = TablesOther.SKP_Silk_Quantization_Offsets_Q10[psEncCtrlC!!.sigtype]!![psEncCtrlC.QuantOffsetType].toInt()
        smpl_buf_idx = 0 /* index of oldest samples */
        decisionDelay = if (Define.DECISION_DELAY < subfr_length) Define.DECISION_DELAY else subfr_length

        /* For voiced frames limit the decision delay to lower than the pitch lag */
        if (psEncCtrlC.sigtype == Define.SIG_TYPE_VOICED) {
            k = 0
            while (k < Define.NB_SUBFR) {
                decisionDelay = if (decisionDelay < psEncCtrlC.pitchL[k] - Define.LTP_ORDER / 2 - 1) decisionDelay else psEncCtrlC.pitchL[k] - Define.LTP_ORDER / 2 - 1
                k++
            }
        }
        LSF_interpolation_flag = if (LSFInterpFactor_Q2 == 1 shl 2) {
            0
        } else {
            1
        }

        /* Setup pointers to start of sub frame */
        pxq = NSQ.xq
        pxq_offset = psEncC.frame_length
        NSQ.sLTP_shp_buf_idx = psEncC.frame_length
        NSQ.sLTP_buf_idx = psEncC.frame_length
        subfr = 0
        k = 0
        while (k < Define.NB_SUBFR) {
            A_Q12 = PredCoef_Q12
            A_Q12_offset = (k shr 1 or 1 - LSF_interpolation_flag) * Define.MAX_LPC_ORDER
            B_Q14 = LTPCoef_Q14
            B_Q14_offset = k * Define.LTP_ORDER
            AR_shp_Q13 = AR2_Q13
            AR_shp_Q13_offset = k * Define.SHAPE_LPC_ORDER_MAX
            NSQ.rewhite_flag = 0
            if (psEncCtrlC.sigtype == Define.SIG_TYPE_VOICED) {
                /* Voiced */
                lag = psEncCtrlC.pitchL[k]

                /* Re-whitening */
                if (k and 3 - (LSF_interpolation_flag shl 1) == 0) {
                    if (k == 2) {
                        /* RESET DELAYED DECISIONS */
                        /* Find winner */
                        RDmin_Q10 = psDelDec[0]!!.RD_Q10
                        Winner_ind = 0
                        i = 1
                        while (i < psEncC.nStatesDelayedDecision) {
                            if (psDelDec[i]!!.RD_Q10 < RDmin_Q10) {
                                RDmin_Q10 = psDelDec[i]!!.RD_Q10
                                Winner_ind = i
                            }
                            i++
                        }
                        i = 0
                        while (i < psEncC.nStatesDelayedDecision) {
                            if (i != Winner_ind) {
                                psDelDec[i]!!.RD_Q10 += Int.MAX_VALUE shr 4
                                assert(psDelDec[i]!!.RD_Q10 >= 0)
                            }
                            i++
                        }

                        /*
						 * Copy final part of signals from winner state to output and long-term
						 * filter states
						 */
                        psDD = psDelDec[Winner_ind]
                        last_smple_idx = smpl_buf_idx + decisionDelay
                        i = 0
                        while (i < decisionDelay) {
                            last_smple_idx = last_smple_idx - 1 and Define.DECISION_DELAY_MASK
                            // q[ i - decisionDelay ] = ( SKP_int )SKP_RSHIFT( psDD.Q_Q10[
                            // last_smple_idx ], 10 );
                            q_tmp[q_tmp_offset + i - decisionDelay] = (psDD!!.Q_Q10[last_smple_idx] shr 10).toByte()

                            // pxq[ i - decisionDelay ] = ( SKP_int16 )SKP_SAT16( SKP_RSHIFT_ROUND(
                            // SKP_SMULWW( psDD.Xq_Q10[ last_smple_idx ],
                            // psDD.Gain_Q16[ last_smple_idx ] ), 10 ) );
                            pxq[pxq_offset + i - decisionDelay] = SigProcFIX.SKP_SAT16(SigProcFIX.SKP_RSHIFT_ROUND(
                                    Macros.SKP_SMULWW(psDD.Xq_Q10[last_smple_idx],
                                            psDD.Gain_Q16[last_smple_idx]), 10)).toShort()
                            NSQ.sLTP_shp_Q10[NSQ.sLTP_shp_buf_idx - decisionDelay + i] = psDD.Shape_Q10[last_smple_idx]
                            i++
                        }
                        subfr = 0
                    }

                    /* Rewhiten with new A coefs */
                    start_idx = psEncC.frame_length - lag - psEncC.predictLPCOrder - Define.LTP_ORDER / 2
                    start_idx = SigProcFIX.SKP_LIMIT_int(start_idx, 0, psEncC.frame_length
                            - psEncC.predictLPCOrder)

                    // SKP_memset( FiltState, 0, psEncC.predictLPCOrder * sizeof( SKP_int32 ) );
                    Arrays.fill(FiltState, 0, psEncC.predictLPCOrder, 0)
                    MA.SKP_Silk_MA_Prediction(NSQ.xq, start_idx + k * psEncC.subfr_length, A_Q12,
                            A_Q12_offset, FiltState, sLTP, start_idx, psEncC.frame_length - start_idx,
                            psEncC.predictLPCOrder)
                    NSQ.sLTP_buf_idx = psEncC.frame_length
                    NSQ.rewhite_flag = 1
                }
            }
            assert(HarmShapeGain_Q14[k] >= 0)
            HarmShapeFIRPacked_Q14 = HarmShapeGain_Q14[k] shr 2
            HarmShapeFIRPacked_Q14 = HarmShapeFIRPacked_Q14 or (HarmShapeGain_Q14[k] shr 1 shl 16)
            SKP_Silk_nsq_del_dec_scale_states(NSQ, psDelDec, x_tmp, x_tmp_offset, x_sc_Q10,
                    subfr_length, sLTP, sLTP_Q16, k, psEncC.nStatesDelayedDecision, smpl_buf_idx,
                    LTP_scale_Q14, Gains_Q16, psEncCtrlC.pitchL)
            val smpl_buf_idx_ptr = IntArray(1)
            smpl_buf_idx_ptr[0] = smpl_buf_idx
            SKP_Silk_noise_shape_quantizer_del_dec(NSQ, psDelDec, psEncCtrlC.sigtype, x_sc_Q10,
                    q_tmp, q_tmp_offset, pxq, pxq_offset, sLTP_Q16, A_Q12, A_Q12_offset, B_Q14,
                    B_Q14_offset, AR_shp_Q13, AR_shp_Q13_offset, lag, HarmShapeFIRPacked_Q14,
                    Tilt_Q14[k], LF_shp_Q14[k], Gains_Q16[k], Lambda_Q10, offset_Q10,
                    psEncC.subfr_length, subfr++, psEncC.shapingLPCOrder, psEncC.predictLPCOrder,
                    psEncC.nStatesDelayedDecision, smpl_buf_idx_ptr, decisionDelay)
            smpl_buf_idx = smpl_buf_idx_ptr[0]
            x_tmp_offset += psEncC.subfr_length
            q_tmp_offset += psEncC.subfr_length
            pxq_offset += psEncC.subfr_length
            k++
        }

        /* Find winner */
        RDmin_Q10 = psDelDec[0]!!.RD_Q10
        Winner_ind = 0
        k = 1
        while (k < psEncC.nStatesDelayedDecision) {
            if (psDelDec[k]!!.RD_Q10 < RDmin_Q10) {
                RDmin_Q10 = psDelDec[k]!!.RD_Q10
                Winner_ind = k
            }
            k++
        }

        /* Copy final part of signals from winner state to output and long-term filter states */
        psDD = psDelDec[Winner_ind]
        psEncCtrlC.Seed = psDD!!.SeedInit
        last_smple_idx = smpl_buf_idx + decisionDelay
        i = 0
        while (i < decisionDelay) {
            last_smple_idx = last_smple_idx - 1 and Define.DECISION_DELAY_MASK
            q_tmp[q_tmp_offset + i - decisionDelay] = (psDD.Q_Q10[last_smple_idx] shr 10).toByte()
            pxq[pxq_offset + i - decisionDelay] = SigProcFIX.SKP_SAT16(SigProcFIX.SKP_RSHIFT_ROUND(
                    Macros.SKP_SMULWW(psDD.Xq_Q10[last_smple_idx], psDD.Gain_Q16[last_smple_idx]), 10)).toShort()
            NSQ.sLTP_shp_Q10[NSQ.sLTP_shp_buf_idx - decisionDelay + i] = psDD.Shape_Q10[last_smple_idx]
            sLTP_Q16[NSQ.sLTP_buf_idx - decisionDelay + i] = psDD.Pred_Q16[last_smple_idx]
            i++
        }
        // SKP_memcpy( NSQ.sLPC_Q14, &psDD.sLPC_Q14[ psEncC.subfr_length ], NSQ_LPC_BUF_LENGTH *
        // sizeof( SKP_int32 ) );
        System.arraycopy(psDD.sLPC_Q14, psEncC.subfr_length, NSQ.sLPC_Q14, 0, Define.NSQ_LPC_BUF_LENGTH())

        /* Update states */
        NSQ.sLF_AR_shp_Q12 = psDD.LF_AR_Q12
        NSQ.prev_inv_gain_Q16 = NSQ.prev_inv_gain_Q16
        NSQ.lagPrev = psEncCtrlC.pitchL[Define.NB_SUBFR - 1]

        /* Save quantized speech and noise shaping signals */
        // SKP_memcpy( NSQ.xq, &NSQ.xq[ psEncC.frame_length ], psEncC.frame_length * sizeof(
        // SKP_int16 ) );
        // SKP_memcpy( NSQ.sLTP_shp_Q10, &NSQ.sLTP_shp_Q10[ psEncC.frame_length ],
        // psEncC.frame_length * sizeof(
        // SKP_int32 ) );
        System.arraycopy(NSQ.xq, psEncC.frame_length, NSQ.xq, 0, psEncC.frame_length)
        System.arraycopy(NSQ.sLTP_shp_Q10, psEncC.frame_length, NSQ.sLTP_shp_Q10, 0,
                psEncC.frame_length)
        // TODO: copy back the q_tmp to the output parameter q;
        System.arraycopy(q_tmp, 0, q, 0, q.size)
    }

    /**
     * Noise shape quantizer for one subframe.
     *
     * @param NSQ
     * NSQ state
     * @param psDelDec
     * Delayed decision states
     * @param sigtype
     * Signal type
     * @param x_Q10
     * @param q
     * @param q_offset
     * @param xq
     * @param xq_offset
     * @param sLTP_Q16
     * LTP filter state
     * @param a_Q12
     * Short term prediction coefs
     * @param a_Q12_offset
     * @param b_Q14
     * Long term prediction coefs
     * @param b_Q14_offset
     * @param AR_shp_Q13
     * Noise shaping coefs
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
     * @param subfr
     * Subframe number
     * @param shapingLPCOrder
     * Shaping LPC filter order
     * @param predictLPCOrder
     * Prediction LPC filter order
     * @param nStatesDelayedDecision
     * Number of states in decision tree
     * @param smpl_buf_idx
     * Index to newest samples in buffers
     * @param decisionDelay
     */
    fun SKP_Silk_noise_shape_quantizer_del_dec(NSQ: SKP_Silk_nsq_state?,  /* I/O NSQ state */
            psDelDec: Array<NSQDelDecStruct?>,  /* I/O Delayed decision states */
            sigtype: Int,  /* I Signal type */
            x_Q10: IntArray,  /* I */
            q: ByteArray,  /* O */
            q_offset: Int, xq: ShortArray?,  /* O */
            xq_offset: Int, sLTP_Q16: IntArray,  /* I/O LTP filter state */
            a_Q12: ShortArray,  /* I Short term prediction coefs */
            a_Q12_offset: Int, b_Q14: ShortArray,  /* I Long term prediction coefs */
            b_Q14_offset: Int, AR_shp_Q13: ShortArray,  /* I Noise shaping coefs */
            AR_shp_Q13_offset: Int, lag: Int,  /* I Pitch lag */
            HarmShapeFIRPacked_Q14: Int,  /* I */
            Tilt_Q14: Int,  /* I Spectral tilt */
            LF_shp_Q14: Int,  /* I */
            Gain_Q16: Int,  /* I */
            Lambda_Q10: Int,  /* I */
            offset_Q10: Int,  /* I */
            length: Int,  /* I Input length */
            subfr: Int,  /* I Subframe number */
            shapingLPCOrder: Int,  /* I Shaping LPC filter order */
            predictLPCOrder: Int,  /* I Prediction LPC filter order */
            nStatesDelayedDecision: Int,  /* I Number of states in decision tree */
            smpl_buf_idx: IntArray,  /* I Index to newest samples in buffers */
            decisionDelay: Int /* I */
    ) {
        var i: Int
        var j: Int
        var k: Int
        var Winner_ind: Int
        var RDmin_ind: Int
        var RDmax_ind: Int
        var last_smple_idx: Int
        var Winner_rand_state: Int
        var LTP_pred_Q14: Int
        var LPC_pred_Q10: Int
        var n_AR_Q10: Int
        var n_LTP_Q14: Int
        var n_LF_Q10: Int
        var r_Q10: Int
        var rr_Q20: Int
        var rd1_Q10: Int
        var rd2_Q10: Int
        var RDmin_Q10: Int
        var RDmax_Q10: Int
        var q1_Q10: Int
        var q2_Q10: Int
        var dither: Int
        var exc_Q10: Int
        var LPC_exc_Q10: Int
        var xq_Q10: Int
        var tmp: Int
        var sLF_AR_shp_Q10: Int
        val pred_lag_ptr: IntArray
        val shp_lag_ptr: IntArray?
        var pred_lag_ptr_offset: Int
        var shp_lag_ptr_offset: Int
        var psLPC_Q14: IntArray
        var psLPC_Q14_offset: Int
        val psSampleState = Array(Define.DEL_DEC_STATES_MAX) { arrayOfNulls<NSQ_sample_struct>(2) }
        /*
		 * psSampleState is an two-dimension array of reference, which should be created manually.
		 */
        run {
            for (Ini_i in 0 until Define.DEL_DEC_STATES_MAX) {
                for (Ini_j in 0..1) {
                    psSampleState[Ini_i][Ini_j] = NSQ_sample_struct()
                }
            }
        }
        var psDD: NSQDelDecStruct?
        var psSS: Array<NSQ_sample_struct?>
        shp_lag_ptr = NSQ!!.sLTP_shp_Q10
        shp_lag_ptr_offset = NSQ.sLTP_shp_buf_idx - lag + Define.HARM_SHAPE_FIR_TAPS / 2
        pred_lag_ptr = sLTP_Q16
        pred_lag_ptr_offset = NSQ.sLTP_buf_idx - lag + Define.LTP_ORDER / 2
        i = 0
        while (i < length) {

            /* Perform common calculations used in all states */

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

            /* Long-term shaping */
            if (lag > 0) {
                /* Symmetric, packed FIR coefficients */
                n_LTP_Q14 = Macros.SKP_SMULWB(
                        shp_lag_ptr[shp_lag_ptr_offset + 0] + shp_lag_ptr[shp_lag_ptr_offset - 2],
                        HarmShapeFIRPacked_Q14)
                n_LTP_Q14 = Macros.SKP_SMLAWT(n_LTP_Q14, shp_lag_ptr[shp_lag_ptr_offset - 1],
                        HarmShapeFIRPacked_Q14)
                // n_LTP_Q14 = SKP_LSHIFT( n_LTP_Q14, 6 );
                n_LTP_Q14 = n_LTP_Q14 shl 6
                shp_lag_ptr_offset++
            } else {
                n_LTP_Q14 = 0
            }
            k = 0
            while (k < nStatesDelayedDecision) {

                /* Delayed decision state */
                psDD = psDelDec[k]

                /* Sample state */
                psSS = psSampleState[k]

                /* Generate dither */
                psDD!!.Seed = SigProcFIX.SKP_RAND(psDD.Seed)

                /* dither = rand_seed < 0 ? 0xFFFFFFFF : 0; */
                // dither = SKP_RSHIFT( psDD.Seed, 31 );
                dither = psDD.Seed shr 31

                /* Pointer used in short term prediction and shaping */
                psLPC_Q14 = psDD.sLPC_Q14
                psLPC_Q14_offset = Define.NSQ_LPC_BUF_LENGTH() - 1 + i
                assert(predictLPCOrder >= 10 /* check that unrolling works */)
                assert(predictLPCOrder and 1 == 0 /* check that order is even */)
                // SKP_assert( ( (SKP_int64)a_Q12 & 3 ) == 0 ); /* check that array starts at 4-byte
                // aligned address */
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

                // n_AR_Q10 = SKP_RSHIFT( n_AR_Q10, 1 ); /* Q11 -> Q10 */
                n_AR_Q10 = n_AR_Q10 shr 1 /* Q11 -> Q10 */
                n_AR_Q10 = Macros.SKP_SMLAWB(n_AR_Q10, psDD.LF_AR_Q12, Tilt_Q14)
                n_LF_Q10 = Macros.SKP_SMULWB(psDD.Shape_Q10[smpl_buf_idx[0]], LF_shp_Q14) shl 2
                n_LF_Q10 = Macros.SKP_SMLAWT(n_LF_Q10, psDD.LF_AR_Q12, LF_shp_Q14)

                /* Input minus prediction plus noise feedback */
                /* r = x[ i ] - LTP_pred - LPC_pred + n_AR + n_Tilt + n_LF + n_LTP */
                tmp = LTP_pred_Q14 - n_LTP_Q14 /* Add Q14 stuff */
                tmp = SigProcFIX.SKP_RSHIFT_ROUND(tmp, 4) /* round to Q10 */
                tmp += LPC_pred_Q10 /* add Q10 stuff */
                tmp -= n_AR_Q10 /* subtract Q10 stuff */
                tmp -= n_LF_Q10 /* subtract Q10 stuff */
                r_Q10 = x_Q10[i] - tmp /* residual error Q10 */

                /* Flip sign depending on dither */
                r_Q10 = (r_Q10 xor dither) - dither
                r_Q10 -= offset_Q10
                r_Q10 = SigProcFIX.SKP_LIMIT_32(r_Q10, -64 shl 10, 64 shl 10)

                /* Find two quantization level candidates and measure their rate-distortion */
                if (r_Q10 < -1536) {
                    q1_Q10 = SigProcFIX.SKP_RSHIFT_ROUND(r_Q10, 10) shl 10
                    r_Q10 -= q1_Q10
                    rd1_Q10 = Macros.SKP_SMLABB(-(q1_Q10 + offset_Q10) * Lambda_Q10, r_Q10, r_Q10) shr 10
                    rd2_Q10 = rd1_Q10 + 1024
                    rd2_Q10 -= SigProcFIX.SKP_ADD_LSHIFT32(Lambda_Q10, r_Q10, 1)
                    q2_Q10 = q1_Q10 + 1024
                } else if (r_Q10 > 512) {
                    q1_Q10 = SigProcFIX.SKP_RSHIFT_ROUND(r_Q10, 10) shl 10
                    r_Q10 -= q1_Q10
                    rd1_Q10 = Macros.SKP_SMLABB((q1_Q10 + offset_Q10) * Lambda_Q10, r_Q10, r_Q10) shr 10
                    rd2_Q10 = rd1_Q10 + 1024
                    rd2_Q10 -= SigProcFIX.SKP_SUB_LSHIFT32(Lambda_Q10, r_Q10, 1)
                    q2_Q10 = q1_Q10 - 1024
                } else { /* r_Q10 >= -1536 && q1_Q10 <= 512 */
                    rr_Q20 = Macros.SKP_SMULBB(offset_Q10, Lambda_Q10)
                    rd2_Q10 = Macros.SKP_SMLABB(rr_Q20, r_Q10, r_Q10) shr 10
                    rd1_Q10 = rd2_Q10 + 1024
                    rd1_Q10 += SigProcFIX.SKP_SUB_RSHIFT32(
                            SigProcFIX.SKP_ADD_LSHIFT32(Lambda_Q10, r_Q10, 1), rr_Q20, 9)
                    q1_Q10 = -1024
                    q2_Q10 = 0
                }
                if (rd1_Q10 < rd2_Q10) {
                    psSS[0]!!.RD_Q10 = psDD.RD_Q10 + rd1_Q10
                    psSS[1]!!.RD_Q10 = psDD.RD_Q10 + rd2_Q10
                    psSS[0]!!.Q_Q10 = q1_Q10
                    psSS[1]!!.Q_Q10 = q2_Q10
                } else {
                    psSS[0]!!.RD_Q10 = psDD.RD_Q10 + rd2_Q10
                    psSS[1]!!.RD_Q10 = psDD.RD_Q10 + rd1_Q10
                    psSS[0]!!.Q_Q10 = q2_Q10
                    psSS[1]!!.Q_Q10 = q1_Q10
                }

                /* Update states for best quantization */

                /* Quantized excitation */
                exc_Q10 = offset_Q10 + psSS[0]!!.Q_Q10
                exc_Q10 = (exc_Q10 xor dither) - dither

                /* Add predictions */
                LPC_exc_Q10 = exc_Q10 + SigProcFIX.SKP_RSHIFT_ROUND(LTP_pred_Q14, 4)
                xq_Q10 = LPC_exc_Q10 + LPC_pred_Q10

                /* Update states */
                sLF_AR_shp_Q10 = xq_Q10 - n_AR_Q10
                psSS[0]!!.sLTP_shp_Q10 = sLF_AR_shp_Q10 - n_LF_Q10
                psSS[0]!!.LF_AR_Q12 = sLF_AR_shp_Q10 shl 2
                psSS[0]!!.xq_Q14 = xq_Q10 shl 4
                psSS[0]!!.LPC_exc_Q16 = LPC_exc_Q10 shl 6

                /* Update states for second best quantization */

                /* Quantized excitation */
                exc_Q10 = offset_Q10 + psSS[1]!!.Q_Q10
                exc_Q10 = (exc_Q10 xor dither) - dither

                /* Add predictions */
                LPC_exc_Q10 = exc_Q10 + SigProcFIX.SKP_RSHIFT_ROUND(LTP_pred_Q14, 4)
                xq_Q10 = LPC_exc_Q10 + LPC_pred_Q10

                /* Update states */
                sLF_AR_shp_Q10 = xq_Q10 - n_AR_Q10
                psSS[1]!!.sLTP_shp_Q10 = sLF_AR_shp_Q10 - n_LF_Q10
                psSS[1]!!.LF_AR_Q12 = sLF_AR_shp_Q10 shl 2
                psSS[1]!!.xq_Q14 = xq_Q10 shl 4
                psSS[1]!!.LPC_exc_Q16 = LPC_exc_Q10 shl 6
                k++
            }
            smpl_buf_idx[0] = smpl_buf_idx[0] - 1 and Define.DECISION_DELAY_MASK /*
																			 * Index to newest
																			 * samples
																			 */
            last_smple_idx = smpl_buf_idx[0] + decisionDelay and Define.DECISION_DELAY_MASK /*
																					 * Index to
																					 * decisionDelay
																					 * old samples
																					 */

            /* Find winner */
            RDmin_Q10 = psSampleState[0][0]!!.RD_Q10
            Winner_ind = 0
            k = 1
            while (k < nStatesDelayedDecision) {
                if (psSampleState[k][0]!!.RD_Q10 < RDmin_Q10) {
                    RDmin_Q10 = psSampleState[k][0]!!.RD_Q10
                    Winner_ind = k
                }
                k++
            }

            /* Increase RD values of expired states */
            Winner_rand_state = psDelDec[Winner_ind]!!.RandState[last_smple_idx]
            k = 0
            while (k < nStatesDelayedDecision) {
                if (psDelDec[k]!!.RandState[last_smple_idx] != Winner_rand_state) {
                    psSampleState[k][0]!!.RD_Q10 = psSampleState[k][0]!!.RD_Q10 + (Int.MAX_VALUE shr 4)
                    psSampleState[k][1]!!.RD_Q10 = psSampleState[k][1]!!.RD_Q10 + (Int.MAX_VALUE shr 4)
                    assert(psSampleState[k][0]!!.RD_Q10 >= 0)
                }
                k++
            }

            /* Find worst in first set and best in second set */
            RDmax_Q10 = psSampleState[0][0]!!.RD_Q10
            RDmin_Q10 = psSampleState[0][1]!!.RD_Q10
            RDmax_ind = 0
            RDmin_ind = 0
            k = 1
            while (k < nStatesDelayedDecision) {

                /* find worst in first set */
                if (psSampleState[k][0]!!.RD_Q10 > RDmax_Q10) {
                    RDmax_Q10 = psSampleState[k][0]!!.RD_Q10
                    RDmax_ind = k
                }
                /* find best in second set */
                if (psSampleState[k][1]!!.RD_Q10 < RDmin_Q10) {
                    RDmin_Q10 = psSampleState[k][1]!!.RD_Q10
                    RDmin_ind = k
                }
                k++
            }

            /* Replace a state if best from second set outperforms worst in first set */
            if (RDmin_Q10 < RDmax_Q10) {
                // SKP_Silk_copy_del_dec_state( &psDelDec[ RDmax_ind ], &psDelDec[ RDmin_ind ], i );
                SKP_Silk_copy_del_dec_state(psDelDec[RDmax_ind], psDelDec[RDmin_ind], i)
                // TODO:how to copy a struct ???
                // SKP_memcpy( &psSampleState[ RDmax_ind ][ 0 ], &psSampleState[ RDmin_ind ][ 1 ],
                // sizeof(
                // NSQ_sample_struct ) );
                psSampleState[RDmax_ind][0] = psSampleState[RDmin_ind][1]!!
                        .clone() as NSQ_sample_struct
            }

            /* Write samples from winner to output and long-term filter states */
            psDD = psDelDec[Winner_ind]
            if (subfr > 0 || i >= decisionDelay) {
                q[q_offset + i - decisionDelay] = (psDD!!.Q_Q10[last_smple_idx] shr 10).toByte()
                xq!![xq_offset + i - decisionDelay] = SigProcFIX.SKP_SAT16(SigProcFIX.SKP_RSHIFT_ROUND(
                        Macros.SKP_SMULWW(psDD.Xq_Q10[last_smple_idx], psDD.Gain_Q16[last_smple_idx]), 10)).toShort()
                NSQ.sLTP_shp_Q10[NSQ.sLTP_shp_buf_idx - decisionDelay] = psDD.Shape_Q10[last_smple_idx]
                sLTP_Q16[NSQ.sLTP_buf_idx - decisionDelay] = psDD.Pred_Q16[last_smple_idx]
            }
            NSQ.sLTP_shp_buf_idx++
            NSQ.sLTP_buf_idx++

            /* Update states */
            k = 0
            while (k < nStatesDelayedDecision) {
                psDD = psDelDec[k]
                // TODO: psSS is an array of reference rather than a reference.
                // psSS = &psSampleState[ k ][ 0 ];
                psSS = psSampleState[k]
                psDD!!.LF_AR_Q12 = psSS[0]!!.LF_AR_Q12
                psDD.sLPC_Q14[Define.NSQ_LPC_BUF_LENGTH() + i] = psSS[0]!!.xq_Q14
                psDD.Xq_Q10[smpl_buf_idx[0]] = psSS[0]!!.xq_Q14 shr 4
                psDD.Q_Q10[smpl_buf_idx[0]] = psSS[0]!!.Q_Q10
                psDD.Pred_Q16[smpl_buf_idx[0]] = psSS[0]!!.LPC_exc_Q16
                psDD.Shape_Q10[smpl_buf_idx[0]] = psSS[0]!!.sLTP_shp_Q10
                psDD.Seed = SigProcFIX.SKP_ADD_RSHIFT32(psDD.Seed, psSS[0]!!.Q_Q10, 10)
                psDD.RandState[smpl_buf_idx[0]] = psDD.Seed
                psDD.RD_Q10 = psSS[0]!!.RD_Q10
                psDD.Gain_Q16[smpl_buf_idx[0]] = Gain_Q16
                k++
            }
            i++
        }
        /* Update LPC states */
        k = 0
        while (k < nStatesDelayedDecision) {
            psDD = psDelDec[k]
            System.arraycopy(psDD!!.sLPC_Q14, length, psDD.sLPC_Q14, 0, Define.NSQ_LPC_BUF_LENGTH())
            k++
        }
    }

    /**
     *
     * @param NSQ
     * NSQ state
     * @param psDelDec
     * Delayed decision states
     * @param x
     * Input in Q0
     * @param x_offset
     * offset of valid data.
     * @param x_sc_Q10
     * nput scaled with 1/Gain in Q10
     * @param length
     * Length of input
     * @param sLTP
     * Re-whitened LTP state in Q0
     * @param sLTP_Q16
     * LTP state matching scaled input
     * @param subfr
     * Subframe number
     * @param nStatesDelayedDecision
     * Number of del dec states
     * @param smpl_buf_idx
     * Index to newest samples in buffers
     * @param LTP_scale_Q14
     * LTP state scaling
     * @param Gains_Q16
     * @param pitchL
     * Pitch lag
     */
    fun SKP_Silk_nsq_del_dec_scale_states(NSQ: SKP_Silk_nsq_state?,  /* I/O NSQ state */
            psDelDec: Array<NSQDelDecStruct?>,  /* I/O Delayed decision states */
            x: ShortArray,  /* I Input in Q0 */
            x_offset: Int, x_sc_Q10: IntArray,  /* O Input scaled with 1/Gain in Q10 */
            length: Int,  /* I Length of input */
            sLTP: ShortArray,  /* I Re-whitened LTP state in Q0 */
            sLTP_Q16: IntArray,  /* O LTP state matching scaled input */
            subfr: Int,  /* I Subframe number */
            nStatesDelayedDecision: Int,  /* I Number of del dec states */
            smpl_buf_idx: Int,  /* I Index to newest samples in buffers */
            LTP_scale_Q14: Int,  /* I LTP state scaling */
            Gains_Q16: IntArray,  /* I */
            pitchL: IntArray? /* I Pitch lag */
    ) {
        var i: Int
        var k: Int
        var scale_length: Int
        val lag: Int
        var inv_gain_Q16: Int
        val gain_adj_Q16: Int
        var inv_gain_Q32: Int
        var psDD: NSQDelDecStruct?
        inv_gain_Q16 = Int.MAX_VALUE / (Gains_Q16[subfr] shr 1)
        inv_gain_Q16 = if (inv_gain_Q16 < Short.MAX_VALUE) inv_gain_Q16 else Short.MAX_VALUE.toInt()
        lag = pitchL!![subfr]
        /* After rewhitening the LTP state is un-scaled. So scale with inv_gain_Q16 */
        if (NSQ!!.rewhite_flag != 0) {
            inv_gain_Q32 = inv_gain_Q16 shl 16
            if (subfr == 0) {
                /* Do LTP downscaling */
                inv_gain_Q32 = Macros.SKP_SMULWB(inv_gain_Q32, LTP_scale_Q14) shl 2
            }
            i = NSQ.sLTP_buf_idx - lag - Define.LTP_ORDER / 2
            while (i < NSQ.sLTP_buf_idx) {
                assert(i < Define.MAX_FRAME_LENGTH)
                sLTP_Q16[i] = Macros.SKP_SMULWB(inv_gain_Q32, sLTP[i].toInt())
                i++
            }
        }

        /* Adjust for changing gain */
        if (inv_gain_Q16 != NSQ.prev_inv_gain_Q16) {
            gain_adj_Q16 = Inlines.SKP_DIV32_varQ(inv_gain_Q16, NSQ.prev_inv_gain_Q16, 16)
            k = 0
            while (k < nStatesDelayedDecision) {
                psDD = psDelDec[k]

                /* Scale scalar states */
                psDD!!.LF_AR_Q12 = Macros.SKP_SMULWW(gain_adj_Q16, psDD.LF_AR_Q12)

                /* scale short term state */i = 0
                while (i < Define.NSQ_LPC_BUF_LENGTH()) {
                    psDD.sLPC_Q14[Define.NSQ_LPC_BUF_LENGTH() - i - 1] = Macros.SKP_SMULWW(gain_adj_Q16,
                            psDD.sLPC_Q14[Define.NSQ_LPC_BUF_LENGTH() - i - 1])
                    i++
                }
                i = 0
                while (i < Define.DECISION_DELAY) {
                    psDD.Pred_Q16[i] = Macros.SKP_SMULWW(gain_adj_Q16, psDD.Pred_Q16[i])
                    psDD.Shape_Q10[i] = Macros.SKP_SMULWW(gain_adj_Q16, psDD.Shape_Q10[i])
                    i++
                }
                k++
            }

            /* Scale long term shaping state */

            /* Calculate length to be scaled, Worst case: Next frame is voiced with max lag */
            scale_length = length * Define.NB_SUBFR /* aprox max lag */
            scale_length -= Macros.SKP_SMULBB(Define.NB_SUBFR - (subfr + 1), length) /*
																					 * subtract
																					 * samples that
																					 * will be too
																					 * old in next
																					 * frame
																					 */
            scale_length = Math.max(scale_length, lag + Define.LTP_ORDER) /*
																	 * make sure to scale whole
																	 * pitch period if voiced
																	 */
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
        }

        /* Scale input */i = 0
        while (i < length) {
            x_sc_Q10[i] = Macros.SKP_SMULBB(x[x_offset + i].toInt(), inv_gain_Q16.toShort().toInt()) shr 6
            i++
        }
        assert(inv_gain_Q16 != 0)
        NSQ.prev_inv_gain_Q16 = inv_gain_Q16
    }

    /**
     *
     * @param DD_dst
     * Dst del dec state
     * @param DD_src
     * Src del dec state
     * @param LPC_state_idx
     * Index to LPC buffer
     */
    fun SKP_Silk_copy_del_dec_state(DD_dst: NSQDelDecStruct?,  /* I Dst del dec state */
            DD_src: NSQDelDecStruct?,  /* I Src del dec state */
            LPC_state_idx: Int /* I Index to LPC buffer */
    ) {
        System.arraycopy(DD_src!!.RandState, 0, DD_dst!!.RandState, 0, Define.DECISION_DELAY)
        System.arraycopy(DD_src.Q_Q10, 0, DD_dst.Q_Q10, 0, Define.DECISION_DELAY)
        System.arraycopy(DD_src.Pred_Q16, 0, DD_dst.Pred_Q16, 0, Define.DECISION_DELAY)
        System.arraycopy(DD_src.Shape_Q10, 0, DD_dst.Shape_Q10, 0, Define.DECISION_DELAY)
        System.arraycopy(DD_src.Xq_Q10, 0, DD_dst.Xq_Q10, 0, Define.DECISION_DELAY)
        System.arraycopy(DD_src.sLPC_Q14, LPC_state_idx, DD_dst.sLPC_Q14, LPC_state_idx,
                Define.NSQ_LPC_BUF_LENGTH())
        DD_dst.LF_AR_Q12 = DD_src.LF_AR_Q12
        DD_dst.Seed = DD_src.Seed
        DD_dst.SeedInit = DD_src.SeedInit
        DD_dst.RD_Q10 = DD_src.RD_Q10
    }
}