/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * compute autocorrelation.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object AutocorrelationFLP {
    /**
     * compute autocorrelation.
     *
     * @param results
     * result (length correlationCount)
     * @param results_offset
     * offset of valid data.
     * @param inputData
     * input data to correlate
     * @param inputData_offset
     * offset of valid data.
     * @param inputDataSize
     * length of input
     * @param correlationCount
     * number of correlation taps to compute
     */
    // TODO: float or double???
    fun SKP_Silk_autocorrelation_FLP(results: FloatArray,  /* O result (length correlationCount) */
            results_offset: Int, inputData: FloatArray,  /* I input data to correlate */
            inputData_offset: Int, inputDataSize: Int,  /* I length of input */
            correlationCount: Int /* I number of correlation taps to compute */
    ) {
        var correlationCount = correlationCount
        var i: Int
        if (correlationCount > inputDataSize) {
            correlationCount = inputDataSize
        }
        i = 0
        while (i < correlationCount) {
            results[results_offset + i] = InnerProductFLP.SKP_Silk_inner_product_FLP(
                    inputData, inputData_offset, inputData, inputData_offset + i, inputDataSize - i).toFloat()
            i++
        }
    }
}