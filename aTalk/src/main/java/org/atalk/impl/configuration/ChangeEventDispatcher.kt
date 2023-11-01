/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.configuration

import org.atalk.service.configuration.ConfigVetoableChangeListener
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.*

/**
 * This is a utility class that can be used by objects that support constrained properties. You
 * can use an instance of this class as a member field and delegate various work to it.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class ChangeEventDispatcher(sourceObject: Any?) {
    /**
     * All property change listeners registered so far.
     */
    private var propertyChangeListeners: MutableList<PropertyChangeListener?>? = null

    /**
     * All listeners registered for vetoable change events.
     */
    private var vetoableChangeListeners: MutableList<ConfigVetoableChangeListener?>? = null

    /**
     * Hashtable for managing property change listeners registered for specific properties. Maps
     * property names to PropertyChangeSupport objects.
     */
    private var propertyChangeChildren: MutableMap<String?, ChangeEventDispatcher>? = null

    /**
     * Hashtable for managing vetoable change listeners registered for specific properties. Maps
     * property names to PropertyChangeSupport objects.
     */
    private var vetoableChangeChildren: MutableMap<String?, ChangeEventDispatcher>? = null

    /**
     * The object to be provided as the "source" for any generated events.
     */
    private val source: Any

    /**
     * Constructs a `VetoableChangeSupport` object.
     *
     * @param sourceObject The object to be given as the source for any events.
     */
    init {
        if (sourceObject == null) throw NullPointerException("sourceObject")
        source = sourceObject
    }

    /**
     * Add a PropertyChangeListener to the listener list. The listener is registered for all properties.
     *
     * @param listener The PropertyChangeChangeListener to be added
     */
    @Synchronized
    fun addPropertyChangeListener(listener: PropertyChangeListener?) {
        if (propertyChangeListeners == null) propertyChangeListeners = Vector()
        propertyChangeListeners!!.add(listener)
    }

    /**
     * Add a PropertyChangeListener for a specific property. The listener will be invoked only
     * when a call on firePropertyChange names that specific property.
     *
     * @param propertyName The name of the property to listen on.
     * @param listener The ConfigurationChangeListener to be added
     */
    @Synchronized
    fun addPropertyChangeListener(propertyName: String?, listener: PropertyChangeListener?) {
        if (propertyChangeChildren == null) {
            propertyChangeChildren = Hashtable()
        }
        var child = propertyChangeChildren!![propertyName]
        if (child == null) {
            child = ChangeEventDispatcher(source)
            propertyChangeChildren!![propertyName] = child
        }
        child.addPropertyChangeListener(listener)
    }

    /**
     * Remove a PropertyChangeListener from the listener list. This removes a
     * ConfigurationChangeListener that was registered for all properties.
     *
     * @param listener The PropertyChangeListener to be removed
     */
    @Synchronized
    fun removePropertyChangeListener(listener: PropertyChangeListener?) {
        if (propertyChangeListeners != null) propertyChangeListeners!!.remove(listener)
    }

    /**
     * Remove a PropertyChangeListener for a specific property.
     *
     * @param propertyName The name of the property that was listened on.
     * @param listener The VetoableChangeListener to be removed
     */
    @Synchronized
    fun removePropertyChangeListener(propertyName: String?,
            listener: PropertyChangeListener?) {
        if (propertyChangeChildren != null) {
            val child = propertyChangeChildren!![propertyName]
            child?.removePropertyChangeListener(listener)
        }
    }

    /**
     * Add a VetoableChangeListener to the listener list. The listener is registered for all properties.
     *
     * @param listener The VetoableChangeListener to be added
     */
    @Synchronized
    fun addVetoableChangeListener(listener: ConfigVetoableChangeListener?) {
        if (vetoableChangeListeners == null) {
            vetoableChangeListeners = Vector()
        }
        vetoableChangeListeners!!.add(listener)
    }

    /**
     * Remove a VetoableChangeListener from the listener list. This removes a
     * VetoableChangeListener that was registered for all properties.
     *
     * @param listener The VetoableChangeListener to be removed
     */
    @Synchronized
    fun removeVetoableChangeListener(listener: ConfigVetoableChangeListener?) {
        if (vetoableChangeListeners != null) vetoableChangeListeners!!.remove(listener)
    }

    /**
     * Add a VetoableChangeListener for a specific property. The listener will be invoked only
     * when a call on fireVetoableChange names that specific property.
     *
     * @param propertyName The name of the property to listen on.
     * @param listener The ConfigurationChangeListener to be added
     */
    @Synchronized
    fun addVetoableChangeListener(propertyName: String?,
            listener: ConfigVetoableChangeListener?) {
        if (vetoableChangeChildren == null) {
            vetoableChangeChildren = Hashtable()
        }
        var child = vetoableChangeChildren!![propertyName]
        if (child == null) {
            child = ChangeEventDispatcher(source)
            vetoableChangeChildren!![propertyName] = child
        }
        child.addVetoableChangeListener(listener)
    }

    /**
     * Remove a VetoableChangeListener for a specific property.
     *
     * @param propertyName The name of the property that was listened on.
     * @param listener The VetoableChangeListener to be removed
     */
    @Synchronized
    fun removeVetoableChangeListener(propertyName: String?, listener: ConfigVetoableChangeListener?) {
        if (vetoableChangeChildren != null) {
            val child = vetoableChangeChildren!![propertyName]
            child?.removeVetoableChangeListener(listener)
        }
    }

    /**
     * Report a vetoable property update to any registered listeners. If no one vetos the change,
     * then fire a new ConfigurationChangeEvent indicating that the change has been accepted. In
     * the case of a PropertyVetoException, end event dispatch and rethrow the exception
     *
     *
     * No event is fired if old and new are equal and non-null.
     *
     * @param propertyName The programmatic name of the property that is about to change..
     * @param oldValue The old value of the property.
     * @param newValue The new value of the property.
     */
    fun fireVetoableChange(propertyName: String?, oldValue: Any?, newValue: Any?) {
        if (vetoableChangeListeners != null || vetoableChangeChildren != null) {
            fireVetoableChange(PropertyChangeEvent(source, propertyName, oldValue, newValue))
        }
    }

    /**
     * Fire a vetoable property update to any registered listeners. If anyone vetos the change,
     * then the exception will be rethrown by this method.
     *
     *
     * No event is fired if old and new are equal and non-null.
     *
     * @param evt The PropertyChangeEvent to be fired.
     */
    private fun fireVetoableChange(evt: PropertyChangeEvent) {
        val oldValue = evt.oldValue
        val newValue = evt.newValue
        val propertyName = evt.propertyName
        if (oldValue != null && oldValue == newValue) return
        var targets: Array<ConfigVetoableChangeListener?>? = null
        var child: ChangeEventDispatcher? = null
        synchronized(this) {
            if (vetoableChangeListeners != null) {
                targets = vetoableChangeListeners!!.toTypedArray()
            }
            if (vetoableChangeChildren != null && propertyName != null) child = vetoableChangeChildren!![propertyName]
        }
        if (vetoableChangeListeners != null && targets != null) {
            for (target in targets!!) target!!.vetoableChange(evt)
        }
        if (child != null) child!!.fireVetoableChange(evt)
    }

    /**
     * Report a bound property update to any registered listeners. No event is fired if old and
     * new are equal and non-null.
     *
     * @param propertyName The programmatic name of the property that was changed.
     * @param oldValue The old value of the property.
     * @param newValue The new value of the property.
     */
    fun firePropertyChange(propertyName: String?, oldValue: Any?, newValue: Any?) {
        if (oldValue == null || oldValue != newValue) {
            firePropertyChange(PropertyChangeEvent(source, propertyName, oldValue, newValue))
        }
    }

    /**
     * Fire an existing PropertyChangeEvent to any registered listeners. No event is fired if the
     * given event's old and new values are equal and non-null.
     *
     * @param evt The PropertyChangeEvent object.
     */
    fun firePropertyChange(evt: PropertyChangeEvent) {
        val oldValue = evt.oldValue
        val newValue = evt.newValue
        val propertyName = evt.propertyName
        if (oldValue != null && oldValue == newValue) return
        if (propertyChangeListeners != null) {
            for (target in propertyChangeListeners!!) target!!.propertyChange(evt)
        }
        if (propertyChangeChildren != null && propertyName != null) {
            val child = propertyChangeChildren!![propertyName]
            child?.firePropertyChange(evt)
        }
    }

    /**
     * Check if there are any listeners for a specific property. (Generic listeners count as well)
     *
     * @param propertyName the property name.
     * @return true if there are one or more listeners for the given property
     */
    @Synchronized
    fun hasPropertyChangeListeners(propertyName: String?): Boolean {
        if (propertyChangeListeners != null && !propertyChangeListeners!!.isEmpty()) {
            // there is a generic listener
            return true
        }
        if (propertyChangeChildren != null) {
            val child = propertyChangeChildren!![propertyName]
            if (child != null && child.propertyChangeListeners != null) return !child.propertyChangeListeners!!.isEmpty()
        }
        return false
    }

    /**
     * Check if there are any vetoable change listeners for a specific property. (Generic vetoable
     * change listeners count as well)
     *
     * @param propertyName the property name.
     * @return true if there are one or more listeners for the given property
     */
    @Synchronized
    fun hasVetoableChangeListeners(propertyName: String?): Boolean {
        if (vetoableChangeListeners != null && !vetoableChangeListeners!!.isEmpty()) {
            // there is a generic listener
            return true
        }
        if (vetoableChangeChildren != null) {
            val child = vetoableChangeChildren!![propertyName]
            if (child != null && child.vetoableChangeListeners != null) return !child.vetoableChangeListeners!!.isEmpty()
        }
        return false
    }
}