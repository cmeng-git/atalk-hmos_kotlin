package net.java.otr4j

import net.java.otr4j.crypto.OtrCryptoEngineImpl
import net.java.otr4j.crypto.OtrCryptoException
import net.java.otr4j.session.*
import org.bouncycastle.util.encoders.Base64
import java.io.*
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

class OtrKeyManagerImpl : OtrKeyManager {
    private val store: OtrKeyManagerStore
    private val listeners = ArrayList<OtrKeyManagerListener>()

    constructor(store: OtrKeyManagerStore) {
        this.store = store
    }

    /*
     * NOTE This class should probably be moved to its own file,
     * maybe in the util package or as OtrKeyManagerStoreImpl in this package.
     */
    class DefaultPropertiesStore(filepath: String?) : OtrKeyManagerStore {
        private val properties = Properties()
        private val filepath: String?

        init {
            require((filepath == null || filepath.isNotEmpty()))
            this.filepath = filepath
            properties.clear()
            BufferedInputStream(FileInputStream(configurationFile)).use { `in` -> properties.load(`in`) }
        }

        @get:Throws(IOException::class)
        private val configurationFile: File
            get() {
                val configFile = File(filepath!!)
                if (!configFile.exists()) configFile.createNewFile()
                return configFile
            }

        override fun setProperty(id: String?, value: Boolean) {
            properties.setProperty(id, "true")
            try {
                store()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @Throws(FileNotFoundException::class, IOException::class)
        private fun store() {
            FileOutputStream(configurationFile).use { out -> properties.store(out, null) }
        }

        override fun setProperty(id: String?, value: ByteArray?) {
            properties.setProperty(id, String(Base64.encode(value)))
            try {
                store()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun removeProperty(id: String) {
            properties.remove(id)
        }

        override fun getPropertyBytes(id: String?): ByteArray? {
            val value = properties.getProperty(id) ?: return null
            return Base64.decode(value)
        }

        override fun getPropertyBoolean(id: String, defaultValue: Boolean): Boolean {
            return try {
                java.lang.Boolean.parseBoolean(Objects.requireNonNull(properties[id]).toString())
            } catch (e: Exception) {
                defaultValue
            }
        }
    }

    constructor(filepath: String?) {
        store = DefaultPropertiesStore(filepath)
    }

    override fun addListener(l: OtrKeyManagerListener) {
        synchronized(listeners) { if (!listeners.contains(l)) listeners.add(l) }
    }

    override fun removeListener(l: OtrKeyManagerListener) {
        synchronized(listeners) { listeners.remove(l) }
    }

    override fun generateLocalKeyPair(sessionID: SessionID?) {
        if (sessionID == null) return
        val accountID = sessionID.accountID
        val keyPair: KeyPair
        keyPair = try {
            KeyPairGenerator.getInstance("DSA").genKeyPair()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            return
        }

        // Store Public Key.
        val pubKey = keyPair.public
        val x509EncodedKeySpec = X509EncodedKeySpec(pubKey.encoded)
        store.setProperty("$accountID.publicKey", x509EncodedKeySpec.encoded)

        // Store Private Key.
        val privKey = keyPair.private
        val pkcs8EncodedKeySpec = PKCS8EncodedKeySpec(privKey.encoded)
        store.setProperty("$accountID.privateKey", pkcs8EncodedKeySpec.encoded)
    }

    override fun getLocalFingerprint(sessionID: SessionID?): String? {
        val keyPair = loadLocalKeyPair(sessionID) ?: return null
        val pubKey = keyPair.public
        return try {
            OtrCryptoEngineImpl().getFingerprint(pubKey)
        } catch (e: OtrCryptoException) {
            e.printStackTrace()
            null
        }
    }

    override fun getLocalFingerprintRaw(sessionID: SessionID?): ByteArray? {
        val keyPair = loadLocalKeyPair(sessionID) ?: return null
        val pubKey = keyPair.public
        return try {
            OtrCryptoEngineImpl().getFingerprintRaw(pubKey)
        } catch (e: OtrCryptoException) {
            e.printStackTrace()
            null
        }
    }

    override fun getRemoteFingerprint(sessionID: SessionID?): String? {
        val remotePublicKey = loadRemotePublicKey(sessionID) ?: return null
        return try {
            OtrCryptoEngineImpl().getFingerprint(remotePublicKey)
        } catch (e: OtrCryptoException) {
            e.printStackTrace()
            null
        }
    }

    override fun isVerified(sessionID: SessionID?): Boolean {
        return (sessionID != null
                && store.getPropertyBoolean(sessionID.userID
                + ".publicKey.verified", false))
    }

    override fun loadLocalKeyPair(sessionID: SessionID?): KeyPair? {
        if (sessionID == null) return null
        val accountID = sessionID.accountID
        // Load Private Key.
        val b64PrivKey = store.getPropertyBytes("$accountID.privateKey") ?: return null
        val privateKeySpec = PKCS8EncodedKeySpec(b64PrivKey)

        // Load Public Key.
        val b64PubKey = store.getPropertyBytes("$accountID.publicKey") ?: return null
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

    override fun loadRemotePublicKey(sessionID: SessionID?): PublicKey? {
        if (sessionID == null) return null
        val userID = sessionID.userID
        val b64PubKey = store.getPropertyBytes("$userID.publicKey") ?: return null
        val publicKeySpec = X509EncodedKeySpec(b64PubKey)
        // Generate KeyPair.
        val keyFactory: KeyFactory
        return try {
            keyFactory = KeyFactory.getInstance("DSA")
            keyFactory.generatePublic(publicKeySpec)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            null
        } catch (e: InvalidKeySpecException) {
            e.printStackTrace()
            null
        }
    }

    override fun savePublicKey(sessionID: SessionID?, pubKey: PublicKey) {
        if (sessionID == null) return
        val x509EncodedKeySpec = X509EncodedKeySpec(pubKey.encoded)
        val userID = sessionID.userID
        store.setProperty("$userID.publicKey", x509EncodedKeySpec.encoded)
        store.removeProperty("$userID.publicKey.verified")
    }

    override fun unverify(sessionID: SessionID?) {
        if (sessionID == null) return
        if (!isVerified(sessionID)) return
        store.removeProperty(sessionID.userID + ".publicKey.verified")
        for (l in listeners) l.verificationStatusChanged(sessionID)
    }

    override fun verify(sessionID: SessionID?) {
        if (sessionID == null) return
        if (isVerified(sessionID)) return
        store.setProperty(sessionID.userID + ".publicKey.verified", true)
        for (l in listeners) l.verificationStatusChanged(sessionID)
    }
}