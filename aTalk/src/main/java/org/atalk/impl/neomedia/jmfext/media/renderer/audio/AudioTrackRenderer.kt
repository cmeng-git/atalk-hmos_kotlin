/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.renderer.audio

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioTrack
import org.atalk.impl.neomedia.NeomediaActivator
import org.atalk.impl.neomedia.device.*
import org.atalk.impl.neomedia.device.AudioSystem.Companion.getAudioSystem
import org.atalk.impl.neomedia.jmfext.media.protocol.audiorecord.DataSource
import org.atalk.service.neomedia.BasicVolumeControl
import org.atalk.service.neomedia.codec.Constants
import timber.log.Timber
import javax.media.*
import javax.media.PlugIn.*
import javax.media.format.AudioFormat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Implements an audio `Renderer` which uses [AudioTrack].
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class AudioTrackRenderer @JvmOverloads constructor(
        enableGainControl: Boolean = true,
) : AbstractAudioRenderer<AudioSystem>(getAudioSystem(AudioSystem.LOCATOR_PROTOCOL_AUDIORECORD)) {
    /**
     * The `AudioTrack` which implements the output device represented by this `Renderer` and renders it.
     */
    private var audioTrack: AudioTrack? = null

    /**
     * The length in bytes of media data to be written into [.audioTrack] via a single call to [AudioTrack.write].
     */
    private var audioTrackWriteLengthInBytes = 0

    /**
     * The `GainControl` through which the volume/gain of rendered media is controlled.
     */
    private val gainControl: GainControl?

    /**
     * The value of [GainControl.getLevel] of [.gainControl] which has been applied to
     * [.audioTrack] using [AudioTrack.setStereoVolume].
     */
    private var gainControlLevelAppliedToAudioTrack = -1f

    /**
     * The buffer into which media data is written during the execution of [.process]
     * and from which media data is read into [.audioTrack] in order to incur latency.
     */
    private var latency: ByteArray? = null

    /**
     * The zero-based index in [.latency] at which the beginning of (actual/valid) media data is contained.
     */
    private var latencyHead = 0

    /**
     * The number of bytes in [.latency] containing (actual/valid) media data.
     */
    private var latencyLength = 0

    /**
     * The `Thread` which reads from [.latency] and writes into [.audioTrack].
     */
    private var latencyThread: Thread? = null

    /**
     * The indicator which determines whether this `AudioTrackRenderer` is to set the
     * priority of the thread in which its [.process] method is executed.
     */
    private var setThreadPriority = true

    /**
     * The type of audio stream in the terms of [AudioManager] to be rendered to the output
     * device represented by this `AudioTrackRenderer`.
     */
    private val streamType: Int

    /**
     * Initializes a new `AudioTrackRenderer` instance.
     */
    init {
        /*
         * Flag enableGainControl also indicates that it's a call audio stream, so we switch stream
         * type here to use different native volume control.
         */
        if (enableGainControl) {
            streamType = AudioManager.STREAM_VOICE_CALL

            val mediaServiceImpl = NeomediaActivator.getMediaServiceImpl()
            gainControl = if (mediaServiceImpl == null) null else mediaServiceImpl.outputVolumeControl as GainControl
        }
        else {
            streamType = AudioManager.STREAM_NOTIFICATION
            gainControl = null
        }
        Timber.d("Audio Track Renderer creating stream for type: %s", streamType)
    }

    /**
     * Implements [PlugIn.close]. Closes this [PlugIn] and releases its resources.
     *
     * @see PlugIn.close
     */
    @Synchronized
    override fun close() {
        if (audioTrack != null) {
            // Timber.w(Exception(), "Audio Track: closed: %s", audioTrack)
            audioTrack!!.release()
            audioTrack = null
            setThreadPriority = true
            var interrupted = false

            while (latencyThread != null) {
                try {
                    (this as Object).wait(20)
                } catch (ie: InterruptedException) {
                    interrupted = true
                }
            }

            if (interrupted) Thread.currentThread().interrupt()
            latency = null
            latencyHead = 0
            latencyLength = 0
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
     * Implements [Renderer.getSupportedInputFormats]. Gets the list of input `Format`s supported by this `Renderer`.
     *
     * @return the list of input `Format`s supported by this `Renderer`
     * @see Renderer.getSupportedInputFormats
     */
    override fun getSupportedInputFormats(): Array<Format> {
        if (sInputFormats == null) {
            val supportedInputSampleRates = DoubleArray(1 + Constants.AUDIO_SAMPLE_RATES.size)
            var supportedInputSampleRateCount = 0

            // sNative (44100/48000) is the same for all Stream Type (Voice or Notification) on  the same device
            val sNative = AudioTrack.getNativeOutputSampleRate(streamType).toDouble()
            if (!Constants.AUDIO_SAMPLE_RATES.toTypedArray().contains(sNative)) {
                supportedInputSampleRates[supportedInputSampleRateCount++] = sNative
            }
            System.arraycopy(Constants.AUDIO_SAMPLE_RATES, 0, supportedInputSampleRates,
                supportedInputSampleRateCount, Constants.AUDIO_SAMPLE_RATES.size)
            supportedInputSampleRateCount += Constants.AUDIO_SAMPLE_RATES.size

            val inFormats = ArrayList<Format>()
            for (i in 0 until supportedInputSampleRateCount) {
                val sampleRate = supportedInputSampleRates[i]

                inFormats.add(AudioFormat(
                    AudioFormat.LINEAR,
                    sampleRate,
                    16 /* sampleSizeInBits */,
                    Format.NOT_SPECIFIED /* channels */,
                    AudioFormat.LITTLE_ENDIAN,
                    AudioFormat.SIGNED,
                    Format.NOT_SPECIFIED /* frameSizeInBits */,
                    NOT_SPECIFIED_DOUBLE /* frameRate */,
                    Format.byteArray)
                )

                inFormats.add(AudioFormat(
                    AudioFormat.LINEAR,
                    sampleRate,
                    8 /* sampleSizeInBits */,
                    Format.NOT_SPECIFIED /* channels */,
                    AudioFormat.LITTLE_ENDIAN,
                    AudioFormat.SIGNED,
                    Format.NOT_SPECIFIED /* frameSizeInBits */,
                    NOT_SPECIFIED_DOUBLE /* frameRate */,
                    Format.byteArray)
                )
            }
            sInputFormats = inFormats.toTypedArray()
        }

        return sInputFormats!!.clone()
    }

    /**
     * Implements [PlugIn.open]. Opens this [PlugIn] and acquires the resources that it needs to operate.
     *
     * @throws ResourceUnavailableException if any of the required resources cannot be acquired @see PlugIn#open()
     */
    @Synchronized
    @Throws(ResourceUnavailableException::class)
    override fun open() {
        if (audioTrack == null) {
            val inputFormat = inputFormat
            val sampleRate = inputFormat!!.sampleRate
            var channels = inputFormat.channels
            if (channels == Format.NOT_SPECIFIED) channels = 1

            val channelConfig = when (channels) {
                1 -> android.media.AudioFormat.CHANNEL_OUT_MONO
                2 -> android.media.AudioFormat.CHANNEL_OUT_STEREO
                else -> throw ResourceUnavailableException("channels")
            }

            val sampleSizeInBits = inputFormat.sampleSizeInBits
            val audioFormat = when (sampleSizeInBits) {
                8 -> android.media.AudioFormat.ENCODING_PCM_8BIT
                16 -> android.media.AudioFormat.ENCODING_PCM_16BIT
                else -> throw ResourceUnavailableException("sampleSizeInBits")
            }

            val bytesPerMillisecond = ((sampleRate / 1000) * channels * (sampleSizeInBits / 8.0)).roundToInt()
            audioTrackWriteLengthInBytes = 20 /* milliseconds */ * bytesPerMillisecond

            /*
             * Give the AudioTrack a large enough buffer size in bytes in case it remedies cracking.
             */
            val audioTrackBufferSizeInBytes = 5 * audioTrackWriteLengthInBytes

            /*
             * Apart from the thread in which #process(Buffer) is executed, use the thread priority
             * for the thread which will create the AudioTrack.
             */
            DataSource.setThreadPriority()
            val bufferSize = max(audioTrackBufferSizeInBytes,
                AudioTrack.getMinBufferSize(sampleRate.toInt(), channelConfig, audioFormat))

            /*
             * @deprecated use {@link AudioTrack.Builder} or
             * {@link #AudioTrack(AudioAttributes, android.media.AudioFormat, int,int,int)}to specify the
             * {@link AudioAttributes} instead of the stream type which is only for volume control.
             */
            val audioAttribute = if (AudioManager.STREAM_VOICE_CALL == streamType) {
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build()
            }
            else {
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            }

            val androidAudioFormat = android.media.AudioFormat.Builder()
                .setChannelMask(channelConfig)
                .setEncoding(audioFormat)
                .setSampleRate(sampleRate.toInt())
                .build()

            audioTrack = AudioTrack(audioAttribute, androidAudioFormat, bufferSize, AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE)

            setThreadPriority = true
            if (USE_SOFTWARE_GAIN) {
                /*
                 * Set the volume of the audioTrack to the maximum value because there is volume control via neomedia.
                 */
                val volume = MAX_AUDIO_TRACK_VOLUME
                val setVolumeStatus = audioTrack!!.setVolume(volume)
                if (setVolumeStatus != AudioTrack.SUCCESS) {
                    Timber.w("AudioTrack.setVolume() failed with return value %s", setVolumeStatus)
                }
            }
            else {
                /*
                 * The level specified by gainControl has not been applied to audioTrack yet.
                 */
                gainControlLevelAppliedToAudioTrack = -1f
            }

            /* Incur latency if requested. */
            latency = if (LATENCY > 0) ByteArray(2 * LATENCY * bytesPerMillisecond) else null
            latencyHead = 0
            latencyLength = 0

            if (latency == null) latencyThread = null
            else {
                latencyThread = object : Thread() {
                    override fun run() {
                        runInLatencyThread()
                    }
                }
                latencyThread!!.isDaemon = true
                latencyThread!!.name = "AudioTrackRenderer.LatencyThread"
                try {
                    latencyThread!!.start()
                } catch (t: Throwable) {
                    latencyThread = null
                    if (t is ThreadDeath) throw t
                    else {
                        val rue = ResourceUnavailableException("latencyThread")
                        rue.initCause(t)
                        throw rue
                    }
                }
            }
        }
    }

    /**
     * Implements [Renderer.process]. Processes the media data contained in a
     * specific [Buffer] and renders it to the output device represented by this `Renderer`.
     *
     * @param buffer the `Buffer` containing the media data to be processed and rendered to the
     * output device represented by this `Renderer`
     * @return one or a combination of the constants defined in [PlugIn]
     * @see Renderer.process
     */
    override fun process(buffer: Buffer): Int {
        /*
         * We do not have early access to the Thread which runs the #process(Buffer) method of this
         * Renderer so we have to set the priority as part of the call to the method in question.
         */
        if (setThreadPriority) {
            setThreadPriority = false
            DataSource.setThreadPriority()
        }

        val format = buffer.format
        var processed: Int

        if ((format == null) || ((inputFormat != null) && inputFormat!!.matches(format))) {
            val data = buffer.data
            val length = buffer.length
            val offset = buffer.offset

            if (data == null || length == 0) {
                /*
                 * There is really no actual data to be processed by this AudioTrackRenderer.
                 */
                processed = BUFFER_PROCESSED_OK
            }
            else if (length < 0 || offset < 0 || data !is ByteArray) {
                /*
                 * The length, the offset and/or the data of the Buffer are not valid.
                 */
                processed = BUFFER_PROCESSED_FAILED
            }
            else {
                synchronized(this) {
                    if (audioTrack == null) {
                        /*
                         * This AudioTrackRenderer is not in a state in which it can process the data of the Buffer.
                         */
                        processed = BUFFER_PROCESSED_FAILED
                    }
                    else {
                        var written: Int

                        // Apply the gain specified by gainControl.
                        if (gainControl != null) {
                            if (USE_SOFTWARE_GAIN) {
                                BasicVolumeControl.applyGain(gainControl, data, offset, length)
                            }
                            else {
                                val gainControlLevel = gainControl.level
                                if (gainControlLevelAppliedToAudioTrack != gainControlLevel) {
                                    val maxVolume = MAX_AUDIO_TRACK_VOLUME
                                    val minVolume = MIN_AUDIO_TRACK_VOLUME
                                    val volume = minVolume + (AUDIO_TRACK_VOLUME_RANGE
                                            * gainControlLevel * ABSTRACT_VOLUME_CONTROL_PERCENT_RANGE)
                                    val effectiveVolume: Float
                                    val effectiveGainControlLevel: Float

                                    if (volume > maxVolume) {
                                        effectiveVolume = maxVolume
                                        effectiveGainControlLevel = (1.0f / ABSTRACT_VOLUME_CONTROL_PERCENT_RANGE)
                                    }
                                    else {
                                        effectiveVolume = volume
                                        effectiveGainControlLevel = gainControlLevel
                                    }

                                    val setVolumeStatus: Int
                                    if (gainControlLevelAppliedToAudioTrack == effectiveGainControlLevel) {
                                        setVolumeStatus = AudioTrack.SUCCESS
                                    }
                                    else {
                                        setVolumeStatus = audioTrack!!.setVolume(effectiveVolume)
                                        if (setVolumeStatus == AudioTrack.SUCCESS)
                                            gainControlLevelAppliedToAudioTrack = effectiveGainControlLevel
                                    }

                                    if ((setVolumeStatus != AudioTrack.SUCCESS)
                                            || (volume > maxVolume)) {
                                        BasicVolumeControl.applyGain(gainControl, data, offset, length)
                                    }
                                }
                            }
                        }

                        if (latency == null)
                            written = audioTrack!!.write(data, offset, length)
                        else {
                            /*
                             * Incur latency i.e. process the specified Buffer by means of the
                             * latency field of this AudioTrackRenderer.
                             */
                            written = 0

                            /*
                             * If there is no free room in the latency buffer, wait for it to be freed.
                             */
                            if (latency!!.size - latencyLength <= 0) {
                                var interrupted = false
                                try {
                                    (this as Object).wait(20)
                                } catch (ie: InterruptedException) {
                                    interrupted = true
                                }
                                if (interrupted)
                                    Thread.currentThread().interrupt()
                            }
                            else {
                                /*
                                 * There is some free room in the latency buffer so we can fill it
                                 * up using the data specified by the caller in the Buffer.
                                 */
                                val latencyTail = latencyHead + latencyLength
                                val freeTail = latency!!.size - latencyTail
                                var toWrite = length

                                if (freeTail > 0) {
                                    val tailToWrite = min(freeTail, toWrite)
                                    System.arraycopy(data, offset, latency!!, latencyTail, tailToWrite)
                                    latencyLength += tailToWrite
                                    toWrite -= tailToWrite
                                    written = tailToWrite
                                }
                                if (toWrite > 0) {
                                    val freeHead = latency!!.size - latencyLength

                                    if (freeHead > 0) {
                                        val headToWrite = min(freeHead, toWrite)
                                        System.arraycopy(data, offset + written, latency!!, 0, headToWrite)
                                        latencyLength += headToWrite
                                        written += headToWrite
                                    }
                                }
                            }
                        }

                        if (written < 0)
                            processed = BUFFER_PROCESSED_FAILED
                        else {
                            processed = BUFFER_PROCESSED_OK
                            if (written == 0) {
                                /*
                                 * If AudioTrack.write() persistently does not write any data to
                                 * the hardware for playback and we return
                                 * INPUT_BUFFER_NOT_CONSUMED, we will enter an infinite loop. We
                                 * might do better to not give up on the first try.
                                 * Unfortunately, there is no documentation from Google on the
                                 * subject to guide us. Consequently, we will base
                                 * our actions/decisions on our test results/observations and we
                                 * will not return INPUT_BUFFER_NOT_CONSUMED (which will
                                 * effectively drop the input Buffer).
                                 */
                                Timber.w("Dropping %d bytes of audio data!", length)
                            }
                            else if (written < length) {
                                processed = INPUT_BUFFER_NOT_CONSUMED
                                buffer.length = length - written
                                buffer.offset = offset + written
                            }
                        }
                    }
                }
            }
        }
        else {
            /*
             * This AudioTrackRenderer does not understand the format of the Buffer.
             */
            processed = BUFFER_PROCESSED_FAILED
        }
        return processed
    }

    /**
     * Runs in [.latencyThread]. Reads from [.latency] and writes into [.audioTrack].
     */
    private fun runInLatencyThread() {
        try {
            DataSource.setThreadPriority()
            var latencyIncurred = false

            while (true) {
                if ((Thread.currentThread() != latencyThread) || (audioTrack == null))
                    break

                synchronized(this) {
                    var wait = false
                    if (!latencyIncurred) {
                        if (latencyLength < (latency!!.size / 2))
                            wait = true
                        else
                            latencyIncurred = true
                    }
                    else if (latencyLength <= 0)
                        wait = true

                    if (wait) {
                        var interrupted = false

                        try {
                            (this as Object).wait(20)
                        } catch (ie: InterruptedException) {
                            interrupted = true
                        }
                        if (interrupted)
                            Thread.currentThread().interrupt()
                    }
                    else {
                        val toWrite = min(min(latencyLength, latency!!.size - latencyHead),
                            2 * audioTrackWriteLengthInBytes)
                        val written = audioTrack!!.write(latency!!, latencyHead, toWrite)

                        if (written < 0) {
                            throw RuntimeException("android.media.AudioTrack #write(byte[], int, int)")
                        }
                        else if (written > 0) {
                            latencyHead += written
                            if (latencyHead >= latency!!.size)
                                latencyHead = 0
                            latencyLength -= written
                        }
                    }
                }
            }
        } finally {
            synchronized(this) {
                if (Thread.currentThread() == latencyThread) {
                    latencyThread = null
                    (this as Object).notify()
                }
            }
        }
    }

    /**
     * Implements [Renderer.start]. Starts rendering to the output device represented by this `Renderer`.
     *
     * @see Renderer.start
     */
    @Synchronized
    override fun start() {
        if (audioTrack != null) {
            // Timber.w(Exception(), "Audio Track: start %s", audioTrack)
            setThreadPriority = true
            audioTrack!!.play()
        }
    }

    /**
     * Implements [Renderer.stop]. Stops rendering to the output device represented by this `Renderer`.
     *
     * @see Renderer.stop
     */
    @Synchronized
    override fun stop() {
        if (audioTrack != null && audioTrack!!.state == AudioTrack.STATE_INITIALIZED) {
            audioTrack!!.stop()
            setThreadPriority = true
        }
    }

    // To change body of implemented methods use File | Settings | File Templates.
    override fun getControl(controlType: String): Any? {
        return null
    }

    override fun getControls(): Array<Any> {
        // To change body of implemented methods use File | Settings | File Templates.
        return emptyArray()
    }

    companion object {
        private const val ABSTRACT_VOLUME_CONTROL_PERCENT_RANGE = (BasicVolumeControl.MAX_VOLUME_PERCENT - BasicVolumeControl.MIN_VOLUME_PERCENT) / 100

        /**
         * The length of the valid volume value range accepted by `AudioTrack` instances.
         */
        private val AUDIO_TRACK_VOLUME_RANGE: Float

        /**
         * The latency in milliseconds to be incurred by `AudioTrackRenderer`.
         */
        private const val LATENCY = 0

        /**
         * The maximum valid volume value accepted by `AudioTrack` instances.
         */
        private val MAX_AUDIO_TRACK_VOLUME = AudioTrack.getMaxVolume()

        /**
         * The minimum valid volume value accepted by `AudioTrack` instances.
         */
        private val MIN_AUDIO_TRACK_VOLUME = AudioTrack.getMinVolume()

        /**
         * The human-readable name of the `AudioTrackRenderer` FMJ plug-in.
         */
        private const val PLUGIN_NAME = "android.media.AudioTrack Renderer"

        /**
         * The indicator which determines whether the gain specified by [.gainControl] is to be
         * applied in a software manner using
         * [BasicVolumeControl.applyGain] or in a hardware manner using [AudioTrack.setVolume].
         *
         * Currently we use software gain control. Output volume is controlled using
         * `AudioManager` by adjusting stream volume. When the minimum value is reached we keep
         * lowering the volume using software gain control. The opposite happens for the maximum volume.
         * See CallVolumeCtrlFragment.
         */
        private const val USE_SOFTWARE_GAIN = true

        /**
         * The list of `Format`s of media data supported as input by this `Renderer`.
         */
        private var sInputFormats: Array<Format>? = null

        init {
            AUDIO_TRACK_VOLUME_RANGE = abs(MAX_AUDIO_TRACK_VOLUME - MIN_AUDIO_TRACK_VOLUME)
        }
    }
}