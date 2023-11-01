/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui

/**
 * Creates and show authentication window, normally to fill in username and password.
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
interface AuthenticationWindowService {
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
     * @param errorMessage an error message if this dialog is shown to indicate
     * the user that something went wrong
     * @param signupLink an URL that allows the user to sign up
     */
    fun create(userName: String?,
            password: CharArray?,
            server: String?,
            isUserNameEditable: Boolean,
            isRememberPassword: Boolean,
            icon: Any?,
            windowTitle: String?,
            windowText: String?,
            usernameLabelText: String?,
            passwordLabelText: String?,
            errorMessage: String?,
            signupLink: String?): AuthenticationWindow?

    /**
     * The window interface used by implementers.
     */
    interface AuthenticationWindow {
        /**
         * Shows window implementation.
         *
         * @param isVisible specifies whether we should be showing or hiding the window.
         */
        fun setVisible(isVisible: Boolean)

        /**
         * Indicates if this window has been canceled.
         *
         * @return `true` if this window has been canceled, `false` - otherwise.
         */
        val isCanceled: Boolean

        /**
         * Returns the user name entered by the user or previously set if the user name is not editable.
         *
         * @return the user name.
         */
        val userName: String?

        /**
         * Returns the password entered by the user.
         *
         * @return the password.
         */
        val password: CharArray?

        /**
         * Indicates if the password should be remembered.
         *
         * @return `true` if the password should be remembered, `false` - otherwise.
         */
        val isRememberPassword: Boolean

        /**
         * Shows or hides the "save password" checkbox.
         * @param allow the checkbox is shown when allow is `true`
         */
        var isAllowSavePassword: Boolean
    }
}