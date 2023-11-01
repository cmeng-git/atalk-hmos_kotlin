/*
 * Copyright @ 2015 Atlassian Pty Ltd
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
 */
package org.atalk.impl.neomedia.transform.srtp

import java.util.*

/**
 * The `SrtpContextFactory` creates the initial crypto contexts for RTP
 * and RTCP encryption using the supplied key material.
 *
 * @author Bing SU (nova.su@gmail.com)
 */
class SrtpContextFactory(sender: Boolean, masterKey: ByteArray?, masterSalt: ByteArray?, srtpPolicy: SrtpPolicy,
                         srtcpPolicy: SrtpPolicy) {
    /**
     * Master encryption key
     */
    private val masterKey: ByteArray

    /**
     * Master salting key
     */
    private val masterSalt: ByteArray

    /**
     * The indicator which determines whether this instance is used by SRTP
     * senders (`true`) or receiver (`false`).
     */
    private val sender: Boolean

    /**
     * Encryption / Authentication policy for SRTP.
     */
    private val srtpPolicy: SrtpPolicy

    /**
     * Encryption / Authentication policy for SRTCP.
     */
    private val srtcpPolicy: SrtpPolicy

    /**
     * Construct a SrtpTransformEngine based on given master encryption key, master salt key and Srtp/Srtcp policy.
     *
     * @param sender `true` if the new instance is to be used by an SRTP sender;
     * `false` if the new instance is to be used by an SRTP receiver
     * @param masterKey the master encryption key
     * @param masterSalt the master salt key
     * @param srtpPolicy SRTP policy
     * @param srtcpPolicy SRTCP policy
     */
    init {
        val encKeyLength = srtpPolicy.encKeyLength
        require(encKeyLength == srtcpPolicy.encKeyLength) { "srtpPolicy.getEncKeyLength() != srtcpPolicy.getEncKeyLength()" }
        if (masterKey != null) {
            require(masterKey.size == encKeyLength) { "masterK.length != encKeyLength (" + masterKey.size + " : " + encKeyLength + ")" }
            this.masterKey = ByteArray(encKeyLength)
            System.arraycopy(masterKey, 0, this.masterKey, 0, encKeyLength)
        } else {
            require(encKeyLength == 0) { "null masterK but encKeyLength != 0" }
            this.masterKey = ByteArray(0)
        }
        val saltKeyLength = srtpPolicy.saltKeyLength
        require(saltKeyLength == srtcpPolicy.saltKeyLength) { "srtpPolicy.getSaltKeyLength() != srtcpPolicy.getSaltKeyLength()" }
        if (masterSalt != null) {
            require(masterSalt.size == saltKeyLength) { "masterS.length != saltKeyLength (" + masterSalt.size + " : " + saltKeyLength + ")" }
            this.masterSalt = ByteArray(saltKeyLength)
            System.arraycopy(masterSalt, 0, this.masterSalt, 0, saltKeyLength)
        } else {
            require(saltKeyLength == 0) { "null masterS but saltKeyLength != 0" }
            this.masterSalt = ByteArray(0)
        }
        this.sender = sender
        this.srtpPolicy = srtpPolicy
        this.srtcpPolicy = srtcpPolicy
    }

    /**
     * Close the transformer engine.
     *
     * The close functions closes all stored default crypto state.
     */
    fun close() {
        Arrays.fill(masterKey, 0.toByte())
        Arrays.fill(masterSalt, 0.toByte())
    }

    /**
     * Derives a new SrtpCryptoContext for use with a new SSRC. The method returns a new SrtpCryptoContext
     * initialized with the master key, master salt, and sender state of this factory.
     * Before the application can use this SrtpCryptoContext it must call the deriveSrtpKeys method.
     *
     * @param ssrc The SSRC for this context
     * @param roc The Roll-Over-Counter for this context
     * @return a new SrtpCryptoContext with all relevant data set.
     */
    fun deriveContext(ssrc: Int, roc: Int): SrtpCryptoContext {
        return SrtpCryptoContext(sender, ssrc, roc, masterKey, masterSalt, srtpPolicy)
    }

    /**
     * Derives a new SrtcpCryptoContext for use with a new SSRC. The method returns a new SrtcpCryptoContext
     * initialized with the master key and master salt of this factory.
     * Before the application can use this SrtpCryptoContext it must call the deriveSrtcpKeys method.
     *
     * @param ssrc The sender SSRC for this context
     * @return a new SrtcpCryptoContext with all relevant data set.
     */
    fun deriveControlContext(ssrc: Int): SrtcpCryptoContext {
        return SrtcpCryptoContext(ssrc, masterKey, masterSalt, srtcpPolicy)
    }
}