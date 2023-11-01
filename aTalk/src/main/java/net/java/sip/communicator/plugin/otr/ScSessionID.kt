/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr

import net.java.otr4j.session.SessionID
import java.util.*

/**
 * Class used to associate a random UUID to an OTR4J SessionID.
 *
 * @author Daniel Perren
 * @author Eng Chong Meng
 */
class ScSessionID
/**
 * Creates a new instance of this class.
 *
 * @param sessionID the OTR4J SessionID that is being wrapped.
 */
(
        /**
         * Gets the wrapped session ID
         *
         * @return sessionID
         */
        val sessionID: SessionID?) {
    /**
     * Get the current GUID.
     *
     * @return The GUID generated for this SessionID.
     */
    val uuid = UUID.randomUUID()

    /**
     * Overrides equals() for the ability to get the hashcode from sessionID.
     *
     * @param other the object to compare
     * @return true if the objects are considered equal.
     */
    override fun equals(other: Any?): Boolean {
        return other != null && sessionID.toString() == other.toString()
    }

    /**
     * Returns [SessionID.hashCode] of the wrapped SessionID.
     *
     * @return HashCode of the wrapped SessionID.
     */
    override fun hashCode(): Int {
        return sessionID.hashCode()
    }

    /**
     * Returns [SessionID.toString] of the wrapped SessionID.
     *
     * @return String representation of the wrapped SessionID.
     */
    override fun toString(): String {
        return sessionID.toString()
    }
}