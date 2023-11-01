/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

import java.util.*

/**
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class Structs

/**
 * Noise shaping quantization state.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_nsq_state : Cloneable {
    var xq = ShortArray(2 * Define.MAX_FRAME_LENGTH) /* Buffer for quantized output signal */
    var sLTP_shp_Q10 = IntArray(2 * Define.MAX_FRAME_LENGTH)
    var sLPC_Q14 = IntArray(Define.MAX_FRAME_LENGTH / Define.NB_SUBFR + Define.MAX_LPC_ORDER)
    var sAR2_Q14 = IntArray(Define.SHAPE_LPC_ORDER_MAX)
    var sLF_AR_shp_Q12 = 0
    var lagPrev = 0
    var sLTP_buf_idx = 0
    var sLTP_shp_buf_idx = 0
    var rand_seed = 0
    var prev_inv_gain_Q16 = 0
    var rewhite_flag = 0

    /**
     * override clone mthod.
     */
    // TODO:
    public override fun clone(): Any {
        var clone: SKP_Silk_nsq_state? = null
        try {
            clone = super.clone() as SKP_Silk_nsq_state
        } catch (e: CloneNotSupportedException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        return clone!!
    }

    /**
     * set all fields of the instance to zero
     */
    fun memZero() {
        Arrays.fill(sAR2_Q14, 0)
        Arrays.fill(sLPC_Q14, 0)
        Arrays.fill(sLTP_shp_Q10, 0)
        Arrays.fill(xq, 0.toShort())
        lagPrev = 0
        prev_inv_gain_Q16 = 0
        rand_seed = 0
        rewhite_flag = 0
        sLF_AR_shp_Q12 = 0
        sLTP_buf_idx = 0
        sLTP_shp_buf_idx = 0
    }
} /* FIX */

/**
 * Class for Low BitRate Redundant (LBRR) information.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_SILK_LBRR_struct {
    var payload = ByteArray(Define.MAX_ARITHM_BYTES)
    var nBytes /* Number of bytes in payload */ = 0
    var usage /* Tells how the payload should be used as FEC */ = 0
    fun memZero() {
        nBytes = 0
        usage = 0
        Arrays.fill(payload, 0.toByte())
    }
}

/**
 * VAD state.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_VAD_state {
    var AnaState = IntArray(2) /* Analysis filterbank state: 0-8 kHz */
    var AnaState1 = IntArray(2) /* Analysis filterbank state: 0-4 kHz */
    var AnaState2 = IntArray(2) /* Analysis filterbank state: 0-2 kHz */
    var XnrgSubfr = IntArray(Define.VAD_N_BANDS) /* Subframe energies */
    var NrgRatioSmth_Q8 = IntArray(Define.VAD_N_BANDS) /* Smoothed energy level in each band */
    var HPstate /* State of differentiator in the lowest band */: Short = 0
    var NL = IntArray(Define.VAD_N_BANDS) /* Noise energy level in each band */
    var inv_NL = IntArray(Define.VAD_N_BANDS) /* Inverse noise energy level in each band */
    var NoiseLevelBias = IntArray(Define.VAD_N_BANDS) /* Noise level estimator bias/offset */
    var counter /* Frame counter used in the initial phase */ = 0
}

/**
 * Range encoder/decoder state.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_range_coder_state {
    var bufferLength = 0
    var bufferIx = 0
    var base_Q32: Long = 0
    var range_Q16: Long = 0
    var error = 0
    var buffer = ByteArray(Define.MAX_ARITHM_BYTES) /* Buffer containing payload */
}

/**
 * Input frequency range detection struct.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_detect_SWB_state {
    var S_HP_8_kHz = Array<IntArray?>(Define.NB_SOS) { IntArray(2) } /* HP filter State */
    var ConsecSmplsAboveThres = 0
    var ActiveSpeech_ms /* Accumulated time with active speech */ = 0
    var SWB_detected /* Flag to indicate SWB input */ = 0
    var WB_detected /* Flag to indicate WB input */ = 0
}

/**
 * Variable cut-off low-pass filter state.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_LP_state {
    var In_LP_State = IntArray(2) /* Low pass filter state */
    var transition_frame_no /* Counter which is mapped to a cut-off frequency */ = 0
    var mode /* Operating mode, 0: switch down, 1: switch up */ = 0
}

