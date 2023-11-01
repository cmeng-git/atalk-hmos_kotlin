/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * NLSF vector decoder.
 *
 * @author Dingxin Xu
 */
object NLSFMSVQDecodeFLP {
    /**
     * NLSF vector decoder.
     *
     * @param pNLSF
     * @param psNLSF_CB_FLP
     * @param NLSFIndices
     * @param NLSFIndices_offset
     * @param LPC_order
     */
    fun SKP_Silk_NLSF_MSVQ_decode_FLP(pNLSF: FloatArray,  /* O Decoded output vector [ LPC_ORDER ] */
            psNLSF_CB_FLP: SKP_Silk_NLSF_CB_FLP?,  /* I NLSF codebook struct */
            NLSFIndices: IntArray?,  /* I NLSF indices [ nStages ] */
            NLSFIndices_offset: Int, LPC_order: Int /* I LPC order used */
    ) {
        var pCB_element: FloatArray?
        var pCB_element_offset: Int
        var s: Int
        var i: Int
        assert(0 <= NLSFIndices!![NLSFIndices_offset + 0] && NLSFIndices[NLSFIndices_offset + 0] < psNLSF_CB_FLP!!.CBStages[0]!!.nVectors)

        /* Point to the first vector element */
        pCB_element = psNLSF_CB_FLP!!.CBStages[0]!!.CB
        pCB_element_offset = NLSFIndices[NLSFIndices_offset + 0] * LPC_order

        /* Initialize with the codebook vector from stage 0 */
        System.arraycopy(pCB_element, pCB_element_offset, pNLSF, 0, LPC_order)
        s = 1
        while (s < psNLSF_CB_FLP.nStages) {
            assert(0 <= NLSFIndices[NLSFIndices_offset + s] && NLSFIndices[NLSFIndices_offset + s] < psNLSF_CB_FLP.CBStages[s]!!.nVectors)
            if (LPC_order == 16) {
                /* Point to the first vector element */
                pCB_element = psNLSF_CB_FLP.CBStages[s]!!.CB
                pCB_element_offset = NLSFIndices[NLSFIndices_offset + s] shl 4

                /* Add the codebook vector from the current stage */
                pNLSF[0] += pCB_element[pCB_element_offset + 0]
                pNLSF[1] += pCB_element[pCB_element_offset + 1]
                pNLSF[2] += pCB_element[pCB_element_offset + 2]
                pNLSF[3] += pCB_element[pCB_element_offset + 3]
                pNLSF[4] += pCB_element[pCB_element_offset + 4]
                pNLSF[5] += pCB_element[pCB_element_offset + 5]
                pNLSF[6] += pCB_element[pCB_element_offset + 6]
                pNLSF[7] += pCB_element[pCB_element_offset + 7]
                pNLSF[8] += pCB_element[pCB_element_offset + 8]
                pNLSF[9] += pCB_element[pCB_element_offset + 9]
                pNLSF[10] += pCB_element[pCB_element_offset + 10]
                pNLSF[11] += pCB_element[pCB_element_offset + 11]
                pNLSF[12] += pCB_element[pCB_element_offset + 12]
                pNLSF[13] += pCB_element[pCB_element_offset + 13]
                pNLSF[14] += pCB_element[pCB_element_offset + 14]
                pNLSF[15] += pCB_element[pCB_element_offset + 15]
            } else {
                /* Point to the first vector element */
                pCB_element = psNLSF_CB_FLP.CBStages[s]!!.CB
                pCB_element_offset = NLSFIndices[NLSFIndices_offset + s] * LPC_order

                /* Add the codebook vector from the current stage */
                i = 0
                while (i < LPC_order) {
                    pNLSF[i] += pCB_element[pCB_element_offset + i]
                    i++
                }
            }
            s++
        }

        /* NLSF stabilization */
        WrappersFLP.SKP_Silk_NLSF_stabilize_FLP(pNLSF, psNLSF_CB_FLP.NDeltaMin, LPC_order)
    }
}