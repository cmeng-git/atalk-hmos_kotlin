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
object PrefilterFLP {
    /**
     * SKP_Silk_prefilter. Main Prefilter Function.
     *
     * @param psEnc
     * Encoder state FLP.
     * @param psEncCtrl
     * Encoder control FLP.
     * @param xw
     * Weighted signal.
     * @param x
     * Speech signal.
     * @param x_offset
     * offset of valid data.
     */
    fun SKP_Silk_prefilter_FLP(psEnc: SKP_Silk_encoder_state_FLP?,  /* I/O Encoder state FLP */
            psEncCtrl: SKP_Silk_encoder_control_FLP,  /* I Encoder control FLP */
            xw: FloatArray,  /* O Weighted signal */
            x: FloatArray?,  /* I Speech signal */
            x_offset: Int) {
        val P = psEnc!!.sPrefilt
        var j: Int
        var k: Int
        var lag: Int
        var HarmShapeGain: Float
        var Tilt: Float
        var LF_MA_shp: Float
        var LF_AR_shp: Float
        val B = FloatArray(2)
        val AR1_shp = FloatArray(Define.NB_SUBFR * Define.SHAPE_LPC_ORDER_MAX)
        val px: FloatArray?
        var px_offset: Int
        val pxw: FloatArray
        var pst_res: FloatArray
        var pxw_offset: Int
        var pst_res_offset: Int
        val HarmShapeFIR = FloatArray(3)
        val st_res = FloatArray(Define.MAX_FRAME_LENGTH / Define.NB_SUBFR + Define.MAX_LPC_ORDER)

        /* Setup pointers */
        px = x
        px_offset = x_offset
        pxw = xw
        pxw_offset = 0
        lag = P.lagPrev
        k = 0
        while (k < Define.NB_SUBFR) {

            /* Update Variables that change per sub frame */
            if (psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_VOICED) {
                lag = psEncCtrl.sCmn.pitchL[k]
            }

            /* Noise shape parameters */
            HarmShapeGain = psEncCtrl.HarmShapeGain[k] * (1.0f - psEncCtrl.HarmBoost[k])
            HarmShapeFIR[0] = TablesOtherFLP.SKP_Silk_HarmShapeFIR_FLP.get(0) * HarmShapeGain
            HarmShapeFIR[1] = TablesOtherFLP.SKP_Silk_HarmShapeFIR_FLP.get(1) * HarmShapeGain
            HarmShapeFIR[2] = TablesOtherFLP.SKP_Silk_HarmShapeFIR_FLP.get(2) * HarmShapeGain
            Tilt = psEncCtrl.Tilt[k]
            LF_MA_shp = psEncCtrl.LF_MA_shp[k]
            LF_AR_shp = psEncCtrl.LF_AR_shp[k]
            // TODO: copy the psEncCtrl.AR1 to a local buffer or use a reference(pointer) to the
            // struct???
            // AR1_shp = psEncCtrl.AR1;
            // AR1_shp_offset = k * SHAPE_LPC_ORDER_MAX;
            Arrays.fill(AR1_shp, 0f)
            System.arraycopy(psEncCtrl.AR1, k * Define.SHAPE_LPC_ORDER_MAX, AR1_shp, 0,
                    psEncCtrl.AR1.size - k * Define.SHAPE_LPC_ORDER_MAX)

            /* Short term FIR filtering */
            LPCAnalysisFilterFLP.SKP_Silk_LPC_analysis_filter_FLP(st_res, AR1_shp, px, px_offset
                    - psEnc.sCmn.shapingLPCOrder, psEnc.sCmn.subfr_length + psEnc.sCmn.shapingLPCOrder,
                    psEnc.sCmn.shapingLPCOrder)
            pst_res = st_res
            pst_res_offset = psEnc.sCmn.shapingLPCOrder // Point to first sample

            /* reduce (mainly) low frequencies during harmonic emphasis */
            B[0] = psEncCtrl.GainsPre[k]
            B[1] = (-psEncCtrl.GainsPre[k]
                    * (psEncCtrl.HarmBoost[k] * HarmShapeGain + PerceptualParametersFLP.INPUT_TILT + (psEncCtrl.coding_quality
                    * PerceptualParametersFLP.HIGH_RATE_INPUT_TILT)))
            pxw[pxw_offset + 0] = B[0] * pst_res[pst_res_offset + 0] + B[1] * P.sHarmHP
            j = 1
            while (j < psEnc.sCmn.subfr_length) {
                pxw[pxw_offset + j] = B[0] * pst_res[pst_res_offset + j] + B[1] * pst_res[pst_res_offset + j - 1]
                j++
            }
            P.sHarmHP = pst_res[pst_res_offset + psEnc.sCmn.subfr_length - 1]
            SKP_Silk_prefilt_FLP(P, pxw, pxw_offset, pxw, pxw_offset, HarmShapeFIR, Tilt,
                    LF_MA_shp, LF_AR_shp, lag, psEnc.sCmn.subfr_length)
            px_offset += psEnc.sCmn.subfr_length
            pxw_offset += psEnc.sCmn.subfr_length
            k++
        }
        P.lagPrev = psEncCtrl.sCmn.pitchL[Define.NB_SUBFR - 1]
    }

