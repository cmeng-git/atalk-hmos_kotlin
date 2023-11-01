/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.MessageListener
import org.jivesoftware.smackx.omemo.OmemoManager

/**
 * Provides basic functionality for sending and receiving InstantMessages.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface OperationSetBasicInstantMessaging : OperationSet {
    /**
     * Create a IMessage instance for sending arbitrary MIME-encoding content.
     *
     * @param content content value
     * @param encType the encryption type for the `content`
     * @param subject a `String` subject or `null` for now subject.
     * @return the newly created message.
     */
    fun createMessage(content: String, encType: Int, subject: String?): IMessage

    /**
     * Create a IMessage instance for sending a simple text messages with default (text/plain)
     * content type and encoding.
     *
     * @param messageText the string content of the message.
     * @return IMessage the newly created message
     */
    fun createMessage(messageText: String): IMessage

    /**
     * Create a IMessage instance with the specified UID, content type and a default encoding. This
     * method can be useful when message correction is required. One can construct the corrected
     * message to have the same UID as the message before correction.
     *
     * @param messageText the string content of the message.
     * @param encType the mime and encryption type for the `content`
     * @param messageUID the unique identifier of this message.
     * @return IMessage the newly created message
     */
    fun createMessageWithUID(messageText: String, encType: Int, messageUID: String): IMessage

    /**
     * Sends the `message` to the destination indicated by the `to` contact.
     *
     * @param to the `Contact` to send `message` to
     * @param message the `IMessage` to send.
     * @throws java.lang.IllegalStateException if the underlying ICQ stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException if `to` is not an instance belonging to the underlying implementation.
     */
    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    fun sendInstantMessage(to: Contact, message: IMessage)

    /**
     * Sends the `message` to the destination indicated by the `to` contact and the
     * specific `toResource`.
     *
     * @param to the `Contact` to send `message` to
     * @param toResource the resource to which the message should be send
     * @param message the `IMessage` to send.
     * @throws java.lang.IllegalStateException if the underlying ICQ stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException if `to` is not an instance belonging to the underlying implementation.
     */
    fun sendInstantMessage(to: Contact, toResource: ContactResource?, message: IMessage)
    fun sendInstantMessage(to: Contact, resource: ContactResource?, message: IMessage, correctedMessageUID: String?,
                           omemoManager: OmemoManager)

    /**
     * Registers a `MessageListener` with this operation set so that it gets notifications of
     * successful message delivery, failure or reception of incoming messages.
     *
     * @param listener the `MessageListener` to register.
     */
    fun addMessageListener(listener: MessageListener)

    /**
     * Unregisters `listener` so that it won't receive any further notifications upon
     * successful message delivery, failure or reception of incoming messages.
     *
     * @param listener the `MessageListener` to unregister.
     */
    fun removeMessageListener(listener: MessageListener)

    /**
     * Determines whether the protocol provider (or the protocol itself) support sending and
     * receiving offline messages. Most often this method would return true for protocols that
     * support offline messages and false for those that don't. It is however possible for a
     * protocol to support these messages and yet have a particular account that does not (i.e.
     * feature not enabled on the protocol server). In cases like this it is possible for this
     * method to return `true` even when offline messaging is not supported, and then have
     * the sendMessage method throw an `OperationFailedException` with code
     * OFFLINE_MESSAGES_NOT_SUPPORTED.
     *
     * @return `true` if the protocol supports offline messages and `false` otherwise.
     */
    fun isOfflineMessagingSupported(): Boolean

    /**
     * Determines whether the protocol supports the supplied content type
     *
     * @param mimeType the mime type we want to check
     * @return `true` if the protocol supports it and `false` otherwise.
     */
    fun isContentTypeSupported(mimeType: Int): Boolean

    /**
     * Determines whether the protocol supports the supplied content type for the given contact.
     *
     * @param mimeType the encode mode we want to check
     * @param contact contact which is checked for supported encType
     * @return `true` if the contact supports it and `false` otherwise.
     */
    fun isContentTypeSupported(mimeType: Int, contact: Contact): Boolean

    /**
     * Returns the inactivity timeout in milliseconds.
     *
     * @return The inactivity timeout in milliseconds. Or -1 if undefined
     */
    fun getInactivityTimeout(): Long
}