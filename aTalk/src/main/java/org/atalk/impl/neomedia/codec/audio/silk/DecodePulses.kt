/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

import java.util.*

/**
 * Decode quantization indices of excitation.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object DecodePulses {
    /**
     * Decode quantization indices of excitation.
     *
     * @param psRC
     * Range coder state.
     * @param psDecCtrl
     * Decoder control.
     * @param q
     * Excitation signal.
     * @param frame_length
     * Frame length (preliminary).
     */
    fun SKP_Silk_decode_pulses(psRC: SKP_Silk_range_coder_state?,  /* I/O Range coder state */
            psDecCtrl: SKP_Silk_decoder_control,  /* I/O Decoder control */
            q: IntArray,  /* O Excitation signal */
            frame_length: Int /* I Frame length (preliminary) */
    ) {
        var i: Int
        var j: Int
        var k: Int
        val iter: Int
        var abs_q: Int
        var nLS: Int
        var bit: Int
        val sum_pulses = IntArray(Define.MAX_NB_SHELL_BLOCKS)
        val nLshifts = IntArray(Define.MAX_NB_SHELL_BLOCKS)
        var pulses_ptr: IntArray
        var pulses_ptr_offset: Int
        val cdf_ptr: IntArray?
        /** */
        /* Decode rate level */
        /** */
        val RateLevelIndex_ptr = IntArray(1)
        RateLevelIndex_ptr[0] = psDecCtrl.RateLevelIndex
        RangeCoder.SKP_Silk_range_decoder(RateLevelIndex_ptr, 0, psRC,
                TablesPulsesPerBlock.SKP_Silk_rate_levels_CDF[psDecCtrl.sigtype], 0,
                TablesPulsesPerBlock.SKP_Silk_rate_levels_CDF_offset)
        psDecCtrl.RateLevelIndex = RateLevelIndex_ptr[0]

        /* Calculate number of shell blocks */
        iter = frame_length / Define.SHELL_CODEC_FRAME_LENGTH
        /** */
        /* Sum-Weighted-Pulses Decoding */
        /** */
        cdf_ptr = TablesPulsesPerBlock.SKP_Silk_pulses_per_block_CDF[psDecCtrl.RateLevelIndex]
        i = 0
        while (i < iter) {
            nLshifts[i] = 0
            RangeCoder.SKP_Silk_range_decoder(sum_pulses, i, psRC, cdf_ptr, 0,
                    TablesPulsesPerBlock.SKP_Silk_pulses_per_block_CDF_offset)

            /* LSB indication */
            while (sum_pulses[i] == Define.MAX_PULSES + 1) {
                nLshifts[i]++
                RangeCoder.SKP_Silk_range_decoder(sum_pulses, i, psRC,
                        TablesPulsesPerBlock.SKP_Silk_pulses_per_block_CDF[Define.N_RATE_LEVELS - 1], 0,
                        TablesPulsesPerBlock.SKP_Silk_pulses_per_block_CDF_offset)
            }
            i++
        }
        /** */
        /* Shell decoding */
        /** */
        i = 0
        while (i < iter) {
            if (sum_pulses[i] > 0) {
                ShellCoder.SKP_Silk_shell_decoder(q, Macros.SKP_SMULBB(i, Define.SHELL_CODEC_FRAME_LENGTH), psRC,
                        sum_pulses[i])
            } else {
                Arrays.fill(q, Macros.SKP_SMULBB(i, Define.SHELL_CODEC_FRAME_LENGTH),
                        Macros.SKP_SMULBB(i, Define.SHELL_CODEC_FRAME_LENGTH) + Define.SHELL_CODEC_FRAME_LENGTH, 0)
            }
            i++
        }
        /** */
        /* LSB Decoding */
        /** */
        i = 0
        while (i < iter) {
            if (nLshifts[i] > 0) {
                nLS = nLshifts[i]
                pulses_ptr = q
                pulses_ptr_offset = Macros.SKP_SMULBB(i, Define.SHELL_CODEC_FRAME_LENGTH)
                k = 0
                while (k < Define.SHELL_CODEC_FRAME_LENGTH) {
                    abs_q = pulses_ptr[pulses_ptr_offset + k]
                    j = 0
                    while (j < nLS) {
                        abs_q = abs_q shl 1
                        val bit_ptr = IntArray(1)
                        RangeCoder.SKP_Silk_range_decoder(bit_ptr, 0, psRC,
                                TablesOther.SKP_Silk_lsb_CDF, 0, 1)
                        bit = bit_ptr[0]
                        abs_q += bit
                        j++
                    }
                    pulses_ptr[pulses_ptr_offset + k] = abs_q
                    k++
                }
            }
            i++
        }
        /** */
        /* Decode and add signs to pulse signal */
        /** */
        CodeSigns.SKP_Silk_decode_signs(psRC, q, frame_length, psDecCtrl.sigtype,
                psDecCtrl.QuantOffsetType, psDecCtrl.RateLevelIndex)
    }
}