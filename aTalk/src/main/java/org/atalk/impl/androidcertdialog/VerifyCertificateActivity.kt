/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidcertdialog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.TextView
import org.atalk.hmos.R
import org.atalk.impl.androidcertdialog.CertificateShowDialog.CertInfoDialogListener
import org.atalk.service.osgi.OSGiActivity
import timber.log.Timber

/**
 * Activity displays the certificate to the user and asks him whether to trust the certificate.
 * It also uses `CertificateInfoDialog` to display detailed information about the certificate.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class VerifyCertificateActivity : OSGiActivity(), CertInfoDialogListener {
    /**
     * Request identifier used to retrieve dialog model.
     */
    private var requestId = 0L

    /**
     * Dialog model.
     */
    private lateinit var certDialog: VerifyCertDialog

    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = intent.getLongExtra(REQ_ID, -1)
        if (requestId == -1L) {
            return  // not serious enough to throw exception
            // throw new RuntimeException("No request id supplied");
        }
        certDialog = CertificateDialogActivator.getDialog(requestId)!!
        if (certDialog == null) {
            Timber.e("No dialog instance found for %s", requestId)
            finish()
            return
        }

        // Prevents from closing the dialog on outside window touch
        setFinishOnTouchOutside(false)
        setContentView(R.layout.verify_certificate)
        val msgView = findViewById<TextView>(R.id.message)
        msgView.text = Html.fromHtml(certDialog.message)
        title = certDialog.title
    }

    /**
     * Method fired when "show certificate info" button is clicked.
     *
     * @param v button's `View`
     */
    fun onShowCertClicked(v: View?) {
        CertificateShowDialog.createFragment(requestId).show(supportFragmentManager, "cert_info")
    }

    /**
     * Method fired when continue button is clicked.
     *
     * @param v button's `View`
     */
    fun onContinueClicked(v: View?) {
        certDialog.setTrusted(true)
        finish()
    }

    /**
     * Method fired when cancel button is clicked.
     *
     * @param v button's `View`
     */
    fun onCancelClicked(v: View?) {
        certDialog!!.setTrusted(false)
        finish()
    }

    /**
     * {@inheritDoc}
     */
    override fun onDestroy() {
        super.onDestroy()
        if (certDialog != null) certDialog!!.notifyFinished()
    }

    /**
     * {@inheritDoc}
     */
    override fun onDialogResult(continueAnyway: Boolean) {
        if (continueAnyway) {
            onContinueClicked(null)
        } else {
            onCancelClicked(null)
        }
    }

    companion object {
        /**
         * Request identifier extra key.
         */
        private const val REQ_ID = "request_id"

        /**
         * Creates new parametrized `Intent` for `VerifyCertificateActivity`.
         *
         * @param ctx Android context.
         * @param requestId request identifier of dialog model.
         * @return new parametrized `Intent` for `VerifyCertificateActivity`.
         */
        fun createIntent(ctx: Context?, requestId: Long?): Intent {
            val intent = Intent(ctx, VerifyCertificateActivity::class.java)
            intent.putExtra(REQ_ID, requestId)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent
        }
    }
}