/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atalk.service.neomedia

/**
 * Class for representing all the different DTMF tones.
 *
 * @author JM HEITZ
 * @author Eng Chong Meng
 */
class DTMFTone
/**
 * Creates a DTMF instance with the specified tone value. The method is private since one would
 * only have to use predefined static instances.
 *
 * @param value
 * one of te DTMF_XXX fields, indicating the value of the tone.
 */
private constructor(
        /**
         * The value of the DTMF tone
         */
        val value: String) {
    /**
     * Returns the string representation of this DTMF tone.
     *
     * @return the `String` representation of this DTMF tone.
     */

    /**
     * Indicates whether some other object is "equal to" this tone.
     *
     *
     *
     * @param target
     * the reference object with which to compare.
     *
     * @return `true` if target represents the same tone as this object.
     */
    override fun equals(target: Any?): Boolean {
        if (target !is DTMFTone) {
            return false
        }
        return target.value == value
    }

    /**
     * Returns a hash code value for the object. This method is supported for the benefit of
     * hashtables such as those provided by `java.util.Hashtable`. The method would
     * actually return the hashcode of the string representation of this DTMF tone.
     *
     *
     *
     * @return a hash code value for this object (same as calling getValue().hashCode()).
     */
    override fun hashCode(): Int {
        return value.hashCode()
    }

    companion object {
        /**
         * The "A" DTMF Tone
         */
        val DTMF_A = DTMFTone("A")

        /**
         * The "B" DTMF Tone
         */
        val DTMF_B = DTMFTone("B")

        /**
         * The "C" DTMF Tone
         */
        val DTMF_C = DTMFTone("C")

        /**
         * The "D" DTMF Tone
         */
        val DTMF_D = DTMFTone("D")

        /**
         * The "0" DTMF Tone
         */
        val DTMF_0 = DTMFTone("0")

        /**
         * The "1" DTMF Tone
         */
        val DTMF_1 = DTMFTone("1")

        /**
         * The "2" DTMF Tone
         */
        val DTMF_2 = DTMFTone("2")

        /**
         * The "3" DTMF Tone
         */
        val DTMF_3 = DTMFTone("3")

        /**
         * The "4" DTMF Tone
         */
        val DTMF_4 = DTMFTone("4")

        /**
         * The "5" DTMF Tone
         */
        val DTMF_5 = DTMFTone("5")

        /**
         * The "6" DTMF Tone
         */
        val DTMF_6 = DTMFTone("6")

        /**
         * The "7" DTMF Tone
         */
        val DTMF_7 = DTMFTone("7")

        /**
         * The "8" DTMF Tone
         */
        val DTMF_8 = DTMFTone("8")

        /**
         * The "9" DTMF Tone
         */
        val DTMF_9 = DTMFTone("9")

        /**
         * The "*" DTMF Tone
         */
        val DTMF_STAR = DTMFTone("*")

        /**
         * The "#" DTMF Tone
         */
        val DTMF_SHARP = DTMFTone("#")

        /**
         * Parses input `value` and return the corresponding tone. If unknown will return null;
         *
         * @param value
         * the input value.
         * @return the corresponding tone, `null` for unknown.
         */
        fun getDTMFTone(value: String?): DTMFTone? {
            return if (value == null) null else if (value == DTMF_0.value) DTMF_0 else if (value == DTMF_1.value) DTMF_1 else if (value == DTMF_2.value) DTMF_2 else if (value == DTMF_3.value) DTMF_3 else if (value == DTMF_4.value) DTMF_4 else if (value == DTMF_5.value) DTMF_5 else if (value == DTMF_6.value) DTMF_6 else if (value == DTMF_7.value) DTMF_7 else if (value == DTMF_8.value) DTMF_8 else if (value == DTMF_9.value) DTMF_9 else if (value == DTMF_A.value) DTMF_A else if (value == DTMF_B.value) DTMF_B else if (value == DTMF_C.value) DTMF_C else if (value == DTMF_D.value) DTMF_D else if (value == DTMF_SHARP.value) DTMF_SHARP else if (value == DTMF_STAR.value) DTMF_STAR else null
        }
    }
}