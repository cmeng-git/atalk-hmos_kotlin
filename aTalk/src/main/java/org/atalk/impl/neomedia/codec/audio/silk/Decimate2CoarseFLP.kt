/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object Decimate2CoarseFLP {
    /* coefficients for coarser 2-fold resampling */
    var A20c_FLP = floatArrayOf(0.064666748046875f, 0.508514404296875f)
    var A21c_FLP = floatArrayOf(0.245666503906250f, 0.819732666015625f)

    /**
     * downsample by a factor 2, coarser.
     *
     * @param in
     * 16 kHz signal [2*len].
     * @param in_offset
     * offset of the valid data.
     * @param S
     * state vector [4].
     * @param S_offset
     * offset of the valid data.
     * @param out
     * 8 kHz signal [len]
     * @param out_offset
     * offset of the valid data.
     * @param scratch
     * scratch memory [3*len].
     * @param scratch_offset
     * offset of the valid data.
     * @param len
     * number of OUTPUT samples.
     */
    fun SKP_Silk_decimate2_coarse_FLP(`in`: FloatArray,  /* I: 16 kHz signal [2*len] */
            in_offset: Int, S: FloatArray,  /* I/O: state vector [4] */
            S_offset: Int, out: FloatArray,  /* O: 8 kHz signal [len] */
            out_offset: Int, scratch: FloatArray,  /* I: scratch memory [3*len] */
            scratch_offset: Int, len: Int /* I: number of OUTPUT samples */
    ) {

        /* de-interleave allpass inputs */
        var k: Int = 0
        while (k < len) {
            scratch[scratch_offset + k] = `in`[in_offset + 2 * k]
            scratch[scratch_offset + k + len] = `in`[in_offset + 2 * k + 1]
            k++
        }

        /* allpass filters */
        AllpassIntFLP.SKP_Silk_allpass_int_FLP(scratch, scratch_offset, S, S_offset, A21c_FLP[0],
                scratch, scratch_offset + 2 * len, len)
        AllpassIntFLP.SKP_Silk_allpass_int_FLP(scratch, scratch_offset + 2 * len, S, S_offset + 1,
                A21c_FLP[1], scratch, scratch_offset, len)
        AllpassIntFLP.SKP_Silk_allpass_int_FLP(scratch, scratch_offset + len, S, S_offset + 2,
                A20c_FLP[0], scratch, scratch_offset + 2 * len, len)
        AllpassIntFLP.SKP_Silk_allpass_int_FLP(scratch, scratch_offset + 2 * len, S, S_offset + 3,
                A20c_FLP[1], scratch, scratch_offset + len, len)

        /* add two allpass outputs */
        k = 0
        while (k < len) {
            out[out_offset + k] = 0.5f * (scratch[scratch_offset + k] + scratch[scratch_offset + k
                    + len])
            k++
        }
    }
}