/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr

import net.java.sip.communicator.plugin.otr.OtrContactManager.OtrContact
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.Contact
import java.security.KeyPair
import java.security.PublicKey

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
interface ScOtrKeyManager {
    fun addListener(l: ScOtrKeyManagerListener)
    fun removeListener(l: ScOtrKeyManagerListener)
    fun verify(contact: OtrContact?, fingerprint: String?)
    fun unverify(contact: OtrContact?, fingerprint: String?)
    fun isVerified(contact: Contact?, fingerprint: String?): Boolean
    fun getFingerprintFromPublicKey(pubKey: PublicKey?): String?
    fun getAllRemoteFingerprints(contact: Contact?): List<String?>?
    fun getLocalFingerprint(account: AccountID?): String?
    fun getLocalFingerprintRaw(account: AccountID?): ByteArray?
    fun saveFingerprint(contact: Contact?, fingerprint: String?)
    fun loadKeyPair(accountID: AccountID?): KeyPair?
    fun generateKeyPair(accountID: AccountID?)
}