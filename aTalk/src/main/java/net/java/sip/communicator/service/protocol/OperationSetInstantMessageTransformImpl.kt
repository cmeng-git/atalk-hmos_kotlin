/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import java.util.*

/**
 * @author George Politis
 */
class OperationSetInstantMessageTransformImpl : OperationSetInstantMessageTransform {
    val transformLayers = Hashtable<Int, Vector<TransformLayer>>()
    override fun addTransformLayer(transformLayer: TransformLayer) {
        this.addTransformLayer(defaultPriority, transformLayer)
    }

    override fun addTransformLayer(priority: Int, transformLayer: TransformLayer) {
        synchronized(transformLayers) {
            if (!transformLayers.containsKey(defaultPriority)) transformLayers[defaultPriority] = Vector()
            transformLayers[defaultPriority]!!.add(transformLayer)
        }
    }

    override fun containsLayer(layer: TransformLayer): Boolean {
        synchronized(transformLayers) {
            for ((_, value) in transformLayers) {
                if (value.contains(layer)) return true
            }
        }
        return false
    }

    override fun removeTransformLayer(transformLayer: TransformLayer) {
        synchronized(transformLayers) {
            for ((_, value) in transformLayers) {
                value.remove(transformLayer)
            }
        }
    }

    companion object {
        private const val defaultPriority = 1
    }
}