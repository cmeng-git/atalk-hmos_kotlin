/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.conference

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.control.ControlsAdapter
import org.atalk.impl.neomedia.protocol.CachingPushBufferStream
import org.atalk.impl.neomedia.protocol.StreamSubstituteBufferTransferHandler
import org.atalk.util.ArrayIOUtils
import timber.log.Timber
import java.io.IOException
import java.lang.reflect.UndeclaredThrowableException
import java.util.*
import javax.media.Buffer
import javax.media.Format
import javax.media.control.BufferControl
import javax.media.format.AudioFormat
import javax.media.format.UnsupportedFormatException
import javax.media.protocol.BufferTransferHandler
import javax.media.protocol.ContentDescriptor
import javax.media.protocol.DataSource
import javax.media.protocol.PullBufferStream
import javax.media.protocol.PushBufferStream
import javax.media.protocol.SourceStream
import kotlin.math.max

/**
 * Represents a `PushBufferStream` which reads data from the `SourceStream`s of the
 * input `DataSource`s of the associated `AudioMixer` and pushes it to
 * `AudioMixingPushBufferStream`s for audio mixing.
 *
 *
 * Pretty much private to `AudioMixer` but extracted into its own file for the sake of
 * clarity.
 *
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
open class AudioMixerPushBufferStream
/**
 * Initializes a new `AudioMixerPushBufferStream` instance to output data in a specific
 * `AudioFormat` for a specific `AudioMixer`.
 *
 * @param audioMixer the `AudioMixer` which creates this instance and for which it is to output data
 * @param outFormat the `AudioFormat` in which the new instance is to output data
 */
