/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Translated from what is an inline header file for general platform.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object Macros {
    // (a32 * (SKP_int32)((SKP_int16)(b32))) >> 16 output have to be 32bit int
    fun SKP_SMULWB(a32: Int, b32: Int): Int {
        return (a32 shr 16) * b32.toShort() + ((a32 and 0x0000FFFF) * b32.toShort() shr 16)
    }

    // a32 + (b32 * (SKP_int32)((SKP_int16)(c32))) >> 16 output have to be 32bit int
    fun SKP_SMLAWB(a32: Int, b32: Int, c32: Int): Int {
        return a32 + ((b32 shr 16) * c32.toShort() + ((b32 and 0x0000FFFF) * c32.toShort() shr 16))
    }

    // (a32 * (b32 >> 16)) >> 16
    fun SKP_SMULWT(a32: Int, b32: Int): Int {
        return (a32 shr 16) * (b32 shr 16) + ((a32 and 0x0000FFFF) * (b32 shr 16) shr 16)
    }

    // a32 + (b32 * (c32 >> 16)) >> 16
    fun SKP_SMLAWT(a32: Int, b32: Int, c32: Int): Int {
        return a32 + (b32 shr 16) * (c32 shr 16) + ((b32 and 0x0000FFFF) * (c32 shr 16) shr 16)
    }

    // (SKP_int32)((SKP_int16)(a3))) * (SKP_int32)((SKP_int16)(b32)) output have to be 32bit int
    fun SKP_SMULBB(a32: Int, b32: Int): Int {
        return a32.toShort() * b32.toShort()
    }

    // a32 + (SKP_int32)((SKP_int16)(b32)) * (SKP_int32)((SKP_int16)(c32)) output have to be 32bit
    // int
    fun SKP_SMLABB(a32: Int, b32: Int, c32: Int): Int {
        return a32 + b32.toShort() * c32.toShort()
    }

    // (SKP_int32)((SKP_int16)(a32)) * (b32 >> 16)
    fun SKP_SMULBT(a32: Int, b32: Int): Int {
        return a32.toShort() * (b32 shr 16)
    }

    // a32 + (SKP_int32)((SKP_int16)(b32)) * (c32 >> 16)
    fun SKP_SMLABT(a32: Int, b32: Int, c32: Int): Int {
        return a32 + b32.toShort() * (c32 shr 16)
    }

    // a64 + (b32 * c32)
    fun SKP_SMLAL(a64: Long, b32: Int, c32: Int): Long {
        return a64 + b32.toLong() * c32.toLong()
    }

    // (a32 * b32) >> 16
    fun SKP_SMULWW(a32: Int, b32: Int): Int {
        return SKP_SMULWB(a32, b32) + a32 * SigProcFIX.SKP_RSHIFT_ROUND(b32, 16)
    }

    // a32 + ((b32 * c32) >> 16)
    fun SKP_SMLAWW(a32: Int, b32: Int, c32: Int): Int {
        return SKP_SMLAWB(a32, b32, c32) + b32 * SigProcFIX.SKP_RSHIFT_ROUND(c32, 16)
    }

    /* add/subtract with output saturated */
    fun SKP_ADD_SAT32(a: Int, b: Int): Int {
        return if (a + b and -0x80000000 == 0) if (a and b and -0x80000000 != 0) Int.MIN_VALUE else a + b else if (a or b and -0x80000000 == 0) Int.MAX_VALUE else a + b
    }

    fun SKP_SUB_SAT32(a: Int, b: Int): Int {
        return if (a - b and -0x80000000 == 0) if (a and (b xor -0x80000000) and -0x80000000 != 0) Int.MIN_VALUE else a - b else if (a xor -0x80000000 and b and -0x80000000 != 0) Int.MAX_VALUE else a - b
    }

    fun SKP_Silk_CLZ16(in16: Short): Int {
        return Integer.numberOfLeadingZeros(in16.toInt() and 0x0000FFFF) - 16
    }

    fun SKP_Silk_CLZ32(in32: Int): Int {
        return Integer.numberOfLeadingZeros(in32)
    }
}