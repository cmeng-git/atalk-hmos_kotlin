/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification

/**
 * An implementation of the `CommandNotificationHandler` interface.
 *
 * @author Yana Stamcheva
 */
class CommandNotificationAction
/**
 * Creates an instance of `CommandNotification` by specifying the `commandDescriptor`, which will
 * point us to the command to execute.
 *
 * @param commandDescriptor
 * a String that should point us to the command to execute
 */
(
        /**
         * Returns the command descriptor.
         *
         * @return the command descriptor
         */
        val descriptor: String?) : NotificationAction(ACTION_COMMAND)