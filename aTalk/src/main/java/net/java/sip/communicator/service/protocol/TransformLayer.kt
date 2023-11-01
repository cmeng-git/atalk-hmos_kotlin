/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.MessageDeliveryFailedEvent
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent

/**
 * An instance of the `TransformLayer`, when registered with
 * `OperationSetInstantMessageTransform` would be passed all message events. The class looks
 * a lot like a `MessageListener` with the major difference being that all the methods are
 * defined with a return value. The events we return would contain all message details after their
 * transformation from by the layer implementation. All methods return `null` in case the
 * `TransformLayer` implementation determines that the message event should not be determined
 * to the upper layers.
 *
 *
 * Important Notice: As of May 5 2009, this operation set is still a work in progress and may change
 * significantly in the following months. Any work based on this interface is therefore likely to
 * require frequent updates to keep compatibility.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface TransformLayer {
    /**
     * Called when a new incoming `Message` has been received. The method returns an instance
     * of `MessageReceivedEvent` which in many cases would be different from the `evt`
     * instance that was passed as param. The param and the return instances could very well (and
     * will often) be instances of different implementations so users of this interface (i.e.
     * protocol implementors) should make no assumptions for the class of the return type and copy
     * the returned instance into a new one if necessary.
     *
     * @param evt the `MessageReceivedEvent` containing the newly received message, its sender and other details.
     *
     * @return an instance of a (possibly new) `MessageReceivedEvent` instance containing the
     * transformed message or `null` if the `TransportLayer` has determined
     * that this message event should not be delivered to the upper layers.
     */
    fun messageReceived(evt: MessageReceivedEvent): MessageReceivedEvent?

    /**
     * Called when the underlying implementation has just been asked by other bundles to send an
     * outgoing message. The method returns an instance of `MessageDeliveredEvent` which in
     * many cases would be different from the `evt` instance that was passed as a parameter.
     * The param and the return instances could very well (and will often) be instances of different
     * implementations so users of this interface (i.e. protocol implementors) should make no
     * assumptions for the class of the return type and copy the returned instance into a new one if necessary.
     *
     * @param evt the MessageDeliveredEvent containing the id of the message that has caused the event.
     *
     * @return a number of instances of (possibly new) `MessageDeliveredEvent` instances
     * containing the transformed message(s) or an empty array if the
     * `TransportLayer` has determined that there are no message event that should be
     * delivered to the upper layers.
     */
    fun messageDeliveryPending(evt: MessageDeliveredEvent): Array<MessageDeliveredEvent?>?

    /**
     * Called when the underlying implementation has received an indication that a message, sent
     * earlier has been successfully received by the destination. The method returns an instance of
     * `MessageDeliveredEvent` which in many cases would be different from the `evt`
     * instance that was passed as a parameter. The param and the return instances could very well
     * (and will often) be instances of different implementations so users of this interface (i.e.
     * protocol implementors) should make no assumptions for the class of the return type and copy
     * the returned instance into a new one if necessary.
     *
     * @param evt the MessageDeliveredEvent containing the id of the message that has caused the event.
     *
     * @return an instance of a (possibly new) `MessageDeliveredEvent` instance containing
     * the transformed message or `null` if the `TransportLayer` has
     * determined that this message event should not be delivered to the upper layers.
     */
    fun messageDelivered(evt: MessageDeliveredEvent): MessageDeliveredEvent?

    /**
     * Called to indicated that delivery of a message sent earlier has failed. Reason code and
     * phrase are contained by the `MessageFailedEvent` The method returns an instance of
     * `MessageDeliveredEvent` which in many cases would be different from the `evt`
     * instance that was passed as a parameter. The param and the return instances could very well
     * (and will often) be instances of different implementations so users of this interface (i.e.
     * protocol implementors) should make no assumptions for the class of the return type and copy
     * the returned instance into a new one if necessary.
     *
     * @param evt the `MessageFailedEvent` containing the ID of the message whose delivery has failed.
     *
     * @return an instance of a (possibly new) `MessageDeliveredEvent` instance containing
     * the transformed message or `null` if the `TransportLayer` has
     * determined that this message event should not be delivered to the upper layers.
     */
    fun messageDeliveryFailed(evt: MessageDeliveryFailedEvent): MessageDeliveryFailedEvent?
}