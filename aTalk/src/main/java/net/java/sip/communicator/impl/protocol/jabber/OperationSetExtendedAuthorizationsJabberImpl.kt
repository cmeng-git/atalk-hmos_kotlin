/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.AuthorizationRequest
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.OperationSetExtendedAuthorizations
import net.java.sip.communicator.service.protocol.OperationSetExtendedAuthorizations.SubscriptionStatus
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.packet.*
import org.jivesoftware.smack.roster.packet.RosterPacket

/**
 * Extended authorization implementation for jabber provider.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class OperationSetExtendedAuthorizationsJabberImpl internal constructor(provider: ProtocolProviderServiceJabberImpl,
        opSetPersPresence: OperationSetPersistentPresenceJabberImpl?) : OperationSetExtendedAuthorizations {
    /**
     * A reference to the persistent presence operation set that we use to match incoming messages
     * to `Contact`s and vice versa.
     */
    private var opSetPersPresence: OperationSetPersistentPresenceJabberImpl? = null

    /**
     * The parent provider.
     */
    private val parentProvider: ProtocolProviderServiceJabberImpl

    /**
     * Creates OperationSetExtendedAuthorizations.
     *
     * @param opSetPersPresence the presence opset.
     * @param provider the parent provider
     */
    init {
        this.opSetPersPresence = opSetPersPresence
        parentProvider = provider
    }

    /**
     * Send a positive authorization to `contact` thus allowing them to add us to their
     * contact list without needing to first request an authorization.
     *
     * @param contact the `Contact` whom we're granting authorization prior to receiving a request.
     * @throws OperationFailedException if we fail sending the authorization.
     */
    @Throws(OperationFailedException::class)
    override fun explicitAuthorize(contact: Contact?) {
        opSetPersPresence!!.assertConnected()
        require(contact is ContactJabberImpl) { "The specified contact is not an jabber contact.$contact" }
        val connection = parentProvider.connection
        val responsePacket = connection!!.stanzaFactory.buildPresenceStanza()
                .ofType(Presence.Type.subscribed).build()
        responsePacket.to = contact.contactJid
        try {
            connection.sendStanza(responsePacket)
        } catch (e: NotConnectedException) {
            throw OperationFailedException("Could not send authorize",
                    OperationFailedException.NETWORK_FAILURE, e)
        } catch (e: InterruptedException) {
            throw OperationFailedException("Could not send authorize",
                    OperationFailedException.NETWORK_FAILURE, e)
        }
    }

    /**
     * Send an authorization request, requesting `contact` to add them to our contact list?
     *
     * @param request the `AuthorizationRequest` that we'd like the protocol provider to send to `contact`.
     * @param contact the `Contact` who we'd be asking for an authorization.
     * @throws OperationFailedException if we fail sending the authorization request.
     */
    @Throws(OperationFailedException::class)
    override fun reRequestAuthorization(request: AuthorizationRequest?, contact: Contact?) {
        opSetPersPresence!!.assertConnected()
        require(contact is ContactJabberImpl) { "The specified contact is not an jabber contact: $contact" }
        val connection = parentProvider.connection
        val responsePacket = connection!!.stanzaFactory.buildPresenceStanza()
                .ofType(Presence.Type.subscribed).build()
        responsePacket.to = contact.contactJid
        try {
            connection.sendStanza(responsePacket)
        } catch (e: NotConnectedException) {
            throw OperationFailedException("Could not send subscribe packet",
                    OperationFailedException.NETWORK_FAILURE, e)
        } catch (e: InterruptedException) {
            throw OperationFailedException("Could not send subscribe packet",
                    OperationFailedException.NETWORK_FAILURE, e)
        }
    }

    /**
     * Returns the subscription status for the `contact` or if not available returns null.
     *
     * @param contact the contact to query for subscription status.
     * @return the subscription status for the `contact` or if not available returns null.
     */
    override fun getSubscriptionStatus(contact: Contact?): SubscriptionStatus? {
        require(contact is ContactJabberImpl) { "The specified contact is not an jabber contact.$contact" }
        val entry = contact.getSourceEntry()
        return if (entry != null) {
            if (entry.type == RosterPacket.ItemType.none || entry.type == RosterPacket.ItemType.from
                    && entry.isSubscriptionPending) {
                SubscriptionStatus.SubscriptionPending
            } else if (entry.type == RosterPacket.ItemType.to || entry.type == RosterPacket.ItemType.both) SubscriptionStatus.Subscribed else SubscriptionStatus.NotSubscribed
        } else null
    }
}