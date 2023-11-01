/*
 * Copyright @ 2015 - present 8x8, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Some of the code in this class is derived from ccRtp's SRTP implementation,
 * which has the following copyright notice:
 *
 * Copyright (C) 2004-2006 the Minisip Team
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307 USA
 */
package org.atalk.impl.neomedia.transform.srtp

import org.atalk.impl.neomedia.transform.srtp.utils.SrtpPacketUtils
import org.atalk.util.ByteArrayBuffer
import org.bouncycastle.crypto.params.KeyParameter
import timber.log.Timber
import java.util.*

/**
 * SrtpCryptoContext class is the core class of SRTP implementation. There can be multiple SRTP
 * sources in one SRTP session. And each SRTP stream has a corresponding SrtpCryptoContext object,
 * identified by SSRC. In this way, different sources can be protected independently.
 *
 * SrtpCryptoContext class acts as a manager class and maintains all the information used in SRTP
 * transformation. It is responsible for deriving encryption/salting/authentication keys from master
 * keys. And it will invoke certain class to encrypt/decrypt (transform/reverse transform) RTP
 * packets. It will hold a replay check db and do replay check against incoming packets.
 *
 * Refer to section 3.2 in RFC3711 for detailed description of cryptographic context.
 *
 * Cryptographic related parameters, i.e. encryption mode / authentication mode, master encryption
 * key and master salt key are determined outside the scope of SRTP implementation. They can be
 * assigned manually, or can be assigned automatically using some key management protocol, such as
 * MIKEY (RFC3830), SDES (RFC4568) or Phil Zimmermann's ZRTP protocol (RFC6189).
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class SrtpCryptoContext : BaseSrtpCryptoContext {
    /**
     * For the receiver only, the rollover counter guessed from the sequence number of the received
     * packet that is currently being processed (i.e. the value is valid during the execution of
     * [.reverseTransformPacket] only.) RFC 3711 refers to it by the name `v`.
     */
    private var guessedROC = 0

    /**
     * RFC 3711: a 32-bit unsigned rollover counter (ROC), which records how many times the 16-bit RTP
     * sequence number has been reset to zero after passing through 65,535.  Unlike the sequence number (SEQ),
     * which SRTP extracts from the RTP packet header, the ROC is maintained by SRTP as described in Section 3.3.1.
     */
    private var roc: Int

    /**
     * RFC 3711: for the receiver only, a 16-bit sequence number `s_l`,
     * which can be thought of as the highest received RTP sequence number (see
     * Section 3.3.1 for its handling), which SHOULD be authenticated since
     * message authentication is RECOMMENDED.
     */
    private var s_l = 0

    /**
     * The indicator which determines whether this instance is used by an SRTP
     * sender (`true`) or receiver (`false`).
     */
    private val sender: Boolean

    /**
     * The indicator which determines whether [.s_l] has seen set i.e.
     * appropriately initialized.
     */
    private var seqNumSet = false

    /**
     * Constructs an empty SrtpCryptoContext using ssrc. The other parameters are set to default null value.
     *
     * @param sender `true` if the new instance is to be used by an SRTP sender; `false` if
     * the new instance is to be used by an SRTP receiver
     * @param ssrc SSRC of this SrtpCryptoContext
     */
    constructor(sender: Boolean, ssrc: Int) : super(ssrc) {
        this.sender = sender
        roc = 0
    }

    /**
     * Constructs a normal SrtpCryptoContext based on the given parameters.
     *
     * @param sender `true` if the new instance is to be used by an SRTP sender; `false` if
     * the new instance is to be used by an SRTP receiver
     * @param ssrc the RTP SSRC that this SRTP cryptographic context protects.
     * @param roc the initial Roll-Over-Counter according to RFC 3711. These are the upper 32-bits
     * of the overall 48-bits SRTP packet index. Refer to chapter 3.2.1 of the RFC.
     * @param masterK byte array holding the master key for this SRTP cryptographic context.
     * Refer to chapter 3.2.1 of the RFC about the role of the master key.
     * @param masterS byte array holding the master salt for this SRTP cryptographic context.
     * It is used to computer the initialization vector that in turn as input to compute
     * the session key, session authentication key, and the session salt.
     * @param policy SRTP policy for this SRTP cryptographic context, defined
     * the encryption algorithm, the authentication algorithm, etc
     */
    constructor(
            sender: Boolean, ssrc: Int, roc: Int,
            masterK: ByteArray, masterS: ByteArray?, policy: SrtpPolicy) : super(ssrc, masterK, masterS, policy) {
        this.sender = sender
        this.roc = roc
        deriveSrtpKeys(masterK, masterS)
    }

    /**
     * Authenticates a specific `RawPacket` if the `policy` of this
     * `SrtpCryptoContext` specifies that authentication is to be performed.
     *
     * @param pkt the `RawPacket` to authenticate
     * @return `true` if the `policy` of this `SrtpCryptoContext` specifies that authentication
     * is to not be performed or `pkt` was successfully authenticated; otherwise, `false`
     */
    private fun authenticatePacket(pkt: ByteArrayBuffer?): SrtpErrorStatus {
        if (policy!!.authType != SrtpPolicy.NULL_AUTHENTICATION) {
            val tagLength = policy.authTagLength

            // get original authentication and store in tempStore
            pkt!!.readRegionToBuff(pkt.length - tagLength, tagLength, tempStore)
            pkt.shrink(tagLength)
            // save computed authentication in tagStore
            authenticatePacketHmac(pkt, guessedROC)

            // compare authentication tags using constant time comparison
            var nonEqual = 0
            for (i in 0 until tagLength) {
                nonEqual = nonEqual or (tempStore[i].toInt() xor tagStore!![i].toInt())
            }
            if (nonEqual != 0)
                return SrtpErrorStatus.AUTH_FAIL
        }
        return SrtpErrorStatus.OK
    }

    /**
     * Checks if a packet is a replayed based on its sequence number. The method
     * supports a 64 packet history relative the the specified sequence number.
     * The sequence number is guaranteed to be real (i.e. not faked) through authentication.
     *
     * @param seqNo sequence number of the packet
     * @param guessedIndex guessed ROC
     * @return `true` if the specified sequence number indicates that the
     * packet is not a replayed one; `false`, otherwise.
     */
    private fun checkReplay(seqNo: Int, guessedIndex: Long): SrtpErrorStatus {
        // Compute the index of the previously received packet and its delta to the newly received packet.
        val localIndex = roc.toLong() shl 16 or s_l.toLong()
        val delta = guessedIndex - localIndex
        return if (delta > 0) {
            SrtpErrorStatus.OK // Packet not received yet.
        } else if (-delta >= REPLAY_WINDOW_SIZE) {
            if (sender) {
                Timber.e("Discarding RTP packet with sequence number %d, SSRC %d because it is outside the replay window! (roc %d, s_l %d), guessedROC %d",
                        seqNo, 0xFFFFFFFFL and ssrc.toLong(), roc, s_l, guessedROC)
            }
            SrtpErrorStatus.REPLAY_OLD // Packet too old.
        } else if (replayWindow ushr (-delta).toInt() and 0x1L != 0L) {
            if (sender) {
                Timber.e("Discarding RTP packet with sequence number %d, SSRC %d because it has been received already! (roc %d, s_l %d), guessedROC %d",
                        seqNo, 0xFFFFFFFFL and ssrc.toLong(), roc, s_l, guessedROC)
            }
            SrtpErrorStatus.REPLAY_FAIL // Packet received already!
        } else {
            SrtpErrorStatus.OK // Packet not received yet.
        }
    }

    /**
     * Derives the srtp session keys from the master key
     */
    private fun deriveSrtpKeys(masterKey: ByteArray, masterSalt: ByteArray?) {
        val kdf = SrtpKdf(masterKey, masterSalt, policy!!)

        // compute the session salt
        kdf.deriveSessionKey(saltKey, SrtpKdf.LABEL_RTP_SALT)

        // compute the session encryption key
        if (cipherCtr != null) {
            val encKey = ByteArray(policy.encKeyLength)
            kdf.deriveSessionKey(encKey, SrtpKdf.LABEL_RTP_ENCRYPTION)
            cipherF8?.init(encKey, saltKey!!)
            cipherCtr.init(encKey)
            Arrays.fill(encKey, 0.toByte())
        }

        // compute the session authentication key
        if (mac != null) {
            val authKey = ByteArray(policy.authKeyLength)
            kdf.deriveSessionKey(authKey, SrtpKdf.LABEL_RTP_MSG_AUTH)
            mac.init(KeyParameter(authKey))
            Arrays.fill(authKey, 0.toByte())
        }
        kdf.close()
    }

    /**
     * For the receiver only, determines/guesses the SRTP index of a received
     * SRTP packet with a specific sequence number.
     *
     * @param seqNo the sequence number of the received SRTP packet.
     * @return the SRTP index of the received SRTP packet with the specified `seqNo`
     */
    private fun guessIndex(seqNo: Int): Long {
        guessedROC = if (s_l < 32768) {
            if (seqNo - s_l > 32768) roc - 1 else roc
        } else {
            if (s_l - 32768 > seqNo) roc + 1 else roc
        }
        return guessedROC.toLong() shl 16 or seqNo.toLong()
    }

    /**
     * Performs Counter Mode AES encryption/decryption
     *
     * @param pkt the RTP packet to be encrypted/decrypted
     */
    private fun processPacketAesCm(pkt: ByteArrayBuffer) {
        val ssrc = SrtpPacketUtils.getSsrc(pkt)
        val seqNo = SrtpPacketUtils.getSequenceNumber(pkt)
        val index = guessedROC.toLong() shl 16 or seqNo.toLong()

        // byte[] iv = new byte[16];
        ivStore[0] = saltKey!![0]
        ivStore[1] = saltKey[1]
        ivStore[2] = saltKey[2]
        ivStore[3] = saltKey[3]
        var i = 4
        while (i < 8) {
            ivStore[i] = ((0xFF and (ssrc shr 7) - i) * 8 xor saltKey[i].toInt()).toByte()
            i++
        }
        i = 8
        while (i < 14) {
            ivStore[i] = (0xFF and ((index shr 13 - i) * 8).toByte().toInt() xor saltKey[i].toInt()).toByte()
            i++
        }
        ivStore[15] = 0
        ivStore[14] = 0
        val rtpHeaderLength = SrtpPacketUtils.getTotalHeaderLength(pkt)
        cipherCtr!!.process(
                pkt.buffer,
                pkt.offset + rtpHeaderLength,
                pkt.length - rtpHeaderLength,
                ivStore)
    }

    /**
     * Performs F8 Mode AES encryption/decryption
     *
     * @param pkt the RTP packet to be encrypted/decrypted
     */
    private fun processPacketAesF8(pkt: ByteArrayBuffer?) {
        // 11 bytes of the RTP header are the 11 bytes of the iv
        // the first byte of the RTP header is not used.
        System.arraycopy(pkt!!.buffer, pkt.offset, ivStore, 0, 12)
        ivStore[0] = 0

        // set the ROC in network order into IV
        val roc = guessedROC
        ivStore[12] = (roc shr 24).toByte()
        ivStore[13] = (roc shr 16).toByte()
        ivStore[14] = (roc shr 8).toByte()
        ivStore[15] = roc.toByte()
        val rtpHeaderLength = SrtpPacketUtils.getTotalHeaderLength(pkt)
        cipherF8!!.process(
                pkt.buffer,
                pkt.offset + rtpHeaderLength,
                pkt.length - rtpHeaderLength,
                ivStore)
    }

    /**
     * Transforms an SRTP packet into an RTP packet. The method is called when
     * an SRTP packet is received. Operations done by the this operation
     * include: authentication check, packet replay check and decryption. Both
     * encryption and authentication functionality can be turned off as long as
     * the SrtpPolicy used in this SrtpCryptoContext is requires no encryption
     * and no authentication. Then the packet will be sent out untouched.
     * However, this is not encouraged. If no SRTP feature is enabled, then we
     * shall not use SRTP TransformConnector. We should use the original method
     * (RTPManager managed transportation) instead.
     *
     * @param pkt the RTP packet that is just received
     * @param skipDecryption if `true`, the decryption of the packet will not be performed (so as not to waste
     * resources when it is not needed). The packet will still be authenticated and the ROC updated.
     * @return [SrtpErrorStatus.OK] if the packet can be accepted; an error status if
     * the packet failed authentication or failed replay check
     */
    @Synchronized
    fun reverseTransformPacket(pkt: ByteArrayBuffer, skipDecryption: Boolean): SrtpErrorStatus {
        if (!SrtpPacketUtils.validatePacketLength(pkt, policy!!.authTagLength)) {
            /* Too short to be a valid SRTP packet */
            return SrtpErrorStatus.INVALID_PACKET
        }
        val seqNo = SrtpPacketUtils.getSequenceNumber(pkt)
        if (seqNo % 5000 == 0)
            Timber.d("Reverse transform for SSRC: %s; SeqNo: %s; s_l: %s; seqNumSet: %s; roc: %s; guessedROC: %s",
                ssrc, seqNo, s_l, seqNumSet, guessedROC, roc)

        // Whether s_l was initialized while processing this packet.
        var seqNumWasJustSet = false
        if (!seqNumSet) {
            seqNumSet = true
            s_l = seqNo
            seqNumWasJustSet = true
        }

        // Guess the SRTP index (48 bit), see RFC 3711, 3.3.1
        // Stores the guessed rollover counter (ROC) in this.guessedROC.
        val guessedIndex = guessIndex(seqNo)
        val ret: SrtpErrorStatus
        var err = SrtpErrorStatus.OK

        // Replay control
        if (policy.isReceiveReplayDisabled || checkReplay(seqNo, guessedIndex).also { err = it } == SrtpErrorStatus.OK) {
            // Authenticate the packet.
            if (authenticatePacket(pkt).also { err = it } == SrtpErrorStatus.OK) {
                if (!skipDecryption) {
                    when (policy.encType) {
                        SrtpPolicy.AESCM_ENCRYPTION, SrtpPolicy.TWOFISH_ENCRYPTION -> processPacketAesCm(pkt)
                        SrtpPolicy.AESF8_ENCRYPTION, SrtpPolicy.TWOFISHF8_ENCRYPTION -> processPacketAesF8(pkt)
                    }
                }
                // Update the rollover counter and highest sequence number if necessary.
                update(seqNo, guessedIndex)
                ret = SrtpErrorStatus.OK
            } else {
                Timber.w("SRTP auth failed for SSRC %s", ssrc)
                ret = err
            }
        } else {
            ret = err
        }
        if (ret != SrtpErrorStatus.OK && seqNumWasJustSet) {
            // We set the initial value of s_l as a result of processing this
            // packet, but the packet failed to authenticate. We shouldn't
            // update our state based on an untrusted packet, so we revert seqNumSet.
            seqNumSet = false
            s_l = 0
        }
        return ret
    }

    /**
     * Transforms an RTP packet into an SRTP packet. The method is called when a normal RTP packet
     * ready to be sent. Operations done by the transformation may include: encryption, using either
     * Counter Mode encryption, or F8 Mode encryption, adding authentication tag, currently HMC SHA1
     * method. Both encryption and authentication functionality can be turned off as long as the
     * SRTPPolicy used in this SrtpCryptoContext is requires no encryption and no authentication.
     * Then the packet will be sent out untouched. However, this is not encouraged. If no SRTP
     * feature is enabled, then we shall not use SRTP TransformConnector. We should use the original
     * method (RTPManager managed transportation) instead.
     *
     * @param pkt the RTP packet that is going to be sent out
     */
    @Synchronized
    fun transformPacket(pkt: ByteArrayBuffer): SrtpErrorStatus {
        val seqNo = SrtpPacketUtils.getSequenceNumber(pkt)
        if (!seqNumSet) {
            seqNumSet = true
            s_l = seqNo
        }

        // Guess the SRTP index (48 bit), see RFC 3711, 3.3.1
        // Stores the guessed ROC in this.guessedROC
        val guessedIndex = guessIndex(seqNo)
        var err = SrtpErrorStatus.OK

        /*
         * XXX The invocation of the checkReplay method here is not meant as
         * replay protection but as a consistency check of our implementation.
         */
        if (policy!!.isSendReplayEnabled && checkReplay(seqNo, guessedIndex).also { err = it } != SrtpErrorStatus.OK) return err
        when (policy.encType) {
            SrtpPolicy.AESCM_ENCRYPTION, SrtpPolicy.TWOFISH_ENCRYPTION -> processPacketAesCm(pkt)
            SrtpPolicy.AESF8_ENCRYPTION, SrtpPolicy.TWOFISHF8_ENCRYPTION -> processPacketAesF8(pkt)
        }

        /* Authenticate the packet. */
        if (policy.authType != SrtpPolicy.NULL_AUTHENTICATION) {
            authenticatePacketHmac(pkt, guessedROC)
            pkt.append(tagStore, policy.authTagLength)
        }

        // Update the ROC if necessary.
        update(seqNo, guessedIndex)
        return SrtpErrorStatus.OK
    }

    /**
     * For the receiver only, updates the rollover counter (i.e. [.roc])
     * and highest sequence number (i.e. [.s_l]) in this cryptographic
     * context using the SRTP/packet index calculated by
     * [.guessIndex] and updates the replay list (i.e.
     * [.replayWindow]). This method is called after all checks were successful.
     *
     * @param seqNo the sequence number of the accepted SRTP packet
     * @param guessedIndex the SRTP index of the accepted SRTP packet calculated by `guessIndex(int)`
     */
    private fun update(seqNo: Int, guessedIndex: Long) {
        val delta = guessedIndex - (roc.toLong() shl 16 or s_l.toLong())

        /* Update the replay bit mask. */
        if (delta >= REPLAY_WINDOW_SIZE) {
            replayWindow = 1
        } else if (delta > 0) {
            replayWindow = replayWindow shl delta.toInt()
            replayWindow = replayWindow or 1L
        } else {
            replayWindow = replayWindow or (1L shl -delta.toInt())
        }
        if (guessedROC == roc) {
            if (seqNo > s_l) s_l = seqNo and 0xffff
        } else if (guessedROC == roc + 1) {
            s_l = seqNo and 0xffff
            roc = guessedROC
        }

        // Limit the debug info to 1 per 1000
        if (seqNo % 5000 == 0) logReplayWindow(guessedIndex)
    }

    /**
     * Logs the current state of the replay window, for debugging purposes.
     */
    private fun logReplayWindow(newIdx: Long) {
        Timber.d("Updated replay window with seqNo: %s. %s", newIdx,
                SrtpPacketUtils.formatReplayWindow((roc shl 16 or s_l).toLong(), replayWindow, REPLAY_WINDOW_SIZE))
    }
}