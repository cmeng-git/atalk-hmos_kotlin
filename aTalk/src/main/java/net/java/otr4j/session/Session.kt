package net.java.otr4j.session

import net.java.otr4j.OtrEngineListener
import net.java.otr4j.OtrException
import net.java.otr4j.OtrPolicy
import net.java.otr4j.io.messages.*
import java.math.BigInteger
import java.security.KeyPair
import java.security.PublicKey

interface Session {
    interface OTRv {
        companion object {
            const val ONE = 1
            const val TWO = 2
            const val THREE = 3
        }
    }

    val sessionStatus: SessionStatus

    val sessionID: SessionID?

    @Throws(OtrException::class)
    fun injectMessage(m: AbstractMessage?)

    @get:Throws(OtrException::class)
    val localKeyPair: KeyPair
    val sessionPolicy: OtrPolicy

    @Throws(OtrException::class)
    fun transformReceiving(content: String?): String?

    @Throws(OtrException::class)
    fun transformSending(content: String, tlvs: List<TLV?>?): Array<String>?

    @Throws(OtrException::class)
    fun transformSending(content: String): Array<String>?

    @Throws(OtrException::class)
    fun startSession()

    @Throws(OtrException::class)
    fun endSession()

    @Throws(OtrException::class)
    fun refreshSession()
    val remotePublicKey: PublicKey?
    fun addOtrEngineListener(l: OtrEngineListener)
    fun removeOtrEngineListener(l: OtrEngineListener)

    @Throws(OtrException::class)
    fun initSmp(question: String?, secret: String)

    @Throws(OtrException::class)
    fun respondSmp(question: String?, secret: String)

    @Throws(OtrException::class)
    fun abortSmp()
    val isSmpInProgress: Boolean
    val s: BigInteger?

    // OTRv3 methods
    val instances: List<SessionImpl>
    val outgoingInstance: Session
    fun setOutgoingInstance(tag: InstanceTag): Boolean
    val senderInstanceTag: InstanceTag
    var receiverInstanceTag: InstanceTag
    var protocolVersion: Int

    @Throws(OtrException::class)
    fun respondSmp(receiverTag: InstanceTag, question: String?, secret: String)
    fun getSessionStatus(tag: InstanceTag): SessionStatus
    fun getRemotePublicKey(tag: InstanceTag): PublicKey
}