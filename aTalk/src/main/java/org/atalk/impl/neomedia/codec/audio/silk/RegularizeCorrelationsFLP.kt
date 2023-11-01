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
object RegularizeCorrelationsFLP {
    /**
     *
     * @param XX
     * Correlation matrices
     * @param xx
     * Correlation values
     * @param xx_offset
     * offset of valid data.
     * @param noise
     * Noise energy to add
     * @param D
     * Dimension of XX
     */
    fun SKP_Silk_regularize_correlations_FLP(XX: FloatArray,  /* I/O Correlation matrices */
            XX_offset: Int, xx: FloatArray,  /* I/O Correlation values */
            xx_offset: Int, noise: Float,  /* I Noise energy to add */
            D: Int /* I Dimension of XX */
    ) {
        var i: Int
        i = 0
        while (i < D) {
            XX[XX_offset + i * D + i] += noise
            i++
        }
        xx[xx_offset] += noise
    }
}