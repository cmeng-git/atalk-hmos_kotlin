/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.otr4j.session

import net.java.otr4j.OtrException
import net.java.otr4j.crypto.OtrCryptoEngine
import net.java.otr4j.crypto.OtrCryptoEngineImpl
import net.java.otr4j.io.SerializationUtils
import net.java.otr4j.io.messages.*
import net.java.otr4j.session.Session.OTRv
import org.atalk.hmos.plugin.timberlog.TimberLog
import timber.log.Timber
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.PublicKey
import java.util.*
import javax.crypto.interfaces.DHPublicKey

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
internal class AuthContextImpl(session: Session) : AuthContext() {
    private var session: Session? = null
    private var authenticationState = 0
    private var remoteDHPublicKeyEncrypted: ByteArray? = null
    private var remoteDHPublicKeyHash: ByteArray? = null
    private var localDHKeyPairID = 0

    override var s: BigInteger? = null
    @Throws(OtrException::class)
    get() {
        if (field == null) {
            field = OtrCryptoEngineImpl().generateSecret(getLocalDHKeyPair1().private, remoteDHPublicKey)
            Timber.log(TimberLog.FINER, "Generated shared secret.")
        }
        return field
    }

    private var c: ByteArray? = null
    private var m1: ByteArray? = null
    private var m2: ByteArray? = null
    private var cp: ByteArray? = null
    private var m1p: ByteArray? = null
    private var m2p: ByteArray? = null

    override var localLongTermKeyPair: KeyPair? = null
    @Throws(OtrException::class)
    get() {
        if (field == null) {
            field = session!!.localKeyPair
        }
        return field
    }

    override var isSecure = false
        private set
    private val messageFactory = MessageFactoryImpl()
    override var remoteLongTermPublicKey: PublicKey? = null
        private set

    init {
        setSession(session)
        reset()
    }

    internal inner class MessageFactoryImpl : MessageFactory() {
        override val queryMessage: QueryMessage
            get() {
                val versions = listOf(OTRv.TWO, OTRv.THREE)
                return QueryMessage(versions)
            }

        @get:Throws(OtrException::class)
        override val dhCommitMessage: DHCommitMessage
            get() {
                val message = DHCommitMessage(session!!.protocolVersion,
                        localDHPublicKeyHash, localDHPublicKeyEncrypted)
                message.senderInstanceTag = session!!.senderInstanceTag.value
                message.receiverInstanceTag = InstanceTag.ZERO_VALUE
                return message
            }

        @get:Throws(OtrException::class)
        override val dhKeyMessage: DHKeyMessage
            get() {
                val dhKeyMessage = DHKeyMessage(session!!.protocolVersion,
                        getLocalDHKeyPair1().public as DHPublicKey)
                dhKeyMessage.senderInstanceTag = session!!.senderInstanceTag.value
                dhKeyMessage.receiverInstanceTag = session!!.receiverInstanceTag.value
                return dhKeyMessage
            }

        @get:Throws(OtrException::class)
        override val revealSignatureMessage: RevealSignatureMessage
            get() {
                val revealSignatureMessage1 = try {
                    val m = SignatureM(getLocalDHKeyPair1().public as DHPublicKey,
                        remoteDHPublicKey, localLongTermKeyPair!!.public, localDHKeyPairID)
                    val otrCryptoEngine = OtrCryptoEngineImpl()
                    val mhash = otrCryptoEngine.sha256Hmac(SerializationUtils.toByteArray(m), getM1())
                    val signature = otrCryptoEngine.sign(mhash, localLongTermKeyPair!!.private)
                    val mysteriousX = SignatureX(localLongTermKeyPair!!.public, localDHKeyPairID, signature)
                    val xEncrypted = otrCryptoEngine.aesEncrypt(getC(), null,
                        SerializationUtils.toByteArray(mysteriousX))
                    val tmp = SerializationUtils.writeData(xEncrypted)
                    val xEncryptedHash = otrCryptoEngine.sha256Hmac160(tmp, getM2())
                    val revealSignatureMessage = RevealSignatureMessage(session!!.protocolVersion, xEncrypted,
                        xEncryptedHash, r)
                    revealSignatureMessage.senderInstanceTag = session!!.senderInstanceTag.value
                    revealSignatureMessage.receiverInstanceTag = session!!.receiverInstanceTag.value
                    revealSignatureMessage
                } catch (e: IOException) {
                    throw OtrException(e)
                }
                return revealSignatureMessage1
            }

        @get:Throws(OtrException::class)
        override val signatureMessage: SignatureMessage
            get() {
                val m = SignatureM(getLocalDHKeyPair1().public as DHPublicKey,
                        remoteDHPublicKey, localLongTermKeyPair!!.public, localDHKeyPairID)
                val otrCryptoEngine = OtrCryptoEngineImpl()
                val mhash = try {
                    otrCryptoEngine.sha256Hmac(SerializationUtils.toByteArray(m), getM1p())
                } catch (e: IOException) {
                    throw OtrException(e)
                }
                val signature = otrCryptoEngine.sign(mhash, localLongTermKeyPair!!.private)
                val mysteriousX = SignatureX(localLongTermKeyPair!!.public, localDHKeyPairID, signature)
                val xEncrypted: ByteArray?
                return try {
                    xEncrypted = otrCryptoEngine.aesEncrypt(getCp(), null, SerializationUtils.toByteArray(mysteriousX))
                    val tmp = SerializationUtils.writeData(xEncrypted)
                    val xEncryptedHash = otrCryptoEngine.sha256Hmac160(tmp, getM2p())
                    val signatureMessage = SignatureMessage(session!!.protocolVersion, xEncrypted, xEncryptedHash)
                    signatureMessage.senderInstanceTag = session!!.senderInstanceTag.value
                    signatureMessage.receiverInstanceTag = session!!.receiverInstanceTag.value
                    signatureMessage
                } catch (e: IOException) {
                    throw OtrException(e)
                }
            }
    }

