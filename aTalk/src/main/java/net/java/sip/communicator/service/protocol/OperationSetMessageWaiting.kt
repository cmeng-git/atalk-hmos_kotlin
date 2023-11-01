/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.MessageWaitingListener

/**
 * Provides notifications for message waiting notifications.
 *
 * @author Damian Minkov
 */
interface OperationSetMessageWaiting : OperationSet {
    /**
     * Message waiting types.
     */
    enum class MessageType
    /**
     * Creates new message type.
     *
     * @param type the type.
     */
    (
            /**
             * Message type String.
             */
            private val type: String) {
        VOICE("voice-message"), FAX("fax-message"), PAGER("pager-message"), MULTIMEDIA("multimedia-message"), TEXT("text-message"), NONE("none");

        /**
         * Returns the type of the message type enum element.
         *
         * @return the message type.
         */
        override fun toString(): String {
            return type
        }

        companion object {
            /**
             * Returns MessageType by its type name.
             *
             * @param type the type.
             * @return the corresponding MessageType.
             */
            fun valueOfByType(type: String): MessageType {
                for (mt in values()) {
                    if (mt.toString() == type) return mt
                }
                return valueOf(type)
            }
        }
    }

    /**
     * Registers a `MessageWaitingListener` with this operation set so that it gets
     * notifications of new and old messages waiting.
     *
     * @param type register the listener for certain type of messages.
     * @param listener the `MessageWaitingListener` to register.
     */
    fun addMessageWaitingNotificationListener(type: MessageType?,
                                              listener: MessageWaitingListener?)

    /**
     * Unregisters `listener` so that it won't receive any further notifications upon new
     * messages waiting notifications delivery.
     *
     * @param type register the listener for certain type of messages.
     * @param listener the `MessageWaitingListener` to unregister.
     */
    fun removeMessageWaitingNotificationListener(type: MessageType?,
                                                 listener: MessageWaitingListener?)
}