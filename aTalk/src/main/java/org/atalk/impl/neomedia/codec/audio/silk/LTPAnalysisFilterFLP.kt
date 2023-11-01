/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

object LTPAnalysisFilterFLP {
    /**
     *
     * @param LTP_res
     * LTP res NB_SUBFR*(pre_lgth+subfr_lngth)
     * @param x
     * Input signal, with preceeding samples
     * @param x_offset
     * offset of valid data.
     * @param B
     * LTP coefficients for each subframe
     * @param pitchL
     * Pitch lags
     * @param invGains
     * Inverse quantization gains
     * @param subfr_length
     * Length of each subframe
     * @param pre_length
     * Preceeding samples for each subframe.
     */
    fun SKP_Silk_LTP_analysis_filter_FLP(LTP_res: FloatArray,  /*
																 * O LTP res
																 * NB_SUBFR*(pre_lgth+subfr_lngth)
																 */
            x: FloatArray?,  /* I Input signal, with preceeding samples */
            x_offset: Int, B: FloatArray?,  /* I LTP coefficients for each subframe */
            pitchL: IntArray?,  /* I Pitch lags */
            invGains: FloatArray,  /* I Inverse quantization gains */
            subfr_length: Int,  /* I Length of each subframe */
            pre_length: Int /* I Preceeding samples for each subframe */
    ) {
        val x_ptr: FloatArray?
        var x_lag_ptr: FloatArray?
        var x_ptr_offset: Int
        var x_lag_ptr_offset: Int
        val Btmp = FloatArray(Define.LTP_ORDER)
        val LTP_res_ptr: FloatArray
        var LTP_res_ptr_offset: Int
        var inv_gain: Float
        var k: Int
        var i: Int
        var j: Int
        x_ptr = x
        x_ptr_offset = x_offset
        LTP_res_ptr = LTP_res
        LTP_res_ptr_offset = 0
        k = 0
        while (k < Define.NB_SUBFR) {
            x_lag_ptr = x_ptr
            x_lag_ptr_offset = x_ptr_offset - pitchL!![k]
            inv_gain = invGains[k]
            i = 0
            while (i < Define.LTP_ORDER) {
                Btmp[i] = B!![k * Define.LTP_ORDER + i]
                i++
            }

            /* LTP analysis FIR filter */
            i = 0
            while (i < subfr_length + pre_length) {
                LTP_res_ptr[LTP_res_ptr_offset + i] = x_ptr!![x_ptr_offset + i]
                /* Subtract long-term prediction */
                j = 0
                while (j < Define.LTP_ORDER) {
                    LTP_res_ptr[LTP_res_ptr_offset + i] -= (Btmp[j]
                            * x_lag_ptr!![x_lag_ptr_offset + Define.LTP_ORDER / 2 - j])
                    j++
                }
                LTP_res_ptr[LTP_res_ptr_offset + i] *= inv_gain
                x_lag_ptr_offset++
                i++
            }

            /* Update pointers */
            LTP_res_ptr_offset += subfr_length + pre_length
            x_ptr_offset += subfr_length
            k++
        }
    }
}