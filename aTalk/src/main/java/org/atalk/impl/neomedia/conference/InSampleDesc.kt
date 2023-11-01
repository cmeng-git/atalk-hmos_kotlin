/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.conference

import java.lang.ref.SoftReference
import javax.media.Buffer
import javax.media.format.AudioFormat

/**
 * Describes a specific set of audio samples read from a specific set of input streams specified by
 * their `InStreamDesc`s.
 *
 *
 * Private to `AudioMixerPushBufferStream` but extracted into its own file for the sake of
 * clarity.
 *
 *
 * @author Lyubomir Marinov
 */
internal class InSampleDesc
/**
 * Initializes a new `InSampleDesc` instance which is to describe a specific set of audio
 * samples read from a specific set of input streams specified by their `InStreamDesc`s.
 *
 * @param inSamples
 * the set of audio samples read from `inStreams`
 * @param inStreams
 * the set of input streams from which `inSamples` were read
 * @param format
 * the `AudioFormat` of `inSamples`
 */
(
        /**
         * The set of audio samples read from [.inStreams].
         */
        val inSamples: Array<ShortArray?>,
        /**
         * The set of input streams from which [.inSamples] were read.
         */
        val inStreams: Array<InStreamDesc>,
        /**
         * The `AudioFormat` of [.inSamples].
         */
        val format: AudioFormat) {

    /**
     * Gets the `Buffer` into which media data is to be read from the input streams
     * associated with this instance.
     *
     * @return the `Buffer` into which media data is to be read from the input streams
     * associated with this instance
     */
    /**
     * Sets the `Buffer` into which media data is to be read from the input streams
     * associated with this instance.
     *
     * @param buffer
     * the `Buffer` into which media data is to be read from the input streams
     * associated with this instance
     */
    /**
     * The `Buffer` into which media data is to be read from [.inStreams].
     */
    private var sBuffer: SoftReference<Buffer>? = null

    var buffer: Buffer?
        get() {
            var buffer = if (sBuffer == null) null else sBuffer!!.get()

            if (buffer == null) {
                buffer = Buffer()
                this.buffer = buffer
            }
            return buffer
        }
        private set(buffer) {
            sBuffer = when (buffer) {
                null ->  null
                else -> SoftReference(buffer)
            }
        }
    /**
     * this.sBuffer = new SoftReference(buffer)
     * Gets the time stamp of `inSamples` to be reported in the `Buffer`s of the
     * `AudioMixingPushBufferStream`s when mixes are read from them.
     *
     * @return the time stamp of `inSamples` to be reported in the `Buffer`s of the
     * `AudioMixingPushBufferStream`s when mixes are read from them
     *//*
			 * Setting the timeStamp more than once does not make sense because the inStreams will
			 * report different timeStamps so only one should be picked up where the very reading
			 * from inStreams takes place.
			 */
    /**
     * Sets the time stamp of `inSamples` to be reported in the `Buffer`s of the
     * `AudioMixingPushBufferStream`s when mixes are read from them.
     *
     * @param timeStamp
     * the time stamp of `inSamples` to be reported in the `Buffer`s of the
     * `AudioMixingPushBufferStream`s when mixes are read from them
     */
    /**
     * The time stamp of `inSamples` to be reported in the `Buffer`s of the
     * `AudioMixingPushBufferStream`s when mixes are read from them.
     */
    var timeStamp = Buffer.TIME_UNKNOWN
        set(timeStamp) {
            field = if (this.timeStamp == Buffer.TIME_UNKNOWN) {
                timeStamp
            } else {
                /*
			 * Setting the timeStamp more than once does not make sense because the inStreams will
			 * report different timeStamps so only one should be picked up where the very reading
			 * from inStreams takes place.
			 */
                throw IllegalStateException("timeStamp")
            }
        }
}