/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import android.text.TextUtils
import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.event.*
import net.java.sip.communicator.service.protocol.media.AbstractOperationSetBasicTelephony
import net.java.sip.communicator.service.protocol.media.MediaAwareCallPeer
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.call.JingleMessageSessionImpl
import org.atalk.hmos.gui.call.JingleMessageSessionImpl.JmStateListener
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.jivesoftware.smack.SmackException.*
import org.jivesoftware.smack.StanzaListener
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smack.filter.*
import org.jivesoftware.smack.packet.ExtensionElement
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smackx.coin.CoinExtension
import org.jivesoftware.smackx.confdesc.CallIdExtension
import org.jivesoftware.smackx.disco.packet.DiscoverInfo
import org.jivesoftware.smackx.jingle.JingleManager
import org.jivesoftware.smackx.jingle.JingleUtil
import org.jivesoftware.smackx.jingle.element.Jingle
import org.jivesoftware.smackx.jingle.element.JingleAction
import org.jivesoftware.smackx.jingle.element.JingleReason
import org.jivesoftware.smackx.jingle_rtp.BasicTelephony
import org.jivesoftware.smackx.jingle_rtp.JingleCallManager
import org.jivesoftware.smackx.jingle_rtp.JingleCallSessionImpl
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransport
import org.jivesoftware.smackx.jingle_rtp.element.SdpTransfer
import org.jivesoftware.smackx.jinglemessage.JingleMessageType
import org.jxmpp.jid.EntityFullJid
import org.jxmpp.jid.FullJid
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import org.jxmpp.util.XmppStringUtils
import timber.log.Timber

/**
 * Implements all call management logic including call transfer, and exports basic telephony support
 * by implementing `OperationSetBasicTelephony`.
 *
 * @author Emil Ivov
 * @author Symphorien Wanko
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
/**
 * A reference to the `ProtocolProviderServiceJabberImpl` instance that created us.
 */
