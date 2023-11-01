/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object TablesOther {
    /* Piece-wise linear mapping from bitrate in kbps to coding quality in dB SNR */
    val TargetRate_table_NB = intArrayOf(0, 8000, 9000, 11000, 13000, 16000, 22000, 100000)
    val TargetRate_table_MB = intArrayOf(0, 10000, 12000, 14000, 17000, 21000, 28000, 100000)
    val TargetRate_table_WB = intArrayOf(0, 11000, 14000, 17000, 21000, 26000, 36000, 100000)
    val TargetRate_table_SWB = intArrayOf(0, 13000, 16000, 19000, 25000, 32000, 46000, 100000)
    val SNR_table_Q1 = intArrayOf(19, 31, 35, 39, 43, 47, 54, 59)
    val SNR_table_one_bit_per_sample_Q7 = intArrayOf(1984, 2240, 2408, 2708)

    /* Filter coeficicnts for HP filter: 4. Order filter implementad as two biquad filters */
    val SKP_Silk_SWB_detect_B_HP_Q13 = arrayOf<ShortArray?>(shortArrayOf(575, -948, 575), shortArrayOf(575, -221, 575), shortArrayOf(575, 104, 575))
    val SKP_Silk_SWB_detect_A_HP_Q13 = arrayOf<ShortArray?>(shortArrayOf(14613, 6868), shortArrayOf(12883, 7337), shortArrayOf(11586, 7911))

    /* Decoder high-pass filter coefficients for 24 kHz sampling, -6 dB @ 44 Hz */
    val SKP_Silk_Dec_A_HP_24 = shortArrayOf(-16220, 8030) // second order AR coefs, Q13
    val SKP_Silk_Dec_B_HP_24 = shortArrayOf(8000, -16000, 8000) // second order MA coefs,

    // Q13
    /* Decoder high-pass filter coefficients for 16 kHz sampling, - 6 dB @ 46 Hz */
    val SKP_Silk_Dec_A_HP_16 = shortArrayOf(-16127, 7940) // second order AR coefs, Q13
    val SKP_Silk_Dec_B_HP_16 = shortArrayOf(8000, -16000, 8000) // second order MA coefs,

    // Q13
    /* Decoder high-pass filter coefficients for 12 kHz sampling, -6 dB @ 44 Hz */
    val SKP_Silk_Dec_A_HP_12 = shortArrayOf(-16043, 7859) // second order AR coefs, Q13
    val SKP_Silk_Dec_B_HP_12 = shortArrayOf(8000, -16000, 8000) // second order MA coefs,

    // Q13
    /* Decoder high-pass filter coefficients for 8 kHz sampling, -6 dB @ 43 Hz */
    val SKP_Silk_Dec_A_HP_8 = shortArrayOf(-15885, 7710) // second order AR coefs, Q13
    val SKP_Silk_Dec_B_HP_8 = shortArrayOf(8000, -16000, 8000) // second order MA coefs, Q13

    /* table for LSB coding */
    val SKP_Silk_lsb_CDF = intArrayOf(0, 40000, 65535)

    /* tables for LTPScale */
    val SKP_Silk_LTPscale_CDF = intArrayOf(0, 32000, 48000, 65535)
    const val SKP_Silk_LTPscale_offset = 2

    /* tables for VAD flag */
    val SKP_Silk_vadflag_CDF = intArrayOf(0, 22000, 65535) // 66% for speech, 33% for no

    // speech
    const val SKP_Silk_vadflag_offset = 1

    /* tables for sampling rate */
    val SKP_Silk_SamplingRates_table = intArrayOf(8, 12, 16, 24)
    val SKP_Silk_SamplingRates_CDF = intArrayOf(0, 16000, 32000, 48000, 65535)
    const val SKP_Silk_SamplingRates_offset = 2

    /* tables for NLSF interpolation factor */
    val SKP_Silk_NLSF_interpolation_factor_CDF = intArrayOf(0, 3706, 8703, 19226, 30926,
            65535)
    const val SKP_Silk_NLSF_interpolation_factor_offset = 4

    /* Table for frame termination indication */
    val SKP_Silk_FrameTermination_CDF = intArrayOf(0, 20000, 45000, 56000, 65535)
    const val SKP_Silk_FrameTermination_offset = 2

    /* Table for random seed */
    val SKP_Silk_Seed_CDF = intArrayOf(0, 16384, 32768, 49152, 65535)
    const val SKP_Silk_Seed_offset = 2

    /* Quantization offsets */
    val SKP_Silk_Quantization_Offsets_Q10 = arrayOf<ShortArray?>(shortArrayOf(Define.OFFSET_VL_Q10.toShort(), Define.OFFSET_VH_Q10.toShort()), shortArrayOf(Define.OFFSET_UVL_Q10.toShort(), Define.OFFSET_UVH_Q10.toShort()))

    /* Table for LTPScale */
    val SKP_Silk_LTPScales_table_Q14 = shortArrayOf(15565, 11469, 8192)

    /*
	 * Elliptic/Cauer filters designed with 0.1 dB passband ripple, 80 dB minimum stopband
	 * attenuation, and [0.95 : 0.15 : 0.35] normalized cut off frequencies.
	 */
    /* Interpolation points for filter coefficients used in the bandwidth transition smoother */
    val SKP_Silk_Transition_LP_B_Q28 = arrayOf<IntArray?>(intArrayOf(250767114, 501534038, 250767114), intArrayOf(209867381, 419732057, 209867381), intArrayOf(170987846, 341967853, 170987846), intArrayOf(131531482, 263046905, 131531482), intArrayOf(89306658, 178584282, 89306658))

    /* Interpolation points for filter coefficients used in the bandwidth transition smoother */
    val SKP_Silk_Transition_LP_A_Q28 = arrayOf<IntArray?>(intArrayOf(506393414, 239854379), intArrayOf(411067935, 169683996), intArrayOf(306733530, 116694253), intArrayOf(185807084, 77959395), intArrayOf(35497197, 57401098))
}