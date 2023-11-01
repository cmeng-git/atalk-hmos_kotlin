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
import okhttp3.internal.notifyAll
import okhttp3.internal.wait
import org.atalk.hmos.plugin.timberlog.TimberLog
import timber.log.Timber

/**
 * The class implements a dispatch event thread. The thread will
 * fire event every time it is added through the `fireChangeEvent()`
 * method and would then deliver it to a registered listener if any.
 * (No fire would be performed until we have a
 * `NetworkConfigurationChangeListener`). If the event has time set
 * we used it as a delay before dispatching the event.
 *
 *
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class NetworkEventDispatcher : Runnable {
    /**
     * Listeners for network configuration changes.
     */
    private val listeners = ArrayList<NetworkConfigurationChangeListener?>()

    /**
     * The events to dispatch.
     */
    private val eventsToDispatch = LinkedHashMap<ChangeEvent, Int>()

    /**
     * start/stop indicator.
     */
    private var stopped = true

    /**
     * The thread that runs this dispatcher.
     */
    private var dispatcherThread: Thread? = null

    /**
     * Adds new `NetworkConfigurationChangeListener` which will be informed for network configuration changes.
     *
     * @param listener the listener.
     */
    fun addNetworkConfigurationChangeListener(listener: NetworkConfigurationChangeListener?) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
                if (dispatcherThread == null) {
                    dispatcherThread = Thread(this)
                    dispatcherThread!!.start()
                }
            }
        }
    }

    /**
     * Remove `NetworkConfigurationChangeListener`.
     *
     * @param listener the listener.
     */
    fun removeNetworkConfigurationChangeListener(
            listener: NetworkConfigurationChangeListener?) {
        synchronized(listeners) { listeners.remove(listener) }
    }
    /**
     * Fire ChangeEvent.
     *
     * @param evt the event to fire.
     */
    /**
     * Fire ChangeEvent.
     *
     * @param evt the event to fire.
     */
    @JvmOverloads
    fun fireChangeEvent(evt: ChangeEvent, wait: Int = 0) {
        synchronized(eventsToDispatch) {
            eventsToDispatch[evt] = wait
            eventsToDispatch.notifyAll()
            if (dispatcherThread == null && listeners.size > 0) {
                dispatcherThread = Thread(this)
                dispatcherThread!!.start()
            }
        }
    }

    /**
     * Runs the waiting thread.
     */
    override fun run() {
        try {
            stopped = false
            loop@ while (!stopped) {
                var eventToProcess: Map.Entry<ChangeEvent, Int>? = null
                var listenersCopy = emptyList<NetworkConfigurationChangeListener?>()

                synchronized(eventsToDispatch) {
                    if (eventsToDispatch.isEmpty()) {
                        try {
                            eventsToDispatch.wait()
                        } catch (ignore: InterruptedException) {
                        }
                    }

                    // no point in dispatching if there's no one listening
                    if (listeners.isNotEmpty()) {
                        // store the ref of the listener in case someone resets it before we've had a chance to notify it.
                        listenersCopy = java.util.ArrayList(listeners)


                        // store the ref of the listener in case someone resets
                        // it before we've had a chance to notify it.
                        val iter = eventsToDispatch.entries.iterator()
                        if (iter.hasNext()) {
                            eventToProcess = iter.next()
                            iter.remove()
                        }
                    }
                }

                if (eventToProcess != null) {
                    if (eventToProcess!!.value > 0) synchronized(this) {
                        try {
                             (this as Object).wait(eventToProcess!!.value.toLong())
                        } catch (ignore: Throwable) {
                        }
                    }

                    for (i in listenersCopy.indices) {
                        Companion.fireChangeEvent(eventToProcess!!.key, listenersCopy[i]!!)
                    }
                }
            }
        } catch (t: Throwable) {
            Timber.e(t, "Error dispatching thread ended unexpectedly")
        }
    }

    /**
     * Interrupts this dispatcher so that it would no longer disptach events.
     */
    fun stop() {
        synchronized(eventsToDispatch) {
            stopped = true
            eventsToDispatch.notifyAll()
            dispatcherThread = null
        }
    }

    /**
     * Returns `true` if this dispatcher is currently running and
     * delivering events when available and `false`
     * otherwise.
     *
     * @return `true` if this dispatcher is currently running and
     * delivering events when available and `false` otherwise.
     */
    val isRunning: Boolean
        get() = !stopped

    companion object {
        /**
         * Fire ChangeEvent.
         *
         * @param evt the event to fire.
         */
        fun fireChangeEvent(evt: ChangeEvent?, listener: NetworkConfigurationChangeListener) {
            try {
                Timber.log(TimberLog.FINER, "firing event to %s evt = %s", listener, evt)
                listener.configurationChanged(evt)
            } catch (e: Throwable) {
                Timber.w(e, "Error delivering event: %s, to: %s", evt, listener)
            }
        }
    }
}