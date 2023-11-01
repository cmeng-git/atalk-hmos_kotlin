/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.configuration

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.configuration.xml.XMLConfigurationStore
import org.atalk.service.configuration.ConfigPropertyVetoException
import org.atalk.service.configuration.ConfigVetoableChangeListener
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.fileaccess.FailSafeTransaction
import org.atalk.service.fileaccess.FileAccessService
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.util.OSUtils
import org.atalk.util.PasswordUtil.replacePasswords
import org.atalk.util.xml.XMLException
import org.jivesoftware.smack.util.StringUtils
import timber.log.Timber
import java.beans.PropertyChangeListener
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.regex.Pattern

/**
 * A straightforward implementation of the `ConfigurationService` using an XML or a
 * .properties file for storing properties. Currently only `String` properties are
 * meaningfully saved (we should probably consider how and whether we should take care of the
 * rest).
 *
 * @author Emil Ivov
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Dmitri Melnikov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ConfigurationServiceImpl : ConfigurationService {
    /**
     * A reference to the currently used configuration file.
     */
    private var configurationFile: File? = null

    /**
     * A set of immutable properties deployed with the application during install time. The
     * properties in this file will be impossible to override and attempts to do so will simply be ignored.
     *
     * @see .defaultProperties
     */
    private val immutableDefaultProperties = HashMap<String?, String>()

    /**
     * A set of properties deployed with the application during install time. Contrary to the
     * properties in [.immutableDefaultProperties] the ones in this map can be overridden
     * with call to the `setProperty()` methods. Still, re-setting one of these properties
     * to `null` would cause for its initial value to be restored.
     */
    private val defaultProperties = HashMap<String?, String>()

    /**
     * Our event dispatcher.
     */
    private val changeEventDispatcher = ChangeEventDispatcher(this)

    /**
     * A (cached) reference to a `FileAccessService` implementation used by this
     * `ConfigurationService` implementation.
     */
    private var faService: FileAccessService? = null

    /**
     * The indicator which determines whether this instance has assigned a value to
     * [.faService]. Introduced in order to avoid multiple attempts to query for a
     * `FileAccessService` implementation while still delaying the initial query.
     */
    private var faServiceIsAssigned = false

    /**
     * The `ConfigurationStore` implementation which contains the property name-value
     * associations of this `ConfigurationService` and performs their actual storing in
     * `configurationFile`.
     */
    private var store: ConfigurationStore? = null

    init {
        /*
         * XXX We explicitly delay the query for the FileAccessService implementation because
         * FileAccessServiceImpl looks for properties set by methods of ConfigurationServiceImpl
         * and we want to make sure that we have given the  chance to this ConfigurationServiceImpl
         * to set these properties before FileAccessServiceImpl looks for them.
         */
        try {
            debugPrintSystemProperties()
            preloadSystemPropertyFiles()
            loadDefaultProperties()
            reloadConfiguration()
        } catch (ex: IOException) {
            Timber.e(ex, "Failed to load the configuration file")
        }
    }

    /**
     * Sets the property with the specified name to the specified value. Calling this method would
     * first trigger a PropertyChangeEvent that will be dispatched to all VetoableChangeListeners.
     * In case no complaints (PropertyVetoException) have been received, the property  will be
     * actually changed and a PropertyChangeEvent will be dispatched.
     *
     * @param propertyName the name of the property
     * @param property the object that we'd like to be come the new value of the property.
     * @throws ConfigPropertyVetoException in case someone is not happy with the change.
     */
    @Throws(ConfigPropertyVetoException::class)
    override fun setProperty(propertyName: String, property: Any?) {
        setProperty(propertyName, property, false)
    }

    /**
     * Sets the property of the specified name to the specified property. Calling this method would
     * first trigger a PropertyChangeEvent that will be dispatched to all VetoableChangeListeners.
     * In case no complaints (PropertyVetoException) have been received, the property will be
     * actually changed and a PropertyChangeEvent will be dispatched. This method also allows the
     * caller to specify whether or not the specified property is a system one.
     *
     * @param propertyName the name of the property to change.
     * @param property the new value of the specified property.
     * @param isSystem specifies whether or not the property being is a System property and should be
     * resolved against the system property set. If the property has previously been
     * specified as system then this value is internally forced to true.
     * @throws ConfigPropertyVetoException in case someone is not happy with the change.
     */
    @Throws(ConfigPropertyVetoException::class)
    override fun setProperty(propertyName: String, property: Any?, isSystem: Boolean) {
        val oldValue = getProperty(propertyName)

        // first check whether the change is ok with everyone
        if (changeEventDispatcher.hasVetoableChangeListeners(propertyName)) changeEventDispatcher.fireVetoableChange(propertyName, oldValue, property)

        // no exception was thrown - lets change the property and fire a change event
        // cmeng - define the location where to store the change properties - can be removed??? file system not use
        doSetProperty(propertyName, property, isSystem)
        try {
            storeConfiguration()
        } catch (ex: IOException) {
            Timber.e("Failed to store configuration after a property change")
        }
        if (changeEventDispatcher.hasPropertyChangeListeners(propertyName)) changeEventDispatcher.firePropertyChange(propertyName, oldValue, property)
    }

    /*
     * Implements ConfigurationService#setProperties(Map). Optimizes the setting of properties by
     * performing a single saving of the property store to the configuration file which is known
     * to be slow because it involves converting the whole store to a string representation
     * and writing a file to the disk.
     *
     * @throws ConfigPropertyVetoException in case someone is not happy with the change.
     */
    @Throws(ConfigPropertyVetoException::class)
    override fun setProperties(properties: Map<String, Any?>?) {
        // first check whether the changes are ok with everyone
        val oldValues = HashMap<String?, Any?>(properties!!.size)
        for ((propertyName, value) in properties) {
            val oldValue = getProperty(propertyName)
            oldValues[propertyName] = oldValue
            if (changeEventDispatcher.hasVetoableChangeListeners(propertyName)) changeEventDispatcher.fireVetoableChange(propertyName, oldValue, value)
        }
        for ((key, value) in properties) doSetProperty(key, value, false)
        try {
            storeConfiguration()
        } catch (ex: IOException) {
            Timber.e("Failed to store configuration after property changes")
        }
        for ((propertyName, value) in properties) {
            if (changeEventDispatcher.hasPropertyChangeListeners(propertyName)) changeEventDispatcher.firePropertyChange(propertyName, oldValues[propertyName], value)
        }
    }

    /**
     * Performs the actual setting of a property with a specific name to a specific new value
     * without asking `VetoableChangeListener`, storing into the configuration file
     * and notifying `PropertyChangeListener`s.
     *
     * @param propertyName the name of the property which is to be set to a specific value
     * @param property the value to be assigned to the property with the specified name
     * @param isSys `true` if the property with the specified name is to be set as a system
     * property; `false`, otherwise
     */
    private fun doSetProperty(propertyName: String, property: Any?, isSys: Boolean) {
        // once set system, a property remains system even if the user specified something else
        var isSystem = isSys
        if (isSystemProperty(propertyName)) isSystem = true

        // ignore requests to override immutable properties:
        if (immutableDefaultProperties.containsKey(propertyName)) return
        if (property == null) {
            store!!.removeProperty(propertyName)
            if (isSystem) {
                // we can't remove or null set a sys property so let's "empty" it.
                System.setProperty(propertyName, "")
            }
        } else if (isSystem) {
            // in case this is a system property, we must only store it in the System property set
            // and keep only a ref locally.
            System.setProperty(propertyName, property.toString())
            store!!.setSystemProperty(propertyName)
        } else {
            store!!.setNonSystemProperty(propertyName, property)
        }
    }

    /**
     * Removes the property with the specified name. Calling this method would first trigger a
     * PropertyChangeEvent that will be dispatched to all VetoableChangeListeners. In case no
     * complaints (PropertyVetoException) have been received, the property will be actually
     * changed and a PropertyChangeEvent will be dispatched. All properties with prefix
     * propertyName will also be removed.
     *
     * @param propertyName the name of the property to change.
     */
    override fun removeProperty(propertyName: String): Int {
        val childPropertyNames = getPropertyNamesByPrefix(propertyName, false)
        val size = childPropertyNames.size + 1
        // remove all child properties
        for (pName in childPropertyNames) {
            removePropertyInternal(pName)
        }
        // remove the parent properties if any
        removePropertyInternal(propertyName)
        try {
            storeConfiguration()
        } catch (ex: IOException) {
            Timber.e("Failed to store configuration after a property change")
        }
        return size
    }

    /**
     * Removes the property with the specified name. Calling this method would first trigger a
     * PropertyChangeEvent that will be dispatched to all VetoableChangeListeners. In case no
     * complaints (PropertyVetoException) have been received, the property will be actually
     * changed and a PropertyChangeEvent will be dispatched. All properties with prefix
     * propertyName will also be removed.
     *
     * Does not store anything.
     *
     * @param propertyName the name of the property to change.
     */
    private fun removePropertyInternal(propertyName: String) {
        val oldValue = getProperty(propertyName)
        // first check whether the change is ok with everyone
        if (changeEventDispatcher.hasVetoableChangeListeners(propertyName)) changeEventDispatcher.fireVetoableChange(propertyName, oldValue, null)

        // no exception was thrown - lets change the property and fire a change event
        Timber.log(TimberLog.FINER, "Will remove prop: %", propertyName)
        store!!.removeProperty(propertyName)
        if (changeEventDispatcher.hasPropertyChangeListeners(propertyName)) changeEventDispatcher.firePropertyChange(propertyName, oldValue, null)
    }

    /**
     * Returns the value of the property with the specified name or null if no such property exists.
     *
     * @param propertyName the name of the property that is being queried.
     * @return the value of the property with the specified name.
     */
    override fun getProperty(propertyName: String): Any? {
        var result: Any? = immutableDefaultProperties[propertyName]
        if (result != null) return result
        result = store!!.getProperty(propertyName)
        if (result == null) result = defaultProperties[propertyName]

        // cmeng - will enable for more testing later
//		if (result == null)
//			Timber.w("Found empty or null property value for: %s", propertyName);
        return result
    }

    /**
     * Returns a `java.util.List` of `String`s containing all property names.
     *
     * @return a `java.util.List`containing all property names
     */
    override fun getAllPropertyNames(name: String): MutableCollection<String?> {
        val resultKeySet = LinkedList<String?>()
        Collections.addAll(resultKeySet, *store!!.getPropertyNames(name))
        return resultKeySet
    }

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
     * In addition to stored properties this method will also search the default mutable and
     * immutable properties.
     *
     * @param prefix a String containing the prefix (the non dotted non-caps part of a property name) that
     * we're looking for.
     * @param exactPrefixMatch a boolean indicating whether the returned property names should all have
     * a prefix that is an exact match of the the `prefix` param or whether properties with
     * prefixes that contain it but are longer than it are also accepted.
     * @return a `java.util.List`containing all property name String-s matching the
     * specified conditions.
     */
    override fun getPropertyNamesByPrefix(prefix: String, exactPrefixMatch: Boolean): MutableList<String> {
        val resultKeySet = HashSet<String?>()

        // first fill in the names from the immutable default property set
        var propertyNameSet: Set<String?>
        var namesArray: Array<String?>
        if (immutableDefaultProperties.isNotEmpty()) {
            propertyNameSet = immutableDefaultProperties.keys
            namesArray = propertyNameSet.toTypedArray()
            getPropertyNamesByPrefix(prefix, exactPrefixMatch, namesArray, resultKeySet)
        }

        // now get property names from the current store.
        getPropertyNamesByPrefix(prefix, exactPrefixMatch, store!!.getPropertyNames(prefix), resultKeySet)

        // finally, get property names from mutable default property set.
        if (defaultProperties.isNotEmpty()) {
            propertyNameSet = defaultProperties.keys
            namesArray = propertyNameSet.toTypedArray()
            getPropertyNamesByPrefix(prefix, exactPrefixMatch, namesArray, resultKeySet)
        }
        return ArrayList(resultKeySet)
    }

    /**
     * Updates the specified `String` `resultSet` to contain all property names in
     * the `names` array that partially or completely match the specified prefix.
     * Depending on the value of the `exactPrefixMatch` parameter the method will (when
     * false) or will not (when exactPrefixMatch is true) include property names that have prefixes
     * longer than the specified `prefix` param.
     *
     * @param prefix a String containing the prefix (the non dotted non-caps part of a property name) that
     * we're looking for.
     * @param exactPrefixMatch a boolean indicating whether the returned property names should all have
     * a prefix that is an exact match of the the `prefix` param or whether properties with
     * prefixes that contain it but are longer than it are also accepted.
     * @param names the list of names that we'd like to search.
     * @return a reference to the updated result set.
     */
    private fun getPropertyNamesByPrefix(prefix: String?, exactPrefixMatch: Boolean,
            names: Array<String?>?, resultSet: MutableSet<String?>): Set<String?> {
        for (key in names!!) {
            // cmeng - A valid Property item must have a "." with suffix propertyName string
            val ix = key!!.lastIndexOf('.')
            if (ix != -1) {
                val keyPrefix = key.substring(0, ix)
                if (exactPrefixMatch) {
                    if (prefix == keyPrefix) {
                        resultSet.add(key)
                    }
                } else if (keyPrefix.startsWith(prefix!!)) {
                    resultSet.add(key)
                }
            }
        }
        return resultSet
    }

    /**
     * Returns a `List` of `String`s containing the property names that have the
     * specified suffix. A suffix is considered to be everything after the last dot in the property name.
     *
     * For example, imagine a configuration service instance containing two properties only:
     *
     * `net.java.sip.communicator.PROP1=value1
     * net.java.sip.communicator.service.protocol.PROP1=value2
    ` *
     *
     * A call to this method with `suffix` equal to "PROP1" will return both properties,
     * whereas the call with `suffix` equal to "communicator.PROP1" or "PROP2" will return
     * an empty `List`. Thus, if the `suffix` argument contains a dot, nothing will be found.
     *
     * @param suffix the suffix for the property names to be returned
     * @return a `List` of `String`s containing the property names which contain the
     * specified `suffix`
     */
    override fun getPropertyNamesBySuffix(suffix: String): List<String> {
        val resultKeySet = LinkedList<String>()
        for (key in store!!.getPropertyNames(suffix)) {
            val ix = key!!.lastIndexOf('.')
            if (ix != -1 && suffix == key.substring(ix + 1)) resultKeySet.add(key)
        }
        return resultKeySet
    }

    /**
     * Adds a PropertyChangeListener to the listener list.
     *
     * @param listener the PropertyChangeListener to be added
     */
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        changeEventDispatcher.addPropertyChangeListener(listener)
    }

    /**
     * Removes a PropertyChangeListener from the listener list.
     *
     * @param listener the PropertyChangeListener to be removed
     */
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        changeEventDispatcher.removePropertyChangeListener(listener)
    }

    /**
     * Adds a PropertyChangeListener to the listener list for a specific property.
     *
     * @param propertyName one of the property names listed above
     * @param listener the PropertyChangeListener to be added
     */
    override fun addPropertyChangeListener(propertyName: String, listener: PropertyChangeListener) {
        changeEventDispatcher.addPropertyChangeListener(propertyName, listener)
    }

    /**
     * Removes a PropertyChangeListener from the listener list for a specific property.
     *
     * @param propertyName a valid property name
     * @param listener the PropertyChangeListener to be removed
     */
    override fun removePropertyChangeListener(propertyName: String, listener: PropertyChangeListener) {
        changeEventDispatcher.removePropertyChangeListener(propertyName, listener)
    }

    /**
     * Adds a VetoableChangeListener to the listener list.
     *
     * @param listener the VetoableChangeListener to be added
     */
    override fun addVetoableChangeListener(listener: ConfigVetoableChangeListener) {
        changeEventDispatcher.addVetoableChangeListener(listener)
    }

    /**
     * Removes a VetoableChangeListener from the listener list.
     *
     * @param listener the VetoableChangeListener to be removed
     */
    override fun removeVetoableChangeListener(listener: ConfigVetoableChangeListener) {
        changeEventDispatcher.removeVetoableChangeListener(listener)
    }

    /**
     * Adds a VetoableChangeListener to the listener list for a specific property.
     *
     * @param propertyName one of the property names listed above
     * @param listener the VetoableChangeListener to be added
     */
    override fun addVetoableChangeListener(propertyName: String, listener: ConfigVetoableChangeListener) {
        changeEventDispatcher.addVetoableChangeListener(propertyName, listener)
    }

    /**
     * Removes a VetoableChangeListener from the listener list for a specific property.
     *
     * @param propertyName a valid property name
     * @param listener the VetoableChangeListener to be removed
     */
    override fun removeVetoableChangeListener(propertyName: String, listener: ConfigVetoableChangeListener) {
        changeEventDispatcher.removeVetoableChangeListener(propertyName, listener)
    }

    /*
     * Implements ConfigurationService#reloadConfiguration().
     */
    @Throws(IOException::class)
    override fun reloadConfiguration() {
        configurationFile = null
        val file = getConfigurationFile()
        if (file != null) {
            if (fileAccessService != null) {
                // Restore the file if necessary.
                val trans = fileAccessService!!.createFailSafeTransaction(file)
                try {
                    trans!!.restoreFile()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to restore configuration file %s", file)
                }
            }
        }
        try {
            store!!.reloadConfiguration(file)
        } catch (xmle: XMLException) {
            throw IOException(xmle)
        }
    }

    /*
     * Implements ConfigurationService#storeConfiguration().
     */
    @Synchronized
    @Throws(IOException::class)
    override fun storeConfiguration() {
        storeConfiguration(getConfigurationFile())
    }

    /**
     * Stores local properties in the specified configuration file.
     *
     * @param file a reference to the configuration file where properties should be stored.
     * @throws IOException if there was a problem writing to the specified file.
     */
    @Throws(IOException::class)
    private fun storeConfiguration(file: File?) {
        /*
         * If the configuration file is forcibly considered read-only, do not write it.
         */
        val readOnly = System.getProperty(ConfigurationService.PNAME_CONFIGURATION_FILE_IS_READ_ONLY)
        if (readOnly != null && java.lang.Boolean.parseBoolean(readOnly)) return

        // write the file.
        var trans: FailSafeTransaction? = null
        if (file != null) {
            val faService = fileAccessService
            if (faService != null) trans = faService.createFailSafeTransaction(file)
        }

        var exception: Throwable? = null
        try {
            trans?.beginTransaction()
            if (file == null) null else FileOutputStream(file).use { stream -> store!!.storeConfiguration(stream) }
            trans?.commit()
        } catch (ex: IllegalStateException) {
            exception = ex
        } catch (ex: IOException) {
            exception = ex
        }
        if (exception != null) {
            Timber.e(exception, "can't write data in the configuration file")
            trans?.rollback()
        }
    }

    /**
     * Use with caution! Returns the name of the configuration file currently used. Placed in
     * HomeDirLocation/HomeDirName [.getScHomeDirLocation] [.getScHomeDirName]
     *
     * @return the name of the configuration file currently used.
     */
    override val configurationFilename: String?
        get() {
            try {
                val file = getConfigurationFile()
                if (file != null) return file.name
            } catch (ex: IOException) {
                Timber.e(ex, "Error loading configuration file")
            }
            return null
        }
    override val isBlindTrustBeforeVerification: Boolean
        get() = getBoolean(ConfigurationService.PNAME_OMEMO_KEY_BLIND_TRUST, true)

    /**
     * Returns the configuration file currently used by the implementation. If there is no such
     * file or this is the first time we reference it a new one is created.
     *
     * @return the configuration File currently used by the implementation.
     */
    @Throws(IOException::class)
    private fun getConfigurationFile(): File? {
        if (configurationFile == null) {
            createConfigurationFile()
            /*
             * Make sure that the properties SC_HOME_DIR_LOCATION and SC_HOME_DIR_NAME are
             * available in the store of this instance so that users don't have to ask the system
             * properties again.
             */
            scHomeDirLocation
            scHomeDirName
        }
        return configurationFile
    }

    /**
     * Determines the name and the format of the configuration file to be used and initializes the
     * [.configurationFile] and [.store] fields of this instance.
     */
    @Throws(IOException::class)
    private fun createConfigurationFile() {
        /*
         * Choose the format of the configuration file so with the slow and fast XML format when necessary.
         */
        val configurationFile = getConfigurationFile("xml", false)
        if (configurationFile == null) {
            /*
             * It's strange that there's no configuration file name but let it play out as it did
             * when the configuration file was in XML format.
             */
            setConfigurationStore(XMLConfigurationStore::class.java)
        } else {
            /*
             * Figure out the format of the configuration file by looking at its extension.
             */
            val name = configurationFile.name
            val extensionBeginIndex = name.lastIndexOf('.')
            val extension = if (extensionBeginIndex > -1) name.substring(extensionBeginIndex) else null

            /*
             * Obviously, a file with the .properties extension is in the properties format. Since
             * there's no file with the .xml extension, the case is simple.
             */
            if (".properties".equals(extension, ignoreCase = true)) {
                this.configurationFile = configurationFile
                if (store !is PropertyConfigurationStore) store = PropertyConfigurationStore()
            } else {
                /*
                 * But if we're told that the configuration file name is with the .xml extension,
                 * we may also have a .properties file or the .xml extension may be only the
                 * default and not forced on us so it may be fine to create a .properties file
                 * and use the properties format anyway.
                 */
                val newConfigurationFile = File(configurationFile.parentFile,
                        (if (extensionBeginIndex > -1) name.substring(0, extensionBeginIndex) else name) + ".properties")

                /*
                 * If there's an actual file with the .properties extension, then we've previously
                 * migrated the configuration from the XML format to the properties format. We
                 * may have failed to delete the migrated .xml file but it's fine because the
                 * .properties file is there to signal that we have to use it instead of the .xml file.
                 */
                if (newConfigurationFile.exists()) {
                    this.configurationFile = newConfigurationFile
                    if (store !is PropertyConfigurationStore) store = PropertyConfigurationStore()
                } else if (getSystemProperty(ConfigurationService.PNAME_CONFIGURATION_FILE_NAME) == null) {
                    val defaultConfigurationStoreClass = defaultConfigurationStoreClass

                    /*
                     * The .xml is not forced on us so we allow ourselves to not obey the default
                     * and use the properties format. If a configuration file in the XML format
                     * exists already, we have to migrate it  to the properties format.
                     */
                    if (configurationFile.exists()) {
                        val xmlStore = XMLConfigurationStore()
                        try {
                            xmlStore.reloadConfiguration(configurationFile)
                        } catch (xmlex: XMLException) {
                            throw IOException(xmlex)
                        }
                        setConfigurationStore(defaultConfigurationStoreClass)
                        if (store != null) copy(xmlStore, store)
                        var exception: Throwable? = null
                        try {
                            storeConfiguration(this.configurationFile)
                        } catch (ex: IllegalStateException) {
                            exception = ex
                        } catch (ex: IOException) {
                            exception = ex
                        }
                        if (exception == null) configurationFile.delete() else {
                            this.configurationFile = configurationFile
                            store = xmlStore
                        }
                    } else {
                        setConfigurationStore(defaultConfigurationStoreClass)
                    }
                } else {
                    /*
                     * The .xml extension is forced on us so we have to assume that whoever forced
                     * it knows what she wants to get so we have to obey and use the XML format.
                     */
                    this.configurationFile = if (configurationFile.exists()) configurationFile else getConfigurationFile("xml", true)
                    if (store !is XMLConfigurationStore) store = XMLConfigurationStore()
                }
            }
        }
    }// no luck, check whether user has specified a custom name in the system properties
    // return "/data/user/0/org.atalk.hmos/files" linked to /data/data/..

    // now save all this as a configuration property so that we don't have to look for it
    // in the sys props next time and so that it is available for other bundles to consult.
