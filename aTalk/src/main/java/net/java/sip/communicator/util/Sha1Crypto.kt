/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util

import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object Sha1Crypto {
    /**
     * Encodes the given text with the SHA-1 algorithm.
     *
     * @param text the text to encode
     * @return the encoded text
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    @Throws(NoSuchAlgorithmException::class, UnsupportedEncodingException::class)
    fun encode(text: String): String {
        val messageDigest = MessageDigest.getInstance("SHA-1")
        val sha1hash: ByteArray
        messageDigest.update(text.toByteArray(charset("iso-8859-1")), 0, text.length)
        sha1hash = messageDigest.digest()
        return convertToHex(sha1hash)
    }

    /**
     * Encodes the given text with the SHA-1 algorithm.
     *
     * @param byteArray the byte array to encode
     * @return the encoded text
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    @Throws(NoSuchAlgorithmException::class, UnsupportedEncodingException::class)
    fun encode(byteArray: ByteArray?): String {
        val messageDigest = MessageDigest.getInstance("SHA-1")
        val sha1hash: ByteArray
        messageDigest.update(byteArray)
        sha1hash = messageDigest.digest()
        return convertToHex(sha1hash)
    }

    /**
     * Converts the given byte data into Hex string.
     *
     * @param data the byte array to convert
     * @return the Hex string representation of the given byte array
     */
    private fun convertToHex(data: ByteArray): String {
        val buf = StringBuffer()
        for (i in data.indices) {
            var halfbyte = data[i].toInt() ushr 4 and 0x0F
            var two_halfs = 0
            do {
                if (0 <= halfbyte && halfbyte <= 9) buf.append(('0'.code + halfbyte).toChar()) else buf.append(('a'.code + (halfbyte - 10)).toChar())
                halfbyte = data[i].toInt() and 0x0F
            } while (two_halfs++ < 1)
        }
        return buf.toString()
    }
}