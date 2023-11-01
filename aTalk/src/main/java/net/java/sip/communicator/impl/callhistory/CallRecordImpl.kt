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

import net.java.sip.communicator.service.callhistory.CallRecord
import net.java.sip.communicator.service.protocol.Call
import java.util.*

/**
 * Add Source call to the CallRecord
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class CallRecordImpl
/**
 * Creates Call Record
 * @param direction String
 * @param startTime Date
 * @param endTime Date
 */
(uuid: String?, direction: String, startTime: Date, endTime: Date?) : CallRecord(uuid, direction, startTime, endTime) {
    /**
     * The source call which this record serves sourceCall Call
     */
    var sourceCall: Call<*>? = null

    /**
     * Set the time when the call finishes. If some peer has no end Time set we set it also
     */
    override var endTime: Date? = null
        set(endTime) {
            field = endTime
            for (item in peerRecords) {
                val itemImpl = item as CallPeerRecordImpl
                if (itemImpl.endTime == null) itemImpl.endTime = endTime
            }
        }
}