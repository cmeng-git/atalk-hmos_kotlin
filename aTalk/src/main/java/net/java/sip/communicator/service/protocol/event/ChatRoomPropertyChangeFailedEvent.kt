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

import net.java.sip.communicator.service.protocol.ChatRoom

import java.beans.PropertyChangeEvent

/**
 * Dispatched to indicate that a change of a chat room property has failed. The modification of a
 * property could fail, because the implementation doesn't support such a property.
 *
 * @author Yana Stamcheva
 */
class ChatRoomPropertyChangeFailedEvent
/**
 * Creates a `ChatRoomPropertyChangeEvent` indicating that a change has occurred for
 * property `propertyName` in the `source` chat room and that its value has
 * changed from `oldValue` to `newValue`.
 *
 *
 *
 * @param source
 * the `ChatRoom`, to which the property belongs
 * @param propertyName
 * the name of the property
 * @param propertyValue
 * the value of the property
 * @param expectedValue
 * the expected after the change value of the property
 * @param reasonCode
 * the code indicating the reason for the failure
 * @param reason
 * more detailed explanation of the failure
 */(source: ChatRoom?, propertyName: String?,
        propertyValue: Any?, expectedValue: Any?,
        /**
         * Indicates why the failure occurred.
         */
        private val reasonCode: Int,
        /**
         * The reason of the failure.
         */
        private val reason: String) : PropertyChangeEvent(source, propertyName, propertyValue, expectedValue) {
    /**
     * Returns the source chat room for this event.
     *
     * @return the `ChatRoom` associated with this event.
     */
    fun getSourceChatRoom(): ChatRoom {
        return getSource() as ChatRoom
    }

    /**
     * Returns the value of the property.
     *
     * @return the value of the property.
     */
    fun getPropertyValue(): Any {
        return oldValue
    }

    /**
     * Return the expected after the change value of the property.
     *
     * @return the expected after the change value of the property
     */
    fun getExpectedValue(): Any {
        return newValue
    }

    /**
     * Returns the code of the failure. One of the static constants declared in this class.
     *
     * @return the code of the failure. One of the static constants declared in this class
     */
    fun getReasonCode(): Int {
        return reasonCode
    }

    /**
     * Returns the reason of the failure.
     *
     * @return the reason of the failure
     */
    fun getReason(): String {
        return reason
    }

    /**
     * Returns a String representation of this event.
     *
     * @return String representation of this event
     */
    override fun toString(): String {
        return ("ChatRoomPropertyChangeEvent[type=" + this.propertyName + " sourceRoom="
                + getSource() + "oldValue=" + this.oldValue + "newValue="
                + this.newValue + "]")
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Indicates that the current implementation doesn't support the given property.
         */
        const val PROPERTY_NOT_SUPPORTED = 0
    }
}