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
package org.atalk.impl.neomedia.transform.dtls

import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.tls.*
import timber.log.Timber
import java.io.IOException

/**
 * Bundles information such as key pair, hash function, fingerprint, etc. about
 * the certificate with which the local endpoint represented by this instance
 * authenticates its ends of DTLS sessions.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */

/**
 * Gets the certificate with which the local endpoint represented by this
 * instance authenticates its ends of DTLS sessions.
 *
 * @return the certificate with which the local endpoint represented by this
 * instance authenticates its ends of DTLS sessions.
 */
class CertificateInfo(
        /**
         * The private and public keys of [.certificate].
         */
        val keyPair: AsymmetricCipherKeyPair,

        /**
         * The certificate with which the local endpoint represented by this
         * instance authenticates its ends of DTLS sessions.
         */
        val certificate: Certificate,
        localFingerprintHashFunction: String, localFingerprint: String, timestamp: Long,
) {
    /**
     * Certificate Signature Algorithm.
     */
    val certificateType: Short

    /**
     * Gets the private and public keys of [.certificate].
     *
     * @return the private and public keys of [.certificate].
     */

    /**
     * The fingerprint of [.certificate].
     */
    val localFingerprint: String

    /**
     * The hash function of [.localFingerprint] (which is the same as the digest algorithm
     * of the signature algorithm of [.certificate] in accord with RFC 4572).
     */
    val localFingerprintHashFunction: String

    /**
     * The timestamp (in milliseconds of system time) of the generation of this `CertificateInfo`.
     */
    val timestamp: Long

    /**
     * Initializes a new `CertificateInfo` instance.
     *
     * @param keyPair the private and public keys of `certificate`
     * @param certificate the certificate with which the local endpoint
     * represented by the new instance is to authenticate its ends of DTLS sessions
     * @param localFingerprintHashFunction hash function of localFingerprint
     * @param localFingerprint of the certificate
     * @param timestamp (ms) of the generation of this CertificateInfo.
     */
    init {
        var certSA: Short = -1
        try {
            certSA = certificate.getCertificateAt(0).legacySignatureAlgorithm
        } catch (e: IOException) {
            Timber.e("Certificate SignatureAlgorithm: %s", e.message)
        }
        certificateType = SignatureAlgorithm.getClientCertificateType(certSA)
        this.localFingerprintHashFunction = localFingerprintHashFunction
        this.localFingerprint = localFingerprint
        this.timestamp = timestamp
    }
}