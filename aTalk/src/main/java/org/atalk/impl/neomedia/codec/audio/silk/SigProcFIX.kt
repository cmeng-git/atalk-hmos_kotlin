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
open class SigProcFIXConstants {
    companion object {
        /**
         * max order of the LPC analysis in schur() and k2a().
         */
        const val SKP_Silk_MAX_ORDER_LPC = 16 /*
												 * max order of the LPC analysis in schur() and
												 * k2a()
												 */

        /**
         * max input length to the correlation.
         */
        const val SKP_Silk_MAX_CORRELATION_LENGTH = 640 /* max input length to the correlation */

        /* Pitch estimator */
        const val SKP_Silk_PITCH_EST_MIN_COMPLEX = 0
        const val SKP_Silk_PITCH_EST_MID_COMPLEX = 1
        const val SKP_Silk_PITCH_EST_MAX_COMPLEX = 2

        /* parameter defining the size and accuracy of the piecewise linear */
        /* cosine approximatin table. */
        const val LSF_COS_TAB_SZ_FIX = 128
        /* rom table with cosine values */
       // (to see rom table value, refer to LSFCosTable.java)
    }
}

object SigProcFIX : SigProcFIXConstants() {
    /**
     * Rotate a32 right by 'rot' bits. Negative rot values result in rotating left. Output is 32bit
     * int.
     *
     * @param a32
     * @param rot
     * @return
     */
    fun SKP_ROR32(a32: Int, rot: Int): Int {
        return if (rot <= 0) a32 shl -rot or (a32 ushr 32) + rot else a32 shl 32 - rot or (a32 ushr rot)
    }
    /* fixed point */
    /**
     * (a32 * b32) output have to be 32bit int
     */
    fun SKP_MUL(a32: Int, b32: Int): Int {
        return a32 * b32
    }

    /**
     * (a32 * b32) output have to be 32bit uint.
     *
     * @param a32
     * @param b32
     * @return
     */
    fun SKP_MUL_uint(a32: Long, b32: Long): Long {
        return a32 * b32
    }

    /**
     * a32 + (b32 * c32) output have to be 32bit int
     *
     * @param a32
     * @param b32
     * @param c32
     * @return
     */
    fun SKP_MLA(a32: Int, b32: Int, c32: Int): Int {
        return a32 + b32 * c32
    }

    /**
     * a32 + (b32 * c32) output have to be 32bit uint
     *
     * @param a32
     * @param b32
     * @param c32
     * @return
     */
    fun SKP_MLA_uint(a32: Long, b32: Long, c32: Long): Long {
        return a32 + b32 * c32
    }

    /**
     * ((a32 >> 16) * (b32 >> 16)) output have to be 32bit int
     *
     * @param a32
     * @param b32
     * @return
     */
    fun SKP_SMULTT(a32: Int, b32: Int): Int {
        return (a32 shr 16) * (b32 shr 16)
    }

    /**
     * a32 + ((b32 >> 16) * (c32 >> 16)) output have to be 32bit int
     *
     * @param a32
     * @param b32
     * @param c32
     * @return
     */
    fun SKP_SMLATT(a32: Int, b32: Int, c32: Int): Int {
        return a32 + (b32 shr 16) * (c32 shr 16)
    }

    /**
     * SKP_ADD64((a64),(SKP_int64)((SKP_int32)(b16) * (SKP_int32)(c16))).
     *
     * @param a64
     * @param b16
     * @param c16
     * @return
     */
    fun SKP_SMLALBB(a64: Long, b16: Short, c16: Short): Long {
        return a64 + b16 * c16
    }

    /**
     * (a32 * b32)
     *
     * @param a32
     * @param b32
     * @return
     */
    fun SKP_SMULL(a32: Int, b32: Int): Long {
        return a32.toLong() * b32
    }
    // multiply-accumulate macros that allow overflow in the addition (ie, no asserts in debug mode)
    /**
     * SKP_MLA(a32, b32, c32).
     */
    fun SKP_MLA_ovflw(a32: Int, b32: Int, c32: Int): Int {
        return a32 + b32 * c32
    }

