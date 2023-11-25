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
package org.atalk.crypto

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.util.account.AccountUtils
import org.apache.commons.lang3.StringUtils
import org.atalk.crypto.omemo.SQLiteOmemoStore
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.util.ViewUtil.setTextViewValue
import org.atalk.service.osgi.OSGiActivity
import org.atalk.util.CryptoHelper
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.OmemoService
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException
import timber.log.Timber
import java.io.IOException
import java.util.*

/**
 * Settings screen displays local private keys. Allows user to generate new or regenerate if one exists.
 *
 * @author Eng Chong Meng
 */
class CryptoPrivateKeys : OSGiActivity() {
    /**
     * Adapter used to displays private keys for all accounts.
     */
    private var accountsAdapter: PrivateKeyListAdapter? = null

    /**
     * Map to store bareJId to accountID sorted in ascending order
     */
    private val accountList = TreeMap<String, AccountID>()

    /* Map contains omemo devices and theirs associated fingerPrint */
    private val deviceFingerprints = TreeMap<String, String>()

    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.list_layout)
        val accountsKeysList = findViewById<ListView>(R.id.list)
        accountsAdapter = PrivateKeyListAdapter(getDeviceFingerPrints())
        accountsKeysList.adapter = accountsAdapter
        registerForContextMenu(accountsKeysList)
    }

    /**
     * Get the list of all registered accounts in ascending order
     *
     * @return the map of all known accounts with bareJid as key.
     */
    private fun getDeviceFingerPrints(): Map<String, String> {
        var deviceJid: String

        // Get all the registered protocolProviders
        val providers = AccountUtils.registeredProviders
        for (pps in providers) {
            if (pps.connection == null) continue
            val omemoManager = OmemoManager.getInstanceFor(pps.connection)
            val userDevice = omemoManager.ownDevice
            val accountId = pps.accountID
            val bareJid = accountId.accountJid

            // Get OmemoDevice fingerprint
            var fingerprint = ""
            deviceJid = OMEMO + userDevice
            try {
                val omemoFingerprint = omemoManager.ownFingerprint
                if (omemoFingerprint != null) fingerprint = omemoFingerprint.toString()
            } catch (e: SmackException.NotLoggedInException) {
                Timber.w("Get own fingerprint Exception: %s", e.message)
            } catch (e: CorruptedOmemoKeyException) {
                Timber.w("Get own fingerprint Exception: %s", e.message)
            } catch (e: IOException) {
                Timber.w("Get own fingerprint Exception: %s", e.message)
            }
            deviceFingerprints[deviceJid] = fingerprint
            accountList[deviceJid] = accountId
        }
        if (deviceFingerprints.isEmpty())
            deviceFingerprints[aTalkApp.getResString(R.string.service_gui_settings_CRYPTO_PRIV_KEYS_EMPTY)] = ""
        return deviceFingerprints
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreateContextMenu(menu: ContextMenu, v: View?, menuInfo: ContextMenu.ContextMenuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menuInflater.inflate(R.menu.crypto_key_ctx_menu, menu)
        val ctxInfo = menuInfo as AdapterContextMenuInfo
        val pos = ctxInfo.position
        val privateKey = accountsAdapter!!.getOwnKeyFromRow(pos)
        val isKeyExist = StringUtils.isNotEmpty(privateKey)
        menu.findItem(R.id.generate).isEnabled = !isKeyExist
        menu.findItem(R.id.regenerate).isEnabled = isKeyExist
    }

    /**
     * {@inheritDoc}
     */
    override fun onContextItemSelected(item: MenuItem): Boolean {
        val info = item.menuInfo as AdapterContextMenuInfo
        val pos = info.position
        val bareJid = accountsAdapter!!.getBareJidFromRow(pos)
        when (item.itemId) {
            R.id.generate -> {
                showGenerateKeyAlert(bareJid, false)
                accountsAdapter!!.notifyDataSetChanged()
                return true
            }
            R.id.regenerate -> {
                showGenerateKeyAlert(bareJid, true)
                accountsAdapter!!.notifyDataSetChanged()
                return true
            }
            R.id.copy -> {
                val privateKey = accountsAdapter!!.getOwnKeyFromRow(pos)
                val cbManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cbManager.setPrimaryClip(ClipData.newPlainText(null, CryptoHelper.prettifyFingerprint(privateKey)))
                Toast.makeText(this, R.string.crypto_toast_FINGERPRINT_COPY, Toast.LENGTH_SHORT).show()
                return true
            }
        }
        return super.onContextItemSelected(item)
    }

    /**
     * Displays alert asking user if he wants to regenerate or generate new privateKey.
     *
     * @param bareJid the account bareJid
     * @param isKeyExist `true`if key exist
     */
    private fun showGenerateKeyAlert(bareJid: String, isKeyExist: Boolean) {
        val accountId = accountList[bareJid]
        val getResStrId = if (isKeyExist) R.string.crypto_dialog_KEY_REGENERATE_QUESTION else R.string.crypto_dialog_KEY_GENERATE_QUESTION
        val warnMsg = if (bareJid.startsWith(OMEMO)) getString(R.string.pref_omemo_regenerate_identities_summary) else ""
        val message = getString(getResStrId, bareJid, warnMsg)
        val b = AlertDialog.Builder(this)
        b.setTitle(R.string.crypto_dialog_KEY_GENERATE_TITLE)
            .setMessage(message)
            .setPositiveButton(R.string.service_gui_PROCEED) { dialog: DialogInterface?, which: Int ->
                if (accountId != null && bareJid.startsWith(OMEMO)) {
                    regenerate(accountId)
                }
                accountsAdapter!!.notifyDataSetChanged()
            }
            .setNegativeButton(R.string.service_gui_CANCEL) { dialog: DialogInterface, which: Int -> dialog.dismiss() }.show()
    }

    /**
     * Regenerate the OMEMO keyPair parameters for the given accountId
     *
     * @param accountId the accountID
     */
    private fun regenerate(accountId: AccountID?) {
        val omemoStore = OmemoService.getInstance().omemoStoreBackend
        (omemoStore as SQLiteOmemoStore).regenerate(accountId!!)
    }

    /**
     * Adapter which displays privateKeys for the given list of accounts.
     */
    private inner class PrivateKeyListAdapter(fingerprintList: Map<String, String>) : BaseAdapter() {
        /**
         * The list of currently displayed devices and FingerPrints.
         */
        private val deviceJid: List<String>
        private val deviceFP: List<String>

        /**
         * Creates new instance of `FingerprintListAdapter`.
         *
         * fingerprintList list of `device` for which OMEMO fingerprints will be displayed.
         */
        init {
            deviceJid = ArrayList(fingerprintList.keys)
            deviceFP = ArrayList(fingerprintList.values)
        }

        /**
         * {@inheritDoc}
         */
        override fun getCount(): Int {
            return deviceFP.size
        }

        /**
         * {@inheritDoc}
         */
        override fun getItem(position: Int): Any {
            return getBareJidFromRow(position)
        }

        /**
         * {@inheritDoc}
         */
        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        /**
         * {@inheritDoc}
         */
        override fun getView(position: Int, rowView: View?, parent: ViewGroup): View {
            var iRowView = rowView
            if (iRowView == null) iRowView = layoutInflater.inflate(R.layout.crypto_privkey_list_row, parent, false)!!
            val bareJid = getBareJidFromRow(position)
            setTextViewValue(iRowView, R.id.protocolProvider, bareJid)
            val fingerprint = getOwnKeyFromRow(position)
            var fingerprintStr = fingerprint
            if (StringUtils.isEmpty(fingerprint)) {
                fingerprintStr = getString(R.string.crypto_NO_KEY_PRESENT)
            }
            setTextViewValue(iRowView, R.id.fingerprint, CryptoHelper.prettifyFingerprint(fingerprintStr))
            return iRowView
        }

        fun getBareJidFromRow(row: Int): String {
            return deviceJid[row]
        }

        fun getOwnKeyFromRow(row: Int): String {
            return deviceFP[row]
        }
    }

    companion object {
        private const val OMEMO = "OMEMO:"
    }
}