    override fun reset() {
        Timber.log(TimberLog.FINER, "Resetting authentication state.")
        authenticationState = NONE
        r = null
        remoteDHPublicKey = null
        remoteDHPublicKeyEncrypted = null
        remoteDHPublicKeyHash = null
        localDHKeyPair = null
        localDHKeyPairID = 1
        localDHPublicKeyBytes = null
        localDHPublicKeyHash = null
        localDHPublicKeyEncrypted = null
        s = null
        c = null
        m1 = null
        m2 = null
        cp = null
        m1p = null
        m2p = null
        localLongTermKeyPair = null
        isSecure = false
    }

    override var r: ByteArray? = null
        get() {
            if (field == null) {
                Timber.log(TimberLog.FINER, "Picking random key r.")
                r = ByteArray(OtrCryptoEngine.AES_KEY_BYTE_LENGTH)
                Random().nextBytes(field)
            }
            return field
        }
        set(r) {
            super.r = r
        }

    override var remoteDHPublicKey: DHPublicKey? = null
        private set(dhPublicKey) {
            // Verifies that Alice's gy is a legal value (2 <= gy <= modulus-2)
            require(dhPublicKey!!.y <= OtrCryptoEngine.MODULUS_MINUS_TWO) { "Illegal D-H Public Key value, Ignoring message." }
            require(dhPublicKey.y >= OtrCryptoEngine.BIGINTEGER_TWO) { "Illegal D-H Public Key value, Ignoring message." }
            Timber.log(TimberLog.FINER, "Received D-H Public Key is a legal value.")
            field = dhPublicKey
        }

    private fun setRemoteDHPublicKeyEncrypted(remoteDHPublicKeyEncrypted: ByteArray?) {
        Timber.log(TimberLog.FINER, "Storing encrypted remote public key.")
        this.remoteDHPublicKeyEncrypted = remoteDHPublicKeyEncrypted
    }

    private fun getRemoteDHPublicKeyEncrypted(): ByteArray? {
        return remoteDHPublicKeyEncrypted
    }

    private fun setRemoteDHPublicKeyHash(remoteDHPublicKeyHash: ByteArray?) {
        Timber.log(TimberLog.FINER, "Storing encrypted remote public key hash.")
        this.remoteDHPublicKeyHash = remoteDHPublicKeyHash
    }

    private fun getRemoteDHPublicKeyHash(): ByteArray? {
        return remoteDHPublicKeyHash
    }

    @Throws(OtrException::class)
    override fun getLocalDHKeyPair1(): KeyPair {
        if (localDHKeyPair == null) {
            localDHKeyPair = OtrCryptoEngineImpl().generateDHKeyPair()
            Timber.log(TimberLog.FINER, "Generated local D-H key pair.")
        }
        return localDHKeyPair!!
    }

