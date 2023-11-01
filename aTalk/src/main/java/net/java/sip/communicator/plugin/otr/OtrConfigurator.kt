/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr

import org.atalk.service.configuration.ConfigurationService
import org.bouncycastle.util.encoders.Base64
import java.util.*

/**
 * A class that gets/sets the OTR configuration values. Introduced to assure our
 * configuration is properly written when `XMLConfigurationStore` is
 * used. Can be seen as a proxy between the [ConfigurationService] and the
 * OTR Plugin.
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
class OtrConfigurator {
    /**
     * Gets an XML tag friendly [String] from a [String].
     *
     * @param s a [String]
     * @return an XML friendly [String]
     */
    private fun getXmlFriendlyString(s: String): String {
        var xml = s
        if (xml == null || xml.isEmpty()) return xml

        // XML Tags are not allowed to start with digits,
        // insert a dummy "p" char.
        if (Character.isDigit(xml[0])) xml = "p$xml"
        val cId = CharArray(xml.length)
        for (i in cId.indices) {
            val c = xml[i]
            cId[i] = if (Character.isLetterOrDigit(c)) c else '_'
        }
        return String(cId)
    }

    /**
     * Puts a given property ID under the OTR namespace and makes sure it is XML
     * tag friendly.
     *
     * @param id the property ID.
     * @return the database defined ID.
     */
    private fun getID(id: String): String {
        return "otr.$id"
    }

    /**
     * Returns the value of the property with the specified name or null if no
     * such property exists ([ConfigurationService.getProperty]
     * proxy).
     *
     * @param id of the property that is being queried.
     * @return the `byte[]` value of the property with the specified name.
     */
    fun getPropertyBytes(id: String): ByteArray? {
        val value = OtrActivator.configService.getString(getID(id))
        return if (value == null) null else Base64.decode(value.toByteArray())
    }

    /**
     * Gets the value of a specific property as a boolean (
     * [ConfigurationService.getBoolean] proxy).
     *
     * @param id of the property that is being queried.
     * @param defaultValue the value to be returned if the specified property
     * name is not associated with a value.
     * @return the `Boolean` value of the property with the specified name.
     */
    fun getPropertyBoolean(id: String, defaultValue: Boolean): Boolean {
        return OtrActivator.configService.getBoolean(getID(id), defaultValue)
    }

    /**
     * Sets the property with the specified name to the specified value (
     * [ConfigurationService.setProperty] proxy). The
     * value is Base64 encoded.
     *
     * @param id the name of the property to change.
     * @param value the new value of the specified property.
     */
    fun setProperty(id: String, value: ByteArray?) {
        val valueToStore = String(Base64.encode(value))
        OtrActivator.configService.setProperty(getID(id), valueToStore)
    }

    /**
     * Sets the property with the specified name to the specified value (
     * [ConfigurationService.setProperty] proxy).
     *
     * @param id the name of the property to change.
     * @param value the new value of the specified property.
     */
    fun setProperty(id: String, value: Any?) {
        OtrActivator.configService.setProperty(getID(id), value)
    }

    /**
     * Removes the property with the specified name (
     * [ConfigurationService.removeProperty] proxy).
     *
     * @param id the name of the property to change.
     */
    fun removeProperty(id: String) {
        OtrActivator.configService.removeProperty(getID(id))
    }

    /**
     * Gets the value of a specific property as a signed decimal integer.
     *
     * @param id the name of the property to change.
     * @param defaultValue the value to be returned if the specified property
     * name is not associated with a value.
     * @return the `int` value of the property
     */
    fun getPropertyInt(id: String, defaultValue: Int): Int {
        return OtrActivator.configService.getInt(getID(id), defaultValue)
    }

    /**
     * Appends `value` to the old value of the property with the
     * specified name. The two values will be comma separated.
     *
     * @param id the name of the property to append to
     * @param value the value to append
     */
    fun appendProperty(id: String, value: Any?) {
        val oldValue = OtrActivator.configService.getProperty(getID(id))
        val newValue = if (oldValue == null) value.toString() else "$oldValue,$value"
        setProperty(id, newValue)
    }

    fun getAppendedProperties(id: String): List<String> {
        val listProperties = OtrActivator.configService.getProperty(getID(id)) as String
                ?: return ArrayList<String>()
        return listProperties.split(",")
    }
}