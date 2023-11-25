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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ListView
import org.atalk.hmos.R
import org.atalk.hmos.gui.util.ViewUtil.setTextViewValue
import org.atalk.service.osgi.OSGiActivity
import org.atalk.util.CryptoHelper
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.exceptions.CannotEstablishOmemoSessionException
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException
import org.jivesoftware.smackx.omemo.internal.OmemoDevice
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint
import org.jivesoftware.smackx.omemo.trust.TrustState
import org.jxmpp.jid.BareJid
import timber.log.Timber
import java.io.IOException

/**
 * OMEMO buddy authenticate dialog.
 *
 * @author Eng Chong Meng
 */
open class OmemoAuthenticateDialog : OSGiActivity() {
    private var mOmemoStore: SQLiteOmemoStore? = null
    private val buddyFingerprints = HashMap<OmemoDevice, String>()
    private val deviceFPStatus = LinkedHashMap<OmemoDevice, FingerprintStatus?>()
    private val fingerprintCheck = HashMap<OmemoDevice?, Boolean>()

    /**
     * Fingerprints adapter instance.
     */
    private var fpListAdapter: FingerprintListAdapter? = null

    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            mOmemoStore = SignalOmemoService.getInstance().omemoStoreBackend as SQLiteOmemoStore
            // IllegalStateException from the field?
        } catch (ex: IllegalStateException) {
            finish()
        }
        setContentView(R.layout.omemo_authenticate_dialog)
        setTitle(R.string.omemo_authbuddydialog_AUTHENTICATE_BUDDY)
        fpListAdapter = FingerprintListAdapter(getBuddyFingerPrints())
        val fingerprintsList = findViewById<ListView>(R.id.fp_list)
        fingerprintsList.adapter = fpListAdapter
        var localFingerprint: String? = null
        var userJid: BareJid? = null
        // mOmemoManager can never be null from caller??? NPE from FFR: OmemoAuthenticateDialog.onCreate (OmemoAuthenticateDialog.java:122)
        // anyway move into try/catch with NullPointerException loop (20220329)
        try {
            userJid = mOmemoManager!!.ownJid
            localFingerprint = mOmemoManager!!.ownFingerprint.toString()
        } catch (e: SmackException.NotLoggedInException) {
            Timber.w("Get own fingerprint exception: %s", e.message)
        } catch (e: CorruptedOmemoKeyException) {
            Timber.w("Get own fingerprint exception: %s", e.message)
        } catch (e: IOException) {
            Timber.w("Get own fingerprint exception: %s", e.message)
        } catch (e: NullPointerException) {
            Timber.w("Get own fingerprint exception: %s", e.message)
        }
        val content = findViewById<View>(android.R.id.content)
        setTextViewValue(content, R.id.localFingerprintLbl,
                getString(R.string.omemo_authbuddydialog_LOCAL_FINGERPRINT, userJid,
                        CryptoHelper.prettifyFingerprint(localFingerprint)))
    }

    /**
     * Gets the list of all known buddyFPs.
     *
     * @return the list of all known buddyFPs.
     */
    private fun getBuddyFingerPrints(): Map<OmemoDevice, String> {
        var fingerprint: String
        var fpStatus: FingerprintStatus?
        if (mOmemoDevices != null) {
            for (device in mOmemoDevices!!) {
                // Default all devices' trust to false
                fingerprintCheck[device] = false
                try {
                    fingerprint = mOmemoManager!!.getFingerprint(device).toString()
                    buddyFingerprints[device] = fingerprint
                    fpStatus = mOmemoStore!!.getFingerprintStatus(device, fingerprint)
                    deviceFPStatus[device] = fpStatus
                } catch (e: CorruptedOmemoKeyException) {
                    buddyFingerprints[device] = Corrupted_OmemoKey
                    deviceFPStatus[device] = null
                } catch (e: CannotEstablishOmemoSessionException) {
                    buddyFingerprints[device] = Corrupted_OmemoKey
                    deviceFPStatus[device] = null
                } catch (e: SmackException.NotLoggedInException) {
                    Timber.w("Smack exception in fingerPrint fetch for omemo device: %s", device)
                } catch (e: SmackException.NotConnectedException) {
                    Timber.w("Smack exception in fingerPrint fetch for omemo device: %s", device)
                } catch (e: SmackException.NoResponseException) {
                    Timber.w("Smack exception in fingerPrint fetch for omemo device: %s", device)
                } catch (e: InterruptedException) {
                    Timber.w("Smack exception in fingerPrint fetch for omemo device: %s", device)
                } catch (e: IOException) {
                    Timber.w("Smack exception in fingerPrint fetch for omemo device: %s", device)
                }
            }
        }
        return buddyFingerprints
    }

    /**
     * Method fired when the ok button is clicked.
     *
     * @param v ok button's `View`.
     */
    fun onOkClicked(v: View?) {
        var allTrusted = true
        var fingerprint: String?
        for ((omemoDevice, fpCheck) in fingerprintCheck) {
            allTrusted = fpCheck && allTrusted
            if (fpCheck) {
                mOmemoDevices!!.remove(omemoDevice)
                fingerprint = buddyFingerprints[omemoDevice]
                if (Corrupted_OmemoKey == fingerprint) {
                    mOmemoStore!!.purgeCorruptedOmemoKey(mOmemoManager, omemoDevice!!)
                } else {
                    trustOmemoFingerPrint(omemoDevice!!, fingerprint)
                }
            } else {
                /* Do not change original fingerprint trust state */
                Timber.w("Leaving the fingerprintStatus as it: %s", omemoDevice)
            }
        }
        if (mListener != null) mListener!!.onAuthenticate(allTrusted, mOmemoDevices)
        finish()
    }

    /**
     * Method fired when the cancel button is clicked.
     *
     * @param v the cancel button's `View`
     */
    fun onCancelClicked(v: View?) {
        if (mListener != null) mListener!!.onAuthenticate(false, mOmemoDevices)
        finish()
    }

    // ============== OMEMO Buddy FingerPrints Handlers ================== //
    private fun isOmemoFPVerified(omemoDevice: OmemoDevice?, fingerprint: String?): Boolean {
        val fpStatus = mOmemoStore!!.getFingerprintStatus(omemoDevice, fingerprint)
        return fpStatus != null && fpStatus.isTrusted
    }

    /**
     * Trust an OmemoIdentity. This involves marking the key as trusted.
     *
     * @param omemoDevice OmemoDevice
     * @param remoteFingerprint fingerprint.
     */
    private fun trustOmemoFingerPrint(omemoDevice: OmemoDevice, remoteFingerprint: String?) {
        val omemoFingerprint = OmemoFingerprint(remoteFingerprint)
        mOmemoStore!!.trustCallBack.setTrust(omemoDevice, omemoFingerprint, TrustState.trusted)
    }

    /**
     * Adapter displays fingerprints for given list of `Contact`s.
     */
    private inner class FingerprintListAdapter(linkedHashMap: Map<OmemoDevice, String>) : BaseAdapter() {
        /**
         * The list of currently displayed buddy FingerPrints.
         */
        private val buddyFPs: Map<OmemoDevice, String>

        /**
         * Creates a new instance of `FingerprintListAdapter`.
         *
         * linkedHashMap list of `Contact` for which OMEMO fingerprints will be displayed.
         */
        init {
            buddyFPs = linkedHashMap
        }

        /**
         * {@inheritDoc}
         */
        override fun getCount(): Int {
            return buddyFPs.size
        }

        /**
         * {@inheritDoc}
         */
        override fun getItem(position: Int): OmemoDevice? {
            return getOmemoDeviceFromRow(position)
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
            if (iRowView == null) iRowView = layoutInflater.inflate(R.layout.omemo_fingerprint_row, parent, false)!!

            val device = getOmemoDeviceFromRow(position)
            val remoteFingerprint = getFingerprintFromRow(position)
            setTextViewValue(iRowView, R.id.protocolProvider, device.toString())
            setTextViewValue(iRowView, R.id.fingerprint, CryptoHelper.prettifyFingerprint(remoteFingerprint))
            val isVerified = isOmemoFPVerified(device, remoteFingerprint)
            val cbFingerprint = iRowView.findViewById<CheckBox>(R.id.fingerprint)
            cbFingerprint.isChecked = isVerified
            cbFingerprint.setOnClickListener { v: View? -> fingerprintCheck[device] = cbFingerprint.isChecked }
            return iRowView
        }

        fun getOmemoDeviceFromRow(row: Int): OmemoDevice? {
            var index = -1
            for (device in buddyFingerprints.keys) {
                index++
                if (index == row) {
                    return device
                }
            }
            return null
        }

        fun getFingerprintFromRow(row: Int): String? {
            var index = -1
            for (fingerprint in buddyFingerprints.values) {
                index++
                if (index == row) {
                    return fingerprint
                }
            }
            return null
        }
    }

    /**
     * The listener that will be notified when user clicks the confirm button or dismisses the dialog.
     */
    interface AuthenticateListener {
        /**
         * Fired when user clicks the dialog's confirm/cancel button.
         *
         * @param allTrusted allTrusted state.
         * @param omemoDevices set of unTrusted devices
         */
        fun onAuthenticate(allTrusted: Boolean, omemoDevices: Set<OmemoDevice>?)
    }

    companion object {
        const val Corrupted_OmemoKey = "Corrupted OmemoKey, purge?"
        private var mOmemoManager: OmemoManager? = null
        private var mOmemoDevices: MutableSet<OmemoDevice>? = null
        private var mListener: AuthenticateListener? = null

        /**
         * Creates parametrized `Intent` of buddy authenticate dialog.
         *
         * @param omemoManager the omemo manager of the session.
         * @return buddy authenticate dialog parametrized with given omemo session's UUID.
         */
        fun createIntent(context: Context?, omemoManager: OmemoManager?, omemoDevices: MutableSet<OmemoDevice>?,
                         listener: AuthenticateListener?): Intent {
            val intent = Intent(context, OmemoAuthenticateDialog::class.java)
            mOmemoManager = omemoManager
            mOmemoDevices = omemoDevices
            mListener = listener

            // Started not from Activity
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent
        }
    }
}