    @get:Throws(OtrException::class)
    override var localDHPublicKeyHash: ByteArray? = null
        get() {
            if (field == null) {
                localDHPublicKeyHash = OtrCryptoEngineImpl().sha256Hash(this.localDHPublicKeyBytes)
                Timber.log(TimberLog.FINER, "Hashed local D-H public key.")
            }
            return field
        }
        set(localDHPublicKeyHash) {
            super.localDHPublicKeyHash = localDHPublicKeyHash
        }

    @get:Throws(OtrException::class)
    override var localDHPublicKeyEncrypted: ByteArray? = null
        get() {
            if (field == null) {
                localDHPublicKeyEncrypted = OtrCryptoEngineImpl().aesEncrypt(this.r, null, this.localDHPublicKeyBytes)
                Timber.log(TimberLog.FINER, "Encrypted our D-H public key.")
            }
            return field
        }
        set(localDHPublicKeyEncrypted) {
            super.localDHPublicKeyEncrypted = localDHPublicKeyEncrypted
        }


    @Throws(OtrException::class)
    private fun getC(): ByteArray {
        if (c != null) return c!!
        val h2 = h2(C_START)
        val buff = ByteBuffer.wrap(h2)
        c = ByteArray(OtrCryptoEngine.AES_KEY_BYTE_LENGTH)
        buff[c!!]
        Timber.log(TimberLog.FINER, "Computed c.")
        return c!!
    }

    @Throws(OtrException::class)
    private fun getM1(): ByteArray {
        if (m1 != null) return m1!!
        val h2 = h2(M1_START)
        val buff = ByteBuffer.wrap(h2)
        val m1 = ByteArray(OtrCryptoEngine.SHA256_HMAC_KEY_BYTE_LENGTH)
        buff[m1]
        Timber.log(TimberLog.FINER, "Computed m1.")
        this.m1 = m1
        return m1
    }

    @Throws(OtrException::class)
    private fun getM2(): ByteArray {
        if (m2 != null) return m2!!
        val h2 = h2(M2_START)
        val buff = ByteBuffer.wrap(h2)
        val m2 = ByteArray(OtrCryptoEngine.SHA256_HMAC_KEY_BYTE_LENGTH)
        buff[m2]
        Timber.log(TimberLog.FINER, "Computed m2.")
        this.m2 = m2
        return m2
    }

    @Throws(OtrException::class)
    private fun getCp(): ByteArray {
        if (cp != null) return cp!!
        val h2 = h2(C_START)
        val buff = ByteBuffer.wrap(h2)
        val cp = ByteArray(OtrCryptoEngine.AES_KEY_BYTE_LENGTH)
        buff.position(OtrCryptoEngine.AES_KEY_BYTE_LENGTH)
        buff[cp]
        Timber.log(TimberLog.FINER, "Computed c'.")
        this.cp = cp
        return cp
    }

    @Throws(OtrException::class)
    private fun getM1p(): ByteArray {
        if (m1p != null) return m1p!!
        val h2 = h2(M1P_START)
        val buff = ByteBuffer.wrap(h2)
        val m1p = ByteArray(OtrCryptoEngine.SHA256_HMAC_KEY_BYTE_LENGTH)
        buff[m1p]
        this.m1p = m1p
        Timber.log(TimberLog.FINER, "Computed m1'.")
        return m1p
    }

    @Throws(OtrException::class)
    private fun getM2p(): ByteArray {
        if (m2p != null) return m2p!!
        val h2 = h2(M2P_START)
        val buff = ByteBuffer.wrap(h2)
        val m2p = ByteArray(OtrCryptoEngine.SHA256_HMAC_KEY_BYTE_LENGTH)
        buff[m2p]
        this.m2p = m2p
        Timber.log(TimberLog.FINER, "Computed m2'.")
        return m2p
    }

    @Throws(OtrException::class)
    private fun h2(b: Byte): ByteArray {
        val secbytes: ByteArray? = try {
            SerializationUtils.writeMpi(s)
        } catch (e: IOException) {
            throw OtrException(e)
        }
        val len = secbytes!!.size + 1
        val buff = ByteBuffer.allocate(len)
        buff.put(b)
        buff.put(secbytes)
        val sdata = buff.array()
        return OtrCryptoEngineImpl().sha256Hash(sdata)
    }

    @get:Throws(OtrException::class)
    override var localDHPublicKeyBytes: ByteArray? = null
        get() {
            if (field == null) {
                try {
                    field = SerializationUtils.writeMpi((getLocalDHKeyPair1().public as DHPublicKey).y)
                } catch (e: IOException) {
                    throw OtrException(e)
                }
            }
            return field
        }

