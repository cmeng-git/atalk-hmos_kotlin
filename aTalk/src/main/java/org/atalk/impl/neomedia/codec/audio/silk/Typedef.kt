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
object Typedef {
    fun SKP_STR_CASEINSENSITIVE_COMPARE(x: String, y: String?): Int {
        return x.compareTo(y!!)
    }

    const val SKP_int64_MAX = 0x7FFFFFFFFFFFFFFFL // 2^63 - 1
    val SKP_int64_MIN = -0x800000000000000L // -2^63
    const val SKP_int32_MAX = 0x7FFFFFFF // 2^31 - 1 = 2147483647
    const val SKP_int32_MIN = -0x80000000 // -2^31 = -2147483648
    const val SKP_int16_MAX: Short = 0x7FFF // 2^15 - 1 = 32767
    const val SKP_int16_MIN = 0x8000.toShort() // -2^15 = -32768
    const val SKP_int8_MAX: Byte = 0x7F // 2^7 - 1 = 127
    const val SKP_int8_MIN = 0x80.toByte() // -2^7 = -128
    const val SKP_uint32_MAX = 0xFFFFFFFFL // 2^32 - 1 = 4294967295
    const val SKP_uint32_MIN = 0x00000000L
    const val SKP_uint16_MAX = 0xFFFF // 2^16 - 1 = 65535
    const val SKP_uint16_MIN = 0x0000
    const val SKP_uint8_MAX: Short = 0xFF // 2^8 - 1 = 255
    const val SKP_uint8_MIN: Short = 0x00
    const val SKP_TRUE = true
    const val SKP_FALSE = false

    /* assertions */
    fun SKP_assert(COND: Boolean) {
        assert(COND)
    }
}