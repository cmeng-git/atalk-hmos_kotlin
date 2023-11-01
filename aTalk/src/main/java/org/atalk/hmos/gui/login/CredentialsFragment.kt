/*
 * aTalk, HMOS XMPP VoIP and Instant Messaging client
 * Copyright 2014-2023 Eng Chong Meng
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
package org.atalk.hmos.gui.login

import android.os.Bundle
import android.text.Html
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import net.java.sip.communicator.service.certificate.CertificateConfigEntry
import org.atalk.hmos.R
import org.atalk.hmos.gui.util.ViewUtil

/**
 * The credentials fragment can be used to retrieve username, password, the "store password" option status, login
 * server overridden option and the server ip:port. Use the arguments to fill the fragment with default values.
 * Supported arguments are:
 * - [.ARG_LOGIN] login default text value; editable only if new user creation
 * - [.ARG_LOGIN_EDITABLE] `boolean` flag indicating if the login field is editable
 * - [.ARG_PASSWORD] password default text value
 * - [.ARG_IB_REGISTRATION] "store password" default `boolean` value
 * - [.ARG_IB_REGISTRATION] "ibr_registration" default `boolean` value
 * - [.ARG_STORE_PASSWORD] "store password" default `boolean` value
 * - [.ARG_IS_SERVER_OVERRIDDEN] "Server Overridden" default `boolean` value
 * - [.ARG_SERVER_ADDRESS] Server address default text value
 * - [.ARG_SERVER_PORT] Server port default text value
 * - [.ARG_LOGIN_REASON] login in reason, present last server return exception if any
 *
 * @author Eng Chong Meng
 */
class CredentialsFragment : Fragment() {
    private lateinit var mServerOverrideCheckBox: CheckBox
    private lateinit var mServerIpField: EditText
    private lateinit var mServerPortField: EditText
    private lateinit var mPasswordField: EditText
    private lateinit var mShowPasswordCheckBox: CheckBox

    /**
     * {@inheritDoc}
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val args = arguments
        val content = inflater.inflate(R.layout.account_credentials, container, false)
        val spinnerDM = content.findViewById<Spinner>(R.id.dnssecModeSpinner)
        val adapterDM = ArrayAdapter.createFromResource(activity!!,
                R.array.dnssec_Mode_name, R.layout.simple_spinner_item)
        adapterDM.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        spinnerDM.adapter = adapterDM
        val dnssecMode = args!!.getString(ARG_DNSSEC_MODE)
        val dnssecModeValues = resources.getStringArray(R.array.dnssec_Mode_value)
        val sPos = listOf(*dnssecModeValues).indexOf(dnssecMode)
        spinnerDM.setSelection(sPos)
        val mUserNameEdit = content.findViewById<EditText>(R.id.username)
        mUserNameEdit.setText(args.getString(ARG_LOGIN))
        mUserNameEdit.isEnabled = args.getBoolean(ARG_LOGIN_EDITABLE, true)
        mShowPasswordCheckBox = content.findViewById(R.id.show_password)
        mPasswordField = content.findViewById(R.id.password)
        mPasswordField.setText(args.getString(ARG_PASSWORD))
        // ViewUtil.setTextViewValue(content, R.id.password, args.getString(ARG_PASSWORD));
        ViewUtil.setCompoundChecked(content, R.id.store_password, args.getBoolean(ARG_STORE_PASSWORD, true))
        ViewUtil.setCompoundChecked(content, R.id.ib_registration, args.getBoolean(ARG_IB_REGISTRATION, false))
        val showCert = content.findViewById<ImageView>(R.id.showCert)
        val clientCertId = args.getString(ARG_CERT_ID)
        if (clientCertId == null || clientCertId == CertificateConfigEntry.CERT_NONE.toString()) {
            showCert.visibility = View.GONE
        }
        mServerOverrideCheckBox = content.findViewById(R.id.serverOverridden)
        mServerIpField = content.findViewById(R.id.serverIpField)
        mServerPortField = content.findViewById(R.id.serverPortField)
        val isShownServerOption = args.getBoolean(ARG_IS_SHOWN_SERVER_OPTION, false)
        if (isShownServerOption) {
            val isServerOverridden = args.getBoolean(ARG_IS_SERVER_OVERRIDDEN, false)
            ViewUtil.setCompoundChecked(content, R.id.serverOverridden, isServerOverridden)
            mServerIpField.setText(args.getString(ARG_SERVER_ADDRESS))
            mServerPortField.setText(args.getString(ARG_SERVER_PORT))
            updateViewVisibility(isServerOverridden)
        } else {
            mServerIpField.visibility = View.GONE
            mServerPortField.visibility = View.GONE
        }

        // make xml text more human readable and link clickable
        val reasonField = content.findViewById<TextView>(R.id.reason_field)
        val xmlText = args.getString(ARG_LOGIN_REASON)
        if (!TextUtils.isEmpty(xmlText)) {
            val loginReason = Html.fromHtml(xmlText!!.replace("\n", "<br/>"))
            reasonField.text = loginReason
        }
        initializeViewListeners()
        return content
    }

    private fun initializeViewListeners() {
        mShowPasswordCheckBox.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean -> ViewUtil.showPassword(mPasswordField, isChecked) }
        mServerOverrideCheckBox.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean -> updateViewVisibility(isChecked) }
    }

    private fun updateViewVisibility(IsServerOverridden: Boolean) {
        if (IsServerOverridden) {
            mServerIpField.visibility = View.VISIBLE
            mServerPortField.visibility = View.VISIBLE
        } else {
            mServerIpField.visibility = View.GONE
            mServerPortField.visibility = View.GONE
        }
    }

    companion object {
        /**
         * Pre-entered login argument.
         */
        const val ARG_LOGIN = "login"

        /**
         * Pre-entered password argument.
         */
        const val ARG_PASSWORD = "password"

        /**
         * Pre-entered dnssecMode argument.
         */
        const val ARG_DNSSEC_MODE = "dnssec_mode"

        /**
         * Argument indicating whether the login can be edited.
         */
        const val ARG_LOGIN_EDITABLE = "login_editable"

        /**
         * Pre-entered "store password" `boolean` value.
         */
        const val ARG_STORE_PASSWORD = "store_pass"

        /**
         * Pre-entered "store password" `boolean` value.
         */
        const val ARG_IB_REGISTRATION = "ib_registration"

        /**
         * Show server option for user entry if true " `boolean` value.
         */
        const val ARG_IS_SHOWN_SERVER_OPTION = "is_shown_server_option"

        /**
         * Pre-entered "is server overridden" `boolean` value.
         */
        const val ARG_IS_SERVER_OVERRIDDEN = "is_server_overridden"

        /**
         * Pre-entered "store server address".
         */
        const val ARG_SERVER_ADDRESS = "server_address"

        /**
         * Pre-entered "store server port".
         */
        const val ARG_SERVER_PORT = "server_port"

        /**
         * Reason for the login / reLogin.
         */
        const val ARG_LOGIN_REASON = "login_reason"
        const val ARG_CERT_ID = "cert_id"
    }
}