/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.certificate

import java.security.cert.Certificate

/**
 * Service that creates dialog that is shown to the user when a certificate verification failed.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
interface VerifyCertificateDialogService {
    /**
     * Creates the dialog.
     *
     * @param certs the certificates list
     * @param title The title of the dialog; when null the resource
     * `service.gui.CERT_DIALOG_TITLE` is loaded and used.
     * @param message A text that describes why the verification failed.
     */
    fun createDialog(certs: Array<Certificate>?, title: String?, message: String?): VerifyCertificateDialog?

    /**
     * The dialog implementers should return `VerifyCertificateDialog`.
     */
    interface VerifyCertificateDialog {
        /**
         * Shows or hides the dialog and waits for user response.
         *
         * @param isVisible whether we should show or hide the dialog.
         */
        fun setVisible(isVisible: Boolean)

        /**
         * Whether the user has accepted the certificate or not.
         *
         * @return whether the user has accepted the certificate or not.
         */
        fun isTrusted(): Boolean

        /**
         * Whether the user has selected to note the certificate so we always trust it.
         *
         * @return whether the user has selected to note the certificate so we always trust it.
         */
        fun isAlwaysTrustSelected(): Boolean
    }
}