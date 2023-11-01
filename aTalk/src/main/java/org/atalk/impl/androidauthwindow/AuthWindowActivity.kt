/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidauthwindow

import android.os.Bundle
import android.view.View
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.util.ViewUtil.ensureEnabled
import org.atalk.hmos.gui.util.ViewUtil.ensureVisible
import org.atalk.hmos.gui.util.ViewUtil.getTextViewValue
import org.atalk.hmos.gui.util.ViewUtil.isCompoundChecked
import org.atalk.hmos.gui.util.ViewUtil.setCompoundChecked
import org.atalk.hmos.gui.util.ViewUtil.setTextViewValue
import org.atalk.service.osgi.OSGiActivity

/**
 * Activity controls authentication dialog for `AuthenticationWindowService`.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AuthWindowActivity : OSGiActivity() {
    /**
     * Authentication window instance
     */
    private lateinit var mAuthWindow: AuthWindowImpl

    private lateinit var contentView: View

    /**
     * Changes will be stored only if flag is set to `false`.
     */
    private var cancelled = true

    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestId = intent.getLongExtra(REQUEST_ID_EXTRA, -1)
        require(requestId != -1L)

        // Content view
        setContentView(R.layout.auth_window)
        contentView = findViewById(android.R.id.content)

        // Server name
        mAuthWindow = AuthWindowServiceImpl.getAuthWindow(requestId)!!
        // NPE return from field
        if (mAuthWindow == null) return
        val server = mAuthWindow.server

        // Title
        var title = mAuthWindow.windowTitle
        if (title == null) {
            title = getString(R.string.service_gui_AUTHENTICATION_WINDOW_TITLE, server)
        }
        setTitle(title)

        // Message
        var message = mAuthWindow.windowText
        if (message == null) {
            message = getString(R.string.service_gui_AUTHENTICATION_REQUESTED_SERVER, server)
        }
        setTextViewValue(getContentView(), R.id.text, message)

        // Username label and field
        if (mAuthWindow.usernameLabel != null) setTextViewValue(getContentView(), R.id.username_label, mAuthWindow.usernameLabel)
        if (mAuthWindow.userName != null) setTextViewValue(getContentView(), R.id.username, mAuthWindow.userName)
        ensureEnabled(getContentView(), R.id.username, mAuthWindow.isUserNameEditable)

        // Password filed and label
        if (mAuthWindow.passwordLabel != null) setTextViewValue(getContentView(), R.id.password_label, mAuthWindow.passwordLabel)
        setCompoundChecked(getContentView(), R.id.store_password, mAuthWindow.isRememberPassword)
        ensureVisible(getContentView(), R.id.store_password, mAuthWindow.isAllowSavePassword)
    }

    /**
     * Fired when the ok button is clicked.
     *
     * @param v ok button's `View`
     */
    fun onOkClicked(v: View?) {
        val userName = getTextViewValue(getContentView(), R.id.username)
        val password = getTextViewValue(getContentView(), R.id.password)
        if (userName == null || password == null) {
            aTalkApp.showToastMessage(R.string.plugin_certconfig_INCOMPLETE)
        } else {
            cancelled = false
            mAuthWindow.setUsername(userName)
            mAuthWindow.setPassword(password)
            mAuthWindow.isRememberPassword = isCompoundChecked(getContentView(), R.id.store_password)
            finish()
        }
    }

    /**
     * Fired when the cancel button is clicked.
     *
     * @param v cancel button's `View`
     */
    fun onCancelClicked(v: View?) {
        cancelled = true
        finish()
    }

    /**
     * {@inheritDoc}
     */
    override fun onDestroy() {
        mAuthWindow.isCanceled = cancelled
        mAuthWindow.windowClosed()
        super.onDestroy()
    }

    companion object {
        /**
         * Request id key.
         */
        const val REQUEST_ID_EXTRA = "request_id"
    }
}