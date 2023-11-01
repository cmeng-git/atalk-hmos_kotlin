/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * copy and multiply a vector by a constant
 *
 * @author Dingxin Xu
 */
object ScaleCopyVectorFLP {
    /**
     * copy and multiply a vector by a constant.
     *
     * @param data_out
     * @param data_out_offset
     * @param data_in
     * @param data_in_offset
     * @param gain
     * @param dataSize
     */
    fun SKP_Silk_scale_copy_vector_FLP(data_out: FloatArray, data_out_offset: Int,
            data_in: FloatArray?, data_in_offset: Int, gain: Float, dataSize: Int) {

        /* 4x unrolled loop */
        val dataSize4: Int = dataSize and 0xFFFC
        var i: Int = 0
        while (i < dataSize4) {
            data_out[data_out_offset + i + 0] = gain * data_in!![data_in_offset + i + 0]
            data_out[data_out_offset + i + 1] = gain * data_in[data_in_offset + i + 1]
            data_out[data_out_offset + i + 2] = gain * data_in[data_in_offset + i + 2]
            data_out[data_out_offset + i + 3] = gain * data_in[data_in_offset + i + 3]
            i += 4
        }

        /* any remaining elements */
        while (i < dataSize) {
            data_out[data_out_offset + i] = gain * data_in!![data_in_offset + i]
            i++
        }
    }
}