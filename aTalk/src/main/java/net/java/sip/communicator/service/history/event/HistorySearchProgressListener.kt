/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history.event

/**
 * When searching into the history a ProgressEvent is fired whenever the progress is changed. Its fired throug the
 * search process informing us about the current progress.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
interface HistorySearchProgressListener {
    /**
     * This method gets called when progress changes through the search process
     *
     * @param evt
     * ProgressEvent the event holding the search condition and the current progress value.
     */
    fun progressChanged(evt: ProgressEvent?)

    companion object {
        /**
         * The minimum value for the progress change. This is value indicates that the process has started.
         */
        const val PROGRESS_MINIMUM_VALUE = 0

        /**
         * The maximum value for the progress change. This is value indicates that the process is finished.
         */
        const val PROGRESS_MAXIMUM_VALUE = 1000
    }
}