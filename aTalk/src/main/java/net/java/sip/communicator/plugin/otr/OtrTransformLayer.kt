/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr

import net.java.otr4j.io.SerializationConstants
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging
import net.java.sip.communicator.service.protocol.TransformLayer
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.MessageDeliveryFailedEvent
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent

/**
 * The Off-the-Record [TransformLayer] implementation.
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
class OtrTransformLayer : TransformLayer {
    /*
	 * Implements TransformLayer#messageDelivered(MessageDeliveredEvent).
	 */
    override fun messageDelivered(evt: MessageDeliveredEvent): MessageDeliveredEvent? {
        // If this is a message otr4j injected earlier, don't display it,
        // this may have to change when we add support for fragmentation..
        return if (OtrActivator.scOtrEngine.isMessageUIDInjected(evt.getSourceMessage().getMessageUID()))
            null else evt
    }

    /*
	 * Implements
	 * TransformLayer#messageDeliveryFailed(MessageDeliveryFailedEvent).
	 */
    override fun messageDeliveryFailed(evt: MessageDeliveryFailedEvent): MessageDeliveryFailedEvent? {
        return evt
    }

    /*
	 * Implements TransformLayer#messageDeliveryPending(MessageDeliveredEvent).
	 */
    override fun messageDeliveryPending(evt: MessageDeliveredEvent): Array<MessageDeliveredEvent?> {
        val contact = evt.getContact()
        val otrContact = OtrContactManager.getOtrContact(contact, evt.getContactResource())

        // If this is a message otr4j injected earlier, return the event as is.
        if (OtrActivator.scOtrEngine.isMessageUIDInjected(evt.getSourceMessage().getMessageUID())) return arrayOf(evt)

        // Process the outgoing message.
        val msgContent = evt.getSourceMessage().getContent()
        val processedMessageContent = OtrActivator.scOtrEngine.transformSending(otrContact, msgContent)
        if (processedMessageContent == null || processedMessageContent.isEmpty() || processedMessageContent[0].isEmpty()) return arrayOfNulls(0)
        if (processedMessageContent.size == 1 && processedMessageContent[0] == msgContent) return arrayOf(evt)
        val processedEvents = arrayOfNulls<MessageDeliveredEvent>(processedMessageContent.size)
        val imOpSet = contact.protocolProvider.getOperationSet(OperationSetBasicInstantMessaging::class.java)
        val encType = evt.getSourceMessage().getEncType()
        val subject = evt.getSourceMessage().getSubject()
        val contactResource = evt.getContactResource()
        val sender = evt.getSender()
        val timeStamp = evt.getTimestamp()
        for (i in processedMessageContent.indices) {
            val fragmentContent = processedMessageContent[i]
            // Forge a new message based on the new contents.
            val processedMessage = imOpSet!!.createMessage(fragmentContent, encType, subject)
            // Create a new processedEvent.
            val processedEvent = MessageDeliveredEvent(processedMessage, contact, contactResource, sender, timeStamp)
            if (processedMessage.getContent()!!.contains(SerializationConstants.HEAD)) {
                processedEvent.setMessageEncrypted(true)
            }
            processedEvents[i] = processedEvent
        }
        return processedEvents
    }

    /*
	 * Implements TransformLayer#messageReceived(MessageReceivedEvent).
	 */
    override fun messageReceived(evt: MessageReceivedEvent): MessageReceivedEvent? {
        val contact = evt.getSourceContact()
        val otrContact = OtrContactManager.getOtrContact(contact, evt.getContactResource())

        // Process the incoming message.
        val msgContent = evt.getSourceMessage().getContent()
        val processedMessageContent = OtrActivator.scOtrEngine.transformReceiving(otrContact, msgContent)

        if (processedMessageContent == null || processedMessageContent.isEmpty()) return null
        if (processedMessageContent == msgContent) return evt

        // Forge a new message based on the new contents.
        val imOpSet = contact.protocolProvider.getOperationSet(OperationSetBasicInstantMessaging::class.java)
        val processedMessage = imOpSet!!.createMessageWithUID(processedMessageContent,
                evt.getSourceMessage().getEncType(), evt.getSourceMessage().getMessageUID())

        // Create a new event and return.
        return MessageReceivedEvent(processedMessage, contact,
                evt.getContactResource(), evt.getSender(), evt.getTimestamp(), evt.getCorrectedMessageUID())
    }
}