/**
 * Class for one stage of MSVQ.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_NLSF_CBS {
    constructor(nVectors: Int, CB_NLSF_Q15: ShortArray, Rates_Q5: ShortArray) {
        this.CB_NLSF_Q15 = CB_NLSF_Q15
        this.nVectors = nVectors
        this.Rates_Q5 = Rates_Q5
    }

    constructor(nVectors: Int, SKP_Silk_NLSF_MSVQ_CB0_10_Q15: ShortArray, Q15_offset: Int,
            SKP_Silk_NLSF_MSVQ_CB0_10_rates_Q5: ShortArray, Q5_offset: Int) {
        this.nVectors = nVectors
        CB_NLSF_Q15 = ShortArray(SKP_Silk_NLSF_MSVQ_CB0_10_Q15.size - Q15_offset)
        System.arraycopy(SKP_Silk_NLSF_MSVQ_CB0_10_Q15, Q15_offset, CB_NLSF_Q15, 0,
                CB_NLSF_Q15.size)
        Rates_Q5 = ShortArray(SKP_Silk_NLSF_MSVQ_CB0_10_rates_Q5.size - Q5_offset)
        System.arraycopy(SKP_Silk_NLSF_MSVQ_CB0_10_rates_Q5, Q5_offset, Rates_Q5, 0,
                Rates_Q5.size)
    }

    constructor() : super() {}

    // TODO: the three fields are constant in C.
    var nVectors = 0
    lateinit var CB_NLSF_Q15: ShortArray
    lateinit var Rates_Q5: ShortArray
}

/**
 * Class containing NLSF MSVQ codebook.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_NLSF_CB_struct {
    constructor(nStates: Int, CBStages: Array<SKP_Silk_NLSF_CBS?>, NDeltaMin_Q15: IntArray,
            CDF: IntArray, StartPtr: Array<IntArray>, MiddleIx: IntArray) {
        this.CBStages = CBStages
        this.CDF = CDF
        this.MiddleIx = MiddleIx
        this.NDeltaMin_Q15 = NDeltaMin_Q15
        nStages = nStates
        this.StartPtr = StartPtr
    }

    constructor() : super() {}

    // TODO: this filed is constant in C.
    var nStages = 0

    /* Fields for (de)quantizing */
    // TODO:CBStates should be defined as an array or an object reference?
    lateinit var CBStages: Array<SKP_Silk_NLSF_CBS?>
    lateinit var NDeltaMin_Q15: IntArray

    /* Fields for arithmetic (de)coding */
    lateinit var CDF: IntArray
    lateinit var StartPtr: Array<IntArray>
    lateinit var MiddleIx: IntArray
}

