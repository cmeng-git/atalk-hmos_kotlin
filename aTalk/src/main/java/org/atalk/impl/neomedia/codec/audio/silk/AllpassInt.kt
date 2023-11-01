/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * First-order allpass filter with transfer function:
 *
 * A + Z^(-1) H(z) = ------------ 1 + A*Z^(-1)
 *
 * Implemented using minimum multiplier filter design.
 *
 * Reference: http://www.univ.trieste.it/~ramponi/teaching/ DSP/materiale/Ch6(2).pdf
 *
 * @author Dingxin Xu
 */
object AllpassInt {
    /**
     * First-order allpass filter.
     *
     * @param in
     * Q25 input signal [len]
     * @param in_offset
     * offset of valid data.
     * @param S
     * Q25 state [1]
     * @param S_offset
     * offset of valid data.
     * @param A
     * Q15 coefficient (0 <= A < 32768)
     * @param out
     * Q25 output signal [len]
     * @param out_offset
     * offset of valid data.
     * @param len
     * Number of samples
     */
    fun SKP_Silk_allpass_int(`in`: IntArray,  /* I: Q25 input signal [len] */
            in_offset: Int, S: IntArray,  /* I/O: Q25 state [1] */
            S_offset: Int, A: Int,  /* I: Q15 coefficient (0 <= A < 32768) */
            out: IntArray,  /* O: Q25 output signal [len] */
            out_offset: Int, len: Int /* I: Number of samples */
    ) {
        var in_offset = in_offset
        var out_offset = out_offset
        var Y2: Int
        var X2: Int
        var S0: Int
        var k: Int
        S0 = S[S_offset]
        k = len - 1
        while (k >= 0) {
            Y2 = `in`[in_offset] - S0
            X2 = (Y2 shr 15) * A + ((Y2 and 0x00007FFF) * A shr 15)
            out[out_offset++] = S0 + X2
            S0 = `in`[in_offset++] + X2
            k--
        }
        S[S_offset] = S0
    }
}