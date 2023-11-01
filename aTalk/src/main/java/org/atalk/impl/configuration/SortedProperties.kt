/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.configuration

import java.util.*

/**
 * This class is a sorted version of classical `java.util.Properties`. It is strongly
 * inspired by http://forums.sun.com/thread.jspa?threadID=141144.
 *
 * @author Sebastien Vincent
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class SortedProperties : Properties() {
    /**
     * Gets an `Enumeration` of the keys in this `Properties` object. Contrary to
     * the original `Properties` implementation, it forces the keys to be alphabetically
     * sorted.
     *
     * @return an `Enumeration` of the keys in this `Properties` object
     */
    @Synchronized
    override fun keys(): Enumeration<Any> {
        val keys = keys.toTypedArray()
        Arrays.sort(keys)
        return object : Enumeration<Any> {
            private var i = 0
            override fun hasMoreElements(): Boolean {
                return i < keys.size
            }

            override fun nextElement(): Any {
                return keys[i++]
            }
        }
    }

    /**
     * Does not allow putting empty `String` keys in this `Properties` object.
     *
     * @param key the key
     * @param value the value
     * @return the previous value of the specified `key` in this `Hashtable`, or
     * `null` if it did not have one
     */
    @Synchronized
    override fun put(key: Any, value: Any): Any? {
        /*
         * We discovered a special case related to the Properties ConfigurationService
         * implementation during testing in which the key was a String composed of null characters
         * only (which would be trimmed) consumed megabytes of heap. Do now allow such keys.
         */
        return if (key.toString().trim { it <= ' ' }.isEmpty()) null else super.put(key, value)
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}