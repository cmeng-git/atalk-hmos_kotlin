/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Upsample using a combination of allpass-based 2x upsampling and FIR interpolation.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object ResamplerPrivateIIRFIR {
    /**
     * Upsample using a combination of allpass-based 2x upsampling and FIR interpolation.
     *
     * @param SS
     * Resampler state.
     * @param out
     * Output signal.
     * @param out_offset
     * offset of valid data.
     * @param in
     * Input signal.
     * @param in_offset
     * offset of valid data.
     * @param inLen
     * Number of input samples.
     */
    fun SKP_Silk_resampler_private_IIR_FIR(SS: Any?,  /* I/O: Resampler state */
            out: ShortArray?,  /* O: Output signal */
            out_offset: Int, `in`: ShortArray,  /* I: Input signal */
            in_offset: Int, inLen: Int /* I: Number of input samples */
    ) {
        var out_offset = out_offset
        var in_offset = in_offset
        var inLen = inLen
        val S = SS as SKP_Silk_resampler_state_struct?
        var nSamplesIn: Int
        var table_index: Int
        var max_index_Q16: Int
        var index_Q16: Int
        val index_increment_Q16: Int
        var res_Q15: Int
        val buf = ShortArray(2 * ResamplerPrivate.RESAMPLER_MAX_BATCH_SIZE_IN
                + ResamplerRom.RESAMPLER_ORDER_FIR_144)
        var buf_ptr: Int

        /* Copy buffered samples to start of buffer */
        // TODO:litter-endian or big-endian???
        // SKP_memcpy( buf, S->sFIR, RESAMPLER_ORDER_FIR_144 * sizeof( SKP_int32 ) );
        for (i_djinn in 0 until ResamplerRom.RESAMPLER_ORDER_FIR_144) {
            // buf[2*i_djinn] = (short)(S.sFIR[i_djinn]>>>16);
            // buf[2*i_djinn+1] = (short)(S.sFIR[i_djinn]&0x0000FFFF);
            // littel-endian
            buf[2 * i_djinn] = (S!!.sFIR[i_djinn] and 0x0000FFFF).toShort()
            buf[2 * i_djinn + 1] = (S.sFIR[i_djinn] ushr 16).toShort()
        }

        /* Iterate over blocks of frameSizeIn input samples */
        index_increment_Q16 = S!!.invRatio_Q16
        while (true) {
            nSamplesIn = Math.min(inLen, S.batchSize)
            if (S.input2x == 1) {
                /* Upsample 2x */
                S.up2_function(S.sIIR, buf, ResamplerRom.RESAMPLER_ORDER_FIR_144, `in`, in_offset,
                        nSamplesIn)
            } else {
                /* Fourth-order ARMA filter */
                ResamplerPrivateARMA4.SKP_Silk_resampler_private_ARMA4(S.sIIR, 0, buf,
                        ResamplerRom.RESAMPLER_ORDER_FIR_144, `in`, in_offset, S.Coefs, 0, nSamplesIn)
            }
            max_index_Q16 = nSamplesIn shl 16 + S.input2x /* +1 if 2x upsampling */

            /* Interpolate upsampled signal and store in output array */
            index_Q16 = 0
            while (index_Q16 < max_index_Q16) {
                table_index = Macros.SKP_SMULWB(index_Q16 and 0xFFFF, 144)
                buf_ptr = index_Q16 shr 16
                res_Q15 = Macros.SKP_SMULBB(buf[buf_ptr].toInt(),
                        ResamplerRom.SKP_Silk_resampler_frac_FIR_144[table_index]!![0].toInt())
                res_Q15 = Macros.SKP_SMLABB(res_Q15, buf[buf_ptr + 1].toInt(),
                        ResamplerRom.SKP_Silk_resampler_frac_FIR_144[table_index]!![1].toInt())
                res_Q15 = Macros.SKP_SMLABB(res_Q15, buf[buf_ptr + 2].toInt(),
                        ResamplerRom.SKP_Silk_resampler_frac_FIR_144[table_index]!![2].toInt())
                res_Q15 = Macros.SKP_SMLABB(res_Q15, buf[buf_ptr + 3].toInt(),
                        ResamplerRom.SKP_Silk_resampler_frac_FIR_144[143 - table_index]!![2].toInt())
                res_Q15 = Macros.SKP_SMLABB(res_Q15, buf[buf_ptr + 4].toInt(),
                        ResamplerRom.SKP_Silk_resampler_frac_FIR_144[143 - table_index]!![1].toInt())
                res_Q15 = Macros.SKP_SMLABB(res_Q15, buf[buf_ptr + 5].toInt(),
                        ResamplerRom.SKP_Silk_resampler_frac_FIR_144[143 - table_index]!![0].toInt())
                out!![out_offset++] = SigProcFIX.SKP_SAT16(SigProcFIX.SKP_RSHIFT_ROUND(
                        res_Q15, 15)).toShort()
                index_Q16 += index_increment_Q16
            }
            in_offset += nSamplesIn
            inLen -= nSamplesIn
            if (inLen > 0) {
                /* More iterations to do; copy last part of filtered signal to beginning of buffer */
                // TODO:litter-endian or big-endian???
                // SKP_memcpy( buf, &buf[ nSamplesIn << S->input2x ], RESAMPLER_ORDER_FIR_144 *
                // sizeof( SKP_int32 ) );
                for (i_djinn in 0 until ResamplerRom.RESAMPLER_ORDER_FIR_144) buf[i_djinn] = buf[(nSamplesIn shl S.input2x) + i_djinn]
            } else {
                break
            }
        }

        /* Copy last part of filtered signal to the state for the next call */
        // TODO:litter-endian or big-endian???
        // SKP_memcpy( S->sFIR, &buf[nSamplesIn << S->input2x ], RESAMPLER_ORDER_FIR_144 * sizeof(
        // SKP_int32 ) );
        for (i_djinn in 0 until ResamplerRom.RESAMPLER_ORDER_FIR_144) {
            // S.sFIR[i_djinn] = (int)buf[(nSamplesIn << S.input2x) + 2*i_djinn] << 16;
            // S.sFIR[i_djinn] |= (int)buf[(nSamplesIn << S.input2x) + 2*i_djinn+1] & 0x0000FFFF;
            // little-endian
            S.sFIR[i_djinn] = buf[(nSamplesIn shl S.input2x) + 2 * i_djinn].toInt() and 0xFF
            S.sFIR[i_djinn] = S.sFIR[i_djinn] or (buf[(nSamplesIn shl S.input2x) + 2 * i_djinn].toInt() shr 8 and 0xFF shl 8 and 0x0000FF00)
            S.sFIR[i_djinn] = S.sFIR[i_djinn] or (buf[(nSamplesIn shl S.input2x) + 2 * i_djinn + 1].toInt() shr 0 and 0xFF shl 16 and 0x00FF0000)
            S.sFIR[i_djinn] = S.sFIR[i_djinn] or (buf[(nSamplesIn shl S.input2x) + 2 * i_djinn + 1].toInt() shr 8 and 0xFF shl 24 and -0x1000000)
        }
    }
}