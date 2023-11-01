/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Apply sine window to signal vector.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object ApplySineWindowFLP {
    /**
     * Apply sine window to signal vector. Window types: 0 -> sine window from 0 to pi. 1 -> sine
     * window from 0 to pi/2. 2 -> sine window from pi/2 to pi.
     *
     * @param px_win
     * Pointer to windowed signal.
     * @param px_win_offset
     * offset of valid data.
     * @param px
     * Pointer to input signal.
     * @param px_offset
     * offset of valid data.
     * @param win_type
     * Selects a window type.
     * @param length
     * Window length, multiple of 4.
     */
    // TODO float or double
    fun SKP_Silk_apply_sine_window_FLP(px_win: FloatArray,  /* O Pointer to windowed signal */
            px_win_offset: Int, px: FloatArray?,  /* I Pointer to input signal */
            px_offset: Int, win_type: Int,  /* I Selects a window type */
            length: Int /* I Window length, multiple of 4 */
    ) {
        var k: Int
        var freq: Float
        val c: Float
        var S0: Float
        var S1: Float
        assert(length and 3 == 0)
        freq = SigProcFLPConstants.PI / (length + 1)
        if (win_type == 0) {
            freq = 2.0f * freq
        }

        /* Approximation of 2 * cos(f) */
        c = 2.0f - freq * freq

        /* Initialize state */
        if (win_type < 2) {
            /* Start from 0 */
            S0 = 0.0f
            /* Approximation of sin(f) */
            S1 = freq
        } else {
            /* Start from 1 */
            S0 = 1.0f
            /* Approximation of cos(f) */
            S1 = 0.5f * c
        }

        /* Uses the recursive equation: sin(n*f) = 2 * cos(f) * sin((n-1)*f) - sin((n-2)*f) */
        /* 4 samples at a time */
        k = 0
        while (k < length) {
            px_win[px_win_offset + k + 0] = px!![px_offset + k + 0] * 0.5f * (S0 + S1)
            px_win[px_win_offset + k + 1] = px[px_offset + k + 1] * S1
            S0 = c * S1 - S0
            px_win[px_win_offset + k + 2] = px[px_offset + k + 2] * 0.5f * (S1 + S0)
            px_win[px_win_offset + k + 3] = px[px_offset + k + 3] * S0
            S1 = c * S0 - S1
            k += 4
        }
    }
}