/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.media.MediaAwareCallConference
import org.apache.commons.lang3.StringUtils
import org.jivesoftware.smackx.jingle.JingleManager
import org.jxmpp.jid.FullJid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException

/**
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class OperationSetResAwareTelephonyJabberImpl
/**
 * Creates an instance of `OperationSetResourceAwareTelephonyImpl` by specifying the
 * basic telephony operation set.
 *
 * basicTelephony the `OperationSetBasicTelephonyJabberImpl` supported by the parent Jabber protocol provider
 */
(
        /**
         * The `OperationSetBasicTelephonyJabberImpl` supported by the parent Jabber protocol provider
         */
        private val jabberTelephony: OperationSetBasicTelephonyJabberImpl) : OperationSetResourceAwareTelephony {
    /**
     * Creates a new `Call` and invites a specific `CallPeer` given by her
     * `Contact` on a specific `ContactResource` to it.
     *
     * @param callee the address of the callee who we should invite to a new call
     * @param calleeResource the specific resource to which the invite should be sent
     * @return a newly created `Call`. The specified `callee` is available in the
     * `Call` as a `CallPeer`
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     */
    @Throws(OperationFailedException::class)
    override fun createCall(callee: Contact?, calleeResource: ContactResource?): Call<*>? {
        return createCall(callee, calleeResource, null)
    }

    /**
     * Creates a new `Call` and invites a specific `CallPeer` given by her
     * `Contact` on a specific `ContactResource` to it.
     *
     * @param callee the address of the callee who we should invite to a new call
     * @param calleeResource the specific resource to which the invite should be sent
     * @return a newly created `Call`. The specified `callee` is available in the
     * `Call` as a `CallPeer`
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     */
    @Throws(OperationFailedException::class)
    override fun createCall(callee: String?, calleeResource: String?): Call<*>? {
        return createCall(callee, calleeResource, null)
    }

    /**
     * Creates a new `Call` and invites a specific `CallPeer` given by her `Contact` to it.
     *
     * @param callee the address of the callee who we should invite to a new call
     * @param calleeResource the specific resource to which the invite should be sent
     * @param conference the `CallConference` in which the newly-created `Call` is to participate
     * @return a newly created `Call`. The specified `callee` is available in the
     * `Call` as a `CallPeer`
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     */
    @Throws(OperationFailedException::class)
    override fun createCall(callee: Contact?, calleeResource: ContactResource?, conference: CallConference?): Call<*>? {
        return createCall(callee!!.address, calleeResource!!.resourceName, conference)
    }

    /**
     * Creates a new `Call` and invites a specific `CallPeer` to it given by her `String` URI.
     *
     * @param uri the address of the callee who we should invite to a new `Call`
     * @param calleeResource the specific resource to which the invite should be sent
     * @param conference the `CallConference` in which the newly-created `Call` is to participate
     * @return a newly created `Call`. The specified `callee` is available in the
     * `Call` as a `CallPeer`
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     */
    @Throws(OperationFailedException::class)
    override fun createCall(uri: String?, calleeResource: String?, conference: CallConference?): Call<*>? {
        val call = CallJabberImpl(jabberTelephony, JingleManager.randomId())
        if (conference != null) call.setConference(conference as MediaAwareCallConference)
        lateinit var fullCalleeUri: FullJid
        try {
            fullCalleeUri = JidCreate.fullFrom(if (StringUtils.isEmpty(calleeResource)) uri else "$uri/$calleeResource")
        } catch (e: XmppStringprepException) {
            e.printStackTrace()
        }
        val callPeer = jabberTelephony.createOutgoingCall(call, uri, fullCalleeUri, null)
                ?: throw OperationFailedException("Failed to create outgoing call because no peer was created",
                        OperationFailedException.INTERNAL_ERROR)
        val callOfCallPeer = callPeer.getCall()
        // We may have a Google Talk call here.
        if (callOfCallPeer != call && conference != null)
            callOfCallPeer!!.setConference(conference)
        return callOfCallPeer
    }
}