/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.audiolevel

import org.atalk.impl.neomedia.control.ControlsAdapter
import org.atalk.service.neomedia.event.SimpleAudioLevelListener
import javax.media.Buffer
import javax.media.Effect
import javax.media.Format
import javax.media.PlugIn
import javax.media.ResourceUnavailableException
import javax.media.format.AudioFormat

/**
 * An effect that would pass data to the `AudioLevelEventDispatcher` so that it would
 * calculate levels and dispatch changes to interested parties.
 *
 * @author Damian Minkov
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class AudioLevelEffect : ControlsAdapter(), Effect {
    /**
     * The `SimpleAudioLevelListener` which this instance associates with its [.eventDispatcher].
     */
    private var audioLevelListener: SimpleAudioLevelListener? = null

    /**
     * The dispatcher of the events which handles the calculation and the event firing in different
     * thread in order to now slow down the JMF codec chain.
     */
    private val eventDispatcher = AudioLevelEventDispatcher("AudioLevelEffect Dispatcher")

    /**
     * The indicator which determines whether [.open] has been called on this instance without an intervening [.close].
     */
    private var open = false

    /**
     * The init supported audio formats by this effect.
     */
    private val supportedAudioFormats: Array<Format> = arrayOf(AudioFormat(
        AudioFormat.LINEAR,
        Format.NOT_SPECIFIED.toDouble(),
        16,
        1,
        AudioFormat.LITTLE_ENDIAN,
        AudioFormat.SIGNED,
        16,
        Format.NOT_SPECIFIED.toDouble(),
        Format.byteArray)
    )

    /**
     * Sets (or unsets if `listener` is `null`), the listener that is going to be notified of audio
     * level changes detected by this effect. Given the semantics of the [AudioLevelEventDispatcher]
     * this effect would do no real work if no listener is set or if it is set to `null`.
     *
     * @param listener the `SimpleAudioLevelListener` that we'd like to receive level changes or
     * `null` if we'd like level measurements to stop.
     */
    fun setAudioLevelListener(listener: SimpleAudioLevelListener?) {
        synchronized(eventDispatcher) {
            audioLevelListener = listener
            if (open)
                eventDispatcher.setAudioLevelListener(audioLevelListener)
        }
    }

    /**
     * Returns the audio level listener.
     *
     * @return the audio level listener or `null` if it does not exist.
     */
    fun getAudioLevelListener(): SimpleAudioLevelListener? {
        synchronized(eventDispatcher) {
            return audioLevelListener
        }
    }

    /**
     * Lists all of the input formats that this codec accepts.
     *
     * @return An array that contains the supported input `Formats`.
     */
    override fun getSupportedInputFormats(): Array<Format> {
        return supportedAudioFormats
    }

    /**
     * Lists the output formats that this codec can generate.
     *
     * @param input The `Format` of the data to be used as input to the plug-in.
     * @return An array that contains the supported output `Formats`.
     */
    override fun getSupportedOutputFormats(input: Format): Array<Format> {
        return arrayOf(AudioFormat(
            AudioFormat.LINEAR,
            (input as AudioFormat).sampleRate,
            16,
            1,
            AudioFormat.LITTLE_ENDIAN,
            AudioFormat.SIGNED,
            16,
            Format.NOT_SPECIFIED.toDouble(),
            Format.byteArray)
        )
    }

    /**
     * Sets the format of the data to be input to this codec.
     *
     * @param format The `Format` to be set.
     * @return The `Format` that was set.
     */
    override fun setInputFormat(format: Format): Format? {
        return if (format is AudioFormat)
            format
        else null
    }

    /**
     * Sets the format for the data this codec outputs.
     *
     * @param format The `Format` to be set.
     * @return The `Format` that was set.
     */
    override fun setOutputFormat(format: Format): Format? {
        return if (format is AudioFormat)
            format
        else null
    }

    /**
     * Performs the media processing defined by this codec.
     *
     * @param inputBuffer The `Buffer` that contains the media data to be processed.
     * @param outputBuffer The `Buffer` in which to store the processed media data.
     * @return `BUFFER_PROCESSED_OK` if the processing is successful.
     * @see PlugIn
     */
    override fun process(inputBuffer: Buffer, outputBuffer: Buffer): Int {
        /*
         * In accord with what an Effect is generally supposed to do, copy the data from the
         * inputBuffer into outputBuffer.
         */
        if (COPY_DATA_FROM_INPUT_TO_OUTPUT) {
            // Copy the actual data from the input to the output.
            val data = outputBuffer.data
            val inputBufferLength = inputBuffer.length
            val bufferData: ByteArray

            if (data is ByteArray && data.size >= inputBufferLength) {
                bufferData = data
            }
            else {
                bufferData = ByteArray(inputBufferLength)
                outputBuffer.data = bufferData
            }

            outputBuffer.length = inputBufferLength
            outputBuffer.offset = 0
            System.arraycopy(inputBuffer.data, inputBuffer.offset, bufferData, 0, inputBufferLength)

            // Now copy the remaining attributes.
            outputBuffer.format = inputBuffer.format
            outputBuffer.header = inputBuffer.header
            outputBuffer.sequenceNumber = inputBuffer.sequenceNumber
            outputBuffer.timeStamp = inputBuffer.timeStamp
            outputBuffer.rtpTimeStamp = inputBuffer.rtpTimeStamp
            outputBuffer.flags = inputBuffer.flags
            outputBuffer.isDiscard = inputBuffer.isDiscard
            outputBuffer.isEOM = inputBuffer.isEOM
            outputBuffer.duration = inputBuffer.duration
        }
        else {
            outputBuffer.copy(inputBuffer)
        }

        /*
         * At long last, do the job which this AudioLevelEffect exists for i.e. deliver the data to
         * eventDispatcher so that its audio level gets calculated and delivered to audioEventListener.
         */
        eventDispatcher.addData(outputBuffer)
        return PlugIn.BUFFER_PROCESSED_OK
    }

    /**
     * Gets the name of this plug-in as a human-readable string.
     *
     * @return A `String` that contains the descriptive name of the plug-in.
     */
    override fun getName(): String {
        return "Audio Level Effect"
    }

    /**
     * Opens this effect.
     *
     * @throws ResourceUnavailableException
     * If all of the required resources cannot be acquired.
     */
    @Throws(ResourceUnavailableException::class)
    override fun open() {
        synchronized(eventDispatcher) {
            if (!open) {
                open = true
                eventDispatcher.setAudioLevelListener(audioLevelListener)
            }
        }
    }

    /**
     * Closes this effect.
     */
    override fun close() {
        synchronized(eventDispatcher) {
            if (open) {
                open = false
                eventDispatcher.setAudioLevelListener(null)
            }
        }
    }

    /**
     * Resets its state.
     */
    override fun reset() {}

    companion object {
        /**
         * The indicator which determines whether `AudioLevelEffect` instances are to perform the copying
         * of the data from input `Buffer`s to output `Buffer`s themselves (e.g. using [System.arraycopy]).
         */
        private const val COPY_DATA_FROM_INPUT_TO_OUTPUT = true
    }
}