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
package org.atalk.hmos.gui.login

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import net.java.sip.communicator.impl.protocol.jabber.JabberActivator
import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.R.id
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.util.ViewUtil
import org.jivesoftware.smack.AbstractXMPPConnection
import org.jivesoftware.smack.ConnectionListener
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.SmackException.NoResponseException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smack.packet.StanzaError
import org.jivesoftware.smack.util.Async
import org.jivesoftware.smackx.captcha.packet.CaptchaExtension
import org.jivesoftware.smackx.iqregisterx.AccountManager
import org.jivesoftware.smackx.xdata.FormField
import org.jivesoftware.smackx.xdata.packet.DataForm
import org.jxmpp.jid.parts.Localpart
import org.jxmpp.util.XmppStringUtils
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL

/**
 * The dialog pops up when the user account login return with "not-authorized" i.e. not
 * registered on server, and user has select the InBand Registration option.
 *
 * The IBRegistration supports Form submission with optional captcha challenge,
 * and the bare attributes format method
 *
 * @author Eng Chong Meng
 */
class IBRCaptchaProcessDialog(private val mContext: Context, private val mPPS: ProtocolProviderServiceJabberImpl, accountId: AccountID, pwd: String) : Dialog(mContext) {
    /**
     * Constructor for the `Captcha Request Dialog` for passing the dialog parameters
     *
     * context the context to which the dialog belongs
     * pps the protocol provider service that offers the service
     * accountId the AccountID of the login user request for IBRegistration
     */
    private val mConnection = mPPS.connection!!
    private val mAccountId = accountId
    private val mPassword = pwd
    private var mReasonText = aTalkApp.getResString(R.string.captcha_registration_reason)

    /**
     * Listens for connection closes or errors.
     */
    private var connectionListener: JabberConnectionListener? = null

    // Map contains extra form field label and variable not in static layout
    private val varMap = HashMap<String?, String>()

    // The layout container to add the extra form fields
    private lateinit var entryFields: LinearLayout
    private lateinit var mCaptchaText: EditText
    private lateinit var mPasswordField: EditText
    private lateinit var mServerOverrideCheckBox: CheckBox
    private lateinit var mServerIpField: EditText
    private lateinit var mServerPortField: EditText
    private lateinit var mReason: TextView
    private lateinit var mImageView: ImageView
    private lateinit var mShowPasswordCheckBox: CheckBox
    private lateinit var mSubmitButton: Button
    private lateinit var mCancelButton: Button
    private lateinit var mOKButton: Button
    private var mCaptcha: Bitmap? = null
    private var mDataForm: DataForm? = null
    private lateinit var formBuilder: DataForm.Builder

    /**
     * {@inheritDoc}
     */
    public override fun onCreate(savedInstanceState: Bundle) {
        this.setContentView(R.layout.ibr_captcha)
        setTitle(mContext.getString(R.string.captcha_registration_request))
        val mUserNameField = findViewById<EditText>(id.username)
        mUserNameField.setText(mAccountId.mUserID)
        mUserNameField.isEnabled = false
        mPasswordField = findViewById(id.password)
        mShowPasswordCheckBox = findViewById(id.show_password)
        mServerOverrideCheckBox = findViewById(id.serverOverridden)
        mServerIpField = findViewById(id.serverIpField)
        mServerPortField = findViewById(id.serverPortField)
        mImageView = findViewById(id.captcha)
        mCaptchaText = findViewById(id.input)
        mReason = findViewById(id.reason_field)
        mSubmitButton = findViewById(id.button_Submit)
        mSubmitButton.visibility = View.VISIBLE
        mOKButton = findViewById(id.button_OK)
        mOKButton.visibility = View.GONE
        mCancelButton = findViewById(id.button_Cancel)
        if (connectionListener == null) {
            connectionListener = JabberConnectionListener()
            mConnection.addConnectionListener(connectionListener)
        }

        // Prevents from closing the dialog on outside touch or Back Key
        setCanceledOnTouchOutside(false)
        setCancelable(false)
        updateDialogContent()
        if (initIBRRegistration()) {
            mReason.text = mReasonText
            updateEntryFields()
            showCaptchaContent()
            initializeViewListeners()
        }
        else {
            onIBRServerFailure()
        }
    }

    /*
     * Update IBRegistration dialog content with the initial user supplied information.
     */
    private fun updateDialogContent() {
        mPasswordField.setText(mPassword)
        val isServerOverridden = mAccountId.isServerOverridden
        mServerOverrideCheckBox.isChecked = isServerOverridden
        mServerIpField.setText(mAccountId.serverAddress)
        mServerPortField.setText(mAccountId.serverPort)
        updateViewVisibility(isServerOverridden)
    }