    /**
     * SKP_SMLABB(a32, b32, c32)
     *
     * @param a32
     * @param b32
     * @param c32
     * @return
     */
    fun SKP_SMLABB_ovflw(a32: Int, b32: Int, c32: Int): Int {
        return a32 + b32.toShort() * c32.toShort()
    }

    /**
     * SKP_SMLABT(a32, b32, c32)
     *
     * @param a32
     * @param b32
     * @param c32
     * @return
     */
    fun SKP_SMLABT_ovflw(a32: Int, b32: Int, c32: Int): Int {
        return a32 + b32.toShort() * (c32 shr 16)
    }

    /**
     * SKP_SMLATT(a32, b32, c32)
     *
     * @param a32
     * @param b32
     * @param c32
     * @return
     */
    fun SKP_SMLATT_ovflw(a32: Int, b32: Int, c32: Int): Int {
        return a32 + (b32 shr 16) * (c32 shr 16)
    }

    /**
     * SKP_SMLAWB(a32, b32, c32)
     *
     * @param a32
     * @param b32
     * @param c32
     * @return
     */
    fun SKP_SMLAWB_ovflw(a32: Int, b32: Int, c32: Int): Int {
        return a32 + ((b32 shr 16) * c32.toShort() + ((b32 and 0x0000FFFF) * c32.toShort() shr 16))
    }

    /**
     * SKP_SMLAWT(a32, b32, c32)
     *
     * @param a32
     * @param b32
     * @param c32
     * @return
     */
    fun SKP_SMLAWT_ovflw(a32: Int, b32: Int, c32: Int): Int {
        return a32 + (b32 shr 16) * (c32 shr 16) + ((b32 and 0x0000FFFF) * (c32 shr 16) shr 16)
    }

    /**
     * ((a64)/(b32)) TODO: rewrite it as a set of SKP_DIV32.
     */
    fun SKP_DIV64_32(a64: Long, b32: Int): Long {
        return a64 / b32
    }

    /**
     * ((int)((a32) / (b16)))
     *
     * @param a32
     * @param b16
     * @return
     */
    fun SKP_DIV32_16(a32: Int, b16: Short): Int {
        return a32 / b16
    }

    /**
     * ((SKP_int32)((a32) / (b32)))
     *
     * @param a32
     * @param b32
     * @return
     */
    fun SKP_DIV32(a32: Int, b32: Int): Int {
        return a32 / b32
    }
    // These macros enables checking for overflow in SKP_Silk_API_Debug.h
    /**
     * ((a) + (b))
     */
    fun SKP_ADD16(a: Short, b: Short): Short {
        return (a + b).toShort()
    }

    /**
     * ((a) + (b))
     *
     * @param a
     * @param b
     * @return
     */
    fun SKP_ADD32(a: Int, b: Int): Int {
        return a + b
    }

    /**
     * ((a) + (b))
     *
     * @param a
     * @param b
     * @return
     */
    fun SKP_ADD64(a: Long, b: Long): Long {
        return a + b
    }

    /**
     * ((a) - (b))
     *
     * @param a
     * @param b
     * @return
     */
    fun SKP_SUB16(a: Short, b: Short): Short {
        return (a - b).toShort()
    }

    /**
     * ((a) - (b))
     *
     * @param a
     * @param b
     * @return
     */
    fun SKP_SUB32(a: Int, b: Int): Int {
        return a - b
    }

    /**
     * ((a) - (b))
     *
     * @param a
     * @param b
     * @return
     */
    fun SKP_SUB64(a: Long, b: Long): Long {
        return a - b
    }

    fun SKP_SAT8(a: Int): Int {
        return when {
            a > Byte.MAX_VALUE -> Byte.MAX_VALUE.toInt()
            a < Byte.MIN_VALUE -> Byte.MIN_VALUE.toInt()
            else -> a
        }
    }

