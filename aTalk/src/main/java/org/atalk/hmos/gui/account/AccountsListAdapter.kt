/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import net.java.sip.communicator.impl.configuration.ConfigurationActivator
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.atalk.hmos.R
import org.atalk.hmos.gui.util.CollectionAdapter
import org.atalk.hmos.gui.util.event.EventListener
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import timber.log.Timber

/**
 * This is a convenience class which implements an Adapter interface to put the list of
 * [Account]s into Android widgets.
 *
 * The [View]s for each row are created from the layout resource id given in constructor.
 * This view should contain: <br></br>
 * - `R.id.accountName` for the account name text ([TextView]) <br></br>
 * - `R.id.accountProtoIcon` for the protocol icon of type ([ImageView]) <br></br>
 * - `R.id.accountStatusIcon` for the presence status icon ([ImageView]) <br></br>
 * - `R.id.accountStatus` for the presence status name ([TextView]) <br></br>
 * It implements [EventListener] to refresh the list on any changes to the [Account].
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class AccountsListAdapter(parent: Activity,
        /**
         * The [View] resources ID describing list's row
         */
        private val listRowResourceID: Int,
        /**
         * The [View] resources ID describing list's row
         */
        private val dropDownRowResourceID: Int,

        accounts: Collection<AccountID?>,

        /**
         * The flag indicates whether disabled accounts should be filtered out from the list
         */
        private val filterDisabledAccounts: Boolean) : CollectionAdapter<Account>(parent), EventListener<AccountEvent?>, ServiceListener {

    /**
     * The [BundleContext] of parent OSGiActivity
     */
    private val bundleContext = ConfigurationActivator.bundleContext

    /**
     * Creates new instance of AccountsListAdapter
     *
     * parent the Activity running this adapter
     * accounts collection of accounts that will be displayed
     * listRowResourceID the layout resource ID see AccountsListAdapter for detailed description
     * filterDisabledAccounts flag indicates if disabled accounts should be filtered out from the list
     */
    init {
        bundleContext.addServiceListener(this)
        initAccounts(accounts)
    }

    /**
     * Initialize the list and filters out disabled accounts if necessary.
     *
     * @param collection set of [AccountID] that will be displayed
     */
    private fun initAccounts(collection: Collection<AccountID?>) {
        val accounts = ArrayList<Account>()
        for (acc in collection) {
            val account = Account(acc!!, bundleContext, parentActivity)
            if (filterDisabledAccounts && !account.isEnabled()) continue

            // Skip hidden accounts
            if (acc.isHidden) continue
            account.addAccountEventListener(this as EventListener<AccountEvent>)
            accounts.add(account)
        }
        setList(accounts)
    }

    override fun serviceChanged(event: ServiceEvent) {
        // if the event is caused by a bundle being stopped, we don't want to know
        if (event.serviceReference.bundle.state == Bundle.STOPPING) {
            return
        }

        // we don't care if the source service is not a protocol provider
        val sourceService = bundleContext.getService(event.serviceReference) as? ProtocolProviderService
                ?: return

        // Add or remove the protocol provider from our accounts list.
        if (event.type == ServiceEvent.REGISTERED) {
            val acc = findAccountID(sourceService.accountID)
            if (acc == null) {
                addAccount(Account(sourceService.accountID, bundleContext, parentActivity.baseContext))
            } else {
                acc.addAccountEventListener(this as EventListener<AccountEvent>)
            }
        } else if (event.type == ServiceEvent.UNREGISTERING) {
            val account = findAccountID(sourceService.accountID)
            // Remove enabled account if exist
            if (account != null && account.isEnabled()) {
                removeAccount(account)
            }
        }
    }

    /**
     * Unregisters status update listeners for accounts
     */
    fun deinitStatusListeners() {
        for (accIdx in 0 until count) {
            val account = getObject(accIdx)
            account.destroy()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun getView(isDropDown: Boolean, item: Account, parent: ViewGroup, inflater: LayoutInflater): View {
        var rowResID = listRowResourceID
        if (isDropDown && dropDownRowResourceID != -1) {
            rowResID = dropDownRowResourceID
        }
        val statusItem = inflater.inflate(rowResID, parent, false)
        val accountName = statusItem.findViewById<TextView>(R.id.protocolProvider)
        val accountProtocol = statusItem.findViewById<ImageView>(R.id.accountProtoIcon)
        val statusIconView = statusItem.findViewById<ImageView>(R.id.accountStatusIcon)
        val accountStatus = statusItem.findViewById<TextView>(R.id.accountStatus)

        // Sets account's properties
        if (accountName != null) accountName.text = item.getAccountName()
        if (accountProtocol != null) {
            val protoIcon = item.getProtocolIcon()
            if (protoIcon != null) {
                accountProtocol.setImageDrawable(protoIcon)
            }
        }
        if (accountStatus != null) accountStatus.text = item.getStatusName()
        if (statusIconView != null) {
            val statusIcon = item.avatarIcon
            if (statusIcon != null) {
                statusIconView.setImageDrawable(statusIcon)
            }
        }
        return statusItem
    }

    /**
     * Check if given `account` exists on the list
     *
     * @param account [AccountID] that has to be found on the list
     * @return `true` if account is on the list
     */
    private fun findAccountID(account: AccountID): Account? {
        for (i in 0 until count) {
            val acc = getObject(i)
            if (acc.getAccountID() == account) return acc
        }
        return null
    }

    /**
     * Adds new account to the list
     *
     * @param account [Account] that will be added to the list
     */
    private fun addAccount(account: Account) {
        if (filterDisabledAccounts && !account.isEnabled()) return
        if (account.getAccountID().isHidden) return
        Timber.d("Account added: %s", account.getUserID())
        add(account)
        account.addAccountEventListener(this as EventListener<AccountEvent>)
    }

    /**
     * Removes the account from the list
     *
     * @param account the [Account] that will be removed from the list
     */
    private fun removeAccount(account: Account) {
        Timber.d("Account removed: %s", account.getUserID())
        account.removeAccountEventListener(this as EventListener<AccountEvent>)
        remove(account)
    }

    /**
     * Does refresh the list
     *
     * @param eventObject the [AccountEvent] that caused the change event
     */
    override fun onChangeEvent(eventObject: AccountEvent?) {
        // Timber.log(TimberLog.FINE, "Not an Error! Received accountEvent update for: "
        //		+ accountEvent.getSource().getAccountName() + " "
        //		+ accountEvent.toString(), new Throwable());
        doRefreshList()
    }
}