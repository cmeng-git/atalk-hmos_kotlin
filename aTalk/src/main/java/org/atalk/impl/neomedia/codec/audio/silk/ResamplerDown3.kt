/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Downsample by a factor 3, low quality.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object ResamplerDown3 {
    const val ORDER_FIR = 4

    /**
     * Downsample by a factor 3, low quality.
     *
     * @param S
     * State vector [ 8 ]
     * @param S_offset
     * offset of valid data.
     * @param out
     * Output signal [ floor(inLen/3) ]
     * @param out_offset
     * offset of valid data.
     * @param in
     * Input signal [ inLen ]
     * @param in_offset
     * offset of valid data.
     * @param inLen
     * Number of input samples
     */
    fun SKP_Silk_resampler_down3(S: IntArray,  /* I/O: State vector [ 8 ] */
            S_offset: Int, out: ShortArray,  /* O: Output signal [ floor(inLen/3) ] */
            out_offset: Int, `in`: ShortArray,  /* I: Input signal [ inLen ] */
            in_offset: Int, inLen: Int /* I: Number of input samples */
    ) {
        var out_offset = out_offset
        var in_offset = in_offset
        var inLen = inLen
        var nSamplesIn: Int
        var counter: Int
        var res_Q6: Int
        val buf = IntArray(ResamplerPrivate.RESAMPLER_MAX_BATCH_SIZE_IN + ORDER_FIR)
        var buf_ptr: Int

        /* Copy buffered samples to start of buffer */
        for (i_djinn in 0 until ORDER_FIR) buf[i_djinn] = S[S_offset + i_djinn]

        /* Iterate over blocks of frameSizeIn input samples */
        while (true) {
            nSamplesIn = Math.min(inLen, ResamplerPrivate.RESAMPLER_MAX_BATCH_SIZE_IN)

            /* Second-order AR filter (output in Q8) */
            ResamplerPrivateAR2.SKP_Silk_resampler_private_AR2(S, ORDER_FIR, buf, ORDER_FIR, `in`,
                    in_offset, ResamplerRom.SKP_Silk_Resampler_1_3_COEFS_LQ, 0, nSamplesIn)

            /* Interpolate filtered signal */
            buf_ptr = 0
            counter = nSamplesIn
            while (counter > 2) {
                /* Inner product */
                res_Q6 = Macros.SKP_SMULWB(buf[buf_ptr] + buf[buf_ptr + 5],
                        ResamplerRom.SKP_Silk_Resampler_1_3_COEFS_LQ[2].toInt())
                res_Q6 = Macros.SKP_SMLAWB(res_Q6, buf[buf_ptr + 1] + buf[buf_ptr + 4],
                        ResamplerRom.SKP_Silk_Resampler_1_3_COEFS_LQ[3].toInt())
                res_Q6 = Macros.SKP_SMLAWB(res_Q6, buf[buf_ptr + 2] + buf[buf_ptr + 3],
                        ResamplerRom.SKP_Silk_Resampler_1_3_COEFS_LQ[4].toInt())

                /* Scale down, saturate and store in output array */
                out[out_offset++] = SigProcFIX.SKP_SAT16(SigProcFIX.SKP_RSHIFT_ROUND(
                        res_Q6, 6)).toShort()
                buf_ptr += 3
                counter -= 3
            }
            in_offset += nSamplesIn
            inLen -= nSamplesIn
            if (inLen > 0) {
                /* More iterations to do; copy last part of filtered signal to beginning of buffer */
                for (i_djinn in 0 until ORDER_FIR) buf[i_djinn] = buf[nSamplesIn + i_djinn]
            } else {
                break
            }
        }

        /* Copy last part of filtered signal to the state for the next call */
        for (i_djinn in 0 until ORDER_FIR) S[S_offset + i_djinn] = buf[nSamplesIn + i_djinn]
    }
}