/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Auto Generated File from generate_pitch_est_tables.m
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object PitchEstTables {
    var SKP_Silk_CB_lags_stage2 = arrayOf<ShortArray?>(shortArrayOf(0, 2, -1, -1, -1, 0, 0, 1, 1, 0, 1), shortArrayOf(0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0), shortArrayOf(0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0), shortArrayOf(0, -1, 2, 1, 0, 1, 1, 0, 0, -1, -1))
    var SKP_Silk_CB_lags_stage3 = arrayOf<ShortArray?>(shortArrayOf(-9, -7, -6, -5, -5, -4, -4, -3, -3, -2, -2, -2, -1, -1, -1, 0, 0, 0, 1, 1, 0, 1, 2, 2, 2,
            3, 3, 4, 4, 5, 6, 5, 6, 8), shortArrayOf(-3, -2, -2, -2, -1, -1, -1, -1, -1, 0, 0, -1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 1,
            1, 2, 1, 2, 2, 2, 2, 3), shortArrayOf(3, 3, 2, 2, 2, 2, 1, 2, 1, 1, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, -1, 0, 0, -1, -1,
            -1, -1, -1, -2, -2, -2), shortArrayOf(9, 8, 6, 5, 6, 5, 4, 4, 3, 3, 2, 2, 2, 1, 0, 1, 1, 0, 0, 0, -1, -1, -1, -2, -2, -2, -3,
            -3, -4, -4, -5, -5, -6, -7))
    var SKP_Silk_Lag_range_stage3 = arrayOf(arrayOf<ShortArray?>(shortArrayOf(-2, 6), shortArrayOf(-1, 5), shortArrayOf(-1, 5), shortArrayOf(-2, 7)), arrayOf<ShortArray?>(shortArrayOf(-4, 8), shortArrayOf(-1, 6), shortArrayOf(-1, 6), shortArrayOf(-4, 9)), arrayOf<ShortArray?>(shortArrayOf(-9, 12), shortArrayOf(-3, 7), shortArrayOf(-2, 7), shortArrayOf(-7, 13)))
    var SKP_Silk_cbk_sizes_stage3 = shortArrayOf(CommonPitchEstDefines.PITCH_EST_NB_CBKS_STAGE3_MIN.toShort(),
            CommonPitchEstDefines.PITCH_EST_NB_CBKS_STAGE3_MID.toShort(), CommonPitchEstDefines.PITCH_EST_NB_CBKS_STAGE3_MAX.toShort())
    var SKP_Silk_cbk_offsets_stage3 = shortArrayOf(
            (CommonPitchEstDefines.PITCH_EST_NB_CBKS_STAGE3_MAX - CommonPitchEstDefines.PITCH_EST_NB_CBKS_STAGE3_MIN shr 1).toShort(),
            (CommonPitchEstDefines.PITCH_EST_NB_CBKS_STAGE3_MAX - CommonPitchEstDefines.PITCH_EST_NB_CBKS_STAGE3_MID shr 1).toShort(), 0)
}