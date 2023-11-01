/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidcertdialog

import android.content.Context
import android.content.Intent
import net.java.sip.communicator.service.certificate.VerifyCertificateDialogService.VerifyCertificateDialog
import org.atalk.hmos.aTalkApp
import java.security.cert.Certificate

/**
 * Implementation of `VerifyCertificateDialog`. Serves as dialog data model for GUI components.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class VerifyCertDialog
/**
 * Creates new instance of `VerifyCertDialog`.
 *
 * @param requestId the request identifier.
 * @param cert the certificate to be verified
 * @param title dialog title
 * @param message dialog message
 */
(
        /**
         * Request id that can be used to retrieve this dialog from `CertificateDialogServiceImpl`.
         */
        private val requestId: Long,
        /**
         * Subject certificate.
         */
        val certificate: Certificate?,
        /**
         * Dialog title supplied by the service.
         */
        val title: String,
        /**
         * Dialog message supplied by the service.
         */
        val message: String?) : VerifyCertificateDialog {
    /**
     * Lock used to hold protocol thread until user decides what to do about the certificate.
     */
    private val finishLock = Object()
    /**
     * Returns certificate to be verified.
     *
     * @return the certificate to be verified.
     */
    /**
     * Returns dialog title.
     *
     * @return dialog title.
     */
    /**
     * Returns dialog message.
     *
     * @return dialog message.
     */

    /**
     * Holds trusted state.
     */
    private var trusted = false

    /**
     * Holds always trust state.
     */
    private var alwaysTrust = false

    /**
     * {@inheritDoc}
     */
    override fun setVisible(isVisible: Boolean) {
        if (!isVisible) {
            // Currently, method is always called with true  it's expected to block until dialog finishes its job.
            return
        }

        // starts the dialog and waits on the lock until finish
        val ctx = aTalkApp.globalContext
        val verifyIntent = VerifyCertificateActivity.createIntent(ctx, requestId)
        ctx.startActivity(verifyIntent)
        synchronized(finishLock) {
            try {
                finishLock.wait()
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun isTrusted(): Boolean {
        return trusted
    }

    /**
     * {@inheritDoc}
     */
    override fun isAlwaysTrustSelected(): Boolean {
        return alwaysTrust
    }

    /**
     * Notifies thread waiting for user decision.
     */
    fun notifyFinished() {
        synchronized(finishLock) { finishLock.notifyAll() }
    }

    /**
     * Sets the trusted flag.
     *
     * @param trusted `true` if subject certificate is trusted by the user.
     */
    fun setTrusted(trusted: Boolean) {
        this.trusted = trusted
    }

    /**
     * Sets always trusted flag.
     *
     * @param alwaysTrust `true` if user decided to always trust subject certificate.
     */
    fun setAlwaysTrust(alwaysTrust: Boolean) {
        this.alwaysTrust = alwaysTrust
    }
}