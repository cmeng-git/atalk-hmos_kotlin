/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr

import net.java.otr4j.crypto.OtrCryptoEngineImpl
import net.java.otr4j.crypto.OtrCryptoException
import net.java.sip.communicator.plugin.otr.OtrContactManager.OtrContact
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.Contact
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

/**
 * @author George Politis
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class ScOtrKeyManagerImpl : ScOtrKeyManager {
    private val configurator = OtrConfigurator()
    private val listeners = Vector<ScOtrKeyManagerListener>()
    override fun addListener(l: ScOtrKeyManagerListener) {
        synchronized(listeners) { if (!listeners.contains(l)) listeners.add(l) }
    }

    /**
     * Gets a copy of the list of `ScOtrKeyManagerListener`s registered with this instance
     * which may safely be iterated without the risk of a `ConcurrentModificationException`.
     *
     * @return a copy of the list of `ScOtrKeyManagerListener`s registered with this
     * instance which may safely be iterated without the risk of a
     * `ConcurrentModificationException`
    `` */
    private fun getListeners(): Array<ScOtrKeyManagerListener> {
        synchronized(listeners) { return listeners.toTypedArray() }
    }

    override fun removeListener(l: ScOtrKeyManagerListener) {
        synchronized(listeners) { listeners.remove(l) }
    }

    override fun verify(contact: OtrContact?, fingerprint: String?) {
        if (fingerprint == null || contact == null) return
        configurator.setProperty(contact.contact!!.address + "." + fingerprint
                + FP_VERIFIED, true)
        for (l in getListeners()) l.contactVerificationStatusChanged(contact)
    }

    override fun unverify(contact: OtrContact?, fingerprint: String?) {
        if (fingerprint == null || contact == null) return
        configurator.setProperty(contact.contact!!.address + "." + fingerprint
                + FP_VERIFIED, false)
        for (l in getListeners()) l.contactVerificationStatusChanged(contact)
    }

    override fun isVerified(contact: Contact?, fingerprint: String?): Boolean {
        return if (fingerprint == null || contact == null) false else configurator.getPropertyBoolean(contact.address + "." + fingerprint
                + FP_VERIFIED, false)
    }

    override fun getAllRemoteFingerprints(contact: Contact?): List<String?>? {
        if (contact == null) return null

        /*
		 * The following lines are needed for backward compatibility with old versions of the otr
		 * plugin. Instead of lists of fingerprints the otr plugin used to store one public key
		 * for every contact in the form of "userID.publicKey=..." and one boolean property in
		 * the form of "userID.publicKey.verified=...". In order not to loose these old
		 * properties we have to convert them to match the new format.
		 */
        val userID = contact.address
        val b64PubKey = configurator.getPropertyBytes(userID + PUBLIC_KEY)
        if (b64PubKey != null) {
            // We delete the old format property because we are going to convert it in the new
            // format
            configurator.removeProperty(userID + PUBLIC_KEY)
            val publicKeySpec = X509EncodedKeySpec(b64PubKey)
            val keyFactory: KeyFactory
            try {
                keyFactory = KeyFactory.getInstance("DSA")
                val pubKey = keyFactory.generatePublic(publicKeySpec)
                val isVerified = configurator.getPropertyBoolean(userID
                        + PUBLIC_KEY_VERIFIED, false)

                // We also make sure to delete this old format property if it exists.
                configurator.removeProperty(userID + PUBLIC_KEY_VERIFIED)
                val fingerprint = getFingerprintFromPublicKey(pubKey)

                // Now we can store the old properties in the new format.
                if (isVerified) verify(OtrContactManager.getOtrContact(contact, null), fingerprint) else unverify(OtrContactManager.getOtrContact(contact, null), fingerprint)

                // Finally we append the new fingerprint to out stored list of
                // fingerprints.
                configurator.appendProperty(userID + FINGER_PRINT, fingerprint)
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
            } catch (e: InvalidKeySpecException) {
                e.printStackTrace()
            }
        }

        // Now we can safely return our list of fingerprints for this contact without worrying
        // that we missed an old format property.
        return configurator.getAppendedProperties(contact.address + FINGER_PRINT)
    }

    override fun getFingerprintFromPublicKey(pubKey: PublicKey?): String? {
        return try {
            OtrCryptoEngineImpl().getFingerprint(pubKey)
        } catch (e: OtrCryptoException) {
            e.printStackTrace()
            null
        }
    }

    override fun getLocalFingerprint(account: AccountID?): String? {
        val keyPair = loadKeyPair(account) ?: return null
        val pubKey = keyPair.public
        return try {
            OtrCryptoEngineImpl().getFingerprint(pubKey)
        } catch (e: OtrCryptoException) {
            e.printStackTrace()
            null
        }
    }

    override fun getLocalFingerprintRaw(account: AccountID?): ByteArray? {
        val keyPair = loadKeyPair(account) ?: return null
        val pubKey = keyPair.public
        return try {
            OtrCryptoEngineImpl().getFingerprintRaw(pubKey)
        } catch (e: OtrCryptoException) {
            e.printStackTrace()
            null
        }
    }

    override fun saveFingerprint(contact: Contact?, fingerprint: String?) {
        if (contact == null) return
        configurator.appendProperty(contact.address + FINGER_PRINT, fingerprint)
        configurator.setProperty(contact.address + "." + fingerprint + FP_VERIFIED,
                false)
    }

    override fun loadKeyPair(accountID: AccountID?): KeyPair? {
        if (accountID == null) return null
        val accountUuid = accountID.accountUniqueID
        // Load Private Key.
        val b64PrivKey = configurator.getPropertyBytes(accountUuid + PRIVATE_KEY)
                ?: return null
        val privateKeySpec = PKCS8EncodedKeySpec(b64PrivKey)

        // Load Public Key.
        val b64PubKey = configurator.getPropertyBytes(accountUuid + PUBLIC_KEY)
                ?: return null
        val publicKeySpec = X509EncodedKeySpec(b64PubKey)
        val publicKey: PublicKey
        val privateKey: PrivateKey

        // Generate KeyPair.
        val keyFactory: KeyFactory
        try {
            keyFactory = KeyFactory.getInstance("DSA")
            publicKey = keyFactory.generatePublic(publicKeySpec)
            privateKey = keyFactory.generatePrivate(privateKeySpec)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            return null
        } catch (e: InvalidKeySpecException) {
            e.printStackTrace()
            return null
        }
        return KeyPair(publicKey, privateKey)
    }

    override fun generateKeyPair(accountID: AccountID?) {
        if (accountID == null) return
        val accountUuid = accountID.accountUniqueID
        val keyPair = try {
            KeyPairGenerator.getInstance("DSA").genKeyPair()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            return
        }

        // Store Public Key.
        val pubKey = keyPair.public
        val x509EncodedKeySpec = X509EncodedKeySpec(pubKey.encoded)
        configurator.setProperty(accountUuid + PUBLIC_KEY, x509EncodedKeySpec.encoded)

        // Store Private Key.
        val privKey = keyPair.private
        val pkcs8EncodedKeySpec = PKCS8EncodedKeySpec(privKey.encoded)
        configurator.setProperty(accountUuid + PRIVATE_KEY, pkcs8EncodedKeySpec.encoded)
    }

    companion object {
        private const val PUBLIC_KEY = ".publicKey"
        private const val PRIVATE_KEY = ".privateKey"
        private const val FINGER_PRINT = ".fingerprints"
        private const val FP_VERIFIED = ".fingerprint_verified"
        private const val PUBLIC_KEY_VERIFIED = ".publicKey_verified"
    }
}