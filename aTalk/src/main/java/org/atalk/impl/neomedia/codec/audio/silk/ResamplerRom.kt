/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Filter coefficients for IIR/FIR polyphase resampling. Total size: 550 Words (1.1 kB).
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object ResamplerRom {
    const val RESAMPLER_DOWN_ORDER_FIR = 12
    const val RESAMPLER_ORDER_FIR_144 = 6

    /* Tables for 2x downsampler. Values above 32767 intentionally wrap to a negative value. */
    const val SKP_Silk_resampler_down2_0: Short = 9872
    const val SKP_Silk_resampler_down2_1 = 39809.toShort()

    /*
	 * Tables for 2x upsampler, low quality. Values above 32767 intentionally wrap to a negative
	 * value.
	 */
    const val SKP_Silk_resampler_up2_lq_0: Short = 8102
    const val SKP_Silk_resampler_up2_lq_1 = 36783.toShort()

    /*
	 * Tables for 2x upsampler, high quality. Values above 32767 intentionally wrap to a negative
	 * value.
	 */
    var SKP_Silk_resampler_up2_hq_0 = shortArrayOf(4280, 33727.toShort())
    var SKP_Silk_resampler_up2_hq_1 = shortArrayOf(16295, 54015.toShort())

    /* Matlab code for the notch filter coefficients: */ /*
	 * B = [1, 0.12, 1]; A = [1, 0.055, 0.8]; G = 0.87; freqz(G * B, A, 2^14, 16e3); axis([0, 8000,
	 * -10, 1]);
	 */
    /*
	 * fprintf('\t%6d, %6d, %6d, %6d\n', round(B(2)*2^16), round(-A(2)*2^16), round((1-A(3))*2^16),
	 * round(G*2^15))
	 */
    var SKP_Silk_resampler_up2_hq_notch = shortArrayOf(7864, -3604, 13107, 28508)

    /* Tables with IIR and FIR coefficients for fractional downsamplers (70 Words) */
    var SKP_Silk_Resampler_3_4_COEFS = shortArrayOf(-18249, -12532, -97, 284, -495, 309, 10268,
            20317, -94, 156, -48, -720, 5984, 18278, -45, -4, 237, -847, 2540, 14662)
    var SKP_Silk_Resampler_2_3_COEFS = shortArrayOf(-11891, -12486, 20, 211, -657, 688, 8423,
            15911, -44, 197, -152, -653, 3855, 13015)
    var SKP_Silk_Resampler_1_2_COEFS = shortArrayOf(2415, -13101, 158, -295, -400, 1265, 4832,
            7968)
    var SKP_Silk_Resampler_3_8_COEFS = shortArrayOf(13270, -13738, -294, -123, 747, 2043, 3339,
            3995, -151, -311, 414, 1583, 2947, 3877, -33, -389, 143, 1141, 2503, 3653)
    var SKP_Silk_Resampler_1_3_COEFS = shortArrayOf(16643, -14000, -331, 19, 581, 1421, 2290, 2845)
    var SKP_Silk_Resampler_2_3_COEFS_LQ = shortArrayOf(-2797, -6507, 4697, 10739, 1567, 8276)
    var SKP_Silk_Resampler_1_3_COEFS_LQ = shortArrayOf(16777, -9792, 890, 1614, 2148)

    /* Tables with coefficients for 4th order ARMA filter (35 Words), in a packed format: */ /* { B1_Q14[1], B2_Q14[1], -A1_Q14[1], -A1_Q14[2], -A2_Q14[1], -A2_Q14[2], gain_Q16 } */ /* where it is assumed that B*_Q14[0], B*_Q14[2], A*_Q14[0] are all 16384 */
    var SKP_Silk_Resampler_320_441_ARMA4_COEFS = shortArrayOf(31454, 24746, -9706, -3386, -17911,
            -13243, 24797)
    var SKP_Silk_Resampler_240_441_ARMA4_COEFS = shortArrayOf(28721, 11254, 3189, -2546, -1495,
            -12618, 11562)
    var SKP_Silk_Resampler_160_441_ARMA4_COEFS = shortArrayOf(23492, -6457, 14358, -4856, 14654,
            -13008, 4456)
    var SKP_Silk_Resampler_120_441_ARMA4_COEFS = shortArrayOf(19311, -15569, 19489, -6950, 21441,
            -13559, 2370)
    var SKP_Silk_Resampler_80_441_ARMA4_COEFS = shortArrayOf(13248, -23849, 24126, -9486, 26806,
            -14286, 1065)

    /* Table with interplation fractions of 1/288 : 2/288 : 287/288 (432 Words) */
    var SKP_Silk_resampler_frac_FIR_144 = arrayOf<ShortArray?>(shortArrayOf(-647, 1884, 30078), shortArrayOf(-625, 1736, 30044), shortArrayOf(-603, 1591, 30005), shortArrayOf(-581, 1448, 29963), shortArrayOf(-559, 1308, 29917), shortArrayOf(-537, 1169, 29867), shortArrayOf(-515, 1032, 29813), shortArrayOf(-494, 898, 29755), shortArrayOf(-473, 766, 29693), shortArrayOf(-452, 636, 29627), shortArrayOf(-431, 508, 29558), shortArrayOf(-410, 383, 29484), shortArrayOf(-390, 260, 29407), shortArrayOf(-369, 139, 29327), shortArrayOf(-349, 20, 29242), shortArrayOf(-330, -97, 29154), shortArrayOf(-310, -211, 29062), shortArrayOf(-291, -324, 28967), shortArrayOf(-271, -434, 28868), shortArrayOf(-253, -542, 28765), shortArrayOf(-234, -647, 28659), shortArrayOf(-215, -751, 28550), shortArrayOf(-197, -852, 28436), shortArrayOf(-179, -951, 28320), shortArrayOf(-162, -1048, 28200), shortArrayOf(-144, -1143, 28077), shortArrayOf(-127, -1235, 27950), shortArrayOf(-110, -1326, 27820), shortArrayOf(-94, -1414, 27687), shortArrayOf(-77, -1500, 27550), shortArrayOf(-61, -1584, 27410), shortArrayOf(-45, -1665, 27268), shortArrayOf(-30, -1745, 27122), shortArrayOf(-15, -1822, 26972), shortArrayOf(0, -1897, 26820), shortArrayOf(15, -1970, 26665), shortArrayOf(29, -2041, 26507), shortArrayOf(44, -2110, 26346), shortArrayOf(57, -2177, 26182), shortArrayOf(71, -2242, 26015), shortArrayOf(84, -2305, 25845), shortArrayOf(97, -2365, 25673), shortArrayOf(110, -2424, 25498), shortArrayOf(122, -2480, 25320), shortArrayOf(134, -2534, 25140), shortArrayOf(146, -2587, 24956), shortArrayOf(157, -2637, 24771), shortArrayOf(168, -2685, 24583), shortArrayOf(179, -2732, 24392), shortArrayOf(190, -2776, 24199), shortArrayOf(200, -2819, 24003), shortArrayOf(210, -2859, 23805), shortArrayOf(220, -2898, 23605), shortArrayOf(229, -2934, 23403), shortArrayOf(238, -2969, 23198), shortArrayOf(247, -3002, 22992), shortArrayOf(255, -3033, 22783), shortArrayOf(263, -3062, 22572), shortArrayOf(271, -3089, 22359), shortArrayOf(279, -3114, 22144), shortArrayOf(286, -3138, 21927), shortArrayOf(293, -3160, 21709), shortArrayOf(300, -3180, 21488), shortArrayOf(306, -3198, 21266), shortArrayOf(312, -3215, 21042), shortArrayOf(318, -3229, 20816), shortArrayOf(323, -3242, 20589), shortArrayOf(328, -3254, 20360), shortArrayOf(333, -3263, 20130), shortArrayOf(338, -3272, 19898), shortArrayOf(342, -3278, 19665), shortArrayOf(346, -3283, 19430), shortArrayOf(350, -3286, 19194), shortArrayOf(353, -3288, 18957), shortArrayOf(356, -3288, 18718), shortArrayOf(359, -3286, 18478), shortArrayOf(362, -3283, 18238), shortArrayOf(364, -3279, 17996), shortArrayOf(366, -3273, 17753), shortArrayOf(368, -3266, 17509), shortArrayOf(369, -3257, 17264), shortArrayOf(371, -3247, 17018), shortArrayOf(372, -3235, 16772), shortArrayOf(372, -3222, 16525), shortArrayOf(373, -3208, 16277), shortArrayOf(373, -3192, 16028), shortArrayOf(373, -3175, 15779), shortArrayOf(373, -3157, 15529), shortArrayOf(372, -3138, 15279), shortArrayOf(371, -3117, 15028), shortArrayOf(370, -3095, 14777), shortArrayOf(369, -3072, 14526), shortArrayOf(368, -3048, 14274), shortArrayOf(366, -3022, 14022), shortArrayOf(364, -2996, 13770), shortArrayOf(362, -2968, 13517), shortArrayOf(359, -2940, 13265), shortArrayOf(357, -2910, 13012), shortArrayOf(354, -2880, 12760), shortArrayOf(351, -2848, 12508), shortArrayOf(348, -2815, 12255), shortArrayOf(344, -2782, 12003), shortArrayOf(341, -2747, 11751), shortArrayOf(337, -2712, 11500), shortArrayOf(333, -2676, 11248), shortArrayOf(328, -2639, 10997), shortArrayOf(324, -2601, 10747), shortArrayOf(320, -2562, 10497), shortArrayOf(315, -2523, 10247), shortArrayOf(310, -2482, 9998), shortArrayOf(305, -2442, 9750), shortArrayOf(300, -2400, 9502), shortArrayOf(294, -2358, 9255), shortArrayOf(289, -2315, 9009), shortArrayOf(283, -2271, 8763), shortArrayOf(277, -2227, 8519), shortArrayOf(271, -2182, 8275), shortArrayOf(265, -2137, 8032), shortArrayOf(259, -2091, 7791), shortArrayOf(252, -2045, 7550), shortArrayOf(246, -1998, 7311), shortArrayOf(239, -1951, 7072), shortArrayOf(232, -1904, 6835), shortArrayOf(226, -1856, 6599), shortArrayOf(219, -1807, 6364), shortArrayOf(212, -1758, 6131), shortArrayOf(204, -1709, 5899), shortArrayOf(197, -1660, 5668), shortArrayOf(190, -1611, 5439), shortArrayOf(183, -1561, 5212), shortArrayOf(175, -1511, 4986), shortArrayOf(168, -1460, 4761), shortArrayOf(160, -1410, 4538), shortArrayOf(152, -1359, 4317), shortArrayOf(145, -1309, 4098), shortArrayOf(137, -1258, 3880), shortArrayOf(129, -1207, 3664), shortArrayOf(121, -1156, 3450), shortArrayOf(113, -1105, 3238), shortArrayOf(105, -1054, 3028), shortArrayOf(97, -1003, 2820), shortArrayOf(89, -952, 2614), shortArrayOf(81, -901, 2409), shortArrayOf(73, -851, 2207))
}