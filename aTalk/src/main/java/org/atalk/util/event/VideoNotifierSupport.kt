/*
 * Copyright @ 2015 Atlassian Pty Ltd
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
package org.atalk.util.event

import java.awt.Component
import java.util.*

/**
 * Represents a mechanism to easily add to a specific `Object` by means
 * of composition support for firing `VideoEvent`s to `VideoListener`s.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class VideoNotifierSupport @JvmOverloads constructor(
        /**
         * The `Object` which is to be reported as the source of the `VideoEvent`s fired by this instance.
         */
        private val source: Any,
        /**
         * The indicator which determines whether this instance delivers the
         * `VideoEvent`s to the [.listeners] synchronously.
         */
        private val synchronous: Boolean = true) {
    /**
     * The list of `VideoEvent`s which are to be delivered to the
     * [.listeners] registered with this instance when [.synchronous] is equal to `false`.
     */
    private val events: MutableList<VideoEvent>? = if (synchronous) null else LinkedList()

    /**
     * The list of `VideoListener`s interested in changes in the
     * availability of visual `Component`s depicting video.
     */
    private val listeners = ArrayList<VideoListener>()

    /**
     * The `Thread` in which [.events] are delivered to the
     * [.listeners] when [.synchronous] is equal to `false`.
     */
    private var thread: Thread? = null
    /**
     * Initializes a new `VideoNotifierSupport` instance which is to facilitate the management of
     * `VideoListener`s and firing `VideoEvent`s to them for a specific `Object`.
     *
     * @param source the `Object` which is to be reported as the source
     * of the `VideoEvent`s fired by the new instance
     * @param synchronous `true` if the new instance is to deliver the
     * `VideoEvent`s synchronously; otherwise, `false`
     */

    /**
     * Adds a specific `VideoListener` to this `VideoNotifierSupport` in order to receive
     * notifications when visual/video `Component`s are being added and removed.
     *
     * Adding a listener which has already been added does nothing i.e. it is not added more than once
     * and thus does not receive one and the same `VideoEvent` multiple times.
     *
     * @param listener the `VideoListener` to be notified when visual/video `Component`s
     * are being added or removed in this `VideoNotifierSupport`
     */
    fun addVideoListener(listener: VideoListener) {
        // if (listener == null) throw NullPointerException("listener")
        synchronized(listeners) { if (!listeners.contains(listener)) listeners.add(listener) }
    }

    private fun doFireVideoEvent(event: VideoEvent) {
        var listeners: Array<VideoListener>
        synchronized(this.listeners) { listeners = this.listeners.toTypedArray() }
        for (listener in listeners) when (event.type) {
            VideoEvent.VIDEO_ADDED -> listener.videoAdded(event)
            VideoEvent.VIDEO_REMOVED -> listener.videoRemoved(event)
            else -> listener.videoUpdate(event)
        }
    }

    /**
     * Notifies the `VideoListener`s registered with this `VideoMediaStream` about a specific
     * type of change in the availability of a specific visual `Component` depicting video.
     *
     * @param type the type of change as defined by `VideoEvent` in the
     * availability of the specified visual `Component` depicting video
     * @param visualComponent the visual `Component` depicting video which has been added or removed
     * @param origin [VideoEvent.LOCAL] if the origin of the video is local (e.g. it is being locally captured);
     * [VideoEvent.REMOTE] if the origin of the video is remote (e.g. a remote peer is streaming it)
     * @param wait `true` if the call is to wait till the specified
     * `VideoEvent` has been delivered to the `VideoListener`s; otherwise, `false`
     * @return `true` if this event and, more specifically, the visual `Component` it describes
     * have been consumed and should be considered owned, referenced (which is important because
     * `Component`s belong to a single `Container` at a time); otherwise, `false`
     */
    fun fireVideoEvent(type: Int, visualComponent: Component?, origin: Int, wait: Boolean): Boolean {
        val event = VideoEvent(source, type, visualComponent, origin)
        fireVideoEvent(event, wait)
        return event.isConsumed
    }

    /**
     * Notifies the `VideoListener`s registered with this instance about a specific `VideoEvent`.
     *
     * @param event the `VideoEvent` to be fired to the `VideoListener`s registered with this instance
     * @param wait `true` if the call is to wait till the specified
     * `VideoEvent` has been delivered to the `VideoListener`s; otherwise, `false`
     */
    fun fireVideoEvent(event: VideoEvent, wait: Boolean) {
        if (synchronous) doFireVideoEvent(event) else {
            synchronized(events!!) {
                events.add(event)
                // if (VideoEvent.VIDEO_REMOVED == event.getType()) {
                //     Timber.e(new Exception("Event VIDEO_REMOVED added (for testing only)?"));
                // }
                if (thread == null) startThread() else (events as Object).notify()
                if (wait) {
                    var interrupted = false
                    while (events.contains(event) && thread != null) {
                        try {
                            (events as Object).wait()
                        } catch (ie: InterruptedException) {
                            interrupted = true
                        }
                    }
                    if (interrupted) Thread.currentThread().interrupt()
                }
            }
        }
    }

    /**
     * Removes a specific `VideoListener` from this `VideoNotifierSupport` to stop
     * receiving notifications when visual/video `Component`s are being added and removed.
     *
     * @param listener the `VideoListener` to be removed that no longer be notified when
     * visual/video `Component`s are being added or removed
     */
    fun removeVideoListener(listener: VideoListener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    private fun runInThread() {
        while (true) {
            var event: VideoEvent
            synchronized(events!!) {
                var emptyTime = -1L
                var interrupted = false
                while (events.isEmpty()) {
                    if (emptyTime == -1L) emptyTime = System.currentTimeMillis() else {
                        val newEmptyTime = System.currentTimeMillis()
                        if (newEmptyTime - emptyTime >= THREAD_TIMEOUT) {
                            (events as Object).notify()
                            return
                        }
                    }
                    try {
                        (events as Object).wait(THREAD_TIMEOUT)
                    } catch (ie: InterruptedException) {
                        interrupted = true
                    }
                }
                if (interrupted) Thread.currentThread().interrupt()
                event = events.removeAt(0)
            }

            if (event != null) {
                try {
                    doFireVideoEvent(event)
                } catch (t: Throwable) {
                    if (t is ThreadDeath) throw t
                }
                synchronized(events) { (events as Object).notify() }
            }
        }
    }

    private fun startThread() {
        thread = object : Thread("VideoNotifierSupportThread") {
            override fun run() {
                try {
                    runInThread()
                } finally {
                    synchronized(events!!) {
                        if (currentThread() == thread) {
                            thread = null
                            if (events.isEmpty()) (events as Object).notify() else startThread()
                        }
                    }
                }
            }
        }
        thread!!.setDaemon(true)
        thread!!.start()
    }

    companion object {
        private const val THREAD_TIMEOUT = 5000L
    }
}