/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.MessageDeliveryFailedEvent
import net.java.sip.communicator.service.protocol.event.MessageListener
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent
import timber.log.Timber
import java.util.*

/**
 * Represents a default implementation of [OperationSetBasicInstantMessaging] in order to make
 * it easier for implementers to provide complete solutions while focusing on implementation-specific details.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class AbstractOperationSetBasicInstantMessaging : OperationSetBasicInstantMessaging {
    /**
     * A list of listeners registered for message events.
     */
    private val messageListeners = LinkedList<MessageListener>()

    /**
     * Registers a MessageListener with this operation set so that it gets notifications of
     * successful message delivery, failure or reception of incoming messages..
     *
     * @param listener the `MessageListener` to register.
     */
    override fun addMessageListener(listener: MessageListener) {
        synchronized(messageListeners) {
            if (!messageListeners.contains(listener)) {
                messageListeners.add(listener)
            }
        }
    }

    /**
     * Unregisters `listener` so that it won't receive any further notifications upon
     * successful message delivery, failure or reception of incoming messages..
     *
     * @param listener the `MessageListener` to unregister.
     */
    override fun removeMessageListener(listener: MessageListener) {
        synchronized(messageListeners) { messageListeners.remove(listener) }
    }

    /**
     * Create a IMessage instance for sending arbitrary MIME-encoding content.
     *
     * @param content content value
     * @param encType the MIME-type for `content`
     * @param subject a `String` subject or `null` for now subject.
     * @return the newly created message.
     */
    fun createMessage(content: ByteArray, encType: Int, subject: String?): IMessage {
        val contentAsString = String(content)
        return createMessage(contentAsString, encType, subject)
    }

    /**
     * Create a IMessage instance for sending a simple text messages with default (text/plain)
     * content type and encoding.
     *
     * @param messageText the string content of the message.
     * @return IMessage the newly created message
     */
    override fun createMessage(messageText: String): IMessage {
        return createMessage(messageText, IMessage.ENCODE_PLAIN, null)
    }

    abstract override fun createMessage(content: String, encType: Int, subject: String?): IMessage

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
    override fun createMessageWithUID(messageText: String, encType: Int, messageUID: String): IMessage {
        return createMessage(messageText)
    }

    protected enum class MessageEventType {
        None, MessageDelivered, MessageReceived, MessageDeliveryFailed, MessageDeliveryPending
    }

    /**
     * Delivers the specified event to all registered message listeners.
     *
     * @param evt the `EventObject` that we'd like delivered to all registered message listeners.
     */
    protected fun fireMessageEvent(evt: EventObject) {
        var listeners: Collection<MessageListener>
        synchronized(messageListeners) { listeners = ArrayList(messageListeners) }
        Timber.d("Dispatching Message Listeners = %d evt = %s", listeners.size, evt)

        /*
         * TODO Create a super class like this MessageEventObject that would contain the
         * MessageEventType. Also we could fire an event for the MessageDeliveryPending event type
         * (modify MessageListener and OperationSetInstantMessageTransform).
         */
        var eventType = MessageEventType.None
        when (evt) {
            is MessageDeliveredEvent -> {
                eventType = MessageEventType.MessageDelivered
            }
            is MessageReceivedEvent -> {
                eventType = MessageEventType.MessageReceived
            }
            is MessageDeliveryFailedEvent -> {
                eventType = MessageEventType.MessageDeliveryFailed
            }
        }

        // Transform the event.
        val events = messageTransform(evt, eventType)
        for (event in events) {
            try {
                if (event == null) return
                for (listener in listeners) {
                    when (eventType) {
                        MessageEventType.MessageDelivered -> listener.messageDelivered(event as MessageDeliveredEvent)
                        MessageEventType.MessageDeliveryFailed -> listener.messageDeliveryFailed(event as MessageDeliveryFailedEvent)
                        MessageEventType.MessageReceived -> listener.messageReceived(event as MessageReceivedEvent)
                        else -> {}
                    }
                }
            } catch (e: Throwable) {
                Timber.e(e, "Error delivering message")
            }
        }
    }

    /**
     * Messages pending delivery to be transformed.
     *
     * @param evt the message delivery event
     * @return returns message delivery events
     */
    protected fun messageDeliveryPendingTransform(evt: MessageDeliveredEvent): Array<MessageDeliveredEvent?> {
        val transformed = messageTransform(evt, MessageEventType.MessageDeliveryPending)
        val size = transformed.size
        val events = arrayOfNulls<MessageDeliveredEvent>(size)
        System.arraycopy(transformed, 0, events, 0, size)
        return events
    }

    /**
     * Transform provided source event by processing transform layers in sequence.
     *
     * @param evt the source event to transform
     * @param eventType the event type of the source event
     * @return returns the resulting (transformed) events, if any. (I.e. an array of 0 or more size containing events.)
     */
    private fun messageTransform(evt: EventObject?, eventType: MessageEventType): Array<EventObject?> {
        if (evt == null) {
            return arrayOfNulls(0)
        }

        val protocolProvider = when (eventType) {
            MessageEventType.MessageDelivered -> (evt as MessageDeliveredEvent).getContact().protocolProvider
            MessageEventType.MessageDeliveryFailed -> (evt as MessageDeliveryFailedEvent).destinationContact.protocolProvider
            MessageEventType.MessageDeliveryPending -> (evt as MessageDeliveredEvent).getContact().protocolProvider
            MessageEventType.MessageReceived -> (evt as MessageReceivedEvent).getSourceContact().protocolProvider
            else -> return arrayOf(evt)
        }
        val opSetMessageTransform = protocolProvider.getOperationSet(OperationSetInstantMessageTransform::class.java) as OperationSetInstantMessageTransformImpl?
                ?: return arrayOf(evt)

        // 'current' contains the events that need to be transformed. It should not contain null values.
        val current = LinkedList<EventObject?>()
        // Add source event as start of transformation.
        current.add(evt)
        // 'next' contains the resulting events after transformation in the current iteration. It
        // should not contain null values.
        val next = LinkedList<EventObject?>()
        for ((_, value) in opSetMessageTransform.transformLayers) {
            for (transformLayer in value) {
                next.clear()
                while (!current.isEmpty()) {
                    val event = current.remove()
                    when (eventType) {
                        MessageEventType.MessageDelivered -> {
                            val transformedDelivered = transformLayer!!.messageDelivered(event as MessageDeliveredEvent)
                            if (transformedDelivered != null) {
                                next.add(transformedDelivered)
                            }
                        }
                        MessageEventType.MessageDeliveryPending -> {
                            val evts = transformLayer!!.messageDeliveryPending(event as MessageDeliveredEvent)
                            for (mde in evts!!) {
                                if (mde != null) {
                                    next.add(mde)
                                }
                            }
                        }
                        MessageEventType.MessageDeliveryFailed -> {
                            val transformedDeliveryFailed = transformLayer!!.messageDeliveryFailed(event as MessageDeliveryFailedEvent)
                            if (transformedDeliveryFailed != null) {
                                next.add(transformedDeliveryFailed)
                            }
                        }
                        MessageEventType.MessageReceived -> {
                            val transformedReceived = transformLayer!!.messageReceived(event as MessageReceivedEvent)
                            if (transformedReceived != null) {
                                next.add(transformedReceived)
                            }
                        }
                        else -> next.add(event)
                    }
                }
                // Set events for next round of transformations.
                current.addAll(next)
            }
        }
        return current.toTypedArray()
    }

    /**
     * Determines whether the protocol supports the supplied content type for the given contact.
     *
     * @param mimeType the mime type we want to check
     * @param contact contact which is checked for supported encType
     * @return `true` if the contact supports it and `false` otherwise.
     */
    override fun isContentTypeSupported(mimeType: Int, contact: Contact): Boolean {
        // by default we support default mime type, for other mime-types method must be overridden
        return IMessage.ENCODE_PLAIN == mimeType
    }

    /**
     * Sends the `message` to the destination indicated by the `to`. Provides a
     * default implementation of this method.
     *
     * @param to the `Contact` to send `message` to
     * @param toResource the resource to which the message should be send
     * @param message the `IMessage` to send.
     */
    override fun sendInstantMessage(to: Contact, toResource: ContactResource?, message: IMessage) {
        sendInstantMessage(to, message)
    }

    /**
     * Returns the inactivity timeout in milliseconds.
     *
     * @return The inactivity timeout in milliseconds. Or -1 if undefined
     */
    override fun getInactivityTimeout(): Long {
        return -1
    }
}