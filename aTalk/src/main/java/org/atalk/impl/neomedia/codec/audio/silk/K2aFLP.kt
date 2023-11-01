/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * step up function, converts reflection coefficients to prediction coefficients.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object K2aFLP {
    /**
     * step up function, converts reflection coefficients to prediction coefficients.
     *
     * @param A
     * prediction coefficients [order].
     * @param rc
     * reflection coefficients [order].
     * @param order
     * prediction order.
     */
    fun SKP_Silk_k2a_FLP(A: FloatArray,  /* O: prediction coefficients [order] */
            rc: FloatArray,  /* I: reflection coefficients [order] */
            order: Int /* I: prediction order */
    ) {
        var k: Int
        var n: Int
        val Atmp = FloatArray(SigProcFIXConstants.SKP_Silk_MAX_ORDER_LPC)
        k = 0
        while (k < order) {
            n = 0
            while (n < k) {
                Atmp[n] = A[n]
                n++
            }
            n = 0
            while (n < k) {
                A[n] += Atmp[k - n - 1] * rc[k]
                n++
            }
            A[k] = -rc[k]
            k++
        }
    }
}