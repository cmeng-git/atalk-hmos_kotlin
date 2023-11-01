/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.recording

import org.atalk.util.MediaType
import org.json.JSONObject

/**
 * Represents an event related to media recording, such as a new SSRC starting to be recorded.
 *
 * @author Boris Grozev
 * @author Vladimir Marinov
 * @author Eng Chong Meng
 */
class RecorderEvent {
    /**
     * The type of this `RecorderEvent`.
     */
    var type = Type.OTHER

    /**
     * A timestamp for this `RecorderEvent`.
     */
    var instant: Long = -1

    /**
     * The SSRC associated with this `RecorderEvent`.
     */
    var ssrc: Long = -1

    /**
     * The SSRC of an audio stream associated with this `RecorderEvent`.
     */
    var audioSsrc: Long = -1

    /**
     * An RTP timestamp for this `RecorderEvent`.
     */
    var rtpTimestamp: Long = -1

    /**
     * An NTP timestamp (represented as a double in seconds) for this `RecorderEvent`.
     */
    private var ntpTime = -1.0

    /**
     * Duration associated with this `RecorderEvent`.
     */
    var duration: Long = -1

    /**
     * An aspect ratio associated with this `RecorderEvent`.
     */
    var aspectRatio = AspectRatio.ASPECT_RATIO_UNKNOWN

    /**
     * A file name associated with this `RecorderEvent`.
     */
    var filename: String? = null

    /**
     * The media type associated with this `RecorderEvent`.
     */
    var mediaType: MediaType? = null

    /**
     * The name of the participant associated with this `RecorderEvent`.
     */
    private var participantName: String? = null

    /**
     * A textual description of the participant associated with this `RecorderEvent`. (human readable)
     */
    private var participantDescription: String? = null
    var endpointId: String? = null
    private var disableOtherVideosOnTop = false

    /**
     * Constructs a `RecorderEvent`.
     */
    constructor() {}

    /**
     * Constructs a `RecorderEvent` and tries to parse its fields from `json`.
     *
     * @param json a JSON object, containing fields with which to initialize the fields of this `RecorderEvent`.
     */
    constructor(json: JSONObject) {
        type = Type.parseString(json.optString("type"))
        instant = json.optLong("instant", -1)
        ssrc = json.optLong("ssrc", -1)
        audioSsrc = json.optLong("audioSsrc", -1)
        ntpTime = json.optLong("ntpTime", -1).toDouble()
        duration = json.optLong("duration", -1)
        aspectRatio = AspectRatio.parseString(json.optString("aspectRatio"))
        filename = json.optString("filename", "null")
        participantName = json.optString("participantName", "null")
        participantDescription = json.optString("participantDescription", "null")
        endpointId = json.optString("endpointId", "null")
        mediaType = MediaType.parseString(json.optString("mediaType"))
        disableOtherVideosOnTop = json.optBoolean("disableOtherVideosOnTop")
    }

    override fun toString(): String {
        return "RecorderEvent: $type @$instant($mediaType)"
    }

    /**
     * `RecorderEvent` types.
     */
    enum class Type(val descriptor: String) {
        /**
         * Indicates the start of a recording.
         */
        RECORDING_STARTED("RECORDING_STARTED"),

        /**
         * Indicates the end of a recording.
         */
        RECORDING_ENDED("RECORDING_ENDED"),

        /**
         * Indicates that the active speaker has changed. The 'audioSsrc' field indicates the SSRC
         * of the audio stream which is now considered active, and the 'ssrc' field contains the
         * SSRC of a video stream associated with the now active audio stream.
         */
        SPEAKER_CHANGED("SPEAKER_CHANGED"),

        /**
         * Indicates that a new stream was added. This is different than RECORDING_STARTED, because
         * a new stream might be saved to an existing recording (for example, a new audio stream
         * might be added to a mix)
         */
        STREAM_ADDED("STREAM_ADDED"),

        /**
         * Default value.
         */
        OTHER("OTHER");

        override fun toString(): String {
            return descriptor
        }

        companion object {
            fun parseString(str: String): Type {
                return values().first  { it.descriptor == str }

                // for (type in values()) if (type.toString() == str) return type
                // return OTHER
            }
        }
    }

    /**
     * Video aspect ratio.
     */
    enum class AspectRatio(private val stringValue: String, var scaleFactor: Double) {
        ASPECT_RATIO_16_9("16_9", 16.0 / 9), ASPECT_RATIO_4_3("4_3", 4.0 / 3), ASPECT_RATIO_UNKNOWN("UNKNOWN", 1.0);

        override fun toString(): String {
            return stringValue
        }

        companion object {
            fun parseString(str: String): AspectRatio {
                for (aspectRatio in values()) if (aspectRatio.toString() == str) return aspectRatio
                return ASPECT_RATIO_UNKNOWN
            }
        }
    }
}