    /**
     * Start the InBand Registration for the accountId on the defined XMPP connection by pps.
     * Registration can either be:
     * - simple username and password or
     * - Form With captcha protection with embedded captcha image if available, else the
     * image is retrieved from the given url in the form.
     *
     * Return `true` if IBRegistration is supported and info is available
     */
    private fun initIBRRegistration(): Boolean {
        // NetworkOnMainThreadException if attempt to reconnect in UI thread; so return if no connection, else deadlock.
        if (!mConnection.isConnected) return false
        try {
            // Check and proceed only if IBRegistration is supported by the server
            val accountManager = AccountManager.getInstance(mConnection)
            if (accountManager.isSupported) {
                val info = accountManager.registrationInfo
                if (info != null) {
                    // do not proceed if dataForm is null
                    val dataForm = info.dataForm ?: return false
                    mDataForm = dataForm
                    val bob = info.boB
                    if (bob != null) {
                        val bytData = bob.bobData.content
                        val stream = ByteArrayInputStream(bytData)
                        mCaptcha = BitmapFactory.decodeStream(stream)
                    }
                    else {
                        val urlField = dataForm.getField("url")
                        if (urlField != null) {
                            val urlString = urlField.firstValue
                            getCaptcha(urlString)
                        }
                    }
                }
                else {
                    mDataForm = null
                    mCaptcha = null
                }
                return true
            }
        } catch (e: InterruptedException) {
            val errMsg = e.message
            val xmppError = StanzaError.from(StanzaError.Condition.not_authorized, errMsg).build()
            mPPS.accountIBRegistered!!.reportFailure(XMPPErrorException(null, xmppError))
            showResult()
        } catch (e: XMPPException) {
            val errMsg = e.message
            val xmppError = StanzaError.from(StanzaError.Condition.not_authorized, errMsg).build()
            mPPS.accountIBRegistered!!.reportFailure(XMPPErrorException(null, xmppError))
            showResult()
        } catch (e: SmackException) {
            val errMsg = e.message
            val xmppError = StanzaError.from(StanzaError.Condition.not_authorized, errMsg).build()
            mPPS.accountIBRegistered!!.reportFailure(XMPPErrorException(null, xmppError))
            showResult()
        }
        return false
    }

    /**
     * Add extra Form fields if there are not in the static layout
     */
    private fun updateEntryFields() {
        entryFields = findViewById(id.entry_fields)
        val inflater = LayoutInflater.from(mContext)
        if (mDataForm != null) {
            val formFields = mDataForm!!.fields
            for (formField in formFields) {
                val type = formField.type
                val `var` = formField.fieldName
                if (type == FormField.Type.hidden || type == FormField.Type.fixed) continue
                val label = formField.label
                val value = formField.firstValue
                if (`var` == "url") {
                    (findViewById<View>(id.url_label) as TextView).text = label
                    val urlLink = findViewById<TextView>(id.url_link)
                    urlLink.text = value
                    urlLink.setOnClickListener { getCaptcha(value) }
                }
                else {
                    if (`var` == CaptchaExtension.USER_NAME || `var` == CaptchaExtension.PASSWORD || `var` == CaptchaExtension.OCR) continue
                    val fieldEntry = inflater.inflate(R.layout.ibr_field_entry_row, null) as LinearLayout
                    val viewLabel = fieldEntry.findViewById<TextView>(id.field_label)
                    val viewRequired = fieldEntry.findViewById<ImageView>(id.star)
                    Timber.w("New entry field: %s = %s", label, `var`)
                    // Keep copy of the variable field for later extracting the user entered value
                    varMap[label] = `var`
                    viewLabel.text = label
                    viewRequired.visibility = if (formField.isRequired) View.VISIBLE else View.INVISIBLE
                    entryFields.addView(fieldEntry)
                }
            }
        }
    }

    /*
     * Update dialog content with the received captcha information for form presentation.
     */
    private fun showCaptchaContent() {
        Handler(Looper.getMainLooper()).post {
            if (mCaptcha != null) {
                findViewById<View>(id.captcha_container).visibility = View.VISIBLE
                // Scale the captcha to the display resolution
                val metrics = mContext.resources.displayMetrics
                val captcha = Bitmap.createScaledBitmap(mCaptcha!!, (mCaptcha!!.width * metrics.scaledDensity).toInt(), (mCaptcha!!.height * metrics.scaledDensity).toInt(), false)
                mImageView.setImageBitmap(captcha)
                mCaptchaText.setHint(R.string.captcha_hint)
                mCaptchaText.requestFocus()
            }
            else {
                findViewById<View>(id.captcha_container).visibility = View.GONE
            }
        }
    }

