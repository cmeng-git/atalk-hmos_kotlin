/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Pitch analyzer function.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object DecodePitch {
    /**
     * Pitch analyzer function.
     *
     * @param lagIndex
     * @param contourIndex
     * @param pitch_lags
     * 4 pitch values.
     * @param Fs_kHz
     * sampling frequency(kHz).
     */
    fun SKP_Silk_decode_pitch(lagIndex: Int,  /* I */
            contourIndex: Int,  /* O */
            pitch_lags: IntArray?,  /* O 4 pitch values */
            Fs_kHz: Int /* I sampling frequency (kHz) */
    ) {
        val lag: Int
        var i: Int
        val min_lag = Macros.SKP_SMULBB(CommonPitchEstDefines.PITCH_EST_MIN_LAG_MS, Fs_kHz)

        /* Only for 24 / 16 kHz version for now */
        lag = min_lag + lagIndex
        if (Fs_kHz == 8) {
            /* Only a small codebook for 8 khz */
            i = 0
            while (i < CommonPitchEstDefines.PITCH_EST_NB_SUBFR) {
                pitch_lags!![i] = lag + PitchEstTables.SKP_Silk_CB_lags_stage2[i]!![contourIndex]
                i++
            }
        } else {
            i = 0
            while (i < CommonPitchEstDefines.PITCH_EST_NB_SUBFR) {
                pitch_lags!![i] = lag + PitchEstTables.SKP_Silk_CB_lags_stage3[i]!![contourIndex]
                i++
            }
        }
    }
}