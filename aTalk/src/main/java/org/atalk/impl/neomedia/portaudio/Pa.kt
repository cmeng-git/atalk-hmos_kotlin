/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.portaudio

import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.util.OSUtils
import org.atalk.util.StringUtils.newString
import timber.log.Timber
import java.lang.reflect.UndeclaredThrowableException

/**
 * Provides the interface to the native PortAudio library.
 *
 * @author Lyubomir Marinov
 * @author Damian Minkov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
object Pa {
    /**
     * The number of milliseconds to be read from or written to a native PortAudio stream in a
     * single transfer of data.
     */
    const val DEFAULT_MILLIS_PER_BUFFER = 20L

    /**
     * The default value for the sample rate of the input and the output PortAudio streams with
     * which they are to be opened if no other specific sample rate is specified to the PortAudio
     * `DataSource` or `PortAudioRenderer` that they represent.
     */
    const val DEFAULT_SAMPLE_RATE = 44100.0
    private var devicesChangedCallback: Runnable? = null

    /**
     * Can be passed as the framesPerBuffer parameter to `Pa_OpenStream()` or
     * `Pa_OpenDefaultStream()` to indicate that the stream callback will accept buffers of
     * any size.
     */
    const val FRAMES_PER_BUFFER_UNSPECIFIED = 0L

    /**
     * Used when creating new stream parameters for suggested latency to use high input/output
     * value.
     */
    const val LATENCY_HIGH = -1.0

    /**
     * Used when creating new stream parameters for suggested latency to use low input/default
     * value.
     */
    const val LATENCY_LOW = -2.0

    /**
     * Used when creating new stream parameters for suggested latency to use default value.
     */
    const val LATENCY_UNSPECIFIED = 0.0

    /**
     * The constant defined by Windows Multimedia and utilized by PortAudio's wmme host API to
     * signal that no device driver is present.
     */
    const val MMSYSERR_NODRIVER = 6L

    /**
     * The constant defined by the native PortAudio library to signal that no device is specified.
     */
    const val paNoDevice = -1

    /**
     * The `PaErrorCode` value defined by the native PortAudio library to signal that no
     * error is detected/reported.
     */
    const val paNoError = 0

    /**
     * The `PaErrorCode` value defined by the native PortAudio library to signal that a
     * timeout has occurred.
     */
    const val paTimedOut = -9987L

    /**
     * The `PaErrorCode` value defined by the native PortAudio library to signal that an
     * unanticipated error has been detected by a host API.
     */
    const val paUnanticipatedHostError = -9999

    /**
     * The name of the `double` property which determines the suggested latency to be used
     * when opening PortAudio streams.
     */
    private const val PROP_SUGGESTED_LATENCY = "neomedia.portaudio.suggestedLatency"

    /**
     * A type used to specify one or more sample formats. The standard format `paFloat32`.
     */
    const val SAMPLE_FORMAT_FLOAT32 = 0x00000001L

    /**
     * A type used to specify one or more sample formats. The standard format `paInt16`.
     */
    const val SAMPLE_FORMAT_INT16 = 0x00000008L

    /**
     * A type used to specify one or more sample formats. The standard format `paInt24`.
     */
    const val SAMPLE_FORMAT_INT24 = 0x00000004L

    /**
     * A type used to specify one or more sample formats. The standard format `paInt32`.
     */
    const val SAMPLE_FORMAT_INT32 = 0x00000002L

    /**
     * A type used to specify one or more sample formats. The standard format `paInt8`.
     */
    const val SAMPLE_FORMAT_INT8 = 0x00000010L

    /**
     * A type used to specify one or more sample formats. The standard format `paUInt8`.
     */
    const val SAMPLE_FORMAT_UINT8 = 0x00000020L

    /**
     * Disables default clipping of out of range samples.
     */
    const val STREAM_FLAGS_CLIP_OFF = 0x00000001L

    /**
     * Disables default dithering.
     */
    const val STREAM_FLAGS_DITHER_OFF = 0x00000002L

    /**
     * Flag requests that where possible a full duplex stream will not discard overflowed input
     * samples without calling the stream callback. This flag is only valid for full duplex
     * callback streams and only when used in combination with the
     * `paFramesPerBufferUnspecified` (`0`) framesPerBuffer parameter. Using this
     * flag incorrectly results in a `paInvalidFlag` error being returned from
     * `Pa_OpenStream` and `Pa_OpenDefaultStream`.
     */
    const val STREAM_FLAGS_NEVER_DROP_INPUT = 0x00000004L

    /**
     * Flags used to control the behavior of a stream. They are passed as parameters to
     * `Pa_OpenStream` or `Pa_OpenDefaultStream`.
     */
    const val STREAM_FLAGS_NO_FLAG = 0L

    /**
     * A mask specifying the platform specific bits.
     */
    const val STREAM_FLAGS_PLATFORM_SPECIFIC_FLAGS = -0x10000L

    /**
     * Call the stream callback to fill initial output buffers, rather than the default behavior of
     * priming the buffers with zeros (silence). This flag has no effect for input-only and
     * blocking read/write streams.
     */
    const val STREAM_FLAGS_PRIME_OUTPUT_BUFFERS_USING_STREAM_CALLBACK = 0x00000008L

    init {
        System.loadLibrary("jnportaudio")
        try {
            Initialize()
        } catch (paex: PortAudioException) {
            Timber.e(paex, "Failed to initialize the PortAudio library.")
            throw UndeclaredThrowableException(paex)
        }
    }

    /**
     * Terminates audio processing immediately without waiting for pending buffers to complete.
     *
     * @param stream the steam pointer.
     * @throws PortAudioException
     */
    @Throws(PortAudioException::class)
    external fun AbortStream(stream: Long)

    /**
     * Closes an audio stream. If the audio stream is active it discards any pending buffers as if
     * `Pa_AbortStream()` had been called.
     *
     * @param stream the steam pointer.
     * @throws PortAudioException
     */
    @Throws(PortAudioException::class)
    external fun CloseStream(stream: Long)

    /**
     * Returns defaultHighInputLatency for the device.
     *
     * @param deviceInfo device info pointer.
     * @return defaultHighInputLatency for the device.
     */
    external fun DeviceInfo_getDefaultHighInputLatency(deviceInfo: Long): Double

    /**
     * Returns defaultHighOutputLatency for the device.
     *
     * @param deviceInfo device info pointer.
     * @return defaultHighOutputLatency for the device.
     */
    external fun DeviceInfo_getDefaultHighOutputLatency(deviceInfo: Long): Double

    /**
     * Returns defaultLowInputLatency for the device.
     *
     * @param deviceInfo device info pointer.
     * @return defaultLowInputLatency for the device.
     */
    external fun DeviceInfo_getDefaultLowInputLatency(deviceInfo: Long): Double

    /**
     * Returns defaultLowOutputLatency for the device.
     *
     * @param deviceInfo device info pointer.
     * @return defaultLowOutputLatency for the device.
     */
    external fun DeviceInfo_getDefaultLowOutputLatency(deviceInfo: Long): Double

    /**
     * The default sample rate for the device.
     *
     * @param deviceInfo device info pointer.
     * @return the default sample rate for the device.
     */
    external fun DeviceInfo_getDefaultSampleRate(deviceInfo: Long): Double

    /**
     * Device UID for the device (persistent across boots).
     *
     * @param deviceInfo device info pointer.
     * @return The device UID.
     */
    fun DeviceInfo_getDeviceUID(deviceInfo: Long): String? {
        return newString(DeviceInfo_getDeviceUIDBytes(deviceInfo))
    }

    /**
     * Device UID for the device (persistent across boots).
     *
     * @param deviceInfo device info pointer.
     * @return The device UID.
     */
    external fun DeviceInfo_getDeviceUIDBytes(deviceInfo: Long): ByteArray?

    /**
     * The host api of the device.
     *
     * @param deviceInfo device info pointer.
     * @return The host api of the device.
     */
    external fun DeviceInfo_getHostApi(deviceInfo: Long): Int

    /**
     * Maximum input channels for the device.
     *
     * @param deviceInfo device info pointer.
     * @return Maximum input channels for the device.
     */
    external fun DeviceInfo_getMaxInputChannels(deviceInfo: Long): Int

    /**
     * Maximum output channels for the device.
     *
     * @param deviceInfo device info pointer.
     * @return Maximum output channels for the device.
     */
    external fun DeviceInfo_getMaxOutputChannels(deviceInfo: Long): Int

    /**
     * Gets the human-readable name of the `PaDeviceInfo` specified by a pointer to it.
     *
     * @param deviceInfo the pointer to the `PaDeviceInfo` to get the human-readable name of
     * @return the human-readable name of the `PaDeviceInfo` pointed to by
     * `deviceInfo`
     */
    fun DeviceInfo_getName(deviceInfo: Long): String? {
        return newString(DeviceInfo_getNameBytes(deviceInfo))
    }

    /**
     * Gets the name as a `byte` array of the PortAudio device specified by the pointer to
     * its `PaDeviceInfo` instance.
     *
     * @param deviceInfo the pointer to the `PaDeviceInfo` instance to get the name of
     * @return the name as a `byte` array of the PortAudio device specified by the
     * `PaDeviceInfo` instance pointed to by `deviceInfo`
     */
    private external fun DeviceInfo_getNameBytes(deviceInfo: Long): ByteArray?

    /**
     * Transport type for the device: BuiltIn, USB, BLuetooth, etc.
     *
     * @param deviceInfo device info pointer.
     * @return The transport type identifier.
     */
    fun DeviceInfo_getTransportType(deviceInfo: Long): String? {
        return newString(DeviceInfo_getTransportTypeBytes(deviceInfo))
    }

    /**
     * Transport type for the device: BuiltIn, USB, BLuetooth, etc.
     *
     * @param deviceInfo device info pointer.
     * @return The transport type identifier.
     */
    external fun DeviceInfo_getTransportTypeBytes(deviceInfo: Long): ByteArray?

    /**
     * Implements a callback which gets called by the native PortAudio counterpart to notify the
     * Java counterpart that the list of PortAudio devices has changed.
     */
    fun devicesChangedCallback() {
        val devicesChangedCallback = devicesChangedCallback
        devicesChangedCallback?.run()
    }

    private external fun free(ptr: Long)

    /**
     * Retrieve the index of the default input device.
     *
     * @return The default input device index for the default host API, or `paNoDevice`
     * if no
     * default input device is available or an error was encountered.
     */
    external fun GetDefaultInputDevice(): Int

    /**
     * Retrieve the index of the default output device.
     *
     * @return The default input device index for the default host API, or `paNoDevice`
     * if no
     * default input device is available or an error was encountered.
     */
    external fun GetDefaultOutputDevice(): Int

    /**
     * Retrieve the number of available devices. The number of available devices may be zero.
     *
     * @return the number of devices.
     * @throws PortAudioException
     */
    @Throws(PortAudioException::class)
    external fun GetDeviceCount(): Int

    /**
     * Returns the PortAudio index of the device identified by a specific `deviceID` or
     * [Pa.paNoDevice] if no such device exists. The `deviceID` is either a
     * `deviceUID` or a (PortAudio device) name depending, for example, on operating
     * system/API availability. Since at least names may not be unique, the PortAudio device to
     * return the index of may be identified more specifically by the minimal numbers of
     * channels to be required from the device for input and output.
     *
     * @param deviceID a `String` identifying the PortAudio device to retrieve the index of. It is
     * either a `deviceUID` or a (PortAudio device) name.
     * @param minInputChannels
     * @param minOutputChannels
     * @return the PortAudio index of the device identified by the specified `deviceID` or
     * `Pa.paNoDevice` if no such device exists
     */
    fun getDeviceIndex(deviceID: String?, minInputChannels: Int, minOutputChannels: Int): Int {
        if (deviceID != null) {
            var deviceCount = 0
            try {
                deviceCount = GetDeviceCount()
            } catch (paex: PortAudioException) {
                /*
                 * A deviceCount equal to 0 will eventually result in a return value equal to
                 * paNoDevice.
                 */
            }
            for (deviceIndex in 0 until deviceCount) {
                val deviceInfo = GetDeviceInfo(deviceIndex)
                /* The deviceID is either the deviceUID or the name. */
                val deviceUID = DeviceInfo_getDeviceUID(deviceInfo)
                if (deviceID == if (deviceUID == null || deviceUID.length == 0) DeviceInfo_getName(deviceInfo) else deviceUID) {
                    /*
                     * Resolve deviceID clashes by further identifying the device through the
                     * numbers of channels that it supports for input and output.
                     */
                    if (minInputChannels > 0
                            && (DeviceInfo_getMaxInputChannels(deviceInfo)
                                    < minInputChannels)) continue
                    if (minOutputChannels > 0
                            && (DeviceInfo_getMaxOutputChannels(deviceInfo)
                                    < minOutputChannels)) continue
                    return deviceIndex
                }
            }
        }

        // No corresponding device was found.
        return paNoDevice
    }

    /**
     * Retrieve a pointer to a PaDeviceInfo structure containing information about the specified
     * device.
     *
     * @param deviceIndex the device index
     * @return pointer to device info structure.
     */
    external fun GetDeviceInfo(deviceIndex: Int): Long

    /**
     * Retrieve a pointer to a structure containing information about a specific host Api.
     *
     * @param hostApiIndex host api index.
     * @return A pointer to an immutable PaHostApiInfo structure describing a specific host API.
     */
    external fun GetHostApiInfo(hostApiIndex: Int): Long

    /**
     * Gets the native `PaSampleFormat` with a specific size in bits.
     *
     * @param sampleSizeInBits the size in bits of the native `PaSampleFormat` to get
     * @return the native `PaSampleFormat` with the specified size in bits
     */
    fun getPaSampleFormat(sampleSizeInBits: Int): Long {
        return when (sampleSizeInBits) {
            8 -> SAMPLE_FORMAT_INT8
            24 -> SAMPLE_FORMAT_INT24
            32 -> SAMPLE_FORMAT_INT32
            else -> SAMPLE_FORMAT_INT16
        }
    }

    /**
     * Retrieve the size of a given sample format in bytes.
     *
     * @param format the format.
     * @return The size in bytes of a single sample in the specified format, or
     * `paSampleFormatNotSupported` if the format is not supported.
     */
    external fun GetSampleSize(format: Long): Int

    /**
     * Retrieve the number of frames that can be read from the stream without waiting.
     *
     * @param stream pointer to the stream.
     * @return returns a non-negative value representing the maximum number of frames that can be
     * read from the stream without blocking or busy waiting or, a `PaErrorCode`
     * (which are always negative) if PortAudio is not initialized or an error is encountered.
     */
    external fun GetStreamReadAvailable(stream: Long): Long

    /**
     * Retrieve the number of frames that can be written to the stream without waiting.
     *
     * @param stream pointer to the stream.
     * @return returns a non-negative value representing the maximum number of frames that can be
     * written to the stream without blocking or busy waiting or, a PaErrorCode (which are
     * always negative) if PortAudio is not initialized or an error is encountered.
     */
    external fun GetStreamWriteAvailable(stream: Long): Long

    /**
     * Gets the suggested latency to be used when opening PortAudio streams.
     *
     * @return the suggested latency to be used when opening PortAudio streams
     */
    val suggestedLatency: Double
        get() {
            val cfg: ConfigurationService = LibJitsi.configurationService
            if (cfg != null) {
                val suggestedLatencyString = cfg.getString(PROP_SUGGESTED_LATENCY)
                if (suggestedLatencyString != null) {
                    try {
                        val suggestedLatency = suggestedLatencyString.toDouble()
                        if (suggestedLatency != LATENCY_UNSPECIFIED) return suggestedLatency
                    } catch (nfe: NumberFormatException) {
                        Timber.e(nfe, "Failed to parse configuration property %s value as a double",
                                PROP_SUGGESTED_LATENCY)
                    }
                }
            }
            return if (OSUtils.IS_MAC || OSUtils.IS_LINUX) LATENCY_HIGH else if (OSUtils.IS_WINDOWS) 0.1 else LATENCY_UNSPECIFIED
        }

    /**
     * The default input device for this host API.
     *
     * @param hostApiInfo pointer to host API info structure.
     * @return The default input device for this host API.
     */
    external fun HostApiInfo_getDefaultInputDevice(hostApiInfo: Long): Int

    /**
     * The default output device for this host API.
     *
     * @param hostApiInfo pointer to host API info structure.
     * @return The default output device for this host API.
     */
    external fun HostApiInfo_getDefaultOutputDevice(hostApiInfo: Long): Int

    /**
     * The number of devices belonging to this host API.
     *
     * @param hostApiInfo pointer to host API info structure.
     * @return The number of devices belonging to this host API.
     */
    external fun HostApiInfo_getDeviceCount(hostApiInfo: Long): Int

    /**
     * The well known unique identifier of this host API.
     *
     * @param hostApiInfo
     * @param hostApiInfo pointer to host API info structure.
     * @return The well known unique identifier of this host API.
     * Enumerator:
     * paInDevelopment
     * paDirectSound
     * paMME
     * paASIO
     * paSoundManager
     * paCoreAudio
     * paOSS
     * paALSA
     * paAL
     * paBeOS
     * paWDMKS
     * paJACK
     * paWASAPI
     * paAudioScienceHPI
     */
    external fun HostApiInfo_getType(hostApiInfo: Long): Int

    /**
     * Initializes the native PortAudio library.
     *
     * @throws PortAudioException
     */
    @Throws(PortAudioException::class)
    private external fun Initialize()

    /**
     * Determine whether it would be possible to open a stream with the specified parameters.
     *
     * @param inputParameters A structure that describes the input parameters used to open a stream.
     * @param outputParameters A structure that describes the output parameters used to open a stream.
     * @param sampleRate The required sampleRate.
     * @return returns 0 if the format is supported, and an error code indicating why the format is
     * not supported otherwise. The constant paFormatIsSupported is provided to compare with
     * the return value for success.
     */
    external fun IsFormatSupported(inputParameters: Long, outputParameters: Long,
            sampleRate: Double): Boolean

    /**
     * Opens a stream for either input, output or both.
     *
     * @param inputParameters the input parameters or 0 if absent.
     * @param outputParameters the output parameters or 0 if absent.
     * @param sampleRate The desired sampleRate.
     * @param framesPerBuffer The number of frames passed to the stream callback function, or the preferred block
     * granularity for a blocking read/write stream
     * @param streamFlags Flags which modify the behavior of the streaming process.
     * @param streamCallback A pointer to a client supplied function that is responsible for processing and filling
     * input and output buffers. If `null`, the stream will be opened in 'blocking
     * read/write' mode.
     * @return pointer to the opened stream.
     * @throws PortAudioException
     */
    @Throws(PortAudioException::class)
    external fun OpenStream(inputParameters: Long, outputParameters: Long,
            sampleRate: Double, framesPerBuffer: Long, streamFlags: Long,
            streamCallback: PortAudioStreamCallback?): Long

    /**
     * Read samples from an input stream. The function doesn't return until the entire buffer has
     * been filled - this may involve waiting for the operating system to supply the data.
     *
     * @param stream pointer to the stream.
     * @param buffer a buffer of sample frames.
     * @param frames The number of frames to be read into buffer.
     * @throws PortAudioException
     */
    @Throws(PortAudioException::class)
    external fun ReadStream(stream: Long, buffer: ByteArray?, frames: Long)

    /**
     * Sets the indicator which determines whether a specific (input) PortAudio stream is to have
     * denoise performed on the audio data it provides.
     *
     * @param stream the (input) PortAudio stream for which denoise is to be enabled or disabled
     * @param denoise `true` if denoise is to be performed on the audio data provided by
     * `stream`; otherwise, `false`
     */
    external fun setDenoise(stream: Long, denoise: Boolean)
    fun setDevicesChangedCallback(devicesChangedCallback: Runnable?) {
        Pa.devicesChangedCallback = devicesChangedCallback
    }

    /**
     * Sets the number of milliseconds of echo to be canceled in the audio data provided by a
     * specific (input) PortAudio stream.
     *
     * @param stream the (input) PortAudio stream for which the number of milliseconds of echo to be
     * canceled is to be set
     * @param echoFilterLengthInMillis the number of milliseconds of echo to be canceled in the audio data provided by
     * `stream`
     */
    external fun setEchoFilterLengthInMillis(stream: Long, echoFilterLengthInMillis: Long)

    /**
     * Commences audio processing.
     *
     * @param stream pointer to the stream
     * @throws PortAudioException
     */
    @Throws(PortAudioException::class)
    external fun StartStream(stream: Long)

    /**
     * Terminates audio processing. It waits until all pending audio buffers have been played
     * before
     * it returns.
     *
     * @param stream pointer to the stream
     * @throws PortAudioException
     */
    @Throws(PortAudioException::class)
    external fun StopStream(stream: Long)

    /**
     * Free StreamParameters resources specified by a pointer to it.
     *
     * @param streamParameters the pointer to the `PaStreamParameters` to free
     */
    fun StreamParameters_free(streamParameters: Long) {
        free(streamParameters)
    }

    /**
     * Creates parameters used for opening streams.
     *
     * @param deviceIndex the device.
     * @param channelCount the channels to be used.
     * @param sampleFormat the sample format.
     * @param suggestedLatency the suggested latency in milliseconds: LATENCY_UNSPECIFIED - use default(default high
     * input/output latency) LATENCY_HIGH - use default high input/output latency LATENCY_LOW
     * - use default low input/output latency ... - any other value in milliseconds (e.g. 0.1
     * is acceptable)
     * @return pointer to the params used for Pa_OpenStream.
     */
    external fun StreamParameters_new(deviceIndex: Int, channelCount: Int,
            sampleFormat: Long, suggestedLatency: Double): Long

    external fun UpdateAvailableDeviceList()

    /**
     * Writes samples to an output stream. Does not return until the specified samples have been
     * consumed - this may involve waiting for the operating system to consume the data.
     *
     *
     * Provides better efficiency than achieved through multiple consecutive calls to
     * [.WriteStream] with one and the same buffer because the JNI access
     * to the bytes of the buffer which is likely to copy the whole buffer is only performed once.
     *
     *
     * @param stream the pointer to the PortAudio stream to write the samples to
     * @param buffer the buffer containing the samples to be written
     * @param offset the byte offset in `buffer` at which the samples to be written start
     * @param frames the number of frames from `buffer` starting at `offset` are to be
     * written with a single write
     * @param numberOfWrites the number of writes each writing `frames` number of frames to be performed
     * @throws PortAudioException if anything goes wrong while writing
     */
    @Throws(PortAudioException::class)
    external fun WriteStream(stream: Long, buffer: ByteArray?, offset: Int, frames: Long,
            numberOfWrites: Int)

    /**
     * Write samples to an output stream. This function doesn't return until the entire buffer has
     * been consumed - this may involve waiting for the operating system to consume the data.
     *
     * @param stream pointer to the stream
     * @param buffer A buffer of sample frames.
     * @param frames The number of frames to be written from buffer.
     * @throws PortAudioException
     */
    @Throws(PortAudioException::class)
    fun WriteStream(stream: Long, buffer: ByteArray?, frames: Long) {
        WriteStream(stream, buffer, 0, frames, 1)
    }

    /**
     * Enumerates the unchanging unique identifiers of each of the supported host APIs. The type is
     * used in the `PaHostApiInfo` structure. The values are guaranteed to be unique and to
     * never change, thus allowing code to be written that conditionally uses host API specific
     * extensions.
     */
    enum class HostApiTypeId(private val value: Int) {
        paAL(9), paALSA(8), paASIO(3), paAudioScienceHPI(14), paBeOS(10), paCoreAudio(5), paDirectSound(1), paInDevelopment(0 /* use while developing support for a new host API */), paJACK(12), paMME(2), paOSS(7), paSoundManager(4), paWASAPI(13), paWDMKS(11);

        companion object {
            /**
             * Returns the `PaHostApiTypeId` which has a specific value or `null` if
             * there is no such representation.
             *
             * @param value
             * @return the `PaHostApiTypeId` which has the specified `value` or
             * `null` if there is no such representation
             */
            fun valueOf(value: Int): HostApiTypeId? {
                for (hati in values()) {
                    if (hati.value == value) return hati
                }
                return null
            }
        }
    }
}