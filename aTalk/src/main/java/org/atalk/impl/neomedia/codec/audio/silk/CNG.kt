/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

import java.util.*

/**
 * SILK CNG.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object CNG {
    /**
     * Generates excitation for CNG LPC synthesis.
     *
     * @param residual
     * CNG residual signal Q0.
     * @param residual_offset
     * offset of the valid data.
     * @param exc_buf_Q10
     * Random samples buffer Q10.
     * @param exc_buf_Q10_offset
     * offset of the valid data.
     * @param Gain_Q16
     * Gain to apply
     * @param length
     * Length
     * @param rand_seed
     * Seed to random index generator
     */
    fun SKP_Silk_CNG_exc(residual: ShortArray,  /* O CNG residual signal Q0 */
            residual_offset: Int, exc_buf_Q10: IntArray?,  /* I Random samples buffer Q10 */
            exc_buf_Q10_offset: Int, Gain_Q16: Int,  /* I Gain to apply */
            length: Int,  /* I Length */
            rand_seed: IntArray /* I/O Seed to random index generator */
    ) {
        var seed: Int
        var i: Int
        var idx: Int
        var exc_mask: Int
        exc_mask = Define.CNG_BUF_MASK_MAX
        while (exc_mask > length) {
            exc_mask = exc_mask shr 1
        }
        seed = rand_seed[0]
        i = 0
        while (i < length) {
            seed = SigProcFIX.SKP_RAND(seed)
            idx = seed shr 24 and exc_mask
            Typedef.SKP_assert(idx >= 0)
            Typedef.SKP_assert(idx <= Define.CNG_BUF_MASK_MAX)
            residual[residual_offset + i] = SigProcFIX.SKP_SAT16(SigProcFIX.SKP_RSHIFT_ROUND(Macros.SKP_SMULWW(exc_buf_Q10!![idx], Gain_Q16), 10)).toShort()
            i++
        }
        rand_seed[0] = seed
    }

    /**
     * Reset CNG.
     *
     * @param psDec
     * Decoder state.
     */
    fun SKP_Silk_CNG_Reset(psDec: SKP_Silk_decoder_state? /* I/O Decoder state */
    ) {
        var i: Int
        val NLSF_step_Q15: Int
        var NLSF_acc_Q15: Int
        NLSF_step_Q15 = Typedef.SKP_int16_MAX / (psDec!!.LPC_order + 1)
        NLSF_acc_Q15 = 0
        i = 0
        while (i < psDec.LPC_order) {
            NLSF_acc_Q15 += NLSF_step_Q15
            psDec.sCNG.CNG_smth_NLSF_Q15[i] = NLSF_acc_Q15
            i++
        }
        psDec.sCNG.CNG_smth_Gain_Q16 = 0
        psDec.sCNG.rand_seed = 3176576
    }

    /**
     * Updates CNG estimate, and applies the CNG when packet was lost.
     *
     * @param psDec
     * Decoder state.
     * @param psDecCtrl
     * Decoder control.
     * @param signal
     * Signal.
     * @param signal_offset
     * offset of the valid data.
     * @param length
     * Length of residual.
     */
    fun SKP_Silk_CNG(psDec: SKP_Silk_decoder_state?,  /* I/O Decoder state */
            psDecCtrl: SKP_Silk_decoder_control,  /* I/O Decoder control */
            signal: ShortArray,  /* I/O Signal */
            signal_offset: Int, length: Int /* I Length of residual */
    ) {
        var i: Int
        var subfr: Int
        var tmp_32: Int
        val Gain_Q26: Int
        var max_Gain_Q16: Int
        val LPC_buf = ShortArray(Define.MAX_LPC_ORDER)
        val CNG_sig = ShortArray(Define.MAX_FRAME_LENGTH)
        val psCNG: SKP_Silk_CNG_struct?
        psCNG = psDec!!.sCNG
        if (psDec.fs_kHz != psCNG.fs_kHz) {
            /* Reset state */
            SKP_Silk_CNG_Reset(psDec)
            psCNG.fs_kHz = psDec.fs_kHz
        }
        if (psDec.lossCnt == 0 && psDec.vadFlag == Define.NO_VOICE_ACTIVITY) {
            /* Update CNG parameters */

            /* Smoothing of LSF's */
            i = 0
            while (i < psDec.LPC_order) {
                psCNG.CNG_smth_NLSF_Q15[i] += Macros.SKP_SMULWB(psDec.prevNLSF_Q15[i]
                        - psCNG.CNG_smth_NLSF_Q15[i], Define.CNG_NLSF_SMTH_Q16)
                i++
            }
            /* Find the subframe with the highest gain */
            max_Gain_Q16 = 0
            subfr = 0
            i = 0
            while (i < Define.NB_SUBFR) {
                if (psDecCtrl.Gains_Q16[i] > max_Gain_Q16) {
                    max_Gain_Q16 = psDecCtrl.Gains_Q16[i]
                    subfr = i
                }
                i++
            }
            /* Update CNG excitation buffer with excitation from this subframe */
            System.arraycopy(psCNG.CNG_exc_buf_Q10, 0, psCNG.CNG_exc_buf_Q10, psDec.subfr_length,
                    (Define.NB_SUBFR - 1) * psDec.subfr_length)
            System.arraycopy(psDec.exc_Q10, subfr * psDec.subfr_length, psCNG.CNG_exc_buf_Q10, 0,
                    psDec.subfr_length)
            /* Smooth gains */
            i = 0
            while (i < Define.NB_SUBFR) {
                psCNG.CNG_smth_Gain_Q16 += Macros.SKP_SMULWB(psDecCtrl.Gains_Q16[i]
                        - psCNG.CNG_smth_Gain_Q16, Define.CNG_GAIN_SMTH_Q16)
                i++
            }
        }

        /* Add CNG when packet is lost and / or when low speech activity */
        if (psDec.lossCnt != 0) { // || psDec.vadFlag == NO_VOICE_ACTIVITY ) {

            /* Generate CNG excitation */
            val psCNG_rand_seed_ptr = IntArray(1)
            psCNG_rand_seed_ptr[0] = psCNG.rand_seed
            SKP_Silk_CNG_exc(CNG_sig, 0, psCNG.CNG_exc_buf_Q10, 0, psCNG.CNG_smth_Gain_Q16, length,
                    psCNG_rand_seed_ptr)
            psCNG.rand_seed = psCNG_rand_seed_ptr[0]

            /* Convert CNG NLSF to filter representation */
            NLSF2AStable.SKP_Silk_NLSF2A_stable(LPC_buf, psCNG.CNG_smth_NLSF_Q15, psDec.LPC_order)
            Gain_Q26 = 1 shl 26 /* 1.0 */

            /* Generate CNG signal, by synthesis filtering */
            if (psDec.LPC_order == 16) {
                LPCSynthesisOrder16.SKP_Silk_LPC_synthesis_order16(CNG_sig, LPC_buf, Gain_Q26,
                        psCNG.CNG_synth_state, CNG_sig, length)
            } else {
                LPCSynthesisFilter.SKP_Silk_LPC_synthesis_filter(CNG_sig, LPC_buf, Gain_Q26,
                        psCNG.CNG_synth_state, CNG_sig, length, psDec.LPC_order)
            }
            /* Mix with signal */
            i = 0
            while (i < length) {
                tmp_32 = signal[signal_offset + i] + CNG_sig[i]
                signal[signal_offset + i] = SigProcFIX.SKP_SAT16(tmp_32).toShort()
                i++
            }
        } else {
            Arrays.fill(psCNG.CNG_synth_state, 0, psDec.LPC_order, 0)
        }
    }
}