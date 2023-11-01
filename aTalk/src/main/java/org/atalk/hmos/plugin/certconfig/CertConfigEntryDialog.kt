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
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import net.java.sip.communicator.impl.certificate.CertificateVerificationActivator
import net.java.sip.communicator.service.certificate.CertificateConfigEntry
import net.java.sip.communicator.service.certificate.CertificateService
import net.java.sip.communicator.service.certificate.KeyStoreType
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.impl.androidcertdialog.X509CertificateView
import org.atalk.persistance.FilePathHelper
import org.atalk.service.osgi.OSGiDialogFragment
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.Provider
import java.security.Security
import java.security.UnrecoverableEntryException
import javax.security.auth.callback.Callback
import javax.security.auth.callback.PasswordCallback
import javax.security.auth.callback.UnsupportedCallbackException

/**
 * Dialog window to add/edit client certificate configuration entries.
 *
 * @author Eng Chong Meng
 */
class CertConfigEntryDialog : OSGiDialogFragment(), View.OnClickListener, CompoundButton.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {
    private lateinit var txtDisplayName: EditText
    private lateinit var txtKeyStore: EditText
    private lateinit var txtKeyStorePassword: EditText
    private lateinit var chkSavePassword: CheckBox
    private lateinit var cmdShowCert: ImageButton
    private lateinit var cboKeyStoreType: Spinner
    private lateinit var cboAlias: Spinner
    private var mKeyStore: KeyStore? = null
    private val keyStoreTypes = ArrayList<KeyStoreType>()
    private var aliasAdapter: ArrayAdapter<String>? = null
    private val mAliasList = ArrayList<String>()
    private lateinit var mContext: Context
    private lateinit var cs: CertificateService

    // Stop cboKeyStoreType from triggering on first entry
    private var newInstall = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        cs = CertConfigActivator.certService
        mContext = context!!
        val contentView = inflater.inflate(R.layout.cert_tls_entry_config, container, false)
        if (dialog != null) {
            dialog!!.setTitle(R.string.plugin_certconfig_CERT_ENTRY_TITLE)
            val window = dialog!!.window
            if (window != null) {
                val displayRectangle = Rect()
                window.decorView.getWindowVisibleDisplayFrame(displayRectangle)
                contentView.minimumWidth = displayRectangle.width()
                contentView.minimumHeight = displayRectangle.height()
            }
        }
        txtDisplayName = contentView.findViewById(R.id.certDisplayName)
        txtKeyStore = contentView.findViewById(R.id.certFileName)
        val mGetContent = browseKeyStore()
        contentView.findViewById<View>(R.id.browse).setOnClickListener { mGetContent.launch("*/*") }