// first let's check whether we already have the name of the directory set as a configuration property
    /**
     * Returns the location of the directory where SIP Communicator is to store user specific data
     * such as configuration files.
     * Message and call history as well as is bundle repository are store SQL Database.
     *
     * @return the location of the directory where SIP Communicator is to store user specific data
     * such as configuration files, message and call history as well as is bundle repository.
     */
    override val scHomeDirLocation: String?
        get() {
            // first let's check whether we already have the name of the directory set as a configuration property
            var scHomeDirLocation: String? = null
            if (store != null) scHomeDirLocation = getString(ConfigurationService.PNAME_SC_HOME_DIR_LOCATION)
            if (scHomeDirLocation == null) {
                // no luck, check whether user has specified a custom name in the system properties
                // return "/data/user/0/org.atalk.hmos/files" linked to /data/data/..
                scHomeDirLocation = getSystemProperty(ConfigurationService.PNAME_SC_HOME_DIR_LOCATION)
                if (scHomeDirLocation == null) scHomeDirLocation = getSystemProperty("user.home")

                // now save all this as a configuration property so that we don't have to look for it
                // in the sys props next time and so that it is available for other bundles to consult.
                if (store != null) {
                    store!!.setNonSystemProperty(ConfigurationService.PNAME_SC_HOME_DIR_LOCATION, scHomeDirLocation)
                }
            }
            return scHomeDirLocation
        }// no luck, check whether user has specified a custom name in the system properties
    // return "/data/user/0/org.atalk.hmos/files" linked to /data/data/..

    // now save all this as a configuration property so that we don't have to look for it
    // in the sys props next time and so that it is available for other bundles to consult.
