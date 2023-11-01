/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * Provides functionality for correcting instant messages.
 *
 * @author Ivan Vergiliev
 */
interface OperationSetMessageCorrection : OperationSetBasicInstantMessaging {
    /**
     * Replaces the message with ID `correctedMessageUID` sent to the contact `to`
     * with the message `message`
     *
     * @param to The contact to send the message to.
     * @param resource The ContactResource to send the message to.
     * @param message The new message.
     * @param correctedMessageUID The ID of the message being replaced.
     */
    fun correctMessage(to: Contact?, resource: ContactResource?, message: IMessage?, correctedMessageUID: String?)
}