/**
 * Encoder state.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_encoder_state {
    var sRC = SKP_Silk_range_coder_state() /* Range coder state */
    var sRC_LBRR = SKP_Silk_range_coder_state() /*
																			 * Range coder state
																			 * (for low bitrate
																			 * redundancy)
																			 */
    var In_HP_State = IntArray(2) /* High pass filter state */
    var sLP = SKP_Silk_LP_state() /* Low pass filter state */
    var sVAD = SKP_Silk_VAD_state() /* Voice activity detector state */
    var LBRRprevLastGainIndex = 0
    var prev_sigtype = 0
    var typeOffsetPrev /* Previous signal type and quantization offset */ = 0
    var prevLag = 0
    var prev_lagIndex = 0
    var API_fs_Hz /* API sampling frequency (Hz) */ = 0
    var prev_API_fs_Hz /* Previous API sampling frequency (Hz) */ = 0
    var maxInternal_fs_kHz /* Maximum internal sampling frequency (kHz) */ = 0
    var fs_kHz /* Internal sampling frequency (kHz) */ = 0
    var fs_kHz_changed /* Did we switch yet? */ = 0
    var frame_length /* Frame length (samples) */ = 0
    var subfr_length /* Subframe length (samples) */ = 0
    var la_pitch /* Look-ahead for pitch analysis (samples) */ = 0
    var la_shape /* Look-ahead for noise shape analysis (samples) */ = 0
    var TargetRate_bps /* Target bitrate (bps) */ = 0
    var PacketSize_ms /* Number of milliseconds to put in each packet */ = 0
    var PacketLoss_perc /* Packet loss rate measured by farend */ = 0
    var frameCounter = 0
    var Complexity /* Complexity setting: 0-> low; 1-> medium; 2->high */ = 0
    var nStatesDelayedDecision /* Number of states in delayed decision quantization */ = 0
    var useInterpolatedNLSFs /* Flag for using NLSF interpolation */ = 0
    var shapingLPCOrder /* Filter order for noise shaping filters */ = 0
    var predictLPCOrder /* Filter order for prediction filters */ = 0
    var pitchEstimationComplexity /* Complexity level for pitch estimator */ = 0
    var pitchEstimationLPCOrder /* Whitening filter order for pitch estimator */ = 0
    var LTPQuantLowComplexity /* Flag for low complexity LTP quantization */ = 0
    var NLSF_MSVQ_Survivors /* Number of survivors in NLSF MSVQ */ = 0
    var first_frame_after_reset = 0
    /*
     * Flag for deactivating NLSF interp. and fluc. reduction after
     * resets
     */

    /* Input/output buffering */
    var inputBuf = ShortArray(Define.MAX_FRAME_LENGTH) /* buffer containin input signal */
    var inputBufIx = 0
    var nFramesInPayloadBuf /* number of frames sitting in outputBuf */ = 0
    var nBytesInPayloadBuf /* number of bytes sitting in outputBuf */ = 0

    /* Parameters For LTP scaling Control */
    var frames_since_onset = 0
    var psNLSF_CB = arrayOfNulls<SKP_Silk_NLSF_CB_struct>(2)
    /*
     * Pointers to
     * voiced/unvoiced NLSF
     * codebooks
     */

    /* Struct for Inband LBRR */
    var LBRR_buffer = arrayOfNulls<SKP_SILK_LBRR_struct>(Define.MAX_LBRR_DELAY)

    /*
	 * LBRR_buffer is an array of references, which has to be created manually.
	 */
    init {
        for (LBRR_bufferIni_i in 0 until Define.MAX_LBRR_DELAY) {
            LBRR_buffer[LBRR_bufferIni_i] = SKP_SILK_LBRR_struct()
        }
    }

    var oldest_LBRR_idx = 0
    var useInBandFEC /* Saves the API setting for query */ = 0
    var LBRR_enabled = 0
    var LBRR_GainIncreases /* Number of shifts to Gains to get LBRR rate Voiced frames */ = 0

    /* Bitrate control */
    var bitrateDiff /* Accumulated diff. between the target bitrate and the switch bitrates */ = 0
    var bitrate_threshold_up /* Threshold for switching to a higher internal sample frequency */ = 0
    var bitrate_threshold_down /* Threshold for switching to a lower internal sample frequency */ = 0
    var resampler_state = SKP_Silk_resampler_state_struct()

    /* DTX */
    var noSpeechCounter /* Counts concecutive nonactive frames, used by DTX */ = 0
    var useDTX /* Flag to enable DTX */ = 0
    var inDTX /* Flag to signal DTX period */ = 0
    var vadFlag /* Flag to indicate Voice Activity */ = 0

    /* Struct for detecting SWB input */
    var sSWBdetect = SKP_Silk_detect_SWB_state()

    /* Buffers */
    var q = ByteArray(Define.MAX_FRAME_LENGTH) /* pulse signal buffer */
    var q_LBRR = ByteArray(Define.MAX_FRAME_LENGTH) /* pulse signal buffer */
}

/**
 * Encoder control.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_encoder_control {
    /* Quantization indices */
    var lagIndex = 0
    var contourIndex = 0
    var PERIndex = 0
    var LTPIndex = IntArray(Define.NB_SUBFR)
    var NLSFIndices = IntArray(Define.NLSF_MSVQ_MAX_CB_STAGES) /* NLSF path of quantized LSF vector */
    var NLSFInterpCoef_Q2 = 0
    var GainsIndices = IntArray(Define.NB_SUBFR)
    var Seed = 0
    var LTP_scaleIndex = 0
    var RateLevelIndex = 0
    var QuantOffsetType = 0
    var sigtype = 0

    /* Prediction and coding parameters */
    var pitchL = IntArray(Define.NB_SUBFR)
    var LBRR_usage /* Low bitrate redundancy usage */ = 0
}

/**
 * Class for Packet Loss Concealment.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_PLC_struct {
    var pitchL_Q8 /* Pitch lag to use for voiced concealment */ = 0
    var LTPCoef_Q14 = ShortArray(Define.LTP_ORDER) /* LTP coeficients to use for voiced concealment */
    var prevLPC_Q12 = ShortArray(Define.MAX_LPC_ORDER)
    var last_frame_lost /* Was previous frame lost */ = 0
    var rand_seed /* Seed for unvoiced signal generation */ = 0
    var randScale_Q14 /* Scaling of unvoiced random signal */: Short = 0
    var conc_energy = 0
    var conc_energy_shift = 0
    var prevLTP_scale_Q14: Short = 0
    var prevGain_Q16 = IntArray(Define.NB_SUBFR)
    var fs_kHz = 0
}

