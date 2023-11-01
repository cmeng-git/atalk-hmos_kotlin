/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidcertdialog

import net.java.sip.communicator.service.certificate.VerifyCertificateDialogService
import net.java.sip.communicator.service.certificate.VerifyCertificateDialogService.VerifyCertificateDialog
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import timber.log.Timber
import java.security.cert.Certificate

/**
 * Android implementation of `VerifyCertificateDialogService`.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class CertificateDialogServiceImpl : VerifyCertificateDialogService {
    /**
     * Maps request ids to `VerifyCertDialog` so that they can be retrieved by Android
     * `Activity` or `Fragments`.
     */
    private val requestMap = HashMap<Long?, VerifyCertDialog>()

    /**
     * {@inheritDoc}
     */
    override fun createDialog(certs: Array<Certificate>?, title: String?, message: String?): VerifyCertificateDialog {
        var mTitle = title
        if (mTitle == null) mTitle = aTalkApp.getResString(R.string.service_gui_CERT_DIALOG_TITLE)
        val requestId = System.currentTimeMillis()
        val verifyCertDialog = VerifyCertDialog(requestId, certs!![0], mTitle, message)
        requestMap[requestId] = verifyCertDialog
        Timber.d("%d creating dialog: %s", hashCode(), requestId)
        // Prevents from closing the dialog on outside touch
        return verifyCertDialog
    }

    /**
     * Retrieves the dialog for given `requestId`.
     *
     * @param requestId dialog's request identifier assigned during dialog creation.
     * @return the dialog for given `requestId`.
     */
    fun retrieveDialog(requestId: Long?): VerifyCertDialog? {
        Timber.d("%d getting dialog: %d", hashCode(), requestId)
        return requestMap[requestId]
    }
}