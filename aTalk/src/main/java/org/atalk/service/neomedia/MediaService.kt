/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import org.atalk.service.neomedia.codec.EncodingConfiguration
import org.atalk.service.neomedia.device.MediaDevice
import org.atalk.service.neomedia.device.ScreenDevice
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.service.neomedia.format.MediaFormatFactory
import org.atalk.service.neomedia.recording.Recorder
import org.atalk.service.neomedia.recording.RecorderEventHandler
import org.atalk.util.MediaType
import java.awt.Point
import java.beans.PropertyChangeListener
import java.io.IOException

/**
 * The `MediaService` service is meant to be a wrapper of media libraries such as JMF, FMJ,
 * FFMPEG, and/or others. It takes care of all media play and capture as well as media transport
 * (e.g. over RTP).
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author MilanKral
 * @author Eng Chong Meng
 */
interface MediaService {
    /**
     * Adds a `PropertyChangeListener` to be notified about changes in the values of the
     * properties of this instance.
     *
     * @param listener the `PropertyChangeListener` to be notified about changes in the values of the
     * properties of this instance
     */
    fun addPropertyChangeListener(listener: PropertyChangeListener)

    /**
     * Those interested in Recorder events add listener through MediaService. This way they don't
     * need to have access to the Recorder instance. Adds a new `Recorder.Listener` to the
     * list of listeners interested in notifications from a `Recorder`.
     *
     * @param listener the new `Recorder.Listener` to be added to the list of listeners interested in
     * notifications from `Recorder`s.
     */
    fun addRecorderListener(listener: Recorder.Listener)

    /**
     * Returns a new `EncodingConfiguration` instance.
     *
     * @return a new `EncodingConfiguration` instance.
     */
    fun createEmptyEncodingConfiguration(): EncodingConfiguration?

    /**
     * Create a `MediaStream` which will use a specific `MediaDevice` for capture and
     * playback of media. The new instance will not have a `StreamConnector` at the time of
     * its construction and a `StreamConnector` will be specified later on in order to enable
     * the new instance to send and receive media.
     *
     * @param device the `MediaDevice` to be used by the new instance for capture and playback of media
     * @return a newly-created `MediaStream` which will use the specified `device` for
     * capture and playback of media
     */
    fun createMediaStream(device: MediaDevice): MediaStream?

    /**
     * Initializes a new `MediaStream` of a specific `MediaType`. The new instance
     * will not have a `MediaDevice` at the time of its initialization and a
     * `MediaDevice` may be specified later on with the constraint that
     * MediaDevice.getMediaType equals `mediaType`.
     *
     * @param mediaType the `MediaType` of the new instance to be initialized
     * @return a new `MediaStream` instance of the specified `mediaType`
     */
    fun createMediaStream(mediaType: MediaType): MediaStream?

    /**
     * Creates a `MediaStream` that will be using the specified `MediaDevice` for both
     * capture and playback of media exchanged via the specified `StreamConnector`.
     *
     * @param connector the `StreamConnector` the stream should use for sending and receiving media or
     * `null` if the stream is to not have a `StreamConnector` configured at
     * initialization time and a `StreamConnector` is to be specified later on
     * @param device the device to be used for both capture and playback of media exchanged via the
     * specified `StreamConnector`
     * @return the newly created `MediaStream`.
     */
    fun createMediaStream(connector: StreamConnector?, device: MediaDevice?): MediaStream?

    /**
     * Initializes a new `MediaStream` instance which is to exchange media of a specific
     * `MediaType` via a specific `StreamConnector`.
     *
     * @param connector the `StreamConnector` the stream should use for sending and receiving media or
     * `null` if the stream is to not have a `StreamConnector` configured at
     * initialization time and a `StreamConnector` is to be specified later on
     * @param mediaType the `MediaType` of the media to be exchanged by the new instance via the
     * specified `connector`
     * @return a new `MediaStream` instance which is to exchange media of the specified
     * `mediaType` via the specified `connector`
     */
    fun createMediaStream(connector: StreamConnector?, mediaType: MediaType?): MediaStream?

    /**
     * Creates a `MediaStream` that will be using the specified `MediaDevice` for both
     * capture and playback of media exchanged via the specified `StreamConnector`.
     *
     * @param connector the `StreamConnector` the stream should use for sending and receiving media or
     * `null` if the stream is to not have a `StreamConnector` configured at
     * initialization time and a `StreamConnector` is to be specified later on
     * @param device the device to be used for both capture and playback of media exchanged via the
     * specified `StreamConnector`
     * @param srtpControl a control which is already created, used to control the ZRTP operations.
     * @return the newly created `MediaStream`.
     */
    fun createMediaStream(connector: StreamConnector?, device: MediaDevice?,
            srtpControl: SrtpControl?): MediaStream?

