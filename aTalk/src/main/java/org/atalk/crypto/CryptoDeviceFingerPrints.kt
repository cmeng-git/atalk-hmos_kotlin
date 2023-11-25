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
import android.os.Bundle
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.Toast
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.util.account.AccountUtils
import org.atalk.crypto.omemo.FingerprintStatus
import org.atalk.crypto.omemo.SQLiteOmemoStore
import org.atalk.hmos.R
import org.atalk.hmos.gui.util.ThemeHelper
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.hmos.gui.util.ViewUtil.setTextViewValue
import org.atalk.persistance.DatabaseBackend
import org.atalk.service.osgi.OSGiActivity
import org.atalk.util.CryptoHelper
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.internal.OmemoDevice
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint
import org.jivesoftware.smackx.omemo.trust.TrustState
import java.util.*

/**
 * Settings screen with known user account and its associated fingerprints
 *
 * @author Eng Chong Meng
 */
class CryptoDeviceFingerPrints : OSGiActivity() {
    private var mDB = DatabaseBackend.readableDB
    private var mOmemoStore: SQLiteOmemoStore? = null

    /* Fingerprints adapter instance. */
    private var fpListAdapter: FingerprintListAdapter? = null

    /* Map contains omemo devices and theirs associated fingerPrint */
    private val deviceFingerprints = TreeMap<String, String>()

    /* Map contains userDevice and its associated FingerPrintStatus */
    private val omemoDeviceFPStatus = LinkedHashMap<String, FingerprintStatus>()

    /* List contains all the own OmemoDevice */
    private val ownOmemoDevice = ArrayList<String>()

