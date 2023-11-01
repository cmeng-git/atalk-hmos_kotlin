package net.java.otr4j.io

import net.java.otr4j.crypto.OtrCryptoEngineImpl
import net.java.otr4j.io.messages.SignatureX
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.interfaces.DSAPublicKey
import java.security.spec.DSAPublicKeySpec
import java.security.spec.InvalidKeySpecException
import javax.crypto.interfaces.DHPublicKey

class OtrInputStream(`in`: InputStream?) : FilterInputStream(`in`), SerializationConstants {
    @Throws(IOException::class)
    private fun readNumber(length: Int): Int {
        val b = ByteArray(length)
        read(b)
        var value = 0
        for (i in b.indices) {
            val shift = (b.size - 1 - i) * 8
            value += b[i].toInt() and 0x000000FF shl shift
        }
        return value
    }

    @Throws(IOException::class)
    fun readByte(): Int {
        return readNumber(SerializationConstants.Companion.TYPE_LEN_BYTE)
    }

    @Throws(IOException::class)
    fun readInt(): Int {
        return readNumber(SerializationConstants.Companion.TYPE_LEN_INT)
    }

    @Throws(IOException::class)
    fun readShort(): Int {
        return readNumber(SerializationConstants.Companion.TYPE_LEN_SHORT)
    }

    @Throws(IOException::class)
    fun readCtr(): ByteArray {
        val b = ByteArray(SerializationConstants.Companion.TYPE_LEN_CTR)
        read(b)
        return b
    }

    @Throws(IOException::class)
    fun readMac(): ByteArray {
        val b = ByteArray(SerializationConstants.Companion.TYPE_LEN_MAC)
        read(b)
        return b
    }

    @Throws(IOException::class)
    fun readBigInt(): BigInteger {
        val b = readData()
        return BigInteger(1, b)
    }

    @Throws(IOException::class)
    fun readData(): ByteArray {
        val dataLen = readNumber(SerializationConstants.Companion.DATA_LEN)
        val b = ByteArray(dataLen)
        read(b)
        return b
    }

    @Throws(IOException::class)
    fun readPublicKey(): PublicKey {
        val type = readShort()
        when (type) {
            0 -> {
                val p = readBigInt()
                val q = readBigInt()
                val g = readBigInt()
                val y = readBigInt()
                val keySpec = DSAPublicKeySpec(y, p, q, g)
                val keyFactory: KeyFactory
                keyFactory = try {
                    KeyFactory.getInstance("DSA")
                } catch (e: NoSuchAlgorithmException) {
                    throw IOException()
                }
                return try {
                    keyFactory.generatePublic(keySpec)
                } catch (e: InvalidKeySpecException) {
                    throw IOException()
                }
                throw UnsupportedOperationException()
            }
            else -> throw UnsupportedOperationException()
        }
    }

    @Throws(IOException::class)
    fun readDHPublicKey(): DHPublicKey? {
        val gyMpi = readBigInt()
        return try {
            OtrCryptoEngineImpl().getDHPublicKey(gyMpi)
        } catch (ex: Exception) {
            throw IOException()
        }
    }

    @Throws(IOException::class)
    fun readTlvData(): ByteArray {
        val len = readNumber(SerializationConstants.Companion.TYPE_LEN_SHORT)
        val b = ByteArray(len)
        `in`.read(b)
        return b
    }

    @Throws(IOException::class)
    fun readSignature(pubKey: PublicKey): ByteArray {
        if (pubKey.algorithm != "DSA") throw UnsupportedOperationException()
        val dsaPubKey = pubKey as DSAPublicKey
        val dsaParams = dsaPubKey.params
        val sig = ByteArray(dsaParams.q.bitLength() / 4)
        read(sig)
        return sig
    }

    @Throws(IOException::class)
    fun readMysteriousX(): SignatureX {
        val pubKey = readPublicKey()
        val dhKeyID = readInt()
        val sig = readSignature(pubKey)
        return SignatureX(pubKey, dhKeyID, sig)
    }
}