/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.conference

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.MediaStreamImpl
import org.atalk.impl.neomedia.control.ReadOnlyBufferControlDelegate
import org.atalk.impl.neomedia.control.ReadOnlyFormatControlDelegate
import org.atalk.impl.neomedia.device.MediaDeviceImpl
import org.atalk.impl.neomedia.device.ReceiveStreamPushBufferDataSource
import org.atalk.impl.neomedia.protocol.BufferStreamAdapter
import org.atalk.impl.neomedia.protocol.CachingPushBufferStream
import org.atalk.impl.neomedia.protocol.PullBufferStreamAdapter
import org.atalk.impl.neomedia.protocol.PushBufferDataSourceAdapter
import org.atalk.impl.neomedia.protocol.PushBufferStreamAdapter
import org.atalk.impl.neomedia.protocol.TranscodingDataSource
import org.atalk.util.OSUtils
import timber.log.Timber
import java.io.IOException
import java.lang.reflect.UndeclaredThrowableException
import javax.media.Buffer
import javax.media.CaptureDeviceInfo
import javax.media.Controls
import javax.media.Format
import javax.media.Time
import javax.media.control.BufferControl
import javax.media.control.FormatControl
import javax.media.format.AudioFormat
import javax.media.protocol.CaptureDevice
import javax.media.protocol.ContentDescriptor
import javax.media.protocol.DataSource
import javax.media.protocol.PullBufferDataSource
import javax.media.protocol.PullBufferStream
import javax.media.protocol.PullDataSource
import javax.media.protocol.PushBufferStream
import javax.media.protocol.PushDataSource
import javax.media.protocol.SourceStream

