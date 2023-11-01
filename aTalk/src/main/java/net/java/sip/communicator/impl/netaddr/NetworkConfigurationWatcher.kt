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
package net.java.sip.communicator.impl.netaddr

import net.java.sip.communicator.service.netaddr.event.ChangeEvent
import net.java.sip.communicator.service.netaddr.event.NetworkConfigurationChangeListener
import net.java.sip.communicator.service.sysactivity.SystemActivityChangeListener
import net.java.sip.communicator.service.sysactivity.SystemActivityNotificationsService
import net.java.sip.communicator.service.sysactivity.event.SystemActivityEvent
import net.java.sip.communicator.util.ServiceUtils
import okhttp3.internal.notifyAll
import org.osgi.framework.Bundle
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

/**
 * Periodically checks the current network interfaces to track changes and fire events on those changes.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class NetworkConfigurationWatcher internal constructor() : SystemActivityChangeListener, ServiceListener, Runnable {
    /**
     * The current active interfaces.
     */
    private val activeInterfaces = HashMap<String, MutableList<InetAddress>>()

    /**
     * Whether thread checking for network notifications is running.
     */
    private var isRunning = false

    /**
     * Service we use to listen for network changes.
     */
    private var systemActivityNotificationsService: SystemActivityNotificationsService? = null

    /**
     * The thread dispatcher of network change events.
     */
    private val eventDispatcher = NetworkEventDispatcher()

    /**
     * Inits configuration watcher.
     */
    init {
        try {
            checkNetworkInterfaces(false, 0, true)
        } catch (e: SocketException) {
            Timber.e(e, "Error checking network interfaces")
        }
    }

    /**
     * Adds new `NetworkConfigurationChangeListener` which will be informed for network configuration changes.
     *
     * @param listener the listener.
     */
    fun addNetworkConfigurationChangeListener(listener: NetworkConfigurationChangeListener) {
        eventDispatcher.addNetworkConfigurationChangeListener(listener)
        initialFireEvents(listener)
        NetaddrActivator.getBundleContext()!!.addServiceListener(this)
        if (systemActivityNotificationsService == null) {
            val systActService = ServiceUtils.getService(NetaddrActivator.getBundleContext(),
                    SystemActivityNotificationsService::class.java)
            handleNewSystemActivityNotificationsService(systActService)
        }
    }

    /**
     * Used to fire initial events to newly added listers.
     *
     * @param listener the listener to fire.
     */
    private fun initialFireEvents(listener: NetworkConfigurationChangeListener) {
        try {
            val e = NetworkInterface.getNetworkInterfaces()
            while (e.hasMoreElements()) {
                val networkInterface = e.nextElement()
                if (networkInterface.isLoopback) continue

                // if interface is up and has some valid(non-local) address add it to currently active
                if (networkInterface.isUp) {
                    val `as` = networkInterface.inetAddresses
                    var hasAddress = false
                    while (`as`.hasMoreElements()) {
                        val inetAddress = `as`.nextElement()
                        if (inetAddress.isLinkLocalAddress) continue
                        hasAddress = true
                        NetworkEventDispatcher.fireChangeEvent(ChangeEvent(
                                networkInterface.name, ChangeEvent.ADDRESS_UP,
                                inetAddress, false, true), listener)
                    }
                    if (hasAddress) NetworkEventDispatcher.fireChangeEvent(ChangeEvent(networkInterface.name,
                            ChangeEvent.IFACE_UP, null, false, true), listener)
                }
            }
        } catch (e: SocketException) {
            Timber.e(e, "Error checking network interfaces")
        }
    }

    /**
     * Saves the reference for the service and add a listener if the desired events are supported. Or start
     * the checking thread otherwise.
     *
     * @param newService
     */
    private fun handleNewSystemActivityNotificationsService(newService: SystemActivityNotificationsService?) {
        if (newService == null) return
        systemActivityNotificationsService = newService
        if (systemActivityNotificationsService!!.isSupported(SystemActivityEvent.EVENT_NETWORK_CHANGE)) {
            systemActivityNotificationsService!!.addSystemActivityChangeListener(this)
        } else {
            if (!isRunning) {
                isRunning = true
                val th = Thread(this)
                // set to max priority to prevent detecting sleep if the cpu is overloaded
                th.priority = Thread.MAX_PRIORITY
                th.start()
            }
        }
    }

    /**
     * Remove `NetworkConfigurationChangeListener`.
     *
     * @param listener the listener.
     */
    fun removeNetworkConfigurationChangeListener(listener: NetworkConfigurationChangeListener?) {
        eventDispatcher.removeNetworkConfigurationChangeListener(listener)
    }

    /**
     * When new protocol provider is registered we add needed listeners.
     *
     * @param serviceEvent ServiceEvent
     */
    override fun serviceChanged(serviceEvent: ServiceEvent) {
        val serviceRef = serviceEvent.serviceReference

        // if the event is caused by a bundle being stopped, we don't want to
        // know we are shutting down
        if (serviceRef.bundle.state == Bundle.STOPPING) {
            return
        }
        val sService = NetaddrActivator.getBundleContext()!!.getService<Any>(serviceRef as ServiceReference<Any>)
        if (sService is SystemActivityNotificationsService) {
            when (serviceEvent.type) {
                ServiceEvent.REGISTERED -> {
                    if (systemActivityNotificationsService != null) return
                    handleNewSystemActivityNotificationsService(sService)
                }
                ServiceEvent.UNREGISTERING -> sService.removeSystemActivityChangeListener(this)
            }
        }
    }

    /**
     * Stop.
     */
    fun stop() {
        if (isRunning) {
            synchronized(this) {
                isRunning = false
                notifyAll()
            }
        }
        eventDispatcher.stop()
    }

    /**
     * This method gets called when a notification action for a particular event
     * type has been changed. We are interested in sleep and network changed events.
     *
     * @param event the `NotificationActionTypeEvent`, which is
     * dispatched when an action has been changed.
     */
    override fun activityChanged(event: SystemActivityEvent?) {
        if (event!!.getEventID() == SystemActivityEvent.EVENT_SLEEP) {
            // oo standby lets fire down to all interfaces so they can reconnect
            downAllInterfaces()
        } else if (event.getEventID() == SystemActivityEvent.EVENT_NETWORK_CHANGE) {
            try {
                checkNetworkInterfaces(true, 0, true)
            } catch (e: SocketException) {
                Timber.e(e, "Error checking network interfaces")
            }
        } else if (event.getEventID() == SystemActivityEvent.EVENT_DNS_CHANGE) {
            try {
                eventDispatcher.fireChangeEvent(ChangeEvent(event.source, ChangeEvent.DNS_CHANGE))
            } catch (t: Throwable) {
                Timber.e("Error dispatching dns change.")
            }
        }
    }

    /**
     * Down all interfaces and fire events for it.
     */
    private fun downAllInterfaces() {
        for (niface in activeInterfaces.keys) {
            eventDispatcher.fireChangeEvent(ChangeEvent(niface, ChangeEvent.IFACE_DOWN, true))
        }
        activeInterfaces.clear()
    }

    /**
     * Checks current interfaces configuration against the last saved
     * active interfaces.
     *
     * @param fireEvents whether we will fire events when we detect
     * that interface is changed. When we start we query the interfaces
     * just to check which are online, without firing events.
     * @param waitBeforeFiringUpEvents milliseconds to wait before
     * firing events for interfaces up, sometimes we must wait a little bit
     * and give time for interfaces to configure fully (dns on linux).
     * @param printDebugInfo whether to print debug info, do not print
     * anything if we are constantly checking as it will flood logs and made them unusable.
     */
    @Throws(SocketException::class)
    private fun checkNetworkInterfaces(fireEvents: Boolean, waitBeforeFiringUpEvents: Int, printDebugInfo: Boolean) {
        val e = NetworkInterface.getNetworkInterfaces()
        // val currentActiveInterfaces: MutableIterator<MutableMap.MutableEntry<String, List<InetAddress>>>
        val currentActiveInterfaces = HashMap<String, MutableList<InetAddress>>()

        while (e.hasMoreElements()) {
            val networkInterface = e.nextElement()
            if (networkInterface.isLoopback) continue

            // if interface is up and has some valid(non-local) address add it to currently active
            if (networkInterface.isUp) {
                val addresses = ArrayList<InetAddress>()
                val `as` = networkInterface.inetAddresses
                while (`as`.hasMoreElements()) {
                    val inetAddress = `as`.nextElement()
                    if (inetAddress.isLinkLocalAddress) continue
                    addresses.add(inetAddress)
                }
                if (addresses.size > 0) currentActiveInterfaces[networkInterface.name] = addresses
            }
        }

        // add network debug info, to track wake up problems
        if (printDebugInfo) {
            for ((key, value) in activeInterfaces) {
                Timber.i("Previously Active %s:%s", key, value)
            }
            for ((key, value) in currentActiveInterfaces) {
                Timber.i("Currently Active %s:%s", key, value)
            }
        }

        // search for down interface
        val inactiveActiveInterfaces = ArrayList(activeInterfaces.keys)
        val currentActiveInterfacesSet = ArrayList(currentActiveInterfaces.keys)
        inactiveActiveInterfaces.removeAll(currentActiveInterfacesSet.toSet())

        // fire that interface has gone down
        for (i in inactiveActiveInterfaces.indices) {
            val iface = inactiveActiveInterfaces[i]
            if (!currentActiveInterfacesSet.contains(iface)) {
                if (fireEvents) eventDispatcher.fireChangeEvent(ChangeEvent(iface, ChangeEvent.IFACE_DOWN))
                activeInterfaces.remove(iface)
            }
        }

        // now look at the addresses of the connected interfaces
        // if something has gown down
        var activeEntriesIter = activeInterfaces.entries.iterator()
        while (activeEntriesIter.hasNext()) {
            val (key, value) = activeEntriesIter.next()
            val addrIter = value.iterator()
            while (addrIter.hasNext()) {
                val addr = addrIter.next()

                // if address is missing in current active interfaces it means it has gone done
                val addresses = currentActiveInterfaces[key]
                if (addresses != null && !addresses.contains(addr)) {
                    if (fireEvents) eventDispatcher.fireChangeEvent(ChangeEvent(key, ChangeEvent.ADDRESS_DOWN, addr))
                    addrIter.remove()
                }
            }
        }

        if (waitBeforeFiringUpEvents > 0
                && currentActiveInterfaces.isNotEmpty()) {
            // calm for a while, we sometimes receive those events and
            // configuration has not yet finished (dns can be the old one)
            synchronized(this) {
                try {
                    (this as Object).wait(waitBeforeFiringUpEvents.toLong())
                } catch (ignore: InterruptedException) {
                }
            }
        }

        // now look at the addresses of the connected interfaces if something has gown up
        activeEntriesIter = currentActiveInterfaces.entries.iterator()
        while (activeEntriesIter.hasNext()) {
            val (key, value) = activeEntriesIter.next()
            for (addr in value) {
                // if address is missing in active interfaces it means it has gone up
                val addresses = activeInterfaces[key]
                if (addresses != null && !addresses.contains(addr)) {
                    if (fireEvents) eventDispatcher.fireChangeEvent(ChangeEvent(key, ChangeEvent.ADDRESS_UP, addr))
                    addresses.add(addr)
                }
            }
        }

        // now we leave with only with the new and up interfaces in currentActiveInterfaces Map
        for (s in activeInterfaces.keys) {
            currentActiveInterfaces.remove(s)
        }

        // fire that interface has gone up
        activeEntriesIter = currentActiveInterfaces.entries.iterator()
        while (activeEntriesIter.hasNext()) {
            val (key, value) = activeEntriesIter.next()
            for (addr in value) {
                if (fireEvents) eventDispatcher.fireChangeEvent(ChangeEvent(key, ChangeEvent.ADDRESS_UP, addr))
            }
            if (fireEvents) {
                // if we haven't waited before, lets wait here and give time to underlying os to configure fully the
                // network interface (receive and store dns config)
                var waitTime = waitBeforeFiringUpEvents
                if (waitTime == 0) {
                    waitTime = 500
                }
                eventDispatcher.fireChangeEvent(ChangeEvent(key, ChangeEvent.IFACE_UP), waitTime)
            }
            activeInterfaces[key] = value
        }
    }

    /**
     * Main loop of this thread.
     */
    override fun run() {
        var last = 0L
        var isAfterStandby = false
        while (isRunning) {
            val curr = System.currentTimeMillis()

            // if time spent between checks is more than 4 times longer than the check interval we consider it as a
            // new check after standby
            if (!isAfterStandby && last != 0L) isAfterStandby = last + 4 * CHECK_INTERVAL - curr < 0
            if (isAfterStandby) {
                // oo standby lets fire down to all interfaces so they can reconnect
                downAllInterfaces()

                // we have fired events for standby, make it to false now so we can calculate it again next time
                isAfterStandby = false
                last = curr

                // give time to interfaces
                synchronized(this) {
                    try {
                        (this as Object).wait(CHECK_INTERVAL)
                    } catch (ignore: Exception) {
                    }
                }
                continue
            }
            try {
                val networkIsUP = activeInterfaces.isNotEmpty()
                checkNetworkInterfaces(true, 1000, false)

                // fire that network has gone up
                if (!networkIsUP && activeInterfaces.isNotEmpty()) {
                    isAfterStandby = false
                }
                // save the last time that we checked
                last = System.currentTimeMillis()
            } catch (e: SocketException) {
                Timber.e(e, "Error checking network interfaces")
            }
            synchronized(this) {
                try {
                    (this as Object).wait(CHECK_INTERVAL)
                } catch (ignore: Exception) {
                }
            }
        }
    }

    companion object {
        /**
         * Interval between check of network configuration.
         */
        private const val CHECK_INTERVAL = 3000L // 3 sec.
    }
}