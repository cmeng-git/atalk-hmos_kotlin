/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * NLSF vector decoder.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object NLSFMSVQDecode {
    /**
     * NLSF vector decoder.
     *
     * @param pNLSF_Q15
     * decoded output vector [LPC_ORDER x 1].
     * @param psNLSF_CB
     * NLSF codebook struct.
     * @param NLSFIndices
     * NLSF indices [nStages x 1].
     * @param LPC_order
     * LPC order used.
     */
    fun SKP_Silk_NLSF_MSVQ_decode(pNLSF_Q15: IntArray,
            /*
             * O Pointer to decoded output vector[LPC_ORDER x 1]
             */
            psNLSF_CB: SKP_Silk_NLSF_CB_struct?,  /* I Pointer to NLSF codebook struct */
            NLSFIndices: IntArray,  /* I Pointer to NLSF indices [nStages x 1] */
            LPC_order: Int /* I LPC order used */
    ) {
        var pCB_element: ShortArray?
        var pCB_element_offset: Int
        var s: Int
        var i: Int

        /* Check that each index is within valid range */
        Typedef.SKP_assert(0 <= NLSFIndices[0] && NLSFIndices[0] < psNLSF_CB!!.CBStages[0]!!.nVectors)

        /* Point to the first vector element */
        pCB_element = psNLSF_CB!!.CBStages[0]!!.CB_NLSF_Q15
        pCB_element_offset = NLSFIndices[0] * LPC_order

        /* Initialize with the codebook vector from stage 0 */
        i = 0
        while (i < LPC_order) {
            pNLSF_Q15[i] = pCB_element[pCB_element_offset + i].toInt()
            i++
        }
        s = 1
        while (s < psNLSF_CB.nStages) {

            /* Check that each index is within valid range */
            Typedef.SKP_assert(0 <= NLSFIndices[s] && NLSFIndices[s] < psNLSF_CB.CBStages[s]!!.nVectors)
            if (LPC_order == 16) {
                /* Point to the first vector element */
                pCB_element = psNLSF_CB.CBStages[s]!!.CB_NLSF_Q15
                pCB_element_offset = NLSFIndices[s] shl 4

                /* Add the codebook vector from the current stage */
                pNLSF_Q15[0] += pCB_element[pCB_element_offset + 0].toInt()
                pNLSF_Q15[1] += pCB_element[pCB_element_offset + 1].toInt()
                pNLSF_Q15[2] += pCB_element[pCB_element_offset + 2].toInt()
                pNLSF_Q15[3] += pCB_element[pCB_element_offset + 3].toInt()
                pNLSF_Q15[4] += pCB_element[pCB_element_offset + 4].toInt()
                pNLSF_Q15[5] += pCB_element[pCB_element_offset + 5].toInt()
                pNLSF_Q15[6] += pCB_element[pCB_element_offset + 6].toInt()
                pNLSF_Q15[7] += pCB_element[pCB_element_offset + 7].toInt()
                pNLSF_Q15[8] += pCB_element[pCB_element_offset + 8].toInt()
                pNLSF_Q15[9] += pCB_element[pCB_element_offset + 9].toInt()
                pNLSF_Q15[10] += pCB_element[pCB_element_offset + 10].toInt()
                pNLSF_Q15[11] += pCB_element[pCB_element_offset + 11].toInt()
                pNLSF_Q15[12] += pCB_element[pCB_element_offset + 12].toInt()
                pNLSF_Q15[13] += pCB_element[pCB_element_offset + 13].toInt()
                pNLSF_Q15[14] += pCB_element[pCB_element_offset + 14].toInt()
                pNLSF_Q15[15] += pCB_element[pCB_element_offset + 15].toInt()
            } else {
                /* Point to the first vector element */
                pCB_element = psNLSF_CB.CBStages[s]!!.CB_NLSF_Q15
                pCB_element_offset = Macros.SKP_SMULBB(NLSFIndices[s], LPC_order)

                /* Add the codebook vector from the current stage */
                i = 0
                while (i < LPC_order) {
                    pNLSF_Q15[i] += pCB_element[pCB_element_offset + i].toInt()
                    i++
                }
            }
            s++
        }

        /* NLSF stabilization */
        NLSFStabilize.SKP_Silk_NLSF_stabilize(pNLSF_Q15, 0, psNLSF_CB.NDeltaMin_Q15, LPC_order)
    }
}