/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.otr4j.io

import net.java.otr4j.io.messages.*
import net.java.otr4j.session.Session.OTRv
import org.bouncycastle.util.encoders.Base64
import java.io.*
import java.math.BigInteger
import java.security.PublicKey
import java.util.*
import java.util.regex.Pattern

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
object SerializationUtils {
    private val PATTERN_WHITESPACE = Pattern.compile("( \\t  \\t\\t\\t\\t \\t \\t \\t  )( \\t \\t  \\t )?(  \\t\\t  \\t )?(  \\t\\t  \\t\\t)?")
    private val HEX_ENCODER = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
    private const val HEX_DECODER = "0123456789ABCDEF"

    // Mysterious X IO.
    @Throws(IOException::class)
    fun toMysteriousX(b: ByteArray?): SignatureX {
        val `in` = ByteArrayInputStream(b)
        val ois = OtrInputStream(`in`)
        val x = ois.readMysteriousX()
        ois.close()
        return x
    }

    @Throws(IOException::class)
    fun toByteArray(x: SignatureX): ByteArray {
        val out = ByteArrayOutputStream()
        val oos = OtrOutputStream(out)
        oos.writeMysteriousX(x)
        val b = out.toByteArray()
        oos.close()
        return b
    }

    // Mysterious M IO.
    @Throws(IOException::class)
    fun toByteArray(m: SignatureM): ByteArray {
        val out = ByteArrayOutputStream()
        val oos = OtrOutputStream(out)
        oos.writeMysteriousX(m)
        val b = out.toByteArray()
        oos.close()
        return b
    }

    // Mysterious T IO.
    @Throws(IOException::class)
    fun toByteArray(t: MysteriousT): ByteArray {
        val out = ByteArrayOutputStream()
        val oos = OtrOutputStream(out)
        oos.writeMysteriousT(t)
        val b = out.toByteArray()
        oos.close()
        return b
    }

    // Basic IO.
    @Throws(IOException::class)
    fun writeData(b: ByteArray?): ByteArray {
        val out = ByteArrayOutputStream()
        val oos = OtrOutputStream(out)
        oos.writeData(b)
        val otrb = out.toByteArray()
        oos.close()
        return otrb
    }

    // BigInteger IO.
    @Throws(IOException::class)
    fun writeMpi(bigInt: BigInteger?): ByteArray {
        val out = ByteArrayOutputStream()
        val oos = OtrOutputStream(out)
        oos.writeBigInt(bigInt)
        val b = out.toByteArray()
        oos.close()
        return b
    }

    @Throws(IOException::class)
    fun readMpi(b: ByteArray?): BigInteger {
        val `in` = ByteArrayInputStream(b)
        val ois = OtrInputStream(`in`)
        val bigint = ois.readBigInt()
        ois.close()
        return bigint
    }

    // Public Key IO.
    @Throws(IOException::class)
    fun writePublicKey(pubKey: PublicKey?): ByteArray {
        val out = ByteArrayOutputStream()
        val oos = OtrOutputStream(out)
        oos.writePublicKey(pubKey)
        val b = out.toByteArray()
        oos.close()
        return b
    }

