/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

/**
 * Manages the list of active (currently plugged-in) notify devices and manages user preferences
 * between all known devices (previously and actually plugged-in).
 *
 * @author Vincent Lucas
 * @author Eng Chong Meng
 */
/**
 * Initializes the notify device list management.
 *
 * @param audioSystem The audio system managing this notify device list.
 */
class NotifyDevices(audioSystem: AudioSystem) : Devices(audioSystem){
    /**
     * Returns the property of the notify devices.
     *
     * @return The property of the notify devices.
     */
    override val propDevice: String
        get() = PROP_DEVICE

    companion object {
        /**
         * The property of the notify devices.
         */
        const val PROP_DEVICE = "notifyDevice"
    }
}