    /**
     * Fetch the captcha bitmap from the given url link on new thread
     *
     * @param urlString Url link to fetch the captcha
     */
    private fun getCaptcha(urlString: String) {
        Thread {
            try {
                if (!TextUtils.isEmpty(urlString)) {
                    val uri = URL(urlString)
                    mCaptcha = BitmapFactory.decodeStream(uri.openConnection().getInputStream())
                    showCaptchaContent()
                }
            } catch (e: IOException) {
                Timber.e(e, "%s", e.message)
            }
        }.start()
    }

    /**
     * Setup all the dialog buttons' listeners for the required actions on user click
     */
    private fun initializeViewListeners() {
        mShowPasswordCheckBox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean -> ViewUtil.showPassword(mPasswordField, isChecked) }
        mServerOverrideCheckBox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean -> updateViewVisibility(isChecked) }
        mImageView.setOnClickListener { mCaptchaText.requestFocus() }
        mSubmitButton.setOnClickListener {
            // server disconnect user if waited for too long
            if (mConnection.isConnected) {
                if (updateAccount()) {
                    onSubmitClicked()
                    showResult()
                }
            }
        }

        // Re-trigger IBR if user click OK - let login takes over
        mOKButton.setOnClickListener {
            closeDialog()
            val globalStatusService = AndroidGUIActivator.globalStatusService!!
            globalStatusService.publishStatus(GlobalStatusEnum.ONLINE)
        }

        // Set IBR to false on user cancel. Otherwise may loop in IBR if server returns error
        mCancelButton.setOnClickListener {
            mAccountId.isIbRegistration = false
            val errMsg = "InBand registration cancelled by user!"
            val xmppError = StanzaError.from(StanzaError.Condition.registration_required, errMsg).build()
            mPPS.accountIBRegistered!!.reportFailure(XMPPErrorException(null, xmppError))
            closeDialog()
        }
    }

    /**
     * Updated AccountID with the parameters entered by user
     */
    private fun updateAccount(): Boolean {
        var password: String? = null
        val pwd = mPasswordField.text
        if (pwd != null && StringUtils.isNotEmpty(pwd.toString().also { password = it })) {
            mAccountId.password = password
            if (mAccountId.isPasswordPersistent) JabberActivator.protocolProviderFactory.storePassword(mAccountId, password)
        }
        else {
            mReason.setText(R.string.captcha_registration_pwd_empty)
            return false
        }

        // Update server override options
        val serverAddress = ViewUtil.toString(mServerIpField)
        val serverPort = ViewUtil.toString(mServerPortField)
        val isServerOverride = mServerOverrideCheckBox.isChecked
        mAccountId.isServerOverridden = isServerOverride
        if (isServerOverride && serverAddress != null && serverPort != null) {
            mAccountId.serverAddress = serverAddress
            mAccountId.serverPort = serverPort
        }
        return true
    }

    /**
     * Handles the `ActionEvent` triggered when one user clicks on the Submit button.
     */
    private fun onSubmitClicked() {
        // Server will end connection on wait timeout due to user no response
        if (mConnection.isConnected) {
            val accountManager = AccountManager.getInstance(mConnection)

            // Only localPart is required
            val userName = XmppStringUtils.parseLocalpart(mAccountId.mUserID)
            val pwd = mPasswordField.text
            try {
                if (mDataForm != null) {
                    formBuilder = DataForm.builder(DataForm.Type.submit)!!
                    addFormField(CaptchaExtension.USER_NAME, userName)
                    if (pwd != null) {
                        addFormField(CaptchaExtension.PASSWORD, pwd.toString())
                    }

                    // Add an extra field if any and its value is not empty
                    val varCount = entryFields.childCount
                    for (i in 0 until varCount) {
                        val row = entryFields.getChildAt(i)
                        val label = ViewUtil.toString(row.findViewById(id.field_label))
                        if (varMap.containsKey(label)) {
                            val data = ViewUtil.toString(row.findViewById(id.field_value))
                            if (data != null) addFormField(varMap[label], data)
                        }
                    }

                    // set captcha challenge required info
                    if (mCaptcha != null) {
                        addFormField(FormField.FORM_TYPE, CaptchaExtension.NAMESPACE)
                        formBuilder.addField(mDataForm!!.getField(CaptchaExtension.CHALLENGE))
                        formBuilder.addField(mDataForm!!.getField(CaptchaExtension.SID))
                        addFormField(CaptchaExtension.ANSWER, "3")
                        val rc = mCaptchaText.text
                        if (rc != null) {
                            addFormField(CaptchaExtension.OCR, rc.toString())
                        }
                    }
                    accountManager.createAccount(formBuilder.build())
                }
                else {
                    val username = Localpart.formUnescapedOrNull(userName)
                    accountManager.sensitiveOperationOverInsecureConnection(false)
                    if (pwd != null) {
                        accountManager.createAccount(username, pwd.toString())
                    }
                }
                // if not exception being thrown, then registration is successful. Clear IBR flag on success
                mAccountId.isIbRegistration = false
                mPPS.accountIBRegistered!!.reportSuccess()
            } catch (ex: Exception) {
                var xmppError: StanzaError? = null
                val errMsg = ex.message
                var errDetails = ""

                when (ex) {
                    is XMPPErrorException -> {
                        xmppError = ex.stanzaError
                        errDetails = xmppError.descriptiveText
                    }
                    is NoResponseException,
                    is SmackException.NotConnectedException,
                    is InterruptedException -> {
                        xmppError = StanzaError.from(StanzaError.Condition.not_acceptable, errMsg).build()
                    }
                }
                Timber.e("Exception: %s; %s", errMsg, errDetails)
                if ((errMsg != null) && errMsg.contains("conflict") && errDetails.contains("exists")) {
                    mAccountId.isIbRegistration = false
                }
                mPPS.accountIBRegistered!!.reportFailure(XMPPErrorException(null, xmppError))
            }
        }
    }

    /**
     * Add field / value to formBuilder for registration
     *
     * @param name the FormField variable
     * @param value the FormField value
     */
    private fun addFormField(name: String?, value: String) {
        val field = FormField.builder(name)
        field.setValue(value)
        formBuilder.addField(field.build())
    }

    private fun closeDialog() {
        if (connectionListener != null) {
            mConnection.removeConnectionListener(connectionListener)
            connectionListener = null
        }
        cancel()
    }

    /**
     * Show or hide server address & port
     *
     * @param IsServerOverridden `true` show server address and port field for user entry
     */
    private fun updateViewVisibility(IsServerOverridden: Boolean) {
        if (IsServerOverridden) {
            mServerIpField.visibility = View.VISIBLE
            mServerPortField.visibility = View.VISIBLE
        }
        else {
            mServerIpField.visibility = View.GONE
            mServerPortField.visibility = View.GONE
        }
    }

    /**
     * Shows IBR registration result.
     */
    private fun showResult() {
        var errMsg: String? = null
        mReasonText = mContext.getString(R.string.captcha_registration_success)
        try {
            val ex = mPPS.accountIBRegistered!!.checkIfSuccessOrWait()
            if (ex != null) {
                errMsg = ex.message
                if (ex is XMPPErrorException) {
                    val errDetails = ex.stanzaError.descriptiveText
                    if (!StringUtils.isEmpty(errDetails)) errMsg += "\n" + errDetails
                }
            }
        } catch (ex: Exception) {
            when (ex) {
                is NoResponseException, is InterruptedException -> {
                    errMsg = ex.message
                }
            }
        }

        if (StringUtils.isNotEmpty(errMsg)) {
            mReasonText = mContext.getString(R.string.captcha_registration_fail, errMsg)
        }
        // close connection on error, else throws connectionClosedOnError on timeout
        Async.go { if (mConnection.isConnected) (mConnection as AbstractXMPPConnection?)!!.disconnect() }
        mReason.text = mReasonText
        mSubmitButton.visibility = View.GONE
        mOKButton.visibility = View.VISIBLE
        mCaptchaText.setHint(R.string.captcha_retry)
        mCaptchaText.isEnabled = false
    }

    // Server failure with start of IBR registration
    private fun onIBRServerFailure() {
        mReasonText = "InBand registration - Server Error!"
        mImageView.visibility = View.GONE
        mReason.text = mReasonText
        mPasswordField.isEnabled = false
        mCaptchaText.visibility = View.GONE
        mSubmitButton.isEnabled = false
        mSubmitButton.alpha = 0.5f
        mOKButton.isEnabled = false
        mOKButton.alpha = 0.5f
        initializeViewListeners()
    }

    /**
     * Listener for jabber connection events
     */
    private inner class JabberConnectionListener : ConnectionListener {
        /**
         * Notification that the connection was closed normally.
         */
        override fun connectionClosed() {}

        /**
         * Notification that the connection was closed due to an exception. When abruptly disconnected.
         * Note: ReconnectionManager was not enabled otherwise it will try to reconnecting to the server.
         * Any update of the view must be on UiThread
         *
         * @param exception contains information of the error.
         */
        override fun connectionClosedOnError(exception: Exception) {
            val errMsg = exception.message
            Timber.e("Captcha-Exception: %s", errMsg)
            val xmppError = StanzaError.from(StanzaError.Condition.remote_server_timeout, errMsg).build()
            mPPS.accountIBRegistered!!.reportFailure(XMPPErrorException(null, xmppError))
            Handler(Looper.getMainLooper()).post { showResult() }
        }

        override fun connected(connection: XMPPConnection) {}
        override fun authenticated(connection: XMPPConnection, resumed: Boolean) {}
    }
}