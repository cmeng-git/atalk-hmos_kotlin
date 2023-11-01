/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Downsample by a factor 4. Note: very low quality, only use with input sampling rates above 96
 * kHz.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object ResamplerPrivateDown4 {
    /**
     * Downsample by a factor 4. Note: very low quality, only use with input sampling rates above 96
     * kHz.
     *
     * @param S
     * State vector [ 2 ].
     * @param S_offset
     * offset of valid data.
     * @param out
     * Output signal [ floor(len/2) ].
     * @param out_offset
     * offset of valid data.
     * @param in
     * Input signal [ len ].
     * @param in_offset
     * offset of valid data.
     * @param inLen
     * Number of input samples.
     */
    fun SKP_Silk_resampler_private_down4(S: IntArray?,  /* I/O: State vector [ 2 ] */
            S_offset: Int, out: ShortArray,  /* O: Output signal [ floor(len/2) ] */
            out_offset: Int, `in`: ShortArray,  /* I: Input signal [ len ] */
            in_offset: Int, inLen: Int /* I: Number of input samples */
    ) {
        val len4 = inLen shr 2
        var in32: Int
        var out32: Int
        var Y: Int
        var X: Int
        assert(ResamplerRom.SKP_Silk_resampler_down2_0 > 0)
        assert(ResamplerRom.SKP_Silk_resampler_down2_1 < 0)

        /* Internal variables and state are in Q10 format */
        var k: Int = 0
        while (k < len4) {

            /* Add two input samples and convert to Q10 */
            in32 = `in`[in_offset + 4 * k] + `in`[in_offset + 4 * k + 1] shl 9

            /* All-pass section for even input sample */
            Y = in32 - S!![S_offset]
            X = Macros.SKP_SMLAWB(Y, Y, ResamplerRom.SKP_Silk_resampler_down2_1.toInt())
            out32 = S[S_offset] + X
            S[S_offset] = in32 + X

            /* Add two input samples and convert to Q10 */
            in32 = `in`[in_offset + 4 * k + 2] + `in`[in_offset + 4 * k + 3] shl 9

            /* All-pass section for odd input sample */
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