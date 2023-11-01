/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

/**
 * Represents all DTMF tones for RTP method (RFC4733).
 *
 * @author JM HEITZ
 * @author Romain Philibert
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class DTMFRtpTone
/**
 * Creates a DTMF instance with the specified tone value. The method is private since one would
 * only have to use predefined static instances.
 *
 * @param value
 * one of the DTMF_XXX fields, indicating the value of the tone.
 * @param code
 * the of the DTMF tone that we'll actually be sending over the wire, as specified by RFC
 * 4733.
 */ private constructor(
        /**
         * The value of the DTMF tone
         */
        val value: String,
        /**
         * The code of the tone, as specified by RFC 4733, and the we'll actually be sending over the
         * wire.
         */
        val code: Byte) {
    /**
     * Returns the string representation of this DTMF tone.
     *
     * @return the `String` representation of this DTMF tone.
     */
    /**
     * Returns the RFC 4733 code of this DTMF tone.
     *
     * @return the RFC 4733 code of this DTMF tone.
     */

    /**
     * Indicates whether some other object is "equal to" this tone.
     *
     * @param target
     * the reference object with which to compare.
     *
     * @return `true` if target represents the same tone as this object.
     */
    override fun equals(target: Any?): Boolean {
        if (target !is DTMFRtpTone) {
            return false
        }
        return target.value == value
    }

    /**
     * Returns a hash code value for the object. This method is supported for the benefit of
     * hashtables such as those provided by `java.util.Hashtable`. The method would
     * actually return the hashcode of the string representation of this DTMF tone.
     *
     * @return a hash code value for this object (same as calling getValue().hashCode()).
     */
    override fun hashCode(): Int {
        return value.hashCode()
    }

    companion object {
        /**
         * The "0" DTMF Tone
         */
        val DTMF_0 = DTMFRtpTone("0", 0.toByte())

        /**
         * The "1" DTMF Tone
         */
        val DTMF_1 = DTMFRtpTone("1", 1.toByte())

        /**
         * The "2" DTMF Tone
         */
        val DTMF_2 = DTMFRtpTone("2", 2.toByte())

        /**
         * The "3" DTMF Tone
         */
        val DTMF_3 = DTMFRtpTone("3", 3.toByte())

        /**
         * The "4" DTMF Tone
         */
        val DTMF_4 = DTMFRtpTone("4", 4.toByte())

        /**
         * The "5" DTMF Tone
         */
        val DTMF_5 = DTMFRtpTone("5", 5.toByte())

        /**
         * The "6" DTMF Tone
         */
        val DTMF_6 = DTMFRtpTone("6", 6.toByte())

        /**
         * The "7" DTMF Tone
         */
        val DTMF_7 = DTMFRtpTone("7", 7.toByte())

        /**
         * The "8" DTMF Tone
         */
        val DTMF_8 = DTMFRtpTone("8", 8.toByte())

        /**
         * The "9" DTMF Tone
         */
        val DTMF_9 = DTMFRtpTone("9", 9.toByte())

        /**
         * The "*" DTMF Tone
         */
        val DTMF_STAR = DTMFRtpTone("*", 10.toByte())

        /**
         * The "#" DTMF Tone
         */
        val DTMF_SHARP = DTMFRtpTone("#", 11.toByte())

        /**
         * The "A" DTMF Tone
         */
        val DTMF_A = DTMFRtpTone("A", 12.toByte())

        /**
         * The "B" DTMF Tone
         */
        val DTMF_B = DTMFRtpTone("B", 13.toByte())

        /**
         * The "C" DTMF Tone
         */
        val DTMF_C = DTMFRtpTone("C", 14.toByte())

        /**
         * The "D" DTMF Tone
         */
        val DTMF_D = DTMFRtpTone("D", 15.toByte())

        /**
         * Maps between protocol and media DTMF objects.
         *
         * @param tone
         * The DTMFTone to be mapped to an DTMFRtpTone.
         * @return The DTMFRtpTone corresponding to the tone specified.
         */
        fun mapTone(tone: DTMFTone): DTMFRtpTone? {
            if (tone == DTMFTone.DTMF_0) return DTMF_0 else if (tone == DTMFTone.DTMF_1) return DTMF_1 else if (tone == DTMFTone.DTMF_2) return DTMF_2 else if (tone == DTMFTone.DTMF_3) return DTMF_3 else if (tone == DTMFTone.DTMF_4) return DTMF_4 else if (tone == DTMFTone.DTMF_5) return DTMF_5 else if (tone == DTMFTone.DTMF_6) return DTMF_6 else if (tone == DTMFTone.DTMF_7) return DTMF_7 else if (tone == DTMFTone.DTMF_8) return DTMF_8 else if (tone == DTMFTone.DTMF_9) return DTMF_9 else if (tone == DTMFTone.DTMF_A) return DTMF_A else if (tone == DTMFTone.DTMF_B) return DTMF_B else if (tone == DTMFTone.DTMF_C) return DTMF_C else if (tone == DTMFTone.DTMF_D) return DTMF_D else if (tone == DTMFTone.DTMF_SHARP) return DTMF_SHARP else if (tone == DTMFTone.DTMF_STAR) return DTMF_STAR
            return null
        }
    }
}