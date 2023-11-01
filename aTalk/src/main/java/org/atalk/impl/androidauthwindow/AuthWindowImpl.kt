/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidauthwindow

import android.content.Context
import android.content.Intent
import android.os.Looper
import net.java.sip.communicator.service.gui.AuthenticationWindowService.AuthenticationWindow
import org.atalk.hmos.aTalkApp
import timber.log.Timber

/**
 * Android `AuthenticationWindow` impl. Serves as a static data model for `AuthWindowActivity`. Is
 * identified by the request id passed as an intent extra. All requests are mapped in `AuthWindowServiceImpl`.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AuthWindowImpl
/**
 * Creates new instance of `AuthWindowImpl`
 *
 * @param requestId request identifier managed by `AuthWindowServiceImpl`
 * @param userName pre entered username
 * @param password pre entered password
 * @param server name of the server that requested authentication
 * @param rememberPassword indicates if store password filed should be checked by default
 * @param windowTitle the title for authentication window
 * @param windowText the message text for authentication window
 * @param usernameLabel label for login field
 * @param passwordLabel label for password field
 */
(private val requestId: Long,
        /**
         * Returns the user name entered by the user or previously set if the user name is not editable.
         *
         * @return the user name.
         */
        override var userName: String?,
        /**
         * Returns the password entered by the user.
         *
         * @return the password.
         */
        override var password: CharArray?,

        /**
         * Returns name of the server that requested authentication.
         *
         * @return name of the server that requested authentication.
         */
        val server: String?,

        /**
         * Returns `true` if username filed is editable.
         *
         * @return `true` if username filed is editable.
         */
        val isUserNameEditable: Boolean,

        /**
         * Sets the store password flag.
         *
         * @param storePassword `true` if the password should be stored.
         */
        override var isRememberPassword: Boolean,

        /**
         * Returns the window title that should be used by authentication dialog.
         *
         * @return the window title that should be used by authentication dialog.
         */
        val windowTitle: String?,

        /**
         * Returns authentication window message text.
         *
         * @return authentication window message text.
         */
        val windowText: String?,

        /**
         * Returns username description text.
         *
         * @return username description text.
         */
        val usernameLabel: String?,

        /**
         * Returns the password label.
         *
         * @return the password label.
         */
        val passwordLabel: String?) : AuthenticationWindow {
    /**
     * Lock object used to stop the thread until credentials are obtained.
     */
    private val notifyLock = Any()

    /**
     * Indicates if this window has been canceled.
     *
     * @return `true` if this window has been canceled, `false` - otherwise.
     */
    /**
     * Sets dialog canceled flag.
     */
    override var isCanceled = false

    /**
     * Shows AuthWindow password request dialog.
     *
     * This function MUST NOT be called from main thread. Otherwise
     * synchronized (notifyLock){} will cause whole UI to freeze.
     *
     * @param isVisible specifies whether we should be showing or hiding the window.
     */
    override fun setVisible(isVisible: Boolean) {
        if (!isVisible) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Timber.e("AuthWindow cannot be called from main thread!")
            return
        }
        val ctx = aTalkApp.globalContext
        val authWindowIntent = Intent(ctx, AuthWindowActivity::class.java)
        authWindowIntent.putExtra(AuthWindowActivity.Companion.REQUEST_ID_EXTRA, requestId)
        authWindowIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(authWindowIntent)

        // This will freeze UI if allow to execute from main thread
        synchronized(notifyLock) {
            try {
                (notifyLock as Object).wait()
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }
    }

    /**
     * Should be called when authentication window is closed. Releases thread that waits for credentials.
     */
    fun windowClosed() {
        synchronized(notifyLock) {
            (notifyLock as Object).notifyAll()
            AuthWindowServiceImpl.Companion.clearRequest(requestId)
        }
    }

    /**
     * Returns `true` if it's allowed to save the password.
     *
     * @return `true` if it's allowed to save the password.
     */
    override var isAllowSavePassword = true

    /**
     * Sets the username entered by the user.
     *
     * @param username the user name entered by the user.
     */
    fun setUsername(username: String?) {
        userName = username
    }

    /**
     * Sets the password entered by the user.
     *
     * @param password the password entered by the user.
     */
    fun setPassword(password: String) {
        this.password = password.toCharArray()
    }
}