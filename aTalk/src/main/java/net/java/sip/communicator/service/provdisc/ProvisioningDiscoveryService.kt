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

import net.java.sip.communicator.service.provdisc.event.DiscoveryListener

/**
 * Service that allow to retrieve a provisioning URL to configure
 * SIP Communicator. Implementations (not exhaustive) could use DHCP,
 * DNS (A, AAAA, SRV, TXT) or mDNS (Bonjour).
 *
 * @author Sebastien Vincent
 */
interface ProvisioningDiscoveryService {
    /**
     * Get the name of the method name used to retrieve provisioning URL.
     *
     * @return method name
     */
    fun getMethodName(): String?

    /**
     * Launch a discovery for a provisioning URL.
     *
     * This method is asynchronous, the response will be notified to any
     * `ProvisioningListener` registered.
     */
    fun startDiscovery()

    /**
     * Launch a discovery for a provisioning URL. This method is synchronous and
     * may block for some time.
     *
     * @return provisioning URL
     */
    fun discoverURL(): String?

    /**
     * Add a listener that will be notified when the
     * `startDiscovery` has finished.
     *
     * @param listener `ProvisioningListener` to add
     */
    fun addDiscoveryListener(listener: DiscoveryListener)

    /**
     * Add a listener that will be notified when the
     * `discoverProvisioningURL` has finished.
     *
     * @param listener `ProvisioningListener` to add
     */
    fun removeDiscoveryListener(listener: DiscoveryListener)
}