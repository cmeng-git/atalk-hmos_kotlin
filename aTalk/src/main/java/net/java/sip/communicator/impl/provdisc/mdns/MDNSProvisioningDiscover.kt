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
package net.java.sip.communicator.impl.provdisc.mdns

import net.java.sip.communicator.service.provdisc.event.DiscoveryEvent
import net.java.sip.communicator.service.provdisc.event.DiscoveryListener
import timber.log.Timber
import java.io.IOException
import javax.jmdns.JmDNS

/**
 * Class that will perform mDNS provisioning discovery.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class MDNSProvisioningDiscover
/**
 * Constructor.
 */
    : Runnable {
    /**
     * List of `ProvisioningListener` that will be notified when a provisioning URL is retrieved.
     */
    private val listeners = ArrayList<DiscoveryListener>()

    /**
     * Reference to JmDNS singleton.
     */
    private var jmdns: JmDNS? = null

    /**
     * Thread entry point. It runs `discoverProvisioningURL` in a separate thread.
     */
    override fun run() {
        val url = discoverProvisioningURL()
        if (url != null) {
            /* as we run in an asynchronous manner, notify the listener */
            val evt = DiscoveryEvent(this, url)
            for (listener in listeners) {
                listener.notifyProvisioningURL(evt)
            }
        }
    }

    /**
     * It sends a mDNS to retrieve provisioning URL and wait for a response.
     * Thread stops after first successful answer that contains the provisioning URL.
     *
     * @return provisioning URL or null if no provisioning URL was discovered
     */
    fun discoverProvisioningURL(): String? {
        val url = StringBuffer()
        jmdns = try {
            JmDNS.create()
        } catch (e: IOException) {
            Timber.i(e, "Failed to create JmDNS")
            return null
        }
        var info = jmdns!!.getServiceInfo("_https._tcp.local", "Provisioning URL", MDNS_TIMEOUT.toLong())
        if (info == null) {
            /* try HTTP */
            info = jmdns!!.getServiceInfo("_http._tcp.local", "Provisioning URL", MDNS_TIMEOUT.toLong())
        }
        if (info != null && info.name == "Provisioning URL") {
            val protocol = info.application
            url.append(info.getURL(protocol))
            val en = info.propertyNames
            if (en.hasMoreElements()) {
                url.append("?")
            }

            /* add the parameters */while (en.hasMoreElements()) {
                val tmp = en.nextElement()
                /* take all other parameters except "path" */
                if (tmp == "path") {
                    continue
                }
                url.append(tmp)
                url.append("=")
                url.append(info.getPropertyString(tmp))
                if (en.hasMoreElements()) {
                    url.append("&")
                }
            }
        }
        /* close jmdns */
        try {
            jmdns!!.close()
            jmdns = null
        } catch (e: Exception) {
            Timber.w(e, "Failed to close JmDNS")
        }
        return if (url.toString().length > 0) url.toString() else null
    }

    /**
     * Add a listener that will be notified when the `discoverProvisioningURL` has finished.
     *
     * @param listener `ProvisioningListener` to add
     */
    fun addDiscoveryListener(listener: DiscoveryListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    /**
     * Add a listener that will be notified when the `discoverProvisioningURL` has finished.
     *
     * @param listener `ProvisioningListener` to add
     */
    fun removeDiscoveryListener(listener: DiscoveryListener) {
        if (listeners.contains(listener)) {
            listeners.remove(listener)
        }
    }

    companion object {
        /**
         * MDNS timeout (in milliseconds).
         */
        private const val MDNS_TIMEOUT = 2000
    }
}