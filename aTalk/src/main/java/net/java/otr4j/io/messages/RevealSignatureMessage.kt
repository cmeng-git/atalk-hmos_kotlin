/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.otr4j.io.messages

import java.util.*

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
class RevealSignatureMessage(protocolVersion: Int, xEncrypted: ByteArray?, xEncryptedMAC: ByteArray?,
                             var revealedKey: ByteArray?) : SignatureMessage(AbstractEncodedMessage.Companion.MESSAGE_REVEALSIG, protocolVersion, xEncrypted, xEncryptedMAC) {
    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        result = prime * result + Arrays.hashCode(revealedKey)
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (!super.equals(obj)) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as RevealSignatureMessage
        return if (!Arrays.equals(revealedKey, other.revealedKey)) false else true
    }
}