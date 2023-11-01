/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.otr4j.session

import android.text.TextUtils
import net.java.otr4j.OtrEngineHost
import net.java.otr4j.OtrEngineListener
import net.java.otr4j.OtrException
import net.java.otr4j.OtrPolicy
import net.java.otr4j.crypto.OtrCryptoEngine
import net.java.otr4j.crypto.OtrCryptoEngineImpl
import net.java.otr4j.io.OtrInputStream
import net.java.otr4j.io.OtrOutputStream
import net.java.otr4j.io.SerializationConstants
import net.java.otr4j.io.SerializationUtils
import net.java.otr4j.io.messages.AbstractEncodedMessage
import net.java.otr4j.io.messages.AbstractMessage
import net.java.otr4j.io.messages.DHCommitMessage
import net.java.otr4j.io.messages.DataMessage
import net.java.otr4j.io.messages.ErrorMessage
import net.java.otr4j.io.messages.MysteriousT
import net.java.otr4j.io.messages.PlainTextMessage
import net.java.otr4j.io.messages.QueryMessage
import net.java.otr4j.util.SelectableMap
import net.java.sip.communicator.plugin.otr.OtrActivator
import net.java.sip.communicator.plugin.otr.OtrContactManager
import net.java.sip.communicator.plugin.otr.OtrContactManager.OtrContact
import net.java.sip.communicator.plugin.otr.ScOtrEngineImpl
import net.java.sip.communicator.service.gui.Chat
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.ChatPanel
import org.atalk.hmos.plugin.timberlog.TimberLog
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.ProtocolException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.PublicKey
import java.util.*
import javax.crypto.interfaces.DHPublicKey

/**
 * @author George Politis
 * @author Danny van Heumen
 * @author Eng Chong Meng
 */
class SessionImpl : Session {
    private val slaveSessions: SelectableMap<InstanceTag, SessionImpl>
    private val isMasterSession: Boolean
    override var sessionID: SessionID? = null
        private set
    private var host: OtrEngineHost? = null

    private var authContext: AuthContext? = null
        get() {
            if (field == null) field = AuthContextImpl(this)
            return field
        }
    private var sessionKeys: Array<Array<SessionKeys?>>? = null
        get() {
            if (field == null) field = Array(2) { arrayOfNulls(2) }
            return field
        }
    private var oldMacKeys: MutableList<ByteArray?>? = null
        get() {
            if (field == null) field = ArrayList()
            return field
        }
    private val otrSm: OtrSm
    override var s: BigInteger? = null
        private set

    private var offerStatus: OfferStatus
    override val senderInstanceTag: InstanceTag
    override var receiverInstanceTag: InstanceTag
        set(receiverInstanceTag) {
            // ReceiverInstanceTag of a slave session is not supposed to change
            if (!isMasterSession) return
            field = receiverInstanceTag
        }

    override var protocolVersion = 0
        get() {
            return if (isMasterSession) field else Session.OTRv.THREE
        }
        set(protocolVersion) {
            // Protocol version of a slave session is not supposed to change
            if (!isMasterSession) return
            field = protocolVersion
        }

    private val assembler: OtrAssembler
    private val fragmenter: OtrFragmenter
    private val listeners = ArrayList<OtrEngineListener>()

    override var remotePublicKey: PublicKey? = null
        get() {
            return if (slaveSessions.isSelected && protocolVersion == Session.OTRv.THREE) slaveSessions.getSelected()!!.remotePublicKey else field
        }

    override fun getRemotePublicKey(tag: InstanceTag): PublicKey {
        return if (tag == receiverInstanceTag) remotePublicKey!! else {
            val slave = slaveSessions[tag]
            if (slave != null) slave.remotePublicKey!! else remotePublicKey!!
        }
    }

    constructor(sessionID: SessionID?, listener: OtrEngineHost?) {
        this.sessionID = sessionID
        host = listener

        // client application calls OtrSessionManager.getSessionStatus()
        // -> create new session if it does not exist, end up here
        // -> setSessionStatus() fires statusChangedEvent
        // -> client application calls OtrSessionManager.getSessionStatus()
        sessionStatus = SessionStatus.PLAINTEXT
        offerStatus = OfferStatus.idle
        otrSm = OtrSm(this, listener)
        senderInstanceTag = InstanceTag()
        receiverInstanceTag = InstanceTag.ZERO_TAG
        slaveSessions = SelectableMap(HashMap<InstanceTag, SessionImpl>())
        isMasterSession = true
        assembler = OtrAssembler(senderInstanceTag)
        fragmenter = OtrFragmenter(this, listener)
    }

