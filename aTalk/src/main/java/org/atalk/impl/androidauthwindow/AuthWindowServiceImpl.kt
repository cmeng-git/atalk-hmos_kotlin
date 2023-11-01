/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidauthwindow

import net.java.sip.communicator.service.gui.AuthenticationWindowService
import net.java.sip.communicator.service.gui.AuthenticationWindowService.AuthenticationWindow

/**
 * Android implementation of `AuthenticationWindowService`. This class manages authentication requests. Each
 * request data is held by the `AuthWindowImpl` identified by assigned request id. Request id is passed to the
 * `AuthWindowActivity` so that it can obtain request data and interact with the user.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AuthWindowServiceImpl : AuthenticationWindowService {
    /**
     * Creates an instance of the `AuthenticationWindow` implementation.
     *
     * @param server the server name
     * @param isUserNameEditable indicates if the user name is editable
     * @param icon the icon to display on the left of the authentication window
     * @param windowTitle customized window title
     * @param windowText customized window text
     * @param usernameLabelText customized username field label text
     * @param passwordLabelText customized password field label text
     * @param errorMessage an error message if this dialog is shown to indicate the user that something went wrong
     * @param signupLink an URL that allows the user to sign up
     */
    override fun create(userName: String?, password: CharArray?, server: String?, isUserNameEditable: Boolean,
            isRememberPassword: Boolean, icon: Any?, windowTitle: String?, windowText: String?, usernameLabelText: String?,
            passwordLabelText: String?, errorMessage: String?, signupLink: String?): AuthenticationWindow {
        val requestId = System.currentTimeMillis()
        val authWindow = AuthWindowImpl(requestId, userName, password, server, isUserNameEditable,
                isRememberPassword, windowTitle, windowText, usernameLabelText, passwordLabelText)
        requestMap[requestId] = authWindow
        return authWindow
    }

    companion object {
        /**
         * Requests map
         */
        private val requestMap = HashMap<Long, AuthWindowImpl>()

        /**
         * Returns `AuthWindowImpl` for given `requestId`.
         *
         * @param requestId the request identifier
         * @return `AuthWindowImpl` identified by given `requestId`.
         */
        fun getAuthWindow(requestId: Long): AuthWindowImpl? {
            return requestMap[requestId]
        }

        /**
         * Called when authentication request processing for given `requestId` is completed or canceled.
         *
         * @param requestId the request identifier
         */
        fun clearRequest(requestId: Long) {
            requestMap.remove(requestId)
        }
    }
}