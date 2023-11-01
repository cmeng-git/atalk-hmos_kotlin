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
 * `WhiteboardObjectReceivedEvent` indicates reception of a new `WhiteboardObject` in
 * the corresponding whiteboard session.
 *
 * @author Julien Waechter
 * @author Emil Ivov
 */
class WhiteboardObjectReceivedEvent(source: WhiteboardSession?, obj: WhiteboardObject?,
        from: Contact?, timestamp: Date?) : EventObject(source) {
    /**
     * The contact that has sent this wbObject.
     */
    private var from: Contact? = null

    /**
     * A timestamp indicating the exact date when the event occurred.
     */
    private var timestamp: Date? = null

    /**
     * The whiteboard object that has just been received.
     */
    private var obj: WhiteboardObject? = null

    /**
     * Creates a `WhiteboardObjectReceivedEvent` representing reception of the
     * `source` WhiteboardObject received from the specified `from` contact.
     *
     * @param source
     * the `WhiteboardSession` that the object has been received in.
     * @param obj
     * the `WhiteboardObject` whose reception this event represents.
     * @param from
     * the `Contact` that has sent this WhiteboardObject.
     * @param timestamp
     * the exact date when the event ocurred.
     */
    init {
        this.obj = obj
        this.from = from
        this.timestamp = timestamp
    }

    /**
     * Returns the source white-board session, to which the received object belongs.
     *
     * @return the source white-board session, to which the received object belongs
     */
    fun getSourceWhiteboardSession(): WhiteboardSession {
        return getSource() as WhiteboardSession
    }

    /**
     * Returns a reference to the `Contact` that has sent the `WhiteboardObject` whose
     * reception this event represents.
     *
     * @return a reference to the `Contact` that has sent the `WhiteboardObject` whose
     * reception this event represents.
     */
    fun getSourceContact(): Contact? {
        return from
    }

    /**
     * Returns the WhiteboardObject that triggered this event
     *
     * @return the `WhiteboardObject` that triggered this event.
     */
    fun getSourceWhiteboardObject(): WhiteboardObject? {
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