/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.otr4j.io.messages

import java.security.PublicKey
import javax.crypto.interfaces.DHPublicKey

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
class SignatureM(var localPubKey: DHPublicKey?, var remotePubKey: DHPublicKey?,
                 var localLongTermPubKey: PublicKey?, var keyPairID: Int) {
    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + keyPairID
        // TODO: Needs work.
        result = prime * result + if (localLongTermPubKey == null) 0 else localLongTermPubKey.hashCode()
        result = prime * result + if (localPubKey == null) 0 else localPubKey.hashCode()
        result = prime * result + if (remotePubKey == null) 0 else remotePubKey.hashCode()
        return result
    }

    override fun equals(obj: Any?): Boolean {
        // TODO: Needs work.
        if (this === obj) return true
        if (obj == null) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as SignatureM
        if (keyPairID != other.keyPairID) return false
        if (localLongTermPubKey == null) {
            if (other.localLongTermPubKey != null) return false
        } else if (localLongTermPubKey != other.localLongTermPubKey) return false
        if (localPubKey == null) {
            if (other.localPubKey != null) return false
        } else if (localPubKey != other.localPubKey) return false
        if (remotePubKey == null) {
            if (other.remotePubKey != null) return false
        } else if (remotePubKey != other.remotePubKey) return false
        return true
    }
}