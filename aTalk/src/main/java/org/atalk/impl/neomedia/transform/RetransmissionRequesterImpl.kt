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
package org.atalk.impl.neomedia.transform

import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.RawPacket
import org.atalk.service.neomedia.RetransmissionRequester
import org.atalk.service.neomedia.codec.Constants
import org.atalk.util.TimeProvider
import org.atalk.util.concurrent.RecurringRunnableExecutor
import timber.log.Timber

/**
 * Detects lost RTP packets for a particular `RtpChannel` and requests their retransmission
 * by sending RTCP NACK packets.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class RetransmissionRequesterImpl(
        /**
         * The [MediaStream] that this instance belongs to.
         */
        private val stream: MediaStream) : SinglePacketTransformerAdapter(), TransformEngine, RetransmissionRequester {
    /**
     * Whether this [RetransmissionRequester] is enabled or not.
     */
    private var enabled = true

    /**
     * Whether this `PacketTransformer` has been closed.
     */
    private var closed = false

    /**
     * The delegate for this [RetransmissionRequesterImpl] which handles
     * the main logic for determining when to send nacks
     */
    private val retransmissionRequesterDelegate = RetransmissionRequesterDelegate(stream, TimeProvider())

    /**
     * Initializes a new `RetransmissionRequester` for the given `RtpChannel`.
     *
     * stream the MediaStream that the instance belongs to.
     */
    init {
        recurringRunnableExecutor.registerRecurringRunnable(retransmissionRequesterDelegate)
        retransmissionRequesterDelegate.setWorkReadyCallback { recurringRunnableExecutor.startOrNotifyThread() }
    }

    /**
     * {@inheritDoc}
     *
     * Implements [SinglePacketTransformer.reverseTransform].
     */
    override fun reverseTransform(pkt: RawPacket): RawPacket {
        if (enabled && !closed) {
            val ssrc: Long?
            val seq: Int
            val format = stream.getFormat(pkt.payloadType)
            if (format == null) {
                ssrc = null
                seq = -1
                Timber.w("format_not_found, stream_hash = %s", stream.hashCode())
            } else if (Constants.RTX.equals(format.encoding, ignoreCase = true)) {
                val receiver = stream.mediaStreamTrackReceiver!!
                val encoding = receiver.findRTPEncodingDesc(pkt)
                if (encoding != null) {
                    ssrc = encoding.primarySSRC
                    seq = pkt.originalSequenceNumber
                } else {
                    ssrc = null
                    seq = -1
                    Timber.w("encoding_not_found, stream_hash = %s", stream.hashCode())
                }
            } else {
                ssrc = pkt.getSSRCAsLong()
                seq = pkt.sequenceNumber
            }
            if (ssrc != null) {
                retransmissionRequesterDelegate.packetReceived(ssrc, seq)
            }
        }
        return pkt
    }

    /**
     * {@inheritDoc}
     */
    override fun close() {
        closed = true
        recurringRunnableExecutor.deRegisterRecurringRunnable(retransmissionRequesterDelegate)
    }
    // TransformEngine methods
    /**
     * {@inheritDoc}
     */
    override val rtpTransformer: PacketTransformer
        get() = this

    /**
     * Implements [TransformEngine.rtcpTransformer].
     */
    override val rtcpTransformer: PacketTransformer?
        get() = null

    // RetransmissionRequester methods
    /**
     * {@inheritDoc}
     */
    override fun enable(enable: Boolean) {
        enabled = enable
    }

    /**
     * {@inheritDoc}
     */
    override fun setSenderSsrc(ssrc: Long) {
        retransmissionRequesterDelegate.setSenderSsrc(ssrc)
    }

    companion object {
        /**
         * Create a single executor to service the nack processing for all the
         * [RetransmissionRequesterImpl] instances
         */
        private val recurringRunnableExecutor = RecurringRunnableExecutor(RetransmissionRequesterImpl::class.java.simpleName)
    }
}