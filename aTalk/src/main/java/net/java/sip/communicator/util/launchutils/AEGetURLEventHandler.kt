/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.launchutils

/**
 * Registers as listener for kAEGetURL AppleScript events.
 * And will handle any url coming from the OS by passing it to LaunchArgHandler.
 *
 * @author Lubomir Marinov
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class AEGetURLEventHandler internal constructor(private val launchArgHandler: LaunchArgHandler) {
    /**
     * The interface for the used callback.
     */
    interface IAEGetURLListener {
        /**
         * Handle the URL event.
         *
         * @param url the URL
         */
        fun handleAEGetURLEvent(url: String)
    }

    init {
        try {
            setAEGetURLListener(object : IAEGetURLListener {
                override fun handleAEGetURLEvent(url: String) {
                    object : Thread() {
                        override fun run() {
                            launchArgHandler.handleArgs(arrayOf(url))
                        }
                    }.start()
                }
            })
        } catch (err: Throwable) {
            //we don't have logging here so dump to stderr
            System.err.println("Warning: Failed to register our command line argument"
                    + " handler. We won't be able to handle command line arguments.")
            err.printStackTrace()
        }
    }

    /**
     * Sets the (global) listener for kAEGetURL AppleScript events.
     *
     *
     * The listener should be prepared to handle any pending events before this
     * method returns because such events may have already been sent by the
     * operating system (e.g. when the application wasn't running and was
     * started in order to handle such an event).
     *
     *
     * @param listener the [IAEGetURLListener] to be set as the (global)
     * listener for kAEGetURL AppleScript events
     */
    private external fun setAEGetURLListener(listener: IAEGetURLListener)

    companion object {
    }
}