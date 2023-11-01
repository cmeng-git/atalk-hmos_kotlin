/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.hmos.aTalkApp
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.MediaStreamImpl
import org.atalk.impl.neomedia.ProcessorUtility
import org.atalk.impl.neomedia.control.AbstractControls
import org.atalk.impl.neomedia.format.MediaFormatImpl
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat
import org.atalk.impl.neomedia.protocol.InbandDTMFDataSource
import org.atalk.impl.neomedia.protocol.MuteDataSource
import org.atalk.impl.neomedia.protocol.RewritablePullBufferDataSource
import org.atalk.impl.neomedia.protocol.RewritablePushBufferDataSource
import org.atalk.impl.neomedia.protocol.TranscodingDataSource
import org.atalk.service.neomedia.DTMFInbandTone
import org.atalk.service.neomedia.MediaDirection
import org.atalk.service.neomedia.codec.Constants
import org.atalk.service.neomedia.control.AdvancedAttributesAwareCodec
import org.atalk.service.neomedia.control.FormatParametersAwareCodec
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.util.MediaType
import org.atalk.util.OSUtils
import org.atalk.util.event.PropertyChangeNotifier
import timber.log.Timber
import java.awt.Dimension
import java.io.IOException
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.media.ConfigureCompleteEvent
import javax.media.ControllerClosedEvent
import javax.media.ControllerErrorEvent
import javax.media.ControllerEvent
import javax.media.ControllerListener
import javax.media.Format
import javax.media.Manager
import javax.media.NoProcessorException
import javax.media.NotConfiguredError
import javax.media.NotRealizedError
import javax.media.Player
import javax.media.Processor
import javax.media.RealizeCompleteEvent
import javax.media.Renderer
import javax.media.UnsupportedPlugInException
import javax.media.control.FormatControl
import javax.media.control.TrackControl
import javax.media.format.AudioFormat
import javax.media.format.VideoFormat
import javax.media.protocol.ContentDescriptor
import javax.media.protocol.DataSource
import javax.media.protocol.PullBufferDataSource
import javax.media.protocol.PushBufferDataSource
import javax.media.rtp.ReceiveStream

