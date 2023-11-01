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
package net.java.sip.communicator.service.callhistory.event

import net.java.sip.communicator.service.history.event.ProgressEvent
import java.util.*

/**
 * A "ProgressEvent" event gets delivered through the search process
 * of CallHistoryService Service.
 * The event is wrapper around the generated event from the History Service
 *
 * @author Damian Minkov
 */
class ProgressEvent(source: Any?,
        /**
         * The `ProgressEvent`.
         */
        private val evt: ProgressEvent, progress: Int) : EventObject(source) {
    /**
     * Gets the current progress that will be fired.
     *
     * @return int the progress value
     */
    /**
     * Sets the progress that will be fired
     *
     * @param progress int progress value
     */
    /**
     * The current progress that we will pass.
     */
    var progress = 0

    /**
     * Constructor.
     *
     * @param source source object
     * @param evt the `ProgressEvent`
     * @param progress initial progress value
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