/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.audiolevel

import org.atalk.impl.neomedia.control.ControlsAdapter
import javax.media.Buffer
import javax.media.Effect
import javax.media.Format
import javax.media.PlugIn
import javax.media.format.AudioFormat

/**
 * An [javax.media.Effect] implementation which calculates audio levels based on the samples
 * in the `Buffer` and includes them in the buffer's `headerExtension` field in the
 * SSRC audio level format specified in RFC6464.
 *
 *
 * The class is based on [AudioLevelEffect], but an important difference is that
 * the actual calculation is performed in the same thread that calls [.process].
 *
 * @author Boris Grozev
 * @author Damian Minkov
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class AudioLevelEffect2 : ControlsAdapter(), Effect {
    /**
     * The supported audio formats by this effect.
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
     * Whether this effect is enabled or disabled. If disabled, this `Effect` will set the
     * RTP header extension of the output buffer to `null`.
     */
    var isEnabled = false

    /**
     * The ID of the RTP header extension for SSRC audio levels, which is to be added by this `Effect`.
     */
    private var rtpHeaderExtensionId: Byte = -1

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

        val data = outputBuffer.data
        var ext = outputBuffer.headerExtension
        if (isEnabled && rtpHeaderExtensionId.toInt() != -1 && data is ByteArray) {
            val level = AudioLevelCalculator.calculateAudioLevel(data,
                outputBuffer.offset, outputBuffer.length)

            if (ext == null) {
                ext = Buffer.RTPHeaderExtension(rtpHeaderExtensionId, ByteArray(1))
            }

            ext.id = rtpHeaderExtensionId
            if (ext.value == null || ext.value.isEmpty())
                ext.value = ByteArray(1)
            ext.value[0] = level
            outputBuffer.headerExtension = ext
        }
        else {
            // Make sure that the output buffer doesn't retain the extension from a previous payload.
            outputBuffer.headerExtension = null
        }
        return PlugIn.BUFFER_PROCESSED_OK
    }

    /**
     * Gets the name of this plug-in as a human-readable string.
     *
     * @return A `String` that contains the descriptive name of the plug-in.
     */
    override fun getName(): String {
        return "Audio Level Effect2"
    }

    /**
     * {@inheritDoc}
     */
    override fun open() {}

    /**
     * {@inheritDoc}
     */
    override fun close() {}

    /**
     * {@inheritDoc}
     */
    override fun reset() {}

    /**
     * Sets the ID of the RTP header extension which will be added.
     *
     * @param rtpHeaderExtensionId the ID to set.
     */
    fun setRtpHeaderExtensionId(rtpHeaderExtensionId: Byte) {
        this.rtpHeaderExtensionId = rtpHeaderExtensionId
    }

    companion object {
        /**
         * The indicator which determines whether `AudioLevelEffect` instances are to perform
         * the copying of the data from input `Buffer`s to output `Buffer`s themselves
         * (e.g. using [System.arraycopy]).
         */
        private const val COPY_DATA_FROM_INPUT_TO_OUTPUT = true
    }
}