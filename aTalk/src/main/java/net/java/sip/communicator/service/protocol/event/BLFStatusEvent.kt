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

import java.util.*

/**
 * The status event used when Busy Lamp Field state changes.
 *
 * @author Damian Minkov
 */
class BLFStatusEvent(source: Any?, type: Int) : EventObject(source) {
    /**
     * The type of the event.
     */
    var type = STATUS_OFFLINE

    /**
     * Constructs a BLFStatus event.
     * @param type the event type
     *
     * @throws IllegalArgumentException
     * if source is null.
     */
    init {
        this.type = type
    }

    override fun toString(): String {
        var statusName: String? = null
        when (type) {
            STATUS_OFFLINE -> statusName = "Offline"
            STATUS_RINGING -> statusName = "Ringing"
            STATUS_BUSY -> statusName = "Busy"
            STATUS_FREE -> statusName = "Free"
        }
        return "BLFStatusEvent{type=$type, name=$statusName}"
    }

    companion object {
        /**
         * Indicates that the `BLFStatusEvent` instance was triggered by the change of the line
         * to offline.
         */
        const val STATUS_OFFLINE = 0

        /**
         * Indicates that the `BLFStatusEvent` instance was triggered by the change of the line
         * to ringing or setting up the call.
         */
        const val STATUS_RINGING = 1

        /**
         * Indicates that the `BLFStatusEvent` instance was triggered by the change of the line
         * to busy, someone is on the phone.
         */
        const val STATUS_BUSY = 2

        /**
         * Indicates that the `BLFStatusEvent` instance was triggered by the change of the line
         * to available, free, no one is using it.
         */
        const val STATUS_FREE = 3
    }
}