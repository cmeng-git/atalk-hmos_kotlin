/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.impl.neomedia.AbstractRTPConnector
import org.atalk.impl.neomedia.format.MediaFormatImpl
import org.atalk.service.neomedia.MediaDirection
import org.atalk.service.neomedia.QualityPreset
import org.atalk.service.neomedia.codec.EncodingConfiguration
import org.atalk.service.neomedia.device.MediaDevice
import org.atalk.service.neomedia.device.MediaDeviceWrapper
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.util.MediaType
import org.atalk.util.event.VideoEvent
import org.atalk.util.event.VideoListener
import java.awt.Component
import java.util.*
import javax.media.Format
import javax.media.Player
import javax.media.Processor
import javax.media.protocol.DataSource

/**
 * Implements a `MediaDevice` which is to be used in video conferencing implemented with an
 * RTP translator.
 *
 * @author Lyubomir Marinov
 * @author Hristo Terezov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class VideoTranslatorMediaDevice
/**
 * Initializes a new `VideoTranslatorMediaDevice` which enables a specific
 * `MediaDevice` to be used in video conferencing implemented with an RTP translator.
 *
 * @param device the `MediaDevice` which the new instance is to enable to be used in video
 * conferencing implemented with an RTP translator
 */
(
        /**
         * The `MediaDevice` which this instance enables to be used in a video conference
         * implemented with an RTP translator.
         */
        private val device: MediaDeviceImpl) : AbstractMediaDevice(), MediaDeviceWrapper, VideoListener {
    /**
     * The `VideoMediaDeviceSession` of [.device] the `outputDataSource` of
     * which is the `captureDevice` of [.streamDeviceSessions].
     */
    private var deviceSession: VideoMediaDeviceSession? = null

    /**
     * The `MediaStreamMediaDeviceSession`s sharing the `outputDataSource` of
     * [.device] as their `captureDevice`.
     */
    private val streamDeviceSessions = LinkedList<MediaStreamMediaDeviceSession>()

    /**
     * Releases the resources allocated by this instance in the course of its execution and
     * prepares it to be garbage collected when all [.streamDeviceSessions] have been closed.
     *
     * @param streamDeviceSession the `MediaStreamMediaDeviceSession` which has been closed
     */
    @Synchronized
    private fun close(streamDeviceSession: MediaStreamMediaDeviceSession) {
        streamDeviceSessions.remove(streamDeviceSession)
        if (deviceSession != null) {
            deviceSession!!.removeRTCPFeedbackMessageCreateListener(streamDeviceSession)
        }
        if (streamDeviceSessions.isEmpty()) {
            if (deviceSession != null) {
                deviceSession!!.removeVideoListener(this)
                deviceSession!!.close(MediaDirection.SENDRECV)
            }
            deviceSession = null
        } else updateDeviceSessionStartedDirection()
    }

    /**
     * Creates a `DataSource` instance for this `MediaDevice` which gives access to
     * the captured media.
     *
     * @return a `DataSource` instance which gives access to the media captured by this
     * `MediaDevice`
     * @see AbstractMediaDevice.createOutputDataSource
     */
    @Synchronized
    override fun createOutputDataSource(): DataSource? {
        if (deviceSession == null) {
            var format: MediaFormatImpl<out Format?>? = null
            var startedDirection = MediaDirection.INACTIVE
            for (streamDeviceSession in streamDeviceSessions) {
                val streamFormat = streamDeviceSession.getFormat()
                if (streamFormat != null && format == null) format = streamFormat
                startedDirection = startedDirection.or(streamDeviceSession.startedDirection)
            }
            val newDeviceSession = device.createSession()
            if (newDeviceSession is VideoMediaDeviceSession) {
                deviceSession = newDeviceSession
                deviceSession!!.addVideoListener(this)
                for (streamDeviceSession in streamDeviceSessions) {
                    deviceSession!!.addRTCPFeedbackMessageCreateListener(streamDeviceSession)
                }
            }
            if (format != null) deviceSession!!.setFormat(format)
            deviceSession!!.start(startedDirection)
        }
        return if (deviceSession == null) null else deviceSession!!.outputDataSource!!
    }

    /**
     * Creates a new `MediaDeviceSession` instance which is to represent the use of this
     * `MediaDevice` by a `MediaStream`.
     *
     * @return a new `MediaDeviceSession` instance which is to represent the use of this
     * `MediaDevice` by a `MediaStream`
     * @see AbstractMediaDevice.createSession
     */
    @Synchronized
    override fun createSession(): MediaDeviceSession {
        val streamDeviceSession = MediaStreamMediaDeviceSession()
        streamDeviceSessions.add(streamDeviceSession)
        return streamDeviceSession
    }

    /**
     * Returns the `MediaDirection` supported by this device.
     *
     * @return `MediaDirection.SENDONLY` if this is a read-only device,
     * `MediaDirection.RECVONLY` if this is a write-only device and
     * `MediaDirection.SENDRECV` if this `MediaDevice` can both capture and
     * render media
     * @see MediaDevice.direction
     */
    override val direction: MediaDirection
        get() = device.direction

    /**
     * Returns the `MediaFormat` that this device is currently set to use when capturing
     * data.
     *
     * @return the `MediaFormat` that this device is currently set to provide media in.
     * @see MediaDevice.format
     */
    override val format: MediaFormat?
        get() = device.format

    /**
     * Returns the `MediaType` that this device supports.
     *
     * @return `MediaType.AUDIO` if this is an audio device or `MediaType.VIDEO` in
     * case of a video device
     * @see MediaDevice.mediaType
     */
    override val mediaType: MediaType
        get() = device.mediaType

    /**
     * Returns a list of `MediaFormat` instances representing the media formats supported by
     * this `MediaDevice`.
     *
     * @param localPreset the preset used to set the send format parameters, used for video and settings
     * @param remotePreset the preset used to set the receive format parameters, used for video and settings
     * @return the list of `MediaFormat`s supported by this device
     * @see MediaDevice.getSupportedFormats
     */
    override fun getSupportedFormats(
            localPreset: QualityPreset?,
            remotePreset: QualityPreset?): List<MediaFormat> {
        return device.getSupportedFormats(localPreset, remotePreset)
    }

    /**
     * Returns a list of `MediaFormat` instances representing the media formats supported by
     * this `MediaDevice` and enabled in `encodingConfiguration`..
     *
     * @param localPreset the preset used to set the send format parameters, used for video and settings
     * @param remotePreset the preset used to set the receive format parameters, used for video and settings
     * @param encodingConfiguration the `EncodingConfiguration` instance to use
     * @return the list of `MediaFormat`s supported by this device and enabled in
     * `encodingConfiguration`.
     * @see MediaDevice.getSupportedFormats
     */
    override fun getSupportedFormats(localPreset: QualityPreset?,
            remotePreset: QualityPreset?, encodingConfiguration: EncodingConfiguration?): List<MediaFormat> {
        return device.getSupportedFormats(localPreset, remotePreset, encodingConfiguration)
    }

    /**
     * Gets the actual `MediaDevice` which this `MediaDevice` is effectively built on
     * top of and forwarding to.
     *
     * @return the actual `MediaDevice` which this `MediaDevice` is effectively built
     * on top of and forwarding to
     * @see MediaDeviceWrapper.getWrappedDevice
     */
    override val wrappedDevice: MediaDevice
        get() = device

    /**
     * Updates the value of the `startedDirection` property of [.deviceSession] to be
     * in accord with the values of the property of [.streamDeviceSessions].
     */
    @Synchronized
    private fun updateDeviceSessionStartedDirection() {
        if (deviceSession == null) return
        var startDirection = MediaDirection.INACTIVE
        for (streamDeviceSession in streamDeviceSessions) {
            startDirection = startDirection.or(streamDeviceSession.startedDirection)
        }
        deviceSession!!.start(startDirection)
        var stopDirection = MediaDirection.INACTIVE
        if (!startDirection.allowsReceiving()) stopDirection = stopDirection.or(MediaDirection.RECVONLY)
        if (!startDirection.allowsSending()) stopDirection = stopDirection.or(MediaDirection.SENDONLY)
        deviceSession!!.stop(stopDirection)
    }

    /**
     * {@inheritDoc}
     *
     *
     * Forwards `event`, to each of the managed `MediaStreamMediaDeviceSession`
     * instances. The event is expected to come from `this.deviceSession`, since
     * `this` is registered there as a `VideoListener`.
     */
    override fun videoAdded(event: VideoEvent) {
        for (sds in streamDeviceSessions) {
            sds.fireVideoEvent(event, false)
        }
    }

    /**
     * {@inheritDoc}
     *
     *
     * Forwards `event`, to each of the managed `MediaStreamMediaDeviceSession`
     * instances. The event is expected to come from `this.deviceSession`, since
     * `this` is registered there as a `VideoListener`.
     */
    override fun videoRemoved(event: VideoEvent) {
        for (sds in streamDeviceSessions) {
            sds.fireVideoEvent(event, false)
        }
    }

    /**
     * {@inheritDoc}
     *
     *
     * Forwards `event`, to each of the managed `MediaStreamMediaDeviceSession`
     * instances. The event is expected to come from `this.deviceSession`, since
     * `this` is registered there as a `VideoListener`.
     */
    override fun videoUpdate(event: VideoEvent) {
        for (sds in streamDeviceSessions) {
            sds.fireVideoEvent(event, false)
        }
    }

    /**
     * Represents the use of this `VideoTranslatorMediaDevice` by a `MediaStream`.
     */
    private inner class MediaStreamMediaDeviceSession
    /**
     * Initializes a new `MediaStreamMediaDeviceSession` which is to represent the
     * use of this `VideoTranslatorMediaDevice` by a `MediaStream`.
     */
        : VideoMediaDeviceSession(this@VideoTranslatorMediaDevice) {
        /**
         * Releases the resources allocated by this instance in the course of its execution and
         * prepares it to be garbage collected.
         */
        override fun close(direction: MediaDirection?) {
            super.close(direction)
            this@VideoTranslatorMediaDevice.close(this)
        }

        /**
         * Creates the `DataSource` that this instance is to read captured media from.
         *
         * @return the `DataSource` that this instance is to read captured media from
         * @see VideoMediaDeviceSession.createCaptureDevice
         */
        override fun createCaptureDevice(): DataSource? {
            return createOutputDataSource()
        }

        /**
         * Initializes a new `Player` instance which is to provide the local visual/video
         * `Component`. The new instance is initialized to render the media of a specific
         * `DataSource`.
         *
         * @param captureDevice the `DataSource` which is to have its media rendered by the new instance as
         * the local visual/video `Component`
         * @return a new `Player` instance which is to provide the local visual/video
         * `Component`
         */
        override fun createLocalPlayer(captureDevice: DataSource?): Player? {
            var captureDevice1 = captureDevice
            synchronized(this@VideoTranslatorMediaDevice) {
                if (deviceSession != null) captureDevice1 = deviceSession!!.getCaptureDevice()
            }
            return super.createLocalPlayer(captureDevice1)
        }

        /**
         * Initializes a new FMJ `Processor` which is to transcode [.captureDevice]
         * into the format of this instance.
         *
         * @return a new FMJ `Processor` which is to transcode `captureDevice` into
         * the format of this instance
         */
        override fun createProcessor(): Processor? {
            return null
        }

        /**
         * Gets the output `DataSource` of this instance which provides the captured (RTP)
         * data to be sent by `MediaStream` to `MediaStreamTarget`.
         *
         * @return the output `DataSource` of this instance which provides the captured
         * (RTP) data to be sent by `MediaStream` to `MediaStreamTarget`
         * @see MediaDeviceSession.outputDataSource
         */
        override val outputDataSource: DataSource
            get() = getConnectedCaptureDevice()!!

        /**
         * Sets the `RTPConnector` that will be used to initialize some codec for RTCP
         * feedback and adds the instance to RTCPFeedbackCreateListners of deviceSession.
         *
         * @param rtpConnector the RTP connector
         */
        override fun setConnector(rtpConnector: AbstractRTPConnector?) {
            super.setConnector(rtpConnector)
            if (deviceSession != null) deviceSession!!.addRTCPFeedbackMessageCreateListener(this)
        }

        /**
         * Notifies this instance that the value of its `startedDirection` property has
         * changed from a specific `oldValue` to a specific `newValue`.
         *
         * @param oldValue the `MediaDirection` which used to be the value of the
         * `startedDirection` property of this instance
         * @param newValue the `MediaDirection` which is the value of the `startedDirection`
         * property of this instance
         */
        override fun startedDirectionChanged(oldValue: MediaDirection?, newValue: MediaDirection) {
            super.startedDirectionChanged(oldValue, newValue)
            updateDeviceSessionStartedDirection()
        }

        /**
         * {@inheritDoc} Returns the local visual `Component` for this
         * `MediaStreamMediaDeviceSession`, which, if present, is maintained in
         * `this.deviceSession`.
         */
        override val localVisualComponent: Component?
            get() = if (deviceSession != null) deviceSession!!.localVisualComponent else null

        /**
         * {@inheritDoc}
         *
         *
         * Creates, if necessary, the local visual `Component` depicting the video being
         * streamed from the local peer to a remote peer. The `Component` is provided by the
         * single `Player` instance, which is maintained for this
         * `VideoTranslatorMediaDevice` and is managed by `this.deviceSession`.
         */
        override fun createLocalVisualComponent(): Component? {
            return if (deviceSession != null) deviceSession!!.createLocalVisualComponent() else null
        }

//        /**
//         * Returns the `Player` instance which provides the local visual/video
//         * `Component`. A single `Player` is maintained for this
//         * `VideoTranslatorMediaDevice`, and it is managed by `this.deviceSession`.
//         */
//        override var localPlayer: Player? = null
//            get() {
//                return if (deviceSession != null) deviceSession!!.localPlayer else null
//            }

        /**
         * {@inheritDoc}
         *
         *
         * Does nothing, because there is no `Player` associated with this
         * `MediaStreamMediaDeviceSession` and therefore nothing to dispose of.
         *
         * @param player the `Player` to dispose of.
         */
        override fun disposeLocalPlayer(player: Player) {}
    }
}