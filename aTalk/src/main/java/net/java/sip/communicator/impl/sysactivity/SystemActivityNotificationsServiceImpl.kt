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
package net.java.sip.communicator.impl.sysactivity

import net.java.sip.communicator.impl.sysactivity.SystemActivityNotifications.NotificationsDelegate
import net.java.sip.communicator.service.sysactivity.SystemActivityChangeListener
import net.java.sip.communicator.service.sysactivity.SystemActivityNotificationsService
import net.java.sip.communicator.service.sysactivity.event.SystemActivityEvent
import okhttp3.internal.notifyAll
import okhttp3.internal.wait
import org.atalk.util.OSUtils
import timber.log.Timber
import java.util.*

/**
 * Service implementation listens for computer changes as sleeping, network change, inactivity.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
open class SystemActivityNotificationsServiceImpl : NotificationsDelegate, SystemActivityNotificationsService, Runnable {
    /**
     * The thread dispatcher of network change events.
     */
    private val eventDispatcher = SystemActivityEventDispatcher()

    /**
     * A list of listeners registered for idle events.
     */
    private val idleChangeListeners = HashMap<SystemActivityChangeListener?, Long>()

    /**
     * Listeners which are fired for idle state and which will be fired with idle end when needed.
     */
    private val listenersInIdleState = ArrayList<SystemActivityChangeListener?>()

    /**
     * Whether current service is started or stopped.
     */
    private var running = false

    /**
     * The time when we received latest network change event.
     */
    private var lastNetworkChange = -1L

    /**
     * Whether network is currently connected.
     */
    private var networkIsConnected: Boolean? = null
    /**
     * Returns or instantiate the manager.
     *
     * @return
     */
    /**
     * The currently instantiated and working manager.
     */
    private var currentRunningManager: SystemActivityManager? = null
        get() {
            if (field == null) {
                try {
                    var className: String? = null
                    if (OSUtils.IS_LINUX) {
                        className = SYSTEM_ACTIVITY_MANAGER_LINUX_CLASS
                    } else if (OSUtils.IS_ANDROID) {
                        className = SYSTEM_ACTIVITY_MANAGER_ANDROID_CLASS
                    }
                    if (className != null) field = Class.forName(className).newInstance() as SystemActivityManager
                } catch (t: Throwable) {
                    Timber.e(t, "Error creating manager")
                }
            }
            return field
        }

    /**
     * Init and start notifications.
     */
    fun start() {
        running = true

        // set the delegate and start notification in new thread make sure we don't block startup process
        val notifystartThread = Thread({
            SystemActivityNotifications.setDelegate(this@SystemActivityNotificationsServiceImpl)
            SystemActivityNotifications.start()
        }, "SystemActivityNotificationsServiceImpl")
        notifystartThread.isDaemon = true
        notifystartThread.start()
        if (isSupported(SystemActivityEvent.EVENT_SYSTEM_IDLE)) {
            // a thread periodically checks system idle state and if it pass the
            // idle time for a particular listener, will inform it.
            val idleNotifyThread = Thread(this,
                    "SystemActivityNotificationsServiceImpl.IdleNotifyThread")
            idleNotifyThread.isDaemon = true
            idleNotifyThread.start()
        }
        if (currentRunningManager != null) currentRunningManager!!.start()
    }

    /**
     * Stop notifications.
     */
    fun stop() {
        SystemActivityNotifications.stop()
        if (currentRunningManager != null) currentRunningManager!!.stop()
        eventDispatcher.stop()
        running = false
        synchronized(this) { this.notifyAll() }
    }

    /**
     * Registers a listener that would be notified of changes that have occurred in the underlying system.
     *
     * @param listener the listener that we'd like to register for changes in the underlying system.
     */
    override fun addSystemActivityChangeListener(listener: SystemActivityChangeListener?) {
        eventDispatcher.addSystemActivityChangeListener(listener)
    }

    /**
     * Remove the specified listener so that it won't receive further notifications of changes that occur in the underlying system
     *
     * @param listener the listener to remove.
     */
    override fun removeSystemActivityChangeListener(listener: SystemActivityChangeListener?) {
        eventDispatcher.removeSystemActivityChangeListener(listener)
    }

    /**
     * Registers a listener that would be notified for idle of the system for `idleTime`.
     *
     * @param idleTime the time in milliseconds after which we will consider system to be idle. This doesn't count
     * when system seems idle as monitor is off or screensaver is on, or desktop is locked.
     * @param listener the listener that we'd like to register for changes in the underlying system.
     */
    override fun addIdleSystemChangeListener(idleTime: Long, listener: SystemActivityChangeListener?) {
        synchronized(idleChangeListeners) { if (idleTime > 0 && !idleChangeListeners.containsKey(listener)) idleChangeListeners[listener] = idleTime }
    }

    /**
     * Remove the specified listener so that it won't receive further notifications for idle system.
     *
     * @param listener the listener to remove.
     */
    override fun removeIdleSystemChangeListener(listener: SystemActivityChangeListener?) {
        synchronized(idleChangeListeners) { idleChangeListeners.remove(listener) }
    }

    /**
     * The time since last user input. The time the system has been idle.
     *
     * @return time the system has been idle.
     */
    override fun getTimeSinceLastInput(): Long {
        return if (SystemActivityNotifications.isLoaded) SystemActivityNotifications.lastInput else -1
    }

    /**
     * Callback method when receiving notifications.
     *
     * @param type type of the notification.
     */
    override fun notify(type: Int) {
        var evt: SystemActivityEvent? = null
        when (type) {
            SystemActivityNotifications.NOTIFY_SLEEP -> evt = SystemActivityEvent(this, SystemActivityEvent.EVENT_SLEEP)
            SystemActivityNotifications.NOTIFY_WAKE -> evt = SystemActivityEvent(this, SystemActivityEvent.EVENT_WAKE)
            SystemActivityNotifications.NOTIFY_DISPLAY_SLEEP -> evt = SystemActivityEvent(this, SystemActivityEvent.EVENT_DISPLAY_SLEEP)
            SystemActivityNotifications.NOTIFY_DISPLAY_WAKE -> evt = SystemActivityEvent(this, SystemActivityEvent.EVENT_DISPLAY_WAKE)
            SystemActivityNotifications.NOTIFY_SCREENSAVER_START -> evt = SystemActivityEvent(this, SystemActivityEvent.EVENT_SCREENSAVER_START)
            SystemActivityNotifications.NOTIFY_SCREENSAVER_WILL_STOP -> evt = SystemActivityEvent(this, SystemActivityEvent.EVENT_SCREENSAVER_WILL_STOP)
            SystemActivityNotifications.NOTIFY_SCREENSAVER_STOP -> evt = SystemActivityEvent(this, SystemActivityEvent.EVENT_SCREENSAVER_STOP)
            SystemActivityNotifications.NOTIFY_SCREEN_LOCKED -> evt = SystemActivityEvent(this, SystemActivityEvent.EVENT_SCREEN_LOCKED)
            SystemActivityNotifications.NOTIFY_SCREEN_UNLOCKED -> evt = SystemActivityEvent(this, SystemActivityEvent.EVENT_SCREEN_UNLOCKED)
            SystemActivityNotifications.NOTIFY_NETWORK_CHANGE -> {
                evt = SystemActivityEvent(this, SystemActivityEvent.EVENT_NETWORK_CHANGE)
            }
            SystemActivityNotifications.NOTIFY_DNS_CHANGE -> {
                evt = SystemActivityEvent(this, SystemActivityEvent.EVENT_DNS_CHANGE)
            }
            SystemActivityNotifications.NOTIFY_QUERY_ENDSESSION -> {

                // both events QUERY_ENDSESSION and ENDSESSION depend on the result one after another
                // we don't put them in new thread in order to give control in the bundles using this events.
                evt = SystemActivityEvent(this, SystemActivityEvent.EVENT_QUERY_ENDSESSION)
                eventDispatcher.fireSystemActivityEventCurrentThread(evt)
                return
            }
            SystemActivityNotifications.NOTIFY_ENDSESSION -> {

                // both events QUERY_ENDSESSION and ENDSESSION depend on the result one after another
                // we don't put them in new thread in order to give control in the bundles using this events.
                evt = SystemActivityEvent(this, SystemActivityEvent.EVENT_ENDSESSION)
                eventDispatcher.fireSystemActivityEventCurrentThread(evt)
                return
            }
        }
        evt?.let { fireSystemActivityEvent(it) }
    }

    /**
     * Callback method when receiving special network notifications.
     *
     * @param family family of network change (ipv6, ipv4)
     * AF_UNSPEC = 0 (The address family is unspecified.)
     * AF_INET = 2 (The Internet Protocol version 4 (IPv4) address family)
     * AF_INET6 = 23 (The Internet Protocol version 6 (IPv6) address family)
     * @param luidIndex unique index of interface
     * @param name name of the interface
     * @param type of the interface
     * Possible values for the interface type are listed in the Ipifcons.h file.
     * common values:
     * IF_TYPE_OTHER = 1 (Some other type of network interface.)
     * IF_TYPE_ETHERNET_CSMACD = 6 (An Ethernet network interface.)
     * IF_TYPE_ISO88025_TOKENRING = 9 (A token ring network interface.)
     * IF_TYPE_PPP = 23 (A PPP network interface.)
     * IF_TYPE_SOFTWARE_LOOPBACK = 24 (A software loopback network interface.)
     * IF_TYPE_IEEE80211 = 71 (An IEEE 802.11 wireless network interface.)
     * IF_TYPE_TUNNEL = 131 (A tunnel type encapsulation network interface.)
     * IF_TYPE_IEEE1394 = 144 (An IEEE 1394 (Firewire) high performance
     * serial bus network interface.)
     * @param connected whether interface is connected or not.
     */
    override fun notifyNetworkChange(family: Int, luidIndex: Long, name: String?, type: Long, connected: Boolean) {
        val current = System.currentTimeMillis()
        if (current - lastNetworkChange <= NETWORK_EVENT_SILENT_TIME && networkIsConnected != null && networkIsConnected == connected) {
            networkIsConnected = connected
            return
        }
        lastNetworkChange = current
        networkIsConnected = connected
        val evt = SystemActivityEvent(this, SystemActivityEvent.EVENT_NETWORK_CHANGE)
        fireSystemActivityEvent(evt)
    }

    /**
     * The thread run method that handles idle notifies.
     *
     * @see Thread.run
     */
    override fun run() {
        while (running) {
            try {
                var idleTime = 0L
                if (idleChangeListeners.isNotEmpty()) {
                    // check
                    idleTime = SystemActivityNotifications.lastInput
                    if (idleTime < idleStateCheckDelay && listenersInIdleState.size > 0) {
                        for (l in listenersInIdleState) {
                            fireSystemIdleEndEvent(l)
                        }
                        listenersInIdleState.clear()
                    }
                    for ((listener, value) in idleChangeListeners) {
                        if (!listenersInIdleState.contains(listener) && value <= idleTime) {
                            fireSystemIdleEvent(listener)
                            listenersInIdleState.add(listener)
                        }
                    }
                }

                // if the minimum check for idle is X minutes we will wait before checking (X - Y + 1sec)
                // where Y is the last idle time returned by OS
                if (listenersInIdleState.size > 0) {
                    idleStateCheckDelay = CHECK_FOR_IDLE_WHEN_IDLE
                } else if (idleTime != 0L) {
                    var minIdleSetting = CHECK_FOR_IDLE_DEFAULT.toLong()
                    if (idleChangeListeners.isNotEmpty()) minIdleSetting = Collections.min(idleChangeListeners.values)
                    val newSetting = (minIdleSetting - idleTime).toInt() + 1000
                    if (newSetting > 0) idleStateCheckDelay = newSetting else idleStateCheckDelay = CHECK_FOR_IDLE_DEFAULT
                } else {
                    idleStateCheckDelay = CHECK_FOR_IDLE_DEFAULT
                }

                // wait for the specified time
                synchronized(this) { (this as Object).wait(idleStateCheckDelay.toLong()) }
            } catch (t: UnsatisfiedLinkError) {
                Timber.e(t, "Missing native impl")
                return
            } catch (t: Throwable) {
                Timber.e(t, "Error checking for idle")
            }
        }
    }

    /**
     * Delivers the specified event to all registered listeners.
     *
     * @param evt the `SystemActivityEvent` that we'd like delivered to all registered message listeners.
     */
    fun fireSystemActivityEvent(evt: SystemActivityEvent) {
        val eventID = evt.getEventID()

        // Add network activity info to track wake up problems.
        if (eventID == SystemActivityEvent.EVENT_NETWORK_CHANGE || eventID == SystemActivityEvent.EVENT_DNS_CHANGE) {
            Timber.d("Received system activity event: %s", evt)
        }
        if (eventID == SystemActivityEvent.EVENT_NETWORK_CHANGE) {
            // Give time to Java to dispatch same event and populate its network interfaces.
            eventDispatcher.fireSystemActivityEvent(evt, 500)
        } else eventDispatcher.fireSystemActivityEvent(evt)
    }

    /**
     * Delivers the specified event to all registered listeners.
     *
     * @param listener listener to inform
     */
    private fun fireSystemIdleEvent(listener: SystemActivityChangeListener?) {
        val evt = SystemActivityEvent(this, SystemActivityEvent.EVENT_SYSTEM_IDLE)
        Timber.d("Dispatching SystemActivityEvent evt=%s", evt)
        try {
            listener!!.activityChanged(evt)
        } catch (t: Throwable) {
            if (t is ThreadDeath) throw t else Timber.e(t, "Error delivering event")
        }
    }

    /**
     * Delivers the specified event to listener.
     *
     * @param listener listener to inform
     */
    private fun fireSystemIdleEndEvent(listener: SystemActivityChangeListener?) {
        val evt = SystemActivityEvent(this, SystemActivityEvent.EVENT_SYSTEM_IDLE_END)
        Timber.d("Dispatching SystemActivityEvent evt = %s", evt)
        try {
            listener!!.activityChanged(evt)
        } catch (t: Throwable) {
            if (t is ThreadDeath) throw t else Timber.e(t, "Error delivering event")
        }
    }

    /**
     * Can check whether an event id is supported on current operation system.
     * Simple return what is implemented in native, and checks
     * are made when possible, for example linux cannot connect to NM through dbus.
     *
     * @param eventID the event to check.
     * @return whether the supplied event id is supported.
     */
    override fun isSupported(eventID: Int): Boolean {
        return if (OSUtils.IS_WINDOWS) {
            when (eventID) {
                SystemActivityEvent.EVENT_SLEEP, SystemActivityEvent.EVENT_WAKE, SystemActivityEvent.EVENT_NETWORK_CHANGE, SystemActivityEvent.EVENT_SYSTEM_IDLE, SystemActivityEvent.EVENT_SYSTEM_IDLE_END -> SystemActivityNotifications.isLoaded
                else -> false
            }
        } else if (OSUtils.IS_MAC) {
            SystemActivityNotifications.isLoaded
        } else if (OSUtils.IS_LINUX) {
            when (eventID) {
                SystemActivityEvent.EVENT_SLEEP, SystemActivityEvent.EVENT_NETWORK_CHANGE -> {
                    val currentRunningManager = currentRunningManager
                    currentRunningManager != null && currentRunningManager.isConnected
                }
                SystemActivityEvent.EVENT_SYSTEM_IDLE, SystemActivityEvent.EVENT_SYSTEM_IDLE_END -> SystemActivityNotifications.isLoaded
                else -> false
            }
        } else OSUtils.IS_ANDROID && eventID == SystemActivityEvent.EVENT_NETWORK_CHANGE
    }

    companion object {
        /**
         * The interval between checks when not idle.
         */
        private const val CHECK_FOR_IDLE_DEFAULT = 30 * 1000

        /**
         * The interval between checks when idle. The interval is shorter
         * so we can react almost immediately when we are active again.
         */
        private const val CHECK_FOR_IDLE_WHEN_IDLE = 1000

        /**
         * The time in milliseconds between two checks for system idle.
         */
        private var idleStateCheckDelay = CHECK_FOR_IDLE_DEFAULT

        /**
         * Sometimes (on windows) we got several network change events
         * this is the time after which latest event we will skip next events.
         */
        private const val NETWORK_EVENT_SILENT_TIME = (10 * 1000).toLong()

        /**
         * The linux impl class name.
         */
        private const val SYSTEM_ACTIVITY_MANAGER_LINUX_CLASS = "net.java.sip.communicator.impl.sysactivity.NetworkManagerListenerImpl"

        /**
         * The android impl class name.
         */
        private const val SYSTEM_ACTIVITY_MANAGER_ANDROID_CLASS = "net.java.sip.communicator.impl.sysactivity.ConnectivityManagerListenerImpl"
    }
}