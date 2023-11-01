/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import java.util.*

/**
 * Represents a listener which is to be notified before and after an associated
 * `DeviceSystem`'s function to update the list of available devices is invoked.
 *
 * @author Lyubomir Marinov
 */
interface UpdateAvailableDeviceListListener : EventListener {
    /**
     * Notifies this listener that the associated `DeviceSystem`'s function to update the
     * list of available devices was invoked.
     *
     * @throws Exception
     * if this implementation encounters an error. Any `Throwable` apart from
     * `ThreadDeath` will be ignored after it is logged for debugging purposes.
     */
    @Throws(Exception::class)
    fun didUpdateAvailableDeviceList()

    /**
     * Notifies this listener that the associated `DeviceSystem`'s function to update the
     * list of available devices will be invoked.
     *
     * @throws Exception
     * if this implementation encounters an error. Any `Throwable` apart from
     * `ThreadDeath` will be ignored after it is logged for debugging purposes.
     */
    @Throws(Exception::class)
    fun willUpdateAvailableDeviceList()
}