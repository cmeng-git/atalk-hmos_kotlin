/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol

import org.atalk.impl.neomedia.control.AbstractControls
import org.atalk.service.neomedia.DTMFInbandTone
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import javax.media.*
import javax.media.format.AudioFormat
import javax.media.protocol.BufferTransferHandler
import javax.media.protocol.PushBufferDataSource
import javax.media.protocol.PushBufferStream

/**
 * Implements a `PushBufferDataSource` wrapper which provides mute support for the wrapped
 * instance.
 *
 *
 * Because the class wouldn't work for our use case without it, `CaptureDevice` is
 * implemented and is being delegated to the wrapped `DataSource` (if it supports the
 * interface in question).
 *
 *
 * @author Lyubomir Marinov
 */
class RewritablePushBufferDataSource
/**
 * Initializes a new `RewritablePushBufferDataSource` instance which is to provide mute
 * support for a specific `PushBufferDataSource`.
 *
 * @param dataSource
 * the `PushBufferDataSource` the new instance is to provide mute support for
 */
(dataSource: PushBufferDataSource) : PushBufferDataSourceDelegate<PushBufferDataSource>(dataSource), MuteDataSource, InbandDTMFDataSource {
    /**
     * Determines whether this `DataSource` is mute.
     *
     * @return `true` if this `DataSource` is mute; otherwise, `false`
     */
    /**
     * Sets the mute state of this `DataSource`.
     *
     * isMute `true` to mute this `DataSource`; otherwise, `false`
     */
    /**
     * The indicator which determines whether this `DataSource` is mute.
     */
    @get:Synchronized
    @set:Synchronized
    override var isMute = false

    /**
     * The tones to send via inband DTMF, if not empty.
     */
    private val tones = LinkedList<DTMFInbandTone>()

    /**
     * {@inheritDoc}
     *
     *
     * Overrides the super implementation to include the type hierarchy of the very wrapped
     * `dataSource` instance into the search for the specified `controlType`.
     */
    override fun getControl(controlType: String): Any? {
        return if (InbandDTMFDataSource::class.java.name == controlType || MuteDataSource::class.java.name == controlType) {
            this
        } else {
            /*
			 * The super implements a delegate so we can be sure that it delegates the
			 * invocation of Controls#getControl(String) to the wrapped dataSource.
			 */
            AbstractControls.queryInterface(dataSource, controlType)
        }
    }

    /**
     * Implements [PushBufferDataSource.getStreams]. Wraps the streams of the wrapped
     * `PushBufferDataSource` into `MutePushBufferStream` instances in order to
     * provide mute support to them.
     *
     * @return an array of `PushBufferStream` instances with enabled mute support
     */
    override fun getStreams(): Array<PushBufferStream> {
        val streams = dataSource.streams
        if (streams != null) {
            for (streamIndex in streams.indices) {
                val stream = streams[streamIndex]
                if (stream != null) streams[streamIndex] = MutePushBufferStream(stream)
            }
        }
        return streams
    }

    /**
     * Adds a new inband DTMF tone to send.
     *
     * @param tone
     * the DTMF tone to send.
     */
    override fun addDTMF(tone: DTMFInbandTone) {
        tones.add(tone)
    }

    /**
     * Determines whether this `DataSource` sends a DTMF tone.
     *
     * @return `true` if this `DataSource` is sending a DTMF tone; otherwise,
     * `false`.
     */
    val isSendingDTMF: Boolean
        get() = !tones.isEmpty()

    /**
     * Implements a `PushBufferStream` wrapper which provides mute support for the wrapped
     * instance.
     */
    private inner class MutePushBufferStream
    /**
     * Initializes a new `MutePushBufferStream` instance which is to provide mute
     * support to a specific `PushBufferStream` .
     *
     * @param stream
     * the `PushBufferStream` the new instance is to provide mute support to
     */
    (stream: PushBufferStream) : SourceStreamDelegate<PushBufferStream?>(stream), PushBufferStream {
        /**
         * Implements [PushBufferStream.getFormat]. Delegates to the wrapped
         * `PushBufferStream`.
         *
         * @return the `Format` of the wrapped `PushBufferStream`
         */
        override fun getFormat(): Format {
            return stream!!.format
        }

        /**
         * Implements [PushBufferStream.read]. If this instance is muted (through its
         * owning `RewritablePushBufferDataSource`), overwrites the data read from the
         * wrapped `PushBufferStream` with silence data.
         *
         * @param buffer a `Buffer` in which the read data is to be returned to the caller
         * @throws IOException if reading from the wrapped `PushBufferStream` fails
         */
        @Throws(IOException::class)
        override fun read(buffer: Buffer) {
            stream!!.read(buffer)
            if (isSendingDTMF) sendDTMF(buffer, tones.poll()!!) else if (isMute) mute(buffer)
        }

        /**
         * Implements [PushBufferStream.setTransferHandler]. Sets up
         * the hiding of the wrapped `PushBufferStream` from the specified
         * `transferHandler` and thus gives this `MutePushBufferStream` full control
         * when the `transferHandler` in question starts calling to the stream given to
         * it in `BufferTransferHandler#transferData(PushBufferStream)`.
         *
         * @param transferHandler a `BufferTransferHandler` to be notified by this instance when data is
         * available for reading from it
         */
        override fun setTransferHandler(transferHandler: BufferTransferHandler?) {
            stream!!.setTransferHandler(if (transferHandler == null) {
                null
            }
            else {
                StreamSubstituteBufferTransferHandler(transferHandler, stream, this)
            })
        }
    }

    companion object {
        /**
         * Replaces the media data contained in a specific `Buffer` with a compatible representation of silence.
         *
         * @param buffer the `Buffer` the data contained in which is to be replaced with silence
         */
        fun mute(buffer: Buffer) {
            val data = buffer.data
            if (data != null) {
                val dataClass = data.javaClass
                val fromIndex = buffer.offset
                val toIndex = fromIndex + buffer.length
                when {
                    Format.byteArray == dataClass -> Arrays.fill(data as ByteArray, fromIndex, toIndex, 0.toByte())
                    Format.intArray == dataClass -> Arrays.fill(data as IntArray, fromIndex, toIndex, 0)
                    Format.shortArray == dataClass -> Arrays.fill(data as ShortArray, fromIndex, toIndex, 0.toShort())
                }
                buffer.data = data
            }
        }

        /**
         * Replaces the media data contained in a specific `Buffer` with an inband DTMF tone
         * signal.
         *
         * @param buffer
         * the `Buffer` the data contained in which is to be replaced with the DTMF tone
         * @param tone
         * the `DMFTTone` to send via inband DTMF signal.
         */
        fun sendDTMF(buffer: Buffer, tone: DTMFInbandTone) {
            val data = buffer.data
            var format: Format? = null

            // Send the inband DTMF tone only if the buffer contains audio data.
            if (data != null && buffer.format.also { format = it } is AudioFormat) {
                val audioFormat = format as AudioFormat
                val sampleSizeInBits = audioFormat.sampleSizeInBits
                // Generates the inband DTMF signal.
                val samples = tone.getAudioSamples(audioFormat.sampleRate, sampleSizeInBits)
                val fromIndex = buffer.offset
                val toIndex = fromIndex + samples.size * (sampleSizeInBits / 8)
                val newData = ByteBuffer.allocate(toIndex)

                // Prepares newData to be endian compliant with original buffer
                // data.
                newData.order(if (audioFormat.endian == AudioFormat.BIG_ENDIAN) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)

                // Keeps data unchanged if stored before the original buffer offset
                // index. Takes care of original data array type (byte, short or int).
                val dataType: Class<*> = data.javaClass
                if (Format.byteArray == dataType) {
                    newData.put(data as ByteArray, 0, fromIndex)
                } else if (Format.shortArray == dataType) {
                    val shortData = data as ShortArray
                    for (i in 0 until fromIndex) newData.putShort(shortData[i])
                } else if (Format.intArray == dataType) {
                    val intData = data as IntArray
                    for (i in 0 until fromIndex) newData.putInt(intData[i])
                }
                when (sampleSizeInBits) {
                    8 -> for (sample in samples) newData.put(sample.toByte())
                    16 -> for (sample in samples) newData.putShort(sample)
                    24, 32 -> throw IllegalArgumentException("buffer.format.sampleSizeInBits must be either 8 or 16, not "
                            + sampleSizeInBits)
                    else -> throw IllegalArgumentException("buffer.format.sampleSizeInBits must be either 8 or 16, not "
                            + sampleSizeInBits)
                }

                // Copies newData up to date into the original buffer.
                // Takes care of original data array type (byte, short or int).
                when {
                    Format.byteArray == dataType -> buffer.data = newData.array()
                    Format.shortArray == dataType -> buffer.data = newData.asShortBuffer().array()
                    Format.intArray == dataType -> buffer.data = newData.asIntBuffer().array()
                }

                // Updates the buffer length.
                buffer.length = toIndex - fromIndex
            }
        }
    }
}