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
 * `WhiteboardObjectDeliveredEvent`s confirms successful delivery of a WhiteboardObject.
 *
 * @author Julien Waechter
 * @author Emil Ivov
 */
class WhiteboardObjectDeliveredEvent(source: WhiteboardSession?,
        /**
         * The whiteboard object that has just been delivered.
         */
        private val obj: WhiteboardObject,
        to: Contact?, timestamp: Date?) : EventObject(source) {
    /**
     * The contact that has sent this wbObject.
     */
    private var to: Contact? = null

    /**
     * A timestamp indicating the exact date when the event occurred.
     */
    private var timestamp: Date? = null

    /**
     * Creates a `WhiteboardObjectDeliveredEvent` representing delivery of the
     * `source` whiteboardObject to the specified `to` contact.
     *
     * @param source
     * the `WhiteboardSession` whose delivery this event represents.
     * @param obj
     * the `WhiteboardObject`
     * @param to
     * the `Contact` that this whiteboardObject was sent to.
     * @param timestamp
     * a date indicating the exact moment when the event ocurred
     */
    init {
        this.to = to
        this.timestamp = timestamp
    }

    /**
     * Returns a reference to the `Contact` that the source `WhiteboardObject` was
     * sent to.
     *
     * @return a reference to the `Contact` that has send the `WhiteboardObject` whose
     * reception this event represents.
     */
    fun getDestinationContact(): Contact? {
        return to
    }

    /**
     * Returns the whiteboardObject that triggered this event
     *
     * @return the `WhiteboardObject` that triggered this event.
     */
    fun getSourceWhiteboardObject(): WhiteboardObject {
        return obj
    }

    /**
     * A timestamp indicating the exact date when the event ocurred.
     *
     * @return a Date indicating when the event ocurred.
     */
    fun getTimestamp(): Date? {
        return timestamp
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}