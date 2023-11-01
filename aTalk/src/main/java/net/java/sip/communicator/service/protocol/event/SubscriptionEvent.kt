/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ContactGroup
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import java.util.*

/**
 * SubscriptionEvents indicate creation removal or failure of a given Subscription. Note that in SIP
 * Communicator the terms Subscription and Contact are quite similar: A contact becomes available
 * and it is possible to query its presence status and user information, once a subscription for
 * this contact has been created.
 *
 * @author Emil Ivov
 */
class SubscriptionEvent @JvmOverloads constructor(source: Contact?, provider: ProtocolProviderService?,
        parentGroup: ContactGroup?, eventID: Int, errorCode: Int = ERROR_UNSPECIFIED, errorReason: String? = null) : EventObject(source) {
    private var eventID = -1
    private var sourceProvider: ProtocolProviderService? = null
    private var parentGroup: ContactGroup? = null
    private var errorCode = ERROR_UNSPECIFIED
    private var errorReason: String? = null
    /**
     * Creates a new Subscription event according to the specified parameters.
     *
     * @param source
     * the Contact instance that this subscription pertains to.
     * @param provider
     * the ProtocolProviderService instance where this event occurred
     * @param parentGroup
     * the ContactGroup underwhich the corresponding Contact is located
     * @param eventID
     * one of the SUBSCRIPTION_XXX static fields indicating the nature of the event.
     * @param errorCode
     * the error code
     * @param errorReason
     * the error reason string
     */
    /**
     * Creates a new Subscription event according to the specified parameters.
     *
     * @param source
     * the Contact instance that this subscription pertains to.
     * @param provider
     * the ProtocolProviderService instance where this event occurred
     * @param parentGroup
     * the ContactGroup underwhich the corresponding Contact is located
     * @param eventID
     * one of the SUBSCRIPTION_XXX static fields indicating the nature of the event.
     */
    init {
        sourceProvider = provider
        this.parentGroup = parentGroup
        this.eventID = eventID
        this.errorCode = errorCode
        this.errorReason = errorReason
    }

    /**
     * Returns the provider that the source contact belongs to.
     *
     * @return the provider that the source contact belongs to.
     */
    fun getSourceProvider(): ProtocolProviderService? {
        return sourceProvider
    }

    /**
     * Returns the provider that the source contact belongs to.
     *
     * @return the provider that the source contact belongs to.
     */
    fun getSourceContact(): Contact {
        return getSource() as Contact
    }

    /**
     * Returns (if applicable) the group containing the contact that cause this event. In the case
     * of a non persistent presence operation set this field is null.
     *
     * @return the ContactGroup (if there is one) containing the contact that caused the event.
     */
    fun getParentGroup(): ContactGroup? {
        return parentGroup
    }

    /**
     * Returns a String representation of this ContactPresenceStatusChangeEvent
     *
     * @return A a String representation of this `SubscriptionEvent`.
     */
    override fun toString(): String {
        val buff = StringBuffer("SubscriptionEvent-[ ContactID=")
        buff.append(getSourceContact().address)
        buff.append(", eventID=").append(getEventID())
        if (getParentGroup() != null) buff.append(", ParentGroup=").append(getParentGroup()!!.getGroupName())
        return buff.toString()
    }

    /**
     * Returns an event id specifying whether the type of this event (e.g. SUBSCRIPTION_CREATED,
     * SUBSCRIPTION_FAILED and etc.)
     *
     * @return one of the SUBSCRIPTION_XXX int fields of this class.
     */
    fun getEventID(): Int {
        return eventID
    }

    /**
     * If event is SUBSCRIPTION_FAILED, returns the error code of the failed event
     *
     * @return error code
     */
    fun getErrorCode(): Int {
        return errorCode
    }

    /**
     * If event is SUBSCRIPTION_FAILED, returns the reason of the error for the failed event
     *
     * @return the String reason for the error
     */
    fun getErrorReason(): String? {
        return errorReason
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Indicates that the SubscriptionEvent instance was triggered by the creation of a new
         * subscription
         */
        const val SUBSCRIPTION_CREATED = 1

        /**
         * Indicates that the SubscriptionEvent instance was triggered by the removal of an existing
         * subscription
         */
        const val SUBSCRIPTION_REMOVED = 2

        /**
         * Indicates that the SubscriptionEvent instance was triggered by the fact that no confirmation
         * of the successful completion of a new subscription has been received.
         */
        const val SUBSCRIPTION_FAILED = 3

        /**
         * Indicates that the SubscriptionEvent instance was triggered by the fact that the presence of
         * a particular contact in the contact list has been confirmed by the server (resolved).
         */
        const val SUBSCRIPTION_RESOLVED = 4

        /**
         * Error code unknown
         */
        const val ERROR_UNSPECIFIED = -1
    }
}