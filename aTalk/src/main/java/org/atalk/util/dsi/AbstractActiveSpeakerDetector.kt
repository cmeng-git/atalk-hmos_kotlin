/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.util.dsi

import java.util.*

/**
 * Provides a base [ActiveSpeakerDetector] which aids the implementations of actual algorithms
 * for the detection/identification of the active/dominant speaker in a multipoint conference.
 *
 * @author Boris Grozev
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class AbstractActiveSpeakerDetector : ActiveSpeakerDetector {
    /**
     * The list of listeners to be notified by this detector when the active speaker changes.
     */
    private val listeners: MutableList<ActiveSpeakerChangedListener> = LinkedList()

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if the specified `listener` is `null`
     */
    override fun addActiveSpeakerChangedListener(listener: ActiveSpeakerChangedListener?) {
        if (listener == null) throw NullPointerException("listener")
        synchronized(listeners) { if (!listeners.contains(listener)) listeners.add(listener) }
    }

    /**
     * Notifies the `ActiveSpeakerChangedListener`s registered with this instance that the
     * active speaker in multipoint conference associated with this instance has changed and is
     * identified by a specific synchronization source identifier/SSRC.
     *
     * @param ssrc the synchronization source identifier/SSRC of the active speaker in the multipoint conference.
     */
    protected fun fireActiveSpeakerChanged(ssrc: Long) {
        val listeners = activeSpeakerChangedListeners
        for (listener in listeners) listener!!.activeSpeakerChanged(ssrc)
    }

    /**
     * Gets the list of listeners to be notified by this detector when the active speaker changes.
     *
     * @return an array of the listeners to be notified by this detector when the active speaker
     * changes. If no such listeners are registered with this instance, an empty array is returned.
     */
    protected val activeSpeakerChangedListeners: Array<ActiveSpeakerChangedListener?>
        protected get() {
            synchronized(listeners) { return if (listeners.size == 0) NO_LISTENERS else (listeners as ArrayList).toArray(NO_LISTENERS) }
        }

    /**
     * {@inheritDoc}
     */
    override fun removeActiveSpeakerChangedListener(listener: ActiveSpeakerChangedListener?) {
        if (listener != null) {
            synchronized(listeners) { listeners.remove(listener) }
        }
    }

    companion object {
        /**
         * An empty array with element type `ActiveSpeakerChangedListener`. Explicitly defined
         * for the purposes of reducing the total number of unnecessary allocations and the undesired
         * effects of the garbage collector.
         */
        private val NO_LISTENERS = arrayOfNulls<ActiveSpeakerChangedListener>(0)
    }
}