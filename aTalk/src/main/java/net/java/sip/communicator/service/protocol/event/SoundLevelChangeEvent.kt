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
 * `SoundLevelChangeEvent`s are triggered whenever a change occurs in the sound level of the
 * audio stream coming from a certain `CallPeer`.
 *
 *
 * In the case of a `CallPeer`, which is also a conference focus and is participating in the
 * conference as a `ConferenceMember` the level would be the aggregated level of all
 * `ConferenceMember`s levels including the one corresponding to the peer itself.
 *
 *
 * In the case of a `CallPeer`, which is also a conference focus, but is NOT participating in
 * the conference as a `ConferenceMember` (server) the level would be the aggregated level of
 * all attached `ConferenceMember`s.
 *
 * @author Yana Stamcheva
 */
class SoundLevelChangeEvent
/**
 * Creates an `StreamSoundLevelEvent` for the given `callPeer` by indicating the
 * current sound level of the audio stream.
 *
 * @param source
 * the source from which the change is received
 * @param level
 * the current sound level of the audio stream
 */(source: Any?,
        /**
         * The audio stream level, for the change of which this event is about.
         */
        private val level: Int) : EventObject(source) {
    /**
     * Returns the current sound level of the audio stream.
     *
     * @return the current sound level of the audio stream
     */
    fun getLevel(): Int {
        return level
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * The maximum level that can be reported for a participant. Level values should be distributed
         * among `MAX_LEVEL` and [.MIN_LEVEL] in a way that would appear uniform to users.
         *
         *
         * **Warning**: The value should be equal to
         * `net.java.sip.communicator.service.neomedia.event.SimpleAudioLevelListener#MAX_VALUE`
         * because we do not currently perform a conversion from the `SimpleAudioLevelListener`
         * range to the `SoundLevelChangeEvent` range when we fire the event.
         *
         */
        const val MAX_LEVEL = 127

        /**
         * The maximum (zero) level that can be reported for a participant. Level values should be
         * distributed among [.MAX_LEVEL] and `MIN_LEVEL` in a way that would appear
         * uniform to users.
         *
         *
         * **Warning**: The value should be equal to
         * `net.java.sip.communicator.service.neomedia.event.SimpleAudioLevelListener#MIN_VALUE`
         * because we do not currently perform a conversion from the `SimpleAudioLevelListener`
         * range to the `SoundLevelChangeEvent` range when we fire the event.
         *
         */
        const val MIN_LEVEL = 0
    }
}