    // Message IO.
    @Throws(IOException::class)
    fun toString(m: AbstractMessage?): String {
        val writer = StringWriter()
        if (m!!.messageType != AbstractMessage.MESSAGE_PLAINTEXT) writer.write(SerializationConstants.HEAD)
        when (m.messageType) {
            AbstractMessage.MESSAGE_ERROR -> {
                val error = m as ErrorMessage?
                writer.write(SerializationConstants.HEAD_ERROR.code)
                writer.write(SerializationConstants.ERROR_PREFIX)
                writer.write(error!!.error)
            }
            AbstractMessage.MESSAGE_PLAINTEXT -> {
                val plaintxt = m as PlainTextMessage?
                writer.write(plaintxt!!.cleanText)
                if (plaintxt.versions != null && plaintxt.versions!!.isNotEmpty()) {
                    writer.write(" \t  \t\t\t\t \t \t \t  ")
                    for (version in plaintxt.versions!!) {
                        if (version == OTRv.ONE) writer.write(" \t \t  \t ")
                        if (version == OTRv.TWO) writer.write("  \t\t  \t ")
                        if (version == OTRv.THREE) writer.write("  \t\t  \t\t")
                    }
                }
            }
            AbstractMessage.MESSAGE_QUERY -> {
                val query = m as QueryMessage?
                if (query!!.versions!!.size == 1 && query.versions!![0] == 1) {
                    writer.write(SerializationConstants.HEAD_QUERY_Q.code)
                } else {
                    writer.write(SerializationConstants.HEAD_QUERY_V.code)
                    for (version in query.versions!!) writer.write(version.toString())
                    writer.write(SerializationConstants.HEAD_QUERY_Q.code)
                }
            }
            AbstractEncodedMessage.MESSAGE_DHKEY, AbstractEncodedMessage.MESSAGE_REVEALSIG, AbstractEncodedMessage.MESSAGE_SIGNATURE, AbstractEncodedMessage.MESSAGE_DH_COMMIT, AbstractEncodedMessage.MESSAGE_DATA -> {
                val o = ByteArrayOutputStream()
                val s = OtrOutputStream(o)
                when (m.messageType) {
                    AbstractEncodedMessage.MESSAGE_DHKEY -> {
                        val dhkey = m as DHKeyMessage?
                        s.writeShort(dhkey!!.protocolVersion)
                        s.writeByte(dhkey.messageType)
                        if (dhkey.protocolVersion == OTRv.THREE) {
                            s.writeInt(dhkey.senderInstanceTag)
                            s.writeInt(dhkey.receiverInstanceTag)
                        }
                        s.writeDHPublicKey(dhkey.dhPublicKey)
                    }
                    AbstractEncodedMessage.MESSAGE_REVEALSIG -> {
                        val revealsig = m as RevealSignatureMessage?
                        s.writeShort(revealsig!!.protocolVersion)
                        s.writeByte(revealsig.messageType)
                        if (revealsig.protocolVersion == OTRv.THREE) {
                            s.writeInt(revealsig.senderInstanceTag)
                            s.writeInt(revealsig.receiverInstanceTag)
                        }
                        s.writeData(revealsig.revealedKey)
                        s.writeData(revealsig.xEncrypted)
                        s.writeMac(revealsig.xEncryptedMAC)
                    }
                    AbstractEncodedMessage.MESSAGE_SIGNATURE -> {
                        val sig = m as SignatureMessage?
                        s.writeShort(sig!!.protocolVersion)
                        s.writeByte(sig.messageType)
                        if (sig.protocolVersion == OTRv.THREE) {
                            s.writeInt(sig.senderInstanceTag)
                            s.writeInt(sig.receiverInstanceTag)
                        }
                        s.writeData(sig.xEncrypted)
                        s.writeMac(sig.xEncryptedMAC)
                    }
                    AbstractEncodedMessage.MESSAGE_DH_COMMIT -> {
                        val dhcommit = m as DHCommitMessage?
                        s.writeShort(dhcommit!!.protocolVersion)
                        s.writeByte(dhcommit.messageType)
                        if (dhcommit.protocolVersion == OTRv.THREE) {
                            s.writeInt(dhcommit.senderInstanceTag)
                            s.writeInt(dhcommit.receiverInstanceTag)
                        }
                        s.writeData(dhcommit.dhPublicKeyEncrypted)
                        s.writeData(dhcommit.dhPublicKeyHash)
                    }
                    AbstractEncodedMessage.MESSAGE_DATA -> {
                        val data = m as DataMessage?
                        s.writeShort(data!!.protocolVersion)
                        s.writeByte(data.messageType)
                        if (data.protocolVersion == OTRv.THREE) {
                            s.writeInt(data.senderInstanceTag)
                            s.writeInt(data.receiverInstanceTag)
                        }
                        s.writeByte(data.flags)
                        s.writeInt(data.senderKeyID)
                        s.writeInt(data.recipientKeyID)
                        s.writeDHPublicKey(data.nextDH)
                        s.writeCtr(data.ctr)
                        s.writeData(data.encryptedMessage)
                        s.writeMac(data.mac)
                        s.writeData(data.oldMACKeys)
                    }
                    else -> throw UnsupportedOperationException("Unsupported message type: " + m.messageType)
                }
                writer.write(SerializationConstants.HEAD_ENCODED.code)
                writer.write(String(Base64.encode(o.toByteArray())))
                writer.write(".")
            }
            else -> throw IOException("Illegal message type.")
        }
        return writer.toString()
    }

