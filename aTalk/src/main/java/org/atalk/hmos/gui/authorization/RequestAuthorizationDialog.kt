/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.authorization

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.service.osgi.OSGiActivity

/**
 * This dialog is displayed in order to prepare the authorization request that has to be sent to
 * the user we want to include in our contact list.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class RequestAuthorizationDialog : OSGiActivity() {
    /**
     * The request holder.
     */
    private var request: AuthorizationHandlerImpl.AuthorizationRequestedHolder? = null

    /**
     * Flag stores the discard state.
     */
    private var discard = false

    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.request_authorization)
        val requestId = intent.getLongExtra(EXTRA_REQUEST_ID, -1L)
        require(requestId != -1L)
        request = AuthorizationHandlerImpl.getRequest(requestId)
        val userID = request!!.contact.protocolProvider.accountID.mUserID
        val contactId = request!!.contact.address
        ViewUtil.setTextViewValue(getContentView(), R.id.requestInfo,
                getString(R.string.service_gui_REQUEST_AUTHORIZATION_MSG, userID, contactId))

        // Prevents from closing the dialog on outside touch
        setFinishOnTouchOutside(false)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // Prevent Back Key from closing the dialog
        return if (keyCode == KeyEvent.KEYCODE_BACK) {
            true
        } else super.onKeyUp(keyCode, event)
    }

    /**
     * Method fired when the request button is clicked.
     *
     * @param v the button's `View`
     */
    fun onRequestClicked(v: View?) {
        val requestText = ViewUtil.getTextViewValue(getContentView(), R.id.requestText)!!
        request!!.submit(requestText)
        discard = false
        finish()
    }

    /**
     * Method fired when the cancel button is clicked.
     *
     * @param v the button's `View`
     */
    fun onCancelClicked(v: View?) {
        discard = true
        finish()
    }

    /**
     * {@inheritDoc}
     */
    override fun onDestroy() {
        if (discard) request!!.discard()
        super.onDestroy()
    }

    companion object {
        /**
         * Request identifier extra key.
         */
        private const val EXTRA_REQUEST_ID = "request_id"

        /**
         * Creates the `Intent` to start `RequestAuthorizationDialog` parametrized with
         * given `requestId`.
         *
         * @param requestId the id of authentication request.
         * @return `Intent` that start `RequestAuthorizationDialog` parametrized with given request id.
         */
        fun getRequestAuthDialogIntent(requestId: Long): Intent {
            val intent = Intent(aTalkApp.globalContext, RequestAuthorizationDialog::class.java)
            intent.putExtra(EXTRA_REQUEST_ID, requestId)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent
        }
    }
}