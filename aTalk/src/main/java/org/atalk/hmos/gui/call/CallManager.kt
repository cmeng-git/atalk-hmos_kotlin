/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call

import android.text.TextUtils
import net.java.sip.communicator.impl.phonenumbers.PhoneNumberI18nServiceImpl
import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.CallConference
import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.service.protocol.CallPeerState
import net.java.sip.communicator.service.protocol.CallState
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.ConferenceDescription
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ContactResource
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.OperationSetAdvancedTelephony
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony
import net.java.sip.communicator.service.protocol.OperationSetDesktopStreaming
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.OperationSetResourceAwareTelephony
import net.java.sip.communicator.service.protocol.OperationSetTelephonyConferencing
import net.java.sip.communicator.service.protocol.OperationSetVideoBridge
import net.java.sip.communicator.service.protocol.OperationSetVideoTelephony
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.media.MediaAwareCall
import net.java.sip.communicator.service.protocol.media.MediaAwareCallPeer
import net.java.sip.communicator.service.protocol.media.ProtocolMediaActivator
import net.java.sip.communicator.util.ConfigurationUtils
import net.java.sip.communicator.util.NetworkUtils
import net.java.sip.communicator.util.account.AccountUtils
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.contactlist.UIContactImpl
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.service.neomedia.MediaUseCase
import org.atalk.service.neomedia.codec.Constants
import org.atalk.service.neomedia.device.MediaDevice
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.util.MediaType
import org.jivesoftware.smackx.avatar.AvatarManager
import timber.log.Timber
import java.text.ParseException
import java.util.*
import javax.swing.JComponent