        set(localDHPublicKeyBytes) {
            super.localDHPublicKeyBytes = localDHPublicKeyBytes
        }

    @Throws(OtrException::class)
    override fun handleReceivingMessage(m: AbstractMessage) {
        when (m.messageType) {
            AbstractEncodedMessage.MESSAGE_DH_COMMIT -> handleDHCommitMessage(m as DHCommitMessage)
            AbstractEncodedMessage.MESSAGE_DHKEY -> handleDHKeyMessage(m as DHKeyMessage)
            AbstractEncodedMessage.MESSAGE_REVEALSIG -> handleRevealSignatureMessage(m as RevealSignatureMessage)
            AbstractEncodedMessage.MESSAGE_SIGNATURE -> handleSignatureMessage(m as SignatureMessage)
            else -> throw UnsupportedOperationException()
        }
    }

    @Throws(OtrException::class)
    private fun validateMessage(m: AbstractEncodedMessage): Boolean {
        val messageTypeName = extractMessageTypeName(m)
        val mySession = session
        val sessionID = mySession!!.sessionID
        Timber.log(TimberLog.FINER, "%s received a %s message from %s through %s.",
                sessionID!!.accountID, messageTypeName, sessionID.userID, sessionID.protocolName)
        return if (m.protocolVersion == OTRv.TWO && !mySession.sessionPolicy.allowV2) {
            Timber.log(TimberLog.FINER, "ALLOW_V2 is not set, ignore this message.")
            false
        } else if (m.protocolVersion == OTRv.THREE && !mySession.sessionPolicy.allowV3) {
            Timber.log(TimberLog.FINER, "ALLOW_V3 is not set, ignore this message.")
            false
        } else if (m.protocolVersion == OTRv.THREE && mySession.senderInstanceTag.value != m.receiverInstanceTag && (m.messageType != AbstractEncodedMessage.MESSAGE_DH_COMMIT
                        || m.receiverInstanceTag != 0)) // from the protocol specification: "For a commit message this will often be 0,
        // since the other party may not have identified their instance tag yet."
        {
            Timber.log(TimberLog.FINER, "Received a %s Message with receiver instance tag that is different from ours, ignore this message",
                    messageTypeName)
            false
        } else {
            true
        }
    }

    @Throws(OtrException::class)
    private fun handleSignatureMessage(m: SignatureMessage) {
        when (authenticationState) {
            AWAITING_SIG -> {
                // Verify MAC.
                if (!m.verify(getM2p())) {
                    Timber.log(TimberLog.FINER, "Signature MACs are not equal, ignoring message.")
                    return
                }

                // Decrypt X.
                val remoteXDecrypted = m.decrypt(getCp())
                val remoteX: SignatureX? = try {
                    SerializationUtils.toMysteriousX(remoteXDecrypted)
                } catch (e: IOException) {
                    throw OtrException(e)
                }
                // Compute signature.
                val localRemoteLongTermPublicKey = remoteX!!.longTermPublicKey
                val remoteM = SignatureM(remoteDHPublicKey,
                        getLocalDHKeyPair1().public as DHPublicKey,
                        localRemoteLongTermPublicKey, remoteX.dhKeyID)
                val otrCryptoEngine = OtrCryptoEngineImpl()
                // Verify signature.
                val signature = try {
                    otrCryptoEngine.sha256Hmac(SerializationUtils.toByteArray(remoteM), getM1p())
                } catch (e: IOException) {
                    throw OtrException(e)
                }
                if (!otrCryptoEngine.verify(signature, localRemoteLongTermPublicKey, remoteX.signature)) {
                    Timber.log(TimberLog.FINER, "Signature verification failed.")
                    return
                }
                isSecure = true
                remoteLongTermPublicKey = localRemoteLongTermPublicKey
            }
            else -> Timber.log(TimberLog.FINER, "We were not expecting a signature, ignoring message.")
        }
    }

