/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

import java.util.*
import kotlin.math.max
import kotlin.Int as Int1

/**
 * SILK PNG.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object PLC {
    const val BWE_COEF_Q16 = 64880 /* 0.99 in Q16 */
    const val V_PITCH_GAIN_START_MIN_Q14 = 11469 /* 0.7 in Q14 */
    const val V_PITCH_GAIN_START_MAX_Q14 = 15565 /* 0.95 in Q14 */
    const val MAX_PITCH_LAG_MS = 18
    const val SA_THRES_Q8 = 50
    const val USE_SINGLE_TAP = true
    const val RAND_BUF_SIZE = 128
    const val RAND_BUF_MASK = RAND_BUF_SIZE - 1
    const val LOG2_INV_LPC_GAIN_HIGH_THRES = 3 /* 2^3 = 8 dB LPC gain */
    const val LOG2_INV_LPC_GAIN_LOW_THRES = 8 /* 2^8 = 24 dB LPC gain */
    const val PITCH_DRIFT_FAC_Q16 = 655 /* 0.01 in Q16 */
    private const val NB_ATT = 2
    private val HARM_ATT_Q15 = shortArrayOf(32440, 31130) /* 0.99, 0.95 */
    private val PLC_RAND_ATTENUATE_V_Q15 = shortArrayOf(31130, 26214) /* 0.95, 0.8 */
    private val PLC_RAND_ATTENUATE_UV_Q15 = shortArrayOf(32440, 29491) /* 0.99, 0.9 */

    /**
     * PLC reset.
     *
     * @param psDec
     * Decoder state.
     */
    fun SKP_Silk_PLC_Reset(psDec: SKP_Silk_decoder_state? /* I/O Decoder state */
    ) {
        psDec!!.sPLC.pitchL_Q8 = psDec.frame_length shr 1
    }

    /**
     *
     * @param psDec
     * Decoder state.
     * @param psDecCtrl
     * Decoder control.
     * @param signal
     * Concealed signal.
     * @param signal_offset
     * offset of the valid data.
     * @param length
     * Length of residual.
     * @param lost
     * Loss flag.
     */
    fun SKP_Silk_PLC(psDec: SKP_Silk_decoder_state?,  /* I Decoder state */
            psDecCtrl: SKP_Silk_decoder_control,  /* I Decoder control */
            signal: ShortArray,  /* O Concealed signal */
            signal_offset: Int1, length: Int1,  /* I length of residual */
            lost: Int1 /* I Loss flag */
    ) {
        /* PLC control function */
        if (psDec!!.fs_kHz != psDec.sPLC.fs_kHz) {
            SKP_Silk_PLC_Reset(psDec)
            psDec.sPLC.fs_kHz = psDec.fs_kHz
        }
        if (lost != 0) {
            /** */
            /* Generate Signal */
            /** */
            SKP_Silk_PLC_conceal(psDec, psDecCtrl, signal, signal_offset, length)
            psDec.lossCnt++
        } else {
            /** */
            /* Update state */
            /** */
            SKP_Silk_PLC_update(psDec, psDecCtrl, signal, signal_offset, length)
        }
    }

    /**
     * Update state of PLC
     *
     * @param psDec
     * Decoder state.
     * @param psDecCtrl
     * Decoder control.
     * @param signal
     * @param signal_offset
     * @param length
     */
    fun SKP_Silk_PLC_update(psDec: SKP_Silk_decoder_state?,  /* (I/O) Decoder state */
            psDecCtrl: SKP_Silk_decoder_control,  /* (I/O) Decoder control */
            signal: ShortArray?, signal_offset: Int1, length: Int1
    ) {
        var LTP_Gain_Q14: Int1
        var temp_LTP_Gain_Q14: Int1
        var i: Int1
        var j: Int1
        val psPLC: SKP_Silk_PLC_struct?
        psPLC = psDec!!.sPLC

        /* Update parameters used in case of packet loss */
        psDec.prev_sigtype = psDecCtrl.sigtype
        LTP_Gain_Q14 = 0
        if (psDecCtrl.sigtype == Define.SIG_TYPE_VOICED) {
            /* Find the parameters for the last subframe which contains a pitch pulse */
            j = 0
            while (j * psDec.subfr_length < psDecCtrl.pitchL[Define.NB_SUBFR - 1]) {
                temp_LTP_Gain_Q14 = 0
                i = 0
                while (i < Define.LTP_ORDER) {
                    temp_LTP_Gain_Q14 += psDecCtrl.LTPCoef_Q14[(Define.NB_SUBFR - 1 - j) * Define.LTP_ORDER + i].toInt()
                    i++
                }
                if (temp_LTP_Gain_Q14 > LTP_Gain_Q14) {
                    LTP_Gain_Q14 = temp_LTP_Gain_Q14
                    System.arraycopy(psDecCtrl.LTPCoef_Q14,
                            Macros.SKP_SMULBB(Define.NB_SUBFR - 1 - j, Define.LTP_ORDER), psPLC.LTPCoef_Q14, 0, Define.LTP_ORDER)
                    psPLC.pitchL_Q8 = psDecCtrl.pitchL[Define.NB_SUBFR - 1 - j] shl 8
                }
                j++
            }
            if (USE_SINGLE_TAP) {
                Arrays.fill(psPLC.LTPCoef_Q14, 0, Define.LTP_ORDER, 0.toShort())
                psPLC.LTPCoef_Q14[Define.LTP_ORDER / 2] = LTP_Gain_Q14.toShort()
            }

            /* Limit LT coefs */
            if (LTP_Gain_Q14 < V_PITCH_GAIN_START_MIN_Q14) {
                val scale_Q10: Int1
                val tmp: Int1
                tmp = V_PITCH_GAIN_START_MIN_Q14 shl 10
                scale_Q10 = tmp / Math.max(LTP_Gain_Q14, 1)
                i = 0
                while (i < Define.LTP_ORDER) {
                    psPLC.LTPCoef_Q14[i] = (Macros.SKP_SMULBB(psPLC.LTPCoef_Q14[i].toInt(), scale_Q10) shr 10).toShort()
                    i++
                }
            } else if (LTP_Gain_Q14 > V_PITCH_GAIN_START_MAX_Q14) {
                val scale_Q14: Int1
                val tmp: Int1
                tmp = V_PITCH_GAIN_START_MAX_Q14 shl 14
                scale_Q14 = tmp / Math.max(LTP_Gain_Q14, 1)
                i = 0
                while (i < Define.LTP_ORDER) {
                    psPLC.LTPCoef_Q14[i] = (Macros.SKP_SMULBB(psPLC.LTPCoef_Q14[i].toInt(), scale_Q14) shr 14).toShort()
                    i++
                }
            }
        } else {
            psPLC.pitchL_Q8 = Macros.SKP_SMULBB(psDec.fs_kHz, 18) shl 8
            Arrays.fill(psPLC.LTPCoef_Q14, 0, Define.LTP_ORDER, 0.toShort())
        }

        /* Save LPC coeficients */
        System.arraycopy(psDecCtrl.PredCoef_Q12[1], 0, psPLC.prevLPC_Q12, 0, psDec.LPC_order)
        psPLC.prevLTP_scale_Q14 = psDecCtrl.LTP_scale_Q14.toShort()

        /* Save Gains */
        System.arraycopy(psDecCtrl.Gains_Q16, 0, psPLC.prevGain_Q16, 0, Define.NB_SUBFR)
    }

    /**
     *
     * @param psDec
     * Decoder state.
     * @param psDecCtrl
     * Decoder control.
     * @param signal
     * concealed signal.
     * @param signal_offset
     * offset of the valid data.
     * @param length
     * Length of residual.
     */
    fun SKP_Silk_PLC_conceal(psDec: SKP_Silk_decoder_state?,  /* I/O Decoder state */
            psDecCtrl: SKP_Silk_decoder_control,  /* I/O Decoder control */
            signal: ShortArray,  /* O concealed signal */
            signal_offset: Int1, length: Int1 /* I length of residual */
    ) {
        var i: Int1
        var j: Int1
        val B_Q14: ShortArray
        val exc_buf = ShortArray(Define.MAX_FRAME_LENGTH)
        var rand_scale_Q14: Short
        val A_Q12_tmp = ShortArray(Define.MAX_LPC_ORDER)
        var rand_seed: Int1
        var rand_Gain_Q15: Int1
        var lag: Int1
        var idx: Int1
        val shift1: Int1
        val shift2: Int1
        val shift_ptr = IntArray(1)
        val energy1: Int1
        val energy2: Int1
        val energy_ptr = IntArray(1)
        val rand_ptr: IntArray?
        var pred_lag_ptr: IntArray?
        val rand_ptr_offset: Int1
        var pred_lag_ptr_offset: Int1
        val sig_Q10 = IntArray(Define.MAX_FRAME_LENGTH)
        var LPC_exc_Q10: Int1
        var LPC_pred_Q10: Int1
        var LTP_pred_Q14: Int1
        val psPLC: SKP_Silk_PLC_struct?
        psPLC = psDec!!.sPLC

        /* Update LTP buffer */
        System.arraycopy(psDec.sLTP_Q16, psDec.frame_length, psDec.sLTP_Q16, 0, psDec.frame_length)

        /* LPC concealment. Apply BWE to previous LPC */
        Bwexpander.SKP_Silk_bwexpander(psPLC.prevLPC_Q12, psDec.LPC_order, BWE_COEF_Q16)

        /* Find random noise component */
        /* Scale previous excitation signal */
        val exc_buf_ptr = exc_buf
        var exc_buf_ptr_offset = 0
        var k = Define.NB_SUBFR shr 1
        while (k < Define.NB_SUBFR) {
            i = 0
            while (i < psDec.subfr_length) {
                exc_buf_ptr[exc_buf_ptr_offset + i] = (Macros.SKP_SMULWW(psDec.exc_Q10[i + k
                        * psDec.subfr_length], psPLC.prevGain_Q16[k]) shr 10).toShort()
                i++
            }
            exc_buf_ptr_offset += psDec.subfr_length
            k++
        }
        /*
		 * Find the subframe with lowest energy of the last two and use that as random noise
		 * generator
		 */
        SumSqrShift.SKP_Silk_sum_sqr_shift(energy_ptr, shift_ptr, exc_buf, 0, psDec.subfr_length)
        energy1 = energy_ptr[0]
        shift1 = shift_ptr[0]
        SumSqrShift.SKP_Silk_sum_sqr_shift(energy_ptr, shift_ptr, exc_buf, psDec.subfr_length,
                psDec.subfr_length)
        energy2 = energy_ptr[0]
        shift2 = shift_ptr[0]
        if (energy1 shr shift2 < energy2 shr shift1) {
            /* First sub-frame has lowest energy */
            rand_ptr = psDec.exc_Q10
            rand_ptr_offset = Math.max(0, 3 * psDec.subfr_length - RAND_BUF_SIZE)
        } else {
            /* Second sub-frame has lowest energy */
            rand_ptr = psDec.exc_Q10
            rand_ptr_offset = Math.max(0, psDec.frame_length - RAND_BUF_SIZE)
        }

        /* Setup Gain to random noise component */
        B_Q14 = psPLC.LTPCoef_Q14
        rand_scale_Q14 = psPLC.randScale_Q14

        /* Setup attenuation gains */
        val harm_Gain_Q15 = HARM_ATT_Q15[Math.min(NB_ATT - 1, psDec.lossCnt)].toInt()
        rand_Gain_Q15 = if (psDec.prev_sigtype == Define.SIG_TYPE_VOICED) {
            PLC_RAND_ATTENUATE_V_Q15[Math.min(NB_ATT - 1, psDec.lossCnt)].toInt()
        } else {
            PLC_RAND_ATTENUATE_UV_Q15[Math.min(NB_ATT - 1, psDec.lossCnt)].toInt()
        }

        /* First Lost frame */
        if (psDec.lossCnt == 0) {
            rand_scale_Q14 = (1 shl 14).toShort()

            /* Reduce random noise Gain for voiced frames */
            if (psDec.prev_sigtype == Define.SIG_TYPE_VOICED) {
                i = 0
                while (i < Define.LTP_ORDER) {
                    rand_scale_Q14 = (rand_scale_Q14 - B_Q14[i]).toShort()
                    i++
                }
                rand_scale_Q14 = Math.max(3277, rand_scale_Q14.toInt()).toShort() /* 0.2 */
                rand_scale_Q14 = (Macros.SKP_SMULBB(rand_scale_Q14.toInt(), psPLC.prevLTP_scale_Q14.toInt()) shr 14).toShort()
            }

            /* Reduce random noise for unvoiced frames with high LPC gain */
            if (psDec.prev_sigtype == Define.SIG_TYPE_UNVOICED) {
                val invGain_Q30: Int1
                val invGain_Q30_ptr = IntArray(1)
                LPCInvPredGain.SKP_Silk_LPC_inverse_pred_gain(invGain_Q30_ptr, psPLC.prevLPC_Q12,
                        psDec.LPC_order)
                invGain_Q30 = invGain_Q30_ptr[0]
                var down_scale_Q30: Int1 = Math.min(1 shl 30 shr LOG2_INV_LPC_GAIN_HIGH_THRES, invGain_Q30)
                down_scale_Q30 = Math.max(1 shl 30 shr LOG2_INV_LPC_GAIN_LOW_THRES,
                        down_scale_Q30)
                down_scale_Q30 = down_scale_Q30 shl LOG2_INV_LPC_GAIN_HIGH_THRES
                rand_Gain_Q15 = Macros.SKP_SMULWB(down_scale_Q30, rand_Gain_Q15) shr 14
            }
        }
        rand_seed = psPLC.rand_seed
        lag = SigProcFIX.SKP_RSHIFT_ROUND(psPLC.pitchL_Q8, 8)
        var sLTP_buf_idx = psDec.frame_length
        /** */
        /* LTP synthesis filtering */
        /** */
        var sig_Q10_ptr = sig_Q10
        var sig_Q10_ptr_offset = 0
        k = 0
        while (k < Define.NB_SUBFR) {

            /* Setup pointer */
            pred_lag_ptr = psDec.sLTP_Q16
            pred_lag_ptr_offset = sLTP_buf_idx - lag + Define.LTP_ORDER / 2
            i = 0
            while (i < psDec.subfr_length) {
                rand_seed = SigProcFIX.SKP_RAND(rand_seed)
                idx = rand_seed shr 25 and RAND_BUF_MASK

                /* Unrolled loop */
                LTP_pred_Q14 = Macros.SKP_SMULWB(pred_lag_ptr[pred_lag_ptr_offset + 0], B_Q14[0].toInt())
                LTP_pred_Q14 = Macros.SKP_SMLAWB(LTP_pred_Q14, pred_lag_ptr[pred_lag_ptr_offset - 1],
                        B_Q14[1].toInt())
                LTP_pred_Q14 = Macros.SKP_SMLAWB(LTP_pred_Q14, pred_lag_ptr[pred_lag_ptr_offset - 2],
                        B_Q14[2].toInt())
                LTP_pred_Q14 = Macros.SKP_SMLAWB(LTP_pred_Q14, pred_lag_ptr[pred_lag_ptr_offset - 3],
                        B_Q14[3].toInt())
                LTP_pred_Q14 = Macros.SKP_SMLAWB(LTP_pred_Q14, pred_lag_ptr[pred_lag_ptr_offset - 4],
                        B_Q14[4].toInt())
                pred_lag_ptr_offset++

                /* Generate LPC residual */
                LPC_exc_Q10 = Macros.SKP_SMULWB(rand_ptr[rand_ptr_offset + idx], rand_scale_Q14.toInt()) shl 2 /*
																								 * Random
																								 * noise
																								 * part
																								 */
                LPC_exc_Q10 += SigProcFIX.SKP_RSHIFT_ROUND(LTP_pred_Q14, 4) /*
																							 * Harmonic
																							 * part
																							 */

                /* Update states */
                psDec.sLTP_Q16[sLTP_buf_idx] = LPC_exc_Q10 shl 6
                sLTP_buf_idx++

                /* Save LPC residual */
                sig_Q10_ptr[sig_Q10_ptr_offset + i] = LPC_exc_Q10
                i++
            }
            sig_Q10_ptr_offset += psDec.subfr_length
            /* Gradually reduce LTP gain */
            j = 0
            while (j < Define.LTP_ORDER) {
                B_Q14[j] = (Macros.SKP_SMULBB(harm_Gain_Q15, B_Q14[j].toInt()) shr 15).toShort()
                j++
            }
            /* Gradually reduce excitation gain */
            rand_scale_Q14 = (Macros.SKP_SMULBB(rand_scale_Q14.toInt(), rand_Gain_Q15) shr 15).toShort()

            /* Slowly increase pitch lag */
            psPLC.pitchL_Q8 += Macros.SKP_SMULWB(psPLC.pitchL_Q8, PITCH_DRIFT_FAC_Q16)
            psPLC.pitchL_Q8 = Math.min(psPLC.pitchL_Q8,
                    Macros.SKP_SMULBB(MAX_PITCH_LAG_MS, psDec.fs_kHz) shl 8)
            lag = SigProcFIX.SKP_RSHIFT_ROUND(psPLC.pitchL_Q8, 8)
            k++
        }
        /** */
        /* LPC synthesis filtering */
        /** */
        sig_Q10_ptr = sig_Q10
        sig_Q10_ptr_offset = 0
        /* Preload LPC coeficients to array on stack. Gives small performance gain */
        System.arraycopy(psPLC.prevLPC_Q12, 0, A_Q12_tmp, 0, psDec.LPC_order)
        Typedef.SKP_assert(psDec.LPC_order >= 10) /* check that unrolling works */
        k = 0
        while (k < Define.NB_SUBFR) {
            i = 0
            while (i < psDec.subfr_length) {

                /* partly unrolled */
                LPC_pred_Q10 = Macros.SKP_SMULWB(psDec.sLPC_Q14[Define.MAX_LPC_ORDER + i - 1], A_Q12_tmp[0].toInt())
                LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psDec.sLPC_Q14[Define.MAX_LPC_ORDER + i - 2],
                        A_Q12_tmp[1].toInt())
                LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psDec.sLPC_Q14[Define.MAX_LPC_ORDER + i - 3],
                        A_Q12_tmp[2].toInt())
                LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psDec.sLPC_Q14[Define.MAX_LPC_ORDER + i - 4],
                        A_Q12_tmp[3].toInt())
                LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psDec.sLPC_Q14[Define.MAX_LPC_ORDER + i - 5],
                        A_Q12_tmp[4].toInt())
                LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psDec.sLPC_Q14[Define.MAX_LPC_ORDER + i - 6],
                        A_Q12_tmp[5].toInt())
                LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psDec.sLPC_Q14[Define.MAX_LPC_ORDER + i - 7],
                        A_Q12_tmp[6].toInt())
                LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psDec.sLPC_Q14[Define.MAX_LPC_ORDER + i - 8],
                        A_Q12_tmp[7].toInt())
                LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psDec.sLPC_Q14[Define.MAX_LPC_ORDER + i - 9],
                        A_Q12_tmp[8].toInt())
                LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psDec.sLPC_Q14[Define.MAX_LPC_ORDER + i - 10],
                        A_Q12_tmp[9].toInt())
                j = 10
                while (j < psDec.LPC_order) {
                    LPC_pred_Q10 = Macros.SKP_SMLAWB(LPC_pred_Q10, psDec.sLPC_Q14[Define.MAX_LPC_ORDER + i - j
                            - 1], A_Q12_tmp[j].toInt())
                    j++
                }
                /* Add prediction to LPC residual */
                sig_Q10_ptr[sig_Q10_ptr_offset + i] = sig_Q10_ptr[sig_Q10_ptr_offset + i] + LPC_pred_Q10

                /* Update states */
                psDec.sLPC_Q14[Define.MAX_LPC_ORDER + i] = sig_Q10_ptr[sig_Q10_ptr_offset + i] shl 4
                i++
            }
            sig_Q10_ptr_offset += psDec.subfr_length
            /* Update LPC filter state */
            System.arraycopy(psDec.sLPC_Q14, psDec.subfr_length, psDec.sLPC_Q14, 0, Define.MAX_LPC_ORDER)
            k++
        }

        /* Scale with Gain */i = 0
        while (i < psDec.frame_length) {
            signal[signal_offset + i] = SigProcFIX.SKP_SAT16(SigProcFIX.SKP_RSHIFT_ROUND(
                    Macros.SKP_SMULWW(sig_Q10[i], psPLC.prevGain_Q16[Define.NB_SUBFR - 1]), 10)).toShort()
            i++
        }
        /** */
        /* Update states */
        /** */
        psPLC.rand_seed = rand_seed
        psPLC.randScale_Q14 = rand_scale_Q14
        i = 0
        while (i < Define.NB_SUBFR) {
            psDecCtrl.pitchL[i] = lag
            i++
        }
    }

    /**
     * Glues concealed frames with new good recieved frames.
     *
     * @param psDec
     * Decoder state.
     * @param psDecCtrl
     * Decoder control.
     * @param signal
     * signal.
     * @param signal_offset
     * offset of the valid data.
     * @param length
     * length of the residual.
     */
    fun SKP_Silk_PLC_glue_frames(psDec: SKP_Silk_decoder_state?,  /* I/O decoder state */
            psDecCtrl: SKP_Silk_decoder_control?,  /* I/O Decoder control */
            signal: ShortArray,  /* I/O signal */
            signal_offset: Int1, length: Int1 /* I length of residual */
    ) {
        var i: Int1
        val energy_shift: Int1
        var energy: Int1
        val psPLC: SKP_Silk_PLC_struct?
        psPLC = psDec!!.sPLC
        if (psDec.lossCnt != 0) {
            /* Calculate energy in concealed residual */
            val energy_ptr = IntArray(1)
            val energy_shift_ptr = IntArray(1)
            SumSqrShift.SKP_Silk_sum_sqr_shift(energy_ptr, energy_shift_ptr, signal, signal_offset,
                    length)
            psPLC.conc_energy = energy_ptr[0]
            psPLC.conc_energy_shift = energy_shift_ptr[0]
            psPLC.last_frame_lost = 1
        } else {
            if (psDec.sPLC.last_frame_lost != 0) {
                val energy_ptr = IntArray(1)
                val energy_shift_ptr = IntArray(1)

                /* Calculate residual in decoded signal if last frame was lost */
                SumSqrShift.SKP_Silk_sum_sqr_shift(energy_ptr, energy_shift_ptr, signal, signal_offset, length)
                energy = energy_ptr[0]
                energy_shift = energy_shift_ptr[0]

                /* Normalize energies */
                if (energy_shift > psPLC.conc_energy_shift) {
                    psPLC.conc_energy = psPLC.conc_energy shr energy_shift - psPLC.conc_energy_shift
                } else if (energy_shift < psPLC.conc_energy_shift) {
                    energy = energy shr psPLC.conc_energy_shift - energy_shift
                }

                /* Fade in the energy difference */
                if (energy > psPLC.conc_energy) {
                    var gain_Q12: Int1
                    var LZ = Macros.SKP_Silk_CLZ32(psPLC.conc_energy)
                    LZ -= 1
                    psPLC.conc_energy = psPLC.conc_energy shl LZ
                    energy = energy shr max(24 - LZ, 0)
                    val frac_Q24 = psPLC.conc_energy / Math.max(energy, 1)
                    gain_Q12 = Inlines.SKP_Silk_SQRT_APPROX(frac_Q24)
                    val slope_Q12 = ((1 shl 12) - gain_Q12) / length
                    i = 0
                    while (i < length) {
                        signal[signal_offset + i] = (gain_Q12 * signal[signal_offset + i] shr 12).toShort()
                        gain_Q12 += slope_Q12
                        gain_Q12 = Math.min(gain_Q12, 1 shl 12)
                        i++
                    }
                }
            }
            psPLC.last_frame_lost = 0
        }
    }
}