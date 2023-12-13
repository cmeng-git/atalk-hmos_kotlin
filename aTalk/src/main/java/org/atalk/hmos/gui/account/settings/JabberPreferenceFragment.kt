/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account.settings

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.text.TextUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator
import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl
import net.java.sip.communicator.plugin.jabberaccregwizz.AccountRegistrationImpl
import net.java.sip.communicator.service.protocol.EncodingsRegistrationUtil
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.SecurityAccountRegistration
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.menu.MainMenuActivity
import org.atalk.hmos.gui.settings.util.SummaryMapper
import org.atalk.util.MediaType
import timber.log.Timber

/**
 * Preferences fragment for Jabber settings. It maps Jabber specific properties to the
 * [Preference]s. Reads from and stores them inside [JabberAccountRegistration].
 *
 * This is an instance of the accountID properties from Account Setting... preference editing. These changes
 * will be merged with the original mAccountProperties and saved to database in doCommitChanges()
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 * @author MilanKral
 */
class JabberPreferenceFragment
/**
 * Creates new instance of `JabberPreferenceFragment`
 */
    : AccountPreferenceFragment(R.xml.acc_jabber_preferences) {

    /**
     * Current user userName which is being edited.
     */
    private var userNameEdited: String? = null

    /**
     * user last entered userName to check for anymore new changes in userName
     */
    private var userNameLastEdited: String? = null

    /**
     * Returns jabber registration wizard.
     *
     * @return jabber registration wizard.
     */
    private fun getJbrWizard(): AccountRegistrationImpl {
        return wizard as AccountRegistrationImpl
    }

    /**
     * {@inheritDoc}
     */
    override val encodingsRegistration: EncodingsRegistrationUtil
        get() {
            return jbrReg.getEncodingsRegistration()
        }

    /**
     * {@inheritDoc}
     */
    override val securityRegistration: SecurityAccountRegistration
        get() {
            return jbrReg.getSecurityRegistration()
        }

    /**
     * {@inheritDoc}
     */
    override fun onInitPreferences() {
        // val wizard = getJbrWizard()
        jbrReg = getJbrWizard().accountRegistration

        // User name and password
        userNameEdited = jbrReg.mUserID
        userNameLastEdited = userNameEdited
        mEditor.putString(P_KEY_USER_ID, userNameEdited)
        mEditor.putString(P_KEY_PASSWORD, jbrReg.password)
        mEditor.putBoolean(P_KEY_STORE_PASSWORD, jbrReg.isRememberPassword())
        mEditor.putString(P_KEY_DNSSEC_MODE, jbrReg.dnssMode)
        mEditor.apply()
    }

    /**
     * {@inheritDoc}
     */
    override fun onPreferencesCreated() {
        dnssecModeLP = findPreference(P_KEY_DNSSEC_MODE)
        if (MainMenuActivity.disableMediaServiceOnFault) {
            findPreference<CheckBoxPreference>(P_KEY_CALL_ENCRYPT)?.isEnabled = false
            findPreference<CheckBoxPreference>(P_KEY_TELEPHONY)?.isEnabled = false
            findPreference<CheckBoxPreference>(P_KEY_AUDIO_ENC)?.isEnabled = false
            findPreference<CheckBoxPreference>(P_KEY_VIDEO_ENC)?.isEnabled = false
        } else {
            // Audio,video and security are optional and should be present in settings XML to be handled
            val audioEncPreference = findPreference<Preference>(P_KEY_AUDIO_ENC)
            if (audioEncPreference != null) {
                audioEncPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference: Preference? ->
                    startEncodingActivity(MediaType.AUDIO)
                    true
                }
            }
            val videoEncPreference = findPreference<Preference>(P_KEY_VIDEO_ENC)
            if (videoEncPreference != null) {
                videoEncPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference: Preference? ->
                    startEncodingActivity(MediaType.VIDEO)
                    true
                }
            }
            val encryptionOnOff = findPreference<Preference>(P_KEY_CALL_ENCRYPT)
            if (encryptionOnOff != null) {
                encryptionOnOff.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference: Preference? ->
                    startSecurityActivity()
                    true
                }
            }
        }
        (findPreference(P_KEY_PROXY_CONFIG) as Preference?)!!.setOnPreferenceClickListener { pref ->
            val boshProxy = BoshProxyDialog(mActivity, jbrReg)
            boshProxy.setTitle(R.string.service_gui_JBR_ICE_SUMMARY)
            boshProxy.show()
            true
        }

