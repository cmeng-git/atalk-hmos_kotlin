/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.configuration

import java.beans.PropertyChangeEvent

/**
 * A PropertyVetoException is thrown when a proposed change to a property represents an unacceptable value.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class ConfigPropertyVetoException
/**
 * Constructs a `PropertyVetoException` with a detailed message.
 *
 * @param message Descriptive message
 * @param evt A PropertyChangeEvent describing the vetoed change.
 */
(message: String?,
        /**
         * A PropertyChangeEvent describing the vetoed change.
         */
        val propertyChangeEvent: PropertyChangeEvent) : RuntimeException(message) {
    /**
     * Gets the vetoed `PropertyChangeEvent`.
     *
     * @return A PropertyChangeEvent describing the vetoed change.
     */

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}