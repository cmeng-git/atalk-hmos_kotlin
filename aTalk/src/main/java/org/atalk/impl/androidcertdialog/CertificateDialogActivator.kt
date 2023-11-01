/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidcertdialog

import net.java.sip.communicator.service.certificate.VerifyCertificateDialogService
import net.java.sip.communicator.util.SimpleServiceActivator
import org.osgi.framework.BundleContext

/**
 * Activator of `VerifyCertificateDialogService` Android implementation.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class CertificateDialogActivator
/**
 * Creates a new instance of CertificateDialogActivator.
 */
    : SimpleServiceActivator<CertificateDialogServiceImpl?>(VerifyCertificateDialogService::class.java, "Android verify certificate service") {
    /**
     * {@inheritDoc}
     */
    override fun createServiceImpl(): CertificateDialogServiceImpl {
        impl = CertificateDialogServiceImpl()
        return impl!!
    }

    /**
     * {@inheritDoc}
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
        super.stop(bundleContext)
        // Clears service reference
        impl = null
    }

    companion object {
        /**
         * Cached instance of service impl.
         */
        var impl: CertificateDialogServiceImpl? = null

        /**
         * Gets the `VerifyCertDialog` for given `requestId`.
         *
         * @param requestId identifier of the request managed by `CertificateDialogServiceImpl`.
         * @return `VerifyCertDialog` for given `requestId` or `null` if service has been shutdown.
         */
        fun getDialog(requestId: Long?): VerifyCertDialog? {
            return if (impl != null) {
                impl!!.retrieveDialog(requestId)
            } else {
                null
            }
        }
    }
}