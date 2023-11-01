/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.otr4j

import net.java.otr4j.session.*
import java.security.KeyPair

/**
 * This interface should be implemented by the host application. It is required for otr4j to work properly.
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
interface OtrEngineHost {
    @Throws(OtrException::class)
    fun injectMessage(sessionID: SessionID?, msg: String?)

    @Throws(OtrException::class)
    fun unreadableMessageReceived(sessionID: SessionID?)

    @Throws(OtrException::class)
    fun unencryptedMessageReceived(sessionID: SessionID?, msg: String?)

    @Throws(OtrException::class)
    fun showError(sessionID: SessionID?, error: String?)

    @Throws(OtrException::class)
    fun showAlert(sessionID: SessionID?, error: String?)

    @Throws(OtrException::class)
    fun smpError(sessionID: SessionID?, tlvType: Int, cheated: Boolean)

    @Throws(OtrException::class)
    fun smpAborted(sessionID: SessionID?)

    @Throws(OtrException::class)
    fun finishedSessionMessage(sessionID: SessionID?, msgText: String?)

    @Throws(OtrException::class)
    fun requireEncryptedMessage(sessionID: SessionID?, msgText: String?)
    fun getSessionPolicy(sessionID: SessionID?): OtrPolicy?

    /**
     * Get instructions for the necessary fragmentation operations.
     *
     * If no fragmentation is necessary, return `null` to set the default
     * fragmentation instructions which are to use an unlimited number of
     * messages of unlimited size each. Hence fragmentation is not necessary or applied.
     *
     * @param sessionID the session ID of the session
     * @return return fragmentation instructions or null for defaults (i.e. no fragmentation)
     */
    fun getFragmenterInstructions(sessionID: SessionID?): FragmenterInstructions?

    @Throws(OtrException::class)
    fun getLocalKeyPair(sessionID: SessionID?): KeyPair?
    fun getLocalFingerprintRaw(sessionID: SessionID?): ByteArray?
    fun askForSecret(sessionID: SessionID, receiverTag: InstanceTag, question: String?)
    fun verify(sessionID: SessionID?, fingerprint: String?, approved: Boolean)
    fun unverify(sessionID: SessionID?, fingerprint: String?)
    fun getReplyForUnreadableMessage(sessionID: SessionID?): String?
    fun getFallbackMessage(sessionID: SessionID?): String?
    fun messageFromAnotherInstanceReceived(sessionID: SessionID?)
    fun multipleInstancesDetected(sessionID: SessionID?)
}