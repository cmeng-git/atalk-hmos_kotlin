/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.AbstractRTPConnector
import org.atalk.impl.neomedia.MediaStreamImpl
import org.atalk.impl.neomedia.NeomediaServiceUtils
import org.atalk.impl.neomedia.RTCPFeedbackMessagePacket
import org.atalk.impl.neomedia.VideoMediaStreamImpl
import org.atalk.impl.neomedia.codec.video.HFlip
import org.atalk.impl.neomedia.codec.video.SwScale
import org.atalk.impl.neomedia.codec.video.h264.DePacketizer
import org.atalk.impl.neomedia.codec.video.h264.JNIDecoder
import org.atalk.impl.neomedia.codec.video.h264.JNIEncoder
import org.atalk.impl.neomedia.control.ImgStreamingControl
import org.atalk.impl.neomedia.format.MediaFormatImpl
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl
import org.atalk.impl.neomedia.transform.ControlTransformInputStream
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.MediaDirection
import org.atalk.service.neomedia.control.KeyFrameControl
import org.atalk.service.neomedia.control.KeyFrameControlAdapter
import org.atalk.service.neomedia.event.RTCPFeedbackMessageCreateListener
import org.atalk.service.neomedia.event.RTCPFeedbackMessageEvent
import org.atalk.service.neomedia.event.RTCPFeedbackMessageListener
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.service.neomedia.format.VideoMediaFormat
import org.atalk.util.MediaType
import org.atalk.util.OSUtils
import org.atalk.util.event.SizeChangeVideoEvent
import org.atalk.util.event.VideoEvent
import org.atalk.util.event.VideoListener
import org.atalk.util.event.VideoNotifierSupport
import org.atalk.util.swing.VideoLayout
import timber.log.Timber
import java.awt.Canvas
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.IOException
import java.util.*
import javax.media.Buffer
import javax.media.Codec
import javax.media.ConfigureCompleteEvent
import javax.media.Controller
import javax.media.ControllerEvent
import javax.media.ControllerListener
import javax.media.Format
import javax.media.Manager
import javax.media.NotConfiguredError
import javax.media.NotRealizedError
import javax.media.Player
import javax.media.PlugIn
import javax.media.Processor
import javax.media.RealizeCompleteEvent
import javax.media.SizeChangeEvent
import javax.media.UnsupportedPlugInException
import javax.media.control.FormatControl
import javax.media.control.FrameRateControl
import javax.media.control.TrackControl
import javax.media.format.VideoFormat
import javax.media.protocol.CaptureDevice
import javax.media.protocol.DataSource
import javax.media.protocol.PullBufferDataSource
import javax.media.protocol.SourceCloneable
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Extends `MediaDeviceSession` to add video-specific functionality.
 *
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Hristo Terezov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
open class VideoMediaDeviceSession
/**
 * Initializes a new `VideoMediaDeviceSession` instance which is to represent the work
 * of a `MediaStream` with a specific video `MediaDevice`.
 *
 * @param device the video `MediaDevice` the use of which by a `MediaStream` is to be
 * represented by the new instance
 */
