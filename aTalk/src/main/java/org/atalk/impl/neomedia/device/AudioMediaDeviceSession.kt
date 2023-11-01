/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.impl.neomedia.audiolevel.AudioLevelEffect
import org.atalk.impl.neomedia.audiolevel.AudioLevelEffect2
import org.atalk.impl.neomedia.jmfext.media.renderer.audio.AbstractAudioRenderer
import org.atalk.service.neomedia.VolumeControl
import org.atalk.service.neomedia.event.SimpleAudioLevelListener
import timber.log.Timber
import javax.media.Codec
import javax.media.ConfigureCompleteEvent
import javax.media.ControllerEvent
import javax.media.Player
import javax.media.Processor
import javax.media.Renderer
import javax.media.UnsupportedPlugInException
import javax.media.control.TrackControl
import javax.media.format.AudioFormat

/**
 * Extends `MediaDeviceSession` to add audio-specific functionality.
 *
 * @author Emil Ivov
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
open class AudioMediaDeviceSession
/**
 * Initializes a new `MediaDeviceSession` instance which is to represent the use of a
 * specific `MediaDevice` by a `MediaStream`.
 *
 * @param device the `MediaDevice` the use of which by a `MediaStream` is to be
 * represented by the new instance
 */
(device: AbstractMediaDevice) : MediaDeviceSession(device) {
    /**
     * The `Effect` that we will register with our `DataSource` in order to measure
     * the audio levels of the local user.
     */
    private val localUserAudioLevelEffect = AudioLevelEffect()

    /**
     * The `Effect` that we will register with our output data source in order to measure the
     * outgoing audio levels.
     */
    private var outputAudioLevelEffect: AudioLevelEffect2? = null

    /**
     * The `VolumeControl` which is to control the volume (level) of the audio (to be) played
     * back by this instance.
     */
    private var outputVolumeControl: VolumeControl? = null

    /**
     * The effect that we will register with our stream in order to measure audio levels of the remote user audio.
     */
    private val streamAudioLevelEffect = AudioLevelEffect()

    /**
     * Copies the playback part of a specific `MediaDeviceSession` into this instance.
     *
     * @param deviceSession the `MediaDeviceSession` to copy the playback part of into this instance
     */
    override fun copyPlayback(deviceSession: MediaDeviceSession) {
        val amds = deviceSession as AudioMediaDeviceSession
        setStreamAudioLevelListener(amds.streamAudioLevelEffect.getAudioLevelListener()!!)
        setLocalUserAudioLevelListener(amds.localUserAudioLevelEffect.getAudioLevelListener())
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation in order to configure the `VolumeControl` of the
     * returned `Renderer` for the purposes of having call/telephony conference-specific volume (levels).
     */
    override fun createRenderer(player: Player, trackControl: TrackControl): Renderer? {
        val renderer = super.createRenderer(player, trackControl)
        if (renderer != null) setVolumeControl(renderer, outputVolumeControl)
        return renderer
    }

    /**
     * Returns the last audio level that was measured by this device session for the specified `ssrc`.
     *
     * @param ssrc the SSRC ID whose last measured audio level we'd like to retrieve.
     * @return the audio level that was last measured for the specified `ssrc` or `-1`
     * if no level has been cached for that ID.
     */
    open fun getLastMeasuredAudioLevel(ssrc: Long): Int {
        return -1
    }

    /**
     * Returns the last audio level that was measured by the underlying mixer for local user.
     *
     * @return the audio level that was last measured for the local user.
     */
    open val lastMeasuredLocalUserAudioLevel: Int
        get() = -1

    /**
     * Called by [MediaDeviceSession.playerControllerUpdate] when the
     * player associated with this session's `ReceiveStream` moves enters the
     * `Configured` state, so we use the occasion to add our audio level effect.
     *
     * @param player the `Player` which is the source of a `ConfigureCompleteEvent`
     * @see MediaDeviceSession.playerConfigureComplete
     */
    override fun playerConfigureComplete(player: Processor) {
        super.playerConfigureComplete(player)
        val tcs = player.trackControls
        if (tcs != null) {
            for (tc in tcs) {
                if (tc.format is AudioFormat) {
                    // Assume there is only one audio track.
                    try {
                        registerStreamAudioLevelJMFEffect(tc)
                    } catch (upie: UnsupportedPlugInException) {
                        Timber.e(upie, "Failed to register stream audio level Effect")
                    }
                    break
                }
            }
        }
    }

    /**
     * Gets notified about `ControllerEvent`s generated by the processor reading our capture
     * data source, calls the corresponding method from the parent class so that it would initialize
     * the processor and then adds the level effect for the local user audio levels.
     *
     * @param ev the `ControllerEvent` specifying the `Controller` which is the source of
     * the event and the very type of the event
     */
    override fun processorControllerUpdate(ev: ControllerEvent) {
        super.processorControllerUpdate(ev)

        // when using translator we do not want any audio level effect
        if (useTranslator) {
            return
        }
        if (ev is ConfigureCompleteEvent) {
            val processor = ev.getSourceController() as Processor?
            processor?.let { registerLocalUserAudioLevelEffect(it) }
        }
    }

    /**
     * Creates an audio level effect and add its to the codec chain of the `TrackControl`
     * assuming that it only contains a single track.
     *
     * @param processor the processor on which track control we need to register a level effect with.
     */
    protected open fun registerLocalUserAudioLevelEffect(processor: Processor) {
        // we register the effect regardless of whether or not we have any listeners at this point because we won't get
        // a second chance. however the effect would do next to nothing unless we register a first listener with it.
        //
        // XXX: i am assuming that a single effect could be reused multiple times
        // if that turns out not to be the case we need to create a new instance here.

        // here we add sound level indicator for captured media from the microphone if there are interested listeners
        try {
            val tcs = processor.trackControls
            if (tcs != null) {
                for (tc in tcs) {
                    if (tc.format is AudioFormat) {
                        // we assume a single track
                        tc.setCodecChain(arrayOf<Codec>(localUserAudioLevelEffect))
                        break
                    }
                }
            }
        } catch (ex: UnsupportedPlugInException) {
            Timber.e(ex, "Effects are not supported by the datasource.")
        }
    }

    /**
     * Adds an audio level effect to the tracks of the specified `trackControl` and so that
     * we would notify interested listeners of audio level changes.
     *
     * @param trackControl the `TrackControl` where we need to register a level effect that would measure
     * the audio levels of the `ReceiveStream` associated with this class.
     * @throws UnsupportedPlugInException if we fail to add our sound level effect to the track control of
     * `mediaStream`'s processor.
     */
    @Throws(UnsupportedPlugInException::class)
    private fun registerStreamAudioLevelJMFEffect(trackControl: TrackControl) {
        // we register the effect regardless of whether or not we have any
        // listeners at this point because we won't get a second chance.
        // however the effect would do next to nothing unless we register a
        // first listener with it. Assume there is only one audio track
        trackControl.setCodecChain(arrayOf<Codec>(streamAudioLevelEffect))
    }

    /**
     * Sets the `SimpleAudioLevelListener` that this session should be notifying about
     * changes in local audio level related information. This class only supports a single listener
     * for audio changes per source (i.e. stream or data source). Audio changes are generally quite
     * time intensive (~ 50 per second) so we are doing this in order to reduce the number of
     * objects associated with the process (such as event instances listener list iterators and sync copies).
     *
     * @param listener the `SimpleAudioLevelListener` to add
     */
    open fun setLocalUserAudioLevelListener(listener: SimpleAudioLevelListener?) {
        if (useTranslator) {
            return
        }
        localUserAudioLevelEffect.setAudioLevelListener(listener)
    }

    /**
     * Sets the `VolumeControl` which is to control the volume (level) of the audio (to be)
     * played back by this instance.
     *
     * @param outputVolumeControl the `VolumeControl` which is to be control the volume (level) of the audio (to
     * be) played back by this instance
     */
    open fun setOutputVolumeControl(outputVolumeControl: VolumeControl) {
        this.outputVolumeControl = outputVolumeControl
    }

    /**
     * Sets `listener` as the `SimpleAudioLevelListener` that we are going to notify
     * every time a change occurs in the audio level of the media that this device session is
     * receiving from the remote party. This class only supports a single listener for audio changes
     * per source (i.e. stream or data source). Audio changes are generally quite time intensive (~
     * 50 per second) so we are doing this in order to reduce the number of objects associated with
     * the process (such as event instances listener list iterators and sync copies).
     *
     * @param listener the `SimpleAudioLevelListener` that we want notified for audio level changes in
     * the remote participant's media.
     */
    open fun setStreamAudioLevelListener(listener: SimpleAudioLevelListener?) {
        if (useTranslator) {
            return
        }
        streamAudioLevelEffect.setAudioLevelListener(listener)
    }

    /**
     * Performs additional configuration on the `Processor`, after it is `configure`d,
     * but before it is `realize`d. Adds the `AudioLevelEffect2` instance to the codec
     * chain, if necessary, in order to enabled audio level measurements.
     *
     * {@inheritDoc}
     */
    override fun createProcessor(): Processor? {
        val processor = super.createProcessor()

        // when using translator we do not want any audio level effect
        if (useTranslator) {
            return processor
        }
        if (processor != null) {
            if (outputAudioLevelEffect != null) {
                for (track in processor.trackControls) {
                    try {
                        track.setCodecChain(arrayOf(outputAudioLevelEffect))
                    } catch (upie: UnsupportedPlugInException) {
                        Timber.w(upie, "Failed to insert the audio level Effect. Output levels will not be included.")
                    }
                }
            }
        }
        return processor
    }

    /**
     * Enables or disables measuring audio levels for the output `DataSource` of this
     * `AudioMediaDeviceSession`.
     *
     * Note that if audio levels are to be enabled, this method needs to be called (with
     * `enabled` set to `true`) before the output `DataSource`, or the
     * `Processor` are accessed (via [.getOutputDataSource] and
     * [.getProcessor]). This limitation allows to not insert an `Effect` in the
     * codec chain when measuring audio levels is not required (since we can only do this before the
     * `Processor` is realized).
     *
     * @param enabled whether to enable or disable output audio levels.
     */
    fun enableOutputSSRCAudioLevels(enabled: Boolean, extensionID: Byte) {
        if (enabled && outputAudioLevelEffect == null) {
            outputAudioLevelEffect = AudioLevelEffect2()
        }
        if (outputAudioLevelEffect != null) {
            outputAudioLevelEffect!!.isEnabled = enabled
            outputAudioLevelEffect!!.setRtpHeaderExtensionId(extensionID)
        }
    }

    companion object {
        /**
         * Implements a utility which facilitates setting a specific `VolumeControl` on a
         * specific `Renderer` for the purposes of control over the volume (level) of the audio
         * (to be) played back by the specified `Renderer`.
         *
         * @param renderer the `Renderer` on which the specified `volumeControl` is to be set
         * @param volumeControl the `VolumeControl` to be set on the specified `renderer`
         */
        fun setVolumeControl(renderer: Renderer, volumeControl: VolumeControl?) {
            if (renderer is AbstractAudioRenderer<*>) {
                renderer.setVolumeControl(volumeControl)
            }
        }
    }
}