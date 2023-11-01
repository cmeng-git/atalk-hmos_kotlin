/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.device

import org.atalk.service.neomedia.MediaDirection
import org.atalk.service.neomedia.QualityPreset
import org.atalk.service.neomedia.RTPExtension
import org.atalk.service.neomedia.codec.EncodingConfiguration
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.util.MediaType

/**
 * The `MediaDevice` class represents capture and playback devices that can be used to grab
 * or render media. Sound cards, USB phones and webcams are examples of such media devices.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface MediaDevice {
    /**
     * Returns the `MediaDirection` supported by this device.
     *
     * @return `MediaDirection.SENDONLY` if this is a read-only device,
     * `MediaDirection.RECVONLY` if this is a write-only device and
     * `MediaDirection.SENDRECV` if this `MediaDevice` can both capture and render media.
     */
    val direction: MediaDirection

    /**
     * Returns the `MediaFormat` that this device is currently set to use when capturing data.
     *
     * @return the `MediaFormat` that this device is currently set to provide media in.
     */
    val format: MediaFormat?

    /**
     * Returns the `MediaType` that this device supports.
     *
     * @return `MediaType.AUDIO` if this is an audio device or `MediaType.VIDEO` in
     * case of a video device.
     */
    val mediaType: MediaType

    /**
     * Returns the `List` of `RTPExtension`s that this device know how to handle.
     *
     * @return the `List` of `RTPExtension`s that this device know how to handle or
     * `null` if the device does not support any RTP extensions.
     */
    val supportedExtensions: List<RTPExtension>?

    /**
     * Returns a list of `MediaFormat` instances representing the media formats supported by
     * this `MediaDevice`.
     *
     * @return the list of `MediaFormat`s supported by this device.
     */
    val supportedFormats: List<MediaFormat>

    /**
     * Returns a list of `MediaFormat` instances representing the media formats supported by
     * this `MediaDevice`.
     *
     * @param localPreset the preset used to set the send format parameters, used for video and settings.
     * @param remotePreset the preset used to set the receive format parameters, used for video and settings.
     * @return the list of `MediaFormat`s supported by this device.
     */
    fun getSupportedFormats(localPreset: QualityPreset?, remotePreset: QualityPreset?): List<MediaFormat>

    /**
     * Returns a list of `MediaFormat` instances representing the media formats supported by
     * this `MediaDevice` and enabled in `encodingConfiguration`.
     *
     * @param localPreset the preset used to set the send format parameters, used for video and settings.
     * @param remotePreset the preset used to set the receive format parameters, used for video and settings.
     * @param encodingConfiguration the `EncodingConfiguration` instance
     * to use.
     * @return the list of `MediaFormat`s supported by this device.
    `` */
    fun getSupportedFormats(localPreset: QualityPreset?,
            remotePreset: QualityPreset?, encodingConfiguration: EncodingConfiguration?): List<MediaFormat>
}