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
package org.atalk.hmos.gui.account.settings

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Spinner
import net.java.sip.communicator.plugin.jabberaccregwizz.JabberAccountRegistrationActivator
import net.java.sip.communicator.service.protocol.ProtocolProviderActivator
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.util.ViewUtil

/**
 * The Bosh-Proxy dialog is the one shown when the user clicks to set Bosh-Proxy preference in Account Settings...
 *
 * @author Eng Chong Meng
 */
class BoshProxyDialog(private val mContext: Context, private val jbrReg: JabberAccountRegistration) : Dialog(mContext), AdapterView.OnItemSelectedListener, TextWatcher, DialogActivity.DialogListener {
    private val mAccountUuid: String

    /**
     * The bosh proxy list view.
     */
    private lateinit var spinnerType: Spinner
    private lateinit var boshUrlSetting: View
    private lateinit var cbHttpProxy: CheckBox
    private lateinit var boshURL: EditText
    private lateinit var proxyHost: EditText
    private lateinit var proxyPort: EditText
    private lateinit var proxyUserName: EditText
    private lateinit var proxyPassword: EditText
    private lateinit var mApplyButton: Button

    /**
     * Flag indicating if there are uncommitted changes - need static to avoid clear by android OS
     */
    private var hasChanges = false
    private var mIndex = -1

    /**
     * Constructs the `Bosh-Proxy Dialog`.
     *
     * context the Context
     * jbrReg the JabberAccountRegistration
     */
    init {
        val editedAccUID = jbrReg.accountUniqueID
        val accManager = ProtocolProviderActivator.accountManager
        val factory = JabberAccountRegistrationActivator.jabberProtocolProviderFactory!!
        mAccountUuid = accManager!!.getStoredAccountUUID(factory, editedAccUID!!)!!
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.service_gui_settings_BOSH_PROXY)
        this.setContentView(R.layout.bosh_proxy_dialog)
        spinnerType = findViewById(R.id.boshProxyType)
        boshUrlSetting = findViewById(R.id.boshURL_setting)
        boshURL = findViewById(R.id.boshURL)
        boshURL.addTextChangedListener(this)

