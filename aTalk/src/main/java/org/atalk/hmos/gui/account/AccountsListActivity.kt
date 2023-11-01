/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ListView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AlertDialog
import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.AccountManager
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.util.ServiceUtils
import net.java.sip.communicator.util.account.AccountUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.account.RemoveAccountDialog.OnAccountRemovedListener
import org.atalk.hmos.gui.account.RemoveAccountDialog.create
import org.atalk.hmos.gui.account.settings.AccountPreferenceActivity
import org.atalk.hmos.gui.contactlist.AddGroupDialog
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.dialogs.ProgressDialogFragment
import org.atalk.hmos.plugin.certconfig.TLS_Configuration
import org.atalk.persistance.FileBackend
import org.atalk.persistance.ServerPersistentStoresRefreshDialog
import org.atalk.service.osgi.OSGiActivity
import org.jivesoftware.smackx.avatar.vcardavatar.VCardAvatarManager
import org.jxmpp.jid.BareJid
import org.jxmpp.stringprep.XmppStringprepException
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * The activity display list of currently stored accounts showing the associated protocol and current status.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class AccountsListActivity : OSGiActivity() {
    /**
     * The list adapter for accounts
     */
    private var listAdapter: AccountStatusListAdapter? = null

    /**
     * The [AccountManager] used to operate on [AccountID]s
     */
    private var accountManager: AccountManager? = null

    /**
     * Stores clicked account in member field, as context info is not available. That's because account
     * list contains on/off buttons and that prevents from "normal" list item clicks / long clicks handling.
     */
    private lateinit var clickedAccount: Account
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setMainTitle(R.string.service_gui_ACCOUNT)
        if (AndroidGUIActivator.bundleContext == null) {
            // No OSGi Exists
            Timber.e("OSGi not initialized")
            finish()
            return
        }
        setContentView(R.layout.account_list)
        accountManager = ServiceUtils.getService(AndroidGUIActivator.bundleContext, AccountManager::class.java)
    }

    override fun onResume() {
        super.onResume()

        // Need to refresh the list each time in case account might be removed in other Activity.
        // Also it can't be removed on "unregistered" event, because on/off buttons will cause the account to disappear
        accountsInit()
    }

    override fun onDestroy() {
        // Unregisters presence status listeners
        if (listAdapter != null) {
            listAdapter!!.deinitStatusListeners()
        }
        super.onDestroy()
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.account_settings_menu, menu)
        return true
    }

    /**
     * {@inheritDoc}
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.add_account -> {
                val intent = Intent(this, AccountLoginActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.add_group -> {
                AddGroupDialog.showCreateGroupDialog(this, null)
                true
            }
            R.id.TLS_Configuration -> {
                val tlsConfiguration = TLS_Configuration()
                val ft = supportFragmentManager.beginTransaction()
                ft.addToBackStack(null)
                ft.replace(android.R.id.content, tlsConfiguration).commit()
                true
            }
            R.id.refresh_database -> {
                ServerPersistentStoresRefreshDialog().show(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Initializes the accounts table.
     */
    private fun accountsInit() {
        // Create accounts array
        val accountIDCollection = AccountUtils.storedAccounts

        // Create account list adapter
        listAdapter = AccountStatusListAdapter(accountIDCollection)

        // Puts the adapter into accounts ListView
        val lv = findViewById<ListView>(R.id.accountListView)
        lv.adapter = listAdapter
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreateContextMenu(menu: ContextMenu, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menuInflater.inflate(R.menu.account_ctx_menu, menu)

        // Set menu title
        menu.setHeaderTitle(clickedAccount.getAccountName())

        // No access for account settings or info if not registered
        val accountSettings = menu.findItem(R.id.account_settings)
        accountSettings.isVisible = clickedAccount.protocolProvider != null
        val accountInfo = menu.findItem(R.id.account_info)
        accountInfo.isVisible = clickedAccount.protocolProvider != null
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.remove -> {
                create(this, clickedAccount, object : OnAccountRemovedListener {
                    override fun onAccountRemoved(account: Account) {
                        listAdapter!!.remove(account)
                    }
                }).show()
                true
            }
            R.id.account_settings -> {
                startPreferenceActivity(clickedAccount)
                true
            }
            R.id.account_info -> {
                startPresenceActivity(clickedAccount)
                true
            }
            R.id.account_cancel -> true
            else -> super.onContextItemSelected(item)
        }
    }

    /**
     * Starts the [AccountPreferenceActivity] for clicked [Account]
     *
     * @param account the `Account` for which preference settings will be opened.
     */
    private fun startPreferenceActivity(account: Account?) {
        val preferences = AccountPreferenceActivity.getIntent(this, account!!.getAccountID())
        startActivity(preferences)
    }

    /**
     * Starts the [AccountInfoPresenceActivity] for clicked [Account]
     *
     * @param account the `Account` for which settings will be opened.
     */
    private fun startPresenceActivity(account: Account?) {
        val statusIntent = Intent(this, AccountInfoPresenceActivity::class.java)
        statusIntent.putExtra(AccountInfoPresenceActivity.INTENT_ACCOUNT_ID,
                account!!.getAccountID().accountUniqueID)
        startActivity(statusIntent)
    }

    /**
     * Class responsible for creating list row Views
     */
    internal inner class AccountStatusListAdapter
    /**
     * Creates new instance of [AccountStatusListAdapter]
     *
     * @param accounts array of currently stored accounts
     */
    (accounts: Collection<AccountID?>) : AccountsListAdapter(this@AccountsListActivity, R.layout.account_list_row, -1, accounts, false) {
        /**
         * Toast instance
         */
        private var offlineToast: Toast? = null

        /**
         * {@inheritDoc}
         */
        override fun getView(isDropDown: Boolean, item: Account, parent: ViewGroup, inflater: LayoutInflater): View {
            // Creates the list view
            val rowView = super.getView(isDropDown, item, parent, inflater)
            rowView.isClickable = true

            rowView.setOnClickListener { v: View? ->
                // Start only for registered accounts
                if (item.protocolProvider != null) {
                    startPreferenceActivity(item)
                } else {
                    val msg = getString(R.string.service_gui_ACCOUNT_UNREGISTERED, item.getAccountName())
                    if (offlineToast == null) {
                        offlineToast = Toast.makeText(this@AccountsListActivity, msg, Toast.LENGTH_SHORT)
                    } else {
                        offlineToast!!.setText(msg)
                    }
                    offlineToast!!.show()
                }
            }

            rowView.setOnLongClickListener { v: View? ->
                registerForContextMenu(v)
                clickedAccount = item
                openContextMenu(v)
                true
            }

            val button = rowView.findViewById<ToggleButton>(R.id.accountToggleButton)
            button.isChecked = item.isEnabled()
            button.setOnCheckedChangeListener { compoundButton: CompoundButton, enable: Boolean ->
                if (accEnableThread != null) {
                    Timber.e("Ongoing operation in progress")
                    return@setOnCheckedChangeListener
                }
                Timber.d("Toggle %s -> %s", item, enable)

                // Prevents from switching the state after key pressed. Refresh will be
                // triggered by the thread when it finishes the operation.
                compoundButton.isChecked = item.isEnabled()
                accEnableThread = AccountEnableThread(item.getAccountID(), enable)
                val message = if (enable) getString(R.string.service_gui_CONNECTING_ACCOUNT, item.getAccountName())
                else getString(R.string.service_gui_DISCONNECTING_ACCOUNT, item.getAccountName())
                progressDialog = ProgressDialogFragment.showProgressDialog(getString(R.string.service_gui_INFO), message)
                accEnableThread!!.start()
            }
            return rowView
        }

    }

    /**
     * The thread that runs enable/disable operations
     */
    internal inner class AccountEnableThread(account: AccountID, enable: Boolean) : Thread() {
        /**
         * The [AccountID] that will be enabled or disabled
         */
        private val account: AccountID

        /**
         * Flag decides whether account shall be disabled or enabled
         */
        private val enable: Boolean

        /**
         * Creates new instance of AccountEnableThread
         *
         * account the AccountID that will be enabled or disabled
         * enable flag indicates if this is enable or disable operation
         */
        init {
            this.account = account
            this.enable = enable
        }

        override fun run() {
            try {
                if (enable) accountManager!!.loadAccount(account) else {
                    accountManager!!.unloadAccount(account)
                }
            } catch (e: OperationFailedException) {
                val message = "Failed to " + (if (enable) "load" else "unload") + " " + account
                Handler(Looper.getMainLooper()).post {
                    AlertDialog.Builder(this@AccountsListActivity)
                            .setTitle(R.string.service_gui_ERROR)
                            .setMessage(message)
                            .setPositiveButton(R.string.service_gui_OK, null)
                            .show()
                }
                Timber.e("Account de/activate Exception: %s", e.message)
            } finally {
                if (DialogActivity.waitForDialogOpened(progressDialog)) {
                    DialogActivity.closeDialog(progressDialog)
                } else {
                    Timber.e("Failed to wait for the dialog: %s", progressDialog)
                }
                accEnableThread = null
            }
        }
    }

    companion object {
        /**
         * Keeps track of displayed "in progress" dialog during account registration.
         */
        private var progressDialog = 0L

        /**
         * Keeps track of thread used to register accounts and prevents from starting multiple at one time.
         */
        private var accEnableThread: AccountEnableThread? = null

        /**
         * Removes the account persistent storage from the device
         *
         * @param accountId the [AccountID] for whom the persistent to be purged from the device
         */
        fun removeAccountPersistentStore(accountId: AccountID?) {
            val pps = accountId!!.protocolProvider
            if (pps is ProtocolProviderServiceJabberImpl) {

                // Purge avatarHash and avatarImages of all contacts belong to the account roster
                val userJid = accountId.bareJid!!
                try {
                    VCardAvatarManager.clearPersistentStorage(userJid)
                } catch (e: XmppStringprepException) {
                    Timber.e("Failed to purge store for: %s", R.string.service_gui_REFRESH_STORES_AVATAR)
                }
                val rosterStoreDirectory = pps.rosterStoreDirectory
                try {
                    if (rosterStoreDirectory != null) FileBackend.deleteRecursive(rosterStoreDirectory)
                } catch (e: IOException) {
                    Timber.e("Failed to purge store for: %s", R.string.service_gui_REFRESH_STORES_ROSTER)
                }

                // Account in unRegistering so discoveryInfoManager == null
                // ScServiceDiscoveryManager discoveryInfoManager = jabberProvider.getDiscoveryManager();
                // File discoInfoStoreDirectory = discoveryInfoManager.getDiscoInfoPersistentStore();
                val discoInfoStoreDirectory = File(aTalkApp.globalContext.filesDir
                        .toString() + "/discoInfoStore_" + userJid)
                try {
                    FileBackend.deleteRecursive(discoInfoStoreDirectory)
                } catch (e: IOException) {
                    Timber.e("Failed to purge store for: %s", R.string.service_gui_REFRESH_STORES_DISCINFO)
                }
            }
        }
    }
}