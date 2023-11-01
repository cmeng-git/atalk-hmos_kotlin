/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.otr4j.crypto

import net.java.otr4j.io.SerializationUtils
import org.bouncycastle.crypto.BufferedBlockCipher
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.SICBlockCipher
import org.bouncycastle.crypto.params.*
import org.bouncycastle.crypto.signers.DSASigner
import org.bouncycastle.util.BigIntegers
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.*
import java.security.interfaces.DSAPrivateKey
import java.security.interfaces.DSAPublicKey
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.interfaces.DHPublicKey
import javax.crypto.spec.DHParameterSpec
import javax.crypto.spec.DHPublicKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
class OtrCryptoEngineImpl : OtrCryptoEngine {
    @Throws(OtrCryptoException::class)
    override fun generateDHKeyPair(): KeyPair? {
        return try {
            val keyPairGenerator = KeyPairGenerator.getInstance("DH")
            keyPairGenerator.initialize(DHParameterSpec(OtrCryptoEngine.Companion.MODULUS, OtrCryptoEngine.Companion.GENERATOR, OtrCryptoEngine.Companion.DH_PRIVATE_KEY_MINIMUM_BIT_LENGTH))
            keyPairGenerator.generateKeyPair()
        } catch (e: Exception) {
            throw OtrCryptoException(e)
        }
    }

    @Throws(OtrCryptoException::class)
    override fun getDHPublicKey(mpiBytes: ByteArray?): DHPublicKey {
        return getDHPublicKey(BigInteger(mpiBytes))
    }

    @Throws(OtrCryptoException::class)
    override fun getDHPublicKey(mpi: BigInteger?): DHPublicKey {
        val pubKeySpecs = DHPublicKeySpec(mpi, OtrCryptoEngine.Companion.MODULUS, OtrCryptoEngine.Companion.GENERATOR)
        return try {
            val keyFac = KeyFactory.getInstance("DH")
            keyFac.generatePublic(pubKeySpecs) as DHPublicKey
        } catch (e: Exception) {
            throw OtrCryptoException(e)
        }
    }

    @Throws(OtrCryptoException::class)
    override fun sha256Hmac(b: ByteArray?, key: ByteArray?): ByteArray {
        return this.sha256Hmac(b, key, 0)
    }

    @Throws(OtrCryptoException::class)
    override fun sha256Hmac(b: ByteArray?, key: ByteArray?, length: Int): ByteArray {
        val keyspec = SecretKeySpec(key, "HmacSHA256")
        val mac: Mac
        mac = try {
            Mac.getInstance("HmacSHA256")
        } catch (e: NoSuchAlgorithmException) {
            throw OtrCryptoException(e)
        }
        try {
            mac.init(keyspec)
        } catch (e: InvalidKeyException) {
            throw OtrCryptoException(e)
        }
        val macBytes = mac.doFinal(b)
        return if (length > 0) {
            val bytes = ByteArray(length)
            val buff = ByteBuffer.wrap(macBytes)
            buff[bytes]
            bytes
        } else {
            macBytes
        }
    }

    @Throws(OtrCryptoException::class)
    override fun sha1Hmac(b: ByteArray?, key: ByteArray?, length: Int): ByteArray {
        return try {
            val keyspec = SecretKeySpec(key, "HmacSHA1")
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(keyspec)
            val macBytes = mac.doFinal(b)
            if (length > 0) {
                val bytes = ByteArray(length)
                val buff = ByteBuffer.wrap(macBytes)
                buff[bytes]
                bytes
            } else {
                macBytes
            }
        } catch (e: Exception) {
            throw OtrCryptoException(e)
        }
    }

    @Throws(OtrCryptoException::class)
    override fun sha256Hmac160(b: ByteArray?, key: ByteArray?): ByteArray {
        return sha256Hmac(b, key, 20)
    }

    @Throws(OtrCryptoException::class)
    override fun sha256Hash(b: ByteArray?): ByteArray {
        return try {
            val sha256 = MessageDigest.getInstance("SHA-256")
            sha256.update(b, 0, b!!.size)
            sha256.digest()
        } catch (e: Exception) {
            throw OtrCryptoException(e)
        }
    }

    @Throws(OtrCryptoException::class)
    override fun sha1Hash(b: ByteArray?): ByteArray {
        return try {
            val sha256 = MessageDigest.getInstance("SHA-1")
            sha256.update(b, 0, b!!.size)
            sha256.digest()
        } catch (e: Exception) {
            throw OtrCryptoException(e)
        }
    }

    @Throws(OtrCryptoException::class)
    override fun aesDecrypt(key: ByteArray?, ctr: ByteArray?, b: ByteArray?): ByteArray {
        var ctr = ctr
        val aesDec = AESEngine()
        val sicAesDec = SICBlockCipher(aesDec)
        val bufSicAesDec = BufferedBlockCipher(sicAesDec)

        // Create initial counter value 0.
        if (ctr == null) ctr = ByteArray(OtrCryptoEngine.Companion.AES_CTR_BYTE_LENGTH)
        bufSicAesDec.init(false, ParametersWithIV(KeyParameter(key), ctr))
        val aesOutLwDec = ByteArray(b!!.size)
        val done = bufSicAesDec.processBytes(b, 0, b.size, aesOutLwDec, 0)
        try {
            bufSicAesDec.doFinal(aesOutLwDec, done)
        } catch (e: Exception) {
            throw OtrCryptoException(e)
        }
        return aesOutLwDec
    }

