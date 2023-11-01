/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * inner product of two SKP_float arrays, with result as double.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object InnerProductFLP {
    /**
     * inner product of two SKP_float arrays, with result as double.
     *
     * @param data1
     * vector1.
     * @param data1_offset
     * offset of valid data.
     * @param data2
     * vector2.
     * @param data2_offset
     * offset of valid data.
     * @param dataSize
     * length of vectors.
     * @return result.
     */
    fun SKP_Silk_inner_product_FLP( /* O result */
            data1: FloatArray,  /* I vector 1 */
            data1_offset: Int, data2: FloatArray,  /* I vector 2 */
            data2_offset: Int, dataSize: Int /* I length of vectors */
    ): Double {
        var i: Int
        val dataSize4: Int
        var result: Double

        /* 4x unrolled loop */
        result = 0.0
        dataSize4 = dataSize and 0xFFFC
        i = 0
        while (i < dataSize4) {
            result += (data1[data1_offset + i + 0] * data2[data2_offset + i + 0] + data1[data1_offset + i + 1] * data2[data2_offset + i + 1] + data1[data1_offset + i + 2] * data2[data2_offset + i + 2] + data1[data1_offset + i + 3] * data2[data2_offset + i + 3]).toDouble()
            i += 4
        }

        /* add any remaining products */
        while (i < dataSize) {
            result += (data1[data1_offset + i] * data2[data2_offset + i]).toDouble()
            i++
        }
        return result
    }
}