/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
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
package net.java.sip.communicator.plugin.defaultresourcepack

import net.java.sip.communicator.service.resources.SettingsPack
import java.util.*

/**
 * The default settings resource pack.
 *
 * @author Damian Minkov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class DefaultSettingsPackImpl : SettingsPack {
    /**
     * Returns a `Map`, containing all [key, value] pairs for this resource pack.
     *
     * @return a `Map`, containing all [key, value] pairs for this resource pack.
     */
    override fun getResources(): MutableMap<String?, String?>? {
        val resourceBundle = ResourceBundle.getBundle(DEFAULT_RESOURCE_PATH)
        val resources = TreeMap<String?, String?>()
        initResources(resourceBundle, resources)
        initPluginResources(resources)
        return resources
    }

    /**
     * Returns the name of this resource pack.
     *
     * @return the name of this resource pack.
     */
    override fun getName(): String {
        return "Default Settings Resources"
    }

    /**
     * Returns the description of this resource pack.
     *
     * @return the description of this resource pack.
     */
    override fun getDescription(): String {
        return "Provide Jitsi default settings resource pack."
    }

    /**
     * Fills the given resource map with all (key,value) pairs obtained from the
     * given `ResourceBundle`. This method will look in the properties
     * files for references to other properties files and will include in the
     * final map data from all referenced files.
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
     * Finds all plugin color resources, matching the "defaults-*.properties" pattern and adds them to this resource pack.
     */
    private fun initPluginResources(resources: MutableMap<String?, String?>) {
        val pluginProperties = DefaultResourcePackActivator.findResourcePaths("resources/config", "defaults-*.properties")
        while (pluginProperties.hasNext()) {
            val resourceBundleName = pluginProperties.next()
            val resourceBundle = ResourceBundle.getBundle(resourceBundleName.substring(0, resourceBundleName.indexOf(".properties")))
            initResources(resourceBundle, resources)
        }
    }

    companion object {
        private const val DEFAULT_RESOURCE_PATH = "resources.config.defaults"
    }
}