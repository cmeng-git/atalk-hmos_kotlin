/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * shell coder; pulse-subframe length is hardcoded.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object ShellCoder {
    /**
     *
     * @param out
     * combined pulses vector [len]
     * @param out_offset
     * offset of valid data.
     * @param in
     * input vector [2 * len]
     * @param in_offset
     * offset of valid data.
     * @param len
     * number of OUTPUT samples
     */
    fun combine_pulses(out: IntArray,  /* O: combined pulses vector [len] */
            out_offset: Int, `in`: IntArray,  /* I: input vector [2 * len] */
            in_offset: Int, len: Int /* I: number of OUTPUT samples */
    ) {
        var k: Int
        k = 0
        while (k < len) {
            out[out_offset + k] = `in`[in_offset + 2 * k] + `in`[in_offset + 2 * k + 1]
            k++
        }
    }

    /**
     *
     * @param sRC
     * compressor data structure
     * @param p_child1
     * pulse amplitude of first child subframe
     * @param p
     * pulse amplitude of current subframe
     * @param shell_table
     * table of shell cdfs
     */
    fun encode_split(sRC: SKP_Silk_range_coder_state?,  /* I/O: compressor data structure */
            p_child1: Int,  /* I: pulse amplitude of first child subframe */
            p: Int,  /* I: pulse amplitude of current subframe */
            shell_table: IntArray?) {
        val cdf: IntArray?
        val cdf_offset: Int
        if (p > 0) {
            cdf = shell_table
            cdf_offset = TablesPulsesPerBlock.SKP_Silk_shell_code_table_offsets[p]
            RangeCoder.SKP_Silk_range_encoder(sRC, p_child1, cdf, cdf_offset)
        }
    }

    /**
     *
     * @param p_child1
     * pulse amplitude of first child subframe
     * @param p_child1_offset
     * offset of valid data.
     * @param p_child2
     * pulse amplitude of second child subframe
     * @param p_child2_offset
     * offset of valid data.
     * @param sRC
     * compressor data structure
     * @param p
     * pulse amplitude of current subframe
     * @param shell_table
     * table of shell cdfs
     */
    fun decode_split(p_child1: IntArray,  /* O: pulse amplitude of first child subframe */
            p_child1_offset: Int, p_child2: IntArray,  /* O: pulse amplitude of second child subframe */
            p_child2_offset: Int, sRC: SKP_Silk_range_coder_state?,  /* I/O: compressor data structure */
            p: Int,  /* I: pulse amplitude of current subframe */
            shell_table: IntArray? /* I: table of shell cdfs */
    ) {
        val cdf_middle: Int
        val cdf: IntArray?
        val cdf_offset: Int
        if (p > 0) {
            cdf_middle = p shr 1
            cdf = shell_table
            cdf_offset = TablesPulsesPerBlock.SKP_Silk_shell_code_table_offsets[p]
            RangeCoder.SKP_Silk_range_decoder(p_child1, p_child1_offset, sRC, cdf, cdf_offset,
                    cdf_middle)
            p_child2[p_child2_offset + 0] = p - p_child1[p_child1_offset + 0]
        } else {
            p_child1[p_child1_offset + 0] = 0
            p_child2[p_child2_offset + 0] = 0
        }
    }

    /**
     * Shell encoder, operates on one shell code frame of 16 pulses.
     *
     * @param sRC
     * compressor data structure.
     * @param pulses0
     * data: nonnegative pulse amplitudes.
     * @param pulses0_offset
     * valid data
     */
    fun SKP_Silk_shell_encoder(sRC: SKP_Silk_range_coder_state?,  /*
																		 * I/O compressor data
																		 * structure
																		 */
            pulses0: IntArray,  /* I data: nonnegative pulse amplitudes */
            pulses0_offset: Int) {
        val pulses1 = IntArray(8)
        val pulses2 = IntArray(4)
        val pulses3 = IntArray(2)
        val pulses4 = IntArray(1)
        assert(Define.SHELL_CODEC_FRAME_LENGTH == 16)

        /* tree representation per pulse-subframe */
        combine_pulses(pulses1, 0, pulses0, pulses0_offset, 8)
        combine_pulses(pulses2, 0, pulses1, 0, 4)
        combine_pulses(pulses3, 0, pulses2, 0, 2)
        combine_pulses(pulses4, 0, pulses3, 0, 1)
        encode_split(sRC, pulses3[0], pulses4[0], TablesPulsesPerBlock.SKP_Silk_shell_code_table3)
        encode_split(sRC, pulses2[0], pulses3[0], TablesPulsesPerBlock.SKP_Silk_shell_code_table2)
        encode_split(sRC, pulses1[0], pulses2[0], TablesPulsesPerBlock.SKP_Silk_shell_code_table1)
        encode_split(sRC, pulses0[pulses0_offset + 0], pulses1[0],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table0)
        encode_split(sRC, pulses0[pulses0_offset + 2], pulses1[1],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table0)
        encode_split(sRC, pulses1[2], pulses2[1], TablesPulsesPerBlock.SKP_Silk_shell_code_table1)
        encode_split(sRC, pulses0[pulses0_offset + 4], pulses1[2],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table0)
        encode_split(sRC, pulses0[pulses0_offset + 6], pulses1[3],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table0)
        encode_split(sRC, pulses2[2], pulses3[1], TablesPulsesPerBlock.SKP_Silk_shell_code_table2)
        encode_split(sRC, pulses1[4], pulses2[2], TablesPulsesPerBlock.SKP_Silk_shell_code_table1)
        encode_split(sRC, pulses0[pulses0_offset + 8], pulses1[4],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table0)
        encode_split(sRC, pulses0[pulses0_offset + 10], pulses1[5],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table0)
        encode_split(sRC, pulses1[6], pulses2[3], TablesPulsesPerBlock.SKP_Silk_shell_code_table1)
        encode_split(sRC, pulses0[pulses0_offset + 12], pulses1[6],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table0)
        encode_split(sRC, pulses0[pulses0_offset + 14], pulses1[7],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table0)
    }

    /**
     * Shell decoder, operates on one shell code frame of 16 pulses.
     *
     * @param pulses0
     * data: nonnegative pulse amplitudes
     * @param pulses0_offset
     * offset of valid data.
     * @param sRC
     * compressor data structure
     * @param pulses4
     * number of pulses per pulse-subframe
     */
    fun SKP_Silk_shell_decoder(pulses0: IntArray,  /* O data: nonnegative pulse amplitudes */
            pulses0_offset: Int, sRC: SKP_Silk_range_coder_state?,  /* I/O compressor data structure */
            pulses4: Int /* I number of pulses per pulse-subframe */
    ) {
        val pulses3 = IntArray(2)
        val pulses2 = IntArray(4)
        val pulses1 = IntArray(8)

        /* this function operates on one shell code frame of 16 pulses */
        Typedef.SKP_assert(Define.SHELL_CODEC_FRAME_LENGTH == 16)
        decode_split(pulses3, 0, pulses3, 1, sRC, pulses4,
                TablesPulsesPerBlock.SKP_Silk_shell_code_table3)
        decode_split(pulses2, 0, pulses2, 1, sRC, pulses3[0],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table2)
        decode_split(pulses1, 0, pulses1, 1, sRC, pulses2[0],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table1)
        decode_split(pulses0, pulses0_offset + 0, pulses0, pulses0_offset + 1, sRC, pulses1[0],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table0)
        decode_split(pulses0, pulses0_offset + 2, pulses0, pulses0_offset + 3, sRC, pulses1[1],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table0)
        decode_split(pulses1, 2, pulses1, 3, sRC, pulses2[1],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table1)
        decode_split(pulses0, pulses0_offset + 4, pulses0, pulses0_offset + 5, sRC, pulses1[2],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table0)
        decode_split(pulses0, pulses0_offset + 6, pulses0, pulses0_offset + 7, sRC, pulses1[3],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table0)
        decode_split(pulses2, 2, pulses2, 3, sRC, pulses3[1],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table2)
        decode_split(pulses1, 4, pulses1, 5, sRC, pulses2[2],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table1)
        decode_split(pulses0, pulses0_offset + 8, pulses0, pulses0_offset + 9, sRC, pulses1[4],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table0)
        decode_split(pulses0, pulses0_offset + 10, pulses0, pulses0_offset + 11, sRC, pulses1[5],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table0)
        decode_split(pulses1, 6, pulses1, 7, sRC, pulses2[3],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table1)
        decode_split(pulses0, pulses0_offset + 12, pulses0, pulses0_offset + 13, sRC, pulses1[6],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table0)
        decode_split(pulses0, pulses0_offset + 14, pulses0, pulses0_offset + 15, sRC, pulses1[7],
                TablesPulsesPerBlock.SKP_Silk_shell_code_table0)
    }
}