    // A private constructor for instantiating 'slave' sessions.
    private constructor(sessionID: SessionID?, listener: OtrEngineHost?, senderTag: InstanceTag, receiverInstanceTag: InstanceTag) {
        this.sessionID = sessionID
        host = listener
        sessionStatus = SessionStatus.PLAINTEXT
        offerStatus = OfferStatus.idle
        otrSm = OtrSm(this, listener)
        senderInstanceTag = senderTag
        this.receiverInstanceTag = receiverInstanceTag
        slaveSessions = SelectableMap(emptyMap<InstanceTag, SessionImpl>() as MutableMap<InstanceTag, SessionImpl>)
        isMasterSession = false
        protocolVersion = Session.OTRv.THREE
        assembler = OtrAssembler(senderInstanceTag)
        fragmenter = OtrFragmenter(this, listener)
    }

    private val encryptionSessionKeys: SessionKeys
        get() {
            Timber.log(TimberLog.FINER, "Getting encryption keys")
            return getSessionKeysByIndex(SessionKeys.Previous, SessionKeys.Current)
        }

    private val mostRecentSessionKeys: SessionKeys
        get() {
            Timber.log(TimberLog.FINER, "Getting most recent keys.")
            return getSessionKeysByIndex(SessionKeys.Current, SessionKeys.Current)
        }

    private fun getSessionKeysByID(localKeyID: Int, remoteKeyID: Int): SessionKeys? {
        Timber.log(TimberLog.FINER, "Searching for session keys with (localKeyID, remoteKeyID) = (%s, %s)", localKeyID, remoteKeyID)
        for (i in sessionKeys!!.indices) {
            for (j in sessionKeys!![i].indices) {
                val current = getSessionKeysByIndex(i, j)
                if (current.localKeyID == localKeyID && current.remoteKeyID == remoteKeyID) {
                    Timber.log(TimberLog.FINER, "Matching keys found.")
                    return current
                }
            }
        }
        return null
    }

    private fun getSessionKeysByIndex(localKeyIndex: Int, remoteKeyIndex: Int): SessionKeys {
        if (sessionKeys!![localKeyIndex][remoteKeyIndex] == null) sessionKeys!![localKeyIndex][remoteKeyIndex] = SessionKeysImpl(localKeyIndex, remoteKeyIndex)
        return sessionKeys!![localKeyIndex][remoteKeyIndex]!!
    }

    @Throws(OtrException::class)
    private fun rotateRemoteSessionKeys(pubKey: DHPublicKey) {
        Timber.log(TimberLog.FINER, "Rotating remote keys.")
        val sess1 = getSessionKeysByIndex(SessionKeys.Current, SessionKeys.Previous)
        if (sess1.isUsedReceivingMACKey) {
            Timber.log(TimberLog.FINER, "Detected used Receiving MAC key. Adding to old MAC keys to reveal it.")
            oldMacKeys!!.add(sess1.receivingMACKey)
        }
        val sess2 = getSessionKeysByIndex(SessionKeys.Previous, SessionKeys.Previous)
        if (sess2.isUsedReceivingMACKey) {
            Timber.log(TimberLog.FINER, "Detected used Receiving MAC key. Adding to old MAC keys to reveal it.")
            oldMacKeys!!.add(sess2.receivingMACKey)
        }
        val sess3 = getSessionKeysByIndex(SessionKeys.Current, SessionKeys.Current)
        sess1.setRemoteDHPublicKey(sess3.remoteKey, sess3.remoteKeyID)
        val sess4 = getSessionKeysByIndex(SessionKeys.Previous, SessionKeys.Current)
        sess2.setRemoteDHPublicKey(sess4.remoteKey, sess4.remoteKeyID)
        sess3.setRemoteDHPublicKey(pubKey, sess3.remoteKeyID + 1)
        sess4.setRemoteDHPublicKey(pubKey, sess4.remoteKeyID + 1)
    }

    @Throws(OtrException::class)
    private fun rotateLocalSessionKeys() {
        Timber.log(TimberLog.FINER, "Rotating local keys.")
        val sess1 = getSessionKeysByIndex(SessionKeys.Previous, SessionKeys.Current)
        if (sess1.isUsedReceivingMACKey) {
            Timber.log(TimberLog.FINER, "Detected used Receiving MAC key. Adding to old MAC keys to reveal it.")
            oldMacKeys!!.add(sess1.receivingMACKey)
        }
        val sess2 = getSessionKeysByIndex(SessionKeys.Previous, SessionKeys.Previous)
        if (sess2.isUsedReceivingMACKey) {
            Timber.log(TimberLog.FINER, "Detected used Receiving MAC key. Adding to old MAC keys to reveal it.")
            oldMacKeys!!.add(sess2.receivingMACKey)
        }
        val sess3 = getSessionKeysByIndex(SessionKeys.Current, SessionKeys.Current)
        sess1.setLocalPair(sess3.localPair, sess3.localKeyID)
        val sess4 = getSessionKeysByIndex(SessionKeys.Current, SessionKeys.Previous)
        sess2.setLocalPair(sess4.localPair, sess4.localKeyID)
        val newPair = OtrCryptoEngineImpl().generateDHKeyPair()
        sess3.setLocalPair(newPair, sess3.localKeyID + 1)
        sess4.setLocalPair(newPair, sess4.localKeyID + 1)
    }

