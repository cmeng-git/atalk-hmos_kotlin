/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account

import android.content.Intent
import android.os.Bundle
import net.java.sip.communicator.service.gui.AccountRegistrationWizard
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.atalk.hmos.R
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.menu.ExitMenuActivity
import org.osgi.framework.BundleContext
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceReference
import timber.log.Timber

/**
 * The `AccountLoginActivity` is the activity responsible for creating or
 * registration a new account on the server.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AccountLoginActivity : ExitMenuActivity(), AccountLoginFragment.AccountLoginListener {
    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this
     * Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Note: Otherwise it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If we have instance state it means the fragment is already created
        if (savedInstanceState == null) {
            // Create AccountLoginFragment fragment
            val login = intent.getStringExtra(USERNAME)
            val password = intent.getStringExtra(PASSWORD)
            val accountLogin = AccountLoginFragment.createInstance(login, password)
            supportFragmentManager.beginTransaction().add(android.R.id.content, accountLogin).commit()
        }
    }

    /**
     * Create an new account database with the given `userName`, `password`
     * and `protocolName`.
     *
     * @param userName the username of the account
     * @param password the password of the account
     * @param protocolName the name of the protocol
     * @return the `ProtocolProviderService` corresponding to the newly signed in account
     */
    private fun createAccount(userName: String, password: String,
            protocolName: String, accountProperties: MutableMap<String, String?>): ProtocolProviderService? {
        val bundleContext = bundleContext!!
        // Find all the available AccountRegistrationWizard that the system has implemented
        var accountWizardRefs: Array<ServiceReference<*>>? = null
        try {
            accountWizardRefs = bundleContext.getServiceReferences(AccountRegistrationWizard::class.java.name, null)
        } catch (ex: InvalidSyntaxException) {
            // this shouldn't happen since we have provided all parameter string
            Timber.e(ex, "Error while retrieving service refs")
        }

        // in case we found none, then exit.
        if (accountWizardRefs == null) {
            Timber.e("No registered account registration wizards found")
            return null
        }
        Timber.d("Found %s already installed providers.", accountWizardRefs.size)

        // Get the user selected AccountRegistrationWizard for account registration
        var selectedWizard: AccountRegistrationWizard? = null
        for (accountWizardRef in accountWizardRefs) {
            val accReg = bundleContext.getService(accountWizardRef) as AccountRegistrationWizard
            if (accReg.protocolName == protocolName) {
                selectedWizard = accReg
                break
            }
        }
        if (selectedWizard == null) {
            Timber.w("No account registration wizard found for protocol name: %s", protocolName)
            return null
        }
        try {
            selectedWizard.isModification = false
            return selectedWizard.signin(userName, password, accountProperties)
        } catch (e: OperationFailedException) {
            Timber.e(e, "Account creation operation failed.")
            when (e.getErrorCode()) {
                OperationFailedException.ILLEGAL_ARGUMENT -> DialogActivity.showDialog(this, R.string.service_gui_LOGIN_FAILED,
                        R.string.service_gui_USERNAME_NULL)
                OperationFailedException.IDENTIFICATION_CONFLICT -> DialogActivity.showDialog(this, R.string.service_gui_LOGIN_FAILED,
                        R.string.service_gui_USER_EXISTS_ERROR)
                OperationFailedException.SERVER_NOT_SPECIFIED -> DialogActivity.showDialog(this, R.string.service_gui_LOGIN_FAILED,
                        R.string.service_gui_SPECIFY_SERVER)
                else -> DialogActivity.showDialog(this, R.string.service_gui_LOGIN_FAILED,
                        R.string.service_gui_ACCOUNT_CREATION_FAILED, e.message)
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while adding account: %s", e.message)
            DialogActivity.showDialog(this, R.string.service_gui_ERROR,
                    R.string.service_gui_ACCOUNT_CREATION_FAILED, e.message)
        }
        return null
    }

    /**
     * See [AccountLoginFragment.AccountLoginListener.onLoginPerformed]
     */
    override fun onLoginPerformed(userName: String, password: String, network: String, accountProperties: MutableMap<String, String?>) {
        val pps = createAccount(userName, password, network, accountProperties)
        if (pps != null) {
            val showContactsIntent = Intent(aTalk.ACTION_SHOW_CONTACTS)
            startActivity(showContactsIntent)
            finish()
        }
    }

    companion object {
        /**
         * The username property name.
         */
        const val USERNAME = "Username"

        /**
         * The password property name.
         */
        const val PASSWORD = "Password"
    }
}