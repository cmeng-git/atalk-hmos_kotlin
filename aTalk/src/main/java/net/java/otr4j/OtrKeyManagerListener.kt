package net.java.otr4j

import net.java.otr4j.session.SessionID

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
interface OtrKeyManagerListener {
    fun verificationStatusChanged(session: SessionID?)
}