    private fun collectOldMacKeys(): ByteArray {
        Timber.log(TimberLog.FINER, "Collecting old MAC keys to be revealed.")
        var len = 0
        for (i in oldMacKeys!!.indices) len += oldMacKeys!![i]!!.size
        val buff = ByteBuffer.allocate(len)
        for (i in oldMacKeys!!.indices) buff.put(oldMacKeys!![i]!!)
        oldMacKeys!!.clear()
        return buff.array()
    }

    @Throws(OtrException::class)
    override fun transformReceiving(content: String?): String? {
        var msgText = content
        if (TextUtils.isEmpty(msgText)) return null
        val policy = sessionPolicy
        if (!policy.allowV1 && !policy.allowV2 && !policy.allowV3) {
            Timber.log(TimberLog.FINER, "Policy does not allow neither V1 nor V2 & V3, ignoring message.")
            return msgText
        }
        msgText = try {
            assembler.accumulate(msgText)
        } catch (e: UnknownInstanceException) {
            // The fragment is not intended for us
            Timber.log(TimberLog.FINER, "%s", e.message)
            host!!.messageFromAnotherInstanceReceived(sessionID)
            return null
        } catch (e: ProtocolException) {
            Timber.w("An invalid message fragment was discarded.")
            return null
        }
        if (msgText == null) return null // Not a complete message (yet).
        val m = try {
            SerializationUtils.toMessage(msgText)
        } catch (e: IOException) {
            throw OtrException(e)
        } ?: return msgText

        // Probably null or empty.
        if (m.messageType != AbstractMessage.MESSAGE_PLAINTEXT) offerStatus = OfferStatus.accepted else if (offerStatus == OfferStatus.sent) offerStatus = OfferStatus.rejected
        if (m is AbstractEncodedMessage && isMasterSession) {
            val encodedM = m
            if (encodedM.protocolVersion == Session.OTRv.THREE) {
                if (encodedM.receiverInstanceTag != senderInstanceTag.value
                        && !(encodedM.messageType == AbstractEncodedMessage.MESSAGE_DH_COMMIT
                                && encodedM.receiverInstanceTag == 0)) {
                    // The message is not intended for us. Discarding...
                    Timber.log(TimberLog.FINER, "Received an encoded message with receiver instance tag"
                            + " that is different from ours, ignore this message")
                    host!!.messageFromAnotherInstanceReceived(sessionID)
                    return null
                }
            }
            if (encodedM.senderInstanceTag != receiverInstanceTag.value
                    && receiverInstanceTag.value != 0) {
                // Message is intended for us but is coming from a different instance.
                // We relay this message to the appropriate session for transforming.
                Timber.log(TimberLog.FINER, "Received an encoded message from a different instance. "
                        + "Our buddy may  be logged from multiple locations.")
                val newReceiverTag = InstanceTag(encodedM.senderInstanceTag)
                synchronized(slaveSessions) {
                    if (!slaveSessions.containsKey(newReceiverTag)) {
                        val session = SessionImpl(sessionID, host,
                                senderInstanceTag, newReceiverTag)
                        if (encodedM.messageType == AbstractEncodedMessage.MESSAGE_DHKEY) {
                            session.authContext!!.set(authContext!!)
                        }
                        session.addOtrEngineListener(object : OtrEngineListener {
                            override fun sessionStatusChanged(sessionID: SessionID?) {
                                for (l in listeners) l.sessionStatusChanged(sessionID)
                            }

                            override fun multipleInstancesDetected(sessionID: SessionID?) {}
                            override fun outgoingSessionChanged(sessionID: SessionID?) {}
                        })
                        slaveSessions[newReceiverTag] = session
                        host!!.multipleInstancesDetected(sessionID)
                        for (l in listeners) l.multipleInstancesDetected(sessionID)
                    }
                }
                return slaveSessions[newReceiverTag]!!.transformReceiving(msgText)
            }
        }
        return when (m.messageType) {
            AbstractEncodedMessage.MESSAGE_DATA -> handleDataMessage(m as DataMessage)
            AbstractMessage.MESSAGE_ERROR -> {
                handleErrorMessage(m as ErrorMessage)
                null
            }
            AbstractMessage.MESSAGE_PLAINTEXT -> handlePlainTextMessage(m as PlainTextMessage)
            AbstractMessage.MESSAGE_QUERY -> {
                handleQueryMessage(m as QueryMessage)
                null
            }
            AbstractEncodedMessage.MESSAGE_DH_COMMIT, AbstractEncodedMessage.MESSAGE_DHKEY, AbstractEncodedMessage.MESSAGE_REVEALSIG, AbstractEncodedMessage.MESSAGE_SIGNATURE -> {
                val auth = authContext!!
                auth.handleReceivingMessage(m)
                if (auth.isSecure) {
                    sessionStatus = SessionStatus.ENCRYPTED
                    Timber.log(TimberLog.FINER, "Entered secure state.")
                }
                null
            }
            else -> throw UnsupportedOperationException("Received an unknown message type.")
        }
    }

