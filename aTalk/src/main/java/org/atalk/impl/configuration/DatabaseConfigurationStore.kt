/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.configuration

import org.atalk.util.xml.XMLException
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.*

/**
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class DatabaseConfigurationStore
/**
 * Initializes a new `DatabaseConfigurationStore` instance with a specific runtime
 * `HashTable` storage.
 *
 * @param properties
 * the `HashTable` which is to become the runtime storage of the new instance
 */
/**
 * Initializes a new `DatabaseConfigurationStore` instance.
 */
protected constructor(properties: Hashtable<Any, Any> = Hashtable<Any, Any>()) : HashtableConfigurationStore<Hashtable<Any, Any>>(properties) {
    /**
     * Removes all property name-value associations currently present in this
     * `ConfigurationStore` instance and de-serializes new property name-value
     * associations from its underlying database (storage).
     *
     * @throws IOException
     * if there is an input error while reading from the underlying database (storage)
     */
    @Throws(IOException::class)
    protected abstract fun reloadConfiguration()

    /**
     * Removes all property name-value associations currently present in this
     * `ConfigurationStore` and de-serializes new property name-value associations from a
     * specific `File` which presumably is in the format represented by this instance.
     *
     * @param file the `File` to be read and to deserialize new property name-value associations
     * from into this instance
     * @throws IOException if there is an input error while reading from the specified `file`
     * @throws XMLException if parsing the contents of the specified `file` fails
     * @see ConfigurationStore.reloadConfiguration
     */
    @Throws(IOException::class, XMLException::class)
    override fun reloadConfiguration(file: File?) {
        properties.clear()
        reloadConfiguration()
    }

    /**
     * Stores/serializes the property name-value associations currently present in this
     * `ConfigurationStore` instance into its underlying database (storage).
     *
     * @throws IOException if there is an output error while storing the properties managed by this
     * `ConfigurationStore` instance into its underlying database (storage)
     */
    @Throws(IOException::class)
    protected fun storeConfiguration() {
    }

    /**
     * Stores/serializes the property name-value associations currently present in this
     * `ConfigurationStore` into a specific `OutputStream` in the format represented by this instance.
     *
     * @param out the `OutputStream` to receive the serialized form of the property name-value
     * associations currently present in this `ConfigurationStore`
     * @throws IOException if there is an output error while storing the properties managed by this
     * `ConfigurationStore` into the specified `file`
     * @see ConfigurationStore.storeConfiguration
     */
    @Throws(IOException::class)
    override fun storeConfiguration(out: OutputStream) {
        storeConfiguration()
    }
}