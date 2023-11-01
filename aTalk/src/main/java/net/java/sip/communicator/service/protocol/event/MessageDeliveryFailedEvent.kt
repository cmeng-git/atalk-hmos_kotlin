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
import net.java.sip.communicator.service.protocol.IMessage
import java.util.*

/**
 * `MessageDeliveryFailedEvent`s inform of failed delivery of an instant message.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class MessageDeliveryFailedEvent
/**
 * Constructor.
 *
 * @param source the message
 * @param to the "to" contact
 * @param errorCode error code
 */
@JvmOverloads constructor(
        source: IMessage?,
        /**
         * The contact that this message has been sent to.
         */
        val destinationContact: Contact,
        /**
         * An error code indicating the reason for the failure of this delivery.
         */
        val errorCode: Int,
        /**
         * A timestamp indicating the exact date when the event occurred.
         */
        val timestamp: Long = System.currentTimeMillis(),
        /**
         * Contains a human readable message indicating the reason for the failure or null if the reason is unknown.
         */
        val reason: String? = null
) : EventObject(source) {
    /**
     * Returns a reference to the `Contact` that the source (failed) `IMessage` was sent to.
     *
     * @return a reference to the `Contact` that the source failed `IMessage` was sent to.
     */
    /**
     * Returns an error code describing the reason for the failure of the message delivery.
     *
     * @return an error code describing the reason for the failure of the message delivery.
     */
    /**
     * Returns a human readable message indicating the reason for the failure or null if the reason is unknown.
     *
     * @return a human readable message indicating the reason for the failure or null if the reason is unknown.
     */
    /**
     * A timestamp indicating the exact date when the event occurred (in this case it is the moment
     * when it was determined that message delivery has failed).
     *
     * @return a long indicating when the event occurred in the form of date timestamp.
     */
    /**
     * Sets the ID of the message being corrected to the passed ID.
     *
     * @return correctedMessageUID The ID of the message being corrected.
     */
    /**
     * The ID of the message being corrected, or null if this was a new message and not a message correction.
     */
    var correctedMessageUID: String? = null
        private set

    /**
     * Constructor.
     *
     * @param source the message
     * @param to the "to" contact
     * @param errorCode error code
     * @param correctedMessageUID The ID of the message being corrected.
     */
    constructor(source: IMessage?, to: Contact, errorCode: Int, correctedMessageUID: String?) : this(source, to, errorCode, System.currentTimeMillis(), null) {
        this.correctedMessageUID = correctedMessageUID
    }
    /**
     * Creates a `MessageDeliveryFailedEvent` indicating failure of delivery of the
     * `source` message to the specified `to` contact.
     *
     * @param source the `IMessage` whose delivery this event represents.
     * @param destinationContact the `Contact` that this message was sent to.
     * @param errorCode an errorCode indicating the reason of the failure.
     * @param timestamp the exact timestamp when it was determined that delivery had failed.
     * @param reason a human readable message indicating the reason for the failure or null if the reason is unknown.
     */
    /**
     * Returns the message that triggered this event
     *
     * @return the `IMessage` that triggered this event.
     */
    val sourceMessage: IMessage
        get() = getSource() as IMessage

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Set when no other error code can describe the exception that occurred.
         */
        const val UNKNOWN_ERROR = 1

        /**
         * Set when delivery fails due to a failure in network communications or a transport error.
         */
        const val NETWORK_FAILURE = 2

        /**
         * Set to indicate that delivery has failed because the provider was not registered.
         */
        const val PROVIDER_NOT_REGISTERED = 3

        /**
         * Set when delivery fails for implementation specific reasons.
         */
        const val INTERNAL_ERROR = 4

        /**
         * Set when delivery fails because we're trying to send a message to a contact that is currently
         * offline and the server does not support offline messages.
         */
        const val OFFLINE_MESSAGES_NOT_SUPPORTED = 5

        /**
         * Set when delivery fails because there are undecided omemo identity detected with omemo message encryption
         */
        const val OMEMO_SEND_ERROR = 6

        /**
         * Set when delivery fails because of dependency on an operation that is unsupported. For
         * example, because it is unknown or not supported at that particular moment.
         */
        const val UNSUPPORTED_OPERATION = 7

        /**
         * Set when delivery fails because we're trying to send a message to a a room where we are not
         * allowed to send messages.
         */
        const val FORBIDDEN = 8
        const val NOT_ACCEPTABLE = 9
        const val SYSTEM_ERROR_MESSAGE = 10
    }
}