    /**
     * SKP_Silk_prefilter_part1. Prefilter for finding Quantizer input signal.
     *
     * @param P
     * @param st_res
     * @param st_res_offset
     * @param xw
     * @param xw_offset
     * @param HarmShapeFIR
     * @param Tilt
     * @param LF_MA_shp
     * @param LF_AR_shp
     * @param lag
     * @param length
     */
    fun SKP_Silk_prefilt_FLP(P: SKP_Silk_prefilter_state_FLP?,  /* (I/O) state */
            st_res: FloatArray,  /* (I) */
            st_res_offset: Int, xw: FloatArray,  /* (O) */
            xw_offset: Int, HarmShapeFIR: FloatArray,  /* (I) */
            Tilt: Float,  /* (I) */
            LF_MA_shp: Float,  /* (I) */
            LF_AR_shp: Float,  /* (I) */
            lag: Int,  /* (I) */
            length: Int /* (I) */
    ) {
        var i: Int
        var idx: Int
        var LTP_shp_buf_idx: Int
        var n_Tilt: Float
        var n_LF: Float
        var n_LTP: Float
        var sLF_AR_shp: Float
        var sLF_MA_shp: Float
        val LTP_shp_buf: FloatArray?

        /* To speed up use temp variables instead of using the struct */
        LTP_shp_buf = P!!.sLTP_shp1
        LTP_shp_buf_idx = P.sLTP_shp_buf_idx1
        sLF_AR_shp = P.sLF_AR_shp1
        sLF_MA_shp = P.sLF_MA_shp1
        i = 0
        while (i < length) {
            if (lag > 0) {
                assert(Define.HARM_SHAPE_FIR_TAPS == 3)
                idx = lag + LTP_shp_buf_idx
                n_LTP = (LTP_shp_buf[idx - Define.HARM_SHAPE_FIR_TAPS / 2 - 1 and Define.LTP_MASK]
                        * HarmShapeFIR[0])
                n_LTP += LTP_shp_buf[idx - Define.HARM_SHAPE_FIR_TAPS / 2 and Define.LTP_MASK] * HarmShapeFIR[1]
                n_LTP += (LTP_shp_buf[idx - Define.HARM_SHAPE_FIR_TAPS / 2 + 1 and Define.LTP_MASK]
                        * HarmShapeFIR[2])
            } else {
                n_LTP = 0f
            }
            n_Tilt = sLF_AR_shp * Tilt
            n_LF = sLF_AR_shp * LF_AR_shp + sLF_MA_shp * LF_MA_shp
            sLF_AR_shp = st_res[st_res_offset + i] - n_Tilt
            sLF_MA_shp = sLF_AR_shp - n_LF
            LTP_shp_buf_idx = LTP_shp_buf_idx - 1 and Define.LTP_MASK
            LTP_shp_buf[LTP_shp_buf_idx] = sLF_MA_shp
            xw[xw_offset + i] = sLF_MA_shp - n_LTP
            i++
        }
        /* Copy temp variable back to state */
        P.sLF_AR_shp1 = sLF_AR_shp
        P.sLF_MA_shp1 = sLF_MA_shp
        P.sLTP_shp_buf_idx1 = LTP_shp_buf_idx
    }
}