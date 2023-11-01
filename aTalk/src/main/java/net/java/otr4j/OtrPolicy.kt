/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.otr4j

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
interface OtrPolicy {
    var allowV1: Boolean
    var allowV2: Boolean
    var allowV3: Boolean
    var requireEncryption: Boolean
    var sendWhitespaceTag: Boolean
    var whitespaceStartAKE: Boolean
    var errorStartAKE: Boolean
    val policy: Int
    var enableAlways: Boolean
    var enableManual: Boolean

    companion object {
        const val ALLOW_V1 = 0x01
        const val ALLOW_V2 = 0x02
        const val ALLOW_V3 = 0x40 // ALLOW_V3 is set to 0x40 for compatibility with older versions
        const val REQUIRE_ENCRYPTION = 0x04
        const val SEND_WHITESPACE_TAG = 0x08
        const val WHITESPACE_START_AKE = 0x10
        const val ERROR_START_AKE = 0x20
        const val VERSION_MASK = ALLOW_V1 or ALLOW_V2 or ALLOW_V3

        // The four old version 1 policies correspond to the following combinations of flags (adding
        // an allowance for version 2 of the protocol):
        const val NEVER = 0x00
        const val OPPORTUNISTIC = (ALLOW_V1 or ALLOW_V2 or ALLOW_V3
                or SEND_WHITESPACE_TAG or WHITESPACE_START_AKE or ERROR_START_AKE)
        const val OTRL_POLICY_MANUAL = ALLOW_V1 or ALLOW_V2 or ALLOW_V3
        const val OTRL_POLICY_ALWAYS = (ALLOW_V1 or ALLOW_V2 or ALLOW_V3
                or REQUIRE_ENCRYPTION or WHITESPACE_START_AKE or ERROR_START_AKE)
        const val OTRL_POLICY_DEFAULT = OPPORTUNISTIC
    }
}