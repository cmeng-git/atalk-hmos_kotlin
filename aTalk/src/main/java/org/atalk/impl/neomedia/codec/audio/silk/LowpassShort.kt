/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * First order low-pass filter, with input as SKP_int16, running at 48 kHz
 *
 * @author Dingxin Xu
 */
object LowpassShort {
    /**
     * First order low-pass filter, with input as SKP_int16, running at 48 kHz.
     *
     * @param in
     * Q15 48 kHz signal; [len]
     * @param in_offset
     * offset of valid data.
     * @param S
     * Q25 state; length = 1
     * @param S_offset
     * offset of valid data.
     * @param out
     * Q25 48 kHz signal; [len]
     * @param out_offset
     * offset of valid data.
     * @param len
     * Signal length
     */
    fun SKP_Silk_lowpass_short(`in`: ShortArray,  /* I: Q15 48 kHz signal; [len] */
            in_offset: Int, S: IntArray,  /* I/O: Q25 state; length = 1 */
            S_offset: Int, out: IntArray,  /* O: Q25 48 kHz signal; [len] */
            out_offset: Int, len: Int /* O: Signal length */
    ) {
        var k: Int
        var in_tmp: Int
        var out_tmp: Int
        var state: Int
        state = S[S_offset + 0]
        k = 0
        while (k < len) {
            in_tmp = 768 * `in`[in_offset + k] /* multiply by 0.75, going from Q15 to Q25 */
            out_tmp = state + in_tmp /* zero at nyquist */
            state = in_tmp - (out_tmp shr 1) /* pole */
            out[out_offset + k] = out_tmp
            k++
        }
        S[S_offset + 0] = state
    }
}