    fun SKP_SAT16(a: Int): Int {
        return when {
            a > Short.MAX_VALUE -> Short.MAX_VALUE.toInt()
            a < Short.MIN_VALUE -> Short.MIN_VALUE.toInt()
            else -> a
        }
    }

    fun SKP_SAT32(a: Long): Long {
        return when {
            a > Int.MAX_VALUE -> Int.MAX_VALUE.toLong()
            a < Int.MIN_VALUE -> Int.MIN_VALUE.toLong()
            else -> a
        }
    }

    /**
     * (a)
     *
     * @param a
     * @return
     */
    fun SKP_CHECK_FIT8(a: Int): Byte {
        return a.toByte()
    }

    /**
     * (a)
     *
     * @param a
     * @return
     */
    fun SKP_CHECK_FIT16(a: Int): Short {
        return a.toShort()
    }

    /**
     * (a)
     *
     * @param a
     * @return
     */
    fun SKP_CHECK_FIT32(a: Int): Int {
        return a
    }

    fun SKP_ADD_SAT16(a: Short, b: Short): Short {
        return SKP_SAT16(a + b).toShort()
    }

    fun SKP_ADD_SAT64(a: Long, b: Long): Long {
        return when {
            (a + b) and -0x800000000000000L == 0L -> if (a and b and (-0x800000000000000L).toLong() != 0L) Long.MIN_VALUE else a + b
            (a or b) and -0x800000000000000L == 0L -> Long.MAX_VALUE
            else -> a + b
        }
    }

    fun SKP_SUB_SAT16(a: Short, b: Short): Short {
        return SKP_SAT16(a - b).toShort()
    }

    fun SKP_SUB_SAT64(a: Long, b: Long): Long {
        return when {
            (a - b) and -0x800000000000000L == 0L -> when {
                a and (b xor -0x800000000000000L) and -0x800000000000000L != 0L -> Long.MIN_VALUE
                else -> (a - b)
            }
            a xor (-0x800000000000000L) and b and (-0x800000000000000L) != 0L -> Long.MAX_VALUE
            else -> (a
                    - b)
        }
    }

    /* Saturation for positive input values */
    fun SKP_POS_SAT32(a: Long): Long {
        return if (a > Int.MAX_VALUE) Int.MAX_VALUE.toLong() else a
    }

    /* Add with saturation for positive input values */
    fun SKP_ADD_POS_SAT8(a: Byte, b: Byte): Byte {
        return if (a + b and 0x80 != 0) Byte.MAX_VALUE else (a + b).toByte()
    }

    fun SKP_ADD_POS_SAT16(a: Short, b: Short): Short {
        return if (a + b and 0x8000 != 0) Short.MAX_VALUE else (a + b).toShort()
    }

    fun SKP_ADD_POS_SAT32(a: Int, b: Int): Int {
        return if (a + b and -0x80000000 != 0) Int.MAX_VALUE else a + b
    }

    fun SKP_ADD_POS_SAT64(a: Long, b: Long): Long {
        return if ((a + b) and -0x800000000000000L != 0L) Long.MAX_VALUE else a + b
    }

    /**
     * ((a)<<(shift)) // shift >= 0, shift < 8
     *
     * @param a
     * @param shift
     * @return
     */
    fun SKP_LSHIFT8(a: Byte, shift: Int): Byte {
        return (a.toInt() shl shift).toByte()
    }

    /**
     * ((a)<<(shift)) // shift >= 0, shift < 16
     *
     * @param a
     * @param shift
     * @return
     */
    fun SKP_LSHIFT16(a: Short, shift: Int): Short {
        return (a.toInt() shl shift).toShort()
    }

    /**
     * ((a)<<(shift)) // shift >= 0, shift < 32
     *
     * @param a
     * @param shift
     * @return
     */
    fun SKP_LSHIFT32(a: Int, shift: Int): Int {
        return a shl shift
    }

    /**
     * ((a)<<(shift)) // shift >= 0, shift < 64
     *
     * @param a
     * @param shift
     * @return
     */
    fun SKP_LSHIFT64(a: Long, shift: Int): Long {
        return a shl shift
    }

