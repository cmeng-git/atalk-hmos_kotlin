/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.msghistory.event

import net.java.sip.communicator.service.history.event.HistorySearchProgressListener

/**
 * When searching into the message history a ProgressEvent is fired whenever the progress is changed. Its fired through
 * the search process informing us about the current progress.
 *
 * @author Damian Minkov
 */
interface MessageHistorySearchProgressListener {
    /**
     * This method gets called when progress changes through the search process
     *
     * @param evt
     * ProgressEvent the event holding the search condition and the current progress value.
     */
    fun progressChanged(evt: ProgressEvent)

    companion object {
        /**
         * The minimum value for the progress change. This is value indicates that the process has started.
         */
        val PROGRESS_MINIMUM_VALUE = HistorySearchProgressListener.PROGRESS_MINIMUM_VALUE

        /**
         * The maximum value for the progress change. This is value indicates that the process is finished.
         */
        val PROGRESS_MAXIMUM_VALUE = HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE
    }
}