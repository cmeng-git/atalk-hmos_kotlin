/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.certificate

import java.security.cert.CertificateException
import java.security.cert.X509Certificate

/**
 * Interface to verify X.509 certificate
 */
interface CertificateMatcher {
    /**
     * Implementations check whether one of the supplied identities is
     * contained in the certificate.
     *
     * @param identitiesToTest The that are compared against the certificate.
     * @param cert The X.509 certificate that was supplied by the server or
     * client.
     * @throws CertificateException When any certificate parsing fails.
     */
    @Throws(CertificateException::class)
    fun verify(identitiesToTest: Iterable<String?>?, cert: X509Certificate?)
}