//        findPreference(P_KEY_USER_ID).setOnPreferenceClickListener(preference -> {
//            startAccountEditor();
//            return true;
//        });
    }
    //    private void startAccountEditor()
    //    {
    //        // Create AccountLoginFragment fragment
    //        String login = "swordfish@atalk.sytes.net";
    //        String password = "1234";
    //
    //        Intent intent = new Intent(mActivity, AccountLoginActivity.class);
    //        intent.putExtra(AccountLoginFragment.ARG_USERNAME, login);
    //        intent.putExtra(AccountLoginFragment.ARG_PASSWORD, password);
    //        startActivity(intent);
    //    }
    /**
     * Starts the [SecurityActivity] to edit account's security preferences
     */
    private fun startSecurityActivity() {
        val intent = Intent(mActivity, SecurityActivity::class.java)
        val securityRegistration = securityRegistration
                ?: throw NullPointerException()
        intent.putExtra(SecurityActivity.EXTR_KEY_SEC_REGISTRATION, securityRegistration)
        getSecurityRegistration.launch(intent)
    }

    /**
     * Handles [SecurityActivity] results
     */
    private var getSecurityRegistration = registerForActivityResult(ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val hasChanges = data!!.getBooleanExtra(SecurityActivity.EXTR_KEY_HAS_CHANGES, false)
            if (!hasChanges) return@registerForActivityResult
            val secReg = data.getSerializableExtra(SecurityActivity.EXTR_KEY_SEC_REGISTRATION) as SecurityAccountRegistration
            val myReg = securityRegistration
            myReg.setCallEncryption(secReg.isCallEncryption())
            myReg.setEncryptionProtocol(secReg.getEncryptionProtocol())
            myReg.setEncryptionProtocolStatus(secReg.getEncryptionProtocolStatus())
            myReg.setSipZrtpAttribute(secReg.isSipZrtpAttribute())
            myReg.setZIDSalt(secReg.getZIDSalt())
            myReg.setDtlsCertSa(secReg.getDtlsCertSa())
            myReg.setSavpOption(secReg.getSavpOption())
            myReg.setSDesCipherSuites(secReg.getSDesCipherSuites())
            uncommittedChanges = true
        }
    }

    /**
     * Starts the [MediaEncodingActivity] in order to edit encoding properties.
     *
     * @param mediaType indicates if AUDIO or VIDEO encodings will be edited
     */
    private fun startEncodingActivity(mediaType: MediaType) {
        val intent = Intent(mActivity, MediaEncodingActivity::class.java)
        intent.putExtra(MediaEncodingActivity.ENC_MEDIA_TYPE_KEY, mediaType)
        val encodingsRegistration = encodingsRegistration
                ?: throw NullPointerException()
        intent.putExtra(MediaEncodingActivity.EXTRA_KEY_ENC_REG, encodingsRegistration)
        getEncodingRegistration.launch(intent)
    }

    /**
     * Handles [MediaEncodingActivity]results
     */
    private var getEncodingRegistration = registerForActivityResult(ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val hasChanges = data!!.getBooleanExtra(MediaEncodingActivity.EXTRA_KEY_HAS_CHANGES, false)
            if (!hasChanges) return@registerForActivityResult
            val encReg = data.getSerializableExtra(MediaEncodingActivity.EXTRA_KEY_ENC_REG) as EncodingsRegistrationUtil
            val myReg = encodingsRegistration
            myReg.setOverrideEncodings(encReg.isOverrideEncodings())
            myReg.setEncodingProperties(encReg.getEncodingProperties())
            uncommittedChanges = true
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun mapSummaries(summaryMapper: SummaryMapper) {
        val emptyStr = emptyPreferenceStr

        // User name and password
        summaryMapper.includePreference(findPreference(P_KEY_USER_ID), emptyStr)
        summaryMapper.includePreference(findPreference(P_KEY_PASSWORD), emptyStr, SummaryMapper.PasswordMask())
        summaryMapper.includePreference(findPreference(P_KEY_DNSSEC_MODE), emptyStr)
    }

    /**
     * {@inheritDoc}
     */
    override fun onSharedPreferenceChanged(shPrefs: SharedPreferences, key: String) {
        // Check to ensure a valid key before proceed
        if ((findPreference(key) as Preference?) == null) return
        super.onSharedPreferenceChanged(shPrefs, key)
        when (key) {
            P_KEY_USER_ID -> {
                getUserConfirmation(shPrefs)
            }

            P_KEY_PASSWORD -> {
                val password = shPrefs.getString(P_KEY_PASSWORD, null)
                // Timber.d("Change password: %s <= %s", password, jbrReg.getPassword());
                if (password == jbrReg.password) {
                    return
                }

                // Change password if user is registered.
                val pps = accountID!!.protocolProvider as ProtocolProviderServiceJabberImpl?
                if (pps!!.changePasswordOnServer(password)) {
                    jbrReg.password = password
                }
                // Reset to old valid password if online change password failed;
                // so actual valid login password is shown in next 'Account setting...' edit.
                else {
                    mEditor.putString(P_KEY_PASSWORD, jbrReg.password)
                    mEditor.apply()
                }
            }

            P_KEY_STORE_PASSWORD -> {
                jbrReg.setRememberPassword(shPrefs.getBoolean(P_KEY_STORE_PASSWORD, false))
            }

            P_KEY_DNSSEC_MODE -> {
                val dnssecMode = shPrefs.getString(P_KEY_DNSSEC_MODE,
                        resources.getStringArray(R.array.dnssec_Mode_value)[0])
                jbrReg.dnssMode = dnssecMode
            }
        }
    }

    /**
     * Warn and get user confirmation if changes of userName will lead to removal of any old messages
     * of the old account. It also checks for valid userName entry.
     *
     * @param shPrefs SharedPreferences
     */
    private fun getUserConfirmation(shPrefs: SharedPreferences) {
        val userName = shPrefs.getString(P_KEY_USER_ID, null)
        if (!TextUtils.isEmpty(userName) && userName!!.contains("@")) {
            val editedAccUid = jbrReg.accountUniqueID
            if (userNameEdited == userName) {
                jbrReg.mUserID = userName
                userNameLastEdited = userName
            } else if (userNameLastEdited != userName) {
                val mhs = MessageHistoryActivator.messageHistoryService
                val msgCount = mhs.getMessageCountForAccountUuid(editedAccUid!!)
                if (msgCount > 0) {
                    val msgPrompt = aTalkApp.getResString(R.string.service_gui_USERNAME_CHANGE_WARN,
                            userName, msgCount, userNameEdited)
                    DialogActivity.showConfirmDialog(aTalkApp.globalContext,
                            aTalkApp.getResString(R.string.service_gui_WARNING), msgPrompt,
                            aTalkApp.getResString(R.string.service_gui_PROCEED), object : DialogActivity.DialogListener {
                        override fun onConfirmClicked(dialog: DialogActivity): Boolean {
                            jbrReg.mUserID= userName
                            userNameLastEdited = userName
                            return true
                        }

                        override fun onDialogCancelled(dialog: DialogActivity) {
                            jbrReg.mUserID = userNameEdited
                            userNameLastEdited = userNameEdited
                            mEditor.putString(P_KEY_USER_ID, jbrReg.mUserID)
                            mEditor.apply()
                        }
                    })
                } else {
                    jbrReg.mUserID = userName
                    userNameLastEdited = userName
                }
            }
        } else {
            userNameLastEdited = userNameEdited
            aTalkApp.showToastMessage(R.string.service_gui_USERNAME_NULL)
        }
    }

    /**
     * This is executed when the user press BackKey. Signin with modification will merge the change properties
     * i.e jbrReg.getAccountProperties() with the accountID mAccountProperties before saving to SQL database
     */
    override fun doCommitChanges() {
        try {
            val accWizard = getJbrWizard()
            accWizard.isModification = true
            accWizard.signin(jbrReg.mUserID!!, jbrReg.password, jbrReg.accountProperties)
        } catch (e: OperationFailedException) {
            Timber.e("Failed to store account modifications: %s", e.localizedMessage)
        }
    }

    companion object {
        // PreferenceScreen and PreferenceCategories for Account Settings...
        private const val P_KEY_TELEPHONY = "pref.screen.jbr.telephony"
        private const val P_KEY_CALL_ENCRYPT = "pref_key_enable_encryption"
        private const val P_KEY_AUDIO_ENC = "pref_cat_enc_audio"
        private const val P_KEY_VIDEO_ENC = "pref_cat_enc_video"

        // Account Settings
        private const val P_KEY_USER_ID = "pref_key_user_id"
        private const val P_KEY_PASSWORD = "pref_key_password"
        private const val P_KEY_STORE_PASSWORD = "pref_key_store_password"
        private const val P_KEY_DNSSEC_MODE = "dns.DNSSEC_MODE"

        // Proxy
        private const val P_KEY_PROXY_CONFIG = "Bosh_Configuration"

        /*
     * A new instance of AccountID and is not the same as accountID.
     * Defined as static, otherwise it may get clear onActivityResult - on some android devices
     */
        lateinit var jbrReg: JabberAccountRegistration
    }
}
