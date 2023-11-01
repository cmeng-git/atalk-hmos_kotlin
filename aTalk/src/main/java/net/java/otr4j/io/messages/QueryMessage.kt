/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.otr4j.io.messages

import java.util.*

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
open class QueryMessage protected constructor(messageType: Int, versions: List<Int?>?) : AbstractMessage(messageType) {
    var versions: List<Int?>?

    init {
        this.versions = if (versions == null) versions else Collections.unmodifiableList(ArrayList(versions))
    }

    constructor(versions: List<Int?>?) : this(MESSAGE_QUERY, versions) {}

    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        result = prime * result + if (versions == null) 0 else versions.hashCode()
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (!super.equals(obj)) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as QueryMessage
        if (versions == null) {
            if (other.versions != null) return false
        } else if (versions != other.versions) return false
        return true
    }
}