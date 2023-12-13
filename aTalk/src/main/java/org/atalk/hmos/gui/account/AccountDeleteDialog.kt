/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.hmos.gui.account

import android.content.Context
import android.os.Bundle
import android.widget.CheckBox
import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.util.account.AccountUtils
import org.atalk.crypto.omemo.SQLiteOmemoStore
import org.atalk.hmos.R
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.dialogs.DialogActivity.DialogListener
import org.jivesoftware.smackx.omemo.OmemoService

/**
 * Helper class that produces "remove account dialog". It asks the user for account removal
 * confirmation and finally removes the account. Interface `OnAccountRemovedListener` is
 * used to notify about account removal which will not be fired if the user cancels the dialog.
 *
 * @author Eng Chong Meng
 */
object AccountDeleteDialog {
    fun create(ctx: Context, account: Account, listener: OnAccountRemovedListener?) {
        val args = Bundle()
        args.putString(AccountDeleteFragment.ARG_MESSAGE,
            ctx.getString(R.string.service_gui_REMOVE_ACCOUNT_MESSAGE, account.getAccountID()))
        val title = ctx.getString(R.string.service_gui_REMOVE_ACCOUNT)

        // Displays the history delete dialog and waits for user confirmation
        DialogActivity.showCustomDialog(ctx, title, AccountDeleteFragment::class.java.name,
            args, ctx.getString(R.string.service_gui_DELETE), object : DialogListener {
                override fun onConfirmClicked(dialog: DialogActivity): Boolean {
                    val cbAccountDelete = dialog.findViewById<CheckBox>(R.id.cb_account_delete)
                    val accountDelete = cbAccountDelete.isChecked
                    onRemoveClicked(account, accountDelete, listener!!)
                    return true
                }

                override fun onDialogCancelled(dialog: DialogActivity) {}
            }, null)
    }

    private fun onRemoveClicked(account: Account, serverAccountDelete: Boolean, l: OnAccountRemovedListener) {
        // Fix "network access on the main thread"
        val removeAccountThread: Thread = object : Thread() {
            override fun run() {
                // cleanup omemo data for the deleted user account
                val accountId = account.getAccountID()
                val omemoStore = OmemoService.getInstance().omemoStoreBackend as SQLiteOmemoStore
                omemoStore.purgeUserOmemoData(accountId)

                // purge persistent storage must happen before removeAccount action
                AccountsListActivity.removeAccountPersistentStore(accountId)

                // Delete account on server
                if (serverAccountDelete) {
                    val pps = account.protocolProvider as ProtocolProviderServiceJabberImpl?
                    pps?.deleteAccountOnServer()
                }

                // Update account status
                removeAccount(accountId)
            }
        }
        removeAccountThread.start()
        try {
            // Simply block UI thread as it shouldn't take too long to uninstall; ANR from field - wait on 3S timeout
            removeAccountThread.join(3000)
            // Notify about results
            l.onAccountRemoved(account)
        } catch (e: InterruptedException) {
            throw java.lang.RuntimeException(e)
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