    @Throws(OtrException::class)
    private fun sendingDHCommitMessage(queryMessage: QueryMessage, supportV1: Boolean) {
        // OTR setup request from buddy is disabled when omemo is ongoing
        val otrContact = ScOtrEngineImpl.getOtrContact(sessionID)
        val chat = OtrActivator.uiService.getChat(otrContact!!.contact!!)
        if ((chat as ChatPanel).isOmemoChat) {
            val msgLocal = aTalkApp.globalContext.getString(R.string.crypto_msg_OMEMO_SESSION_OTR_NOT_ALLOW)
            host!!.showAlert(sessionID, msgLocal)
            val msgRemote = aTalkApp.globalContext.getString(R.string.crypto_msg_OMEMO_SESSION_OTR_NOT_ALLOW_SENDER)
            host!!.injectMessage(sessionID, msgRemote)
            return
        }
        val policy = sessionPolicy
        if (queryMessage.versions!!.contains(Session.OTRv.THREE) && policy.allowV3) {
            Timber.log(TimberLog.FINER, "V3 message tag found and supported.")
            val dhCommit = authContext!!.respondAuth(Session.OTRv.THREE)
            if (isMasterSession) {
                for (session in slaveSessions.values) {
                    session.authContext!!.reset()
                    session.authContext!!.set(authContext!!)
                }
            }
            Timber.log(TimberLog.FINER, "Sending D-H Commit Message")
            injectMessage(dhCommit)
        } else if (queryMessage.versions!!.contains(Session.OTRv.TWO) && policy.allowV2) {
            Timber.log(TimberLog.FINER, "V2 message tag found and supported.")
            val dhCommit = authContext!!.respondAuth(Session.OTRv.TWO)
            Timber.log(TimberLog.FINER, "Sending D-H Commit Message")
            injectMessage(dhCommit)
        } else if (queryMessage.versions!!.contains(Session.OTRv.ONE) && policy.allowV1) {
            if (supportV1) {
                Timber.log(TimberLog.FINER, "V1 message tag found and supported. - ignoring.")
            } else {
                Timber.log(TimberLog.FINER, "V1 message tag found but not supported.")
                throw UnsupportedOperationException()
            }
        }
    }

    @Throws(OtrException::class)
    private fun handleQueryMessage(queryMessage: QueryMessage) {
        Timber.log(TimberLog.FINER, "%s received a query message from %s through %s.",
                sessionID!!.accountID, sessionID!!.userID, sessionID!!.protocolName)
        sendingDHCommitMessage(queryMessage, true)
    }

    @Throws(OtrException::class)
    private fun handleErrorMessage(errorMessage: ErrorMessage) {
        Timber.log(TimberLog.FINER, "%s received an error message from %s through %s.",
                sessionID!!.accountID, sessionID!!.userID, sessionID!!.protocolName)
        host!!.showError(sessionID, errorMessage.error)
        val policy = sessionPolicy
        if (policy.errorStartAKE) {
            Timber.log(TimberLog.FINER, "Error message starts AKE.")
            val versions = ArrayList<Int>()
            if (policy.allowV1) versions.add(Session.OTRv.ONE)
            if (policy.allowV2) versions.add(Session.OTRv.TWO)
            if (policy.allowV3) versions.add(Session.OTRv.THREE)
            Timber.log(TimberLog.FINER, "Sending Query")
            injectMessage(QueryMessage(versions))
        }
    }

