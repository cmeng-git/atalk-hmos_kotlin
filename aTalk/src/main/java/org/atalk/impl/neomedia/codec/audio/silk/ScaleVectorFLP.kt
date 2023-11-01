/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * scale a vector.
 *
 * @author Dingxin Xu
 */
object ScaleVectorFLP {
    /**
     * multiply a vector by a constant.
     *
     * @param data1
     * @param gain
     * @param dataSize
     */
    fun SKP_Silk_scale_vector_FLP(data1: FloatArray, data1_offset: Int, gain: Float, dataSize: Int) {

        /* 4x unrolled loop */
        val dataSize4: Int = dataSize and 0xFFFC
        var i: Int = 0
        while (i < dataSize4) {
            data1[data1_offset + i + 0] *= gain
            data1[data1_offset + i + 1] *= gain
            data1[data1_offset + i + 2] *= gain
            data1[data1_offset + i + 3] *= gain
            i += 4
        }

        /* any remaining elements */
        while (i < dataSize) {
            data1[data1_offset + i] *= gain
            i++
        }
    }
}