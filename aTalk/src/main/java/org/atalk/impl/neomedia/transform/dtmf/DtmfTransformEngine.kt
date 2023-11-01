/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.dtmf

import net.sf.fmj.media.rtp.*
import org.atalk.impl.neomedia.AudioMediaStreamImpl
import org.atalk.impl.neomedia.transform.*
import org.atalk.service.neomedia.DTMFRtpTone
import org.atalk.service.neomedia.RawPacket
import org.atalk.service.neomedia.codec.Constants
import org.atalk.util.MediaType
import timber.log.Timber
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.media.Format
import kotlin.math.max

/**
 * The class is responsible for sending DTMF tones in an RTP audio stream as described by RFC4733.
 *
 * @author Emil Ivov
 * @author Romain Philibert
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class DtmfTransformEngine
/**
 * Creates an engine instance that will be replacing audio packets with DTMF ones upon request.
 *
 * stream the `AudioMediaStream` whose RTP packets we are going to be replacing with DTMF.
 */
(
        /**
         * The `AudioMediaStreamImpl` that this transform engine was created by and that it's
         * going to deliver DTMF packets for.
         */
        private val mediaStream: AudioMediaStreamImpl) : SinglePacketTransformer(), TransformEngine {
    /**
     * The enumeration contains a set of states that reflect the progress of
     */
    private enum class ToneTransmissionState {
        /**
         * Indicates that this engine is not currently sending DTMF.
         */
        IDLE,

        /**
         * Indicates that we are currently in the process of sending a DTMF tone, and we have
         * already sent at least one packet.
         */
        SENDING,

        /**
         * Indicates that the user has requested that DTMF transmission be stopped but we haven't
         * acted upon that request yet (i.e. we have yet to send a single retransmission)
         */
        END_REQUESTED,

        /**
         * Indicates that the user has requested that DTMF transmission be stopped we have already
         * sent a retransmission of the final packet.
         */
        END_SEQUENCE_INITIATED
    }

    /**
     * The dispatcher that is delivering tones to the media steam.
     */
    private var dtmfDispatcher: DTMFDispatcher? = null

    /**
     * The status that this engine is currently in.
     */
    private var toneTransmissionState = ToneTransmissionState.IDLE

    /**
     * The tone that we are supposed to be currently transmitting.
     */
    private val currentTone = Vector<DTMFRtpTone>(1, 1)

    /**
     * The number of tone for which we have received a stop request. This is used to signal that
     * stop is already received for "currentTone" not yet sent.
     */
    private var nbToneToStop = 0

    /**
     * A mutex used to control the start and the stop of a tone and thereby to control concurrent
     * modification access to "currentTone", "nbToneToStop" and "toneTransmissionState".
     */
    private val startStopToneMutex = Any()

    /**
     * The duration (in timestamp units or in other words ms*8) that we have transmitted the
     * current tone for.
     */
    private var currentDuration = 0

    /**
     * The current transmitting timestamp.
     */
    private var currentTimestamp: Long = 0

    /**
     * We send 3 end packets and this is the counter of remaining packets.
     */
    private var remainingsEndPackets = 0// the default is 50 ms. RECOMMENDED in rfc4733.
    /**
     * Gets the current duration of every event we send.
     *
     * @return the current duration of every event we send
     */
    /**
     * Current duration of every event we send.
     */
    private var currentSpacingDuration = Format.NOT_SPECIFIED
        get() {
            if (field == Format.NOT_SPECIFIED) {
                val format = mediaStream.format
                val clockRate = if (format == null) {
                    val mediaType = mediaStream.mediaType
                    if (MediaType.VIDEO == mediaType) 90000.0
                    else -1.0
                } else format.clockRate

                // the default is 50 ms. RECOMMENDED in rfc4733.
                if (clockRate > 0) field = clockRate.toInt() / 50
            }
            return field
        }

    /**
     * Tells if the current tone has been sent for at least the minimal duration.
     */
    private var lastMinimalDuration = false

    /**
     * The minimal DTMF tone duration. The default value is `560` corresponding to 70 ms.
     * This can be changed by using the "neomedia.transform.dtmf.minimalToneDuration" property.
     */
    private var minimalToneDuration = 0

    /**
     * The maximal DTMF tone duration. The default value is -1 telling to stop only when the user
     * asks to. This can be changed by using the "neomedia.transform.dtmf.maximalToneDuration"
     * property.
     */
    private var maximalToneDuration = 0

    /**
     * The DTMF tone volume.
     */
    private var volume = 0

    /**
     * Close the transformer and underlying transform engine.
     *
     *
     * Nothing to do here.
     */
    override fun close() {}

    /**
     * Always returns `null` since this engine does not require any RTCP transformations.
     *
     * @return `null` since this engine does not require any RTCP transformations.
     */
    override val rtcpTransformer: PacketTransformer?
        get() = null

    /**
     * Returns a reference to this class since it is performing RTP transformations in here.
     *
     * @return a reference to `this` instance of the `DtmfTransformEngine`.
     */
    override val rtpTransformer: PacketTransformer
        get() = this

    /**
     * A stub meant to handle incoming DTMF packets.
     *
     * @param pkt an incoming packet that we need to parse and handle in case we determine it to be DTMF.
     * @return the `pkt` if it is not a DTMF tone and `null` otherwise since we will
     * be handling the packet ourselves and their's no point in feeding it to the application.
     */
    override fun reverseTransform(pkt: RawPacket): RawPacket? {
        val currentDtmfPayload = mediaStream.getDynamicRTPPayloadType(Constants.TELEPHONE_EVENT)
        if (currentDtmfPayload == pkt!!.payloadType) {
            val p = DtmfRawPacket(pkt)
            if (dtmfDispatcher == null) {
                dtmfDispatcher = DTMFDispatcher()
                Thread(dtmfDispatcher).start()
            }
            dtmfDispatcher!!.addTonePacket(p)

            // ignore received dtmf packets if jmf receive change in rtp payload stops reception
            return null
        }
        return pkt
    }

    /**
     * Replaces `pkt` with a DTMF packet if this engine is in a DTMF transmission mode or
     * returns it unchanged otherwise.
     *
     * @param pkt the audio packet that we may want to replace with a DTMF one.
     * @return `pkt` with a DTMF packet if this engine is in a DTMF transmission mode or
     * returns it unchanged otherwise.
     */
    override fun transform(pkt: RawPacket): RawPacket {
        var packet = pkt
        if (currentTone.isEmpty() || packet.version != RTPHeader.VERSION) {
            return packet
        }
        val toneCode = currentTone.firstElement().code
        val currentDtmfPayload = mediaStream.getDynamicRTPPayloadType(Constants.TELEPHONE_EVENT)
        check(currentDtmfPayload.toInt() != -1) {
            ("Can't send DTMF when no payload "
                    + "type has been negotiated for DTMF events.")
        }
        val dtmfPkt = DtmfRawPacket(packet.buffer, packet.offset,
                packet.length, currentDtmfPayload)
        val audioPacketTimestamp = dtmfPkt.timestamp
        var pktEnd = false
        var pktMarker = false
        var pktDuration = 0
        checkIfCurrentToneMustBeStopped()
        if (toneTransmissionState == ToneTransmissionState.IDLE) {
            lastMinimalDuration = false
            currentDuration = 0
            currentDuration += currentSpacingDuration
            pktDuration = currentDuration
            pktMarker = true
            currentTimestamp = audioPacketTimestamp
            synchronized(startStopToneMutex) { toneTransmissionState = ToneTransmissionState.SENDING }
        } else if (toneTransmissionState == ToneTransmissionState.SENDING
                || (toneTransmissionState == ToneTransmissionState.END_REQUESTED
                        && !lastMinimalDuration)) {
            currentDuration += currentSpacingDuration
            pktDuration = currentDuration
            if (currentDuration > minimalToneDuration) {
                lastMinimalDuration = true
            }
            if (maximalToneDuration != -1 && currentDuration > maximalToneDuration) {
                toneTransmissionState = ToneTransmissionState.END_REQUESTED
            }
            // Check for long state event
            if (currentDuration > 0xFFFF) {
                // When duration > 0xFFFF we first send a packet with duration =
                // 0xFFFF. For the next packet, the duration start from beginning
                // but the audioPacketTimestamp is set to the time when the long
                // duration event occurs.
                pktDuration = 0xFFFF
                currentDuration = 0
                currentTimestamp = audioPacketTimestamp
            }
        } else if (toneTransmissionState == ToneTransmissionState.END_REQUESTED) {
            // The first ending packet do have the End flag set.
            // But the 2 next will have the End flag set.
            //
            // The audioPacketTimestamp and the duration field stay unchanged
            // for the 3 last packets
            currentDuration += currentSpacingDuration
            pktDuration = currentDuration
            pktEnd = true
            remainingsEndPackets = 2
            synchronized(startStopToneMutex) { toneTransmissionState = ToneTransmissionState.END_SEQUENCE_INITIATED }
        } else if (toneTransmissionState == ToneTransmissionState.END_SEQUENCE_INITIATED) {
            pktEnd = true
            pktDuration = currentDuration
            remainingsEndPackets--
            if (remainingsEndPackets == 0) {
                synchronized(startStopToneMutex) {
                    toneTransmissionState = ToneTransmissionState.IDLE
                    currentTone.removeAt(0)
                }
            }
        }
        dtmfPkt.init(toneCode.toInt(), pktEnd, pktMarker, pktDuration, currentTimestamp, volume)
        packet = dtmfPkt
        return packet
    }

    /**
     * DTMF sending stub: this is where we should set the transformer in the proper state so
     * that it would start replacing packets with dtmf codes.
     *
     * @param tone the tone that we'd like to start sending.
     * @param minimalToneDuration The minimal DTMF tone duration.
     * @param maximalToneDuration The maximal DTMF tone duration.
     * @param volume The DTMF tone volume.
     */
    fun startSending(tone: DTMFRtpTone, minimalToneDuration: Int, maximalToneDuration: Int, volume: Int) {
        synchronized(startStopToneMutex) {

            // If the GUI throws several start and only one stop (i.e. when
            // holding a key pressed on Windows), then check that we have the
            // good number of tone to stop.
            stopSendingDTMF()
            currentTone.add(tone)
        }
        // Converts duration in ms into duration in timestamp units (here the
        // codec of telephone-event is 8000 Hz).
        this.minimalToneDuration = minimalToneDuration * 8
        this.maximalToneDuration = maximalToneDuration * 8
        if (maximalToneDuration == -1) {
            this.maximalToneDuration = -1
        } else if (this.maximalToneDuration < this.minimalToneDuration) {
            this.maximalToneDuration = this.minimalToneDuration
        }

        // we used to sent 0 for this field, keep it that way if not set.
        this.volume = max(volume, 0)
    }

    /**
     * Interrupts transmission of a `DTMFRtpTone` started with the
     * `startSendingDTMF()` method. Has no effect if no tone is currently being sent.
     *
     * @see AudioMediaStream.stopSendingDTMF
     */
    fun stopSendingDTMF() {
        synchronized(startStopToneMutex) {

            // Check if there is currently one tone in a stopping state.
            val stoppingTone = if (toneTransmissionState == ToneTransmissionState.END_REQUESTED
                    || toneTransmissionState == ToneTransmissionState.END_SEQUENCE_INITIATED) 1 else 0

            // Verify that the number of tone to stop does not exceed the number
            // of waiting or sending tones.
            if (currentTone.size > nbToneToStop + stoppingTone) {
                ++nbToneToStop
            }
        }
    }

    /**
     * Stops threads that this transform engine is using for even delivery.
     */
    fun stop() {
        if (dtmfDispatcher != null) dtmfDispatcher!!.stop()
    }

    /**
     * Changes the current tone state, and requests to stop it if necessary.
     */
    private fun checkIfCurrentToneMustBeStopped() {
        synchronized(startStopToneMutex) {
            if (nbToneToStop > 0 && toneTransmissionState == ToneTransmissionState.SENDING) {
                --nbToneToStop
                toneTransmissionState = ToneTransmissionState.END_REQUESTED
            }
        }
    }

    /**
     * A simple thread that waits for new tones to be reported from incoming RTP packets and then
     * delivers them to the `AudioMediaStream` associated with this engine. The reason we
     * need to do this in a separate thread is of course the time sensitive nature of incoming RTP
     * packets.
     */
    private inner class DTMFDispatcher : Runnable {
        /**
         * Indicates whether this thread is supposed to be running
         */
        private var isRunning = false

        /**
         * The tone that we last reported to the listener
         */
        private var lastReportedTone: DTMFRtpTone? = null

        /**
         * Timestamp the last reported tone start; used to identify starts
         * when the marker bit is lost
         */
        private var lastReportedStart: Long = 0

        /**
         * Timestamp the last reported tone end; used to prevent duplicates
         */
        private var lastReportedEnd: Long = 0

        /**
         * The maximum number of DTMF events that are queued for processing
         */
        private val QUEUE_SIZE = 100

        /**
         * The queue of `DtmfRawPacket`s pending processing
         */
        private val queue = LinkedBlockingQueue<DtmfRawPacket>(QUEUE_SIZE)

        /**
         * Waits for new tone events to be reported via the
         * `addTonePacket()` method and delivers them as start / end
         * events to the `AudioMediaStream` that we are associated with.
         */
        override fun run() {
            isRunning = true
            while (isRunning) {
                var tone: DTMFRtpTone?

                val pkt: DtmfRawPacket? = try {
                    queue.poll(500, TimeUnit.MILLISECONDS)
                } catch (iex: InterruptedException) {
                    continue
                }

                // The current thread has potentially waited.
                if (!isRunning) {
                    break
                }
                if (pkt == null) {
                    continue
                }
                tone = getToneFromPacket(pkt)

                /*
                 * Detect DTMF tone start by looking for new tones
                 * It doesn't make sense to look at the 'marked' flag as those
                 * packets may be re-sent multiple times if they also contain
                 * the 'end' bit.
                 */
                if (lastReportedTone == null
                        && pkt.timestamp != lastReportedStart) {
                    Timber.d("Delivering DTMF tone start: %s", tone!!.value)
                    // now notify our listener
                    mediaStream.fireDTMFEvent(tone, false)
                    lastReportedStart = pkt.timestamp
                    lastReportedTone = tone
                }

                /*
                 * Detect DTMF tone end via the explicit 'end' flag.
                 * End packets are repeated for redundancy. To filter out
                 * duplicates, we track them by their timestamp.
                 * Start and end may be present in the same packet, typically
                 * for durations below 120 ms.
                 */
                if (pkt.isEnd && pkt.timestamp != lastReportedEnd && tone === lastReportedTone) {
                    Timber.d("Delivering DTMF tone end: %s", tone!!.value)
                    // now notify our listener
                    mediaStream.fireDTMFEvent(tone, true)
                    lastReportedEnd = pkt.timestamp
                    lastReportedTone = null
                }
            }
        }

        /**
         * A packet that we should convert to tone and deliver to our media stream and its
         * listeners in a separate thread.
         *
         * @param p the packet we will convert and deliver.
         */
        fun addTonePacket(p: DtmfRawPacket) {
            queue.offer(p.clone() as DtmfRawPacket)
        }

        /**
         * Causes our run method to exit so that this thread would stop handling levels.
         */
        fun stop() {
            isRunning = false
            queue.clear()
        }

        /**
         * Maps DTMF packet codes to our DTMFRtpTone objects.
         *
         * @param p the packet
         * @return the corresponding tone.
         */
        private fun getToneFromPacket(p: DtmfRawPacket): DTMFRtpTone? {
            for (i in supportedTones.indices) {
                val t = supportedTones[i]
                if (t.code.toInt() == p.code) return t
            }
            return null
        }
    }

    companion object {
        /**
         * Array of all supported tones.
         */
        private val supportedTones = arrayOf(DTMFRtpTone.DTMF_0,
                DTMFRtpTone.DTMF_1, DTMFRtpTone.DTMF_2, DTMFRtpTone.DTMF_3, DTMFRtpTone.DTMF_4,
                DTMFRtpTone.DTMF_5, DTMFRtpTone.DTMF_6, DTMFRtpTone.DTMF_7, DTMFRtpTone.DTMF_8,
                DTMFRtpTone.DTMF_9, DTMFRtpTone.DTMF_A, DTMFRtpTone.DTMF_B, DTMFRtpTone.DTMF_C,
                DTMFRtpTone.DTMF_D, DTMFRtpTone.DTMF_SHARP, DTMFRtpTone.DTMF_STAR)
    }
}