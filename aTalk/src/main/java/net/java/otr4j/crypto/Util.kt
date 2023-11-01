/*
 *  Java OTR library
 *  Copyright (C) 2008-2009  Ian Goldberg, Muhaimeen Ashraf, Andrew Chung,
 *                           Can Tang
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of version 2.1 of the GNU Lesser General
 *  Public License as published by the Free Software Foundation.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package net.java.otr4j.crypto

object Util {
    fun arrayEquals(b1: ByteArray?, b2: ByteArray?): Boolean {
        if (b1 == null || b2 == null || b1.size != b2.size) {
            return false
        }
        for (i in b1.indices) {
            if (b1[i] != b2[i]) {
                return false
            }
        }
        return true
    }

    fun checkBytes(s: String, bytes: ByteArray) {
        val hexString = StringBuilder()
        for (i in bytes.indices) {
            hexString.append(Integer.toHexString(bytes[i].toInt() ushr 4 and 0x0F))
                    .append(Integer.toHexString(0x0F and bytes[i].toInt()))
        }
        println("$s: $hexString")
    }

    fun writeInt(dst: ByteArray, index: Int, src: Int) {
        dst[index] = (src shr 24 and 0xff).toByte()
        dst[index + 1] = (src shr 16 and 0xff).toByte()
        dst[index + 2] = (src shr 8 and 0xff).toByte()
        dst[index + 3] = (src and 0xff).toByte()
    }

    fun readInt(src: ByteArray, index: Int): Int {
        return (src[index].toInt() shl 24
                or ((src[index + 1].toInt() shl 16) and 0xff0000)
                or ((src[index + 2].toInt() shl 8) and 0xff00)
                or (src[index + 3].toInt() and 0xff))
    }

    fun hexStringToBytes(s: String): ByteArray? {
        val sbytes = s.toByteArray()
        if (sbytes.size % 2 != 0) return null
        val ret = ByteArray(sbytes.size / 2)
        for (i in ret.indices) {
            if (sbytes[2 * i] >= 'A'.code.toByte() && sbytes[2 * i] <= 'F'.code.toByte()) {
                ret[i] = (sbytes[2 * i] - ('A'.code - 10) shl 4).toByte()
            } else if (sbytes[2 * i] >= 'a'.code.toByte() && sbytes[2 * i] <= 'f'.code.toByte()) {
                ret[i] = (sbytes[2 * i] - ('a'.code - 10) shl 4).toByte()
            } else {
                ret[i] = (sbytes[2 * i] - '0'.code.toByte() shl 4).toByte()
            }
            if (sbytes[2 * i + 1] >= 'A'.code.toByte() && sbytes[2 * i + 1] <= 'F'.code.toByte()) {
                ret[i] = (ret[i].toInt() or (sbytes[2 * i + 1] - ('A'.code - 10)).toByte().toInt()).toByte()
            } else if (sbytes[2 * i + 1] >= 'a'.code.toByte() && sbytes[2 * i + 1] <= 'f'.code.toByte()) {
                ret[i] = (ret[i].toInt() or (sbytes[2 * i + 1] - ('a'.code - 10)).toByte().toInt()).toByte()
            } else {
                ret[i] = (ret[i].toInt() or (sbytes[2 * i + 1] - '0'.code.toByte()).toByte().toInt()).toByte()
            }
        }
        return ret
    }

    fun bytesToHexString(mpi: ByteArray): String {
        val hex = ByteArray(2 * mpi.size)
        for (i in mpi.indices) {
            var num = (mpi[i].toInt() shr 4 and 0xf)
            if (num <= 9) {
                hex[2 * i] = ('0'.code + num).toByte()
            } else {
                hex[2 * i] = ('A'.code + num - 10).toByte()
            }
            num = (mpi[i].toInt() and 0xf)
            if (num <= 9) {
                hex[2 * i + 1] = ('0'.code + num).toByte()
            } else {
                hex[2 * i + 1] = ('A'.code + num - 10).toByte()
            }
        }
        return String(hex)
    }
}