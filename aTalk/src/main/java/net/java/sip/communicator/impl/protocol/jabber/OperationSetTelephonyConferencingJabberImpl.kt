/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.event.*
import net.java.sip.communicator.service.protocol.media.AbstractOperationSetTelephonyConferencing
import net.java.sip.communicator.service.protocol.media.ConferenceInfoDocument
import net.java.sip.communicator.service.protocol.media.MediaAwareCallConference
import net.java.sip.communicator.service.protocol.media.MediaAwareCallPeer
import org.atalk.util.xml.XMLException
import org.jivesoftware.smack.SmackException.NoResponseException
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.filter.StanzaFilter
import org.jivesoftware.smack.iqrequest.IQRequestHandler
import org.jivesoftware.smack.iqrequest.IQRequestHandler.Mode
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smackx.coin.CoinExtension
import org.jivesoftware.smackx.coin.CoinIQ
import org.jivesoftware.smackx.disco.packet.DiscoverInfo
import org.jivesoftware.smackx.jingle.JingleManager
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import timber.log.Timber
import java.util.*

/**
 * Implements `OperationSetTelephonyConferencing` for Jabber.
 *
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Boris Grozev
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class OperationSetTelephonyConferencingJabberImpl(parentProvider: ProtocolProviderServiceJabberImpl) : AbstractOperationSetTelephonyConferencing<ProtocolProviderServiceJabberImpl, OperationSetBasicTelephonyJabberImpl, CallJabberImpl, CallPeerJabberImpl, String>(parentProvider), RegistrationStateChangeListener, StanzaFilter {
    /**
     * The Jabber Incoming Conference Call IQRequest Handler.
     */
    private var iqRequestHandler: IQRequestHandlerImpl? = null

    /**
     * Synchronization object.
     */
    private val lock = Any()

    /**
     * Field indicates whether COIN notification are disabled or not.
     */
    private val isCoinDisabled: Boolean

    /**
     * Initializes a new `OperationSetTelephonyConferencingJabberImpl` instance which is to
     * provide telephony conferencing services for the specified Jabber
     * `ProtocolProviderService` implementation.
     *
     * parentProvider the Jabber `ProtocolProviderService` implementation which has requested the
     * creation of the new instance and for which the new instance is to provide telephony
     * conferencing services
     */
    init {
        this.parentProvider.addRegistrationStateChangeListener(this)
        isCoinDisabled = JabberActivator.getConfigurationService()!!.getBoolean(DISABLE_COIN_PROP_NAME, false)
    }

    /**
     * Implementation of method `registrationStateChange` from interface
     * RegistrationStateChangeListener for setting up (or down) our `JingleManager` when an
     * `XMPPConnection` is available
     *
     * @param evt the event received
     */
    override fun registrationStateChanged(evt: RegistrationStateChangeEvent) {
        super.registrationStateChanged(evt)
        val registrationState = evt.getNewState()
        when {
            RegistrationState.REGISTERED == registrationState -> {
                subscribeForCoinPackets()
                Timber.d("Subscribes to Coin packets")
            }
            RegistrationState.UNREGISTERED == registrationState -> {
                unsubscribeForCoinPackets()
                Timber.d("Unsubscribes to Coin packets")
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
    override fun notifyCallPeers(call: Call<*>?) {
        if (!isCoinDisabled && call!!.isConferenceFocus) {
            synchronized(lock) {
                // send conference-info to all CallPeers of the specified call.
                val i = call.getCallPeers()
                while (i.hasNext()) {
                    notify(i.next())
                }
            }
        }
    }

    /**
     * Notifies a specific `CallPeer` about changes in the telephony conference-related
     * information.
     *
     * @param callPeer the `CallPeer` to notify.
     */
    private fun notify(callPeer: CallPeer?) {
        if (callPeer !is CallPeerJabberImpl) return

        // Don't send COINs to peers with might not be ready to accept COINs yet
        val peerState = callPeer.getState()
        if (peerState == CallPeerState.CONNECTING || peerState == CallPeerState.UNKNOWN || peerState == CallPeerState.INITIATING_CALL || peerState == CallPeerState.DISCONNECTED || peerState == CallPeerState.FAILED) return
        val timeSinceLastCoin = System.currentTimeMillis() - callPeer.getLastConferenceInfoSentTimestamp()
        if (timeSinceLastCoin < COIN_MIN_INTERVAL) {
            if (callPeer.isConfInfoScheduled()) return
            Timber.i("Scheduling to send a COIN to %s", callPeer)
            callPeer.setConfInfoScheduled(true)
            Thread {
                try {
                    Thread.sleep(1 + COIN_MIN_INTERVAL - timeSinceLastCoin)
                } catch (ex: InterruptedException) {
                    ex.printStackTrace()
                }
                notify(callPeer)
            }.start()
            return
        }

        // check that callPeer supports COIN before sending him a conference-info
        val to = getBasicTelephony()!!.getFullCalleeURI(callPeer.getPeerJid())

        // XXX if this generates actual disco#info requests we might want to cache it.
        try {
            var discoverInfo: DiscoverInfo? = null
            try {
                discoverInfo = parentProvider.discoveryManager!!.discoverInfo(to)
            } catch (e: NoResponseException) {
                e.printStackTrace()
            } catch (e: NotConnectedException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            if (!discoverInfo!!.containsFeature(ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_COIN)) {
                Timber.i("%s does not support COIN", callPeer.getAddress())
                callPeer.setConfInfoScheduled(false)
                return
            }
        } catch (xmppe: XMPPException) {
            Timber.w(xmppe, "Failed to retrieve DiscoverInfo for %s", to)
        }
        val currentConfInfo = getCurrentConferenceInfo(callPeer)
        val lastSentConfInfo = callPeer.getLastConferenceInfoSent()
        val diff: ConferenceInfoDocument? = if (lastSentConfInfo == null) currentConfInfo else getConferenceInfoDiff(lastSentConfInfo, currentConfInfo!!)
        if (diff != null) {
            val newVersion = if (lastSentConfInfo == null) 1 else lastSentConfInfo.version + 1
            diff.version = newVersion
            val iq = getConferenceInfo(callPeer, diff)
            if (iq != null) {
                try {
                    parentProvider.connection!!.sendStanza(iq)
                } catch (e: NotConnectedException) {
                    Timber.e(e, "Could not send conference IQ")
                    return
                } catch (e: InterruptedException) {
                    Timber.e(e, "Could not send conference IQ")
                    return
                }

                // We save currentConfInfo, because it is of state "full", while diff could be a partial
                currentConfInfo!!.version = newVersion
                callPeer.setLastConferenceInfoSent(currentConfInfo)
                callPeer.setLastConferenceInfoSentTimestamp(System.currentTimeMillis())
            }
        }
        callPeer.setConfInfoScheduled(false)
    }

    /**
     * Generates the conference-info IQ to be sent to a specific `CallPeer` in order to
     * notify it of the current state of the conference managed by the local peer.
     *
     * @param callPeer the `CallPeer` to generate conference-info XML for
     * @param confInfo the `ConferenceInformationDocument` which is to be included in the IQ
     * @return the conference-info IQ to be sent to the specified `callPeer` in order to
     * notify it of the current state of the conference managed by the local peer
     */
    private fun getConferenceInfo(callPeer: CallPeerJabberImpl, confInfo: ConferenceInfoDocument): CoinIQ? {
        callPeer.sid ?: return null

        val coinIQ = object : CoinIQ() {
            public override fun getIQChildElementBuilder(xml: IQChildElementXmlStringBuilder): IQChildElementXmlStringBuilder {
                // strip both "conference-info" ends' tags and rebuild xml;
                val xmlString = confInfo.toString()
                        .replace("<conference-info xmlns=\"urn:ietf:params:xml:ns:conference-info\"", "")
                        .replace("</conference-info>", "")
                return xml.append(xmlString) as IQChildElementXmlStringBuilder
            }
        }
        val call = callPeer.getCall()
        coinIQ.from = call!!.sourceProvider.ourJID
        coinIQ.to = callPeer.getPeerJid()
        coinIQ.type = IQ.Type.set
        return coinIQ
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
    override fun createOutgoingCall(): CallJabberImpl {
        return CallJabberImpl(getBasicTelephony()!!, JingleManager.randomId())
    }

    /**
     * {@inheritDoc}
     *
     *
     * Implements the protocol-dependent part of the logic of inviting a callee to a `Call`.
     * The protocol-independent part of that logic is implemented by
     * [AbstractOperationSetTelephonyConferencing.inviteCalleeToCall].
     */
    @Throws(OperationFailedException::class)
    override fun doInviteCalleeToCall(calleeAddress: String, call: CallJabberImpl): CallPeer {
        return getBasicTelephony()!!.createOutgoingCall(call, calleeAddress,
                listOf(CoinExtension.getBuilder()
                        .setFocus(true)
                        .build())) as CallPeer
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
    @Throws(OperationFailedException::class)
    override fun parseAddressString(calleeAddressString: String?): String {
        return try {
            getBasicTelephony()!!.getFullCalleeURI(JidCreate.from(calleeAddressString)).toString()
        } catch (e: XmppStringprepException) {
            throw OperationFailedException("Could not parse: $calleeAddressString", 0, e)
        } catch (e: IllegalArgumentException) {
            throw OperationFailedException("Could not parse: $calleeAddressString", 0, e)
        }
    }

    /**
     * Subscribes us to notifications about incoming Coin packets.
     */
    private fun subscribeForCoinPackets() {
        iqRequestHandler = IQRequestHandlerImpl()
        parentProvider.connection!!.registerIQRequestHandler(iqRequestHandler)
    }

    /**
     * UnSubscribes us from notifications about incoming Coin packets.
     */
    private fun unsubscribeForCoinPackets() {
        val connection = parentProvider.connection
        if (connection != null && iqRequestHandler != null) {
            connection.unregisterIQRequestHandler(iqRequestHandler)
        }
    }

    /**
     * Tests whether or not the specified packet should be handled by this operation set. This
     * method is called by smack prior to packet delivery and it would only accept `CoinIQ`s.
     *
     * @param packet the packet to test.
     * @return true if and only if `packet` passes the filter.
     */
    override fun accept(packet: Stanza): Boolean {
        return packet is CoinIQ
    }

    /**
     * Handles incoming coin packets and passes them to the corresponding method based on their action.
     */
    // setup for Coin Request Handler
    inner class IQRequestHandlerImpl : IQRequestHandler {

        override fun getMode(): Mode {
            return Mode.async
        }

        override fun getType(): IQ.Type {
            return IQ.Type.set
        }

        override fun getElement(): String {
            return CoinIQ.ELEMENT
        }

        override fun getNamespace(): String {
            return CoinIQ.NAMESPACE
        }

        override fun handleIQRequest(iq: IQ?): IQ? {
            val coinIQ = iq as CoinIQ
            var errorMessage = ""

            /*
             * To prevent hijacking sessions from others, we should send the ack only if this is a
             * session-initiate with RTP content or if we are the owners of the packet's SID.
             */
            // first ack all coin "set" requests.
            when (coinIQ.type) {
                IQ.Type.set -> {
                    val ack = IQ.createResultIQ(coinIQ)
                    try {
                        parentProvider.connection!!.sendStanza(ack)
                    } catch (e: NotConnectedException) {
                        e.printStackTrace()
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
                IQ.Type.error -> {
                    val error = coinIQ.error
                    if (error != null) {
                        val msg = error.conditionText
                        errorMessage = (if (msg != null) "$msg " else "") + "Error code: " + error.condition
                    }
                    Timber.e("Received error in COIN packet. %s", errorMessage)
                    return null
                }
                else -> {}
            }

            val sid = coinIQ.sid
            if (sid != null) {
                val callPeer: CallPeerJabberImpl? = getBasicTelephony()!!.activeCallsRepository.findCallPeerBySid(sid)
                if (callPeer != null) {
                    handleCoin(callPeer, coinIQ)
                    Timber.d("Processing COIN from %s (version = %s)", coinIQ.from, coinIQ.version)
                }
            }
            return null
        }

        /**
         * Handles a specific `CoinIQ` sent from a specific `CallPeer`.
         *
         * @param callPeer the `CallPeer` from which the specified `CoinIQ` was sent
         * @param coinIQ the `CoinIQ` which was sent from the specified `callPeer`
         */
        private fun handleCoin(callPeer: CallPeerJabberImpl, coinIQ: CoinIQ) {
            val mCoinIQ = coinIQ.childElementXML
            try {
                setConferenceInfoXML(callPeer, mCoinIQ.toString())
            } catch (e: XMLException) {
                Timber.e("Could not handle received COIN from %s; ResultCoinIQ: %s", callPeer, mCoinIQ)
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     *
     * For COINs (XEP-0298), we use the attributes of the `conference-info` element to
     * piggyback a Jingle SID. This is temporary and should be removed once we choose a better way
     * to pass the SID.
     */
    override fun getCurrentConferenceInfo(callPeer: MediaAwareCallPeer<*, *, *>): ConferenceInfoDocument? {
        val confInfo = super.getCurrentConferenceInfo(callPeer)
        if (callPeer is CallPeerJabberImpl && confInfo != null) {
            confInfo.sid = callPeer.sid
        }
        return confInfo
    }

    /**
     * {@inheritDoc}
     */
    override fun getLocalEntity(callPeer: CallPeer?): String? {
        val sessionIQ = (callPeer as CallPeerJabberImpl?)!!.sessionIQ
        val from = sessionIQ!!.from

        // cmeng - local callPeer IQ's (from == null)
        if (from != null) {
            val chatRoomName = from.asBareJid()
            val opSetMUC = parentProvider
                    .getOperationSet(OperationSetMultiUserChat::class.java) as OperationSetMultiUserChatJabberImpl?
            var room: ChatRoom? = null
            if (opSetMUC != null) room = opSetMUC.getChatRoom(chatRoomName)
            Timber.i("### RoomName creation (result): %s (%s)", callPeer.toString(), chatRoomName)
            if (room != null) return "xmpp:" + chatRoomName + "/" + room.getUserNickname()
        }
        return "xmpp:" + parentProvider.ourJID
    }

    /**
     * {@inheritDoc}
     */
    override fun getLocalDisplayName(): String? {
        return null
    }

    /**
     * {@inheritDoc}
     *
     *
     * The URI of the returned `ConferenceDescription` is the occupant JID with which we have
     * joined the room.
     *
     *
     * If a Videobridge is available for our `ProtocolProviderService` we use it. TODO: this
     * should be relaxed when we refactor the Videobridge implementation, so that any Videobridge
     * (on any protocol provider) can be used.
     */
    override fun setupConference(chatRoom: ChatRoom): ConferenceDescription? {
        val videoBridge = parentProvider.getOperationSet(OperationSetVideoBridge::class.java)
        val isVideobridge = videoBridge != null && videoBridge.isActive()
        val call = CallJabberImpl(getBasicTelephony()!!, JingleManager.randomId())
        call.isAutoAnswer = true
        val uri = "xmpp:" + chatRoom.getIdentifier() + "/" + chatRoom.getUserNickname()
        val cd = ConferenceDescription(uri, call.callId)
        call.addCallChangeListener(object : CallChangeListener {
            override fun callStateChanged(evt: CallChangeEvent) {
                if (CallState.CALL_ENDED == evt.newValue) chatRoom.publishConference(null, null)
            }

            override fun callPeerRemoved(evt: CallPeerEvent) {}
            override fun callPeerAdded(evt: CallPeerEvent) {}
        })
        if (isVideobridge) {
            call.setConference(MediaAwareCallConference(true))

            // For Jitsi Videobridge we set the transports to RAW-UDP, otherwise
            // we leave them empty (meaning both RAW-UDP and ICE could be used)
            cd.addTransport(ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RAW_UDP_0)
        }
        Timber.i("Setup a conference with uri = %s and callid = %s. Videobridge in use: %s",
                uri, call.callId, isVideobridge)
        return cd
    }

    companion object {
        /**
         * The minimum interval in milliseconds between COINs sent to a single `CallPeer`.
         */
        private const val COIN_MIN_INTERVAL = 200

        /**
         * Property used to disable COIN notifications.
         */
        const val DISABLE_COIN_PROP_NAME = "protocol.jabber.DISABLE_COIN"
    }
}