(
        /**
         * The `AudioMixer` which created this `AudioMixerPushBufferStream`.
         */
        private val audioMixer: AudioMixer,
        /**
         * The `AudioFormat` of the data this instance outputs.
         */
        val outFormat: AudioFormat,
) : ControlsAdapter(), PushBufferStream {
    /**
     * The `SourceStream`s (in the form of `InStreamDesc` so that this instance can
     * track back the `AudioMixingPushBufferDataSource` which outputs the mixed audio stream
     * and determine whether the associated `SourceStream` is to be included into the mix)
     * from which this instance reads its data.
     */
    private var inStreams: Array<InStreamDesc>? = null

    /**
     * The `Object` which synchronizes the access to [.inStreams] -related members.
     */
    private val inStreamsSyncRoot = Any()

    /**
     * The cache of `short` arrays utilized by this instance for the purposes of reducing
     * garbage collection.
     */
    private val shortArrayCache = ShortArrayCache()

    /**
     * The `AudioFormat` of the `Buffer` read during the last read from one of the
     * [.inStreams]. Only used for debugging purposes.
     */
    private var lastReadInFormat: AudioFormat? = null
    /**
     * Implements [PushBufferStream.getFormat]. Returns the `AudioFormat` in which
     * this instance was configured to output its data.
     *
     * @return the `AudioFormat` in which this instance was configured to output its data
     */

    /**
     * The `AudioMixingPushBufferStream`s to which this instance pushes data for audio
     * mixing.
     */
    private val outStreams = ArrayList<AudioMixingPushBufferStream>()

    /**
     * The number of times that [.outStreams] has been modified via
     * [.addOutStream] and
     * [.removeOutStream] in order to allow
     * [AudioMixer.start] and
     * [AudioMixer.stop] to be invoked outside blocks
     * synchronized on `outStreams`.
     */
    private var outStreamsGeneration = 0L

    /**
     * The `BufferTransferHandler` through which this instance gets notifications from its
     * input `SourceStream`s that new data is available for audio mixing.
     */
    private val transferHandler = object : BufferTransferHandler {
        /**
         * The cached `Buffer` instance to be used during the execution of
         * [.transferData] in order to reduce garbage collection.
         */
        private val buffer = Buffer()
        override fun transferData(stream: PushBufferStream) {
            buffer.isDiscard = false
            buffer.flags = 0
            buffer.length = 0
            buffer.offset = 0
            this@AudioMixerPushBufferStream.transferData(buffer)
        }
    }

    /**
     * A copy of [.outStreams] which will cause no `ConcurrentModificationException`
     * and which has been introduced to reduce allocations and garbage collection.
     */
    private var unmodifiableOutStreams: Array<AudioMixingPushBufferStream>? = null

    /**
     * Adds a specific `AudioMixingPushBufferStream` to the collection of such streams to
     * which this instance is to push the data for audio mixing it reads from its input
     * `SourceStream`s.
     *
     * @param outStream the `AudioMixingPushBufferStream` to add to the collection of such streams to
     * which this instance is to push the data for audio mixing it reads from its input
     * `SourceStream`s
     * @throws IOException if `outStream` was the first `AudioMixingPushBufferStream` and the
     * `AudioMixer` failed to start
     */
    @Throws(IOException::class)
    fun addOutStream(outStream: AudioMixingPushBufferStream?) {
        requireNotNull(outStream) { "outStream" }
        var start = false
        var generation = 0L
        synchronized(outStreams) {
            if (!outStreams.contains(outStream) && outStreams.add(outStream)) {
                unmodifiableOutStreams = null
                if (outStreams.size == 1) {
                    start = true
                    generation = ++outStreamsGeneration
                }
            }
        }
        if (start) {
            /*
             * The start method of AudioMixer is potentially blocking so it has been moved out of
             * synchronized blocks in order to reduce the risks of deadlocks.
             */
            audioMixer.start(this, generation)
        }
    }

    /**
     * Implements [SourceStream.endOfStream]. Delegates to the input `SourceStreams`
     * of this instance.
     *
     * @return `true` if all input `SourceStream`s of this instance have reached the
     * end of their content; `false`, otherwise
     */
    override fun endOfStream(): Boolean {
        synchronized(inStreamsSyncRoot) {
            if (inStreams != null) {
                for (inStreamDesc in inStreams!!) {
                    if (!inStreamDesc.inStream.endOfStream()) return false
                }
            }
        }
        return true
    }

    /**
     * Attempts to equalize the length in milliseconds of the buffering performed by the
     * `inStreams` in order to always read and mix one and the same length in milliseconds.
     */
    fun equalizeInStreamBufferLength() {
        synchronized(inStreamsSyncRoot) {
            if (inStreams == null || inStreams!!.isEmpty()) return

            /*
             * The first inStream is expected to be from the CaptureDevice and no custom
             * BufferControl is provided for it so the bufferLength is whatever it says.
             */
            val bufferControl = getBufferControl(inStreams!![0]) as BufferControl?
            val bufferLength = bufferControl?.bufferLength
                    ?: CachingPushBufferStream.DEFAULT_BUFFER_LENGTH

            for (i in 1 until inStreams!!.size) {
                (getBufferControl(inStreams!![i]) as BufferControl?)?.bufferLength = bufferLength
            }
        }
    }

    /**
     * Gets the `BufferControl` of a specific input stream. The returned
     * `BufferControl` may be available through its input `DataSource`, its
     * transcoding `DataSource` if any or the very input stream.
     *
     * @param inStreamDesc an `InStreamDesc` which describes the input stream and its originating
     * `DataSource`s from which the `BufferControl` is to be retrieved
     * @return the `BufferControl` of the specified input stream found in its input
     * `DataSource`, its transcoding `DataSource` if any or the very input
     * stream if such a control exists; otherwise, `null`
    `` */
    private fun getBufferControl(inStreamDesc: InStreamDesc): BufferControl {
        val inDataSourceDesc = inStreamDesc.inDataSourceDesc

        // Try the DataSource which directly provides the specified inStream.
        val effectiveInDataSource = inDataSourceDesc.effectiveInDataSource
        val bufferControlType = BufferControl::class.java.name
        if (effectiveInDataSource != null) {
            val bufferControl = effectiveInDataSource.getControl(bufferControlType) as BufferControl?
            if (bufferControl != null) return bufferControl
        }

        /*
         * If transcoding is taking place and the transcodingDataSource does not have a
         * BufferControl, try the inDataSource which is being transcoded.
         */
        val inDataSource = inDataSourceDesc.inDataSource as DataSource?
        if (inDataSource != null && inDataSource != effectiveInDataSource) {
            val bufferControl = inDataSource.getControl(bufferControlType) as BufferControl?
            if (bufferControl != null) return bufferControl
        }

        // If everything else has failed, try the very inStream.
        return inStreamDesc.inStream.getControl(bufferControlType) as BufferControl
    }

    /**
     * Implements [SourceStream.getContentDescriptor]. Returns a `ContentDescriptor`
     * which describes the content type of this instance.
     *
     * @return a `ContentDescriptor` which describes the content type of this instance
     */
    override fun getContentDescriptor(): ContentDescriptor {
        return ContentDescriptor(audioMixer.contentType)
    }

    /**
     * Implements [SourceStream.getContentLength]. Delegates to the input
     * `SourceStreams` of this instance.
     *
     * @return the length of the content made available by this instance which is the maximum
     * length of the contents made available by its input `StreamSource`s
     */
    override fun getContentLength(): Long {
        var contentLength = 0L
        synchronized(inStreamsSyncRoot) {
            if (inStreams != null) for (inStreamDesc in inStreams!!) {
                val inContentLength = inStreamDesc.inStream.contentLength
                if (SourceStream.LENGTH_UNKNOWN == inContentLength) return SourceStream.LENGTH_UNKNOWN
                if (contentLength < inContentLength) contentLength = inContentLength
            }
        }
        return contentLength
    }

    override fun getFormat(): Format {
        return outFormat
    }

    /**
     * Gets the `SourceStream`s (in the form of `InStreamDesc`s) from which this
     * instance reads audio samples.
     *
     * @return an array of `InStreamDesc`s which describe the input `SourceStream`s
     * from which this instance reads audio samples
     */
    fun getInStreams(): Array<InStreamDesc>? {
        synchronized(inStreamsSyncRoot) { return if (inStreams == null) null else inStreams!!.clone() }
    }

    /**
     * Implements [PushBufferStream.read]. Reads audio samples from the input
     * `SourceStreams` of this instance in various formats, converts the read audio samples
     * to one and the same format and pushes them to the output
     * `AudioMixingPushBufferStream`s for the very audio mixing.
     *
     * @param buffer the `Buffer` in which the audio samples read from the input
     * `SourceStream`s are to be returned to the caller
     * @throws IOException if any of the input `SourceStream`s throws such an exception while reading
     * from them or anything else goes wrong
     */
    @Throws(IOException::class)
    override fun read(buffer: Buffer) {
        var inSampleDesc: InSampleDesc?
        var inStreamCount: Int
        val format = outFormat
        synchronized(inStreamsSyncRoot) {
            val inStreams = inStreams
            if (inStreams == null || inStreams.isEmpty()) {
                return
            } else {
                inSampleDesc = buffer.data as InSampleDesc
                // format
                if (inSampleDesc != null && inSampleDesc!!.format != format) inSampleDesc = null
                // inStreams
                inStreamCount = inStreams.size
                if (inSampleDesc != null) {
                    val inSampleDescInStreams = inSampleDesc!!.inStreams
                    if (inSampleDescInStreams.size == inStreamCount) {
                        for (i in 0 until inStreamCount) {
                            if (inSampleDescInStreams[i] != inStreams[i]) {
                                inSampleDesc = null
                                break
                            }
                        }
                    } else {
                        inSampleDesc = null
                    }
                }
                if (inSampleDesc == null) {
                    inSampleDesc = InSampleDesc(arrayOfNulls(inStreamCount), inStreams.clone(), format)
                }
            }
        }
        var maxInSampleCount = try {
            readInPushBufferStreams(format, inSampleDesc)
        } catch (ufex: UnsupportedFormatException) {
            val ioex = IOException()
            ioex.initCause(ufex)
            throw ioex
        }
        maxInSampleCount = max(maxInSampleCount,
                readInPullBufferStreams(format, maxInSampleCount, inSampleDesc))
        buffer.data = inSampleDesc
        buffer.length = maxInSampleCount

        /*
         * Convey the timeStamp so that it can be reported by the Buffers of the
         * AudioMixingPushBufferStreams when mixes are read from them.
         */
        val timeStamp = inSampleDesc!!.timeStamp
        if (timeStamp != Buffer.TIME_UNKNOWN) buffer.timeStamp = timeStamp
    }

    /**
     * Reads audio samples from the input `PullBufferStream`s of this instance and converts
     * them to a specific output `AudioFormat`. An attempt is made to read a specific
     * maximum number of samples from each of the `PullBufferStream`s but the very
     * `PullBufferStream` may not honor the request.
     *
     * @param outFormat the `AudioFormat` in which the audio samples read from the
     * `PullBufferStream`s are to be converted before being returned
     * @param outSampleCount the maximum number of audio samples to be read from each of the
     * `PullBufferStream`s but the very `PullBufferStream`s may not honor the
     * request
     * @param inSampleDesc an `InStreamDesc` which specifies the input streams to be read and the
     * collection of audio samples in which the read audio samples are to be returned
     * @return the maximum number of audio samples actually read from the input
     * `PullBufferStream`s of this instance
     * @throws IOException if anything goes wrong while reading the specified input streams
     */
    @Throws(IOException::class)
    private fun readInPullBufferStreams(outFormat: AudioFormat, outSampleCount: Int, inSampleDesc: InSampleDesc?): Int {
        val inStreams = inSampleDesc!!.inStreams
        val maxInSampleCount = 0
        for (inStream in inStreams) if (inStream.inStream is PullBufferStream)
            throw UnsupportedOperationException(AudioMixerPushBufferStream::class.java.simpleName
                    + ".readInPullBufferStreams(AudioFormat,int,InSampleDesc)")
        return maxInSampleCount
    }

    /**
     * Reads audio samples from a specific `PushBufferStream` and converts them to a
     * specific output `AudioFormat`. An attempt is made to read a specific maximum number
     * of samples from the specified `PushBufferStream` but the very
     * `PushBufferStream` may not honor the request.
     *
     * @param inStreamDesc an `InStreamDesc` which specifies the input `PushBufferStream` to read
     * from
     * @param outFormat the `AudioFormat` to which the samples read from `inStream` are to be
     * converted before being returned
     * @param sampleCount the maximum number of samples which the read operation should attempt to read from
     * `inStream` but the very `inStream` may not honor the request
     * @param outBuffer the `Buffer` into which the array of `int` audio samples read from the
     * specified `inStream` is to be written
     * @throws IOException if anything wrong happens while reading `inStream`
     * @throws UnsupportedFormatException if converting the samples read from `inStream` to `outFormat` fails
     */
    @Throws(IOException::class, UnsupportedFormatException::class)
    private fun readInPushBufferStream(
            inStreamDesc: InStreamDesc?, outFormat: AudioFormat,
            sampleCount: Int, outBuffer: Buffer?,
    ) {
        val inStream = inStreamDesc!!.inStream as PushBufferStream
        val inStreamFormat = inStream.format as AudioFormat
        val inBuffer = inStreamDesc.getBuffer(true)
        if (sampleCount != 0) {
            if (Format.byteArray == inStreamFormat.dataType) {
                val data = inBuffer!!.data
                val length = sampleCount * (inStreamFormat.sampleSizeInBits / 8)
                if (data !is ByteArray || data.size != length) inBuffer.data = ByteArray(length)
            } else {
                throw UnsupportedFormatException("!Format.getDataType().equals(byte[].class)",
                        inStreamFormat)
            }
        }
        inBuffer!!.isDiscard = false
        inBuffer.flags = 0
        inBuffer.length = 0
        inBuffer.offset = 0
        audioMixer.read(inStream, inBuffer, inStreamDesc.inDataSourceDesc.inDataSource)

        /*
         * If the media is to be discarded, don't even bother with the checks and the conversion.
         */
        if (inBuffer.isDiscard) {
            outBuffer!!.isDiscard = true
            return
        }
        val inLength = inBuffer.length
        if (inLength <= 0) {
            outBuffer!!.isDiscard = true
            return
        }
        var inFormat = inBuffer.format as AudioFormat?
        if (inFormat == null) inFormat = inStreamFormat
        if (TimberLog.isTraceEnable) {
            if (lastReadInFormat == null) lastReadInFormat = inFormat else if (!lastReadInFormat!!.matches(inFormat)) {
                lastReadInFormat = inFormat
                Timber.log(TimberLog.FINER, "Read inSamples in different format %s", lastReadInFormat)
            }
        }
        val inFormatSigned = inFormat.signed
        if (inFormatSigned != AudioFormat.SIGNED && inFormatSigned != Format.NOT_SPECIFIED) {
            throw UnsupportedFormatException("AudioFormat.getSigned()", inFormat)
        }
        val inChannels = inFormat.channels
        val outChannels = outFormat.channels
        if (inChannels != outChannels && inChannels != Format.NOT_SPECIFIED && outChannels != Format.NOT_SPECIFIED) {
            Timber.e("Read inFormat with channels %s  while expected outFormat channels is %s",
                    inChannels, outChannels)
            throw UnsupportedFormatException("AudioFormat.getChannels()", inFormat)
        }

        // Warn about different sampleRates.
        val inSampleRate = inFormat.sampleRate
        val outSampleRate = outFormat.sampleRate
        if (inSampleRate != outSampleRate) {
            Timber.w("Read inFormat with sampleRate " + inSampleRate
                    + " while expected outFormat sampleRate is " + outSampleRate)
        }
        val inData = inBuffer.data
        when (inData) {
            null -> {
                outBuffer!!.isDiscard = true
            }

            is ByteArray -> {
                val inSampleSizeInBits = inFormat.sampleSizeInBits
                val outSampleSizeInBits = outFormat.sampleSizeInBits
                if (inSampleSizeInBits != outSampleSizeInBits) {
                    Timber.log(TimberLog.FINER, "Read inFormat with sampleSizeInBits " + inSampleSizeInBits
                            + ". Will convert to sampleSizeInBits " + outSampleSizeInBits)
                }
                val outLength: Int
                val outSamples: ShortArray?
                when (inSampleSizeInBits) {
                    16 -> {
                        outLength = inLength / 2
                        outSamples = shortArrayCache.validateShortArraySize(outBuffer, outLength)
                        when (outSampleSizeInBits) {
                            16 -> {
                                var i = 0
                                while (i < outLength) {
                                    outSamples!![i] = ArrayIOUtils.readShort(inData, i * 2)
                                    i++
                                }
                            }
                            8, 24, 32 -> throw UnsupportedFormatException("AudioFormat.getSampleSizeInBits()", outFormat)
                            else -> throw UnsupportedFormatException("AudioFormat.getSampleSizeInBits()", outFormat)
                        }
                    }
                    8, 24, 32 -> throw UnsupportedFormatException("AudioFormat.getSampleSizeInBits()", inFormat)
                    else -> throw UnsupportedFormatException("AudioFormat.getSampleSizeInBits()", inFormat)
                }
                outBuffer!!.flags = inBuffer.flags
                outBuffer.format = outFormat
                outBuffer.length = outLength
                outBuffer.offset = 0
                outBuffer.timeStamp = inBuffer.timeStamp
            }

            else -> {
                throw UnsupportedFormatException("Format.getDataType().equals(" + inData.javaClass
                        + ")", inFormat)
            }
        }
    }

    /**
     * Reads audio samples from the input `PushBufferStream`s of this instance and converts
     * them to a specific output `AudioFormat`.
     *
     * @param outFormat the `AudioFormat` in which the audio samples read from the
     * `PushBufferStream`s are to be converted before being returned
     * @param inSampleDesc an `InSampleDesc` which specifies the input streams to be read and the
     * collection of audio samples in which the read audio samples are to be returned
     * @return the maximum number of audio samples actually read from the input
     * `PushBufferStream`s of this instance
     * @throws IOException if anything wrong happens while reading the specified input streams
     * @throws UnsupportedFormatException if any of the input streams provides media in a format different than
     * `outFormat`
     */
    @Throws(IOException::class, UnsupportedFormatException::class)
    private fun readInPushBufferStreams(outFormat: AudioFormat, inSampleDesc: InSampleDesc?): Int {
        val inStreams = inSampleDesc!!.inStreams
        val buffer = inSampleDesc.buffer
        var maxInSampleCount = 0
        val inSamples = inSampleDesc.inSamples
        for (i in inStreams.indices) {
            val inStreamDesc = inStreams[i]
            val inStream = inStreamDesc.inStream
            if (inStream is PushBufferStream) {
                buffer!!.isDiscard = false
                buffer.flags = 0
                buffer.length = 0
                buffer.offset = 0
                readInPushBufferStream(inStreamDesc, outFormat, maxInSampleCount, buffer)
                var sampleCount: Int
                var samples: ShortArray?
                if (buffer.isDiscard) {
                    sampleCount = 0
                    samples = null
                } else {
                    sampleCount = buffer.length
                    if (sampleCount <= 0) {
                        sampleCount = 0
                        samples = null
                    } else {
                        samples = buffer.data as ShortArray
                    }
                }
                if (sampleCount != 0) {
                    /*
                     * The short array with the samples will be used via inputSamples so the buffer
                     * cannot use it anymore.
                     */
                    buffer.data = null

                    /*
                     * If the samples array has more elements than sampleCount, the elements in
                     * question may contain stale samples.
                     */
                    if (samples!!.size > sampleCount) {
                        Arrays.fill(samples, sampleCount, samples.size, 0.toShort())
                    }
                    inSamples[i] = if (buffer.flags and Buffer.FLAG_SILENCE == 0) samples else null
                    if (maxInSampleCount < samples.size) maxInSampleCount = samples.size

                    /*
                     * Convey the timeStamp so that it can be set to the Buffers of the
                     * AudioMixingPushBufferStreams when mixes are read from them. Since the
                     * inputStreams will report different timeStamps, only use the first meaningful
                     * timestamp for now.
                     */
                    if (inSampleDesc.timeStamp == Buffer.TIME_UNKNOWN) inSampleDesc.timeStamp = buffer.timeStamp
                    continue
                }
            }
            inSamples[i] = null
        }
        return maxInSampleCount
    }

    /**
     * Removes a specific `AudioMixingPushBufferStream` from the collection of such streams
     * to which this instance pushes the data for audio mixing it reads from its input
     * `SourceStream`s.
     *
     * @param outStream the `AudioMixingPushBufferStream` to remove from the collection of such streams
     * to which this instance pushes the data for audio mixing it reads from its input
     * `SourceStream`s
     * @throws IOException if `outStream` was the last `AudioMixingPushBufferStream` and the
     * `AudioMixer` failed to stop
     */
    @Throws(IOException::class)
    fun removeOutStream(outStream: AudioMixingPushBufferStream?) {
        var stop = false
        var generation = 0L
        synchronized(outStreams) {
            if (outStream != null && outStreams.remove(outStream)) {
                unmodifiableOutStreams = null
                if (outStreams.isEmpty()) {
                    stop = true
                    generation = ++outStreamsGeneration
                }
            }
        }
        if (stop) {
            /*
             * The stop method of AudioMixer is potentially blocking so it has been moved out of
             * synchronized blocks in order to reduce the risks of deadlocks.
             */
            audioMixer.stop(this, generation)
        }
    }

    /**
     * Pushes a copy of a specific set of input audio samples to a specific
     * `AudioMixingPushBufferStream` for audio mixing. Audio samples read from input
     * `DataSource`s which the `AudioMixingPushBufferDataSource` owner of the
     * specified `AudioMixingPushBufferStream` has specified to not be included in the
     * output mix are not pushed to the `AudioMixingPushBufferStream` .
     *
     * @param outStream the `AudioMixingPushBufferStream` to push the specified set of audio samples to
     * @param inSampleDesc the set of audio samples to be pushed to `outStream` for audio mixing
     * @param maxCount the maximum number of audio samples available in `inSamples`
     */
    private fun setInSamples(outStream: AudioMixingPushBufferStream, inSampleDesc: InSampleDesc, maxCount: Int) {
        var maxInSampleCount = maxCount
        var inSamples = inSampleDesc.inSamples
        val inStreams = inSampleDesc.inStreams
        inSamples = inSamples.clone()
        val captureDevice = audioMixer.captureDevice
        val outDataSource = outStream.dataSource
        val outDataSourceIsSendingDTMF = (captureDevice is AudioMixingPushBufferDataSource
                && outDataSource.isSendingDTMF)
        val outDataSourceIsMute = outDataSource.isMute
        var i = 0
        var o = 0
        while (i < inSamples.size) {
            val inStreamDesc = inStreams[i]
            val inDataSource = inStreamDesc.inDataSourceDesc.inDataSource
            if (outDataSourceIsSendingDTMF && inDataSource == captureDevice) {
                val inStream = inStreamDesc.inStream as PushBufferStream
                val inStreamFormat = inStream.format as AudioFormat
                // Generate the inband DTMF signal.
                val nextToneSignal = outDataSource.getNextToneSignal(
                        inStreamFormat.sampleRate, inStreamFormat.sampleSizeInBits)
                inSamples[i] = nextToneSignal
                if (maxInSampleCount < nextToneSignal.size) maxInSampleCount = nextToneSignal.size
            } else if (outDataSource == inStreamDesc.outDataSource || outDataSourceIsMute && inDataSource === captureDevice) {
                inSamples[i] = null
            }

            /*
             * Have the samples of the contributing streams at the head of the sample set (and the
             * non-contributing at the tail) in order to optimize determining the number of
             * contributing streams later on and, consequently, the mixing.
             */
            val inStreamSamples = inSamples[i]
            if (inStreamSamples != null) {
                if (i != o) {
                    inSamples[o] = inStreamSamples
                    inSamples[i] = null
                }
                o++
            }
            i++
        }
        outStream.setInSamples(inSamples, maxInSampleCount, inSampleDesc.timeStamp)
    }

    /**
     * Sets the `SourceStream`s (in the form of `InStreamDesc`) from which this
     * instance is to read audio samples and push them to the `AudioMixingPushBufferStream`s
     * for audio mixing.
     *
     * @param inStreams the `SourceStream`s (in the form of `InStreamDesc`) from which this
     * instance is to read audio samples and push them to the
     * `AudioMixingPushBufferStream`s for audio mixing
     */
    fun setInStreams(inStreams: Collection<InStreamDesc>?) {
        var oldValue: Array<InStreamDesc>?
        val newValue = inStreams?.toTypedArray()

        synchronized(inStreamsSyncRoot) {
            oldValue = this.inStreams
            this.inStreams = newValue
        }

        val valueIsChanged = !Arrays.equals(oldValue, newValue)
        if (valueIsChanged) {
            if (oldValue != null) setTransferHandler(oldValue, null)
            if (newValue == null) return
            var skippedForTransferHandler = false
            for (inStreamDesc in newValue) {
                val inStream = inStreamDesc.inStream as? PushBufferStream ?: continue
                if (!skippedForTransferHandler) {
                    skippedForTransferHandler = true
                    continue
                }
                if (inStream !is CachingPushBufferStream) {
                    val cachingInStream = CachingPushBufferStream(inStream)
                    inStreamDesc.inStream = cachingInStream
                    Timber.log(TimberLog.FINER, "Created CachingPushBufferStream with hashCode "
                            + cachingInStream.hashCode() + " for inStream with hashCode "
                            + inStream.hashCode())
                }
            }
            setTransferHandler(newValue, transferHandler)
            equalizeInStreamBufferLength()
            if (TimberLog.isTraceEnable) {
                val oldValueLength = if (oldValue == null) 0 else oldValue!!.size
                val difference = newValue.size - oldValueLength
                when {
                    difference > 0 -> Timber.log(TimberLog.FINER, "Added %s inStream(s) and the total is 5s", difference, newValue.size)
                    difference < 0 -> Timber.log(TimberLog.FINER, "Removed %s inStream(s) and the total is %s", difference, newValue.size)
                }
            }
        }
    }

    /**
     * Implements [PushBufferStream.setTransferHandler]. Because this
     * instance pushes data to multiple output `AudioMixingPushBufferStreams`, a single
     * `BufferTransferHandler` is not sufficient and thus this method is unsupported and
     * throws `UnsupportedOperationException`.
     *
     * @param transferHandler the `BufferTransferHandler` to be notified by this `PushBufferStream`
     * when media is available for reading
     */
    override fun setTransferHandler(transferHandler: BufferTransferHandler) {
        throw UnsupportedOperationException(AudioMixerPushBufferStream::class.java.simpleName
                + ".setTransferHandler(BufferTransferHandler)")
    }

    /**
     * Sets a specific `BufferTransferHandler` to a specific collection of
     * `SourceStream`s (in the form of `InStreamDesc`) abstracting the differences
     * among the various types of `SourceStream`s.
     *
     * @param inStreams the input `SourceStream`s to which the specified `BufferTransferHandler`
     * is to be set
     * @param transferHandler the `BufferTransferHandler` to be set to the specified `inStreams`
     */
    private fun setTransferHandler(inStreams: Array<InStreamDesc>?, transferHandler: BufferTransferHandler?) {
        if (inStreams == null || inStreams.isEmpty()) return
        var transferHandlerIsSet = false
        for (inStreamDesc in inStreams) {
            val inStream = inStreamDesc.inStream
            if (inStream is PushBufferStream) {
                var inStreamTransferHandler: BufferTransferHandler?
                val inPushBufferStream = inStream
                inStreamTransferHandler = if (transferHandler == null) null else if (transferHandlerIsSet) {
                    BufferTransferHandler {
                        /*
                         * Do nothing because we don't want the associated PushBufferStream to
                         * cause the transfer of data from this AudioMixerPushBufferStream.
                         */
                    }
                } else {
                    StreamSubstituteBufferTransferHandler(
                            transferHandler, inPushBufferStream, this)
                }
                inPushBufferStream.setTransferHandler(inStreamTransferHandler)
                transferHandlerIsSet = true
            }
        }
    }

    /**
     * Reads audio samples from the input `SourceStream`s of this instance and pushes
     * them to its output `AudioMixingPushBufferStream`s for audio mixing.
     *
     * @param buffer the cached `Buffer` instance to be used during the execution of the method in
     * order to reduce garbage collection. The `length` of the `buffer` will be
     * reset to `0` before and after the execution of the method.
     */
    private fun transferData(buffer: Buffer) {
        try {
            read(buffer)
        } catch (ex: IOException) {
            throw UndeclaredThrowableException(ex)
        }
        val inSampleDesc = buffer.data as InSampleDesc
        val inSamples = inSampleDesc.inSamples as Array<ShortArray?>?
        val maxInSampleCount = buffer.length
        if (inSamples == null || inSamples.isEmpty() || maxInSampleCount <= 0) return
        var outStreams: Array<AudioMixingPushBufferStream>?
        synchronized(this.outStreams) {
            outStreams = unmodifiableOutStreams
            if (outStreams == null) {
                outStreams = this.outStreams.toTypedArray()
                unmodifiableOutStreams = outStreams
            }
        }
        for (outStream in outStreams!!) setInSamples(outStream, inSampleDesc, maxInSampleCount)

        /*
         * The input samples have already been delivered to the output streams and are no longer
         * necessary.
         */
        for (i in inSamples.indices) {
            shortArrayCache.deallocateShortArray(inSamples[i])
            inSamples[i] = null
        }
    }
}