    /**
     * (a, shift) SKP_LSHIFT32(a, shift) // shift >= 0, shift < 32
     *
     * @param a
     * @param shift
     * @return
     */
    fun SKP_LSHIFT(a: Int, shift: Int): Int {
        return a shl shift
    }

    /**
     * ((a)>>(shift)) // shift >= 0, shift < 8
     *
     * @param a
     * @param shift
     * @return
     */
    fun SKP_RSHIFT8(a: Byte, shift: Int): Byte {
        return (a.toInt() shr shift).toByte()
    }

    /**
     * ((a)>>(shift)) // shift >= 0, shift < 16
     *
     * @param a
     * @param shift
     * @return
     */
    fun SKP_RSHIFT16(a: Short, shift: Int): Short {
        return (a.toInt() shr shift).toShort()
    }

    /**
     * ((a)>>(shift)) // shift >= 0, shift < 32
     *
     * @param a
     * @param shift
     * @return
     */
    fun SKP_RSHIFT32(a: Int, shift: Int): Int {
        return a shr shift
    }

    /**
     * ((a)>>(shift)) // shift >= 0, shift < 64
     *
     * @param a
     * @param shift
     * @return
     */
    fun SKP_RSHIFT64(a: Long, shift: Int): Long {
        return a shr shift
    }

    /**
     * SKP_RSHIFT32(a, shift) // shift >= 0, shift < 32
     *
     * @param a
     * @param shift
     * @return
     */
    fun SKP_RSHIFT(a: Int, shift: Int): Int {
        return a shr shift
    }

    /* saturates before shifting */
    fun SKP_LSHIFT_SAT16(a: Short, shift: Int): Short {
        return SKP_LSHIFT16(
                SKP_LIMIT_16(a, (Short.MIN_VALUE.toInt() shr shift).toShort(), (Short.MAX_VALUE.toInt() shr shift).toShort()),
                shift)
    }

    fun SKP_LSHIFT_SAT32(a: Int, shift: Int): Int {
        return SKP_LSHIFT32(SKP_LIMIT(a, Int.MIN_VALUE shr shift, Int.MAX_VALUE shr shift),
                shift)
    }

    /**
     * ((a)<<(shift)) // shift >= 0, allowed to overflow
     *
     * @param a
     * @param shift
     * @return
     */
    fun SKP_LSHIFT_ovflw(a: Int, shift: Int): Int {
        return a shl shift
    }

    /**
     * ((a)<<(shift)) // shift >= 0
     *
     * @param a
     * @param shift
     * @return
     */
    fun SKP_LSHIFT_uint(a: Int, shift: Int): Int {
        return a shl shift
    }

    /**
     * ((a)>>(shift)) // shift >= 0
     *
     * @param a
     * @param shift
     * @return
     */
    fun SKP_RSHIFT_uint(a: Int, shift: Int): Int {
        return a ushr shift
    }

    /**
     * ((a) + SKP_LSHIFT((b), (shift))) // shift >= 0
     *
     * @param a
     * @param b
     * @param shift
     * @return
     */
    fun SKP_ADD_LSHIFT(a: Int, b: Int, shift: Int): Int {
        return a + (b shl shift)
    }

    /**
     * SKP_ADD32((a), SKP_LSHIFT32((b), (shift))) // shift >= 0
     *
     * @param a
     * @param b
     * @param shift
     * @return
     */
    fun SKP_ADD_LSHIFT32(a: Int, b: Int, shift: Int): Int {
        return a + (b shl shift)
    }

    /**
     * ((a) + SKP_LSHIFT_uint((b), (shift))) // shift >= 0
     *
     * @param a
     * @param b
     * @param shift
     * @return
     */
    fun SKP_ADD_LSHIFT_uint(a: Int, b: Int, shift: Int): Int {
        return a + (b shl shift)
    }