    /**
     * Initializes a new `MediaStream` instance which is to exchange media of a specific
     * `MediaType` via a specific `StreamConnector`. The security of the media
     * exchange is to be controlled by a specific `SrtpControl`.
     *
     * @param connector the `StreamConnector` the stream should use for sending and receiving media or
     * `null` if the stream is to not have a `StreamConnector` configured at
     * initialization time and a `StreamConnector` is to be specified later on
     * @param mediaType the `MediaType` of the media to be exchanged by the new instance via the
     * specified `connector`
     * @param srtpControl the `SrtpControl` to control the security of the media exchange
     * @return a new `MediaStream` instance which is to exchange media of the specified
     * `mediaType` via the specified `connector`
     */
    fun createMediaStream(connector: StreamConnector?, mediaType: MediaType?,
            srtpControl: SrtpControl?): MediaStream?

    /**
     * Creates a new `MediaDevice` which uses a specific `MediaDevice` to capture and
     * play back media and performs mixing of the captured media and the media played back by any
     * other users of the returned `MediaDevice`. For the `AUDIO` `MediaType`,
     * the returned device is commonly referred to as an audio mixer. The `MediaType` of the
     * returned `MediaDevice` is the same as the `MediaType` of the specified `device`.
     *
     * @param device the `MediaDevice` which is to be used by the returned `MediaDevice` to
     * actually capture and play back media
     * @return a new `MediaDevice` instance which uses `device` to capture and play
     * back media and performs mixing of the captured media and the media played back by any
     * other users of the returned `MediaDevice` instance
     */
    fun createMixer(device: MediaDevice): MediaDevice?

    /**
     * Creates a new `Recorder` instance that can be used to record a call which captures and
     * plays back media using a specific `MediaDevice`.
     *
     * @param device the `MediaDevice` which is used for media capture and playback by the call to
     * be recorded
     * @return a new `Recorder` instance that can be used to record a call which captures and
     * plays back media using the specified `MediaDevice`
     */
    fun createRecorder(device: MediaDevice): Recorder?

    /**
     * Creates a new `Recorder` instance that can be used to record media from a specific
     * `RTPTranslator`.
     *
     * @param translator the `RTPTranslator` for which to create a `Recorder`
     * @return a new `Recorder` instance that can be used to record media from a specific
     * `RTPTranslator`.
     */
    fun createRecorder(translator: RTPTranslator): Recorder?

    /**
     * Initializes a new `RTPTranslator` which is to forward RTP and RTCP traffic between
     * multiple `MediaStream`s.
     *
     * @return a new `RTPTranslator` which is to forward RTP and RTCP traffic between
     * multiple `MediaStream`s
     */
    fun createRTPTranslator(): RTPTranslator?

    /**
     * Initializes a new `SrtpControl` instance with a specific `SrtpControlType`.
     *
     * @param srtpControlType the `SrtpControlType` of the new instance
     * @param myZid ZRTP seed value
     * @return a new `SrtpControl` instance with the specified `srtpControlType`
     */
    fun createSrtpControl(srtpControlType: SrtpControlType, myZid: ByteArray?): SrtpControl?

    /**
     * Get available `ScreenDevice`s.
     *
     * @return screens
     */
    val availableScreenDevices: List<ScreenDevice?>?

    /**
     * Returns the current `EncodingConfiguration` instance.
     *
     * @return the current `EncodingConfiguration` instance.
     */
    val currentEncodingConfiguration: EncodingConfiguration?

    /**
     * Returns the default `MediaDevice` for the specified media `type`.
     *
     * @param mediaType a `MediaType` value indicating the kind of device that we are trying to obtain.
     * @param useCase `MediaUseCase` value indicating for the use-case of device that we are trying
     * to obtain.
     * @return the currently default `MediaDevice` for the specified `MediaType`, or
     * `null` if no such device exists.
     */
    fun getDefaultDevice(mediaType: MediaType, useCase: MediaUseCase): MediaDevice?

    /**
     * Get default `ScreenDevice` device.
     *
     * @return default screen device
     */
    val defaultScreenDevice: ScreenDevice?

    /**
     * Returns a list containing all devices known to this service implementation and handling the
     * specified `MediaType`.
     *
     * @param mediaType the media type (i.e. AUDIO or VIDEO) that we'd like to obtain the device list for.
     * @param useCase `MediaUseCase` value indicating for the use-case of device that we are trying
     * to obtain.
     * @return the list of `MediaDevice`s currently known to handle the specified
     * `mediaType`.
     */
    fun getDevices(mediaType: MediaType, useCase: MediaUseCase): List<MediaDevice>?

    /**
     * Returns a [Map] that binds indicates whatever preferences the media service
     * implementation may have for the RTP payload type numbers that get dynamically assigned to
     * [MediaFormat]s with no static payload type. The method is useful for formats such as
     * "telephone-event" for example that is statically assigned the 101 payload type by some legacy
     * systems. Signalling protocol implementations such as SIP and XMPP should make sure that,
     * whenever this is possible, they assign to formats the dynamic payload type returned in this
     * [Map].
     *
     * @return a [Map] binding some formats to a preferred dynamic RTP payload type number.
     */
    val dynamicPayloadTypePreferences: MutableMap<MediaFormat, Byte>

