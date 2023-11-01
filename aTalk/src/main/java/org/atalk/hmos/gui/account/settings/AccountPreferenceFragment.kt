/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account.settings

import android.app.ProgressDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import androidx.preference.ListPreference
import net.java.sip.communicator.service.gui.AccountRegistrationWizard
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.EncodingsRegistrationUtil
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.SecurityAccountRegistration
import net.java.sip.communicator.util.account.AccountUtils
import org.atalk.hmos.R
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.settings.util.SummaryMapper
import org.atalk.service.osgi.OSGiPreferenceFragment
import org.osgi.framework.BundleContext
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceReference
import timber.log.Timber

/**
 * The fragment shares common parts for all protocols settings. It handles security and encoding preferences.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 * @author MilanKral
 */
abstract class AccountPreferenceFragment
/**
 * Creates new instance of [AccountPreferenceFragment]
 *
 * @param preferencesResourceId the ID of preferences xml file for current protocol
 */
(
        /**
         * The ID of protocol preferences xml file passed in constructor
         */
        private val preferencesResourceId: Int) : OSGiPreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    /**
     * Utility that maps current preference value to summary
     */
    private val summaryMapper = SummaryMapper()

    /**
     * The progress dialog shown when changes are being committed
     */
    private var mProgressDialog: ProgressDialog? = null

    /**
     * Returns currently edited [AccountID].
     *
     * @return currently edited [AccountID].
     */
    var accountID: AccountID? = null

    /**
     * The wizard used to edit accounts
     */
    lateinit var wizard: AccountRegistrationWizard

    /**
     * Returns `true` if preference views have been initialized with values from the registration object.
     *
     * @return `true` if preference views have been initialized with values from the registration object.
     */
    /**
     * We load values only once into shared preferences to not reset values on screen rotated event.
     */
    protected var isInitialized = false
        private set

    /**
     * The [Thread] which runs the commit operation in background
     */
    private var commitThread: Thread? = null
    protected var dnssecModeLP: ListPreference? = null

    /**
     * Parent Activity of the Account Preference Fragment.
     * Initialize onCreate. Dynamic retrieve may sometimes return null;
     */
    protected lateinit var mActivity: AccountPreferenceActivity
    protected lateinit var shPrefs: SharedPreferences

    /**
     * Method should return `EncodingsRegistrationUtil` if it supported by impl fragment.
     * Preference categories with keys: `pref_cat_audio_encoding` and/or
     * `pref_cat_video_encoding` must be included in preferences xml to trigger encodings activities.
     *
     * @return impl fragments should return `EncodingsRegistrationUtil` if encodings are supported.
     */
    protected abstract val encodingsRegistration: EncodingsRegistrationUtil

    /**
     * Method should return `SecurityAccountRegistration` if security details are supported
     * by impl fragment. Preference category with key `pref_key_enable_encryption` must be
     * present to trigger security edit activity.
     *
     * @return `SecurityAccountRegistration` if security details are supported by impl fragment.
     */
    protected abstract val securityRegistration: SecurityAccountRegistration

    /**
     * {@inheritDoc}
     */
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the preferences from the given resource
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPrefTitle(R.string.service_gui_ACCOUNT_SETTINGS)
        setPreferencesFromResource(preferencesResourceId, rootKey)
        if (savedInstanceState != null) {
            this.isInitialized = savedInstanceState.getBoolean(STATE_INIT_FLAG)
        }
        this.mActivity = activity as AccountPreferenceActivity
        val accountID = arguments!!.getString(EXTRA_ACCOUNT_ID)
        var account = AccountUtils.getAccountIDForUID(accountID!!)
        val pps = AccountUtils.getRegisteredProviderForAccount(account)
        if (pps == null) {
            Timber.w("No protocol provider registered for %s", account)
            mActivity.finish()
            return
        }
        shPrefs = preferenceManager.sharedPreferences!!
        shPrefs.registerOnSharedPreferenceChangeListener(this)
        shPrefs.registerOnSharedPreferenceChangeListener(summaryMapper)

        /*
         * Workaround for de-synchronization problem when account was created for the first time.
         * During account creation process another instance was returned by AccountManager and
         * another from corresponding ProtocolProvider. We should use that one from the provider.
         */
        account = pps.accountID

        // Loads the account details
        loadAccount(account)

        // Preference View can be manipulated at this point
        onPreferencesCreated()

        // Preferences summaries mapping
        mapSummaries(summaryMapper)
    }

    /**
     * Unregisters preference listeners.
     */
    override fun onStop() {
        shPrefs.unregisterOnSharedPreferenceChangeListener(this)
        shPrefs.unregisterOnSharedPreferenceChangeListener(summaryMapper)
        dismissOperationInProgressDialog()
        super.onStop()
    }

    /**
     * Load the `account` and its encoding and security properties if exist as reference for update
     * before merging with the original mAccountProperties in #doCommitChanges() in the sub-class
     *
     * @param account the [AccountID] that will be edited
     */
    fun loadAccount(account: AccountID) {
        accountID = account
        wizard = findRegistrationService(account.protocolName)
        if (wizard == null) throw NullPointerException()
        if (this.isInitialized) {
            System.err.println("Initialized not loading account data")
            return
        }
        val pps = AccountUtils.getRegisteredProviderForAccount(account)
        wizard.loadAccount(pps)
        onInitPreferences()
        this.isInitialized = true
    }

    /**
     * Method is called before preference XML file is loaded. Subclasses should perform preference
     * views initialization here.
     */
    protected abstract fun onInitPreferences()

    /**
     * Method is called after preference views have been created and can be found by using findPreference() method.
     */
    protected abstract fun onPreferencesCreated()

    /**
     * Stores `initialized` flag.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_INIT_FLAG, this.isInitialized)
    }

    /**
     * Finds the wizard for given protocol name
     *
     * @param protocolName the name of the protocol
     * @return [AccountRegistrationWizard] for given `protocolName`
     */
    private fun findRegistrationService(protocolName: String): AccountRegistrationWizard {
        val accountWizardRefs: Array<ServiceReference<AccountRegistrationWizard>>
        try {
            val context = AndroidGUIActivator.bundleContext
            accountWizardRefs = context!!.getServiceReferences(AccountRegistrationWizard::class.java.name, null) as Array<ServiceReference<AccountRegistrationWizard>>
            for (accountWizardRef in accountWizardRefs) {
                val wizard = context.getService(accountWizardRef) as AccountRegistrationWizard
                if (wizard.protocolName == protocolName) return wizard
            }
        } catch (ex: InvalidSyntaxException) {
            // this shouldn't happen since we're providing no parameter string but let's log just in case.
            Timber.e(ex, "Error while retrieving service refs")
        }
        throw RuntimeException("No wizard found for protocol: $protocolName")
    }

    /**
     * Method called after all preference Views are created and initialized. Subclasses can use
     * given `summaryMapper` to include it's preferences in summary mapping
     *
     * @param summaryMapper the [SummaryMapper] managed by this [AccountPreferenceFragment] that can
     * be used by subclasses to map preference's values into their summaries
     */
    protected abstract fun mapSummaries(summaryMapper: SummaryMapper)

    /**
     * Returns the string that should be used as preference summary when no value has been set.
     *
     * @return the string that should be used as preference summary when no value has been set.
     */
    protected val emptyPreferenceStr: String
        get() = getString(R.string.service_gui_SETTINGS_NOT_SET)

    /**
     * {@inheritDoc}
     */
    override fun onSharedPreferenceChanged(shPrefs: SharedPreferences, key: String) {
        uncommittedChanges = true
    }

    /**
     * Subclasses should implement account changes commit in this method
     */
    protected abstract fun doCommitChanges()

    /**
     * Commits the changes and shows "in progress" dialog
     */
    fun commitChanges() {
        if (!uncommittedChanges) {
            mActivity.finish()
            return
        }
        try {
            if (commitThread != null) return
            displayOperationInProgressDialog()
            commitThread = Thread {
                doCommitChanges()
                mActivity.finish()
            }
            commitThread!!.start()
        } catch (e: Exception) {
            Timber.e("Error occurred while trying to commit changes: %s", e.message)
            mActivity.finish()
        }
    }

    /**
     * Shows the "in progress" dialog with a TOT of 5S if commit hangs
     */
    private fun displayOperationInProgressDialog() {
        val context = view!!.rootView.context
        val title = resources.getText(R.string.service_gui_COMMIT_PROGRESS_TITLE)
        val msg = resources.getText(R.string.service_gui_COMMIT_PROGRESS_MSG)
        mProgressDialog = ProgressDialog.show(context, title, msg, true, false)
        Handler().postDelayed({
            Timber.d("Timeout in saving")
            mActivity.finish()
        }, 5000)
    }

    /**
     * Hides the "in progress" dialog
     */
    private fun dismissOperationInProgressDialog() {
        Timber.d("Dismiss mProgressDialog: %s", mProgressDialog)
        if (mProgressDialog != null && mProgressDialog!!.isShowing) {
            mProgressDialog!!.dismiss()
            mProgressDialog = null
        }
    }

    companion object {
        /**
         * Account unique ID extra key
         */
        const val EXTRA_ACCOUNT_ID = "accountID"

        /**
         * State key for "initialized" flag
         */
        private const val STATE_INIT_FLAG = "initialized"

        /**
         * The key identifying edit encodings request
         */
        protected const val EDIT_ENCODINGS = 1

        /**
         * The key identifying edit security details request
         */
        protected const val EDIT_SECURITY = 2

        /**
         * Flag indicating if there are uncommitted changes - need static to avoid clear by android OS
         */
        @JvmStatic
        var uncommittedChanges = false
    }
}