/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.gsm

import org.rubycoder.gsm.GSMEncoder

/**
 * GSMEncoderUtil class
 *
 * @author Martin Harvan
 * @author Damian Minkov
 */
object GSMEncoderUtil {
    private val encoder = GSMEncoder()

    /**
     * number of bytes in GSM frame
     */
    private const val GSM_BYTES = 33

    /**
     * number of PCM bytes needed to encode
     */
    private const val PCM_BYTES = 320

    /**
     * number of PCM ints needed to encode
     */
    private const val PCM_INTS = 160

    /**
     * Encode data to GSM.
     *
     * @param bigEndian
     * if the data is in big endian format
     * @param data
     * data to encode
     * @param offset
     * offset
     * @param length
     * length of data
     * @param decoded
     * array of encoded data.
     */
    fun gsmEncode(bigEndian: Boolean, data: ByteArray, offset: Int, length: Int, decoded: ByteArray?) {
        for (i in offset until length / PCM_BYTES) {
            val input = IntArray(PCM_INTS)
            val output = ByteArray(GSM_BYTES)
            for (j in 0 until PCM_INTS) {
                var index = j shl 1
                input[j] = data[i * PCM_BYTES + index++].toInt()
                input[j] = input[j] shl 8
                input[j] = input[j] or (data[i * PCM_BYTES + index++].toInt() and 0xFF)
            }
            encoder.encode(output, input)
            System.arraycopy(output, 0, decoded!!, i * GSM_BYTES, GSM_BYTES)
        }
    }
}