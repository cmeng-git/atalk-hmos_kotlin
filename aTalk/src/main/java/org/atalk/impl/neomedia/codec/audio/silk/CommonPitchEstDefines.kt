/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Definitions For Fix pitch estimator.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object CommonPitchEstDefines {
    const val PITCH_EST_MAX_FS_KHZ = 24 /* Maximum sampling frequency used */
    const val PITCH_EST_FRAME_LENGTH_MS = 40 /* 40 ms */
    const val PITCH_EST_MAX_FRAME_LENGTH = PITCH_EST_FRAME_LENGTH_MS * PITCH_EST_MAX_FS_KHZ
    const val PITCH_EST_MAX_FRAME_LENGTH_ST_1 = PITCH_EST_MAX_FRAME_LENGTH shr 2
    const val PITCH_EST_MAX_FRAME_LENGTH_ST_2 = PITCH_EST_MAX_FRAME_LENGTH shr 1

    // TODO: PITCH_EST_SUB_FRAME is neither defined nor used, temporally ignore it;
    // static final int PITCH_EST_MAX_SF_FRAME_LENGTH = (PITCH_EST_SUB_FRAME *
    // PITCH_EST_MAX_FS_KHZ);
    const val PITCH_EST_MAX_LAG_MS = 18 /* 18 ms -> 56 Hz */
    const val PITCH_EST_MIN_LAG_MS = 2 /* 2 ms -> 500 Hz */
    const val PITCH_EST_MAX_LAG = PITCH_EST_MAX_LAG_MS * PITCH_EST_MAX_FS_KHZ
    const val PITCH_EST_MIN_LAG = PITCH_EST_MIN_LAG_MS * PITCH_EST_MAX_FS_KHZ
    const val PITCH_EST_NB_SUBFR = 4
    const val PITCH_EST_D_SRCH_LENGTH = 24
    const val PITCH_EST_MAX_DECIMATE_STATE_LENGTH = 7
    const val PITCH_EST_NB_STAGE3_LAGS = 5
    const val PITCH_EST_NB_CBKS_STAGE2 = 3
    const val PITCH_EST_NB_CBKS_STAGE2_EXT = 11
    const val PITCH_EST_CB_mn2 = 1
    const val PITCH_EST_CB_mx2 = 2
    const val PITCH_EST_NB_CBKS_STAGE3_MAX = 34
    const val PITCH_EST_NB_CBKS_STAGE3_MID = 24
    const val PITCH_EST_NB_CBKS_STAGE3_MIN = 16
}