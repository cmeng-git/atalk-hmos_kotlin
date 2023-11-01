/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.accounts.NetworkErrorException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder

/**
 * Authenticator service that returns a subclass of AbstractAccountAuthenticator in onBind()
 *
 * @author Yana Stamcheva
 */
class AccountAuthenticatorService
/**
 * Creates an instance of `AccountAuthenticatorService`.
 */
    : Service() {
    /**
     * Returns the communication channel to the service. May return null if clients can not bind
     * to the service. The returned IBinder is usually for a complex interface that has been
     * described using aidl.
     *
     * @param intent
     * The Intent that was used to bind to this service, as given to Context.bindService. Note
     * that any extras that were included with the Intent at that point will not be seen here.
     * @return Return an IBinder through which clients can call on to the service.
     */
    override fun onBind(intent: Intent): IBinder? {
        var ret: IBinder? = null
        if (intent.action == AccountManager.ACTION_AUTHENTICATOR_INTENT) ret = getAuthenticator()!!.iBinder
        return ret
    }

    /**
     * Returns the authenticator implementation.
     *
     * @return the authenticator implementation
     */
    private fun getAuthenticator(): AccountAuthenticatorImpl? {
        if (sAccountAuthenticator == null) sAccountAuthenticator = AccountAuthenticatorImpl(this)
        return sAccountAuthenticator
    }

    /**
     * An implementation of the `AbstractAccountAuthenticator`.
     */
    private class AccountAuthenticatorImpl
    /**
     * Creates an instance of `AccountAuthenticatorImpl` by specifying the android context.
     */
    (
        /**
         * The android context.
         */
        private val context: Context) : AbstractAccountAuthenticator(context) {

        /**
         * The user has requested to add a new account to the system. We return an intent that
         * will launch our login screen if the user has not logged in yet, otherwise our activity
         * will just pass the user's credentials on to the account manager.
         *
         * @param response
         * to send the result back to the AccountManager, will never be null
         * @param accountType
         * the type of account to add, will never be null
         * @param authTokenType
         * the type of auth token to retrieve after adding the account, may be null
         * @param requiredFeatures
         * a String array of authenticator-specific features that the added account must
         * support, may be null
         * @param options
         * a Bundle of authenticator-specific options, may be null
         */
        @Throws(NetworkErrorException::class)
        override fun addAccount(response: AccountAuthenticatorResponse, accountType: String,
                authTokenType: String, requiredFeatures: Array<String>, options: Bundle): Bundle {
            val reply = Bundle()
            val i = Intent(context, AccountLoginActivity::class.java)
            i.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
            reply.putParcelable(AccountManager.KEY_INTENT, i)
            return reply
        }

        /**
         * Checks that the user knows the credentials of an account.
         *
         * @param response
         * to send the result back to the AccountManager, will never be null
         * @param account
         * the account whose credentials are to be checked, will never be null
         * @param options
         * a Bundle of authenticator-specific options, may be null
         */
        override fun confirmCredentials(response: AccountAuthenticatorResponse, account: Account,
                options: Bundle): Bundle? {
            return null
        }

        /**
         * Returns a Bundle that contains the Intent of the activity that can be used to edit the
         * properties. In order to indicate success the activity should call response.setResult()
         * with a non-null Bundle.
         *
         * @param response
         * used to set the result for the request. If the Constants.INTENT_KEY is set in the
         * bundle then this response field is to be used for sending future results if and
         * when the Intent is started.
         * @param accountType
         * the AccountType whose properties are to be edited.
         */
        override fun editProperties(response: AccountAuthenticatorResponse, accountType: String): Bundle? {
            return null
        }

        /**
         * Gets the authtoken for an account.
         *
         * @param response
         * to send the result back to the AccountManager, will never be null
         * @param account
         * the account whose credentials are to be retrieved, will never be null
         * @param authTokenType
         * the type of auth token to retrieve, will never be null
         * @param options
         * a Bundle of authenticator-specific options, may be null
         */
        @Throws(NetworkErrorException::class)
        override fun getAuthToken(response: AccountAuthenticatorResponse, account: Account,
                authTokenType: String, options: Bundle): Bundle? {
            return null
        }

        /**
         * Ask the authenticator for a localized label for the given authTokenType.
         *
         * @param authTokenType
         * the authTokenType whose label is to be returned, will never be null
         */
        override fun getAuthTokenLabel(authTokenType: String): String? {
            return null
        }

        /**
         * Checks if the account supports all the specified authenticator specific features.
         *
         * @param response
         * to send the result back to the AccountManager, will never be null
         * @param account
         * the account to check, will never be null
         * @param features
         * an array of features to check, will never be null
         */
        @Throws(NetworkErrorException::class)
        override fun hasFeatures(response: AccountAuthenticatorResponse, account: Account,
                features: Array<String>): Bundle? {
            return null
        }

        /**
         * Update the locally stored credentials for an account.
         *
         * @param response
         * to send the result back to the AccountManager, will never be null
         * @param account
         * the account whose credentials are to be updated, will never be null
         * @param authTokenType
         * the type of auth token to retrieve after updating the credentials, may be null
         * @param options
         * a Bundle of authenticator-specific options, may be null
         */
        override fun updateCredentials(response: AccountAuthenticatorResponse, account: Account,
                authTokenType: String, options: Bundle): Bundle? {
            return null
        }
    }

    companion object {
        /**
         * The identifier of this authenticator.
         */
        private const val TAG = "AccountAuthenticatorService"

        private var sAccountAuthenticator: AccountAuthenticatorImpl? = null
    }
}