    @Throws(OtrException::class)
    private fun handleRevealSignatureMessage(m: RevealSignatureMessage) {
        when (authenticationState) {
            AWAITING_REVEALSIG -> {
                // Use the received value of r to decrypt the value of gx received
                // in the D-H Commit Message, and verify the hash therein.
                // Decrypt the encrypted signature, and verify the signature and the MACs.
                // If everything checks out:

                // * Reply with a Signature Message.
                // * Transition authstate to AUTHSTATE_NONE.
                // * Transition msgstate to MSGSTATE_ENCRYPTED.
                // * TODO If there is a recent stored message, encrypt it and send it as a Data Message.
                val otrCryptoEngine = OtrCryptoEngineImpl()
                // Uses r to decrypt the value of gx sent earlier
                val remoteDHPublicKeyDecrypted = otrCryptoEngine.aesDecrypt(m.revealedKey,
                        null, getRemoteDHPublicKeyEncrypted())

                // Verifies that HASH(gx) matches the value sent earlier
                val remoteDHPublicKeyHash = otrCryptoEngine.sha256Hash(remoteDHPublicKeyDecrypted)
                if (!Arrays.equals(remoteDHPublicKeyHash, getRemoteDHPublicKeyHash())) {
                    Timber.log(TimberLog.FINER, "Hashes don't match, ignoring message.")
                    return
                }

                // Verifies that Bob's gx is a legal value (2 <= gx <= modulus-2)
                val remoteDHPublicKeyMpi = try {
                    SerializationUtils.readMpi(remoteDHPublicKeyDecrypted)
                } catch (e: IOException) {
                    throw OtrException(e)
                }
                remoteDHPublicKey = otrCryptoEngine.getDHPublicKey(remoteDHPublicKeyMpi)

                // Verify received Data.
                if (!m.verify(getM2())) {
                    Timber.log(TimberLog.FINER, "Signature MACs are not equal, ignoring message.")
                    return
                }

                // Decrypt X.
                val remoteXDecrypted = m.decrypt(getC())
                val remoteX = try {
                    SerializationUtils.toMysteriousX(remoteXDecrypted)
                } catch (e: IOException) {
                    throw OtrException(e)
                }

                // Compute signature.
                val remoteLongTermPublicKey = remoteX.longTermPublicKey
                val remoteM = SignatureM(remoteDHPublicKey,
                        getLocalDHKeyPair1().public as DHPublicKey,
                        remoteLongTermPublicKey, remoteX.dhKeyID)

                // Verify signature.
                val signature = try {
                    otrCryptoEngine.sha256Hmac(SerializationUtils.toByteArray(remoteM), getM1())
                } catch (e: IOException) {
                    throw OtrException(e)
                }
                if (!otrCryptoEngine.verify(signature, remoteLongTermPublicKey, remoteX.signature)) {
                    Timber.log(TimberLog.FINER, "Signature verification failed.")
                    return
                }
                Timber.log(TimberLog.FINER, "Signature verification succeeded.")
                authenticationState = NONE
                isSecure = true
                this.remoteLongTermPublicKey = remoteLongTermPublicKey
                session!!.injectMessage(messageFactory.signatureMessage)
            }
            else -> Timber.log(TimberLog.FINER, "Ignoring message.")
        }
    }

    @Throws(OtrException::class)
    private fun handleDHKeyMessage(m: DHKeyMessage) {
        session!!.receiverInstanceTag = InstanceTag(m.senderInstanceTag)
        when (authenticationState) {
            NONE, AWAITING_DHKEY -> {
                // Reply with a Reveal Signature Message and transition authstate to AUTHSTATE_AWAITING_SIG
                remoteDHPublicKey = m.dhPublicKey
                authenticationState = AWAITING_SIG
                session!!.injectMessage(messageFactory.revealSignatureMessage)
                Timber.log(TimberLog.FINER, "Sent Reveal Signature.")
            }
            AWAITING_SIG -> if (m.dhPublicKey!!.y == remoteDHPublicKey!!.y) {
                // If this D-H Key message is the same the one you received
                // earlier (when you entered AUTHSTATE_AWAITING_SIG):
                // Retransmit your Reveal Signature Message.
                session!!.injectMessage(messageFactory.revealSignatureMessage)
                Timber.log(TimberLog.FINER, "Resent Reveal Signature.")
            } else {
                // Otherwise: Ignore the message.
                Timber.log(TimberLog.FINER, "Ignoring message.")
            }
            else -> {}
        }
    }

