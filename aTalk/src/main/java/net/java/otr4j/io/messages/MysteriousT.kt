package net.java.otr4j.io.messages

import java.util.*
import javax.crypto.interfaces.DHPublicKey

class MysteriousT(var protocolVersion: Int, var senderInstanceTag: Int, var receiverInstanceTag: Int,
                  flags: Int, senderKeyID: Int, recipientKeyID: Int, nextDH: DHPublicKey?,
                  ctr: ByteArray?, encryptedMessage: ByteArray?) {
    var messageType: Int
    var flags: Int
    var senderKeyID: Int
    var recipientKeyID: Int
    var nextDH: DHPublicKey?
    var ctr: ByteArray?
    var encryptedMessage: ByteArray?

    init {
        messageType = AbstractEncodedMessage.Companion.MESSAGE_DATA
        this.flags = flags
        this.senderKeyID = senderKeyID
        this.recipientKeyID = recipientKeyID
        this.nextDH = nextDH
        this.ctr = ctr
        this.encryptedMessage = encryptedMessage
    }

    override fun hashCode(): Int {
        // TODO: Needs work.
        val prime = 31
        var result = 1
        result = prime * result + Arrays.hashCode(ctr)
        result = prime * result + Arrays.hashCode(encryptedMessage)
        result = prime * result + flags
        result = prime * result + messageType
        result = prime * result + if (nextDH == null) 0 else nextDH.hashCode()
        result = prime * result + protocolVersion
        result = prime * result + recipientKeyID
        result = prime * result + senderKeyID
        result = prime * result + senderInstanceTag
        result = prime * result + receiverInstanceTag
        return result
    }

    override fun equals(obj: Any?): Boolean {
        // TODO: Needs work.
        if (this === obj) return true
        if (obj == null) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as MysteriousT
        if (!Arrays.equals(ctr, other.ctr)) return false
        if (!Arrays.equals(encryptedMessage, other.encryptedMessage)) return false
        if (flags != other.flags) return false
        if (messageType != other.messageType) return false
        if (nextDH == null) {
            if (other.nextDH != null) return false
        } else if (nextDH != other.nextDH) return false
        if (protocolVersion != other.protocolVersion) return false
        if (recipientKeyID != other.recipientKeyID) return false
        if (senderKeyID != other.senderKeyID) return false
        if (senderInstanceTag != other.senderInstanceTag) return false
        return if (receiverInstanceTag != other.receiverInstanceTag) false else true
    }
}