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
package org.atalk.crypto.omemo

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.util.account.AccountUtils
import org.atalk.hmos.R
import org.atalk.service.osgi.OSGiActivity
import org.jivesoftware.smackx.omemo.OmemoService
import java.util.*

/**
 * In case your device list gets filled with old unused identities, you can clean it up. This will remove
 * all inactive devices from the device list and only publish the device that you are using right now.
 *
 * @author Eng Chong Meng
 */
class OmemoDeviceDeleteDialog : OSGiActivity() {
    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accountMap = Hashtable<String, ProtocolProviderService>()
        val accounts = ArrayList<CharSequence>()
        val providers = AccountUtils.registeredProviders
        for (pps in providers) {
            if (pps.connection != null && pps.connection!!.isAuthenticated) {
                val accountId = pps.accountID
                val userId = accountId.mUserID!!
                accountMap[userId] = pps
                accounts.add(userId)
            }
        }
        val checkedItems = BooleanArray(accountMap.size)
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.pref_omemo_purge_devices_title)
        builder.setMultiChoiceItems(accounts.toTypedArray(), checkedItems) { dialog: DialogInterface, which: Int, isChecked: Boolean ->
            checkedItems[which] = isChecked
            val multiChoiceDialog = dialog as AlertDialog
            for (item in checkedItems) {
                if (item) {
                    multiChoiceDialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = true
                    return@setMultiChoiceItems
                }
            }
            multiChoiceDialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false
        }
        builder.setNegativeButton(R.string.service_gui_CANCEL) { dialog: DialogInterface?, which: Int -> finish() }
        builder.setPositiveButton(R.string.crypto_dialog_button_DELETE) { dialog: DialogInterface?, which: Int ->
            val mOmemoStore = OmemoService.getInstance().omemoStoreBackend as SQLiteOmemoStore
            for (i in checkedItems.indices) {
                if (checkedItems[i]) {
                    val pps = accountMap[accounts[i].toString()]
                    if (pps != null) {
                        mOmemoStore.purgeInactiveUserDevices(pps)
                    }
                }
            }
            finish()
        }
        val dialog = builder.create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
    }
}