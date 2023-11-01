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
abstract class AbstractMessage(var messageType: Int) {
    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + messageType
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as AbstractMessage
        return if (messageType != other.messageType) false else true
    }

    companion object {
        // Unencoded
        const val MESSAGE_ERROR = 0xff
        const val MESSAGE_QUERY = 0x100
        const val MESSAGE_PLAINTEXT = 0x102
    }
}