/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Up-sample by a factor 2, low quality.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object ResamplerUp2 {
    /**
     * Up-sample by a factor 2, low quality.
     *
     * @param S
     * State vector [ 2 ].
     * @param S_offset
     * offset of valid data.
     * @param out
     * Output signal [ 2 * len ].
     * @param out_offset
     * offset of valid data.
     * @param in
     * Input signal [ len ].
     * @param in_offset
     * offset of valid data.
     * @param len
     * Number of input samples.
     */
    fun SKP_Silk_resampler_up2(S: IntArray?,  /* I/O: State vector [ 2 ] */
            S_offset: Int, out: ShortArray?,  /* O: Output signal [ 2 * len ] */
            out_offset: Int, `in`: ShortArray,  /* I: Input signal [ len ] */
            in_offset: Int, len: Int /* I: Number of input samples */
    ) {
        var k: Int
        var in32: Int
        var out32: Int
        var Y: Int
        var X: Int
        assert(ResamplerRom.SKP_Silk_resampler_up2_lq_0 > 0)
        assert(ResamplerRom.SKP_Silk_resampler_up2_lq_1 < 0)
        /* Internal variables and state are in Q10 format */
        k = 0
        while (k < len) {

            /* Convert to Q10 */
            in32 = `in`[in_offset + k].toInt() shl 10

            /* All-pass section for even output sample */
            Y = in32 - S!![S_offset + 0]
            X = Macros.SKP_SMULWB(Y, ResamplerRom.SKP_Silk_resampler_up2_lq_0.toInt())
            out32 = S[S_offset + 0] + X
            S[S_offset + 0] = in32 + X

            /* Convert back to int16 and store to output */
            out!![out_offset + 2 * k] = SigProcFIX.SKP_SAT16(SigProcFIX.SKP_RSHIFT_ROUND(
                    out32, 10)).toShort()

            /* All-pass section for odd output sample */
            Y = in32 - S[S_offset + 1]
            X = Macros.SKP_SMLAWB(Y, Y, ResamplerRom.SKP_Silk_resampler_up2_lq_1.toInt())
            out32 = S[S_offset + 1] + X
            S[S_offset + 1] = in32 + X

            /* Convert back to int16 and store to output */
            out[out_offset + 2 * k + 1] = SigProcFIX.SKP_SAT16(SigProcFIX.SKP_RSHIFT_ROUND(
                    out32, 10)).toShort()
            k++
        }
    }
}