/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.device

/**
 * Represents a special-purpose `MediaDevice` which is effectively built on top of and
 * forwarding to another `MediaDevice`.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface MediaDeviceWrapper : MediaDevice {
    /**
     * Gets the actual `MediaDevice` which this `MediaDevice` is effectively built on
     * top of and forwarding to.
     *
     * @return the actual `MediaDevice` which this `MediaDevice` is effectively built
     * on top of and forwarding to
     */
    val wrappedDevice: MediaDevice?
}