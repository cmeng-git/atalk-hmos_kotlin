package net.java.otr4j.session

import net.java.otr4j.OtrEngineHost
import net.java.otr4j.OtrException
import net.java.otr4j.crypto.OtrCryptoEngineImpl
import net.java.otr4j.crypto.OtrCryptoException
import net.java.otr4j.crypto.SM
import net.java.otr4j.crypto.SM.SMException
import net.java.otr4j.crypto.SM.SMState
import net.java.otr4j.io.OtrOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class OtrSm(private val session: Session, private val engineHost: OtrEngineHost?) {
    private var smstate: SMState? = null

    /**
     * Construct an OTR Socialist Millionaire handler object.
     *
     * session The session reference.
     * engineHost The host where we can present messages or ask for the shared secret.
     */
    init {
        reset()
    }

    fun reset() {
        smstate = SMState()
    }

    /**
     * Respond to or initiate an SMP negotiation
     *
     * @param question The question to present to the peer, if initiating. May be `null` for no
     * question. If not initiating, then it should be received question in order to clarify
     * whether this is shared secret verification.
     * @param secret The secret.
     * @param initiating Whether we are initiating or responding to an initial request.
     * @return TLVs to send to the peer
     * @throws OtrException MVN_PASS_JAVADOC_INSPECTION
     */
    @Throws(OtrException::class)
    fun initRespondSmp(question: String?, secret: String, initiating: Boolean): List<TLV?> {
        if (!initiating && !smstate!!.asked) throw OtrException(
                IllegalStateException("There is no question to be answered."))

        /*
         * Construct the combined secret as a SHA256 hash of: Version byte (0x01), Initiator
         * fingerprint (20 bytes), responder fingerprint (20 bytes), secure session id, input secret
         */
        val ourFp = engineHost!!.getLocalFingerprintRaw(session.sessionID)
        val theirFp: ByteArray?
        val remotePublicKey = session.remotePublicKey
        theirFp = try {
            OtrCryptoEngineImpl().getFingerprintRaw(remotePublicKey)
        } catch (e: OtrCryptoException) {
            throw OtrException(e)
        }
        val sessionId = try {
            computeSessionId(session.s)
        } catch (ex: SMException) {
            throw OtrException(ex)
        }
        val combinedBufLen = 41 + sessionId.size + secret.length
        val combinedBuf = ByteArray(combinedBufLen)
        combinedBuf[0] = 1
        if (initiating) {
            System.arraycopy(ourFp!!, 0, combinedBuf, 1, 20)
            System.arraycopy(theirFp!!, 0, combinedBuf, 21, 20)
        } else {
            System.arraycopy(theirFp!!, 0, combinedBuf, 1, 20)
            System.arraycopy(ourFp!!, 0, combinedBuf, 21, 20)
        }
        System.arraycopy(sessionId, 0, combinedBuf, 41, sessionId.size)
        System.arraycopy(secret.toByteArray(), 0,
                combinedBuf, 41 + sessionId.size, secret.length)
        val sha256 = try {
            MessageDigest.getInstance("SHA-256")
        } catch (ex: NoSuchAlgorithmException) {
            throw OtrException(ex)
        }
        val combinedSecret = sha256.digest(combinedBuf)
        var smpmsg: ByteArray?
        smpmsg = try {
            if (initiating) {
                SM.step1(smstate, combinedSecret)
            } else {
                SM.step2b(smstate, combinedSecret)
            }
        } catch (ex: SMException) {
            throw OtrException(ex)
        }

        // If we've got a question, attach it to the smpmsg
        if (question != null && initiating) {
            val bytes = question.toByteArray(StandardCharsets.UTF_8)
            val qsmpmsg = ByteArray(bytes.size + 1 + smpmsg!!.size)
            System.arraycopy(bytes, 0, qsmpmsg, 0, bytes.size)
            System.arraycopy(smpmsg, 0, qsmpmsg, bytes.size + 1, smpmsg.size)
            smpmsg = qsmpmsg
        }
        val sendtlv = TLV(
                if (initiating) (if (question != null) TLV.SMP1Q else TLV.SMP1) else TLV.SMP2, smpmsg)
        smstate!!.nextExpected = if (initiating) SM.EXPECT2 else SM.EXPECT3
        smstate!!.approved = initiating || question == null
        return makeTlvList(sendtlv)
    }

    /**
     * Create an abort TLV and reset our state.
     *
     * @return TLVs to send to the peer
     * @throws OtrException MVN_PASS_JAVADOC_INSPECTION
     */
    @Throws(OtrException::class)
    fun abortSmp(): List<TLV?> {
        val sendtlv = TLV(TLV.SMP_ABORT, ByteArray(0))
        smstate!!.nextExpected = SM.EXPECT1
        return makeTlvList(sendtlv)
    }

    val isSmpInProgress: Boolean
        get() = smstate!!.nextExpected > SM.EXPECT1

    @Throws(OtrException::class)
    fun doProcessTlv(tlv: TLV): Boolean {
        /* If TLVs contain SMP data, process it */
        val nextMsg = smstate!!.nextExpected
        val tlvType = tlv.type
        val pubKey = session.remotePublicKey
        var fingerprint: String? = null
        try {
            fingerprint = OtrCryptoEngineImpl().getFingerprint(pubKey)
        } catch (e: OtrCryptoException) {
            e.printStackTrace()
        }
        if (tlvType == TLV.SMP1Q && nextMsg == SM.EXPECT1) {
            /*
             * We can only do the verification half now. We must wait for the secret to be entered to continue.
             */
            val question = tlv.value
            var qlen = 0
            while (qlen != question!!.size && question[qlen].toInt() != 0) {
                qlen++
            }
            if (qlen == question.size) qlen = 0 else qlen++
            val input = ByteArray(question.size - qlen)
            System.arraycopy(question, qlen, input, 0, question.size - qlen)
            try {
                SM.step2a(smstate, input, 1)
            } catch (e: SMException) {
                throw OtrException(e)
            }
            if (qlen != 0) qlen--
            val plainq = ByteArray(qlen)
            System.arraycopy(question, 0, plainq, 0, qlen)
            if (smstate!!.smProgState != SM.PROG_CHEATED) {
                smstate!!.asked = true
                val questionUTF = String(plainq, StandardCharsets.UTF_8)
                engineHost!!.askForSecret(session.sessionID!!, session.receiverInstanceTag, questionUTF)
            } else {
                engineHost!!.smpError(session.sessionID, tlvType, true)
                reset()
            }
        } else if (tlvType == TLV.SMP1Q) {
            engineHost!!.smpError(session.sessionID, tlvType, false)
        } else if (tlvType == TLV.SMP1 && nextMsg == SM.EXPECT1) {
            /*
             * We can only do the verification half now. We must wait for the secret to be entered to continue.
             */
            try {
                SM.step2a(smstate, tlv.value!!, 0)
            } catch (e: SMException) {
                throw OtrException(e)
            }
            if (smstate!!.smProgState != SM.PROG_CHEATED) {
                smstate!!.asked = true
                engineHost!!.askForSecret(session.sessionID!!, session.receiverInstanceTag, null)
            } else {
                engineHost!!.smpError(session.sessionID, tlvType, true)
                reset()
            }
        } else if (tlvType == TLV.SMP1) {
            engineHost!!.smpError(session.sessionID, tlvType, false)
        } else if (tlvType == TLV.SMP2 && nextMsg == SM.EXPECT2) {
            val nextmsg = try {
                SM.step3(smstate, tlv.value!!)
            } catch (e: SMException) {
                throw OtrException(e)
            }
            if (smstate!!.smProgState != SM.PROG_CHEATED) {
                /* Send msg with next smp msg content */
                val sendtlv = TLV(TLV.SMP3, nextmsg)
                smstate!!.nextExpected = SM.EXPECT4
                val msg = session.transformSending("", makeTlvList(sendtlv))
                for (part in msg!!) {
                    engineHost!!.injectMessage(session.sessionID, part)
                }
            } else {
                engineHost!!.smpError(session.sessionID, tlvType, true)
                reset()
            }
        } else if (tlvType == TLV.SMP2) {
            engineHost!!.smpError(session.sessionID, tlvType, false)
        } else if (tlvType == TLV.SMP3 && nextMsg == SM.EXPECT3) {
            val nextmsg = try {
                SM.step4(smstate, tlv.value!!)
            } catch (e: SMException) {
                throw OtrException(e)
            }

            /* Set trust level based on result */
            if (smstate!!.smProgState == SM.PROG_SUCCEEDED) {
                engineHost!!.verify(session.sessionID, fingerprint, smstate!!.approved)
            } else {
                engineHost!!.unverify(session.sessionID, fingerprint)
            }
            if (smstate!!.smProgState != SM.PROG_CHEATED) {
                /* Send msg with next smp msg content */
                val sendtlv = TLV(TLV.SMP4, nextmsg)
                val msg = session.transformSending("", makeTlvList(sendtlv))
                for (part in msg!!) {
                    engineHost.injectMessage(session.sessionID, part)
                }
            } else {
                engineHost.smpError(session.sessionID, tlvType, true)
            }
            reset()
        } else if (tlvType == TLV.SMP3) {
            engineHost!!.smpError(session.sessionID, tlvType, false)
        } else if (tlvType == TLV.SMP4 && nextMsg == SM.EXPECT4) {
            try {
                SM.step5(smstate, tlv.value!!)
            } catch (e: SMException) {
                throw OtrException(e)
            }
            if (smstate!!.smProgState == SM.PROG_SUCCEEDED) {
                engineHost!!.verify(session.sessionID, fingerprint, smstate!!.approved)
            } else {
                engineHost!!.unverify(session.sessionID, fingerprint)
            }
            if (smstate!!.smProgState == SM.PROG_CHEATED) {
                engineHost.smpError(session.sessionID, tlvType, true)
            }
            reset()
        } else if (tlvType == TLV.SMP4) {
            engineHost!!.smpError(session.sessionID, tlvType, false)
        } else if (tlvType == TLV.SMP_ABORT) {
            engineHost!!.smpAborted(session.sessionID)
            reset()
        } else return false
        return true
    }

    private fun makeTlvList(sendtlv: TLV): List<TLV?> {
        val tlvs = ArrayList<TLV?>(1)
        tlvs.add(sendtlv)
        return tlvs
    }

    companion object {
        /* Compute secret session ID as hash of agreed secret */
        @Throws(SMException::class)
        private fun computeSessionId(s: BigInteger?): ByteArray {
            val sdata: ByteArray
            try {
                val out = ByteArrayOutputStream()
                val oos = OtrOutputStream(out)
                oos.write(0x00)
                oos.writeBigInt(s)
                sdata = out.toByteArray()
                oos.close()
            } catch (e1: IOException) {
                throw SMException(e1)
            }

            /* Calculate the session id */
            val sha256 = try {
                MessageDigest.getInstance("SHA-256")
            } catch (e: NoSuchAlgorithmException) {
                throw SMException("cannot find SHA-256")
            }
            val res = sha256.digest(sdata)
            val secureSessionId = ByteArray(8)
            System.arraycopy(res, 0, secureSessionId, 0, 8)
            return secureSessionId
        }
    }
}