/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.util.event

import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

/**
 * Represents a source of `PropertyChangeEvent`s which notifies
 * `PropertyChangeListener`s about changes in the values of properties.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
open class PropertyChangeNotifier
/**
 * Initializes a new `PropertyChangeNotifier` instance.
 */
{
    /**
     * The list of `PropertyChangeListener`s interested in and notified about changes in
     * the values of the properties of this `PropertyChangeNotifier`.
     */
    private val listeners = ArrayList<PropertyChangeListener>()

    /**
     * Adds a specific `PropertyChangeListener` to the list of listeners
     * interested in and notified about changes in the values of the properties
     * of this `PropertyChangeNotifier`.
     *
     * @param listener a `PropertyChangeListener` to be notified about
     * changes in the values of the properties of this
     * `PropertyChangeNotifier`. If the specified listener is already in the list of
     * interested listeners (i.e. it has been previously added), it is not added again.
     */
    fun addPropertyChangeListener(listener: PropertyChangeListener) {
        if (listener == null) {
            Timber.d("The specified argument listener is nul and that does not make sense.")
        } else {
            synchronized(listeners) { if (!listeners.contains(listener)) listeners.add(listener) }
        }
    }

    /**
     * Fires a new `PropertyChangeEvent` to the
     * `PropertyChangeListener`s registered with this
     * `PropertyChangeNotifier` in order to notify about a change in the
     * value of a specific property which had its old value modified to a
     * specific new value. `PropertyChangeNotifier` does not check
     * whether the specified `oldValue` and `newValue` are indeed different.
     *
     * @param property the name of the property of this
     * `PropertyChangeNotifier` which had its value changed
     * @param oldValue the value of the property with the specified name before the change
     * @param newValue the value of the property with the specified name after the change
     */
    protected open fun firePropertyChange(property: String?, oldValue: Any?, newValue: Any?) {
        var ls: Array<PropertyChangeListener>
        synchronized(listeners) { ls = listeners.toTypedArray() }
        if (ls.isNotEmpty()) {
            val ev = PropertyChangeEvent(
                    getPropertyChangeSource(property, oldValue, newValue),
                    property, oldValue, newValue)
            for (l in ls) {
                try {
                    l.propertyChange(ev)
                } catch (t: Throwable) {
                    if (t is InterruptedException) {
                        Thread.currentThread().interrupt()
                    } else if (t is ThreadDeath) {
                        throw t
                    } else {
                        Timber.w(t, "A PropertyChangeListener threw an exception while handling a PropertyChangeEvent.")
                    }
                }
            }
        }
    }

    /**
     * Gets the `Object` to be reported as the source of a new
     * `PropertyChangeEvent` which is to notify the `PropertyChangeListener`s
     * registered with this `PropertyChangeNotifier` about the change in the value of a
     * property with a  specific name from a specific old value to a specific new value.
     *
     * @param property the name of the property which had its value changed from
     * the specified old value to the specified new value
     * @param oldValue the value of the property with the specified name before the change
     * @param newValue the value of the property with the specified name after the change
     * @return the `Object` to be reported as the source of the new
     * `PropertyChangeEvent` which is to notify the
     * `PropertyChangeListener`s registered with this
     * `PropertyChangeNotifier` about the change in the value of the
     * property with the specified name from the specified old value to the specified new value
     */
    protected open fun getPropertyChangeSource(property: String?, oldValue: Any?, newValue: Any?): Any {
        return this
    }

    /**
     * Removes a specific `PropertyChangeListener` from the list of
     * listeners interested in and notified about changes in the values of the
     * properties of this `PropertyChangeNotifer`.
     *
     * @param listener a `PropertyChangeListener` to no longer be
     * notified about changes in the values of the properties of this
     * `PropertyChangeNotifier`
     */
    fun removePropertyChangeListener(listener: PropertyChangeListener) {
        synchronized(listeners) { listeners.remove(listener) }
    }
}