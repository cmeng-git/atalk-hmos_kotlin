/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Upsample by a factor 2, high quality.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object ResamplerPrivateUp2HQ {
    /**
     * Upsample by a factor 2, high quality. Uses 2nd order allpass filters for the 2x upsampling,
     * followed by a notch filter just above Nyquist.
     *
     * @param S
     * Resampler state [ 6 ].
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
     * Number of INPUT samples.
     */
    fun SKP_Silk_resampler_private_up2_HQ(S: IntArray?,  /* I/O: Resampler state [ 6 ] */
            S_offset: Int, out: ShortArray?,  /* O: Output signal [ 2 * len ] */
            out_offset: Int, `in`: ShortArray,  /* I: Input signal [ len ] */
            in_offset: Int, len: Int /* I: Number of INPUT samples */
    ) {
        var k: Int
        var in32: Int
        var out32_1: Int
        var out32_2: Int
        var Y: Int
        var X: Int
        assert(ResamplerRom.SKP_Silk_resampler_up2_hq_0[0] > 0)
        assert(ResamplerRom.SKP_Silk_resampler_up2_hq_0[1] < 0)
        assert(ResamplerRom.SKP_Silk_resampler_up2_hq_1[0] > 0)
        assert(ResamplerRom.SKP_Silk_resampler_up2_hq_1[1] < 0)

        /* Internal variables and state are in Q10 format */
        k = 0
        while (k < len) {

            /* Convert to Q10 */in32 = `in`[in_offset + k].toInt() shl 10

            /* First all-pass section for even output sample */
            Y = in32 - S!![S_offset]
            X = Macros.SKP_SMULWB(Y, ResamplerRom.SKP_Silk_resampler_up2_hq_0[0].toInt())
            out32_1 = S[S_offset] + X
            S[S_offset] = in32 + X

            /* Second all-pass section for even output sample */
            Y = out32_1 - S[S_offset + 1]
            X = Macros.SKP_SMLAWB(Y, Y, ResamplerRom.SKP_Silk_resampler_up2_hq_0[1].toInt())
            out32_2 = S[S_offset + 1] + X
            S[S_offset + 1] = out32_1 + X

            /* Biquad notch filter */
            out32_2 = Macros.SKP_SMLAWB(out32_2, S[S_offset + 5],
                    ResamplerRom.SKP_Silk_resampler_up2_hq_notch[2].toInt())
            out32_2 = Macros.SKP_SMLAWB(out32_2, S[S_offset + 4],
                    ResamplerRom.SKP_Silk_resampler_up2_hq_notch[1].toInt())
            out32_1 = Macros.SKP_SMLAWB(out32_2, S[S_offset + 4],
                    ResamplerRom.SKP_Silk_resampler_up2_hq_notch[0].toInt())
            S[S_offset + 5] = out32_2 - S[S_offset + 5]

            /* Apply gain in Q15, convert back to int16 and store to output */
            out!![out_offset + 2 * k] = SigProcFIX.SKP_SAT16(Macros.SKP_SMLAWB(256, out32_1,
                    ResamplerRom.SKP_Silk_resampler_up2_hq_notch[3].toInt()) shr 9).toShort()

            /* First all-pass section for odd output sample */
            Y = in32 - S[S_offset + 2]
            X = Macros.SKP_SMULWB(Y, ResamplerRom.SKP_Silk_resampler_up2_hq_1[0].toInt())
            out32_1 = S[S_offset + 2] + X
            S[S_offset + 2] = in32 + X

            /* Second all-pass section for odd output sample */
            Y = out32_1 - S[S_offset + 3]
            X = Macros.SKP_SMLAWB(Y, Y, ResamplerRom.SKP_Silk_resampler_up2_hq_1[1].toInt())
            out32_2 = S[S_offset + 3] + X
            S[S_offset + 3] = out32_1 + X

            /* Biquad notch filter */
            out32_2 = Macros.SKP_SMLAWB(out32_2, S[S_offset + 4],
                    ResamplerRom.SKP_Silk_resampler_up2_hq_notch[2].toInt())
            out32_2 = Macros.SKP_SMLAWB(out32_2, S[S_offset + 5],
                    ResamplerRom.SKP_Silk_resampler_up2_hq_notch[1].toInt())
            out32_1 = Macros.SKP_SMLAWB(out32_2, S[S_offset + 5],
                    ResamplerRom.SKP_Silk_resampler_up2_hq_notch[0].toInt())
            S[S_offset + 4] = out32_2 - S[S_offset + 4]

            /* Apply gain in Q15, convert back to int16 and store to output */
            out[out_offset + 2 * k + 1] = SigProcFIX.SKP_SAT16(Macros.SKP_SMLAWB(256, out32_1,
                    ResamplerRom.SKP_Silk_resampler_up2_hq_notch[3].toInt()) shr 9).toShort()
            k++
        }
    }

    /**
     * the wrapper method.
     *
     * @param SS
     * Resampler state (unused).
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
    fun SKP_Silk_resampler_private_up2_HQ_wrapper(SS: Any?,  /* I/O: Resampler state (unused) */
            out: ShortArray?,  /* O: Output signal [ 2 * len ] */
            out_offset: Int, `in`: ShortArray,  /* I: Input signal [ len ] */
            in_offset: Int, len: Int /* I: Number of input samples */
    ) {
        val S = SS as SKP_Silk_resampler_state_struct?
        SKP_Silk_resampler_private_up2_HQ(S!!.sIIR, 0, out, out_offset, `in`, in_offset, len)
    }
}