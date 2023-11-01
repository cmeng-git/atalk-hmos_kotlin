/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object LevinsondurbinFLP {
    /**
     * Solve the normal equations using the Levinson-Durbin recursion.
     *
     * @param A
     * prediction coefficients [order].
     * @param A_offset
     * offset of valid data.
     * @param corr
     * input auto-correlations [order + 1].
     * @param order
     * prediction order.
     * @return prediction error energy.
     */
    fun SKP_Silk_levinsondurbin_FLP( /* O prediction error energy */
            A: FloatArray?,  /* O prediction coefficients [order] */
            A_offset: Int, corr: FloatArray,  /* I input auto-correlations [order + 1] */
            order: Int /* I prediction order */
    ): Float {
        var i: Int
        var mHalf: Int
        var m: Int
        val min_nrg: Float
        var nrg: Float
        var t: Float
        var km: Float
        var Atmp1: Float
        var Atmp2: Float
        min_nrg = 1e-12f * corr[0] + 1e-9f
        nrg = corr[0]
        nrg = Math.max(min_nrg, nrg)
        A!![A_offset] = corr[1] / nrg
        nrg -= A[A_offset] * corr[1]
        nrg = Math.max(min_nrg, nrg)
        m = 1
        while (m < order) {
            t = corr[m + 1]
            i = 0
            while (i < m) {
                t -= A[A_offset + i] * corr[m - i]
                i++
            }

            /* reflection coefficient */
            km = t / nrg

            /* residual energy */
            nrg -= km * t
            nrg = Math.max(min_nrg, nrg)
            mHalf = m shr 1
            i = 0
            while (i < mHalf) {
                Atmp1 = A[A_offset + i]
                Atmp2 = A[A_offset + m - i - 1]
                A[A_offset + m - i - 1] -= km * Atmp1
                A[A_offset + i] -= km * Atmp2
                i++
            }
            if (m and 1 != 0) {
                A[A_offset + mHalf] -= km * A[A_offset + mHalf]
            }
            A[A_offset + m] = km
            m++
        }

        /* return the residual energy */
        return nrg
    }
}