/*
 * Copyright @ 2017 Atlassian Pty Ltd
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

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.rtcp.NACKPacket
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.TransmissionFailedException
import org.atalk.util.RTPUtils
import org.atalk.util.TimeProvider
import org.atalk.util.concurrent.RecurringRunnable
import org.atalk.util.concurrent.RecurringRunnableExecutor
import org.atalk.util.logging.Logger
import timber.log.Timber
import java.io.IOException
import kotlin.math.max

/**
 * Detects lost RTP packets for a particular `RtpChannel` and requests
 * their retransmission by sending RTCP NACK packets.
 *
 * @author Boris Grozev
 * @author George Politis
 * @author bbaldino
 * @author Eng Chong Meng
 */
class RetransmissionRequesterDelegate
/**
 * Initializes a new `RetransmissionRequesterDelegate` for the given `RtpChannel`.
 *
 * @param stream the [MediaStream] that the instance belongs to.
 */
    (
        /**
         * The [MediaStream] that this instance belongs to.
         */
        private val stream: MediaStream,
        private val timeProvider: TimeProvider,
) : RecurringRunnable {
    /**
     * Maps an SSRC to the `Requester` instance corresponding to it.
     * TODO: purge these somehow (RTCP BYE? Timeout?)
     */
    private val requesters = HashMap<Long, Requester>()

    /**
     * The SSRC which will be used as Packet Sender SSRC in NACK packets sent
     * by this `RetransmissionRequesterDelegate`.
     */
    private var senderSsrc = -1L

    /**
     * A callback which allows this class to signal it has nack work that is ready to be run
     */
    private var workReadyCallback: Runnable? = null

    /**
     * Notify this requester that a packet has been received
     */
    fun packetReceived(ssrc: Long, seqNum: Int) {
        // TODO(gp) Don't NACK higher temporal layers.
        val requester = getOrCreateRequester(ssrc)
        // If the reception of this packet resulted in there being work that
        // is ready to be done now, fire the work ready callback
        if (requester!!.received(seqNum)) {
            if (workReadyCallback != null) {
                workReadyCallback!!.run()
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override val timeUntilNextRun: Long
        get() {
            val now = timeProvider.currentTimeMillis()
            val nextDueRequester = nextDueRequester
            return if (nextDueRequester == null) {
                WAKEUP_INTERVAL_MILLIS
            }
            else {
                Timber.log(TimberLog.FINER, hashCode().toString() + "%s: Next nack is scheduled for ssrc %s at %s. (current time is %s)",
                    nextDueRequester.ssrc, nextDueRequester.nextRequestAt.coerceAtLeast(0), now)
                max(nextDueRequester.nextRequestAt - now, 0)
            }
        }

    fun setWorkReadyCallback(workReadyCallback: Runnable?) {
        this.workReadyCallback = workReadyCallback
    }

    /**
     * {@inheritDoc}
     */
    override fun run() {
        val now = timeProvider.currentTimeMillis()
        Timber.log(TimberLog.FINER, "%s running at %s", hashCode(), now)
        val dueRequesters = getDueRequesters(now)
        Timber.log(TimberLog.FINER, "%s has %s due requesters", hashCode(), dueRequesters.size)
        if (dueRequesters.isNotEmpty()) {
            val nackPackets = createNackPackets(now, dueRequesters)
            Timber.log(TimberLog.FINER, "%s injecting %s nack packets", hashCode(), nackPackets.size)
            if (nackPackets.isNotEmpty()) {
                injectNackPackets(nackPackets)
            }
        }
    }

    private fun getOrCreateRequester(ssrc: Long): Requester? {
        var requester: Requester?
        synchronized(requesters) {
            requester = requesters[ssrc]
            if (requester == null) {
                Timber.d("Creating new Requester for SSRC %s", ssrc)
                requester = Requester(ssrc)
                requesters[ssrc] = requester!!
            }
        }
        return requester
    }

    private val nextDueRequester: Requester?
        get() {
            var nextDueRequester: Requester? = null
            synchronized(requesters) {
                for (requester in requesters.values) {
                    if (requester.nextRequestAt != -1L
                            && (nextDueRequester == null || requester.nextRequestAt < nextDueRequester!!.nextRequestAt)) {
                        nextDueRequester = requester
                    }
                }
            }
            return nextDueRequester
        }

    /**
     * Get a list of the requesters (not necessarily in sorted order) which are due to request as of the given time
     *
     * @param currentTime the current time
     * @return a list of the requesters (not necessarily in sorted order) which are due to request as of the given time
     */
    private fun getDueRequesters(currentTime: Long): List<Requester> {
        val dueRequesters = ArrayList<Requester>()
        synchronized(requesters) {
            for (requester in requesters.values) {
                if (requester.isDue(currentTime)) {
                    Timber.log(TimberLog.FINER, hashCode().toString() + "%s requester for ssrc %s has work due at %s(now = %s) and is missing packets: %s",
                        requester.ssrc, requester.nextRequestAt, currentTime, requester.missingSeqNums)
                    dueRequesters.add(requester)
                }
            }
        }
        return dueRequesters
    }

    /**
     * Inject the given nack packets into the outgoing stream
     *
     * @param nackPackets the nack packets to inject
     */
    private fun injectNackPackets(nackPackets: List<NACKPacket>) {
        for (nackPacket in nackPackets) {
            try {
                val packet = try {
                    nackPacket.toRawPacket()
                } catch (ioe: IOException) {
                    Timber.w(ioe, "Failed to create a NACK packet")
                    continue
                }

                Timber.log(TimberLog.FINER, "Sending a NACK: %s", nackPacket)
                stream.injectPacket(packet,  /* data */false,  /* after */null)
            } catch (e: TransmissionFailedException) {
                Timber.w(e.cause, "Failed to inject packet in MediaStream.")
            }
        }
    }

    /**
     * Gather the packets currently marked as missing and create NACKs for them
     *
     * @param dueRequesters the requesters which are due to have nack packets generated
     */
    private fun createNackPackets(now: Long, dueRequesters: List<Requester>): List<NACKPacket> {
        val packetsToRequest = HashMap<Long, Set<Int>>()
        for (dueRequester in dueRequesters) {
            synchronized(dueRequester) {
                val missingPackets = dueRequester.missingSeqNums
                if (missingPackets.isNotEmpty()) {
                    Timber.log(TimberLog.FINER, "%S Sending nack with packets %S for ssrc %S",
                        hashCode(), missingPackets, dueRequester.ssrc)
                    packetsToRequest[dueRequester.ssrc] = missingPackets
                    dueRequester.notifyNackCreated(now, missingPackets)
                }
            }
        }
        val nackPackets = ArrayList<NACKPacket>()
        for ((sourceSsrc, missingPackets) in packetsToRequest) {
            val nack = NACKPacket(senderSsrc, sourceSsrc, missingPackets)
            nackPackets.add(nack)
        }
        return nackPackets
    }

    /**
     * Handles packets for a single SSRC.
     */
    private inner class Requester
    /**
     * Initializes a new `Requester` instance for the given SSRC.
     */
        (
            /**
             * The SSRC for this instance.
             */
            val ssrc: Long,
    ) {
        /**
         * The highest received RTP sequence number.
         */
        private var lastReceivedSeq = -1

        /**
         * The time that the next request for this SSRC should be sent.
         */
        var nextRequestAt = -1L

        /**
         * The set of active requests for this SSRC. The keys are the sequence numbers.
         */
        private val requests = HashMap<Int, Request>()

        /**
         * Check if this [Requester] is due to send a nack
         *
         * @param currentTime the current time, in ms
         * @return true if this [Requester] is due to send a nack, false otherwise
         */
        fun isDue(currentTime: Long): Boolean {
            return nextRequestAt != -1L && nextRequestAt <= currentTime
        }

        /**
         * Handles a received RTP packet with a specific sequence number.
         *
         * @param seq the RTP sequence number of the received packet.
         * @return true if there is work for this requester ready to be done now, false otherwise
         */
        @Synchronized
        fun received(seq: Int): Boolean {
            if (lastReceivedSeq == -1) {
                lastReceivedSeq = seq
                return false
            }

            val diff = RTPUtils.getSequenceNumberDelta(seq, lastReceivedSeq)
            if (diff <= 0) {
                // An older packet, possibly already requested.
                val r = requests.remove(seq)
                if (requests.isEmpty()) {
                    nextRequestAt = -1
                }

                if (r != null) {
                    val rtt = stream.mediaStreamStats.sendStats.rtt
                    if (rtt > 0) {

                        // firstRequestSentAt is if we created a Request, but
                        // haven't yet sent a NACK. Assume a delta of 0 in that case.
                        val firstRequestSentAt = r.firstRequestSentAt
                        val delta = if (firstRequestSentAt > 0)
                            timeProvider.currentTimeMillis() - r.firstRequestSentAt
                        else 0
                        Timber.d("%s retr_received,stream = %d; delay = %d; rtt = %d",
                            Logger.Category.STATISTICS, stream.hashCode(), delta, rtt)
                    }
                }
            }
            else if (diff == 1) {
                // The very next packet, as expected.
                lastReceivedSeq = seq
            }
            else if (diff <= MAX_MISSING) {
                var missing = (lastReceivedSeq + 1) % (1 shl 16)
                while (missing != seq) {
                    val request = Request(missing)
                    requests[missing] = request
                    missing = (missing + 1) % (1 shl 16)
                }
                lastReceivedSeq = seq
                nextRequestAt = 0
                return true
            }
            else  // if (diff > MAX_MISSING)
            {
                // Too many packets missing. Reset.
                lastReceivedSeq = seq
                Timber.d("Resetting retransmission requester state. SSRC: %S, last received: %S, current: %S. Removing %S unsatisfied requests.",
                    ssrc, lastReceivedSeq, seq, requests.size)
                requests.clear()
                nextRequestAt = -1
            }
            return false
        }

        /**
         * Returns a set of RTP sequence numbers which are considered still MIA,
         * and for which a retransmission request needs to be sent.
         * Assumes that the returned set of sequence numbers will be requested
         * immediately and updates the state accordingly (i.e. increments the
         * timesRequested counters and sets the time of next request).
         *
         * @return a set of RTP sequence numbers which are considered still MIA,
         * and for which a retransmission request needs to be sent.
         */
        @get:Synchronized
        val missingSeqNums: Set<Int>
            get() = HashSet(requests.keys)

        /**
         * Notify this requester that a nack was sent at the given time
         *
         * @param time the time at which the nack was sent
         */
        @Synchronized
        fun notifyNackCreated(time: Long, sequenceNumbers: Collection<Int>) {
            for (seqNum in sequenceNumbers) {
                val request = requests[seqNum]
                request!!.timesRequested++
                if (request.timesRequested == MAX_REQUESTS) {
                    Timber.d("Generated the last NACK for SSRC = %S seq = %S. Time since the first request: %S",
                        ssrc, request.seq, time - request.firstRequestSentAt)
                    requests.remove(seqNum)
                    continue
                }
                if (request.timesRequested == 1) {
                    request.firstRequestSentAt = time
                }
            }
            nextRequestAt = if (requests.isNotEmpty())
                time + RE_REQUEST_AFTER_MILLIS
            else -1
        }
    }

    /**
     * Represents a request for the retransmission of a specific RTP packet.
     */
    private class Request
    /**
     * Initializes a new `Request` instance with the given RTP sequence number.
     *
     * @param seq the RTP sequence number.
     */
        (
            /**
             * The RTP sequence number.
             */
            val seq: Int,
    ) {
        /**
         * The system time at the moment a retransmission request for this
         * packet was first sent.
         */
        var firstRequestSentAt = -1L

        /**
         * The number of times that a retransmission request for this packet
         * has been sent.
         */
        var timesRequested = 0
    }

    /**
     * {@inheritDoc}
     */
    fun setSenderSsrc(ssrc: Long) {
        senderSsrc = ssrc
    }

    companion object {
        /**
         * If more than `MAX_MISSING` consecutive packets are lost, we will
         * not request retransmissions for them, but reset our state instead.
         */
        const val MAX_MISSING = 100

        /**
         * The maximum number of retransmission requests to be sent for a single RTP packet.
         */
        const val MAX_REQUESTS = 10

        /**
         * The interval after which another retransmission request will be sent
         * for a packet, unless it arrives. Ideally this should not be a constant,
         * but should be based on the RTT to the endpoint.
         */
        const val RE_REQUEST_AFTER_MILLIS = 150

        /**
         * The interval we'll ask the [RecurringRunnableExecutor] to check back
         * in if there is no current work
         * TODO(brian): i think we should actually be able to get rid of this and
         * just rely on scheduled work and the 'work ready now' callback
         */
        const val WAKEUP_INTERVAL_MILLIS = 1000L
    }
}