/*
 * otr4j, the open source java otr library.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.otr4j.session

import net.java.otr4j.OtrException
import net.java.otr4j.io.messages.*
import java.math.BigInteger
import java.security.KeyPair
import java.security.PublicKey
import javax.crypto.interfaces.DHPublicKey

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
internal abstract class AuthContext {
    // These parameters are initialized when generating D-H Commit Messages.
    // If the Session that this AuthContext belongs to is the 'master' session
    // then these parameters must be replicated to all slave session's auth contexts.
    open var r: ByteArray? = null
    open var localDHKeyPair: KeyPair? = null
    open var localDHPublicKeyBytes: ByteArray? = null
    open var localDHPublicKeyHash: ByteArray? = null
    open var localDHPublicKeyEncrypted: ByteArray? = null

    internal abstract inner class MessageFactory {
        abstract val queryMessage: QueryMessage

        @get:Throws(OtrException::class)
        abstract val dhCommitMessage: DHCommitMessage

        @get:Throws(OtrException::class)
        abstract val dhKeyMessage: DHKeyMessage

        @get:Throws(OtrException::class)
        abstract val revealSignatureMessage: RevealSignatureMessage

        @get:Throws(OtrException::class)
        abstract val signatureMessage: SignatureMessage
    }

    /**
     * Sets this instances settings to the values of the supplied one.
     * @param otherAuthContext we set the property values of this
     */
    fun set(otherAuthContext: AuthContext) {
        r = otherAuthContext.r
        localDHKeyPair = otherAuthContext.localDHKeyPair
        localDHPublicKeyBytes = otherAuthContext.localDHPublicKeyBytes
        localDHPublicKeyHash = otherAuthContext.localDHPublicKeyHash
        localDHPublicKeyEncrypted = otherAuthContext.localDHPublicKeyEncrypted
    }


    abstract val isSecure: Boolean
    abstract val remoteDHPublicKey: DHPublicKey?
    abstract val remoteLongTermPublicKey: PublicKey?

    @get:Throws(OtrException::class)
    abstract val s: BigInteger?

    @get:Throws(OtrException::class)
    abstract val localLongTermKeyPair: KeyPair?

    abstract fun reset()
    @Throws(OtrException::class)
    abstract fun getLocalDHKeyPair1(): KeyPair

    @Throws(OtrException::class)
    abstract fun handleReceivingMessage(m: AbstractMessage)

    @Throws(OtrException::class)
    abstract fun startAuth()

    @Throws(OtrException::class)
    abstract fun respondAuth(version: Int): DHCommitMessage?


    companion object {
        const val NONE = 0
        const val AWAITING_DHKEY = 1
        const val AWAITING_REVEALSIG = 2
        const val AWAITING_SIG = 3
        const val V1_SETUP = 4
        const val C_START = 0x01.toByte()
        const val M1_START = 0x02.toByte()
        const val M2_START = 0x03.toByte()
        const val M1P_START = 0x04.toByte()

        @Deprecated("use {@link #M1P_START} instead ")
        val M1p_START = M1P_START
        const val M2P_START = 0x05.toByte()

        @Deprecated("use {@link #M2P_START} instead ")
        val M2p_START = M2P_START
    }
}