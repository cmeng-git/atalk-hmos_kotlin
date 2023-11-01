/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.ChatStateNotificationsListener
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smackx.chatstates.ChatState

/**
 * The operation set allows user bundles (e.g. the user interface) to send and receive chatState
 * notifications to and from other `Contact`s.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface OperationSetChatStateNotifications : OperationSet {
    /**
     * Adds `l` to the list of listeners registered for receiving `ChatStateNotificationEvent`s
     *
     * @param istener the `ChatStateNotificationsListener` listener that we'd like to add
     */
    fun addChatStateNotificationsListener(istener: ChatStateNotificationsListener)

    /**
     * Removes `l` from the list of listeners registered for receiving `ChatStateNotificationEvent`s
     *
     * @param listener the `ChatStateNotificationsListener` listener that we'd like to remove
     */
    fun removeChatStateNotificationsListener(listener: ChatStateNotificationsListener)

    /**
     * Sends a notification to `notifiedContact` that we have entered `chatState`.
     *
     * @param chatDescriptor the chatDescriptor to notify
     * @param chatState the chat state as defined in ChatState that we have entered.
     * @throws java.lang.IllegalStateException if the underlying ICQ stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException if `notifiedContact` is not an instance belonging
     * to the underlying implementation.
     */
    @Throws(IllegalStateException::class, IllegalArgumentException::class, SmackException.NotConnectedException::class, InterruptedException::class)
    fun sendChatStateNotification(chatDescriptor: Any?, chatState: ChatState?)
}