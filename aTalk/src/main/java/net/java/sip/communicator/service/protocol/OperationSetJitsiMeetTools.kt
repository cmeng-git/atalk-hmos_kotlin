/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import org.jivesoftware.smack.packet.ExtensionElement
import org.json.JSONObject

/**
 * The operation set provides functionality specific to Jitsi Meet WebRTC conference and is
 * currently used in the SIP gateway.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
interface OperationSetJitsiMeetTools : OperationSet {
    /**
     * Adds given feature to communication protocol capabilities list of parent [ProtocolProviderService].
     *
     * @param featureName feature name to be added to the capabilities list.
     */
    fun addSupportedFeature(featureName: String?)

    /**
     * Removes given feature from communication protocol capabilities list of parent [ProtocolProviderService].
     *
     * @param featureName feature name to be removed from the capabilities list.
     */
    fun removeSupportedFeature(featureName: String?)

    /**
     * Includes given `ExtensionElement` in multi user chat presence and sends presence
     * update packet to the chat room.
     *
     * @param chatRoom the `ChatRoom` for which the presence will be updated.
     * @param extension the `ExtensionElement` to be included in MUC presence.
     */
    fun sendPresenceExtension(chatRoom: ChatRoom?, extension: ExtensionElement?)

    /**
     * Removes given `PacketExtension` from the multi user chat presence
     * and sends presence update packet to the chat room.
     *
     * @param chatRoom the `ChatRoom` for which the presence will be
     *
     * @param extension the `PacketExtension` to be removed from the MUC presence.
     */
    fun removePresenceExtension(chatRoom: ChatRoom?, extension: ExtensionElement?)

    /**
     * Sets the status message of our MUC presence and sends presence status update packet to the server.
     *
     * @param chatRoom the `ChatRoom` for which the presence status message will be changed.
     * @param statusMessage the text that will be used as our presence status message in the MUC.
     */
    fun setPresenceStatus(chatRoom: ChatRoom?, statusMessage: String?)

    /**
     * Adds given `listener` to the list of [JitsiMeetRequestListener]s.
     *
     * @param listener the [JitsiMeetRequestListener] to be notified about future events.
     */
    fun addRequestListener(listener: JitsiMeetRequestListener?)

    /**
     * Removes given `listener` from the list of [JitsiMeetRequestListener]s.
     *
     * @param listener the [JitsiMeetRequestListener] that will be no longer notified about Jitsi Meet events.
     */
    fun removeRequestListener(listener: JitsiMeetRequestListener?)

    /**
     * Sends a JSON to the specified `callPeer`.
     *
     * @param callPeer the CallPeer to which we send the JSONObject to.
     * @param jsonObject the JSONObject that we send to the CallPeer.
     * @param parameterMap a map which is used to set specific parameters for the protocol used to send the jsonObject.
     * @throws OperationFailedException thrown in case anything goes wrong
     * while preparing or sending the JSONObject.
     */
    @Throws(OperationFailedException::class)
    fun sendJSON(callPeer: CallPeer?,
            jsonObject: JSONObject?,
            parameterMap: Map<String?, Any?>?)

    /**
     * Interface used to handle Jitsi Meet conference requests.
     */
    interface JitsiMeetRequestListener {
        /**
         * Events is fired for an incoming call that contains information about Jitsi Meet
         * conference room to be joined.
         *
         * @param call the incoming [Call] instance.
         * @param jitsiMeetRoom the name of multi user chat room that is hosting Jitsi Meet conference.
         * @param extraData extra data passes for this request in the form of Map<name></name>, value>.
         */
        fun onJoinJitsiMeetRequest(call: Call<*>?, jitsiMeetRoom: String?, extraData: Map<String?, String?>?)

        /**
         * Event is fired after startmuted extension is received.
         *
         * @param startMutedFlags startMutedFlags[0] represents the muted status of audio stream.
         * startMuted[1] represents the muted status of video stream.
         */
        fun onSessionStartMuted(startMutedFlags: BooleanArray?)

        /**
         * Event is fired when a JSON is received from a CallPeer.
         *
         * @param callPeer the CallPeer that sent the JSONObject.
         * @param jsonObject the JSONObject that was received from the CallPeer.
         * @param parameterMap a map which describes protocol specific parameters used to receive the jsonObject.
         */
        fun onJSONReceived(callPeer: CallPeer?, jsonObject: JSONObject?, parameterMap: Map<String?, Any?>?)
    }
}