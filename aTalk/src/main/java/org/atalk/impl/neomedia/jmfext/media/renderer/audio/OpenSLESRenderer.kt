/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.renderer.audio

import org.atalk.impl.neomedia.NeomediaActivator
import org.atalk.impl.neomedia.device.*
import org.atalk.impl.neomedia.device.AudioSystem.Companion.getAudioSystem
import org.atalk.impl.neomedia.jmfext.media.protocol.audiorecord.DataSource
import org.atalk.service.neomedia.BasicVolumeControl
import org.atalk.service.neomedia.codec.Constants
import javax.media.*
import javax.media.PlugIn.BUFFER_PROCESSED_FAILED
import javax.media.PlugIn.BUFFER_PROCESSED_OK
import javax.media.format.AudioFormat

/**
 * Implements an audio `Renderer` which uses OpenSL ES.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class OpenSLESRenderer @JvmOverloads constructor(
        enableGainControl: Boolean = true,
) : AbstractAudioRenderer<AudioSystem>(getAudioSystem(AudioSystem.LOCATOR_PROTOCOL_OPENSLES)) {
    /**
     * The `GainControl` through which the volume/gain of rendered media is controlled.
     */
    private val gainControl: GainControl?
    private var ptr = 0L

    /**
     * The indicator which determines whether this `OpenSLESRenderer` is to set the priority
     * of the thread in which its [.process] method is executed.
     */
    private var setThreadPriority = true

    /**
     * Initializes a new `OpenSLESRenderer` instance.
     * enableGainControl `true` to enable controlling the volume/gain of the rendered media; otherwise, `false`
     */
    init {
        gainControl = if (enableGainControl) {
            val mediaServiceImpl = NeomediaActivator.getMediaServiceImpl()
            if (mediaServiceImpl == null) null else mediaServiceImpl.outputVolumeControl as GainControl
        }
        else null
    }

    /**
     * Implements [PlugIn.close]. Closes this [PlugIn] and releases its resources.
     *
     * @see PlugIn.close
     */
    @Synchronized
    override fun close() {
        if (ptr != 0L) {
            close(ptr)
            ptr = 0
            setThreadPriority = true
        }
    }

    /**
     * Gets the descriptive/human-readable name of this FMJ plug-in.
     *
     * @return the descriptive/human-readable name of this FMJ plug-in
     */
    override fun getName(): String {
        return PLUGIN_NAME
    }

    /**
     * Gets the list of input `Format`s supported by this `OpenSLESRenderer`.
     *
     * @return the list of input `Format`s supported by this `OpenSLESRenderer`
     */
    override fun getSupportedInputFormats(): Array<Format> {
        return SUPPORTED_INPUT_FORMATS.clone()
    }

    /**
     * Implements [PlugIn.open]. Opens this [PlugIn] and acquires the resources that
     * it needs to operate.
     *
     * @throws ResourceUnavailableException if any of the required resources cannot be acquired
     * @see PlugIn.open
     */
    @Synchronized
    @Throws(ResourceUnavailableException::class)
    override fun open() {
        if (ptr == 0L) {
            val inputFormat = inputFormat
            var channels = inputFormat!!.channels
            if (channels == Format.NOT_SPECIFIED) channels = 1

            /*
             * Apart from the thread in which #process(Buffer) is executed, use the thread priority
             * for the thread which will create the OpenSL ES Audio Player.
             */
            DataSource.setThreadPriority()
            ptr = open(inputFormat.encoding, inputFormat.sampleRate,
                inputFormat.sampleSizeInBits, channels, inputFormat.endian,
                inputFormat.signed, inputFormat.dataType)
            if (ptr == 0L) throw ResourceUnavailableException()
            setThreadPriority = true
        }
    }

    /**
     * Implements [Renderer.process]. Processes the media data contained in a specific
     * [Buffer] and renders it to the output device represented by this `Renderer`.
     *
     * @param buffer the `Buffer` containing the media data to be processed and rendered to the
     * output device represented by this `Renderer`
     * @return one or a combination of the constants defined in [PlugIn]
     * @see Renderer.process
     */
    override fun process(buffer: Buffer): Int {
        if (setThreadPriority) {
            setThreadPriority = false
            DataSource.setThreadPriority()
        }
        val format = buffer.format
        var processed: Int
        if (format == null || inputFormat != null && inputFormat!!.matches(format)) {
            val data = buffer.data
            val length = buffer.length
            val offset = buffer.offset
            if (data == null || length == 0) {
                /*
                 * There is really no actual data to be processed by this OpenSLESRenderer.
                 */
                processed = BUFFER_PROCESSED_OK
            }
            else if (length < 0 || offset < 0) {
                /* The length and/or the offset of the Buffer are not valid. */
                processed = BUFFER_PROCESSED_FAILED
            }
            else {
                synchronized(this) {
                    processed = if (ptr == 0L) {
                        /*
                         * This OpenSLESRenderer is not in a state in which it can process the data
                         * of the Buffer.
                         */
                        BUFFER_PROCESSED_FAILED
                    }
                    else {
                        // Apply software gain.
                        if (gainControl != null) {
                            BasicVolumeControl.applyGain(gainControl, (data as ByteArray), offset, length)
                        }
                        process(ptr, data, offset, length)
                    }
                }
            }
        }
        else {
            /*
             * This OpenSLESRenderer does not understand the format of the Buffer.
             */
            processed = BUFFER_PROCESSED_FAILED
        }
        return processed
    }

    /**
     * Implements [Renderer.start]. Starts rendering to the output device represented by
     * this `Renderer`.
     *
     * @see Renderer.start
     */
    @Synchronized
    override fun start() {
        if (ptr != 0L) {
            setThreadPriority = true
            start(ptr)
        }
    }

    /**
     * Implements [Renderer.stop]. Stops rendering to the output device represented by this
     * `Renderer`.
     *
     * @see Renderer.stop
     */
    @Synchronized
    override fun stop() {
        if (ptr != 0L) {
            stop(ptr)
            setThreadPriority = true
        }
    }

    private external fun close(ptr: Long)

    @Throws(ResourceUnavailableException::class)
    private external fun open(
            encoding: String, sampleRate: Double, sampleSizeInBits: Int,
            channels: Int, endian: Int, signed: Int, dataType: Class<*>,
    ): Long

    private external fun process(ptr: Long, data: Any, offset: Int, length: Int): Int

    private external fun start(ptr: Long)

    private external fun stop(ptr: Long)

    companion object {
        /**
         * The human-readable name of the `OpenSLESRenderer` FMJ plug-in.
         */
        private const val PLUGIN_NAME = "OpenSL ES Renderer"

        /**
         * The list of input `Format`s supported by `OpenSLESRenderer` instances.
         */
        private val SUPPORTED_INPUT_FORMATS: Array<Format>

        init {
            System.loadLibrary("jnopensles")
            val supportedInputSampleRates = Constants.AUDIO_SAMPLE_RATES
            val supportedInputSampleRateCount = supportedInputSampleRates.size

            val SUPPORTED_INPUT_FORMATS = ArrayList<Format>()
            for (i in 0 until supportedInputSampleRateCount) {
                SUPPORTED_INPUT_FORMATS.add(AudioFormat(
                    AudioFormat.LINEAR,
                    supportedInputSampleRates[i],
                    16,
                    Format.NOT_SPECIFIED, /* channels */
                    AudioFormat.LITTLE_ENDIAN,
                    AudioFormat.SIGNED,
                    Format.NOT_SPECIFIED, /* frameSizeInBits */
                    Format.NOT_SPECIFIED.toDouble(), /* frameRate */
                    Format.byteArray)
                )
            }
            this.SUPPORTED_INPUT_FORMATS = SUPPORTED_INPUT_FORMATS.toTypedArray()
        }
    }
}