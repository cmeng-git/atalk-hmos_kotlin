/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * first-order allpass filter.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object AllpassIntFLP {
    /**
     * first-order allpass filter.
     *
     * @param in
     * input signal [len].
     * @param in_offset
     * offset of valid data.
     * @param S
     * I/O: state [1].
     * @param S_offset
     * offset of valid data.
     * @param A
     * coefficient (0 <= A < 1).
     * @param out
     * output signal [len].
     * @param out_offset
     * offset of valid data.
     * @param len
     * number of samples.
     */
    // TODO:float or double ???
    fun SKP_Silk_allpass_int_FLP(`in`: FloatArray,  /* I: input signal [len] */
            in_offset: Int, S: FloatArray,  /* I/O: state [1] */
            S_offset: Int, A: Float,  /* I: coefficient (0 <= A < 1) */
            out: FloatArray,  /* O: output signal [len] */
            out_offset: Int, len: Int /* I: number of samples */
    ) {
        var in_offset = in_offset
        var out_offset = out_offset
        var Y2: Float
        var X2: Float
        var S0: Float
        var k: Int
        S0 = S[S_offset]
        k = len - 1
        while (k >= 0) {
            Y2 = `in`[in_offset] - S0
            X2 = Y2 * A
            out[out_offset++] = S0 + X2
            S0 = `in`[in_offset++] + X2
            k--
        }
        S[S_offset] = S0
    }
}