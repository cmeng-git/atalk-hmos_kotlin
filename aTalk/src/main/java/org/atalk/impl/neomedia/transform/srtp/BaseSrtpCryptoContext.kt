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

import org.atalk.impl.neomedia.transform.srtp.crypto.Aes
import org.atalk.impl.neomedia.transform.srtp.crypto.HmacSha1
import org.atalk.impl.neomedia.transform.srtp.crypto.OpenSslWrapperLoader
import org.atalk.impl.neomedia.transform.srtp.crypto.SrtpCipherCtr
import org.atalk.impl.neomedia.transform.srtp.crypto.SrtpCipherCtrJava
import org.atalk.impl.neomedia.transform.srtp.crypto.SrtpCipherCtrOpenSsl
import org.atalk.impl.neomedia.transform.srtp.crypto.SrtpCipherF8
import org.atalk.util.ByteArrayBuffer
import org.bouncycastle.crypto.Mac
import org.bouncycastle.crypto.engines.TwofishEngine
import org.bouncycastle.crypto.macs.SkeinMac

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
 * @author MilanKral
 */
open class BaseSrtpCryptoContext {
    /**
     * implements the counter cipher mode for RTP according to RFC 3711
     */
    protected val cipherCtr: SrtpCipherCtr?

    /**
     * F8 mode cipher
     */
    protected val cipherF8: SrtpCipherF8?

    /**
     * Temp store.
     */
    protected val ivStore = ByteArray(16)

    /**
     * The HMAC object we used to do packet authentication
     */
    protected val mac // used for various HMAC computations
            : Mac?

    /**
     * Encryption / Authentication policy for this session
     */
    protected val policy: SrtpPolicy?

    /**
     * Temp store.
     */
    protected val rbStore = ByteArray(4)

    /**
     * Bit mask for replay check
     */
    protected var replayWindow: Long = 0

    /**
     * Derived session salting key
     */
    protected val saltKey: ByteArray?
    /**
     * Gets the SSRC of this SRTP cryptographic context
     *
     * @return the SSRC of this SRTP cryptographic context
     */
    /**
     * RTP/RTCP SSRC of this cryptographic context
     */
    val ssrc: Int

    /**
     * Temp store.
     */
    protected val tagStore: ByteArray?

    /**
     * this is a working store, used by some methods to avoid new operations the methods must use
     * this only to store results for immediate processing
     */
    protected val tempStore = ByteArray(100)

    protected constructor(ssrc: Int) {
        this.ssrc = ssrc
        cipherCtr = null
        cipherF8 = null
        mac = null
        policy = null
        saltKey = null
        tagStore = null
    }

    protected constructor(ssrc: Int, masterK: ByteArray?, masterS: ByteArray?, policy: SrtpPolicy) {
        this.ssrc = ssrc
        this.policy = policy
        val encKeyLength = policy.encKeyLength
        if (masterK != null) {
            require(masterK.size == encKeyLength) { "masterK.length != encKeyLength" }
        } else {
            require(encKeyLength == 0) { "null masterK but encKeyLength != 0" }
        }
        val saltKeyLength = policy.saltKeyLength
        if (masterS != null) {
            require(masterS.size == saltKeyLength) { "masterS.length != saltKeyLength" }
        } else {
            require(saltKeyLength == 0) { "null masterS but saltKeyLength != 0" }
        }
        var cipherCtr: SrtpCipherCtr? = null
        var cipherF8: SrtpCipherF8? = null
        var saltKey: ByteArray? = null
        when (policy.encType) {
            SrtpPolicy.NULL_ENCRYPTION -> {}
            SrtpPolicy.AESF8_ENCRYPTION -> {
                cipherF8 = SrtpCipherF8(Aes.createBlockCipher(encKeyLength))
                // use OpenSSL if available and AES128 is in use
                cipherCtr = if (OpenSslWrapperLoader.isLoaded && (encKeyLength == 16 || encKeyLength == 24 || encKeyLength == 32)) {
                    SrtpCipherCtrOpenSsl()
                } else {
                    SrtpCipherCtrJava(Aes.createBlockCipher(encKeyLength))
                }
                saltKey = ByteArray(saltKeyLength)
            }
            SrtpPolicy.AESCM_ENCRYPTION -> {
                cipherCtr = if (OpenSslWrapperLoader.isLoaded && (encKeyLength == 16 || encKeyLength == 24 || encKeyLength == 32)) {
                    SrtpCipherCtrOpenSsl()
                } else {
                    SrtpCipherCtrJava(Aes.createBlockCipher(encKeyLength))
                }
                saltKey = ByteArray(saltKeyLength)
            }
            SrtpPolicy.TWOFISHF8_ENCRYPTION -> {
                cipherF8 = SrtpCipherF8(TwofishEngine())
                cipherCtr = SrtpCipherCtrJava(TwofishEngine())
                saltKey = ByteArray(saltKeyLength)
            }
            SrtpPolicy.TWOFISH_ENCRYPTION -> {
                cipherCtr = SrtpCipherCtrJava(TwofishEngine())
                saltKey = ByteArray(saltKeyLength)
            }
        }
        this.cipherCtr = cipherCtr
        this.cipherF8 = cipherF8
        this.saltKey = saltKey
        val mac: Mac?
        val tagStore: ByteArray?
        when (policy.authType) {
            SrtpPolicy.HMACSHA1_AUTHENTICATION -> {
                mac = HmacSha1.createMac()
                tagStore = ByteArray(mac.macSize)
            }
            SrtpPolicy.SKEIN_AUTHENTICATION -> {
                tagStore = ByteArray(policy.authTagLength)
                mac = SkeinMac(SkeinMac.SKEIN_512, tagStore.size * 8)
            }
            SrtpPolicy.NULL_AUTHENTICATION -> {
                mac = null
                tagStore = null
            }
            else -> {
                mac = null
                tagStore = null
            }
        }
        this.mac = mac
        this.tagStore = tagStore
    }

    /**
     * Authenticates a packet. Calculated authentication tag is returned/stored in [.tagStore]
     * .
     *
     * @param pkt the RTP packet to be authenticated
     * @param rocIn Roll-Over-Counter
     */
    @Synchronized
    protected fun authenticatePacketHmac(pkt: ByteArrayBuffer, rocIn: Int) {
        mac!!.update(pkt.buffer, pkt.offset, pkt.length)
        rbStore[0] = (rocIn shr 24).toByte()
        rbStore[1] = (rocIn shr 16).toByte()
        rbStore[2] = (rocIn shr 8).toByte()
        rbStore[3] = rocIn.toByte()
        mac.update(rbStore, 0, rbStore.size)
        mac.doFinal(tagStore, 0)
    }

    /**
     * Closes this crypto context. The close functions deletes key data and performs a cleanup of
     * this crypto context. Clean up key data, maybe this is the second time. However, sometimes we
     * cannot know if the CryptoContext was used and the application called deriveSrtpKeys(...).
     */
    @Synchronized
    fun close() {
        /* TODO, clean up ciphers and mac. */
    }

    /**
     * Gets the authentication tag length of this SRTP cryptographic context
     *
     * @return the authentication tag length of this SRTP cryptographic context
     */
    val authTagLength: Int
        get() = policy!!.authTagLength

    companion object {
        /**
         * The replay check windows size.
         */
        const val REPLAY_WINDOW_SIZE: Long = 64
    }
}