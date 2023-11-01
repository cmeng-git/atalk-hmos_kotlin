/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * processing of gains.
 *
 * @author Dingxin Xu
 */
object ProcessGainsFLP {
    /**
     * Processing of gains.
     *
     * @param psEnc
     * Encoder state FLP
     * @param psEncCtrl
     * Encoder control FLP
     */
    fun SKP_Silk_process_gains_FLP(psEnc: SKP_Silk_encoder_state_FLP?,  /* I/O Encoder state FLP */
            psEncCtrl: SKP_Silk_encoder_control_FLP /* I/O Encoder control FLP */
    ) {
        val psShapeSt = psEnc!!.sShape
        var k: Int
        val pGains_Q16 = IntArray(Define.NB_SUBFR)
        val s: Float
        val InvMaxSqrVal: Float
        var gain: Float

        /* Gain reduction when LTP coding gain is high */
        if (psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_VOICED) {
            s = 1.0f - 0.5f * SigProcFLP.SKP_sigmoid(0.25f * (psEncCtrl.LTPredCodGain - 12.0f))
            k = 0
            while (k < Define.NB_SUBFR) {
                psEncCtrl.Gains[k] *= s
                k++
            }
        }

        /* Limit the quantized signal */
        InvMaxSqrVal = (Math.pow(2.0, (0.33f * (21.0f - psEncCtrl.current_SNR_dB)).toDouble()) / psEnc.sCmn.subfr_length).toFloat()
        k = 0
        while (k < Define.NB_SUBFR) {

            /* Soft limit on ratio residual energy and squared gains */
            gain = psEncCtrl.Gains[k]
            gain = Math.sqrt((gain * gain + psEncCtrl.ResNrg[k] * InvMaxSqrVal).toDouble()).toFloat()
            psEncCtrl.Gains[k] = if (gain < 32767.0f) gain else 32767.0f
            k++
        }

        /* Prepare gains for noise shaping quantization */
        k = 0
        while (k < Define.NB_SUBFR) {
            pGains_Q16[k] = (psEncCtrl.Gains[k] * 65536.0f).toInt()
            k++
        }

        /* Noise shaping quantization */
        val LastGainIndex_ptr = IntArray(1)
        LastGainIndex_ptr[0] = psShapeSt.LastGainIndex
        GainQuant.SKP_Silk_gains_quant(psEncCtrl.sCmn.GainsIndices, pGains_Q16, LastGainIndex_ptr,
                psEnc.sCmn.nFramesInPayloadBuf)
        psShapeSt.LastGainIndex = LastGainIndex_ptr[0]
        /* Overwrite unquantized gains with quantized gains and convert back to Q0 from Q16 */
        k = 0
        while (k < Define.NB_SUBFR) {
            psEncCtrl.Gains[k] = pGains_Q16[k] / 65536.0f
            k++
        }

        /*
		 * Set quantizer offset for voiced signals. Larger offset when LTP coding gain is low or
		 * tilt is high (ie low-pass)
		 */
        if (psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_VOICED) {
            if (psEncCtrl.LTPredCodGain + psEncCtrl.input_tilt > 1.0f) {
                psEncCtrl.sCmn.QuantOffsetType = 0
            } else {
                psEncCtrl.sCmn.QuantOffsetType = 1
            }
        }

        /* Quantizer boundary adjustment */
        if (psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_VOICED) {
            psEncCtrl.Lambda = 1.2f - 0.4f * psEnc.speech_activity - 0.3f * psEncCtrl.input_quality
            +0.2f * psEncCtrl.sCmn.QuantOffsetType - 0.1f * psEncCtrl.coding_quality
        } else {
            psEncCtrl.Lambda = 1.2f - 0.4f * psEnc.speech_activity - 0.4f * psEncCtrl.input_quality
            +0.4f * psEncCtrl.sCmn.QuantOffsetType - 0.1f * psEncCtrl.coding_quality
        }
        assert(psEncCtrl.Lambda >= 0.0f)
        assert(psEncCtrl.Lambda < 2.0f)
    }
}