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

import  java.util.*

/**
 * Event that notify that remote control feature has been granted. This is used in desktop sharing
 * related usage. After rights being granted, local peer should notify keyboard and mouse events to
 * remote peer.
 *
 * @author Sebastien Vincent
 */
class RemoteControlGrantedEvent
/**
 * Constructs a new `RemoteControlGrantedEvent` object.
 *
 * @param source
 * source object
 */
(source: Any?) : EventObject(source) {
    /**
     * Get the `CallPeer`.
     *
     * @return the `CallPeer`
     */
    fun getCallPeer(): CallPeer {
        return getSource() as CallPeer
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}