    /* Map contains bareJid and its associated Contact */
    private val contactList = HashMap<String, Contact?>()
    private var contact: Contact? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mOmemoStore = SignalOmemoService.getInstance().omemoStoreBackend as SQLiteOmemoStore
        setContentView(R.layout.list_layout)
        fpListAdapter = FingerprintListAdapter(getDeviceFingerPrints())
        val fingerprintsList = findViewById<ListView>(R.id.list)
        fingerprintsList.adapter = fpListAdapter
        registerForContextMenu(fingerprintsList)
    }

    /**
     * Gets the list of all known fingerPrints for OMEMO.
     *
     * @return a map of all known Map<bareJid></bareJid>, fingerPrints>.
     */
    private fun getDeviceFingerPrints(): Map<String, String> {
        // Get the protocol providers and meta-contactList service
        val providers = AccountUtils.registeredProviders

        // Get all the omemoDevices' fingerPrints from database
        getOmemoDeviceFingerprintStatus()
        for (pps in providers) {
            if (pps.connection == null) continue

            // Generate a list of own omemoDevices
            val omemoManager = OmemoManager.getInstanceFor(pps.connection)
            val userDevice = OMEMO + omemoManager.ownDevice
            ownOmemoDevice.add(userDevice)

        }
        return deviceFingerprints
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreateContextMenu(menu: ContextMenu, v: View?, menuInfo: ContextMenu.ContextMenuInfo) {
        var isVerified = false
        val keyExists = true
        super.onCreateContextMenu(menu, v, menuInfo)
        val inflater = menuInflater
        inflater.inflate(R.menu.fingerprint_ctx_menu, menu)
        val mTrust = menu.findItem(R.id.trust)
        val mDistrust = menu.findItem(R.id.distrust)
        val ctxInfo = menuInfo as AdapterContextMenuInfo
        val pos = ctxInfo.position
        val remoteFingerprint = fpListAdapter!!.getFingerprintFromRow(pos)
        val bareJid = fpListAdapter!!.getBareJidFromRow(pos)
        if (bareJid.startsWith(OMEMO)) {
            isVerified = isOmemoFPVerified(bareJid, remoteFingerprint)
        }

        // set visibility of trust option menu based on fingerPrint state
        mTrust.isVisible = !isVerified && keyExists
        mDistrust.isVisible = isVerified
        if (bareJid.startsWith(OMEMO)
                && (isOwnOmemoDevice(bareJid) || !isOmemoDeviceActive(bareJid))) {
            mTrust.isVisible = false
            mDistrust.isVisible = false
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onContextItemSelected(item: MenuItem): Boolean {
        val info = item.menuInfo as AdapterContextMenuInfo
        val pos = info.position
        val bareJid = fpListAdapter!!.getBareJidFromRow(pos)
        val remoteFingerprint = fpListAdapter!!.getFingerprintFromRow(pos)
        contact = contactList[bareJid]
        when (item.itemId) {
            R.id.trust -> {
                if (bareJid.startsWith(OMEMO)) {
                    trustOmemoFingerPrint(bareJid, remoteFingerprint)
                    val msg = getString(R.string.crypto_toast_OMEMO_TRUST_MESSAGE_RESUME, bareJid)
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
                fpListAdapter!!.notifyDataSetChanged()
                return true
            }
            R.id.distrust -> {
                if (bareJid.startsWith(OMEMO)) {
                    distrustOmemoFingerPrint(bareJid, remoteFingerprint)
                    val msg = getString(R.string.crypto_toast_OMEMO_DISTRUST_MESSAGE_STOP, bareJid)
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
                fpListAdapter!!.notifyDataSetChanged()
                return true
            }
            R.id.copy -> {
                val cbManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                if (cbManager != null) {
                    cbManager.setPrimaryClip(ClipData.newPlainText(null,
                        CryptoHelper.prettifyFingerprint(remoteFingerprint)))
                    Toast.makeText(this, R.string.crypto_toast_FINGERPRINT_COPY, Toast.LENGTH_SHORT).show()
                }
                return true
            }
            R.id.cancel -> return true
        }
        return super.onContextItemSelected(item)
    }
    // ============== OMEMO Device FingerPrintStatus Handlers ================== //
    /**
     * Fetch the OMEMO FingerPrints for all the device
     * Remove all those Devices has null fingerPrints
     */
    private fun getOmemoDeviceFingerprintStatus() {
        var fpStatus: FingerprintStatus?
        val cursor = mDB.query(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, null,
            null, null, null, null, null)
        while (cursor.moveToNext()) {
            fpStatus = FingerprintStatus.fromCursor(cursor)
            if (fpStatus != null) {
                val bareJid = OMEMO + fpStatus.omemoDevice
                omemoDeviceFPStatus[bareJid] = fpStatus
                deviceFingerprints[bareJid] = fpStatus.fingerPrint!!
            }
        }
        cursor.close()
    }

    /**
     * Get the trust state of fingerPrint from database. Do not get from local copy of omemoDeviceFPStatus as
     * trust state if not being updated
     *
     * @param userDevice OmemoDevice
     * @param fingerprint OmemoFingerPrint
     * @return boolean trust state
     */
    private fun isOmemoFPVerified(userDevice: String, fingerprint: String): Boolean {
        val omemoDevice = getOmemoDevice(userDevice)
        val fpStatus = mOmemoStore!!.getFingerprintStatus(omemoDevice, fingerprint)
        return fpStatus != null && fpStatus.isTrusted
    }

    private fun isOmemoDeviceActive(userDevice: String): Boolean {
        val fpStatus = omemoDeviceFPStatus[userDevice]
        return fpStatus != null && fpStatus.isActive
    }

    private fun isOwnOmemoDevice(userDevice: String): Boolean {
        return ownOmemoDevice.contains(userDevice)
    }

    private fun getOmemoDevice(userDevice: String): OmemoDevice {
        val fpStatus = omemoDeviceFPStatus[userDevice]
        return fpStatus!!.omemoDevice!!
    }

    /**
     * Trust an OmemoIdentity. This involves marking the key as trusted.
     *
     * @param bareJid BareJid
     * @param remoteFingerprint fingerprint
     */
    private fun trustOmemoFingerPrint(bareJid: String, remoteFingerprint: String) {
        val omemoDevice = getOmemoDevice(bareJid)
        val omemoFingerprint = OmemoFingerprint(remoteFingerprint)
        mOmemoStore!!.trustCallBack.setTrust(omemoDevice, omemoFingerprint, TrustState.trusted)
    }

    /**
     * Distrust an OmemoIdentity. This involved marking the key as distrusted.
     *
     * @param bareJid bareJid
     * @param remoteFingerprint fingerprint
     */
    private fun distrustOmemoFingerPrint(bareJid: String, remoteFingerprint: String) {
        val omemoDevice = getOmemoDevice(bareJid)
        val omemoFingerprint = OmemoFingerprint(remoteFingerprint)
        mOmemoStore!!.trustCallBack.setTrust(omemoDevice, omemoFingerprint, TrustState.untrusted)
    }
    //==============================================================
    /**
     * Adapter displays fingerprints for given list of `omemoDevices`s and `contacts`.
     */
    private inner class FingerprintListAdapter(linkedHashMap: Map<String, String>) : BaseAdapter() {
        /**
         * The list of currently displayed devices and FingerPrints.
         */
        private val deviceJid: List<String>
        private val deviceFP: List<String>

        /**
         * Creates new instance of `FingerprintListAdapter`.
         *
         * linkedHashMap list of `device` for which OMEMO fingerprints will be displayed.
         */
        init {
            deviceJid = ArrayList(linkedHashMap.keys)
            deviceFP = ArrayList(linkedHashMap.values)
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
            if (iRowView == null) {
                iRowView = layoutInflater.inflate(R.layout.crypto_fingerprint_row, parent, false)!!
            }

            var isVerified = false
            val bareJid = getBareJidFromRow(position)
            val remoteFingerprint = getFingerprintFromRow(position)
            setTextViewValue(iRowView, R.id.protocolProvider, bareJid)
            setTextViewValue(iRowView, R.id.fingerprint, CryptoHelper.prettifyFingerprint(remoteFingerprint))

            // Color for active fingerPrints
            ViewUtil.setTextViewColor(iRowView, R.id.fingerprint,
                if (ThemeHelper.isAppTheme(ThemeHelper.Theme.DARK)) R.color.textColorWhite else R.color.textColorBlack)
            if (bareJid.startsWith(OMEMO)) {
                when {
                    isOwnOmemoDevice(bareJid) ->
                        ViewUtil.setTextViewColor(iRowView, R.id.fingerprint, R.color.blue)
                    !isOmemoDeviceActive(bareJid) ->
                        ViewUtil.setTextViewColor(iRowView, R.id.fingerprint, R.color.grey500)
                }
                isVerified = isOmemoFPVerified(bareJid, remoteFingerprint)
            }

            val status = if (isVerified) R.string.crypto_FINGERPRINT_VERIFIED else R.string.crypto_FINGERPRINT_NOT_VERIFIED
            val verifyStatus = getString(R.string.crypto_FINGERPRINT_STATUS, getString(status))
            setTextViewValue(iRowView, R.id.fingerprint_status, verifyStatus)
            ViewUtil.setTextViewColor(iRowView, R.id.fingerprint_status, if (isVerified) (if (ThemeHelper.isAppTheme(ThemeHelper.Theme.DARK)) R.color.textColorWhite else R.color.textColorBlack) else R.color.orange500)
            return iRowView
        }

        fun getBareJidFromRow(row: Int): String {
            return deviceJid[row]
        }

        fun getFingerprintFromRow(row: Int): String {
            return deviceFP[row]
        }
    }

    companion object {
        private const val OMEMO = "OMEMO:"
    }
}