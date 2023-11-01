/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Interpolate two vectors.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object Interpolate {
    /**
     * Interpolate two vectors.
     *
     * @param xi
     * interpolated vector.
     * @param x0
     * first vector.
     * @param x1
     * second vector.
     * @param ifact_Q2
     * interp. factor, weight on 2nd vector.
     * @param d
     * number of parameters.
     */
    fun SKP_Silk_interpolate(xi: IntArray,  /* O interpolated vector */
            x0: IntArray,  /* I first vector */
            x1: IntArray,  /* I second vector */
            ifact_Q2: Int,  /* I interp. factor, weight on 2nd vector */
            d: Int /* I number of parameters */
    ) {
        var i: Int
        assert(ifact_Q2 >= 0)
        assert(ifact_Q2 <= 1 shl 2)
        i = 0
        while (i < d) {
            xi[i] = x0[i] + ((x1[i] - x0[i]) * ifact_Q2 shr 2)
            i++
        }
    }
}