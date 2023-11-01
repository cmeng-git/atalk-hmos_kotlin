/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Rate-Distortion calculations for multiple input data vectors
 *
 * @author Dingxin Xu
 */
object NLSFVQRateDistortionFLP {
    /**
     * Rate-Distortion calculations for multiple input data vectors.
     *
     * @param pRD
     * Rate-distortion values [psNLSF_CBS_FLP->nVectors*N]
     * @param psNLSF_CBS_FLP
     * NLSF codebook stage struct
     * @param in
     * Input vectors to be quantized
     * @param w
     * Weight vector
     * @param rate_acc
     * Accumulated rates from previous stage
     * @param mu
     * Weight between weighted error and rate
     * @param N
     * Number of input vectors to be quantized
     * @param LPC_order
     * LPC order
     */
    fun SKP_Silk_NLSF_VQ_rate_distortion_FLP(pRD: FloatArray,  /*
																 * O Rate-distortion values
																 * [psNLSF_CBS_FLP->nVectors*N]
																 */
            psNLSF_CBS_FLP: SKP_Silk_NLSF_CBS_FLP?,  /* I NLSF codebook stage struct */
            `in`: FloatArray,  /* I Input vectors to be quantized */
            w: FloatArray?,  /* I Weight vector */
            rate_acc: FloatArray,  /* I Accumulated rates from previous stage */
            mu: Float,  /* I Weight between weighted error and rate */
            N: Int,  /* I Number of input vectors to be quantized */
            LPC_order: Int /* I LPC order */
    ) {

        /* Compute weighted quantization errors for all input vectors over one codebook stage */
        NLSFVQSumErrorFLP.SKP_Silk_NLSF_VQ_sum_error_FLP(pRD, `in`, w, psNLSF_CBS_FLP!!.CB, N,
                psNLSF_CBS_FLP.nVectors, LPC_order)

        /* Loop over input vectors */
        val pRD_vec: FloatArray = pRD
        var pRD_vec_offset: Int = 0
        var n: Int = 0
        var i: Int

        while (n < N) {
            /* Add rate cost to error for each codebook vector */
            i = 0
            while (i < psNLSF_CBS_FLP.nVectors) {
                pRD_vec[pRD_vec_offset + i] += mu * (rate_acc[n] + psNLSF_CBS_FLP.Rates[i])
                i++
            }
            pRD_vec_offset += psNLSF_CBS_FLP.nVectors
            n++
        }
    }
}