/**
 * Class for CNG.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_CNG_struct {
    var CNG_exc_buf_Q10 = IntArray(Define.MAX_FRAME_LENGTH)
    var CNG_smth_NLSF_Q15 = IntArray(Define.MAX_LPC_ORDER)
    var CNG_synth_state = IntArray(Define.MAX_LPC_ORDER)
    var CNG_smth_Gain_Q16 = 0
    var rand_seed = 0
    var fs_kHz = 0
}

/**
 * Decoder state
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_decoder_state {
    var sRC = SKP_Silk_range_coder_state() /* Range coder state */
    var prev_inv_gain_Q16 = 0
    var sLTP_Q16 = IntArray(2 * Define.MAX_FRAME_LENGTH)
    var sLPC_Q14 = IntArray(Define.MAX_FRAME_LENGTH / Define.NB_SUBFR + Define.MAX_LPC_ORDER)
    var exc_Q10 = IntArray(Define.MAX_FRAME_LENGTH)
    var res_Q10 = IntArray(Define.MAX_FRAME_LENGTH)
    var outBuf = ShortArray(2 * Define.MAX_FRAME_LENGTH) /* Buffer for output signal */
    var lagPrev /* Previous Lag */ = 0
    var LastGainIndex /* Previous gain index */ = 0
    var LastGainIndex_EnhLayer /* Previous gain index */ = 0
    var typeOffsetPrev /* Previous signal type and quantization offset */ = 0
    var HPState = IntArray(Define.DEC_HP_ORDER) /* HP filter state */
    lateinit var HP_A /* HP filter AR coefficients */: ShortArray
    lateinit var HP_B /* HP filter MA coefficients */: ShortArray
    var fs_kHz /* Sampling frequency in kHz */ = 0
    var prev_API_sampleRate /* Previous API sample frequency (Hz) */ = 0
    var frame_length /* Frame length (samples) */ = 0
    var subfr_length /* Subframe length (samples) */ = 0
    var LPC_order /* LPC order */ = 0
    var prevNLSF_Q15 = IntArray(Define.MAX_LPC_ORDER) /* Used to interpolate LSFs */
    var first_frame_after_reset /*
								 * Flag for deactivating NLSF interp. and fluc. reduction after
								 * resets
								 */ = 0

    /* For buffering payload in case of more frames per packet */
    var nBytesLeft = 0
    var nFramesDecoded = 0
    var nFramesInPacket = 0
    var moreInternalDecoderFrames = 0
    var FrameTermination = 0
    var resampler_state = SKP_Silk_resampler_state_struct()
    var psNLSF_CB = arrayOfNulls<SKP_Silk_NLSF_CB_struct>(2)
    /*
     * Pointers to
     * voiced/unvoiced NLSF
     * codebooks
     */

    /* Parameters used to investigate if inband FEC is used */
    var vadFlag = 0
    var no_FEC_counter /* Counts number of frames wo inband FEC */ = 0
    var inband_FEC_offset /* 0: no FEC, 1: FEC with 1 packet offset, 2: FEC w 2 packets offset */ = 0
    var sCNG = SKP_Silk_CNG_struct()

    /* Stuff used for PLC */
    var sPLC = SKP_Silk_PLC_struct()
    var lossCnt = 0
    var prev_sigtype /* Previous sigtype */ = 0
}

/**
 * Decoder control.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_decoder_control {
    /* prediction and coding parameters */
    var pitchL = IntArray(Define.NB_SUBFR)
    var Gains_Q16 = IntArray(Define.NB_SUBFR)
    var Seed = 0

    /* holds interpolated and final coefficients, 4-byte aligned */ // TODO:
    var dummy_int32PredCoef_Q12 = IntArray(2)
    var PredCoef_Q12 = Array<ShortArray?>(2) { ShortArray(Define.MAX_LPC_ORDER) }
    var LTPCoef_Q14 = ShortArray(Define.LTP_ORDER * Define.NB_SUBFR)
    var LTP_scale_Q14 = 0

    /* quantization indices */
    var PERIndex = 0
    var RateLevelIndex = 0
    var QuantOffsetType = 0
    var sigtype = 0
    var NLSFInterpCoef_Q2 = 0
}