/**
 * Represents the use of a specific `MediaDevice` by a `MediaStream`.
 *
 * @author Lyubomir Marinov
 * @author Damian Minkov
 * @author Emil Ivov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
open class MediaDeviceSession protected constructor(device: AbstractMediaDevice) : PropertyChangeNotifier() {
    /**
     * The JMF `DataSource` of [.device] through which this instance accesses the media captured by it.
     */
    private var captureDevice: DataSource? = null

    /**
     * The indicator which determines whether [DataSource.connect] has been successfully
     * executed on [.captureDevice].
     */
    private var captureDeviceIsConnected = false

    /**
     * The `ContentDescriptor` which specifies the content type in which this
     * `MediaDeviceSession` is to output the media captured by its `MediaDevice`.
     */
    private var contentDescriptor: ContentDescriptor? = null

    /**
     * Gets the `MediaDevice` associated with this instance and the work of a
     * `MediaStream` with which is represented by it.
     *
     * @return the `MediaDevice` associated with this instance and the work of a
     * `MediaStream` with which is represented by it
     */
    /**
     * The `MediaDevice` used by this instance to capture and play back media.
     */
    val device: AbstractMediaDevice

    /**
     * The last JMF `Format` set to this instance by a call to its
     * [.setFormat] and to be set as the output format of [.processor].
     */
    private var mFormat: MediaFormatImpl<out Format>? = null

    /**
     * The list of playbacks of `ReceiveStream`s and/or `DataSource`s performed by
     * respective `Player`s on the `MediaDevice` represented by this instance. The
     * (read and write) accesses to the field are to be synchronized using [.playbacksLock].
     */
    private val playbacks = LinkedList<Playback>()

    /**
     * The `ReadWriteLock` which is used to synchronize the (read and write) accesses to [.playbacks].
     */
    private val playbacksLock = ReentrantReadWriteLock()

    /**
     * The `ControllerListener` which listens to the `Player`s of [.playbacks] for `ControllerEvent`s.
     */
    private val playerControllerListener = ControllerListener { ev ->
        /**
         * Notifies this `ControllerListener` that the `Controller` which it is registered with has generated an event.
         *
         * @param ev the `ControllerEvent` specifying the `Controller` which is
         * the source of the event, and the very type of the event.
         * @see ControllerListener.controllerUpdate
         */

        // Timber.w("Media device session controller updated: %s", ev.getSource());
        playerControllerUpdate(ev)
    }

    /**
     * The JMF `Processor` which transcodes [.captureDevice] into the format of this instance.
     */
    private var processor: Processor? = null

    /**
     * The `ControllerListener` which listens to [.processor] for `ControllerEvent`s.
     */
    private var processorControllerListener: ControllerListener? = null

    /**
     * The indicator which determines whether [.processor] has received a
     * `ControllerClosedEvent` at an unexpected time in its execution. A value of
     * `false` does not mean that `processor` exists or that it is not closed, it
     * just means that if `processor` failed to be initialized or it received a
     * `ControllerClosedEvent`, it was at an expected time of its execution and that the
     * fact in question was reflected, for example, by setting `processor` to `null`.
     * If there is no `processorIsPrematurelyClosed` field and `processor` is set to
     * `null` or left existing after the receipt of `ControllerClosedEvent`, it will
     * either lead to not firing a `PropertyChangeEvent` for `OUTPUT_DATA_SOURCE`
     * when it has actually changed and, consequently, cause the `SendStream`s of
     * `MediaStreamImpl` to not be recreated or it will be impossible to detect that
     * `processor` cannot have its format set and will thus be left broken even for
     * subsequent calls to [.setFormat].
     */
    private var processorIsPrematurelyClosed = false

    /**
     * The `MediaDirection` in which this `MediaDeviceSession` has been started.
     */
    var startedDirection = MediaDirection.INACTIVE

    /**
     * If the player have to be disposed when we [.close] this instance.
     */
    private var disposePlayerOnClose = true

    /**
     * Whether output size has changed after latest processor config. Used for video streams.
     */
    @JvmField
    protected var outputSizeChanged = false

    /**
     * Whether this device session is used by a stream which uses a translator.
     */
    var useTranslator = false

    /**
     * Initializes a new `MediaDeviceSession` instance which is to represent the use of a
     * specific `MediaDevice` by a `MediaStream`.
     *
     * device the `MediaDevice` the use of which by a `MediaStream` is to be represented by the new instance
     */
    init {
        checkDevice(device)
        this.device = device
    }

    /**
     * Sets the indicator which determines whether this instance is to dispose of its associated
     * player upon closing.
     *
     * @param disposePlayerOnClose `true` to have this instance dispose of its associated player upon closing;
     * otherwise, `false`
     */
    fun setDisposePlayerOnClose(disposePlayerOnClose: Boolean) {
        this.disposePlayerOnClose = disposePlayerOnClose
    }

    /**
     * Adds `ssrc` to the array of SSRC identifiers representing parties that this
     * `MediaDeviceSession` is currently receiving streams from. We use this method mostly
     * as a way of to caching SSRC identifiers during a conference call so that the streams that
     * are sending CSRC lists could have them ready for use rather than have to construct them for
     * every RTP packet.
     *
     * @param ssrc the new SSRC identifier that we'd like to add to the array of `ssrc`
     * identifiers stored by this session.
     */
    protected fun addSSRC(ssrc: Long) {
        // init if necessary
        if (remoteSSRCList == null) {
            remoteSSRCList = longArrayOf(ssrc)
            return
        }

        // check whether we already have this ssrc
        for (aSsrcList in remoteSSRCList!!) {
            if (ssrc == aSsrcList) return
        }

        // resize the array and add the new ssrc to the end.
        val newSsrcList = LongArray(remoteSSRCList!!.size + 1)
        System.arraycopy(remoteSSRCList as Any, 0, newSsrcList, 0, remoteSSRCList!!.size)
        newSsrcList[newSsrcList.size - 1] = ssrc
        remoteSSRCList = newSsrcList
    }

    /**
     * Asserts that a specific `MediaDevice` is acceptable to be set as the
     * `MediaDevice` of this instance. Allows extenders to override and customize the check.
     *
     * @param device the `MediaDevice` to be checked for suitability to become the
     * `MediaDevice` of this instance
     */
    protected open fun checkDevice(device: AbstractMediaDevice) {}

    /**
     * Releases the resources allocated by this instance in the course of its execution and
     * prepares it to be garbage collected.
     *
     * cmeng: should close only the required direction e.g. toggle camera should not close remote video
     * Need to clean up this section
     */
    open fun close(direction: MediaDirection?) {
        try {
            stop(direction)
        } finally {
            /*
             * XXX The order of stopping the playback and capture is important here because when we
             * use echo cancellation the capture accesses data from the playback and thus there is
             * synchronization to avoid segfaults but this synchronization can sometimes lead to a
             * slow stop of the playback. That is why we stop the capture first.
             */

            // capture
            disconnectCaptureDevice()
            closeProcessor()

            // playback
            if (disposePlayerOnClose) disposePlayer()
            processor = null
            captureDevice = null
        }
    }

    /**
     * Makes sure [.processor] is closed.
     */
    private fun closeProcessor() {
        if (processor != null) {
            if (processorControllerListener != null) processor!!.removeControllerListener(processorControllerListener)
            processor!!.stop()
            Timber.log(TimberLog.FINER, "Stopped Processor with hashCode %s", processor.hashCode())
            if (processor!!.state == Processor.Realized) {
                val dataOutput = try {
                    processor!!.dataOutput
                } catch (nre: NotRealizedError) {
                    null
                }
                dataOutput?.disconnect()
            }
            processor!!.deallocate()
            processor!!.close()
            processorIsPrematurelyClosed = false

            /*
             * Once the processor uses the captureDevice, the captureDevice has to be reconnected
             * on its next use.
             */
            disconnectCaptureDevice()
        }
    }

    /**
     * Creates the `DataSource` that this instance is to read captured media from.
     *
     * @return the `DataSource` that this instance is to read captured media from
     */
    protected open fun createCaptureDevice(): DataSource? {
        var captureDevice = device.createOutputDataSource()
        if (captureDevice != null) {
            var muteDataSource = AbstractControls.queryInterface(captureDevice, MuteDataSource::class.java)
            if (muteDataSource == null) {
                // Try to enable muting.
                if (captureDevice is PushBufferDataSource) {
                    captureDevice = RewritablePushBufferDataSource(captureDevice)
                }
                else if (captureDevice is PullBufferDataSource) {
                    captureDevice = RewritablePullBufferDataSource(captureDevice)
                }
                muteDataSource = AbstractControls.queryInterface(captureDevice, MuteDataSource::class.java)
            }
            if (muteDataSource != null) muteDataSource.isMute = isMute
        }
        return captureDevice
    }

    /**
     * Creates a new `Player` for a specific `DataSource` so that it is played
     * back on the `MediaDevice` represented by this instance.
     *
     * @param dataSource the `DataSource` to create a new `Player` for
     * @return a new `Player` for the specified `dataSource`
     */
    protected open fun createPlayer(dataSource: DataSource): Player? {
        var player: Processor? = null
        var exception: Throwable? = null
        try {
            player = device.createPlayer(dataSource)
        } catch (ex: Exception) {
            exception = ex
        }
        if (exception != null) {
            Timber.e(exception, "Failed to create Player for %s", MediaStreamImpl.toString(dataSource))
        }
        else if (player != null) {
            /*
             * We cannot wait for the Player to get configured (e.g. with waitForState) because it
             * will stay in the Configuring state until it reads some media. In the case of a
             * ReceiveStream not sending media (e.g. abnormally stopped), it will leave us blocked.
             */
            player.addControllerListener(playerControllerListener)
            player.configure()
            Timber.log(TimberLog.FINER, "Created Player with hashCode %s for %s",
                player.hashCode(), MediaStreamImpl.toString(dataSource))
        }
        return player
    }

    /**
     * Initializes a new FMJ `Processor` which is to transcode [.captureDevice] into
     * the format of this instance.
     *
     * @return a new FMJ `Processor` which is to transcode `captureDevice` into the
     * format of this instance
     */
    protected open fun createProcessor(): Processor? {
        val captureDevice = getConnectedCaptureDevice()
        if (captureDevice != null) {
            var processor: Processor? = null
            var exception: Throwable? = null
            try {
                processor = Manager.createProcessor(captureDevice)
            } catch (ioe: IOException) {
                exception = ioe
            } catch (ioe: NoProcessorException) {
                exception = ioe
            }
            if (exception != null) Timber.e(exception, "Failed to create Processor for %s", captureDevice)
            else {
                if (processorControllerListener == null) {
                    processorControllerListener = ControllerListener { event ->
                        /**
                         * Notifies this `ControllerListener` that the `Controller`
                         * which it is registered with has generated an event.
                         *
                         * @param event
                         * the `ControllerEvent` specifying the `Controller`
                         * which is the source of the event and the very type of the event
                         * @see ControllerListener.controllerUpdate
                         */
                        /**
                         * Notifies this `ControllerListener` that the `Controller`
                         * which it is registered with has generated an event.
                         *
                         * @param event
                         * the `ControllerEvent` specifying the `Controller`
                         * which is the source of the event and the very type of the event
                         * @see ControllerListener.controllerUpdate
                         */
                        processorControllerUpdate(event)
                    }
                }
                processor!!.addControllerListener(processorControllerListener)
                if (waitForState(processor, Processor.Configured)) {
                    this.processor = processor
                    processorIsPrematurelyClosed = false
                }
                else {
                    if (processorControllerListener != null) processor.removeControllerListener(processorControllerListener)
                    this.processor = null
                }
            }
        }
        return processor
    }

    /**
     * Creates a `ContentDescriptor` to be set on a specific `Processor` of captured
     * media to be sent to the remote peer. Allows extenders to override. The default implementation
     * returns [ContentDescriptor.RAW_RTP].
     *
     * @param processor the `Processor` of captured media to be sent to the remote peer which is to
     * have its `contentDescriptor` set to the returned `ContentDescriptor`
     * @return a `ContentDescriptor` to be set on the specified `processor` of
     * captured media to be sent to the remote peer
     */
    private fun createProcessorContentDescriptor(processor: Processor?): ContentDescriptor {
        return if (contentDescriptor == null) ContentDescriptor(ContentDescriptor.RAW_RTP) else contentDescriptor!!
    }

    /**
     * Initializes a `Renderer` instance which is to be utilized by a specific
     * `Player` in order to play back the media represented by a specific
     * `TrackControl`. Allows extenders to override and, optionally, perform additional
     * configuration of the returned `Renderer`.
     *
     * @param player the `Player` which is to utilize the initialized/returned `Renderer`
     * @param trackControl the `TrackControl` which represents the media to be played back (and,
     * technically, on which the initialized/returned `Renderer` is to be set)
     * @return the `Renderer` which is to be set on the specified `trackControl`. If
     * `null`, [TrackControl.setRenderer] is not invoked on the
     * specified `trackControl`.
     */
    protected open fun createRenderer(player: Player, trackControl: TrackControl): Renderer? {
        return device.createRenderer()
    }

    /**
     * Makes sure [.captureDevice] is disconnected.
     */
    private fun disconnectCaptureDevice() {
        if (captureDevice != null) {
            /*
             * As reported by Carlos Alexandre, stopping before disconnecting resolves a slow disconnect on Linux.
             */
            try {
                captureDevice!!.stop()
            } catch (ioe: IOException) {
                /*
                 * We cannot do much about the exception because we're not really interested in the
                 * stopping but rather in calling DataSource#disconnect() anyway.
                 */
                Timber.e(ioe, "Failed to properly stop captureDevice %s", captureDevice)
            }
            captureDevice!!.disconnect()
            captureDeviceIsConnected = false
        }
    }

    /**
     * Releases the resources allocated by the `Player`s of [.playbacks] in the course
     * of their execution and prepares them to be garbage collected.
     */
    private fun disposePlayer() {
        val writeLock = playbacksLock.writeLock()
        writeLock.lock()
        try {
            for (playback in playbacks) {
                if (playback.player != null) {
                    disposePlayer(playback.player!!)
                    playback.player = null
                }
            }
        } finally {
            writeLock.unlock()
        }
    }

    /**
     * Releases the resources allocated by a specific `Player` in the course of its
     * execution
     * and prepares it to be garbage collected.
     *
     * @param player the `Player` to dispose of
     */
    protected open fun disposePlayer(player: Player) {
        player.removeControllerListener(playerControllerListener)
        player.stop()
        Timber.d("Media call disposed of the player")
        player.deallocate()
        player.close()
    }

    /**
     * Gets the `DataSource` that this instance uses to read captured media from. If it does
     * not exist yet, it is created.
     *
     * @return the `DataSource` that this instance uses to read captured media from
     */
    @Synchronized
    open fun getCaptureDevice(): DataSource? {
        if (captureDevice == null)
            captureDevice = createCaptureDevice()

        return captureDevice
    }

    /**
     * Gets [.captureDevice] in a connected state. If this instance is not connected to
     * `captureDevice` yet, first tries to connect to it. Returns `null` if this
     * instance fails to create `captureDevice` or to connect to it.
     *
     * @return [.captureDevice] in a connected state; `null` if this instance fails to
     * create `captureDevice` or to connect to it
     */
    protected fun getConnectedCaptureDevice(): DataSource? {
        var captureDevice = getCaptureDevice()
        if (captureDevice != null && !captureDeviceIsConnected) {
            /*
         * Give this instance a chance to set up an optimized media codec chain by setting the
         * output Format on the input CaptureDevice.
         */
            try {
                if (mFormat != null) setCaptureDeviceFormat(captureDevice, mFormat!!)
            } catch (t: Throwable) {
                Timber.w(t,
                    "Failed to setup an optimized media codec chain by setting the output Format on the input CaptureDevice")
            }
            var exception: Throwable? = null
            try {
                device.connect(captureDevice)
            } catch (ioex: IOException) {
                exception = ioex
            }
            if (exception == null) captureDeviceIsConnected = true
            else {
                Timber.e(exception, "Failed to connect to %s", MediaStreamImpl.toString(captureDevice))
                captureDevice = null
            }
        }
        return captureDevice
    }

    /**
     * Gets the JMF `Format` in which this instance captures media.
     *
     * @return the JMF `Format` in which this instance captures media.
     */
    private fun getProcessorFormat(): Format? {
        val processor = getProcessor()
        if (processor != null && this.processor === processor
                && !processorIsPrematurelyClosed) {
            val mediaType = mediaType
            for (trackControl in processor.trackControls) {
                if (!trackControl.isEnabled) continue
                val jmfFormat = trackControl.format
                val type = if (jmfFormat is VideoFormat) MediaType.VIDEO else MediaType.AUDIO
                if (mediaType == type) return jmfFormat
            }
        }
        return null
    }

    /**
     * Gets the `MediaFormat` in which this instance captures media from its associated `MediaDevice`.
     *
     * @return the `MediaFormat` in which this instance captures media from its associated `MediaDevice`
     */
    fun getFormat(): MediaFormatImpl<out Format>? {
        /*
         * If the Format of the processor is different than the format of this MediaDeviceSession,
         * we'll likely run into unexpected issues so debug whether there are such cases.
         */
        if (processor != null) {
            val processorFormat = getProcessorFormat()
            val format = if (mFormat == null) null else mFormat!!.format
            val processorFormatMatchesFormat = processorFormat?.matches(format) ?: (format == null)
            if (!processorFormatMatchesFormat) {
                Timber.d("processorFormat != format; processorFormat = `%s`; format = `%s'",
                    processorFormat, format)
            }
        }
        return mFormat
    }

    /**
     * Gets the `MediaType` of the media captured and played back by this instance. It is
     * the same as the `MediaType` of its associated `MediaDevice`.
     *
     * @return the `MediaType` of the media captured and played back by this instance as
     * reported by MediaDevice.getMediaType of its associated `MediaDevice`
     */
    private val mediaType: MediaType
        get() = device.mediaType
    /*
     * Whoever wants the outputDataSource, they expect it to be started in accord with the
     * previously-set direction.
     */

    /**
     * Gets the output `DataSource` of this instance which provides the captured (RTP) data
     * to be sent by `MediaStream` to `MediaStreamTarget`.
     *
     * @return the output `DataSource` of this instance which provides the captured (RTP)
     * data to be sent by `MediaStream` to `MediaStreamTarget`
     */
    open val outputDataSource: DataSource?
        get() {
            val processor = getProcessor()
            val outputDataSource: DataSource?
            if (processor == null
                    || (processor.state < Processor.Realized && !waitForState(processor, Processor.Realized)))
                outputDataSource = null
            else {
                outputDataSource = processor.dataOutput
                if (outputDataSource != null) {
                    Timber.log(TimberLog.FINER, "Processor with hashCode %s provided %s",
                        processor.hashCode(), MediaStreamImpl.toString(outputDataSource))
                }

                /*
                 * Whoever wants the outputDataSource, they expect it to be started in accord with the
                 * previously-set direction.
                 */
                startProcessorInAccordWithDirection(processor)
            }
            return outputDataSource
        }

    /**
     * Gets the information related to the playback of a specific `DataSource` on the
     * `MediaDevice` represented by this `MediaDeviceSession`.
     *
     * @param dataSource the `DataSource` to get the information related to the playback of
     * @return the information related to the playback of the specified `DataSource` on the
     * `MediaDevice` represented by this `MediaDeviceSession`
     */
    private fun getPlayback(dataSource: DataSource): Playback? {
        val readLock = playbacksLock.readLock()
        readLock.lock()
        try {
            for (playback in playbacks) {
                if (playback.dataSource === dataSource) return playback
            }
        } finally {
            readLock.unlock()
        }
        return null
    }

    /**
     * Gets the information related to the playback of a specific `ReceiveStream` on the
     * `MediaDevice` represented by this `MediaDeviceSession`.
     *
     * @param receiveStream the `ReceiveStream` to get the information related to the playback of
     * @return the information related to the playback of the specified `ReceiveStream` on
     * the `MediaDevice` represented by this `MediaDeviceSession`
     */
    private fun getPlayback(receiveStream: ReceiveStream): Playback? {
        val readLock = playbacksLock.readLock()
        readLock.lock()
        try {
            for (playback in playbacks) {
                if (playback.receiveStream == receiveStream) return playback
            }
        } finally {
            readLock.unlock()
        }
        return null
    }

    /**
     * Gets the `Player` rendering the `ReceiveStream` with a specific SSRC.
     *
     * @param ssrc the SSRC of the `ReceiveStream` to get the rendering the `Player` of
     * @return the `Player` rendering the `ReceiveStream` with the specified `ssrc`
     */
    protected fun getPlayer(ssrc: Long): Player? {
        val readLock = playbacksLock.readLock()
        readLock.lock()
        try {
            for (playback in playbacks) {
                val playbackSSRC = 0xFFFFFFFFL and playback.receiveStream!!.ssrc
                if (playbackSSRC == ssrc) return playback.player
            }
        } finally {
            readLock.unlock()
        }
        return null
    }

    /**
     * Gets the `Player`s rendering the `ReceiveStream`s of this instance on its
     * associated `MediaDevice`.
     *
     * @return the `Player`s rendering the `ReceiveStream`s of this instance on its
     * associated `MediaDevice`
     */
    protected val players: List<Player>
        get() {
            val readLock = playbacksLock.readLock()
            val players: MutableList<Player>
            readLock.lock()
            try {
                players = ArrayList(playbacks.size)
                for (playback in playbacks) {
                    if (playback.player != null) players.add(playback.player!!)
                }
            } finally {
                readLock.unlock()
            }
            return players
        }

    /**
     * Gets the JMF `Processor` which transcodes the `MediaDevice` of this instance
     * into the format of this instance. If the `Processor` in question does not exist, the
     * method will create it.
     *
     *
     * **Warning**: Because the method will unconditionally create the `Processor` if it
     * does not exist and because the creation of the `Processor` will connect to the
     * `CaptureDevice` of this instance, extreme care is to be taken when invoking the
     * method in order to ensure that the existence of the `Processor` is really in accord
     * with the rest of the state of this instance. Overall, the method is to be considered
     * private and is to not be invoked outside the `MediaDeviceSession` class.
     *
     *
     * @return the JMF `Processor` which transcodes the `MediaDevice` of this
     * instance into the format of this instance
     */
    private fun getProcessor(): Processor? {
        if (processor == null)
            processor = createProcessor()
        return processor
    }

    /**
     * Gets a list of the `ReceiveStream`s being played back on the `MediaDevice`
     * represented by this instance.
     *
     * @return a list of `ReceiveStream`s being played back on the `MediaDevice`
     * represented by this instance
     */
    val receiveStreams: List<ReceiveStream>
        get() {
            val readLock = playbacksLock.readLock()
            val receiveStreams: ArrayList<ReceiveStream>
            readLock.lock()
            try {
                receiveStreams = ArrayList<ReceiveStream>(playbacks.size)
                for (playback in playbacks) {
                    if (playback.receiveStream != null) receiveStreams.add(playback.receiveStream!!)
                }
            } finally {
                readLock.unlock()
            }
            return receiveStreams
        }

