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

import net.java.sip.communicator.service.sysactivity.SystemActivityChangeListener
import net.java.sip.communicator.service.sysactivity.event.SystemActivityEvent
import okhttp3.internal.notifyAll
import okhttp3.internal.wait
import timber.log.Timber
import java.util.*

/**
 * The class implements a dispatch event thread. The thread will
 * fire event every time it is added through the `fireSystemActivityEvent()`
 * method and would then deliver it to a registered listener if any.
 * If the event has time set we used it as a delay before dispatching the event.
 *
 *
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class SystemActivityEventDispatcher : Runnable {
    /**
     * A list of listeners registered for system activity events.
     */
    private val listeners = LinkedList<SystemActivityChangeListener?>()

    /**
     * start/stop indicator.
     */
    private var stopped = true

    /**
     * The thread that runs this dispatcher.
     */
    private var dispatcherThread: Thread? = null

    /**
     * The events to dispatch.
     */
    private val eventsToDispatch = LinkedHashMap<SystemActivityEvent, Int>()

    /**
     * Registers a listener that would be notified of changes that have occurred
     * in the underlying system.
     *
     * @param listener the listener that we'd like to register for changes in
     * the underlying system.
     */
    fun addSystemActivityChangeListener(
            listener: SystemActivityChangeListener?) {
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
     * Remove the specified listener so that it won't receive further
     * notifications of changes that occur in the underlying system
     *
     * @param listener the listener to remove.
     */
    fun removeSystemActivityChangeListener(listener: SystemActivityChangeListener?) {
        synchronized(listeners) { listeners.remove(listener) }
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
     * Delivers the specified event to all registered listeners. Without
     * using the thread, but delivering them in the calling thread.
     *
     * @param evt the `SystemActivityEvent` that we'd like delivered to
     * all registered message listeners.
     */
    fun fireSystemActivityEventCurrentThread(evt: SystemActivityEvent) {
        val listenersCopy = ArrayList(listeners)
        for (i in listenersCopy.indices) {
            fireSystemActivityEvent(
                    evt,
                    listenersCopy[i])
        }
    }
    /**
     * Delivers the specified event to all registered listeners.
     *
     * @param evt the `SystemActivityEvent` that we'd like delivered to
     * all registered message listeners.
     * @param wait time in ms. to wait before firing the event.
     */
    /**
     * Delivers the specified event to all registered listeners.
     *
     * @param evt the `SystemActivityEvent` that we'd like delivered to all registered message listeners.
     */
    @JvmOverloads
    fun fireSystemActivityEvent(evt: SystemActivityEvent, wait: Int = 0) {
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
     * Delivers the specified event to the `listener`.
     *
     * @param evt the `SystemActivityEvent` that we'd like delivered to
     * the listener.
     * @param listener that will receive the event.
     */
    private fun fireSystemActivityEvent(evt: SystemActivityEvent, listener: SystemActivityChangeListener?) {
        Timber.d("Dispatching SystemActivityEvent Listeners=" + listeners.size + " evt=" + evt)
        if (evt.getEventID() == SystemActivityEvent.EVENT_NETWORK_CHANGE
                || evt.getEventID() == SystemActivityEvent.EVENT_DNS_CHANGE) {
            Timber.i("Dispatching SystemActivityEvent Listeners = %s evt = %s", listeners.size, evt)
        }
        try {
            listener!!.activityChanged(evt)
        } catch (e: Throwable) {
            Timber.e(e, "Error delivering event")
        }
    }

    /**
     * Runs the waiting thread.
     */
    override fun run() {
        try {
            stopped = false
            while (!stopped) {
                var eventToProcess: Map.Entry<SystemActivityEvent, Int>? = null
                var listenersCopy = emptyList<SystemActivityChangeListener?>()
                synchronized(eventsToDispatch) {
                    if (eventsToDispatch.isEmpty()) {
                        try {
                            eventsToDispatch.wait()
                        } catch (ignore: InterruptedException) {
                        }
                    }

                    //no point in dispatching if there's no one
                    //listening
                    if (listeners.size != 0) {
                        //store the ref of the listener in case someone resets
                        //it before we've had a chance to notify it.
                        listenersCopy = ArrayList(listeners)
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
                        fireSystemActivityEvent(eventToProcess!!.key, listenersCopy[i])
                    }
                }
            }
        } catch (t: Throwable) {
            Timber.e(t, "Error dispatching thread ended unexpectedly")
        }
    }
}