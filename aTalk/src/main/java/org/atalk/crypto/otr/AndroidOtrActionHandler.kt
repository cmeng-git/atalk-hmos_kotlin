/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.crypto.otr

import net.java.sip.communicator.plugin.otr.OtrActionHandler
import net.java.sip.communicator.plugin.otr.ScOtrEngineImpl
import org.atalk.hmos.aTalkApp
import java.util.*

/**
 * Android `OtrActionHandler` implementation.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidOtrActionHandler : OtrActionHandler {
    /**
     * {@inheritDoc}
     */
    override fun onAuthenticateLinkClicked(uuid: UUID) {
        val scSessionID = ScOtrEngineImpl.getScSessionForGuid(uuid)
        if (scSessionID != null) {
            aTalkApp.globalContext.startActivity(OtrAuthenticateDialog.Companion.createIntent(uuid))
        } else {
            System.err.println("Session for gui: $uuid no longer exists")
        }
    }
}