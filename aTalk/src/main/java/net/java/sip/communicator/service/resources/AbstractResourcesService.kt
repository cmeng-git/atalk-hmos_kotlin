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
package net.java.sip.communicator.service.resources

import net.java.sip.communicator.service.resources.ResourceManagementServiceUtils.getLocale
import net.java.sip.communicator.util.ServiceUtils.getService
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleContext
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.text.MessageFormat
import java.util.*
import javax.swing.ImageIcon

/**
 * The abstract class for ResourceManagementService. It listens for
 * [ResourcePack] that are registered and exposes them later for use by
 * subclasses. It implements default behaviour for most methods.
 */
abstract class AbstractResourcesService(
        /**
         * The OSGI BundleContext
         */
        private val bundleContext: BundleContext) : ResourceManagementService, ServiceListener {

    /**
     * Resources for currently loaded `SettingsPack`.
     */
    private var settingsResources: MutableMap<String?, String?>? = null

    /**
     * Currently loaded settings pack.
     */
    private var settingsPack: ResourcePack? = null

    /**
     * Resources for currently loaded `LanguagePack`.
     */
    private var languageResources: Map<String?, String?>? = null

    /**
     * Currently loaded language pack.
     */
    private var languagePack: LanguagePack? = null

    /**
     * The [Locale] of `languageResources` so that the caching of the later
     * can be used when a string with the same `Locale` is requested.
     */
    private var languageLocale: Locale? = null

    /**
     * Resources for currently loaded `ImagePack`.
     */
    private var imageResources: MutableMap<String?, String?>? = null
    /**
     * Currently loaded image pack.
     *
     * @return the currently loaded image pack
     */
    /**
     * Currently loaded image pack.
     */
    protected var imagePack: ImagePack? = null
        private set
    /**
     * Resources for currently loaded `ColorPack`.
     *
     * @return the currently color resources
     */
    /**
     * Resources for currently loaded `ColorPack`.
     */
    protected var colorResources: MutableMap<String?, String?>? = null
        private set

    /**
     * Currently loaded color pack.
     */
    private var colorPack: ResourcePack? = null

    /**
     * Resources for currently loaded `SoundPack`.
     */
    private var soundResources: Map<String?, String?>? = null

    /**
     * Currently loaded sound pack.
     *
     * @return the currently loaded sound pack
     */
    /**
     * Currently loaded sound pack.
     */
    protected var soundPack: ResourcePack? = null
        private set

    /**
     * Currently loaded `SkinPack`.
     *
     * @return the currently loaded skin pack
     */
    /**
     * Currently loaded `SkinPack`.
     */
    protected var skinPack: SkinPack? = null
        private set

    /**
     * Creates an instance of `AbstractResourcesService`.
     *
     * bundleContext the OSGi bundle context
     */
    init {
        bundleContext.addServiceListener(this)
        colorPack = getDefaultResourcePack(ColorPack::class.java, ColorPack.RESOURCE_NAME_DEFAULT_VALUE)
        if (colorPack != null) colorResources = getResources(colorPack!!)
        imagePack = getDefaultResourcePack(ImagePack::class.java, ImagePack.RESOURCE_NAME_DEFAULT_VALUE)
        if (imagePack != null) imageResources = getResources(imagePack!!)

        // changes the default locale if set in the config
        val confService = getService(bundleContext, ConfigurationService::class.java)
        val defaultLocale = confService!!.getProperty(ResourceManagementService.DEFAULT_LOCALE_CONFIG) as String?
        if (defaultLocale != null) Locale.setDefault(getLocale(defaultLocale))
        languagePack = getDefaultResourcePack(LanguagePack::class.java, LanguagePack.RESOURCE_NAME_DEFAULT_VALUE)
        if (languagePack != null) {
            languageLocale = Locale.getDefault()
            languageResources = languagePack!!.getResources(languageLocale)
        }
        settingsPack = getDefaultResourcePack(SettingsPack::class.java, SettingsPack.RESOURCE_NAME_DEFAULT_VALUE)
        if (settingsPack != null) settingsResources = getResources(settingsPack!!)
        soundPack = getDefaultResourcePack(SoundPack::class.java, SoundPack.RESOURCE_NAME_DEFAULT_VALUE)
        if (soundPack != null) soundResources = getResources(soundPack!!)
        skinPack = getDefaultResourcePack(SkinPack::class.java, SkinPack.RESOURCE_NAME_DEFAULT_VALUE)
        if (skinPack != null) {
            if (imageResources != null) imageResources!!.putAll(skinPack!!.getImageResources()!!)
            colorResources!!.putAll(skinPack!!.getColorResources()!!)
            settingsResources!!.putAll(skinPack!!.getSettingsResources()!!)
        }
    }

    /**
     * Handles all `ServiceEvent`s corresponding to `ResourcePack`
     * being registered or unregistered.
     *
     * @param event the `ServiceEvent` that notified us
     */
    override fun serviceChanged(event: ServiceEvent) {
        val sService = bundleContext.getService(event.serviceReference) as? ResourcePack ?: return
        if (event.type == ServiceEvent.REGISTERED) {
            Timber.i("Resource registered %s", sService)
            val resources = getResources(sService)
            when {
                sService is ColorPack && colorPack == null -> {
                    colorPack = sService
                    colorResources = resources
                }
                sService is ImagePack && imagePack == null -> {
                    imagePack = sService
                    imageResources = resources
                }
                sService is LanguagePack && languagePack == null -> {
                    languagePack = sService
                    languageLocale = Locale.getDefault()
                    languageResources = resources
                }
                sService is SettingsPack && settingsPack == null -> {
                    settingsPack = sService
                    settingsResources = resources
                }
                sService is SoundPack && soundPack == null -> {
                    soundPack = sService
                    soundResources = resources
                }
                sService is SkinPack && skinPack == null -> {
                    skinPack = sService
                    if (imagePack != null) imageResources = getResources(imagePack!!)
                    if (colorPack != null) colorResources = getResources(colorPack!!)
                    if (settingsPack != null) settingsResources = getResources(settingsPack!!)
                    if (imageResources != null) imageResources!!.putAll(skinPack!!.getImageResources()!!)
                    colorResources!!.putAll(skinPack!!.getColorResources()!!)
                    settingsResources!!.putAll(skinPack!!.getSettingsResources()!!)
                    onSkinPackChanged()
                }
            }
        } else if (event.type == ServiceEvent.UNREGISTERING) {
            if (sService is ColorPack && colorPack == sService) {
                colorPack = getDefaultResourcePack(ColorPack::class.java, ColorPack.RESOURCE_NAME_DEFAULT_VALUE)
                if (colorPack != null) colorResources = getResources(colorPack!!)
            } else if (sService is ImagePack && imagePack == sService) {
                imagePack = getDefaultResourcePack(ImagePack::class.java, ImagePack.RESOURCE_NAME_DEFAULT_VALUE)
                if (imagePack != null) imageResources = getResources(imagePack!!)
            } else if (sService is LanguagePack && languagePack == sService) {
                languagePack = getDefaultResourcePack(LanguagePack::class.java, LanguagePack.RESOURCE_NAME_DEFAULT_VALUE)
            } else if (sService is SettingsPack && settingsPack == sService) {
                settingsPack = getDefaultResourcePack(SettingsPack::class.java, SettingsPack.RESOURCE_NAME_DEFAULT_VALUE)
                if (settingsPack != null) settingsResources = getResources(settingsPack!!)
            } else if (sService is SoundPack && soundPack == sService) {
                soundPack = getDefaultResourcePack(SoundPack::class.java, SoundPack.RESOURCE_NAME_DEFAULT_VALUE)
                if (soundPack != null) soundResources = getResources(soundPack!!)
            } else if (sService is SkinPack && skinPack == sService) {
                if (imagePack != null) {
                    imageResources = getResources(imagePack!!)
                }
                if (colorPack != null) {
                    colorResources = getResources(colorPack!!)
                }
                if (settingsPack != null) {
                    settingsResources = getResources(settingsPack!!)
                }
                skinPack = getDefaultResourcePack(SkinPack::class.java, SkinPack.RESOURCE_NAME_DEFAULT_VALUE)
                if (skinPack != null) {
                    imageResources!!.putAll(skinPack!!.getImageResources()!!)
                    colorResources!!.putAll(skinPack!!.getColorResources()!!)
                    settingsResources!!.putAll(skinPack!!.getSettingsResources()!!)
                }
                onSkinPackChanged()
            }
        }
    }

    /**
     * Method is invoked when the SkinPack is loaded or unloaded.
     */
    protected abstract fun onSkinPackChanged()

    /**
     * Searches for the `ResourcePack` corresponding to the given `className` and ``.
     *
     * @param clazz The name of the resource class.
     * @param typeName The name of the type we're looking for. For example: RESOURCE_NAME_DEFAULT_VALUE
     * @return the `ResourcePack` corresponding to the given `className` and ``.
     */
    private fun <T : ResourcePack?> getDefaultResourcePack(clazz: Class<T>?, typeName: String): T? {
        var serRefs: Collection<ServiceReference<T>?>?
        val osgiFilter = "(" + ResourcePack.RESOURCE_NAME + "=" + typeName + ")"
        try {
            serRefs = bundleContext.getServiceReferences(clazz, osgiFilter)
        } catch (ex: InvalidSyntaxException) {
            serRefs = null
            Timber.e(ex, "Could not obtain resource packs reference.")
        }
        return if (serRefs != null && !serRefs.isEmpty()) {
            bundleContext.getService(serRefs.iterator().next())
        } else null
    }

    /**
     * Returns the `Map` of (key, value) pairs contained in the given resource pack.
     *
     * @param resourcePack The `ResourcePack` from which we're obtaining the resources.
     * @return the `Map` of (key, value) pairs contained in the given resource pack.
     */
    protected fun getResources(resourcePack: ResourcePack): MutableMap<String?, String?>? {
        return resourcePack.getResources()
    }

    /**
     * All the locales in the language pack.
     *
     * @return all the locales this Language pack contains.
     */
    override val availableLocales: Iterator<Locale?>?
        get() = languagePack!!.getAvailableLocales()

    /**
     * Returns the string for given `key` for specified `locale`.
     * It's the real process of retrieving string for specified locale.
     * The result is used in other methods that operate on localized strings.
     *
     * @param key the key name for the string
     * @param locale the Locale of the string
     * @return the resources string corresponding to the given `key` and `locale`
     */
    protected open fun doGetI18String(key: String?, locale: Locale?): String? {
        val stringResources = if (locale != null && locale == languageLocale) {
            languageResources
        } else {
            if (languagePack == null) null else languagePack!!.getResources(locale)
        }
        return stringResources?.get(key)
    }

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @return An internationalized string corresponding to the given key.
     */
    override fun getI18NString(key: String): String? {
        return getI18NString(key, null, Locale.getDefault())
    }

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The identifier of the string.
     * @param params the parameters to pass to the localized string
     * @return An internationalized string corresponding to the given key.
     */
    override fun getI18NString(key: String, params: Array<String>?): String? {
        return getI18NString(key, params, Locale.getDefault())
    }

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @param locale The locale.
     * @return An internationalized string corresponding to the given key and given locale.
     */
    override fun getI18NString(key: String, locale: Locale): String? {
        return getI18NString(key, null, locale)
    }

    /**
     * Does the additional processing on the resource string. It removes "&"
     * marks used for mnemonics and other characters.
     *
     * @param resourceString_ the resource string to be processed
     * @return the processed string
     */
    private fun processI18NString(resourceString_: String?): String? {
        var resourceString = resourceString_ ?: return null
        val mnemonicIndex = resourceString.indexOf('&')
        if (mnemonicIndex == 0 || (mnemonicIndex > 0
                        && resourceString[mnemonicIndex - 1] != '\\')) {
            val firstPart = resourceString.substring(0, mnemonicIndex)
            val secondPart = resourceString.substring(mnemonicIndex + 1)
            resourceString = firstPart + secondPart
        }
        if (resourceString.indexOf('\\') > -1) {
            resourceString = resourceString.replace("\\\\".toRegex(), "")
        }
        if (resourceString.indexOf("''") > -1) {
            resourceString = resourceString.replace("''".toRegex(), "'")
        }
        return resourceString
    }

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @param params the parameters to pass to the localized string
     * @param locale The locale.
     * @return An internationalized string corresponding to the given key.
     */
    override fun getI18NString(key: String, params: Array<String>?, locale: Locale?): String? {
        var resourceString = doGetI18String(key, locale)
        if (resourceString == null) {
            Timber.w("Missing resource for key: %s", key)
            return "!$key!"
        }
        if (params != null) {
            resourceString = MessageFormat.format(resourceString, *params as Array<Any?>)
        }
        return processI18NString(resourceString)
    }

    /**
     * Returns the character after the first '&' in the internationalized string corresponding to `key`
     *
     * @param key The identifier of the string in the resources properties file.
     * @return the character after the first '&' in the internationalized string corresponding to `key`.
     */
    override fun getI18nMnemonic(key: String): Char {
        return getI18nMnemonic(key, Locale.getDefault())
    }

    /**
     * Returns the character after the first '&' in the internationalized string corresponding to `key`
     *
     * @param key The identifier of the string in the resources properties file.
     * @param locale The locale that we'd like to receive the result in.
     * @return the character after the first '&' in the internationalized string corresponding to `key`.
     */
    override fun getI18nMnemonic(key: String, locale: Locale?): Char {
        val resourceString = doGetI18String(key, locale)
        if (resourceString == null) {
            Timber.w("Missing resource for key: %s", key)
            return 0.toChar()
        }
        val mnemonicIndex = resourceString.indexOf('&')
        return if (mnemonicIndex > -1 && mnemonicIndex < resourceString.length - 1) {
            resourceString[mnemonicIndex + 1]
        } else 0.toChar()
    }

    /**
     * Returns the string value of the corresponding configuration key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @return the string of the corresponding configuration key.
     */
    override fun getSettingsString(key: String): String? {
        return if (settingsResources == null) null else settingsResources!![key]
    }

    /**
     * Returns the int value of the corresponding configuration key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @return the int value of the corresponding configuration key.
     */
    override fun getSettingsInt(key: String): Int {
        val resourceString = getSettingsString(key)
        if (resourceString == null) {
            Timber.w("Missing resource for key: %s", key)
            return 0
        }
        return resourceString.toInt()
    }

    /**
     * Returns an `URL` from a given identifier.
     *
     * @param urlKey The identifier of the url.
     * @return The url for the given identifier.
     */
    override fun getSettingsURL(urlKey: String): URL? {
        val path = getSettingsString(urlKey)
        if (path == null || path.isEmpty()) {
            Timber.w("Missing resource for key: %s", urlKey)
            return null
        }
        return settingsPack!!.javaClass.classLoader!!.getResource(path)
    }

    /**
     * Returns a stream from a given identifier.
     *
     * @param streamKey The identifier of the stream.
     * @return The stream for the given identifier.
     */
    override fun getSettingsInputStream(streamKey: String): InputStream? {
        return getSettingsInputStream(streamKey, settingsPack!!.javaClass)
    }

    /**
     * Returns a stream from a given identifier, obtained through the class
     * loader of the given resourceClass.
     *
     * @param streamKey The identifier of the stream.
     * @param resourceClass the resource class through which the resource would be obtained
     * @return The stream for the given identifier.
     */
    override fun getSettingsInputStream(streamKey: String, resourceClass: Class<*>?): InputStream? {
        val path = getSettingsString(streamKey)
        if (path == null || path.isEmpty()) {
            Timber.w("Missing resource for key: %s", streamKey)
            return null
        }
        return resourceClass!!.classLoader!!.getResourceAsStream(path)
    }

    /**
     * Returns the image path corresponding to the given key.
     *
     * @param key The identifier of the image in the resource properties file.
     * @return the image path corresponding to the given key.
     */
    override fun getImagePath(key: String): String? {
        return if (imageResources == null) null else imageResources!![key]
    }

    /**
     * Loads an image from a given image identifier.
     *
     * @param imageID The identifier of the image.
     * @return The image for the given identifier.
     */
    override fun getImageInBytes(imageID: String): ByteArray? {
        val `in` = getImageInputStream(imageID) ?: return null
        var image: ByteArray? = null
        try {
            image = ByteArray(`in`.available())
            `in`.read(image)
        } catch (e: IOException) {
            Timber.e(e, "Failed to load image:%s", imageID)
        }
        return image
    }

    /**
     * Loads an image from a given image identifier.
     *
     * @param imageID The identifier of the image.
     * @return The image for the given identifier.
     */
    override fun getImage(imageID: String): ImageIcon? {
        val imageURL = getImageURL(imageID)
        return if (imageURL == null) null else ImageIcon(imageURL)
    }

    /**
     * Returns the path of the sound corresponding to the given property key.
     *
     * @param soundKey the key, for the sound path
     * @return the path of the sound corresponding to the given property key.
     */
    override fun getSoundPath(soundKey: String): String? {
        return soundResources!![soundKey]
    }
}