package net.java.otr4j

import net.java.otr4j.session.SessionID
import java.security.KeyPair
import java.security.PublicKey

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
interface OtrKeyManager {
    fun addListener(l: OtrKeyManagerListener)
    fun removeListener(l: OtrKeyManagerListener)
    fun verify(sessionID: SessionID?)
    fun unverify(sessionID: SessionID?)
    fun isVerified(sessionID: SessionID?): Boolean
    fun getRemoteFingerprint(sessionID: SessionID?): String?
    fun getLocalFingerprint(sessionID: SessionID?): String?
    fun getLocalFingerprintRaw(sessionID: SessionID?): ByteArray?
    fun savePublicKey(sessionID: SessionID?, pubKey: PublicKey)
    fun loadRemotePublicKey(sessionID: SessionID?): PublicKey?
    fun loadLocalKeyPair(sessionID: SessionID?): KeyPair?
    fun generateLocalKeyPair(sessionID: SessionID?)
}