/**
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
object CallManager {
    // Jingle Message id / Jingle session-initiate sid
    const val CALL_SID = "call_sid"
    const val CALL_EVENT = "call_event"

    // True to indicate the jingleMessage <accept/> is auto-send onReceive the <propose/> stanza
    const val AUTO_ACCEPT = "auto_accept"

    // android call parameters
    const val CALL_TRANSFER = "CallTransfer"

    /**
     * A table mapping protocol `Call` objects to the GUI dialogs that are currently used to display them.
     * The string ID is an instance of the time when the call is first activated
     */
    private val activeCalls = HashMap<String?, Call<*>?>()

    /**
     * A map of active outgoing calls per `UIContactImpl`.
     */
    private var uiContactCalls: MutableMap<Call<*>, UIContactImpl>? = null

    @Synchronized
    fun addActiveCall(call: Call<*>): String {
        var key = call.callId
        if (TextUtils.isEmpty(key)) {
            key = System.currentTimeMillis().toString()
            Timber.e("CallId is not initialized with jingle sid: %s", key)
        }
        synchronized(activeCalls) { activeCalls.put(key, call) }
        return key
    }

    @Synchronized
    fun removeActiveCall(callKey: String?) {
        synchronized(activeCalls) {
            activeCalls.remove(callKey)
        }
    }

    @Synchronized
    fun removeActiveCall(call: Call<*>?) {
        synchronized(activeCalls) {
            if (!activeCalls.containsValue(call)) return

            val activeCallsIter = activeCalls.keys.iterator()
            val toRemove = ArrayList<String?>()
            while (activeCallsIter.hasNext()) {
                val key = activeCallsIter.next()
                if (activeCalls[key] == call) toRemove.add(key)
            }

            for (removeKey in toRemove) {
                removeActiveCall(removeKey)
            }
        }
    }

    /**
     * @param callKey an instance of the time when the call is first activated,
     * it is used for later identification of the call.
     * @return the active call
     */
    @Synchronized
    fun getActiveCall(callKey: String?): Call<*>? {
        synchronized(activeCalls) { return activeCalls[callKey] }
    }

    /**
     * Returns currently active calls.
     *
     * @return collection of currently active calls.
     */
    fun getActiveCalls(): Collection<Call<*>?> {
        synchronized(activeCalls) { return activeCalls.values }
    }

    /**
     * Returns the number of currently active calls.
     *
     * @return the number of currently active calls.
     */
    @Synchronized
    fun getActiveCallsCount(): Int {
        synchronized(activeCalls) { return activeCalls.size }
    }

    /**
     * Answers the given call with the required media.
     *
     * @param call the call to answer
     * @param isVideoCall the incoming call type (audio/video?)
     */
    fun answerCall(call: Call<*>?, isVideoCall: Boolean) {
        answerCall(call, null, isVideoCall)
    }

    /**
     * Answers a specific `Call` with or without video and, optionally, does that in a
     * telephony conference with an existing `Call`.
     *
     * @param call the call to answer
     * @param existingCall current call in progress
     * @param isVideoCall the incoming call type (audio/video?)
     */
    private fun answerCall(call: Call<*>?, existingCall: Call<*>?, isVideoCall: Boolean) {
        // if (existingCall == null)
        // openCallContainerIfNecessary(call)
        AnswerCallThread(call, existingCall, isVideoCall).start()
    }

    /**
     * Answers the given call in an existing call. It will end up with a conference call.
     *
     * @param call the call to answer
     */
    fun answerCallInFirstExistingCall(call: Call<*>?) {
        // Find the first existing call.
        val existingCallIter = getInProgressCalls().iterator()
        val existingCall = if (existingCallIter.hasNext()) existingCallIter.next() else null
        answerCall(call, existingCall, false /* without video */)
    }

    /**
     * Merges specific existing `Call`s into a specific telephony conference.
     *
     * @param conference the conference
     * @param calls list of calls
     */
    fun mergeExistingCalls(conference: CallConference, calls: Collection<Call<*>>) {
        MergeExistingCalls(conference, calls).start()
    }

    /**
     * Hang ups the given call.
     *
     * @param call the call to hang up
     */
    fun hangupCall(call: Call<*>?) {
        HangupCallThread(call).start()
    }

    /**
     * Hang ups the given `callPeer`.
     *
     * @param peer the `CallPeer` to hang up
     */
    fun hangupCallPeer(peer: CallPeer?) {
        HangupCallThread(peer).start()
    }

    /**
     * Asynchronously hangs up the `Call`s participating in a specific `CallConference`.
     *
     * @param conference the `CallConference` whose participating `Call`s are to be hanged up
     */
    fun hangupCalls(conference: CallConference?) {
        HangupCallThread(conference).start()
    }

    /**
     * Creates a call to the contact represented by the given string.
     *
     * @param protocolProvider the protocol provider to which this call belongs.
     * @param contact the contact to call to
     * @param isVideoCall true to setup video call
     */
    fun createCall(protocolProvider: ProtocolProviderService, contact: String?, isVideoCall: Boolean) {
        CreateCallThread(protocolProvider, contact, isVideoCall).start()
    }

    /**
     * Creates a call to the contact represented by the given string.
     *
     * @param protocolProvider the protocol provider to which this call belongs.
     * @param contact the contact to call to
     * @param uiContact the meta contact we're calling
     * @param isVideoCall true to setup video call
     */
    fun createCall(protocolProvider: ProtocolProviderService, contact: String?, uiContact: UIContactImpl?,
            isVideoCall: Boolean) {
        CreateCallThread(protocolProvider, null, null, uiContact, contact, null, null, isVideoCall).start()
    }

    /**
     * Enables/disables local video for a specific `Call`.
     *
     * @param call the `Call` to enable/disable to local video for
     * @param enable `true` to enable the local video; otherwise, `false`
     */
    fun enableLocalVideo(call: Call<*>, enable: Boolean) {
        EnableLocalVideoThread(call, enable).start()
    }

    /**
     * Indicates if the local video is currently enabled for the given `call`.
     *
     * @param call the `Call`, for which we would to check if the local video streaming is currently enabled
     * @return `true` if the local video streaming is currently enabled for the given
     * `call`, `false` otherwise
     */
    fun isLocalVideoEnabled(call: Call<*>?): Boolean {
        val telephony = call!!.pps.getOperationSet(OperationSetVideoTelephony::class.java)
        return telephony != null && telephony.isLocalVideoAllowed(call)
    }
    /**
     * Creates a call to the given call string. The given component indicates where should be
     * shown the "call via" menu if needed.
     *
     * callString the string to call
     * c the component, which indicates where should be shown the "call via" menu if needed
     * l listener that is notified when the call interface has been started after call was created
     */
    /**
     * Creates a call to the given call string. The given component indicates where should be
     * shown the "call via" menu if needed.
     *
     * @param cString the string to call
     * @param c the component, which indicates where should be shown the "call via" menu if needed
     */
    @JvmOverloads
    fun createCall(cString: String, c: JComponent?, l: CallInterfaceListener? = null) {
        var callString = cString
        callString = callString.trim { it <= ' ' }

        // Removes special characters from phone numbers.
        if (ConfigurationUtils.isNormalizePhoneNumber() && !NetworkUtils.isValidIPAddress(callString)) {
            callString = AndroidGUIActivator.phoneNumberI18nService!!.normalize(callString).toString()
        }
        val telephonyProviders = getTelephonyProviders()
        if (telephonyProviders.size == 1) {
            createCall(telephonyProviders[0], callString, false)
            l?.callInterfaceStarted()
        }
        else if (telephonyProviders.size > 1) {
            /*
             * Allow plugins which do not have a (Jitsi) UI to create calls by automatically
             * picking up a telephony provider.
             */
            if (c == null) {
                var preferredTelephonyProvider: ProtocolProviderService? = null
                for (telephonyProvider in telephonyProviders) {
                    try {
                        val presenceOpSet = telephonyProvider.getOperationSet(OperationSetPresence::class.java)
                        if (presenceOpSet?.findContactByID(callString) != null) {
                            preferredTelephonyProvider = telephonyProvider
                            break
                        }
                    } catch (t: Throwable) {
                        if (t is ThreadDeath) throw t
                    }
                }
                if (preferredTelephonyProvider == null) preferredTelephonyProvider = telephonyProviders[0]
                createCall(preferredTelephonyProvider, callString, false)
                l?.callInterfaceStarted()
            }
            else {
                val chooseAccountDialog = ChooseCallAccountPopupMenu(c, callString, telephonyProviders, l)

                // chooseAccountDialog.setLocation(c.getLocation());
                chooseAccountDialog.showPopupMenu()
            }
        }
        else {
            DialogActivity.showDialog(aTalkApp.globalContext,
                    R.string.service_gui_WARNING, R.string.service_gui_NO_ONLINE_TELEPHONY_ACCOUNT)
        }
    }

    /**
     * Creates a call to the given list of contacts.
     *
     * @param protocolProvider the protocol provider to which this call belongs.
     * @param callees the list of contacts to call to
     */
    fun createConferenceCall(callees: Array<String>, protocolProvider: ProtocolProviderService) {
        val crossProtocolCallees = HashMap<ProtocolProviderService, List<String>>()
        crossProtocolCallees[protocolProvider] = callees.asList()
        createConferenceCall(crossProtocolCallees)
    }

    /**
     * Invites the given list of `callees` to the given conference `call`.
     *
     * @param callees the list of contacts to invite
     * @param call the protocol provider to which this call belongs
     */
    fun inviteToConferenceCall(callees: Array<String>, call: Call<*>) {
        val crossProtocolCallees = HashMap<ProtocolProviderService, List<String>>()
        crossProtocolCallees[call.pps] = callees.asList()
        inviteToConferenceCall(crossProtocolCallees, call)
    }

    /**
     * Invites the given list of `callees` to the given conference `call`.
     *
     * @param callees the list of contacts to invite
     * @param call existing call
     */
    private fun inviteToConferenceCall(callees: Map<ProtocolProviderService, List<String>>, call: Call<*>?) {
        InviteToConferenceCallThread(callees, call).start()
    }

    /**
     * Invites specific `callees` to a specific telephony conference.
     *
     * @param callees the list of contacts to invite
     * @param conference the telephony conference to invite the specified `callees` into
     */
    fun inviteToConferenceCall(callees: Map<ProtocolProviderService, List<String>>,
            conference: CallConference?) {
        /*
         * InviteToConferenceCallThread takes a specific Call but actually invites to the
         * telephony conference associated with the specified Call (if any). In order to not
         * change the signature of its constructor at this time, just pick up a Call
         * participating in the specified telephony conference (if any).
         */
        var call: Call<*>? = null
        if (conference != null) {
            val calls = conference.calls
            if (calls.isNotEmpty()) call = calls[0]
        }
        InviteToConferenceCallThread(callees, call).start()
    }

    /**
     * Asynchronously creates a new conference `Call` with a specific list of
     * participants/callees.
     *
     * @param callees the list of participants/callees to invite to a newly-created conference `Call`
     */
    fun createConferenceCall(callees: Map<ProtocolProviderService, List<String>>) {
        InviteToConferenceCallThread(callees, null).start()
    }

    /**
     * Asynchronously creates a new video bridge conference `Call` with a specific list of
     * participants/callees.
     *
     * @param callProvider the `ProtocolProviderService` to use for creating the call
     * @param callees the list of participants/callees to invite to the newly-created video bridge
     * conference `Call`
     */
    fun createJitsiVideobridgeConfCall(callProvider: ProtocolProviderService, callees: Array<String>) {
        InviteToConferenceBridgeThread(callProvider, callees, null).start()
    }

    /**
     * Invites the given list of `callees` to the given conference `call`.
     *
     * @param callees the list of contacts to invite
     * @param call the protocol provider to which this call belongs
     */
    fun inviteToJitsiVideobridgeConfCall(callees: Array<String>, call: Call<*>) {
        InviteToConferenceBridgeThread(call.pps, callees, call).start()
    }

    /**
     * Puts on or off hold the given `callPeer`.
     *
     * @param callPeer the peer to put on/off hold
     * @param isOnHold indicates the action (on hold or off hold)
     */
    private fun putOnHold(callPeer: CallPeer, isOnHold: Boolean) {
        PutOnHoldCallPeerThread(callPeer, isOnHold).start()
    }

    /**
     * Puts on or off hold the given `call`. (cmeng-android)
     *
     * @param call the peer to put on/off hold
     * @param isOnHold indicates the action (on hold or off hold)
     */
    fun putOnHold(call: Call<*>, isOnHold: Boolean) {
        val peers = call.getCallPeers()
        while (peers.hasNext()) {
            putOnHold(peers.next(), isOnHold)
        }
    }

    /**
     * Transfers the given `peer` to the given `target`.
     *
     * @param peer the `CallPeer` to transfer
     * @param target the `CallPeer` target to transfer to
     */
    fun transferCall(peer: CallPeer, target: CallPeer) {
        val telephony = peer.getCall()!!.pps.getOperationSet(OperationSetAdvancedTelephony::class.java)
        if (telephony != null) {
            try {
                telephony.transfer(peer, target)
            } catch (ex: OperationFailedException) {
                val error = aTalkApp.getResString(R.string.gui_call_transfer_failed,
                        peer.getAddress(), target.getAddress(), ex.message)
                Timber.w("%s", error)
                DialogActivity.showDialog(aTalkApp.globalContext,
                        aTalkApp.getResString(R.string.gui_call_transfer_call), error)
            }
        }
    }

    /**
     * Transfers the given `peer` to the given `target`.
     *
     * @param peer the `CallPeer` to transfer
     * @param target the target of the transfer
     */
    fun transferCall(peer: CallPeer, target: String?) {
        val telephony = peer.getCall()!!.pps.getOperationSet(OperationSetAdvancedTelephony::class.java)
        if (telephony != null) {
            try {
                telephony.transfer(peer, target)
            } catch (ex: OperationFailedException) {
                val error = aTalkApp.getResString(R.string.gui_call_transfer_failed,
                        peer.getAddress(), target, ex.message)
                Timber.w("%s", error)
                DialogActivity.showDialog(aTalkApp.globalContext,
                        aTalkApp.getResString(R.string.gui_call_transfer_call), error)
            }
        }
    }

    /**
     * Returns a list of all currently registered telephony providers.
     *
     * @return a list of all currently registered telephony providers
     */
    private fun getTelephonyProviders(): List<ProtocolProviderService> {
        return AccountUtils.getRegisteredProviders(OperationSetBasicTelephony::class.java)
    }

    /**
     * Returns a list of all currently registered telephony providers supporting conferencing.
     *
     * @return a list of all currently registered telephony providers supporting conferencing
     */
    fun getTelephonyConferencingProviders(): List<ProtocolProviderService> {
        return AccountUtils.getRegisteredProviders(OperationSetTelephonyConferencing::class.java)
    }

    /**
     * Returns a collection of all currently in progress calls. A call is active if it is in
     * progress so the method merely delegates to
     *
     * @return a collection of all currently in progress calls.
     */
    fun getInProgressCalls(): Collection<Call<*>?> {
        return getActiveCalls()
    }

    /**
     * Returns the image corresponding to the given `peer`.
     *
     * @param peer the call peer, for which we're returning an image
     * @return the peer image
     */
    fun getPeerImage(peer: CallPeer): ByteArray? {
        var image: ByteArray? = null
        val peerJid = peer.getPeerJid()!!.asBareJid()
        // We search for a contact corresponding to this call peer and try to get its image.
        if (peer.getPeerJid() != null) {
            image = AvatarManager.getAvatarImageByJid(peerJid)
        }
        return image
    }

    /**
     * Indicates if we have video streams to show in this interface.
     *
     * @param call the call to check for video streaming
     * @return `true` if we have video streams to show in this interface; otherwise, `false`
     */
    fun isVideoStreaming(call: Call<*>): Boolean {
        return isVideoStreaming(call.getConference())
    }

    /**
     * Indicates if we have video streams to show in this interface.
     *
     * @param conference the conference we check for video streaming
     * @return `true` if we have video streams to show in this interface; otherwise, `false`
     */
    private fun isVideoStreaming(conference: CallConference): Boolean {
        for (call in conference.calls) {
            val videoTelephony = call.pps.getOperationSet(OperationSetVideoTelephony::class.java)
                    ?: continue
            if (videoTelephony.isLocalVideoStreaming(call)) return true
            val callPeers = call.getCallPeers()
            while (callPeers.hasNext()) {
                val remoteVideos = videoTelephony.getVisualComponents(callPeers.next())
                if (remoteVideos != null && remoteVideos.isNotEmpty()) return true
            }
        }
        return false
    }

    /**
     * Indicates if the given call is currently muted.
     *
     * @param call the call to check
     * @return `true` if the given call is currently muted, `false` - otherwise
     */
    fun isMute(call: Call<*>?): Boolean {
        return if (call is MediaAwareCall<*, *, *>) {
            call.isMute
        }
        else {
            false
        }
    }

    /**
     * Mutes/unmutes the given call.
     *
     * @param call the call to mute/unmute
     * @param isMute `true` to mute the call, `false` to unmute it
     */
    fun setMute(call: Call<*>?, isMute: Boolean) {
        Timber.d("Set mute to %s", isMute)
        MuteThread(call, isMute).start()
    }

    /**
     * Checks if the call has been put on hold by local user.
     *
     * @param call the `Call` that will be checked.
     * @return `true` if given `Call` is locally on hold.
     */
    fun isLocallyOnHold(call: Call<*>): Boolean {
        var onHold = false
        val peers = call.getCallPeers()
        if (peers.hasNext()) {
            val peerState = peers.next().getState()
            onHold = CallPeerState.ON_HOLD_LOCALLY == peerState || CallPeerState.ON_HOLD_MUTUALLY == peerState
        }
        else {
            Timber.w("No peer belongs to call: %s", call.toString())
        }
        return onHold
    }

    /**
     * Returns of supported/enabled list of audio formats for a provider.
     *
     * @param device the `MediaDevice`, which audio formats we're looking for
     * @param protocolProvider the provider to check.
     * @return list of supported/enabled audio formats or empty list otherwise.
     */
    private fun getAudioFormats(device: MediaDevice, protocolProvider: ProtocolProviderService): List<MediaFormat> {
        val res = ArrayList<MediaFormat>()
        val accountProperties = protocolProvider.accountID.accountProperties
        val overrideEncodings = accountProperties[ProtocolProviderFactory.OVERRIDE_ENCODINGS]

        val formats = if (java.lang.Boolean.parseBoolean(overrideEncodings)) {
            /*
             * The account properties associated with account override the global EncodingConfiguration.
             */
            val encodingConfiguration = ProtocolMediaActivator.mediaService!!.createEmptyEncodingConfiguration()
            encodingConfiguration!!.loadProperties(accountProperties, ProtocolProviderFactory.ENCODING_PROP_PREFIX)
            device.getSupportedFormats(null, null, encodingConfiguration)
        }
        /* The global EncodingConfiguration is in effect. */
        else {
            device.supportedFormats
        }

        // skip the special telephony event
        for (format in formats) {
            if (format.encoding != Constants.TELEPHONE_EVENT) res.add(format)
        }

        return res
    }

    /**
     * Creates a video call through the given `protocolProvider`.
     *
     * @param protocolProvider the `ProtocolProviderService` through which to make the call
     * @param contact the `Contact` to call
     * @param uiContact the `UIContactImpl` we're calling
     * @param stringContact the contact string to call
     * @throws OperationFailedException thrown if the call operation fails
     * @throws ParseException thrown if the contact string is malformatted
     */
    @Throws(OperationFailedException::class, ParseException::class)
    private fun internalCallVideo(protocolProvider: ProtocolProviderService,
            contact: Contact?, uiContact: UIContactImpl?, stringContact: String?) {
        val telephony = protocolProvider.getOperationSet(OperationSetVideoTelephony::class.java)
        var createdCall: Call<*>? = null
        if (telephony != null) {
            if (contact != null) {
                createdCall = telephony.createVideoCall(contact)
            }
            else if (stringContact != null) createdCall = telephony.createVideoCall(stringContact)
        }
        if (uiContact != null && createdCall != null) addUIContactCall(uiContact, createdCall)
        // if (createdCall != null)
        // addActiveCall(createdCall);
    }

    /**
     * Creates a call through the given `protocolProvider`.
     *
     * @param protocolProvider the `ProtocolProviderService` through which to make the call
     * @param contact the `Contact` to call
     * @param stringContact the contact string to call
     * @param contactResource the specific `ContactResource` to call
     * @param uiContact the `UIContactImpl` we're calling
     * @throws OperationFailedException thrown if the call operation fails
     * @throws ParseException thrown if the contact string is malformatted
     */
    @Throws(OperationFailedException::class, ParseException::class)
    private fun internalCall(protocolProvider: ProtocolProviderService, contact: Contact?,
            stringContact: String, contactResource: ContactResource?, uiContact: UIContactImpl?) {
        val telephony = protocolProvider.getOperationSet(OperationSetBasicTelephony::class.java)
        val resourceTelephony = protocolProvider.getOperationSet(OperationSetResourceAwareTelephony::class.java)
        var createdCall: Call<*>? = null

        if (resourceTelephony != null && contactResource != null) {
            if (contact != null) {
                createdCall = resourceTelephony.createCall(contact, contactResource)
            }
            else if (StringUtils.isNotEmpty(stringContact)) {
                createdCall = resourceTelephony.createCall(stringContact, contactResource.resourceName)
            }
        }
        else if (telephony != null) {
            if (contact != null) {
                createdCall = telephony.createCall(contact)
            }
            else if (StringUtils.isNotEmpty(stringContact)) {
                createdCall = telephony.createCall(stringContact)
            }
        }
        if (uiContact != null && createdCall != null) addUIContactCall(uiContact, createdCall)
    }

    /**
     * Creates a call through the given `protocolProvider`.
     *
     * @param protocolProvider the `ProtocolProviderService` through which to make the call
     * @param conferenceDescription the description of the conference to call
     * @param chatRoom the chat room associated with the call.
     */
    @Throws(OperationFailedException::class)
    private fun internalCall(protocolProvider: ProtocolProviderService,
            conferenceDescription: ConferenceDescription, chatRoom: ChatRoom) {
        protocolProvider.getOperationSet(OperationSetBasicTelephony::class.java)?.createCall(conferenceDescription, chatRoom)
    }

    /**
     * Returns the `MetaContact`, to which the given `Call` was initially created.
     *
     * @param call the `Call`, which corresponding `MetaContact` we're looking for
     * @return the `UIContactImpl`, to which the given `Call` was initially created
     */
    fun getCallUIContact(call: Call<*>): UIContactImpl? {
        return if (uiContactCalls != null) uiContactCalls!![call] else null
    }

    /**
     * Adds a call for a `metaContact`.
     *
     * @param uiContact the `UIContact` corresponding to the call
     * @param call the `Call` corresponding to the `MetaContact`
     */
    private fun addUIContactCall(uiContact: UIContactImpl, call: Call<*>) {
        if (uiContactCalls == null) uiContactCalls = WeakHashMap()
        uiContactCalls!![call] = uiContact
    }

    /**
     * Normalizes the phone numbers (if any) in a list of `String` contact addresses or
     * phone numbers.
     *
     * @param callees the list of contact addresses or phone numbers to be normalized
     */
    private fun normalizePhoneNumbers(callees: Array<String>) {
        for (i in callees.indices) callees[i] = AndroidGUIActivator.phoneNumberI18nService!!.normalize(callees[i]).toString()
    }

    /**
     * Creates the mute call thread.
     */
    private class MuteThread(private val call: Call<*>?, private val isMute: Boolean) : Thread() {
        override fun run() {
            if (call != null) {
                val telephony = call.pps.getOperationSet(OperationSetBasicTelephony::class.java)
                telephony!!.setMute(call, isMute)
            }
        }
    }

    /**
     * Creates a new (audio-only or video) `Call` to a contact specified as a
     * `Contact` instance or a `String` contact address/identifier.
     */
    private class CreateCallThread(protocolProvider: ProtocolProviderService, contact: Contact?,
            contactResource: ContactResource?, uiContact: UIContactImpl?, stringContact: String?,
            conferenceDescription: ConferenceDescription?, chatRoom: ChatRoom?, video: Boolean) : Thread() {
        /**
         * The contact to call.
         */
        private val contact: Contact?

        /**
         * The specific contact resource to call.
         */
        private val contactResource: ContactResource?

        /**
         * The `UIContactImpl` we're calling.
         */
        private val uiContact: UIContactImpl?

        /**
         * The protocol provider through which the call goes.
         */
        private val protocolProvider: ProtocolProviderService

        /**
         * The string to call.
         */
        private val stringContact: String?

        /**
         * The description of a conference to call, if any.
         */
        private val conferenceDescription: ConferenceDescription?

        /**
         * The indicator which determines whether this instance is to create a new video (as
         * opposed to audio-only) `Call`.
         */
        private val video: Boolean

        /**
         * The chat room associated with the call.
         */
        private val chatRoom: ChatRoom?

        /**
         * Creates an instance of `CreateCallThread`.
         *
         * @param protocolProvider the protocol provider through which the call is going.
         * @param contact the contact to call
         * @param contactResource the specific `ContactResource` we're calling
         * @param video indicates if this is a video call
         */
        constructor(protocolProvider: ProtocolProviderService, contact: Contact?,
                contactResource: ContactResource?, video: Boolean) : this(protocolProvider, contact, contactResource, null, null, null, null, video) {
        }

        /**
         * Creates an instance of `CreateCallThread`.
         *
         * @param protocolProvider the protocol provider through which the call is going.
         * @param contact the contact to call
         * @param video indicates if this is a video call
         */
        constructor(protocolProvider: ProtocolProviderService, contact: String?, video: Boolean) : this(protocolProvider, null, null, null, contact, null, null, video) {}

        /**
         * Initializes a new `CreateCallThread` instance which is to create a new
         * `Call` to a conference specified via a `ConferenceDescription`.
         *
         * @param protocolProvider the `ProtocolProviderService` which is to perform
         * the establishment of the new `Call`.
         * @param conferenceDescription the description of the conference to call.
         * @param chatRoom the chat room associated with the call.
         */
        constructor(protocolProvider: ProtocolProviderService, conferenceDescription: ConferenceDescription?,
                chatRoom: ChatRoom?) : this(protocolProvider, null, null, null, null, conferenceDescription, chatRoom,
                false /* audio */) {
        }

        /**
         * Initializes a new `CreateCallThread` instance which is to create a new
         * `Call` to a contact specified either as a `Contact` instance or as a
         * `String` contact address/identifier.
         *
         * The constructor is private because it relies on its arguments being validated prior to its invocation.
         *
         * protocolProvider the ProtocolProviderService which is to perform the establishment of the new Call
         * contact the contact to call
         * contactResource the specific contact resource to call
         * uiContact the ui contact we're calling
         * stringContact the string callee to call
         * conferenceDescription the description of a conference to call
         * chatRoom the chat room associated with the call.
         * video `true` if this instance is to create a new video (as opposed to audio-only) `Call`
         */
        init {
            this.protocolProvider = protocolProvider
            this.contact = contact
            this.contactResource = contactResource
            this.uiContact = uiContact
            this.stringContact = stringContact
            this.video = video
            this.conferenceDescription = conferenceDescription
            this.chatRoom = chatRoom
        }

        override fun run() {
            if (!video) {
                // if it is not video let's check for available audio codec and available audio devices
                val mediaService = AndroidGUIActivator.mediaService
                val dev = mediaService!!.getDefaultDevice(MediaType.AUDIO, MediaUseCase.CALL)!!
                val formats = getAudioFormats(dev, protocolProvider)
                var errMsg: String? = null
                if (!dev.direction.allowsSending()) errMsg = aTalkApp.getResString(R.string.service_gui_CALL_NO_AUDIO_DEVICE)
                else if (formats.isEmpty()) {
                    errMsg = aTalkApp.getResString(R.string.service_gui_CALL_NO_AUDIO_CODEC)
                }
                if (errMsg != null) {
                    DialogActivity.showDialog(aTalkApp.globalContext,
                            R.string.service_gui_CALL, R.string.service_gui_CALL_NO_DEVICE_CODEC_H, errMsg)
                    return
                }
            }
            var contact = contact
            var stringContact = stringContact
            if (ConfigurationUtils.isNormalizePhoneNumber()
                    && !NetworkUtils.isValidIPAddress(stringContact!!)) {
                if (contact != null) {
                    stringContact = contact.address
                    contact = null
                }
                if (stringContact != null) {
                    stringContact = PhoneNumberI18nServiceImpl().normalize(stringContact)
                }
            }
            try {
                if (conferenceDescription != null) {
                    internalCall(protocolProvider, conferenceDescription, chatRoom!!)
                }
                else {
                    if (video) {
                        internalCallVideo(protocolProvider, contact, uiContact, stringContact)
                    }
                    else {
                        internalCall(protocolProvider, contact, stringContact!!, contactResource, uiContact)
                    }
                }
            } catch (t: Throwable) {
                if (t is ThreadDeath) throw t

                Timber.e(t, "The call could not be created: ")
                var message = aTalkApp.getResString(R.string.service_gui_CREATE_CALL_FAILED)

                if (t.message != null)
                    message += "\n" + t.message
                DialogActivity.showDialog(aTalkApp.globalContext, aTalkApp.getResString(R.string.service_gui_ERROR), message)
            }
        }
    }

    /**
     * Creates a desktop sharing session with the given Contact or a given String.
     */
    private class CreateDesktopSharingThread(protocolProvider: ProtocolProviderService, contact: String,
            uiContact: UIContactImpl?, mediaDevice: MediaDevice?, fullscreen: Boolean) : Thread() {
        /**
         * The string contact to share the desktop with.
         */
        private val stringContact: String

        /**
         * The protocol provider through which we share our desktop.
         */
        private val protocolProvider: ProtocolProviderService

        /**
         * The media device corresponding to the screen we would like to share.
         */
        private val mediaDevice: MediaDevice?

        /**
         * The `UIContactImpl` we're calling.
         */
        private val uiContact: UIContactImpl?

        /**
         * Whether user has selected sharing full screen or region.
         */
        private val fullscreen: Boolean

        /**
         * Creates a desktop sharing session thread.
         *
         * protocolProvider protocol provider through which we share our desktop
         * contact the contact to share the desktop with
         * uiContact the `UIContact`, which initiated the desktop sharing session
         * mediaDevice the media device corresponding to the screen we would like to share
         */
        init {
            this.protocolProvider = protocolProvider
            stringContact = contact
            this.uiContact = uiContact
            this.mediaDevice = mediaDevice
            this.fullscreen = fullscreen
        }

        override fun run() {
            val desktopSharingOpSet = protocolProvider.getOperationSet(OperationSetDesktopStreaming::class.java) ?: return

            /*
             * XXX If we are here and we just discover that OperationSetDesktopStreaming is not supported, then we're
             * already in trouble - we've already started a whole new thread just to check that a reference is null.
             */
            var exception: Throwable? = null
            var createdCall: Call<*>? = null
            try {
                createdCall = if (mediaDevice != null) {
                    desktopSharingOpSet.createVideoCall(stringContact, mediaDevice)
                }
                else desktopSharingOpSet.createVideoCall(stringContact)
            } catch (e: OperationFailedException) {
                exception = e
            } catch (e: ParseException) {
                exception = e
            }
            if (exception != null) {
                Timber.e("The call could not be created: %s", exception.message)
                DialogActivity.showDialog(aTalkApp.globalContext,
                        aTalkApp.getResString(R.string.service_gui_ERROR), exception.message)
            }
            if (uiContact != null && createdCall != null) addUIContactCall(uiContact, createdCall)
            if (createdCall != null && fullscreen) {
                // new FullScreenShareIndicator(createdCall);
            }
        }
    }

    /**
     * Answers to all `CallPeer`s associated with a specific `Call` and, optionally,
     * does that in a telephony conference with an existing `Call`.
     */
    private class AnswerCallThread(
            /**
             * The `Call` which is to be answered.
             */
            private val call: Call<*>?,
            /**
             * The existing `Call`, if any, which represents a telephony conference in which
             * [.call] is to be answered.
             */
            private val existingCall: Call<*>?,
            /**
             * The indicator which determines whether this instance is to answer [.call] with
             * video.
             */
            private val isVideoCall: Boolean) : Thread() {
        override fun run() {
            if (existingCall != null) call!!.setConference(existingCall.getConference())
            val pps = call!!.pps
            val peers = call.getCallPeers()
            while (peers.hasNext()) {
                val peer = peers.next()
                if (isVideoCall) {
                    val telephony = pps.getOperationSet(OperationSetVideoTelephony::class.java)
                    try {
                        telephony!!.answerVideoCallPeer(peer)
                    } catch (ofe: OperationFailedException) {
                        Timber.e("Could not answer %s with video because of the following exception: %s",
                                peer, ofe.message)
                    }
                }
                else {
                    val telephony = pps.getOperationSet(OperationSetBasicTelephony::class.java)
                    try {
                        telephony!!.answerCallPeer(peer)
                    } catch (ofe: OperationFailedException) {
                        Timber.e("Could not answer %s because of the following exception: %s", peer, ofe.message)
                    }
                }
            }
        }
    }

    /**
     * Invites a list of callees to a conference `Call`. If the specified `Call` is
     * `null`, creates a brand new telephony conference.
     */
    private class InviteToConferenceCallThread(callees: Map<ProtocolProviderService, List<String>>, call: Call<*>?) : Thread() {
        /**
         * The addresses of the callees to be invited into the telephony conference to be
         * organized by this instance. For further details, refer to the documentation on the
         * `callees` parameter of the respective `InviteToConferenceCallThread`
         * constructor.
         */
        private val callees: Map<ProtocolProviderService, List<String>>

        /**
         * The `Call`, if any, into the telephony conference of which [.callees] are
         * to be invited. If non-`null`, its `CallConference` state will be shared
         * with all `Call`s established by this instance for the purposes of having the
         * `callees` into the same telephony conference.
         */
        private val call: Call<*>?

        /**
         * Initializes a new `InviteToConferenceCallThread` instance which is to invite a
         * list of callees to a conference `Call`. If the specified `call` is
         * `null`, creates a brand new telephony conference.
         *
         * callees the addresses of the callees to be invited into a telephony conference. The
         * addresses are provided in multiple `List<String>`s. Each such list
         * of addresses is mapped by the `ProtocolProviderService` through which they
         * are to be invited into the telephony conference. If there are multiple
         * `ProtocolProviderService`s in the specified `Map`, the resulting
         * telephony conference is known by the name &quot;cross-protocol&quot;. It is also
         * allowed to have a list of addresses mapped to `null` which means that the
         * new instance will automatically choose
         * a `ProtocolProviderService` to invite the respective callees into the
         * telephony conference.
         * call the `Call` to invite the specified `callees` into. If `null`,
         * this instance will create a brand new telephony conference. Technically, a
         * `Call` instance is protocol/account-specific and it is possible to have
         * cross-protocol/account telephony conferences. That's why the specified
         * `callees` are invited into one and the same `CallConference`:
         * the one in which the specified `call` is participating or a new one if
         * `call` is `null`. Of course, an attempt is made to have all callees
         * from one and the same protocol/account into one `Call` instance.
         */
        init {
            this.callees = callees
            this.call = call
        }

        /**
         * Invites [.callees] into a telephony conference which is optionally specified by
         * [.call].
         */
        override fun run() {
            var conference = call?.getConference()
            for (entry in callees.entries) {
                var pps = entry.key

                /*
                 * We'd like to allow specifying callees without specifying an associated ProtocolProviderService.
                 */
                if (pps != null) {
                    pps.getOperationSet(OperationSetBasicTelephony::class.java) ?: continue
                }
                val contactList = entry.value
                val contactArray = contactList.toTypedArray()
                if (ConfigurationUtils.isNormalizePhoneNumber()) normalizePhoneNumbers(contactArray)

                /* Try to have a single Call per ProtocolProviderService. */
                var ppsCall: Call<*>?
                if (call != null && call.pps == pps) ppsCall = call
                else {
                    ppsCall = null
                    if (conference != null) {
                        val conferenceCalls = conference.calls
                        if (pps == null) {
                            /*
                             * We'd like to allow specifying callees without specifying an
                             * associated ProtocolProviderService. The simplest approach is to
                             * just choose the first ProtocolProviderService involved in the
                             * telephony conference.
                             */
                            if (call == null) {
                                if (conferenceCalls.isNotEmpty()) {
                                    ppsCall = conferenceCalls[0]
                                    pps = ppsCall.pps
                                }
                            }
                            else {
                                ppsCall = call
                                pps = ppsCall.pps
                            }
                        }
                        else {
                            for (conferenceCall in conferenceCalls) {
                                if (pps == conferenceCall.pps) {
                                    ppsCall = conferenceCall
                                    break
                                }
                            }
                        }
                    }
                }
                val telephonyConferencing = pps.getOperationSet(OperationSetTelephonyConferencing::class.java)
                try {
                    if (ppsCall == null) {
                        ppsCall = telephonyConferencing!!.createConfCall(contactArray, conference)
                        if (conference == null) conference = ppsCall.getConference()
                    }
                    else {
                        for (contact in contactArray) {
                            telephonyConferencing!!.inviteCalleeToCall(contact, ppsCall)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to invite callee: %s", contactArray.contentToString())
                    DialogActivity.showDialog(aTalkApp.globalContext,
                            aTalkApp.getResString(R.string.service_gui_ERROR), e.message)
                }
            }
        }
    }

    /**
     * Invites a list of callees to a specific conference `Call`. If the specified
     * `Call` is `null`, creates a brand new telephony conference.
     */
    private class InviteToConferenceBridgeThread(callProvider: ProtocolProviderService, callees: Array<String>, call: Call<*>?) : Thread() {
        private val callProvider: ProtocolProviderService
        private val callees: Array<String>
        private val call: Call<*>?

        init {
            this.callProvider = callProvider
            this.callees = callees
            this.call = call
        }

        override fun run() {
            val opSetVideoBridge = callProvider.getOperationSet(OperationSetVideoBridge::class.java)

            // Normally if this method is called then this should not happen
            // but we check in order to be sure to be able to proceed.
            if (opSetVideoBridge == null || !opSetVideoBridge.isActive()) return
            if (ConfigurationUtils.isNormalizePhoneNumber()) normalizePhoneNumbers(callees)
            try {
                if (call == null) {
                    opSetVideoBridge.createConfCall(callees)
                }
                else {
                    for (contact in callees) opSetVideoBridge.inviteCalleeToCall(contact, call)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to invite callee: %s", callees.toString())
                DialogActivity.showDialog(aTalkApp.globalContext,
                        aTalkApp.getResString(R.string.service_gui_ERROR), e.message)
            }
        }
    }

    /**
     * Hangs up a specific `Call` (i.e. all `CallPeer`s associated with a
     * `Call`), `CallConference` (i.e. all `Call`s participating in a
     * `CallConference`), or `CallPeer`.
     */
    private class HangupCallThread private constructor(private val call: Call<*>?, conference: CallConference?, peer: CallPeer?) : Thread() {
        private val conference: CallConference?
        private val peer: CallPeer?

        /**
         * Initializes a new `HangupCallThread` instance which is to hang up a specific
         * `Call` i.e. all `CallPeer`s associated with the `Call`.
         *
         * @param call the `Call` whose associated `CallPeer`s are to be hanged up
         */
        constructor(call: Call<*>?) : this(call, null, null) {
            // this.call = call;
        }

        /**
         * Initializes a new `HangupCallThread` instance which is to hang up a specific
         * `CallConference` i.e. all `Call`s participating in the
         * `CallConference`.
         *
         * @param conference the `CallConference` whose participating `Call`s re to be hanged up
         */
        constructor(conference: CallConference?) : this(null, conference, null)

        /**
         * Initializes a new `HangupCallThread` instance which is to hang up a specific `CallPeer`.
         *
         * @param peer the `CallPeer` to hang up
         */
        constructor(peer: CallPeer?) : this(null, null, peer)

        /**
         * Initializes a new `HangupCallThread` instance which is to hang up a specific
         * `Call`, `CallConference`, or `CallPeer`.
         *
         * call the `Call` whose associated `CallPeer`s are to be hanged up
         * conference the `CallConference` whose participating `Call`s re to be hanged up
         * peer the `CallPeer` to hang up
         */
        init {
            this.conference = conference
            this.peer = peer
        }

        override fun run() {
            /*
             * There is only an OperationSet which hangs up a CallPeer at a time so prepare a list
             * of all CallPeers to be hanged up.
             */
            val peers = HashSet<CallPeer>()
            if (call != null) {
                val peerIter = call.getCallPeers()
                while (peerIter.hasNext()) {
                    peers.add(peerIter.next())
                }
            }
            if (conference != null) {
                peers.addAll(conference.callPeers as Collection<CallPeer>)
            }
            if (peer != null) {
                peers.add(peer)
            }
            for (peer in peers) {
                val basicTelephony = peer.getProtocolProvider().getOperationSet(OperationSetBasicTelephony::class.java)
                try {
                    // Must send JingleMessage retract to close the loop if Jingle RTP has yet to start
                    if (CallState.CALL_INITIALIZATION == peer.getCall()!!.getCallState()) {
                        JingleMessageSessionImpl.sendJingleMessageRetract(peer)
                    }
                    basicTelephony!!.hangupCallPeer(peer)
                } catch (ofe: OperationFailedException) {
                    Timber.e(ofe, "Could not hang up: %s", peer)
                }
            }
            removeActiveCall(call)
        }
    }

    /**
     * Creates the EnableLocalVideoThread.
     */
    private class EnableLocalVideoThread
    /**
     * Creates the EnableLocalVideoThread.
     *
     * @param call the call, for which to enable/disable
     * @param enable allow to have LocalVideo streaming if true
     */
    (private val call: Call<*>, private val enable: Boolean) : Thread() {
        override fun run() {
            val videoTelephony = call.pps.getOperationSet(OperationSetVideoTelephony::class.java)
            if (videoTelephony != null) {
                try {
                    videoTelephony.setLocalVideoAllowed(call, enable)
                } catch (ex: OperationFailedException) {
                    Timber.e("Failed to toggle the streaming of local video. %s", ex.message)
                }
            }
        }
    }

    /**
     * Puts on hold the given `CallPeer`.
     */
    private class PutOnHoldCallPeerThread(callPeer: CallPeer, isOnHold: Boolean) : Thread() {
        private val callPeer: CallPeer
        private val isOnHold: Boolean

        init {
            this.callPeer = callPeer
            this.isOnHold = isOnHold
        }

        override fun run() {
            val telephony= callPeer.getProtocolProvider().getOperationSet(OperationSetBasicTelephony::class.java)
            try {
                if (isOnHold) telephony!!.putOnHold(callPeer) else telephony!!.putOffHold(callPeer)
            } catch (ex: OperationFailedException) {
                Timber.e(ex, "Failed to put %s %s", callPeer.getAddress(), if (isOnHold) " on hold." else " off hold. ")
            }
        }
    }

    /**
     * Merges specific existing `Call`s into a specific telephony conference.
     */
    private class MergeExistingCalls(conference: CallConference, calls: Collection<Call<*>>) : Thread() {
        /**
         * The telephony conference in which [.calls] are to be merged.
         */
        private val conference: CallConference

        /**
         * Second call.
         */
        private val calls: Collection<Call<*>>

        /**
         * Initializes a new `MergeExistingCalls` instance which is to merge specific
         * existing `Call`s into a specific telephony conference.
         *
         * conference the telephony conference in which the specified `Call`s are to be merged
         * calls the `Call`s to be merged into the specified telephony conference
         */
        init {
            this.conference = conference
            this.calls = calls
        }

        /**
         * Puts off hold the `CallPeer`s of a specific `Call` which are locally on
         * hold.
         *
         * @param call the `Call` which is to have its `CallPeer`s put off hold
         */
        private fun putOffHold(call: Call<*>) {
            val peers = call.getCallPeers()
            val telephony = call.pps.getOperationSet(OperationSetBasicTelephony::class.java)
            while (peers.hasNext()) {
                val callPeer = peers.next()
                var putOffHold = true
                if (callPeer is MediaAwareCallPeer<*, *, *>) {
                    putOffHold = callPeer.mediaHandler.isLocallyOnHold
                }
                if (putOffHold) {
                    try {
                        telephony!!.putOffHold(callPeer)
                        sleep(400)
                    } catch (ofe: Exception) {
                        Timber.e("Failed to put off hold. %s", ofe.message)
                    }
                }
            }
        }

        override fun run() {
            // conference
            for (call in conference.calls) putOffHold(call)

            // calls
            if (!calls.isEmpty()) {
                for (call in calls) {
                    if (conference.containsCall(call)) continue
                    putOffHold(call)

                    /*
                     * Dispose of the CallPanel associated with the Call which is to be merged.
                     */
                    // cmeng - closeCallContainerIfNotNecessary(conference, false);
                    call.setConference(conference)
                }
            }
        }
    }
}