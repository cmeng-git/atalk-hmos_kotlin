/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.service.gui

import android.content.Context
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.util.UtilActivator
import org.apache.commons.lang3.StringUtils
import org.jxmpp.jid.Jid

/**
 * The `AccountRegistrationWizard` is meant to provide a wizard which will guide the user
 * through a protocol account registration. Each `AccountRegistrationWizard` should
 * provide a set of `WizardPage`s, an icon, the name and the description of the
 * corresponding protocol.
 *
 * Note that the `AccountRegistrationWizard` is NOT a real wizard, it doesn't handle
 * wizard events. Each UI Service implementation should provide its own wizard UI control, which
 * should manage all the events, panels and buttons, etc.
 *
 * It depends on the wizard implementation in the UI for whether or not a summary will be shown
 * to the user before "Finish".
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
abstract class AccountRegistrationWizard {
    /**
     * Indicates if this wizard is modifying an existing account or is creating a new one.
     *
     * @return `true` to indicate that this wizard is currently in modification mode,
     * `false` - otherwise.
     */
    /**
     * Sets the modification property to indicate if this wizard is opened for a modification.
     *
     * @param isModification indicates if this wizard is opened for modification or for creating a new account.
     */
    /**
     * Is current wizard run as modification of an existing account.
     */
    var isModification = false

    /**
     * Returns the protocol icon that will be shown on the left of the protocol name in the list,
     * where user will choose the protocol to register to.
     *
     * @return a short description of the protocol.
     */
    abstract val icon: ByteArray?

    /**
     * Returns the image that will be shown on the left of the wizard pages.
     *
     * @return the image that will be shown on the left of the wizard pages
     */
    abstract val pageImage: ByteArray?

    /**
     * Returns the protocol display name that will be shown in the list, where user will choose
     * the protocol to register to.
     *
     * @return the protocol name.
     */
    abstract val protocolName: String?

    /**
     * Returns a short description of the protocol that will be shown on the right of the
     * protocol name in the list, where user will choose the protocol to register to.
     *
     * @return a short description of the protocol.
     */
    abstract val protocolDescription: String?

    /**
     * Returns an example string, which should indicate to the user how the user name should look
     * like. For example: john@jabber.org.
     *
     * @return an example string, which should indicate to the user how the user name should look
     * like.
     */
    abstract val userNameExample: String?

    /**
     * Loads all data concerning the given `ProtocolProviderService`. This method is meant
     * to be used when a modification in an already created account is needed.
     *
     * @param protocolProvider The `ProtocolProviderService` to load data from.
     */
    abstract fun loadAccount(protocolProvider: ProtocolProviderService?)

    /**
     * Returns the identifier of the first account registration wizard page. This method is meant
     * to be used by the wizard container to determine, which is the first page to show to the user.
     *
     * @return the identifier of the first account registration wizard page
     */
    abstract val firstPageIdentifier: Any?

    /**
     * Returns the identifier of the last account registration wizard page. This method is meant
     * to be used by the wizard container to determine which is the page to show before the
     * summary page (of course if there's a summary).
     *
     * @return the identifier of the last account registration wizard page
     */
    abstract val lastPageIdentifier: Any?

    /**
     * Returns a set of key-value pairs that will represent the summary for this wizard.
     *
     * @return a set of key-value pairs that will represent the summary for this wizard.
     */
    abstract val summary: Iterator<Map.Entry<String?, String?>?>?

    /**
     * Defines the operations that will be executed when the user clicks on the wizard "Signin"
     * button.
     *
     * @return the created `ProtocolProviderService` corresponding to the new account
     * @throws OperationFailedException if the operation didn't succeed
     */
    @Throws(OperationFailedException::class)
    abstract fun signin(): ProtocolProviderService?

    /**
     * Defines the operations that will be executed when the user clicks on the wizard "Signin"
     * button.
     *
     * @param userName the user name to sign in with
     * @param password the password to sign in with
     * @param accountProperties additional account parameter for login
     * @return the created `ProtocolProviderService` corresponding to the new account
     * @throws OperationFailedException if the operation didn't succeed
     */
    @Throws(OperationFailedException::class)
    abstract fun signin(userName: String, password: String?, accountProperties: MutableMap<String, String?>?): ProtocolProviderService?

    /**
     * Indicates that the account corresponding to the given `protocolProvider` has been removed.
     *
     * @param protocolProvider the protocol provider that has been removed
     */
    fun accountRemoved(protocolProvider: ProtocolProviderService?) {}

    /**
     * Returns `true` if the web sign up is supported by the current implementation,
     * `false` - otherwise.
     *
     * @return `true` if the web sign up is supported by the current implementation,
     * `false` - otherwise
     */
    val isWebSignupSupported: Boolean
        get() = false

    /**
     * Defines the operation that will be executed when user clicks on the "Sign up" link.
     *
     * @throws UnsupportedOperationException if the web sign up operation is not supported by the current implementation.
     */
    @Throws(UnsupportedOperationException::class)
    fun webSignup() {
    }

    /**
     * Returns `true` if the inBand registration is supported by the current
     * implementation, `false` - otherwise.
     *
     * @return `true` if the inBand registration is supported by the current
     * implementation, `false` - otherwise
     */
    open val isInBandRegistrationSupported: Boolean
        get() = false

    /**
     * Defines the operation that will be executed when user clicks on the "Sign up" link with
     * the "Register new account on server" checked.
     *
     * Registration is executed in Asynchronous task and the result will be returned in
     *
     * @see TaskCompleted.onRegistrationCompleted
     */
    fun registerAccount(context: Context?, userJid: Jid?, password: String?, host: String?, port: Int) {}

    /* Define Result you would like to return from registerAccount ASyncTask completion */
    interface TaskCompleted {
        fun onRegistrationCompleted(result: Boolean?)
    }

    /**
     * Returns the forgot password link name.
     *
     * @return the forgot password link name
     */
    val forgotPasswordLinkName: String?
        get() = null

    /**
     * Returns the forgot password link if one exists.
     *
     * @return the forgot password link
     */
    val forgotPasswordLink: String?
        get() = null

    /**
     * Returns a simple account registration form that would be the first form shown to the user.
     * Only if the user needs more settings she'll choose to open the advanced wizard, consisted by all pages.
     *
     * @param isCreateAccount indicates if the simple form should be opened as a create account form or as a login form
     * @return a simple account registration form
     */
    abstract fun getSimpleForm(isCreateAccount: Boolean): Any?

    /**
     * Indicates whether this wizard enables the simple "sign in" form shown when the user opens
     * the application for the first time. The simple "sign in" form allows user to configure her
     * account in one click, just specifying her username and password and leaving any other
     * configuration as by default.
     *
     * @return `true` if the simple "Sign in" form is enabled or `false` otherwise.
     */
    val isSimpleFormEnabled: Boolean
        get() = true

    /**
     * Whether the advanced configuration is enabled. Gives an option to disable/hide advanced config button.
     *
     * @return whether the advanced configuration is enabled.
     */
    val isAdvancedConfigurationEnabled: Boolean
        get() = true// Check for preferred account through the PREFERRED_ACCOUNT_WIZARD property.

    /**
     * Indicates if this wizard is for the preferred protocol.
     *
     * @return `true` if this wizard corresponds to the preferred protocol, otherwise returns `false`
     */
    open val isPreferredProtocol: Boolean
        get() {
            // Check for preferred account through the PREFERRED_ACCOUNT_WIZARD property.
            val prefWName = UtilActivator.resources.getSettingsString("gui.PREFERRED_ACCOUNT_WIZARD")
            return StringUtils.isNotEmpty(prefWName) && prefWName == this.javaClass.name
        }

    /**
     * Indicates if a wizard is hidden. This may be used if we don't want that a wizard appears
     * in the list of available networks.
     *
     * @return `true` to indicate that a wizard is hidden, `false` otherwise
     */
    val isHidden: Boolean
        get() = false
}