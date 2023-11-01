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
package net.java.sip.communicator.service.provdisc.event

import java.util.*

/**
 * Event representing that a provisioning URL has been retrieved.
 *
 * @author Sebastien Vincent
 */
class DiscoveryEvent(source: Any?, url: String?) : EventObject(source) {
    /**
     * Provisioning URL.
     */
    private var url: String? = null

    /**
     * Constructor.
     *
     * @param source object that have created this event
     * @param url provisioning URL
     */
    init {
        this.url = url
    }

    /**
     * Get the provisioning URL.
     *
     * @return provisioning URL
     */
    fun getProvisioningURL(): String? {
        return url
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}