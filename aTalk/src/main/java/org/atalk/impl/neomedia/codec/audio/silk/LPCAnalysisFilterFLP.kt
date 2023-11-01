/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * LPC analysis filter NB! State is kept internally and the filter always starts with zero state
 * first Order output samples are not set
 *
 * @author Jing Dai
 * @author Dignxin Xu
 */
object LPCAnalysisFilterFLP {
    /**
     *
     * @param r_LPC
     * LPC residual signal
     * @param PredCoef
     * LPC coefficients
     * @param s
     * Input signal
     * @param s_offset
     * offset of valid data.
     * @param length
     * Length of input signal
     * @param Order
     * LPC order
     */
    fun SKP_Silk_LPC_analysis_filter_FLP(r_LPC: FloatArray,  /* O LPC residual signal */
            PredCoef: FloatArray?,  /* I LPC coefficients */
            s: FloatArray?,  /* I Input signal */
            s_offset: Int, length: Int,  /* I Length of input signal */
            Order: Int /* I LPC order */
    ) {
        assert(Order <= length)
        when (Order) {
            8 -> SKP_Silk_LPC_analysis_filter8_FLP(r_LPC, PredCoef, s, s_offset, length)
            10 -> SKP_Silk_LPC_analysis_filter10_FLP(r_LPC, PredCoef, s, s_offset, length)
            12 -> SKP_Silk_LPC_analysis_filter12_FLP(r_LPC, PredCoef, s, s_offset, length)
            16 -> SKP_Silk_LPC_analysis_filter16_FLP(r_LPC, PredCoef, s, s_offset, length)
            else -> assert(false)
        }

        /* Set first LPC Order samples to zero instead of undefined */
        for (i in 0 until Order) r_LPC[i] = 0f
    }

    /**
     * 16th order LPC analysis filter, does not write first 16 samples.
     *
     * @param r_LPC
     * LPC residual signal
     * @param PredCoef
     * LPC coefficients
     * @param s
     * Input signal
     * @param s_offset
     * @param length
     * Length of input signal
     */
    fun SKP_Silk_LPC_analysis_filter16_FLP(r_LPC: FloatArray,  /* O LPC residual signal */
            PredCoef: FloatArray?,  /* I LPC coefficients */
            s: FloatArray?,  /* I Input signal */
            s_offset: Int, length: Int /* I Length of input signal */
    ) {
        var ix = 16
        var LPC_pred: Float
        var s_ptr: FloatArray?
        var s_ptr_offset: Int
        while (ix < length) {
            s_ptr = s
            s_ptr_offset = s_offset + ix - 1

            /* short-term prediction */
            LPC_pred = s_ptr!![s_ptr_offset] * PredCoef!![0] + s_ptr[s_ptr_offset - 1] * PredCoef[1] + s_ptr[s_ptr_offset - 2] * PredCoef[2] + s_ptr[s_ptr_offset - 3] * PredCoef[3] + s_ptr[s_ptr_offset - 4] * PredCoef[4] + s_ptr[s_ptr_offset - 5] * PredCoef[5] + s_ptr[s_ptr_offset - 6] * PredCoef[6] + s_ptr[s_ptr_offset - 7] * PredCoef[7] + s_ptr[s_ptr_offset - 8] * PredCoef[8] + s_ptr[s_ptr_offset - 9] * PredCoef[9] + s_ptr[s_ptr_offset - 10] * PredCoef[10] + s_ptr[s_ptr_offset - 11] * PredCoef[11] + s_ptr[s_ptr_offset - 12] * PredCoef[12] + s_ptr[s_ptr_offset - 13] * PredCoef[13] + s_ptr[s_ptr_offset - 14] * PredCoef[14] + s_ptr[s_ptr_offset - 15] * PredCoef[15]

            /* prediction error */
            r_LPC[ix] = s_ptr[s_ptr_offset + 1] - LPC_pred
            ix++
        }
    }

