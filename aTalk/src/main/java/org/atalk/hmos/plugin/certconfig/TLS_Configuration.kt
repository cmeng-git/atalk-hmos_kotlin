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
package org.atalk.hmos.plugin.certconfig

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.Spinner
import net.java.sip.communicator.service.certificate.CertificateConfigEntry
import net.java.sip.communicator.service.certificate.CertificateService
import org.atalk.hmos.R
import org.atalk.service.osgi.OSGiFragment
import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.security.Security

/**
 * Advanced configuration form to define client TLS certificate templates.
 *
 * @author Eng Chong Meng
 */
class TLS_Configuration : OSGiFragment(), View.OnClickListener, CompoundButton.OnCheckedChangeListener, AdapterView.OnItemSelectedListener, PropertyChangeListener, CertConfigEntryDialog.OnFinishedCallback {
    private lateinit var cvs: CertificateService

    /**
     * Certificate spinner list for selection
     */
    private var mCertEntry: CertificateConfigEntry? = null
    private val mCertList = ArrayList<String>()
    private lateinit var certAdapter: ArrayAdapter<String>
    private lateinit var certSpinner: Spinner

    /**
     * A map of <row></row>, CertificateConfigEntry>
     */
    private val mCertEntryList = LinkedHashMap<Int, CertificateConfigEntry>()
    private lateinit var chkEnableOcsp: CheckBox
    private lateinit var cmdRemove: Button
    private lateinit var cmdEdit: Button
    private lateinit var mContext: Context

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mContext = context!!
        cvs = CertConfigActivator.certService
        CertConfigActivator.configService.addPropertyChangeListener(this)
        val content = inflater.inflate(R.layout.cert_tls_config, container, false)
        val chkEnableRevocationCheck = content.findViewById<CheckBox>(R.id.cb_crl)
        chkEnableRevocationCheck.setOnCheckedChangeListener(this)
        chkEnableOcsp = content.findViewById(R.id.cb_ocsp)
        chkEnableOcsp.setOnCheckedChangeListener(this)
        certSpinner = content.findViewById(R.id.cboCert)
        initCertSpinner()
        val mAdd = content.findViewById<Button>(R.id.cmd_add)
        mAdd.setOnClickListener(this)
        cmdRemove = content.findViewById(R.id.cmd_remove)
        cmdRemove.setOnClickListener(this)
        cmdEdit = content.findViewById(R.id.cmd_edit)
        cmdEdit.setOnClickListener(this)
        return content
    }

    private fun initCertSpinner() {
        initCertList()
        certAdapter = ArrayAdapter(mContext, R.layout.simple_spinner_item, mCertList)
        certAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        certSpinner.adapter = certAdapter
        certSpinner.onItemSelectedListener = this
    }

    private fun initCertList() {
        mCertList.clear()
        val certEntries = cvs.getClientAuthCertificateConfigs()
        for (idx in certEntries.indices) {
            val entry = certEntries[idx]
            mCertList.add(entry.toString())
            mCertEntryList[idx] = entry
        }
    }

    override fun onClick(v: View) {
        val dialog: CertConfigEntryDialog
        val ft = parentFragmentManager.beginTransaction()
        ft.addToBackStack(null)
        when (v.id) {
            R.id.cmd_add -> {
                dialog = CertConfigEntryDialog.getInstance(CertificateConfigEntry.CERT_NONE, this)
                dialog.show(ft, "CertConfigEntry")
            }
            R.id.cmd_remove -> if (mCertEntry != null) {
                Timber.d("Certificate Entry removed: %s", mCertEntry!!.id)
                CertConfigActivator.certService.removeClientAuthCertificateConfig(mCertEntry!!.id)
            }
            R.id.cmd_edit -> if (mCertEntry != null) {
                dialog = CertConfigEntryDialog.getInstance(mCertEntry, this)
                dialog.show(ft, "CertConfigEntry")
            }
        }
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        val enabled = java.lang.Boolean.valueOf(isChecked).toString()
        when (buttonView.id) {
            R.id.cb_crl -> {
                CertConfigActivator.configService.setProperty(
                        CertificateService.PNAME_REVOCATION_CHECK_ENABLED, isChecked)
                System.setProperty(CertificateService.SECURITY_CRLDP_ENABLE, enabled)
                System.setProperty(CertificateService.SECURITY_SSL_CHECK_REVOCATION, enabled)
                chkEnableOcsp.isEnabled = isChecked
            }
            R.id.cb_ocsp -> {
                CertConfigActivator.configService.setProperty(
                        CertificateService.PNAME_OCSP_ENABLED, isChecked)
                Security.setProperty(CertificateService.SECURITY_OCSP_ENABLE, enabled)
            }
        }
    }

    override fun onItemSelected(adapter: AdapterView<*>, view: View, pos: Int, id: Long) {
        if (adapter.id == R.id.cboCert) {
            certSpinner.setSelection(pos)
            mCertEntry = mCertEntryList[pos]
            cmdRemove.isEnabled = true
            cmdEdit.isEnabled = true
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}
    override fun propertyChange(evt: PropertyChangeEvent) {
        if (evt.propertyName.startsWith(CertificateService.PNAME_CLIENTAUTH_CERTCONFIG_BASE)) {
            initCertList()
            certAdapter.notifyDataSetChanged()
        }
    }

    override fun onCloseDialog(success: Boolean, entry: CertificateConfigEntry?) {
        if (success) {
            CertConfigActivator.certService.setClientAuthCertificateConfig(entry)
        }
    }
}