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

import net.java.sip.communicator.service.resources.*
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import timber.log.Timber
import java.util.*

/**
 * @author damencho
 * @author Eng Chong Meng
 */
class DefaultResourcePackActivator : BundleActivator {
    @Throws(Exception::class)
    override fun start(bc: BundleContext) {
        bundleContext = bc
        val colPackImpl = DefaultColorPackImpl()
        val props = Hashtable<String, String>()
        props[ResourcePack.RESOURCE_NAME] = ColorPack.RESOURCE_NAME_DEFAULT_VALUE
        bundleContext!!.registerService(ColorPack::class.java.name, colPackImpl, props)
        val imgPackImpl = DefaultImagePackImpl()
        val imgProps = Hashtable<String, String>()
        imgProps[ResourcePack.RESOURCE_NAME] = ImagePack.RESOURCE_NAME_DEFAULT_VALUE
        bundleContext!!.registerService(ImagePack::class.java.name, imgPackImpl, imgProps)

        //		DefaultLanguagePackImpl langPackImpl = new DefaultLanguagePackImpl();
        //		Hashtable<String, String> langProps = new Hashtable<String, String>();
        //		langProps.put(ResourcePack.RESOURCE_NAME, LanguagePack.RESOURCE_NAME_DEFAULT_VALUE);
        //		bundleContext.registerService(LanguagePack.class.getName(), langPackImpl, langProps);
        val setPackImpl = DefaultSettingsPackImpl()
        val setProps = Hashtable<String, String>()
        setProps[ResourcePack.RESOURCE_NAME] = SettingsPack.RESOURCE_NAME_DEFAULT_VALUE
        bundleContext!!.registerService(SettingsPack::class.java.name, setPackImpl, setProps)
        val sndPackImpl = DefaultSoundPackImpl()
        val sndProps = Hashtable<String, String>()
        sndProps[ResourcePack.RESOURCE_NAME] = SoundPack.RESOURCE_NAME_DEFAULT_VALUE
        bundleContext!!.registerService(SoundPack::class.java.name, sndPackImpl, sndProps)
        Timber.i("Default resources ... [REGISTERED]")
    }

    @Throws(Exception::class)
    override fun stop(bc: BundleContext) {
    }

    companion object {
        var bundleContext: BundleContext? = null

        // buffer for ressource files found
        private val ressourcesFiles = Hashtable<String, Iterator<String>>()

        /**
         * Finds all properties files for the given path in this bundle.
         *
         * @param path the path pointing to the properties files.
         */
        fun findResourcePaths(path: String, pattern: String): Iterator<String> {
            val bufferedResult = ressourcesFiles[path + pattern]
            if (bufferedResult != null) {
                return bufferedResult
            }
            val propertiesList = ArrayList<String>()
            val propertiesUrls = bundleContext!!.bundle.findEntries(path, pattern, false)
            if (propertiesUrls != null) {
                while (propertiesUrls.hasMoreElements()) {
                    val propertyUrl = propertiesUrls.nextElement()

                    // Remove the first slash.
                    var propertyFilePath = propertyUrl.path.substring(1)

                    // Replace all slashes with dots.
                    propertyFilePath = propertyFilePath.replace("/".toRegex(), ".")
                    propertiesList.add(propertyFilePath)
                }
            }
            val result = propertiesList.iterator()
            ressourcesFiles[path + pattern] = result
            return result
        }
    }
}