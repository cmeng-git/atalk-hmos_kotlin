/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media

import net.java.sip.communicator.service.protocol.AbstractConferenceMember
import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.CallConference
import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.service.protocol.CallPeerState
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.ConferenceDescription
import net.java.sip.communicator.service.protocol.ConferenceMember
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony
import net.java.sip.communicator.service.protocol.OperationSetTelephonyConferencing
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.RegistrationState
import net.java.sip.communicator.service.protocol.event.CallChangeEvent
import net.java.sip.communicator.service.protocol.event.CallChangeListener
import net.java.sip.communicator.service.protocol.event.CallEvent
import net.java.sip.communicator.service.protocol.event.CallListener
import net.java.sip.communicator.service.protocol.event.CallPeerAdapter
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent
import net.java.sip.communicator.service.protocol.event.CallPeerEvent
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener
import org.atalk.service.neomedia.MediaDirection
import org.atalk.service.neomedia.MediaStream
import org.atalk.util.MediaType
import org.atalk.util.xml.XMLException
import org.jxmpp.stringprep.XmppStringprepException
import org.w3c.dom.Element
import org.w3c.dom.Node
import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

/**
 * Represents a default implementation of `OperationSetTelephonyConferencing` in order to
 * make it easier for implementers to provide complete solutions while focusing on
 * implementation-specific details.
 *
 * @param <ProtocolProviderServiceT>
 * @param <OperationSetBasicTelephonyT>
 * @param <MediaAwareCallT>
 * @param <MediaAwareCallPeerT>
 * @param <CalleeAddressT>
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
</CalleeAddressT></MediaAwareCallPeerT></MediaAwareCallT></OperationSetBasicTelephonyT></ProtocolProviderServiceT> */
abstract class AbstractOperationSetTelephonyConferencing<
        ProtocolProviderServiceT : ProtocolProviderService,
        OperationSetBasicTelephonyT : OperationSetBasicTelephony<ProtocolProviderServiceT>,
        MediaAwareCallT : MediaAwareCall<MediaAwareCallPeerT, OperationSetBasicTelephonyT, ProtocolProviderServiceT>,
        MediaAwareCallPeerT : MediaAwareCallPeer<MediaAwareCallT, *, ProtocolProviderServiceT>, CalleeAddressT,
        >
