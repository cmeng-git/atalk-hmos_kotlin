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
package net.java.sip.communicator.impl.resources

import net.java.sip.communicator.impl.resources.util.SkinJarBuilder
import net.java.sip.communicator.service.gui.UIService
import net.java.sip.communicator.service.resources.AbstractResourcesService
import net.java.sip.communicator.service.resources.ImagePack
import net.java.sip.communicator.util.ServiceUtils.getService
import org.osgi.framework.ServiceEvent
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import javax.swing.ImageIcon

/**
 * A default implementation of the `ResourceManagementService`.
 *
 * @author Damian Minkov
 * @author Yana Stamcheva
 * @author Lubomir Marinov
 * @author Adam Netocny
 * @author Eng Chong Meng
 */
class ResourceManagementServiceImpl internal constructor() : AbstractResourcesService(ResourceManagementActivator.bundleContext!!) {
    /**
     * UI Service reference.
     */
    private var uiService: UIService? = null

    /**
     * Initializes already registered default resource packs.
     */
    init {
        val serv = uIService
        serv?.repaintUI()
    }

    /**
     * Returns the `UIService` obtained from the bundle context.
     *
     * @return the `UIService` obtained from the bundle context
     */
    private val uIService: UIService?
        get() {
            if (uiService == null) {
                uiService = getService(ResourceManagementActivator.bundleContext, UIService::class.java)
            }
            return uiService
        }

    /**
     * Gets a reference to the `UIService` when this one is registered.
     *
     * @param event the `ServiceEvent` that has notified us
     */
    override fun serviceChanged(event: ServiceEvent) {
        super.serviceChanged(event)
        val sService = ResourceManagementActivator.bundleContext!!.getService(event.serviceReference)
        if (sService is UIService && uiService == null && event.type == ServiceEvent.REGISTERED) {
            uiService = sService
            uiService!!.repaintUI()
        } else if (sService is UIService
                && event.type == ServiceEvent.UNREGISTERING) {
            if (uiService != null && uiService == sService) {
                uiService = null
            }
        }
    }

    /**
     * Repaints the whole UI when a skin pack has changed.
     */
    override fun onSkinPackChanged() {
        val serv = uIService
        serv?.repaintUI()
    }

    /**
     * Returns the int representation of the color corresponding to the given key.
     *
     * @param key The key of the color in the colors properties file.
     * @return the int representation of the color corresponding to the given key.
     */
    override fun getColor(key: String): Int {
        val res = colorResources!![key]
        return if (res == null) {
            Timber.e("Missing color resource for key: %s", key)
            0xFFFFFF
        } else res.toInt(16)
    }

    /**
     * Returns the string representation of the color corresponding to the
     * given key.
     *
     * @param key The key of the color in the colors properties file.
     * @return the string representation of the color corresponding to the
     * given key.
     */
    override fun getColorString(key: String): String {
        val res = colorResources!![key]
        return if (res == null) {
            Timber.e("Missing color resource for key: %s", key)
            "0xFFFFFF"
        } else res
    }

    /**
     * Returns the `InputStream` of the image corresponding to the given path.
     *
     * @param path The path to the image file.
     * @return the `InputStream` of the image corresponding to the given path.
     */
    override fun getImageInputStreamForPath(path: String): InputStream? {
        val skinPack = skinPack
        if (skinPack != null) {
            if (skinPack.javaClass.classLoader!!.getResourceAsStream(path) != null) {
                return skinPack.javaClass.classLoader!!.getResourceAsStream(path)
            }
        }
        val imagePack = imagePack
        return if (path != null && imagePack != null) imagePack.javaClass.classLoader!!.getResourceAsStream(path) else null
    }

    /**
     * Returns the `InputStream` of the image corresponding to the given key.
     *
     * @param streamKey The identifier of the image in the resource properties file.
     * @return the `InputStream` of the image corresponding to the given key.
     */
    override fun getImageInputStream(streamKey: String): InputStream? {
        val path = getImagePath(streamKey)
        if (path == null || path.isEmpty()) {
            Timber.w("Missing resource for key: %s", streamKey)
            return null
        }
        return getImageInputStreamForPath(path)
    }

    /**
     * Returns the `URL` of the image corresponding to the given key.
     *
     * @param urlKey The identifier of the image in the resource properties file.
     * @return the `URL` of the image corresponding to the given key
     */
    override fun getImageURL(urlKey: String): URL? {
        val path = getImagePath(urlKey)
        if (path == null || path.isEmpty()) {
            Timber.i("Missing resource for key: %s", urlKey)
            return null
        }
        return getImageURLForPath(path)
    }

    /**
     * Returns the `URL` of the image corresponding to the given path.
     *
     * @param path The path to the given image file.
     * @return the `URL` of the image corresponding to the given path.
     */
    override fun getImageURLForPath(path: String?): URL {
        val skinPack = skinPack
        if (skinPack != null) {
            if (skinPack.javaClass.classLoader!!.getResource(path) != null) {
                return skinPack.javaClass.classLoader!!.getResource(path)
            }
        }
        val imagePack = imagePack
        return imagePack!!.javaClass.classLoader!!.getResource(path)
    }

    /**
     * Returns the `URL` of the sound corresponding to the given property key.
     *
     * @return the `URL` of the sound corresponding to the given property key.
     */
    override fun getSoundURL(urlKey: String): URL? {
        val path = getSoundPath(urlKey)
        if (path == null || path.isEmpty()) {
            Timber.w("Missing resource for key: %s", urlKey)
            return null
        }
        return getSoundURLForPath(path)
    }

    /**
     * Returns the `URL` of the sound corresponding to the given path.
     *
     * @param path the path, for which we're looking for a sound URL
     * @return the `URL` of the sound corresponding to the given path.
     */
    override fun getSoundURLForPath(path: String): URL {
        return soundPack!!.javaClass.classLoader!!.getResource(path)
    }

    /**
     * Loads an image from a given image identifier.
     *
     * @param imageID The identifier of the image.
     * @return The image for the given identifier.
     */
    override fun getImageInBytes(imageID: String): ByteArray? {
        val ins = getImageInputStream(imageID) ?: return null
        var image: ByteArray? = null
        try {
            image = ByteArray(ins.available())
            ins.read(image)
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
     * Builds a new skin bundle from the zip file content.
     *
     * @param zipFile Zip file with skin information.
     * @return `File` for the bundle.
     * @throws Exception When something goes wrong.
     */
    @Throws(Exception::class)
    override fun prepareSkinBundleFromZip(zipFile: File?): File {
        return SkinJarBuilder.createBundleFromZip(zipFile, imagePack!!)
    }

    /**
     * Gets the specified setting from the config service if present, otherwise
     * from the embedded resources (resources/config/defaults.properties).
     *
     * @param key The setting to lookup.
     * @return The setting for the key or `null` if not found.
     */
    override fun getSettingsString(key: String): String? {
        var configValue = ResourceManagementActivator.configService!!.getProperty(key)
        if (configValue == null) {
            configValue = super.getSettingsString(key)
        }
        return configValue?.toString()
    }
}