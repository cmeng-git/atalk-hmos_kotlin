package net.java.otr4j.io

import net.java.otr4j.io.messages.MysteriousT
import net.java.otr4j.io.messages.SignatureM
import net.java.otr4j.io.messages.SignatureX
import org.bouncycastle.util.BigIntegers
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.math.BigInteger
import java.security.PublicKey
import java.security.interfaces.DSAPublicKey
import javax.crypto.interfaces.DHPublicKey

class OtrOutputStream(out: OutputStream?) : FilterOutputStream(out), SerializationConstants {
    @Throws(IOException::class)
    private fun writeNumber(value: Int, length: Int) {
        val b = ByteArray(length)
        for (i in 0 until length) {
            val offset = (b.size - 1 - i) * 8
            b[i] = (value ushr offset and 0xFF).toByte()
        }
        write(b)
    }

    @Throws(IOException::class)
    fun writeBigInt(bi: BigInteger?) {
        val b = BigIntegers.asUnsignedByteArray(bi)
        writeData(b)
    }

    @Throws(IOException::class)
    fun writeByte(b: Int) {
        writeNumber(b, SerializationConstants.Companion.TYPE_LEN_BYTE)
    }

    @Throws(IOException::class)
    fun writeData(b: ByteArray?) {
        val len = b?.size ?: 0
        writeNumber(len, SerializationConstants.Companion.DATA_LEN)
        if (len > 0) write(b)
    }

    @Throws(IOException::class)
    fun writeInt(i: Int) {
        writeNumber(i, SerializationConstants.Companion.TYPE_LEN_INT)
    }

    @Throws(IOException::class)
    fun writeShort(s: Int) {
        writeNumber(s, SerializationConstants.Companion.TYPE_LEN_SHORT)
    }

    @Throws(IOException::class)
    fun writeMac(mac: ByteArray?) {
        require(!(mac == null || mac.size != SerializationConstants.Companion.TYPE_LEN_MAC))
        write(mac)
    }

    @Throws(IOException::class)
    fun writeCtr(ctr: ByteArray?) {
        if (ctr == null || ctr.size < 1) return
        var i = 0
        while (i < SerializationConstants.Companion.TYPE_LEN_CTR && i < ctr.size) {
            write(ctr[i].toInt())
            i++
        }
    }

    @Throws(IOException::class)
    fun writeDHPublicKey(dhPublicKey: DHPublicKey?) {
        val b = BigIntegers.asUnsignedByteArray(dhPublicKey!!.y)
        writeData(b)
    }

    @Throws(IOException::class)
    fun writePublicKey(pubKey: PublicKey?) {
        if (pubKey !is DSAPublicKey) throw UnsupportedOperationException(
                "Key types other than DSA are not supported at the moment.")
        val dsaKey = pubKey
        writeShort(0)
        val dsaParams = dsaKey.params
        writeBigInt(dsaParams.p)
        writeBigInt(dsaParams.q)
        writeBigInt(dsaParams.g)
        writeBigInt(dsaKey.y)
    }

    @Throws(IOException::class)
    fun writeTlvData(b: ByteArray?) {
        val len = b?.size ?: 0
        writeNumber(len, SerializationConstants.Companion.TLV_LEN)
        if (len > 0) write(b)
    }

    @Throws(IOException::class)
    fun writeSignature(signature: ByteArray?, pubKey: PublicKey?) {
        if (pubKey!!.algorithm != "DSA") throw UnsupportedOperationException()
        out.write(signature)
    }

    @Throws(IOException::class)
    fun writeMysteriousX(x: SignatureX) {
        writePublicKey(x.longTermPublicKey)
        writeInt(x.dhKeyID)
        writeSignature(x.signature, x.longTermPublicKey)
    }

    @Throws(IOException::class)
    fun writeMysteriousX(m: SignatureM) {
        writeBigInt(m.localPubKey!!.y)
        writeBigInt(m.remotePubKey!!.y)
        writePublicKey(m.localLongTermPubKey)
        writeInt(m.keyPairID)
    }

    @Throws(IOException::class)
    fun writeMysteriousT(t: MysteriousT) {
        writeShort(t.protocolVersion)
        writeByte(t.messageType)
        if (t.protocolVersion == 3) {
            writeInt(t.senderInstanceTag)
            writeInt(t.receiverInstanceTag)
        }
        writeByte(t.flags)
        writeInt(t.senderKeyID)
        writeInt(t.recipientKeyID)
        writeDHPublicKey(t.nextDH)
        writeCtr(t.ctr)
        writeData(t.encryptedMessage)
    }
}