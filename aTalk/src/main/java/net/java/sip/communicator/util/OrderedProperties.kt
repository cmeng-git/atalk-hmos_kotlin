/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util

import java.util.*

/**
 * Implementation of Properties that keep order of couples [key, value] added.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class OrderedProperties : Properties() {
    /**
     * A linked hashmap to keep entry in order.
     */
    private val linkedMap = LinkedHashMap<Any, Any>()

    /**
     * Get the object pointed by key.
     *
     * @param key key
     * @return value pointed by key or null if not set
     */
    override fun get(key: Any): Any? {
        return linkedMap[key]
    }

    /**
     * Put an couple key, value
     *
     * @param key key
     * @param value value
     * @return previous value pointed by key if any, null otherwise
     */
    override fun put(key: Any, value: Any): Any? {
        return linkedMap.put(key, value)
    }

    /**
     * Remove a key entry
     *
     * @param key key
     * @return previous value pointed by key if any, null otherwise
     */
    override fun remove(key: Any): Any? {
        return linkedMap.remove(key)
    }

    /**
     * Clear the entries.
     */
    override fun clear() {
        linkedMap.clear()
    }

    /**
     * Get the keys enumeration.
     *
     * @return keys enumeration
     */
    override fun keys(): Enumeration<Any> {
        return Collections.enumeration(linkedMap.keys)
    }

    /**
     * Get the elements of the `LinkedHashMap`.
     *
     * @return enumeration
     */
    override fun elements(): Enumeration<Any> {
        return Collections.enumeration(linkedMap.values)
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}