/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.notification

import net.java.sip.communicator.service.notification.LogMessageNotificationAction
import net.java.sip.communicator.service.notification.LogMessageNotificationHandler
import net.java.sip.communicator.service.notification.NotificationAction
import org.atalk.hmos.plugin.timberlog.TimberLog
import timber.log.Timber

/**
 * An implementation of the `LogMessageNotificationHandler` interface.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class LogMessageNotificationHandlerImpl : LogMessageNotificationHandler {
    /**
     * Logs a message through the sip communicator Logger.
     *
     * @param action the action to act upon
     * @param message the message coming from the event
     */
    override fun logMessage(action: LogMessageNotificationAction?, message: String?) {
        if (action!!.logType == LogMessageNotificationAction.ERROR_LOG_TYPE) Timber.e("%s", message) else if (action.logType == LogMessageNotificationAction.INFO_LOG_TYPE) Timber.i("%s", message) else if (action.logType == LogMessageNotificationAction.TRACE_LOG_TYPE) Timber.log(TimberLog.FINER, "%s", message)
    }

    override val actionType: String
        get() = NotificationAction.ACTION_LOG_MESSAGE
}