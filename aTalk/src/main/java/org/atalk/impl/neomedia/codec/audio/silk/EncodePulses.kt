/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Encode quantization indices of excitation.
 *
 * @author Dingxin Xu
 */
object EncodePulses {
    /**
     *
     * @param pulses_comb
     * @param pulses_in
     * @param pulses_in_offset
     * offset of valid data.
     * @param max_pulses
     * max value for sum of pulses.
     * @param len
     * number of output values.
     * @return
     */
    fun combine_and_check( /* return ok */
            pulses_comb: IntArray,  /* O */
            pulses_in: IntArray,  /* I */
            pulses_in_offset: Int, max_pulses: Int,  /* I max value for sum of pulses */
            len: Int /* I number of output values */
    ): Int {
        var k: Int
        var sum: Int
        k = 0
        while (k < len) {
            sum = pulses_in[pulses_in_offset + 2 * k] + pulses_in[pulses_in_offset + 2 * k + 1]
            if (sum > max_pulses) {
                return 1
            }
            pulses_comb[k] = sum
            k++
        }
        return 0
    }

    /**
     * Encode quantization indices of excitation.
     *
     * @param psRC
     * Range coder state
     * @param sigtype
     * Sigtype
     * @param QuantOffsetType
     * QuantOffsetType
     * @param q
     * quantization
     * @param frame_length
     * Frame length
     */
    fun SKP_Silk_encode_pulses(psRC: SKP_Silk_range_coder_state?,  /* I/O Range coder state */
            sigtype: Int,  /* I Sigtype */
            QuantOffsetType: Int,  /* I QuantOffsetType */
            q: ByteArray?,  /* I quantization indices */
            frame_length: Int /* I Frame length */
    ) {
        var i: Int
        var k: Int
        var j: Int
        val iter: Int
        var bit: Int
        var nLS: Int
        var scale_down: Int
        var RateLevelIndex = 0
        var abs_q: Int
        var minSumBits_Q6: Int
        var sumBits_Q6: Int
        val abs_pulses = IntArray(Define.MAX_FRAME_LENGTH)
        val sum_pulses = IntArray(Define.MAX_NB_SHELL_BLOCKS)
        val nRshifts = IntArray(Define.MAX_NB_SHELL_BLOCKS)
        val pulses_comb = IntArray(8)
        val abs_pulses_ptr: IntArray
        var abs_pulses_ptr_offset: Int
        var pulses_ptr: ByteArray?
        var pulses_ptr_offset: Int
        val cdf_ptr: IntArray?
        var nBits_ptr: ShortArray?
        /** */
        /* Prepare for shell coding */
        /** */
        /* Calculate number of shell blocks */
        iter = frame_length / Define.SHELL_CODEC_FRAME_LENGTH

        /* Take the absolute value of the pulses */
        i = 0
        while (i < frame_length) {
            abs_pulses[i + 0] = if (q!![i + 0] > 0) q[i + 0].toInt() else -q[i + 0]
            abs_pulses[i + 1] = if (q[i + 1] > 0) q[i + 1].toInt() else -q[i + 1]
            abs_pulses[i + 2] = if (q[i + 2] > 0) q[i + 2].toInt() else -q[i + 2]
            abs_pulses[i + 3] = if (q[i + 3] > 0) q[i + 3].toInt() else -q[i + 3]
            i += 4
        }

        /* Calc sum pulses per shell code frame */
        abs_pulses_ptr = abs_pulses
        abs_pulses_ptr_offset = 0
        i = 0
        while (i < iter) {
            nRshifts[i] = 0
            while (true) {
                /* 1+1 -> 2 */
                scale_down = combine_and_check(pulses_comb, abs_pulses_ptr, abs_pulses_ptr_offset,
                        TablesPulsesPerBlock.SKP_Silk_max_pulses_table[0], 8)

                /* 2+2 -> 4 */
                scale_down += combine_and_check(pulses_comb, pulses_comb, 0,
                        TablesPulsesPerBlock.SKP_Silk_max_pulses_table[1], 4)

                /* 4+4 -> 8 */
                scale_down += combine_and_check(pulses_comb, pulses_comb, 0,
                        TablesPulsesPerBlock.SKP_Silk_max_pulses_table[2], 2)

                /* 8+8 -> 16 */
                sum_pulses[i] = pulses_comb[0] + pulses_comb[1]
                if (sum_pulses[i] > TablesPulsesPerBlock.SKP_Silk_max_pulses_table[3]) {
                    scale_down++
                }
                if (scale_down != 0) {
                    /* We need to down scale the quantization signal */
                    nRshifts[i]++
                    k = 0
                    while (k < Define.SHELL_CODEC_FRAME_LENGTH) {
                        abs_pulses_ptr[abs_pulses_ptr_offset + k] = abs_pulses_ptr[abs_pulses_ptr_offset
                                + k] shr 1
                        k++
                    }
                } else {
                    /* Jump out of while(1) loop and go to next shell coding frame */
                    break
                }
            }
            abs_pulses_ptr_offset += Define.SHELL_CODEC_FRAME_LENGTH
            i++
        }
        /** */
        /* Rate level */
        /** */
        /* find rate level that leads to fewest bits for coding of pulses per block info */
        minSumBits_Q6 = Int.MAX_VALUE
        k = 0
        while (k < Define.N_RATE_LEVELS - 1) {
            nBits_ptr = TablesPulsesPerBlock.SKP_Silk_pulses_per_block_BITS_Q6[k]
            sumBits_Q6 = TablesPulsesPerBlock.SKP_Silk_rate_levels_BITS_Q6[sigtype]!![k].toInt()
            i = 0
            while (i < iter) {
                sumBits_Q6 += if (nRshifts[i] > 0) {
                    nBits_ptr!![Define.MAX_PULSES + 1].toInt()
                } else {
                    nBits_ptr!![sum_pulses[i]].toInt()
                }
                i++
            }
            if (sumBits_Q6 < minSumBits_Q6) {
                minSumBits_Q6 = sumBits_Q6
                RateLevelIndex = k
            }
            k++
        }
        RangeCoder.SKP_Silk_range_encoder(psRC, RateLevelIndex,
                TablesPulsesPerBlock.SKP_Silk_rate_levels_CDF[sigtype], 0)
        /** */
        /* Sum-Weighted-Pulses Encoding */
        /** */
        cdf_ptr = TablesPulsesPerBlock.SKP_Silk_pulses_per_block_CDF[RateLevelIndex]
        i = 0
        while (i < iter) {
            if (nRshifts[i] == 0) {
                RangeCoder.SKP_Silk_range_encoder(psRC, sum_pulses[i], cdf_ptr, 0)
            } else {
                RangeCoder.SKP_Silk_range_encoder(psRC, Define.MAX_PULSES + 1, cdf_ptr, 0)
                k = 0
                while (k < nRshifts[i] - 1) {
                    RangeCoder.SKP_Silk_range_encoder(psRC, Define.MAX_PULSES + 1,
                            TablesPulsesPerBlock.SKP_Silk_pulses_per_block_CDF[Define.N_RATE_LEVELS - 1], 0)
                    k++
                }
                RangeCoder.SKP_Silk_range_encoder(psRC, sum_pulses[i],
                        TablesPulsesPerBlock.SKP_Silk_pulses_per_block_CDF[Define.N_RATE_LEVELS - 1], 0)
            }
            i++
        }
        /** */
        /* Shell Encoding */
        /** */
        i = 0
        while (i < iter) {
            if (sum_pulses[i] > 0) {
                ShellCoder.SKP_Silk_shell_encoder(psRC, abs_pulses, i * Define.SHELL_CODEC_FRAME_LENGTH)
            }
            i++
        }
        /** */
        /* LSB Encoding */
        /** */
        i = 0
        while (i < iter) {
            if (nRshifts[i] > 0) {
                pulses_ptr = q
                pulses_ptr_offset = i * Define.SHELL_CODEC_FRAME_LENGTH
                nLS = nRshifts[i] - 1
                k = 0
                while (k < Define.SHELL_CODEC_FRAME_LENGTH) {
                    abs_q = if (pulses_ptr!![pulses_ptr_offset + k] > 0) pulses_ptr[pulses_ptr_offset
                            + k].toInt() else -pulses_ptr[pulses_ptr_offset + k]
                    j = nLS
                    while (j > 0) {
                        bit = abs_q shr j and 1
                        RangeCoder.SKP_Silk_range_encoder(psRC, bit, TablesOther.SKP_Silk_lsb_CDF,
                                0)
                        j--
                    }
                    bit = abs_q and 1
                    RangeCoder.SKP_Silk_range_encoder(psRC, bit, TablesOther.SKP_Silk_lsb_CDF, 0)
                    k++
                }
            }
            i++
        }
        /** */
        /* Encode signs */
        /** */
        CodeSigns.SKP_Silk_encode_signs(psRC, q, frame_length, sigtype, QuantOffsetType,
                RateLevelIndex)
    }
}