        cbHttpProxy = findViewById(R.id.cbHttpProxy)
        cbHttpProxy.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean -> hasChanges = true }

        proxyHost = findViewById(R.id.proxyHost)
        proxyHost.addTextChangedListener(this)

        proxyPort = findViewById(R.id.proxyPort)
        proxyPort.addTextChangedListener(this)

        proxyUserName = findViewById(R.id.proxyUsername)
        proxyUserName.addTextChangedListener(this)

        proxyPassword = findViewById(R.id.proxyPassword)
        proxyPassword.addTextChangedListener(this)

        initBoshProxyDialog()
        val showPassword = findViewById<CheckBox>(R.id.show_password)
        showPassword.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean -> ViewUtil.showPassword(proxyPassword, isChecked) }
        mApplyButton = findViewById(R.id.button_Apply)
        mApplyButton.setOnClickListener { v: View? ->
            if (hasChanges) {
                if (saveBoshProxySettings()) cancel()
            }
        }
        val cancelButton = findViewById<Button>(R.id.button_Cancel)
        cancelButton.setOnClickListener { v: View? -> checkUnsavedChanges() }
        setCanceledOnTouchOutside(false)
        hasChanges = false
    }

    override fun onBackPressed() {
        if (!hasChanges) {
            super.onBackPressed()
        } else {
            checkUnsavedChanges()
        }
    }

    /**
     * initialize the Bosh-proxy dialog with the db stored values
     */
    private fun initBoshProxyDialog() {
        val adapterType = ArrayAdapter.createFromResource(mContext, R.array.bosh_proxy_type, R.layout.simple_spinner_item)
        adapterType.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = adapterType
        spinnerType.onItemSelectedListener = this
        val type = jbrReg.proxyType
        if (!TextUtils.isEmpty(type)) {
            mIndex = 0
            while (mIndex < spinnerType.count) {
                if (spinnerType.getItemAtPosition(mIndex) == type) {
                    spinnerType.setSelection(mIndex)
                    onItemSelected(spinnerType, spinnerType.selectedView, mIndex, spinnerType.selectedItemId)
                    break
                }
                mIndex++
            }
        }
        boshURL.setText(jbrReg.getBoshUrl())
        cbHttpProxy.isChecked = jbrReg.isBoshHttpProxyEnabled()
        proxyHost.setText(jbrReg.proxyAddress)
        proxyPort.setText(jbrReg.proxyPort)
        proxyUserName.setText(jbrReg.proxyUserName)
        proxyPassword.setText(jbrReg.proxyPassword)
    }

    override fun onItemSelected(adapter: AdapterView<*>?, view: View, pos: Int, id: Long) {
        val type = adapter!!.getItemAtPosition(pos) as String
        if (BOSH == type) {
            boshUrlSetting.visibility = View.VISIBLE
        } else {
            boshUrlSetting.visibility = View.GONE
        }
        if (mIndex != pos) {
            mIndex = pos
            hasChanges = true
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        // Another interface callback
    }

    /**
     * Save user entered Bosh-Proxy settings.
     */
    private fun saveBoshProxySettings(): Boolean {
        val sType = spinnerType.selectedItem
        val type = sType?.toString() ?: NONE
        val boshUrl = ViewUtil.toString(boshURL)
        val host = ViewUtil.toString(proxyHost)
        val port = ViewUtil.toString(proxyPort)
        val userName = ViewUtil.toString(proxyUserName)
        val password = ViewUtil.toString(proxyPassword)

        val accPrefix = "$mAccountUuid."
        val configSrvc = ProtocolProviderActivator.getConfigurationService()
        configSrvc!!.setProperty(accPrefix + ProtocolProviderFactory.PROXY_TYPE, type)
        jbrReg.proxyType = type

        when (type) {
            BOSH -> {
                if (boshUrl == null) {
                    aTalkApp.showToastMessage(R.string.plugin_proxy_BOSHURL_NULL)
                    return false
                }
                configSrvc.setProperty(accPrefix + ProtocolProviderFactory.BOSH_URL, boshUrl)
                jbrReg.setBoshUrl(boshUrl)

                val isHttpProxy = cbHttpProxy.isChecked
                configSrvc.setProperty(accPrefix + ProtocolProviderFactory.BOSH_PROXY_HTTP_ENABLED, isHttpProxy)
                jbrReg.setBoshHttpProxyEnabled(isHttpProxy)

                // Continue with proxy settings checking if BOSH HTTP Proxy is enabled
                if (isHttpProxy) {
                    if (host == null || port == null) {
                        aTalkApp.showToastMessage(R.string.plugin_proxy_HOST_PORT_NULL)
                        return false
                    }
                }
            }
            HTTP, SOCKS4, SOCKS5 -> if (host == null || port == null) {
                aTalkApp.showToastMessage(R.string.plugin_proxy_HOST_PORT_NULL)
                return false
            }
            NONE -> {}
            else -> {}
        }

        // value if null will remove the parameter from DB
        configSrvc.setProperty(accPrefix + ProtocolProviderFactory.PROXY_ADDRESS, host)
        jbrReg.proxyAddress = host
        configSrvc.setProperty(accPrefix + ProtocolProviderFactory.PROXY_PORT, port)
        jbrReg.proxyPort = port
        configSrvc.setProperty(accPrefix + ProtocolProviderFactory.PROXY_USERNAME, userName)
        jbrReg.proxyUserName = userName
        configSrvc.setProperty(accPrefix + ProtocolProviderFactory.PROXY_PASSWORD, password)
        jbrReg.proxyPassword = password

        // remove obsolete setting from DB - to be remove on later version (>2.0.4)
        configSrvc.setProperty(accPrefix + ProtocolProviderFactory.IS_USE_PROXY, null)
        AccountPreferenceFragment.uncommittedChanges = true
        return true
    }

    /**
     * check for any unsaved changes and alert user
     */
    private fun checkUnsavedChanges() {
        if (hasChanges) {
            DialogActivity.showConfirmDialog(mContext,
                    R.string.service_gui_UNSAVED_CHANGES_TITLE,
                    R.string.service_gui_UNSAVED_CHANGES,
                    R.string.service_gui_SAVE, this)
        } else {
            cancel()
        }
    }

    /**
     * Fired when user clicks the dialog's confirm button.
     *
     * @param dialog source `DialogActivity`.
     */
    override fun onConfirmClicked(dialog: DialogActivity): Boolean {
        return mApplyButton.performClick()
    }

    /**
     * Fired when user dismisses the dialog.
     *
     * @param dialog source `DialogActivity`
     */
    override fun onDialogCancelled(dialog: DialogActivity) {
        cancel()
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable) {
        hasChanges = true
    }

    companion object {
        const val NONE = "NONE"
        const val BOSH = "BOSH"
        const val HTTP = "HTTP"
        private const val SOCKS4 = "SOCKS4"
        private const val SOCKS5 = "SOCKS5"
    }
}