/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import net.java.sip.communicator.plugin.otr.OtrActivator
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.util.account.AccountUtils
import org.atalk.crypto.omemo.SQLiteOmemoStore
import org.atalk.hmos.R
import org.jivesoftware.smackx.omemo.OmemoService

/**
 * Helper class that produces "remove account dialog". It asks the user for account removal
 * confirmation and finally removes the account. Interface `OnAccountRemovedListener` is
 * used to notify about account removal which will not be fired if the user cancels the dialog.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
object RemoveAccountDialog {
    fun create(ctx: Context, account: Account, listener: OnAccountRemovedListener): AlertDialog {
        val alert = AlertDialog.Builder(ctx)
        return alert.setTitle(R.string.service_gui_REMOVE_ACCOUNT)
                .setMessage(ctx.getString(R.string.service_gui_REMOVE_ACCOUNT_MESSAGE, account.getAccountID()))
                .setPositiveButton(R.string.service_gui_YES) { dialog: DialogInterface, which: Int -> onRemoveClicked(dialog, account, listener) }
                .setNegativeButton(R.string.service_gui_NO) { dialog: DialogInterface, which: Int -> dialog.dismiss() }.create()
    }

    private fun onRemoveClicked(dialog: DialogInterface, account: Account, l: OnAccountRemovedListener) {
        // Fix "network access on the main thread"
        val removeAccountThread = object : Thread() {
            override fun run() {
                // cleanup omemo data for the deleted user account
                val accountId = account.getAccountID()
                val omemoStore = OmemoService.getInstance().omemoStoreBackend as SQLiteOmemoStore
                omemoStore.purgeUserOmemoData(accountId)

                // purge persistent storage must happen before removeAccount action
                AccountsListActivity.removeAccountPersistentStore(accountId)
                removeAccount(accountId)
            }
        }
        removeAccountThread.start()
        try {
            // Simply block UI thread as it shouldn't take too long to uninstall; ANR from field - wait on 3S timeout
            removeAccountThread.join(3000)
            // Notify about results
            l.onAccountRemoved(account)
            dialog.dismiss()
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Remove all the properties of the given `Account` from the accountProperties database.
     * Note: accountUuid without any suffix as propertyName will remove all the properties in
     * the accountProperties for the specified accountUuid
     *
     * @param accountId the accountId that will be uninstalled from the system.
     */
    private fun removeAccount(accountId: AccountID) {
        val providerFactory = AccountUtils.getProtocolProviderFactory(accountId.protocolName)
        val accountUuid = accountId.accountUuid!!
        OtrActivator.configService.setProperty(accountUuid, null)
        val isUninstalled = providerFactory!!.uninstallAccount(accountId)
        if (!isUninstalled) throw RuntimeException("Failed to uninstall account")
    }

    /**
     * Interfaces used to notify about account removal which happens after the user confirms the action.
     */
    interface OnAccountRemovedListener {
        /**
         * Fired after `Account` is removed from the system which happens after user
         * confirms the action. Will not be fired when user dismisses the dialog.
         *
         * @param account removed `Account`.
         */
        fun onAccountRemoved(account: Account)
    }
}