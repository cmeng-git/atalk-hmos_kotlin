/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 *
 *
 * Some of the code in this class is derived from ccRtp's SRTP implementation, which has the
 * following copyright notice:
 *
 * Copyright (C) 2004-2006 the Minisip Team
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307 USA
 */
package org.atalk.impl.neomedia.transform.srtp

import org.atalk.impl.neomedia.transform.srtp.utils.SrtcpPacketUtils
import org.atalk.impl.neomedia.transform.srtp.utils.SrtpPacketUtils
import org.atalk.util.ByteArrayBuffer
import org.bouncycastle.crypto.params.KeyParameter
import timber.log.Timber
import java.util.*

/**
 * SrtcpCryptoContext class is the core class of SRTCP implementation. There can
 * be multiple SRTCP sources in one SRTP session. And each SRTCP stream has a
 * corresponding SrtcpCryptoContext object, identified by sender SSRC. In this way,
 * different sources can be protected independently.
 *
 * SrtcpCryptoContext class acts as a manager class and maintains all the
 * information used in SRTCP transformation. It is responsible for deriving
 * encryption/salting/authentication keys from master keys. And it will invoke
 * certain class to encrypt/decrypt (transform/reverse transform) RTCP packets.
 * It will hold a replay check db and do replay check against incoming packets.
 *
 * Refer to section 3.2 in RFC3711 for detailed description of cryptographic
 * context.
 *
 * Cryptographic related parameters, i.e. encryption mode / authentication mode,
 * master encryption key and master salt key are determined outside the scope of
 * SRTP implementation. They can be assigned manually, or can be assigned
 * automatically using some key management protocol, such as MIKEY (RFC3830),
 * SDES (RFC4568) or Phil Zimmermann's ZRTP protocol (RFC6189).
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class SrtcpCryptoContext : BaseSrtpCryptoContext {
    /**
     * Index received so far
     */
    private var receivedIndex = 0

    /**
     * Index sent so far
     */
    private var sentIndex = 0

    /**
     * Construct an empty SrtcpCryptoContext using ssrc. The other parameters are set to default null value.
     *
     * @param ssrc SSRC of this SrtcpCryptoContext
     */
    constructor(ssrc: Int) : super(ssrc)

    /**
     * Construct a normal SrtcpCryptoContext based on the given parameters.
     *
     * @param ssrc the RTP SSRC that this SRTP cryptographic context protects.
     * @param masterKey byte array holding the master key for this SRTP cryptographic context. Refer to
     * chapter 3.2.1 of the RFC about the role of the master key.
     * @param masterSalt byte array holding the master salt for this SRTP cryptographic context. It is used to
     * computer the initialization vector that in turn is input to compute the session key,
     * session authentication key and the session salt.
     * @param policy SRTP policy for this SRTP cryptographic context, defined the encryption algorithm, the
     * authentication algorithm, etc
     */
    constructor(ssrc: Int, masterKey: ByteArray, masterSalt: ByteArray?, policy: SrtpPolicy) : super(ssrc, masterKey, masterSalt, policy) {
        deriveSrtcpKeys(masterKey, masterSalt)
    }

    /**
     * Checks if a packet is a replayed on based on its sequence number. The method supports a 64
     * packet history relative to the given sequence number. Sequence Number is guaranteed to be
     * real (not faked) through authentication.
     *
     * @param index index number of the SRTCP packet
     * @return true if this sequence number indicates the packet is not a replayed one, false if not
     */
    private fun checkReplay(index: Int): SrtpErrorStatus {
        // Compute the index of the previously received packet and its delta to the new received packet.
        val delta = (index - receivedIndex).toLong()
        return if (delta > 0) SrtpErrorStatus.OK // Packet not yet received
        else if (-delta >= REPLAY_WINDOW_SIZE) SrtpErrorStatus.REPLAY_OLD // Packet too old
        else if (replayWindow ushr (-delta).toInt() and 0x1L != 0L) SrtpErrorStatus.REPLAY_FAIL // Packet already received!
        else SrtpErrorStatus.OK // Packet not yet received
    }

    /**
     * Derives the srtcp session keys from the master key.
     */
    private fun deriveSrtcpKeys(masterKey: ByteArray, masterSalt: ByteArray?) {
        val kdf = SrtpKdf(masterKey, masterSalt, policy!!)

        // compute the session salt
        kdf.deriveSessionKey(saltKey, SrtpKdf.LABEL_RTCP_SALT)

        // compute the session encryption key
        if (cipherCtr != null) {
            val encKey = ByteArray(policy.encKeyLength)
            kdf.deriveSessionKey(encKey, SrtpKdf.LABEL_RTCP_ENCRYPTION)
            cipherF8?.init(encKey, saltKey!!)
            cipherCtr.init(encKey)
            Arrays.fill(encKey, 0.toByte())
        }

        // compute the session authentication key
        if (mac != null) {
            val authKey = ByteArray(policy.authKeyLength)
            kdf.deriveSessionKey(authKey, SrtpKdf.LABEL_RTCP_MSG_AUTH)
            mac.init(KeyParameter(authKey))
            Arrays.fill(authKey, 0.toByte())
        }
        kdf.close()
    }

    /**
     * Performs Counter Mode AES encryption/decryption
     *
     * @param pkt the RTP packet to be encrypted/decrypted
     */
    private fun processPacketAesCm(pkt: ByteArrayBuffer, index: Int) {
        val ssrc = SrtcpPacketUtils.getSenderSsrc(pkt)

        /*
         * Compute the CM IV (refer to chapter 4.1.1 in RFC 3711):
         *
         * k_s   XX XX XX XX XX XX XX XX XX XX XX XX XX XX
         * SSRC              XX XX XX XX
         * index                               XX XX XX XX
         * ------------------------------------------------------XOR
         * IV    XX XX XX XX XX XX XX XX XX XX XX XX XX XX 00 00
         *        0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
         */ivStore[0] = saltKey!![0]
        ivStore[1] = saltKey[1]
        ivStore[2] = saltKey[2]
        ivStore[3] = saltKey[3]

        // The shifts transform the ssrc and index into network order
        ivStore[4] = (ssrc shr 24 and 0xff xor saltKey[4].toInt()).toByte()
        ivStore[5] = (ssrc shr 16 and 0xff xor saltKey[5].toInt()).toByte()
        ivStore[6] = (ssrc shr 8 and 0xff xor saltKey[6].toInt()).toByte()
        ivStore[7] = (ssrc and 0xff xor saltKey[7].toInt()).toByte()
        ivStore[8] = saltKey[8]
        ivStore[9] = saltKey[9]
        ivStore[10] = (index shr 24 and 0xff xor saltKey[10].toInt()).toByte()
        ivStore[11] = (index shr 16 and 0xff xor saltKey[11].toInt()).toByte()
        ivStore[12] = (index shr 8 and 0xff xor saltKey[12].toInt()).toByte()
        ivStore[13] = (index and 0xff xor saltKey[13].toInt()).toByte()
        ivStore[15] = 0
        ivStore[14] = 0

        // Encrypted part excludes fixed header (8 bytes)
        val payloadOffset = 8
        val payloadLength = pkt.length - payloadOffset
        cipherCtr!!.process(pkt.buffer, pkt.offset + payloadOffset, payloadLength, ivStore)
    }

    /**
     * Performs F8 Mode AES encryption/decryption
     *
     * @param pkt the RTP packet to be encrypted/decrypted
     */
    private fun processPacketAesF8(pkt: ByteArrayBuffer?, idx: Int) {
        // 4 bytes of the iv are zero
        // the first byte of the RTP header is not used.
        var index = idx
        ivStore[0] = 0
        ivStore[1] = 0
        ivStore[2] = 0
        ivStore[3] = 0

        // Need the encryption flag
        index = index or -0x80000000

        // set the index and the encrypt flag in network order into IV
        ivStore[4] = (index shr 24).toByte()
        ivStore[5] = (index shr 16).toByte()
        ivStore[6] = (index shr 8).toByte()
        ivStore[7] = index.toByte()

        // The fixed header follows and fills the rest of the IV
        System.arraycopy(pkt!!.buffer, pkt.offset, ivStore, 8, 8)

        // Encrypted part excludes fixed header (8 bytes), index (4 bytes), and
        // authentication tag (variable according to policy)
        val payloadOffset = 8
        val payloadLength = pkt.length - (4 + policy!!.authTagLength)
        cipherF8!!.process(pkt.buffer, pkt.offset + payloadOffset, payloadLength, ivStore)
    }

    /**
     * Transform a SRTCP packet into a RTCP packet. The method is called when an
     * SRTCP packet was received. Operations done by the method include:
     * authentication check, packet replay check and decryption. Both encryption
     * and authentication functionality can be turned off as long as the
     * SrtpPolicy used in this SrtpCryptoContext requires no encryption and no
     * authentication. Then the packet will be sent out untouched. However, this
     * is not encouraged. If no SRTCP feature is enabled, then we shall not use
     * SRTP TransformConnector. We should use the original method (RTPManager
     * managed transportation) instead.
     *
     * @param pkt the received RTCP packet
     * @return `SrtpErrorStatus#OK` if the packet can be accepted or another
     * error status if authentication or replay check failed
     */
    @Synchronized
    fun reverseTransformPacket(pkt: ByteArrayBuffer): SrtpErrorStatus {
        var decrypt = false
        val tagLength = policy!!.authTagLength

        /* Too short to be a valid SRTCP packet */
        if (!SrtcpPacketUtils.validatePacketLength(pkt, tagLength))
            return SrtpErrorStatus.INVALID_PACKET

        val indexEflag = SrtcpPacketUtils.getIndex(pkt, tagLength)
        if (indexEflag and -0x80000000 == -0x80000000) decrypt = true
        val index = indexEflag and -0x80000000.inv().toInt()
        var err: SrtpErrorStatus

        /* Replay control */
        if (checkReplay(index).also { err = it } != SrtpErrorStatus.OK) {
            return err
        }

        /* Authenticate the packet */
        if (policy.authType != SrtpPolicy.NULL_AUTHENTICATION) {
            // get original authentication data and store in tempStore
            pkt.readRegionToBuff(pkt.length - tagLength, tagLength, tempStore)

            // Shrink packet to remove the authentication tag and index
            // because this is part of authenticated data
            pkt.shrink(tagLength + 4)

            // compute, then save authentication in tagStore
            authenticatePacketHmac(pkt, indexEflag)
            var nonEqual = 0
            for (i in 0 until tagLength) {
                nonEqual = nonEqual or (tempStore[i].toInt() xor tagStore!![i].toInt())
            }
            if (nonEqual != 0)
                return SrtpErrorStatus.AUTH_FAIL
        }
        if (decrypt) {
            /* Decrypt the packet using Counter Mode encryption */
            if (policy.encType == SrtpPolicy.AESCM_ENCRYPTION
                    || policy.encType == SrtpPolicy.TWOFISH_ENCRYPTION) {
                processPacketAesCm(pkt, index)
            } else if (policy.encType == SrtpPolicy.AESF8_ENCRYPTION
                    || policy.encType == SrtpPolicy.TWOFISHF8_ENCRYPTION) {
                processPacketAesF8(pkt, index)
            }
        }
        update(index)
        return SrtpErrorStatus.OK
    }

    /**
     * Transform a RTP packet into a SRTP packet. The method is called when a normal RTP packet
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
        var encrypt = false
        /* Encrypt the packet using Counter Mode encryption */
        if (policy!!.encType == SrtpPolicy.AESCM_ENCRYPTION
                || policy.encType == SrtpPolicy.TWOFISH_ENCRYPTION) {
            processPacketAesCm(pkt, sentIndex)
            encrypt = true
        } else if (policy.encType == SrtpPolicy.AESF8_ENCRYPTION
                || policy.encType == SrtpPolicy.TWOFISHF8_ENCRYPTION) {
            processPacketAesF8(pkt, sentIndex)
            encrypt = true
        }
        var index = 0
        if (encrypt)
            index = sentIndex or -0x80000000

        // Grow packet storage in one step
        pkt.grow(4 + policy.authTagLength)

        // Authenticate the packet
        // The authenticate method gets the index via parameter and stores
        // it in network order in rbStore variable.
        if (policy.authType != SrtpPolicy.NULL_AUTHENTICATION) {
            authenticatePacketHmac(pkt, index)
            pkt.append(rbStore, 4)
            pkt.append(tagStore, policy.authTagLength)
        }
        sentIndex++
        sentIndex = sentIndex and -0x80000000.inv().toInt() // clear possible overflow
        return SrtpErrorStatus.OK
    }

    /**
     * Logs the current state of the replay window, for debugging purposes.
     */
    private fun logReplayWindow(newIdx: Long) {
        Timber.d("Updated replay window with %s. %s", newIdx,
                SrtpPacketUtils.formatReplayWindow(receivedIndex.toLong(), replayWindow, REPLAY_WINDOW_SIZE))
    }

    /**
     * Updates the SRTP packet index. The method is called after all checks were successful.
     *
     * @param index index number of the accepted packet
     */
    private fun update(index: Int) {
        val delta = index - receivedIndex

        /* update the replay bit mask */
        if (delta >= REPLAY_WINDOW_SIZE) {
            replayWindow = 1
            receivedIndex = index
        } else if (delta > 0) {
            replayWindow = replayWindow shl delta
            replayWindow = replayWindow or 1L
            receivedIndex = index
        } else {
            replayWindow = replayWindow or (1L shl -delta)
        }
        if (index % 500 == 0) logReplayWindow(index.toLong())
    }
}