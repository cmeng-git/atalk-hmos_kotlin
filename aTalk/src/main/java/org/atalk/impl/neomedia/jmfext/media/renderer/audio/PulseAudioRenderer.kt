/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.renderer.audio

import org.atalk.impl.neomedia.MediaUtils
import org.atalk.impl.neomedia.device.AudioSystem
import org.atalk.impl.neomedia.device.PulseAudioSystem
import org.atalk.impl.neomedia.pulseaudio.PA
import org.atalk.impl.neomedia.pulseaudio.PA.stream_request_cb_t
import org.atalk.service.neomedia.BasicVolumeControl
import java.beans.PropertyChangeEvent
import java.io.IOException
import java.lang.reflect.UndeclaredThrowableException
import javax.media.*
import javax.media.format.AudioFormat

/**
 * Implements an audio `Renderer` which uses PulseAudio.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class PulseAudioRenderer @JvmOverloads constructor(
        mediaRole: String? = null,
) : AbstractAudioRenderer<PulseAudioSystem>(
    PulseAudioSystem.pulseAudioSystem,
    if (mediaRole == null || PulseAudioSystem.MEDIA_ROLE_PHONE == mediaRole)
        AudioSystem.DataFlow.PLAYBACK
    else AudioSystem.DataFlow.NOTIFY) {
    /**
     * The number of channels of audio data this `PulseAudioRenderer` is configured to render.
     */
    private var channels = 0

    /**
     * The indicator which determines whether [.stream]'s playback is paused or resumed.
     */
    private var corked = true

    /**
     * The `pa_cvolume` (structure) instance used by this `PulseAudioRenderer` to set
     * the per-channel volume of [.stream].
     */
    private var cvolume = 0L

    /**
     * The name of the sink [.stream] is connected to.
     */
    private var dev: String? = null

    /**
     * The level of the volume specified by the `GainControl` associated with this
     * `PulseAudioRenderer` which has been applied to [.stream].
     */
    private var gainControlLevel = 0f

    /**
     * The PulseAudio logic role of the media played back by this `PulseAudioRenderer`.
     */
    private val mediaRole: String

    /**
     * The PulseAudio stream which performs the actual rendering of audio data for this `PulseAudioRenderer`.
     */
    private var stream = 0L

    /**
     * The PulseAudio callback which notifies this `PulseAudioRenderer` that [.stream]
     * requests audio data to play back.
     */
    private val writeCb = object : stream_request_cb_t {
        override fun callback(s: Long, nbytes: Int) {
            audioSystem!!.signalMainloop(false)
        }
    }
    /**
     * Initializes a new `PulseAudioRenderer` instance with a specific PulseAudio media
     * role.
     *
     * @param mediaRole the PulseAudio media role to initialize the new instance with
     */
    /**
     * Initializes a new `PulseAudioRenderer` instance with a default PulseAudio media role.
     */
    init {
        checkNotNull(audioSystem) { "audioSystem" }
        this.mediaRole = mediaRole ?: PulseAudioSystem.MEDIA_ROLE_PHONE
    }

    /**
     * Applies the volume specified by a specific `GainControl` on a specified sample of audio `data`.
     *
     * @param gainControl the `GainControl` which specifies the volume to set on `data`
     * @param data the audio data to set the volume specified by `gainControl` on
     * @param offset the offset in `data` at which the valid audio data begins
     * @param length the number of bytes of valid audio data in `data` beginning at `offset`
     */
    private fun applyGain(gainControl: GainControl, data: ByteArray, offset: Int, length: Int) {
        if (SOFTWARE_GAIN || cvolume == 0L) {
            if (length > 0)
                BasicVolumeControl.applyGain(gainControl, data, offset, length)
        }
        else {
            val gainControlLevel = gainControl.level

            if (this.gainControlLevel != gainControlLevel) {
                this.gainControlLevel = gainControlLevel
                setStreamVolume(stream, gainControlLevel)
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun close() {
        audioSystem!!.lockMainloop()
        try {
            val stream = stream
            if (stream != 0L) {
                try {
                    stopWithMainloopLock()
                } finally {
                    val cvolume = cvolume
                    this.cvolume = 0
                    this.stream = 0
                    corked = true
                    dev = null
                    audioSystem.signalMainloop(false)
                    if (cvolume != 0L) PA.cvolume_free(cvolume)
                    PA.stream_disconnect(stream)
                    PA.stream_unref(stream)
                }
            }
            super.close()
        } finally {
            audioSystem.unlockMainloop()
        }
    }

    /**
     * Pauses or resumes the playback of audio data through [.stream].
     *
     * @param b `true` to pause the playback of audio data or `false` to resume it
     */
    private fun cork(b: Boolean) {
        corked = try {
            PulseAudioSystem.corkStream(stream, b)
            b
        } catch (ioe: IOException) {
            throw UndeclaredThrowableException(ioe)
        } finally {
            audioSystem!!.signalMainloop(false)
        }
    }

    /**
     * Returns the name of the sink this `PulseAudioRenderer` is configured to connect
     * [.stream] to.
     *
     * @return the name of the sink this `PulseAudioRenderer` is configured to connect
     * [.stream] to
     */
    private val locatorDev: String?
        get() {
            val locator = getLocator()
            var locatorDev: String?

            if (locator == null) {
                locatorDev = null
            }
            else {
                locatorDev = locator.remainder
                if (locatorDev != null && locatorDev.isEmpty()) locatorDev = null
            }
            return locatorDev
        }

    /**
     * {@inheritDoc}
     */
    override fun getName(): String {
        return PLUGIN_NAME
    }

    /**
     * {@inheritDoc}
     */
    override fun getSupportedInputFormats(): Array<Format> {
        return SUPPORTED_INPUT_FORMATS.clone()
    }

    /**
     * {@inheritDoc}
     */
    @Throws(ResourceUnavailableException::class)
    override fun open() {
        audioSystem!!.lockMainloop()
        try {
            openWithMainloopLock()
            super.open()
        } finally {
            audioSystem.unlockMainloop()
        }
    }

    /**
     * Opens this `PulseAudioRenderer` i.e. initializes the PulseAudio stream which is to
     * play audio data back. The method executes with the assumption that the PulseAudio event loop
     * object is locked by the executing thread.
     *
     * @throws ResourceUnavailableException if the opening of this `PulseAudioRenderer` failed
     */
    @Throws(ResourceUnavailableException::class)
    private fun openWithMainloopLock() {
        if (stream != 0L) return
        val format = inputFormat
        var sampleRate = format!!.sampleRate.toInt()
        var channels = format.channels
        var sampleSizeInBits = format.sampleSizeInBits
        if (sampleRate == Format.NOT_SPECIFIED && MediaUtils.MAX_AUDIO_SAMPLE_RATE != Format.NOT_SPECIFIED.toDouble()) sampleRate = MediaUtils.MAX_AUDIO_SAMPLE_RATE.toInt()
        if (channels == Format.NOT_SPECIFIED) channels = 1
        if (sampleSizeInBits == Format.NOT_SPECIFIED) sampleSizeInBits = 16
        var stream = 0L
        var exception: Throwable? = null
        try {
            stream = audioSystem!!.createStream(sampleRate, channels,
                javaClass.name, mediaRole)
            this.channels = channels
        } catch (ex: java.lang.Exception) {
            when (ex) {
                is IllegalStateException,
                is RuntimeException,
                -> {
                    val rue = ResourceUnavailableException()
                    rue.initCause(ex)
                    throw rue
                }
            }
        }

        if (stream == 0L) throw ResourceUnavailableException("stream")
        try {
            var attr = PA.buffer_attr_new(-1, 2 /* millis / 10 */
                    * (sampleRate / 100) * channels * (sampleSizeInBits / 8), -1, -1, -1)
            if (attr == 0L) throw ResourceUnavailableException("pa_buffer_attr_new")
            try {
                val stateCallback = Runnable { audioSystem.signalMainloop(false) }
                PA.stream_set_state_callback(stream, stateCallback)
                val dev = locatorDev
                PA.stream_connect_playback(stream, dev, attr,
                    PA.STREAM_ADJUST_LATENCY or PA.STREAM_START_CORKED, 0, 0)
                try {
                    if (attr != 0L) {
                        PA.buffer_attr_free(attr)
                        attr = 0
                    }
                    val state = audioSystem.waitForStreamState(stream, PA.STREAM_READY)
                    if (state != PA.STREAM_READY) throw ResourceUnavailableException("stream.state")
                    PA.stream_set_write_callback(stream, writeCb)
                    setStreamVolume(stream)
                    this.stream = stream
                    this.dev = dev
                } finally {
                    if (this.stream == 0L) PA.stream_disconnect(stream)
                }
            } finally {
                if (attr != 0L) PA.buffer_attr_free(attr)
            }
        } finally {
            if (this.stream == 0L) PA.stream_unref(stream)
        }
    }

    /**
     * Notifies this instance that the value of the AudioSystem.PROP_PLAYBACK_DEVICE
     * property of its associated `AudioSystem` has changed.
     *
     * @param ev a `PropertyChangeEvent` which specifies details about the change such as the
     * name of the property and its old and new values
     */
    override fun playbackDevicePropertyChange(ev: PropertyChangeEvent?) {
        /*
         * FIXME Disabled due to freezes reported by Vincent Lucas and Kertesz Laszlo on the dev
         * mailing list.
         */
        audioSystem!!.lockMainloop()
        try {
            /*
             * If the stream is not open, changes to the default playback device do not really
             * concern this Renderer because it will pick them up when it gets open.
             */
            val open = stream != 0L
            if (open) {
                /*
                 * One and the same name of the sink that stream is connected to in the server may
                 * come from different MediaLocator instances.
                 */
                val locatorDev = locatorDev
                if (dev != locatorDev) {
                    /*
                     * PulseAudio has the capability to move a stream to a different device while
                     * the stream is connected to a sink. In other words, it may turn out that the
                     * stream is already connected to the sink with the specified name at this time
                     * of the execution.
                     */
                    val streamDev = PA.stream_get_device_name(stream)
                    if (streamDev != locatorDev) {
                        /*
                         * The close method will stop this Renderer if it is currently started.
                         */
                        val start = !corked
                        close()
                        try {
                            open()
                        } catch (rue: ResourceUnavailableException) {
                            throw UndeclaredThrowableException(rue)
                        }
                        if (start) start()
                    }
                }
            }
        } finally {
            audioSystem.unlockMainloop()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun process(buffer: Buffer): Int {
        if (buffer.isDiscard) return PlugIn.BUFFER_PROCESSED_OK
        if (buffer.length <= 0) return PlugIn.BUFFER_PROCESSED_OK
        var ret: Int
        audioSystem!!.lockMainloop()
        ret = try {
            processWithMainloopLock(buffer)
        } finally {
            audioSystem.unlockMainloop()
        }
        if (ret != PlugIn.BUFFER_PROCESSED_FAILED && buffer.length > 0) ret = ret or PlugIn.INPUT_BUFFER_NOT_CONSUMED
        return ret
    }

    /**
     * Plays back the audio data of a specific FMJ `Buffer` through [.stream]. The
     * method executes with the assumption that the PulseAudio event loop object is locked by the
     * executing thread.
     *
     * @param buffer the FMJ `Buffer` which specifies the audio data to play back
     * @return `BUFFER_PROCESSED_OK` if the specified `buffer` was successfully
     * sumbitted for playback through [.stream]; otherwise,
     * `BUFFER_PROCESSED_FAILED`
     */
    private fun processWithMainloopLock(buffer: Buffer): Int {
        if (stream == 0L || corked) return PlugIn.BUFFER_PROCESSED_FAILED
        var writableSize = PA.stream_writable_size(stream)
        val ret: Int
        if (writableSize <= 0) {
            audioSystem!!.waitMainloop()
            ret = PlugIn.BUFFER_PROCESSED_OK
        }
        else {
            val data = buffer.data as ByteArray
            val offset = buffer.offset
            val length = buffer.length
            if (writableSize > length) writableSize = length

            val gainControl = getGainControl()
            gainControl?.let { applyGain(it, data, offset, writableSize) }

            val writtenSize = PA.stream_write(stream, data, offset, writableSize, null, 0,
                PA.SEEK_RELATIVE)

            if (writtenSize < 0) {
                ret = PlugIn.BUFFER_PROCESSED_FAILED
            }
            else {
                ret = PlugIn.BUFFER_PROCESSED_OK
                buffer.length = length - writtenSize
                buffer.offset = offset + writtenSize
            }
        }
        return ret
    }

    /**
     * Sets the volume of a specific PulseAudio `stream` to a level specified by the
     * `GainControl` associated with this `PulseAudioRenderer`.
     *
     * @param stream the PulseAudio stream to set the volume of
     */
    private fun setStreamVolume(stream: Long) {
        var gainControl: GainControl? = null

        if (!SOFTWARE_GAIN && gainControl.also { gainControl = it!! } != null) {
            cvolume = PA.cvolume_new()
            var freeCvolume = true

            try {
                val gainControlLevel = gainControl!!.level
                setStreamVolume(stream, gainControlLevel)
                this.gainControlLevel = gainControlLevel
                freeCvolume = false
            } finally {
                if (freeCvolume) {
                    PA.cvolume_free(cvolume)
                    cvolume = 0
                }
            }
        }
    }

    /**
     * Sets the volume of a specific PulseAudio `stream` to a specific `level`.
     *
     * @param stream the PulseAudio stream to set the volume of
     * @param level the volume to set on `stream`
     */
    private fun setStreamVolume(stream: Long, level: Float) {
        val volume = PA.sw_volume_from_linear(level * (BasicVolumeControl.MAX_VOLUME_PERCENT / 100).toDouble())
        val cvolumeSet = PA.cvolume_set(cvolume, channels, volume)

        val o: Long = PA.context_set_sink_input_volume(audioSystem!!.getContext(),
            PA.stream_get_index(stream), cvolume, null)
        if (o != 0L) PA.operation_unref(o)
    }

    /**
     * {@inheritDoc}
     */
    override fun start() {
        audioSystem!!.lockMainloop()
        try {
            if (stream == 0L) {
                try {
                    openWithMainloopLock()
                } catch (rue: ResourceUnavailableException) {
                    throw UndeclaredThrowableException(rue)
                }
            }
            cork(false)
        } finally {
            audioSystem.unlockMainloop()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun stop() {
        audioSystem!!.lockMainloop()
        try {
            stopWithMainloopLock()
        } finally {
            audioSystem.unlockMainloop()
        }
    }

    /**
     * Pauses the playback of audio data performed by [.stream]. The method executes with the
     * assumption that the PulseAudio event loop object is locked by the executing thread.
     */
    private fun stopWithMainloopLock() {
        if (stream != 0L) cork(true)
    }

    companion object {
        /**
         * The human-readable `PlugIn` name of the `PulseAudioRenderer` instances.
         */
        private const val PLUGIN_NAME = "PulseAudio Renderer"

        /*
     * FIXME The control of the volume through the native PulseAudio API has been reported to
     * maximize the system-wide volume of the source with flat volumes i.e.
     * https://java.net/jira/browse/JITSI-1050 (Pulseaudio changes volume to maximum values).
     */
        private const val SOFTWARE_GAIN = true

        /**
         * The list of JMF `Format`s of audio data which `PulseAudioRenderer` instances
         * are capable of rendering.
         */
        private val SUPPORTED_INPUT_FORMATS = arrayOf<Format>(
            AudioFormat(
                AudioFormat.LINEAR,
                Format.NOT_SPECIFIED /* sampleRate */.toDouble(),
                16,
                Format.NOT_SPECIFIED /* channels */,
                AudioFormat.LITTLE_ENDIAN,
                AudioFormat.SIGNED,
                Format.NOT_SPECIFIED /* frameSizeInBits */,
                Format.NOT_SPECIFIED /* frameRate */.toDouble(),
                Format.byteArray)
        )
    }
}