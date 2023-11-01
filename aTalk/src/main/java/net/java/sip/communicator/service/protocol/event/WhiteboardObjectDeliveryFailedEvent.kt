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
import net.java.sip.communicator.service.protocol.WhiteboardSession
import net.java.sip.communicator.service.protocol.whiteboardobjects.WhiteboardObject
import java.util.*

/**
 * `WhiteboardObjectDeliveredEvent`s are used to report that delivery of a whiteboardObject
 * has failed.
 *
 * @author Julien Waechter
 * @author Emil Ivov
 */
class WhiteboardObjectDeliveryFailedEvent(source: WhiteboardSession?, obj: WhiteboardObject?,
        to: Contact?, errorCode: Int, timestamp: Date?) : EventObject(source) {
    /**
     * The contact that this whiteboard object has been sent to.
     */
    private var to: Contact? = null

    /**
     * An error code indicating the reason for the failure of this delivery.
     */
    private var errorCode = UNKNOWN_ERROR

    /**
     * A timestamp indicating the exact date when the event occurred.
     */
    private var timestamp: Date? = null

    /**
     * The whiteboard object delivery of which has failed.
     */
    private var obj: WhiteboardObject? = null

    /**
     * Creates a `WhiteboardObjectDeliveryFailedEvent` indicating failure of delivery of the
     * `obj` WhiteboardObject to the specified `to` contact.
     *
     * @param source
     * the `WhiteboardSession` where the failure has occcurred.
     * @param obj
     * the `WhiteboardObject` the white-board object.
     * @param to
     * the `Contact` that this WhiteboardObject was sent to.
     * @param errorCode
     * an errorCode indicating the reason for the failure.
     * @param timestamp
     * the exact Date when it was determined that delivery had failed.
     */
    init {
        this.obj = obj
        this.to = to
        this.errorCode = errorCode
        this.timestamp = timestamp
    }

    /**
     * Returns a reference to the `Contact` that the source (failed)
     * `WhiteboardObject` was sent to.
     *
     * @return a reference to the `Contact` that the source failed `WhiteboardObject`
     * was sent to.
     */
    fun getDestinationContact(): Contact? {
        return to
    }

    /**
     * Returns an error code describing the reason for the failure of the white-board object
     * delivery.
     *
     * @return an error code describing the reason for the failure of the white-board object
     * delivery.
     */
    fun getErrorCode(): Int {
        return errorCode
    }

    /**
     * A timestamp indicating the exact date when the event ocurred (in this case it is the moment
     * when it was determined that whiteboardObject delivery has failed).
     *
     * @return a Date indicating when the event ocurred.
     */
    fun getTimestamp(): Date? {
        return timestamp
    }

    /**
     * Returns the WhiteboardObject that triggered this event
     *
     * @return the `WhiteboardObject` that triggered this event.
     */
    fun getSourceWhiteboardObject(): WhiteboardObject? {
        return obj
    }

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
         * Set when delivery fails because we're trying to send a whiteboard object to a contact that is
         * currently offline and the server does not support offline whiteboard objects.
         */
        const val OFFLINE_MESSAGES_NOT_SUPPORTED = 5
    }
}