    @Throws(OtrException::class)
    private fun handleDataMessage(data: DataMessage): String? {
        Timber.log(TimberLog.FINER, "%s received a data message from %s.",
                sessionID!!.accountID, sessionID!!.userID)
        when (sessionStatus) {
            SessionStatus.ENCRYPTED -> {
                Timber.log(TimberLog.FINER, "Message state is ENCRYPTED. Trying to decrypt message.")
                // Find matching session keys.
                val senderKeyID = data.senderKeyID
                val recipientKeyID = data.recipientKeyID
                val matchingKeys = getSessionKeysByID(recipientKeyID, senderKeyID)
                if (matchingKeys == null) {
                    Timber.log(TimberLog.FINER, "No matching keys found.")
                    host!!.unreadableMessageReceived(sessionID)
                    injectMessage(ErrorMessage(AbstractMessage.MESSAGE_ERROR,
                            host!!.getReplyForUnreadableMessage(sessionID)))
                    return null
                }

                // Verify received MAC with a locally calculated MAC.
                Timber.log(TimberLog.FINER, "Transforming T to byte[] to calculate it's HmacSHA1.")
                val serializedT: ByteArray
                try {
                    serializedT = SerializationUtils.toByteArray(data.t)
                } catch (e: IOException) {
                    throw OtrException(e)
                }
                val otrCryptoEngine = OtrCryptoEngineImpl()
                val computedMAC = otrCryptoEngine.sha1Hmac(serializedT,
                        matchingKeys.receivingMACKey, SerializationConstants.TYPE_LEN_MAC)
                if (!Arrays.equals(computedMAC, data.mac)) {
                    Timber.log(TimberLog.FINER, "MAC verification failed, ignoring message")
                    host!!.unreadableMessageReceived(sessionID)
                    injectMessage(ErrorMessage(AbstractMessage.MESSAGE_ERROR,
                            host!!.getReplyForUnreadableMessage(sessionID)))
                    return null
                }
                Timber.log(TimberLog.FINER, "Computed HmacSHA1 value matches sent one.")

                // Mark this MAC key as old to be revealed.
                matchingKeys.isUsedReceivingMACKey = true
                matchingKeys.receivingCtr = data.ctr!!
                val dmc = otrCryptoEngine.aesDecrypt(matchingKeys.receivingAESKey,
                        matchingKeys.receivingCtr, data.encryptedMessage)
                var decryptedMsgContent: String
                // Expect bytes to be text encoded in UTF-8.
                decryptedMsgContent = String(dmc, StandardCharsets.UTF_8)
                Timber.log(TimberLog.FINER, "Decrypted message: '%s", decryptedMsgContent)

                // Rotate keys if necessary.
                val mostRecent = mostRecentSessionKeys
                if (mostRecent.localKeyID == recipientKeyID) rotateLocalSessionKeys()
                if (mostRecent.remoteKeyID == senderKeyID) rotateRemoteSessionKeys(data.nextDH!!)

                // Handle TLVs
                var tlvs: MutableList<TLV>? = null
                var tlvIndex = decryptedMsgContent.indexOf(0x0.toChar())
                if (tlvIndex > -1) {
                    decryptedMsgContent = decryptedMsgContent.substring(0, tlvIndex)
                    tlvIndex++
                    val tlvsb = ByteArray(dmc.size - tlvIndex)
                    System.arraycopy(dmc, tlvIndex, tlvsb, 0, tlvsb.size)
                    tlvs = LinkedList()
                    val tin = ByteArrayInputStream(tlvsb)
                    while (tin.available() > 0) {
                        var type: Int
                        var tdata: ByteArray
                        val eois = OtrInputStream(tin)
                        try {
                            type = eois.readShort()
                            tdata = eois.readTlvData()
                            eois.close()
                        } catch (e: IOException) {
                            throw OtrException(e)
                        }
                        tlvs.add(TLV(type, tdata))
                    }
                }
                if (tlvs != null && tlvs.size > 0) {
                    for (tlv in tlvs) {
                        when (tlv.type) {
                            TLV.DISCONNECTED -> {
                                sessionStatus = SessionStatus.FINISHED
                                return null
                            }
                            else -> if (otrSm.doProcessTlv(tlv)) return null
                        }
                    }
                }
                return decryptedMsgContent
            }
            SessionStatus.FINISHED, SessionStatus.PLAINTEXT -> {
                host!!.unreadableMessageReceived(sessionID)
                injectMessage(ErrorMessage(AbstractMessage.MESSAGE_ERROR,
                        host!!.getReplyForUnreadableMessage(sessionID)))
            }
        }
        return null
    }

    @Throws(OtrException::class)
    override fun injectMessage(m: AbstractMessage?) {
        var msg = try {
            SerializationUtils.toString(m)
        } catch (e: IOException) {
            throw OtrException(e)
        }
        if (m is QueryMessage) msg += host!!.getFallbackMessage(sessionID)
        if (SerializationUtils.otrEncoded(msg)) {
            // Content is OTR encoded, so we are allowed to partition.
            val fragments: Array<String>?
            try {
                fragments = fragmenter.fragment(msg)
                for (fragment in fragments) {
                    host!!.injectMessage(sessionID, fragment)
                }
            } catch (e: IOException) {
                Timber.w("Failed to fragment message according to provided instructions.")
                throw OtrException(e)
            }
        } else {
            host!!.injectMessage(sessionID, msg)
        }
    }