    @Throws(OtrException::class)
    private fun handleDHCommitMessage(m: DHCommitMessage) {
        session!!.receiverInstanceTag = InstanceTag(m.senderInstanceTag)
        when (authenticationState) {
            NONE -> {
                // Reply with a D-H Key Message, and transition authstate to AUTHSTATE_AWAITING_REVEALSIG.
                reset()
                session!!.protocolVersion = m.protocolVersion
                setRemoteDHPublicKeyEncrypted(m.dhPublicKeyEncrypted)
                setRemoteDHPublicKeyHash(m.dhPublicKeyHash)
                authenticationState = AWAITING_REVEALSIG
                session!!.injectMessage(messageFactory.dhKeyMessage)
                Timber.log(TimberLog.FINER, "Sent D-H key.")
            }
            AWAITING_DHKEY -> {
                // This is the trickiest transition in the whole protocol. It
                // indicates that you have already sent a D-H Commit message to your
                // correspondent, but that he either didn't receive it, or just
                // didn't receive it yet, and has sent you one as well. The symmetry
                // will be broken by comparing the hashed gx you sent in your D-H
                // Commit Message with the one you received, considered as 32-byte
                // unsigned big-endian values.
                val ourHash = BigInteger(1, this.localDHPublicKeyHash)
                val theirHash = BigInteger(1, m.dhPublicKeyHash)
                if (theirHash.compareTo(ourHash) == -1) {
                    // Ignore the incoming D-H Commit message, but resend your D-H
                    // Commit message.
                    session!!.injectMessage(messageFactory.dhCommitMessage)
                    Timber.log(TimberLog.FINER, "Ignored the incoming D-H Commit message," +
                            " but resent our D-H Commit message.")
                } else {
                    // *Forget* your old gx value that you sent (encrypted) earlier,
                    // and pretend you're in AUTHSTATE_NONE; i.e. reply with a D-H
                    // Key Message, and transition authstate to
                    // AUTHSTATE_AWAITING_REVEALSIG.
                    reset()
                    session!!.protocolVersion = m.protocolVersion
                    setRemoteDHPublicKeyEncrypted(m.dhPublicKeyEncrypted)
                    setRemoteDHPublicKeyHash(m.dhPublicKeyHash)
                    authenticationState = AWAITING_REVEALSIG
                    session!!.injectMessage(messageFactory.dhKeyMessage)
                    Timber.log(TimberLog.FINER, "Forgot our old gx value that we sent (encrypted) earlier," +
                            " and pretended we're in AUTHSTATE_NONE -> Sent D-H key.")
                }
            }
            AWAITING_REVEALSIG -> {
                // Retransmit your D-H Key Message (the same one as you sent when
                // you entered AUTHSTATE_AWAITING_REVEALSIG). Forget the old D-H
                // Commit message, and use this new one instead.
                setRemoteDHPublicKeyEncrypted(m.dhPublicKeyEncrypted)
                setRemoteDHPublicKeyHash(m.dhPublicKeyHash)
                session!!.injectMessage(messageFactory.dhKeyMessage)
                Timber.log(TimberLog.FINER, "Sent D-H key.")
            }
            AWAITING_SIG -> {
                // Reply with a new D-H Key message, and transition authstate to AUTHSTATE_AWAITING_REVEALSIG
                reset()
                setRemoteDHPublicKeyEncrypted(m.dhPublicKeyEncrypted)
                setRemoteDHPublicKeyHash(m.dhPublicKeyHash)
                authenticationState = AWAITING_REVEALSIG
                session!!.injectMessage(messageFactory.dhKeyMessage)
                Timber.log(TimberLog.FINER, "Sent D-H key.")
            }
            V1_SETUP -> throw UnsupportedOperationException("Can not handle message in auth. state "
                    + authenticationState)
            else -> throw UnsupportedOperationException("Can not handle message in auth. state "
                    + authenticationState)
        }
    }

    @Throws(OtrException::class)
    override fun startAuth() {
        Timber.log(TimberLog.FINER, "Starting Authenticated Key Exchange, sending query message")
        session!!.injectMessage(messageFactory.queryMessage)
    }

    @Throws(OtrException::class)
    override fun respondAuth(version: Int): DHCommitMessage {
        if (version != OTRv.TWO && version != OTRv.THREE) throw OtrException(Exception("Only allowed versions are: 2, 3"))
        Timber.log(TimberLog.FINER, "Responding to Query Message")
        reset()
        session!!.protocolVersion = version
        authenticationState = AWAITING_DHKEY
        Timber.log(TimberLog.FINER, "Generating D-H Commit.")
        return messageFactory.dhCommitMessage
    }

    private fun setSession(session: Session) {
        this.session = session
    }

    companion object {
        /**
         * Returns a simple, human-readable name for a type of message.
         *
         * @return this returns `getClass().getSimpleName()`,
         * removing the string "Message" in the end, if it is present,
         * thus "my.pkg.MyMessage" would return "My"
         */
        private fun extractMessageTypeName(msg: AbstractMessage): String {
            return msg.javaClass.simpleName.replaceFirst("Message$".toRegex(), "")
        }
    }
}