    /**
     * ((a) + SKP_RSHIFT((b), (shift))) // shift >= 0
     *
     * @param a
     * @param b
     * @param shift
     * @return
     */
    fun SKP_ADD_RSHIFT(a: Int, b: Int, shift: Int): Int {
        return a + (b shr shift)
    }

    /**
     * SKP_ADD32((a), SKP_RSHIFT32((b), (shift))) // shift >= 0
     *
     * @param a
     * @param b
     * @param shift
     * @return
     */
    fun SKP_ADD_RSHIFT32(a: Int, b: Int, shift: Int): Int {
        return a + (b shr shift)
    }

    /**
     * ((a) + SKP_RSHIFT_uint((b), (shift))) // shift >= 0
     *
     * @param a
     * @param b
     * @param shift
     * @return
     */
    fun SKP_ADD_RSHIFT_uint(a: Int, b: Int, shift: Int): Int {
        return a + (b ushr shift)
    }

    /**
     * SKP_SUB32((a), SKP_LSHIFT32((b), (shift))) // shift >= 0
     *
     * @param a
     * @param b
     * @param shift
     * @return
     */
    fun SKP_SUB_LSHIFT32(a: Int, b: Int, shift: Int): Int {
        return a - (b shl shift)
    }

    /**
     * SKP_SUB32((a), SKP_RSHIFT32((b), (shift))) // shift >= 0
     *
     * @param a
     * @param b
     * @param shift
     * @return
     */
    fun SKP_SUB_RSHIFT32(a: Int, b: Int, shift: Int): Int {
        return a - (b shr shift)
    }
    /* Requires that shift > 0 */
    /**
     * ((shift) == 1 ? ((a) >> 1) + ((a) & 1) : (((a) >> ((shift) - 1)) + 1) >> 1)
     */
    fun SKP_RSHIFT_ROUND(a: Int, shift: Int): Int {
        return if (shift == 1) (a shr 1) + (a and 1) else a shr shift - 1 + 1 shr 1
    }

    /**
     * ((shift) == 1 ? ((a) >> 1) + ((a) & 1) : (((a) >> ((shift) - 1)) + 1) >> 1)
     *
     * @param a
     * @param shift
     * @return
     */
    fun SKP_RSHIFT_ROUND64(a: Long, shift: Int): Long {
        return if (shift == 1) (a shr 1) + (a and 1L) else a shr shift - 1 + 1 shr 1
    }

    /* Number of rightshift required to fit the multiplication */
    fun SKP_NSHIFT_MUL_32_32(a: Int, b: Int): Int {
        return -(31 - (32 - Macros.SKP_Silk_CLZ32(Math.abs(a)) + (32 - Macros.SKP_Silk_CLZ32(Math.abs(b)))))
    }

    fun SKP_NSHIFT_MUL_16_16(a: Short, b: Short): Int {
        return -(15 - (16 - Macros.SKP_Silk_CLZ16(Math.abs(a.toInt()).toShort()) + (16 - Macros.SKP_Silk_CLZ16(Math
                .abs(b.toInt()).toShort()))))
    }

    fun SKP_min(a: Int, b: Int): Int {
        return if (a < b) a else b
    }

    fun SKP_max(a: Int, b: Int): Int {
        return if (a > b) a else b
    }
    /* Macro to convert floating-point constants to fixed-point */
    /**
     * ((int)((C) * (1 << (Q)) + 0.5))
     */
    fun SKP_FIX_CONST(C: Float, Q: Int): Int {
        return (C * (1 shl Q) + 0.5).toInt()
    }

    /* SKP_min() versions with typecast in the function call */
    fun SKP_min_int(a: Int, b: Int): Int {
        return if (a < b) a else b
    }

    fun SKP_min_16(a: Short, b: Short): Short {
        return if (a < b) a else b
    }

    fun SKP_min_32(a: Int, b: Int): Int {
        return if (a < b) a else b
    }

    fun SKP_min_64(a: Long, b: Long): Long {
        return if (a < b) a else b
    }

    /* SKP_min() versions with typecast in the function call */
    fun SKP_max_int(a: Int, b: Int): Int {
        return if (a > b) a else b
    }

