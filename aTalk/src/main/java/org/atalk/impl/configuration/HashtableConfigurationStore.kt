/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.configuration

import java.util.*

/**
 * A simple in-memory [ConfigurationStore] implementation that only uses a hashTable.
 *
 * @param <T>
 * the hashTable extension that descendants are going to use.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class HashtableConfigurationStore<T : Hashtable<Any, Any>>

/**
 * Creates an instance of this class using `properties` as the set of properties where
 * it will be storing an retrieving properties.
 *
 * @param properties
 * the map that this store will use for storing and retrieving properties.
 */
protected constructor(
        /**
         * The `HashTable` instance which stores the property name-value associations of this
         * `ConfigurationStore` instance and which is effectively adapted by this instance to
         * `ConfigurationStore`.
         */
        protected val properties: T,
) : ConfigurationStore {

    /**
     * Implements [ConfigurationStore.getProperty]. If this
     * `ConfigurationStore` contains a value associated with the specified property name,
     * returns it. Otherwise, searches for a system property with the specified name and returns
     * its value.
     *
     * @param name
     * the name of the property to get the value of
     * @return the value in this `ConfigurationStore` of the property with the specified
     * name; `null` if the property with the specified name does not have an association
     * with a value in this `ConfigurationStore`
     * @see ConfigurationStore.getProperty
     */
    override fun getProperty(name: String): Any? {
        val value = properties[name]
        return value ?: System.getProperty(name)
    }

    /**
     * Implements [ConfigurationStore.getPropertyNames]. Gets the names of the
     * properties which have values associated in this `ConfigurationStore`.
     *
     * @return an array of `String`s which specify the names of the properties that have
     * values associated in this `ConfigurationStore`; an empty array if this instance
     * contains no property values
     * @see ConfigurationStore.getPropertyNames
     */
    override fun getPropertyNames(name: String): Array<String?> {
        synchronized(properties) {
            return properties.keys.toTypedArray() as Array<String?>
        }
    }

    /**
     * Implements [ConfigurationStore.isSystemProperty]. Considers a property to be
     * system if the system properties contain a value associated with its name.
     *
     * @param name
     * the name of a property which is to be determined whether it is a system property
     * @return `true` if the specified name stands for a system property; `false`,
     * otherwise
     * @see ConfigurationStore.isSystemProperty
     */
    override fun isSystemProperty(name: String): Boolean {
        return System.getProperty(name) != null
    }

    /**
     * Implements [ConfigurationStore.removeProperty]. Removes the value association
     * in this `ConfigurationStore` of the property with a specific name. If the property
     * with the specified name is not associated with a value in this
     * `ConfigurationStore`, does nothing.
     *
     * @param name
     * the name of the property which is to have its value association in this `ConfigurationStore` removed
     * @see ConfigurationStore.removeProperty
     */
    override fun removeProperty(name: String) {
        properties.remove(name)
    }

    /**
     * Implements [ConfigurationStore.setNonSystemProperty].
     *
     * @param name
     * the name of the non-system property to be set to the specified value in this
     * `ConfigurationStore`
     * @param value
     * the value to be assigned to the non-system property with the specified name in this
     * `ConfigurationStore`
     * @see ConfigurationStore.setNonSystemProperty
     */
    override fun setNonSystemProperty(name: String, value: Any?) {
        properties.put(name, value)
    }

    /**
     * Implements [ConfigurationStore.setSystemProperty]. Since system properties
     * are managed through the `System` class, setting a property as system in this
     * `ConfigurationStore` effectively removes any existing value associated with the
     * specified property name from this instance.
     *
     * @param name
     * the name of the property to be set as a system property in this
     * `ConfigurationStore`
     * @see ConfigurationStore.setSystemProperty
     */
    override fun setSystemProperty(name: String) {
        removeProperty(name)
    }
}