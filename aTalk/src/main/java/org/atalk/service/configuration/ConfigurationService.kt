/*
 * aTalk, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.configuration

import java.beans.PropertyChangeListener
import java.io.IOException

/**
 * The configuration services provides a centralized approach of storing persistent configuration ata.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Dmitri Melnikov
 * @author Eng Chong Meng
 */
interface ConfigurationService {
    /**
     * Sets the property with the specified name to the specified value. Calling this method would
     * first trigger a PropertyChangeEvent that will be dispatched to all VetoableChangeListeners.
     * In case no complaints (PropertyVetoException) have been received, the property will be
     * actually changed and a  PropertyChangeEvent will be dispatched.
     *
     * @param propertyName the name of the property to change.
     * @param property the new value of the specified property.
     * @throws ConfigPropertyVetoException in case the changed has been refused by at least one propertyChange listener.
     */
    fun setProperty(propertyName: String, property: Any?)

    /**
     * Sets the property with the specified name to the specified. Calling this method would first
     * trigger a PropertyChangeEvent that will be dispatched to all VetoableChangeListeners. In
     * case no complaints (PropertyVetoException) have been received, the property will be
     * actually changed and a PropertyChangeEvent will be dispatched. This method also allows the
     * caller to specify whether or not the specified property is a system one.
     *
     *
     * @param propertyName the name of the property to change.
     * @param property the new value of the specified property.
     * @param isSystem specifies whether or not the property being is a System property and should be
     * resolved against the system property set
     * @throws ConfigPropertyVetoException in case the changed has been refused by at least one propertyChange listener.
     */
    fun setProperty(propertyName: String, property: Any?, isSystem: Boolean)

    /**
     * Sets a set of specific properties to specific values as a batch operation meaning that first
     * `VetoableChangeListener`s are asked to approve the modifications of the
     * specified properties to the specified values, then the modifications are performed if no
     * complaints have been raised in the form of
     * `PropetyVetoException` and finally `PropertyChangeListener`s are
     * notified about the changes of each of the specified properties. The batch operations
     * allows the  `ConfigurationService` implementations to optimize, for example,
     * the saving of the configuration which in this case can be performed only once for the
     * setting of multiple properties.
     *
     * @param properties a `Map` of property names to their new values to be set
     * @throws ConfigPropertyVetoException if a change in at least one of the properties has been refused by
     * at least one of the `VetoableChangeListener`s
     */
    fun setProperties(properties: Map<String, Any?>?)

    /**
     * Returns the value of the property with the specified name or null if no such property exists.
     *
     * @param propertyName the name of the property that is being queried.
     * @return the value of the property with the specified name.
     */
    fun getProperty(propertyName: String): Any?

    /**
     * Removes the property with the specified name. Calling this method would first trigger a
     * PropertyChangeEvent that will be dispatched to all VetoableChangeListeners. In case no
     * complaints (PropertyVetoException) have been received, the property will be actually
     * changed and a PropertyChangeEvent will be dispatched. All properties with prefix
     * propertyName will also be removed.
     *
     *
     * @param propertyName the name of the property to change.
     * @throws ConfigPropertyVetoException in case the changed has been refused by at least one propertyChange listener.
     */
    fun removeProperty(propertyName: String): Int

    /**
     * Returns a `java.util.List` of `String`s containing all property names.
     *
     * @return a `java.util.List`containing all property names
     */
    fun getAllPropertyNames(name: String): MutableCollection<String?>?

    /**
     * Returns a `java.util.List` of `String`s containing the all property names
     * that have the specified prefix. Depending on the value of the `exactPrefixMatch`
     * parameter the method will (when false) or will not (when exactPrefixMatch is true) include
     * property names that have prefixes longer than the specified `prefix` param.
     *
     * Example:
     *
     * Imagine a configuration service instance containing 2 properties only:<br></br>
     * `
     * net.java.sip.communicator.PROP1=value1<br></br>
     * net.java.sip.communicator.service.protocol.PROP1=value2
    ` *
     *
     * A call to this method with a prefix="net.java.sip.communicator" and exactPrefixMatch=true
     * would only return the first property - net.java.sip.communicator.PROP1, whereas the same
     * call with exactPrefixMatch=false would return both properties as the second prefix
     * includes the requested prefix string.
     *
     *
     * @param prefix a String containing the prefix (the non dotted non-caps part of a property name) that
     * we're looking for.
     * @param exactPrefixMatch a boolean indicating whether the returned property names should all have a prefix that
     * is an exact match of the the `prefix` param or whether properties with
     * prefixes that contain it but are longer than it are also accepted.
     * @return a `java.util.List`containing all property name String-s matching the specified conditions.
     */
    fun getPropertyNamesByPrefix(prefix: String, exactPrefixMatch: Boolean): MutableList<String>

