/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec

import timber.log.Timber
import java.util.*
import javax.media.*
import javax.media.format.AudioFormat

/**
 * An `Effect` which detects discontinuities in an audio stream by monitoring the input
 * `Buffer`s' timestamps and lengths, and inserts silence to account for missing data.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class SilenceEffect : AbstractCodec2, Effect {
    /**
     * Whether to use the input `Buffer`s' RTP timestamps (with
     * `Buffer.getRtpTimestamp()`), or their "regular" timestamps (with
     * `Buffer.getTimestamp()`).
     */
    private val useRtpTimestamp: Boolean

    /**
     * The clock rate for the timestamps of input `Buffer`s (i.e. the number of units which
     * constitute one second).
     */
    private val clockRate: Int

    /**
     * The total number of samples of silence inserted by this instance.
     */
    private var totalSamplesInserted = 0

    /**
     * The timestamp (either the RTP timestamp, or the `Buffer`'s timestamp, according to the
     * value of [.useRtpTimestamp]) of the last sample that was output by this `Codec`.
     */
    private var lastOutputTimestamp = Buffer.TIME_UNKNOWN
    private var listener: Listener? = null

    /**
     * Initializes a new `SilenceEffect`, which is to use the input `Buffer`s'
     * timestamps (as opposed to using their RTP timestamps).
     */
    constructor() : super(NAME, AudioFormat::class.java, SUPPORTED_FORMATS) {
        useRtpTimestamp = false
        // Buffer.getTimestamp() will be used, which is in nanoseconds.
        clockRate = 1000 * 1000 * 1000
    }

    /**
     * Initializes a new `SilenceEffect`, which is to use the input `Buffer`s' RTP
     * timestamps.
     *
     * @param rtpClockRate the clock rate that the RTP timestamps use.
     */
    constructor(rtpClockRate: Int) : super(NAME, AudioFormat::class.java, SUPPORTED_FORMATS) {
        useRtpTimestamp = true
        clockRate = rtpClockRate
    }

    /**
     * {@inheritDoc}
     */
    override fun doClose() {
        Timber.i("Closing SilenceEffect, inserted a total of %d samples of silence.", totalSamplesInserted)
    }

    /**
     * {@inheritDoc}
     */
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
    }

    /**
     * Processes `inBuf`, and either copies its data to `outBuf` or copies silence
     *
     * @param inBuf the input `Buffer`.
     * @param outBuf the output `Buffer`.
     * @return `BUFFER_PROCESSED_OK` if `inBuf`'s date was copied to `outBuf`,
     * and `INPUT_BUFFER_NOT_CONSUMED` if silence was inserted instead.
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        var useInput = true
        val timestamp = if (useRtpTimestamp) inBuf.rtpTimeStamp else inBuf.timeStamp
        if (timestamp == Buffer.TIME_UNKNOWN) {
            // if the current Buffer's timestamp is unknown, we don't know how
            // much silence to insert, so we let the Buffer pass and reset our state.
            lastOutputTimestamp = Buffer.TIME_UNKNOWN
        } else if (lastOutputTimestamp == Buffer.TIME_UNKNOWN) {
            // Initialize lastOutputTimestamp. The samples from the current
            // buffer will be added below.
            lastOutputTimestamp = timestamp
            if (listener != null) listener!!.onSilenceNotInserted(timestamp)
        } else  // timestamp != -1 && lastOutputTimestamp != -1
        {
            var diff = timestamp - lastOutputTimestamp
            if (useRtpTimestamp && diff < -(1L shl 31)) {
                // RTP timestamps have wrapped
                diff += 1L shl 32
            } else if (useRtpTimestamp && diff < 0) {
                // an older packet received (possibly a retransmission)
                outBuf.isDiscard = true
                return BUFFER_PROCESSED_OK
            }
            var diffSamples = Math.round((diff * sampleRate).toDouble() / clockRate)
            if (diffSamples > MAX_SAMPLES_SILENCE) {
                Timber.i("More than the maximum of %d samples of silence need to be inserted.", MAX_SAMPLES_SILENCE)
                if (listener != null) listener!!.onSilenceNotInserted(timestamp)
                lastOutputTimestamp = timestamp
                diffSamples = 0
            }
            if (diffSamples > 0) {
                useInput = false
                val samplesInserted = setSilence(outBuf, diffSamples.toInt())
                totalSamplesInserted += samplesInserted
                if (useRtpTimestamp) {
                    // outBuf.setRtpTimeStamp(lastOutputTimestamp);
                } else {
                    outBuf.timeStamp = lastOutputTimestamp
                }
                outBuf.duration = diffSamples * 1000L * 1000L * 1000L / sampleRate
                lastOutputTimestamp = calculateTimestamp(lastOutputTimestamp, samplesInserted.toLong())
            }
        }
        if (useInput) {
            val inLen = inBuf.length
            if (COPY_DATA_FROM_INPUT_TO_OUTPUT) {
                // Copy the actual data from the input to the output.
                val outData = validateByteArraySize(outBuf, inLen, false)
                outBuf.length = inLen
                outBuf.offset = 0
                System.arraycopy(inBuf.data, inBuf.offset, outData, 0, inLen)

                // Now copy the remaining attributes.
                outBuf.format = inBuf.format
                outBuf.header = inBuf.header
                outBuf.sequenceNumber = inBuf.sequenceNumber
                outBuf.timeStamp = inBuf.timeStamp
                outBuf.rtpTimeStamp = inBuf.rtpTimeStamp
                outBuf.flags = inBuf.flags
                outBuf.isDiscard = inBuf.isDiscard
                outBuf.isEOM = inBuf.isEOM
                outBuf.duration = inBuf.duration
            } else {
                outBuf.copy(inBuf)
            }
            lastOutputTimestamp = calculateTimestamp(lastOutputTimestamp, (inLen * 8
                    / sampleSizeInBits).toLong())
        }
        return if (useInput) BUFFER_PROCESSED_OK else INPUT_BUFFER_NOT_CONSUMED
    }

    /**
     * Returns the timestamp obtained by adding `samplesToAdd` samples (using a sample rate
     * of `this.sampleRate` per second) to timestamp (with a clock rate of
     * `this.clockRate` per second).
     *
     * @param oldTimestamp the timestamp to which to add.
     * @param samplesToAdd the number of samples to add.
     * @return the timestamp obtained by adding `samplesToAdd` samples (using a sample rate
     * of `this.sampleRate` per second) to timestamp (with a clock rate of
     * `this.clockRate` per second).
     */
    private fun calculateTimestamp(oldTimestamp: Long, samplesToAdd: Long): Long {
        // duration of samplesToAdd (in seconds per clockRate)
        val duration = Math.round((clockRate * samplesToAdd).toDouble() / sampleRate)
        var timestamp = oldTimestamp + duration

        // RTP timestamps come from a 32bit field and wrap.
        if (useRtpTimestamp && timestamp > 1L shl 32) timestamp -= 1L shl 32
        return timestamp
    }

    /**
     * Fills the data of `buf` to at most `samples` samples of silence. Returns the
     * actual number of samples used.
     *
     * @param buf the `Buffer` to fill with silence
     * @param samples the number of samples of silence to fill.
     * @return the number of samples of silence added in `buf`.
     */
    private fun setSilence(buf: Buffer, samples: Int): Int {
        val samplesToFill = Math.min(samples, MAX_SAMPLES_PER_PACKET)
        val len = samplesToFill * sampleSizeInBits / 8
        val data = validateByteArraySize(buf, len, false)
        Arrays.fill(data, 0.toByte())
        buf.offset = 0
        buf.length = len
        return samplesToFill
    }

    /**
     * Resets the state of this `SilenceEffect`.
     *
     * TODO: is it appropriate to override the `reset()` method?
     */
    fun resetSilence() {
        lastOutputTimestamp = Buffer.TIME_UNKNOWN
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    interface Listener {
        fun onSilenceNotInserted(timestamp: Long)
    }

    companion object {
        /**
         * The indicator which determines whether `SilenceEffect` instances are to perform the
         * copying of the data from input `Buffer`s to output `Buffer`s themselves (e.g.
         * using [System.arraycopy]).
         */
        private const val COPY_DATA_FROM_INPUT_TO_OUTPUT = true

        /**
         * The name of this `PlugIn`.
         */
        private const val NAME = "Silence Effect"

        /**
         * The maximum number of samples of silence to insert in a single `Buffer`.
         */
        private const val MAX_SAMPLES_PER_PACKET = 48000

        /**
         * The sample rate of the input/output format.
         */
        private const val sampleRate = 48000

        /**
         * The size of a single sample of input in bits.
         */
        private const val sampleSizeInBits = 16

        /**
         * Max samples of silence to insert between two `Buffer`s.
         */
        private const val MAX_SAMPLES_SILENCE = sampleRate * 3 // 3sec

        /**
         * The `Format`s supported as input/output by this `Effect`.
         */
        val SUPPORTED_FORMATS = arrayOf<Format>(AudioFormat(
                AudioFormat.LINEAR,
                sampleRate.toDouble(),
                sampleSizeInBits,
                1,  //channels
                Format.NOT_SPECIFIED,  //endian
                Format.NOT_SPECIFIED) //signed/unsigned
        )
    }
}