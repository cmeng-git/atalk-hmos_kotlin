/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.configuration

import java.beans.PropertyChangeEvent
import java.util.*

/**
 * This interface uses SC's own ProperteyVetoException.
 */
interface ConfigVetoableChangeListener : EventListener {
    /**
     * Fired before a Bean's property changes.
     *
     * @param e the change (containing the old and new values)
     * @throws ConfigPropertyVetoException if the change is vetoed by the listener
     */
    @Throws(ConfigPropertyVetoException::class)
    fun vetoableChange(e: PropertyChangeEvent?)
}