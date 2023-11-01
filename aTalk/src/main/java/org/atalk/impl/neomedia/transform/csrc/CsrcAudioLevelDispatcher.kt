/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.csrc

import org.atalk.impl.neomedia.AudioMediaStreamImpl
import org.atalk.util.concurrent.ExecutorUtils
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A simple dispatcher that handles new audio levels reported from incoming
 * RTP packets and then asynchronously delivers them to associated
 * `AudioMediaStreamImpl`. The asynchronous processing is necessary
 * due to time sensitive nature of incoming RTP packets.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Yura Yaroshevich
 */
class CsrcAudioLevelDispatcher(mediaStream: AudioMediaStreamImpl?) {
    /**
     * The levels added to this instance (by the `reverseTransform`
     * method of a `PacketTransformer` implementation) last.
     */
    private val levels = AtomicReference<LongArray?>()

    /**
     * The `AudioMediaStreamImpl` which listens to this event dispatcher.
     * If `null`, this event dispatcher is stopped. If non-`null`,
     * this event dispatcher is started.
     */
    private val mediaStream: AudioMediaStreamImpl

    /**
     * Indicates that [CsrcAudioLevelDispatcher] should continue delivery
     * of audio levels updates to [.mediaStream] on separate thread.
     */
    private val running = AtomicBoolean(true)

    /**
     * A cached instance of [.deliverAudioLevelsToMediaStream] runnable
     * to reduce allocations.
     */
    private val deliverRunnable = Runnable { deliverAudioLevelsToMediaStream() }

    /**
     * Initializes a new `CsrcAudioLevelDispatcher` to dispatch events
     * to a specific `AudioMediaStreamImpl`.
     *
     * @param mediaStream the `AudioMediaStreamImpl` to which the new instance is to dispatch events
     */
    init {
        requireNotNull(mediaStream) { "mediaStream is null" }
        this.mediaStream = mediaStream
    }

    /**
     * A level matrix that we should deliver to our media stream and its listeners in a separate thread.
     *
     * @param levels the levels that we'd like to queue for processing.
     * @param rtpTime the timestamp carried by the RTP packet which carries the specified `levels`
     */
    fun addLevels(levels: LongArray?, rtpTime: Long) {
        if (!running.get()) {
            return
        }
        this.levels.set(levels)

        // submit asynchronous delivery of audio levels update
        threadPool.execute(deliverRunnable)
    }

    /**
     * Closes current [CsrcAudioLevelDispatcher] to prevent further
     * audio level updates delivery to associated media stream.
     */
    fun close() {
        running.set(false)
        levels.set(null)
    }

    /**
     * Delivers last reported audio levels to associated [.mediaStream]
     */
    private fun deliverAudioLevelsToMediaStream() {
        if (!running.get()) {
            return
        }

        // read and reset latest audio levels
        val latestAudioLevels = levels.getAndSet(null)
        if (latestAudioLevels != null) {
            mediaStream.audioLevelsReceived(latestAudioLevels)
        }
    }

    companion object {
        /**
         * The executor service to asynchronously execute method which delivers
         * audio level updates to `AudioMediaStreamImpl`
         */
        private val threadPool = ExecutorUtils.newCachedThreadPool(true, CsrcAudioLevelDispatcher::class.java.name + "-")
    }
}