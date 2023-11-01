/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.renderer.audio

import net.sf.fmj.media.util.MediaThread
import org.atalk.impl.neomedia.NeomediaServiceUtils
import org.atalk.impl.neomedia.device.AudioSystem
import org.atalk.impl.neomedia.device.AudioSystem.Companion.getAudioSystem
import org.atalk.impl.neomedia.device.AudioSystem.DataFlow
import org.atalk.impl.neomedia.device.NotifyDevices
import org.atalk.impl.neomedia.device.PlaybackDevices
import org.atalk.impl.neomedia.jmfext.media.renderer.AbstractRenderer
import org.atalk.service.neomedia.VolumeControl
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.nio.ByteOrder
import javax.media.Format
import javax.media.GainControl
import javax.media.MediaLocator
import javax.media.ResourceUnavailableException
import javax.media.format.AudioFormat

/**
 * Provides an abstract base implementation of `Renderer` which processes media in `AudioFormat`
 * in order to facilitate extenders.
 *
 * @param <T> the runtime type of the `AudioSystem` which provides the playback device used by the `AbstractAudioRenderer`
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class AbstractAudioRenderer<T : AudioSystem> protected constructor(
        audioSystem: T?,
        dataFlow: DataFlow = DataFlow.PLAYBACK,
) : AbstractRenderer<AudioFormat>() {
    /**
     * The `AudioSystem` which provides the playback device used by this `Renderer`.
     */
    protected val audioSystem: T?

    /**
     * The flow of the media data (to be) implemented by this instance which is either
     * [AudioSystem.DataFlow.NOTIFY] or [AudioSystem.DataFlow.PLAYBACK].
     */
    private val dataFlow: DataFlow

    /**
     * The `GainControl` through which the volume/gain of the media rendered by this instance is (to be) controlled.
     */
    private val gainControl: GainControl?

    /**
     * The `MediaLocator` which specifies the playback device to be used by this `Renderer`.
     */
    private var locator: MediaLocator? = null

    /**
     * The `PropertyChangeListener` which listens to changes in the values of the properties of [.audioSystem].
     */
    private val propertyChangeListener = PropertyChangeListener { ev -> propertyChange(ev) }

    /**
     * The `VolumeControl` through which the volume/gain of the media rendered by this
     * instance is (to be) controlled. If non-`null`, overrides [.gainControl].
     */
    private var volumeControl: VolumeControl? = null

    /**
     * Initializes a new `AbstractAudioRenderer` instance which is to use playback devices
     * provided by a specific `AudioSystem`.
     *
     * audioSystem the `AudioSystem` which is to provide the playback devices to be used by the new instance
     */
    init {
        require(!(dataFlow != DataFlow.NOTIFY && dataFlow != DataFlow.PLAYBACK)) { "dataFlow" }
        this.audioSystem = audioSystem
        this.dataFlow = dataFlow

        /*
         * XXX The Renderer implementations are probed for their supportedInputFormats during
         * the initialization of MediaServiceImpl so the latter may not be available at this
         * time. Which is not much of a problem given than the GainControl is of no interest
         * during the probing of the supportedInputFormats.
         */
        gainControl = if (DataFlow.PLAYBACK == dataFlow) {
            val mediaServiceImpl = NeomediaServiceUtils.mediaServiceImpl
            if (mediaServiceImpl == null)
                null
            else
                mediaServiceImpl.outputVolumeControl as GainControl
        }
        else null
    }

    /**
     * Initializes a new `AbstractAudioRenderer` instance which is to use for notification or
     * playback devices provided by an `AudioSystem` specified by the protocol of the
     * `MediaLocator`s of the `CaptureDeviceInfo`s registered by the `AudioSystem`.
     *
     * @param locatorProtocol the protocol of the `MediaLocator`s of the `CaptureDeviceInfo`
     * registered by the `AudioSystem` which is to provide the notification or
     * playback devices to be used by the new instance
     * @param dataFlow the flow of the media data to be implemented by the new instance i.e. whether
     * notification or playback devices provided by the specified `audioSystem` are to be used by
     * the new instance. Must be either [AudioSystem.DataFlow.NOTIFY] or [AudioSystem.DataFlow.PLAYBACK].
     */
    protected constructor(
            locatorProtocol: String?,
            dataFlow: DataFlow = DataFlow.PLAYBACK,
    ) : this(getAudioSystem(locatorProtocol) as T?, dataFlow)

    /**
     * {@inheritDoc}
     */
    override fun close() {
        audioSystem?.removePropertyChangeListener(propertyChangeListener)
    }

    /**
     * Implements [javax.media.Controls.getControls]. Gets the available controls over this
     * instance. `AbstractAudioRenderer` returns a [GainControl] if available.
     *
     * @return an array of `Object`s which represent the available controls over this instance
     */
    override fun getControls(): Array<Any> {
        val gainControl = getGainControl()
        return gainControl?.let { arrayOf(it) } ?: super.getControls()
    }

    /**
     * Gets the `GainControl`, if any, which controls the volume level of the audio (to be) played back by this `Renderer`.
     *
     * @return the `GainControl`, if any, which controls the volume level of the audio
     * (to be) played back by this `Renderer`
     */
    protected fun getGainControl(): GainControl? {
        val volumeControl = volumeControl
        var gainControl = gainControl
        if (volumeControl is GainControl) gainControl = volumeControl
        return gainControl
    }

    /**
     * Gets the `MediaLocator` which specifies the playback device to be used by this `Renderer`.
     *
     * @return the `MediaLocator` which specifies the playback device to be used by this `Renderer`
     */
    fun getLocator(): MediaLocator? {
        var locator = locator
        if (locator == null && audioSystem != null) {
            val device = audioSystem.getSelectedDevice(dataFlow)
            if (device != null)
                locator = device.locator
        }
        return locator
    }

    /**
     * {@inheritDoc}
     */
    override fun getSupportedInputFormats(): Array<Format> {
        /*
         * XXX If the AudioSystem (class) associated with this Renderer (class and its instances)
         * fails to initialize, the following may throw a NullPointerException. Such a throw should
         * be considered appropriate.
         */
        return audioSystem!!.getDevice(dataFlow, getLocator()!!)!!.formats
    }

    /**
     * {@inheritDoc}
     */
    @Throws(ResourceUnavailableException::class)
    override fun open() {
        /*
         * If this Renderer has not been forced to use a playback device with a specific
         * MediaLocator, it will use the default playback device (of its associated AudioSystem).
         * In the case of using the default playback device, change the playback device used by
         * this instance upon changes of the default playback device.
         */
        if (locator == null && audioSystem != null) {
            /*
             * We actually want to allow the user to switch the playback and/or notify device to
             * none mid-stream in order to disable the playback. If an extender does not want to
             * support that behavior, they will throw an exception and/or not call this implementation anyway.
             */
            audioSystem.addPropertyChangeListener(propertyChangeListener)
        }
    }

    /**
     * Notifies this instance that the value of the property of [.audioSystem] which
     * identifies the default notification or playback (as determined by [.dataFlow]) device
     * has changed. The default implementation does nothing so extenders may safely not call
     * back to their `AbstractAudioRenderer` super.
     *
     * @param ev a `PropertyChangeEvent` which specifies details about the change such as the
     * name of the property and its old and new values
     */
    protected open fun playbackDevicePropertyChange(ev: PropertyChangeEvent?) {}

    /**
     * Notifies this instance about a specific `PropertyChangeEvent`.
     * `AbstractAudioRenderer` listens to changes in the values of the properties of
     * [.audioSystem].
     *
     * @param ev the `PropertyChangeEvent` to notify this instance about
     */
    private fun propertyChange(ev: PropertyChangeEvent) {
        val propertyName = when (dataFlow) {
            DataFlow.NOTIFY -> NotifyDevices.PROP_DEVICE
            DataFlow.PLAYBACK -> PlaybackDevices.PROP_DEVICE

            // The value of the field dataFlow is either NOTIFY or PLAYBACK.
            else ->
                return
        }
        if (propertyName == ev.propertyName)
            playbackDevicePropertyChange(ev)
    }

    /**
     * Sets the `MediaLocator` which specifies the playback device to be used by this `Renderer`.
     *
     * @param locator the `MediaLocator` which specifies the playback device to be used by this `Renderer`
     */
    open fun setLocator(locator: MediaLocator?) {
        if (this.locator == null) {
            if (locator == null)
                return
        }
        else if (this.locator == locator)
            return

        this.locator = locator
    }

    /**
     * Sets the `VolumeControl` which is to control the volume (level) of the audio (to be)
     * played back by this `Renderer`.
     *
     * @param volumeControl the `VolumeControl` which is to control the volume (level) of the audio (to be)
     * played back by this `Renderer`
     */
    fun setVolumeControl(volumeControl: VolumeControl?) {
        this.volumeControl = volumeControl
    }

    companion object {
        /**
         * The native byte order of the hardware upon which this Java virtual machine is running
         * expressed in the `endian` term of [AudioFormat].
         */
        val NATIVE_AUDIO_FORMAT_ENDIAN = when (ByteOrder.BIG_ENDIAN) {
            ByteOrder.nativeOrder() -> AudioFormat.BIG_ENDIAN
            else -> AudioFormat.LITTLE_ENDIAN
        }

        const val NOT_SPECIFIED_DOUBLE = -1.0

        /**
         * Changes the priority of the current thread to a value which is considered appropriate for
         * the purposes of audio processing.
         */
        fun useAudioThreadPriority() {
            useThreadPriority(MediaThread.getAudioPriority())
        }
    }
}