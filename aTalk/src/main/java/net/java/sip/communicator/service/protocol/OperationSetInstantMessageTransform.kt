/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * The Instant Message Transform operation set allows, when \ supported to insert message transform
 * layers that could change incoming messages before they are delivered to the user and outgoing
 * ones before they reach the protocol stack and get sent. One use case of this operation set is
 * support of upper layer encryption mechanisms like Omemo. Other cases may include hyperlink
 * presentation, support for wiki words etc.
 *
 *
 * Important Notice: As of May 5 2009, this operation set is still a work in progress and may change
 * significantly in the following months. Any work based on this interface is therefore likely to
 * require frequent updates to keep compatibility.
 *
 * @author Emil Ivov
 */
interface OperationSetInstantMessageTransform : OperationSet {
    /**
     * Adds a transformation layer to this protocol provider using a default priority value.
     *
     * @param transformLayer
     * the `TransformLayer` that we'd like to add to our protocol provider.
     */
    fun addTransformLayer(transformLayer: TransformLayer)

    /**
     * Adds `transformLayer` to the layers currently used for message transformation in this
     * provider and assigns the specified `priority` to it.
     *
     * @param priority
     * the priority/order index that we'd like to insert `transportLayer` at.
     * @param transformLayer
     * the layer we are registering
     */
    fun addTransformLayer(priority: Int, transformLayer: TransformLayer)

    /**
     * Removes `transformLayer` from the list of currently registered transform layers so
     * that it won't be notified for further message events.
     *
     * @param transformLayer
     * the layer we are trying to remove.
     */
    fun removeTransformLayer(transformLayer: TransformLayer)

    /**
     * Determines whether `layer` is currently registered with this provider.
     *
     * @param layer
     * the layer for which we'd like to know whether it is currently registered with this
     * provider.
     *
     * @return `true` if `layer` is currently registered with this provider and
     * `false` otherwise.
     */
    fun containsLayer(layer: TransformLayer): Boolean
}