    @Throws(OtrCryptoException::class)
    override fun aesEncrypt(key: ByteArray?, ctr: ByteArray?, b: ByteArray?): ByteArray {
        var ctr = ctr
        val aesEnc = AESEngine()
        val sicAesEnc = SICBlockCipher(aesEnc)
        val bufSicAesEnc = BufferedBlockCipher(sicAesEnc)

        // Create initial counter value 0.
        if (ctr == null) ctr = ByteArray(OtrCryptoEngine.Companion.AES_CTR_BYTE_LENGTH)
        bufSicAesEnc.init(true, ParametersWithIV(KeyParameter(key), ctr))
        val aesOutLwEnc = ByteArray(b!!.size)
        val done = bufSicAesEnc.processBytes(b, 0, b.size, aesOutLwEnc, 0)
        try {
            bufSicAesEnc.doFinal(aesOutLwEnc, done)
        } catch (e: Exception) {
            throw OtrCryptoException(e)
        }
        return aesOutLwEnc
    }

    @Throws(OtrCryptoException::class)
    override fun generateSecret(privKey: PrivateKey?, pubKey: PublicKey?): BigInteger {
        return try {
            val ka = KeyAgreement.getInstance("DH")
            ka.init(privKey)
            ka.doPhase(pubKey, true)
            val sb = ka.generateSecret()
            BigInteger(1, sb)
        } catch (e: Exception) {
            throw OtrCryptoException(e)
        }
    }

    @Throws(OtrCryptoException::class)
    override fun sign(b: ByteArray?, privatekey: PrivateKey?): ByteArray {
        require(privatekey is DSAPrivateKey)
        val dsaParams = privatekey.params
        val bcDSAParameters = DSAParameters(dsaParams.p, dsaParams.q, dsaParams.g)
        val bcDSAPrivateKeyParms = DSAPrivateKeyParameters(privatekey.x, bcDSAParameters)
        val dsaSigner = DSASigner()
        dsaSigner.init(true, bcDSAPrivateKeyParms)
        val q = dsaParams.q

        // Ian: Note that if you can get the standard DSA implementation you're
        // using to not hash its input, you should be able to pass it ((256-bit
        // value) mod q), (rather than truncating the 256-bit value) and all
        // should be well.
        // ref: Interop problems with libotr - DSA signature
        val bmpi = BigInteger(1, b)
        val rs = dsaSigner.generateSignature(BigIntegers.asUnsignedByteArray(bmpi.mod(q)))
        val siglen = q.bitLength() / 4
        val rslen = siglen / 2
        val rb = BigIntegers.asUnsignedByteArray(rs[0])
        val sb = BigIntegers.asUnsignedByteArray(rs[1])

        // Create the final signature array, padded with zeros if necessary.
        val sig = ByteArray(siglen)
        System.arraycopy(rb, 0, sig, rslen - rb.size, rb.size)
        System.arraycopy(sb, 0, sig, sig.size - sb.size, sb.size)
        return sig
    }

    @Throws(OtrCryptoException::class)
    override fun verify(b: ByteArray?, pubKey: PublicKey?, rs: ByteArray?): Boolean {
        require(pubKey is DSAPublicKey)
        val dsaParams = pubKey.params
        val qlen = dsaParams.q.bitLength() / 8
        val buff = ByteBuffer.wrap(rs)
        val r = ByteArray(qlen)
        buff[r]
        val s = ByteArray(qlen)
        buff[s]
        return verify(b, pubKey, r, s)
    }

    @Throws(OtrCryptoException::class)
    private fun verify(b: ByteArray?, pubKey: PublicKey, r: ByteArray, s: ByteArray): Boolean {
        return verify(b, pubKey, BigInteger(1, r), BigInteger(1, s))
    }

    @Throws(OtrCryptoException::class)
    private fun verify(b: ByteArray?, pubKey: PublicKey, r: BigInteger, s: BigInteger): Boolean {
        require(pubKey is DSAPublicKey)
        val dsaParams = pubKey.params
        val q = dsaParams.q
        val bcDSAParams = DSAParameters(dsaParams.p, q, dsaParams.g)
        val dsaPrivParms = DSAPublicKeyParameters(pubKey.y, bcDSAParams)

        // Ian: Note that if you can get the standard DSA implementation you're using to not hash
        // its input, you should be able to pass it ((256-bit value) mod q), (rather than
        // truncating the 256-bit value) and all should be well.
        // ref: Interop problems with libotr - DSA signature
        val dsaSigner = DSASigner()
        dsaSigner.init(false, dsaPrivParms)
        val bmpi = BigInteger(1, b)
        return dsaSigner.verifySignature(BigIntegers.asUnsignedByteArray(bmpi.mod(q)), r, s)
    }

    @Throws(OtrCryptoException::class)
    override fun getFingerprint(pubKey: PublicKey?): String? {
        val b = getFingerprintRaw(pubKey)
        return SerializationUtils.byteArrayToHexString(b)
    }

    @Throws(OtrCryptoException::class)
    override fun getFingerprintRaw(pubKey: PublicKey?): ByteArray {
        val b: ByteArray
        b = try {
            val bRemotePubKey = SerializationUtils.writePublicKey(pubKey)
            if (pubKey!!.algorithm == "DSA") {
                val trimmed = ByteArray(bRemotePubKey!!.size - 2)
                System.arraycopy(bRemotePubKey, 2, trimmed, 0, trimmed.size)
                OtrCryptoEngineImpl().sha1Hash(trimmed)
            } else OtrCryptoEngineImpl().sha1Hash(bRemotePubKey)
        } catch (e: IOException) {
            throw OtrCryptoException(e)
        }
        return b
    }
}