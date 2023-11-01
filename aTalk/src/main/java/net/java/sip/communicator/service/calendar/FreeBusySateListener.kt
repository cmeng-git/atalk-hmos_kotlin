/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.calendar

import net.java.sip.communicator.service.calendar.CalendarService.BusyStatusEnum

/**
 * A interface for listener that listens for calendar free busy status changes.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
interface FreeBusySateListener {
    /**
     * A method that is called when the free busy status is changed.
     *
     * @param oldStatus
     * the old value of the status.
     * @param newStatus
     * the new value of the status.
     */
    fun onStatusChanged(oldStatus: BusyStatusEnum?, newStatus: BusyStatusEnum?)
}