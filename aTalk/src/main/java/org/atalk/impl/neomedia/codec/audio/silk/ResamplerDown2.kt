/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Downsample by a factor 2, mediocre quality.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object ResamplerDown2 {
    /**
     * Downsample by a factor 2, mediocre quality.
     *
     * @param S
     * State vector [ 2 ].
     * @param S_offset
     * offset of valid data.
     * @param out
     * Output signal [ len ].
     * @param out_offset
     * offset of valid data.
     * @param in
     * Input signal [ floor(len/2) ].
     * @param in_offset
     * offset of valid data.
     * @param inLen
     * Number of input samples.
     */
    fun SKP_Silk_resampler_down2(S: IntArray?,  /* I/O: State vector [ 2 ] */
            S_offset: Int, out: ShortArray,  /* O: Output signal [ len ] */
            out_offset: Int, `in`: ShortArray,  /* I: Input signal [ floor(len/2) ] */
            in_offset: Int, inLen: Int /* I: Number of input samples */
    ) {
        var k: Int
        val len2 = inLen shr 1
        var in32: Int
        var out32: Int
        var Y: Int
        var X: Int
        assert(ResamplerRom.SKP_Silk_resampler_down2_0 > 0)
        assert(ResamplerRom.SKP_Silk_resampler_down2_1 < 0)

        /* Internal variables and state are in Q10 format */
        k = 0
        while (k < len2) {

            /* Convert to Q10 */
            in32 = `in`[in_offset + 2 * k].toInt() shl 10

            /* All-pass section for even input sample */
            Y = in32 - S!![S_offset]
            X = Macros.SKP_SMLAWB(Y, Y, ResamplerRom.SKP_Silk_resampler_down2_1.toInt())
            out32 = S[S_offset] + X
            S[S_offset] = in32 + X

            /* Convert to Q10 */
            in32 = `in`[in_offset + 2 * k + 1].toInt() shl 10

            /* All-pass section for odd input sample, and add to output of previous section */
            Y = in32 - S[S_offset + 1]
            X = Macros.SKP_SMULWB(Y, ResamplerRom.SKP_Silk_resampler_down2_0.toInt())
            out32 += S[S_offset + 1]
            out32 += X
            S[S_offset + 1] = in32 + X

            /* Add, convert back to int16 and store to output */
            out[out_offset + k] = SigProcFIX.SKP_SAT16(SigProcFIX.SKP_RSHIFT_ROUND(out32,
                    11)).toShort()
            k++
        }
    }
}