/*
 * otr4j, the open source java otr library.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.otr4j.crypto

import java.math.BigInteger
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.interfaces.DHPublicKey

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
interface OtrCryptoEngine {
    @Throws(OtrCryptoException::class)
    fun generateDHKeyPair(): KeyPair?

    @Throws(OtrCryptoException::class)
    fun getDHPublicKey(mpiBytes: ByteArray?): DHPublicKey

    @Throws(OtrCryptoException::class)
    fun getDHPublicKey(mpi: BigInteger?): DHPublicKey

    @Throws(OtrCryptoException::class)
    fun sha256Hmac(b: ByteArray?, key: ByteArray?): ByteArray

    @Throws(OtrCryptoException::class)
    fun sha256Hmac(b: ByteArray?, key: ByteArray?, length: Int): ByteArray

    @Throws(OtrCryptoException::class)
    fun sha1Hmac(b: ByteArray?, key: ByteArray?, length: Int): ByteArray

    @Throws(OtrCryptoException::class)
    fun sha256Hmac160(b: ByteArray?, key: ByteArray?): ByteArray

    @Throws(OtrCryptoException::class)
    fun sha256Hash(b: ByteArray?): ByteArray

    @Throws(OtrCryptoException::class)
    fun sha1Hash(b: ByteArray?): ByteArray

    @Throws(OtrCryptoException::class)
    fun aesDecrypt(key: ByteArray?, ctr: ByteArray?, b: ByteArray?): ByteArray

    @Throws(OtrCryptoException::class)
    fun aesEncrypt(key: ByteArray?, ctr: ByteArray?, b: ByteArray?): ByteArray

    @Throws(OtrCryptoException::class)
    fun generateSecret(privKey: PrivateKey?, pubKey: PublicKey?): BigInteger

    @Throws(OtrCryptoException::class)
    fun sign(b: ByteArray?, privatekey: PrivateKey?): ByteArray

    @Throws(OtrCryptoException::class)
    fun verify(b: ByteArray?, pubKey: PublicKey?, rs: ByteArray?): Boolean

    @Throws(OtrCryptoException::class)
    fun getFingerprint(pubKey: PublicKey?): String?

    @Throws(OtrCryptoException::class)
    fun getFingerprintRaw(pubKey: PublicKey?): ByteArray

    companion object {
        const val MODULUS_TEXT = "00FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA237327FFFFFFFFFFFFFFFF"
        val MODULUS = BigInteger(MODULUS_TEXT, 16)
        val BIGINTEGER_TWO = BigInteger.valueOf(2)
        val MODULUS_MINUS_TWO = MODULUS.subtract(BIGINTEGER_TWO)
        val GENERATOR = BigInteger("2", 10)
        const val AES_KEY_BYTE_LENGTH = 16
        const val AES_CTR_BYTE_LENGTH = 16
        const val SHA256_HMAC_KEY_BYTE_LENGTH = 32
        const val DH_PRIVATE_KEY_MINIMUM_BIT_LENGTH = 320
        const val DSA_PUB_TYPE = 0
        const val DSA_KEY_LENGTH = 1024
    }
}