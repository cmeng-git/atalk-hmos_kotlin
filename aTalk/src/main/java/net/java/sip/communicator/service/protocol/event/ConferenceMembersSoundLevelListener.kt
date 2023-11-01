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

/**
 * Notifies interested parties in `ConferenceMember`s sound level changes. When a
 * `CallPeer` is participating in the conference also as a `ConferenceMember` its
 * audio level would be included in the map of received levels.
 *
 * @author Yana Stamcheva
 */
interface ConferenceMembersSoundLevelListener {
    /**
     * Indicates that a change has occurred in the sound level of some of the
     * `ConferenceMember`s coming from a given `CallPeer`. It's presumed that all
     * `ConferenceMember`s NOT contained in the event have a 0 sound level.
     *
     * @param event
     * the `ConferenceMembersSoundLevelEvent` containing the new level
     */
    fun soundLevelChanged(event: ConferenceMembersSoundLevelEvent?)
}