// first let's check whether we already have the name of the directory set as a configuration property
    /**
     * Returns the name of the directory where SIP Communicator is to store user specific data
     * such as configuration files, message and call history as well as is bundle repository.
     *
     * @return the name of the directory where SIP Communicator is to store user specific data
     * such as configuration files, message and call history as well as is bundle repository.
     */
    override val scHomeDirName: String
        get() {
            // first let's check whether we already have the name of the directory set as a configuration property
            var scHomeDirName: String? = null
            if (store != null) scHomeDirName = getString(ConfigurationService.PNAME_SC_HOME_DIR_NAME)
            if (scHomeDirName == null) {
                // no luck, check whether user has specified a custom name in the system properties
                // return "/data/user/0/org.atalk.hmos/files" linked to /data/data/..
                scHomeDirName = getSystemProperty(ConfigurationService.PNAME_SC_HOME_DIR_NAME)
                if (scHomeDirName == null) scHomeDirName = ".sip-communicator"

                // now save all this as a configuration property so that we don't have to look for it
                // in the sys props next time and so that it is available for other bundles to consult.
                if (store != null) store!!.setNonSystemProperty(ConfigurationService.PNAME_SC_HOME_DIR_NAME, scHomeDirName)
            }
            return scHomeDirName
        }

    /**
     * Returns a reference to the configuration file that the service should load. The method
     * would try to load a file with the name sip-communicator.xml unless a different one is
     * specified in the system property net.java .sip.communicator.PROPERTIES_FILE_NAME. The
     * method would first try to load the file from the current directory if it exists this is not
     * the case a load would be attempted from the $HOME/.sip-communicator directory. In case it
     * was not found there either we'll look for it in all locations currently present in the
     * $CLASSPATH. In case we find it in there we will copy it to the $HOME/.sip-communicator
     * directory in case it was in a jar archive and return the reference to the newly created
     * file. In case the file is to be found nowhere - a new empty file in the user home
     * directory and returns a link to that one.
     *
     * @param extension the extension of the file name of the configuration file. The specified extension may
     * not be taken into account if the the configuration file name is forced through a system property.
     * @param create `true` to create the configuration file with the determined file name if it
     * does not exist; `false` to only figure out the file name of the configuration file without creating it
     * @return the configuration file currently used by the implementation.
     */
    @Throws(IOException::class)
    private fun getConfigurationFile(extension: String, create: Boolean): File {
        // see whether we have a user specified name for the conf file
        var pFileName = getSystemProperty(ConfigurationService.PNAME_CONFIGURATION_FILE_NAME)
        if (pFileName == null) pFileName = "sip-communicator.$extension"

        // try to open the file in current directory
        val configFileInCurrentDir = File(pFileName)
        if (configFileInCurrentDir.exists()) {
            Timber.d("Using config file in current dir: %s", configFileInCurrentDir.absolutePath)
            return configFileInCurrentDir
        }

        // we didn't find it in ".", try the SIP Communicator home directory first check whether a
        // custom SC home directory is specified
        val configDir = File(scHomeDirLocation, scHomeDirName)
        val configFileInUserHomeDir = File(configDir, pFileName)
        if (configFileInUserHomeDir.exists()) {
            Timber.d("Using config file in \$HOME/.sip-communicator: %s", configFileInUserHomeDir.absolutePath)
            return configFileInUserHomeDir
        }

        // If we are in a jar - copy config file from jar to user home.
        val `in` = javaClass.classLoader?.getResourceAsStream(pFileName)

        // Return an empty file if there wasn't any in the jar null check report from John J.Barton - IBM
        if (`in` == null) {
            if (create) {
                configDir.mkdirs()
                configFileInUserHomeDir.createNewFile()
                Timber.d("Created an empty file in \$HOME: %s", configFileInUserHomeDir.absolutePath)
            }
            return configFileInUserHomeDir
        }
        Timber.log(TimberLog.FINER, "Copying config file from JAR into %s", configFileInUserHomeDir.absolutePath)
        configDir.mkdirs()
        try {
            copy(`in`, configFileInUserHomeDir)
        } finally {
            try {
                `in`.close()
            } catch (ioex: IOException) {
                /*
                 * Ignore it because it doesn't matter and, most importantly, it shouldn't prevent
                 * us from using the configuration file.
                 */
                ioex.printStackTrace()
            }
        }
        return configFileInUserHomeDir
    }

    /**
     * Returns the String value of the specified property (minus all encompassing whitespaces)and
     * null in case no property value was mapped against the specified propertyName, or in case
     * the returned property string had zero length or contained whitespaces only.
     *
     * @param propertyName the name of the property that is being queried.
     * @return the result of calling the property's toString method and null in case there was no
     * value mapped against the specified `propertyName`, or the returned string had zero
     * length or contained whitespaces only.
     */
    override fun getString(propertyName: String): String? {
        val propValue = getProperty(propertyName) ?: return null
        return StringUtils.returnIfNotEmptyTrimmed(propValue.toString())
    }

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
    override fun getString(propertyName: String, defaultValue: String?): String? {
        val value = getString(propertyName)
        return if (StringUtils.isNullOrEmpty(value)) defaultValue else value
    }

    /**
     * Implements ConfigurationService#getBoolean(String, boolean).
     */
    override fun getBoolean(propertyName: String, defaultValue: Boolean): Boolean {
        val value = getString(propertyName)
        return if (StringUtils.isNullOrEmpty(value)) defaultValue else java.lang.Boolean.parseBoolean(value)
    }

    /**
     * Gets a (cached) reference to a `FileAccessService` implementation to be used by this
     * `ConfigurationService` implementation.
     *
     * @return a (cached) reference to a `FileAccessService` implementation
     */
    @get:Synchronized
    private val fileAccessService: FileAccessService?
        get() {
            if (faService == null && !faServiceIsAssigned) {
                faService = LibJitsi.fileAccessService
                faServiceIsAssigned = true
            }
            return faService
        }

    /**
     * Gets the value of a specific property as a signed decimal integer. If the specified
     * property name is associated with a value in this `ConfigurationService`, the string
     * representation of the value is parsed into a signed decimal integer according to the rules
     * of [Integer.parseInt] . If parsing the value as a signed decimal integer
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
    override fun getInt(propertyName: String, defaultValue: Int): Int {
        val stringValue = getString(propertyName)
        var intValue = defaultValue
        if (stringValue != null && stringValue.isNotEmpty()) {
            try {
                intValue = stringValue.toInt()
            } catch (ex: NumberFormatException) {
                Timber.e(ex, " %sdoes not appear to be an integer. Defaulting to %s",
                        propertyName, defaultValue)
            }
        }
        return intValue
    }

    /**
     * {@inheritDoc}
     */
    override fun getDouble(propertyName: String, defaultValue: Double): Double {
        val stringValue = getString(propertyName)
        var doubleValue = defaultValue
        if (stringValue != null && stringValue.isNotEmpty()) {
            try {
                doubleValue = stringValue.toDouble()
            } catch (ex: NumberFormatException) {
                Timber.e(ex, "%s does not appear to be a double. Defaulting to %s",
                        propertyName, defaultValue)
            }
        }
        return doubleValue
    }

    /**
     * Gets the value of a specific property as a signed decimal long integer. If the specified
     * property name is associated with a value in this `ConfigurationService`, the string
     * representation of the value is parsed into a signed decimal long integer according to
     * the rules of Long.parseLong . If parsing the value as a signed decimal long
     * integer fails or there is no value associated with the specified property name,
     * `defaultValue` is returned.
     *
     * @param propertyName the name of the property to get the value of as a signed decimal long integer
     * @param defaultValue the value to be returned if parsing the value of the specified property name as a
     * signed decimal long integer fails or there is no value associated with the specified
     * property name in this `ConfigurationService`
     * @return the value of the property with the specified name in this
     * `ConfigurationService` as a signed decimal long integer;
     * `defaultValue` if parsing the value of the specified property name fails or no value
     * is associated in this `ConfigurationService` with the specified property name
     */
    override fun getLong(propertyName: String, defaultValue: Long): Long {
        val stringValue = getString(propertyName)
        var longValue = defaultValue
        if (stringValue != null && stringValue.isNotEmpty()) {
            try {
                longValue = stringValue.toLong()
            } catch (ex: NumberFormatException) {
                Timber.e(ex, "%s does not appear to be a longinteger. Defaulting to %s",
                        propertyName, defaultValue)
            }
        }
        return longValue
    }

    /**
     * Determines whether the property with the specified `propertyName` has been
     * previously declared as System
     *
     * @param propertyName the name of the property to verify
     * @return true if someone at some point specified that property to be system. (This could
     * have been either through a call to setProperty(string, true)) or by setting the system
     * attribute in the xml conf file to true.
     */
    private fun isSystemProperty(propertyName: String): Boolean {
        return store!!.isSystemProperty(propertyName)
    }

    /**
     * Deletes the configuration file currently used by this implementation.
     */
    override fun purgeStoredConfiguration() {
        if (configurationFile != null) {
            configurationFile!!.delete()
            configurationFile = null
        }
        if (store != null) for (name in store!!.getPropertyNames("")) store!!.removeProperty(name!!)
    }

    /**
     * Goes over all system properties and outputs their names and values for debug purposes.
     * Changed that system properties are printed in INFO level and this way they
     * are included in the beginning of every users log file.
     */
    private fun debugPrintSystemProperties() {
        try {
            // Password system properties
            var exclusion: Pattern? = null
            if (PASSWORD_SYS_PROPS != null) {
                exclusion = Pattern.compile(PASSWORD_SYS_PROPS, Pattern.CASE_INSENSITIVE)
            }
            // Password command line arguments
            var passwordArgs: List<String>? = null
            if (PASSWORD_CMD_LINE_ARGS != null) passwordArgs = PASSWORD_CMD_LINE_ARGS.split(",")
            for ((key1, value1) in System.getProperties()) {
                val key = key1.toString()
                var value = value1.toString()
                // Check if this key value should be masked
                if (exclusion != null && exclusion.matcher(key).find()) {
                    value = "**********"
                }
                // Mask command line arguments
                if (passwordArgs != null && "sun.java.command" == key) {
                    value = replacePasswords(value, passwordArgs)
                }
                Timber.i("%s = %s", key, value)
            }
        } catch (e: RuntimeException) {
            Timber.w(e, "An exception occurred while writing debug info")
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun logConfigurationProperties(passwordPattern: String) {
        if (!TimberLog.isTraceEnable) return
        var exclusion: Pattern? = null
        if (!StringUtils.isNullOrEmpty(passwordPattern)) {
            exclusion = passwordPattern.let { Pattern.compile(it, Pattern.CASE_INSENSITIVE) }
        }
        for (pp in getAllPropertyNames(passwordPattern)) {
            var v = getProperty(pp!!) ?: continue

            // Not sure if this can happen, but just in case...
            if (exclusion != null && exclusion.matcher(pp).find()) {
                v = "**********"
            }
            Timber.i("%s = %s", pp, v)
        }
    }

    /**
     * The method scans the contents of the SYS_PROPS_FILE_NAME_PROPERTY where it expects to find
     * a comma separated list of names of files that should be loaded as system properties. The
     * method then parses these files and loads their contents as system properties. All such
     * files have to be in a location that's in the classpath.
     */
    private fun preloadSystemPropertyFiles() {
        val propFilesListStr = StringUtils.returnIfNotEmptyTrimmed(System.getProperty(SYS_PROPS_FILE_NAME_PROPERTY))
                ?: return
        val tokenizer = StringTokenizer(propFilesListStr, ";,", false)
        while (tokenizer.hasMoreTokens()) {
            var fileName = tokenizer.nextToken()
            try {
                fileName = fileName.trim { it <= ' ' }
                val fileProps = Properties()
                ClassLoader.getSystemResourceAsStream(fileName).use { stream -> fileProps.load(stream) }

                // now set all of this file's properties as system properties
                for ((key, value) in fileProps) System.setProperty(key as String, value as String)
            } catch (ex: Exception) {
                // this is an insignificant method that should never affect the rest of the
                // application so we'll afford ourselves to kind of silence all possible
                // exceptions (which would most often be IOExceptions). We will however log them
                // in case anyone would be interested.
                Timber.e(ex, "Failed to load property file: %s", fileName)
            }
        }
    }

    /**
     * Specifies the configuration store that this instance of the configuration service
     * implementation must use.
     *
     * @param clazz the [ConfigurationStore] that this configuration service instance instance has to use.
     * @throws IOException if loading properties from the specified store fails.
     */
    @Throws(IOException::class)
    private fun setConfigurationStore(clazz: Class<out ConfigurationStore>) {
        var extension: String? = null
        if (PropertyConfigurationStore::class.java.isAssignableFrom(clazz)) extension = "properties" else if (XMLConfigurationStore::class.java.isAssignableFrom(clazz)) extension = "xml"
        configurationFile = if (extension == null) null else getConfigurationFile(extension, true)
        if (!clazz.isInstance(store)) {
            var exception: Throwable? = null
            try {
                store = clazz.newInstance()
            } catch (ex: IllegalAccessException) {
                exception = ex
            } catch (ex: InstantiationException) {
                exception = ex
            }
            if (exception != null) throw RuntimeException(exception)
        }
    }

    /**
     * Loads the default property maps from the Jitsi installation directory then overrides them
     * with the default override values.
     */
    private fun loadDefaultProperties() {
        loadDefaultProperties(DEFAULT_PROPS_FILE_NAME)
        loadDefaultProperties(DEFAULT_OVERRIDES_PROPS_FILE_NAME)
    }

    /**
     * Tests whether the application has been launched using Java WebStart
     */
    private val isLaunchedByWebStart: Boolean
        get() {
            var hasJNLP: Boolean
            hasJNLP = try {
                Class.forName("javax.jnlp.ServiceManager")
                true
            } catch (ex: ClassNotFoundException) {
                false
            }
            val jwsVersion = System.getProperty("javawebstart.version")
            if (jwsVersion != null && jwsVersion.isNotEmpty()) {
                hasJNLP = true
            }
            return hasJNLP
        }

    /**
     * Loads the specified default properties maps from the Jitsi installation directory.
     * Typically this file is to be called for the default properties and the admin overrides.
     *
     * @param fileName the name of the file we need to load.
     */
    private fun loadDefaultProperties(fileName: String) {
        try {
            val fileProps = Properties()
            val fileStream = if (OSUtils.IS_ANDROID) {
                javaClass.classLoader?.getResourceAsStream(fileName)
            } else if (isLaunchedByWebStart) {
                Timber.i("WebStart classloader")
                Thread.currentThread().contextClassLoader?.getResourceAsStream(fileName)
            } else {
                Timber.i("Normal classloader")
                ClassLoader.getSystemResourceAsStream(fileName)
            }
            if (fileStream == null) {
                Timber.i("Failed to find '%s' with class loader, will continue without it.", fileName)
                return
            }
            fileProps.load(fileStream)
            fileStream.close()

            // now get those properties and place them into the mutable and immutable properties maps.
            for ((key, value1) in fileProps) {
                var name = key as String
                val value = value1 as String?
                if (value == null || name.trim { it <= ' ' }.isEmpty()) {
                    continue
                }
                if (name.startsWith("*")) {
                    name = name.substring(1)
                    if (name.trim { it <= ' ' }.isEmpty()) {
                        continue
                    }

                    // it seems that we have a valid default immutable property
                    immutableDefaultProperties[name] = value

                    // in case this is an override, make sure we remove previous definitions of this property
                    defaultProperties.remove(name)
                } else {
                    // this property is a regular, mutable default property.
                    defaultProperties[name] = value

                    // in case this is an override, make sure we remove previous definitions of this property
                    immutableDefaultProperties.remove(name)
                }
            }
        } catch (ex: Exception) {
            // we can function without defaults so we are just logging those.
            Timber.i("No defaults property file loaded: %s. Not a problem.", fileName)
            Timber.d(ex, "load exception")
        }
    }

    companion object {
        /**
         * The name of the `ConfigurationStore` class to be used as the default when no
         * specific `ConfigurationStore` class is determined as necessary.
         */
        private const val DEFAULT_CONFIGURATION_STORE_CLASS_NAME = "net.java.sip.communicator.impl.configuration.SQLiteConfigurationStore"

        /**
         * Name of the system file name property.
         */
        private const val SYS_PROPS_FILE_NAME_PROPERTY = "net.java.sip.communicator.SYS_PROPS_FILE_NAME"

        /**
         * Name of the file containing default properties.
         */
        private const val DEFAULT_PROPS_FILE_NAME = "atalk-defaults.properties"

        /**
         * Name of the file containing overrides (possibly set by the developer) for any of the default properties.
         */
        private const val DEFAULT_OVERRIDES_PROPS_FILE_NAME = "atalk-default-overrides.properties"

        /**
         * Specify names of command line arguments which are password, so that their values will be
         * masked when 'sun.java.command' is printed to the logs. Separate each name with a comma.
         */
        private val PASSWORD_CMD_LINE_ARGS: String? = null

        /**
         * Set this filed value to a regular expression which will be used to select system
         * properties mKeys whose values should be masked when printed out to the logs.
         */
        private val PASSWORD_SYS_PROPS: String? = null

        /**
         * Gets the `ConfigurationStore` `Class` to be used as the default when no
         * specific `ConfigurationStore` `Class` is determined as necessary.
         *
         * @return the `ConfigurationStore` `Class` to be used as the default when no
         * specific `ConfigurationStore` `Class` is determined as necessary
         */
        private val defaultConfigurationStoreClass: Class<out ConfigurationStore>
            get() {
                var defaultConfigurationStoreClass: Class<out ConfigurationStore>? = null
                var clazz: Class<*>? = null
                try {
                    clazz = Class.forName(DEFAULT_CONFIGURATION_STORE_CLASS_NAME)
                } catch (ignore: ClassNotFoundException) {
                }
                if (clazz != null && ConfigurationStore::class.java.isAssignableFrom(clazz)) {
                    defaultConfigurationStoreClass = clazz as Class<out ConfigurationStore>?
                }
                if (defaultConfigurationStoreClass == null) {
                    defaultConfigurationStoreClass = PropertyConfigurationStore::class.java
                }
                return defaultConfigurationStoreClass
            }

        private fun copy(src: ConfigurationStore, dest: ConfigurationStore?) {
            for (name in src.getPropertyNames("")) {
                when {
                    src.isSystemProperty(name!!) -> dest!!.setSystemProperty(name)
                    else -> dest!!.setNonSystemProperty(name, src.getProperty(name))
                }
            }
        }

        /**
         * Copies the contents of a specific `InputStream` as bytes into a specific output `File`.
         *
         * @param inputStream the `InputStream` the contents of which is to be output in the specified `File`
         * @param outputFile the `File` to write the contents of the specified `InputStream` into
         * @throws IOException IO Exception
         */
        @Throws(IOException::class)
        private fun copy(inputStream: InputStream, outputFile: File) {
            FileOutputStream(outputFile).use { outputStream ->
                val bytes = ByteArray(4 * 1024)
                var bytesRead: Int
                while (inputStream.read(bytes).also { bytesRead = it } != -1) {
                    outputStream.write(bytes, 0, bytesRead)
                }
            }
        }

        /**
         * Returns the value of the specified java system property. In case the value was a zero
         * length String or one that only contained whitespaces, null is returned. This method is for
         * internal use only. Users of the configuration service are to use the getProperty() or
         * getString() methods which would automatically determine whether a property is system or not.
         *
         * @param propertyName the name of the property whose value we need.
         * @return the value of the property with name propertyName or null if the value had length 0
         * or only contained spaces tabs or new lines.
         */
        private fun getSystemProperty(propertyName: String): String? {
            val retval = System.getProperty(propertyName)
            return StringUtils.returnIfNotEmptyTrimmed(retval)
        }
    }
}