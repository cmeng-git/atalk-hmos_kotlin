/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.conference

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.control.ControlsAdapter
import org.atalk.util.ArrayIOUtils.writeShort
import timber.log.Timber
import java.io.IOException
import java.util.*
import javax.media.Buffer
import javax.media.Format
import javax.media.format.AudioFormat
import javax.media.format.UnsupportedFormatException
import javax.media.protocol.*
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Represents a `PushBufferStream` containing the result of the audio mixing of
 * `DataSource`s.
 *
 * @author Lyubomir Marinov
 */
class AudioMixingPushBufferStream
/**
 * Initializes a new `AudioMixingPushBufferStream` mixing the input data of a specific
 * `AudioMixerPushBufferStream` and excluding from the mix the audio contributions of a
 * specific `AudioMixingPushBufferDataSource`.
 *
 * @param audioMixerStream the `AudioMixerPushBufferStream` reading data from input `DataSource`s
 * and to push it to the new `AudioMixingPushBufferStream`
 * @param dataSource the `AudioMixingPushBufferDataSource` which has requested the initialization of
 * the new instance and which defines the input data to not be mixed in the output of the
 * new instance
 */
internal constructor(
        /**
         * The `AudioMixerPushBufferStream` which reads data from the input `DataSource`s
         * and pushes it to this instance to be mixed.
         */
        private val audioMixerStream: AudioMixerPushBufferStream,
        /**
         * The `AudioMixingPushBufferDataSource` which created and owns this instance and
         * defines the input data which is to not be mixed in the output of this `PushBufferStream`.
         */
        val dataSource: AudioMixingPushBufferDataSource) : ControlsAdapter(), PushBufferStream {
    /**
     * The total number of `byte`s read out of this `PushBufferStream` through
     * [.read]. Intended for the purposes of debugging at the time of this writing.
     */
    private var bytesRead = 0L
    /**
     * Gets the `AudioMixingPushBufferDataSource` which created and owns this instance and
     * defines the input data which is to not be mixed in the output of this
     * `PushBufferStream`.
     *
     * @return the `AudioMixingPushBufferDataSource` which created and owns this instance
     * and defines the input data which is to not be mixed in the output of this
     * `PushBufferStream`
     */

    /**
     * The collection of input audio samples still not mixed and read through this
     * `AudioMixingPushBufferStream`.
     */
    private var inSamples: Array<ShortArray?>? = null

    /**
     * The maximum number of per-stream audio samples available through `inSamples`.
     */
    private var maxInSampleCount = 0

    /**
     * The audio samples output by the last invocation of [.mix].
     * Cached in order to reduce allocations and garbage collection.
     */
    private var outSamples: ShortArray? = null

    /**
     * The `Object` which synchronizes the access to the data to be read from this
     * `PushBufferStream` i.e. to [.inSamples], [.maxInSampleCount] and
     * [.timeStamp].
     */
    private val readSyncRoot = Any()

    /**
     * The time stamp of [.inSamples] to be reported in the specified `Buffer` when
     * data is read from this instance.
     */
    private var timeStamp = Buffer.TIME_UNKNOWN

    /**
     * The `BufferTransferHandler` through which this `PushBufferStream` notifies its
     * clients that new data is available for reading.
     */
    private var mTransferHandler: BufferTransferHandler? = null

    private fun allocateOutSamples(minSize: Int): ShortArray {
        var outSamples = outSamples
        if (outSamples == null || outSamples.size < minSize) {
            outSamples = ShortArray(minSize)
            this.outSamples = outSamples
        }
        return outSamples
    }

    /**
     * Implements [SourceStream.endOfStream]. Delegates to the wrapped
     * `AudioMixerPushBufferStream` because this instance is just a facet to it.
     *
     * @return `true` if this `SourceStream` has reached the end of the media it
     * makes available; otherwise, `false`
     */
    override fun endOfStream(): Boolean {
        /*
         * TODO If the inSamples haven't been consumed yet, don't report the end of this stream
         * even if the wrapped stream has reached its end.
         */
        return audioMixerStream.endOfStream()
    }

    /**
     * Implements [SourceStream.getContentDescriptor]. Delegates to the wrapped
     * `AudioMixerPushBufferStream` because this instance is just a facet to it.
     *
     * @return a `ContentDescriptor` which describes the content being made available by
     * this `SourceStream`
     */
    override fun getContentDescriptor(): ContentDescriptor {
        return audioMixerStream.contentDescriptor
    }

    /**
     * Implements [SourceStream.getContentLength]. Delegates to the wrapped
     * `AudioMixerPushBufferStream` because this instance is just a facet to it.
     *
     * @return the length of the media being made available by this `SourceStream`
     */
    override fun getContentLength(): Long {
        return audioMixerStream.contentLength
    }

    /**
     * Implements [PushBufferStream.getFormat]. Delegates to the wrapped
     * `AudioMixerPushBufferStream` because this instance is just a facet to it.
     *
     * @return the `Format` of the audio being made available by this
     * `PushBufferStream`
     */
    override fun getFormat(): AudioFormat {
        return audioMixerStream.outFormat
    }

    /**
     * Mixes as in audio mixing a specified collection of audio sample sets and returns the
     * resulting mix audio sample set in a specific `AudioFormat`.
     *
     * @param inSamples the collection of audio sample sets to be mixed into one audio sample set in the sense
     * of audio mixing
     * @param outFormat the `AudioFormat` in which the resulting mix audio sample set is to be
     * produced. The `format` property of the specified `outBuffer` is expected
     * to be set to the same value but it is provided as a method argument in order to avoid
     * casting from `Format` to `AudioFormat` .
     * @param outSampleCount the size of the resulting mix audio sample set to be produced
     * @return the resulting audio sample set of the audio mixing of the specified input audio
     * sample sets
     */
    private fun mix(inSamples: Array<ShortArray?>, outFormat: AudioFormat, outSampleCount: Int): ShortArray {
        val outSamples: ShortArray

        /*
         * The trivial case of performing mixing the samples of a single stream. Then there is
         * nothing to mix and the input becomes the output.
         */
        if (inSamples.size == 1 || inSamples[1] == null) {
            val inStreamSamples = inSamples[0]
            val inStreamSampleCount: Int
            if (inStreamSamples == null) {
                inStreamSampleCount = 0
                outSamples = allocateOutSamples(outSampleCount)
            } else if (inStreamSamples.size < outSampleCount) {
                inStreamSampleCount = inStreamSamples.size
                outSamples = allocateOutSamples(outSampleCount)
                System.arraycopy(inStreamSamples, 0, outSamples, 0, inStreamSampleCount)
            } else {
                inStreamSampleCount = outSampleCount
                outSamples = inStreamSamples
            }
            if (inStreamSampleCount != outSampleCount) {
                Arrays.fill(outSamples, inStreamSampleCount, outSampleCount, 0.toShort())
            }
            return outSamples
        }
        outSamples = allocateOutSamples(outSampleCount)
        Arrays.fill(outSamples, 0, outSampleCount, 0.toShort())
        val maxOutSample = try {
            getMaxOutSample(outFormat).toFloat()
        } catch (ufex: UnsupportedFormatException) {
            throw UnsupportedOperationException(ufex)
        }
        for (inStreamSamples in inSamples) {
            if (inStreamSamples != null) {
                val inStreamSampleCount = min(inStreamSamples.size, outSampleCount)
                if (inStreamSampleCount != 0) {
                    for (i in 0 until inStreamSampleCount) {
                        val inStreamSample = inStreamSamples[i].toInt()
                        val outSample = outSamples[i].toInt()
                        outSamples[i] = (inStreamSample + outSample
                                - (inStreamSample * (outSample / maxOutSample)).roundToLong()).toShort()
                    }
                }
            }
        }
        return outSamples
    }

    /**
     * Implements [PushBufferStream.read]. If `inSamples` are available, mixes
     * them and writes the mix to the specified `Buffer` performing the necessary data type
     * conversions.
     *
     * @param buffer the `Buffer` to receive the data read from this instance
     * @throws IOException if anything wrong happens while reading from this instance
     */
    @Throws(IOException::class)
    override fun read(buffer: Buffer) {
        var inSamples: Array<ShortArray?>?
        var maxInSampleCount: Int
        var timeStamp: Long
        synchronized(readSyncRoot) {
            inSamples = this.inSamples
            maxInSampleCount = this.maxInSampleCount
            timeStamp = this.timeStamp
            this.inSamples = null
            this.maxInSampleCount = 0
        }
        if (inSamples == null || inSamples!!.isEmpty() || maxInSampleCount <= 0) {
            buffer.isDiscard = true
            return
        }
        val outFormat = format
        val outSamples = mix(inSamples!!, outFormat, maxInSampleCount)
        val outSampleCount = min(maxInSampleCount, outSamples.size)
        if (Format.byteArray == outFormat.dataType) {
            val outLength: Int
            val o = buffer.data
            var outData: ByteArray? = null
            if (o is ByteArray) outData = o
            when (outFormat.sampleSizeInBits) {
                16 -> {
                    outLength = outSampleCount * 2
                    if (outData == null || outData.size < outLength) outData = ByteArray(outLength)
                    var i = 0
                    while (i < outSampleCount) {
                        writeShort(outSamples[i], outData, i * 2)
                        i++
                    }
                }
                8, 24, 32 -> throw UnsupportedOperationException(
                        "AudioMixingPushBufferStream.read(Buffer)")
                else -> throw UnsupportedOperationException(
                        "AudioMixingPushBufferStream.read(Buffer)")
            }
            buffer.data = outData
            buffer.format = outFormat
            buffer.length = outLength
            buffer.offset = 0
            buffer.timeStamp = timeStamp
            bytesRead += outLength.toLong()
        } else {
            throw UnsupportedOperationException("AudioMixingPushBufferStream.read(Buffer)")
        }
    }

    /**
     * Sets the collection of audio sample sets to be mixed in the sense of audio mixing by this
     * stream when data is read from it. Triggers a push to the clients of this stream.
     *
     * @param inSamples the collection of audio sample sets to be mixed by this stream when data is read from
     * it
     * @param maxInSampleCount the maximum number of per-stream audio samples available through `inSamples`
     * @param timeStamp the time stamp of `inSamples` to be reported in the specified `Buffer`
     * when data is read from this instance
     */
    fun setInSamples(inSamples: Array<ShortArray?>?, maxInSampleCount: Int, timeStamp: Long) {
        synchronized(readSyncRoot) {
            this.inSamples = inSamples
            this.maxInSampleCount = maxInSampleCount
            this.timeStamp = timeStamp
        }
        mTransferHandler?.transferData(this)
    }

    /**
     * Implements [PushBufferStream.setTransferHandler]. Sets the `BufferTransferHandler` which
     * is to be notified by this instance when it has media available for reading.
     *
     * @param transferHandler the `BufferTransferHandler` to be notified by this instance when it has media
     * available for reading
     */
    override fun setTransferHandler(transferHandler: BufferTransferHandler) {
        this.mTransferHandler = transferHandler
    }

    /**
     * Starts the pushing of data out of this stream.
     *
     * @throws IOException if starting the pushing of data out of this stream fails
     */
    @Synchronized
    @Throws(IOException::class)
    fun start() {
        audioMixerStream.addOutStream(this)
        Timber.log(TimberLog.FINER, "Started %s with hashCode %s", javaClass.simpleName, hashCode())
    }

    /**
     * Stops the pushing of data out of this stream.
     *
     * @throws IOException if stopping the pushing of data out of this stream fails
     */
    @Synchronized
    @Throws(IOException::class)
    fun stop() {
        audioMixerStream.removeOutStream(this)
        Timber.log(TimberLog.FINER, "Stopped %s with hashCode %s", javaClass.simpleName, hashCode())
    }

    companion object {
        /**
         * Gets the maximum possible value for an audio sample of a specific `AudioFormat`.
         *
         * @param outFormat the `AudioFormat` of which to get the maximum possible value for an audio
         * sample
         * @return the maximum possible value for an audio sample of the specified `AudioFormat`
         * @throws UnsupportedFormatException if the specified `outFormat` is not supported by the underlying implementation
         */
        @Throws(UnsupportedFormatException::class)
        private fun getMaxOutSample(outFormat: AudioFormat): Int {
            return when (outFormat.sampleSizeInBits) {
                8 -> Byte.MAX_VALUE.toInt()
                16 -> Short.MAX_VALUE.toInt()
                32 -> Int.MAX_VALUE
                24 -> throw UnsupportedFormatException("Format.getSampleSizeInBits()", outFormat)
                else -> throw UnsupportedFormatException("Format.getSampleSizeInBits()", outFormat)
            }
        }
    }
}