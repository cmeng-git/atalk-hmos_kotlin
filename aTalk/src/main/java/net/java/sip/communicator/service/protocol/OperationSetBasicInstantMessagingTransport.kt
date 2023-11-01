/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package net.java.sip.communicator.service.protocol

/**
 * Provides additional information on the transport on which Basic Instant Messaging communication
 * is built. Note that this refers to the characteristics of the instant messaging protocol, not to
 * the underlying TCP or UDP transport layer.
 *
 * This interface defines methods that provide information on the transport facilities that are used
 * by the Basic Instant Messaging protocol implementation. Methods can be used to query the
 * transport channel for information such as maximum message sizes and allowed number of consecutive
 * messages.
 *
 * @author Danny van Heumen
 */
interface OperationSetBasicInstantMessagingTransport : OperationSet {
    /**
     * Compute the maximum message size for a messaging being sent to the provided contact.
     *
     *
     *
     * If there is no limit to the message size, please use constant [.UNLIMITED].
     *
     *
     * @param contact
     * the contact to which the message will be sent
     * @return returns the maximum size of the message or UNLIMITED if there is no limit
     */
    fun getMaxMessageSize(contact: Contact?): Int

    /**
     * Compute the maximum number of consecutive messages allowed to be sent to this contact.
     *
     *
     *
     * If there is no limit to the number of messages, please use constant [.UNLIMITED].
     *
     *
     * @param contact
     * the contact to which the messages are sent
     * @return returns the maximum number of messages to send
     */
    fun getMaxNumberOfMessages(contact: Contact?): Int

    companion object {
        /**
         * Constant value indicating unlimited size or number.
         */
        const val UNLIMITED = -1
    }
}