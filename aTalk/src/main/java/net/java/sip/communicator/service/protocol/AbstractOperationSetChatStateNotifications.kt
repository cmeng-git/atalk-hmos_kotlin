/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.ChatStateNotificationEvent
import net.java.sip.communicator.service.protocol.event.ChatStateNotificationsListener
import timber.log.Timber

/**
 * Represents a default implementation of `OperationSetChatStateNotifications` in order to make
 * it easier for implementers to provide complete solutions while focusing on implementation-specific details.
 *
 * @param <T> the type of the `ProtocolProviderService` implementation providing the
 * `AbstractOperationSetChatStateNotifications` implementation
 *
 * @author Lubomir Marinov
 * @author Eng Chong Meng
</T> */
abstract class AbstractOperationSetChatStateNotifications<T : ProtocolProviderService?> protected constructor(parentProvider: T) : OperationSetChatStateNotifications {
    /**
     * The provider that created us.
     */
    protected val parentProvider: T?

    /**
     * The list of currently registered `ChatStateNotificationsListener`s.
     */
    private val chatStateNotificationsListeners = ArrayList<ChatStateNotificationsListener>()

    /**
     * Initializes a new `AbstractOperationSetChatStateNotifications` instance created by a
     * specific `ProtocolProviderService` instance.
     *
     * parentProvider the `ProtocolProviderService` which creates the new instance
     */
    init {
        this.parentProvider = parentProvider
    }

    /**
     * Adds `listener` to the list of listeners registered for receiving `ChatStateNotificationEvent`s.
     *
     * @param istener the `TypingNotificationsListener` listener that we'd like to add
     * @see OperationSetChatStateNotifications.addChatStateNotificationsListener
     */
    override fun addChatStateNotificationsListener(istener: ChatStateNotificationsListener) {
        synchronized(chatStateNotificationsListeners) { if (!chatStateNotificationsListeners.contains(istener)) chatStateNotificationsListeners.add(istener) }
    }

    /**
     * Utility method throwing an exception if the stack is not properly initialized.
     *
     * @throws IllegalStateException if the underlying stack is not registered and initialized
     */
    @Throws(IllegalStateException::class)
    protected open fun assertConnected() {
        checkNotNull(parentProvider) { "The provider must be non-null before being able to  communicate." }
        check(parentProvider.isRegistered) { "The provider must be signed on the service before being able to communicate." }
    }

    /**
     * Delivers a `ChatStateNotificationEvent` to all registered listeners.
     *
     * sourceContact the contact who has sent the notification
     * chatState the chat state from event delivery
     */
    fun fireChatStateNotificationsEvent(evt: ChatStateNotificationEvent?) {
        var listeners: Array<ChatStateNotificationsListener>
        synchronized(chatStateNotificationsListeners) { listeners = chatStateNotificationsListeners.toTypedArray() }

        // Timber.d("Dispatching ChatState Event to %d listeners with  chatState: %s", listeners.length, evt.getChatState());
        for (listener in listeners) listener.chatStateNotificationReceived(evt!!)
    }

    /**
     * Delivers a `ChatStateNotificationEvent` to all registered listeners for delivery failed event.
     *
     * sourceContact the contact who has sent the notification
     * evtCode the code of the event to deliver
     */
    fun fireChatStateNotificationsDeliveryFailedEvent(evt: ChatStateNotificationEvent) {
        var listeners: Array<ChatStateNotificationsListener>
        synchronized(chatStateNotificationsListeners) { listeners = chatStateNotificationsListeners.toTypedArray() }
        Timber.d("Dispatching Delivery Failure ChatState Event to %d listeners. ChatDescriptor '%s' has chatState: %s",
                listeners.size, evt.getChatDescriptor().toString(), evt.getChatState())
        for (listener in listeners) listener.chatStateNotificationDeliveryFailed(evt)
    }

    /**
     * Removes `listener` from the list of listeners registered for receiving
     * `ChatStateNotificationEvent`s.
     *
     * @param listener the `TypingNotificationsListener` listener that we'd like to remove
     * @see OperationSetChatStateNotifications.removeChatStateNotificationsListener
     */
    override fun removeChatStateNotificationsListener(listener: ChatStateNotificationsListener) {
        synchronized(chatStateNotificationsListeners) { chatStateNotificationsListeners.remove(listener) }
    }
}