    @Throws(IOException::class)
    fun toMessage(string: String?): AbstractMessage? {
        if (string == null || string.isEmpty()) return null
        val idxHead = string.indexOf(SerializationConstants.HEAD)
        if (idxHead > -1) {
            // Message **contains** the string "?OTR". Check to see if it is an error message,
            // a query message or a data message.
            val contentType = string[idxHead + SerializationConstants.HEAD.length]
            var content = string.substring(idxHead + SerializationConstants.HEAD.length + 1)
            if (contentType == SerializationConstants.HEAD_ERROR
                    && content.startsWith(SerializationConstants.ERROR_PREFIX)) {
                // Error tag found.
                content = content.substring(idxHead + SerializationConstants.ERROR_PREFIX.length)
                return ErrorMessage(AbstractMessage.MESSAGE_ERROR, content)
            } else if (contentType == SerializationConstants.HEAD_QUERY_V
                    || contentType == SerializationConstants.HEAD_QUERY_Q) {
                // Query tag found.
                val versions = ArrayList<Int?>()
                var versionString: String? = null
                if (SerializationConstants.HEAD_QUERY_Q == contentType) {
                    versions.add(OTRv.ONE)
                    if (content[0] == 'v') {
                        versionString = content.substring(1, content.indexOf('?'))
                    }
                } else if (SerializationConstants.HEAD_QUERY_V == contentType) {
                    versionString = content.substring(0, content.indexOf('?'))
                }
                if (versionString != null) {
                    val sr = StringReader(versionString)
                    var c: Int
                    while (sr.read().also { c = it } != -1) {
                        if (!versions.contains(c)) versions.add(c.toChar().toString().toInt())
                    }
                }
                return QueryMessage(versions)
            } else if (idxHead == 0 && contentType == SerializationConstants.HEAD_ENCODED) {
                // Data message found.
                /*
                 * BC 1.48 added a check to throw an exception if a non-base64 character is
                 * encountered. An OTR message consists of ?OTR:AbcDefFe. (note the terminating
                 * point). Otr4j doesn't strip this point before passing the content to the
                 * base64 decoder. So in order to decode the content string we have to get
                 * rid of the '.' first.
                 */
                val bin = ByteArrayInputStream(
                        Base64.decode(content.substring(0, content.length - 1).toByteArray()))
                val otr = OtrInputStream(bin)
                // We have an encoded message.
                val protocolVersion = otr.readShort()
                val messageType = otr.readByte()
                var senderInstanceTag = 0
                var recipientInstanceTag = 0
                if (protocolVersion == OTRv.THREE) {
                    senderInstanceTag = otr.readInt()
                    recipientInstanceTag = otr.readInt()
                }
                return when (messageType) {
                    AbstractEncodedMessage.MESSAGE_DATA -> {
                        val flags = otr.readByte()
                        val senderKeyID = otr.readInt()
                        val recipientKeyID = otr.readInt()
                        val nextDH = otr.readDHPublicKey()
                        val ctr = otr.readCtr()
                        val encryptedMessage = otr.readData()
                        val mac = otr.readMac()
                        val oldMacKeys = otr.readMac()
                        val dataMessage = DataMessage(protocolVersion, flags,
                                senderKeyID, recipientKeyID, nextDH, ctr, encryptedMessage, mac, oldMacKeys)
                        dataMessage.senderInstanceTag = senderInstanceTag
                        dataMessage.receiverInstanceTag = recipientInstanceTag
                        otr.close()
                        dataMessage
                    }
                    AbstractEncodedMessage.MESSAGE_DH_COMMIT -> {
                        val dhPublicKeyEncrypted = otr.readData()
                        val dhPublicKeyHash = otr.readData()
                        val dhCommitMessage = DHCommitMessage(protocolVersion,
                                dhPublicKeyHash, dhPublicKeyEncrypted)
                        dhCommitMessage.senderInstanceTag = senderInstanceTag
                        dhCommitMessage.receiverInstanceTag = recipientInstanceTag
                        otr.close()
                        dhCommitMessage
                    }
                    AbstractEncodedMessage.MESSAGE_DHKEY -> {
                        val dhPublicKey = otr.readDHPublicKey()
                        val dhKeyMessage = DHKeyMessage(protocolVersion, dhPublicKey)
                        dhKeyMessage.senderInstanceTag = senderInstanceTag
                        dhKeyMessage.receiverInstanceTag = recipientInstanceTag
                        otr.close()
                        dhKeyMessage
                    }
                    AbstractEncodedMessage.MESSAGE_REVEALSIG -> {
                        val revealedKey = otr.readData()
                        val xEncrypted = otr.readData()
                        val xEncryptedMac = otr.readMac()
                        val revealSignatureMessage = RevealSignatureMessage(protocolVersion, xEncrypted, xEncryptedMac, revealedKey)
                        revealSignatureMessage.senderInstanceTag = senderInstanceTag
                        revealSignatureMessage.receiverInstanceTag = recipientInstanceTag
                        otr.close()
                        revealSignatureMessage
                    }
                    AbstractEncodedMessage.MESSAGE_SIGNATURE -> {
                        val xEncryted = otr.readData()
                        val xEncryptedMac = otr.readMac()
                        val signatureMessage = SignatureMessage(protocolVersion, xEncryted, xEncryptedMac)
                        signatureMessage.senderInstanceTag = senderInstanceTag
                        signatureMessage.receiverInstanceTag = recipientInstanceTag
                        otr.close()
                        signatureMessage
                    }
                    else -> {
                        // NOTE by gp: aren't we being a little too harsh here? Passing the message
                        // as a plaintext message to the host application shouldn't hurt anybody.
                        otr.close()
                        throw IOException("Illegal message type.")
                    }
                }
            }
        }

        // Try to detect whitespace tag.
        val matcher = PATTERN_WHITESPACE.matcher(string)
        var v1 = false
        var v2 = false
        var v3 = false
        while (matcher.find()) {
            if (!v1 && matcher.start(2) > -1) v1 = true
            if (!v2 && matcher.start(3) > -1) v2 = true
            if (!v3 && matcher.start(3) > -1) v3 = true
            if (v1 && v2 && v3) break
        }
        val cleanText = matcher.replaceAll("")
        var versions: MutableList<Int?>? = null
        if (v1 || v2 || v3) {
            versions = ArrayList()
            if (v1) versions.add(OTRv.ONE)
            if (v2) versions.add(OTRv.TWO)
            if (v3) versions.add(OTRv.THREE)
        }
        return PlainTextMessage(versions, cleanText)
    }

    fun byteArrayToHexString(bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        val out = StringBuilder(bytes.size * 2)
        for (anIn in bytes) {
            out.append(HEX_ENCODER[anIn.toInt() ushr 4 and 0x0F])
            out.append(HEX_ENCODER[anIn.toInt() and 0x0F])
        }
        return out.toString()
    }

    fun hexStringToByteArray(value: String): ByteArray {
        var bValue = value
        bValue = bValue.uppercase(Locale.getDefault())
        val out = ByteArrayOutputStream()
        var index = 0
        while (index < bValue.length) {
            val high = HEX_DECODER.indexOf(bValue[index])
            val low = HEX_DECODER.indexOf(bValue[index + 1])
            out.write((high shl 4) + low)
            index += 2
        }
        return out.toByteArray()
    }

    /**
     * Check whether the provided content is OTR encoded.
     *
     * @param content the content to investigate
     * @return returns true if content is OTR encoded, or false otherwise
     */
    fun otrEncoded(content: String?): Boolean {
        return content!!.startsWith(
                SerializationConstants.HEAD + SerializationConstants.HEAD_ENCODED)
    }
}