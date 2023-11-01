/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.resources

import java.io.File
import java.io.InputStream
import java.net.URL
import java.util.*
import javax.swing.ImageIcon

/**
 * The Resource Management Service gives easy access to common resources for the application
 * including texts, images, sounds and some configurations.
 *
 * @author Damian Minkov
 * @author Adam Netocny
 * @author Eng Chong Meng
 */
interface ResourceManagementService {
    // Color pack methods
    /**
     * Returns the int representation of the color corresponding to the given key.
     *
     * @param key The key of the color in the colors properties file.
     * @return the int representation of the color corresponding to the given key.
     */
    fun getColor(key: String): Int

    /**
     * Returns the string representation of the color corresponding to the given key.
     *
     * @param key The key of the color in the colors properties file.
     * @return the string representation of the color corresponding to the given key.
     */
    fun getColorString(key: String): String?

    /**
     * Returns the `InputStream` of the image corresponding to the given path.
     *
     * @param path The path to the image file.
     * @return the `InputStream` of the image corresponding to the given path.
     */
    fun getImageInputStreamForPath(path: String): InputStream?

    /**
     * Returns the `InputStream` of the image corresponding to the given key.
     *
     * @param streamKey The identifier of the image in the resource properties file.
     * @return the `InputStream` of the image corresponding to the given key.
     */
    fun getImageInputStream(streamKey: String): InputStream?

    /**
     * Returns the `URL` of the image corresponding to the given key.
     *
     * @param urlKey The identifier of the image in the resource properties file.
     * @return the `URL` of the image corresponding to the given key
     */
    fun getImageURL(urlKey: String): URL?

    /**
     * Returns the `URL` of the image corresponding to the given path.
     *
     * @param path The path to the given image file.
     * @return the `URL` of the image corresponding to the given path.
     */
    fun getImageURLForPath(path: String?): URL?

    /**
     * Returns the image path corresponding to the given key.
     *
     * @param key The identifier of the image in the resource properties file.
     * @return the image path corresponding to the given key.
     */
    fun getImagePath(key: String): String?

    /**
     * All the locales in the language pack.
     *
     * @return all the locales this Language pack contains.
     */
    val availableLocales: Iterator<Locale?>?

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @return An internationalized string corresponding to the given key.
     */
    fun getI18NString(key: String): String?

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @param locale The locale.
     * @return An internationalized string corresponding to the given key and given locale.
     */
    fun getI18NString(key: String, locale: Locale): String?

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @param params An array of parameters to be replaced in the returned string.
     * @return An internationalized string corresponding to the given key and given locale.
     */
    fun getI18NString(key: String, params: Array<String>?): String?

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @param params An array of parameters to be replaced in the returned string.
     * @param locale The locale.
     * @return An internationalized string corresponding to the given key.
     */
    fun getI18NString(key: String, params: Array<String>?, locale: Locale?): String?

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @return An internationalized string corresponding to the given key.
     */
    fun getI18nMnemonic(key: String): Char

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The key of the string.
     * @param locale The locale.
     * @return An internationalized string corresponding to the given key.
     */
    fun getI18nMnemonic(key: String, locale: Locale?): Char
    // Settings pack methods
    /**
     * Returns an url for the setting corresponding to the given key. Used when the setting is an
     * actual file.
     *
     * @param urlKey The key of the setting.
     * @return Url to the corresponding resource.
     */
    fun getSettingsURL(urlKey: String): URL?

    /**
     * Returns an InputStream for the setting corresponding to the given key. Used when the
     * setting is an actual file.
     *
     * @param streamKey The key of the setting.
     * @return InputStream to the corresponding resource.
     */
    fun getSettingsInputStream(streamKey: String): InputStream?

    /**
     * Returns a stream from a given identifier, obtained through the class loader of the given
     * resourceClass.
     *
     * @param streamKey The identifier of the stream.
     * @param resourceClass the resource class through which the resource would be obtained
     * @return The stream for the given identifier.
     */
    fun getSettingsInputStream(streamKey: String, resourceClass: Class<*>?): InputStream?

    /**
     * Returns the int value of the corresponding configuration key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @return the int value of the corresponding configuration key.
     */
    fun getSettingsString(key: String): String?

    /**
     * Returns the int value of the corresponding configuration key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @return the int value of the corresponding configuration key.
     */
    fun getSettingsInt(key: String): Int
    // Sound pack methods
    /**
     * Returns an url for the sound resource corresponding to the given key.
     *
     * @param urlKey The key of the setting.
     * @return Url to the corresponding resource.
     */
    fun getSoundURL(urlKey: String): URL?

    /**
     * Returns an url for the sound resource corresponding to the given path.
     *
     * @param path The path to the sound resource.
     * @return Url to the corresponding resource.
     */
    fun getSoundURLForPath(path: String): URL?

    /**
     * Returns the path of the sound corresponding to the given property key.
     *
     * @param soundKey The key of the sound.
     * @return the path of the sound corresponding to the given property key.
     */
    fun getSoundPath(soundKey: String): String?

    /**
     * Constructs an `ImageIcon` from the specified image ID and returns it.
     *
     * @param imageID The identifier of the image.
     * @return An `ImageIcon` containing the image with the given identifier.
     */
    fun getImage(imageID: String): ImageIcon?

    /**
     * Loads the image with the specified ID and returns a byte array containing it.
     *
     * @param imageID The identifier of the image.
     * @return A byte array containing the image with the given identifier.
     */
    fun getImageInBytes(imageID: String): ByteArray?

    /**
     * Builds a new skin bundle from the zip file content.
     *
     * @param zipFile Zip file with skin information.
     * @return `File` for the bundle.
     * @throws Exception When something goes wrong.
     */
    @Throws(Exception::class)
    fun prepareSkinBundleFromZip(zipFile: File?): File?

    companion object {
        // Language pack methods
        /**
         * Default Locale config string.
         */
        const val DEFAULT_LOCALE_CONFIG = "resources.DefaultLocale"
    }
}