    fun SKP_max_16(a: Short, b: Short): Short {
        return if (a > b) a else b
    }

    fun SKP_max_32(a: Int, b: Int): Int {
        return if (a > b) a else b
    }

    fun SKP_max_64(a: Long, b: Long): Long {
        return if (a > b) a else b
    }

    fun SKP_LIMIT(a: Int, limit1: Int, limit2: Int): Int {
        return if (limit1 > limit2) if (a > limit1) limit1 else (if (a < limit2) limit2 else a) else if (a > limit2) limit2 else if (a < limit1) limit1 else a
    }

    fun SKP_LIMIT(a: Float, limit1: Float, limit2: Float): Float {
        return if (limit1 > limit2) if (a > limit1) limit1 else (if (a < limit2) limit2 else a) else if (a > limit2) limit2 else if (a < limit1) limit1 else a
    }

    fun SKP_LIMIT_int(a: Int, limit1: Int, limit2: Int): Int {
        return if (limit1 > limit2) if (a > limit1) limit1 else (if (a < limit2) limit2 else a) else if (a > limit2) limit2 else if (a < limit1) limit1 else a
    }

    fun SKP_LIMIT_16(a: Short, limit1: Short, limit2: Short): Short {
        return if (limit1 > limit2) if (a > limit1) limit1 else (if (a < limit2) limit2 else a) else if (a > limit2) limit2 else if (a < limit1) limit1 else a
    }

    fun SKP_LIMIT_32(a: Int, limit1: Int, limit2: Int): Int {
        return if (limit1 > limit2) if (a > limit1) limit1 else (if (a < limit2) limit2 else a) else if (a > limit2) limit2 else if (a < limit1) limit1 else a
    }

    /**
     * (((a) > 0) ? (a) : -(a)) Be careful, SKP_abs returns wrong when input equals to SKP_intXX_MIN
     *
     * @param a
     * @return
     */
    fun SKP_abs(a: Int): Int {
        return if (a > 0) a else -a
    }

    fun SKP_abs_int(a: Int): Int {
        return a xor (a shr Integer.SIZE) - 1 - (a shr Integer.SIZE - 1)
    }

    fun SKP_abs_int32(a: Int): Int {
        return (a xor (a shr 31)) - (a shr 31)
    }

    fun SKP_abs_int64(a: Long): Long {
        return if (a > 0) a else -a
    }

    fun SKP_sign(a: Int): Int {
        return if (a > 0) 1 else if (a < 0) -1 else 0
    }

    fun SKP_sqrt(a: Int): Double {
        return Math.sqrt(a.toDouble())
    }

    /**
     * PSEUDO-RANDOM GENERATOR Make sure to store the result as the seed for the next call (also in
     * between frames), otherwise result won't be random at all. When only using some of the bits,
     * take the most significant bits by right-shifting. Do not just mask off the lowest bits.
     * SKP_RAND(seed) (SKP_MLA_ovflw(907633515, (seed), 196314165))
     *
     * @param seed
     * @return
     */
    fun SKP_RAND(seed: Int): Int {
        return 907633515 + seed * 196314165
    }

    // Add some multiplication functions that can be easily mapped to ARM.
    // SKP_SMMUL: Signed top word multiply.
    // ARMv6 2 instruction cycles.
    // ARMv3M+ 3 instruction cycles. use SMULL and ignore LSB registers.(except xM)
    // #define SKP_SMMUL(a32, b32) (SKP_int32)SKP_RSHIFT(SKP_SMLAL(SKP_SMULWB((a32), (b32)), (a32),
    // SKP_RSHIFT_ROUND((b32), 16)), 16)
    // the following seems faster on x86
    // #define SKP_SMMUL(a32, b32) (SKP_int32)SKP_RSHIFT64(SKP_SMULL((a32), (b32)), 32)
    fun SKP_SMMUL(a32: Int, b32: Int): Int {
        return (a32.toLong() * b32 shr 32).toInt()
    }
}