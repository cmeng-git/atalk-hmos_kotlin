package net.java.otr4j

import net.java.otr4j.session.Session
import net.java.otr4j.session.SessionID

/**
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
interface OtrSessionManager {
    /**
     * Get an OTR session.
     * @param sessionID the session to retrieve
     * @return MVN_PASS_JAVADOC_INSPECTION
     */
    fun getSession(sessionID: SessionID?): Session?
    fun addOtrEngineListener(l: OtrEngineListener)
    fun removeOtrEngineListener(l: OtrEngineListener)
}