package net.java.otr4j

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
class OtrPolicyImpl : OtrPolicy {
    override var policy = 0
        private set

    constructor() {
        policy = OtrPolicy.Companion.NEVER
    }

    constructor(policy: Int) {
        this.policy = policy
    }

    override var allowV1: Boolean
        get() = policy and OtrPolicy.Companion.ALLOW_V1 != 0
        set(value) {
            policy = if (value) policy or OtrPolicy.Companion.ALLOW_V1 else policy and OtrPolicy.Companion.ALLOW_V1.inv()
        }
    override var allowV2: Boolean
        get() = policy and OtrPolicy.Companion.ALLOW_V2 != 0
        set(value) {
            policy = if (value) policy or OtrPolicy.Companion.ALLOW_V2 else policy and OtrPolicy.Companion.ALLOW_V2.inv()
        }
    override var allowV3: Boolean
        get() = policy and OtrPolicy.Companion.ALLOW_V3 != 0
        set(value) {
            policy = if (value) policy or OtrPolicy.Companion.ALLOW_V3 else policy and OtrPolicy.Companion.ALLOW_V3.inv()
        }
    override var errorStartAKE: Boolean
        get() = policy and OtrPolicy.Companion.ERROR_START_AKE != 0
        set(value) {
            policy = if (value) policy or OtrPolicy.Companion.ERROR_START_AKE else policy and OtrPolicy.Companion.ERROR_START_AKE.inv()
        }
    override var requireEncryption: Boolean
        get() = enableManual && policy and OtrPolicy.Companion.REQUIRE_ENCRYPTION != 0
        set(value) {
            policy = if (value) policy or OtrPolicy.Companion.REQUIRE_ENCRYPTION else policy and OtrPolicy.Companion.REQUIRE_ENCRYPTION.inv()
        }
    override var sendWhitespaceTag: Boolean
        get() = policy and OtrPolicy.Companion.SEND_WHITESPACE_TAG != 0
        set(value) {
            policy = if (value) policy or OtrPolicy.Companion.SEND_WHITESPACE_TAG else policy and OtrPolicy.Companion.SEND_WHITESPACE_TAG.inv()
        }
    override var whitespaceStartAKE: Boolean
        get() = policy and OtrPolicy.Companion.WHITESPACE_START_AKE != 0
        set(value) {
            policy = if (value) policy or OtrPolicy.Companion.WHITESPACE_START_AKE else policy and OtrPolicy.Companion.WHITESPACE_START_AKE.inv()
        }
    override var enableAlways: Boolean
        get() = (enableManual && errorStartAKE
                && sendWhitespaceTag && whitespaceStartAKE)
        set(value) {
            if (value) enableManual = true
            errorStartAKE = value
            sendWhitespaceTag = value
            whitespaceStartAKE = value
        }
    override var enableManual: Boolean
        get() = allowV1 && allowV2 && allowV3
        set(value) {
            allowV1 = value
            allowV2 = value
            allowV3 = value
        }

    override fun equals(obj: Any?): Boolean {
        if (obj === this) return true
        if (obj == null || obj.javaClass != this.javaClass) return false
        val other = obj as OtrPolicy
        return other.policy == policy
    }

    override fun hashCode(): Int {
        return policy
    }
}