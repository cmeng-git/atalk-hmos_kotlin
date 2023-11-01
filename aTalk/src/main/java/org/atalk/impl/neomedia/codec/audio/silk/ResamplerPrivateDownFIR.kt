/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Resample with a 2x downsampler (optional), a 2nd order AR filter followed by FIR interpolation.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object ResamplerPrivateDownFIR {
    /**
     * Resample with a 2x downsampler (optional), a 2nd order AR filter followed by FIR
     * interpolation.
     *
     * SS Resampler state.
     * out Output signal.
     * out_offset offset of valid data.
     * in Input signal.
     * in_offset offset of valid data.
     * inLen Number of input samples.
     */
    fun SKP_Silk_resampler_private_down_FIR(SS: Any?,  /* I/O: Resampler state */
            out: ShortArray?,  /* O: Output signal */
            out_offset_: Int, `in`: ShortArray,  /* I: Input signal */
            in_offset_: Int, inLen_: Int /* I: Number of input samples */
    ) {
        var out_offset = out_offset_
        var in_offset = in_offset_
        var inLen = inLen_
        val S = SS as SKP_Silk_resampler_state_struct?
        var nSamplesIn: Int
        var interpol_ind: Int
        var max_index_Q16: Int
        var index_Q16: Int
        val index_increment_Q16: Int
        var res_Q6: Int
        val buf1 = ShortArray(ResamplerPrivate.RESAMPLER_MAX_BATCH_SIZE_IN / 2)
        val buf2 = IntArray(ResamplerPrivate.RESAMPLER_MAX_BATCH_SIZE_IN
                + ResamplerRom.RESAMPLER_DOWN_ORDER_FIR)
        var buf_ptr: IntArray
        var buf_ptr_offset: Int
        var interpol_ptr: ShortArray?
        val FIR_Coefs: ShortArray?
        var interpol_ptr_offset: Int
        val FIR_Coefs_offset: Int

        /* Copy buffered samples to start of buffer */
        // TODO: arrayCopy();
        // SKP_memcpy( buf2, S->sFIR, RESAMPLER_DOWN_ORDER_FIR * sizeof( SKP_int32 ) );
        for (i_djinn in 0 until ResamplerRom.RESAMPLER_DOWN_ORDER_FIR) buf2[i_djinn] = S!!.sFIR[i_djinn]
        FIR_Coefs = S!!.Coefs
        FIR_Coefs_offset = 2

        /* Iterate over blocks of frameSizeIn input samples */
        index_increment_Q16 = S.invRatio_Q16
        while (true) {
            nSamplesIn = Math.min(inLen, S.batchSize)
            if (S.input2x == 1) {
                /* Downsample 2x */
                ResamplerDown2.SKP_Silk_resampler_down2(S.sDown2, 0, buf1, 0, `in`, in_offset,
                        nSamplesIn)
                nSamplesIn = nSamplesIn shr 1

                /* Second-order AR filter (output in Q8) */
                ResamplerPrivateAR2.SKP_Silk_resampler_private_AR2(S.sIIR, 0, buf2,
                        ResamplerRom.RESAMPLER_DOWN_ORDER_FIR, buf1, 0, S.Coefs, 0, nSamplesIn)
            } else {
                /* Second-order AR filter (output in Q8) */
                ResamplerPrivateAR2.SKP_Silk_resampler_private_AR2(S.sIIR, 0, buf2,
                        ResamplerRom.RESAMPLER_DOWN_ORDER_FIR, `in`, in_offset, S.Coefs, 0, nSamplesIn)
            }
            max_index_Q16 = nSamplesIn shl 16

            /* Interpolate filtered signal */
            if (S.FIR_Fracs == 1) {
                index_Q16 = 0
                while (index_Q16 < max_index_Q16) {

                    /* Integer part gives pointer to buffered input */
                    buf_ptr = buf2
                    buf_ptr_offset = index_Q16 shr 16

                    /* Inner product */
                    res_Q6 = Macros.SKP_SMULWB(buf_ptr[buf_ptr_offset] + buf_ptr[buf_ptr_offset + 11],
                            FIR_Coefs!![FIR_Coefs_offset].toInt())
                    res_Q6 = Macros.SKP_SMLAWB(res_Q6, buf_ptr[buf_ptr_offset + 1]
                            + buf_ptr[buf_ptr_offset + 10], FIR_Coefs[FIR_Coefs_offset + 1].toInt())
                    res_Q6 = Macros.SKP_SMLAWB(res_Q6, buf_ptr[buf_ptr_offset + 2]
                            + buf_ptr[buf_ptr_offset + 9], FIR_Coefs[FIR_Coefs_offset + 2].toInt())
                    res_Q6 = Macros.SKP_SMLAWB(res_Q6, buf_ptr[buf_ptr_offset + 3]
                            + buf_ptr[buf_ptr_offset + 8], FIR_Coefs[FIR_Coefs_offset + 3].toInt())
                    res_Q6 = Macros.SKP_SMLAWB(res_Q6, buf_ptr[buf_ptr_offset + 4]
                            + buf_ptr[buf_ptr_offset + 7], FIR_Coefs[FIR_Coefs_offset + 4].toInt())
                    res_Q6 = Macros.SKP_SMLAWB(res_Q6, buf_ptr[buf_ptr_offset + 5]
                            + buf_ptr[buf_ptr_offset + 6], FIR_Coefs[FIR_Coefs_offset + 5].toInt())

                    /* Scale down, saturate and store in output array */
                    out!![out_offset++] = SigProcFIX.SKP_SAT16(SigProcFIX.SKP_RSHIFT_ROUND(
                            res_Q6, 6)).toShort()
                    index_Q16 += index_increment_Q16
                }
            } else {
                index_Q16 = 0
                while (index_Q16 < max_index_Q16) {

                    /* Integer part gives pointer to buffered input */
                    buf_ptr = buf2
                    buf_ptr_offset = index_Q16 shr 16

                    /* Fractional part gives interpolation coefficients */
                    interpol_ind = Macros.SKP_SMULWB(index_Q16 and 0xFFFF, S.FIR_Fracs)

                    /* Inner product */
                    interpol_ptr = FIR_Coefs
                    // BugFix interpol_ptr_offset = ResamplerRom.RESAMPLER_DOWN_ORDER_FIR / 2 *
                    // interpol_ind;
                    interpol_ptr_offset = FIR_Coefs_offset + ResamplerRom.RESAMPLER_DOWN_ORDER_FIR / 2 * interpol_ind
                    res_Q6 = Macros.SKP_SMULWB(buf_ptr[buf_ptr_offset], interpol_ptr!![interpol_ptr_offset].toInt())
                    res_Q6 = Macros.SKP_SMLAWB(res_Q6, buf_ptr[buf_ptr_offset + 1],
                            interpol_ptr[interpol_ptr_offset + 1].toInt())
                    res_Q6 = Macros.SKP_SMLAWB(res_Q6, buf_ptr[buf_ptr_offset + 2],
                            interpol_ptr[interpol_ptr_offset + 2].toInt())
                    res_Q6 = Macros.SKP_SMLAWB(res_Q6, buf_ptr[buf_ptr_offset + 3],
                            interpol_ptr[interpol_ptr_offset + 3].toInt())
                    res_Q6 = Macros.SKP_SMLAWB(res_Q6, buf_ptr[buf_ptr_offset + 4],
                            interpol_ptr[interpol_ptr_offset + 4].toInt())
                    res_Q6 = Macros.SKP_SMLAWB(res_Q6, buf_ptr[buf_ptr_offset + 5],
                            interpol_ptr[interpol_ptr_offset + 5].toInt())
                    interpol_ptr = FIR_Coefs
                    // BugFix interpol_ptr_offset = ResamplerRom.RESAMPLER_DOWN_ORDER_FIR / 2 * (
                    // S.FIR_Fracs - 1 -
                    // interpol_ind );
                    interpol_ptr_offset = FIR_Coefs_offset + ResamplerRom.RESAMPLER_DOWN_ORDER_FIR / 2 * (S.FIR_Fracs - 1 - interpol_ind)
                    res_Q6 = Macros.SKP_SMLAWB(res_Q6, buf_ptr[buf_ptr_offset + 11],
                            interpol_ptr!![interpol_ptr_offset].toInt())
                    res_Q6 = Macros.SKP_SMLAWB(res_Q6, buf_ptr[buf_ptr_offset + 10],
                            interpol_ptr[interpol_ptr_offset + 1].toInt())
                    res_Q6 = Macros.SKP_SMLAWB(res_Q6, buf_ptr[buf_ptr_offset + 9],
                            interpol_ptr[interpol_ptr_offset + 2].toInt())
                    res_Q6 = Macros.SKP_SMLAWB(res_Q6, buf_ptr[buf_ptr_offset + 8],
                            interpol_ptr[interpol_ptr_offset + 3].toInt())
                    res_Q6 = Macros.SKP_SMLAWB(res_Q6, buf_ptr[buf_ptr_offset + 7],
                            interpol_ptr[interpol_ptr_offset + 4].toInt())
                    res_Q6 = Macros.SKP_SMLAWB(res_Q6, buf_ptr[buf_ptr_offset + 6],
                            interpol_ptr[interpol_ptr_offset + 5].toInt())

                    /* Scale down, saturate and store in output array */
                    out!![out_offset++] = SigProcFIX.SKP_SAT16(SigProcFIX.SKP_RSHIFT_ROUND(
                            res_Q6, 6)).toShort()
                    index_Q16 += index_increment_Q16
                }
            }
            in_offset += nSamplesIn shl S.input2x
            inLen -= nSamplesIn shl S.input2x
            if (inLen > S.input2x) {
                /* More iterations to do; copy last part of filtered signal to beginning of buffer */
                // TODO: arrayCopy();
                // SKP_memcpy( buf2, &buf2[ nSamplesIn ], RESAMPLER_DOWN_ORDER_FIR * sizeof(
                // SKP_int32 ) );
                for (i_djinn in 0 until ResamplerRom.RESAMPLER_DOWN_ORDER_FIR) buf2[i_djinn] = buf2[nSamplesIn + i_djinn]
            } else {
                break
            }
        }

        /* Copy last part of filtered signal to the state for the next call */
        // TODO: arrayCopy();
        // SKP_memcpy( S->sFIR, &buf2[ nSamplesIn ], RESAMPLER_DOWN_ORDER_FIR * sizeof( SKP_int32 )
        // );
        for (i_djinn in 0 until ResamplerRom.RESAMPLER_DOWN_ORDER_FIR) S.sFIR[i_djinn] = buf2[nSamplesIn + i_djinn]
    }
}