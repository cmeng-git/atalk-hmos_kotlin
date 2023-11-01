/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * compute weighted quantization errors for LPC_order element input vectors, over one codebook
 * stage.
 *
 * @author Dingxin Xu
 */
object NLSFVQSumErrorFLP {
    /**
     * compute weighted quantization errors for LPC_order element input vectors, over one codebook
     * stage.
     *
     * @param err
     * Weighted quantization errors [ N * K ]
     * @param in
     * Input vectors [ N * LPC_order ]
     * @param w
     * Weighting vectors [ N * LPC_order ]
     * @param pCB
     * Codebook vectors [ K * LPC_order ]
     * @param N
     * Number of input vectors
     * @param K
     * Number of codebook vectors
     * @param LPC_order
     * pCB
     */
    fun SKP_Silk_NLSF_VQ_sum_error_FLP(err: FloatArray,  /*
															 * O Weighted quantization errors [ N *
															 * K ]
															 */
            `in`: FloatArray,  /* I Input vectors [ N * LPC_order ] */
            w: FloatArray?,  /* I Weighting vectors [ N * LPC_order ] */
            pCB: FloatArray?,  /* I Codebook vectors [ K * LPC_order ] */
            N: Int,  /* I Number of input vectors */
            K: Int,  /* I Number of codebook vectors */
            LPC_order: Int /* I LPC order */
    ) {
        var i: Int
        var n: Int
        var diff: Float
        var sum_error: Float
        val Wcpy = FloatArray(Define.MAX_LPC_ORDER)
        var cb_vec: FloatArray?
        var cb_vec_offset: Int

        /* Copy to local stack */
        System.arraycopy(w, 0, Wcpy, 0, LPC_order)

        // TODO:
        var err_tmp_offset = 0
        var in_tmp_offset = 0
        if (LPC_order == 16) {
            /* Loop over input vectors */
            n = 0
            while (n < N) {

                /* Loop over codebook */
                cb_vec = pCB
                cb_vec_offset = 0
                i = 0
                while (i < K) {

                    /* Compute weighted squared quantization error */
                    diff = `in`[in_tmp_offset + 0] - cb_vec!![cb_vec_offset + 0]
                    sum_error = Wcpy[0] * diff * diff
                    diff = `in`[in_tmp_offset + 1] - cb_vec[cb_vec_offset + 1]
                    sum_error += Wcpy[1] * diff * diff
                    diff = `in`[in_tmp_offset + 2] - cb_vec[cb_vec_offset + 2]
                    sum_error += Wcpy[2] * diff * diff
                    diff = `in`[in_tmp_offset + 3] - cb_vec[cb_vec_offset + 3]
                    sum_error += Wcpy[3] * diff * diff
                    diff = `in`[in_tmp_offset + 4] - cb_vec[cb_vec_offset + 4]
                    sum_error += Wcpy[4] * diff * diff
                    diff = `in`[in_tmp_offset + 5] - cb_vec[cb_vec_offset + 5]
                    sum_error += Wcpy[5] * diff * diff
                    diff = `in`[in_tmp_offset + 6] - cb_vec[cb_vec_offset + 6]
                    sum_error += Wcpy[6] * diff * diff
                    diff = `in`[in_tmp_offset + 7] - cb_vec[cb_vec_offset + 7]
                    sum_error += Wcpy[7] * diff * diff
                    diff = `in`[in_tmp_offset + 8] - cb_vec[cb_vec_offset + 8]
                    sum_error += Wcpy[8] * diff * diff
                    diff = `in`[in_tmp_offset + 9] - cb_vec[cb_vec_offset + 9]
                    sum_error += Wcpy[9] * diff * diff
                    diff = `in`[in_tmp_offset + 10] - cb_vec[cb_vec_offset + 10]
                    sum_error += Wcpy[10] * diff * diff
                    diff = `in`[in_tmp_offset + 11] - cb_vec[cb_vec_offset + 11]
                    sum_error += Wcpy[11] * diff * diff
                    diff = `in`[in_tmp_offset + 12] - cb_vec[cb_vec_offset + 12]
                    sum_error += Wcpy[12] * diff * diff
                    diff = `in`[in_tmp_offset + 13] - cb_vec[cb_vec_offset + 13]
                    sum_error += Wcpy[13] * diff * diff
                    diff = `in`[in_tmp_offset + 14] - cb_vec[cb_vec_offset + 14]
                    sum_error += Wcpy[14] * diff * diff
                    diff = `in`[in_tmp_offset + 15] - cb_vec[cb_vec_offset + 15]
                    sum_error += Wcpy[15] * diff * diff
                    err[err_tmp_offset + i] = sum_error
                    cb_vec_offset += 16
                    i++
                }
                err_tmp_offset += K
                in_tmp_offset += 16
                n++
            }
        } else {
            assert(LPC_order == 10)

            /* Loop over input vectors */
            n = 0
            while (n < N) {

                /* Loop over codebook */
                cb_vec = pCB
                cb_vec_offset = 0
                i = 0
                while (i < K) {

                    /* Compute weighted squared quantization error */
                    diff = `in`[in_tmp_offset + 0] - cb_vec!![cb_vec_offset + 0]
                    sum_error = Wcpy[0] * diff * diff
                    diff = `in`[in_tmp_offset + 1] - cb_vec[cb_vec_offset + 1]
                    sum_error += Wcpy[1] * diff * diff
                    diff = `in`[in_tmp_offset + 2] - cb_vec[cb_vec_offset + 2]
                    sum_error += Wcpy[2] * diff * diff
                    diff = `in`[in_tmp_offset + 3] - cb_vec[cb_vec_offset + 3]
                    sum_error += Wcpy[3] * diff * diff
                    diff = `in`[in_tmp_offset + 4] - cb_vec[cb_vec_offset + 4]
                    sum_error += Wcpy[4] * diff * diff
                    diff = `in`[in_tmp_offset + 5] - cb_vec[cb_vec_offset + 5]
                    sum_error += Wcpy[5] * diff * diff
                    diff = `in`[in_tmp_offset + 6] - cb_vec[cb_vec_offset + 6]
                    sum_error += Wcpy[6] * diff * diff
                    diff = `in`[in_tmp_offset + 7] - cb_vec[cb_vec_offset + 7]
                    sum_error += Wcpy[7] * diff * diff
                    diff = `in`[in_tmp_offset + 8] - cb_vec[cb_vec_offset + 8]
                    sum_error += Wcpy[8] * diff * diff
                    diff = `in`[in_tmp_offset + 9] - cb_vec[cb_vec_offset + 9]
                    sum_error += Wcpy[9] * diff * diff
                    err[err_tmp_offset + i] = sum_error
                    cb_vec_offset += 10
                    i++
                }
                err_tmp_offset += K
                in_tmp_offset += 10
                n++
            }
        }
    }
}