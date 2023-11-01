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
package net.java.sip.communicator.service.sysactivity.event

import java.util.*

/**
 * An event class representing system activity that has occurred.
 * The event id indicates the exact reason for this event.
 * @author Damian Minkov
 */
class SystemActivityEvent
/**
 * Constructs a prototypical Event.
 *
 * @param source The object on which the Event initially occurred.
 * @param eventID the type of the event.
 * @throws IllegalArgumentException if source is null.
 */
(source: Any?,
    /**
     * The type of the event.
     */
    private val eventID: Int) : EventObject(source) {
    /**
     * Returns the type of the event.
     * @return the event ID
     */
    fun getEventID(): Int {
        return eventID
    }

    /**
     * Returns a String representation of this SystemActivityEvent object.
     *
     * @return  A a String representation of this SystemActivityEvent object.
     */
    override fun toString(): String {
        return javaClass.name + "[eventID=" + eventID + "]"
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Notify that computers is going to sleep.
         */
        const val EVENT_SLEEP = 0

        /**
         * Notify that computer is wakeing up after stand by.
         */
        const val EVENT_WAKE = 1

        /**
         * Computer display has stand by.
         */
        const val EVENT_DISPLAY_SLEEP = 2

        /**
         * Computer display wakes up after stand by.
         */
        const val EVENT_DISPLAY_WAKE = 3

        /**
         * Screensaver has been started.
         */
        const val EVENT_SCREENSAVER_START = 4

        /**
         * Screensaver will stop.
         */
        const val EVENT_SCREENSAVER_WILL_STOP = 5

        /**
         * Screensaver has been stopped.
         */
        const val EVENT_SCREENSAVER_STOP = 6

        /**
         * Screen has been locked.
         */
        const val EVENT_SCREEN_LOCKED = 7

        /**
         * Screen has been unlocked.
         */
        const val EVENT_SCREEN_UNLOCKED = 8

        /**
         * A change in network configuration has occurred.
         */
        const val EVENT_NETWORK_CHANGE = 9

        /**
         * A system idle event has occurred.
         */
        const val EVENT_SYSTEM_IDLE = 10

        /**
         * A system was in idle state and now exits.
         */
        const val EVENT_SYSTEM_IDLE_END = 11

        /**
         * A change in dns configuration has occurred.
         */
        const val EVENT_DNS_CHANGE = 12

        /**
         * Informing that the machine is logging of or shutting down.
         */
        const val EVENT_QUERY_ENDSESSION = 13

        /**
         * The log off or shutdown is in process for us, no matter
         * what other process has replied, whether one of them has canceled
         * or not the current end of session. It's like that cause we have answered
         * that we will shutdown.
         */
        const val EVENT_ENDSESSION = 14
    }
}