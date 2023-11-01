/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Variable order MA filter.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object MA {
    /**
     * Variable order MA filter.
     *
     * @param in
     * input signal.
     * @param in_offset
     * offset of valid data.
     * @param B
     * MA coefficients, Q13 [order+1].
     * @param S
     * state vector [order].
     * @param out
     * output signal.
     * @param out_offset
     * offset of valid data.
     * @param len
     * signal length.
     * @param order
     * filter order.
     */
    fun SKP_Silk_MA(`in`: ShortArray,  /* I: input signal */
            in_offset: Int, B: ShortArray,  /* I: MA coefficients, Q13 [order+1] */
            S: IntArray,  /* I/O: state vector [order] */
            out: ShortArray,  /* O: output signal */
            out_offset: Int, len: Int,  /* I: signal length */
            order: Int /* I: filter order */
    ) {
        var k: Int
        var d: Int
        var in16: Int
        var out32: Int
        k = 0
        while (k < len) {
            in16 = `in`[in_offset + k].toInt()
            out32 = Macros.SKP_SMLABB(S[0], in16, B[0].toInt())
            out32 = SigProcFIX.SKP_RSHIFT_ROUND(out32, 13)
            d = 1
            while (d < order) {
                S[d - 1] = Macros.SKP_SMLABB(S[d], in16, B[d].toInt())
                d++
            }
            S[order - 1] = Macros.SKP_SMULBB(in16, B[order].toInt())

            /* Limit */
            out[out_offset + k] = SigProcFIX.SKP_SAT16(out32).toShort()
            k++
        }
    }

    /**
     * Variable order MA prediction error filter.
     *
     * @param in
     * Input signal.
     * @param in_offset
     * offset of valid data.
     * @param B
     * MA prediction coefficients, Q12 [order].
     * @param B_offset
     * @param S
     * State vector [order].
     * @param out
     * Output signal.
     * @param out_offset
     * offset of valid data.
     * @param len
     * Signal length.
     * @param order
     * Filter order.
     */
    fun SKP_Silk_MA_Prediction(`in`: ShortArray?,  /* I: Input signal */
            in_offset: Int, B: ShortArray?,  /* I: MA prediction coefficients, Q12 [order] */
            B_offset: Int, S: IntArray,  /* I/O: State vector [order] */
            out: ShortArray,  /* O: Output signal */
            out_offset: Int, len: Int,  /* I: Signal length */
            order: Int /* I: Filter order */
    ) {
        var k: Int
        var d: Int
        var in16: Int
        var out32: Int
        k = 0
        while (k < len) {
            in16 = `in`!![in_offset + k].toInt()
            out32 = (in16 shl 12) - S[0]
            out32 = SigProcFIX.SKP_RSHIFT_ROUND(out32, 12)
            d = 0
            while (d < order - 1) {
                S[d] = SigProcFIX.SKP_SMLABB_ovflw(S[d + 1], in16, B!![B_offset + d].toInt())
                d++
            }
            S[order - 1] = Macros.SKP_SMULBB(in16, B!![B_offset + order - 1].toInt())

            /* Limit */
            out[out_offset + k] = SigProcFIX.SKP_SAT16(out32).toShort()
            k++
        }
    }

    /**
     *
     * @param in
     * input signal.
     * @param in_offset
     * offset of valid data.
     * @param B
     * MA prediction coefficients, Q13 [order].
     * @param S
     * state vector [order].
     * @param out
     * output signal.
     * @param out_offset
     * offset of valid data.
     * @param len
     * signal length.
     * @param order
     * filter order.
     */
    fun SKP_Silk_MA_Prediction_Q13(`in`: ShortArray,  /* I: input signal */
            in_offset: Int, B: ShortArray,  /* I: MA prediction coefficients, Q13 [order] */
            S: IntArray,  /* I/O: state vector [order] */
            out: ShortArray,  /* O: output signal */
            out_offset: Int, len: Int,  /* I: signal length */
            order: Int /* I: filter order */
    ) {
        var k: Int
        var d: Int
        var in16: Int
        var out32: Int
        k = 0
        while (k < len) {
            in16 = `in`[in_offset + k].toInt()
            out32 = (in16 shl 13) - S[0]
            out32 = SigProcFIX.SKP_RSHIFT_ROUND(out32, 13)
            d = 0
            while (d < order - 1) {
                S[d] = Macros.SKP_SMLABB(S[d + 1], in16, B[d].toInt())
                d++
            }
            S[order - 1] = Macros.SKP_SMULBB(in16, B[order - 1].toInt())

            /* Limit */
            out[out_offset + k] = SigProcFIX.SKP_SAT16(out32).toShort()
            k++
        }
    }

    /**
     *
     * @param in
     * Input signal.
     * @param in_offset
     * offset of valid data.
     * @param B
     * MA prediction coefficients, Q12 [order].
     * @param S
     * State vector [order].
     * @param out
     * Output signal.
     * @param out_offset
     * offset of valid data.
     * @param len
     * Signal length.
     * @param Order
     * Filter order.
     */
    fun SKP_Silk_LPC_analysis_filter(`in`: ShortArray,  /* I: Input signal */
            in_offset: Int, B: ShortArray,  /* I: MA prediction coefficients, Q12 [order] */
            S: ShortArray,  /* I/O: State vector [order] */
            out: ShortArray,  /* O: Output signal */
            out_offset: Int, len: Int,  /* I: Signal length */
            Order: Int /* I: Filter order */
    ) {
        var k: Int
        var j: Int
        var idx: Int
        val Order_half = Order shr 1
        var out32_Q12: Int
        var out32: Int
        var SA: Short
        var SB: Short
        /* Order must be even */
        Typedef.SKP_assert(2 * Order_half == Order)

        /* S[] values are in Q0 */
        k = 0
        while (k < len) {
            SA = S[0]
            out32_Q12 = 0
            j = 0
            while (j < Order_half - 1) {
                idx = Macros.SKP_SMULBB(2, j) + 1
                /* Multiply-add two prediction coefficients for each loop */
                SB = S[idx]
                S[idx] = SA
                out32_Q12 = Macros.SKP_SMLABB(out32_Q12, SA.toInt(), B[idx - 1].toInt())
                out32_Q12 = Macros.SKP_SMLABB(out32_Q12, SB.toInt(), B[idx].toInt())
                SA = S[idx + 1]
                S[idx + 1] = SB
                j++
            }

            /* Unrolled loop: epilog */
            SB = S[Order - 1]
            S[Order - 1] = SA
            out32_Q12 = Macros.SKP_SMLABB(out32_Q12, SA.toInt(), B[Order - 2].toInt())
            out32_Q12 = Macros.SKP_SMLABB(out32_Q12, SB.toInt(), B[Order - 1].toInt())

            /* Subtract prediction */
            out32_Q12 = Macros.SKP_SUB_SAT32(`in`[in_offset + k].toInt() shl 12, out32_Q12)

            /* Scale to Q0 */
            out32 = SigProcFIX.SKP_RSHIFT_ROUND(out32_Q12, 12)

            /* Saturate output */
            out[out_offset + k] = SigProcFIX.SKP_SAT16(out32).toShort()

            /* Move input line */
            S[0] = `in`[in_offset + k]
            k++
        }
    }
}