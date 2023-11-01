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

import org.atalk.util.OSUtils
import timber.log.Timber

/**
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
object SystemActivityNotifications {
    /**
     * Computer display has stand by.
     */
    const val NOTIFY_DISPLAY_SLEEP = 2

    /**
     * Computer display wakes up after stand by.
     */
    const val NOTIFY_DISPLAY_WAKE = 3

    /**
     * A change in dns configuration has occurred.
     */
    const val NOTIFY_DNS_CHANGE = 10

    /**
     * All processes have been informed about ending session, now notify for the actual end session.
     */
    const val NOTIFY_ENDSESSION = 12

    /**
     * A change in network configuration has occurred.
     */
    const val NOTIFY_NETWORK_CHANGE = 9

    /**
     * Notifies for start of process of ending desktop session, logoff or shutdown.
     */
    const val NOTIFY_QUERY_ENDSESSION = 11

    /**
     * Screen has been locked.
     */
    const val NOTIFY_SCREEN_LOCKED = 7

    /**
     * Screen has been unlocked.
     */
    const val NOTIFY_SCREEN_UNLOCKED = 8

    /**
     * Screensaver has been started.
     */
    const val NOTIFY_SCREENSAVER_START = 4

    /**
     * Screensaver has been stopped.
     */
    const val NOTIFY_SCREENSAVER_STOP = 6

    /**
     * Screensaver will stop.
     */
    const val NOTIFY_SCREENSAVER_WILL_STOP = 5

    /**
     * Notify that computers is going to sleep.
     */
    const val NOTIFY_SLEEP = 0

    /**
     * Notify that computer is wakeing up after stand by.
     */
    const val NOTIFY_WAKE = 1

    /**
     * The native instance.
     */
    private var ptr = 0L

    /**
     * Init native library.
     */
    init {
        try {
            // Don't load native library on Android to prevent the exception
            if (!OSUtils.IS_ANDROID) {
//                System.loadLibrary("sysactivitynotifications", SystemActivityNotifications.class.getClassLoader());
                ptr = allocAndInit()
                if (ptr == -1L) ptr = 0
            }
        } catch (t: Throwable) {
            if (t is ThreadDeath) throw t else Timber.w(t, "Failed to initialize native counterpart")
        }
    }

    /**
     * Allocate native resources and gets a pointer.
     *
     * @return
     */
    private fun allocAndInit(): Long {
        return 0
    }

    /**
     * Returns the when was last input in milliseconds. The time when there was any activity on the computer.
     *
     * @return the last input in milliseconds
     */
    val lastInput: Long
        get() = 0

    /**
     * Whether native library is loaded.
     *
     * @return whether native library is loaded.
     */
    val isLoaded: Boolean
        get() = ptr != 0L

    /**
     * Release native resources.
     *
     * @param ptr
     */
    private fun release(ptr: Long) {}

    /**
     * Sets notifier delegate.
     *
     * @param ptr
     * @param delegate
     */
    fun setDelegate(ptr: Long, delegate: NotificationsDelegate?) {}

    /**
     * Sets delegate.
     *
     * @param delegate
     */
    fun setDelegate(delegate: NotificationsDelegate?) {
        if (ptr != 0L) setDelegate(ptr, delegate)
    }

    /**
     * Start.
     */
    fun start() {
        if (ptr != 0L) start(ptr)
    }

    /**
     * Start processing.
     *
     * @param ptr
     */
    private fun start(ptr: Long) {}

    /**
     * Stop.
     */
    fun stop() {
        if (ptr != 0L) {
            stop(ptr)
            release(ptr)
            ptr = 0
        }
    }

    /**
     * Stop processing.
     *
     * @param ptr
     */
    private fun stop(ptr: Long) {}

    /**
     * Delegate class to be notified about changes.
     */
    interface NotificationsDelegate {
        /**
         * Callback method when receiving notifications.
         *
         * @param type
         */
        fun notify(type: Int)

        /**
         * Callback method when receiving special network notifications.
         *
         * @param family family of network change (ipv6, ipv4)
         * @param luidIndex unique index of interface
         * @param name name of the interface
         * @param type of the interface
         * @param connected whether interface is connected or not.
         */
        fun notifyNetworkChange(
                family: Int,
                luidIndex: Long,
                name: String?,
                type: Long,
                connected: Boolean)
    }
}