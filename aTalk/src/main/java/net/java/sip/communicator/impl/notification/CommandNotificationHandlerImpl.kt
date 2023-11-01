/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.notification

import net.java.sip.communicator.service.notification.CommandNotificationAction
import net.java.sip.communicator.service.notification.CommandNotificationHandler
import net.java.sip.communicator.service.notification.NotificationAction
import org.apache.commons.lang3.StringUtils
import timber.log.Timber
import java.io.IOException

/**
 * An implementation of the `CommandNotificationHandler` interface.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class CommandNotificationHandlerImpl : CommandNotificationHandler {
    /**
     * Executes the command, given by the `descriptor` of a specific `CommandNotificationAction`.
     *
     * @param action the action to act upon.
     * @param cmdargs command-line arguments.
     */
    override fun execute(action: CommandNotificationAction?, cmdargs: Map<String, String>?) {
        var actionDescriptor = action!!.descriptor
        if (StringUtils.isBlank(actionDescriptor)) return
        if (cmdargs != null) {
            for ((key, value) in cmdargs) {
                actionDescriptor = actionDescriptor!!.replace("\${$key}", value)
            }
        }
        try {
            Runtime.getRuntime().exec(actionDescriptor)
        } catch (ioe: IOException) {
            Timber.e(ioe, "Failed to execute the following command: %s", action.descriptor)
        }
    }

    override val actionType: String
        get() = NotificationAction.ACTION_COMMAND
}