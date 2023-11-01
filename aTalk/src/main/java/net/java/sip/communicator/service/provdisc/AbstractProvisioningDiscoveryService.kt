/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.service.provdisc

import net.java.sip.communicator.service.provdisc.event.DiscoveryEvent
import net.java.sip.communicator.service.provdisc.event.DiscoveryListener

/**
 * Abstract base class of ProvisioningDiscoveryService that ease implementation
 *
 * @author seb
 */
abstract class AbstractProvisioningDiscoveryService : ProvisioningDiscoveryService {
    /**
     * List of `ProvisioningListener` that will be notified when
     * a provisioning URL is retrieved.
     */
    private val listeners = ArrayList<DiscoveryListener>()

    /**
     * Get the name of the method name used to retrieve provisioning URL.
     *
     * @return method name
     */
    abstract override fun getMethodName(): String?

    /**
     * Launch a discovery for a provisioning URL.
     *
     * This method is asynchronous, the response will be notified to any
     * `ProvisioningListener` registered.
     */
    abstract override fun startDiscovery()

    /**
     * Launch a discovery for a provisioning URL. This method is synchronous and
     * may block for some time.
     *
     * @return provisioning URL
     */
    abstract override fun discoverURL(): String?

    /**
     * Add a listener that will be notified when the
     * `discoverProvisioningURL` has finished.
     *
     * @param listener `ProvisioningListener` to add
     */
    override fun addDiscoveryListener(listener: DiscoveryListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    /**
     * Add a listener that will be notified when the
     * `discoverProvisioningURL` has finished.
     *
     * @param listener `ProvisioningListener` to add
     */
    override fun removeDiscoveryListener(listener: DiscoveryListener) {
        if (listeners.contains(listener)) {
            listeners.remove(listener)
        }
    }

    /**
     * Notify all listeners about a `DiscoveryEvent`.
     *
     * @param event `DiscoveryEvent` that contains provisioning URL
     */
    fun fireDiscoveryEvent(event: DiscoveryEvent?) {
        for (listener in listeners) {
            listener.notifyProvisioningURL(event)
        }
    }
}