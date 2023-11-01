/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history.event

import java.util.*

/**
 * A "ProgressEvent" event gets delivered through the search process of HistoryReader Service. The event is created with
 * arguments - the search conditions (if they do not exist null is passed).
 *
 * We must know the search conditions due the fact that we must differ searches if more than one exist. The real
 * information is the progress of the current search.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class ProgressEvent @JvmOverloads constructor(source: Any, startDate: Date?, endDate: Date?, keywords: Array<String>?, progress: Int = 0) : EventObject(source) {
    /**
     * The start date in the search condition.
     *
     * @return Date start date value
     */
    /**
     * The start date in the search condition.
     */
    var startDate: Date? = null
    /**
     * The end date in the search condition.
     *
     * @return Date end date value
     */
    /**
     * The end date in the search condition.
     */
    var endDate: Date? = null
    /**
     * The keywords in the search condition.
     *
     * @return String[] array of keywords fo searching
     */
    /**
     * The keywords in the search condition.
     */
    var keywords: Array<String>? = null
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
     * Constructs a new `ProgressEvent`.
     *
     * @param source
     * Object The source firing this event
     * @param startDate
     * Date The start date in the search condition.
     * @param endDate
     * Date The end date in the search condition.
     * @param keywords
     * String[] The keywords in the search condition.
     * @param progress
     * int The current progress that we will pass.
     */
    /**
     * Constructs a new `ProgressEvent`.
     *
     * @param source
     * Object The source firing this event
     * @param startDate
     * Date The start date in the search condition.
     * @param endDate
     * Date The end date in the search condition.
     */
    /**
     * Constructs a new `ProgressEvent`.
     *
     * @param source
     * Object The source firing this event
     * @param startDate
     * Date The start date in the search condition.
     * @param endDate
     * Date The end date in the search condition.
     * @param keywords
     * String[] The keywords in the search condition.
     */
    init {
        this.startDate = startDate
        this.endDate = endDate
        this.keywords = keywords
        this.progress = progress
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}