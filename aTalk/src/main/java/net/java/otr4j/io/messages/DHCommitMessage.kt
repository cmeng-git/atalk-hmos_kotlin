/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.otr4j.io.messages

import java.util.*

/**
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
class DHCommitMessage(protocolVersion: Int, var dhPublicKeyHash: ByteArray?,
                      var dhPublicKeyEncrypted: ByteArray?) : AbstractEncodedMessage(AbstractEncodedMessage.Companion.MESSAGE_DH_COMMIT, protocolVersion) {
    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        result = prime * result + Arrays.hashCode(dhPublicKeyEncrypted)
        result = prime * result + Arrays.hashCode(dhPublicKeyHash)
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (!super.equals(obj)) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as DHCommitMessage
        if (!Arrays.equals(dhPublicKeyEncrypted, other.dhPublicKeyEncrypted)) return false
        return if (!Arrays.equals(dhPublicKeyHash, other.dhPublicKeyHash)) false else true
    }
}