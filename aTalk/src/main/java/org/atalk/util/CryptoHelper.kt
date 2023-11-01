package org.atalk.util

import java.util.regex.Pattern

object CryptoHelper {
    const val FILETRANSFER = "?FILETRANSFERv1:"
    private val hexArray = "0123456789abcdef".toCharArray()
    val UUID_PATTERN = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    val ONE = byteArrayOf(0, 0, 0, 1)
    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    fun hexToBytes(hexString: String): ByteArray {
        val len = hexString.length
        val array = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            array[i / 2] = ((hexString[i].digitToIntOrNull(16)
                    ?: -1 shl 4) + hexString[i + 1].digitToIntOrNull(16)!! ?: -1).toByte()
            i += 2
        }
        return array
    }

    fun hexToString(hexString: String): String {
        return String(hexToBytes(hexString))
    }

    fun concatenateByteArrays(a: ByteArray, b: ByteArray): ByteArray {
        val result = ByteArray(a.size + b.size)
        System.arraycopy(a, 0, result, 0, a.size)
        System.arraycopy(b, 0, result, a.size, b.size)
        return result
    }

    fun prettifyFingerprint(fingerprint: String?): String {
        if (fingerprint == null) {
            return ""
        } else if (fingerprint.length < 40) {
            return fingerprint
        }
        val builder = StringBuilder(fingerprint.lowercase().replace("\\s".toRegex(), ""))
        var i = 8
        while (i < builder.length) {
            builder.insert(i, ' ')
            i += 9
        }
        return builder.toString()
    }

    fun prettifyFingerprintCert(fingerprint: String?): String {
        val builder = StringBuilder(fingerprint)
        var i = 2
        while (i < builder.length) {
            builder.insert(i, ':')
            i += 3
        }
        return builder.toString()
    }
}