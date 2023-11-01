/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.MessageListener
import java.io.File

/**
 * Provides basic functionality for sending and receiving SMS Messages.
 *
 * @author Damian Minkov
 */
interface OperationSetSmsMessaging : OperationSet {
    /**
     * Create a IMessage instance for sending arbitrary MIME-encoding content.
     *
     * @param content
     * content value
     * @param contentType
     * the MIME-type for `content`
     * @param contentEncoding
     * encoding used for `content`
     * @return the newly created message.
     */
    fun createMessage(content: ByteArray?, contentType: String?, contentEncoding: String?): IMessage?

    /**
     * Create a IMessage instance for sending a sms messages with default (text/plain) content type
     * and encoding.
     *
     * @param messageText
     * the string content of the message.
     * @return IMessage the newly created message
     */
    fun createMessage(messageText: String?): IMessage?

    /**
     * Sends the `message` to the destination indicated by the `to` contact.
     *
     * @param to
     * the `Contact` to send `message` to
     * @param message
     * the `IMessage` to send.
     * @throws java.lang.IllegalStateException
     * if the underlying stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException
     * if `to` is not an instance belonging to the underlying implementation.
     */
    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    fun sendSmsMessage(to: Contact?, message: IMessage?)

    /**
     * Sends the `message` to the destination indicated by the `to` parameter.
     *
     * @param to
     * the destination to send `message` to
     * @param message
     * the `IMessage` to send.
     * @throws java.lang.IllegalStateException
     * if the underlying stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException
     * if `to` is not an instance belonging to the underlying implementation.
     */
    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    fun sendSmsMessage(to: String?, message: IMessage?)

    /**
     * Sends the `file` to the destination indicated by the `to` parameter.
     *
     * @param to
     * the destination to send `message` to
     * @param file
     * the `file` to send.
     * @throws java.lang.IllegalStateException
     * if the underlying stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException
     * if `to` is not an instance belonging to the underlying implementation.
     * @throws OperationNotSupportedException
     * if the given contact client or server does not support file transfers
     */
    @Throws(IllegalStateException::class, IllegalArgumentException::class, OperationNotSupportedException::class)
    fun sendMultimediaFile(to: Contact?, file: File?): FileTransfer?

    /**
     * Registers a MessageListener with this operation set so that it gets notifications of
     * successful message delivery, failure or reception of incoming messages..
     *
     * @param listener
     * the `MessageListener` to register.
     */
    fun addMessageListener(listener: MessageListener?)

    /**
     * Unregisters `listener` so that it won't receive any further notifications upon
     * successful message delivery, failure or reception of incoming messages..
     *
     * @param listener
     * the `MessageListener` to unregister.
     */
    fun removeMessageListener(listener: MessageListener?)

    /**
     * Determines whether the protocol supports the supplied content type
     *
     * @param contentType
     * the type we want to check
     * @return `true` if the protocol supports it and `false` otherwise.
     */
    fun isContentTypeSupported(contentType: String?): Boolean

    /**
     * Returns the contact to send sms to.
     *
     * @param to
     * the number to send sms.
     * @return the contact representing the receiver of the sms.
     */
    fun getContact(to: String?): Contact?

    /**
     * Whether the implementation do not know how to send sms to the supplied contact and should as
     * for number.
     *
     * @param to
     * the contact to send sms.
     * @return whether user needs to enter number for the sms recipient.
     */
    fun askForNumber(to: Contact?): Boolean
}