    /**
     * Returns a `List` of `String`s containing the property names that have the
     * specified suffix. A suffix is considered to be everything after the last dot in the property name.
     *
     * For example, imagine a configuration service instance containing two properties only:
     *
     * `
     * net.java.sip.communicator.PROP1=value1
     * net.java.sip.communicator.service.protocol.PROP1=value2
    ` *
     *
     * A call to this method with `suffix` equal to "PROP1" will return both properties,
     * whereas the call with `suffix` equal to "communicator.PROP1" or "PROP2" will return
     * an empty `List`. Thus, if the `suffix` argument contains a dot, nothing will be found.
     *
     *
     * @param suffix the suffix for the property names to be returned
     * @return a `List` of `String`s containing the property names which contain the
     * specified `suffix`
     */
    fun getPropertyNamesBySuffix(suffix: String): List<String>

    /**
     * Returns the String value of the specified property and null in case no property value was
     * mapped against the specified propertyName, or in case the returned property string had
     * zero length or contained whitespaces only.
     *
     * @param propertyName the name of the property that is being queried.
     * @return the result of calling the property's toString method and null in case there was no
     * value mapped against the specified `propertyName`, or the returned string had zero
     * length or contained whitespaces only.
     */
     fun getString(propertyName: String): String?

    /**
     * Returns the String value of the specified property and null in case no property value was
     * mapped against the specified propertyName, or in case the returned property string had
     * zero length or contained whitespaces only.
     *
     * @param propertyName the name of the property that is being queried.
     * @param defaultValue the value to be returned if the specified property name is not associated with a value
     * in this `ConfigurationService`
     * @return the result of calling the property's toString method and `defaultValue`
     * in case there was no value mapped against the specified `propertyName`, or the
     * returned string had zero length or contained whitespaces only.
     */
     fun getString(propertyName: String, defaultValue: String?): String?

    /**
     * Gets the value of a specific property as a boolean. If the specified property name is
     * associated with a value in this `ConfigurationService`, the string
     * representation of the value is parsed into a boolean according to the rules of
     * [Boolean.parseBoolean] . Otherwise, `defaultValue` is returned.
     *
     * @param propertyName the name of the property to get the value of as a boolean
     * @param defaultValue the value to be returned if the specified property name is not associated with a value
     * in this `ConfigurationService`
     * @return the value of the property with the specified name in this
     * `ConfigurationService` as a boolean;
     * `defaultValue` if the property with the specified name is not associated with a
     * value in this `ConfigurationService`
     */
    fun getBoolean(propertyName: String, defaultValue: Boolean): Boolean

    /**
     * Gets the value of a specific property as a signed decimal integer. If the specified
     * property name is associated with a value in this `ConfigurationService`, the string
     * representation of the value is parsed into a signed decimal integer according to the rules
     * of [Integer.parseInt]. If parsing the value as a signed decimal integer
     * fails or there is no value associated with the specified property name,
     * `defaultValue` is returned.
     *
     * @param propertyName the name of the property to get the value of as a signed decimal integer
     * @param defaultValue the value to be returned if parsing the value of the specified property name as a
     * signed decimal integer fails or there is no value associated with the specified
     * property name in this `ConfigurationService`
     * @return the value of the property with the specified name in this
     * `ConfigurationService` as a signed decimal integer; `defaultValue` if
     * parsing the value of the specified property name fails or no value is associated in this
     * `ConfigurationService` with the specified property name
     */
    fun getInt(propertyName: String, defaultValue: Int): Int

