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
open class SigProcFLPConstants {
    companion object {
        /* Pitch estimator */
        const val SigProc_PITCH_EST_MIN_COMPLEX = 0
        const val SigProc_PITCH_EST_MID_COMPLEX = 1
        const val SigProc_PITCH_EST_MAX_COMPLEX = 2
        const val PI = 3.1415926536f
    }
}

object SigProcFLP : SigProcFLPConstants() {
    fun SKP_min_float(a: Float, b: Float): Float {
        return if (a < b) a else b
    }

    fun SKP_max_float(a: Float, b: Float): Float {
        return if (a > b) a else b
    }

    fun SKP_abs_float(a: Float): Float {
        return Math.abs(a)
    }

    fun SKP_LIMIT_float(a: Float, limit1: Float, limit2: Float): Float {
        return if (limit1 > limit2) if (a > limit1) limit1 else (if (a < limit2) limit2 else a) else if (a > limit2) limit2 else if (a < limit1) limit1 else a
    }

    /* sigmoid function */
    fun SKP_sigmoid(x: Float): Float {
        return (1.0 / (1.0 + Math.exp(-x.toDouble()))).toFloat()
    }

    /* floating-point to integer conversion (rounding) */
    fun SKP_float2short_array(out: ShortArray, out_offset: Int, `in`: FloatArray?, in_offset: Int,
            length: Int) {
        var k: Int
        k = length - 1
        while (k >= 0) {
            val x = `in`!![in_offset + k].toDouble()
            out[out_offset + k] = SigProcFIX.SKP_SAT16((if (x > 0) x + 0.5 else x - 0.5).toInt()).toShort()
            k--
        }
    }

    /* floating-point to integer conversion (rounding) */
    fun SKP_float2int(x: Double): Int {
        return (if (x > 0) x + 0.5 else x - 0.5).toInt()
    }

    /* integer to floating-point conversion */
    fun SKP_short2float_array(out: FloatArray?, out_offset: Int, `in`: ShortArray?, in_offset: Int,
            length: Int) {
        var k: Int
        k = length - 1
        while (k >= 0) {
            out!![out_offset + k] = `in`!![in_offset + k].toFloat()
            k--
        }
    }

    // TODO: #define SKP_round(x) (SKP_float)((x)>=0 ? (SKP_int64)((x)+0.5) : (SKP_int64)((x)-0.5))
    fun SKP_round(x: Float): Float {
        return if (x >= 0) (x + 0.5).toLong().toFloat() else (x - 0.5).toLong().toFloat()
    }
}