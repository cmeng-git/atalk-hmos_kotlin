/*
 * otr4j, the open source java otr librar
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.otr4j

import net.java.otr4j.session.Session
import net.java.otr4j.session.SessionID
import net.java.otr4j.session.SessionImpl
import java.util.*

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
class OtrSessionManagerImpl(host: OtrEngineHost?) : OtrSessionManager {
    private var host: OtrEngineHost? = null
    private var sessions: MutableMap<SessionID, Session>? = null
    private val listeners = Vector<OtrEngineListener>()

    init {
        requireNotNull(host) { "OtrEgineHost is required." }
        setHost(host)
    }

    override fun getSession(sessionID: SessionID?): Session? {
        require((sessionID == null || sessionID != SessionID.EMPTY))
        if (sessions == null) sessions = Hashtable()
        return if (!sessions!!.containsKey(sessionID)) {
            val session = SessionImpl(sessionID, host)
            sessions!![sessionID!!] = session
            session.addOtrEngineListener(object : OtrEngineListener {
                override fun sessionStatusChanged(sessionID: SessionID?) {
                    for (l in listeners) l.sessionStatusChanged(sessionID)
                }

                override fun multipleInstancesDetected(sessionID: SessionID?) {
                    for (l in listeners) l.multipleInstancesDetected(sessionID)
                }

                override fun outgoingSessionChanged(sessionID: SessionID?) {
                    for (l in listeners) l.outgoingSessionChanged(sessionID)
                }
            })
            session
        } else sessions!![sessionID]
    }

    private fun setHost(host: OtrEngineHost) {
        this.host = host
    }

    override fun addOtrEngineListener(l: OtrEngineListener) {
        synchronized(listeners) { if (!listeners.contains(l)) listeners.add(l) }
    }

    override fun removeOtrEngineListener(l: OtrEngineListener) {
        synchronized(listeners) { listeners.remove(l) }
    }
}