    /**
     * 12th order LPC analysis filter, does not write first 12 samples.
     *
     * @param r_LPC
     * LPC residual signal
     * @param PredCoef
     * LPC coefficients
     * @param s
     * Input signal
     * @param s_offset
     * offset of valid data.
     * @param length
     * Length of input signal
     */
    fun SKP_Silk_LPC_analysis_filter12_FLP(r_LPC: FloatArray,  /* O LPC residual signal */
            PredCoef: FloatArray?,  /* I LPC coefficients */
            s: FloatArray?,  /* I Input signal */
            s_offset: Int, length: Int /* I Length of input signal */
    ) {
        var ix = 12
        var LPC_pred: Float
        var s_ptr: FloatArray?
        var s_ptr_offset: Int
        while (ix < length) {
            s_ptr = s
            s_ptr_offset = s_offset + ix - 1

            /* short-term prediction */
            LPC_pred = s_ptr!![s_ptr_offset] * PredCoef!![0] + s_ptr[s_ptr_offset - 1] * PredCoef[1] + s_ptr[s_ptr_offset - 2] * PredCoef[2] + s_ptr[s_ptr_offset - 3] * PredCoef[3] + s_ptr[s_ptr_offset - 4] * PredCoef[4] + s_ptr[s_ptr_offset - 5] * PredCoef[5] + s_ptr[s_ptr_offset - 6] * PredCoef[6] + s_ptr[s_ptr_offset - 7] * PredCoef[7] + s_ptr[s_ptr_offset - 8] * PredCoef[8] + s_ptr[s_ptr_offset - 9] * PredCoef[9] + s_ptr[s_ptr_offset - 10] * PredCoef[10] + s_ptr[s_ptr_offset - 11] * PredCoef[11]

            /* prediction error */
            r_LPC[ix] = s_ptr[s_ptr_offset + 1] - LPC_pred
            ix++
        }
    }

    /**
     * 10th order LPC analysis filter, does not write first 10 samples
     *
     * @param r_LPC
     * LPC residual signal
     * @param PredCoef
     * LPC coefficients
     * @param s
     * Input signal
     * @param s_offset
     * offset of valid data.
     * @param length
     * Length of input signal
     */
    fun SKP_Silk_LPC_analysis_filter10_FLP(r_LPC: FloatArray,  /* O LPC residual signal */
            PredCoef: FloatArray?,  /* I LPC coefficients */
            s: FloatArray?,  /* I Input signal */
            s_offset: Int, length: Int /* I Length of input signal */
    ) {
        var ix = 10
        var LPC_pred: Float
        var s_ptr: FloatArray?
        var s_ptr_offset: Int
        while (ix < length) {
            s_ptr = s
            s_ptr_offset = s_offset + ix - 1

            /* short-term prediction */
            LPC_pred = s_ptr!![s_ptr_offset] * PredCoef!![0] + s_ptr[s_ptr_offset - 1] * PredCoef[1] + s_ptr[s_ptr_offset - 2] * PredCoef[2] + s_ptr[s_ptr_offset - 3] * PredCoef[3] + s_ptr[s_ptr_offset - 4] * PredCoef[4] + s_ptr[s_ptr_offset - 5] * PredCoef[5] + s_ptr[s_ptr_offset - 6] * PredCoef[6] + s_ptr[s_ptr_offset - 7] * PredCoef[7] + s_ptr[s_ptr_offset - 8] * PredCoef[8] + s_ptr[s_ptr_offset - 9] * PredCoef[9]

            /* prediction error */
            r_LPC[ix] = s_ptr[s_ptr_offset + 1] - LPC_pred
            ix++
        }
    }

    /**
     * 8th order LPC analysis filter, does not write first 8 samples.
     *
     * @param r_LPC
     * LPC residual signal
     * @param PredCoef
     * LPC coefficients
     * @param s
     * Input signal
     * @param s_offset
     * offset of valid data.
     * @param length
     * Length of input signal
     */
    fun SKP_Silk_LPC_analysis_filter8_FLP(r_LPC: FloatArray,  /* O LPC residual signal */
            PredCoef: FloatArray?,  /* I LPC coefficients */
            s: FloatArray?,  /* I Input signal */
            s_offset: Int, length: Int /* I Length of input signal */
    ) {
        var ix = 8
        var LPC_pred: Float
        var s_ptr: FloatArray?
        var s_ptr_offset: Int
        while (ix < length) {
            s_ptr = s
            s_ptr_offset = s_offset + ix - 1

            /* short-term prediction */
            LPC_pred = s_ptr!![s_ptr_offset] * PredCoef!![0] + s_ptr[s_ptr_offset - 1] * PredCoef[1] + s_ptr[s_ptr_offset - 2] * PredCoef[2] + s_ptr[s_ptr_offset - 3] * PredCoef[3] + s_ptr[s_ptr_offset - 4] * PredCoef[4] + s_ptr[s_ptr_offset - 5] * PredCoef[5] + s_ptr[s_ptr_offset - 6] * PredCoef[6] + s_ptr[s_ptr_offset - 7] * PredCoef[7]

            /* prediction error */
            r_LPC[ix] = s_ptr[s_ptr_offset + 1] - LPC_pred
            ix++
        }
    }
}