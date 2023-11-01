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
class StructsFLP

/**
 * Noise shaping analysis state.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_shape_state_FLP {
    var LastGainIndex = 0
    var HarmBoost_smth = 0f
    var HarmShapeGain_smth = 0f
    var Tilt_smth = 0f

    /**
     * set all fields of the instance to zero
     */
    fun memZero() {
        LastGainIndex = 0
        HarmBoost_smth = 0f
        HarmShapeGain_smth = 0f
        Tilt_smth = 0f
    }
}

/**
 * Prefilter state
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_prefilter_state_FLP {
    var sLTP_shp1 = FloatArray(Define.LTP_BUF_LENGTH)
    var sLTP_shp2 = FloatArray(Define.LTP_BUF_LENGTH)
    var sAR_shp1 = FloatArray(Define.SHAPE_LPC_ORDER_MAX + 1)
    var sAR_shp2 = FloatArray(Define.SHAPE_LPC_ORDER_MAX)
    var sLTP_shp_buf_idx1 = 0
    var sLTP_shp_buf_idx2 = 0
    var sAR_shp_buf_idx2 = 0
    var sLF_AR_shp1 = 0f
    var sLF_MA_shp1 = 0f
    var sLF_AR_shp2 = 0f
    var sLF_MA_shp2 = 0f
    var sHarmHP = 0f
    var rand_seed = 0
    var lagPrev = 0

    /**
     * set all fields of the instance to zero
     */
    fun memZero() {
        Arrays.fill(sAR_shp1, 0f)
        Arrays.fill(sAR_shp2, 0f)
        Arrays.fill(sLTP_shp1, 0f)
        Arrays.fill(sLTP_shp2, 0f)
        sLTP_shp_buf_idx1 = 0
        sLTP_shp_buf_idx2 = 0
        sAR_shp_buf_idx2 = 0
        sLF_AR_shp1 = 0f
        sLF_AR_shp2 = 0f
        sLF_MA_shp1 = 0f
        sLF_MA_shp2 = 0f
        sHarmHP = 0f
        rand_seed = 0
        lagPrev = 0
    }
}

/**
 * Prediction analysis state
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_predict_state_FLP {
    var pitch_LPC_win_length = 0
    var min_pitch_lag /* Lowest possible pitch lag (samples) */ = 0
    var max_pitch_lag /* Highest possible pitch lag (samples) */ = 0
    var prev_NLSFq = FloatArray(Define.MAX_LPC_ORDER) /* Previously quantized NLSF vector */

    /**
     * set all fields of the instance to zero
     */
    fun memZero() {
        pitch_LPC_win_length = 0
        max_pitch_lag = 0
        min_pitch_lag = 0
        Arrays.fill(prev_NLSFq, 0.0f)
    }
}
/** */ /* Structure containing NLSF MSVQ codebook */ /** */ /* structure for one stage of MSVQ */
class SKP_Silk_NLSF_CBS_FLP {
    constructor() : super() {}
    constructor(nVectors: Int, CB: FloatArray, Rates: FloatArray) {
        this.nVectors = nVectors
        this.CB = CB
        this.Rates = Rates
    }

    constructor(nVectors: Int, CB: FloatArray, CB_offset: Int, Rates: FloatArray,
            Rates_offset: Int) {
        this.nVectors = nVectors
        this.CB = FloatArray(CB.size - CB_offset)
        System.arraycopy(CB, CB_offset, this.CB, 0, this.CB.size)
        this.Rates = FloatArray(Rates.size - Rates_offset)
        System.arraycopy(Rates, Rates_offset, this.Rates, 0, this.Rates.size)
    }

    var nVectors = 0
    lateinit var CB: FloatArray
    lateinit var Rates: FloatArray
}

class SKP_Silk_NLSF_CB_FLP {
    constructor() : super() {}
    constructor(nStages: Int, CBStages: Array<SKP_Silk_NLSF_CBS_FLP?>, NDeltaMin: FloatArray,
            CDF: IntArray?, StartPtr: Array<IntArray>?, MiddleIx: IntArray?) {
        this.nStages = nStages
        this.CBStages = CBStages
        this.NDeltaMin = NDeltaMin
        this.CDF = CDF
        this.StartPtr = StartPtr
        this.MiddleIx = MiddleIx
    }

    // const SKP_int32 nStages;
    var nStages = 0

