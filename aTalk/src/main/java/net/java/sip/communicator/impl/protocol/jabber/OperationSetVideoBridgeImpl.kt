/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.OperationNotSupportedException
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony
import net.java.sip.communicator.service.protocol.OperationSetTelephonyConferencing
import net.java.sip.communicator.service.protocol.OperationSetVideoBridge
import net.java.sip.communicator.service.protocol.RegistrationState
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener
import net.java.sip.communicator.service.protocol.media.MediaAwareCallConference
import org.jivesoftware.smack.filter.AndFilter
import org.jivesoftware.smack.filter.StanzaExtensionFilter
import org.jivesoftware.smack.filter.StanzaFilter
import org.jivesoftware.smack.filter.StanzaTypeFilter
import org.jivesoftware.smack.iqrequest.AbstractIqRequestHandler
import org.jivesoftware.smack.iqrequest.IQRequestHandler
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smackx.colibri.ColibriConferenceIQ
import org.jxmpp.stringprep.XmppStringprepException

/**
 * Implements `OperationSetVideoBridge` for Jabber.
 *
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class OperationSetVideoBridgeImpl(
        /**
         * The `ProtocolProviderService` implementation which initialized this instance, owns it
         * and is often referred to as its parent.
         */
        private val protocolProvider: ProtocolProviderServiceJabberImpl) : AbstractIqRequestHandler(ColibriConferenceIQ.ELEMENT, ColibriConferenceIQ.NAMESPACE, IQ.Type.set, IQRequestHandler.Mode.async), OperationSetVideoBridge, RegistrationStateChangeListener {
    /**
     * Creates an instance of `OperationSetVideoBridgeImpl` by specifying the parent
     * `ProtocolProviderService` announcing this operation set.
     *
     * protocolProvider the parent Jabber protocol provider
     */
    init {
        protocolProvider.addRegistrationStateChangeListener(this)
    }

    /**
     * Creates a conference call with the specified callees as call peers via a video bridge
     * provided by the parent Jabber provider.
     *
     * @param callees the list of addresses that we should call
     * @return the newly created conference call containing all CallPeers
     * @throws OperationFailedException if establishing the conference call fails
     * @throws OperationNotSupportedException if the provider does not have any conferencing features.
     */
    @Throws(OperationFailedException::class, OperationNotSupportedException::class, XmppStringprepException::class)
    override fun createConfCall(callees: Array<String>): Call<*> {
        return protocolProvider.getOperationSet(OperationSetTelephonyConferencing::class.java)!!
                .createConfCall(callees, MediaAwareCallConference(true))
    }

    /**
     * Invites the callee represented by the specified uri to an already existing call using a video
     * bridge provided by the parent Jabber provider. The difference between this method and
     * createConfCall is that inviteCalleeToCall allows a user to add new peers to an already
     * established conference.
     *
     * @param uri the callee to invite to an existing conf call.
     * @param call the call that we should invite the callee to.
     * @return the CallPeer object corresponding to the callee represented by the specified uri.
     * @throws OperationFailedException if inviting the specified callee to the specified call fails
     * @throws OperationNotSupportedException if allowing additional callees to a pre-established call is not supported.
     */
    @Throws(OperationFailedException::class, OperationNotSupportedException::class, XmppStringprepException::class)
    override fun inviteCalleeToCall(uri: String, call: Call<*>): CallPeer {
        return protocolProvider.getOperationSet(OperationSetTelephonyConferencing::class.java)!!.inviteCalleeToCall(uri, call)
    }

    /**
     * Indicates if there's an active video bridge available at this moment. The Jabber provider may
     * announce support for video bridge, but it should not be used for calling until it becomes actually active.
     *
     * @return `true` to indicate that there's currently an active available video bridge, `false` - otherwise
     */
    override fun isActive(): Boolean {
        val jitsiVideobridge = protocolProvider.jitsiVideobridge
        return jitsiVideobridge != null && jitsiVideobridge.isNotEmpty()
    }

    /**
     * Notifies this instance that a specific `ColibriConferenceIQ` has been received.
     *
     * @param conferenceIQ the `ColibriConferenceIQ` which has been received
     */
    private fun processColibriConferenceIQ(conferenceIQ: ColibriConferenceIQ) {
        /*
         * The application is not a Jitsi Videobridge server but a client. Consequently, the
         * specified ColibriConferenceIQ is sent to it in relation to the part of the application's
         * functionality which makes requests to a Jitsi Videobridge server i.e. CallJabberImpl.
         *
         * Additionally, the method processColibriConferenceIQ is presently tasked with processing
         * ColibriConferenceIQ requests only. They are SET IQs sent by the Jitsi Videobridge server
         * to notify the application about updates in the states of (colibri) conferences organized
         * by the application.
         */
        if (IQ.Type.set == conferenceIQ.type && conferenceIQ.id != null) {
            val basicTelephony = protocolProvider.getOperationSet(OperationSetBasicTelephony::class.java)
            if (basicTelephony != null) {
                val i = basicTelephony.getActiveCalls()
                while (i!!.hasNext()) {
                    val call = i.next()
                    if (call is CallJabberImpl) {
                        val conference = call.getConference()
                        if (conference != null && conference.isJitsiVideobridge) {
                            /*
                             * TODO We may want to disallow rogue CallJabberImpl instances which may
                             * throw an exception to prevent the conferenceIQ from reaching the
                             * CallJabberImpl instance which it was meant for.
                             */
                            if (call.processColibriConferenceIQ(conferenceIQ)) break
                        }
                    }
                }
            }
        }
    }

    override fun handleIQRequest(iqRequest: IQ): IQ {
        val conferenceIQ = iqRequest as ColibriConferenceIQ
        processColibriConferenceIQ(conferenceIQ)
        return IQ.createResultIQ(iqRequest)
    }

    /**
     * {@inheritDoc}
     *
     * Implements [RegistrationStateChangeListener]. Notifies this instance that there has
     * been a change in the `RegistrationState` of [.protocolProvider]. Subscribes this
     * instance to [ColibriConferenceIQ]s as soon as `protocolProvider` is registered
     * and unSubscribes it as soon as `protocolProvider` is unregistered.
     */
    override fun registrationStateChanged(evt: RegistrationStateChangeEvent) {
        val registrationState = evt.getNewState()
        if (RegistrationState.REGISTERED == registrationState) {
            protocolProvider.connection!!.registerIQRequestHandler(this)
        } else if (RegistrationState.UNREGISTERED == registrationState) {
            val connection = protocolProvider.connection
            connection?.unregisterIQRequestHandler(this)
        }
    }

    companion object {
        /*
     * Thumbnail request StanzaFilter for handling the request
     */
        // private static final StanzaFilter COLIBRI_EXT = new AndFilter(new StanzaTypeFilter(IQ.class),
        //    IQTypeFilter.GET, new StanzaExtensionFilter(ColibriConferenceIQ.ELEMENT, ColibriConferenceIQ.NAMESPACE));
        private val COLIBRI_FILTER = AndFilter(StanzaTypeFilter.IQ,
                StanzaExtensionFilter(ColibriConferenceIQ.ELEMENT, ColibriConferenceIQ.NAMESPACE))
    }
}