    /**
     * Gets the `MediaFormatFactory` through which `MediaFormat` instances may be
     * created for the purposes of working with the `MediaStream`s created by this
     * `MediaService`.
     *
     * @return the `MediaFormatFactory` through which `MediaFormat` instances may be
     * created for the purposes of working with the `MediaStream`s created by this
     * `MediaService`
     */
    val formatFactory: MediaFormatFactory?

    /**
     * Gets the `VolumeControl` which controls the volume level of audio input/capture.
     *
     * @return the `VolumeControl` which controls the volume level of audio input/capture
     */
    val inputVolumeControl: VolumeControl?

    /**
     * Get a `MediaDevice` for a part of desktop streaming/sharing.
     *
     * @param width width of the part
     * @param height height of the part
     * @param x origin of the x coordinate (relative to the full desktop)
     * @param y origin of the y coordinate (relative to the full desktop)
     * @return `MediaDevice` representing the part of desktop or null if problem
     */
    fun getMediaDeviceForPartialDesktopStreaming(width: Int, height: Int, x: Int, y: Int): MediaDevice?

    /**
     * Get origin for desktop streaming device.
     *
     * @param mediaDevice media device
     * @return origin
     */
    fun getOriginForDesktopStreamingDevice(mediaDevice: MediaDevice): Point?

    /**
     * Gets the `VolumeControl` which controls the volume level of audio output/playback.
     *
     * @return the `VolumeControl` which controls the volume level of audio output/playback
     */
    val outputVolumeControl: VolumeControl?

    /**
     * Gives access to currently registered `Recorder.Listener`s.
     *
     * @return currently registered `Recorder.Listener`s.
     */
    fun getRecorderListeners(): Iterator<Recorder.Listener>

    /**
     * Creates a preview component for the specified device(video device) used to show video preview
     * from it.
     *
     * @param device the video device
     * @param preferredWidth the width we prefer for the component
     * @param preferredHeight the height we prefer for the component
     * @return the preview component.
     */
    fun getVideoPreviewComponent(device: MediaDevice?, preferredWidth: Int, preferredHeight: Int): Any?

    /**
     * If the `MediaDevice` corresponds to partial desktop streaming device.
     *
     * @param mediaDevice `MediaDevice`
     * @return true if `MediaDevice` is a partial desktop streaming device, false otherwise
     */
    fun isPartialStreaming(mediaDevice: MediaDevice?): Boolean

    /**
     * Removes a `PropertyChangeListener` to no longer be notified about changes in the
     * values of the properties of this instance.
     *
     * @param listener the `PropertyChangeListener` to no longer be notified about changes in the
     * values of the properties of this instance
     */
    fun removePropertyChangeListener(listener: PropertyChangeListener)

    /**
     * Removes an existing `Recorder.Listener` from the list of listeners interested in
     * notifications from `Recorder`s.
     *
     * @param listener the existing `Listener` to be removed from the list of listeners interested in
     * notifications from `Recorder`s
     */
    fun removeRecorderListener(listener: Recorder.Listener)

    /**
     * Returns the value which will be used for the canonical end-point identifier (CNAME) in RTCP
     * packets sent by this running instance of libjitsi.
     *
     * @return the value which will be used for the canonical end-point identifier (CNAME) in RTCP
     * packets sent by this running instance of libjitsi.
     */
    val rtpCname: String?

    /**
     * Creates a `RecorderEventHandler` instance that saves received events in JSON format.
     *
     * @param filename the filename into which the created `RecorderEventHandler` will save received
     * events.
     * @return a `RecorderEventHandler` instance that saves received events in JSON format.
     * @throws IOException if a `RecorderEventHandler` could not be created for `filename`.
     */
    @Throws(IOException::class)
    fun createRecorderEventHandlerJson(filename: String?): RecorderEventHandler?

    companion object {
        /**
         * The name of the property of `MediaService` the value of which corresponds to the value
         * returned by [.getDefaultDevice]. The `oldValue` and the
         * `newValue` of the fired `PropertyChangeEvent` are not to be relied on and
         * instead a call to `getDefaultDevice` is to be performed to retrieve the new value.
         */
        const val DEFAULT_DEVICE = "defaultDevice"

        /**
         * The name of the property which controls whether the libjitsi codecs
         * which depend on ffmpeg (currently mp3, h264 and amrwb) will be enabled.
         */
        const val ENABLE_FFMPEG_CODECS_PNAME = "neomedia.MediaService.ENABLE_FFMPEG_CODECS"

        /**
         * The name of the property which controls whether the h264 formats
         * will be registered in libjitsi even if the ffmpeg codec is missing.
         */
        const val ENABLE_H264_FORMAT_PNAME = "neomedia.MediaService.ENABLE_H264_FORMAT"
    }
}