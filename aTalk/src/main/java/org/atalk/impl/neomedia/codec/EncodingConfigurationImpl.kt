/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec

import org.atalk.impl.neomedia.MediaUtils
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl
import org.atalk.service.neomedia.codec.Constants
import org.atalk.service.neomedia.codec.EncodingConfiguration
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.util.MediaType

/**
 * Configuration of encoding priorities.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
open class EncodingConfigurationImpl : EncodingConfiguration() {
    /**
     * Constructor. Loads the default preferences.
     */
    init {
        initializeFormatPreferences()
    }

    /**
     * Sets default format preferences; their priorities are in descending order of the number values;
     * Set the value to zero to disable the codec.
     */
    private fun initializeFormatPreferences() {
        // first init default preferences for video codecs; use Vp8 over VP9 (not very stable)
        setEncodingPreference("VP8", VideoMediaFormatImpl.DEFAULT_CLOCK_RATE, 1300)
        setEncodingPreference("VP9", VideoMediaFormatImpl.DEFAULT_CLOCK_RATE, 1200)
        setEncodingPreference("H264", VideoMediaFormatImpl.DEFAULT_CLOCK_RATE, 1100)
        setEncodingPreference("JPEG", VideoMediaFormatImpl.DEFAULT_CLOCK_RATE, 950)
        setEncodingPreference("H261", VideoMediaFormatImpl.DEFAULT_CLOCK_RATE, 800)

        // audio codecs
        setEncodingPreference("opus", 48000.0, 750)
        setEncodingPreference("SILK", 24000.0, 714)
        setEncodingPreference("SILK", 16000.0, 713)
        setEncodingPreference("G722", 8000.0 /* actually, 16 kHz */, 705)
        setEncodingPreference("speex", 32000.0, 701)
        setEncodingPreference("speex", 16000.0, 700)
        setEncodingPreference("PCMU", 8000.0, 650)
        setEncodingPreference("PCMA", 8000.0, 600)
        setEncodingPreference("iLBC", 8000.0, 500)
        setEncodingPreference("GSM", 8000.0, 450)
        setEncodingPreference("speex", 8000.0, 352)
        setEncodingPreference("G723", 8000.0, 150)
        setEncodingPreference("SILK", 12000.0, 0)
        setEncodingPreference("SILK", 8000.0, 0)
        setEncodingPreference("G729", 8000.0, 0 /* proprietary */)

        // enables by default telephone event(DTMF rfc4733), with lowest
        // priority as it is not needed to order it with audio codecs
        setEncodingPreference(Constants.TELEPHONE_EVENT, 8000.0, 1)
    }

    /**
     * Sets `pref` as the preference associated with the `encoding`. Use this method for
     * both audio and video encodings and don't worry if preferences are equal since we rarely need
     * to compare prefs of video encodings to those of audio encodings.
     *
     * @param encoding the SDP int of the encoding whose pref we're setting.
     * @param clockRate clock rate
     * @param pref a positive int indicating the preference for that encoding.
     */
    override fun setEncodingPreference(encoding: String, clockRate: Double, pref: Int) {
        var mediaFormat: MediaFormat? = null

        /*
         * The key in encodingPreferences associated with a MediaFormat is currently composed of
         * the encoding and the clockRate only so it makes sense to ignore the format parameters.
         */
        for (mf in MediaUtils.getMediaFormats(encoding)) {
            if (mf.clockRate == clockRate) {
                mediaFormat = mf
                break
            }
        }
        if (mediaFormat != null) {
            encodingPreferences[getEncodingPreferenceKey(mediaFormat)] = pref
        }
    }

    /**
     * Returns all the available encodings for a specific `MediaType`. This includes
     * disabled ones (ones with priority 0).
     *
     * @param type the `MediaType` we would like to know the available encodings of
     * @return array of `MediaFormat` supported for the `MediaType`
     */
    override fun getAllEncodings(type: MediaType): Array<MediaFormat> {
        return MediaUtils.getMediaFormats(type)
    }

    /**
     * Compares the two formats for order. Returns a negative integer, zero, or a positive integer
     * as the first format has been assigned a preference higher, equal to, or greater than the one
     * of the second.
     *
     * @param enc1 the first format to compare for preference.
     * @param enc2 the second format to compare for preference
     * @return a negative integer, zero, or a positive integer as the first format has been
     * assigned a preference higher, equal to, or greater than the one of the second
     */
    override fun compareEncodingPreferences(enc1: MediaFormat, enc2: MediaFormat): Int {
        var res = getPriority(enc2) - getPriority(enc1)

        /*
         * If the encodings are with same priority, compare them by name. If we return equals,
         * TreeSet will not add equal encodings.
         */
        if (res == 0) {
            res = enc1.encoding.compareTo(enc2.encoding, ignoreCase = true)
            /*
             * There are formats with one and same encoding (name) but different clock rates.
             */
            if (res == 0) {
                res = enc2.clockRate.compareTo(enc1.clockRate)
                /*
                 * Then again, there are formats (e.g. H.264) with the same encoding name and
                 * clock rate but different format parameters (e.g. packetization-mode).
                 */
                if (res == 0) {
                    // Try to preserve the order specified by MediaUtils.
                    var index1: Int
                    var index2 = 0
                    if (MediaUtils.getMediaFormatIndex(enc1).also { index1 = it } != -1 && MediaUtils.getMediaFormatIndex(enc2).also { index2 = it } != -1) {
                        res = index1 - index2
                    }
                    if (res == 0) {
                        // * The format with more parameters will be considered here to be the format
                        // * with higher priority.
                        val fmtps1 = enc1.formatParameters
                        val fmtps2 = enc2.formatParameters
                        val fmtpCount1 = fmtps1.size
                        val fmtpCount2 = fmtps2.size

                        /*
                         * TODO Even if the number of format parameters is equal, the two formats
                         * may still be different. Consider ordering by the values of the format
                         * parameters as well.
                         */
                        res = fmtpCount2 - fmtpCount1
                    }
                }
            }
        }
        return res
    }
}