/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.event

/**
 * Represents a listener (to be) notified about changes in the volume level/value maintained by a
 * `VolumeControl` .
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
interface VolumeChangeListener {
    /**
     * Notifies this instance that the volume level/value maintained by a source
     * `VolumeControl` (to which this instance has previously been added) has changed.
     *
     * @param volumeChangeEvent
     * a `VolumeChangeEvent` which details the source `VolumeControl` which has
     * fired the notification and the volume level/value
     */
    fun volumeChange(volumeChangeEvent: VolumeChangeEvent)
}