    /* fields for (de)quantizing */
    lateinit var CBStages: Array<SKP_Silk_NLSF_CBS_FLP?>
    lateinit var NDeltaMin: FloatArray

    /* fields for arithmetic (de)coding */ // const SKP_uint16 *CDF;
    var CDF: IntArray? = null

    // const SKP_uint16 * const *StartPtr;
    var StartPtr: Array<IntArray>? = null

    // const SKP_int *MiddleIx;
    var MiddleIx: IntArray? = null
}
/** */ /* Noise shaping quantization state */
/** */
/**
 * Encoder state FLP.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_encoder_state_FLP {
    // SKP_Silk_encoder_state sCmn; /* Common struct, shared with fixed-point code */
    var sCmn = SKP_Silk_encoder_state() /*
																 * Common struct, shared with
																 * fixed-point code
																 */
    var variable_HP_smth1 /* State of first smoother */ = 0f
    var variable_HP_smth2 /* State of second smoother */ = 0f
    var sShape = SKP_Silk_shape_state_FLP() /* Noise shaping state */
    var sPrefilt = SKP_Silk_prefilter_state_FLP() /* Prefilter State */
    var sPred = SKP_Silk_predict_state_FLP() /* Prediction State */
    var sNSQ = SKP_Silk_nsq_state() /* Noise Shape Quantizer State */
    var sNSQ_LBRR = SKP_Silk_nsq_state() /*
															 * Noise Shape Quantizer State ( for low
															 * bitrate redundancy )
															 */

    /*
	 * Function pointer to noise shaping quantizer (will be set to SKP_Silk_NSQ or
	 * SKP_Silk_NSQ_del_dec)
	 */
    // void (* NoiseShapingQuantizer)( SKP_Silk_encoder_state *, SKP_Silk_encoder_control *,
    // SKP_Silk_nsq_state *, const
    // SKP_int16 *,
    // SKP_int8 *, const SKP_int, const SKP_int16 *, const SKP_int16 *, const SKP_int16 *, const
    // SKP_int *,
    // const SKP_int *, const SKP_int32 *, const SKP_int32 *, SKP_int, const SKP_int
    // );
    var noiseShapingQuantizerCB: NoiseShapingQuantizerFP? = null
    fun NoiseShapingQuantizer(psEnc: SKP_Silk_encoder_state?, psEncCtrl: SKP_Silk_encoder_control?,
            NSQ: SKP_Silk_nsq_state?, x: ShortArray, q: ByteArray?, arg6: Int, arg7: ShortArray,
            arg8: ShortArray, arg9: ShortArray, arg10: IntArray, arg11: IntArray,
            arg12: IntArray, arg13: IntArray, arg14: Int, arg15: Int) {
        noiseShapingQuantizerCB!!.NoiseShapingQuantizer(psEnc, psEncCtrl, NSQ, x, q, arg6, arg7,
                arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15)
    }

    /* Buffer for find pitch and noise shape analysis */
    var x_buf = FloatArray(2 * Define.MAX_FRAME_LENGTH + Define.LA_SHAPE_MAX) /*
																	 * Buffer for find pitch and
																	 * noise shape analysis
																	 */

    // djinn: add a parameter: offset
    var x_buf_offset = 0
    var LTPCorr /* Normalized correlation from pitch lag estimator */ = 0f
    var mu_LTP /* Rate-distortion tradeoff in LTP quantization */ = 0f
    var SNR_dB /* Quality setting */ = 0f
    var avgGain /* average gain during active speech */ = 0f
    var BufferedInChannel_ms /*
								 * Simulated number of ms buffer in channel because of exceeded
								 * TargetRate_bps
								 */ = 0f
    var speech_activity /* Speech activity */ = 0f
    var pitchEstimationThreshold /* Threshold for pitch estimator */ = 0f

    /* Parameters for LTP scaling control */
    var prevLTPredCodGain = 0f
    var HPLTPredCodGain = 0f
    var inBandFEC_SNR_comp /* Compensation to SNR_DB when using inband FEC Voiced */ = 0f
    var psNLSF_CB_FLP = arrayOfNulls<SKP_Silk_NLSF_CB_FLP>(2) /*
																		 * Pointers to
																		 * voiced/unvoiced NLSF
																		 * codebooks
																		 */
}

