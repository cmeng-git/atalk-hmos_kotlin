/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.media.AbstractOperationSetVideoTelephony
import org.atalk.service.neomedia.QualityControl
import timber.log.Timber

/**
 * Implements `OperationSetVideoTelephony` in order to give access to video-specific
 * functionality in the Jabber protocol implementation.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */

/**
 * Initializes a new `OperationSetVideoTelephonyJabberImpl` instance which builds upon
 * the telephony-related functionality of a specific
 * `OperationSetBasicTelephonyJabberImpl`.
 *
 * @param basicTelephony the `OperationSetBasicTelephonyJabberImpl` the new extension should build upon
 */
class OperationSetVideoTelephonyJabberImpl(basicTelephony: OperationSetBasicTelephonyJabberImpl)
    : AbstractOperationSetVideoTelephony<OperationSetBasicTelephonyJabberImpl, ProtocolProviderServiceJabberImpl, CallJabberImpl, CallPeerJabberImpl>(basicTelephony) {
    /**
     * Implements OperationSetVideoTelephony#setLocalVideoAllowed(Call, boolean). Modifies the local
     * media setup to reflect the requested setting for the streaming of the local video and then
     * re-invites all CallPeers to re-negotiate the modified media setup.
     *
     * @param call the call where we'd like to allow sending local video.
     * @param allowed `true` if local video transmission is allowed and `false` otherwise.
     * @throws OperationFailedException if video initialization fails.
     */
    @Throws(OperationFailedException::class)
    override fun setLocalVideoAllowed(call: Call<*>, allowed: Boolean) {
        super.setLocalVideoAllowed(call, allowed)
        (call as CallJabberImpl).modifyVideoContent()
    }

    /**
     * Create a new video call and invite the specified CallPeer to it.
     *
     * @param uri the address of the callee that we should invite to a new call.
     * @return CallPeer the CallPeer that will represented by the specified uri. All following state
     * change events will be delivered through that call peer. The Call that this peer is a
     * member of could be retrieved from the CallParticipant instance with the use of the corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the video call.
     */
    @Throws(OperationFailedException::class)
    override fun createVideoCall(uri: String?): Call<*>? {
        return createOutgoingVideoCall(uri)
    }

    /**
     * Create a new video call and invite the specified CallPeer to it.
     *
     * @param callee the address of the callee that we should invite to a new call.
     * @return CallPeer the CallPeer that will represented by the specified uri. All following state
     * change events will be delivered through that call peer. The Call that this peer is a
     * member of could be retrieved from the CallParticipant instance with the use of the corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the video call.
     */
    @Throws(OperationFailedException::class)
    override fun createVideoCall(callee: Contact?): Call<*>? {
        return createOutgoingVideoCall(callee!!.address)
    }

    /**
     * Check if the remote part supports Jingle video.
     *
     * @param calleeAddress Contact address
     * @return true if contact support Jingle video, false otherwise
     * @throws OperationFailedException with the corresponding code if we fail to create the video call.
     */
    @Throws(OperationFailedException::class)
    private fun createOutgoingVideoCall(calleeAddress: String?): Call<*>? {
        Timber.d("creating outgoing video call for %s", calleeAddress)
        if (parentProvider.connection == null) {
            throw OperationFailedException("Failed to create OutgoingJingleSession.\n"
                    + "We don't have a valid XMPPConnection.", OperationFailedException.INTERNAL_ERROR)
        }

        /* enable video */
        val call = CallJabberImpl(basicTelephony, basicTelephony.sid)
        call.setLocalVideoAllowed(true, getMediaUseCase())
        val callPeer: CallPeer? = basicTelephony.createOutgoingCall(call, calleeAddress, null)

        /*
         * XXX OperationSetBasicTelephonyJabberImpl#createOutgoingCall( CallJabberImpl, String) may
         * have replaced the CallJabberImpl instance created above with a CallGTalkImpl instance.
         */
        return callPeer!!.getCall()
    }

    /**
     * Indicates a user request to answer an incoming call with video enabled from the specified CallPeer.
     *
     * @param peer the call peer that we'd like to answer.
     * @throws OperationFailedException with the corresponding code if we encounter an error while performing this operation.
     */
    @Throws(OperationFailedException::class)
    override fun answerVideoCallPeer(peer: CallPeer?) {
        val callPeer = peer as CallPeerJabberImpl?
        /* answer with video */
        callPeer!!.getCall()!!.setLocalVideoAllowed(true, getMediaUseCase())
        callPeer.answer()
    }

    /**
     * Returns the quality control for video calls if any. Return null so protocols who supports it to override it.
     *
     * @param peer the peer which this control operates on.
     * @return the implemented quality control.
     */
    override fun getQualityControl(peer: CallPeer?): QualityControl? {
        return if (peer is CallPeerJabberImpl) peer.getMediaHandler().getQualityControl() else null
    }
}