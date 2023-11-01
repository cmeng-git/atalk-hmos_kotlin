/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.otr4j.io.messages

import net.java.otr4j.OtrException
import net.java.otr4j.crypto.OtrCryptoEngineImpl
import net.java.otr4j.io.SerializationUtils
import java.io.IOException
import java.util.*

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
open class SignatureMessage protected constructor(messageType: Int, protocolVersion: Int, var xEncrypted: ByteArray?,
                                                  var xEncryptedMAC: ByteArray?) : AbstractEncodedMessage(messageType, protocolVersion) {
    constructor(protocolVersion: Int, xEncrypted: ByteArray?, xEncryptedMAC: ByteArray?) : this(AbstractEncodedMessage.Companion.MESSAGE_SIGNATURE, protocolVersion, xEncrypted, xEncryptedMAC) {}

    @Throws(OtrException::class)
    fun decrypt(key: ByteArray?): ByteArray? {
        return OtrCryptoEngineImpl().aesDecrypt(key, null, xEncrypted)
    }

    @Throws(OtrException::class)
    fun verify(key: ByteArray?): Boolean {
        // Hash the key.
        val xbEncrypted: ByteArray?
        xbEncrypted = try {
            SerializationUtils.writeData(xEncrypted)
        } catch (e: IOException) {
            throw OtrException(e)
        }
        val xEncryptedMAC = OtrCryptoEngineImpl().sha256Hmac160(xbEncrypted, key)
        // Verify signature.
        return Arrays.equals(this.xEncryptedMAC, xEncryptedMAC)
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        result = prime * result + Arrays.hashCode(xEncrypted)
        result = prime * result + Arrays.hashCode(xEncryptedMAC)
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (!super.equals(obj)) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as SignatureMessage
        if (!Arrays.equals(xEncrypted, other.xEncrypted)) return false
        return if (!Arrays.equals(xEncryptedMAC, other.xEncryptedMAC)) false else true
    }
}