package net.java.otr4j

import net.java.otr4j.session.SessionID

/**
 * This interface should be implemented by the host application. It notifies
 * about session status changes.
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
interface OtrEngineListener {
    fun sessionStatusChanged(sessionID: SessionID?)
    fun multipleInstancesDetected(sessionID: SessionID?)
    fun outgoingSessionChanged(sessionID: SessionID?)
}