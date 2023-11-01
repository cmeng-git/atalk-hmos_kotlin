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
class PlainTextMessage(versions: List<Int?>?, var cleanText: String?) : QueryMessage(AbstractMessage.Companion.MESSAGE_PLAINTEXT, versions) {
    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        result = prime * result + if (cleanText == null) 0 else cleanText.hashCode()
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (!super.equals(obj)) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as PlainTextMessage
        if (cleanText == null) {
            if (other.cleanText != null) return false
        } else if (cleanText != other.cleanText) return false
        return true
    }
}