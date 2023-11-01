/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * First order low-pass filter, with input as SKP_int32, running at 48 kHz
 *
 * @author Dingxin Xu
 */
object LowpassInt {
    /**
     * First order low-pass filter, with input as SKP_int32, running at 48 kHz
     *
     * @param in
     * Q25 48 kHz signal; length = len
     * @param in_offset
     * offset of valid data.
     * @param S
     * Q25 state; length = 1
     * @param S_offset
     * offset of valid data.
     * @param out
     * Q25 48 kHz signal; length = len
     * @param out_offset
     * offset of valid data.
     * @param len
     * Number of samples
     */
    fun SKP_Silk_lowpass_int(`in`: IntArray,  /* I: Q25 48 kHz signal; length = len */
            in_offset: Int, S: IntArray,  /* I/O: Q25 state; length = 1 */
            S_offset: Int, out: IntArray,  /* O: Q25 48 kHz signal; length = len */
            out_offset: Int, len: Int /* I: Number of samples */
    ) {
        var in_offset = in_offset
        var out_offset = out_offset
        var k: Int
        var in_tmp: Int
        var out_tmp: Int
        var state: Int
        state = S[S_offset + 0]
        k = len
        while (k > 0) {
            in_tmp = `in`[in_offset++]
            in_tmp -= in_tmp shr 2 /* multiply by 0.75 */
            out_tmp = state + in_tmp /* zero at nyquist */
            state = in_tmp - (out_tmp shr 1) /* pole */
            out[out_offset++] = out_tmp
            k--
        }
        S[S_offset + 0] = state
    }
}