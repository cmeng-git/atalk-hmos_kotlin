/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

import kotlin.math.abs

/**
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
open class InlinesConstants {
    companion object {
        const val SKP_SIN_APPROX_CONST0 = 1073735400
        const val SKP_SIN_APPROX_CONST1 = -82778932
        const val SKP_SIN_APPROX_CONST2 = 1059577
        const val SKP_SIN_APPROX_CONST3 = -5013
    }
}

object Inlines : InlinesConstants() {
    /**
     * count leading zeros of long.
     *
     * @param in
     * . input
     * @return
     */
    fun SKP_Silk_CLZ64(`in`: Long): Int {
        return java.lang.Long.numberOfLeadingZeros(`in`)
    }

    /**
     * get number of leading zeros and fractional part (the bits right after the leading one).
     *
     * @param in
     * @param lz
     * @param frac_Q7
     */
    fun SKP_Silk_CLZ_FRAC(`in`: Int,  /* I: input */
            lz: IntArray,  /* O: number of leading zeros */
            frac_Q7: IntArray) /* O: the 7 bits right after the leading one */ {
        val lzeros = Integer.numberOfLeadingZeros(`in`)
        lz[0] = lzeros
        frac_Q7[0] = SigProcFIX.SKP_ROR32(`in`, 24 - lzeros) and 0x7f
    }

    /**
     * Approximation of square root Accuracy: < +/- 10% for output values > 15 < +/- 2.5% for output
     * values > 120
     *
     * @param x
     * @return
     */
    fun SKP_Silk_SQRT_APPROX(x: Int): Int {
        var y: Int
        val lz = IntArray(1)
        val frac_Q7 = IntArray(1)
        if (x <= 0) {
            return 0
        }
        SKP_Silk_CLZ_FRAC(x, lz, frac_Q7)
        y = if (lz[0] and 1 != 0) {
            32768
        } else {
            46214 /* 46214 = sqrt(2) * 32768 */
        }

        /* get scaling right */
        y = y shr (lz[0] shr 1)

        /* increment using fractional part of input */
        y = Macros.SKP_SMLAWB(y, y, Macros.SKP_SMULBB(213, frac_Q7[0]))
        return y
    }

    /**
     * returns the number of left shifts before overflow for a 16 bit number (ITU definition with
     * norm(0)=0).
     *
     * @param a
     * @return
     */
    fun SKP_Silk_norm16(a: Short): Int {

        /* if ((a == 0) || (a == SKP_int16_MIN)) return(0); */
        if (a.toInt() shl 1 == 0) return 0
        var a32 = a.toInt()
        /* if (a32 < 0) a32 = -a32 - 1; */
        a32 = a32 xor (a32 shr 31)
        return Integer.numberOfLeadingZeros(a32) - 17
    }

    /**
     * returns the number of left shifts before overflow for a 32 bit number (ITU definition with
     * norm(0)=0)
     *
     * @param a
     * @return
     */
    fun SKP_Silk_norm32(a: Int): Int {

        /* if ((a == 0) || (a == Interger.MIN_VALUE)) return(0); */
        var a = a
        if (a shl 1 == 0) return 0

        /* if (a < 0) a = -a - 1; */
        a = a xor (a shr 31)
        return Integer.numberOfLeadingZeros(a) - 1
    }

    /**
     * Divide two int32 values and return result as int32 in a given Q-domain.
     *
     * @param a32
     * numerator (Q0)
     * @param b32
     * denominator (Q0)
     * @param Qres
     * Q-domain of result (>= 0)
     * @return returns a good approximation of "(a32 << Qres) / b32"
     */
    fun SKP_DIV32_varQ /* O returns a good approximation of "(a32 << Qres) / b32" */(a32: Int,  /* I numerator (Q0) */
            b32: Int,  /* I denominator (Q0) */
            Qres: Int /* I Q-domain of result (>= 0) */
    ): Int {
        val lshift: Int
        val b32_inv: Int
        var a32_nrm: Int
        val b32_nrm: Int
        assert(b32 != 0)
        assert(Qres >= 0)

        /* Compute number of bits head room and normalize inputs */
        val a_headrm = Integer.numberOfLeadingZeros(abs(a32)) - 1
        a32_nrm = a32 shl a_headrm /* Q: a_headrm */
        val b_headrm = Integer.numberOfLeadingZeros(abs(b32)) - 1
        b32_nrm = b32 shl b_headrm /* Q: b_headrm */

        /* Inverse of b32, with 14 bits of precision */
        b32_inv = (Int.MAX_VALUE shr 2) / (b32_nrm shr 16) /* Q: 29 + 16 - b_headrm */

        /* First approximation */
        var result = Macros.SKP_SMULWB(a32_nrm, b32_inv) /* Q: 29 + a_headrm - b_headrm */

        /* Compute residual by subtracting product of denominator and first approximation */
        a32_nrm -= SigProcFIX.SKP_SMMUL(b32_nrm, result) shl 3 /* Q: a_headrm */

        /* Refinement */
        result = Macros.SKP_SMLAWB(result, a32_nrm, b32_inv) /* Q: 29 + a_headrm - b_headrm */

        /* Convert to Qres domain */
        lshift = 29 + a_headrm - b_headrm - Qres
        return if (lshift <= 0) {
            SigProcFIX.SKP_LSHIFT_SAT32(result, -lshift)
        } else {
            if (lshift < 32) {
                result shr lshift
            } else {
                /* Avoid undefined result */
                0
            }
        }
    }

    /**
     * Invert int32 value and return result as int32 in a given Q-domain.
     *
     * @param b32
     * denominator (Q0)
     * @param Qres
     * Q-domain of result (> 0)
     * @return returns a good approximation of "(1 << Qres) / b32"
     */
    fun SKP_INVERSE32_varQ /* O returns a good approximation of "(1 << Qres) / b32" */(b32: Int,  /* I denominator (Q0) */
            Qres: Int /* I Q-domain of result (> 0) */
    ): Int {
        val lshift: Int
        val b32_inv: Int
        val b32_nrm: Int
        assert(b32 != 0)
        assert(Qres > 0)

        /* Compute number of bits head room and normalize input */
        val b_headrm = Integer.numberOfLeadingZeros(Math.abs(b32)) - 1
        b32_nrm = b32 shl b_headrm /* Q: b_headrm */

        /* Inverse of b32, with 14 bits of precision */
        b32_inv = (Int.MAX_VALUE shr 2) / (b32_nrm shr 16) /* Q: 29 + 16 - b_headrm */

        /* First approximation */
        var result = b32_inv shl 16 /* Q: 61 - b_headrm */

        /* Compute residual by subtracting product of denominator and first approximation from one */
        val err_Q32 = -Macros.SKP_SMULWB(b32_nrm, b32_inv) shl 3 /* Q32 */

        /* Refinement */
        result = Macros.SKP_SMLAWW(result, err_Q32, b32_inv) /* Q: 61 - b_headrm */

        /* Convert to Qres domain */
        lshift = 61 - b_headrm - Qres
        return if (lshift <= 0) {
            SigProcFIX.SKP_LSHIFT_SAT32(result, -lshift)
        } else {
            if (lshift < 32) {
                result shr lshift
            } else {
                /* Avoid undefined result */
                0
            }
        }
    }

    /**
     * Sine approximation; an input of 65536 corresponds to 2 * pi Uses polynomial expansion of the
     * input to the power 0, 2, 4 and 6 The relative error is below 1e-5
     *
     * @param x
     * @return returns approximately 2^24 * sin(x * 2 * pi / 65536).
     */
    private fun SKP_Silk_SIN_APPROX_Q24( /* O returns approximately 2^24 * sin(x * 2 * pi / 65536) */
            x: Int): Int {
        var x = x
        var y_Q30: Int

        /* Keep only bottom 16 bits (the function repeats itself with period 65536) */
        x = x and 65535

        /* Split range in four quadrants */
        if (x <= 32768) {
            if (x < 16384) {
                /* Return cos(pi/2 - x) */
                x = 16384 - x
            } else {
                /* Return cos(x - pi/2) */
                x -= 16384
            }
            if (x < 1100) {
                /* Special case: high accuracy */
                return Macros.SKP_SMLAWB(1 shl 24, x * x, -5053)
            }
            x = Macros.SKP_SMULWB(x shl 8, x) /* contains x^2 in Q20 */
            y_Q30 = Macros.SKP_SMLAWB(SKP_SIN_APPROX_CONST2, x, SKP_SIN_APPROX_CONST3)
            y_Q30 = Macros.SKP_SMLAWW(SKP_SIN_APPROX_CONST1, x, y_Q30)
            y_Q30 = Macros.SKP_SMLAWW(SKP_SIN_APPROX_CONST0 + 66, x, y_Q30)
        } else {
            if (x < 49152) {
                /* Return -cos(3*pi/2 - x) */
                x = 49152 - x
            } else {
                /* Return -cos(x - 3*pi/2) */
                x -= 49152
            }
            if (x < 1100) {
                /* Special case: high accuracy */
                return Macros.SKP_SMLAWB(-1 shl 24, x * x, 5053)
            }
            x = Macros.SKP_SMULWB(x shl 8, x) /* contains x^2 in Q20 */
            y_Q30 = Macros.SKP_SMLAWB(-SKP_SIN_APPROX_CONST2, x, -SKP_SIN_APPROX_CONST3)
            y_Q30 = Macros.SKP_SMLAWW(-SKP_SIN_APPROX_CONST1, x, y_Q30)
            y_Q30 = Macros.SKP_SMLAWW(-SKP_SIN_APPROX_CONST0, x, y_Q30)
        }
        return SigProcFIX.SKP_RSHIFT_ROUND(y_Q30, 6)
    }

    /**
     * Cosine approximation; an input of 65536 corresponds to 2 * pi The relative error is below
     * 1e-5
     *
     * @param x
     * @return returns approximately 2^24 * cos(x * 2 * pi / 65536).
     */
    fun SKP_Silk_COS_APPROX_Q24( /* O returns approximately 2^24 * cos(x * 2 * pi / 65536) */
            x: Int): Int {
        return SKP_Silk_SIN_APPROX_Q24(x + 16384)
    }
}