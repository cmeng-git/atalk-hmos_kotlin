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
object EnergyFLP {
    /**
     * sum of squares of a float array, with result as double.
     *
     * @param data
     * @param data_offset
     * @param dataSize
     * @return
     */
    // TODO: float or double???
    fun SKP_Silk_energy_FLP(data: FloatArray, data_offset: Int, dataSize: Int): Double {
        var i: Int
        val dataSize4: Int
        var result: Double

        /* 4x unrolled loop */
        result = 0.0
        dataSize4 = dataSize and 0xFFFC
        i = 0
        while (i < dataSize4) {
            result += (data[data_offset + i + 0] * data[data_offset + i + 0] + data[data_offset + i + 1] * data[data_offset + i + 1] + (data[data_offset + i + 2]
                    * data[data_offset + i + 2]) + data[data_offset + i + 3] * data[data_offset + i + 3]).toDouble()
            i += 4
        }

        /* add any remaining products */
        while (i < dataSize) {
            result += (data[data_offset + i] * data[data_offset + i]).toDouble()
            i++
        }
        assert(result >= 0.0)
        return result
    }
}