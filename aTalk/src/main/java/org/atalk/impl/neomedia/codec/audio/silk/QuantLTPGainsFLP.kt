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
object QuantLTPGainsFLP {
    /**
     *
     * @param B
     * (Un-)quantized LTP gains
     * @param cbk_index
     * Codebook index
     * @param periodicity_index
     * Periodicity index
     * @param W
     * Error weights
     * @param mu
     * Mu value (R/D tradeoff)
     * @param lowComplexity
     * Flag for low complexity
     */
    fun SKP_Silk_quant_LTP_gains_FLP(B: FloatArray?,  /* I/O (Un-)quantized LTP gains */
            cbk_index: IntArray?,  /* O Codebook index */
            periodicity_index: IntArray,  /* O Periodicity index */
            W: FloatArray,  /* I Error weights */
            mu: Float,  /* I Mu value (R/D tradeoff) */
            lowComplexity: Int /* I Flag for low complexity */
    ) {
        // SKP_int j, k, temp_idx[ NB_SUBFR ], cbk_size;
        // const SKP_uint16 *cdf_ptr;
        // const SKP_int16 *cl_ptr;
        // const SKP_int16 *cbk_ptr_Q14;
        // const SKP_float *b_ptr, *W_ptr;
        // SKP_float rate_dist_subfr, rate_dist, min_rate_dist;
        var j: Int
        var k: Int
        var cbk_size: Int
        val temp_idx = IntArray(Define.NB_SUBFR)
        var cdf_ptr: IntArray?
        var cdf_ptr_offset: Int
        var cl_ptr: ShortArray?
        var cl_ptr_offset: Int
        var cbk_ptr_Q14: ShortArray?
        var cbk_ptr_Q14_offset: Int
        var b_ptr: FloatArray?
        var W_ptr: FloatArray
        var b_ptr_offset: Int
        var W_ptr_offset: Int
        var rate_dist_subfr = 0f
        var rate_dist: Float
        var min_rate_dist: Float
        /** */
        /* Iterate over different codebooks with different */
        /* rates/distortions, and choose best */
        /** */
        // min_rate_dist = SKP_float_MAX;
        min_rate_dist = Float.MAX_VALUE
        k = 0
        while (k < 3) {
            cdf_ptr = TablesLTP.SKP_Silk_LTP_gain_CDF_ptrs[k]
            cl_ptr = TablesLTP.SKP_Silk_LTP_gain_BITS_Q6_ptrs[k]
            cbk_ptr_Q14 = TablesLTP.SKP_Silk_LTP_vq_ptrs_Q14[k]
            cbk_size = TablesLTP.SKP_Silk_LTP_vq_sizes[k]

            /* Setup pointer to first subframe */
            W_ptr = W
            W_ptr_offset = 0
            b_ptr = B
            b_ptr_offset = 0
            rate_dist = 0.0f
            j = 0
            while (j < Define.NB_SUBFR) {
                val rate_dist_subfr_ptr = FloatArray(1)
                rate_dist_subfr_ptr[0] = rate_dist_subfr
                VQNearestNeighborFLP.SKP_Silk_VQ_WMat_EC_FLP(temp_idx,  /*
																		 * O index of best codebook
																		 * vector
																		 */
                        j, rate_dist_subfr_ptr,  /* O best weighted quantization error + mu * rate */
                        b_ptr,  /* I input vector to be quantized */
                        b_ptr_offset, W_ptr,  /* I weighting matrix */
                        W_ptr_offset, cbk_ptr_Q14,  /* I codebook */
                        cl_ptr,  /* I code length for each codebook vector */
                        mu,  /* I tradeoff between weighted error and rate */
                        cbk_size /* I number of vectors in codebook */
                )
                rate_dist_subfr = rate_dist_subfr_ptr[0]
                // Silk_VQ_nearest_neighbor_FLP.SKP_Silk_VQ_WMat_EC_FLP(
                // &temp_idx[ j ], /* O index of best codebook vector */
                // &rate_dist_subfr, /* O best weighted quantization error + mu * rate */
                // b_ptr, /* I input vector to be quantized */
                // W_ptr, /* I weighting matrix */
                // cbk_ptr_Q14, /* I codebook */
                // cl_ptr, /* I code length for each codebook vector */
                // mu, /* I tradeoff between weighted error and rate */
                // cbk_size /* I number of vectors in codebook */
                // );
                rate_dist += rate_dist_subfr

                // b_ptr += LTP_ORDER;
                // W_ptr += LTP_ORDER * LTP_ORDER;
                b_ptr_offset += Define.LTP_ORDER
                W_ptr_offset += Define.LTP_ORDER * Define.LTP_ORDER
                j++
            }
            if (rate_dist < min_rate_dist) {
                min_rate_dist = rate_dist
                // SKP_memcpy( cbk_index, temp_idx, NB_SUBFR * sizeof( SKP_int ) );
                // *periodicity_index = k;
                System.arraycopy(temp_idx, 0, cbk_index, 0, Define.NB_SUBFR)
                periodicity_index[0] = k
            }

            /* Break early in low-complexity mode if rate distortion is below threshold */
            if (lowComplexity != 0 && rate_dist * 16384.0f < TablesLTP.SKP_Silk_LTP_gain_middle_avg_RD_Q14) {
                break
            }
            k++
        }

        // cbk_ptr_Q14 = SKP_Silk_LTP_vq_ptrs_Q14[ *periodicity_index ];
        cbk_ptr_Q14 = TablesLTP.SKP_Silk_LTP_vq_ptrs_Q14[periodicity_index[0]]
        j = 0
        while (j < Define.NB_SUBFR) {

            // SKP_short2float_array( &B[ j * LTP_ORDER ],
            // &cbk_ptr_Q14[ cbk_index[ j ] * LTP_ORDER ],
            // LTP_ORDER );
            SigProcFLP.SKP_short2float_array(B, j * Define.LTP_ORDER, cbk_ptr_Q14, cbk_index!![j]
                    * Define.LTP_ORDER, Define.LTP_ORDER)
            j++
        }
        j = 0
        while (j < Define.NB_SUBFR * Define.LTP_ORDER) {
            B!![j] *= DefineFLP.Q14_CONVERSION_FAC
            j++
        }
    }
}