/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.codec

import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.service.neomedia.format.MediaFormatFactory
import org.atalk.util.MediaType
import timber.log.Timber
import java.util.*

/**
 * A base class manages encoding configurations. It holds information about supported formats.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
abstract class EncodingConfiguration {
    /**
     * The `Comparator` which sorts the sets according to the settings in `encodingPreferences`.
     */
    private val encodingComparator = Comparator { enc1: MediaFormat, enc2: MediaFormat -> compareEncodingPreferences(enc1, enc2) }

    /**
     * That's where we keep format preferences matching SDP formats to integers. We keep preferences
     * for both audio and video formats here in case we'd ever need to compare them to one another.
     * In most cases however both would be de-correlated and other components (such as the UI)
     * should present them separately.
     */
    val encodingPreferences = HashMap<String, Int>()

    /**
     * The cache of supported `AudioMediaFormat`s ordered by decreasing priority.
     */
    private var supportedAudioEncodings: Set<MediaFormat?>? = null

    /**
     * The cache of supported `VideoMediaFormat`s ordered by decreasing priority.
     */
    private var supportedVideoEncodings: Set<MediaFormat?>? = null

    /**
     * Updates the codecs in the supported sets according to the preferences in encodingPreferences.
     * If the preference value is `0`, the codec is disabled.
     */
    private fun updateSupportedEncodings() {
        /*
         * If they need updating, their caches are invalid and need rebuilding next time they are requested.
         */
        supportedAudioEncodings = null
        supportedVideoEncodings = null
    }

    /**
     * Gets the `Set` of enabled available `MediaFormat`s with the specified
     * `MediaType` sorted in decreasing priority.
     *
     * @param type the `MediaType` of the `MediaFormat`s to get
     * @return a `Set` of enabled available `MediaFormat`s with the specified `MediaType` sorted in decreasing priority
     */
    private fun updateSupportedEncodings(type: MediaType): Set<MediaFormat?> {
        val enabled = TreeSet(encodingComparator)
        for (format in getAllEncodings(type)) {
            if (getPriority(format) > 0) enabled.add(format)
        }
        return enabled
    }

    /**
     * Sets `pref` as the preference associated with `encoding`. Use this method for
     * both audio and video encodings and don't worry if preferences are equal since we rarely need
     * to compare prefs of video encodings to those of audio encodings.
     *
     * @param encoding the SDP int of the encoding whose pref we're setting.
     * @param clockRate clock rate
     * @param pref a positive int indicating the preference for that encoding.
     */
    protected abstract fun setEncodingPreference(encoding: String, clockRate: Double, pref: Int)

    /**
     * Sets `priority` as the preference associated with `encoding`. Use this method
     * for both audio and video encodings and don't worry if the preferences are equal since we
     * rarely need to compare the preferences of video encodings to those of audio encodings.
     *
     * @param encoding the `MediaFormat` specifying the encoding to set the priority of
     * @param priority a positive `int` indicating the priority of `encoding` to set
     */
    open fun setPriority(encoding: MediaFormat, priority: Int) {
        val encodingEncoding = encoding.encoding

        /*
         * Since we'll remember the priority in the ConfigurationService by associating it
         * with a property name/key based on encoding and clock rate only, it does not make sense to
         * store the MediaFormat in encodingPreferences because MediaFormat is much more specific
         * than just encoding and clock rate.
         */
        setEncodingPreference(encodingEncoding, encoding.clockRate, priority)
        updateSupportedEncodings()
    }

    /**
     * Get the priority for a `MediaFormat`.
     *
     * @param encoding the `MediaFormat`
     * @return the priority
     */
    fun getPriority(encoding: MediaFormat): Int {
        /*
         * Directly returning encodingPreference.get(encoding) will throw a NullPointerException if
         * encodingPreferences does not contain a mapping for encoding.
         */
        val priority = encodingPreferences[getEncodingPreferenceKey(encoding)]
        return priority ?: 0
    }

    /**
     * Returns all the available encodings for a specific `MediaType`. This includes disabled
     * ones (ones with priority 0).
     *
     * @param type the `MediaType` we would like to know the available encodings of
     * @return array of `MediaFormat` supported for the `MediaType`
     */
    abstract fun getAllEncodings(type: MediaType): Array<MediaFormat>

    /**
     * Returns the supported `MediaFormat`s i.e. the enabled available `MediaFormat`s,
     * sorted in decreasing priority. Returns only the formats of type `type`.
     *
     * @param type the `MediaType` of the supported `MediaFormat`s to get
     * @return an array of the supported `MediaFormat`s i.e. the enabled available
     * `MediaFormat`s sorted in decreasing priority. Returns only the formats of type
     * `type`.
     */
    fun getEnabledEncodings(type: MediaType?): Array<MediaFormat?> {
        val supportedEncodings: Set<MediaFormat?>?
        when (type) {
            MediaType.AUDIO -> {
                if (supportedAudioEncodings == null) supportedAudioEncodings = updateSupportedEncodings(type)
                supportedEncodings = supportedAudioEncodings
            }
            MediaType.VIDEO -> {
                if (supportedVideoEncodings == null) supportedVideoEncodings = updateSupportedEncodings(type)
                supportedEncodings = supportedVideoEncodings
            }
            else -> return arrayOfNulls(0)
        }
        return supportedEncodings!!.toTypedArray()
    }

    /**
     * Compares the two formats for order. Returns a negative integer, zero, or a positive integer
     * as the first format has been assigned a preference higher, equal to, or greater than the one
     * of the second.
     *
     * @param enc1 the first format to compare for preference.
     * @param enc2 the second format to compare for preference
     * @return a negative integer, zero, or a positive integer as the first format has been assigned
     * a preference higher, equal to, or greater than the one of the second
     */
    protected abstract fun compareEncodingPreferences(enc1: MediaFormat, enc2: MediaFormat): Int

    /**
     * Gets the key in [.encodingPreferences] which is associated with the priority of a
     * specific `MediaFormat`.
     *
     * @param encoding the `MediaFormat` to get the key in [.encodingPreferences] of
     * @return the key in [.encodingPreferences] which is associated with the priority of the
     * specified `encoding`
     */
    fun getEncodingPreferenceKey(encoding: MediaFormat): String {
        return encoding.encoding + "/" + encoding.clockRateString
    }
    /**
     * Stores the format preferences in this instance in the given `Map`, using
     * `prefix` as a prefix to the key. Entries in the format (prefix+formatName,
     * formatPriority) will be added to `properties`, one for each available format. Note
     * that a "." is not automatically added to `prefix`.
     */
    /**
     * Stores the format preferences in this instance in the given `Map`. Entries in the
     * format (formatName, formatPriority) will be added to `properties`, one for each
     * available format.
     *
     * @param properties The `Map` where entries will be added.
     * @param prefix The prefix to use.
     */
    @JvmOverloads
    fun storeProperties(properties: MutableMap<String, String>, prefix: String = "") {
        for (mediaType in MediaType.values()) {
            for (mediaFormat in getAllEncodings(mediaType)) {
                properties[prefix + getEncodingPreferenceKey(mediaFormat)] = getPriority(mediaFormat).toString()
            }
        }
    }
    /**
     * Parses a `Map<String></String>, String>` and updates the format preferences according to it. For
     * each entry, if it's key does not begin with `prefix`, its ignored. If the key begins with
     * `prefix`, look for an encoding name after the last ".", and interpret the key value as preference.
     */
    /**
     * Parses a `Map<String></String>, String>` and updates the format preferences according to it.
     * Does not use a prefix.
     *
     * @param properties The `Map` to parse.
     * @param prefix The prefix to use.
     * @see EncodingConfiguration.loadProperties
     */
    @JvmOverloads
    fun loadProperties(properties: Map<String, String?>, prefix: String? = "") {
        for ((pName, prefStr) in properties) {
            var fmtName: String
            if (!pName.startsWith(prefix!!)) continue
            fmtName = if (pName.contains(".")) pName.substring(pName.lastIndexOf('.') + 1) else pName

            // legacy
            if (fmtName.contains("sdp")) {
                fmtName = fmtName.replace("sdp".toRegex(), "")
                /*
                 * If the current version of the property name is also associated with a value,
                 * ignore the value for the legacy one.
                 */
                if (properties.containsKey(pName.replace("sdp".toRegex(), ""))) continue
            }
            var preference: Int
            var encoding: String
            var clockRate: Double
            try {
                preference = prefStr!!.toInt()
                val encodingClockRateSeparator = fmtName.lastIndexOf('/')
                if (encodingClockRateSeparator > -1) {
                    encoding = fmtName.substring(0, encodingClockRateSeparator)
                    clockRate = fmtName.substring(encodingClockRateSeparator + 1).toDouble()
                }
                else {
                    encoding = fmtName
                    clockRate = MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED
                }
            } catch (nfe: NumberFormatException) {
                Timber.w(nfe, "Failed to parse format (%s) of preference (%s).", fmtName, prefStr)
                continue
            }
            setEncodingPreference(encoding, clockRate, preference)
        }

        // now update the arrays so that they are returned by order of preference.
        updateSupportedEncodings()
    }

    /**
     * Load the preferences stored in `encodingConfiguration`
     *
     * @param encodingConfiguration the `EncodingConfiguration` to load preferences from.
     */
    fun loadEncodingConfiguration(encodingConfiguration: EncodingConfiguration) {
        val properties = HashMap<String, String>()
        encodingConfiguration.storeProperties(properties)
        loadProperties(properties)
    }

    /**
     * Returns `true` if there is at least one enabled format for media type `type`.
     *
     * @param mediaType The media type, MediaType.AUDIO or MediaType.VIDEO
     * @return `true` if there is at least one enabled format for media type `type`.
     */
    fun hasEnabledFormat(mediaType: MediaType?): Boolean {
        return getEnabledEncodings(mediaType).isNotEmpty()
    }
}