/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 *
 * @author Dingxin Xu
 */
object VQNearestNeighborFLP {
    /**
     * entropy constrained MATRIX-weighted VQ, for a single input data vector.
     *
     * @param ind
     * Index of best codebook vector
     * @param rate_dist
     * Best weighted quant. error + mu * rate
     * @param in
     * Input vector to be quantized
     * @param W
     * Weighting matrix
     * @param cb
     * Codebook
     * @param cl_Q6
     * Code length for each codebook vector
     * @param mu
     * Tradeoff between WSSE and rate
     * @param L
     * Number of vectors in codebook
     */
    fun SKP_Silk_VQ_WMat_EC_FLP(ind: IntArray,  /* O Index of best codebook vector */
            ind_offset: Int, rate_dist: FloatArray,  /* O Best weighted quant. error + mu * rate */
            `in`: FloatArray?,  /* I Input vector to be quantized */
            in_offset: Int, W: FloatArray,  /* I Weighting matrix */
            W_offset: Int, cb: ShortArray?,  /* I Codebook */
            cl_Q6: ShortArray?,  /* I Code length for each codebook vector */
            mu: Float,  /* I Tradeoff between WSSE and rate */
            L: Int /* I Number of vectors in codebook */
    ) {
        // SKP_int k;
        // SKP_float sum1;
        // SKP_float diff[ 5 ];
        // const SKP_int16 *cb_row;
        var k: Int
        var sum1: Float
        val diff = FloatArray(5)
        val cb_row: ShortArray?
        var cb_row_offset = 0

        /* Loop over codebook */
        // *rate_dist = SKP_float_MAX;
        rate_dist[0] = Float.MAX_VALUE
        cb_row = cb
        cb_row_offset = 0
        k = 0
        while (k < L) {

            /* Calc difference between in vector and cbk vector */
            diff[0] = `in`!![in_offset + 0] - cb_row!![0] * DefineFLP.Q14_CONVERSION_FAC
            diff[1] = `in`[in_offset + 1] - cb_row[1] * DefineFLP.Q14_CONVERSION_FAC
            diff[2] = `in`[in_offset + 2] - cb_row[2] * DefineFLP.Q14_CONVERSION_FAC
            diff[3] = `in`[in_offset + 3] - cb_row[3] * DefineFLP.Q14_CONVERSION_FAC
            diff[4] = `in`[in_offset + 4] - cb_row[4] * DefineFLP.Q14_CONVERSION_FAC

            /* Weighted rate */
            sum1 = mu * cl_Q6!![k] / 64.0f

            /* Add weighted quantization error, assuming W is symmetric */
            /* first row of W */
            sum1 += (diff[0]
                    * (W[W_offset + 0] * diff[0] + 2.0f * (W[W_offset + 1] * diff[1] + (W[W_offset + 2]
                    * diff[2]) + W[W_offset + 3] * diff[3] + W[W_offset + 4] * diff[4])))

            /* second row of W */
            sum1 += (diff[1]
                    * (W[W_offset + 6] * diff[1] + 2.0f * (W[W_offset + 7] * diff[2] + (W[W_offset + 8]
                    * diff[3]) + W[W_offset + 9] * diff[4])))

            /* third row of W */
            sum1 += (diff[2]
                    * (W[W_offset + 12] * diff[2] + 2.0f * (W[W_offset + 13] * diff[3] + W[W_offset + 14]
                    * diff[4])))

            /* fourth row of W */
            sum1 += diff[3] * (W[W_offset + 18] * diff[3] + 2.0f * (W[W_offset + 19] * diff[4]))

            /* last row of W */
            sum1 += diff[4] * (W[W_offset + 24] * diff[4])

            /* find best */
            if (sum1 < rate_dist[0]) {
                rate_dist[0] = sum1
                ind[ind_offset + 0] = k
            }

            /* Go to next cbk vector */
            cb_row_offset += Define.LTP_ORDER
            k++
        }
    }
}