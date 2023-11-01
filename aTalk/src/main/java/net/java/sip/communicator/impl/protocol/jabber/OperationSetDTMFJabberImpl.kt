/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.media.AbstractOperationSetDTMF
import org.atalk.service.neomedia.AudioMediaStream
import org.atalk.service.neomedia.DTMFMethod
import org.atalk.service.neomedia.DTMFTone
import org.atalk.util.MediaType
import timber.log.Timber

/**
 * Class responsible for sending a DTMF Tone using using rfc4733 or Inband.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class OperationSetDTMFJabberImpl
/**
 * Constructor.
 *
 * @param pps the Jabber Protocol provider service
 */
(pps: ProtocolProviderServiceJabberImpl?) : AbstractOperationSetDTMF(pps!!) {
    /**
     * Sends the `DTMFTone` `tone` to `callPeer`.
     *
     * @param callPeer the call peer to send `tone` to.
     * @param tone the DTMF tone to send to `callPeer`.
     * @throws OperationFailedException with code OPERATION_NOT_SUPPORTED if DTMF tones are not supported for
     * `callPeer`.
     * @throws NullPointerException if one of the arguments is null.
     * @throws IllegalArgumentException in case the call peer does not belong to the underlying implementation.
     */
    @Synchronized
    @Throws(OperationFailedException::class)
    override fun startSendingDTMF(callPeer: CallPeer?, tone: DTMFTone?) {
        if (callPeer == null || tone == null) {
            throw NullPointerException("Argument is null")
        }
        require(callPeer is CallPeerJabberImpl)
        val cp = callPeer
        var cpDTMFMethod = dtmfMethod

        // If "auto" DTMF mode selected, automatically selects RTP DTMF is
        // telephone-event is available. Otherwise selects INBAND DTMF.
        if (dtmfMethod === DTMFMethod.AUTO_DTMF) {
            cpDTMFMethod = if (isRFC4733Active(cp)) {
                DTMFMethod.RTP_DTMF
            } else {
                DTMFMethod.INBAND_DTMF
            }
        }

        // If the account is configured to use RTP DTMF method and the call
        // does not manage telephone events. Then, we log it for future debugging.
        if (dtmfMethod === DTMFMethod.RTP_DTMF && !isRFC4733Active(cp)) {
            Timber.d("RTP DTMF used without telephone-event capacities")
        }
        (cp.getMediaHandler()!!.getStream(MediaType.AUDIO) as AudioMediaStream?)!!.startSendingDTMF(tone,
                cpDTMFMethod, minimalToneDuration, maximalToneDuration, volume)
    }

    /**
     * Stops sending DTMF.
     *
     * @param callPeer the call peer that we'd like to stop sending DTMF to.
     */
    @Synchronized
    override fun stopSendingDTMF(callPeer: CallPeer?) {
        if (callPeer == null) {
            throw NullPointerException("Argument is null")
        }
        require(callPeer is CallPeerJabberImpl)
        val cp = callPeer
        var cpDTMFMethod = dtmfMethod

        // If "auto" DTMF mode selected, automatically selects RTP DTMF is
        // telephon-event is available. Otherwise selects INBAND DMTF.
        if (dtmfMethod === DTMFMethod.AUTO_DTMF) {
            cpDTMFMethod = if (isRFC4733Active(cp)) {
                DTMFMethod.RTP_DTMF
            } else {
                DTMFMethod.INBAND_DTMF
            }
        }

        // If the account is configured to use RTP DTMF method and the call
        // does not manage telephone events. Then, we log it for future debugging.
        if (dtmfMethod === DTMFMethod.RTP_DTMF && !isRFC4733Active(cp)) {
            Timber.d("RTP DTMF used without telephone-event capacities")
        }
        (cp.getMediaHandler()!!.getStream(MediaType.AUDIO) as AudioMediaStream?)!!
                .stopSendingDTMF(cpDTMFMethod)
    }
}