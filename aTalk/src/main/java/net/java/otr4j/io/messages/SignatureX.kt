/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.otr4j.io.messages

import java.security.PublicKey
import java.util.*

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
class SignatureX(var longTermPublicKey: PublicKey?, var dhKeyID: Int, var signature: ByteArray?) {
    override fun hashCode(): Int {
        // TODO: Needs work.
        val prime = 31
        var result = 1
        result = prime * result + dhKeyID
        result = prime * result + if (longTermPublicKey == null) 0 else longTermPublicKey.hashCode()
        result = prime * result + Arrays.hashCode(signature)
        return result
    }

    override fun equals(obj: Any?): Boolean {
        // TODO: Needs work.
        if (this === obj) return true
        if (obj == null) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as SignatureX
        if (dhKeyID != other.dhKeyID) return false
        if (longTermPublicKey == null) {
            if (other.longTermPublicKey != null) return false
        } else if (longTermPublicKey != other.longTermPublicKey) return false
        return if (!Arrays.equals(signature, other.signature)) false else true
    }
}