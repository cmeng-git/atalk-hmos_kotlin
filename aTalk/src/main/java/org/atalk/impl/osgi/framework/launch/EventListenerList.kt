/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.osgi.framework.launch

import org.osgi.framework.Bundle
import java.util.*

/**
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class EventListenerList {
    private val elements: MutableList<Element<*>?> = LinkedList()
    @Synchronized
    fun <T : EventListener?> add(
            bundle: Bundle?,
            clazz: Class<T>?,
            listener: T?): Boolean {
        if (bundle == null) throw NullPointerException("bundle")
        if (clazz == null) throw NullPointerException("clazz")
        if (listener == null) throw NullPointerException("listener")
        val index = indexOf(bundle, clazz, listener)
        return if (index == -1) elements.add(Element(bundle, clazz, listener)) else false
    }

    @Synchronized
    fun <T : EventListener?> getListeners(
            clazz: Class<T>): Array<T> {
        val eventListeners = arrayOfNulls<EventListener>(elements.size)
        var count = 0
        for (element in elements) if (element!!.clazz == clazz) eventListeners[count++] = element.listener
        val listeners = java.lang.reflect.Array.newInstance(clazz, count) as Array<T>
        System.arraycopy(eventListeners, 0, listeners, 0, count)
        return listeners
    }

    @Synchronized
    private fun <T : EventListener?> indexOf(
            bundle: Bundle,
            clazz: Class<T>,
            listener: T): Int {
        var index = 0
        val count = elements.size
        while (index < count) {
            val element = elements[index]
            if (element!!.bundle == bundle && element.clazz == clazz && element.listener === listener) return index
            index++
        }
        return -1
    }

    @Synchronized
    fun <T : EventListener?> remove(
            bundle: Bundle,
            clazz: Class<T>,
            listener: T): Boolean {
        val index = indexOf(bundle, clazz, listener)
        return if (index == -1) false else {
            elements.removeAt(index)
            true
        }
    }

    @Synchronized
    fun removeAll(bundle: Bundle): Boolean {
        var changed = false
        var index = 0
        val count = elements.size
        while (index < count) {
            if (elements[index]!!.bundle == bundle && elements.removeAt(index) != null) changed = true else index++
        }
        return changed
    }

    private class Element<T : EventListener?>(val bundle: Bundle, val clazz: Class<T>, val listener: T)
}