/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.authorization

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.CompoundButton
import android.widget.Spinner
import net.java.sip.communicator.service.protocol.AuthorizationResponse
import net.java.sip.communicator.service.protocol.AuthorizationResponse.AuthorizationResponseCode
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.contactlist.MetaContactGroupAdapter
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.service.osgi.OSGiActivity

/**
 * The dialog is displayed when someone wants to add us to his contact list and the authorization
 * is required.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class AuthorizationRequestedDialog : OSGiActivity() {
    /**
     * Request holder object.
     */
    var request: AuthorizationHandlerImpl.AuthorizationRequestedHolder? = null

    /**
     * Ignore request by default
     */
    var responseCode = AuthorizationResponse.IGNORE

    /**
     * {@inheritDoc}
     */
    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.authorization_requested)
        val requestId = intent.getLongExtra(EXTRA_REQUEST_ID, -1)
        require(requestId != -1L)
        request = AuthorizationHandlerImpl.getRequest(requestId)
        val contactId = request!!.contact.address
        val content = findViewById<View>(android.R.id.content)
        ViewUtil.setTextViewValue(content, R.id.requestInfo,
                getString(R.string.service_gui_AUTHORIZATION_REQUESTED_INFO, contactId))
        ViewUtil.setTextViewValue(content, R.id.addToContacts,
                getString(R.string.service_gui_ADD_AUTHORIZED_CONTACT, contactId))
        val contactGroupSpinner = findViewById<Spinner>(R.id.selectGroupSpinner)
        contactGroupSpinner.adapter = MetaContactGroupAdapter(this, R.id.selectGroupSpinner, true, true)
        val addToContactsCb = findViewById<CompoundButton>(R.id.addToContacts)
        addToContactsCb.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            updateAddToContactsStatus(isChecked)
        }

        // Prevents from closing the dialog on outside touch
        setFinishOnTouchOutside(false)
    }

    /**
     * {@inheritDoc}
     */
    override fun onResume() {
        super.onResume()

        // Update add to contacts status
        updateAddToContactsStatus(ViewUtil.isCompoundChecked(getContentView(), R.id.addToContacts))
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // Prevent Back Key from closing the dialog
        return if (keyCode == KeyEvent.KEYCODE_BACK) {
            true
        } else super.onKeyUp(keyCode, event)
    }

    /**
     * Updates select group spinner status based on add to contact list checkbox state.
     *
     * @param isChecked `true` if "add to contacts" checkbox is checked.
     */
    private fun updateAddToContactsStatus(isChecked: Boolean) {
        ViewUtil.ensureEnabled(getContentView(), R.id.selectGroupSpinner, isChecked)
    }

    /**
     * Method fired when user accept the request.
     *
     * @param v the button's `View`
     */
    fun onAcceptClicked(v: View?) {
        responseCode = AuthorizationResponse.ACCEPT
        finish()
    }

    /**
     * Method fired when reject button is clicked.
     *
     * @param v the button's `View`
     */
    fun onRejectClicked(v: View?) {
        responseCode = AuthorizationResponse.REJECT
        finish()
    }

    /**
     * Method fired when ignore button is clicked.
     *
     * @param v the button's `View`
     */
    fun onIgnoreClicked(v: View?) {
        finish()
    }

    /**
     * {@inheritDoc}
     */
    override fun onDestroy() {
        super.onDestroy()

        // cmeng - Handle in OperationSetPersistentPresenceJabberImpl#handleSubscribeReceived
//		if (ViewUtil.isCompoundChecked(getContentView(), R.id.addToContacts)
//				&& responseCode.equals(AuthorizationResponse.ACCEPT)) {
//			// Add to contacts
//			Spinner groupSpinner = findViewById(R.id.selectGroupSpinner);
//			ContactListUtils.addContact(request.contact.getProtocolProvider(),
//					(MetaContactGroup) groupSpinner.getSelectedItem(), request.contact.address);
//		}
        request!!.notifyResponseReceived(responseCode)
    }

    companion object {
        /**
         * Request id managed by `AuthorizationHandlerImpl`.
         */
        private const val EXTRA_REQUEST_ID = "request_id"

        /**
         * Shows `AuthorizationRequestedDialog` for the request with given `id`.
         *
         * @param id request identifier for which new dialog will be displayed.
         */
        fun showDialog(id: Long?) {
            val ctx = aTalkApp.globalContext
            val showIntent = Intent(ctx, AuthorizationRequestedDialog::class.java)
            showIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            showIntent.putExtra(EXTRA_REQUEST_ID, id)
            ctx.startActivity(showIntent)
        }
    }
}