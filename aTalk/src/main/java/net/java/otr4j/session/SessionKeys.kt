package net.java.otr4j.session

import net.java.otr4j.OtrException
import java.math.BigInteger
import java.security.KeyPair
import javax.crypto.interfaces.DHPublicKey

interface SessionKeys {
    fun setLocalPair(keyPair: KeyPair?, localPairKeyID: Int)
    fun setRemoteDHPublicKey(pubKey: DHPublicKey?, remoteKeyID: Int)
    fun incrementSendingCtr()
    val sendingCtr: ByteArray
    var receivingCtr: ByteArray

    @get:Throws(OtrException::class)
    var sendingAESKey: ByteArray?

    @get:Throws(OtrException::class)
    val receivingAESKey: ByteArray?

    @get:Throws(OtrException::class)
    val sendingMACKey: ByteArray?

    @get:Throws(OtrException::class)
    val receivingMACKey: ByteArray?
    fun setS(s: BigInteger?)
    var isUsedReceivingMACKey: Boolean
    val localKeyID: Int
    val remoteKeyID: Int
    val remoteKey: DHPublicKey?
    val localPair: KeyPair?

    companion object {
        const val PREVIOUS = 0
        const val CURRENT = 1
        const val Previous = PREVIOUS
        const val Current = CURRENT
        const val HIGH_SEND_BYTE = 0x01.toByte()
        const val HIGH_RECEIVE_BYTE = 0x02.toByte()
        const val LOW_SEND_BYTE = 0x02.toByte()
        const val LOW_RECEIVE_BYTE = 0x01.toByte()
    }
}