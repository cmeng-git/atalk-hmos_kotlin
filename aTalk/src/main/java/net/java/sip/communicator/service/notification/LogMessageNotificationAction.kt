/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification

/**
 * An implementation of the `LogMessageNotificationHandler` interface.
 *
 * @author Yana Stamcheva
 */
class LogMessageNotificationAction
/**
 * Creates an instance of `LogMessageNotificationHandlerImpl` by specifying the log type.
 *
 * @param logType
 * the type of the log
 */
(
        /**
         * Returns the type of the log
         *
         * @return the type of the log
         */
        val logType: String?) : NotificationAction(NotificationAction.ACTION_LOG_MESSAGE) {

    companion object {
        /**
         * Indicates that this log is of type trace. If this `logType` is set the messages would be logged as trace
         * logs.
         */
        const val TRACE_LOG_TYPE = "TraceLog"

        /**
         * Indicates that this log is of type info. If this `logType` is set the messages would be logged as info
         * logs.
         */
        const val INFO_LOG_TYPE = "InfoLog"

        /**
         * Indicates that this log is of type error. If this `logType` is set the messages would be logged as error
         * logs.
         */
        const val ERROR_LOG_TYPE = "ErrorLog"
    }
}