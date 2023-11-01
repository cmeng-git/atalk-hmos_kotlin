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
object SchurFLP {
    /**
     *
     * @param refl_coef
     * reflection coefficients (length order)
     * @param ref1_coef_offset
     * offset of valid data.
     * @param auto_corr
     * autotcorreation sequence (length order+1)
     * @param auto_corr_offset
     * offset of valid data.
     * @param order
     * order
     */
    fun SKP_Silk_schur_FLP(refl_coef: FloatArray,  /* O reflection coefficients (length order) */
            ref1_coef_offset: Int, auto_corr: FloatArray,  /* I autotcorreation sequence (length order+1) */
            auto_corr_offset: Int, order: Int /* I order */
    ) {
        var k: Int
        var n: Int
        val C = Array(SigProcFIXConstants.SKP_Silk_MAX_ORDER_LPC + 1) { FloatArray(2) }
        var Ctmp1: Float
        var Ctmp2: Float
        var rc_tmp: Float

        /* copy correlations */
        k = 0
        while (k < order + 1) {
            C[k][1] = auto_corr[auto_corr_offset + k]
            C[k][0] = C[k][1]
            k++
        }
        k = 0
        while (k < order) {

            /* get reflection coefficient */
            rc_tmp = -C[k + 1][0] / Math.max(C[0][1], 1e-9f)

            /* save the output */
            refl_coef[ref1_coef_offset + k] = rc_tmp

            /* update correlations */n = 0
            while (n < order - k) {
                Ctmp1 = C[n + k + 1][0]
                Ctmp2 = C[n][1]
                C[n + k + 1][0] = Ctmp1 + Ctmp2 * rc_tmp
                C[n][1] = Ctmp2 + Ctmp1 * rc_tmp
                n++
            }
            k++
        }
    }
}