    /**
     * Gets the value of a specific property as a double. If the specified property name is
     * associated with a value in this `ConfigurationService`, the string representation
     * of the value is parsed into a double according to the rules of
     * [Double.parseDouble]. If there is no value, or parsing of the value fails,
     * `defaultValue` is returned.
     *
     * @param propertyName the name of the property.
     * @param defaultValue the default value to be returned.
     * @return the value of the property with the specified name in this
     * `ConfigurationService` as a double, or `defaultValue`.
     */
    fun getDouble(propertyName: String, defaultValue: Double): Double

    /**
     * Gets the value of a specific property as a signed decimal long integer. If the specified
     * property name is associated with a value in this `ConfigurationService`, the string
     * representation of the value is parsed into a signed decimal long integer according to the
     * rules of [Long.parseLong]. If parsing the value as a signed decimal long
     * integer fails or there is no value associated with the specified property name,
     * `defaultValue` is returned.
     *
     * @param propertyName the name of the property to get the value of as a signed decimal long integer
     * @param defaultValue the value to be returned if parsing the value of the specified property name as a
     * signed decimal long integer fails or there is no value associated with the specified
     * property name in this `ConfigurationService`
     * @return the value of the property with the specified name in this
     * `ConfigurationService` as a signed
     * decimal long integer; `defaultValue` if parsing the value of the specified property
     * name fails or no value is associated in this `ConfigurationService` with the specified property name
     */
    fun getLong(propertyName: String, defaultValue: Long): Long

    /**
     * Adds a PropertyChangeListener to the listener list. The listener is registered for all
     * properties in the current configuration.
     *
     * @param listener the PropertyChangeListener to be added
     */
    fun addPropertyChangeListener(listener: PropertyChangeListener)

    /**
     * Removes a PropertyChangeListener from the listener list.
     *
     * @param listener the PropertyChangeListener to be removed
     */
    fun removePropertyChangeListener(listener: PropertyChangeListener)

    /**
     * Adds a PropertyChangeListener to the listener list for a specific property. In case a
     * property with the specified name does not exist the listener is still added and would only
     * be taken into account from the moment such a property is set by someone.
     *
     * @param propertyName one of the property names listed above
     * @param listener the PropertyChangeListener to be added
     */
    fun addPropertyChangeListener(propertyName: String, listener: PropertyChangeListener)

    /**
     * Removes a PropertyChangeListener from the listener list for a specific property. This
     * method should be used to remove PropertyChangeListeners that were registered for a
     * specific property. The method has no effect when called for a listener that was not
     * registered for that specific property.
     *
     * @param propertyName a valid property name
     * @param listener the PropertyChangeListener to be removed
     */
    fun removePropertyChangeListener(propertyName: String, listener: PropertyChangeListener)

    /**
     * Adds a VetoableChangeListener to the listener list. The listener is registered for all
     * properties in the configuration.
     *
     * @param listener the VetoableChangeListener to be added
     */
    fun addVetoableChangeListener(listener: ConfigVetoableChangeListener)

    /**
     * Removes a VetoableChangeListener from the listener list.
     *
     * @param listener the VetoableChangeListener to be removed
     */
    fun removeVetoableChangeListener(listener: ConfigVetoableChangeListener)

    /**
     * Adds a VetoableChangeListener to the listener list for a specific property.
     *
     * @param propertyName one of the property names listed above
     * @param listener the VetoableChangeListener to be added
     */
    fun addVetoableChangeListener(propertyName: String, listener: ConfigVetoableChangeListener)

    /**
     * Removes a VetoableChangeListener from the listener list for a specific property.
     *
     * @param propertyName a valid property name
     * @param listener the VetoableChangeListener to be removed
     */
    fun removeVetoableChangeListener(propertyName: String, listener: ConfigVetoableChangeListener)

    /**
     * Store the current set of properties back to the configuration file. The name of the
     * configuration file is queried from the system property net.java.sip.communicator
     * .PROPERTIES_FILE_NAME, and is set to sip-communicator.xml in case the property does not
     * contain a valid file name. The location might be one of three  possible, checked in
     * the following order: <br></br>
     * 1. The current directory. <br></br>
     * 2. The sip-communicator directory in the user.home ($HOME/.sip-communicator)
     * 3. A location
     * in the classpath (such as the sip-communicator jar file).
     *
     * In the last case the file is copied to the sip-communicator configuration directory right
     * after being extracted from the classpath location.
     *
     * @throws IOException in case storing the configuration failed.
     */
    @Throws(IOException::class)
    fun storeConfiguration()

