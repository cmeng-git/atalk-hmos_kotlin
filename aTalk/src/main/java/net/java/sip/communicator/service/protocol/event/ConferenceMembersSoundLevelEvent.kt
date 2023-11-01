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

import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.service.protocol.ConferenceMember
import java.util.*

/**
 * Notifies interested parties in `ConferenceMember`s sound level changes. When a
 * `CallPeer` is participating in the conference also as a `ConferenceMember` its
 * sound level would be included in the map of received levels.
 *
 * @author Yana Stamcheva
 */
class ConferenceMembersSoundLevelEvent
/**
 * Creates an instance of `ConferenceMembersSoundLevelEvent` for the given
 * `callPeer` by indicating the mapping of `ConferenceMember`s and sound levels.
 *
 * @param callPeer
 * the `CallPeer` for which this event occurred
 * @param levels
 * the mapping of `ConferenceMember`s to sound levels
 */(callPeer: CallPeer?,
        /**
         * The mapping of `ConferenceMember`s to sound levels. It is presumed that all
         * `ConferenceMember`s not contained in the map has a 0 sound level.
         */
        private val levels: Map<ConferenceMember, Int>) : EventObject(callPeer) {
    /**
     * Returns the source `CallPeer` for which the event occurred.
     *
     * @return the source `CallPeer` for which the event occurred
     */
    fun getSourcePeer(): CallPeer {
        return getSource() as CallPeer
    }

    /**
     * Returns the mapping of `ConferenceMember`s to sound levels. It is presumed that all
     * `ConferenceMember`s not contained in the map has a 0 sound level.
     *
     * @return the mapping of `ConferenceMember`s to sound levels
     */
    fun getLevels(): Map<ConferenceMember, Int> {
        return levels
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * The maximum level that can be reported for a participant in a conference. Level values should
         * be distributed among MAX_LEVEL and MIN_LEVEL in a way that would appear uniform to users.
         */
        const val MAX_LEVEL = 255

        /**
         * The maximum (zero) level that can be reported for a participant in a conference. Level values
         * should be distributed among MAX_LEVEL and MIN_LEVEL in a way that would appear uniform to
         * users.
         */
        const val MIN_LEVEL = 0
    }
}