/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.ProtocolNames
import net.java.sip.communicator.util.account.AccountUtils
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.call.AndroidCallUtil
import org.atalk.service.osgi.OSGiActivity

/**
 * The activity runs preference fragments for different protocols.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AccountPreferenceActivity : OSGiActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    /**
     * The [AccountPreferenceFragment]
     */
    private var preferencesFragment: AccountPreferenceFragment? = null
    private var userUniqueID: String? = null

    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Settings cannot be opened during a call
        if (AndroidCallUtil.checkCallInProgress(this)) return
        userUniqueID = intent.getStringExtra(EXTRA_USER_ID)
        val account = AccountUtils.getAccountIDForUID(userUniqueID!!)

        // account is null before a new user is properly and successfully registered with the server
        if (account != null) {
            // Gets the registration wizard service for account protocol
            val protocolName = account.protocolName
            if (savedInstanceState == null) {
                preferencesFragment = createPreferencesFragment(userUniqueID, protocolName)

                // Display the fragment as the main content.
                supportFragmentManager.beginTransaction()
                        .replace(android.R.id.content, preferencesFragment!!, ACCOUNT_FRAGMENT_TAG)
                        .commit()
            }
            else {
                val aFragment = supportFragmentManager.findFragmentByTag(ACCOUNT_FRAGMENT_TAG)
                if (aFragment is AccountPreferenceFragment) {
                    preferencesFragment = aFragment
                }
                else {
                    aTalkApp.showToastMessage("No valid registered account found: $userUniqueID")
                    finish()
                }
            }
        }
        else {
            aTalkApp.showToastMessage("No valid registered account found: $userUniqueID")
            finish()
        }
    }

    /**
     * Creates impl preference fragment based on protocol name.
     *
     * @param userUniqueID the account unique ID identifying edited account.
     * @param protocolName protocol name for which the impl fragment will be created.
     *
     * @return impl preference fragment for given `userUniqueID` and `protocolName`.
     */
    private fun createPreferencesFragment(userUniqueID: String?, protocolName: String): AccountPreferenceFragment {
        val preferencesFragment = when (protocolName) {
            ProtocolNames.JABBER -> JabberPreferenceFragment()
            ProtocolNames.SIP -> SipPreferenceFragment()
            else -> throw IllegalArgumentException("Unsupported protocol name: $protocolName")
        }
        val args = Bundle()
        args.putString(AccountPreferenceFragment.EXTRA_ACCOUNT_ID, userUniqueID)
        preferencesFragment.arguments = args
        return preferencesFragment
    }

    /**
     * Catches the back key and commits the changes if any.
     * {@inheritDoc}
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // Catch the back key code and perform commit operation
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val fragments = supportFragmentManager.fragments
            if (fragments.isNotEmpty()) {
                val fragment = fragments[fragments.size - 1]
                if (fragment is JabberPreferenceFragment || fragment is SipPreferenceFragment) {
                    preferencesFragment!!.commitChanges()
                    return true
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Called when a preference in the tree rooted at the parent Preference has been clicked.
     *
     * @param caller The caller reference
     * @param pref The click preference to launch
     *
     * @return true always
     */
    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        args.putString(AccountPreferenceFragment.EXTRA_ACCOUNT_ID, userUniqueID)
        val fm = supportFragmentManager
        val fragment = fm.fragmentFactory.instantiate(classLoader, pref.fragment!!)
        fragment.arguments = args
        fragment.setTargetFragment(caller, 0)

        // Replace the existing Fragment with the new Fragment
        fm.beginTransaction()
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit()
        return true
    }

    companion object {
        /**
         * Extra key used to pass the unique user ID using [android.content.Intent]
         */
        const val EXTRA_USER_ID = "user_id_key"
        private const val ACCOUNT_FRAGMENT_TAG = "AccountPreferenceFragment"

        /**
         * Creates new `Intent` for starting account preferences activity.
         *
         * @param ctx the context.
         * @param accountID `AccountID` for which preferences will be opened.
         *
         * @return `Intent` for starting account preferences activity parametrized with given `AccountID`.
         */
        fun getIntent(ctx: Context?, accountID: AccountID): Intent {
            val intent = Intent(ctx, AccountPreferenceActivity::class.java)
            intent.putExtra(EXTRA_USER_ID, accountID.accountUniqueID)
            return intent
        }
    }
}