/**
 * Encoder control FLP
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_encoder_control_FLP {
    var sCmn = SKP_Silk_encoder_control() /*
																	 * Common struct, shared with
																	 * fixed-point code
																	 */

    /* Prediction and coding parameters */
    var Gains = FloatArray(Define.NB_SUBFR)
    var PredCoef = Array<FloatArray?>(2) { FloatArray(Define.MAX_LPC_ORDER) } /* holds interpolated and final coefficients */
    var LTPCoef = FloatArray(Define.LTP_ORDER * Define.NB_SUBFR)
    var LTP_scale = 0f

    /* Prediction and coding parameters */
    var Gains_Q16 = IntArray(Define.NB_SUBFR)

    // TODO: SKP_array_of_int16_4_byte_aligned( PredCoef_Q12[ 2 ], MAX_LPC_ORDER );
    var dummy_int32PredCoef_Q12 = IntArray(2)
    var PredCoef_Q12 = Array(2) { ShortArray(Define.MAX_LPC_ORDER) }
    var LTPCoef_Q14 = ShortArray(Define.LTP_ORDER * Define.NB_SUBFR)
    var LTP_scale_Q14 = 0

    /* Noise shaping parameters */ /* Testing */ // TODO SKP_array_of_int16_4_byte_aligned( AR2_Q13, NB_SUBFR * SHAPE_LPC_ORDER_MAX );
    var dummy_int32AR2_Q13 = 0
    var AR2_Q13 = ShortArray(Define.NB_SUBFR * Define.SHAPE_LPC_ORDER_MAX)
    var LF_shp_Q14 = IntArray(Define.NB_SUBFR) /* Packs two int16 coefficients per int32 value */
    var Tilt_Q14 = IntArray(Define.NB_SUBFR)
    var HarmShapeGain_Q14 = IntArray(Define.NB_SUBFR)
    var Lambda_Q10 = 0

    /* Noise shaping parameters */
    var AR1 = FloatArray(Define.NB_SUBFR * Define.SHAPE_LPC_ORDER_MAX)
    var AR2 = FloatArray(Define.NB_SUBFR * Define.SHAPE_LPC_ORDER_MAX)
    var LF_MA_shp = FloatArray(Define.NB_SUBFR)
    var LF_AR_shp = FloatArray(Define.NB_SUBFR)
    var GainsPre = FloatArray(Define.NB_SUBFR)
    var HarmBoost = FloatArray(Define.NB_SUBFR)
    var Tilt = FloatArray(Define.NB_SUBFR)
    var HarmShapeGain = FloatArray(Define.NB_SUBFR)
    var Lambda = 0f
    var input_quality = 0f
    var coding_quality = 0f
    var pitch_freq_low_Hz = 0f
    var current_SNR_dB = 0f

    /* Measures */
    var sparseness = 0f
    var LTPredCodGain = 0f
    var input_quality_bands = FloatArray(Define.VAD_N_BANDS)
    var input_tilt = 0f
    var ResNrg = FloatArray(Define.NB_SUBFR) /* Residual energy per subframe */
}

interface NoiseShapingQuantizerFP {
    /*
	 * Function pointer to noise shaping quantizer (will be set to SKP_Silk_NSQ or
	 * SKP_Silk_NSQ_del_dec)
	 */
    fun NoiseShapingQuantizer(psEnc: SKP_Silk_encoder_state?, psEncCtrl: SKP_Silk_encoder_control?,
            NSQ: SKP_Silk_nsq_state?, x: ShortArray, q: ByteArray?, arg6: Int, arg7: ShortArray,
            arg8: ShortArray, arg9: ShortArray, arg10: IntArray, arg11: IntArray,
            arg12: IntArray, arg13: IntArray, arg14: Int, arg15: Int) /*
	 * Function pointer to noise shaping quantizer (will be set to SKP_Silk_NSQ or
	 * SKP_Silk_NSQ_del_dec)
	 */
    // void (* NoiseShapingQuantizer)( SKP_Silk_encoder_state *, SKP_Silk_encoder_control *,
    // SKP_Silk_nsq_state *, const
    // SKP_int16 *,
    // SKP_int8 *, const SKP_int, const SKP_int16 *, const SKP_int16 *, const SKP_int16 *, const
    // SKP_int *,
    // const SKP_int *, const SKP_int32 *, const SKP_int32 *, SKP_int, const SKP_int
    // );
}