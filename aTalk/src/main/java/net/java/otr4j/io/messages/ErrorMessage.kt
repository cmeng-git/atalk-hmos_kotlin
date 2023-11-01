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
class ErrorMessage(messageType: Int, var error: String?) : AbstractMessage(messageType) {
    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        result = prime * result + if (error == null) 0 else error.hashCode()
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (!super.equals(obj)) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as ErrorMessage
        if (error == null) {
            if (other.error != null) return false
        } else if (error != other.error) return false
        return true
    }
}