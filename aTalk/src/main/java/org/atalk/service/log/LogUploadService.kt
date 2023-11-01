/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.log

/**
 * Send/upload logs, to specified destination.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
interface LogUploadService {
    /**
     * Send the log files.
     *
     * @param destinations array of destination addresses
     * @param subject the subject if available
     * @param title the title for the action, used any intermediate dialogs that need to be shown, like "Choose action:".
     */
    fun sendLogs(destinations: Array<String?>?, subject: String?, title: String?)
}