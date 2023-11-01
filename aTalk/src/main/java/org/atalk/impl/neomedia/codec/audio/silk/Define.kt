/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * This class contains a number of defines that controls the operation of SILK. Most of these should
 * be left alone for ensuring proper operation. However, a few can be changed if operation different
 * from the default is desired.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object Define {
    const val MAX_FRAMES_PER_PACKET = 5

    /* MAX DELTA LAG used for multiframe packets */
    const val MAX_DELTA_LAG = 10

    /* Lower limit on bitrate for each mode */
    const val MIN_TARGET_RATE_NB_BPS = 5000
    const val MIN_TARGET_RATE_MB_BPS = 7000
    const val MIN_TARGET_RATE_WB_BPS = 8000
    const val MIN_TARGET_RATE_SWB_BPS = 20000

    /* Transition bitrates between modes */
    const val SWB2WB_BITRATE_BPS = 26000
    const val WB2SWB_BITRATE_BPS = 32000
    const val WB2MB_BITRATE_BPS = 15000
    const val MB2WB_BITRATE_BPS = 20000
    const val MB2NB_BITRATE_BPS = 10000
    const val NB2MB_BITRATE_BPS = 14000

    /* Integration/hysteresis threshold for lowering internal sample frequency */ /*
	 * 30000000 -> 6 sec if bitrate is 5000 bps below limit; 3 sec if bitrate is 10000 bps below
	 * limit
	 */
    const val ACCUM_BITS_DIFF_THRESHOLD = 30000000
    const val TARGET_RATE_TAB_SZ = 8

    /* DTX settings */
    const val NO_SPEECH_FRAMES_BEFORE_DTX = 5 /* eq 100 ms */
    const val MAX_CONSECUTIVE_DTX = 20 /* eq 400 ms */
    const val USE_LBRR = 1

    /* Amount of concecutive no FEC packets before telling JB */
    const val NO_LBRR_THRES = 10

    /* Maximum delay between real packet and LBRR packet */
    const val MAX_LBRR_DELAY = 2
    const val LBRR_IDX_MASK = 1
    const val INBAND_FEC_MIN_RATE_BPS = 18000 /*
													 * Dont use inband FEC below this total target
													 * rate
													 */
    const val LBRR_LOSS_THRES = 2 /* Start adding LBRR at this loss rate (needs tuning) */

    /* LBRR usage defines */
    const val SKP_SILK_NO_LBRR = 0 /* No LBRR information for this packet */
    const val SKP_SILK_ADD_LBRR_TO_PLUS1 = 1 /* Add LBRR for this packet to packet n + 1 */
    const val SKP_SILK_ADD_LBRR_TO_PLUS2 = 2 /* Add LBRR for this packet to packet n + 2 */

    /* Frame termination indicator defines */
    const val SKP_SILK_LAST_FRAME = 0 /* Last frames in packet */
    const val SKP_SILK_MORE_FRAMES = 1 /* More frames to follow this one */
    const val SKP_SILK_LBRR_VER1 = 2 /* LBRR information from packet n - 1 */
    const val SKP_SILK_LBRR_VER2 = 3 /* LBRR information from packet n - 2 */
    const val SKP_SILK_EXT_LAYER = 4 /* Extension layers added */

    /* Number of Second order Sections for SWB detection HP filter */
    const val NB_SOS = 3
    const val HP_8_KHZ_THRES = 10 /* average energy per sample, above 8 kHz */
    const val CONCEC_SWB_SMPLS_THRES = 480 * 15 /* 300 ms */
    const val WB_DETECT_ACTIVE_SPEECH_MS_THRES = 15000 /*
																 * ms of active speech needed for WB
																 * detection
																 */

    /* Low complexity setting */
    const val LOW_COMPLEXITY_ONLY = false

    /* Activate bandwidth transition filtering for mode switching */
    const val SWITCH_TRANSITION_FILTERING = 1

    /* Decoder Parameters */
    const val DEC_HP_ORDER = 2

    /* Maximum sampling frequency, should be 16 for embedded */
    const val MAX_FS_KHZ = 24
    const val MAX_API_FS_KHZ = 48

    /* Signal Types used by silk */
    const val SIG_TYPE_VOICED = 0
    const val SIG_TYPE_UNVOICED = 1

    /* VAD Types used by silk */
    const val NO_VOICE_ACTIVITY = 0
    const val VOICE_ACTIVITY = 1

    /* Number of samples per frame */
    const val FRAME_LENGTH_MS = 20 /* 20 ms */
    const val MAX_FRAME_LENGTH = FRAME_LENGTH_MS * MAX_FS_KHZ

    /* Milliseconds of lookahead for pitch analysis */
    const val LA_PITCH_MS = 3
    const val LA_PITCH_MAX = LA_PITCH_MS * MAX_FS_KHZ

    /* Milliseconds of lookahead for noise shape analysis */
    const val LA_SHAPE_MS = 5
    const val LA_SHAPE_MAX = LA_SHAPE_MS * MAX_FS_KHZ

    /* Order of LPC used in find pitch */
    const val FIND_PITCH_LPC_ORDER_MAX = 16

    /* Length of LPC window used in find pitch */
    const val FIND_PITCH_LPC_WIN_MS = 30 + (LA_PITCH_MS shl 1)
    const val FIND_PITCH_LPC_WIN_MAX = FIND_PITCH_LPC_WIN_MS * MAX_FS_KHZ
    val PITCH_EST_COMPLEXITY_HC_MODE = SigProcFIXConstants.SKP_Silk_PITCH_EST_MAX_COMPLEX
    val PITCH_EST_COMPLEXITY_MC_MODE = SigProcFIXConstants.SKP_Silk_PITCH_EST_MID_COMPLEX
    val PITCH_EST_COMPLEXITY_LC_MODE = SigProcFIXConstants.SKP_Silk_PITCH_EST_MIN_COMPLEX

    /* Max number of bytes in payload output buffer (may contain multiple frames) */
    const val MAX_ARITHM_BYTES = 1024
    const val RANGE_CODER_WRITE_BEYOND_BUFFER = -1
    const val RANGE_CODER_CDF_OUT_OF_RANGE = -2
    const val RANGE_CODER_NORMALIZATION_FAILED = -3
    const val RANGE_CODER_ZERO_INTERVAL_WIDTH = -4
    const val RANGE_CODER_DECODER_CHECK_FAILED = -5
    const val RANGE_CODER_READ_BEYOND_BUFFER = -6
    const val RANGE_CODER_ILLEGAL_SAMPLING_RATE = -7
    const val RANGE_CODER_DEC_PAYLOAD_TOO_LONG = -8

    /* dB level of lowest gain quantization level */
    const val MIN_QGAIN_DB = 6

    /* dB level of highest gain quantization level */
    const val MAX_QGAIN_DB = 86

    /* Number of gain quantization levels */
    const val N_LEVELS_QGAIN = 64

    /* Max increase in gain quantization index */
    const val MAX_DELTA_GAIN_QUANT = 40

    /* Max decrease in gain quantization index */
    const val MIN_DELTA_GAIN_QUANT = -4

    /* Quantization offsets (multiples of 4) */
    const val OFFSET_VL_Q10 = 32
    const val OFFSET_VH_Q10 = 100
    const val OFFSET_UVL_Q10 = 100
    const val OFFSET_UVH_Q10 = 256

    /* Maximum numbers of iterations used to stabilize a LPC vector */
    const val MAX_LPC_STABILIZE_ITERATIONS = 20
    const val MAX_LPC_ORDER = 16
    const val MIN_LPC_ORDER = 10

    /* Find Pred Coef defines */
    const val LTP_ORDER = 5

    /* LTP quantization settings */
    const val NB_LTP_CBKS = 3

    /* Number of subframes */
    const val NB_SUBFR = 4

    /* Flag to use harmonic noise shaping */
    const val USE_HARM_SHAPING = 1

    /* Max LPC order of noise shaping filters */
    const val SHAPE_LPC_ORDER_MAX = 16
    const val HARM_SHAPE_FIR_TAPS = 3

    /* Length of LPC window used in noise shape analysis */
    const val SHAPE_LPC_WIN_MS = 15
    const val SHAPE_LPC_WIN_16_KHZ = SHAPE_LPC_WIN_MS * 16
    const val SHAPE_LPC_WIN_24_KHZ = SHAPE_LPC_WIN_MS * 24
    const val SHAPE_LPC_WIN_MAX = SHAPE_LPC_WIN_MS * MAX_FS_KHZ

    /* Maximum number of delayed decision states */
    const val DEL_DEC_STATES_MAX = 4
    const val LTP_BUF_LENGTH = 512
    const val LTP_MASK = LTP_BUF_LENGTH - 1
    const val DECISION_DELAY = 32
    const val DECISION_DELAY_MASK = DECISION_DELAY - 1

    /* number of subframes for excitation entropy coding */
    const val SHELL_CODEC_FRAME_LENGTH = 16
    const val MAX_NB_SHELL_BLOCKS = MAX_FRAME_LENGTH / SHELL_CODEC_FRAME_LENGTH

    /* number of rate levels, for entropy coding of excitation */
    const val N_RATE_LEVELS = 10

    /* maximum sum of pulses per shell coding frame */
    const val MAX_PULSES = 18
    const val MAX_MATRIX_SIZE = MAX_LPC_ORDER /* Max of LPC Order and LTP order */
    fun NSQ_LPC_BUF_LENGTH(): Int {
        return if (MAX_LPC_ORDER > DECISION_DELAY) MAX_LPC_ORDER else DECISION_DELAY
    }
    /** */ /* High pass filtering */
    /** */
    const val HIGH_PASS_INPUT = true
    /** */ /* Voice activity detector */
    /** */
    const val VAD_N_BANDS = 4 /* 0-1, 1-2, 2-4, and 4-8 kHz */
    const val VAD_INTERNAL_SUBFRAMES_LOG2 = 2
    const val VAD_INTERNAL_SUBFRAMES = 1 shl VAD_INTERNAL_SUBFRAMES_LOG2
    const val VAD_NOISE_LEVEL_SMOOTH_COEF_Q16 = 1024 /* Must be < 4096 */
    const val VAD_NOISE_LEVELS_BIAS = 50

    /* Sigmoid settings */
    const val VAD_NEGATIVE_OFFSET_Q5 = 128 /* sigmoid is 0 at -128 */
    const val VAD_SNR_FACTOR_Q16 = 45000

    /* smoothing for SNR measurement */
    const val VAD_SNR_SMOOTH_COEF_Q18 = 4096

    /* NLSF quantizer */
    const val NLSF_MSVQ_MAX_CB_STAGES = 10 /* Update manually when changing codebooks */
    const val NLSF_MSVQ_MAX_VECTORS_IN_STAGE = 128 /* Update manually when changing codebooks */
    const val NLSF_MSVQ_MAX_VECTORS_IN_STAGE_TWO_TO_END = 16 /*
																	 * Update manually when changing
																	 * codebooks
																	 */
    const val NLSF_MSVQ_FLUCTUATION_REDUCTION = true
    const val MAX_NLSF_MSVQ_SURVIVORS = 16
    const val MAX_NLSF_MSVQ_SURVIVORS_LC_MODE = 2
    const val MAX_NLSF_MSVQ_SURVIVORS_MC_MODE = 4

    /* Based on above defines, calculate how much memory is necessary to allocate */
    fun NLSF_MSVQ_TREE_SEARCH_MAX_VECTORS_EVALUATED_LC_MODE(): Int {
        return if (NLSF_MSVQ_MAX_VECTORS_IN_STAGE > MAX_NLSF_MSVQ_SURVIVORS_LC_MODE * NLSF_MSVQ_MAX_VECTORS_IN_STAGE_TWO_TO_END) NLSF_MSVQ_MAX_VECTORS_IN_STAGE else MAX_NLSF_MSVQ_SURVIVORS_LC_MODE * NLSF_MSVQ_MAX_VECTORS_IN_STAGE_TWO_TO_END
    }

    fun NLSF_MSVQ_TREE_SEARCH_MAX_VECTORS_EVALUATED(): Int {
        return if (NLSF_MSVQ_MAX_VECTORS_IN_STAGE > MAX_NLSF_MSVQ_SURVIVORS * NLSF_MSVQ_MAX_VECTORS_IN_STAGE_TWO_TO_END) NLSF_MSVQ_MAX_VECTORS_IN_STAGE else MAX_NLSF_MSVQ_SURVIVORS * NLSF_MSVQ_MAX_VECTORS_IN_STAGE_TWO_TO_END
    }

    const val NLSF_MSVQ_SURV_MAX_REL_RD = 4

    /* Transition filtering for mode switching */
    const val TRANSITION_TIME_UP_MS = 5120 // 5120 = 64 * FRAME_LENGTH_MS * (

    // TRANSITION_INT_NUM - 1 ) =
    // 64*(20*4)
    const val TRANSITION_TIME_DOWN_MS = 2560 // 2560 = 32 * FRAME_LENGTH_MS * (

    // TRANSITION_INT_NUM - 1 ) =
    // 32*(20*4)
    const val TRANSITION_NB = 3 /* Hardcoded in tables */
    const val TRANSITION_NA = 2 /* Hardcoded in tables */
    const val TRANSITION_INT_NUM = 5 /* Hardcoded in tables */
    const val TRANSITION_FRAMES_UP = TRANSITION_TIME_UP_MS / FRAME_LENGTH_MS
    const val TRANSITION_FRAMES_DOWN = TRANSITION_TIME_DOWN_MS / FRAME_LENGTH_MS
    const val TRANSITION_INT_STEPS_UP = TRANSITION_FRAMES_UP / (TRANSITION_INT_NUM - 1)
    const val TRANSITION_INT_STEPS_DOWN = TRANSITION_FRAMES_DOWN / (TRANSITION_INT_NUM - 1)

    // TODO:no need to convert from C to Java?
    /* Row based */
    // #define matrix_ptr(Matrix_base_adr, row, column, N) *(Matrix_base_adr + ((row)*(N)+(column)))
    // #define matrix_adr(Matrix_base_adr, row, column, N) (Matrix_base_adr + ((row)*(N)+(column)))
    /* Column based */
    // #ifndef matrix_c_ptr
    // # define matrix_c_ptr(Matrix_base_adr, row, column, M) *(Matrix_base_adr +
    // ((row)+(M)*(column)))
    // #endif
    // #define matrix_c_adr(Matrix_base_adr, row, column, M) (Matrix_base_adr +
    // ((row)+(M)*(column)))
    /* BWE factors to apply after packet loss */
    const val BWE_AFTER_LOSS_Q16 = 63570

    /* Defines for CN generation */
    const val CNG_BUF_MASK_MAX = 255 /* 2^floor(log2(MAX_FRAME_LENGTH)) */
    const val CNG_GAIN_SMTH_Q16 = 4634 /* 0.25^(1/4) */
    const val CNG_NLSF_SMTH_Q16 = 16348 /* 0.25 */
}