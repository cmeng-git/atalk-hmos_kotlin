/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.otr4j.io.messages

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
abstract class AbstractEncodedMessage : AbstractMessage {
    // Fields.
    var protocolVersion: Int
    var senderInstanceTag = 0
    var receiverInstanceTag = 0

    // Ctor.
    constructor(messageType: Int, protocolVersion: Int) : super(messageType) {
        this.protocolVersion = protocolVersion
    }

    @JvmOverloads
    constructor(messageType: Int, protocolVersion: Int, senderInstanceTag: Int,
                recipientInstanceTag: Int = 0) : super(messageType) {
        this.protocolVersion = protocolVersion
        this.senderInstanceTag = senderInstanceTag
        receiverInstanceTag = recipientInstanceTag
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        result = prime * result + protocolVersion
        result = prime * result + senderInstanceTag
        result = prime * result + receiverInstanceTag
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (!super.equals(obj)) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as AbstractEncodedMessage
        if (protocolVersion != other.protocolVersion) return false
        if (senderInstanceTag != other.senderInstanceTag) return false
        return if (receiverInstanceTag != other.receiverInstanceTag) false else true
    }

    companion object {
        // Encoded Message Types
        const val MESSAGE_DH_COMMIT = 0x02
        const val MESSAGE_DATA = 0x03
        const val MESSAGE_DHKEY = 0x0a
        const val MESSAGE_REVEALSIG = 0x11
        const val MESSAGE_SIGNATURE = 0x12
    }
}