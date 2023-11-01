/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Second order AR filter with single delay elements.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object ResamplerPrivateAR2 {
    /**
     * Second order AR filter with single delay elements.
     *
     * @param S
     * State vector [ 2 ].
     * @param S_offset
     * offset of valid data.
     * @param out_Q8
     * Output signal.
     * @param out_Q8_offset
     * offset of valid data.
     * @param in
     * Input signal.
     * @param in_offset
     * offset of valid data.
     * @param A_Q14
     * AR coefficients, Q14.
     * @param A_Q14_offset
     * offset of valid data.
     * @param len
     * Signal length.
     */
    fun SKP_Silk_resampler_private_AR2(S: IntArray?,  /* I/O: State vector [ 2 ] */
            S_offset: Int, out_Q8: IntArray,  /* O: Output signal */
            out_Q8_offset: Int, `in`: ShortArray,  /* I: Input signal */
            in_offset: Int, A_Q14: ShortArray?,  /* I: AR coefficients, Q14 */
            A_Q14_offset: Int, len: Int /* I: Signal length */
    ) {
        var k: Int
        var out32: Int
        k = 0
        while (k < len) {
            out32 = S!![S_offset] + (`in`[in_offset + k].toInt() shl 8)
            out_Q8[out_Q8_offset + k] = out32
            out32 = out32 shl 2
            S[S_offset] = Macros.SKP_SMLAWB(S[S_offset + 1], out32, A_Q14!![A_Q14_offset].toInt())
            S[S_offset + 1] = Macros.SKP_SMULWB(out32, A_Q14[A_Q14_offset + 1].toInt())
            k++
        }
    }
}