(device: AbstractMediaDevice) : MediaDeviceSession(device), RTCPFeedbackMessageCreateListener {
    /**
     * `RTCPFeedbackMessageListener` instance that will be passed to
     * [.rtpConnector] to handle RTCP PLI requests.
     */
    private var encoder: RTCPFeedbackMessageListener? = null

    /**
     * The `KeyFrameControl` used by this`VideoMediaDeviceSession` as a means to
     * control its key frame-related logic.
     */
    private var keyFrameControl: KeyFrameControl? = null

    /**
     * The `KeyFrameRequester` implemented by this `VideoMediaDeviceSession` and
     * provided to [.keyFrameControl] .
     */
    private var keyFrameRequester: KeyFrameControl.KeyFrameRequester? = null

    /**
     * The `Object` which synchronizes the access to [.localPlayer] .
     */
    private val localPlayerSyncRoot = Any()

    /**
     * The `Player` which provides the local visual/video `Component`.
     */
    var localPlayer: Player? = null
        get() {
            synchronized(localPlayerSyncRoot) { return field }
        }

    /**
     * Local SSRC.
     */
    private var localSSRC = -1L

    /**
     * Output size of the stream.
     *
     *
     * It is used to specify a different size (generally lesser ones) than the capture device
     * provides. Typically one usage can be in desktop streaming/sharing session when sender
     * desktop is bigger than remote ones.
     */
    private var outputSize: Dimension? = null

    /**
     * The `SwScale` inserted into the codec chain of the `Player` rendering the
     * media received from the remote peer and enabling the explicit setting of the video size.
     */
    private var playerScaler: SwScale? = null

    /**
     * Remote SSRC.
     */
    private var remoteSSRC = -1L

    /**
     * The list of `RTCPFeedbackMessageCreateListener` which will be notified when a
     * `RTCPFeedbackMessageListener` is created.
     */
    private val rtcpFeedbackMessageCreateListeners = LinkedList<RTCPFeedbackMessageCreateListener>()

    /**
     * The `RTPConnector` with which the `RTPManager` of this instance is to be or is
     * already initialized.
     */
    private var rtpConnector: AbstractRTPConnector? = null

    /**
     * Use or not RTCP feedback Picture Loss Indication to request keyframes. Does not affect
     * handling of received RTCP feedback events.
     */
    private var useRTCPFeedbackPLI = false

    /**
     * The facility which aids this instance in managing a list of `VideoListener`s and firing `VideoEvent`s to them.
     */
    private val videoNotifierSupport = VideoNotifierSupport(this, false)

    /**
     * Adds `RTCPFeedbackMessageCreateListener`.
     *
     * @param listener the listener to add
     */
    fun addRTCPFeedbackMessageCreateListener(listener: RTCPFeedbackMessageCreateListener) {
        synchronized(rtcpFeedbackMessageCreateListeners) { rtcpFeedbackMessageCreateListeners.add(listener) }
        if (encoder != null) listener.onRTCPFeedbackMessageCreate(encoder!!)
    }

    /**
     * Adds a specific `VideoListener` to this instance in order to receive notifications
     * when visual/video `Component`s are being added and removed.
     *
     *
     * Adding a listener which has already been added does nothing i.e. it is not added more than
     * once and thus does not receive one and the same `VideoEvent` multiple times.
     *
     *
     * @param listener the `VideoListener` to be notified when visual/video `Component`s are
     * being added or removed in this instance
     */
    fun addVideoListener(listener: VideoListener) {
        videoNotifierSupport.addVideoListener(listener)
    }

    /**
     * Asserts that a specific `MediaDevice` is acceptable to be set as the
     * `MediaDevice` of this instance. Makes sure that its `MediaType` is [MediaType.VIDEO].
     *
     * @param device the `MediaDevice` to be checked for suitability to become the `MediaDevice` of this instance
     * @see MediaDeviceSession.checkDevice
     */
    override fun checkDevice(device: AbstractMediaDevice) {
        require(MediaType.VIDEO == device.mediaType) {
            "device"
        }
    }

    /**
     * Gets notified about `ControllerEvent`s generated by [.localPlayer].
     *
     * @param ev the `ControllerEvent` specifying the `Controller
     *
     *  `true` if the image displayed in the local visual `Component` is to be
     * horizontally flipped otherwise, `false`
    ` */
    private fun controllerUpdateForCreateLocalVisualComponent(ev: ControllerEvent, hFlip: Boolean) {
        if (ev is ConfigureCompleteEvent) {
            val player = ev.getSourceController() as Processor

            /*
             * Use SwScale for the scaling since it produces an image with better quality and add
             * the "flip" effect to the video.
             */
            val trackControls = player.trackControls
            if (trackControls != null && trackControls.isNotEmpty()) {
                try {
                    for (trackControl in trackControls) {
                        trackControl.setCodecChain(if (hFlip) arrayOf<Codec>(HFlip(), SwScale()) else arrayOf<Codec>(SwScale()))
                        break
                    }
                } catch (upiex: UnsupportedPlugInException) {
                    Timber.w(upiex, "Failed to add HFlip/SwScale Effect")
                }
            }

            // Turn the Processor into a Player.
            try {
                player.contentDescriptor = null
            } catch (nce: NotConfiguredError) {
                Timber.e(nce, "Failed to set ContentDescriptor of Processor")
            }
            player.realize()
        } else if (ev is RealizeCompleteEvent) {
            val player = ev.getSourceController() as Player
            val visualComponent = player.visualComponent
            val start = if (null == visualComponent) false else {
                fireVideoEvent(VideoEvent.VIDEO_ADDED, visualComponent, VideoEvent.LOCAL, false)
                true
            }
            if (start) player.start() else {
                // No listener is interested in our event so free the resources.
                synchronized(localPlayerSyncRoot) { if (localPlayer == player) localPlayer = null }
                player.stop()
                player.deallocate()
                player.close()
            }
        } else if (ev is SizeChangeEvent) {
            /*
             * Mostly for the sake of completeness, notify that the size of the local video has
             * changed like we do for the remote videos.
             */
            val scev = ev
            playerSizeChange(scev.sourceController, VideoEvent.LOCAL, scev.width, scev.height)
        }
    }

    /**
     * Creates the `DataSource` that this instance is to read captured media from.
     *
     * @return the `DataSource` that this instance is to read captured media from
     */
    override fun createCaptureDevice(): DataSource? {
        /*
         * Create our DataSource as SourceCloneable so we can use it to both display local video
         * and stream to remote peer.
         */
        var captureDevice = super.createCaptureDevice()
        if (captureDevice != null) {
            val protocol = captureDevice.locator?.protocol
            var frameRate: Float
            val deviceConfig = NeomediaServiceUtils.mediaServiceImpl!!.deviceConfiguration

            // Apply the video size and frame rate configured by the user.
            if (DeviceSystem.LOCATOR_PROTOCOL_IMGSTREAMING == protocol) {
                /*
                 * It is not clear at this time what the default frame rate for desktop streaming should be.
                 */
                frameRate = 10f
            } else {
                var videoSize = deviceConfig.getVideoSize()
                // if we have an output size smaller than our current settings, respect that size
                if (outputSize != null && videoSize.height > outputSize!!.height && videoSize.width > outputSize!!.width) videoSize = outputSize!!
                val dim = VideoMediaStreamImpl.selectVideoSize(captureDevice, videoSize.width, videoSize.height)
                frameRate = deviceConfig.getFrameRate().toFloat()
                if (dim != null) Timber.i("Video set initial resolution: [%dx%d]", dim.width, dim.height)
            }
            val frameRateControl = captureDevice.getControl(FrameRateControl::class.java.name) as FrameRateControl?
            if (frameRateControl != null) {
                val maxSupportedFrameRate = frameRateControl.maxSupportedFrameRate
                if (maxSupportedFrameRate > 0 && frameRate > maxSupportedFrameRate) frameRate = maxSupportedFrameRate
                if (frameRate > 0) frameRateControl.frameRate = frameRate

                // print initial video frame rate, when starting video
                Timber.i("video send FPS: %s", if (frameRate == -1f) "default(no restriction)" else frameRate)
            }
            if (captureDevice !is SourceCloneable) {
                val cloneableDataSource = Manager.createCloneableDataSource(captureDevice)
                if (cloneableDataSource != null) captureDevice = cloneableDataSource
            }
        }
        return captureDevice
    }

    /**
     * Initializes a new `Player` instance which is to provide the local visual/video
     * `Component`. The new instance is initialized to render the media of the
     * `captureDevice` of this `MediaDeviceSession`.
     *
     * @return a new `Player` instance which is to provide the local visual/video `Component`
     */
    private fun createLocalPlayer(): Player? {
        return createLocalPlayer(getCaptureDevice())
    }

    /**
     * Initializes a new `Player` instance which is to provide the local visual/video
     * `Component`. The new instance is initialized to render the media of a specific `DataSource`.
     *
     * @param captureDevice the `DataSource` which is to have its media rendered by the new instance as the
     * local visual/video `Component`
     * @return a new `Player` instance which is to provide the local visual/video `Component`
     */
    protected open fun createLocalPlayer(captureDevice: DataSource?): Player? {
        val dataSource = if (captureDevice is SourceCloneable) {
            (captureDevice as SourceCloneable?)!!.createClone()
        }
        else {
            null
        }
        var localPlayer: Processor? = null

        if (dataSource != null) {
            var exception: Exception? = null
            try {
                localPlayer = Manager.createProcessor(dataSource)
            } catch (ex: Exception) {
                exception = ex
            }
            if (exception == null) {
                if (localPlayer != null) {
                    /*
                     * If a local visual Component is to be displayed for desktop
                     * sharing/streaming, do not flip it because it does not seem natural.
                     */
                    val hflip = captureDevice!!.getControl(ImgStreamingControl::class.java.name) == null
                    localPlayer.addControllerListener(ControllerListener { ev: ControllerEvent -> controllerUpdateForCreateLocalVisualComponent(ev, hflip) })
                    localPlayer.configure()
                }
            } else {
                Timber.e(exception, "Failed to connect to %s", MediaStreamImpl.toString(dataSource))
            }
        }
        return localPlayer
    }

    /**
     * Creates the visual `Component` depicting the video being streamed from the local peer
     * to the remote peer.
     *
     * @return the visual `Component` depicting the video being streamed from the local peer
     * to the remote peer if it was immediately created or `null` if it was not
     * immediately created and it is to be delivered to the currently registered
     * `VideoListener`s in a `VideoEvent` with type
     * [VideoEvent.VIDEO_ADDED] and origin [VideoEvent.LOCAL]
     */
    open fun createLocalVisualComponent(): Component? {
        // On Android local preview is displayed directly using Surface provided
        // to the recorder. We don't want to build unused codec chain.
        if (OSUtils.IS_ANDROID) {
            return null
        }

        /*
         * Displaying the currently streamed desktop is perceived as unnecessary because the user
         * sees the whole desktop anyway. Instead, a static image will be presented.
         */
        val captureDevice = getCaptureDevice()!!
        if (captureDevice.getControl(ImgStreamingControl::class.java.name) != null) {
            return createLocalVisualComponentForDesktopStreaming()
        }

        /*
         * The visual Component to depict the video being streamed from the local peer to the
         * remote peer is created by JMF and its Player so it is likely to take noticeably
         * long time. Consequently, we will deliver it to the currently registered
         * VideoListeners in a VideoEvent after returning from the call.
         */
        var localVisualComponent: Component?
        synchronized(localPlayerSyncRoot) {
            if (localPlayer == null) localPlayer = createLocalPlayer()
            localVisualComponent = if (localPlayer == null) null else getVisualComponent(localPlayer!!)
        }
        /*
         * If the local visual/video Component exists at this time, it has likely been created by a
         * previous call to this method. However, the caller may still depend on a VIDEO_ADDED
         * event being fired for it.
         */
        if (localVisualComponent != null) {
            fireVideoEvent(VideoEvent.VIDEO_ADDED, localVisualComponent, VideoEvent.LOCAL, false)
        }
        return localVisualComponent
    }

    /**
     * Creates the visual `Component` to depict the streaming of the desktop of the local
     * peer to the remote peer.
     *
     * @return the visual `Component` to depict the streaming of the desktop of the local
     * peer to the remote peer
     */
    private fun createLocalVisualComponentForDesktopStreaming(): Component? {
        val icon = LibJitsi.resourceManagementService.getImage(DESKTOP_STREAMING_ICON)
        val canvas: Canvas?
        if (icon == null) canvas = null else {
            val img = icon.image
            canvas = object : Canvas() {
                override fun paint(g: Graphics) {
                    val width = width
                    val height = height
                    g.setColor(Color.BLACK)
                    g.fillRect(0, 0, width, height)
                    val imgWidth = img.getWidth(this)
                    val imgHeight = img.getHeight(this)
                    if (imgWidth < 1 || imgHeight < 1) return
                    var scale = false
                    var scaleFactor = 1f
                    if (imgWidth > width) {
                        scale = true
                        scaleFactor = width / imgWidth.toFloat()
                    }
                    if (imgHeight > height) {
                        scale = true
                        scaleFactor = scaleFactor.coerceAtMost(height / imgHeight.toFloat())
                    }
                    val dstWidth: Int
                    val dstHeight: Int
                    if (scale) {
                        dstWidth = (imgWidth * scaleFactor).roundToInt()
                        dstHeight = (imgHeight * scaleFactor).roundToInt()
                    } else {
                        dstWidth = imgWidth
                        dstHeight = imgHeight
                    }
                    val dstX = (width - dstWidth) / 2
                    val dstY = (height - dstWidth) / 2
                    g.drawImage(img, dstX, dstY, dstX + dstWidth, dstY + dstHeight, 0, 0, imgWidth,
                            imgHeight, this)
                }
            }
            val iconSize = Dimension(icon.iconWidth, icon.iconHeight)
            canvas.setMaximumSize(iconSize)
            canvas.setPreferredSize(iconSize)

            /*
             * Set a clue so that we can recognize it if it gets received as an argument to
             * #disposeLocalVisualComponent().
             */
            canvas.setName(DESKTOP_STREAMING_ICON)
            fireVideoEvent(VideoEvent.VIDEO_ADDED, canvas, VideoEvent.LOCAL, false)
        }
        return canvas
    }

    /**
     * Releases the resources allocated by a specific local `Player` in the course of its
     * execution and prepares it to be garbage collected. If the specified `Player` is
     * rendering video, notifies the `VideoListener`s of this instance that its visual
     * `Component` is to no longer be used by firing a [VideoEvent.VIDEO_REMOVED]
     * `VideoEvent`.
     *
     * @param player the `Player` to dispose of
     * @see MediaDeviceSession.disposePlayer
     */
    protected open fun disposeLocalPlayer(player: Player) {
        /*
         * The player is being disposed so let the (interested) listeners know its
         * Player#getVisualComponent() (if any) should be released.
         */
        var visualComponent: Component? = null
        try {
            visualComponent = getVisualComponent(player)
            player.stop()
            player.deallocate()
            player.close()
        } finally {
            synchronized(localPlayerSyncRoot) { if (localPlayer === player) localPlayer = null }
            if (visualComponent != null) {
                fireVideoEvent(VideoEvent.VIDEO_REMOVED, visualComponent, VideoEvent.LOCAL, false)
            }
        }
    }

    /**
     * Disposes of the local visual `Component` of the local peer.
     *
     * @param component the local visual `Component` of the local peer to dispose of
     */
    protected fun disposeLocalVisualComponent(component: Component?) {
        if (component != null) {
            /*
             * Desktop streaming does not use a Player but a Canvas with its name equal to the
             * value of DESKTOP_STREAMING_ICON.
             */
            if (DESKTOP_STREAMING_ICON == component.name) {
                fireVideoEvent(VideoEvent.VIDEO_REMOVED, component, VideoEvent.LOCAL, false)
            } else {
                var localPlayer: Player?
                synchronized(localPlayerSyncRoot) { localPlayer = this.localPlayer }
                if (localPlayer != null) {
                    val localPlayerVisualComponent = getVisualComponent(localPlayer!!)
                    if (localPlayerVisualComponent == null || localPlayerVisualComponent === component) disposeLocalPlayer(localPlayer!!)
                }
            }
        }
    }

    /**
     * Releases the resources allocated by a specific `Player` in the course of its
     * execution and prepares it to be garbage collected. If the specified `Player`
     * is rendering video, notifies the `VideoListener`s of this instance that its
     * visual `Component` is to no longer be used by firing a
     * [VideoEvent.VIDEO_REMOVED] `VideoEvent`.
     *
     * @param player the `Player` to dispose of
     * @see MediaDeviceSession.disposePlayer
     */
    override fun disposePlayer(player: Player) {
        /*
         * The player is being disposed so let the (interested) listeners know its
         * Player#getVisualComponent() (if any) should be released.
         */
        val visualComponent = getVisualComponent(player)
        super.disposePlayer(player)
        if (visualComponent != null) {
            fireVideoEvent(VideoEvent.VIDEO_REMOVED, visualComponent, VideoEvent.REMOTE, false)
        }
    }

    /**
     * Notify the `VideoListener`s registered with this instance about a specific type of
     * change in the availability of a specific visual `Component` depicting video.
     *
     * @param type the type of change as defined by `VideoEvent` in the availability of the
     * specified visual `Component` depicting video
     * @param visualComponent the visual `Component` depicting video which has been added
     * or removed in this instance
     * @param origin [VideoEvent.LOCAL] if the origin of the video is local (e.g. it is being locally
     * captured) [VideoEvent.REMOTE] if the origin of the video is remote (e.g. a
     * remote peer is streaming it)
     * @param wait `true` if the call is to wait till the specified `VideoEvent` has been
     * delivered to the `VideoListener`s otherwise, `false`
     * @return `true` if this event and, more specifically, the visual `Component` it
     * describes have been consumed and should be considered owned, referenced (which is
     * important because `Component`s belong to a single `Container` at a
     * time) otherwise, `false`
     */
    private fun fireVideoEvent(type: Int, visualComponent: Component?, origin: Int, wait: Boolean): Boolean {
        Timber.log(TimberLog.FINER, "Firing VideoEvent with type %s, originated from %s and Wait is %s",
                VideoEvent.typeToString(type), VideoEvent.originToString(origin), wait)
        return videoNotifierSupport.fireVideoEvent(type, visualComponent, origin, wait)
    }

    /**
     * Notifies the `VideoListener`s registered with this instance about a specific `VideoEvent`.
     *
     * @param videoEvent the `VideoEvent` to be fired to the `VideoListener`s registered with
     * this instance
     * @param wait `true` if the call is to wait till the specified `VideoEvent` has been
     * delivered to the `VideoListener`s otherwise, `false`
     */
    fun fireVideoEvent(videoEvent: VideoEvent, wait: Boolean) {
        videoNotifierSupport.fireVideoEvent(videoEvent, wait)
    }

    /**
     * Gets the JMF `Format` of the `captureDevice` of this `MediaDeviceSession`.
     *
     * @return the JMF `Format` of the `captureDevice` of this `MediaDeviceSession`
     */
    private val captureDeviceFormat: Format?
        get() {
            val captureDevice = getCaptureDevice()

            if (captureDevice != null) {
                var formatControls: Array<FormatControl?>? = null
                if (captureDevice is CaptureDevice) {
                    formatControls = (captureDevice as CaptureDevice).formatControls
                }
                if (formatControls == null || formatControls.isEmpty()) {
                    val formatControl = captureDevice.getControl(FormatControl::class.java.name) as FormatControl?
                    if (formatControl != null) formatControls = arrayOf(formatControl)
                }
                if (formatControls != null) {
                    for (formatControl in formatControls) {
                        val format = formatControl!!.format
                        if (format != null) return format
                    }
                }
            }
            return null
        }

    /**
     * Gets the visual `Component`, if any, depicting the video streamed from the local peer
     * to the remote peer.
     *
     * @return the visual `Component` depicting the local video if local video is actually
     * being streamed from the local peer to the remote peer otherwise, `null`
     */
    open val localVisualComponent: Component?
        get() {
            synchronized(localPlayerSyncRoot) { return if (localPlayer == null) null else getVisualComponent(localPlayer!!) }
        }

    /**
     * Returns the FMJ `Format` of the video we are receiving from the remote peer.
     *
     * @return the FMJ `Format` of the video we are receiving from the remote peer or
     * `null` if we are not receiving any video or the FMJ `Format` of the
     * video we are receiving from the remote peer cannot be determined
     */
    val receivedVideoFormat: VideoFormat?
        get() {
            if (playerScaler != null) {
                val format = playerScaler!!.inputFormat
                if (format is VideoFormat) {
                    return format
                }
            }
            return null
        }

    /**
     * Returns the format of the video we are streaming to the remote peer.
     *
     * @return The video format of the sent video. Null, if no video is sent.
     */
    val sentVideoFormat: VideoFormat?
        get() {
            val capture = getCaptureDevice()
            if (capture is PullBufferDataSource) {
                val streams = (capture as PullBufferDataSource?)!!.streams
                for (stream in streams) {
                    val format = stream.format as VideoFormat?
                    if (format != null) return format
                }
            }
            return null
        }

    /**
     * Gets the visual `Component`s rendering the `ReceiveStream` corresponding to the given ssrc.
     *
     * @param ssrc the src-id of the receive stream, which visual `Component` we're looking for
     * @return the visual `Component` rendering the `ReceiveStream` corresponding to
     * the given ssrc
     */
    fun getVisualComponent(ssrc: Long): Component? {
        val player = getPlayer(ssrc)
        return if (player == null) null else getVisualComponent(player)
    }/*
         * When we know (through means such as SDP) that we don't want to receive, it doesn't make
         * sense to wait for the remote peer to acknowledge our desire. So we'll just stop
         * depicting the video of the remote peer regardless of whether it stops or continues its sending.
         */

    /**
     * Gets the visual `Component`s where video from the remote peer is being rendered.
     *
     * @return the visual `Component`s where video from the remote peer is being rendered
     */
    val visualComponents: List<Component>
        get() {
            val visualComponents = LinkedList<Component>()

            /*
             * When we know (through means such as SDP) that we don't want to receive, it doesn't make
             * sense to wait for the remote peer to acknowledge our desire. So we'll just stop
             * depicting the video of the remote peer regardless of whether it stops or continues its sending.
             */
            if (startedDirection.allowsReceiving()) {
                for (player in players) {
                    val visualComponent = getVisualComponent(player)
                    if (visualComponent != null) visualComponents.add(visualComponent)
                }
            }
            return visualComponents
        }

    /**
     * Implements [KeyFrameControl.KeyFrameRequester.requestKeyFrame] of
     * [.keyFrameRequester].
     *
     * @param keyFrameRequester the `KeyFrameControl.KeyFrameRequester` on which the method is invoked
     * @return `true` if this `KeyFrameRequester` has indeed requested a key frame
     * from the remote peer of the associated `VideoMediaStream` in response to the
     * call otherwise, `false`
     */
    private fun keyFrameRequesterRequestKeyFrame(keyFrameRequester: KeyFrameControl.KeyFrameRequester): Boolean {
        var requested = false
        if (useRTCPFeedbackPLI) {
            try {
                val controlOutputStream = rtpConnector!!.controlOutputStream
                if (controlOutputStream != null) {
                    RTCPFeedbackMessagePacket(RTCPFeedbackMessageEvent.FMT_PLI,
                            RTCPFeedbackMessageEvent.PT_PS, localSSRC,
                            remoteSSRC).writeTo(controlOutputStream)
                    requested = true
                }
            } catch (ioe: IOException) {
                /*
                 * Apart from logging the IOException, there are not a lot of ways to handle it.
                 */
            }
        }
        return requested
    }

    /**
     * Notifies this `VideoMediaDeviceSession` of a new `RTCPFeedbackListener`
     *
     * @param rtcpFeedbackMessageListener the listener to be added.
     */
    override fun onRTCPFeedbackMessageCreate(rtcpFeedbackMessageListener: RTCPFeedbackMessageListener,
    ) {
        if (rtpConnector != null) {
            try {
                (rtpConnector!!.controlInputStream as ControlTransformInputStream)
                        .addRTCPFeedbackMessageListener(rtcpFeedbackMessageListener)
            } catch (ioe: IOException) {
                Timber.e(ioe, "Error cannot get RTCP input stream")
            }
        }
    }

    /**
     * Notifies this instance that a specific `Player` of remote content has generated a
     * `ConfigureCompleteEvent`.
     *
     * @param player the `Player` which is the source of a `ConfigureCompleteEvent`
     * @see MediaDeviceSession.playerConfigureComplete
     */
    override fun playerConfigureComplete(player: Processor) {
        super.playerConfigureComplete(player)
        val trackControls = player.trackControls
        var playerScaler: SwScale? = null

        /* We don't add SwScale, KeyFrameControl on Android. */
        if (trackControls != null && trackControls.isNotEmpty() && !OSUtils.IS_ANDROID) {
            val fmjEncoding = getFormat()!!.jMFEncoding
            try {
                for (trackControl in trackControls) {
                    /*
                     * Since SwScale will scale any input size into the configured output size, we
                     * may never get SizeChangeEvent from the player. We'll generate it ourselves
                     * then.
                     */
                    playerScaler = PlayerScaler(player)

                    /*
                     * For H.264, we will use RTCP feedback. For example, to tell the sender that
                     * we've missed a frame.
                     */
                    if ("h264/rtp".equals(fmjEncoding, ignoreCase = true)) {
                        val depacketizer = DePacketizer()
                        val decoder = JNIDecoder()
                        if (keyFrameControl != null) {
                            depacketizer.setKeyFrameControl(keyFrameControl!!)
                            decoder.setKeyFrameControl(object : KeyFrameControlAdapter() {
                                override fun requestKeyFrame(urgent: Boolean): Boolean {
                                    return depacketizer.requestKeyFrame(urgent)
                                }
                            })
                        }
                        trackControl.setCodecChain(arrayOf<Codec>(depacketizer, decoder, playerScaler))
                    } else {
                        trackControl.setCodecChain(arrayOf<Codec>(playerScaler))
                    }
                    break
                }
            } catch (upiex: UnsupportedPlugInException) {
                Timber.e(upiex, "Failed to add SwScale or H.264 DePacketizer to codec chain")
                playerScaler = null
            }
        }
        this.playerScaler = playerScaler
    }

    /**
     * Gets notified about `ControllerEvent`s generated by a specific `Player` of remote content.
     *
     * @param ev the `ControllerEvent` specifying the `Controller` which is the source of
     * the event and the very type of the event
     * @see MediaDeviceSession.playerControllerUpdate
     */
    override fun playerControllerUpdate(ev: ControllerEvent) {
        super.playerControllerUpdate(ev)

        /*
         * If SwScale is in the chain and it forces a specific size of the output, the
         * SizeChangeEvents of the Player do not really notify about changes in the size of the
         * input. Besides, playerScaler will take care of the events in such a case.
         */
        if (ev is SizeChangeEvent
                && (playerScaler == null || playerScaler!!.outputSize == null)) {
            playerSizeChange(ev.sourceController, VideoEvent.REMOTE, ev.width, ev.height)
        }
    }

    /**
     * Notifies this instance that a specific `Player` of remote content has generated a
     * `RealizeCompleteEvent`.
     *
     * @param player the `Player` which is the source of a `RealizeCompleteEvent`.
     * @see MediaDeviceSession.playerRealizeComplete
     */
    override fun playerRealizeComplete(player: Processor) {
        super.playerRealizeComplete(player)
        val visualComponent = getVisualComponent(player)
        visualComponent?.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(ev: ComponentEvent) {
                playerVisualComponentResized(player, ev)
            }
        })
    }

    /**
     * Notify this instance that a specific `Player` of local or remote content/video has
     * generated a `SizeChangeEvent`.
     * cmeng: trigger by user?
     *
     * @param sourceController the `Player` which is the source of the eventFR
     * @param origin [VideoEvent.LOCAL] or [VideoEvent.REMOTE] which specifies the origin of
     * the visual `Component` displaying video which is concerned
     * @param width the width reported in the event
     * @param height the height reported in the event
     * @see SizeChangeEvent
     */
    protected fun playerSizeChange(sourceController: Controller, origin: Int, width: Int, height: Int) {
        /*
         * Invoking anything that is likely to change the UI in the Player thread seems like a
         * performance hit so bring it into the event thread.
         */
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { playerSizeChange(sourceController, origin, width, height) }
            return
        }
        val player = sourceController as Player
        val visualComponent = getVisualComponent(player)

        if (visualComponent != null) {
            /*
             * The Player will notify the new size, and before it reaches the Renderer.
             * The notification/event may as well arrive before the Renderer reflects the
             * new size onto the preferredSize of the Component. In order to make sure the new
             * size is reflected on the preferredSize of the Component before the notification/event
             * arrives to its destination/listener, reflect it as soon as possible i.e. now.
             */
            try {
                val prefSize = visualComponent.preferredSize
                if ((prefSize == null || prefSize.width < 1 || prefSize.height < 1
                                || !VideoLayout.areAspectRatiosEqual(prefSize, width, height)) || prefSize.width < width || prefSize.height < height) {
                    visualComponent.preferredSize = Dimension(width, height)
                }
            } finally {
                fireVideoEvent(SizeChangeVideoEvent(this, visualComponent, origin, width, height), false)
                Timber.d("Remote video size change event: %dx%d", width, height)
            }
        }
    }

    /**
     * Notify this instance that the visual `Component` of a `Player` rendering
     * remote content has been resized.
     *
     * @param player the `Player` rendering remote content the visual `Component` of which has been resized
     * @param ev a `ComponentEvent` which specifies the resized `Component`
     */
    private fun playerVisualComponentResized(player: Processor?, ev: ComponentEvent) {
        if (playerScaler == null) return
        val visualComponent = ev.component

        /*
         * When the visualComponent is not in a UI hierarchy, its size is not expected to be
         * representative of what the user is seeing.
         */
        if (visualComponent.isDisplayable) return
        val outputSize = visualComponent.size
        var outputWidth = outputSize.width.toFloat()
        var outputHeight = outputSize.height.toFloat()
        if (outputWidth < SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH || outputHeight < SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH) return

        /*
         * The size of the output video will be calculated so that it fits into the visualComponent
         * and the video aspect ratio is preserved. The presumption here is that the inputFormat
         * holds the video size with the correct aspect ratio.
         */
        val inputFormat = playerScaler!!.inputFormat
        val inputSize = (inputFormat as VideoFormat).size ?: return
        val inputWidth = inputSize.width
        val inputHeight = inputSize.height
        if (inputWidth < 1 || inputHeight < 1) return

        // Preserve the aspect ratio.
        outputHeight = outputWidth * inputHeight / inputWidth

        // Fit the output video into the visualComponent.
        var scale = false
        val widthRatio: Float
        val heightRatio: Float
        if (abs(outputWidth - inputWidth) < 1) {
            scale = true
            widthRatio = outputWidth / inputWidth
        } else widthRatio = 1f
        if (abs(outputHeight - inputHeight) < 1) {
            scale = true
            heightRatio = outputHeight / inputHeight
        } else heightRatio = 1f
        if (scale) {
            val scaleFactor = min(widthRatio, heightRatio)
            outputWidth = inputWidth * scaleFactor
            outputHeight = inputHeight * scaleFactor
        }
        outputSize.width = outputWidth.toInt()
        outputSize.height = outputHeight.toInt()
        val playerScalerOutputSize = playerScaler!!.outputSize
        if (playerScalerOutputSize == null) playerScaler!!.outputSize = outputSize else {
            /*
             * If we are not going to make much of a change, do not even bother because any scaling
             * in the Renderer will not be noticeable anyway.
             */
            val outputWidthDelta = outputSize.width - playerScalerOutputSize.width
            val outputHeightDelta = outputSize.height - playerScalerOutputSize.height
            if (outputWidthDelta < -1 || outputWidthDelta > 1 || outputHeightDelta < -1 || outputHeightDelta > 1) {
                playerScaler!!.outputSize = outputSize
            }
        }
    }

    /**
     * Removes `RTCPFeedbackMessageCreateListener`.
     *
     * @param listener the listener to remove
     */
    fun removeRTCPFeedbackMessageCreateListener(listener: RTCPFeedbackMessageCreateListener) {
        synchronized(rtcpFeedbackMessageCreateListeners) { rtcpFeedbackMessageCreateListeners.remove(listener) }
    }

    /**
     * Removes a specific `VideoListener` from this instance in order to have to no longer
     * receive notifications when visual/video `Component`s are being added and removed.
     *
     * @param listener the `VideoListener` to no longer be notified when visual/video
     * `Component`s are being added or removed in this instance
     */
    fun removeVideoListener(listener: VideoListener) {
        videoNotifierSupport.removeVideoListener(listener)
    }

    /**
     * Sets the `RTPConnector` that will be used to initialize some codec for RTCP feedback.
     *
     * @param rtpConnector the RTP connector
     */
    open fun setConnector(rtpConnector: AbstractRTPConnector?) {
        this.rtpConnector = rtpConnector
    }

    /**
     * Sets the `MediaFormat` in which this `MediaDeviceSession` outputs the media
     * captured by its `MediaDevice`.
     *
     * @param format the `MediaFormat` in which this `MediaDeviceSession` is to output the
     * media captured by its `MediaDevice`
     */
    override fun setFormat(format: MediaFormat) {
        if (format is VideoMediaFormat
                && format.frameRate != -1f) {
            val frameRateControl = getCaptureDevice()!!.getControl(FrameRateControl::class.java.name) as FrameRateControl?

            if (frameRateControl != null) {
                var frameRate = format.frameRate
                val maxSupportedFrameRate = frameRateControl.maxSupportedFrameRate
                if ((maxSupportedFrameRate > 0) && (frameRate > maxSupportedFrameRate)) frameRate = maxSupportedFrameRate
                if (frameRate > 0) {
                    frameRateControl.frameRate = frameRate
                    Timber.i("video send FPS: %s", frameRate)
                }
            }
        }
        super.setFormat(format)
    }

    /**
     * Sets the `KeyFrameControl` to be used by this `VideoMediaDeviceSession` as a
     * means of control over its key frame-related logic.
     *
     * @param keyFrameControl the `KeyFrameControl` to be used by this `VideoMediaDeviceSession` as a
     * means of control over its key frame-related logic
     */
    fun setKeyFrameControl(keyFrameControl: KeyFrameControl?) {
        if (this.keyFrameControl != keyFrameControl) {
            if (this.keyFrameControl != null && keyFrameRequester != null)
                this.keyFrameControl!!.removeKeyFrameRequester(keyFrameRequester!!)

            this.keyFrameControl = keyFrameControl

            if (this.keyFrameControl != null && keyFrameRequester != null)
                this.keyFrameControl!!.addKeyFrameRequester(-1, keyFrameRequester!!)
        }
    }

    /**
     * Set the local SSRC.
     *
     * @param localSSRC local SSRC
     */
    fun setLocalSSRC(localSSRC: Long) {
        this.localSSRC = localSSRC
    }

    /**
     * Sets the size of the output video.
     *
     * @param size the size of the output video
     */
    fun setOutputSize(size: Dimension?) {
        val equal = if (size == null) outputSize == null else size == outputSize
        if (!equal) {
            outputSize = size
            outputSizeChanged = true
        }
    }

    /**
     * Sets the `MediaFormatImpl` in which a specific `Processor` producing media to
     * be streamed to the remote peer is to output.
     *
     * @param processor the `Processor` to set the output `MediaFormatImpl` of
     * @param mediaFormat the `MediaFormatImpl` to set on `processor`
     * @see MediaDeviceSession.setProcessorFormat
     */
    override fun setProcessorFormat(processor: Processor, mediaFormat: MediaFormatImpl<out Format>) {
        var format = mediaFormat.format
        /*
         * Add a size in the output format. As VideoFormat has no setter, we recreate the object.
         * Also check whether capture device can output such a size.
         */
        if (outputSize != null && outputSize!!.width > 0 && outputSize!!.height > 0) {
            val deviceSize = (captureDeviceFormat as VideoFormat).size
            val videoFormatSize: Dimension?
            if (deviceSize != null && (deviceSize.width > outputSize!!.width || deviceSize.height > outputSize!!.height)) {
                videoFormatSize = outputSize
            } else {
                videoFormatSize = deviceSize
                outputSize = null
            }
            val videoFormat = format as VideoFormat
            /*
             * FIXME The assignment to the local variable format makes no difference because it is
             * no longer user afterwards.
             */
            format = VideoFormat(videoFormat.encoding, videoFormatSize,
                    videoFormat.maxDataLength, videoFormat.dataType,
                    videoFormat.frameRate)
        } else outputSize = null
        super.setProcessorFormat(processor, mediaFormat)
    }

    /**
     * Sets the `MediaFormatImpl` of a specific `TrackControl` of the
     * `Processor` which produces the media to be streamed by this `MediaDeviceSession`
     * to the remote peer. Allows extenders to override the set procedure and to detect when the
     * JMF `Format` of the specified `TrackControl` changes.
     *
     * @param trackControl the `TrackControl` to set the JMF `Format` of
     * @param mediaFormat the `MediaFormatImpl` to be set on the specified `TrackControl`. Though
     * `mediaFormat` encapsulates a JMF `Format`, `format` is to be set
     * on the specified `trackControl` because it may be more specific. In any case,
     * the two JMF `Format`s match. The `MediaFormatImpl` is provided anyway
     * because it carries additional information such as format parameters.
     * @param format the JMF `Format` to be set on the specified `TrackControl`. Though
     * `mediaFormat` encapsulates a JMF `Format`, the specified `format`
     * is to be set on the specified `trackControl` because it may be more specific
     * than the JMF `Format` of the `mediaFormat`
     * @return the JMF `Format` set on `TrackControl` after the attempt to set the
     * specified `mediaFormat` or `null` if the specified `format` was
     * found to be incompatible with `trackControl`
     * @see MediaDeviceSession.setProcessorFormat
     */
    override fun setProcessorFormat(
            trackControl: TrackControl,
            mediaFormat: MediaFormatImpl<out Format>, format: Format?,
    ): Format? {
        var encoder: JNIEncoder? = null
        var scaler: SwScale? = null
        var codecCount = 0

        /*
         * For H.264 we will monitor RTCP feedback. For example, if we receive a PLI/FIR
         * message, we will send a keyframe.
         */
        /*
         * The current Android video capture device system provided H.264 so it is not possible to
         * insert an H.264 encoder in the chain. Ideally, we will want to base the decision on the
         * format of the capture device and not on the operating system. In a perfect worlds, we
         * will re-implement the functionality bellow using a Control interface and we will not
         * bother with inserting customized codecs.
         */
        // aTalk uses external h264, so accept OSUtils.IS_ANDROID ???
        if (!OSUtils.IS_ANDROID && "h264/rtp".equals(format!!.encoding, ignoreCase = true)) {
            encoder = JNIEncoder()

            // packetization-mode
            val formatParameters = mediaFormat.formatParameters
            val packetizationMode = if (formatParameters == null) null else formatParameters[VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP]
            encoder.setPacketizationMode(packetizationMode)

            // additionalCodecSettings
            encoder.setAdditionalCodecSettings(mediaFormat.additionalCodecSettings)
            this.encoder = encoder
            onRTCPFeedbackMessageCreate(encoder)
            synchronized(rtcpFeedbackMessageCreateListeners) { for (l in rtcpFeedbackMessageCreateListeners) l.onRTCPFeedbackMessageCreate(encoder) }
            if (keyFrameControl != null) encoder.setKeyFrameControl(keyFrameControl!!)
            codecCount++
        }
        if (outputSize != null) {
            /*
             * We have been explicitly told to use a specific output size so insert a SwScale into
             * the codec chain which is to take care of the specified output size. However, since
             * the video frames which it will output will be streamed to a remote peer, preserve
             * the aspect ratio of the input.
             */
            scaler = SwScale(fixOddYuv420Size = false, preserveAspectRatio = true)
            scaler.outputSize = outputSize!!
            codecCount++
        }
        val codecs = arrayOfNulls<Codec>(codecCount)
        codecCount = 0
        if (scaler != null) codecs[codecCount++] = scaler
        if (encoder != null) codecs[codecCount++] = encoder
        if (codecCount != 0) {
            /*
             * Add our custom SwScale and possibly RTCP aware codec to the codec chain so that it
             * will be used instead of default.
             */
            try {
                trackControl.setCodecChain(codecs)
            } catch (upiex: UnsupportedPlugInException) {
                Timber.e(upiex, "Failed to add SwScale/JNIEncoder to codec chain")
            }
        }
        return super.setProcessorFormat(trackControl, mediaFormat, format)
    }

    /**
     * Set the remote SSRC.
     *
     * @param remoteSSRC remote SSRC
     */
    fun setRemoteSSRC(remoteSSRC: Long) {
        this.remoteSSRC = remoteSSRC
    }

    /**
     * Sets the indicator which determines whether RTCP feedback Picture Loss Indication (PLI) is
     * to be used to request keyframes.
     *
     * @param useRTCPFeedbackPLI `true` to use PLI otherwise, `false`
     */
    fun setRTCPFeedbackPLI(useRTCPFeedbackPLI: Boolean) {
        if (this.useRTCPFeedbackPLI != useRTCPFeedbackPLI) {
            this.useRTCPFeedbackPLI = useRTCPFeedbackPLI
            if (this.useRTCPFeedbackPLI) {
                if (keyFrameRequester == null) {
                    keyFrameRequester = object : KeyFrameControl.KeyFrameRequester {
                        override fun requestKeyFrame(): Boolean {
                            return keyFrameRequesterRequestKeyFrame(this)
                        }
                    }
                }
                if (keyFrameControl != null)
                    keyFrameControl!!.addKeyFrameRequester(-1, keyFrameRequester!!)
            } else if (keyFrameRequester != null) {
                if (keyFrameControl != null)
                    keyFrameControl!!.removeKeyFrameRequester(keyFrameRequester!!)
                keyFrameRequester = null
            }
        }
    }

    /**
     * Notify this instance that the value of its `startedDirection` property has changed
     * from a specific `oldValue` to a specific `newValue`.
     *
     * @param oldValue the `MediaDirection` which used to be the value of the
     * `startedDirection` property of this instance
     * @param newValue the `MediaDirection` which is the value of the `startedDirection`
     * property of this instance
     */
    override fun startedDirectionChanged(oldValue: MediaDirection?, newValue: MediaDirection) {
        super.startedDirectionChanged(oldValue, newValue)
        try {
            var localPlayer: Player?
            synchronized(localPlayerSyncRoot) { localPlayer = this.localPlayer }
            if (newValue.allowsSending()) {
                if (localPlayer == null) createLocalVisualComponent()
            } else if (localPlayer != null) {
                disposeLocalPlayer(localPlayer!!)
            }
        } catch (t: Throwable) {
            if (t is ThreadDeath) throw t else {
                Timber.e(t, "Failed to start/stop the preview of the local video")
            }
        }

        /*
         * Translate the starting and stopping of the playback into respective VideoEvents for the REMOTE origin.
         */
        for (player in players) {
            val state = player.state

            /*
             * The visual Component of a Player is safe to access and, respectively, report through
             * a VideoEvent only when the Player is Realized.
             */
            if (state < Player.Realized) {
                continue
            }
            if (newValue.allowsReceiving()) {
                if (state != Player.Started) {
                    player.start()
                    val visualComponent = getVisualComponent(player)
                    if (visualComponent != null) {
                        fireVideoEvent(VideoEvent.VIDEO_ADDED, visualComponent, VideoEvent.REMOTE, false)
                    }
                }
            } else {
                /*
                 * cmeng: Video size change is triggered by media decoder and may change due to quality change
                 * Therefore must not dispose of the player when a remote video dimension change
                 * otherwise there is no player when new video streaming/format is received (with no jingle action).
                 */
                val visualComponent = getVisualComponent(player)
                player.stop()
                if (visualComponent != null) {
                    fireVideoEvent(VideoEvent.VIDEO_REMOVED, visualComponent, VideoEvent.REMOTE, false)
                }
            }
        }
    }

    /**
     * Extends `SwScale` in order to provide scaling with high quality to a specific
     * `Player` of remote video.
     */
    private inner class PlayerScaler
    /**
     * Initializes a new `PlayerScaler` instance which is to provide scaling with high
     * quality to a specific `Player` of remote video.
     *
     * @param player the `Player` of remote video into the codec chain of which the new instance is to be set
     */
    (
            /**
             * The `Player` into the codec chain of which this `SwScale` is set.
             */
            private val player: Player,
    ) : SwScale(true) {

        /**
         * The last size reported in the form of a `SizeChangeEvent`.
         */
        private var lastSize: Dimension? = null

        /**
         * Determines when the input video sizes changes and reports it as a
         * `SizeChangeVideoEvent` because `Player` is unable to do it when this
         * `SwScale` is scaling to a specific `outputSize`.
         *
         * @param inBuf input buffer
         * @param outBuf output buffer
         * @return the native `PaSampleFormat`
         * @see SwScale.process
         */
        override fun process(inBuf: Buffer, outBuf: Buffer): Int {
            val result = super.process(inBuf, outBuf)
            if (result == PlugIn.BUFFER_PROCESSED_OK) {
                val inputFormat = inputFormat
                if (inputFormat != null) {
                    val size = (inputFormat as VideoFormat).size
                    if ((size != null) && (size.height >= MIN_SWS_SCALE_HEIGHT_OR_WIDTH) && (size.width >= MIN_SWS_SCALE_HEIGHT_OR_WIDTH)
                            && (size != lastSize)) {
                        lastSize = size
                        playerSizeChange(player, VideoEvent.REMOTE, lastSize!!.width, lastSize!!.height)
                    }
                }
            }
            return result
        }

        /**
         * Ensures that this `SwScale` preserves the aspect ratio of its input video when scaling.
         *
         * @param format format to set
         * @return format
         * @see SwScale.setInputFormat
         */
        override fun setInputFormat(format: Format): Format {
            var ipFormat = format
            ipFormat = super.setInputFormat(ipFormat)!!
            if (ipFormat is VideoFormat) {
                val inputSize = ipFormat.size
                if (inputSize != null && inputSize.width > 0) {
                    val outputSize = outputSize
                    var outputWidth = 0
                    if (outputSize != null && outputSize.width.also { outputWidth = it } > 0) {
                        val outputHeight = (outputWidth * inputSize.height / inputSize.width.toFloat()).toInt()
                        val outputHeightDelta = outputHeight - outputSize.height
                        if (outputHeightDelta < -1 || outputHeightDelta > 1) {
                            super.outputSize = Dimension(outputWidth, outputHeight)
                        }
                    }
                }
            }
            return ipFormat
        }
    }

    companion object {
        /**
         * The image ID of the icon which is to be displayed as the local visual `Component`
         * depicting the streaming of the desktop of the local peer to the remote peer.
         */
        private const val DESKTOP_STREAMING_ICON = "impl.media.DESKTOP_STREAMING_ICON"

        /**
         * Gets the visual `Component` of a specific `Player` if it has one and
         * ignores the failure to access it if the specified `Player` is unrealized.
         *
         * @param player the `Player` to get the visual `Component` of if it has one
         * @return the visual `Component` of the specified `Player` if it has one
         * `null` if the specified `Player` does not have a visual
         * `Component` or the `Player` is unrealized
         */
        private fun getVisualComponent(player: Player): Component? {
            var visualComponent: Component? = null
            if (player.state >= Player.Realized) {
                try {
                    visualComponent = player.visualComponent
                } catch (nre: NotRealizedError) {
                    Timber.w("Called Player#getVisualComponent on unrealized player %s: %s", player, nre.message)
                }
            }
            return visualComponent
        }
    }
}