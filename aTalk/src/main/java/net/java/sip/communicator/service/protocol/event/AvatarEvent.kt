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

import net.java.sip.communicator.service.protocol.OperationSetAvatar
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import java.util.*

/**
 * Instances of this class represent a change avatar of a protocol
 *
 * @author Damien Roth
 */
class AvatarEvent
/**
 * Creates an event instance indicating that the specified protocol has changed its avatar to
 * `newAvatar`.
 *
 * @param sourceOp
 * the operation set that generated this event
 * @param sourceProvider
 * the protocol provider that the contact belongs to
 * @param newAvatar
 * the new avatar
 */
(sourceOp: OperationSetAvatar?,
        /**
         * The provider that has generated the event.
         */
        private val sourceProvider: ProtocolProviderService,
        /**
         * The new avatar
         */
        private val newAvatar: ByteArray?) : EventObject(sourceOp) {
    /**
     * Returns the provider that the source belongs to.
     *
     * @return the provider that the source belongs to.
     */
    fun getSourceProvider(): ProtocolProviderService {
        return sourceProvider
    }

    /**
     * Returns the new avatar
     *
     * @return the new avatar
     */
    fun getNewAvatar(): ByteArray? {
        return newAvatar
    }

    /**
     * Returns the `OperationSetAvatar` instance that is the source of this event.
     *
     * @return the `OperationSetAvatar` instance that is the source of this event.
     */
    fun getSourceAvatarOperationSet(): OperationSetAvatar {
        return getSource() as OperationSetAvatar
    }

    /**
     * Returns a String representation of this AvatarEvent
     *
     * @return a `String` representation of this `AvatarEvent`.
     */
    override fun toString(): String {
        return "AvatarEvent-[ Provider=" + getSourceProvider() + "]"
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}