class OperationSetBasicTelephonyJabberImpl(
        private val mPPS: ProtocolProviderServiceJabberImpl,
) : AbstractOperationSetBasicTelephony<ProtocolProviderServiceJabberImpl>(),
        RegistrationStateChangeListener, OperationSetSecureSDesTelephony, OperationSetSecureZrtpTelephony,
        JmStateListener, StanzaListener, BasicTelephony, OperationSetAdvancedTelephony<ProtocolProviderServiceJabberImpl> {
    private var mJingleSession: JingleCallSessionImpl? = null

    /**
     * Jingle session-initiate sid; set either via JingleMessage or initSid via legacy call without JingleMessage support.
     * The JingleMessage Sid must be used for Jingle session-initiate sid, else call is rejected by conversations.
     */
    private var mSid: String? = null
    private var mConnection: XMPPConnection? = null
    /**
     * Returns a reference to the [ActiveCallsRepositoryJabberImpl] that we are currently using.
     *
     * @return a reference to the [ActiveCallsRepositoryJabberImpl] that we are currently using.
     */
    /**
     * Contains references for all currently active (non ended) calls.
     */
    val activeCallsRepository = ActiveCallsRepositoryJabberImpl(this)

    /**
     * Implementation of method `registrationStateChange` from interface RegistrationStateChangeListener
     * for setting up (or down) our `JingleManager` when an `XMPPConnection` is available
     *
     * @param evt the event received
     */
    override fun registrationStateChanged(evt: RegistrationStateChangeEvent) {
        val registrationState = evt.getNewState()
        if (registrationState === RegistrationState.REGISTERED) {
            mConnection = mPPS.connection
            JingleCallManager.getInstanceFor(mConnection, this)
            JingleMessageSessionImpl.addJmStateListener(this)
            val RESPONDER_ERROR_FILTER = AndFilter(IQTypeFilter.ERROR, ToMatchesFilter.createFull(mConnection!!.user))
            mConnection!!.removeAsyncStanzaListener(this)
            mConnection!!.addAsyncStanzaListener(this, RESPONDER_ERROR_FILTER)
        }
        else if (registrationState === RegistrationState.UNREGISTERED) {
            if (mConnection != null) {
                mConnection!!.removeAsyncStanzaListener(this)
            }
            JingleMessageSessionImpl.removeJmStateListener(this)
        }
    }

    /**
     * Creates a new `Call` and invites a specific `CallPeer` to it given by her `String` URI.
     *
     * @param callee the address of the callee who we should invite to a new `Call`
     * @param conference the `CallConference` in which the newly-created `Call` is to participate
     *
     * @return a newly created `Call`. The specified `callee` is available in the
     * `Call` as a `CallPeer`
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     * @see OperationSetBasicTelephony.createCall
     */
    @Throws(OperationFailedException::class)
    override fun createCall(callee: String, conference: CallConference?): Call<*>? {
        val call = CallJabberImpl(this, sid)
        if (conference != null)
            call.setConference(conference)

        val callPeer = createOutgoingCall(call, callee, null)
                ?: throw OperationFailedException("Failed to create outgoing call because no peer was created",
                    OperationFailedException.INTERNAL_ERROR)

        val callOfCallPeer = callPeer.getCall()
        // We may have a Google Talk call here.
        if (callOfCallPeer != call && conference != null)
            callOfCallPeer!!.setConference(conference)

        return callOfCallPeer
    }

    /**
     * {@inheritDoc}
     *
     * Creates a new `CallJabberImpl` and initiates a jingle session to the JID obtained
     * from the `uri` of `cd`.
     *
     * If `cd` contains a `callid`, adds the "callid" element as an extension to the session-initiate IQ.
     * Uses the supported transports of `cd`
     */
    @Throws(OperationFailedException::class)
    override fun createCall(cd: ConferenceDescription?, chatRoom: ChatRoom?): CallJabberImpl {
        val call = CallJabberImpl(this, sid)
        (chatRoom as ChatRoomJabberImpl?)!!.addConferenceCall(call)
        call.addCallChangeListener(object : CallChangeListener {
            override fun callPeerAdded(evt: CallPeerEvent) {}
            override fun callPeerRemoved(evt: CallPeerEvent) {}
            override fun callStateChanged(evt: CallChangeEvent) {
                if (CallState.CALL_ENDED == evt.newValue) {
                    chatRoom!!.removeConferenceCall(call)
                }
            }
        })
        var remoteUri = cd!!.getUri()
        if (remoteUri!!.startsWith("xmpp:")) remoteUri = remoteUri.substring(5)
        val remoteJid = try {
            JidCreate.fullFrom(remoteUri)
        } catch (e: XmppStringprepException) {
            throw OperationFailedException("Invalid remote JID", OperationFailedException.GENERAL_ERROR, e)
        }
        val sessionInitiateExtensions = ArrayList<ExtensionElement?>(2)
        val callid = cd.getCallId()
        if (callid != null) {
            sessionInitiateExtensions.add(CallIdExtension(callid))
        }

        // String password = cd.getPassword();
        // if (password != null)
        // extensions.add(new PasswordPacketExtension(password));
        call.initiateSession(remoteJid, null, sessionInitiateExtensions, cd.getSupportedTransports())
        return call
    }

    /**
     * Return a new generated random jingle session Id or retrieve it from mJingleSession if call handle via JingleMessage
     *
     * @return Jingle session Id
     */
    val sid: String
        get() = if (mSid != null) mSid!! else JingleManager.randomId()

    /**
     * init mSid with a randomId if callee does not support JingleMessage protocol
     */
    fun initSid() {
        mSid = JingleManager.randomId()
    }

    /**
     * Init and establish the specified call.
     *
     * @param call the `CallJabberImpl` that will be used to initiate the call
     * @param calleeAddress the address of the callee that we'd like to connect with.
     * @param siChildElement a collection of additional and optional `ExtensionElement`s to be
     * added to the `session-initiate` [Jingle] which is to init the specified `call`
     *
     * @return the `CallPeer` that represented by the specified uri. All following state
     * change events will be delivered through that call peer. The `Call` that this
     * peer is a member of could be retrieved from the `CallPeer` instance with the
     * use of the corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the call.
     */
    @Throws(OperationFailedException::class)
    fun createOutgoingCall(
            call: CallJabberImpl?, calleeAddress: String?,
            siChildElement: Iterable<ExtensionElement?>?,
    ): AbstractCallPeer<*, *>? {
        var calleeJid: FullJid? = null
        if (calleeAddress!!.contains("/")) {
            try {
                calleeJid = JidCreate.fullFrom(calleeAddress)
            } catch (e: XmppStringprepException) {
                e.printStackTrace()
                return null
            }
        }
        return createOutgoingCall(call, calleeAddress, calleeJid, siChildElement)
    }

    @Throws(OperationFailedException::class)
    fun createOutgoingCall(
            call: CallJabberImpl?, calleeJid: FullJid,
            siChildElement: Iterable<ExtensionElement?>?,
    ): AbstractCallPeer<*, *> {
        return createOutgoingCall(call, calleeJid.toString(), calleeJid, siChildElement)
    }

    /**
     * Init and establish the specified call.
     *
     * @param call the `CallJabberImpl` that will be used to initiate the call
     * @param calleeAddress_ the address of the callee that we'd like to connect with.
     * @param fullCalleeURI_ the full Jid address, which if specified would explicitly initiate a call to this full address
     * @param sessionInitiateExtensions a collection of additional and optional `ExtensionElement`s to be
     * added to the `session-initiate` [Jingle] which is to init the specified `call`
     *
     * @return the `CallPeer` that represented by the specified uri. All following state
     * change events will be delivered through that call peer. The `Call` that this
     * peer is a member of could be retrieved from the `CallPeer` instance with the
     * use of the corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the call.
     */
    @Throws(OperationFailedException::class)
    fun createOutgoingCall(
            call: CallJabberImpl?, calleeAddress_: String?,
            fullCalleeURI_: FullJid?, sessionInitiateExtensions: Iterable<ExtensionElement?>?,
    ): AbstractCallPeer<*, *> {
        var calleeAddress = calleeAddress_
        var fullCalleeURI = fullCalleeURI_
        Timber.i("Creating outgoing call to %s", calleeAddress)
        if (mConnection == null || call == null) {
            throw OperationFailedException("Failed to create Outgoing Jingle Session - NO valid XMPPConnection.",
                OperationFailedException.INTERNAL_ERROR)
        }
        val isGoogle = mPPS.isGmailOrGoogleAppsAccount
        var isGoogleVoice = calleeAddress!!.endsWith(GOOGLE_VOICE_DOMAIN)
        if (isGoogle && !calleeAddress.contains("@")) {
            calleeAddress += "@$GOOGLE_VOICE_DOMAIN"
            isGoogleVoice = true
        }

        // if address is not suffixed by @domain, append address with the domain corresponding
        // to the OVERRIDE_PHONE_SUFFIX property if defined or user account service domain
        val accountJID = getProtocolProvider().accountID
        val telephonyDomain = accountJID.getOverridePhoneSuffix()
        if (!calleeAddress.contains("@")) {
            var serviceName = telephonyDomain
            if (TextUtils.isEmpty(serviceName)) serviceName = XmppStringUtils.parseDomain(accountJID.mUserID)
            calleeAddress += "@$serviceName"
        }
        val isTelephonyCall = telephonyDomain != null && calleeAddress.endsWith(telephonyDomain)

        // getAccountPropertyString(JabberAccountID.TELEPHONY_BYPASS_GTALK_CAPS);
        val bypassDomain = accountJID.getTelephonyDomainBypassCaps()
        val alwaysCallGtalk = bypassDomain != null && bypassDomain == XmppStringUtils.parseDomain(
            calleeAddress) || isGoogleVoice
        var calleeJid: Jid? = null
        try {
            calleeJid = JidCreate.from(calleeAddress)
        } catch (e: XmppStringprepException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
        var isPrivateMessagingContact = false
        val mucOpSet = getProtocolProvider().getOperationSet(OperationSetMultiUserChat::class.java)
        if (mucOpSet != null) {
            isPrivateMessagingContact = mucOpSet.isPrivateMessagingContact(calleeJid)
        }

        // Throw exception if the call is none of the above criteria check
        if ((!Roster.getInstanceFor(mConnection).contains(calleeJid!!.asBareJid())
                        && !isPrivateMessagingContact) && !alwaysCallGtalk && !isTelephonyCall) {
            throw OperationFailedException(aTalkApp.getResString(R.string.service_gui_NOT_IN_CALLGROUP,
                calleeAddress), OperationFailedException.FORBIDDEN)
        }
        var fullCalleeJid: Jid? = fullCalleeURI
        var discoInfoJid: Jid? = fullCalleeURI
        if (fullCalleeURI == null) {
            /*
             * @ see XEP-0100: Gateway Interaction - must also confirm to XEP-0115 for smack to accept disco#info
             * 1. use calleeJid for fullCalleeURI
             * 2. use gateway Jid to get disco#info from server
             */
            if (isTelephonyCall) {
                fullCalleeJid = calleeJid

                // pbx gateway has only the domain part and without resource being specified.
                try {
                    discoInfoJid = JidCreate.domainBareFrom(telephonyDomain)
                } catch (e: XmppStringprepException) {
                    Timber.w("DomainJid creation failed for: %s", telephonyDomain)
                }
                if (discoInfoJid == null) {
                    throw OperationFailedException(aTalkApp.getResString(R.string.service_gui_NOT_IN_ROSTER,
                        telephonyDomain), OperationFailedException.ILLEGAL_ARGUMENT)
                }
            }
            else {
                fullCalleeURI = discoverFullJid(calleeJid)
                if (fullCalleeURI != null) {
                    fullCalleeJid = fullCalleeURI
                    discoInfoJid = fullCalleeURI
                }
                else {
                    if (telephonyDomain != null) throw OperationFailedException(
                        aTalkApp.getResString(R.string.service_gui_NOT_IN_ROSTER,
                            telephonyDomain), OperationFailedException.ILLEGAL_ARGUMENT)
                    else throw OperationFailedException(aTalkApp.getResString(R.string.service_gui_INVALID_ADDRESS,
                        fullCalleeJid), OperationFailedException.ILLEGAL_ARGUMENT)
                }
            }
        }
        var di: DiscoverInfo? = null
        try {
            // check if the remote client supports telephony.
            di = mPPS.discoveryManager!!.discoverInfo(discoInfoJid!!)
        } catch (ex: XMPPException) {
            Timber.w(ex, "could not retrieve info for %s", discoInfoJid)
        } catch (ex: NoResponseException) {
            Timber.w(ex, "could not retrieve info for %s", discoInfoJid)
        } catch (ex: NotConnectedException) {
            Timber.w(ex, "could not retrieve info for %s", discoInfoJid)
        } catch (ex: InterruptedException) {
            Timber.w(ex, "could not retrieve info for %s", discoInfoJid)
        }
        if (di === null) {
            Timber.i("%s: jingle not supported?", discoInfoJid)
            throw OperationFailedException(aTalkApp.getResString(R.string.service_gui_NO_JINGLE_SUPPORT,
                discoInfoJid), OperationFailedException.NOT_SUPPORTED_OPERATION)
        }

        /*
         * in case we figure that calling people without a resource id is impossible, we'll have to
         * uncomment the following lines. keep in mind that this would mean - no calls to PSTN
         * though if (fullCalleeURI.indexOf('/') < 0) { throw new OperationFailedException(
         * "Failed to create OutgoingJingleSession.\nUser calleeAddress is unknown to us.",
         * OperationFailedException.INTERNAL_ERROR); }
         */

        // initiate call
        val peer: AbstractCallPeer<*, *>
        try {
            peer = call.initiateSession(fullCalleeJid!!.asFullJidIfPossible(), di, sessionInitiateExtensions, null)
        } catch (t: Throwable) {
            /*
             * The Javadoc on ThreadDeath says: If ThreadDeath is caught by a method, it is
             * important that it be rethrown so that the thread actually dies.
             */
            if (t is ThreadDeath) throw t
            else {
                if (telephonyDomain != null) throw OperationFailedException(
                    aTalkApp.getResString(R.string.service_gui_NOT_IN_ROSTER,
                        telephonyDomain), OperationFailedException.ILLEGAL_ARGUMENT)
                else {
                    var message: String? = t.toString()
                    if (t.cause != null) {
                        message = t.cause!!.message
                    }
                    else if (t.message != null) {
                        message = t.message
                    }
                    Timber.e("Initiate call session Exception: %s", message)
                    throw OperationFailedException(message, OperationFailedException.NETWORK_FAILURE)
                }
            }
        }
        return peer
    }

    /**
     * Discovers the resource for `calleeAddress` with the highest priority which supports
     * either Jingle or Gtalk. Returns the full JID.
     *
     * @param calleeAddress the bareJid of the callee
     *
     * @return the full callee URI (Jid)
     */
    private fun discoverFullJid(calleeAddress: Jid?): EntityFullJid? {
        var discoverInfo: DiscoverInfo? = null
        var jabberStatus: PresenceStatus? = null
        var bestPriority = -1
        var calleeURI: Jid
        var fullCalleeURI: Jid? = null
        val it = Roster.getInstanceFor(mConnection).getPresences(calleeAddress!!.asBareJid())
        for (presence in it) {
            val priority = if (presence.priority == Int.MIN_VALUE) 0 else presence.priority
            calleeURI = presence.from
            try {
                // check if the remote client supports telephony.
                discoverInfo = mPPS.discoveryManager!!.discoverInfo(calleeURI)
            } catch (ex: XMPPException) {
                Timber.w(ex, "could not retrieve info for: %s", fullCalleeURI)
            } catch (ex: NoResponseException) {
                Timber.w(ex, "could not retrieve info for: %s", fullCalleeURI)
            } catch (ex: NotConnectedException) {
                Timber.w(ex, "could not retrieve info for: %s", fullCalleeURI)
            } catch (ex: InterruptedException) {
                Timber.w(ex, "could not retrieve info for: %s", fullCalleeURI)
            }
            if (discoverInfo !== null && discoverInfo.containsFeature(
                        ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE)) {
                if (priority > bestPriority) {
                    bestPriority = priority
                    fullCalleeURI = calleeURI
                    jabberStatus = OperationSetPersistentPresenceJabberImpl
                            .jabberStatusToPresenceStatus(presence, mPPS)
                }
                else if (priority == bestPriority && jabberStatus != null) {
                    val tempStatus = OperationSetPersistentPresenceJabberImpl
                            .jabberStatusToPresenceStatus(presence, mPPS)
                    if (tempStatus > jabberStatus) {
                        fullCalleeURI = calleeURI
                        jabberStatus = tempStatus
                    }
                }
            }
        }
        Timber.i("Callee jid for outgoing call: %s, priority %s", fullCalleeURI, bestPriority)
        return fullCalleeURI?.asEntityFullJidIfPossible()
    }

    /**
     * Gets the full callee URI for a specific callee address.
     *
     * @param calleeJid the callee address to get the full callee URI for
     *
     * @return the full callee URI for the specified `calleeAddress`
     */
    fun getFullCalleeURI(calleeJid: Jid?): EntityFullJid {
        return if (calleeJid!!.isEntityFullJid) calleeJid.asEntityFullJidIfPossible()
        else Roster.getInstanceFor(mConnection).getPresence(calleeJid.asBareJid()).from.asEntityFullJidIfPossible()
    }

    /**
     * Gets the full callee URI for a specific callee address.
     *
     * @param calleeAddress the callee address to get the full callee URI for
     *
     * @return the full callee URI for the specified <tt>calleeAddress</tt>
     */
    private fun getFullCalleeURI(calleeAddress: String?): EntityFullJid {
        return try {
            val calleeJid = JidCreate.from(calleeAddress)
            getFullCalleeURI(calleeJid)
        } catch (e: XmppStringprepException) {
            throw IllegalArgumentException("calleeAddress is not a valid Jid", e)
        }
    }

    /**
     * Returns an iterator over all currently active calls.
     *
     * @return an iterator over all currently active calls.
     */
    override fun getActiveCalls(): Iterator<CallJabberImpl?> {
        return activeCallsRepository.getActiveCalls()
    }

    /**
     * Returns the active call peer corresponding to the given sid.
     *
     * @param sid the Jingle session ID of the active `Call` between the local peer and the
     * callee in the case of attended transfer; `null` in the case of unattended transfer
     *
     * @return The active call peer corresponding to the given sid. "null" if there is no such call.
     */
    fun getActiveCallPeer(sid: String?): CallPeerJabberImpl? {
        return activeCallsRepository.findCallPeerBySid(sid)
    }

    /**
     * Resumes communication with a call peer previously put on hold.
     *
     * @param peer the call peer to put on hold.
     *
     * @throws OperationFailedException if we fail to send the "hold" message.
     */
    @Synchronized
    @Throws(OperationFailedException::class)
    override fun putOffHold(peer: CallPeer?) {
        putOnHold(peer, false)
    }

    /**
     * Puts the specified CallPeer "on hold".
     *
     * @param peer the peer that we'd like to put on hold.
     *
     * @throws OperationFailedException if we fail to send the "hold" message.
     */
    @Synchronized
    @Throws(OperationFailedException::class)
    override fun putOnHold(peer: CallPeer?) {
        putOnHold(peer, true)
    }

    /**
     * Puts the specified `CallPeer` on or off hold.
     *
     * @param peer the `CallPeer` to be put on or off hold
     * @param on `true` to have the specified `CallPeer` put on hold; `false`, otherwise
     *
     * @throws OperationFailedException if we fail to send the "hold" message.
     */
    @Throws(OperationFailedException::class)
    private fun putOnHold(peer: CallPeer?, on: Boolean) {
        if (peer is CallPeerJabberImpl) peer.putOnHold(on)
    }

    /**
     * Ends the call with the specified `peer`.
     *
     * @param peer the peer that we'd like to hang up on.
     *
     * @throws ClassCastException if peer is not an instance of this CallPeerSipImpl.
     * @throws OperationFailedException if we fail to terminate the call.
     */
    @Synchronized
    @Throws(ClassCastException::class, OperationFailedException::class)
    override fun hangupCallPeer(peer: CallPeer?) {
        hangupCallPeer(peer, OperationSetBasicTelephony.HANGUP_REASON_NORMAL_CLEARING, null)
    }

    /**
     * Ends the call with the specified `peer`.
     *
     * @param peer the peer that we'd like to hang up on.
     * @param reasonCode indicates if the hangup is following to a call failure or
     * simply a disconnect indicate by the reason.
     * @param reason the reason of the hangup. If the hangup is due to a call failure,
     * then this string could indicate the reason of the failure
     */
    @Throws(OperationFailedException::class)
    override fun hangupCallPeer(peer: CallPeer?, reasonCode: Int, reason: String?) {
        val failed = reasonCode != OperationSetBasicTelephony.HANGUP_REASON_NORMAL_CLEARING

        // if we are failing a peer and have a reason, add the reason packet extension
        var jingleReason: JingleReason? = null
        if (failed && reason != null) {
            val reasonIC = convertReasonCodeToSIPCode(reasonCode)
            if (reasonIC != null) {
                jingleReason = JingleReason(reasonIC, reasonIC.toString(), null)
            }
        }

        // XXX maybe add answer/hangup abstract method to MediaAwareCallPeer
        if (peer is CallPeerJabberImpl) {
            try {
                peer.hangup(failed, reason, jingleReason)
            } catch (e: NotConnectedException) {
                throw OperationFailedException("Could not hang up", OperationFailedException.GENERAL_ERROR, e)
            } catch (e: InterruptedException) {
                throw OperationFailedException("Could not hang up", OperationFailedException.GENERAL_ERROR, e)
            }
        }
    }

    /**
     * Implements method `answerCallPeer` from `OperationSetBasicTelephony`.
     *
     * @param peer the call peer that we want to answer
     *
     * @throws OperationFailedException if we fail to answer
     */
    @Throws(OperationFailedException::class)
    override fun answerCallPeer(peer: CallPeer?) {
        // XXX maybe add answer/hangup abstract method to MediaAwareCallPeer
        if (peer is CallPeerJabberImpl) peer.answer()
    }

    /**
     * Closes all active calls. And releases resources.
     */
    fun shutdown() {
        Timber.log(TimberLog.FINER, "Ending all active calls.")
        val activeCalls = activeCallsRepository.getActiveCalls()

        // this is fast, but events aren't triggered ...
        // jingleManager.disconnectAllSessions();

        // go through all active calls.
        while (activeCalls.hasNext()) {
            val call = activeCalls.next()
            val callPeers = call!!.getCallPeers()

            // go through all call peers and say bye to every one.
            while (callPeers.hasNext()) {
                val peer = callPeers.next()
                try {
                    hangupCallPeer(peer)
                } catch (ex: Exception) {
                    Timber.w(ex, "Failed to properly hangup peer %s", peer)
                }
            }
        }
    }

    /**
     * Register JingleSession Handler immediately upon accepting the call via JingleMessage; to avoid race condition
     * when receiving call from conversations where transport is sent individually after session-initiate at fast pace.
     * https://discourse.igniterealtime.org/t/smack-4-4-5-jingle-manager-implementation-has-problem-to-handle-conversations-incoming-call/91573
     *
     * @param type JingleMessage type
     * @param remote the remote caller
     * @param sid Jingle session Id
     */
    override fun onJmStateChange(type: JingleMessageType?, remote: FullJid?, sid: String?) {
        when (type) {
            JingleMessageType.accept -> {
                mSid = sid
                // as a session responder (incoming call)
                mJingleSession = JingleCallSessionImpl(mConnection, remote, sid, null, this)
            }
            JingleMessageType.proceed -> mSid = sid
            JingleMessageType.retract -> {
                Timber.w("Call retract by remote: (%s) %s", sid, remote)
                if (mJingleSession != null) {
                    mJingleSession!!.unregisterJingleSessionHandler()
                }
                mSid = null
            }
            else -> mSid = null
        }
    }

    /**
     * Handle error for the intended user returns by Jingle responder; Todo: should not be required
     *
     * @see [6.3 Responder Response](https://xmpp.org/extensions/xep-0166.html.protocol-response)
     */
    @Throws(NotConnectedException::class, InterruptedException::class, NotLoggedInException::class)
    override fun processStanza(stanza: Stanza) {
        val err = stanza.error
        val message = aTalkApp.getResString(R.string.service_gui_CALL_ERROR,
            if (err != null) err.condition else "Unknown")
        Timber.e(message)
        if (getActiveCalls().hasNext()) {
            val callPeer = getActiveCalls().next()!!.getCallPeers().next()
            if (callPeer != null && callPeer.getPeerJid()!!.isParentOf(stanza.from)) {
                callPeer.setState(CallPeerState.FAILED, message)
            }
        }
    }

    override fun handleJingleSession(jingle: Jingle, session: JingleCallSessionImpl) {
        mJingleSession = session
        handleJingleSession(jingle)
    }

    override fun handleJingleSession(jingle: Jingle) {
        try {
            /*
             * let's first see whether we have a peer that's concerned by this IQ.
             * callPeer is null until session-initiate is received when as a responder.
             * Otherwise non-null when it acts as an initiator
             */
            val callPeer = activeCallsRepository.findCallPeerBySid(jingle.sid)
            if (callPeer == null) {
                processJingleSynchronize(jingle)
            }
            else {
                processJingle(jingle, callPeer)
            }
        } catch (t: Throwable) {
            val packetClass = jingle.javaClass.simpleName
            Timber.e(t, "Error handling incoming IQ: %s (%s)", packetClass, jingle.stanzaId)
            /*
             * The Javadoc on ThreadDeath says: If ThreadDeath is caught by a method, it is
             * important that it be rethrown so that the thread actually dies.
             */
            if (t is ThreadDeath) throw t
        }
    }

    /**
     * Analyzes the `jingle`'s action and passes it to the corresponding handler.
     * Mainly for as a responder until session-initiate is received => processJingle()
     *
     * @param jingleIQ the [Jingle] packet we need to be analyzing.
     */
    @Synchronized
    private fun processJingleSynchronize(jingleIQ: Jingle) {
        val callPeer: CallPeerJabberImpl?
        val action = jingleIQ.action
        Timber.d("### Processing Jingle IQ  %s: (%s) synchronized", jingleIQ.stanzaId, jingleIQ.action)
        when (action) {
            JingleAction.session_initiate -> {
                // Initiator attribute is RECOMMENDED but not REQUIRED attribute for Jingle "session-initiate".
                // When Initiator attribute is not present copy the value from IQ "from" attribute. Allow per XEP-0166
                if (jingleIQ.initiator == null) {
                    jingleIQ.initiator = jingleIQ.from.asEntityFullJidIfPossible()
                }

//                StartMutedExtension startMutedExt = jingle.getExtension(StartMutedExtension.class);
//                if (startMutedExt != null) {
//                    OperationSetJitsiMeetToolsJabberImpl operationSetJitsiMeetTools
//                            = (OperationSetJitsiMeetToolsJabberImpl) mPPS.getOperationSet(OperationSetJitsiMeetTools.class);
//
//                    if (operationSetJitsiMeetTools != null) {
//                        boolean[] startMutedFlags = {
//                                Boolean.parseBoolean(startMutedExt.getAttributeValue(
//                                        StartMutedExtension.AUDIO_ATTRIBUTE_NAME)),
//
//                                Boolean.parseBoolean(startMutedExt.getAttributeValue(
//                                        StartMutedExtension.VIDEO_ATTRIBUTE_NAME))
//                        };
//                        operationSetJitsiMeetTools.notifySessionStartMuted(startMutedFlags);
//                    }
//                    else {
//                        Timber.w("StartMutedPacketExtension not handled! OperationSetJitsiMeetTools not available.");
//                    }
//                }
                var call: CallJabberImpl? = null
                // check to see if this session-initiate is for call <transfer/>
                val transfer = jingleIQ.getExtension(SdpTransfer::class.java)
                if (transfer != null) {
                    val sid = transfer.sid
                    if (sid != null) {
                        val attendantCall = activeCallsRepository.findBySid(sid)
                        if (attendantCall != null) {
                            val attendant = attendantCall.getPeerBySid(sid)
                            // Check and proceed if we are legally involved in the session.
                            if (attendant != null
                                    && transfer.from.isParentOf(attendant.getPeerJid())
                                    && transfer.to.isParentOf(mPPS.ourJID)) {
                                call = attendantCall
                            }
                        }
                    }
                }
                val callidExt = jingleIQ.getExtension(CallIdExtension::class.java)
                if (callidExt != null) {
                    val callid = callidExt.text
                    if (callid != null) call = activeCallsRepository.findByCallId(callid)
                }
                if (transfer != null && callidExt != null) Timber.w(
                    "Receive session-initiate with both 'transfer' and 'callid' extensions. Ignore 'transfer' and use 'callid'.")

                // start init new call if not already in call conference, must use sid from jingleIQ
                if (call == null) {
                    call = CallJabberImpl(this, jingleIQ.sid)
                }

                /*
                 * cmeng: 20220504;
                 * Below problem is resolved via jingleMessage support in normal call; what about call transfer???
                 *
                 * cmeng: 20200622;
                 * Must deployed method synchronized and update the new callPeer to activeCallsRepository asap;
                 * Otherwise peer sending trailing standalone transport-info (not part of session-initiate)
                 * (e.g. conversations ~ 60ms) will be in race-condition; the transport-info is received before
                 * the callPeer has been initialized and hence not being processed at all.
                 */
                callPeer = CallPeerJabberImpl(call, jingleIQ, mJingleSession!!)
                call.addCallPeer(callPeer)

                /*
                 * cmeng (20200611): change to new merged trailing transport-info's with the session-initiate
                 * before processing i.e. not using the next synchronous method call to avoid ANR.
                 */processSessionInitiateInternal(call, callPeer, jingleIQ)
            }
            JingleAction.transport_info -> {
                // Assume callPeer has been setup in synchronise session-initiate; However callPeer may be null if
                // the caller prematurely terminate the call or caller sends transport-info before session-initiate.
                callPeer = activeCallsRepository.findCallPeerBySid(jingleIQ.sid)
                processTransportInfo(callPeer, jingleIQ)
            }
            JingleAction.session_terminate -> {
                // Actual handler has moved to => processJingle#session_terminate;
                Timber.w("Received session-terminate after call has disconnected: %s. JingleSession: %s",
                    jingleIQ.stanzaId, mJingleSession)
                // Received session_terminate from remote after the call has ended; due to async operations between local and remote.
                // unregisterJingleSessionHandler just in case, but without sending session-terminate else => item-not-found
                if (mJingleSession != null) {
                    mJingleSession!!.unregisterJingleSessionHandler()
                }
                contentMedias = null
            }
            JingleAction.session_info -> Timber.w("Received unhandled Jingle IQ: %s. Action: %s", jingleIQ.stanzaId,
                action)
            else -> Timber.w("Received unhandled Jingle IQ: %s. Action: %s", jingleIQ.stanzaId, action)
        }
    }

    /**
     * Analyzes the `jingle`'s action and passes it to the corresponding handler.
     * the rest of these cases deal with exiting peers (mainly for as an initiator)
     * Or drop this handler when as a responder, after received the session-initiate
     *
     * @param jingle the [Jingle] packet we need to be analyzing.
     */
    @Throws(NotConnectedException::class, InterruptedException::class)
    private fun processJingle(jingle: Jingle, callPeer: CallPeerJabberImpl) {
        val action = jingle.action
        Timber.d("### Processing Jingle IQ (%s: %s); callPeer: %s", action, jingle.stanzaId, callPeer.getAddress())
        when (action) {
            JingleAction.session_accept -> {
                jingleSI = null
                // Allow to processTransportInfo any embedded Transport-info; before processSessionAccept.
                processTransportInfo(callPeer, jingle)
                processSessionAccept(callPeer, jingle)
            }
            JingleAction.session_info -> {
                // Check for jingle rtp session-info as first priority
                val info = jingle.sessionInfo
                if (info != null) {
                    callPeer.processSessionInfo(info)
                }
                else {
                    val transfer = jingle.getExtension(SdpTransfer::class.java)
                    if (transfer != null) {
                        try {
                            callPeer.processTransfer(transfer, jingle)
                        } catch (ofe: OperationFailedException) {
                            Timber.e(ofe, "Failed to transfer to %s", transfer.to)
                        }
                    }

                    // process the coin stanza if any
                    val coinExt = jingle.getExtension(CoinExtension::class.java)
                    if (coinExt != null) {
                        callPeer.setConferenceFocus(coinExt.isFocus)
                    }
                }
            }
            JingleAction.session_terminate -> callPeer.processSessionTerminate(jingle)
            JingleAction.content_accept -> callPeer.processContentAccept(jingle)
            JingleAction.content_add -> callPeer.processContentAdd(jingle)
            JingleAction.content_modify -> callPeer.processContentModify(jingle)
            JingleAction.content_reject -> callPeer.processContentReject(jingle)
            JingleAction.content_remove -> callPeer.processContentRemove(jingle)
            JingleAction.transport_info -> processTransportInfo(callPeer, jingle)
            JingleAction.source_add -> callPeer.processSourceAdd(jingle)
            JingleAction.source_remove -> callPeer.processSourceRemove(jingle)
            else -> Timber.e("Received unhandled Jingle IQ id: %s. Action: %s", jingle.stanzaId, action)
        }
    }

    /**
     * For the following parameters usages:
     *
     * @see .processSessionAccept
     * @see .processSessionInitiate
     * @see .processTransportInfo
     */
    private var jingleSI: Jingle? = null

    // Use to cache transport-info's received prior to session-initiate from conversations
    private var mediaTransports: MutableMap<String, IceUdpTransport>? = null

    // Flag to indicate if the transport-info for session-accept has been processed.
    // Only start to process jingleSA after at least one candidate/mediaType has been processed.
    private var mTIProcessed = false
    private var jingleSA: Jingle? = null

    // A reference of the media to be processed for transport-info to avoid media prune by ice4j;
    private var contentMedias: MutableList<String>? = null

    /**
     * Creates a new instance.
     *
     * protocolProvider a reference to the `ProtocolProviderServiceJabberImpl` instance that created us.
     */
    init {
        mPPS.addRegistrationStateChangeListener(this)
    }
    // Use as cache for at least one candidate/mediaType before process.
    // private Jingle jingleTransports = null;
    /**
     * Proceed to process session-accept only after at least one candidate/mediaType has been process.
     * This is to avoid media stream being pruned by ice4j;
     * Conversations sends only one candidate per transport-info stanza before and/or after session-accept;
     *
     * @param callPeer CallPeerJabberImpl
     * @param jingle Jingle element of session-accept
     *
     * @see .processTransportInfo
     * @see IceUdpTransportManager.startConnectivityEstablishment
     */
    private fun processSessionAccept(callPeer: CallPeerJabberImpl, jingle: Jingle) {
        if (!mTIProcessed) {
            jingleSA = jingle
        }
        else {
            jingleSA = null
            try {
                callPeer.processSessionAccept(jingle)
            } catch (e: NotConnectedException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Proceed to process session-initiate upon received, independent whether it contains the required transport candidates;
     * Conversations sends only one candidate per transport-info stanza before and after session-initiate;
     * aTalk is able to handle trailing transport-info conditions even the processSessionInitiate() has started
     * For leading transport-info's: if any, merged cached mediaTransports() before processing; need priority sorting.
     *
     * Received incoming call must have at least mic enabled before it is allowed to proceed;
     * Actual Video sending is handled in: ReceivedCallActivity#answerCall(Call, boolean)
     *
     * Note: Process in new thread so jingleSI can get updated with trailing transport-info received. Found that
     * actual startConnectivityEstablishment happen much later:
     * a. ~4s if transport-info is embedded in session-initiate jingle
     * b. ~10 if trailing transport-info is used e.g. conversations with last candidate at ~8s
     *
     * @param call CallJabberImpl
     * @param callPeer CallPeerJabberImpl
     * @param jingle Jingle element of session-initiate
     *
     * @see IceUdpTransportManager.startConnectivityEstablishment
     * @see .processTransportInfo
     */
    private fun processSessionInitiateInternal(call: CallJabberImpl, callPeer: CallPeerJabberImpl, jingle: Jingle) {
        // boolean isVideoCall = false;
        // for (JingleContent jingleContent : jingle.getContents()) {
        //     isVideoCall = "video".equalsIgnoreCase(((RtpDescription) jingleContent.getDescription()).getMedia());
        // }

        // Check for resource permission before proceed; mic must be enabled at a minimum.
        if (aTalk.isMediaCallAllowed(false)) {
            jingleSI = jingle
            object : Thread() {
                override fun run() {
                    call.processSessionInitiate(callPeer, jingle, mJingleSession!!)
                    jingleSI = null
                }
            }.start()
        }
        else {
            try {
                JingleUtil(mConnection).sendSessionTerminateUnsupportedApplications(jingle.initiator, jingle.sid)
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            } catch (e: XMPPErrorException) {
                throw RuntimeException(e)
            } catch (e: NotConnectedException) {
                throw RuntimeException(e)
            } catch (e: NoResponseException) {
                throw RuntimeException(e)
            }
        }
    }

    /**
     * Process the remote transport-info received during call for both initiator and responder
     * # For incoming session-initiate: merge the pre/trailing transport-info candidates before process the stanza;
     * allowing a time window of 200ms for collections; late comer will handle individually
     *
     * # For session-accept: all the transport-info's must be combined and processed in one go
     * i.e. combined all transport-info before calling processOfferTransportInfo();
     * otherwise ice4j will prune any unspecified media from the ice agent
     *
     * 1. aTalk/Jitsi implementations:
     * a. transport-info's are embedded within session-initiate stanza;
     * b. transport-info's are sent prior to session-accept stanza; - can be processed individually without problem
     *
     * 2. Conversations implementations:
     * a. transport-info's are sent in single candidate/transport-info per media after session-initiate stanza;
     * b. transport-info's are sent in single candidate/transport-info per media prior to session-accept stanza;
     *
     * @param jingleTransport Jingle IQ stanza with action='transport-info'
     * @param callPeer CallPeerJabberImpl to be targeted
     */
    private fun processTransportInfo(callPeer: CallPeerJabberImpl?, jingleTransport: Jingle) {
        // Cached all transport-info received prior to session-initiate e.g. Conversations sends transport-info's before session-initiate
        if (callPeer == null) {
            if (mediaTransports == null) {
                mediaTransports = LinkedHashMap()
            }
            for (content in jingleTransport.contents) {
                val contentName = content.name
                val mediaTransport = mediaTransports!![contentName]
                val contentTransport = content.getFirstChildElement(IceUdpTransport::class.java)
                if (mediaTransport == null) {
                    mediaTransports!![contentName] = contentTransport
                }
                else {
                    for (candidate in contentTransport.candidateList) {
                        mediaTransport.addChildElement(candidate)
                    }
                }
            }
            return
        }

        // Merge the remote transport-info candidate's to the session-initiate of the same content element
        // for both leading and trailing transport-info's for session-initiate processing
        if (jingleSI != null) {
            if (mediaTransports != null) {
                Timber.d("### Process session-initiate transport (leading): %s", mediaTransports!!.keys)
                for (contentSI in jingleSI!!.contents) {
                    val mediaTransport = mediaTransports!![contentSI.name]
                    if (mediaTransport != null) {
                        val contentTransport = contentSI.getFirstChildElement(IceUdpTransport::class.java)
                        for (candidate in mediaTransport.candidateList) {
                            contentTransport.addChildElement(candidate)
                        }
                    }
                }
                mediaTransports = null
            }
            Timber.d("### Process session-initiate transport (trailing): %s", jingleTransport.stanzaId)
            for (content in jingleTransport.contents) {
                val contentName = content.name
                for (contentSI in jingleSI!!.contents) {
                    if (contentName == contentSI.name) {
                        val contentTransport = contentSI.getFirstChildElement(IceUdpTransport::class.java)
                        for (transport in content.getChildElements(IceUdpTransport::class.java)) {
                            for (candidate in transport.candidateList) {
                                contentTransport.addChildElement(candidate)
                            }
                        }
                    }
                }
            }
        }
        else {
            if (contentMedias == null) contentMedias = callPeer.contentMedia
            for (content in jingleTransport.contents) {
                contentMedias!!.remove(content.name)
            }
            mTIProcessed = contentMedias!!.isEmpty()
            try {
                Timber.d("### Process Jingle transport-info (session-accept) media: %s; %s, %s",
                    contentMedias, mTIProcessed, jingleSA)
                // For testing of clone function only: cmeng 20220302
//                JingleContent content = jingleTransport.getContents().get(0);
//                IceUdpTransport transport = content.getFirstChildElement(IceUdpTransport.class);
//                IceUdpTransport transportx = AbstractXmlElement.clone(transport);
//                Timber.d("Cloned transport: %s=>%s; %s", transport, transportx, transportx.toXML());
                callPeer.processOfferTransportInfo(jingleTransport)
                if (jingleSA != null) {
                    callPeer.processSessionAccept(jingleSA!!)
                    jingleSA = null
                }
            } catch (e: NotConnectedException) {
                Timber.w("Process transport-info error: %s", e.message)
            } catch (e: InterruptedException) {
                Timber.w("Process transport-info error: %s", e.message)
            }
        }
    }

    /**
     * Returns the protocol provider that this operation set belongs to.
     *
     * @return a reference to the `ProtocolProviderService` that created this operation set.
     */
    override fun getProtocolProvider(): ProtocolProviderServiceJabberImpl {
        return mPPS
    }

    /**
     * Gets the secure state of the call session in which a specific peer is involved
     *
     * @param peer the peer for who the call state is required
     *
     * @return the call state
     */
    override fun isSecure(peer: CallPeer?): Boolean {
        return (peer as MediaAwareCallPeer<*, *, *>?)!!.getMediaHandler().isSecure
    }

    /**
     * Attended transfer (in the sense of call transfer) a specific `CallPeer` to a specific callee
     * address which already participates in an active `Call`.
     *
     * The method is suitable for providing the implementation of 'attended' call transfer
     * (though no such requirement is imposed).
     *
     * @param peer the `CallPeer` to be transferred to the specified callee address
     * @param target the address in the form of `CallPeer` of the callee to transfer `peer` to
     *
     * @throws OperationFailedException if something goes wrong
     * @see OperationSetAdvancedTelephony.transfer
     */
    @Throws(OperationFailedException::class)
    override fun transfer(peer: CallPeer?, target: CallPeer?) {
        val jabberTarget = target as CallPeerJabberImpl?
        val to = getFullCalleeURI(jabberTarget!!.getPeerJid())
        /*
         * XEP-0251: Jingle Session Transfer says: Before doing [attended transfer], the attendant
         * SHOULD verify that the callee supports Jingle session transfer.
         */
        try {
            val discoverInfo = mPPS.discoveryManager!!.discoverInfo(to)
            if (!discoverInfo!!.containsFeature(
                        ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_TRANSFER_0)) {
                throw OperationFailedException("Callee " + to + " does not support"
                        + " XEP-0251: Jingle Session Transfer", OperationFailedException.INTERNAL_ERROR)
            }
        } catch (xmppe: XMPPException) {
            Timber.w(xmppe, "Failed to retrieve DiscoverInfo for %s", to)
        } catch (xmppe: InterruptedException) {
            Timber.w(xmppe, "Failed to retrieve DiscoverInfo for %s", to)
        } catch (xmppe: NoResponseException) {
            Timber.w(xmppe, "Failed to retrieve DiscoverInfo for %s", to)
        } catch (xmppe: NotConnectedException) {
            Timber.w(xmppe, "Failed to retrieve DiscoverInfo for %s", to)
        }
        transfer(peer, to, jabberTarget.sid)
    }

    /**
     * Unattended transfer (in the sense of call transfer) a specific `CallPeer` to a specific callee
     * address which may or may not already be participating in an active `Call`.
     *
     * The method is suitable for providing the implementation of 'unattended' call transfer
     * (though no such requirement is imposed).
     *
     * @param peer the `CallPeer` to be transferred to the specified callee address
     * @param target the address of the callee to transfer `peer` to
     *
     * @throws OperationFailedException if something goes wrong
     * @see OperationSetAdvancedTelephony.transfer
     */
    @Throws(OperationFailedException::class)
    override fun transfer(peer: CallPeer?, target: String?) {
        val targetJid = getFullCalleeURI(target)
        transfer(peer, targetJid, null)
    }

    /**
     * Transfer (in the sense of call transfer) a specific `CallPeer` to a specific callee
     * address which may optionally be participating in an active `Call`.
     *
     * @param peer the `CallPeer` to perform the transferring, to the specified callee address
     * @param to the address of the callee to transfer `peer` to
     * @param sid the Jingle session ID of the active `Call` between the local peer and the
     * callee in the case of attended transfer; `null` in the case of unattended transfer
     *
     * @throws OperationFailedException if something goes wrong
     */
    @Throws(OperationFailedException::class)
    private fun transfer(peer: CallPeer?, to: EntityFullJid, sid: String?) {
        val caller = getFullCalleeURI(peer!!.getPeerJid())
        try {
            val discoverInfo = mPPS.discoveryManager!!.discoverInfo(caller)
            if (!discoverInfo!!.containsFeature(
                        ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_TRANSFER_0)) {
                throw OperationFailedException("Caller " + caller + " does not support"
                        + " XEP-0251: Jingle Session Transfer", OperationFailedException.INTERNAL_ERROR)
            }
        } catch (xmppe: XMPPException) {
            Timber.w(xmppe, "Failed to retrieve DiscoverInfo for %s", to)
        } catch (xmppe: InterruptedException) {
            Timber.w(xmppe, "Failed to retrieve DiscoverInfo for %s", to)
        } catch (xmppe: NoResponseException) {
            Timber.w(xmppe, "Failed to retrieve DiscoverInfo for %s", to)
        } catch (xmppe: NotConnectedException) {
            Timber.w(xmppe, "Failed to retrieve DiscoverInfo for %s", to)
        }
        (peer as CallPeerJabberImpl?)!!.transfer(to, sid)
    }

    /**
     * Transfer authority used for interacting with user for unknown calls and the requests for transfer.
     *
     * @param authority transfer authority.
     */
    override fun setTransferAuthority(authority: TransferAuthority?) {}

    companion object {
        /**
         * Google Voice domain.
         */
        const val GOOGLE_VOICE_DOMAIN = "voice.google.com"

        /**
         * Converts the codes for hangup from OperationSetBasicTelephony one to the jabber reasons.
         *
         * @param reasonCode the reason code.
         *
         * @return the jabber Response.
         */
        private fun convertReasonCodeToSIPCode(reasonCode: Int): JingleReason.Reason? {
            return when (reasonCode) {
                OperationSetBasicTelephony.HANGUP_REASON_NORMAL_CLEARING -> JingleReason.Reason.success
                OperationSetBasicTelephony.HANGUP_REASON_ENCRYPTION_REQUIRED -> JingleReason.Reason.security_error
                OperationSetBasicTelephony.HANGUP_REASON_TIMEOUT -> JingleReason.Reason.timeout
                OperationSetBasicTelephony.HANGUP_REASON_BUSY_HERE -> JingleReason.Reason.busy
                else -> null
            }
        }
    }
}