//    /**
//     * Gets the `MediaDirection` in which this instance has been started. For example, a
//     * `MediaDirection` which returns `true` for `allowsSending()` signals
//     * that this instance is capturing media from its `MediaDevice`.
//     *
//     * @return the `MediaDirection` in which this instance has been started
//     */
//    fun getStartedDirection(): MediaDirection {
//        return startedDirection
//    }

    /**
     * Gets a list of the `MediaFormat`s in which this instance is capable of capturing
     * media from its associated `MediaDevice`.
     *
     * @return a new list of `MediaFormat`s in which this instance is capable of capturing
     * media from its associated `MediaDevice`
     */
    val supportedFormats: List<MediaFormat>
        get() {
            val processor = getProcessor()
            val supportedFormats = HashSet<Format>()
            if (processor != null && this.processor === processor
                    && !processorIsPrematurelyClosed) {
                val mediaType = mediaType
                for (trackControl in processor.trackControls) {
                    if (!trackControl.isEnabled) continue
                    for (supportedFormat in trackControl.supportedFormats) {
                        when (mediaType) {
                            MediaType.AUDIO -> if (supportedFormat is AudioFormat) supportedFormats.add(supportedFormat)
                            MediaType.VIDEO -> if (supportedFormat is VideoFormat) supportedFormats.add(supportedFormat)
                            else -> {}
                        }
                    }
                }
            }
            val supportedMediaFormats = ArrayList<MediaFormat>(supportedFormats.size)
            for (format in supportedFormats) supportedMediaFormats.add(MediaFormatImpl.createInstance(format)!!)
            return supportedMediaFormats
        }

    /**
     * Notifies this `MediaDeviceSession` that a `DataSource` has been added for
     * playback on the represented `MediaDevice`.
     *
     * @param playbackDataSource the `DataSource` which has been added for playback on the represented
     * `MediaDevice`
     */
    protected open fun playbackDataSourceAdded(playbackDataSource: DataSource) {}

    /**
     * Notifies this `MediaDeviceSession` that a `DataSource` has been removed from
     * playback on the represented `MediaDevice`.
     *
     * @param playbackDataSource the `DataSource` which has been removed from playback on the represented
     * `MediaDevice`
     */
    protected open fun playbackDataSourceRemoved(playbackDataSource: DataSource) {}

    /**
     * Notifies this `MediaDeviceSession` that a `DataSource` has been changed on the
     * represented `MediaDevice`.
     *
     * @param playbackDataSource the `DataSource` which has been added for playback on the represented
     * `MediaDevice`
     */
    protected open fun playbackDataSourceUpdated(playbackDataSource: DataSource) {}

    /**
     * Notifies this `MediaDeviceSession` that a `DataSource` has been changed on the
     * represented `MediaDevice`.
     *
     * @param playbackDataSource the `DataSource` which has been added for playback on the represented
     * `MediaDevice`
     */
    fun playbackDataSourceChanged(playbackDataSource: DataSource) {
        playbackDataSourceUpdated(playbackDataSource)
    }

    /**
     * Notifies this instance that a specific `Player` of remote content has generated a
     * `ConfigureCompleteEvent`. Allows extenders to carry out additional processing on the `Player`.
     *
     * @param player the `Player` which is the source of a `ConfigureCompleteEvent`
     */
    protected open fun playerConfigureComplete(player: Processor) {
        val tcs = player.trackControls
        if (tcs != null && tcs.isNotEmpty()) {
            for (i in tcs.indices) {
                val tc = tcs[i]
                val renderer = createRenderer(player, tc)
                if (renderer != null) {
                    try {
                        tc.setRenderer(renderer)
                    } catch (upie: UnsupportedPlugInException) {
                        Timber.w(upie, "Failed to set %s renderer on track %s", renderer.javaClass.name, i)
                    }
                }
            }
        }
    }

    /**
     * Gets notified about `ControllerEvent`s generated by a specific `Player` of remote content.
     *
     * Extenders who choose to override are advised to override more specialized methods such as
     * [.playerConfigureComplete] and [.playerRealizeComplete].
     * In any case, extenders overriding this method should call the super implementation.
     *
     * @param ev the `ControllerEvent` specifying the `Controller` which is the source of
     * the event and the very type of the event
     */
    protected open fun playerControllerUpdate(ev: ControllerEvent) {
        if (ev is ConfigureCompleteEvent) {
            val player = ev.getSourceController() as Processor?
            if (player != null) {
                playerConfigureComplete(player)

                /*
                 * To use the processor as a Player we must set its ContentDescriptor to null.
                 */
                try {
                    player.contentDescriptor = null
                } catch (nce: NotConfiguredError) {
                    Timber.e(nce, "Failed to set ContentDescriptor to Player.")
                    return
                }
                player.realize()
            }
        }
        else if (ev is RealizeCompleteEvent) {
            val player = ev.getSourceController() as Processor?
            if (player != null) {
                Timber.w("playerControllerUpdate: RealizeCompleteEvent <= %s", player)
                playerRealizeComplete(player)
                player.start()
            }
        }
    }

    /**
     * Notifies this instance that a specific `Player` of remote content has generated a
     * `RealizeCompleteEvent`. Allows extenders to carry out additional processing on the `Player`.
     *
     * @param player the `Player` which is the source of a `RealizeCompleteEvent`
     */
    protected open fun playerRealizeComplete(player: Processor) {}

    /**
     * Gets notified about `ControllerEvent`s generated by [.processor].
     *
     * @param ev the `ControllerEvent` specifying the `Controller` which is the source of
     * the event and the very type of the event
     */
    protected open fun processorControllerUpdate(ev: ControllerEvent) {
        if (ev is ConfigureCompleteEvent) {
            val processor = ev.getSourceController() as Processor?
            if (processor != null) {
                try {
                    processor.contentDescriptor = createProcessorContentDescriptor(processor)
                } catch (nce: NotConfiguredError) {
                    Timber.e(nce, "Failed to set ContentDescriptor to Processor.")
                }
                if (mFormat != null) setProcessorFormat(processor, mFormat!!)
            }
        }
        else if (ev is ControllerClosedEvent) {
            // cmeng: unsupported hw codec will trigger this event
            if (ev is ControllerErrorEvent) {
                val errMessage = ev.message
                Timber.w("ControllerErrorEvent: %s", errMessage)
                aTalkApp.showToastMessage(errMessage)
            }
            else {
                Timber.d("ControllerClosedEvent: %s", ev.message)
            }
            val processor = ev.getSourceController() as Processor?
            if (processor != null && this.processor === processor) processorIsPrematurelyClosed = true
        }
        else if (ev is RealizeCompleteEvent) {
            val processor = ev.getSourceController() as Processor
            for (fpac in getAllTrackControls(FormatParametersAwareCodec::class.java, processor)) {
                val formatParameters = if (mFormat == null) null else mFormat!!.formatParameters
                if (formatParameters != null) fpac.setFormatParameters(formatParameters)
            }
            for (aaac in getAllTrackControls(
                AdvancedAttributesAwareCodec::class.java, processor)) {
                val advanceAttrs = mFormat?.advancedAttributes
                if (advanceAttrs != null) aaac.setAdvancedAttributes(advanceAttrs)
            }
        }
    }

    /**
     * Removes `ssrc` from the array of SSRC identifiers representing parties that this
     * `MediaDeviceSession` is currently receiving streams from.
     *
     * @param ssrc the SSRC identifier that we'd like to remove from the array of `ssrc`
     * identifiers stored by this session.
     */
    protected fun removeSSRC(ssrc: Long) {
        // find the ssrc
        if (remoteSSRCList == null || remoteSSRCList!!.isEmpty()) {
            // list is already empty so there's nothing to do.
            return
        }

        var index = -1
        for (i in remoteSSRCList!!.indices) {
            if (remoteSSRCList!![i] == ssrc) {
                index = i
                break
            }
        }

        // the ssrc we are trying to remove is not in the list so there's nothing to do.
        if (index < 0 || index >= remoteSSRCList!!.size) {
            return
        }

        // if we get here and the list has a single element this would mean we
        // simply need to empty it as the only element is the one we are removing
        if (remoteSSRCList!!.size == 1) {
            remoteSSRCList = null
            return
        }

        val newSsrcList = LongArray(remoteSSRCList!!.size - 1)
        System.arraycopy(remoteSSRCList!!, 0, newSsrcList, 0, index)
        if (index < remoteSSRCList!!.size - 1) {
            System.arraycopy(remoteSSRCList!!, index + 1, newSsrcList, index, remoteSSRCList!!.size - index - 1)
        }
        remoteSSRCList = newSsrcList
    }

    /**
     * Notifies this instance that a specific `ReceiveStream` has been added to the list of
     * playbacks of `ReceiveStream`s and/or `DataSource`s performed by respective
     * `Player`s on the `MediaDevice` represented by this instance.
     *
     * @param receiveStream the `ReceiveStream` which has been added to the list of playbacks of
     * `ReceiveStream`s and/or `DataSource` s performed by respective
     * `Player`s on the `MediaDevice` represented by this instance
     */
    protected open fun receiveStreamAdded(receiveStream: ReceiveStream) {}

    /**
     * Notifies this instance that a specific `ReceiveStream` has been removed from the list
     * of playbacks of `ReceiveStream`s and/or `DataSource`s performed by respective
     * `Player`s on the `MediaDevice` represented by this instance.
     *
     * @param receiveStream the `ReceiveStream` which has been removed from the list of playbacks of
     * `ReceiveStream`s and/or `DataSource`s performed by respective
     * `Player`s on the `MediaDevice` represented by this instance
     */
    protected open fun receiveStreamRemoved(receiveStream: ReceiveStream) {}
    private fun setCaptureDeviceFormat(captureDevice: DataSource, mediaFormat: MediaFormatImpl<out Format?>) {
        val format = mediaFormat.format
        if (format is AudioFormat) {
            val channels = format.channels
            var sampleRate = if (OSUtils.IS_ANDROID) format.sampleRate else Format.NOT_SPECIFIED.toDouble()
            if (channels != Format.NOT_SPECIFIED || sampleRate != Format.NOT_SPECIFIED.toDouble()) {
                val formatControl = captureDevice.getControl(FormatControl::class.java.name) as FormatControl?
                if (formatControl != null) {
                    val supportedFormats = formatControl.supportedFormats
                    if (supportedFormats != null && supportedFormats.isNotEmpty()) {
                        if (sampleRate != Format.NOT_SPECIFIED.toDouble()) {
                            /*
                             * As per RFC 3551.4.5.2, because of a mistake in RFC 1890 and for
                             * backward compatibility, G.722 should always be announced as 8000
                             * even though it is wideband.
                             */
                            val encoding = format.encoding
                            if ((Constants.G722.equals(encoding, ignoreCase = true)
                                            || Constants.G722_RTP.equals(encoding, ignoreCase = true)) && sampleRate == 8000.0) {
                                sampleRate = 16000.0
                            }
                        }
                        var supportedAudioFormat: Format? = null
                        for (sf in supportedFormats) {
                            if (sf is AudioFormat) {
                                if (Format.NOT_SPECIFIED != channels && sf.channels != channels) continue
                                if (Format.NOT_SPECIFIED.toDouble() != sampleRate && sf.sampleRate != sampleRate) continue
                                supportedAudioFormat = sf
                                break
                            }
                        }
                        if (supportedAudioFormat != null) formatControl.format = supportedAudioFormat
                    }
                }
            }
        }
    }

    /**
     * Sets the `ContentDescriptor` which specifies the content type in which this
     * `MediaDeviceSession` is to output the media captured by its `MediaDevice`. The
     * default content type in which `MediaDeviceSession` outputs the media captured by its
     * `MediaDevice` is [ContentDescriptor.RAW_RTP].
     *
     * @param contentDescriptor the `ContentDescriptor` which specifies the content type in which this
     * `MediaDeviceSession` is to output the media captured by its `MediaDevice`
     */
    fun setContentDescriptor(contentDescriptor: ContentDescriptor?) {
        if (contentDescriptor == null) throw NullPointerException("contentDescriptor")
        this.contentDescriptor = contentDescriptor
    }

    /**
     * Sets the `MediaFormat` in which this `MediaDeviceSession` outputs the media
     * captured by its `MediaDevice`.
     *
     * @param format the `MediaFormat` in which this `MediaDeviceSession` is to output the
     * media captured by its `MediaDevice`
     */
    open fun setFormat(format: MediaFormat) {
        require(mediaType == format.mediaType) { "format" }

        /*
         * We need javax.media.Format and we know how to convert MediaFormat to it only for
         * MediaFormatImpl so assert early.
         */
        // val mediaFormatImpl = format as MediaFormatImpl<out Format>
        this.mFormat = format as MediaFormatImpl<out Format>
        Timber.log(TimberLog.FINER, "Set format %s on %s %s", this.mFormat, javaClass.simpleName, hashCode())

        /*
         * If the processor is after Configured, setting a different format will silently fail.
         * Recreate the processor in order to be able to set the different format.
         */
        if (processor != null) {
            val processorState = processor!!.state
            if (processorState == Processor.Configured) setProcessorFormat(processor!!, this.mFormat!!)
            else if (processorIsPrematurelyClosed
                    || (processorState > Processor.Configured
                            && this.mFormat!!.format != getProcessorFormat())
                    || outputSizeChanged) {
                outputSizeChanged = false
                setProcessor(null)
            }
        }
    }

    /**
     * Sets the `MediaFormatImpl` in which a specific `Processor` producing media to
     * be streamed to the remote peer is to output.
     *
     * @param processor the `Processor` to set the output `MediaFormatImpl` of
     * @param mediaFormat the `MediaFormatImpl` to set on `processor`
     */
    protected open fun setProcessorFormat(processor: Processor, mediaFormat: MediaFormatImpl<out Format>) {
        val trackControls = processor.trackControls
        val mediaType = mediaType
        val format = mediaFormat.format
        for (trackIndex in trackControls.indices) {
            val trackControl = trackControls[trackIndex]
            if (!trackControl.isEnabled) continue
            val supportedFormats = trackControl.supportedFormats
            if (supportedFormats == null || supportedFormats.isEmpty()) {
                trackControl.isEnabled = false
                continue
            }
            var supportedFormat: Format? = null
            when (mediaType) {
                MediaType.AUDIO -> if (supportedFormats[0] is AudioFormat) {
                    supportedFormat = findFirstMatchingFormat(supportedFormats, format)

                    /*
                     * We've failed to find a supported format so try to use whatever we've
                     * been told and, if it fails, the caller will at least know why.
                     */
                    if (supportedFormat == null) supportedFormat = format
                }

                MediaType.VIDEO -> if (supportedFormats[0] is VideoFormat) {
                    supportedFormat = findFirstMatchingFormat(supportedFormats, format)

                    /*
                     * We've failed to find a supported format so try to use whatever we've
                     * been told and, if it fails, the caller will at least know why.
                     */
                    if (supportedFormat == null) supportedFormat = format
                    if (supportedFormat != null) supportedFormat = assertSize(supportedFormat as VideoFormat)
                }
                else -> {}
            }
            if (supportedFormat == null) trackControl.isEnabled = false
            else if (supportedFormat != trackControl.format) {
                val setFormat = setProcessorFormat(trackControl, mediaFormat, supportedFormat)
                if (setFormat == null) Timber.e("Failed to set format of track %s to %s. Processor is in state %s",
                    trackIndex, supportedFormat, processor.state)
                else if (setFormat != supportedFormat) Timber.w("Failed to change format of track %s from %s to %s. Processor is in state %s",
                    trackIndex, setFormat, supportedFormat, processor.state)
                else Timber.log(TimberLog.FINER, "Set format of track %s to %s", trackIndex, setFormat)
            }
        }
    }

    /**
     * Sets the `MediaFormatImpl` of a specific `TrackControl` of the
     * `Processor` which produces the media to be streamed by this
     * `MediaDeviceSession` to the remote peer. Allows extenders to override the set procedure
     * and to detect when the JMF `Format` of the specified `TrackControl` changes.
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
     * specified `format` or `null` if the specified `format` was found
     * to be incompatible with `trackControl`
     */
    protected open fun setProcessorFormat(
            trackControl: TrackControl,
            mediaFormat: MediaFormatImpl<out Format>, format: Format?,
    ): Format? {
        return trackControl.setFormat(format)
    }

    /**
     * Determines whether this `MediaDeviceSession` is set to output "silence" instead of
     * the actual media fed from its `CaptureDevice` .
     *
     * @return `true` if this `MediaDeviceSession` is set to output "silence" instead
     * of the actual media fed from its `CaptureDevice`; otherwise, `false`
     */
    /**
     * The indicator which determines whether this `MediaDeviceSession` is set to output
     * "silence" instead of the actual media captured from [.captureDevice].
     */
    open var isMute = false
        get() {
            val captureDevice = captureDevice ?: return field
            val muteDataSource = AbstractControls.queryInterface(captureDevice, MuteDataSource::class.java)
            return muteDataSource != null && muteDataSource.isMute
        }
        /**
         * Sets the indicator which determines whether this `MediaDeviceSession` is set to
         * output "silence" instead of the actual media fed from its `CaptureDevice`.
         *
         * @param mute `true` to set this `MediaDeviceSession` to output "silence" instead of
         * the actual media fed from its `CaptureDevice`; otherwise, `false`
         */
        set(mute) {
            if (field != mute) {
                field = mute
                AbstractControls.queryInterface(captureDevice, MuteDataSource::class.java)?.isMute = field
            }
        }

    /**
     * Adds a new inband DTMF tone to send.
     *
     * @param tone the DTMF tone to send.
     */
    fun addDTMF(tone: DTMFInbandTone) {
        AbstractControls.queryInterface(captureDevice, InbandDTMFDataSource::class.java)?.addDTMF(tone)
    }

    /**
     * Adds a specific `DataSource` to the list of playbacks of `ReceiveStream`s
     * and/or `DataSource`s performed by respective `Player`s on the
     * `MediaDevice` represented by this instance.
     *
     * @param playbackDataSource the `DataSource` which to be added to the list of playbacks of
     * `ReceiveStream`s and/or `DataSource`s performed by respective
     * `Player`s on the `MediaDevice` represented by this instance
     */
    open fun addPlaybackDataSource(playbackDataSource: DataSource) {
        val writeLock = playbacksLock.writeLock()
        val readLock = playbacksLock.readLock()
        var added = false
        writeLock.lock()
        try {
            var playback = getPlayback(playbackDataSource)
            if (playback == null) {
                if (playbackDataSource is ReceiveStreamPushBufferDataSource) {
                    val receiveStream = playbackDataSource.receiveStream
                    playback = getPlayback(receiveStream)
                }
                if (playback == null) {
                    playback = Playback(playbackDataSource)
                    playbacks.add(playback)
                }
                else playback.dataSource = playbackDataSource
                playback.player = createPlayer(playbackDataSource)
                readLock.lock()
                added = true
            }
        } finally {
            writeLock.unlock()
        }
        if (added) {
            try {
                playbackDataSourceAdded(playbackDataSource)
            } finally {
                readLock.unlock()
            }
        }
    }

    /**
     * Removes a specific `DataSource` from the list of playbacks of `ReceiveStream`s
     * and/or `DataSource`s performed by respective `Player`s on the
     * `MediaDevice` represented by this instance.
     *
     * @param playbackDataSource the `DataSource` which to be removed from the list of playbacks of
     * `ReceiveStream`s and/or `DataSource`s performed by respective
     * `Player`s on the `MediaDevice` represented by this instance
     */
    open fun removePlaybackDataSource(playbackDataSource: DataSource) {
        val writeLock = playbacksLock.writeLock()
        val readLock = playbacksLock.readLock()
        var removed = false
        writeLock.lock()
        try {
            val playback = getPlayback(playbackDataSource)
            if (playback != null) {
                if (playback.player != null) {
                    disposePlayer(playback.player!!)
                    playback.player = null
                }
                playback.dataSource = null
                if (playback.receiveStream == null) playbacks.remove(playback)
                readLock.lock()
                removed = true
            }
        } finally {
            writeLock.unlock()
        }
        if (removed) {
            try {
                playbackDataSourceRemoved(playbackDataSource)
            } finally {
                readLock.unlock()
            }
        }
    }

    /**
     * Sets the JMF `Processor` which is to transcode [.captureDevice] into the format
     * of this instance.
     *
     * @param processor the JMF `Processor` which is to transcode [.captureDevice] into the
     * format of this instance
     */
    private fun setProcessor(processor: Processor?) {
        if (this.processor != processor) {
            closeProcessor()
            this.processor = processor

            /*
             * Since the processor has changed, its output DataSource known to the public has also changed.
             */
            firePropertyChange(OUTPUT_DATA_SOURCE, null, null)
        }
    }

    /**
     * Adds a specific `ReceiveStream` to the list of playbacks of `ReceiveStream`s
     * and/or `DataSource`s performed by respective `Player`s on the
     * `MediaDevice` represented by this instance.
     *
     * @param receiveStream the `ReceiveStream` which to be added to the list of playbacks of
     * `ReceiveStream`s and/or `DataSource`s performed by respective
     * `Player`s on the `MediaDevice` represented by this instance
     */
    open fun addReceiveStream(receiveStream: ReceiveStream) {
        val writeLock = playbacksLock.writeLock()
        val readLock = playbacksLock.readLock()
        var added = false

        writeLock.lock()
        try {
            if (getPlayback(receiveStream) == null) {
                playbacks.add(Playback(receiveStream))
                addSSRC(0xFFFFFFFFL and receiveStream.ssrc)

                // playbackDataSource
                var receiveStreamDataSource = receiveStream.dataSource
                if (receiveStreamDataSource != null) {
                    if (receiveStreamDataSource is PushBufferDataSource) {
                        receiveStreamDataSource = ReceiveStreamPushBufferDataSource(
                            receiveStream, receiveStreamDataSource, true)
                    }
                    else {
                        Timber.w("Adding ReceiveStream with DataSource not of type PushBufferDataSource but "
                                + "%s which may prevent the ReceiveStream from properly transferring to another"
                                + " MediaDevice if such a need arises.", receiveStreamDataSource.javaClass.simpleName)
                    }
                    addPlaybackDataSource(receiveStreamDataSource)
                }
                readLock.lock()
                added = true
            }
        } finally {
            writeLock.unlock()
        }
        if (added) {
            try {
                receiveStreamAdded(receiveStream)
            } finally {
                readLock.unlock()
            }
        }
    }

    /**
     * Removes a specific `ReceiveStream` from the list of playbacks of
     * `ReceiveStream`s and/or `DataSource`s performed by respective `Player`s
     * on the `MediaDevice` represented by this instance.
     *
     * @param receiveStream the `ReceiveStream` which to be removed from the list of playbacks of
     * `ReceiveStream`s and/or `DataSource`s performed by respective
     * `Player`s on the `MediaDevice` represented by this instance
     */
    open fun removeReceiveStream(receiveStream: ReceiveStream) {
        val writeLock = playbacksLock.writeLock()
        val readLock = playbacksLock.readLock()
        var removed = false
        writeLock.lock()
        try {
            val playback = getPlayback(receiveStream)
            if (playback != null) {
                removeSSRC(0xFFFFFFFFL and receiveStream.ssrc)
                if (playback.dataSource != null) removePlaybackDataSource(playback.dataSource!!)
                if (playback.dataSource != null) {
                    Timber.w("Removing ReceiveStream with an associated DataSource.")
                }
                playbacks.remove(playback)
                readLock.lock()
                removed = true
            }
        } finally {
            writeLock.unlock()
        }
        if (removed) {
            try {
                receiveStreamRemoved(receiveStream)
            } finally {
                readLock.unlock()
            }
        }
    }

    /**
     * Returns the list of SSRC identifiers that this device session is handling streams from. In
     * this case (i.e. the case of a device session handling a single remote party) we would rarely
     * (if ever) have more than a single SSRC identifier returned. However, we would also be using
     * the same method to query a device session operating over a mixer in which case we would have
     * the SSRC IDs of all parties currently contributing to the mixing.
     *
     * @return a `long[]` array of SSRC identifiers that this device session is handling streams from.
     */
    /**
     * The list of SSRC identifiers representing the parties that we are currently handling receive streams from.
     */
    open var remoteSSRCList: LongArray? = null
        /**
         * Sets the list of SSRC identifiers that this device stores to `newSsrcList` and
         * fires a `PropertyChangeEvent` for the `SSRC_LIST` property.
         */
        set(newSsrcList) {
            val oldSsrcList = remoteSSRCList
            field = newSsrcList
            firePropertyChange(SSRC_LIST, oldSsrcList, remoteSSRCList)
        }

    /**
     * Starts the processing of media in this instance in a specific direction.
     *
     * @param direction a `MediaDirection` value which represents the direction of the processing of
     * media to be started. For example, [MediaDirection.SENDRECV] to start both
     * capture and playback of media in this instance or [MediaDirection.SENDONLY] to
     * only start the capture of media in this instance
     */
    fun start(direction: MediaDirection?) {
        if (direction == null) throw NullPointerException("direction")
        val oldValue = startedDirection
        startedDirection = startedDirection.or(direction)
        if (oldValue != startedDirection) startedDirectionChanged(oldValue, startedDirection)
    }

    /**
     * Notifies this instance that the value of its `startedDirection` property has changed
     * from a specific `oldValue` to a specific `newValue`. Allows extenders to
     * override and perform additional processing of the change. Overriding implementations must
     * call this implementation in order to ensure the proper execution of this `MediaDeviceSession`.
     *
     * @param oldValue the `MediaDirection` which used to be the value of the `startedDirection` property of this instance
     * @param newValue the `MediaDirection` which is the value of the `startedDirection`
     * property of this instance
     */
    protected open fun startedDirectionChanged(oldValue: MediaDirection?, newValue: MediaDirection) {
        if (newValue.allowsSending()) {
            val processor = getProcessor()
            processor?.let { startProcessorInAccordWithDirection(it) }
        }
        else if (processor != null && processor!!.state > Processor.Configured) {
            processor!!.stop()
            Timber.log(TimberLog.FINER, "Stopped Processor with hashCode %s", processor.hashCode())
        }
    }

    /**
     * Starts a specific `Processor` if this `MediaDeviceSession` has been started
     * and
     * the specified `Processor` is not started.
     *
     * @param processor the `Processor` to start
     */
    private fun startProcessorInAccordWithDirection(processor: Processor) {
        if (startedDirection.allowsSending() && processor.state != Processor.Started) {
            processor.start()
            Timber.log(TimberLog.FINER, "Started Processor with hashCode %s", processor.hashCode())
        }
    }

    /**
     * Stops the processing of media in this instance in a specific direction.
     *
     * @param direction a `MediaDirection` value which represents the direction of the processing of
     * media to be stopped. For example, [MediaDirection.SENDRECV] to stop both capture
     * and playback of media in this instance or [MediaDirection.SENDONLY] to only stop
     * the capture of media in this instance
     */
    fun stop(direction: MediaDirection?) {
        if (direction == null) throw NullPointerException("direction")
        val oldValue = startedDirection
        when (startedDirection) {
            MediaDirection.SENDRECV -> when {
                direction == startedDirection -> startedDirection = MediaDirection.INACTIVE
                direction.allowsReceiving() -> startedDirection = MediaDirection.SENDONLY
                direction.allowsSending() -> startedDirection = MediaDirection.RECVONLY
            }
            MediaDirection.SENDONLY -> if (direction.allowsSending()) startedDirection = MediaDirection.INACTIVE
            MediaDirection.RECVONLY -> if (direction.allowsReceiving()) startedDirection = MediaDirection.INACTIVE
            MediaDirection.INACTIVE -> {}
        }
        if (oldValue != startedDirection) startedDirectionChanged(oldValue, startedDirection)
    }

    /**
     * Copies the playback part of a specific `MediaDeviceSession` into this instance.
     *
     * @param deviceSession the `MediaDeviceSession` to copy the playback part of into this instance
     */
    open fun copyPlayback(deviceSession: MediaDeviceSession) {
        if (deviceSession.disposePlayerOnClose) {
            Timber.e("Cannot copy playback if MediaDeviceSession has closed it")
        }
        else {
            /*
             * TODO Technically, we should be synchronizing the (read and write) accesses to the
             * playbacks fields. In practice, we are not doing it because it likely was the easiest
             * to not bother with it at the time of its writing.
             */
            playbacks.addAll(deviceSession.playbacks)
            remoteSSRCList = deviceSession.remoteSSRCList
        }
    }

    /**
     * Represents the information related to the playback of a `DataSource` on the
     * `MediaDevice` represented by a `MediaDeviceSession`. The `DataSource`
     * may have an associated `ReceiveStream`.
     */
    private class Playback {
        /**
         * The `DataSource` the information related to the playback of which is represented
         * by this instance and which is associated with [.receiveStream].
         */
        var dataSource: DataSource? = null

        /**
         * The `ReceiveStream` the information related to the playback of which is
         * represented by this instance and which is associated with [.dataSource].
         */
        var receiveStream: ReceiveStream? = null

        /**
         * The `Player` which performs the actual playback.
         */
        var player: Player? = null

        /**
         * Initializes a new `Playback` instance which is to represent the information
         * related to the playback of a specific `DataSource`.
         *
         * @param dataSource the `DataSource` the information related to the playback of which is to be
         * represented by the new instance
         */
        constructor(dataSource: DataSource?) {
            this.dataSource = dataSource
        }

        /**
         * Initializes a new `Playback` instance which is to represent the information
         * related to the playback of a specific `ReceiveStream`.
         *
         * @param receiveStream the `ReceiveStream` the information related to the playback of which is to
         * be represented by the new instance
         */
        constructor(receiveStream: ReceiveStream?) {
            this.receiveStream = receiveStream
        }
    }

    /**
     * Returns the `TranscodingDataSource` associated with `receiveStream`.
     *
     * @param receiveStream the `ReceiveStream` to use
     * @return the `TranscodingDataSource` associated with `receiveStream`.
     */
    fun getTranscodingDataSource(receiveStream: ReceiveStream): TranscodingDataSource? {
        var transcodingDataSource: TranscodingDataSource? = null
        if (device is AudioMixerMediaDevice) {
            transcodingDataSource = device.getTranscodingDataSource(receiveStream.dataSource)
        }
        return transcodingDataSource
    }

    /**
     * Searches for controls of type `controlType` in the `TrackControl`s of the
     * `Processor` used to transcode the `MediaDevice` of this instance into the
     * format of this instance. Returns a `Set` of instances of class `controlType`,
     * always non-null.
     *
     * @param controlType the name of the class to search for.
     * @return A non-null `Set` of all `controlType`s found.
     */
    fun <T> getEncoderControls(controlType: Class<T>): Set<T> {
        return getAllTrackControls(controlType, processor)
    }

    /**
     * Searches for controls of type `controlType` in the `TrackControl`s of the
     * `Processor` used to decode `receiveStream`. Returns a `Set` of
     * instances of class `controlType`, always non-null.
     *
     * @param receiveStream the `ReceiveStream` whose `Processor`'s `TrackControl`s are to be
     * searched.
     * @param controlType the name of the class to search for.
     * @return A non-null `Set` of all `controlType`s found.
     */
    fun <T> getDecoderControls(receiveStream: ReceiveStream, controlType: Class<T>): Set<T> {
        val transcodingDataSource = getTranscodingDataSource(receiveStream)
        return if (transcodingDataSource == null) emptySet()
        else {
            getAllTrackControls(controlType, transcodingDataSource.transcodingProcessor)
        }
    }

    /**
     * Returns the `Set` of controls of type `controlType`, which are controls for
     * some of `processor`'s `TrackControl`s.
     *
     * @param controlType the name of the class to search for.
     * @param processor the `Processor` whose `TrackControls`s will be searched.
     * @return A non-null `Set` of all `controlType`s found.
     */
    private fun <T> getAllTrackControls(controlType: Class<T>, processor: Processor?): MutableSet<T> {
        var controls: MutableSet<T>? = null

        if (processor != null && processor.state >= Processor.Realized) {
            val trackControls = processor.trackControls
            if (trackControls != null && trackControls.isNotEmpty()) {
                for (trackControl in trackControls) {
                    val obj = trackControl.getControl(controlType.name)
                    if (controlType.isInstance(obj)) {
                        if (controls == null) controls = HashSet()
                        controls.add(obj as T)
                    }
                }
            }
        }
        if (controls == null) controls = Collections.emptySet()
        return controls!!
    }

    companion object {
        /**
         * The name of the `MediaDeviceSession` instance property the value of which represents
         * the output `DataSource` of the `MediaDeviceSession` instance which provides
         * the captured (RTP) data to be sent by `MediaStream` to `MediaStreamTarget`.
         */
        const val OUTPUT_DATA_SOURCE = "OUTPUT_DATA_SOURCE"

        /**
         * The name of the property that corresponds to the array of SSRC identifiers that we store in this
         * `MediaDeviceSession` instance and that we update upon adding and removing `ReceiveStream`
         */
        const val SSRC_LIST = "SSRC_LIST"

        /**
         * For JPEG, we know that they only work for particular sizes. So we'll perform extra
         * checking here to make sure they are of the right sizes.
         *
         * @param sourceFormat the original format to check the size of
         * @return the modified `VideoFormat` set to the size we support
         */
        private fun assertSize(sourceFormat: VideoFormat): VideoFormat {
            val width: Int
            val height: Int

            // JPEG
            if (sourceFormat.matches(Format(VideoFormat.JPEG_RTP))) {
                val size = sourceFormat.size

                // For JPEG, make sure width and height are divisible by 8.
                width = if (size.width % 8 == 0) size.width else size.width / 8 * 8
                height = if (size.height % 8 == 0) size.height else size.height / 8 * 8
            }
            else {
                // For other video format, we'll just leave it alone then.
                return sourceFormat
            }
            val result = VideoFormat(
                null,
                Dimension(width, height),
                Format.NOT_SPECIFIED,
                null,
                Format.NOT_SPECIFIED.toFloat())
            return result.intersects(sourceFormat) as VideoFormat
        }

        /**
         * Finds the first `Format` instance in a specific list of `Format`s which
         * matches a specific `Format`. The implementation considers a pair of
         * `Format`s matching if they have the same encoding.
         *
         * @param formats the array of `Format`s to be searched for a match to the specified `format`
         * @param format the `Format` to search for a match in the specified `formats`
         * @return the first element of `formats` which matches `format` i.e. is of the same encoding
         */
        private fun findFirstMatchingFormat(formats: Array<Format>, format: Format?): Format? {
            val formatSampleRate = if (format is AudioFormat) format.sampleRate else Format.NOT_SPECIFIED.toDouble()
            val parameterizedVideoFormat = if (format is ParameterizedVideoFormat) format else null
            for (match in formats) {
                if (match.isSameEncoding(format)) {
                    /*
                 * The encoding alone is, of course, not enough. For example, AudioFormats may have
                 * different sample rates (i.e. clock rates as we call them in MediaFormat).
                 */
                    if (match is AudioFormat) {
                        if (formatSampleRate != Format.NOT_SPECIFIED.toDouble()) {
                            val matchSampleRate = match.sampleRate
                            if (matchSampleRate != Format.NOT_SPECIFIED.toDouble() && matchSampleRate != formatSampleRate) continue
                        }
                    }
                    else if (match is ParameterizedVideoFormat) {
                        if (!match.formatParametersMatch(format)) continue
                    }
                    else if (parameterizedVideoFormat != null) {
                        if (!parameterizedVideoFormat.formatParametersMatch(match)) continue
                    }
                    return match
                }
            }
            return null
        }

        /**
         * Waits for the specified JMF `Processor` to enter the specified `state` and
         * returns `true` if `processor` has successfully entered `state` or
         * `false` if `process` has failed to enter `state`.
         *
         * @param processor the JMF `Processor` to wait on
         * @param state the state as defined by the respective `Processor` state constants to wait
         * `processor` to enter
         * @return `true` if `processor` has successfully entered `state`; otherwise, `false`
         */
        private fun waitForState(processor: Processor, state: Int): Boolean {
            return ProcessorUtility().waitForState(processor, state)
        }
    }
}