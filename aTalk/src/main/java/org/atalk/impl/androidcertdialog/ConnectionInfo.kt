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

import android.content.DialogInterface
import android.os.Bundle
import android.text.Html
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import net.java.sip.communicator.impl.certificate.CertificateServiceImpl
import net.java.sip.communicator.plugin.jabberaccregwizz.JabberAccountRegistrationActivator
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.OperationSetConnectionInfo
import net.java.sip.communicator.service.protocol.OperationSetTLS
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.TransportProtocol
import net.java.sip.communicator.util.account.AccountUtils.storedAccounts
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.service.osgi.OSGiActivity
import java.util.*

/**
 * Setting screen which displays protocolProvider connection info and servers SSL Certificates.
 * Unregistered accounts without any approved certificates are not shown.
 *
 * a. Short click to display the SSL certificate for registered account.
 * b. Long Click to delete any manually approved self signed SSL certificates if any.
 *
 * @author Eng Chong Meng
 */
class ConnectionInfo : OSGiActivity() {
    /**
     * List of AccountId to its array of manual approved self signed certificates
     */
    private val certificateEntry = Hashtable<AccountID, List<String>>()

    /*
     * Adapter used to display connection info and SSL certificates for all protocolProviders.
     */
    private var mCIAdapter: ConnectionInfoAdapter? = null
    private var cvs: CertificateServiceImpl? = null