protected constructor(
        /**
         * The `ProtocolProviderService` implementation which created this instance and for which
         * telephony conferencing services are being provided by this instance.
         */
        protected val parentProvider: ProtocolProviderServiceT,
) : OperationSetTelephonyConferencing, RegistrationStateChangeListener, PropertyChangeListener, CallListener, CallChangeListener {

    /**
     * The `OperationSetBasicTelephony` implementation which this instance uses to carry out
     * tasks such as establishing `Call`s.
     */
    private var basicTelephony: OperationSetBasicTelephonyT? = null

    /**
     * The `CallPeerListener` which listens to modifications in the properties/state of
     * `CallPeer` so that NOTIFY requests can be sent from a conference focus to its
     * conference members to update them with the latest information about the `CallPeer`.
     */
    private val callPeerListener = object : CallPeerAdapter() {
        /**
         * Indicates that a change has occurred in the status of the source `CallPeer`.
         *
         * @param evt
         * the `CallPeerChangeEvent` instance containing the source event as well as
         * its previous and its new status
         */
        override fun peerStateChanged(evt: CallPeerChangeEvent) {
            val peer: CallPeer? = evt.getSourceCallPeer()
            if (peer != null) {
                val call: Call<*>? = peer.getCall()
                if (call != null) {
                    val state: CallPeerState? = peer.getState()
                    if (state != null && state != CallPeerState.DISCONNECTED
                            && state != CallPeerState.FAILED) {
                        notifyAll(call)
                    }
                }
            }
        }
    }

    /**
     * Initializes a new `AbstractOperationSetTelephonyConferencing` instance which is to
     * provide telephony conferencing services for the specified `ProtocolProviderService`
     * implementation.
     *
     * parentProvider the `ProtocolProviderService` implementation which has requested the creation
     * of the new instance and for which the new instance is to provide telephony
     * conferencing services
     */
    init {
        parentProvider.addRegistrationStateChangeListener(this)
    }

    /**
     * Notifies this `OperationSetTelephonyConferencing` that its `basicTelephony`
     * property has changed its value from a specific `oldValue` to a specific `newValue`
     *
     * @param oldValue the old value of the `basicTelephony` property
     * @param newValue the new value of the `basicTelephony` property
     */
    private fun basicTelephonyChanged(
            oldValue: OperationSetBasicTelephonyT?,
            newValue: OperationSetBasicTelephonyT?,
    ) {
        oldValue?.removeCallListener(this)
        newValue?.addCallListener(this)
    }

    /**
     * Notifies this `CallListener` that a specific `Call` has been established.
     *
     * @param event a `CallEvent` which specified the newly-established `Call`
     */
    private fun callBegun(event: CallEvent) {
        val call: Call<*> = event.sourceCall
        call.addCallChangeListener(this)

        /*
         * If there were any CallPeers in the Call prior to our realization that it has begun,
         * pretend that they are added afterwards.
         */
        val callPeerIter: Iterator<CallPeer?> = call.getCallPeers()
        while (callPeerIter.hasNext()) {
            callPeerAdded(CallPeerEvent(callPeerIter.next(), call,
                CallPeerEvent.CALL_PEER_ADDED))
        }
    }

    /**
     * Notifies this `CallListener` that a specific `Call` has ended.
     *
     * @param event a `CallEvent` which specified the `Call` which has just ended
     */
    override fun callEnded(event: CallEvent) {
        val call: Call<*> = event.sourceCall

        /*
         * If there are still CallPeers after our realization that it has ended, pretend that they
         * are removed before that.
         */
        val callPeerIter: Iterator<CallPeer?> = call.getCallPeers()
        while (callPeerIter.hasNext()) {
            callPeerRemoved(CallPeerEvent(callPeerIter.next(), call,
                CallPeerEvent.CALL_PEER_REMOVED))
        }
        call.removeCallChangeListener(this)
    }

    /**
     * Notifies this `CallChangeListener` that a specific `CallPeer` has been added to
     * a specific `Call`.
     *
     * @param evt a `CallPeerEvent` which specifies the `CallPeer` which has been added to
     * a `Call`
     */
    override fun callPeerAdded(evt: CallPeerEvent) {
        val callPeer = evt.getSourceCallPeer() as MediaAwareCallPeer<*, *, *>
        callPeer.addCallPeerListener(callPeerListener)
        callPeer.getMediaHandler().addPropertyChangeListener(this)
        callPeersChanged(evt)
    }

    /**
     * Notifies this `CallChangeListener` that a specific `CallPeer` has been remove
     * from a specific `Call`.
     *
     * @param evt a `CallPeerEvent` which specifies the `CallPeer` which has been removed
     * from a `Call`
     */
    override fun callPeerRemoved(evt: CallPeerEvent) {
        val callPeer = evt.getSourceCallPeer() as MediaAwareCallPeerT
        callPeer.removeCallPeerListener(callPeerListener)
        callPeer.getMediaHandler().removePropertyChangeListener(this)
        callPeersChanged(evt)
    }

    /**
     * Notifies this `CallChangeListener` that the `CallPeer` list of a specific
     * `Call` has been modified by adding or removing a specific `CallPeer`.
     *
     * @param event a `CallPeerEvent` which specifies the `CallPeer` which has been added to
     * or removed from a `Call`
     */
    private fun callPeersChanged(event: CallPeerEvent) {
        notifyAll(event.getSourceCall())
    }

    /**
     * Notifies this `CallChangeListener` that a specific `Call` has changed its
     * state. Does nothing.
     *
     * @param evt a `CallChangeEvent` which specifies the `Call` which has changed its
     * state, the very state which has been changed and the values of the state before and
     * after the change
     */
    override fun callStateChanged(evt: CallChangeEvent) {
        if (CallChangeEvent.CALL_PARTICIPANTS_CHANGE == evt.propertyName) {
            notifyAll(evt.sourceCall)
        }
    }

    /**
     * Creates a conference call with the specified callees as call peers.
     *
     * @param callees the list of addresses that we should call
     * @return the newly created conference call containing all CallPeers
     * @throws OperationFailedException if establishing the conference call fails
     * @see OperationSetTelephonyConferencing.createConfCall
     */
    @Throws(OperationFailedException::class, XmppStringprepException::class)
    override fun createConfCall(callees: Array<String>): Call<*> {
        return createConfCall(callees, null)
    }

    /**
     * Creates a conference `Call` with the specified callees as `CallPeers`.
     *
     * @param callees the list of addresses that we should call
     * @param conference the `CallConference` which represents the state of the telephony conference
     * into which the specified callees are to be invited
     * @return the newly-created conference call containing all `CallPeer`s
     * @throws OperationFailedException if establishing the conference `Call` fails
     */
    @Throws(OperationFailedException::class, XmppStringprepException::class)
    override fun createConfCall(callees: Array<String>, conference: CallConference?): Call<*> {
        var conference_ = conference
        val calleeAddresses: MutableList<CalleeAddressT> = ArrayList(callees.size)
        for (callee in callees) calleeAddresses.add(parseAddressString(callee))
        val call = createOutgoingCall()
        if (conference_ == null) conference_ = call.conference else call.setConference(conference_)
        conference_!!.isConferenceFocus = true
        for (calleeAddress in calleeAddresses) doInviteCalleeToCall(calleeAddress, call)
        return call
    }

    /**
     * Creates a new outgoing `Call` into which conference callees are to be invited by this
     * `OperationSetTelephonyConferencing`.
     *
     * @return a new outgoing `Call` into which conference callees are to be invited by this
     * `OperationSetTelephonyConferencing`
     * @throws OperationFailedException if anything goes wrong
     */
    @Throws(OperationFailedException::class)
    protected abstract fun createOutgoingCall(): MediaAwareCallT

    /**
     * Invites a callee with a specific address to join a specific `Call` for the purposes of
     * telephony conferencing.
     *
     * @param calleeAddress the address of the callee to be invited to the specified existing `Call`
     * @param call the existing `Call` to invite the callee with the specified address to
     * @return a new `CallPeer` instance which describes the signaling and the media
     * streaming of the newly-invited callee within the specified `Call`
     * @throws OperationFailedException if inviting the specified callee to the specified `Call` fails
     */
    @Throws(OperationFailedException::class)
    protected abstract fun doInviteCalleeToCall(
            calleeAddress: CalleeAddressT,
            call: MediaAwareCallT,
    ): CallPeer

    /**
     * Gets the `OperationSetBasicTelephony` implementation which this instance uses to carry
     * out tasks such as establishing `Call`s.
     *
     * @return the `OperationSetBasicTelephony` implementation which this instance uses to
     * carry out tasks such as establishing `Call`s
     */
    fun getBasicTelephony(): OperationSetBasicTelephonyT? {
        return basicTelephony
    }

    private fun getEndpointMediaProperties(endpoint: Node, properties: MutableMap<String, Any?>) {
        val endpointChildList = endpoint.childNodes
        val endpoingChildCount = endpointChildList.length
        for (endpointChildIndex in 0 until endpoingChildCount) {
            val endpointChild = endpointChildList.item(endpointChildIndex)
            if (ELEMENT_MEDIA == endpointChild.nodeName) {
                val mediaChildList = endpointChild.childNodes
                val mediaChildCount = mediaChildList.length
                var srcId: String? = null
                var status: String? = null
                var type: String? = null
                for (mediaChildIndex in 0 until mediaChildCount) {
                    val mediaChild = mediaChildList.item(mediaChildIndex)
                    val mediaChildName = mediaChild.nodeName
                    if (ELEMENT_SRC_ID == mediaChildName) srcId = mediaChild.textContent else if (ELEMENT_STATUS == mediaChildName) status = mediaChild.textContent else if (ELEMENT_TYPE == mediaChildName) type = mediaChild.textContent
                }
                if (MediaType.AUDIO.toString().equals(type, true)) {
                    properties[ConferenceMember.AUDIO_SSRC_PROPERTY_NAME] = srcId
                    properties[ConferenceMember.AUDIO_STATUS_PROPERTY_NAME] = status
                }
                else if (MediaType.VIDEO.toString().equals(type, true)) {
                    properties[ConferenceMember.VIDEO_SSRC_PROPERTY_NAME] = srcId
                    properties[ConferenceMember.VIDEO_STATUS_PROPERTY_NAME] = status
                }
            }
        }
    }

    /**
     * Reads the text content of the `status` XML element of a specific `endpoint` XML
     * element.
     *
     * @param endpoint an XML `Node` which represents the `endpoint` XML element from which to
     * get the text content of its `status` XML element
     * @return the text content of the `status` XML element of the specified
     * `endpoint` XML element if any; otherwise, `null`
     */
    private fun getEndpointStatus(endpoint: Node): String? {
        val childNodes = endpoint.childNodes
        val childCount = childNodes.length
        for (i in 0 until childCount) {
            val child = childNodes.item(i)
            if (ELEMENT_STATUS == child.nodeName) return child.textContent
        }
        return null
    }

    /**
     * Gets the remote SSRC to be reported in the conference-info XML for a specific
     * `CallPeer`'s media of a specific `MediaType`.
     *
     * @param callPeer the `CallPeer` whose remote SSRC for the media of the specified
     * `mediaType` is to be returned
     * @param mediaType the `MediaType` of the specified `callPeer`'s media whose remote SSRC is
     * to be returned
     * @return the remote SSRC to be reported in the conference-info XML for the specified
     * `callPeer`'s media of the specified `mediaType`
     */
    private fun getRemoteSourceID(callPeer: MediaAwareCallPeer<*, *, *>, mediaType: MediaType?): Long {
        var remoteSourceID = callPeer.mediaHandler!!.getRemoteSSRC(mediaType)
        if (remoteSourceID != -1L) {
            /*
             * TODO Technically, we are detecting conflicts within a Call while we should be
             * detecting them within the whole CallConference.
             */
            val call = callPeer.call
            if (call != null) {
                for (aCallPeer in call.getCallPeerList()) {
                    if (aCallPeer != callPeer) {
                        val aRemoteSourceID = aCallPeer.getMediaHandler().getRemoteSSRC(mediaType)
                        if (aRemoteSourceID == remoteSourceID) {
                            remoteSourceID = -1
                            break
                        }
                    }
                }
            }
        }
        return remoteSourceID
    }

    /**
     * Notifies this `CallListener` that a specific incoming `Call` has been received.
     *
     * @param event a `CallEvent` which specifies the newly-received incoming `Call`
     */
    override fun incomingCallReceived(event: CallEvent) {
        callBegun(event)
    }

    /**
     * Invites the callee represented by the specified uri to an already existing call. The
     * difference between this method and createConfCall is that inviteCalleeToCall allows a user to
     * transform an existing 1 to 1 call into a conference call, or add new peers to an already
     * established conference.
     *
     * @param uri the callee to invite to an existing conf call.
     * @param call the call that we should invite the callee to.
     * @return the CallPeer object corresponding to the callee represented by the specified uri.
     * @throws OperationFailedException if inviting the specified callee to the specified call fails
     */
    @Throws(OperationFailedException::class, XmppStringprepException::class)
    override fun inviteCalleeToCall(uri: String, call: Call<*>): CallPeer {
        val calleeAddress = parseAddressString(uri)
        val mediaAwareCallT = call as MediaAwareCallT
        mediaAwareCallT.conference.isConferenceFocus = true
        return doInviteCalleeToCall(calleeAddress, mediaAwareCallT)
    }

    /**
     * Notifies all `CallPeer`s associated with the telephony conference in which a specific
     * `Call` is participating about changes in the telephony conference-related information.
     *
     * @param call the `Call` which specifies the telephony conference the associated
     * `CallPeer`s of which are to be notified about changes in the telephony
     * conference-related information
     */
    protected fun notifyAll(call: Call<*>) {
        val conference = call.getConference()
        if (conference == null) notifyCallPeers(call)
        else {
            /*
             * Make each Call notify its CallPeers through its OperationSetTelephonyConferencing
             * (i.e. its protocol).
             */
            for (conferenceCall in conference.calls) {
                val opSet = conferenceCall.pps
                    .getOperationSet(OperationSetTelephonyConferencing::class.java)
                if (opSet is AbstractOperationSetTelephonyConferencing<*, *, *, *, *>) {
                    opSet.notifyCallPeers(conferenceCall)
                }
            }
        }
    }

    /**
     * Notifies all `CallPeer`s associated with a specific `Call` about changes in the
     * telephony conference-related information. In contrast, [.notifyAll] notifies all
     * `CallPeer`s associated with the telephony conference in which a specific `Call`
     * is participating.
     *
     * @param call the `Call` whose `CallPeer`s are to be notified about changes in the
     * telephony conference-related information
     */
    protected abstract fun notifyCallPeers(call: Call<*>?)

    /**
     * Notifies this `CallListener` that a specific outgoing `Call` has been created.
     *
     * @param event a `CallEvent` which specifies the newly-created outgoing `Call`
     */
    override fun outgoingCallCreated(event: CallEvent) {
        callBegun(event)
    }

    /**
     * Parses a `String` value which represents a callee address specified by the user into
     * an object which is to actually represent the callee during the invitation to a conference
     * `Call`.
     *
     * @param calleeAddressString a `String` value which represents a callee address to be parsed into an object
     * which is to actually represent the callee during the invitation to a conference
     * `Call`
     * @return an object which is to actually represent the specified `calleeAddressString`
     * during the invitation to a conference `Call`
     * @throws OperationFailedException if parsing the specified `calleeAddressString` fails
     */
    @Throws(OperationFailedException::class, XmppStringprepException::class)
    protected abstract fun parseAddressString(calleeAddressString: String?): CalleeAddressT

    /**
     * Notifies this `PropertyChangeListener` that the value of a specific property of the
     * notifier it is registered with has changed.
     *
     * @param ev a `PropertyChangeEvent` which describes the source of the event, the name of
     * the property which has changed its value and the old and new values of the property
     * @see PropertyChangeListener.propertyChange
     */
    override fun propertyChange(ev: PropertyChangeEvent) {
        val propertyName = ev.propertyName
        if (CallPeerMediaHandler.AUDIO_LOCAL_SSRC == propertyName || CallPeerMediaHandler.AUDIO_REMOTE_SSRC == propertyName || CallPeerMediaHandler.VIDEO_LOCAL_SSRC == propertyName || CallPeerMediaHandler.VIDEO_REMOTE_SSRC == propertyName) {
            val mediaHandler = ev.source as CallPeerMediaHandler<MediaAwareCallPeerT>
            val call: Call<*>? = mediaHandler.peer.call
            call?.let { notifyAll(it) }
        }
    }

    /**
     * Notifies this `RegistrationStateChangeListener` that the
     * `ProtocolProviderService` has registered with change to the registration state.
     *
     * @param evt a `RegistrationStateChangeEvent` which specifies the old and the new value of
     * the registration state of the `ProtocolProviderService` this
     * `RegistrationStateChangeListener` listens to
     */
    override fun registrationStateChanged(evt: RegistrationStateChangeEvent) {
        val newState = evt.getNewState()
        if (RegistrationState.REGISTERED == newState) {
            val basicTelephony = parentProvider.getOperationSet(OperationSetBasicTelephony::class.java) as OperationSetBasicTelephonyT
            if (this.basicTelephony != basicTelephony) {
                val oldValue: OperationSetBasicTelephonyT? = this.basicTelephony
                this.basicTelephony = basicTelephony
                basicTelephonyChanged(oldValue, this.basicTelephony)
            }
        }
        else if (RegistrationState.UNREGISTERED == newState) {
            if (basicTelephony != null) {
                val oldValue: OperationSetBasicTelephonyT? = basicTelephony
                basicTelephony = null
                basicTelephonyChanged(oldValue, null)
            }
        }
    }

    /**
     * Updates the conference-related properties of a specific `CallPeer` such as
     * `conferenceFocus` and `conferenceMembers` with the information described in
     * `confInfo`. `confInfo` must be a document with "full" state.
     *
     * @param callPeer the `CallPeer` which is a conference focus and has sent the specified
     * conference-info XML document
     * @param confInfo the conference-info XML document to use to update the conference-related information
     * of the local peer represented by the associated `Call`. It must have a "full"
     * state.
     */
    private fun setConferenceInfoDocument(
            callPeer: MediaAwareCallPeerT,
            confInfo: ConferenceInfoDocument,
    ): Int {
        val usersList = confInfo.document!!.getElementsByTagName(ELEMENT_USERS)
        val conferenceMembers = callPeer.getConferenceMembers()
        val toRemove: Array<ConferenceMember?> = conferenceMembers.toTypedArray()
        val toRemoveCount = toRemove.size
        var changed = false
        if (usersList.length > 0) {
            val userList = usersList.item(0).childNodes
            val userCount = userList.length
            val conferenceMemberProperties: MutableMap<String, Any?> = HashMap()
            for (userIndex in 0 until userCount) {
                val user = userList.item(userIndex)
                if (ELEMENT_USER != user.nodeName) continue
                val address = stripParametersFromAddress((user as Element).getAttribute("entity"))
                if (address == null || address.isEmpty()) continue

                /*
                 * Determine the ConferenceMembers who are no longer in the list i.e. are to be
                 * removed.
                 */
                var conferenceMember: AbstractConferenceMember? = null
                for (i in 0 until toRemoveCount) {
                    val aConferenceMember = toRemove[i]
                    if (aConferenceMember != null
                            && address.equals(aConferenceMember.getAddress(), ignoreCase = true)) {
                        toRemove[i] = null
                        conferenceMember = aConferenceMember as AbstractConferenceMember?
                        break
                    }
                }

                // Create the new ones.
                var addConferenceMember: Boolean
                if (conferenceMember == null) {
                    conferenceMember = AbstractConferenceMember(callPeer, address)
                    addConferenceMember = true
                }
                else addConferenceMember = false

                // Update the existing ones.
                if (conferenceMember != null) {
                    val userChildList = user.getChildNodes()
                    val userChildCount = userChildList.length
                    var displayName: String? = null
                    var endpointStatus: String? = null
                    conferenceMemberProperties[ConferenceMember.AUDIO_SSRC_PROPERTY_NAME] = null
                    conferenceMemberProperties[ConferenceMember.AUDIO_STATUS_PROPERTY_NAME] = null
                    conferenceMemberProperties[ConferenceMember.VIDEO_SSRC_PROPERTY_NAME] = null
                    conferenceMemberProperties[ConferenceMember.VIDEO_STATUS_PROPERTY_NAME] = null
                    for (userChildIndex in 0 until userChildCount) {
                        val userChild = userChildList.item(userChildIndex)
                        val userChildName = userChild.nodeName
                        if (ELEMENT_DISPLAY_TEXT == userChildName) displayName = userChild.textContent
                        else if (ELEMENT_ENDPOINT == userChildName) {
                            endpointStatus = getEndpointStatus(userChild)
                            getEndpointMediaProperties(userChild, conferenceMemberProperties)
                        }
                    }
                    conferenceMember.setDisplayName(displayName)
                    conferenceMember.setEndpointStatus(endpointStatus)
                    changed = conferenceMember.setProperties(conferenceMemberProperties)
                    if (addConferenceMember) callPeer.addConferenceMember(conferenceMember)
                }
            }
        }

        /*
         * Remove the ConferenceMember instances which are no longer present in the conference-info
         * XML document.
         */
        for (conferenceMemberToRemove in toRemove) {
            if (conferenceMemberToRemove != null) callPeer.removeConferenceMember(conferenceMemberToRemove)
        }
        if (changed) notifyAll(callPeer.call as Call<*>)
        callPeer.lastConferenceInfoReceived = confInfo
        return confInfo.version
    }

    /**
     * Updates the conference-related properties of a specific `CallPeer` such as
     * `conferenceFocus` and `conferenceMembers` with information received from it as
     * a conference focus in the form of a conference-info XML document.
     *
     * @param callPeer the `CallPeer` which is a conference focus and has sent the specified
     * conference-info XML document
     * @param conferenceInfoXML the conference-info XML document sent by `callPeer` in order to update the
     * conference-related information of the local peer represented by the associated
     * `Call`
     * @return the value of the `version` attribute of the `conference-info` XML
     * element of the specified `conferenceInfoXML` if it was successfully parsed and
     * represented in the specified `callPeer`
     * @throws XMLException If `conferenceInfoXML` couldn't be parsed as a `ConferenceInfoDocument`
     */
    @Throws(XMLException::class)
    protected fun setConferenceInfoXML(callPeer: MediaAwareCallPeerT, conferenceInfoXML: String): Int {
        val confInfo = ConferenceInfoDocument(conferenceInfoXML)

        /*
         * The CallPeer sent conference-info XML so we're sure it's a conference focus.
         */
        callPeer.setConferenceFocus(true)

        /*
         * The following implements the procedure outlined in section 4.6 of RFC4575 - Constructing
         * Coherent State
         */
        val documentVersion = confInfo.version
        val ourVersion = callPeer.getLastConferenceInfoReceivedVersion()
        val documentState = confInfo.state
        return if (ourVersion == -1) {
            if (documentState == ConferenceInfoDocument.State.FULL) {
                setConferenceInfoDocument(callPeer, confInfo)
            }
            else {
                Timber.w("Received a conference-info document with state '%s'. Cannot apply it, because we haven't initialized a local document yet. Sending peer: %s", documentState, callPeer)
                -1
            }
        }
        else if (documentVersion <= ourVersion) {
            Timber.i("Received a stale conference-info document. Local version %s, document version %s. Sending peer: %s",
                ourVersion, documentVersion, callPeer)
            -1
        }
        else  // ourVersion != -1 && ourVersion < documentVersion
        {
            if (documentState == ConferenceInfoDocument.State.FULL) setConferenceInfoDocument(callPeer, confInfo)
            else if (documentState == ConferenceInfoDocument.State.DELETED) {
                Timber.w("Received a conference-info document with state 'deleted', can't handle. Sending peer: %s",
                    callPeer)
                -1
            }
            else if (documentState == ConferenceInfoDocument.State.PARTIAL) {
                if (documentVersion == ourVersion + 1) updateConferenceInfoDocument(callPeer, confInfo)
                else {
                    /*
                     * According to RFC4575 we "MUST generate a subscription refresh request to
                     * trigger a full state notification".
                     */
                    Timber.w("Received a Conference Information document with state '%s' and version %s. Cannon apply it, because local version is %s. Sending peer: %s",
                        documentState, documentVersion, ourVersion, callPeer)
                    -1
                }
            }
            else -1 // unreachable
        }
    }

    /**
     * Creates a `ConferenceInfoDocument` which describes the current state of the conference
     * in which `callPeer` participates. The created document contains a "full" description
     * (as opposed to a partial description, see RFC4575).
     *
     * @return a `ConferenceInfoDocument` which describes the current state of the conference
     * in which this `CallPeer` participates.
     */
    protected open fun getCurrentConferenceInfo(callPeer: MediaAwareCallPeer<*, *, *>): ConferenceInfoDocument? {
        val confInfo = try {
            ConferenceInfoDocument()
        } catch (e: XMLException) {
            return null
        }
        confInfo.state = ConferenceInfoDocument.State.FULL
        confInfo.entity = getLocalEntity(callPeer)
        val call = callPeer.call ?: return null
        val conferenceCallPeers: List<CallPeer?> = CallConference.getCallPeers(call)
        confInfo.setUserCount(1 + conferenceCallPeers.size) /* the local peer/user */

        /* The local user */
        addPeerToConferenceInfo(confInfo, callPeer, false)

        /* Remote users */
        for (conferenceCallPeer in conferenceCallPeers) {
            if (conferenceCallPeer is MediaAwareCallPeer<*, *, *>) {
                addPeerToConferenceInfo(confInfo, conferenceCallPeer, true)
            }
        }
        return confInfo
    }

    /**
     * Adds a `user` element to `confInfo` which describes `callPeer`, or the
     * local peer if `remote` is `false`.
     *
     * @param confInfo the `ConferenceInformationDocument` to which to add a `user` element
     * @param callPeer the `CallPeer` which should be described
     * @param remote `true` to describe `callPeer`, or `false` to describe the local
     * peer.
     */
    private fun addPeerToConferenceInfo(
            confInfo: ConferenceInfoDocument,
            callPeer: MediaAwareCallPeer<*, *, *>, remote: Boolean,
    ) {
        val entity = if (remote) callPeer.entity
        else getLocalEntity(callPeer)
        Timber.i("### Getting entity (remote): %s (%s)", entity, remote)

        val user = confInfo.addNewUser(entity)
        val displayName = if (remote) callPeer.getDisplayName()
        else getLocalDisplayName()!!

        user.displayText = displayName
        val endpoint = user.addNewEndpoint(entity)
        endpoint.status = if (remote) getEndpointStatus(callPeer)
        else ConferenceInfoDocument.EndpointStatusType.connected

        val mediaHandler = callPeer.mediaHandler
        for (mediaType in MediaType.values()) {
            val stream: MediaStream? = mediaHandler!!.getStream(mediaType)
            if (stream != null || !remote) {
                var srcId = -1L
                if (remote) {
                    srcId = getRemoteSourceID(callPeer, mediaType)
                }
                else if (stream != null) {
                    srcId = stream.getLocalSourceID()
                }
                else  // stream == null && !remote
                {
                    /*
                     * If we are describing the local peer, but we don't have media streams with
                     * callPeer (which is the case when we send conference-info while the other side
                     * is still ringing), we can try to obtain our local SSRC from the streams we
                     * have already set up with the other participants in the conference.
                     */
                    for (otherCallPeer in callPeer.call!!.getCallPeerList()) {
                        val otherStream = otherCallPeer.mediaHandler.getStream(mediaType)
                        if (otherStream != null) {
                            srcId = otherStream.getLocalSourceID()
                            break
                        }
                    }
                }
                var direction = MediaDirection.INACTIVE
                if (remote) {
                    direction = callPeer.getDirection(mediaType).reverseDirection
                }
                else {
                    when {
                        mediaType == MediaType.AUDIO && callPeer.mediaHandler!!.isLocalAudioTransmissionEnabled ->
                            direction = direction.or(MediaDirection.SENDONLY)
                        mediaType == MediaType.VIDEO && callPeer.isLocalVideoStreaming ->
                            direction = direction.or(MediaDirection.SENDONLY)
                    }
                    if (callPeer.getDirection(mediaType).allowsReceiving())
                        direction = direction.or(MediaDirection.RECVONLY)
                }

                if (srcId != -1L || direction != MediaDirection.INACTIVE) {
                    val media = endpoint.addNewMedia(mediaType.toString())
                    media.type = mediaType.toString()
                    if (srcId != -1L) media.srcId = srcId.toString()
                    media.status = direction.toString()
                }
            }
        }
    }

    /**
     * Returns a string to be used for the `entity` attribute of the `user` element
     * for the local peer, in a Conference Information document to be sent to `callPeer`
     *
     * @param callPeer The `CallPeer` for which we are creating a Conference Information document.
     * @return a string to be used for the `entity` attribute of the `user` element
     * for the local peer, in a Conference Information document to be sent to
     * `callPeer`
     */
    protected abstract fun getLocalEntity(callPeer: CallPeer?): String?

    /**
     * Returns the display name for the local peer, which is to be used when we send Conference
     * Information.
     *
     * @return the display name for the local peer, which is to be used when we send Conference
     * Information.
     */
    protected abstract fun getLocalDisplayName(): String?

    /**
     * Gets the `EndpointStatusType` to use when describing `callPeer` in a Conference
     * Information document.
     *
     * @param callPeer the `CallPeer` which is to get its state described in a `status` XML
     * element of an `endpoint` XML element
     * @return the `EndpointStatusType` to use when describing `callPeer` in a
     * Conference Information document.
     */
    private fun getEndpointStatus(callPeer: CallPeer): ConferenceInfoDocument.EndpointStatusType? {
        val callPeerState: CallPeerState? = callPeer.getState()
        if (CallPeerState.ALERTING_REMOTE_SIDE == callPeerState) return ConferenceInfoDocument.EndpointStatusType.alerting
        if (CallPeerState.CONNECTING == callPeerState || CallPeerState.CONNECTING_WITH_EARLY_MEDIA == callPeerState) return ConferenceInfoDocument.EndpointStatusType.pending
        if (CallPeerState.DISCONNECTED == callPeerState) return ConferenceInfoDocument.EndpointStatusType.disconnected
        if (CallPeerState.INCOMING_CALL == callPeerState) return ConferenceInfoDocument.EndpointStatusType.dialing_in
        if (CallPeerState.INITIATING_CALL == callPeerState) return ConferenceInfoDocument.EndpointStatusType.dialing_out

        /*
         * RFC4575 does not list an appropriate endpoint status for "remotely on hold", e.g. the
         * endpoint is not "hearing" the conference mix, but it's media stream *is* being mixed into
         * the conference.
         *
         * We use the on-hold status anyway, because it's the one that makes the most sense.
         */
        if (CallPeerState.ON_HOLD_REMOTELY == callPeerState) return ConferenceInfoDocument.EndpointStatusType.on_hold

        /*
         * he/she is neither "hearing" the conference mix nor is his/her media being mixed in the
         * conference
         */
        if (CallPeerState.ON_HOLD_LOCALLY == callPeerState || CallPeerState.ON_HOLD_MUTUALLY == callPeerState) return ConferenceInfoDocument.EndpointStatusType.on_hold
        return if (CallPeerState.CONNECTED == callPeerState) ConferenceInfoDocument.EndpointStatusType.connected else null
    }

    /**
     * @param from A document with state `full` from which to generate a "diff".
     * @param to A document with state `full` to which to generate a "diff"
     * @return a `ConferenceInfoDocument`, such that when it is applied to `from`
     * using the procedure defined in section 4.6 of RFC4575, the result is `to`. May
     * return `null` if `from` and `to` are not found to be different
     * (that is, in case no document needs to be sent)
     */
    @Throws(IllegalArgumentException::class)
    protected fun getConferenceInfoDiff(
            from: ConferenceInfoDocument,
            to: ConferenceInfoDocument,
    ): ConferenceInfoDocument? {
        require(from.state == ConferenceInfoDocument.State.FULL) { "The 'from' document needs to have state=full" }
        require(to.state == ConferenceInfoDocument.State.FULL) { "The 'to' document needs to have state=full" }
        if (!isPartialNotificationEnabled()) {
            return if (conferenceInfoDocumentsMatch(from, to)) null else to
        }
        val diff = try {
            ConferenceInfoDocument()
        } catch (e: XMLException) {
            return if (conferenceInfoDocumentsMatch(from, to)) null else to
        }
        diff.state = ConferenceInfoDocument.State.PARTIAL
        diff.usersState = ConferenceInfoDocument.State.PARTIAL

        // temporary, used for xmpp only
        val sid = to.sid
        if (sid != null && sid != "") diff.sid = to.sid
        diff.userCount = to.userCount
        diff.entity = to.entity
        var needsPartial = false
        var hasDifference = false
        if (from.entity != to.entity || from.userCount != to.userCount) {
            hasDifference = true
        }

        // find users which have been removed in 'to'
        for (user in from.usersList) {
            if (to.getUser(user.entity) == null) {
                val deletedUser = diff.addNewUser(user.entity)
                deletedUser.state = ConferenceInfoDocument.State.DELETED
                hasDifference = true
                needsPartial = true
            }
        }
        for (toUser in to.usersList) {
            val fromUser = from.getUser(toUser.entity)
            if (!usersMatch(toUser, fromUser)) {
                hasDifference = true
                diff.addUser(toUser)
            }
            else {
                // if there is a "user" element which didn't change, we skip it
                // and we need to send state=partial, because otherwise it will
                // be removed by the recipient
                needsPartial = true
            }
        }
        Timber.d("Generated partial notification. From: %s\nTo: %s\nDiff: %s(hasDifference: %s",
            from, to, diff, hasDifference)
        if (!hasDifference) return null

        /*
         * In some cases (when all the user elements have changed, and none have been removed) we
         * are essentially generating a full document, but marking it 'partial'. In this case it is
         * better to send the full document, just in case the receiver lost the previous document
         * somehow.
         */
        if (!needsPartial) {
            diff.state = ConferenceInfoDocument.State.FULL
            diff.usersState = ConferenceInfoDocument.State.FULL
        }
        return diff
    }

    /**
     * Updates the conference-related properties of a specific `CallPeer` such as
     * `conferenceFocus` and `conferenceMembers` with information received from it as
     * a conference focus in the form of a partial conference-info XML document.
     *
     * @param callPeer the `CallPeer` which is a conference focus and has sent the specified partial
     * conference-info XML document
     * @param diff the partial conference-info XML document sent by `callPeer` in order to update
     * the conference-related information of the local peer represented by the associated
     * `Call`
     * @return the value of the `version` attribute of the `conference-info` XML
     * element of the specified `conferenceInfoXML` if it was successfully parsed and
     * represented in the specified `callPeer`
     */
    private fun updateConferenceInfoDocument(callPeer: MediaAwareCallPeerT, diff: ConferenceInfoDocument): Int {
        // "apply" diff to ourDocument, result is in newDocument
        val ourDocument = callPeer.lastConferenceInfoReceived
        val newDocument: ConferenceInfoDocument
        when (diff.usersState) {
            ConferenceInfoDocument.State.FULL -> {
                // if users is 'full', all its children must be full
                newDocument = try {
                    ConferenceInfoDocument(diff)
                } catch (e: XMLException) {
                    Timber.e("Could not create a new ConferenceInfoDocument")
                    return -1
                }
                newDocument.state = ConferenceInfoDocument.State.FULL
            }
            ConferenceInfoDocument.State.DELETED -> {
                newDocument = try {
                    ConferenceInfoDocument()
                } catch (e: XMLException) {
                    Timber.e(e, "Could not create a new ConferenceInfoDocument")
                    return -1
                }
                newDocument.version = diff.version
                newDocument.entity = diff.entity
                newDocument.userCount = diff.userCount
            }
            else  // 'partial'
            -> {
                newDocument = try {
                    ConferenceInfoDocument(ourDocument!!)
                } catch (e: XMLException) {
                    Timber.e(e, "Could not create a new ConferenceInfoDocument")
                    return -1
                }
                newDocument.version = diff.version
                newDocument.entity = diff.entity
                newDocument.userCount = diff.userCount
                for (user in diff.usersList) {
                    when (user.state) {
                        ConferenceInfoDocument.State.FULL -> {
                            newDocument.removeUser(user.entity)
                            newDocument.addUser(user)
                        }
                        ConferenceInfoDocument.State.DELETED -> {
                            newDocument.removeUser(user.entity)
                        }
                        else  // partial
                        -> {
                            val ourUser = newDocument.getUser(user.entity)
                            ourUser!!.displayText = user.displayText
                            for (endpoint in user.endpoints) {
                                when (endpoint.state) {
                                    ConferenceInfoDocument.State.FULL -> {
                                        ourUser.removeEndpoint(endpoint.entity)
                                        ourUser.addEndpoint(endpoint)
                                    }
                                    ConferenceInfoDocument.State.DELETED -> {
                                        ourUser.removeEndpoint(endpoint.entity)
                                    }
                                    else  // partial
                                    -> {
                                        val ourEndpoint = ourUser.getEndpoint(endpoint.entity)
                                        for (media in endpoint.medias) {
                                            ourEndpoint!!.removeMedia(media.id)
                                            ourEndpoint.addMedia(media)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Timber.d("Applied a partial conference-info notification. Base: %s\nDiff: %s\nResult: %s",
            ourDocument, diff, newDocument)
        return setConferenceInfoDocument(callPeer, newDocument)
    }

    /**
     * @param a A document with state `full` which to compare to `b`
     * @param b A document with state `full` which to compare to `a`
     * @return `false` if the two documents are found to be different, `true`
     * otherwise (that is, it can return true for non-identical documents).
     */
    private fun conferenceInfoDocumentsMatch(a: ConferenceInfoDocument, b: ConferenceInfoDocument): Boolean {
        require(a.state == ConferenceInfoDocument.State.FULL) { "The 'a' document needs to have state=full" }
        require(b.state == ConferenceInfoDocument.State.FULL) { "The 'b' document needs to have state=full" }
        if (!stringsMatch(a.entity, b.entity)) return false else if (a.userCount != b.userCount) return false else if (a.usersList.size != b.usersList.size) return false
        for (aUser in a.usersList) {
            if (!usersMatch(aUser, b.getUser(aUser.entity))) return false
        }
        return true
    }

    /**
     * Checks whether two `ConferenceInfoDocument.User` instances match according to the
     * needs of our implementation. Can return `true` for users which are not identical.
     *
     * @param a A `ConferenceInfoDocument.User` to compare
     * @param b A `ConferenceInfoDocument.User` to compare
     * @return `false` if `a` and `b` are found to be different in a way that
     * is significant for our needs, `true` otherwise.
     */
    private fun usersMatch(a: ConferenceInfoDocument.User?, b: ConferenceInfoDocument.User?): Boolean {
        if (a == null && b == null) return true else if (a == null || b == null) return false else if (!stringsMatch(a.entity, b.entity)) return false else if (!stringsMatch(a.displayText, b.displayText)) return false else if (a.endpoints.size != b.endpoints.size) return false
        for (aEndpoint in a.endpoints) {
            if (!endpointsMatch(aEndpoint, b.getEndpoint(aEndpoint.entity))) return false
        }
        return true
    }

    /**
     * Checks whether two `ConferenceInfoDocument.Endpoint` instances match according to the
     * needs of our implementation. Can return `true` for endpoints which are not identical.
     *
     * @param a A `ConferenceInfoDocument.Endpoint` to compare
     * @param b A `ConferenceInfoDocument.Endpoint` to compare
     * @return `false` if `a` and `b` are found to be different in a way that
     * is significant for our needs, `true` otherwise.
     */
    private fun endpointsMatch(
            a: ConferenceInfoDocument.Endpoint?,
            b: ConferenceInfoDocument.Endpoint?,
    ): Boolean {
        if (a == null && b == null) return true else if (a == null || b == null) return false else if (!stringsMatch(a.entity, b.entity)) return false else if (a.status != b.status) return false else if (a.medias.size != b.medias.size) return false
        for (aMedia in a.medias) {
            if (!mediasMatch(aMedia, b.getMedia(aMedia.id))) return false
        }
        return true
    }

    /**
     * Checks whether two `ConferenceInfoDocument.Media` instances match according to the
     * needs of our implementation. Can return `true` for endpoints which are not identical.
     *
     * @param a A `ConferenceInfoDocument.Media` to compare
     * @param b A `ConferenceInfoDocument.Media` to compare
     * @return `false` if `a` and `b` are found to be different in a way that
     * is significant for our needs, `true` otherwise.
     */
    private fun mediasMatch(a: ConferenceInfoDocument.Media?, b: ConferenceInfoDocument.Media?): Boolean {
        return if (a == null && b == null) true else if (a == null || b == null) false else if (!stringsMatch(a.id, b.id)) false else if (!stringsMatch(a.srcId, b.srcId)) false else if (!stringsMatch(a.type, b.type)) false else stringsMatch(a.status, b.status)
    }

    /**
     * @param a A `String` to compare to `b`
     * @param b A `String` to compare to `a`
     * @return `true` if and only if `a` and `b` are both `null`, or
     * they are equal as `String`s
     */
    private fun stringsMatch(a: String?, b: String?): Boolean {
        if (a == null && b == null) return true else if (a == null || b == null) return false
        return a == b
    }

    /**
     * Checks whether sending of RFC4575 partial notifications is enabled in the configuration. If
     * disabled, RFC4575 documents will always be sent with state 'full'.
     *
     * @return `true` if sending of RFC4575 partial notifications is enabled in the
     * configuration.
     */
    private fun isPartialNotificationEnabled(): Boolean {
        val s: String? = parentProvider.accountID.accountProperties[PARTIAL_NOTIFICATIONS_PROP_NAME]
        return s == null || java.lang.Boolean.parseBoolean(s)
    }

    /**
     * {@inheritDoc}
     *
     * Unimplemented by default, returns `null`.
     */
    override fun setupConference(chatRoom: ChatRoom): ConferenceDescription? {
        return null
    }

    companion object {
        /**
         * The name of the conference-info XML element `display-text`.
         */
        protected const val ELEMENT_DISPLAY_TEXT = "display-text"

        /**
         * The name of the conference-info XML element `endpoint`.
         */
        protected const val ELEMENT_ENDPOINT = "endpoint"

        /**
         * The name of the conference-info XML element `media`.
         */
        protected const val ELEMENT_MEDIA = "media"

        /**
         * The name of the conference-info XML element `src-id`.
         */
        protected const val ELEMENT_SRC_ID = "src-id"

        /**
         * The name of the conference-info XML element `status`.
         */
        protected const val ELEMENT_STATUS = "status"

        /**
         * The name of the conference-info XML element `type`.
         */
        protected const val ELEMENT_TYPE = "type"

        /**
         * The name of the conference-info XML element `user`.
         */
        protected const val ELEMENT_USER = "user"

        /**
         * The name of the conference-info XML element `users`.
         */
        protected const val ELEMENT_USERS = "users"

        /**
         * The name of the account property which specifies whether we should generate and send RFC4575
         * partial notifications (as opposed to always sending 'full' documents)
         */
        private const val PARTIAL_NOTIFICATIONS_PROP_NAME = "RFC4575_PARTIAL_NOTIFICATIONS_ENABLED"

        /**
         * Removes the parameters (specified after a semicolon) from a specific address `String`
         * if any are present in it.
         *
         * @param address the `String` value representing an address from which any parameters are to be
         * removed
         * @return a `String` representing the specified `address` without any parameters
         */
        fun stripParametersFromAddress(address: String?): String? {
            var iAddress = address
            if (iAddress != null) {
                val parametersBeginIndex = iAddress.indexOf(';')
                if (parametersBeginIndex > -1) iAddress = iAddress.substring(0, parametersBeginIndex)
            }
            return iAddress
        }
    }
}