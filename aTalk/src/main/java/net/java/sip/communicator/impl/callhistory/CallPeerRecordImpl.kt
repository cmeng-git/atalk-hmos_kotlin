/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.impl.callhistory

import net.java.sip.communicator.service.callhistory.CallPeerRecord
import net.java.sip.communicator.service.protocol.CallPeerState
import java.util.*

/**
 * Added some setters to CallPeerRecord
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class CallPeerRecordImpl
/**
 * Creates CallPeerRecord
 * @param peerAddress String
 * @param startTime Date
 * @param endTime Date
 */
(peerAddress: String, startTime: Date, endTime: Date?) : CallPeerRecord(peerAddress, startTime, endTime) {
    /**
     * The participant's address - entityFullJid
     */
    override var peerAddress: String? = null
        get() = super.peerAddress
        public set(peerAddress) {
            field = peerAddress
        }

    /**
     * The display name of the call peer in this record - entityJid.
     */
    override var displayName: String? = null
        get() = super.displayName
        public set(displayName) {
            field = displayName
        }

    /**
     * Sets the time the peer joined the call
     */
    override var startTime: Date? = null
        get() = super.startTime
        public set(startTime) {
            field = startTime
        }

    /**
     * The time at which peer leaves the call
     */
    override var endTime: Date? = null
        get() = super.endTime
        public set(endTime) {
            field = endTime
        }

    /**
     * The call peer state
     */
    override var state = CallPeerState.UNKNOWN
        get() = super.state
        public set(state) {
            field = state
        }
}