    @Throws(OtrException::class)
    private fun handlePlainTextMessage(plainTextMessage: PlainTextMessage): String {
        Timber.log(TimberLog.FINER, "%s received a plaintext message from %s through %s.",
                sessionID!!.accountID, sessionID!!.userID, sessionID!!.protocolName)
        val policy = sessionPolicy
        val versions: Collection<Int?>? = plainTextMessage.versions
        if (versions == null || versions.isEmpty()) {
            Timber.log(TimberLog.FINER, "Received plaintext message without the whitespace tag.")
            return when (this.sessionStatus) {
                SessionStatus.ENCRYPTED, SessionStatus.FINISHED -> {
                    // Display the message to the user, but warn him that the message was received
                    // non-encrypted.
                    host!!.unencryptedMessageReceived(sessionID, plainTextMessage.cleanText)
                    plainTextMessage.cleanText!!
                }
                SessionStatus.PLAINTEXT -> {
                    // Simply display the message to the user. If REQUIRE_ENCRYPTION is set, warn
                    // him that the message was received non-encrypted.
                    if (policy.requireEncryption) {
                        host!!.unencryptedMessageReceived(sessionID, plainTextMessage.cleanText)
                    }
                    plainTextMessage.cleanText!!
                }
            }
        } else {
            Timber.log(TimberLog.FINER, "Received plaintext message with the whitespace tag.")
            when (this.sessionStatus) {
                SessionStatus.ENCRYPTED, SessionStatus.FINISHED ->                     // Remove the whitespace tag and display the message to the user, but warn
                    // him that the message was received non-encrypted.
                    host!!.unencryptedMessageReceived(sessionID, plainTextMessage.cleanText)
                SessionStatus.PLAINTEXT ->                     // Remove the whitespace tag and display the message to the user. If
                    // REQUIRE_ENCRYPTION is set, warn him that the message was received non-encrypted.
                    if (policy.requireEncryption) host!!.unencryptedMessageReceived(sessionID, plainTextMessage.cleanText)
            }
            if (policy.whitespaceStartAKE) {
                Timber.log(TimberLog.FINER, "WHITESPACE_START_AKE is set")
                try {
                    sendingDHCommitMessage(plainTextMessage, false)
                } catch (ex: OtrException) {
                    ex.printStackTrace()
                }
            }
        }
        return plainTextMessage.cleanText!!
    }

    @Throws(OtrException::class)
    override fun transformSending(content: String): Array<String>? {
        return this.transformSending(content, null)
    }