    /**
     * Deletes the current configuration and reloads it from the configuration file. The name of
     * the configuration file is queried from the system property net.java.sip.communicator
     * .PROPERTIES_FILE_NAME, and is set to sip-communicator.xml in case the property does not
     * contain a valid file name. The location might be one of three possible, checked in the
     * following order: <br></br>
     * 1. The current directory. <br></br>
     * 2. The sip-communicator directory in the user.home ($HOME/.sip-communicator) 3. A location
     * in the classpath (such as the sip-communicator jar file).
     *
     * In the last case the file is copied to the sip-communicator configuration directory right
     * after being extracted from the classpath location.
     *
     * @throws IOException in case reading the configuration fails
     */
    @Throws(IOException::class)
    fun reloadConfiguration()

    /**
     * Removes all locally stored properties leaving an empty configuration. Implementations that
     * use a file for storing properties may simply delete it when this method is called.
     */
    fun purgeStoredConfiguration()

    /**
     * Prints all configuration properties on 'INFO' logging level *except* that properties which
     * name matches given regular expression will have their values masked with ***.
     *
     * @param passwordPattern regular expression which detects properties which
     * values should be masked.
     */
    fun logConfigurationProperties(passwordPattern: String)

    /**
     * Returns the name of the directory where aTalk is to store user specific data such as
     * configuration files, message and call history as well as is bundle repository.
     *
     * @return the name of the directory where aTalk is to store user specific data such as
     * configuration files, message and call history as well as is bundle repository.
     */
    val scHomeDirName: String?

    /**
     * Returns the location of the directory where aTalk is to store user specific data such as
     * configuration files, message and call history as well as is bundle repository.
     *
     * @return the location of the directory where aTalk is to store user specific data such as
     * configuration files, message and call history as well as is bundle repository.
     */
    val scHomeDirLocation: String?

    /**
     * Use with caution! Returns the name of the configuration file currently used. Placed in
     * HomeDirLocation/HomeDirName [.getScHomeDirLocation] [.getScHomeDirName]
     *
     * @return the name of the configuration file currently used.
     */
    val configurationFilename: String?

    /**
     * Returns the set state of the Blind Trust Before Verification for omemo contacts
     *
     * @return BlindTrustBeforeVerification set state
     */
    val isBlindTrustBeforeVerification: Boolean

    companion object {
        /**
         * The name of the property that indicates the name of the directory where aTalk is to store
         * user specific data such as configuration files, message and call history.
         */
        const val PNAME_SC_HOME_DIR_NAME = "net.java.sip.communicator.SC_HOME_DIR_NAME"

        /**
         * The name of the property that indicates the location of the directory where aTalk is to
         * store user specific data such as configuration files, message and call history.
         */
        const val PNAME_SC_HOME_DIR_LOCATION = "net.java.sip.communicator.SC_HOME_DIR_LOCATION"

        /**
         * The name of the property that indicates the location of the directory where aTalk is to store cached data.
         */
        const val PNAME_SC_CACHE_DIR_LOCATION = "net.java.sip.communicator.SC_CACHE_DIR_LOCATION"

        /**
         * The name of the property that indicates the location of the directory where aTalk is to store cached data.
         */
        const val PNAME_SC_LOG_DIR_LOCATION = "net.java.sip.communicator.SC_LOG_DIR_LOCATION"

        /**
         * The name of the boolean system property which indicates whether the configuration file is
         * to be considered read-only. The default value is `false` which means that the
         * configuration file is considered writable.
         */
        const val PNAME_CONFIGURATION_FILE_IS_READ_ONLY = "net.java.sip.communicator.CONFIGURATION_FILE_IS_READ_ONLY"

        /**
         * The name of the system property that stores the name of the configuration file.
         */
        const val PNAME_CONFIGURATION_FILE_NAME = "net.java.sip.communicator.CONFIGURATION_FILE_NAME"

        /**
         * The name of the property that users may trust omemo key before its verification.
         */
        const val PNAME_OMEMO_KEY_BLIND_TRUST = "omemo.KEY_BLIND_TRUST"
    }
}