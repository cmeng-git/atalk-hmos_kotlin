/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.CallListener
import org.atalk.service.neomedia.recording.Recorder
import java.text.ParseException

/**
 * An Operation Set defining all basic telephony operations such as conducting simple calls and etc.
 * Note that video is not considered as a part of a supplementary operation set and if included in
 * the service should be available behind the basic telephony set.
 *
 * @param <T> the provider extension class like for example `ProtocolProviderServiceSipImpl` or
 * `ProtocolProviderServiceJabberImpl`
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
</T> */
interface OperationSetBasicTelephony<T : ProtocolProviderService> : OperationSet {
    /**
     * Registers the specified CallListener with this provider so that it could be notified when
     * incoming calls are received. This method is called by the implementation of the PhoneUI service.
     *
     * @param listener the listener to register with this provider.
     */
    fun addCallListener(listener: CallListener)

    /**
     * Removes the specified listener from the list of call listeners.
     *
     * @param listener the listener to unregister.
     */
    fun removeCallListener(listener: CallListener)

    /**
     * Creates a new `Call` and invites a specific `CallPeer` to it given by her `String` URI.
     *
     * @param uri the address of the callee who we should invite to a new `Call`
     * @return a newly created `Call`. The specified `callee` is available in the
     * `Call` as a `CallPeer`
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     * @throws ParseException if `callee` is not a valid SIP address `String`
     */
    @Throws(OperationFailedException::class, ParseException::class)
    fun createCall(uri: String): Call<*>?

    /**
     * Creates a new `Call` and invites a specific `CallPeer` to it given by her `Contact`.
     *
     * @param callee the address of the callee who we should invite to a new call
     * @return a newly created `Call`. The specified `callee` is available in the
     * `Call` as a `CallPeer`
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     */
    @Throws(OperationFailedException::class)
    fun createCall(callee: Contact): Call<*>?

    /**
     * Creates a new `Call` and invites a specific `CallPeer` to it given by her `String` URI.
     *
     * @param callee the address of the callee who we should invite to a new `Call`
     * @param conference the `CallConference` in which the newly-created `Call` is to participate
     * @return a newly created `Call`. The specified `callee` is available in the
     * `Call` as a `CallPeer`
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     * @throws ParseException if `callee` is not a valid SIP address `String`
     */
    @Throws(OperationFailedException::class, ParseException::class)
    fun createCall(callee: String, conference: CallConference?): Call<*>?

    /**
     * Creates a new `Call` and invites a specific `CallPeer` to it given by her `Contact`.
     *
     * @param callee the address of the callee who we should invite to a new call
     * @param conference the `CallConference` in which the newly-created `Call` is to participate
     * @return a newly created `Call`. The specified `callee` is available in the
     * `Call` as a `CallPeer`
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     */
    @Throws(OperationFailedException::class)
    fun createCall(callee: Contact, conference: CallConference?): Call<*>?

    /**
     * Creates a new `Call` and sends an invite to the conference described in `cd`. A
     * `CallPeer` corresponding the `cd` will be created and added to the returned `Call`
     *
     * @param cd the conference to send an invite to
     * @param chatRoom the chat room associated with the call.
     * @return a newly created `Call`, to which a `CallPeer` corresponding to
     * `cd` has been added.
     */
    @Throws(OperationFailedException::class)
    fun createCall(cd: ConferenceDescription?, chatRoom: ChatRoom?): Call<*>?

    /**
     * Indicates a user request to answer an incoming call from the specified CallPeer.
     *
     * @param peer the call peer that we'd like to answer.
     * @throws OperationFailedException with the corresponding code if we encounter an error while performing this operation.
     */
    @Throws(OperationFailedException::class)
    fun answerCallPeer(peer: CallPeer?)

    /**
     * Puts the specified CallPeer "on hold". In other words incoming media flows are not played and
     * outgoing media flows are either muted or stopped, without actually interrupting the session.
     *
     * @param peer the peer that we'd like to put on hold.
     * @throws OperationFailedException with the corresponding code if we encounter an error while performing this operation.
     */
    @Throws(OperationFailedException::class)
    fun putOnHold(peer: CallPeer?)

    /**
     * Resumes communication with a call peer previously put on hold. If the specified peer is not
     * "On Hold" at the time putOffHold is called, the method has no effect.
     *
     * @param peer the call peer to put on hold.
     * @throws OperationFailedException with the corresponding code if we encounter an error while performing this operation
     */
    @Throws(OperationFailedException::class)
    fun putOffHold(peer: CallPeer?)

    /**
     * Indicates a user request to end a call with the specified call peer.
     *
     * @param peer the peer that we'd like to hang up on.
     * @throws OperationFailedException with the corresponding code if we encounter an error while performing this operation.
     */
    @Throws(OperationFailedException::class)
    fun hangupCallPeer(peer: CallPeer?)

