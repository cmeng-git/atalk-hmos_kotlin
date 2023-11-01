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
object TablesGain {
    var SKP_Silk_gain_CDF = arrayOf<IntArray?>(intArrayOf(0, 18, 45, 94, 181, 320, 519, 777, 1093, 1468, 1909, 2417, 2997, 3657, 4404, 5245, 6185,
            7228, 8384, 9664, 11069, 12596, 14244, 16022, 17937, 19979, 22121, 24345, 26646, 29021,
            31454, 33927, 36438, 38982, 41538, 44068, 46532, 48904, 51160, 53265, 55184, 56904,
            58422, 59739, 60858, 61793, 62568, 63210, 63738, 64165, 64504, 64769, 64976, 65133,
            65249, 65330, 65386, 65424, 65451, 65471, 65487, 65501, 65513, 65524, 65535), intArrayOf(0, 214, 581, 1261, 2376, 3920, 5742, 7632, 9449, 11157, 12780, 14352, 15897, 17427,
            18949, 20462, 21957, 23430, 24889, 26342, 27780, 29191, 30575, 31952, 33345, 34763,
            36200, 37642, 39083, 40519, 41930, 43291, 44602, 45885, 47154, 48402, 49619, 50805,
            51959, 53069, 54127, 55140, 56128, 57101, 58056, 58979, 59859, 60692, 61468, 62177,
            62812, 63368, 63845, 64242, 64563, 64818, 65023, 65184, 65306, 65391, 65447, 65482,
            65505, 65521, 65535))
    const val SKP_Silk_gain_CDF_offset = 32
    var SKP_Silk_delta_gain_CDF = intArrayOf(0, 2358, 3856, 7023, 15376, 53058, 59135, 61555,
            62784, 63498, 63949, 64265, 64478, 64647, 64783, 64894, 64986, 65052, 65113, 65169, 65213,
            65252, 65284, 65314, 65338, 65359, 65377, 65392, 65403, 65415, 65424, 65432, 65440, 65448,
            65455, 65462, 65470, 65477, 65484, 65491, 65499, 65506, 65513, 65521, 65528, 65535)
    const val SKP_Silk_delta_gain_CDF_offset = 5
}