    @Throws(OtrException::class)
    override fun transformSending(content: String, tlvs: List<TLV?>?): Array<String>? {
        return if (isMasterSession && slaveSessions.isSelected && protocolVersion == Session.OTRv.THREE) {
            slaveSessions.getSelected()!!.transformSending(content, tlvs)
        } else when (this.sessionStatus) {
            SessionStatus.PLAINTEXT -> {
                val otrPolicy = sessionPolicy
                if (otrPolicy.requireEncryption) {
                    startSession()
                    host!!.requireEncryptedMessage(sessionID, content)
                    null
                } else {
                    if (otrPolicy.sendWhitespaceTag && offerStatus != OfferStatus.rejected) {
                        offerStatus = OfferStatus.sent
                        var versions: MutableList<Int?>? = ArrayList(3)
                        if (otrPolicy.allowV1) versions!!.add(Session.OTRv.ONE)
                        if (otrPolicy.allowV2) versions!!.add(Session.OTRv.TWO)
                        if (otrPolicy.allowV3) versions!!.add(Session.OTRv.THREE)
                        if (versions!!.isEmpty()) versions = null
                        val abstractMessage = PlainTextMessage(versions, content)
                        try {
                            arrayOf(SerializationUtils.toString(abstractMessage))
                        } catch (e: IOException) {
                            throw OtrException(e)
                        }
                    } else {
                        arrayOf(content)
                    }
                }
            }
            SessionStatus.ENCRYPTED -> {
                Timber.log(TimberLog.FINER, "%s sends an encrypted message to %s through %s.",
                        sessionID!!.accountID, sessionID!!.userID, sessionID!!.protocolName)

                // Get encryption keys.
                val encryptionKeys = encryptionSessionKeys
                val senderKeyID = encryptionKeys.localKeyID
                val recipientKeyID = encryptionKeys.remoteKeyID

                // Increment CTR.
                encryptionKeys.incrementSendingCtr()
                val ctr = encryptionKeys.sendingCtr
                val out = ByteArrayOutputStream()
                if (content != null && content.isNotEmpty()) {
                    try {
                        out.write(content.toByteArray(StandardCharsets.UTF_8))
                    } catch (e: IOException) {
                        throw OtrException(e)
                    }
                }

                // Append tlvs
                if (tlvs != null && tlvs.isNotEmpty()) {
                    out.write(0x00.toByte().toInt())
                    val eoos = OtrOutputStream(out)
                    for (tlv in tlvs) {
                        try {
                            eoos.writeShort(tlv!!.type)
                            eoos.writeTlvData(tlv.value)
                        } catch (e: IOException) {
                            throw OtrException(e)
                        }
                    }
                }
                val otrCryptoEngine = OtrCryptoEngineImpl()
                val data = out.toByteArray()
                // Encrypt message.
                Timber.log(TimberLog.FINER, "Encrypting message with keyids (localKeyID, remoteKeyID) = (%s, %s)",
                        senderKeyID, recipientKeyID)
                val encryptedMsg = otrCryptoEngine.aesEncrypt(encryptionKeys.sendingAESKey, ctr, data)

                // Get most recent keys to get the next D-H public key.
                val mostRecentKeys = mostRecentSessionKeys
                val nextDH = mostRecentKeys.localPair!!.public as DHPublicKey

                // Calculate T.
                val t = MysteriousT(protocolVersion,
                        senderInstanceTag.value, receiverInstanceTag.value, 0,
                        senderKeyID, recipientKeyID, nextDH, ctr, encryptedMsg)

                // Calculate T hash.
                val sendingMACKey = encryptionKeys.sendingMACKey
                Timber.log(TimberLog.FINER, "Transforming T to byte[] to calculate it's HmacSHA1.")
                val serializedT: ByteArray
                try {
                    serializedT = SerializationUtils.toByteArray(t)
                } catch (e: IOException) {
                    throw OtrException(e)
                }
                val mac = otrCryptoEngine.sha1Hmac(serializedT, sendingMACKey, SerializationConstants.TYPE_LEN_MAC)

                // Get old MAC keys to be revealed.
                val oldKeys = collectOldMacKeys()
                val dataMessage = DataMessage(t, mac, oldKeys)
                dataMessage.senderInstanceTag = senderInstanceTag.value
                dataMessage.receiverInstanceTag = receiverInstanceTag.value
                return try {
                    val completeMessage = SerializationUtils.toString(dataMessage)
                    fragmenter.fragment(completeMessage)
                } catch (e: IOException) {
                    throw OtrException(e)
                }
            }
            SessionStatus.FINISHED -> {
                host!!.finishedSessionMessage(sessionID, content)
                null
            }
        }
    }

    @Throws(OtrException::class)
    override fun startSession() {
        if (slaveSessions.isSelected && protocolVersion == Session.OTRv.THREE) {
            slaveSessions.getSelected()!!.startSession()
            return
        }
        if (this.sessionStatus == SessionStatus.ENCRYPTED) return
        if (!sessionPolicy.allowV2 && !sessionPolicy.allowV3) throw UnsupportedOperationException()
        authContext!!.startAuth()
    }

    @Throws(OtrException::class)
    override fun endSession() {
        if (slaveSessions.isSelected && protocolVersion == Session.OTRv.THREE) {
            slaveSessions.getSelected()!!.endSession()
            return
        }
        when (this.sessionStatus) {
            SessionStatus.ENCRYPTED -> {
                val tlvs: MutableList<TLV?> = ArrayList(1)
                tlvs.add(TLV(TLV.DISCONNECTED, null))
                val msg = this.transformSending("", tlvs)
                for (part in msg!!) {
                    host!!.injectMessage(sessionID, part)
                }
                sessionStatus = SessionStatus.PLAINTEXT
            }
            SessionStatus.FINISHED -> sessionStatus = SessionStatus.PLAINTEXT
            SessionStatus.PLAINTEXT -> {}
        }
    }

    @Throws(OtrException::class)
    override fun refreshSession() {
        endSession()
        startSession()
    }

    override fun addOtrEngineListener(l: OtrEngineListener) {
        synchronized(listeners) { if (!listeners.contains(l)) listeners.add(l) }
    }

    override fun removeOtrEngineListener(l: OtrEngineListener) {
        synchronized(listeners) { listeners.remove(l) }
    }

    override val sessionPolicy: OtrPolicy
        get() = host!!.getSessionPolicy(sessionID)!!

    @get:Throws(OtrException::class)
    override val localKeyPair: KeyPair
        get() = host!!.getLocalKeyPair(sessionID)!!

    @Throws(OtrException::class)
    override fun initSmp(question: String?, secret: String) {
        if (slaveSessions.isSelected && protocolVersion == Session.OTRv.THREE) {
            slaveSessions.getSelected()!!.initSmp(question, secret)
            return
        }
        if (this.sessionStatus != SessionStatus.ENCRYPTED) return
        val tlvs = otrSm.initRespondSmp(question, secret, true)
        val msg = transformSending("", tlvs)
        for (part in msg!!) {
            host!!.injectMessage(sessionID, part)
        }
    }

