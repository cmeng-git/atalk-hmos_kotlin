/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.msghistory.event

import net.java.sip.communicator.service.history.event.ProgressEvent
import java.util.*

/**
 * A "ProgressEvent" event gets delivered through the search process of MessageHistoryService Service. The event is
 * wrapper around the generated event from the History Service
 *
 * @author Damian Minkov
 */
class ProgressEvent(source: Any, private val evt: ProgressEvent, progress: Int) : EventObject(source) {
    /**
     * Gets the current progress that will be fired.
     *
     * @return int the progress value
     */
    /**
     * Sets the progress that will be fired
     *
     * @param progress
     * int progress value
     */
    /**
     * The current progress that we will pass.
     */
    var progress = 0

    /**
     * Constructor.
     *
     * @param source
     * source `Object`
     * @param evt
     * the event
     * @param progress
     * initial progress
     */
    init {
        this.progress = progress
    }

    /**
     * The end date in the search condition.
     *
     * @return Date end date value
     */
    val endDate: Date?
        get() = evt.endDate

    /**
     * The keywords in the search condition.
     *
     * @return String[] array of keywords fo searching
     */
    val keywords: Array<String>?
        get() = evt.keywords

    /**
     * The start date in the search condition.
     *
     * @return Date start date value
     */
    val startDate: Date?
        get() = evt.startDate

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}