/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Upsample by a factor 4. Note: very low quality, only use with output sampling rates above 96 kHz.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object ResamplerPrivateUp4 {
    /**
     * Upsample by a factor 4. Note: very low quality, only use with output sampling rates above 96
     * kHz.
     *
     * @param S
     * State vector [ 2 ].
     * @param S_offset
     * offset of valid data.
     * @param out
     * Output signal [ 4 * len ].
     * @param out_offset
     * offset of valid data.
     * @param in
     * Input signal [ len ].
     * @param in_offset
     * offset of valid data.
     * @param len
     * Number of INPUT samples.
     */
    fun SKP_Silk_resampler_private_up4(S: IntArray?,  /* I/O: State vector [ 2 ] */
            S_offset: Int, out: ShortArray?,  /* O: Output signal [ 4 * len ] */
            out_offset: Int, `in`: ShortArray,  /* I: Input signal [ len ] */
            in_offset: Int, len: Int /* I: Number of INPUT samples */
    ) {
        var k: Int
        var in32: Int
        var out32: Int
        var Y: Int
        var X: Int
        var out16: Int
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
            out16 = SigProcFIX.SKP_SAT16(SigProcFIX.SKP_RSHIFT_ROUND(out32, 10)).toShort().toInt()
            out!![out_offset + 4 * k] = out16.toShort()
            out[out_offset + 4 * k + 1] = out16.toShort()

            /* All-pass section for odd output sample */
            Y = in32 - S[S_offset + 1]
            X = Macros.SKP_SMLAWB(Y, Y, ResamplerRom.SKP_Silk_resampler_up2_lq_1.toInt())
            out32 = S[S_offset + 1] + X
            S[S_offset + 1] = in32 + X

            /* Convert back to int16 and store to output */
            out16 = SigProcFIX.SKP_SAT16(SigProcFIX.SKP_RSHIFT_ROUND(out32, 10)).toShort().toInt()
            out[out_offset + 4 * k + 2] = out16.toShort()
            out[out_offset + 4 * k + 3] = out16.toShort()
            k++
        }
    }
}