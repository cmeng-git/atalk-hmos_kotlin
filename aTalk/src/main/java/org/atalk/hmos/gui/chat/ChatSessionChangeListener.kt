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
package org.atalk.hmos.gui.chat

/**
 * Listens for changes in [ChatSession].
 * @author George Politis
 * @author Eng Chong Meng
 */
interface ChatSessionChangeListener {
    /**
     * Called when the current [ChatTransport] has
     * changed.
     *
     * @param chatSession the [ChatSession] it's current
     * [ChatTransport] has changed
     */
    fun currentChatTransportChanged(chatSession: ChatSession?)

    /**
     * When a property of the chatTransport has changed.
     * @param eventID the event id representing the property of the transport
     * that has changed.
     */
    fun currentChatTransportUpdated(eventID: Int)

    companion object {
        /**
         * The icon representing the ChatTransport has changed.
         */
        const val ICON_UPDATED = 1
    }
}