    /*
     * X509 SSL Certificate view on dialog window
     */
    private var viewCertDialog: X509CertificateView? = null
    private var deleteDialog: AlertDialog? = null

    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.list_layout)
        val providerKeysList = findViewById<ListView>(R.id.list)
        cvs = JabberAccountRegistrationActivator.certificateService as CertificateServiceImpl?
        val accountIDS = initCertificateEntry()
        mCIAdapter = ConnectionInfoAdapter(accountIDS)
        providerKeysList.adapter = mCIAdapter
        providerKeysList.onItemClickListener = OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long -> showSslCertificate(position) }
        providerKeysList.onItemLongClickListener = OnItemLongClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
            showSslCertificateDeleteAlert(position)
            true
        }
    }

    /*
     * Dismissed any opened dialog to avoid window leaks on rotation
     */
    override fun onPause() {
        super.onPause()
        if (viewCertDialog != null && viewCertDialog!!.isShowing) {
            viewCertDialog!!.dismiss()
            viewCertDialog = null
        }
        if (deleteDialog != null && deleteDialog!!.isShowing) {
            deleteDialog!!.dismiss()
            deleteDialog = null
        }
    }

    /**
     * Init and populate AccountIDs with all registered accounts or
     * account has manual approved self-signed certificate.
     *
     * @return a list of all accountIDs for display list
     */
    private fun initCertificateEntry(): List<AccountID> {
        certificateEntry.clear()
        val certEntries = cvs!!.allServerAuthCertificates

        // List of the accounts for display
        val accountIDS = ArrayList<AccountID>()

        // List of all local stored accounts
        val userAccounts = storedAccounts

        /*
         * Iterate all the local stored accounts; add to display list if there are associated user approved
         * certificates, or the account is registered for SSL certificate display.
         */
        for (accountId in userAccounts) {
            val pps = accountId!!.protocolProvider
            val serviceName = accountId.service
            val sslCerts = ArrayList<String>()
            for (certEntry in certEntries) {
                if (certEntry.contains(serviceName!!)) {
                    sslCerts.add(certEntry)
                }
            }
            if (sslCerts.size != 0 || pps != null && pps.isRegistered) {
                accountIDS.add(accountId)
                certificateEntry[accountId] = sslCerts

                // remove any assigned certs from certEntries
                for (cert in sslCerts) {
                    certEntries.remove(cert)
                }
            }
        }
        return accountIDS
    }

    /**
     * Displays SSL Certificate information.
     * Invoked when user short clicks a link in the editor pane.
     *
     * @param position the position of `SSL Certificate` in adapter's list which will be displayed.
     */
    private fun showSslCertificate(position: Int) {
        val accountId = mCIAdapter!!.getItem(position)
        val pps = accountId.protocolProvider
        if (pps != null && pps.isRegistered) {
            val opSetTLS = pps.getOperationSet(OperationSetTLS::class.java)
            val chain = opSetTLS!!.getServerCertificates()
            if (chain != null) {
                viewCertDialog = X509CertificateView(this, chain)
                viewCertDialog!!.show()
            } else aTalkApp.showToastMessage(aTalkApp.getResString(R.string.service_gui_callinfo_TLS_CERTIFICATE_CONTENT) + ": null!")
        } else {
            aTalkApp.showToastMessage(R.string.plugin_certconfig_SHOW_CERT_EXCEPTION, accountId)
        }
    }

    /**
     * Displays alert asking user if he wants to delete the selected SSL Certificate. (Long click)
     * Delete both the serviceName certificate and the _xmpp-client.serviceName
     *
     * @param position the position of `SSL Certificate` in adapter's list which has to be used in the alert.
     */
    private fun showSslCertificateDeleteAlert(position: Int) {
        val accountId = mCIAdapter!!.getItem(position)
        val certs = certificateEntry[accountId]!!
        // Just display the SSL certificate info if none to delete
        if (certs.isEmpty()) {
            showSslCertificate(position)
            return
        }
        val bareJid = accountId.accountJid
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.service_gui_settings_SSL_CERTIFICATE_REMOVE)
                .setMessage(getString(R.string.service_gui_settings_SSL_CERTIFICATE_PURGE, bareJid))
                .setPositiveButton(R.string.service_gui_YES) { dialog: DialogInterface, which: Int ->
                    for (certEntry in certs) cvs!!.removeCertificateEntry(certEntry)

                    // Update the adapter Account list after a deletion.
                    mCIAdapter!!.setAccountIDs(initCertificateEntry())
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.service_gui_NO) { dialog: DialogInterface, which: Int -> dialog.dismiss() }
        deleteDialog = builder.create()
        deleteDialog!!.show()
    }

    /**
     * Constructs the connection info text.
     * Do not use ISAddress.getHostName(); this may make a network access for a reverse IP lookup
     * and cause NetworkOnMainThreadException
     */
    private fun loadDetails(pps: ProtocolProviderService): String {
        val buff = StringBuilder()
        buff.append("<html><body>")

        // Protocol name
        buff.append(getItemString(getString(R.string.service_gui_settings_PROTOCOL), pps.protocolName))

        // Server address and port
        val opSetConnInfo = pps.getOperationSet(OperationSetConnectionInfo::class.java)
        if (opSetConnInfo != null) {
            val isAddress = opSetConnInfo.getServerAddress()
            // buff.append(getItemString(getString(R.string.service_gui_settings_ADDRESS),
            //      (ISAddress == null) ? "" : ISAddress.getHostName()));
            buff.append(getItemString(getString(R.string.service_gui_settings_ADDRESS),
                    if (isAddress == null) "" else isAddress.hostString))
            buff.append(getItemString(getString(R.string.service_gui_settings_PORT),
                    isAddress?.port?.toString() ?: ""))
        }

        // Transport protocol
        val preferredTransport = pps.transportProtocol
        if (preferredTransport != TransportProtocol.UNKNOWN)
            buff.append(getItemString(getString(R.string.service_gui_callinfo_CALL_TRANSPORT), preferredTransport.toString()))

        // TLS information
        val opSetTLS = pps.getOperationSet(OperationSetTLS::class.java)
        if (opSetTLS != null) {
            buff.append(getItemString(getString(R.string.service_gui_callinfo_TLS_PROTOCOL), opSetTLS.getProtocol()))
            buff.append(getItemString(getString(R.string.service_gui_callinfo_TLS_CIPHER_SUITE), opSetTLS.getCipherSuite()))
            buff.append("<b><u><font color=\"aqua\">")
                    .append(getString(R.string.service_gui_callinfo_VIEW_CERTIFICATE))
                    .append("</font></u></b>")
        }
        buff.append("</body></html>")
        return buff.toString()
    }

    /**
     * Returns an HTML string corresponding to the given labelText and infoText,
     * that could be easily added to the information text pane.
     *
     * @param labelText the label text that would be shown in bold
     * @param info the info text that would be shown in plain text
     * @return the newly constructed HTML string
     */
    private fun getItemString(labelText: String, info: String?): String {
        var infoText = info
        if (StringUtils.isNotEmpty(infoText)) {
            if (infoText!!.contains("TLS")) infoText = "<small>$infoText</small>"
        } else infoText = ""
        return "&#8226; <b>$labelText</b> : $infoText<br/>"
    }

    /**
     * Adapter which displays Connection Info for list of `ProtocolProvider`s.
     */
    internal inner class ConnectionInfoAdapter
    /**
     * Creates a new instance of `SslCertificateListAdapter`.
     *
     * @param accountIDS the list of `AccountId`s for which connection info and
     * certificates will be displayed by this adapter.
     */
    (
            /**
             * List of `AccountID` for which the connection info and certificates are being displayed.
             */
            private var accountIDs: List<AccountID>) : BaseAdapter() {
        /**
         * Call to update the new List item; notify data change after update
         *
         * @param accountIDS the list of `AccountId`s for which connection info and
         * certificates will be displayed by this adapter.
         */
        fun setAccountIDs(accountIDS: List<AccountID>) {
            accountIDs = accountIDS
            notifyDataSetChanged()
        }

        /**
         * {@inheritDoc}
         */
        override fun getCount(): Int {
            return accountIDs.size
        }

        /**
         * {@inheritDoc}
         */
        override fun getItem(position: Int): AccountID {
            return accountIDs[position]
        }

        /**
         * {@inheritDoc}
         */
        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        /**
         * {@inheritDoc}
         */
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            // Keeps reference to avoid future findViewById()
            var cView = convertView
            val ciViewHolder: CIViewHolder
            if (cView == null) {
                cView = layoutInflater.inflate(R.layout.connection_info_list_row, parent, false)
                ciViewHolder = CIViewHolder()
                ciViewHolder.protocolService = cView.findViewById(R.id.protocolProvider)
                ciViewHolder.connectionInfo = cView.findViewById(R.id.connectionInfo)
                cView.tag = ciViewHolder
            } else {
                ciViewHolder = cView.tag as CIViewHolder
            }
            val accountId = getItem(position)
            val accountName = "<u>$accountId</u>"
            ciViewHolder.protocolService!!.text = Html.fromHtml(accountName)
            val detailInfo: String
            val pps = accountId.protocolProvider
            detailInfo = if (pps != null) {
                loadDetails(accountId.protocolProvider!!)
            } else {
                getString(R.string.service_gui_ACCOUNT_UNREGISTERED, "&#8226; ")
            }
            ciViewHolder.connectionInfo!!.text = Html.fromHtml(detailInfo)
            return cView!!
        }
    }

    private class CIViewHolder {
        var protocolService: TextView? = null
        var connectionInfo: TextView? = null
    }
}