    /**
     * Ends the call with the specified `peer`.
     *
     * @param peer the peer that we'd like to hang up on.
     * @param reasonCode indicates if the hangup is following to a call failure or simply a disconnect indicate
     * by the reason.
     * @param reason the reason of the hangup. If the hangup is due to a call failure, then this string
     * could indicate the reason of the failure
     * @throws OperationFailedException if we fail to terminate the call.
     */
    @Throws(OperationFailedException::class)
    fun hangupCallPeer(peer: CallPeer?, reasonCode: Int, reason: String?)

    /**
     * Returns an iterator over all currently active calls.
     *
     * @return Iterator
     */
    fun getActiveCalls(): Iterator<Call<*>?>?

    /**
     * Sets the mute state of the `Call`.
     *
     *
     * Muting audio streams sent from the call is implementation specific and one of the possible
     * approaches to it is sending silence.
     *
     *
     * @param call the `Call` whos mute state is set
     * @param mute `true` to mute the call streams being sent to `peers`; otherwise, `false`
     */
    fun setMute(call: Call<*>?, mute: Boolean)

    /**
     * Returns the protocol provider that this operation set belongs to.
     *
     * @return a reference to the `ProtocolProviderService` that created this operation set.
     */
    fun getProtocolProvider(): T

    /**
     * Creates a new `Recorder` which is to record the specified `Call` (into a file
     * which is to be specified when starting the returned `Recorder`).
     *
     * @param call the `Call` which is to be recorded by the returned `Recorder` when the
     * latter is started
     * @return a new `Recorder` which is to record the specified `call` (into a file
     * which is to be specified when starting the returned `Recorder`)
     * @throws OperationFailedException if anything goes wrong while creating the new `Recorder`
     * for the specified `call`
     */
    @Throws(OperationFailedException::class)
    fun createRecorder(call: Call<*>?): Recorder?

    companion object {
        /**
         * The name of the property that contains the maximum port number that we'd like our RTP
         * managers to bind upon.
         */
        const val MAX_MEDIA_PORT_NUMBER_PROPERTY_NAME = "protocol.MAX_MEDIA_PORT_NUMBER"

        /**
         * The name of the property that contains the minimum port number that we'd like our RTP
         * managers to bind upon.
         */
        const val MIN_MEDIA_PORT_NUMBER_PROPERTY_NAME = "protocol.MIN_MEDIA_PORT_NUMBER"

        /**
         * The name of the property that contains the minimum port number that we'd like our Video RTP
         * managers to bind upon.
         */
        const val MIN_VIDEO_PORT_NUMBER_PROPERTY_NAME = "protocol.MIN_VIDEO_PORT_NUMBER"

        /**
         * The name of the property that contains the maximum port number that we'd like our Video RTP
         * managers to bind upon.
         */
        const val MAX_VIDEO_PORT_NUMBER_PROPERTY_NAME = "protocol.MAX_VIDEO_PORT_NUMBER"

        /**
         * The name of the property that contains the minimum port number that we'd like our Audio RTP
         * managers to bind upon.
         */
        const val MIN_AUDIO_PORT_NUMBER_PROPERTY_NAME = "protocol.MIN_AUDIO_PORT_NUMBER"

        /**
         * The name of the property that contains the maximum port number that we'd like our Audio RTP
         * managers to bind upon.
         */
        const val MAX_AUDIO_PORT_NUMBER_PROPERTY_NAME = "protocol.MAX_AUDIO_PORT_NUMBER"

        /**
         * The name of the property that contains the minimum port number that we'd like our Data
         * Channel (e.g. Pseudo TCP) managers to bind upon.
         */
        const val MIN_DATA_CHANNEL_PORT_NUMBER_PROPERTY_NAME = "protocol.MIN_DATA_CHANNEL_PORT_NUMBER"

        /**
         * The name of the property that contains the maximum port number that we'd like our Data
         * Channel RTP managers to bind upon.
         */
        const val MAX_DATA_CHANNEL_PORT_NUMBER_PROPERTY_NAME = "protocol.MAX_DATA_CHANNEL_PORT_NUMBER"

        /**
         * Reason code used to hangup peer, indicates normal hangup.
         */
        const val HANGUP_REASON_NORMAL_CLEARING = 200

        /**
         * Reason code used to hangup peer when we wait for some event and it timeouted.
         */
        const val HANGUP_REASON_TIMEOUT = 408

        /**
         * Reason code used to hangup peer if call was not encrypted.
         */
        const val HANGUP_REASON_ENCRYPTION_REQUIRED = 609

        /**
         * Reason code used to hangup peer, indicates busy here.
         */
        const val HANGUP_REASON_BUSY_HERE = 486

        /**
         * Reason code used to hangup peer, indicates internal server error.
         */
        const val HANGUP_REASON_ERROR = 500
    }
}