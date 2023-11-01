/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.otr4j.io.messages

import javax.crypto.interfaces.DHPublicKey

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
class DHKeyMessage(protocolVersion: Int, var dhPublicKey: DHPublicKey?) : AbstractEncodedMessage(AbstractEncodedMessage.Companion.MESSAGE_DHKEY, protocolVersion) {
    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        // TODO: Needs work.
        result = prime * result + if (dhPublicKey == null) 0 else dhPublicKey.hashCode()
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (!super.equals(obj)) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as DHKeyMessage
        if (dhPublicKey == null) {
            if (other.dhPublicKey != null) return false
        } else if (dhPublicKey!!.y.compareTo(other.dhPublicKey!!.y) != 0) return false
        return true
    }
}