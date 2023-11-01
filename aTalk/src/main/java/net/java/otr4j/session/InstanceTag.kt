package net.java.otr4j.session

import java.util.*

/**
 * @author Marin Dzhigarov
 * @author Eng Chong Meng
 */
class InstanceTag {
    /**
     * Value of the instance tag instance.
     */
    val value: Int

    constructor() {
        val `val` = (RANDOM.nextDouble() * RANGE).toLong() + SMALLEST_VALUE
        // Because 0xffffffff is the maximum value for both the tag and the 32 bit integer range,
        // we are able to cast to int without loss. The (decimal) interpretation changes, though,
        // because Java's int interprets the last bit as the sign bit. This does
        // not matter, however, since we do not need to do value comparisons / ordering. We only
        // care about equal/not equal.
        value = `val`.toInt()
    }

    internal constructor(value: Int) {
        require(isValidInstanceTag(value)) { "Invalid tag value." }
        this.value = value
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InstanceTag) return false
        return value == other.value
    }

    override fun hashCode(): Int {
        return value
    }

    companion object {
        private val RANDOM = Random()
        const val ZERO_VALUE = 0

        /**
         * The smallest possible valid tag value.
         */
        const val SMALLEST_VALUE = 0x00000100

        /**
         * The highest possible tag value.
         * Note that this is -1 in the decimal representation.
         */
        const val HIGHEST_VALUE = -0x1
        val ZERO_TAG = InstanceTag(ZERO_VALUE)
        val SMALLEST_TAG = InstanceTag(SMALLEST_VALUE)
        val HIGHEST_TAG = InstanceTag(HIGHEST_VALUE)

        /**
         * Range for valid instance tag values.
         * Corrected for existence of smallest value boundary.
         */
        private const val RANGE = 0xfffffeffL
        fun isValidInstanceTag(tagValue: Int): Boolean {
            // Note that the decimal representation of Java's int is always signed, that means that
            // any value over 0x7fffffff will be interpreted as a negative value. So, instead we
            // verify that the tag value is not in the "forbidden range". Other than the forbidden
            // range, every possible value of the 32 bits of memory is  acceptable.
            return !(0 < tagValue && tagValue < SMALLEST_VALUE)
        }
    }
}