/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.MessageWaitingListener

/**
 * A notification message that is used to deliver notifications for an waiting server message.
 *
 * @see MessageWaitingListener, MessageWaitingEvent
 *
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class NotificationMessage
/**
 * Creates an instance of `NotificationMessage` by specifying the name of the contact
 * from which the message is, the message group, any additional details and the message actual
 * text.
 *
 * @param source
 * the notification message source
 * @param fromContact
 * the contact from which the message is coming
 * @param messageGroup
 * the name of the group of messages to which this message belongs
 * @param messageDetails
 * additional details related to the message
 * @param messageText
 * the text of the message
 */
(
        /**
         * The notification message source.
         */
        private val source: Any,
        /**
         * The contact from which the message is coming.
         */
        private val fromContact: String,
        /**
         * The name of the group of messages to which this message belongs, if there's any.
         */
        private val messageGroup: String,
        /**
         * Additional details related to the message.
         */
        private val messageDetails: String,
        /**
         * The text of the message.
         */
        private val messageText: String) {
    /**
     * Returns the notification message source.
     *
     * @return the notification message source
     */
    fun getSource(): Any {
        return source
    }

    /**
     * Returns the contact from which the message is coming
     *
     * @return the contact from which the message is coming
     */
    fun getFromContact(): String {
        return fromContact
    }

    /**
     * Returns the name of the group of messages to which this message belongs.
     *
     * @return the name of the group of messages to which this message belongs
     */
    fun getMessageGroup(): String {
        return messageGroup
    }

    /**
     * Returns the additional details related to the message
     *
     * @return the additional details related to the message
     */
    fun getMessageDetails(): String {
        return messageDetails
    }

    /**
     * Returns the text of the message
     *
     * @return the text of the message
     */
    fun getMessageText(): String {
        return messageText
    }
}