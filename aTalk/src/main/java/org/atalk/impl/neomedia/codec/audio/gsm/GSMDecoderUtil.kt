/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.gsm

import org.rubycoder.gsm.GSMDecoder
import org.rubycoder.gsm.InvalidGSMFrameException
import timber.log.Timber

/**
 * GSMDecoderUtil class
 *
 * @author Martin Harvan
 * @author Damian Minkov
 */
object GSMDecoderUtil {
    private val decoder = GSMDecoder()
    private const val GSM_BYTES = 33
    private const val PCM_INTS = 160
    private const val PCM_BYTES = 320

    /**
     * Decode GSM data.
     *
     * @param bigEndian if the data are in big endian format
     * @param data the GSM data
     * @param offset offset
     * @param length length of the data
     * @param decoded decoded data array
     */
    fun gsmDecode(bigEndian: Boolean, data: ByteArray?, offset: Int, length: Int, decoded: ByteArray) {
        for (i in 0 until length / GSM_BYTES) {
            val output = IntArray(PCM_INTS)
            val input = ByteArray(GSM_BYTES)
            System.arraycopy(data!!, i * GSM_BYTES, input, 0, GSM_BYTES)

            try {
                decoder.decode(input, output)
            } catch (e: InvalidGSMFrameException) {
                Timber.d("Invalid GSM Frame Exception: %s", e.message)
            }

            for (j in 0 until PCM_INTS) {
                var index = j shl 1
                if (bigEndian) {
                    decoded[index + i * PCM_BYTES] = (output[j] and 0xff00 shr 8).toByte()
                    decoded[++index + i * PCM_BYTES] = (output[j] and 0x00ff).toByte()
                } else {
                    decoded[index + i * PCM_BYTES] = (output[j] and 0x00ff).toByte()
                    decoded[++index + i * PCM_BYTES] = (output[j] and 0xff00 shr 8).toByte()
                }
            }
        }
    }
}