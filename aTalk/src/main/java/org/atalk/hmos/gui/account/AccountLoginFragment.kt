/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account

import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
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
import android.widget.EditText
import android.widget.Spinner
import net.java.sip.communicator.service.certificate.CertificateConfigEntry
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.gui.account.AccountLoginFragment.AccountLoginListener
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.hmos.plugin.certconfig.CertConfigActivator
import org.atalk.service.osgi.OSGiFragment

/**
 * The `AccountLoginFragment` is used for creating new account, but can be also used to obtain
 * user credentials. In order to do that parent `Activity` must implement [AccountLoginListener].
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AccountLoginFragment : OSGiFragment(), AdapterView.OnItemSelectedListener {
    /**
     * Contains all implementation specific properties that define the account.
     */
    protected var accountProperties = HashMap<String, String?>()

    /**
     * The listener parent Activity that will be notified when user enters login, password,
     * server overridden option and server parameters etc
     */
    private var loginListener: AccountLoginListener? = null
    private var mPasswordField: EditText? = null
    private var mServerIpField: EditText? = null
    private var mServerPortField: EditText? = null
    private var mShowPasswordCheckBox: CheckBox? = null
    private var mSavePasswordCheckBox: CheckBox? = null
    private var mClientCertCheckBox: CheckBox? = null
    private var mServerOverrideCheckBox: CheckBox? = null
    private var mIBRegistrationCheckBox: CheckBox? = null
    private var spinnerNwk: Spinner? = null
    private var spinnerDM: Spinner? = null
    private var spinnerCert: Spinner? = null
    private var mCertEntry: CertificateConfigEntry? = null

    /**
     * A map of <row></row>, CertificateConfigEntry>
     */
    private val mCertEntryList = LinkedHashMap<Int, CertificateConfigEntry>()
    private lateinit var mContext: Context

    /**
     * {@inheritDoc}
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
        if (context is AccountLoginListener) {
            loginListener = context
        } else {
            throw RuntimeException("Account login listener unspecified")
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onDetach() {
        super.onDetach()
        loginListener = null
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val content = inflater.inflate(R.layout.account_create_new, container, false)
        spinnerNwk = content.findViewById(R.id.networkSpinner)
        val adapterNwk = ArrayAdapter.createFromResource(mContext,
                R.array.networks_array, R.layout.simple_spinner_item)
        adapterNwk.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        spinnerNwk!!.adapter = adapterNwk
        spinnerDM = content.findViewById(R.id.dnssecModeSpinner)
        val adapterDM = ArrayAdapter.createFromResource(mContext,
                R.array.dnssec_Mode_name, R.layout.simple_spinner_item)
        adapterDM.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        spinnerDM!!.adapter = adapterDM
        mPasswordField = content.findViewById(R.id.passwordField)
        mShowPasswordCheckBox = content.findViewById(R.id.show_password)
        mSavePasswordCheckBox = content.findViewById(R.id.store_password)
        mIBRegistrationCheckBox = content.findViewById(R.id.ibRegistration)
        mClientCertCheckBox = content.findViewById(R.id.clientCertEnable)
        spinnerCert = content.findViewById(R.id.clientCertEntry)
        initCertList()
        mServerOverrideCheckBox = content.findViewById(R.id.serverOverridden)
        mServerIpField = content.findViewById(R.id.serverIpField)
        mServerPortField = content.findViewById(R.id.serverPortField)

        // Hide ip and port fields on first create
        updateCertEntryViewVisibility(false)
        updateViewVisibility(false)
        initializeViewListeners()
        initButton(content)
        val extras = arguments
        if (extras != null) {
            val username = extras.getString(ARG_USERNAME)
            if (StringUtils.isNotEmpty(username)) {
                ViewUtil.setTextViewValue(container!!, R.id.usernameField, username)
            }
            val password = extras.getString(ARG_PASSWORD)
            if (StringUtils.isNotEmpty(password)) {
                ViewUtil.setTextViewValue(content, R.id.passwordField, password)
            }
        }
        return content
    }

    /**
     * Certificate spinner list for selection
     */
    private fun initCertList() {
        val certList = ArrayList<String>()
        var certEntries: MutableList<CertificateConfigEntry> = ArrayList()
        val cvs = CertConfigActivator.certService
        if (cvs != null) // NPE from field
            certEntries = cvs.getClientAuthCertificateConfigs()
        certEntries.add(0, CertificateConfigEntry.CERT_NONE)
        for (idx in certEntries.indices) {
            val entry = certEntries[idx]
            certList.add(entry.toString())
            mCertEntryList[idx] = entry
        }
        val certAdapter = ArrayAdapter(mContext, R.layout.simple_spinner_item, certList)
        certAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        spinnerCert!!.adapter = certAdapter
        spinnerCert!!.onItemSelectedListener = this
    }

    private fun initializeViewListeners() {
        mShowPasswordCheckBox!!.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean -> ViewUtil.showPassword(mPasswordField, isChecked) }
        mClientCertCheckBox!!.setOnCheckedChangeListener{ buttonView: CompoundButton?, isChecked: Boolean -> updateCertEntryViewVisibility(isChecked) }
        mServerOverrideCheckBox!!.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean -> updateViewVisibility(isChecked) }
    }

    /**
     * Initializes the sign in button.
     */
    private fun initButton(content: View) {
        val signInButton = content.findViewById<Button>(R.id.buttonSignIn)
        signInButton.isEnabled = true
        signInButton.setOnClickListener { v: View? ->
            // Translate network label to network value
            val networkValues = resources.getStringArray(R.array.networks_array_values)
            val selectedNetwork = networkValues[spinnerNwk!!.selectedItemPosition]

            // Translate dnssecMode label to dnssecMode value
            val dnssecModeValues = resources.getStringArray(R.array.dnssec_Mode_value)
            val selectedDnssecMode = dnssecModeValues[spinnerDM!!.selectedItemPosition]
            accountProperties[ProtocolProviderFactory.DNSSEC_MODE] = selectedDnssecMode

            // cmeng - must trim all leading and ending whitespace character entered
            // get included by android from auto correction checker
            val userName = ViewUtil.toString(content.findViewById(R.id.usernameField))
            val password = ViewUtil.toString(mPasswordField)
            if (mClientCertCheckBox!!.isChecked && CertificateConfigEntry.CERT_NONE != mCertEntry) {
                accountProperties[ProtocolProviderFactory.CLIENT_TLS_CERTIFICATE] = mCertEntry.toString()
            } else {
                accountProperties[ProtocolProviderFactory.CLIENT_TLS_CERTIFICATE] = CertificateConfigEntry.CERT_NONE.toString()
            }
            val serverAddress = ViewUtil.toString(mServerIpField)
            val serverPort = ViewUtil.toString(mServerPortField)
            val savePassword = mSavePasswordCheckBox!!.isChecked.toString()
            accountProperties[ProtocolProviderFactory.PASSWORD_PERSISTENT] = savePassword
            val ibRegistration = mIBRegistrationCheckBox!!.isChecked.toString()
            accountProperties[ProtocolProviderFactory.IBR_REGISTRATION] = ibRegistration

            // Update server override options
            if (mServerOverrideCheckBox!!.isChecked && serverAddress != null && serverPort != null) {
                accountProperties[ProtocolProviderFactory.IS_SERVER_OVERRIDDEN] = "true"
                accountProperties[ProtocolProviderFactory.SERVER_ADDRESS] = serverAddress
                accountProperties[ProtocolProviderFactory.SERVER_PORT] = serverPort
            } else {
                accountProperties[ProtocolProviderFactory.IS_SERVER_OVERRIDDEN] = "false"
            }
            loginListener!!.onLoginPerformed(userName!!, password!!, selectedNetwork, accountProperties)
        }
        val cancelButton = content.findViewById<Button>(R.id.buttonCancel)
        cancelButton.setOnClickListener { v: View? -> activity!!.finish() }
    }

    private fun updateCertEntryViewVisibility(isEnabled: Boolean) {
        if (isEnabled) {
            spinnerCert!!.visibility = View.VISIBLE
        } else {
            spinnerCert!!.visibility = View.GONE
        }
    }

    private fun updateViewVisibility(IsServerOverridden: Boolean) {
        if (IsServerOverridden) {
            mServerIpField!!.visibility = View.VISIBLE
            mServerPortField!!.visibility = View.VISIBLE
        } else {
            mServerIpField!!.visibility = View.GONE
            mServerPortField!!.visibility = View.GONE
        }
    }

    /**
     * Stores the given `protocolProvider` data in the android system accounts.
     *
     * @param protocolProvider the `ProtocolProviderService`, corresponding to the account to store
     */
    private fun storeAndroidAccount(protocolProvider: ProtocolProviderService) {
        val accountProps = protocolProvider.accountID.accountProperties
        val username = accountProps[ProtocolProviderFactory.USER_ID]
        val account = Account(username, getString(R.string.ACCOUNT_TYPE))
        val extraData = Bundle()
        for (key in accountProps.keys) {
            extraData.putString(key, accountProps[key])
        }
        val am = AccountManager.get(activity)
        val accountCreated = am.addAccountExplicitly(account,
                accountProps[ProtocolProviderFactory.PASSWORD], extraData)
        val extras = arguments
        if (extras != null) {
            if (accountCreated) { // Pass the new account back to the account manager
                val response = extras.getParcelable<AccountAuthenticatorResponse>(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE)!!
                val result = Bundle()
                result.putString(AccountManager.KEY_ACCOUNT_NAME, username)
                result.putString(AccountManager.KEY_ACCOUNT_TYPE, getString(R.string.ACCOUNT_TYPE))
                result.putAll(extraData)
                response.onResult(result)
            }
            // TODO: notify about account authentication
            // finish()
        }
    }

    override fun onItemSelected(adapter: AdapterView<*>, view: View, pos: Int, id: Long) {
        if (adapter.id == R.id.clientCertEntry) {
            mCertEntry = mCertEntryList[pos]
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}

    /**
     * The interface is used to notify listener when user click the sign-in button.
     */
    interface AccountLoginListener {
        /**
         * Method is called when user click the sign in button.
         *
         * @param userName the login account entered by the user.
         * @param password the password entered by the user.
         * @param network the network name selected by the user.
         */
        fun onLoginPerformed(userName: String, password: String, network: String, accountProperties: MutableMap<String, String?>)
    }

    companion object {
        /**
         * The username property name.
         */
        const val ARG_USERNAME = "Username"

        /**
         * The password property name.
         */
        const val ARG_PASSWORD = "Password"

        /**
         * The password property name.
         */
        const val ARG_CLIENT_CERT = "ClientCert"

        /**
         * Creates new `AccountLoginFragment` with optionally filled login and password fields.
         *
         * @param login optional login text that will be filled on the form.
         * @param password optional password text that will be filled on the form.
         * @return new instance of parametrized `AccountLoginFragment`.
         */
        fun createInstance(login: String?, password: String?): AccountLoginFragment {
            val fragment = AccountLoginFragment()
            val args = Bundle()
            args.putString(ARG_USERNAME, login)
            args.putString(ARG_PASSWORD, password)
            return fragment
        }
    }
}