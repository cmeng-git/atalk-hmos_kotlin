/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.audiolevel

import org.atalk.util.ArrayIOUtils

/**
 * Implements the calculation of audio level as defined by RFC 6465 &quot;A Real-time Transport
 * Protocol (RTP) Header Extension for Mixer-to-Client Audio Level Indication&quot;.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
object AudioLevelCalculator {
    /**
     * The maximum audio level.
     */
    const val MAX_AUDIO_LEVEL: Byte = 0

    /**
     * The minimum audio level.
     */
    const val MIN_AUDIO_LEVEL: Byte = 127

    /**
     * Calculates the audio level of a signal with specific `samples`.
     *
     * @param samples
     * the samples of the signal to calculate the audio level of
     * @param offset
     * the offset in `samples` in which the samples start
     * @param length
     * the length in bytes of the signal in `samples`
     * starting at `offset`
     * @return the audio level of the specified signal
    `` */
    fun calculateAudioLevel(samples: ByteArray, offset: Int, length: Int): Byte {
        var offset = offset
        var rms = 0.0 // root mean square (RMS) amplitude
        while (offset < length) {
            var sample = ArrayIOUtils.readShort(samples, offset).toDouble()
            sample /= Short.MAX_VALUE
            rms += sample * sample
            offset += 2
        }
        val sampleCount = length / 2
        rms = if (sampleCount == 0) 0.0 else Math.sqrt(rms / sampleCount)
        var db: Double
        if (rms > 0) {
            db = 20 * Math.log10(rms)
            // XXX The audio level is expressed in -dBov.
            db = -db
            // Ensure that the calculated audio level is within the range
            // between MIN_AUDIO_LEVEL and MAX_AUDIO_LEVEL.
            if (db > MIN_AUDIO_LEVEL) db = MIN_AUDIO_LEVEL.toDouble() else if (db < MAX_AUDIO_LEVEL) db = MAX_AUDIO_LEVEL.toDouble()
        } else {
            db = MIN_AUDIO_LEVEL.toDouble()
        }
        return db.toInt().toByte()
    }
}