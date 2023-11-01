/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account.settings

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import ch.imvs.sdes4j.srtp.SrtpCryptoSuite
import net.java.sip.communicator.service.protocol.SecurityAccountRegistration
import net.java.sip.communicator.util.UtilActivator
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.settings.util.SummaryMapper
import org.atalk.service.neomedia.SDesControl
import org.atalk.service.osgi.OSGiActivity
import org.atalk.service.osgi.OSGiPreferenceFragment
import java.io.Serializable

/**
 * The activity allows user to edit security part of account settings.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 * @author MilanKral
 */
open class SecurityActivity : OSGiActivity(), SecurityProtocolsDialogFragment.DialogClosedListener {
    /**
     * Fragment implementing [Preference] support in this activity.
     */
    private var securityFragment: SecurityPreferenceFragment? = null

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this
     * Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle). Note: Otherwise it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            securityFragment = SecurityPreferenceFragment()

            // Display the fragment as the main content.
            supportFragmentManager.beginTransaction().replace(android.R.id.content, securityFragment!!).commit()
        } else {
            securityFragment = supportFragmentManager.findFragmentById(android.R.id.content) as SecurityPreferenceFragment?
        }
    }

    override fun onDialogClosed(dialog: SecurityProtocolsDialogFragment) {
        securityFragment!!.onDialogClosed(dialog)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val result = Intent()
            result.putExtra(EXTR_KEY_SEC_REGISTRATION, securityFragment!!.securityReg)
            result.putExtra(EXTR_KEY_HAS_CHANGES, securityFragment!!.hasChanges)
            setResult(Activity.RESULT_OK, result)
            finish()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Fragment handles [Preference]s used for manipulating security settings.
     */
    class SecurityPreferenceFragment : OSGiPreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val summaryMapper = SummaryMapper()

        /**
         * Flag indicating if any changes have been made in this activity
         */
        var hasChanges = false
        lateinit var securityReg: SecurityAccountRegistration

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPrefTitle(R.string.service_gui_settings_MESSAGING_SECURITY_TITLE)
            securityReg = if (savedInstanceState == null) {
                val intent = activity!!.intent
                intent.getSerializableExtra(EXTR_KEY_SEC_REGISTRATION) as SecurityAccountRegistration
            } else {
                savedInstanceState[STATE_SEC_REG] as SecurityAccountRegistration
            }

            // Load the preferences from an XML resource - findPreference() to work properly
            addPreferencesFromResource(R.xml.acc_call_encryption_preferences)
            val encEnable = findPreference<CheckBoxPreference>(PREF_KEY_SEC_ENABLED)
            encEnable!!.isChecked = securityReg.isCallEncryption()

            // ZRTP
            val secProtocolsPref = findPreference<Preference>(PREF_KEY_SEC_PROTO_DIALOG)!!
            secProtocolsPref.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference: Preference? ->
                showEditSecurityProtocolsDialog()
                true
            }
            val zrtpAttr = findPreference<CheckBoxPreference>(PREF_KEY_SEC_SIPZRTP_ATTR)!!
            zrtpAttr.isChecked = securityReg.isSipZrtpAttribute()
            initResetZID()

            // DTLS_SRTP
            val dtlsPreference = findPreference<ListPreference>(PREF_KEY_SEC_DTLS_CERT_SA)!!
            val tlsCertSA = securityReg.getDtlsCertSa()
            dtlsPreference.value = tlsCertSA
            dtlsPreference.summary = tlsCertSA

            // SDES
            val savpPreference = findPreference<ListPreference>(PREF_KEY_SEC_SAVP_OPTION)!!
            savpPreference.setValueIndex(securityReg.getSavpOption())
            summaryMapper.includePreference(savpPreference, "")
            loadCipherSuites()
        }

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            outState.putSerializable(STATE_SEC_REG, securityReg)
        }

        override fun onResume() {
            super.onResume()
            updatePreferences()
            val shPrefs = preferenceScreen.sharedPreferences!!
            shPrefs.registerOnSharedPreferenceChangeListener(this)
            shPrefs.registerOnSharedPreferenceChangeListener(summaryMapper)
        }

        override fun onPause() {
            val shPrefs = preferenceScreen.sharedPreferences!!
            shPrefs.unregisterOnSharedPreferenceChangeListener(this)
            shPrefs.unregisterOnSharedPreferenceChangeListener(summaryMapper)
            super.onPause()
        }

        private fun initResetZID() {
            (findPreference(PREF_KEY_SEC_RESET_ZID) as Preference?)!!.setOnPreferenceClickListener { preference ->
                securityReg.randomZIDSalt()
                hasChanges = true
                Toast.makeText(activity, R.string.ZID_has_been_reset_toast, Toast.LENGTH_SHORT).show()
                true
            }
        }

        /**
         * Loads cipher suites
         */
        private fun loadCipherSuites() {
            // TODO: fix static values initialization and default ciphers
            var ciphers = securityReg.getSDesCipherSuites()
            if (ciphers == null) ciphers = defaultCiphers
            val cipherList = findPreference<MultiSelectListPreference>(PREF_KEY_SEC_CIPHER_SUITES)
            cipherList!!.entries = cryptoSuiteEntries
            cipherList.entryValues = cryptoSuiteEntries
            val selected: MutableSet<String>
            selected = HashSet()
            if (ciphers != null) {
                for (entry in cryptoSuiteEntries) {
                    if (ciphers.contains(entry)) selected.add(entry)
                }
            }
            cipherList.values = selected
        }

        /**
         * Shows the dialog that will allow user to edit security protocols settings
         */
        private fun showEditSecurityProtocolsDialog() {
            val securityDialog = SecurityProtocolsDialogFragment()
            val encryption = securityReg.getEncryptionProtocol()
            val encryptionStatus = securityReg.getEncryptionProtocolStatus()
            val args = Bundle()
            args.putSerializable(SecurityProtocolsDialogFragment.ARG_ENCRYPTION, encryption as Serializable)
            args.putSerializable(SecurityProtocolsDialogFragment.ARG_ENCRYPTION_STATUS, encryptionStatus as Serializable)
            securityDialog.arguments = args
            val ft = parentFragmentManager.beginTransaction()
            securityDialog.show(ft, "SecProtocolsDlgFragment")
        }

        fun onDialogClosed(dialog: SecurityProtocolsDialogFragment) {
            if (dialog.hasChanges()) {
                hasChanges = true
                dialog.commit(securityReg)
            }
            updateUsedProtocolsSummary()
        }

        /**
         * Refresh specifics summaries
         */
        private fun updatePreferences() {
            updateUsedProtocolsSummary()
            updateZRTpOptionSummary()
            updateCipherSuitesSummary()
        }

        /**
         * Sets the summary for protocols preference
         */
        private fun updateUsedProtocolsSummary() {
            val encMap = securityReg.getEncryptionProtocol()
            val encryptionsInOrder = ArrayList(encMap.keys)

            // ComparingInt is only available in API-24
            encryptionsInOrder.sortWith { s: String, s2: String -> encMap[s]!! - encMap[s2]!! }
            val encStatus = securityReg.getEncryptionProtocolStatus()
            val summary = StringBuilder()
            var idx = 1
            for (encryption in encryptionsInOrder) {
                if (java.lang.Boolean.TRUE == encStatus[encryption]) {
                    if (idx > 1) summary.append(" ")
                    summary.append(idx++).append(". ").append(encryption)
                }
            }
            var summaryStr = summary.toString()
            if (summaryStr.isEmpty()) {
                summaryStr = aTalkApp.getResString(R.string.service_gui_LIST_NONE)
            }
            val preference = findPreference<Preference>(PREF_KEY_SEC_PROTO_DIALOG)
            preference!!.summary = summaryStr
        }

        /**
         * Sets the ZRTP signaling preference summary
         */
        private fun updateZRTpOptionSummary() {
            val pref = findPreference<Preference>(PREF_KEY_SEC_SIPZRTP_ATTR)
            val isOn = pref!!.sharedPreferences!!.getBoolean(PREF_KEY_SEC_SIPZRTP_ATTR, true)
            val summary = if (isOn)
                aTalkApp.getResString(R.string.service_gui_SEC_ZRTP_SIGNALING_ON)
            else
                aTalkApp.getResString(R.string.service_gui_SEC_ZRTP_SIGNALING_OFF)
            pref.summary = summary
        }

        /**
         * Sets the cipher suites preference summary
         */
        private fun updateCipherSuitesSummary() {
            val ml = findPreference(PREF_KEY_SEC_CIPHER_SUITES) as MultiSelectListPreference?
            val summary = getCipherSuitesSummary(ml!!)
            ml.summary = summary
        }

        /**
         * Gets the summary text for given cipher suites preference
         *
         * @param ml the preference used for cipher suites setup
         * @return the summary text describing currently selected cipher suites
         */
        private fun getCipherSuitesSummary(ml: MultiSelectListPreference): String {
            val selected = ml.values
            val sb = StringBuilder()
            var firstElem = true
            for (entry in cryptoSuiteEntries) {
                if (selected.contains(entry)) {
                    if (firstElem) {
                        sb.append(entry)
                        firstElem = false
                    } else {
                        // separator must not have space. Otherwise, result in unknown crypto suite error.
                        sb.append(",")
                        sb.append(entry)
                    }
                }
            }
            if (selected.isEmpty()) sb.append(aTalkApp.getResString(R.string.service_gui_LIST_NONE))
            return sb.toString()
        }

        override fun onSharedPreferenceChanged(shPreferences: SharedPreferences, key: String) {
            hasChanges = true
            when (key) {
                PREF_KEY_SEC_ENABLED -> {
                    securityReg.setCallEncryption(shPreferences.getBoolean(PREF_KEY_SEC_ENABLED, true))
                }
                PREF_KEY_SEC_SIPZRTP_ATTR -> {
                    updateZRTpOptionSummary()
                    securityReg.setSipZrtpAttribute(shPreferences.getBoolean(key, true))
                }
                PREF_KEY_SEC_DTLS_CERT_SA -> {
                    val lp = findPreference<ListPreference>(key)!!
                    val certSA = lp.value
                    lp.summary = certSA
                    securityReg.setDtlsCertSa(certSA)
                }
                PREF_KEY_SEC_SAVP_OPTION -> {
                    val lp = findPreference<ListPreference>(key)!!
                    val idx = lp.findIndexOfValue(lp.value)
                    securityReg.setSavpOption(idx)
                }
                PREF_KEY_SEC_CIPHER_SUITES -> {
                    val ml = findPreference<MultiSelectListPreference>(key)!!
                    val summary = getCipherSuitesSummary(ml)
                    ml.summary = summary
                    securityReg.setSDesCipherSuites(summary)
                }
            }
        }

        companion object {
            private const val STATE_SEC_REG = "security_reg"
        }
    }

    companion object {
        /**
         * The intent's extra key for passing the [SecurityAccountRegistration]
         */
        const val EXTR_KEY_SEC_REGISTRATION = "secRegObj"

        /**
         * The intent's extra key of boolean indicating if any changes have been made by this activity
         */
        const val EXTR_KEY_HAS_CHANGES = "hasChanges"

        /**
         * Default value for cipher suites string property
         */
        private val defaultCiphers = UtilActivator.resources.getSettingsString(SDesControl.SDES_CIPHER_SUITES)
        private const val PREF_KEY_SEC_ENABLED = "pref_key_enable_encryption"
        private const val PREF_KEY_SEC_PROTO_DIALOG = "pref_key_enc_protos_dialog"
        private const val PREF_KEY_SEC_SIPZRTP_ATTR = "pref_key_enc_sipzrtp_attr"
        private const val PREF_KEY_SEC_CIPHER_SUITES = "pref_key_ecn_cipher_suites"
        private const val PREF_KEY_SEC_SAVP_OPTION = "pref_key_enc_savp_option"
        private const val PREF_KEY_SEC_RESET_ZID = "pref.key.zid.reset"
        private const val PREF_KEY_SEC_DTLS_CERT_SA = "pref_key_enc_dtls_cert_signature_algorithm"
        private val cryptoSuiteEntries = arrayOf(
                SrtpCryptoSuite.AES_256_CM_HMAC_SHA1_80,
                SrtpCryptoSuite.AES_256_CM_HMAC_SHA1_32,
                SrtpCryptoSuite.AES_192_CM_HMAC_SHA1_80,
                SrtpCryptoSuite.AES_192_CM_HMAC_SHA1_32,
                SrtpCryptoSuite.AES_CM_128_HMAC_SHA1_80,
                SrtpCryptoSuite.AES_CM_128_HMAC_SHA1_32,
                SrtpCryptoSuite.F8_128_HMAC_SHA1_80
        )
    }
}