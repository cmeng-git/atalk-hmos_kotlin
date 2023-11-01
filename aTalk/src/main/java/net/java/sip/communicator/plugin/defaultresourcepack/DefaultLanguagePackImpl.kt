/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.plugin.defaultresourcepack

import net.java.sip.communicator.service.resources.LanguagePack
import net.java.sip.communicator.service.resources.ResourceManagementServiceUtils.getLocale
import java.net.URL
import java.util.*

/**
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class DefaultLanguagePackImpl : LanguagePack {
    /**
     * The locale used for the last resources request
     */
    private var localeInBuffer: Locale? = null

    /**
     * The result of the last resources request
     */
    private var lastResourcesAsked: MutableMap<String?, String?>? = null

    /**
     * All language resource locales.
     */
    private val availableLocales = Vector<Locale>()

    /**
     * Constructor.
     */
    init {
        // Finds all the files *.properties in the path : /resources/languages.
        val fsEnum = DefaultResourcePackActivator.bundleContext!!.bundle
                .findEntries("/resources/languages", "*.properties", false)
        if (fsEnum != null) {
            while (fsEnum.hasMoreElements()) {
                val fileName = (fsEnum.nextElement() as URL).file
                val localeIndex = fileName.indexOf('_')
                if (localeIndex != -1) {
                    val localeId = fileName.substring(localeIndex + 1, fileName.indexOf('.', localeIndex))
                    availableLocales.add(getLocale(localeId))
                }
            }
        }
    }

    /**
     * Returns a `Map`, containing all [key, value] pairs for this resource pack.
     *
     * @return a `Map`, containing all [key, value] pairs for this resource pack.
     */
    override fun getResources(): MutableMap<String?, String?>? {
        return getResources(Locale.getDefault())
    }

    /**
     * Returns a `Map`, containing all [key, value] pairs for the given locale.
     *
     * @param locale The `Locale` we're looking for.
     * @return a `Map`, containing all [key, value] pairs for the given locale.
     */
    override fun getResources(locale: Locale?): MutableMap<String?, String?>? {
        // check if we didn't computed it at the previous call
        if (locale == localeInBuffer && lastResourcesAsked != null) {
            return lastResourcesAsked
        }
        val resourceBundle = ResourceBundle.getBundle(DEFAULT_RESOURCE_PATH, locale, object : ResourceBundle.Control() {
            // work around Java's backwards compatibility
            override fun toBundleName(baseName: String, locale: Locale): String {
                return when (locale) {
                    Locale("he") -> {
                        baseName + "_he"
                    }
                    Locale("yi") -> {
                        baseName + "_yi"
                    }
                    Locale("id") -> {
                        baseName + "_id"
                    }
                    else -> super.toBundleName(baseName, locale)
                }
            }
        })
        val resources = Hashtable<String?, String?>()
        initResources(resourceBundle, resources)
        initPluginResources(resources, locale)

        // keep it just in case of...
        localeInBuffer = locale
        lastResourcesAsked = resources
        return resources
    }

    /**
     * Returns a Set of the keys contained only in the ResourceBundle for locale.
     *
     * @param locale the locale for which the keys are requested
     * @return a Set of the keys contained only in the ResourceBundle for locale
     */
    override fun getResourceKeys(locale: Locale?): Set<String?> {
        try {
            val handleKeySet = ResourceBundle::class.java.getDeclaredMethod("handleKeySet")
            handleKeySet.isAccessible = true
            return handleKeySet.invoke(ResourceBundle.getBundle(DEFAULT_RESOURCE_PATH, locale)) as Set<String?>
        } catch (e: Exception) {
        }
        return HashSet()
    }

    /**
     * Returns the name of this resource pack.
     *
     * @return the name of this resource pack.
     */
    override fun getName(): String {
        return "Default Language Resources"
    }

    /**
     * Returns the description of this resource pack.
     *
     * @return the description of this resource pack.
     */
    override fun getDescription(): String {
        return "Provide Jitsi default Language resource pack."
    }

    /**
     * Fills the given resource map with all (key,value) pairs obtained from the given `ResourceBundle`.
     * This method will look in the properties files for references to other properties files and will include
     * in the final map data from all referenced files.
     *
     * @param resourceBundle The initial `ResourceBundle`, corresponding to the "main" properties file.
     * @param resources A `Map` that would store the data.
     */
    private fun initResources(resourceBundle: ResourceBundle, resources: MutableMap<String?, String?>) {
        val colorKeys = resourceBundle.keys
        while (colorKeys.hasMoreElements()) {
            val key = colorKeys.nextElement()
            val value = resourceBundle.getString(key)
            resources[key] = value
        }
    }

    /**
     * Finds all plugin color resources, matching the "images-*.properties" pattern and adds them to this resource pack.
     */
    private fun initPluginResources(resources: MutableMap<String?, String?>, locale: Locale?) {
        val pluginProperties = DefaultResourcePackActivator.findResourcePaths("resources/languages", "strings-*.properties")
        while (pluginProperties.hasNext()) {
            val resourceBundleName = pluginProperties.next()
            if (resourceBundleName.indexOf('_') == -1) {
                val resourceBundle = ResourceBundle.getBundle(resourceBundleName.substring(0,
                        resourceBundleName.indexOf(".properties")), locale)
                initResources(resourceBundle, resources)
            }
        }
    }

    /**
     * All the locales in the language pack.
     *
     * @return all the locales this Language pack contains.
     */
    override fun getAvailableLocales(): Iterator<Locale?> {
        return availableLocales.iterator()
    }

    companion object {
        private const val DEFAULT_RESOURCE_PATH = "resources.languages.resources"
    }
}