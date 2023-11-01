/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.configuration

import java.io.*
import java.util.*

/**
 * Implements a `ConfigurationStore` which stores property name-value associations in a
 * `Properties` instance and supports its serialization format for the configuration file of
 * `ConfigurationServiceImpl`. Because of the `Properties` backend which can
 * associate names only `String` values, instances of `PropertyConfigurationStore`
 * convert property values to `String` using `Object#toString()`.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class PropertyConfigurationStore
/**
 * Initializes a new `PropertyConfigurationStore` instance.
 */
    : HashtableConfigurationStore<Properties>(SortedProperties()) {

    /**
     * Implements [ConfigurationStore.reloadConfiguration]. Removes all property
     * name-value associations currently present in this `ConfigurationStore` and
     * de-serializes new property name-value associations from a specific `File` which
     * presumably is in the format represented by this instance.
     *
     * @param file
     * the `File` to be read and to deserialize new property name-value associations
     * from into this instance
     * @throws IOException
     * if there is an input error while reading from the specified `file`
     * @see ConfigurationStore.reloadConfiguration
     */
    @Throws(IOException::class)
    override fun reloadConfiguration(file: File?) {
        properties.clear()

        if (file != null) {
            val ins = BufferedInputStream(FileInputStream(file))
            ins.use { ins ->
                properties.load(ins)
            }
        }
    }

    /**
     * Overrides [HashtableConfigurationStore.setNonSystemProperty]. As the
     * backend of this instance is a `Properties` instance, it can only store
     * `String` values and the specified value to be associated with the specified
     * property name is converted to a `String`.
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
        properties.setProperty(name, value.toString())
    }

    /**
     * Implements [ConfigurationStore.storeConfiguration]. Stores/serializes
     * the property name-value associations currently present in this `ConfigurationStore`
     * into a specific `OutputStream` in the format represented by this instance.
     *
     * @param out
     * the `OutputStream` to receive the serialized form of the property name-value
     * associations currently present in this `ConfigurationStore`
     * @throws IOException
     * if there is an output error while storing the properties managed by this
     * `ConfigurationStore` into the specified `file`
     * @see ConfigurationStore.storeConfiguration
     */
    @Throws(IOException::class)
    override fun storeConfiguration(out: OutputStream) {
        properties.store(out, null)
    }
}