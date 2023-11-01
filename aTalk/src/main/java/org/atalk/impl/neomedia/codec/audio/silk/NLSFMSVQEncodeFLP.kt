/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

import java.util.*
import kotlin.math.min

/**
 * NLSF vector encoder.
 *
 * @author Dingxin Xu
 */
object NLSFMSVQEncodeFLP {
    /**
     * NLSF vector encoder.
     *
     * @param NLSFIndices
     * Codebook path vector [ CB_STAGES ]
     * @param pNLSF
     * Quantized NLSF vector [ LPC_ORDER ]
     * @param psNLSF_CB_FLP
     * Codebook object
     * @param pNLSF_q_prev
     * Prev. quantized NLSF vector [LPC_ORDER]
     * @param pW
     * NLSF weight vector [ LPC_ORDER ]
     * @param NLSF_mu
     * Rate weight for the RD optimization
     * @param NLSF_mu_fluc_red
     * Fluctuation reduction error weight
     * @param NLSF_MSVQ_Survivors
     * Max survivors from each stage
     * @param LPC_order
     * LPC order
     * @param deactivate_fluc_red
     * Deactivate fluctuation reduction
     */
    fun SKP_Silk_NLSF_MSVQ_encode_FLP(NLSFIndices: IntArray?,  /*
																 * O Codebook path vector [
																 * CB_STAGES ]
																 */
            pNLSF: FloatArray,  /* I/O Quantized NLSF vector [ LPC_ORDER ] */
            psNLSF_CB_FLP: SKP_Silk_NLSF_CB_FLP?,  /* I Codebook object */
            pNLSF_q_prev: FloatArray?,  /* I Prev. quantized NLSF vector [LPC_ORDER] */
            pW: FloatArray,  /* I NLSF weight vector [ LPC_ORDER ] */
            NLSF_mu: Float,  /* I Rate weight for the RD optimization */
            NLSF_mu_fluc_red: Float,  /* I Fluctuation reduction error weight */
            NLSF_MSVQ_Survivors: Int,  /* I Max survivors from each stage */
            LPC_order: Int,  /* I LPC order */
            deactivate_fluc_red: Int /* I Deactivate fluctuation reduction */
    ) {
        var i: Int
        var s: Int
        var k: Int
        var cur_survivors: Int
        var prev_survivors: Int
        var input_index: Int
        var cb_index: Int
        var bestIndex: Int
        var se: Float
        var wsse: Float
        var rateDistThreshold: Float
        var bestRateDist: Float
        val pNLSF_in = FloatArray(Define.MAX_LPC_ORDER)
        val pRateDist: FloatArray
        val pRate: FloatArray
        val pRate_new: FloatArray
        val pTempIndices: IntArray
        val pPath: IntArray
        val pPath_new: IntArray
        val pRes: FloatArray
        val pRes_new: FloatArray
        if (Define.LOW_COMPLEXITY_ONLY) {
            pRateDist = FloatArray(Define.NLSF_MSVQ_TREE_SEARCH_MAX_VECTORS_EVALUATED_LC_MODE())
            pRate = FloatArray(Define.MAX_NLSF_MSVQ_SURVIVORS_LC_MODE)
            pRate_new = FloatArray(Define.MAX_NLSF_MSVQ_SURVIVORS_LC_MODE)
            pTempIndices = IntArray(Define.MAX_NLSF_MSVQ_SURVIVORS_LC_MODE)
            pPath = IntArray(Define.MAX_NLSF_MSVQ_SURVIVORS_LC_MODE * Define.NLSF_MSVQ_MAX_CB_STAGES)
            pPath_new = IntArray(Define.MAX_NLSF_MSVQ_SURVIVORS_LC_MODE * Define.NLSF_MSVQ_MAX_CB_STAGES)
            pRes = FloatArray(Define.MAX_NLSF_MSVQ_SURVIVORS_LC_MODE * Define.MAX_LPC_ORDER)
            pRes_new = FloatArray(Define.MAX_NLSF_MSVQ_SURVIVORS_LC_MODE * Define.MAX_LPC_ORDER)
        } else {
            pRateDist = FloatArray(Define.NLSF_MSVQ_TREE_SEARCH_MAX_VECTORS_EVALUATED())
            pRate = FloatArray(Define.MAX_NLSF_MSVQ_SURVIVORS)
            pRate_new = FloatArray(Define.MAX_NLSF_MSVQ_SURVIVORS)
            pTempIndices = IntArray(Define.MAX_NLSF_MSVQ_SURVIVORS)
            pPath = IntArray(Define.MAX_NLSF_MSVQ_SURVIVORS * Define.NLSF_MSVQ_MAX_CB_STAGES)
            pPath_new = IntArray(Define.MAX_NLSF_MSVQ_SURVIVORS * Define.NLSF_MSVQ_MAX_CB_STAGES)
            pRes = FloatArray(Define.MAX_NLSF_MSVQ_SURVIVORS * Define.MAX_LPC_ORDER)
            pRes_new = FloatArray(Define.MAX_NLSF_MSVQ_SURVIVORS * Define.MAX_LPC_ORDER)
        }
        var pConstFloat: FloatArray
        var pConstFloat_offset: Int
        var pFloat: FloatArray
        var pFloat_offset: Int
        var pConstInt: IntArray
        var pConstInt_offset: Int
        var pInt: IntArray
        var pInt_offset: Int
        var pCB_element: FloatArray
        var pCB_element_offset: Int
        var pCurrentCBStage: SKP_Silk_NLSF_CBS_FLP?
        assert(NLSF_MSVQ_Survivors <= Define.MAX_NLSF_MSVQ_SURVIVORS)
        assert(!Define.LOW_COMPLEXITY_ONLY || NLSF_MSVQ_Survivors <= Define.MAX_NLSF_MSVQ_SURVIVORS_LC_MODE)
        cur_survivors = NLSF_MSVQ_Survivors

        /* Copy the input vector */
        System.arraycopy(pNLSF, 0, pNLSF_in, 0, LPC_order)
        /** */
        /* Tree search for the multi-stage vector quantizer */
        /** */

        /* Clear accumulated rates */
        Arrays.fill(pRate, 0, NLSF_MSVQ_Survivors, 0f)

        /* Copy NLSFs into residual signal vector */
        System.arraycopy(pNLSF, 0, pRes, 0, LPC_order)

        /* Set first stage values */
        prev_survivors = 1

        /* Loop over all stages */
        s = 0
        while (s < psNLSF_CB_FLP!!.nStages) {


            /* Set a pointer to the current stage codebook */
            pCurrentCBStage = psNLSF_CB_FLP.CBStages[s]

            /* Calculate the number of survivors in the current stage */
            cur_survivors = min(NLSF_MSVQ_Survivors, prev_survivors * pCurrentCBStage!!.nVectors)
            if (!Define.NLSF_MSVQ_FLUCTUATION_REDUCTION) {
                /* Find a single best survivor in the last stage, if we */
                /* do not need candidates for fluctuation reduction */
                if (s == psNLSF_CB_FLP.nStages - 1) {
                    cur_survivors = 1
                }
            }
            /* Nearest neighbor clustering for multiple input data vectors */
            NLSFVQRateDistortionFLP.SKP_Silk_NLSF_VQ_rate_distortion_FLP(pRateDist,
                    pCurrentCBStage, pRes, pW, pRate, NLSF_mu, prev_survivors, LPC_order)

            /* Sort the rate-distortion errors */
            SortFLP.SKP_Silk_insertion_sort_increasing_FLP(pRateDist, 0, pTempIndices,
                    prev_survivors * pCurrentCBStage.nVectors, cur_survivors)

            /* Discard survivors with rate-distortion values too far above the best one */
            rateDistThreshold = Define.NLSF_MSVQ_SURV_MAX_REL_RD * pRateDist[0]
            while (pRateDist[cur_survivors - 1] > rateDistThreshold && cur_survivors > 1) {
                cur_survivors--
            }

            /*
			 * Update accumulated codebook contributions for the 'cur_survivors' best codebook
			 * indices
			 */
            k = 0
            while (k < cur_survivors) {
                if (s > 0) {
                    /* Find the indices of the input and the codebook vector */
                    if (pCurrentCBStage.nVectors == 8) {
                        input_index = pTempIndices[k] shr 3
                        cb_index = pTempIndices[k] and 7
                    } else {
                        input_index = pTempIndices[k] / pCurrentCBStage.nVectors
                        cb_index = pTempIndices[k] - input_index * pCurrentCBStage.nVectors
                    }
                } else {
                    /* Find the indices of the input and the codebook vector */
                    input_index = 0
                    cb_index = pTempIndices[k]
                }

                /*
				 * Subtract new contribution from the previous residual vector for each of
				 * 'cur_survivors'
				 */
                pConstFloat = pRes
                pConstFloat_offset = input_index * LPC_order
                pCB_element = pCurrentCBStage.CB
                pCB_element_offset = cb_index * LPC_order
                pFloat = pRes_new
                pFloat_offset = k * LPC_order
                i = 0
                while (i < LPC_order) {
                    pFloat[pFloat_offset + i] = (pConstFloat[pConstFloat_offset + i]
                            - pCB_element[pCB_element_offset + i])
                    i++
                }

                /* Update accumulated rate for stage 1 to the current */
                pRate_new[k] = pRate[input_index] + pCurrentCBStage.Rates[cb_index]

                /* Copy paths from previous matrix, starting with the best path */
                pConstInt = pPath
                pConstInt_offset = input_index * psNLSF_CB_FLP.nStages
                pInt = pPath_new
                pInt_offset = k * psNLSF_CB_FLP.nStages
                i = 0
                while (i < s) {
                    pInt[pInt_offset + i] = pConstInt[pConstInt_offset + i]
                    i++
                }
                /* Write the current stage indices for the 'cur_survivors' to the best path matrix */
                pInt[pInt_offset + s] = cb_index
                k++
            }
            if (s < psNLSF_CB_FLP.nStages - 1) {
                /* Copy NLSF residual matrix for next stage */
                System.arraycopy(pRes_new, 0, pRes, 0, cur_survivors * LPC_order)

                /* Copy rate vector for next stage */
                System.arraycopy(pRate_new, 0, pRate, 0, cur_survivors)

                /* Copy best path matrix for next stage */
                System.arraycopy(pPath_new, 0, pPath, 0, cur_survivors * psNLSF_CB_FLP.nStages)
            }
            prev_survivors = cur_survivors
            s++
        }

        /* (Preliminary) index of the best survivor, later to be decoded */bestIndex = 0
        if (Define.NLSF_MSVQ_FLUCTUATION_REDUCTION) {
            /** */
            /* NLSF fluctuation reduction */
            /** */
            if (deactivate_fluc_red != 1) {

                /*
				 * Search among all survivors, now taking also weighted fluctuation errors into account
				 */
                bestRateDist = Float.MAX_VALUE
                s = 0
                while (s < cur_survivors) {

                    /* Decode survivor to compare with previous quantized NLSF vector */
                    NLSFMSVQDecodeFLP.SKP_Silk_NLSF_MSVQ_decode_FLP(pNLSF, psNLSF_CB_FLP,
                            pPath_new, s * psNLSF_CB_FLP.nStages, LPC_order)

                    /* Compare decoded NLSF vector with the previously quantized vector */
                    wsse = 0f
                    i = 0
                    while (i < LPC_order) {

                        /* Compute weighted squared quantization error for index i */
                        se = pNLSF[i] - pNLSF_q_prev!![i]
                        wsse += pW[i] * se * se

                        /* Compute weighted squared quantization error for index i + 1 */
                        se = pNLSF[i + 1] - pNLSF_q_prev[i + 1]
                        wsse += pW[i + 1] * se * se
                        i += 2
                    }

                    /* Add the fluctuation reduction penalty to the rate distortion error */
                    wsse = pRateDist[s] + wsse * NLSF_mu_fluc_red

                    /* Keep index of best survivor */
                    if (wsse < bestRateDist) {
                        bestRateDist = wsse
                        bestIndex = s
                    }
                    s++
                }
            }
        }

        /* Copy best path to output argument */
        System.arraycopy(pPath_new, bestIndex * psNLSF_CB_FLP.nStages, NLSFIndices, 0,
                psNLSF_CB_FLP.nStages)

        /* Decode and stabilize the best survivor */
        NLSFMSVQDecodeFLP.SKP_Silk_NLSF_MSVQ_decode_FLP(pNLSF, psNLSF_CB_FLP, NLSFIndices, 0,
                LPC_order)
    }
}