/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.androidcertdialog

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.CompoundButton
import androidx.appcompat.app.AlertDialog
import org.atalk.hmos.R
import org.atalk.service.osgi.OSGiDialogFragment

/**
 * The dialog that displays certificate details. It allows user to mark the certificate as "always trusted".
 * The dialog details are created dynamically in html format. That's because it's certificate implementation
 * dependent. Parent `Activity` must implement `CertInfoDialogListener`.
 *
 * @author Eng Chong Meng
 */
class CertificateShowDialog : OSGiDialogFragment() {
    /**
     * Parent `Activity` listening for this dialog results.
     */
    private var listener: CertInfoDialogListener? = null

    /**
     * {@inheritDoc}
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val request = arguments!!.getLong(ARG_REQ_ID)
        val certDialog = CertificateDialogActivator.Companion.impl!!.retrieveDialog(request)
                ?: throw RuntimeException("No dialog model found for: $request")

        // Alert view and its title
        val b = AlertDialog.Builder(activity!!)
        b.setTitle(certDialog.title)

        // Certificate content in html format
        val contentView = activity!!.layoutInflater.inflate(R.layout.cert_info, null)
        val certView = contentView.findViewById<WebView>(R.id.certificateInfo)
        val settings = certView.settings
        settings.defaultFontSize = 10
        settings.defaultFixedFontSize = 10
        settings.builtInZoomControls = true
        val cert = certDialog.certificate
        val certInfo = X509CertificateView(activity)
        val certHtml = certInfo.toString(cert)
        certView.loadData(certHtml, "text/html", "utf-8")

        // Always trust checkbox
        val alwaysTrustBtn = contentView.findViewById<CompoundButton>(R.id.alwaysTrust)

        // Updates always trust property of dialog model
        alwaysTrustBtn.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean -> CertificateDialogActivator.Companion.getDialog(request)!!.setAlwaysTrust(isChecked) }

        // contentView.findViewById(R.id.dummyView).setVisibility(View.GONE);
        return b.setView(contentView).create()
    }

    /**
     * {@inheritDoc}
     */
    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        listener = activity as CertInfoDialogListener
    }

    /**
     * Method fired when continue button is clicked.
     *
     * @param v button's `View`.
     */
    fun onContinueClicked(v: View?) {
        listener!!.onDialogResult(true)
        dismiss()
    }

    /**
     * Method fired when cancel button is clicked.
     *
     * @param v button's `View`.
     */
    fun onCancelClicked(v: View?) {
        listener!!.onDialogResult(false)
        dismiss()
    }

    /**
     * Interface used to pass dialog result to parent `Activity`.
     */
    interface CertInfoDialogListener {
        /**
         * Fired when dialog is dismissed. Passes the result as an argument.
         *
         * @param continueAnyway `true` if continue anyway button was pressed, `false`
         * means that the dialog was discarded or cancel button was pressed.
         */
        fun onDialogResult(continueAnyway: Boolean)
    }

    companion object {
        /**
         * Argument holding request id used to retrieve dialog model.
         */
        private const val ARG_REQ_ID = "request_id"

        /**
         * Creates new instance of `CertificateInfoDialog` parametrized with given `requestId`.
         *
         * @param requestId identifier of dialog model managed by `CertificateDialogServiceImpl`
         * @return new instance of `CertificateInfoDialog` parametrized with given `requestId`.
         */
        fun createFragment(requestId: Long): CertificateShowDialog {
            val dialog = CertificateShowDialog()
            val args = Bundle()
            args.putLong(ARG_REQ_ID, requestId)
            dialog.arguments = args
            return dialog
        }
    }
}