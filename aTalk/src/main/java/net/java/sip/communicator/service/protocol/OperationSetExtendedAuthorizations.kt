/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.OperationFailedException

/**
 * Contains methods that would allow service users to re-request authorizations to add a contact to
 * their contact list or, send them an authorization before having been asked.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface OperationSetExtendedAuthorizations : OperationSet {
    /**
     * The available subscription of the contact.
     */
    enum class SubscriptionStatus {
        /**
         * Subscription state when we are not subscribed for the contacts presence statuses.
         */
        NotSubscribed,

        /**
         * Subscription state when we are subscribed for the contact statuses.
         */
        Subscribed,

        /**
         * When we have subscribed for contact statuses, but haven't received authorization yet.
         */
        SubscriptionPending
    }

    /**
     * Send a positive authorization to `contact` thus allowing them to add us to their
     * contact list without needing to first request an authorization.
     *
     * @param contact the `Contact` whom we're granting authorization prior to receiving a request.
     * @throws OperationFailedException if we fail sending the authorization.
     */
    @Throws(OperationFailedException::class)
    fun explicitAuthorize(contact: Contact?)

    /**
     * Send an authorization request, requesting `contact` to add them to our contact list?
     *
     * @param request the `AuthorizationRequest` that we'd like the protocol provider to send to
     * `contact`.
     * @param contact the `Contact` who we'd be asking for an authorization.
     * @throws OperationFailedException if we fail sending the authorization request.
     */
    @Throws(OperationFailedException::class)
    fun reRequestAuthorization(request: AuthorizationRequest?, contact: Contact?)

    /**
     * Returns the subscription status for the `contact` or if not available returns null.
     *
     * @param contact the contact to query for subscription status.
     * @return the subscription status for the `contact` or if not available returns null.
     */
    fun getSubscriptionStatus(contact: Contact?): SubscriptionStatus?
}