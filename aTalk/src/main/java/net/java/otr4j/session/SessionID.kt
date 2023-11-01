/*
 * otr4j, the open source java otr library.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.otr4j.session

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
class SessionID(val accountID: String?, val userID: String?, val protocolName: String?) {

    override fun toString(): String {
        return accountID + '_' + protocolName + '_' + userID
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + (accountID?.hashCode() ?: 0)
        result = prime * result + (protocolName?.hashCode() ?: 0)
        result = prime * result + (userID?.hashCode() ?: 0)
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as SessionID
        if (accountID == null) {
            if (other.accountID != null) return false
        } else if (accountID != other.accountID) return false
        if (protocolName == null) {
            if (other.protocolName != null) return false
        } else if (protocolName != other.protocolName) return false
        return if (userID == null) {
            other.userID == null
        } else userID == other.userID
    }

    companion object {
        val EMPTY = SessionID(null, null, null)
    }
}