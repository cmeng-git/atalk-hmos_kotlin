/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidresources

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.WindowManager
import net.java.sip.communicator.service.resources.AbstractResourcesService
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.osgi.OSGiService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory
import java.util.*

/**
 * An Android implementation of the [ResourceManagementService].
 *
 * Strings - requests are redirected to the strings defined in "strings.xml" file, but in case
 * the string is not found it will try to look for strings defined in default string resources.
 *
 * Dots in keys are replaced with "_", as they can not be used for string names in "strings.xml".
 * For example the string for key "service.gui.CLOSE" should be declared as:
 * &lt;string name="service_gui_CLOSE"&gt;Close&lt;/string&gt;
 *
 * Requests for other locales are redirected to corresponding folders as it's defined in Android
 * localization mechanism.
 *
 * Colors - mapped directly to those defined in /res/values/colors.xml
 *
 * Sounds - are stored in res/raw folder. The mappings are read from the sounds.properties or
 * other SoundPack's provided. Properties should point to sound file names without the extension.
 * For example: BUSY=busy (points to /res/raw/busy.wav)
 *
 * Images - images work the same as sounds except they are stored in drawable folders.
 *
 * For parts of aTalk source that directly referred to image paths it will map the requests to the
 * drawable Android application resource names, so that we can take advantage of built-in image
 * size resolving mechanism. The mapping must be specified in file [.IMAGE_PATH_RESOURCE].properties.
 * Sample entries:
 * resources/images/protocol/sip/sip16x16.png=sip_logo
 * resources/images/protocol/sip/sip32x32.png=sip_logo
 * resources/images/protocol/sip/sip48x48.png=sip_logo
 * resources/images/protocol/sip/sip64x64.png=sip_logo
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidResourceServiceImpl : AbstractResourcesService(AndroidResourceManagementActivator.bundleContext) {
    /**
     * Android image path translation resource TODO: Remove direct path requests for resources
     */
    private val androidImagePathPack = ResourceBundle.getBundle(IMAGE_PATH_RESOURCE)

    /**
     * The application package name(org.atalk.hmos)
     */
    private val packageName: String

    /**
     * The Android application context
     */
    private val androidContext: Context

    /**
     * The [Resources] cache for language other than default
     */
    private var cachedLocaleResources: Resources? = null

    /**
     * The [Locale] of cached locale resources
     */
    private var cachedResLocale: Locale? = null

    /**
     * Initializes already registered default resource packs.
     */
    init {
        Timber.log(TimberLog.FINER, "Loaded image path resource: %s", androidImagePathPack)
        val bundleContext = AndroidResourceManagementActivator.bundleContext
        val serviceRef = bundleContext.getServiceReference(OSGiService::class.java)
        val osgiService = bundleContext.getService(serviceRef)
        resources = osgiService.resources
        packageName = osgiService.packageName
        androidContext = osgiService.applicationContext
        if (!factorySet) {
            URL.setURLStreamHandlerFactory(AndroidResourceURLHandlerFactory())
            factorySet = true
        }
    }

    override fun onSkinPackChanged() {
        // Not interested (at least for now)
    }

    /**
     * Gets the resource ID for given color `strKey`.
     *
     * @param strKey the color text identifier that has to be resolved
     * @return the resource ID for given color `strKey`
     */
    private fun getColorId(strKey: String?): Int {
        return getResourceId("color", strKey)
    }

    /**
     * Returns the int representation of the color corresponding to the given key.
     *
     * @param key The key of the color in the colors properties file.
     * @return the int representation of the color corresponding to the given key.
     */
    override fun getColor(key: String): Int {
        val id = getColorId(key)
        return if (id == 0) {
            -0x1
        } else resources!!.getColor(id, null)
    }

    /**
     * Returns the string representation of the color corresponding to the given key.
     *
     * @param key The key of the color in the colors properties file.
     * @return the string representation of the color corresponding to the given key.
     */
    override fun getColorString(key: String): String {
        val id = getColorId(key)
        return if (id == 0) {
            "0xFFFFFFFF"
        } else resources!!.getString(id)
    }

    /**
     * Returns a drawable resource id for given name.
     *
     * @param key the name of drawable
     */
    private fun getDrawableId(key: String?): Int {
        return getResourceId("drawable", key)
    }

    /**
     * Returns the resource id for the given name of specified type.
     *
     * @param typeName the type name (color, drawable, raw, string ...)
     * @param key the resource name
     * @return the resource id for the given name of specified type
     */
    private fun getResourceId(typeName: String, key: String?): Int {
        val id = resources!!.getIdentifier(key, typeName, packageName)
        if (id == 0) Timber.e("Unresolved '%s' key: %s", typeName, key)
        return id
    }

    /**
     * Returns the `InputStream` of the image corresponding to the given path.
     *
     * @param path The path to the image file.
     * @return the `InputStream` of the image corresponding to the given path.
     */
    override fun getImageInputStreamForPath(path: String): InputStream? {
        Timber.log(TimberLog.FINER, "Request for resource path: %s", path)
        if (androidImagePathPack.containsKey(path)) {
            val translatedPath = androidImagePathPack.getString(path)
            Timber.log(TimberLog.FINER, "Translated path: %s", translatedPath)
            if (translatedPath != null) {
                return getImageInputStream(translatedPath)
            }
        }
        return null
    }

    /**
     * Returns the `InputStream` of the image corresponding to the given key.
     *
     * @param streamKey The identifier of the image in the resource properties file.
     * @return the `InputStream` of the image corresponding to the given key.
     */
    override fun getImageInputStream(streamKey: String): InputStream? {
        // Try to lookup images.properties for key mapping
        var key = streamKey
        val resolvedPath = super.getImagePath(key)
        if (resolvedPath != null) {
            key = resolvedPath
        }
        val id = getDrawableId(key)
        return if (id != 0) {
            resources!!.openRawResource(id)
        } else null
    }

    /**
     * Returns the `URL` of the image corresponding to the given key.
     *
     * @param urlKey The identifier of the image in the resource properties file.
     * @return the `URL` of the image corresponding to the given key
     */
    override fun getImageURL(urlKey: String): URL? {
        return getImageURLForPath(getImagePath(urlKey))
    }

    /**
     * Returns the `URL` of the image corresponding to the given path.
     *
     * @param path The path to the given image file.
     * @return the `URL` of the image corresponding to the given path.
     */
    override fun getImageURLForPath(path: String?): URL? {
        return if (path == null) null else try {
            URL(path)
        } catch (e: MalformedURLException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Returns the image path corresponding to the given key.
     *
     * @param key The identifier of the image in the resource properties file.
     * @return the image path corresponding to the given key.
     */
    override fun getImagePath(key: String): String? {
        var reference = super.getImagePath(key)
        if (reference == null) {
            // If no mapping found use key directly
            reference = key
        }

        val id = getDrawableId(reference)
        return if (id == 0) null else "$PROTOCOL://$id"
    }

    /**
     * Returns the string resource id for given `key`.
     *
     * @param key the name of string resource as defined in "strings.xml"
     * @return the string value for given `key`
     */
    private fun getStringId(key: String): Int {
        return getResourceId("string", key)
    }

    override fun doGetI18String(key: String?, locale: Locale?): String? {
        var usedRes = resources
        val resourcesLocale = usedRes!!.configuration.locale
        if (locale != null && locale != resourcesLocale) {
            if (locale != cachedResLocale) {
                // Create the Resources object for recently requested locale and caches it in
                // case another request may come up
                val conf = resources!!.configuration
                conf.locale = locale
                val assets = androidContext.assets
                val metrics = DisplayMetrics()
                val wm = androidContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.defaultDisplay.getMetrics(metrics)
                cachedLocaleResources = Resources(assets, metrics, conf)
                cachedResLocale = locale
            }
            usedRes = cachedLocaleResources
        }

        /*
         * Does replace the "." with "_" as they do not work in strings.xml, they are replaced
         * anyway during the resources generation process
         */
        val id = getStringId(key!!.replace(".", "_"))
        return if (id == 0) {
            // If not found tries to get from resources.properties
            super.doGetI18String(key, locale)
        } else usedRes!!.getString(id)
    }

    /**
     * The sound resource identifier. Sounds are stored in res/raw folder.
     *
     * @param key the name of sound, for busy.wav it will be just busy
     * @return the sound resource id for given `key`
     */
    private fun getSoundId(key: String?): Int {
        return getResourceId("raw", key)
    }

    /**
     * Returns the `URL` of the sound corresponding to the given property key.
     *
     * @param urlKey the key string
     * @return the `URL` of the sound corresponding to the given property key.
     */
    override fun getSoundURL(urlKey: String): URL? {
        return try {
            val path = getSoundPath(urlKey) ?: return null
            URL(path)
        } catch (e: MalformedURLException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Returns the `URL` of the sound corresponding to the given path.
     *
     * @param path the path, for which we're looking for a sound URL
     * @return the `URL` of the sound corresponding to the given path.
     */
    override fun getSoundURLForPath(path: String): URL? {
        return getSoundURL(path)
    }

    /**
     * Returns the path for given `soundKey`. It's formatted with protocol name in the URI format.
     *
     * @param soundKey the key, for the sound path
     */
    override fun getSoundPath(soundKey: String): String? {
        var reference = super.getSoundPath(soundKey)
        if (reference == null) {
            // If there's no definition in .properties try to access directly by the name
            reference = soundKey
        }
        val id = getSoundId(reference)
        if (id == 0) {
            Timber.e("No sound defined for: %s", soundKey)
            return null
        }
        return PROTOCOL + "://" + id
    }

    /**
     * Not supported at the moment.
     *
     * @param zipFile the zip file from which we prepare a skin
     * @return the prepared file
     * @throws Exception
     */
    @Throws(Exception::class)
    override fun prepareSkinBundleFromZip(zipFile: File?): File? {
        throw UnsupportedOperationException()
    }

    /**
     * Some kind of hack to be able to produce URLs pointing to Android resources. It allows to
     * produce URL with protocol name of [.PROTOCOL] that will be later handled by this factory.
     */
    private class AndroidResourceURLHandlerFactory : URLStreamHandlerFactory {
        override fun createURLStreamHandler(s: String): URLStreamHandler? {
            return if (s == PROTOCOL) {
                AndroidResourceURlHandler()
            } else null
        }
    }

    /**
     * The URL handler that handles Android resource paths redirected to Android resources.
     */
    private class AndroidResourceURlHandler : URLStreamHandler() {
        @Throws(IOException::class)
        override fun openConnection(url: URL): URLConnection {
            return AndroidURLConnection(url)
        }
    }

    /**
     * It does open [InputStream] from URLs that were produced for
     * AndroidResourceURLHandlerFactory.PROTOCOL protocol.
     */
    private class AndroidURLConnection(url: URL?) : URLConnection(url) {
        private var id = 0
        @Throws(IOException::class)
        override fun connect() {
        }

        @Throws(IOException::class)
        override fun getInputStream(): InputStream {
            val idStr = super.getURL().host
            return try {
                id = idStr.toInt()
                resources!!.openRawResource(id)
            } catch (exc: NumberFormatException) {
                throw IOException("Invalid resource id: $idStr")
            }
        }
    }

    companion object {
        /**
         * Path to the .properties file containing image path's translations to android drawable resources
         */
        private const val IMAGE_PATH_RESOURCE = "resources.images.image_path"

        /**
         * android resource path prefix for /res/raw
         */
        const val PROTOCOL = "atalk.resource"

        /**
         * The [Resources] object for application context
         */
        private var resources: Resources? = null
        private var factorySet = false
    }
}