/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.otr4j.io.messages

import java.util.*
import javax.crypto.interfaces.DHPublicKey

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
class DataMessage     // Ctor.
(protocolVersion: Int, var flags: Int, var senderKeyID: Int,
 var recipientKeyID: Int, var nextDH: DHPublicKey?, var ctr: ByteArray?,
 var encryptedMessage: ByteArray?, // Fields.
 var mac: ByteArray?, var oldMACKeys: ByteArray?) : AbstractEncodedMessage(AbstractEncodedMessage.Companion.MESSAGE_DATA, protocolVersion) {
    constructor(t: MysteriousT, mac: ByteArray?, oldMacKeys: ByteArray?) : this(t.protocolVersion, t.flags, t.senderKeyID, t.recipientKeyID, t.nextDH, t.ctr,
            t.encryptedMessage, mac, oldMacKeys) {
    }

    // Methods.
    val t: MysteriousT
        get() = MysteriousT(protocolVersion, senderInstanceTag,
                receiverInstanceTag, flags, senderKeyID, recipientKeyID, nextDH, ctr, encryptedMessage)

    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        result = prime * result + Arrays.hashCode(ctr)
        result = prime * result + Arrays.hashCode(encryptedMessage)
        result = prime * result + flags
        result = prime * result + Arrays.hashCode(mac)
        // TODO: Needs work.
        result = prime * result + if (nextDH == null) 0 else nextDH.hashCode()
        result = prime * result + Arrays.hashCode(oldMACKeys)
        result = prime * result + recipientKeyID
        result = prime * result + senderKeyID
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (!super.equals(obj)) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as DataMessage
        if (!Arrays.equals(ctr, other.ctr)) return false
        if (!Arrays.equals(encryptedMessage, other.encryptedMessage)) return false
        if (flags != other.flags) return false
        if (!Arrays.equals(mac, other.mac)) return false
        if (nextDH == null) {
            if (other.nextDH != null) return false
        } else if (nextDH != other.nextDH) return false
        if (!Arrays.equals(oldMACKeys, other.oldMACKeys)) return false
        if (recipientKeyID != other.recipientKeyID) return false
        return senderKeyID == other.senderKeyID
    }
}