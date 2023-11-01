/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.osgi.framework.launch

import org.atalk.impl.osgi.framework.AsyncExecutor
import org.osgi.framework.Bundle
import org.osgi.framework.BundleEvent
import org.osgi.framework.BundleListener
import org.osgi.framework.FrameworkEvent
import org.osgi.framework.FrameworkListener
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import timber.log.Timber
import java.util.*
import java.util.concurrent.RejectedExecutionException

/**
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class EventDispatcher {
    private val executor = AsyncExecutor<Command<*>>()
    private val listeners = EventListenerList()

    fun <T : EventListener> addListener(bundle: Bundle?, clazz: Class<T>, listener: T): Boolean {
        return listeners.add(bundle, clazz, listener)
    }

    fun fireBundleEvent(event: BundleEvent) {
        fireEvent(BundleListener::class.java, event)
    }

    private fun <T : EventListener> fireEvent(clazz: Class<T>, event: EventObject) {
        val listeners = listeners.getListeners(clazz)
        if (listeners.isNotEmpty()) try {
            executor.execute(Command(clazz, event))
        } catch (ree: RejectedExecutionException) {
            Timber.e(ree, "Error firing event")
        }
    }

    fun fireServiceEvent(event: ServiceEvent) {
        fireEvent(ServiceListener::class.java, event)
    }

    fun <T : EventListener?> removeListener(bundle: Bundle, clazz: Class<T>, listener: T): Boolean {
        return listeners.remove(bundle, clazz, listener)
    }

    fun removeListeners(bundle: Bundle): Boolean {
        return listeners.removeAll(bundle)
    }

    fun stop() {
        executor.shutdownNow()
    }

//        private inner class Command<T : EventListener>(clazz: Class<T>, event: EventObject) : Runnable {
//            private val clazz: Class<out EventListener>
//            private val event: EventObject
//
//            init {
//                this.clazz = clazz
//                this.event = event
//            }
    private inner class Command<T : EventListener>(
            private val clazz: Class<T>,
            private val event: EventObject
    ) : Runnable {

        override fun run() {
            // Fetches listeners before command is started to get latest version of the list
            val listeners = listeners.getListeners(clazz)
            for (listener in listeners) {
                try {
                    if (BundleListener::class.java == clazz) {
                        (listener as BundleListener?)!!.bundleChanged(event as BundleEvent)
                    } else if (ServiceListener::class.java == clazz) {
                        (listener as ServiceListener?)!!.serviceChanged(event as ServiceEvent)
                    }
                } catch (t: Throwable) {
                    Timber.e(t, "Error dispatching event")
                    if (FrameworkListener::class.java == clazz && (event as FrameworkEvent).type != FrameworkEvent.ERROR) {
                        // TODO Auto-generated method stub
                    }
                }
            }
        }
    }
}