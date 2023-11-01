/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.ProtocolIcon
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.*

/**
 * Represents the Jabber protocol icon. Implements the `ProtocolIcon` interface in order to
 * provide a Jabber icon image in two different sizes.
 *
 * @author Yana Stamcheva
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
class ProtocolIconJabberImpl(
        /**
         * The path where all protocol icons are placed.
         */
        private val iconPath: String) : ProtocolIcon {

    /**
     * A hash table containing the protocol icon in different sizes.
     */
    private val iconsTable = Hashtable<String?, ByteArray?>()

    /**
     * A hash table containing the path to the protocol icon in different sizes.
     */
    private val iconPathsTable = Hashtable<String?, String>()

    /**
     * Creates an instance of this class by passing to it the path, where all protocol icons are placed.
     *
     * iconPath the protocol icon path
     */
    init {
        iconsTable[ProtocolIcon.ICON_SIZE_16x16] = loadIcon("$iconPath/status16x16-online.png")
        iconsTable[ProtocolIcon.ICON_SIZE_32x32] = loadIcon("$iconPath/logo32x32.png")
        iconsTable[ProtocolIcon.ICON_SIZE_48x48] = loadIcon("$iconPath/logo48x48.png")
        iconPathsTable[ProtocolIcon.ICON_SIZE_16x16] = "$iconPath/status16x16-online.png"
        iconPathsTable[ProtocolIcon.ICON_SIZE_32x32] = "$iconPath/logo32x32.png"
        iconPathsTable[ProtocolIcon.ICON_SIZE_48x48] = "$iconPath/logo48x48.png"
    }

    /**
     * Implements the `ProtocolIcon.getSupportedSizes()` method. Returns an iterator to a set
     * containing the supported icon sizes.
     *
     * @return an iterator to a set containing the supported icon sizes
     */
    override fun getSupportedSizes(): Iterator<String?> {
        return iconsTable.keys.iterator()
    }

    /**
     * Returns TRUE if a icon with the given size is supported, FALSE-otherwise.
     *
     * @return TRUE if a icon with the given size is supported, FALSE-otherwise.
     */
    override fun isSizeSupported(iconSize: String?): Boolean {
        return iconsTable.containsKey(iconSize)
    }

    /**
     * Returns the icon image in the given size.
     *
     * @param iconSize the icon size; one of ICON_SIZE_XXX constants
     */
    override fun getIcon(iconSize: String?): ByteArray? {
        return iconsTable[iconSize]
    }

    /**
     * Returns a path to the icon with the given size.
     *
     * @param iconSize the size of the icon we're looking for
     * @return the path to the icon with the given size
     */
    override fun getIconPath(iconSize: String?): String? {
        return iconPathsTable[iconSize]
    }

    /**
     * Returns the icon image used to represent the protocol connecting state.
     *
     * @return the icon image used to represent the protocol connecting state
     */
    override fun getConnectingIcon(): ByteArray? {
        return loadIcon("$iconPath/status16x16-connecting.gif")
    }

    companion object {
        private var resourcesService: ResourceManagementService? = null

        /**
         * Loads an image from a given image path.
         *
         * @param imagePath The identifier of the image.
         * @return The image for the given identifier.
         */
        fun loadIcon(imagePath: String): ByteArray? {
            var `is`: InputStream? = null
            try {
                // try to load path it maybe valid url
                `is` = URL(imagePath).openStream()
            } catch (ignore: Exception) {
            }
            if (`is` == null) `is` = resources!!.getImageInputStreamForPath(imagePath)
            if (`is` == null) return ByteArray(0)
            var icon: ByteArray? = null
            try {
                icon = ByteArray(`is`.available())
                `is`.read(icon)
            } catch (e: IOException) {
                Timber.e(e, "Failed to load icon: %s", imagePath)
            }
            return icon
        }

        /**
         * Get the `ResourceMaangementService` registered.
         *
         * @return `ResourceManagementService` registered
         */
        val resources: ResourceManagementService?
            get() {
                if (resourcesService == null) {
                    val serviceReference = JabberActivator.bundleContext.getServiceReference(ResourceManagementService::class.java.name)
                            ?: return null
                    resourcesService = JabberActivator.bundleContext.getService<Any>(serviceReference as ServiceReference<Any>) as ResourceManagementService
                }
                return resourcesService
            }
    }
}