    @Throws(OtrException::class)
    override fun respondSmp(question: String?, secret: String) {
        if (slaveSessions.isSelected && protocolVersion == Session.OTRv.THREE) {
            slaveSessions.getSelected()!!.respondSmp(question, secret)
            return
        }
        if (this.sessionStatus != SessionStatus.ENCRYPTED) return
        val tlvs = otrSm.initRespondSmp(question, secret, false)
        val msg = transformSending("", tlvs)
        for (part in msg!!) {
            host!!.injectMessage(sessionID, part)
        }
    }

    @Throws(OtrException::class)
    override fun abortSmp() {
        if (slaveSessions.isSelected && protocolVersion == Session.OTRv.THREE) {
            slaveSessions.getSelected()!!.abortSmp()
            return
        }
        if (this.sessionStatus != SessionStatus.ENCRYPTED) return
        val tlvs = otrSm.abortSmp()
        val msg = transformSending("", tlvs)
        for (part in msg!!) {
            host!!.injectMessage(sessionID, part)
        }
    }

    override val isSmpInProgress: Boolean
        get() = if (slaveSessions.isSelected && protocolVersion == Session.OTRv.THREE) slaveSessions.getSelected()!!.isSmpInProgress else otrSm.isSmpInProgress


    override val instances: List<SessionImpl>
        get() {
            val result: MutableList<SessionImpl> = ArrayList()
            result.add(this)
            result.addAll(slaveSessions.values)
            return result
        }

    @Throws(OtrException::class)
    override fun respondSmp(receiverTag: InstanceTag, question: String?, secret: String) {
        if (receiverTag == receiverInstanceTag) {
            respondSmp(question, secret)
        } else {
            val slave: Session? = slaveSessions[receiverTag]
            if (slave != null) slave.respondSmp(question, secret) else respondSmp(question, secret)
        }
    }

    override var sessionStatus: SessionStatus
        get() {
            return if (slaveSessions.isSelected && protocolVersion == Session.OTRv.THREE) {
                slaveSessions.getSelected()!!.sessionStatus
            } else field
        }

        @Throws(OtrException::class)
        set(sessionStatus) {
            when (sessionStatus) {
                SessionStatus.ENCRYPTED -> {
                    val auth = authContext!!
                    s = auth.s
                    Timber.log(TimberLog.FINER, "Setting most recent session keys from auth.")
                    run {
                        var i = 0
                        while (i < this.sessionKeys!![0].size) {
                            val current = getSessionKeysByIndex(0, i)
                            current.setLocalPair(auth.getLocalDHKeyPair1(), 1)
                            current.setRemoteDHPublicKey(auth.remoteDHPublicKey, 1)
                            current.setS(auth.s)
                            i++
                        }
                    }
                    val nextDH: KeyPair? = OtrCryptoEngineImpl().generateDHKeyPair()
                    var i = 0
                    while (i < sessionKeys!![1].size) {
                        val current = getSessionKeysByIndex(1, i)
                        current.setRemoteDHPublicKey(auth.remoteDHPublicKey, 1)
                        current.setLocalPair(nextDH, 2)
                        i++
                    }
                    remotePublicKey = auth.remoteLongTermPublicKey
                    auth.reset()
                    otrSm.reset()
                }
                SessionStatus.FINISHED, SessionStatus.PLAINTEXT -> {}
            }
            if (sessionStatus == this.sessionStatus) return
            field = sessionStatus
            for (l in listeners) l.sessionStatusChanged(sessionID)
        }

    override fun getSessionStatus(tag: InstanceTag): SessionStatus {
        return if (tag == receiverInstanceTag) sessionStatus else {
            val slave: Session? = slaveSessions[tag]
            slave?.sessionStatus ?: sessionStatus
        }
    }

    override val outgoingInstance: Session
        get() = if (slaveSessions.isSelected) {
            slaveSessions.getSelected()!!
        } else {
            this
        }

    override fun setOutgoingInstance(tag: InstanceTag): Boolean {
        // Only master session can set the outgoing session.
        if (!isMasterSession) return false
        if (tag == receiverInstanceTag) {
            slaveSessions.deselect()
            for (l in listeners) l.outgoingSessionChanged(sessionID)
            return true
        }
        return if (slaveSessions.containsKey(tag)) {
            slaveSessions.select(tag)
            for (l in listeners) {
                l.outgoingSessionChanged(sessionID)
            }
            true
        } else {
            slaveSessions.deselect()
            false
        }
    }
}