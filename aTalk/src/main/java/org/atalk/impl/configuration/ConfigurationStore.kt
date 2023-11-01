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

/**
 * Abstracts the runtime storage, the serialization and deserialization of the configuration
 * properties and their associated values of `ConfigurationServiceImpl` and the format of
 * the configuration file. Thus `ConfigurationServiceImpl` can operate regardless of these
 * specifics and takes care of asking the `VetoableChangeListener`s, converting the
 * property values to the requested types and notifying the `PropertyChangeListener`s.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface ConfigurationStore {
    /**
     * Gets the value in this `ConfigurationStore` of a property with a specific name.
     *
     * @param name
     * the name of the property to get the value of
     * @return the value in this `ConfigurationStore` of the property with the specified
     * name; `null` if the property with the specified name does not have an association
     * with a value in this `ConfigurationStore`
     */
    fun getProperty(name: String): Any?

    /**
     * Gets the names of the properties which have values associated in this
     * `ConfigurationStore`.
     *
     * @return an array of `String`s which specify the names of the properties that have
     * values associated in this `ConfigurationStore`; an empty array if this instance
     * contains no property values
     */
    fun getPropertyNames(name: String): Array<String?>

    /**
     * Determines whether a specific name stands for a system property.
     *
     * @param name
     * the name of a property which is to be determined whether it is a system property
     * @return `true` if the specified name stands for a system property; `false`,
     * otherwise
     */
    fun isSystemProperty(name: String): Boolean

    /**
     * Removes all property name-value associations currently present in this
     * `ConfigurationStore` and de-serializes new property name-value associations from a
     * specific `File` which presumably is in the format represented by this instance.
     *
     * @param file
     * the `File` to be read and to deserialize new property name-value associations
     * from into this instance
     * @throws IOException
     * if there is an input error while reading from the specified `file`
     * @throws XMLException
     * if parsing the contents of the specified `file` fails
     */
    @Throws(IOException::class, XMLException::class)
    fun reloadConfiguration(file: File?)

    /**
     * Removes the value association in this `ConfigurationStore` of the property with a
     * specific name. If the property with the specified name is not associated with a value in
     * this `ConfigurationStore`, does nothing.
     *
     * @param name
     * the name of the property which is to have its value association in this
     * `ConfigurationStore`
     * removed
     */
    fun removeProperty(name: String)

    /**
     * Sets the value of a non-system property with a specific name to a specific value in this
     * `ConfigurationStore`.
     *
     * @param name
     * the name of the non-system property to be set to the specified value in this
     * `ConfigurationStore`
     * @param value
     * the value to be assigned to the non-system property with the specified name in this
     * `ConfigurationStore`
     */
    fun setNonSystemProperty(name: String, value: Any?)

    /**
     * Sets a property with a specific name to be considered a system property by the
     * `ConfigurationStore`.
     *
     * @param name
     * the name of the property to be set as a system property in this
     * `ConfigurationStore`
     */
    fun setSystemProperty(name: String)

    /**
     * Stores/serializes the property name-value associations currently present in this
     * `ConfigurationStore` into a specific `OutputStream` in the format
     * represented by this instance.
     *
     * @param out
     * the `OutputStream` to receive the serialized form of the property name-value
     * associations currently present in this `ConfigurationStore`
     * @throws IOException
     * if there is an output error while storing the properties managed by this
     * `ConfigurationStore` into the specified `file`
     */
    @Throws(IOException::class)
    fun storeConfiguration(out: OutputStream)
}