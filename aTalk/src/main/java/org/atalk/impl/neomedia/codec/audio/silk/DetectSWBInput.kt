/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Detect SWB input by measuring energy above 8 kHz.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object DetectSWBInput {
    /**
     * Detect SWB input by measuring energy above 8 kHz.
     *
     * @param psSWBdetect
     * encoder state
     * @param samplesIn
     * input to encoder
     * @param samplesIn_offset
     * offset of valid data.
     * @param nSamplesIn
     * length of input
     */
    fun SKP_Silk_detect_SWB_input(psSWBdetect: SKP_Silk_detect_SWB_state?,
            /*
             * (I/O) encoder state
             */
            samplesIn: ShortArray,  /* (I) input to encoder */
            samplesIn_offset: Int, nSamplesIn: Int /* (I) length of input */
    ) {
        val shift = IntArray(1)
        val in_HP_8_kHz = ShortArray(Define.MAX_FRAME_LENGTH)
        val energy_32 = IntArray(1)

        /* High pass filter with cutoff at 8 khz */
        var HP_8_kHz_len = Math.min(nSamplesIn, Define.MAX_FRAME_LENGTH)
        HP_8_kHz_len = Math.max(HP_8_kHz_len, 0)

        /* Cutoff around 9 khz */
        /* A = conv(conv([8192,14613, 6868], [8192,12883, 7337]), [8192,11586, 7911]); */
        /* B = conv(conv([575, -948, 575], [575, -221, 575]), [575, 104, 575]); */
        Biquad.SKP_Silk_biquad(samplesIn, samplesIn_offset,
                TablesOther.SKP_Silk_SWB_detect_B_HP_Q13[0],
                TablesOther.SKP_Silk_SWB_detect_A_HP_Q13[0], psSWBdetect!!.S_HP_8_kHz[0], in_HP_8_kHz, 0,
                HP_8_kHz_len)
        var i: Int = 1
        while (i < Define.NB_SOS) {
            Biquad.SKP_Silk_biquad(in_HP_8_kHz, 0, TablesOther.SKP_Silk_SWB_detect_B_HP_Q13[i],
                    TablesOther.SKP_Silk_SWB_detect_A_HP_Q13[i], psSWBdetect.S_HP_8_kHz[i],
                    in_HP_8_kHz, 0, HP_8_kHz_len)
            i++
        }

        /* Calculate energy in HP signal */
        SumSqrShift.SKP_Silk_sum_sqr_shift(energy_32, shift, in_HP_8_kHz, 0, HP_8_kHz_len)

        /*
		 * Count concecutive samples above threshold, after adjusting threshold for number of input
		 * samples and shift
		 */
        if (energy_32[0] > Macros.SKP_SMULBB(Define.HP_8_KHZ_THRES, HP_8_kHz_len) shr shift[0]) {
            psSWBdetect.ConsecSmplsAboveThres += nSamplesIn
            if (psSWBdetect.ConsecSmplsAboveThres > Define.CONCEC_SWB_SMPLS_THRES) {
                psSWBdetect.SWB_detected = 1
            }
        } else {
            psSWBdetect.ConsecSmplsAboveThres -= nSamplesIn
            psSWBdetect.ConsecSmplsAboveThres = Math.max(psSWBdetect.ConsecSmplsAboveThres, 0)
        }

        /* If sufficient speech activity and no SWB detected, we detect the signal as being WB */
        if (psSWBdetect.ActiveSpeech_ms > Define.WB_DETECT_ACTIVE_SPEECH_MS_THRES && psSWBdetect.SWB_detected == 0) {
            psSWBdetect.WB_detected = 1
        }
    }
}