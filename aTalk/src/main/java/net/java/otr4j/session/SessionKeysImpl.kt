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
import org.atalk.hmos.plugin.timberlog.TimberLog
import timber.log.Timber
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyPair
import java.util.*
import javax.crypto.interfaces.DHPublicKey

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
internal class SessionKeysImpl(localKeyIndex: Int, remoteKeyIndex: Int) : SessionKeys {
    private val keyDescription: String
    override val sendingCtr = ByteArray(16)

    override var localKeyID = 0
        private set
    override var remoteKeyID = 0
        private set
    override var remoteKey: DHPublicKey? = null
        private set
    override var localPair: KeyPair? = null
        private set
    override var isUsedReceivingMACKey = false
    private var s: BigInteger? = null
    private var isHigh: Boolean? = null

    init {
        var tmpKeyDescription = if (localKeyIndex == 0) "(Previous local, " else "(Most recent local, "
        tmpKeyDescription += if (remoteKeyIndex == 0) "Previous remote)" else "Most recent remote)"
        keyDescription = tmpKeyDescription
    }

    override fun setLocalPair(keyPair: KeyPair?, localPairKeyID: Int) {
        localPair = keyPair
        localKeyID = localPairKeyID
        Timber.log(TimberLog.FINER, "%s current local key ID: %s", keyDescription, localKeyID)
        reset()
    }

    override fun setRemoteDHPublicKey(pubKey: DHPublicKey?, remoteKeyID: Int) {
        remoteKey = pubKey
        this.remoteKeyID = remoteKeyID
        Timber.log(TimberLog.FINER, "%s current remote key ID: %s", keyDescription, this.remoteKeyID)
        reset()
    }

    override fun incrementSendingCtr() {
        Timber.log(TimberLog.FINER, "Incrementing counter for (localkeyID, remoteKeyID) = (%s, %s)",
                localKeyID, remoteKeyID)
        for (i in 7 downTo 0) if ((++sendingCtr[i]).toInt() != 0) break
    }

    override var receivingCtr = ByteArray(16)
        set(ctr) {
            System.arraycopy(ctr, 0, receivingCtr, 0, ctr.size)
        }

    private fun reset() {
        Timber.log(TimberLog.FINER, "Resetting %s session keys.", keyDescription)
        Arrays.fill(sendingCtr, 0x00.toByte())
        Arrays.fill(receivingCtr, 0x00.toByte())
        sendingAESKey = null
        receivingAESKey = null
        sendingMACKey = null
        receivingMACKey = null
        isUsedReceivingMACKey = false
        s = null
        if (localPair != null && remoteKey != null) {
            isHigh = (localPair!!.public as DHPublicKey).y
                    .abs().compareTo(remoteKey!!.y.abs()) == 1
        }
    }

    @Throws(OtrException::class)
    private fun h1(b: Byte): ByteArray? {
        return try {
            val secbytes = SerializationUtils.writeMpi(getS())
            val len = secbytes.size + 1
            val buff = ByteBuffer.allocate(len)
            buff.put(b)
            buff.put(secbytes)
            OtrCryptoEngineImpl().sha1Hash(buff.array())
        } catch (e: Exception) {
            throw OtrException(e)
        }
    }

    override var sendingAESKey: ByteArray? = null
        @Throws(OtrException::class)
        get() {
            if (field != null) return field
            var sendbyte = SessionKeys.LOW_SEND_BYTE
            if (isHigh!!) sendbyte = SessionKeys.HIGH_SEND_BYTE
            val h1 = h1(sendbyte)
            val key = ByteArray(OtrCryptoEngine.AES_KEY_BYTE_LENGTH)
            val buff = ByteBuffer.wrap(h1!!)
            buff[key]
            Timber.log(TimberLog.FINER, "Calculated sending AES key.")
            this.sendingAESKey = key
            return field
        }

    override var receivingAESKey: ByteArray? = null
        @Throws(OtrException::class)
        get() {
            if (field != null) return field
            var receivebyte = SessionKeys.LOW_RECEIVE_BYTE
            if (isHigh!!) receivebyte = SessionKeys.HIGH_RECEIVE_BYTE
            val h1 = h1(receivebyte)
            val key = ByteArray(OtrCryptoEngine.AES_KEY_BYTE_LENGTH)
            val buff = ByteBuffer.wrap(h1!!)
            buff[key]
            Timber.log(TimberLog.FINER, "Calculated receiving AES key.")
            this.receivingAESKey = key
            return field
        }

    override var sendingMACKey: ByteArray? = null
        @Throws(OtrException::class)
        get() {
            if (field != null) return field
            this.sendingMACKey = OtrCryptoEngineImpl().sha1Hash(sendingAESKey)
            Timber.log(TimberLog.FINER, "Calculated sending MAC key.")
            return field
        }

    override var receivingMACKey: ByteArray? = null
    @Throws(OtrException::class)
    get() {
        if (field == null) {
            this.receivingMACKey = OtrCryptoEngineImpl().sha1Hash(receivingAESKey)
            Timber.log(TimberLog.FINER, "Calculated receiving AES key.")
        }
        return field
    }

    @Throws(OtrException::class)
    private fun getS(): BigInteger? {
        if (s == null) {
            s = OtrCryptoEngineImpl().generateSecret(localPair!!.private,
                    remoteKey)
            Timber.log(TimberLog.FINER, "Calculating shared secret S.")
        }
        return s
    }

    override fun setS(s: BigInteger?) {
        this.s = s
    }
}