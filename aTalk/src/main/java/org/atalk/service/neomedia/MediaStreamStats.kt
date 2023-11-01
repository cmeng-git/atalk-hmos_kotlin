/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import org.atalk.service.neomedia.rtp.RTCPPacketListener
import org.atalk.service.neomedia.rtp.RTCPReports
import java.awt.Dimension

/**
 * Class used to compute stats concerning a MediaStream.
 *
 * @author Vincent Lucas
 * @author Lyubomir Marinov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
interface MediaStreamStats {
    /**
     * Returns the jitter average of this download stream.
     *
     * @return the last jitter average computed (in ms).
     */
    @get:Deprecated("use the appropriate method from {@link MediaStreamStats2} instead.")
    val downloadJitterMs: Double

    /**
     * Returns the percent loss of the download stream.
     *
     * @return the last loss rate computed (in %).
     */
    @get:Deprecated("use the appropriate method from {@link MediaStreamStats2} instead.")
    val downloadPercentLoss: Double

    /**
     * Returns the bandwidth used by this download stream.
     *
     * @return the last used download bandwidth computed (in Kbit/s).
     */
    @get:Deprecated("use the appropriate method from {@link MediaStreamStats2} instead.")
    val downloadRateKiloBitPerSec: Double

    /**
     * Returns the download video size if this stream downloads a video, or `null` if not.
     *
     * @return the download video size if this stream downloads a video, or `null` if not.
     */
    val downloadVideoSize: Dimension?

    /**
     * Returns the `MediaStream` encoding.
     *
     * @return the encoding used by the stream.
     */
    val encoding: String?

    /**
     * Returns the `MediaStream` encoding rate (in Hz).
     *
     * @return the encoding rate used by the stream.
     */
    val encodingClockRate: String?

    /**
     * Returns the delay in milliseconds introduced by the jitter buffer.
     *
     * @return the delay in milliseconds introduced by the jitter buffer
     */
    val jitterBufferDelayMs: Int

    /**
     * Returns the delay in number of packets introduced by the jitter buffer.
     *
     * @return the delay in number of packets introduced by the jitter buffer
     */
    val jitterBufferDelayPackets: Int

    /**
     * Returns the local IP address of the `MediaStream`.
     *
     * @return the local IP address of the stream.
     */
    val localIPAddress: String?

    /**
     * Returns the local port of the `MediaStream`.
     *
     * @return the local port of the stream.
     */
    val localPort: Int

    /**
     * Returns the number of received bytes since the beginning of the session.
     *
     * @return the number of received bytes for this stream.
     */
    @get:Deprecated("use the appropriate method from {@link MediaStreamStats2} instead.")
    val nbReceivedBytes: Long

    /**
     * Returns the number of sent bytes since the beginning of the session.
     *
     * @return the number of sent bytes for this stream.
     */
    @get:Deprecated("use the appropriate method from {@link MediaStreamStats2} instead.")
    val nbSentBytes: Long

    /**
     * Returns the total number of discarded packets since the beginning of the session.
     *
     * @return the total number of discarded packets since the beginning of the session.
     */
    val nbDiscarded: Long

    /**
     * Returns the number of packets discarded since the beginning of the session, because the
     * packet queue was full.
     *
     * @return the number of packets discarded since the beginning of the session, because the
     * packet queue was full.
     */
    val nbDiscardedFull: Int

    /**
     * Returns the number of packets discarded since the beginning of the session, because they
     * were late.
     *
     * @return the number of packets discarded since the beginning of the session, because they
     * were late.
     */
    val nbDiscardedLate: Int

    /**
     * Returns the number of packets discarded since the beginning of the session, because the
     * packet queue was reset.
     *
     * @return the number of packets discarded since the beginning of the session, because the
     * packet queue was reset.
     */
    val nbDiscardedReset: Int

    /**
     * Returns the number of packets discarded since the beginning of the session, while the packet
     * queue was shrinking.
     *
     * @return the number of packets discarded since the beginning of the session, while the packet
     * queue was shrinking.
     */
    val nbDiscardedShrink: Int

    /**
     * Returns the number of packets for which FEC data was decoded.
     *
     * @return the number of packets for which FEC data was decoded
     */
    val nbFec: Long

    /**
     * Returns the total number of packets that are send or receive for this
     * stream since the stream is created.
     *
     * @return the total number of packets.
     */
    @get:Deprecated("use the appropriate method from {@link MediaStreamStats2} instead.")
    val nbPackets: Long

    /**
     * Returns the number of lost packets for that stream.
     *
     * @return the number of lost packets.
     */
    @get:Deprecated("use the appropriate method from {@link MediaStreamStats2} instead.")
    val nbPacketsLost: Long

    /**
     * Returns the number of packets currently in the packet queue.
     *
     * @return the number of packets currently in the packet queue.
     */
    val packetQueueCountPackets: Int

    /**
     * Returns the current size of the packet queue.
     *
     * @return the current size of the packet queue.
     */
    val packetQueueSize: Int

    /**
     * Returns the current percent of discarded packets.
     *
     * @return the current percent of discarded packets.
     */
    val percentDiscarded: Double

    /**
     * Returns the remote IP address of the `MediaStream`.
     *
     * @return the remote IP address of the stream.
     */
    val remoteIPAddress: String?

    /**
     * Returns the remote port of the `MediaStream`.
     *
     * @return the remote port of the stream.
     */
    val remotePort: Int

    /**
     * Gets the detailed statistics about the RTCP reports sent and received by the associated
     * local peer.
     *
     * @return the detailed statistics about the RTCP reports sent and received by the associated
     * local peer
     */
    val rtcpReports: RTCPReports?

    /**
     * Returns the RTT computed with the RTCP feedback (cf. RFC3550, section 6.4.1, subsection
     * "delay since last SR (DLSR): 32 bits").
     *
     * @return The RTT computed with the RTCP feedback. Returns `-1` if
     * the RTT has not been computed yet. Otherwise the RTT in ms.
     */
    @get:Deprecated("use the appropriate method from {@link MediaStreamStats2} instead.")
    val rttMs: Long

    /**
     * Returns the jitter average of this upload stream.
     *
     * @return the last jitter average computed (in ms).
     */
    @get:Deprecated("use the appropriate method from {@link MediaStreamStats2} instead.")
    val uploadJitterMs: Double

    /**
     * Returns the percent loss of the upload stream.
     *
     * @return the last loss rate computed (in %).
     */
    @get:Deprecated("use the appropriate method from {@link MediaStreamStats2} instead.")
    val uploadPercentLoss: Double

    /**
     * Returns the bandwidth used by this download stream.
     *
     * @return the last used upload bandwidth computed (in Kbit/s).
     */
    @get:Deprecated("use the appropriate method from {@link MediaStreamStats2} instead.")
    val uploadRateKiloBitPerSec: Double

    /**
     * Returns the upload video size if this stream uploads a video, or `null` if not.
     *
     * @return the upload video size if this stream uploads a video, or `null` if not.
     */
    val uploadVideoSize: Dimension?

    /**
     * Checks whether there is an adaptive jitter buffer enabled for at least one of the
     * `ReceiveStream`s of the `MediaStreamImpl`.
     *
     * @return `true` if there is an adaptive jitter buffer enabled for at least one of the
     * `ReceiveStream`s of the `MediaStreamImpl`; otherwise, `false`
     */
    val isAdaptiveBufferEnabled: Boolean

    /**
     * Computes and updates information for a specific stream.
     */
    fun updateStats()

    /**
     * Gets the minimum RTP jitter value reported by us in an RTCP report, in milliseconds. Returns
     * -1D if the value is unknown.
     *
     * @return the minimum RTP jitter value reported by us in an RTCP report, in milliseconds.
     */
    val minDownloadJitterMs: Double

    /**
     * Gets the maximum RTP jitter value reported by us in an RTCP report, in milliseconds. Returns
     * -1D if the value is unknown.
     *
     * @return the maximum RTP jitter value reported by us in an RTCP report, in milliseconds.
     */
    val maxDownloadJitterMs: Double

    /**
     * Gets the average of the RTP jitter values reported to us in RTCP reports, in milliseconds.
     * Returns -1D if the value is unknown.
     *
     * @return the average of the RTP jitter values reported to us in RTCP reports, in
     * milliseconds.
     * Returns -1D if the value is unknown.
     */
    val avgDownloadJitterMs: Double

    /**
     * Gets the minimum RTP jitter value reported to us in an RTCP report, in milliseconds. Returns
     * -1D if the value is unknown.
     *
     * @return the minimum RTP jitter value reported to us in an RTCP report, in milliseconds.
     */
    val minUploadJitterMs: Double

    /**
     * Gets the maximum RTP jitter value reported to us in an RTCP report, in milliseconds. Returns
     * -1D if the value is unknown.
     *
     * @return the maximum RTP jitter value reported to us in an RTCP report, in milliseconds.
     */
    val maxUploadJitterMs: Double

    /**
     * Gets the average of the RTP jitter values reported to us in RTCP reports, in milliseconds.
     * Returns -1D if the value is unknown.
     *
     * @return the average of the RTP jitter values reported to us in RTCP reports, in
     * milliseconds.
     * Returns -1D if the value is unknown.
     */
    val avgUploadJitterMs: Double

    /**
     * Returns the number of packets sent since the beginning of the session.
     *
     * @return the number of packets sent since the beginning of the session.
     */
    @get:Deprecated("use the appropriate method from {@link MediaStreamStats2} instead.")
    val nbPacketsSent: Long

    /**
     * Returns the number of packets received since the beginning of the session.
     *
     * @return the number of packets received since the beginning of the session.
     */
    @get:Deprecated("use the appropriate method from {@link MediaStreamStats2} instead.")
    val nbPacketsReceived: Long

    /**
     * Returns the number of RTP packets sent by the remote side, but not received by us.
     *
     * @return the number of RTP packets sent by the remote side, but not received by us.
     */
    @get:Deprecated("use the appropriate method from {@link MediaStreamStats2} instead.")
    val downloadNbPacketLost: Long

    /**
     * Returns the number of RTP packets sent by us, but not received by the remote side.
     *
     * @return the number of RTP packets sent by us, but not received by the remote side.
     */
    @get:Deprecated("use the appropriate method from {@link MediaStreamStats2} instead.")
    val uploadNbPacketLost: Long

    /**
     * Adds a listener which will be notified when NACK packets are received.
     *
     * listener
     * the listener.
     */
    fun addRTCPPacketListener(listener: RTCPPacketListener)

    /**
     * Adds a listener which will be notified when REMB packets are received.
     *
     * listener
     * the listener.
     */
    fun removeRTCPPacketListener(listener: RTCPPacketListener)

    /**
     * Gets the rate at which we are currently sending data to the remote endpoint in bits per
     * second. This is almost the same as [.getUploadRateKiloBitPerSec]. The duplication
     * is necessary, because of implementation details.
     *
     * @return the rate at which we are currently sending data to the remote endpoint, in bits
     * per second.
     */
    @get:Deprecated("use the appropriate method from {@link MediaStreamStats2} instead.")
    val sendingBitrate: Long
}