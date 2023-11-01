/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.calendar

import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService

/**
 * A service for calendar. It defines for accessing the current free busy status and add / remove listeners for the free
 * busy status.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
interface CalendarService {
    /**
     * Defines the possible free busy statuses.
     *
     * @author Hristo Terezov
     */
    enum class BusyStatusEnum
    /**
     * Constructs new status.
     *
     * @param value
     * the value of the status
     */
    (
            /**
             * The value of the status.
             */
            val value: Long) {
        /**
         * The Free status.
         */
        FREE(0x00000000L),

        /**
         * The In meeting status.
         */
        IN_MEETING(0x00000001L),

        /**
         * The busy status.
         */
        BUSY(0x00000002L),

        /**
         * The out of office status.
         */
        OUT_OF_OFFICE(0x00000003L);
        /**
         * Returns the value of the status.
         *
         * @return the value of the status.
         */

        /**
         * The priority of the status
         */
        private val priority: Int? = null

        /**
         * Returns the priority of the status
         *
         * @return the priority of the status
         */
        fun getPriority(): Int {
            return priority ?: ordinal
        }

        companion object {
            /**
             * Finds `BusyStatusEnum` instance by given value of the status.
             *
             * @param value
             * the value of the status we are searching for.
             * @return the status or `FREE` if no status is found.
             */
            fun getFromLong(value: Long): BusyStatusEnum {
                for (state in values()) {
                    if (state.value == value) {
                        return state
                    }
                }
                return FREE
            }
        }
    }

    /**
     * Returns the current value of the free busy status.
     *
     * @return the current value of the free busy status.
     */
    val status: BusyStatusEnum?

    /**
     * Adds free busy listener.
     *
     * @param listener
     * the listener to be added.
     */
    fun addFreeBusySateListener(listener: FreeBusySateListener?)

    /**
     * Removes free busy listener.
     *
     * @param listener
     * the listener to be removed.
     */
    fun removeFreeBusySateListener(listener: FreeBusySateListener?)

    /**
     * Handles presence status changed from "On the Phone"
     *
     * @param presenceStatuses
     * the remembered presence statuses
     * @return `true` if the status is changed.
     */
    fun onThePhoneStatusChanged(presenceStatuses: Map<ProtocolProviderService?, PresenceStatus?>?): Boolean

    /**
     * Returns the remembered presence statuses
     *
     * @return the remembered presence statuses
     */
    val rememberedStatuses: Map<ProtocolProviderService?, PresenceStatus?>?

    companion object {
        /**
         * The name of the configuration property which specifies whether free busy status is disabled i.e. whether it
         * should set the presence statuses of online accounts to &quot;In Meeting&quot;.
         */
        const val PNAME_FREE_BUSY_STATUS_DISABLED = "calendar.FreeBusyStatus.disabled"
    }
}