/**
 * Represents an audio mixer which manages the mixing of multiple audio streams i.e. it is able to
 * output a single audio stream which contains the audio of multiple input audio streams.
 *
 *
 * The input audio streams are provided to the `AudioMixer` through
 * [.addInDataSource] in the form of input `DataSource` s giving access to
 * one or more input `SourceStreams`.
 *
 *
 *
 * The output audio stream representing the mix of the multiple input audio streams is provided by
 * the `AudioMixer` in the form of a `AudioMixingPushBufferDataSource` giving access
 * to a `AudioMixingPushBufferStream`. Such an output is obtained through
 * [.createOutDataSource]. The `AudioMixer` is able to provide multiple output audio
 * streams at one and the same time, though, each of them containing the mix of a subset of the
 * input audio streams.
 *
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
open class AudioMixer(device: CaptureDevice) {
    /**
     * Gets the `BufferControl` of this instance and, respectively, its
     * `AudioMixingPushBufferDataSource` s.
     *
     * @return the `BufferControl` of this instance and, respectively, its
     * `AudioMixingPushBufferDataSource`s if such a control is available for the
     * `CaptureDevice` of this instance; otherwise, `null`
     */
    /**
     * The `BufferControl` of this instance and, respectively, its
     * `AudioMixingPushBufferDataSource`s.
     */
    var bufferControl: BufferControl? = null
        get() {
            if (field == null && captureDevice is Controls) {
                val captureDeviceBufferControl = (captureDevice as Controls)
                        .getControl(BufferControl::class.java.name) as BufferControl?
                if (captureDeviceBufferControl != null) field = ReadOnlyBufferControlDelegate(captureDeviceBufferControl)
            }
            return field
        }
        private set

    /**
     * The `CaptureDevice` capabilities provided by the
     * `AudioMixingPushBufferDataSource`s created by this `AudioMixer` . JMF's
     * `Manager.createMergingDataSource(DataSource[])` requires the interface implementation
     * for audio if it is implemented for video and it is indeed the case for our use case of
     * `AudioMixingPushBufferDataSource`.
     */
    val captureDevice: CaptureDevice

    /**
     * The number of output `AudioMixingPushBufferDataSource`s reading from this
     * `AudioMixer` which are connected. When the value is greater than zero, this
     * `AudioMixer` is connected to the input `DataSource`s it manages.
     */
    private var connected = 0

    /**
     * The collection of input `DataSource`s this instance reads audio data from.
     */
    private val inDataSources = ArrayList<InDataSourceDesc>()
    /**
     * Gets the `AudioMixingPushBufferDataSource` containing the mix of all input
     * `DataSource`s excluding the `CaptureDevice` of this `AudioMixer` and is
     * thus meant for playback on the local peer in a call.
     *
     * @return the `AudioMixingPushBufferDataSource` containing the mix of all input
     * `DataSource`s excluding the `CaptureDevice` of this `AudioMixer`
     * and is thus meant for playback on the local peer in a call
     */
    /**
     * The `AudioMixingPushBufferDataSource` which contains the mix of
     * `inDataSources` excluding `captureDevice` and is thus meant for playback on
     * the local peer in a call.
     */
    val localOutDataSource: AudioMixingPushBufferDataSource

    /**
     * The output `AudioMixerPushBufferStream` through which this instance pushes audio
     * sample data to `AudioMixingPushBufferStream` s to be mixed.
     */
    var outStream: AudioMixerPushBufferStream? = null
        /**
         * Gets the `AudioMixerPushBufferStream`, first creating it if it does not exist
         * already, which reads data from the input `DataSource`s of this `AudioMixer`
         * and pushes it to output `AudioMixingPushBufferStream`s for audio mixing.
         */
        get() {
            synchronized(inDataSources) {
                val outFormat = if (field == null) outFormatFromInDataSources else field!!.outFormat

                // force to preferred output format
                // outFormat = PREFFERED_OUTPUT_FORMAT;
                setOutFormatToInDataSources(outFormat)
                val inStreams: Collection<InStreamDesc?>
                try {
                    getInStreamsFromInDataSources(outFormat!!, if (field == null) null else field!!.getInStreams())
                } catch (ioex: IOException) {
                    throw UndeclaredThrowableException(ioex)
                }.also { inStreams = it }

                if (field == null) {
                    field = AudioMixerPushBufferStream(this, outFormat)
                    startedGeneration = 0
                }

                field!!.setInStreams(inStreams as Collection<InStreamDesc>)
                return field
            }
        }

    /**
     * The number of output `AudioMixingPushBufferDataSource`s reading from this
     * `AudioMixer` which are started. When the value is greater than zero, this
     * `AudioMixer` is started and so are the input `DataSource`s it manages.
     */
    private var started = 0

    /**
     * The greatest generation with which [.start] or
     * [.stop] has been invoked.
     */
    private var startedGeneration = 0L

    /**
     * Initializes a new `AudioMixer` instance. Because JMF's
     * `Manager.createMergingDataSource(DataSource[])` requires the implementation of
     * `CaptureDevice` for audio if it is implemented for video and it is indeed the cause
     * for our use case of `AudioMixingPushBufferDataSource`, the new `AudioMixer`
     * instance provides specified `CaptureDevice` capabilities to the
     * `AudioMixingPushBufferDataSource`s it creates. The specified `CaptureDevice` is
     * also added as the first input `DataSource` of the new instance.
     *
     * captureDevice the `CaptureDevice` capabilities to be provided to the
     * `AudioMixingPushBufferDataSource`s created by the new instance and its first
     * input `DataSource`
     */
    init {
        /*
         * AudioMixer provides PushBufferDataSources so it needs a way to push them. It does the
         * pushing by using the pushes of its CaptureDevice i.e. it has to be a
         * PushBufferDataSource.
         */
        var captureDevice = device
        if (captureDevice is PullBufferDataSource) {
            captureDevice = PushBufferDataSourceAdapter(captureDevice as PullBufferDataSource)
        }

        // Try to enable tracing on captureDevice.
        if (TimberLog.isTraceEnable) {
            captureDevice = MediaDeviceImpl.createTracingCaptureDevice(captureDevice)!!
        }
        this.captureDevice = captureDevice
        localOutDataSource = createOutDataSource()
        addInDataSource(this.captureDevice as DataSource, localOutDataSource)
    }

    /**
     * Adds a new input `DataSource` to the collection of input `DataSource`s from
     * which this instance reads audio. If the specified `DataSource` indeed provides audio,
     * the respective contributions to the mix are always included.
     *
     * @param inDataSource a new `DataSource` to input audio to this instance
     */
    fun addInDataSource(inDataSource: DataSource?) {
        addInDataSource(inDataSource, null)
    }

    /**
     * Adds a new input `DataSource` to the collection of input `DataSource`s from
     * which this instance reads audio. If the specified `DataSource` indeed provides audio,
     * the respective contributions to the mix will be excluded from the mix output provided
     * through a specific `AudioMixingPushBufferDataSource`.
     *
     * @param inDataSource a new `DataSource` to input audio to this instance
     * @param outDataSource the `AudioMixingPushBufferDataSource` to not include the audio contributions of
     * `inDataSource` in the mix it outputs
     */
    fun addInDataSource(inDataSource: DataSource?, outDataSource: AudioMixingPushBufferDataSource?) {
        if (inDataSource == null) throw NullPointerException("inDataSource")

        synchronized(inDataSources) {
            for (inDataSourceDesc in inDataSources) require(inDataSource != inDataSourceDesc.inDataSource) { "inDataSource" }
            val inDataSourceDesc = InDataSourceDesc(inDataSource, outDataSource)
            val added = inDataSources.add(inDataSourceDesc)
            if (added) {
                Timber.log(TimberLog.FINER, "Added input DataSource with hashCode %s", inDataSource.hashCode())

                /*
                 * If the other inDataSources have already been connected, connect to the new
                 * one as well.
                 */
                if (connected > 0) {
                    try {
                        inDataSourceDesc.connect(this)
                    } catch (ioex: IOException) {
                        throw UndeclaredThrowableException(ioex)
                    }
                }

                // Update outStream with any new inStreams.
                if (outStream != null) outStream

                /*
                 * If the other inDataSources have been started, start the new one as well.
                 */
                if (started > 0) {
                    try {
                        inDataSourceDesc.start()
                    } catch (ioe: IOException) {
                        throw UndeclaredThrowableException(ioe)
                    }
                }
            }
        }
    }

    /**
     * Notifies this `AudioMixer` that an output `AudioMixingPushBufferDataSource`
     * reading from it has been connected. The first of the many
     * `AudioMixingPushBufferDataSource` s reading from this `AudioMixer` which gets
     * connected causes it to connect to the input `DataSource`s it manages.
     *
     * @throws IOException if input/output error occurred
     */
    @Throws(IOException::class)
    fun connect() {
        synchronized(inDataSources) {
            if (connected == 0) {
                for (inDataSourceDesc in inDataSources) try {
                    inDataSourceDesc.connect(this)
                } catch (ioe: IOException) {
                    Timber.e(ioe, "Failed to connect to inDataSource %s",
                            MediaStreamImpl.toString(inDataSourceDesc.inDataSource))
                    throw ioe
                }

                /*
                 * Since the media of the input streams is to be mixed, their bufferLengths have to
                 * be equal. After a DataSource is connected, its BufferControl is available and
                 * its * bufferLength may change so make sure that the bufferLengths of the input
                 * streams are equal.
                 */
                if (outStream != null)
                    outStream!!.equalizeInStreamBufferLength()
            }
            connected++
        }
    }

    /**
     * Connects to a specific `DataSource` which this `AudioMixer`
     * will read audio from. The specified `DataSource` is known to exist because of a
     * specific `DataSource` added as an input to this instance i.e. it may be an actual
     * input `DataSource` added to this instance or a `DataSource` transcoding an
     * input `DataSource` added to this instance.
     *
     * @param dataSource the `DataSource` to connect to
     * @param inDataSource the `DataSource` which is the cause for `dataSource` to exist in this
     * `AudioMixer`
     * @throws IOException if anything wrong happens while connecting to `dataSource`
    `` */
    @Throws(IOException::class)
    open fun connect(dataSource: DataSource, inDataSource: DataSource) {
        dataSource.connect()
    }

    /**
     * Notifies this `AudioMixer` that a specific input `DataSource` has finished its
     * connecting procedure. Primarily meant for input `DataSource` which have their
     * connecting executed in a separate thread as are, for example, input `DataSource`s
     * which are being transcoded.
     *
     * @param inDataSource the `InDataSourceDesc` of the input `DataSource` which has finished its
     * connecting procedure
     * @throws IOException if anything wrong happens while including `inDataSource` into the mix
     */
    @Throws(IOException::class)
    fun connected(inDataSource: InDataSourceDesc) {
        synchronized(inDataSources) {
            if (inDataSources.contains(inDataSource) && connected > 0) {
                if (started > 0) inDataSource.start()
                if (outStream != null) outStream
            }
        }
    }

    /**
     * Creates a new `InStreamDesc` instance which is to describe a specific input
     * `SourceStream` originating from a specific input `DataSource` given by its
     * `InDataSourceDesc`.
     *
     * @param inStream the input `SourceStream` to be described by the new instance
     * @param inDataSourceDesc the input `DataSource` given by its `InDataSourceDesc` to be described
     * by the new instance
     * @return a new `InStreamDesc` instance which describes the specified input
     * `SourceStream` and `DataSource`
     */
    private fun createInStreamDesc(inStream: SourceStream, inDataSourceDesc: InDataSourceDesc): InStreamDesc {
        return InStreamDesc(inStream, inDataSourceDesc)
    }

    /**
     * Creates a new `AudioMixingPushBufferDataSource` which gives access to a single audio
     * stream representing the mix of the audio streams input into this `AudioMixer` through
     * its input `DataSource`s. The returned `AudioMixingPushBufferDataSource` can
     * also be used to include new input `DataSources` in this `AudioMixer` but have
     * their contributions not included in the mix available through the returned
     * `AudioMixingPushBufferDataSource`.
     *
     * @return a new `AudioMixingPushBufferDataSource` which gives access to a single audio
     * stream representing the mix of the audio streams input into this `AudioMixer`
     * through its input `DataSource`s
     */
    fun createOutDataSource(): AudioMixingPushBufferDataSource {
        return AudioMixingPushBufferDataSource(this)
    }

    /**
     * Creates a `DataSource` which attempts to transcode the tracks of a specific input
     * `DataSource` into a specific output `Format` .
     *
     * @param inDataSourceDesc the `InDataSourceDesc` describing the input `DataSource` to be
     * transcoded into the specified output `Format` and to receive the transcoding `DataSource`
     * @param outFormat the `Format` in which the tracks of the input `DataSource` are to be transcoded
     * @return `true` if a new transcoding `DataSource` has been created for the
     * input `DataSource` described by `inDataSourceDesc`; otherwise, `false`
     * @throws IOException if an error occurs while creating the transcoding `DataSource`, connecting to
     * it or staring it
     */
    @Throws(IOException::class)
    private fun createTranscodingDataSource(inDataSourceDesc: InDataSourceDesc, outFormat: Format): Boolean {
        return if (inDataSourceDesc.createTranscodingDataSource(outFormat)) {
            if (connected > 0) inDataSourceDesc.connect(this)
            if (started > 0) inDataSourceDesc.start()
            true
        } else false
    }

    /**
     * Notifies this `AudioMixer` that an output `AudioMixingPushBufferDataSource`
     * reading from it has been disconnected. The last of the many
     * `AudioMixingPushBufferDataSource`s reading from this `AudioMixer` which gets
     * disconnected causes it to disconnect from the input `DataSource`s it manages.
     */
    fun disconnect() {
        synchronized(inDataSources) {
            if (connected <= 0) return
            connected--
            if (connected == 0) {
                for (inDataSourceDesc in inDataSources) inDataSourceDesc.disconnect()

                /*
                 * XXX Make the outStream to release the inStreams. Otherwise, the PushBufferStream
                 * ones which have been wrapped into CachingPushBufferStream may remain waiting.
                 */
                // cmeng - outStream may be null if earlier setup failed
                if (outStream != null) {
                    outStream!!.setInStreams(null)
                    outStream = null
                }
                startedGeneration = 0
            }
        }
    }

    /**
     * Gets the `CaptureDeviceInfo` of the `CaptureDevice` this `AudioMixer`
     * provides through its output `AudioMixingPushBufferDataSource`s.
     *
     * @return the `CaptureDeviceInfo` of the `CaptureDevice` this
     * `AudioMixer` provides through its output `AudioMixingPushBufferDataSource`s
     */
    val captureDeviceInfo: CaptureDeviceInfo
        get() = captureDevice.captureDeviceInfo

    /**
     * Gets the content type of the data output by this `AudioMixer`.
     *
     * @return the content type of the data output by this `AudioMixer`
     */
    val contentType: String
        get() = ContentDescriptor.RAW

    /**
     * Gets the duration of each one of the output streams produced by this `AudioMixer`.
     *
     * @return the duration of each one of the output streams produced by this `AudioMixer`
     */
    val duration: Time
        get() = (captureDevice as DataSource).duration

    /**
     * Gets an `InStreamDesc` from a specific existing list of `InStreamDesc`s which
     * describes a specific `SourceStream`. If such an `InStreamDesc` does not exist,
     * returns `null`.
     *
     * @param inStream the `SourceStream` to locate an `InStreamDesc` for in
     * `existingInStreamDescs`
     * @param existingInStreamDescs the list of existing `InStreamDesc`s in which an `InStreamDesc` for
     * `inStream` is to be located
     * @return an `InStreamDesc` from `existingInStreamDescs` which describes
     * `inStream` if such an `InStreamDesc` exists; otherwise, `null`
     */
    private fun getExistingInStreamDesc(inStream: SourceStream, existingInStreamDescs: Array<InStreamDesc>?): InStreamDesc? {
        if (existingInStreamDescs == null) return null
        for (existingInStreamDesc in existingInStreamDescs) {
            val existingInStream = existingInStreamDesc.inStream
            if (existingInStream === inStream) return existingInStreamDesc
            if (existingInStream is BufferStreamAdapter<*> && (existingInStream as BufferStreamAdapter<*>?)!!.stream === inStream) return existingInStreamDesc
            if (existingInStream is CachingPushBufferStream && (existingInStream as CachingPushBufferStream?)!!.stream === inStream) return existingInStreamDesc
        }
        return null
    }/*
         * Setting the format of the captureDevice once we've started using it is likely to wreak
         * havoc so disable it.
         */

    /**
     * Gets an array of `FormatControl`s for the `CaptureDevice` this
     * `AudioMixer` provides through its output `AudioMixingPushBufferDataSource`s.
     *
     * @return an array of `FormatControl`s for the `CaptureDevice` this
     * `AudioMixer` provides through its output
     * `AudioMixingPushBufferDataSource`s
     */
    val formatControls: Array<out FormatControl>
        get() {
            /*
         * Setting the format of the captureDevice once we've started using it is likely to wreak
         * havoc so disable it.
         */
            val formatControls = captureDevice.formatControls
            if (!OSUtils.IS_ANDROID && formatControls != null) {
                for (i in formatControls.indices) {
                    formatControls[i] = ReadOnlyFormatControlDelegate(formatControls[i])
                }
            }
            return formatControls
        }

    /**
     * Gets the `SourceStream`s (in the form of `InStreamDesc`) of a specific
     * `DataSource` (provided in the form of `InDataSourceDesc`) which produce
     * data in a specific `AudioFormat` (or a matching one).
     *
     * @param inDataSourceDesc the `DataSource` (in the form of `InDataSourceDesc`) which is to be
     * examined for `SourceStreams` producing data in the specified
     * `AudioFormat`
     * @param outFormat the `AudioFormat` in which the collected `SourceStream`s are to produce
     * data
     * @param existingInStreams the `InStreamDesc` instances which already exist and which are used to avoid
     * creating multiple `InStreamDesc`s for input `SourceStream`s which
     * already have ones
     * @param inStreams the `List` of `InStreamDesc` in which the discovered
     * `SourceStream`s are to be returned
     * @return `true` if `SourceStream`s produced by the specified input
     * `DataSource` and outputting data in the specified `AudioFormat` were
     * discovered and reported in `inStreams`; otherwise, `false`
     */
    private fun getInStreamsFromInDataSource(
            inDataSourceDesc: InDataSourceDesc,
            outFormat: AudioFormat?, existingInStreams: Array<InStreamDesc>?, inStreams: MutableList<InStreamDesc?>,
    ): Boolean {
        val inDataSourceStreams = inDataSourceDesc.streams
        if (inDataSourceStreams != null) {
            var added = false
            for (inStream in inDataSourceStreams) {
                val inFormat = getFormat(inStream!!)!!
                if (matches(inFormat, outFormat)) {
                    var inStreamDesc = getExistingInStreamDesc(inStream, existingInStreams)
                    if (inStreamDesc == null) inStreamDesc = createInStreamDesc(inStream, inDataSourceDesc)
                    if (inStreams.add(inStreamDesc)) added = true
                }
            }
            return added
        }
        val inDataSource = inDataSourceDesc.effectiveInDataSource ?: return false
        val inFormat = getFormat(inDataSource)
        if (inFormat != null && !matches(inFormat, outFormat)) {
            if (inDataSource is PushDataSource) {
                for (inStream in inDataSource.streams) {
                    var inStreamDesc = getExistingInStreamDesc(inStream, existingInStreams)
                    if (inStreamDesc == null) inStreamDesc = createInStreamDesc(PushBufferStreamAdapter(inStream,
                            inFormat), inDataSourceDesc)
                    inStreams.add(inStreamDesc)
                }
                return true
            }
            if (inDataSource is PullDataSource) {
                for (inStream in inDataSource.streams) {
                    var inStreamDesc = getExistingInStreamDesc(inStream, existingInStreams)
                    if (inStreamDesc == null) inStreamDesc = createInStreamDesc(PullBufferStreamAdapter(inStream,
                            inFormat), inDataSourceDesc)
                    inStreams.add(inStreamDesc)
                }
                return true
            }
        }
        return false
    }

    /**
     * Gets the `SourceStream`s (in the form of `InStreamDesc`) of the
     * `DataSource`s from which this `AudioMixer` reads data which produce data in a
     * specific `AudioFormat`. When an input `DataSource` does not have such
     * `SourceStream`s, an attempt is made to transcode its tracks so that such
     * `SourceStream`s can be retrieved from it after transcoding.
     *
     * @param outFormat the `AudioFormat` in which the retrieved `SourceStream`s are to produce
     * data
     * @param existingInStreams the `SourceStream`s which are already known to this `AudioMixer`
     * @return a new collection of `SourceStream`s (in the form of `InStreamDesc`)
     * retrieved from the input `DataSource`s of this `AudioMixer` and
     * producing data in the specified `AudioFormat`
     * @throws IOException if anything wrong goes while retrieving the input `SourceStream`s from the
     * input `DataSource`s
     */
    @Throws(IOException::class)
    private fun getInStreamsFromInDataSources(outFormat: AudioFormat, existingInStreams: Array<InStreamDesc>?,
    ): Collection<InStreamDesc?> {
        val inStreams = ArrayList<InStreamDesc?>()
        synchronized(inDataSources) {
            for (inDataSourceDesc in inDataSources) {
                val got = getInStreamsFromInDataSource(inDataSourceDesc, outFormat, existingInStreams, inStreams)
                if (!got && createTranscodingDataSource(inDataSourceDesc, outFormat))
                    getInStreamsFromInDataSource(inDataSourceDesc, outFormat, existingInStreams, inStreams)
            }
        }
        return inStreams
    }
    // LITTLE_ENDIAN// SIGNED

    /**
     * Gets the `AudioFormat` in which the input `DataSource`s of this
     * `AudioMixer` can produce data and which is to be the output `Format` of this
     * `AudioMixer`.
     *
     * @return the `AudioFormat` in which the input `DataSource`s of this
     * `AudioMixer` can produce data and which is to be the output `Format` of
     * this `AudioMixer`
     */
    private val outFormatFromInDataSources: AudioFormat?
        get() {
            val formatControlType = FormatControl::class.java.name
            var outFormat: AudioFormat? = null

            synchronized(inDataSources) {
                for (inDataSource in inDataSources) {
                    val effectiveInDataSource = inDataSource.effectiveInDataSource ?: continue

                    val formatControl = effectiveInDataSource.getControl(formatControlType) as FormatControl?
                    if (formatControl != null) {
                        val format = formatControl.format as AudioFormat?
                        if (format != null) {
                            // SIGNED
                            val signed = format.signed
                            if (AudioFormat.SIGNED == signed || Format.NOT_SPECIFIED == signed) {
                                // LITTLE_ENDIAN
                                val endian = format.endian
                                if (AudioFormat.LITTLE_ENDIAN == endian || Format.NOT_SPECIFIED == endian) {
                                    outFormat = format
                                    break
                                }
                            }
                        }
                    }
                }
            }
            if (outFormat == null) outFormat = DEFAULT_OUTPUT_FORMAT
            Timber.log(TimberLog.FINER, "Determined outFormat of AudioMixer from inDataSources to be %s", outFormat)
            return outFormat
        }

    /**
     * Searches this object's `inDataSource`s for one that matches `inDataSource`,
     * and returns it's associated `TranscodingDataSource`. Currently this is only used
     * when the `MediaStream` needs access to the codec chain used to playback one of it's
     * `ReceiveStream`s.
     *
     * @param inDataSource the `DataSource` to search for.
     * @return The `TranscodingDataSource` associated with `inDataSource`, if we can
     * find one, `null` otherwise.
     */
    fun getTranscodingDataSource(inDataSource: DataSource): TranscodingDataSource? {
        for (inDataSourceDesc in inDataSources) {
            when (val ourDataSource = inDataSourceDesc.inDataSource) {
                inDataSource -> return inDataSourceDesc.transcodingDataSource
                is ReceiveStreamPushBufferDataSource -> {
                    // Sometimes the inDataSource has come to AudioMixer wrapped in
                    // a ReceiveStreamPushBufferDataSource. We consider it to match.
                    if (ourDataSource.dataSource == inDataSource) return inDataSourceDesc.transcodingDataSource
                }
            }
        }
        return null
    }

    /**
     * Determines whether a specific `Format` matches a specific `Format` in the
     * sense of JMF `Format` matching. Since this `AudioMixer` and the audio mixing
     * functionality related to it can handle varying characteristics of a certain output
     * `Format`, the only requirement for the specified `Format`s to match is for
     * both of them to have one and the same encoding.
     *
     * @param input the `Format` for which it is required to determine whether it matches a
     * specific `Format`
     * @param pattern the `Format` against which the specified `input` is to be matched
     * @return `true` if the specified `input` matches the specified
     * `pattern` in the sense of JMF `Format` matching; otherwise, `false`
    `` */
    private fun matches(input: Format, pattern: AudioFormat?): Boolean {
        return input is AudioFormat && input.isSameEncoding(pattern)
    }

    /**
     * Reads media from a specific `PushBufferStream` which belongs to a specific
     * `DataSource` into a specific output `Buffer`. Allows extenders to tap into the
     * reading and monitor and customize it.
     *
     * @param stream the `PushBufferStream` to read media from and known to belong to the specified
     * `DataSOurce`
     * @param buffer the output `Buffer` in which the media read from the specified `stream`
     * is to be written so that it gets returned to the caller
     * @param dataSource the `DataSource` from which `stream` originated
     * @throws IOException if anything wrong happens while reading from the specified `stream`
     */
    @Throws(IOException::class)
    open fun read(stream: PushBufferStream, buffer: Buffer, dataSource: DataSource) {
        stream.read(buffer)
    }

    /**
     * Removes `DataSource`s accepted by a specific `DataSourceFilter` from the list
     * of input `DataSource`s of this `AudioMixer` from which it reads audio to be
     * mixed.
     *
     * @param dataSourceFilter the `DataSourceFilter` which selects the `DataSource`s to be removed
     * from the list of input `DataSource`s of this `AudioMixer` from which it
     * reads audio to be mixed
     */
    fun removeInDataSources(dataSourceFilter: DataSourceFilter) {
        synchronized(inDataSources) {
            val inDataSourceIter = inDataSources.iterator()
            var removed = false
            while (inDataSourceIter.hasNext()) {
                val inDsDesc = inDataSourceIter.next()
                if (dataSourceFilter.accept(inDsDesc.inDataSource)) {
                    inDataSourceIter.remove()
                    removed = true
                    try {
                        inDsDesc.stop()
                        inDsDesc.disconnect()
                    } catch (ex: IOException) {
                        Timber.e(ex, "Failed to stop DataSource")
                    }
                }
            }
            if (removed && outStream != null) outStream
        }
    }

    /**
     * Sets a specific `AudioFormat`, if possible, as the output format of the input
     * `DataSource`s of this `AudioMixer` in an attempt to not have to perform
     * explicit transcoding of the input `SourceStream` s.
     *
     * @param outFormat the `AudioFormat` in which the input `DataSource`s of this
     * `AudioMixer` are to be instructed to output
     */
    private fun setOutFormatToInDataSources(outFormat: AudioFormat?) {
        val formatControlType = FormatControl::class.java.name
        synchronized(inDataSources) {
            for (inDataSourceDesc in inDataSources) {
                val formatControl = inDataSourceDesc.getControl(formatControlType) as FormatControl?
                if (formatControl != null) {
                    val inFormat = formatControl.format
                    if (inFormat == null || !matches(inFormat, outFormat)) {
                        val setFormat = formatControl.setFormat(outFormat)
                        when {
                            setFormat == null -> Timber.e("Failed to set format of inDataSource to %s", outFormat)
                            setFormat != outFormat -> Timber.w("Failed to change format of inDataSource from %s to %s", setFormat, outFormat)
                            else -> Timber.log(TimberLog.FINER, "Set format of inDataSource to %s", setFormat)
                        }
                    }
                }
            }
        }
    }

    /**
     * Starts the input `DataSource`s of this `AudioMixer`.
     *
     * @param outStream the `AudioMixerPushBufferStream` which requests this `AudioMixer` to
     * start. If `outStream` is the current one and only
     * `AudioMixerPushBufferStream` of this `AudioMixer`, this
     * `AudioMixer` starts if it hasn't started yet. Otherwise, the request is ignored.
     * @param generation a value generated by `outStream` indicating the order of the invocations of the
     * `start` and `stop` methods performed by `outStream` allowing it
     * to execute the said methods outside synchronized blocks for the purposes of reducing
     * deadlock risks
     * @throws IOException if any of the input `DataSource`s of this `AudioMixer` throws such an
     * exception while attempting to start it
     */
    @Throws(IOException::class)
    fun start(outStream: AudioMixerPushBufferStream, generation: Long) {
        synchronized(inDataSources) {

            /*
             * AudioMixer has only one outStream at a time and only its current outStream knows
             * when it has to start (and stop).
             */
            if (this.outStream != outStream) return

            /*
             * The notion of generations was introduced in order to allow outStream to invoke the
             * start and stop methods outside synchronized blocks. The generation value always
             * increases in a synchronized block.
             */
            startedGeneration = if (startedGeneration < generation) generation else return
            if (started == 0) {
                for (inDataSourceDesc in inDataSources) inDataSourceDesc.start()
            }
            started++
        }
    }

    /**
     * Stops the input `DataSource`s of this `AudioMixer`.
     *
     * @param outStream the `AudioMixerPushBufferStream` which requests this `AudioMixer` to
     * stop. If `outStream` is the current one and only
     * `AudioMixerPushBufferStream` of this `AudioMixer`, this
     * `AudioMixer` stops. Otherwise, the request is ignored.
     * @param generation a value generated by `outStream` indicating the order of the invocations of the
     * `start` and `stop` methods performed by `outStream` allowing it
     * to execute the said methods outside synchronized blocks for the purposes of reducing
     * deadlock risks
     * @throws IOException if any of the input `DataSource`s of this `AudioMixer` throws such an
     * exception while attempting to stop it
     */
    @Throws(IOException::class)
    fun stop(outStream: AudioMixerPushBufferStream, generation: Long) {
        synchronized(inDataSources) {

            /*
             * AudioMixer has only one outStream at a time and only its current outStream knows
             * when it has to stop (and start).
             */
            if (this.outStream != outStream) return

            /*
             * The notion of generations was introduced in order to allow outStream to invoke the
             * start and stop methods outside synchronized blocks. The generation value always
             * increases in a synchronized block.
             */
            startedGeneration = if (startedGeneration < generation) generation else return
            if (started <= 0) return
            started--
            if (started == 0) {
                for (inDataSourceDesc in inDataSources) inDataSourceDesc.stop()
            }
        }
    }

    companion object {
        /**
         * The default output `AudioFormat` in which `AudioMixer`,
         * `AudioMixingPushBufferDataSource` and `AudioMixingPushBufferStream` output
         * audio.
         */
        private val DEFAULT_OUTPUT_FORMAT = AudioFormat(
                AudioFormat.LINEAR,
                8000.0,
                16,
                1,
                AudioFormat.LITTLE_ENDIAN,
                AudioFormat.SIGNED)

        // cmeng - added
        private val PREFFERED_OUTPUT_FORMAT = AudioFormat(
                AudioFormat.LINEAR,
                22050.0,
                16,
                1,
                AudioFormat.LITTLE_ENDIAN,
                AudioFormat.SIGNED)

        /**
         * Gets the `Format` in which a specific `DataSource` provides stream data.
         *
         * @param dataSource the `DataSource` for which the `Format` in which it provides stream data
         * is to be determined
         * @return the `Format` in which the specified `dataSource` provides stream data
         * if it was determined; otherwise, `null`
         */
        private fun getFormat(dataSource: DataSource): Format? {
            return (dataSource.getControl(FormatControl::class.java.name) as FormatControl?)?.format
        }

        /**
         * Gets the `Format` in which a specific `SourceStream` provides data.
         *
         * @param stream the `SourceStream` for which the `Format` in which it provides data is
         * to be determined
         * @return the `Format` in which the specified `SourceStream` provides data if it
         * was determined; otherwise, `null`
         */
        private fun getFormat(stream: SourceStream): Format? {
            if (stream is PushBufferStream) return stream.format
            return if (stream is PullBufferStream) stream.format else null
        }
    }
}