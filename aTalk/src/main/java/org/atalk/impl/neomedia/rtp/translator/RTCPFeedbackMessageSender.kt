/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.rtp.translator

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.RTCPFeedbackMessagePacket
import org.atalk.impl.neomedia.VideoMediaStreamImpl
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.event.RTCPFeedbackMessageEvent
import org.atalk.util.concurrent.PeriodicRunnable
import org.atalk.util.concurrent.RecurringRunnableExecutor
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Allows sending RTCP feedback message packets such as FIR, takes care of their
 * (command) sequence numbers.
 *
 * @author Boris Grozev
 * @author Lyubomir Marinov
 * @author George Politis
 * @author Eng Chong Meng
 */
class RTCPFeedbackMessageSender
/**
 * Initializes a new `RTCPFeedbackMessageSender` instance which is to
 * send RTCP feedback message packets through a specific
 * `RTPTranslatorImpl`.
 *
 * @param rtpTranslator the `RTPTranslatorImpl` through which the new instance is to send RTCP
 * feedback message packets and the SSRC of which is to be used as the SSRC of packet
 * sender
 */
(
        /**
         * The `RTPTranslatorImpl` through which this `RTCPFeedbackMessageSender` sends
         * RTCP feedback message packets. The synchronization source identifier (SSRC) of
         * `rtpTranslator` is used as the SSRC of packet sender.
         */
        private val rtpTranslator: RTPTranslatorImpl) {
    /**
     * The [RecurringRunnableExecutor] which will periodically call
     * [KeyframeRequester.run] and trigger their retry logic.
     */
    private val recurringRunnableExecutor = RecurringRunnableExecutor(RTCPFeedbackMessageSender::class.java.simpleName)

    /**
     * The keyframe requester. One per media source SSRC.
     */
    private val kfRequesters: ConcurrentMap<Long, KeyframeRequester> = ConcurrentHashMap()

    /**
     * Gets the synchronization source identifier (SSRC) to be used as SSRC of
     * packet sender in RTCP feedback message packets.
     *
     * @return the SSRC of packet sender
     */
    private val senderSSRC: Long
        get() {
            val ssrc = rtpTranslator.getLocalSSRC(null)
            return if (ssrc == Long.MAX_VALUE) -1 else ssrc
        }

    /**
     * Sends an RTCP Full Intra Request (FIR) or Picture Loss Indication (PLI),
     * to the media sender/source with a specific synchronization source
     * identifier (SSRC).
     * Whether to send a FIR or a PLI message is decided based on whether the
     * [MediaStream] associated with the SSRC supports FIR or PLI.
     *
     * @param mediaSenderSSRC the SSRC of the media sender/source
     * @return `true` if an RTCP message was sent; otherwise, `false`.
     */
    @Deprecated("Use the generic {@link #requestKeyframe(long)} instead.")
    fun sendFIR(mediaSenderSSRC: Int): Boolean {
        return requestKeyframe(mediaSenderSSRC.toLong() and 0xffffffffL)
    }

    /**
     * Sends an RTCP Full Intra Request (FIR) or Picture Loss Indication (PLI), to the media
     * sender/source with a specific synchronization source identifier (SSRC). Whether to send a
     * FIR or a PLI message is decided based on whether the
     * [MediaStream] associated with the SSRC supports FIR or PLI.
     *
     * @param mediaSenderSSRC the SSRC of the media sender/source
     * @return `true` if an RTCP message was sent; otherwise, `false`.
     */
    fun requestKeyframe(mediaSenderSSRC: Long): Boolean {
        var registerRecurringRunnable = false
        var keyframeRequester = kfRequesters[mediaSenderSSRC]
        if (keyframeRequester == null) {
            // Avoided repeated creation of unneeded objects until get fails.
            keyframeRequester = KeyframeRequester(mediaSenderSSRC)
            val existingKfRequester = kfRequesters.putIfAbsent(
                    mediaSenderSSRC, keyframeRequester)
            if (existingKfRequester != null) {
                // Another thread beat this one to putting a keyframe requester.
                // That other thread is responsible for registering the keyframe
                // requester with the recurring runnable executor.
                keyframeRequester = existingKfRequester
            } else {
                registerRecurringRunnable = true
            }
        }
        if (registerRecurringRunnable) {
            // TODO (2016-12-29) Think about eventually de-registering these
            // runnable, but note that with the current code this MUST NOT happen inside run()
            // because of concurrent modification of the executor's list.
            recurringRunnableExecutor.registerRecurringRunnable(keyframeRequester)
        }
        return keyframeRequester.maybeRequest(true)
    }

    /**
     * Sends an RTCP Full Intra Request (FIR) or Picture Loss Indication (PLI),
     * to media senders/sources with a specific synchronization source identifiers (SSRCs).
     * Whether to send a FIR or a PLI message is decided based on whether the
     * [MediaStream] associated with the SSRC supports FIR or PLI.
     *
     * @param mediaSenderSSRCs the SSRCs of the media senders/sources
     * @return `true` if an RTCP message was sent; otherwise,
     * `false`.
     */
    @Deprecated("Use the generic {@link #requestKeyframe(long[])} instead.")
    fun sendFIR(mediaSenderSSRCs: IntArray): Boolean {
        val ssrcsAsLong = LongArray(mediaSenderSSRCs.size)
        for (i in ssrcsAsLong.indices) {
            ssrcsAsLong[i] = mediaSenderSSRCs[i].toLong() and 0xffffffffL
        }
        return requestKeyframe(ssrcsAsLong)
    }

    /**
     * Sends an RTCP Full Intra Request (FIR) or Picture Loss Indication (PLI),
     * to media senders/sources with a specific synchronization source identifiers (SSRCs).
     * Whether to send a FIR or a PLI message is decided based on whether the
     * [MediaStream] associated with the SSRC supports FIR or PLI.
     *
     * @param mediaSenderSSRCs the SSRCs of the media senders/sources
     * @return `true` if an RTCP message was sent; otherwise, `false`.
     */
    private fun requestKeyframe(mediaSenderSSRCs: LongArray?): Boolean {
        if (mediaSenderSSRCs == null || mediaSenderSSRCs.isEmpty()) {
            return false
        }
        var requested = false
        for (mediaSenderSSRC in mediaSenderSSRCs) {
            if (requestKeyframe(mediaSenderSSRC)) {
                requested = true
            }
        }
        return requested
    }

    /**
     * Notifies this instance that an RTP packet has been received from a peer
     * represented by a specific `StreamRTPManagerDesc`.
     *
     * @param streamRTPManager a `StreamRTPManagerDesc` which identifies
     * the peer from which an RTP packet has been received
     * @param buf the buffer which contains the bytes of the received RTP or
     * RTCP packet
     * @param off the zero-based index in `buf` at which the bytes of the
     * received RTP or RTCP packet begin
     * @param len the number of bytes in `buf` beginning at `off`
     * which represent the received RTP or RTCP packet
     */
    fun maybeStopRequesting(streamRTPManager: StreamRTPManagerDesc?,
            ssrc: Long, buf: ByteArray, off: Int, len: Int) {
        val kfRequester = kfRequesters[ssrc]
        kfRequester?.maybeStopRequesting(streamRTPManager, buf, off, len)
    }

    /**
     * Releases the resources allocated by this instance in the course of its
     * execution and prepares it to be garbage collected.
     */
    fun dispose() {
        recurringRunnableExecutor.close()
    }

    /**
     * The `KeyframeRequester` is responsible for sending FIR requests to
     * a specific media sender identified by its SSRC.
     */
    internal inner class KeyframeRequester
    /**
     * Ctor.
     *
     * @param mediaSenderSSRC
     */
    (
            /**
             * The media sender SSRC of this `KeyframeRequester`
             */
            private val mediaSenderSSRC: Long) : PeriodicRunnable(FIR_RETRY_INTERVAL_MS.toLong()) {
        /**
         * The sequence number of the next FIR.
         */
        private val sequenceNumber = AtomicInteger(0)

        /**
         * The number of FIR that are left to be sent before stopping.
         */
        private var remainingRetries = 0

        /**
         * {@inheritDoc}
         */
        override fun run() {
            super.run()
            maybeRequest(false)
        }

        /**
         * Notifies this instance that an RTP packet has been received from a
         * peer represented by a specific `StreamRTPManagerDesc`.
         *
         * @param streamRTPManager a `StreamRTPManagerDesc` which
         * identifies the peer from which an RTP packet has been received
         * @param buf the buffer which contains the bytes of the received RTP or RTCP packet
         * @param off the zero-based index in `buf` at which the bytes of
         * the received RTP or RTCP packet begin
         * @param len the number of bytes in `buf` beginning at
         * `off` which represent the received RTP or RTCP packet
         */
        fun maybeStopRequesting(
                streamRTPManager: StreamRTPManagerDesc?, buf: ByteArray, off: Int, len: Int) {
            if (remainingRetries == 0) {
                return
            }
            if (!streamRTPManager!!.streamRTPManager.mediaStream.isKeyFrame(buf, off, len)) {
                return
            }
            Timber.log(TimberLog.FINER, "Stopping FIRs to ssrc = %s", mediaSenderSSRC)

            // This lock only runs while we're waiting for a key frame. It
            // should not slow things down significantly.
            synchronized(this) { remainingRetries = 0 }
        }

        /**
         * Sends an FIR RTCP message.
         *
         * @param allowResetRemainingRetries true if it's allowed to reset the remaining retries, false otherwise.
         */
        fun maybeRequest(allowResetRemainingRetries: Boolean): Boolean {
            synchronized(this) {
                if (allowResetRemainingRetries) {
                    remainingRetries = if (remainingRetries == 0) {
                        Timber.log(TimberLog.FINER, "Starting FIRs to ssrc = %s", mediaSenderSSRC)
                        FIR_MAX_RETRIES
                    } else {
                        // There's a pending FIR. Pretend that we're sending an FIR.
                        Timber.log(TimberLog.FINER, "Pending FIRs to ssrc = %s", mediaSenderSSRC)
                        return true
                    }
                } else if (remainingRetries == 0) {
                    return false
                }
                remainingRetries--
                Timber.i("Sending a FIR to ssrc = %s remainingRetries = %s", mediaSenderSSRC, remainingRetries)
            }

            val senderSSRC = senderSSRC
            if (senderSSRC == -1L) {
                Timber.w("Not sending an FIR because the sender SSRC is -1.")
                return false
            }

            val streamRTPManager = rtpTranslator.findStreamRTPManagerByReceiveSSRC(mediaSenderSSRC.toInt())
            if (streamRTPManager == null) {
                Timber.w("Not sending an FIR because the stream RTP manager is null.")
                return false
            }

            // TODO: Use only one of the RTCP packet implementations
            // (RTCPFeedbackMessagePacket or RTCPFBPacket)
            val request: RTCPFeedbackMessagePacket
            val videoMediaStream = streamRTPManager.mediaStream as VideoMediaStreamImpl

            // If the media sender supports both, we will send a PLI. If it
            // supports neither, we will also send a PLI to better handle the
            // case where signaling is inaccurate (e.g. missing), because all
            // currently known browsers support PLI.
            if (!videoMediaStream.supportsPli()
                    && videoMediaStream.supportsFir()) {
                request = RTCPFeedbackMessagePacket(
                        RTCPFeedbackMessageEvent.FMT_FIR,
                        RTCPFeedbackMessageEvent.PT_PS,
                        senderSSRC,
                        mediaSenderSSRC)
                request.setSequenceNumber(sequenceNumber.incrementAndGet())
            } else {
                request = RTCPFeedbackMessagePacket(
                        RTCPFeedbackMessageEvent.FMT_PLI,
                        RTCPFeedbackMessageEvent.PT_PS,
                        senderSSRC,
                        mediaSenderSSRC)
                if (!videoMediaStream.supportsPli()) {
                    Timber.w("Sending a PLI to a media sender for which PLI support hasn't been explicitly signaled.")
                }
            }
            return rtpTranslator.writeControlPayload(request, videoMediaStream)
        }
    }

    companion object {
        /**
         * The interval in milliseconds at which we re-send an FIR, if the previous
         * one was not satisfied.
         */
        private const val FIR_RETRY_INTERVAL_MS = 300

        /**
         * The maximum number of times to send a FIR.
         */
        private const val FIR_MAX_RETRIES = 10
    }
}