        // Init the keyStore Type Spinner
        cboKeyStoreType = contentView.findViewById(R.id.cboKeyStoreType)
        keyStoreTypes.add(KS_NONE)
        keyStoreTypes.addAll(cs.getSupportedKeyStoreTypes())
        val keyStoreAdapter = ArrayAdapter(mContext, R.layout.simple_spinner_item, keyStoreTypes)
        keyStoreAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        cboKeyStoreType.adapter = keyStoreAdapter
        cboKeyStoreType.onItemSelectedListener = this
        cboKeyStoreType.isEnabled = false
        txtKeyStorePassword = contentView.findViewById(R.id.keyStorePassword)
        txtKeyStorePassword.isEnabled = false
        val chkShowPassword = contentView.findViewById<CheckBox>(R.id.show_password)
        chkShowPassword.setOnCheckedChangeListener(this)
        chkSavePassword = contentView.findViewById(R.id.chkSavePassword)
        chkSavePassword.setOnCheckedChangeListener(this)
        cmdShowCert = contentView.findViewById(R.id.showCert)
        cmdShowCert.setOnClickListener(this)
        cmdShowCert.isEnabled = false
        cboAlias = contentView.findViewById(R.id.cboAlias)
        aliasAdapter = ArrayAdapter(mContext, R.layout.simple_spinner_item, mAliasList)
        aliasAdapter!!.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        cboAlias.adapter = aliasAdapter
        cboAlias.onItemSelectedListener = this
        cboAlias.isEnabled = false
        val cmdOk = contentView.findViewById<Button>(R.id.button_OK)
        cmdOk.setOnClickListener(this)
        val cmdCancel = contentView.findViewById<Button>(R.id.button_Cancel)
        cmdCancel.setOnClickListener(this)
        certConfigEntryInit()
        isCancelable = false
        return contentView
    }

    /**
     * Initialization the edited certificate or add new certificate
     */
    private fun certConfigEntryInit() {
        val displayName = mEntry!!.displayName
        txtDisplayName.setText(displayName)
        txtKeyStore.setText(mEntry!!.keyStore)

        // Init edited certificate parameters
        if (mEntry!!.keyStore != null) {
            txtKeyStorePassword.setText(mEntry!!.keyStorePassword)
            chkSavePassword.isChecked = mEntry!!.isSavePassword
            cboKeyStoreType.isEnabled = true
            cboKeyStoreType.setSelection(getIndexForType(mEntry!!.keyStoreType!!))
            initKeyStoreAlias()
            cboAlias.setSelection(getIndexForAlias(mEntry!!.alias))
            cboAlias.isEnabled = true
        }
    }

    /**
     * Initialize KeyStore on Edit or with new SSL client certificate installation.
     * a. loadKeyStore() must not run in UI Thread
     * b. loadAliases() must execute after loadKeyStore()
     * c. loadAliases() needs to be in UI thread as it access to UI components
     */
    private fun initKeyStoreAlias() {
        Thread {
            try {
                mKeyStore = loadKeyStore()
                runOnUiThread { loadAliases() }
            } catch (ex: KeyStoreException) {
                Timber.e(ex, "Load KeyStore Exception")
                aTalkApp.showGenericError(R.string.plugin_certconfig_INVALID_KEYSTORE_TYPE, ex.message)
            } catch (ex: UnrecoverableEntryException) {
                Timber.e(ex, "Load KeyStore Exception")
                aTalkApp.showGenericError(R.string.plugin_certconfig_INVALID_KEYSTORE_TYPE, ex.message)
            }
        }.start()
    }

    /**
     * Open the keystore selected by the user. If the type is set as PKCS#11,
     * the file is loaded as a provider. If the store is protected by a
     * password, the user is being asked by an authentication dialog.
     *
     * @return The loaded keystore
     * @throws KeyStoreException when something goes wrong
     * @throws UnrecoverableEntryException Happen in android Note-5 (not working)
     */
    @Throws(KeyStoreException::class, UnrecoverableEntryException::class)
    private fun loadKeyStore(): KeyStore? {
        val keyStore = ViewUtil.toString(txtKeyStore) ?: return null
        val f = File(keyStore)
        val keyStoreType = (cboKeyStoreType.selectedItem as KeyStoreType).name
        if (PKCS11 == keyStoreType) {
            val config = "name=${f.name}\nlibrary=${f.absoluteFile}"
            try {
                val pkcs11c = Class.forName("sun.security.pkcs11.SunPKCS11")
                val c = pkcs11c.getConstructor(InputStream::class.java)
                val p = c.newInstance(ByteArrayInputStream(config.toByteArray())) as Provider
                Security.insertProviderAt(p, 0)
            } catch (e: Exception) {
                Timber.e("Tried to access the PKCS11 provider on an unsupported platform or the load : %s", e.message)
            }
        }
        val ksBuilder = KeyStore.Builder.newInstance(keyStoreType, null, f,
                KeyStore.CallbackHandlerProtection { callbacks: Array<Callback?> ->
                    for (cb in callbacks) {
                        if (cb !is PasswordCallback) {
                            throw UnsupportedCallbackException(cb)
                        }
                        val ksPassword = ViewUtil.toCharArray(txtKeyStorePassword)
                        if (ksPassword != null || chkSavePassword.isChecked) {
                            cb.password = ksPassword
                        } else {
                            val authenticationWindowService = CertificateVerificationActivator.authenticationWindowService!!
                            val aw = authenticationWindowService.create(f.name, null, keyStoreType, false,
                                    false, null, null, null, null, null, null, null)!!
                            aw.isAllowSavePassword = PKCS11 != keyStoreType
                            aw.setVisible(true)
                            if (!aw.isCanceled) {
                                cb.password = aw.password
                                runOnUiThread {
                                    // if (!PKCS11.equals(keyStoreType) && aw.isRememberPassword()) {
                                    if (PKCS11 != keyStoreType) {
                                        txtKeyStorePassword.setText(String(aw.password!!))
                                    }
                                    chkSavePassword.isChecked = aw.isRememberPassword
                                }
                            } else {
                                throw IOException("User cancel")
                            }
                        }
                    }
                }
        )
        return ksBuilder.keyStore
    }

    /**
     * Load the certificate entry aliases from the chosen keystore.
     */
    private fun loadAliases() {
        if (mKeyStore == null) return
        mAliasList.clear()
        try {
            val e = mKeyStore!!.aliases()
            while (e.hasMoreElements()) {
                mAliasList.add(e.nextElement())
            }
            aliasAdapter!!.notifyDataSetChanged()
        } catch (e: KeyStoreException) {
            aTalkApp.showGenericError(R.string.plugin_certconfig_ALIAS_LOAD_EXCEPTION, e.message)
        }
    }

    /**
     * Opens a FileChooserDialog to let the user pick a keystore and tries to
     * auto-detect the keystore type using the file extension
     */
    private fun browseKeyStore(): ActivityResultLauncher<String> {
        return registerForActivityResult<String, Uri>(ActivityResultContracts.GetContent()) { fileUri: Uri? ->
            if (fileUri != null) {
                val inFile = File(FilePathHelper.getFilePath(mContext, fileUri)!!)
                if (inFile.exists()) {
                    newInstall = true
                    cboKeyStoreType.isEnabled = true
                    cboKeyStoreType.setSelection(0)
                    cboAlias.isEnabled = true
                    txtDisplayName.setText(inFile.name)
                    txtKeyStore.setText(inFile.absolutePath)
                    var resolved = false
                    for (kt in cs.getSupportedKeyStoreTypes()) {
                        for (ext in kt.fileExtensions) {
                            if (inFile.name.endsWith(ext)) {
                                cboKeyStoreType.setSelection(getIndexForType(kt))
                                resolved = true
                                break
                            }
                        }
                        if (resolved) {
                            break
                        }
                    }
                } else aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST)
            }
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.showCert -> showSelectedCertificate()
            R.id.button_OK -> {
                if (cboAlias.selectedItem == null || ViewUtil.toString(txtDisplayName) == null || ViewUtil.toString(txtKeyStore) == null) {
                    aTalkApp.showGenericError(R.string.plugin_certconfig_INCOMPLETE)
                    return
                }
                mEntry!!.displayName = ViewUtil.toString(txtDisplayName)
                mEntry!!.keyStore = ViewUtil.toString(txtKeyStore)
                mEntry!!.keyStoreType = cboKeyStoreType.selectedItem as KeyStoreType
                mEntry!!.alias = cboAlias.selectedItem.toString()
                if (chkSavePassword.isChecked) {
                    mEntry!!.isSavePassword =true
                    mEntry!!.keyStorePassword = ViewUtil.toString(txtKeyStorePassword)
                } else {
                    mEntry!!.isSavePassword = false
                    mEntry!!.keyStorePassword = null
                }
                closeDialog(true)
            }
            R.id.button_Cancel -> closeDialog(false)
        }
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        when (buttonView.id) {
            R.id.show_password -> ViewUtil.showPassword(txtKeyStorePassword, isChecked)
            R.id.chkSavePassword -> txtKeyStorePassword.isEnabled = (chkSavePassword.isChecked
                    && (cboKeyStoreType.selectedItem as KeyStoreType).hasKeyStorePassword()
                    )
        }
    }

    override fun onItemSelected(adapter: AdapterView<*>, view: View, position: Int, id: Long) {
        when (adapter.id) {
            R.id.cboKeyStoreType -> {
                // Proceed if new install or != NONE. First item always get selected onEntry
                val kt = cboKeyStoreType.selectedItem as KeyStoreType
                if (!newInstall || KS_NONE == kt) {
                    return
                }
                if (PKCS11 != kt.name) chkSavePassword.isEnabled = true
                txtKeyStorePassword.isEnabled = kt.hasKeyStorePassword() && chkSavePassword.isChecked
                initKeyStoreAlias()
            }
            R.id.cboAlias -> cmdShowCert.isEnabled = cboAlias.selectedItem != null
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}
    private fun showSelectedCertificate() {
        try {
            val chain = mKeyStore!!.getCertificateChain(cboAlias.selectedItem.toString())
            // must use getActivity: otherwise -> token null is not valid; is your activity running?
            val viewCertDialog = X509CertificateView(activity, chain)
            viewCertDialog.show()
        } catch (e1: KeyStoreException) {
            aTalkApp.showGenericError(R.string.plugin_certconfig_SHOW_CERT_EXCEPTION, e1.message)
        }
    }

    private fun getIndexForType(type: KeyStoreType): Int {
        for (i in keyStoreTypes.indices) {
            if (keyStoreTypes[i] == type) {
                return i
            }
        }
        return -1
    }

    private fun getIndexForAlias(alias: String?): Int {
        if (alias != null) {
            for (i in 0 until aliasAdapter!!.count) {
                if (alias == aliasAdapter!!.getItem(i)) return i
            }
        }
        return -1
    }

    private fun closeDialog(success: Boolean) {
        if (finishedCallback != null) finishedCallback!!.onCloseDialog(success, mEntry)
        dismiss()
    }

    interface OnFinishedCallback {
        fun onCloseDialog(success: Boolean, entry: CertificateConfigEntry?)
    }

    companion object {
        // ------------------------------------------------------------------------
        // Fields and services
        // ------------------------------------------------------------------------
        private val KS_NONE = KeyStoreType(aTalkApp.getResString(R.string.service_gui_LIST_NONE), arrayOf(""), false)
        private const val PKCS11 = "PKCS11"

        // Use Static scope to prevent crash on screen rotation
        private var mEntry: CertificateConfigEntry? = null

        /**
         * callback to caller with status and entry value
         */
        private var finishedCallback: OnFinishedCallback? = null
        fun getInstance(entry: CertificateConfigEntry?, callback: OnFinishedCallback?): CertConfigEntryDialog {
            val dialog = CertConfigEntryDialog()
            mEntry = entry
            finishedCallback = callback
            return dialog
        }
    }
}