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
package org.atalk.persistance

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl
import net.java.sip.communicator.impl.protocol.jabber.ScServiceDiscoveryManager
import net.java.sip.communicator.plugin.loggingutils.LogsCollector
import net.java.sip.communicator.service.protocol.RegistrationState
import net.java.sip.communicator.util.account.AccountUtils
import org.atalk.crypto.omemo.SQLiteOmemoStore
import org.atalk.hmos.BuildConfig
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.persistance.migrations.OmemoDBCreate
import org.atalk.service.fileaccess.FileCategory
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.osgi.OSGiFragment
import org.jivesoftware.smackx.avatar.vcardavatar.VCardAvatarManager
import org.jivesoftware.smackx.caps.EntityCapsManager
import org.jivesoftware.smackx.omemo.OmemoService.getInstance
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Dialog allowing user to refresh persistent stores.
 *
 * @author Eng Chong Meng
 */
class ServerPersistentStoresRefreshDialog : OSGiFragment() {
    /**
     * {@inheritDoc}
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.refresh_persistent_stores, container, false)
        if (BuildConfig.DEBUG) {
            view.findViewById<View>(R.id.cb_export_database).visibility = View.VISIBLE
            // view.findViewById(R.id.cb_del_database).setVisibility(View.VISIBLE);
        }
        return view
    }

    /**
     * Displays create refresh store dialog. If the source wants to be notified about the result
     * should pass the listener here or `null` otherwise.
     *
     * @param parent the parent `Activity`
     */
    fun show(parent: Activity) {
        DialogActivity.showCustomDialog(parent,
                parent.getString(R.string.service_gui_REFRESH_STORES),
                ServerPersistentStoresRefreshDialog::class.java.name, null,
                parent.getString(R.string.service_gui_REFRESH_APPLY),
                DialogListenerImpl(), null)
    }

    /**
     * Implements `DialogActivity.DialogListener` interface and handles refresh stores process.
     */
    internal inner class DialogListenerImpl : DialogActivity.DialogListener {
        override fun onConfirmClicked(dialog: DialogActivity): Boolean {
            val view = dialog.contentFragment!!.view!!
            val cbRoster= view.findViewById<CheckBox>(R.id.cb_roster)
            val cbCaps = view.findViewById<CheckBox>(R.id.cb_caps)
            val cbDiscoInfo = view.findViewById<CheckBox>(R.id.cb_discoInfo)
            val cbAvatar = view.findViewById<CheckBox>(R.id.cb_avatar)
            val cbOmemo = view.findViewById<CheckBox>(R.id.cb_omemo)
            val cbDebugLog = view.findViewById<CheckBox>(R.id.cb_debug_log)
            val cbExportDB = view.findViewById<CheckBox>(R.id.cb_export_database)
            val cbDeleteDB = view.findViewById<CheckBox>(R.id.cb_del_database)
            if (cbRoster.isChecked) {
                refreshRosterStore()
            }
            if (cbCaps.isChecked) {
                refreshCapsStore()
            }
            if (cbDiscoInfo.isChecked) {
                refreshDiscoInfoStore()
            }
            if (cbAvatar.isChecked) {
                purgeAvatarStorage()
            }
            if (cbOmemo.isChecked) {
                purgeOmemoStorage()
            }
            if (cbDebugLog.isChecked) {
                purgeDebugLog()
            }
            if (cbExportDB.isChecked) {
                exportDB()
            }
            if (cbDeleteDB.isChecked) {
                deleteDB()
            }
            return true
        }

        override fun onDialogCancelled(dialog: DialogActivity) {}
    }

    /**
     * Process to refresh roster store for each registered account
     * Persistent Store for XEP-0237:Roster Versioning
     */
    private fun refreshRosterStore() {
        val ppServices = AccountUtils.registeredProviders
        for (pps in ppServices) {
            val jabberProvider = pps as ProtocolProviderServiceJabberImpl
            val rosterStoreDirectory = jabberProvider.rosterStoreDirectory
            if (rosterStoreDirectory != null && rosterStoreDirectory.exists()) {
                try {
                    FileBackend.deleteRecursive(rosterStoreDirectory)
                } catch (e: IOException) {
                    Timber.e("Failed to purge store for: %s", R.string.service_gui_REFRESH_STORES_ROSTER)
                }
                jabberProvider.initRosterStore()
            }
        }
    }

    /**
     * Process to refresh the single Entity Capabilities store for all accounts
     * Persistent Store for XEP-0115:Entity Capabilities
     */
    private fun refreshCapsStore() {
        // stop roster from accessing the store
        EntityCapsManager.setPersistentCache(null)
        EntityCapsManager.clearMemoryCache()
        val entityStoreDirectory = ScServiceDiscoveryManager.entityPersistentStore
        if (entityStoreDirectory != null && entityStoreDirectory.exists()) {
            try {
                FileBackend.deleteRecursive(entityStoreDirectory)
            } catch (e: IOException) {
                Timber.e("Failed to purchase store for: %s", R.string.service_gui_REFRESH_STORES_CAPS)
            }
            ScServiceDiscoveryManager.initEntityPersistentStore()
        }
    }

    /**
     * Process to refresh Disco#info store for all accounts
     * Persistent Store for XEP-0030:Service Discovery
     */
    private fun refreshDiscoInfoStore() {
        val ppServices = AccountUtils.registeredProviders
        for (pps in ppServices) {
            val jabberProvider = pps as ProtocolProviderServiceJabberImpl
            val discoveryInfoManager = jabberProvider.discoveryManager
                    ?: return
            if (jabberProvider.isRegistered) {
                if (RegistrationState.REGISTERED == jabberProvider.registrationState) {
                    // stop discoveryInfoManager from accessing the store
                    discoveryInfoManager.setDiscoInfoPersistentStore(null)
                    discoveryInfoManager.clearDiscoInfoPersistentCache()
                }
            }
            val discoInfoStoreDirectory = discoveryInfoManager.discoInfoPersistentStore
            if (discoInfoStoreDirectory != null && discoInfoStoreDirectory.exists()) {
                try {
                    FileBackend.deleteRecursive(discoInfoStoreDirectory)
                } catch (e: IOException) {
                    Timber.e("Failed to purchase store for: %s", R.string.service_gui_REFRESH_STORES_DISCINFO)
                }
                discoveryInfoManager.initDiscoInfoPersistentStore()
            }
        }
    }

    /**
     * Process to clear the VCard Avatar Index and purge persistent storage for all accounts
     * XEP-0153: vCard-Based Avatars
     */
    private fun purgeAvatarStorage() {
        VCardAvatarManager.clearPersistentStorage()
    }

    /**
     * Process to purge persistent storage for OMEMO_Store
     * XEP-0384: OMEMO Encryption
     */
    private fun purgeOmemoStorage() {
        // accountID omemo key attributes
        val JSONKEY_REGISTRATION_ID = "omemoRegId"
        val JSONKEY_CURRENT_PREKEY_ID = "omemoCurPreKeyId"
        val ctx = aTalkApp.globalContext
        val omemoStoreDB = getInstance().omemoStoreBackend
        val ppServices = AccountUtils.registeredProviders
        if (omemoStoreDB is SQLiteOmemoStore) {
            val db = DatabaseBackend.getInstance(ctx)
            for (pps in ppServices) {
                val accountId = pps.accountID
                accountId.unsetKey(JSONKEY_CURRENT_PREKEY_ID)
                accountId.unsetKey(JSONKEY_REGISTRATION_ID)
                db.updateAccount(accountId)
            }
            OmemoDBCreate.createOmemoTables(db.writableDatabase)

            // start to regenerate all Omemo data for registered accounts - has exception
            // SQLiteOmemoStore.loadOmemoSignedPreKey().371 There is no SignedPreKeyRecord for: 0
            // SignedPreKeyRecord.getKeyPair()' on a null object reference
            for (pps in ppServices) {
                val accountId = pps.accountID
                omemoStoreDB.regenerate(accountId)
            }
        } else {
            val omemoStore = "OMEMO_Store"
            val omemoDir = File(ctx.filesDir, omemoStore)
            if (omemoDir.exists()) {
                try {
                    FileBackend.deleteRecursive(omemoDir)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        Timber.i("### Omemo store has been refreshed!")
    }

    private fun exportDB() {
        val clFileName = "contactlist.xml"
        val omemoStore = "OMEMO_Store"
        val database = "databases"
        val sharedPrefs = "shared_prefs"
        val history = "history_ver1.0"
        val appFilesDir = aTalkApp.globalContext.filesDir
        val appRootDir = appFilesDir.parentFile
        val appDBDir = File(appRootDir, database)
        val appSPDir = File(appRootDir, sharedPrefs)
        val appHistoryDir = File(appFilesDir, history)
        val appOmemoDir = File(appFilesDir, omemoStore)
        val appXmlFP = File(appRootDir, clFileName)
        val atalkExportDir = FileBackend.getaTalkStore(FileBackend.EXPROT_DB, true)
        try {
            // Clean up old contents before create new
            FileBackend.deleteRecursive(atalkExportDir)
            if (!atalkExportDir.mkdirs()) {
                Timber.e("Could not create atalk dir: %s", atalkExportDir)
            }
            // To copy everything under files (large amount of data).
            // FileBackend.copyRecursive(appDBDir, atalkDLDir, null);
            FileBackend.copyRecursive(appDBDir, atalkExportDir, database)
            FileBackend.copyRecursive(appSPDir, atalkExportDir, sharedPrefs)
            if (appOmemoDir.exists()) {
                FileBackend.copyRecursive(appOmemoDir, atalkExportDir, omemoStore)
            }
            if (appHistoryDir.exists()) {
                FileBackend.copyRecursive(appHistoryDir, atalkExportDir, history)
            }
            if (appXmlFP.exists()) {
                FileBackend.copyRecursive(appXmlFP, atalkExportDir, clFileName)
            }
        } catch (e: Exception) {
            Timber.w("Export database exception: %s", e.message)
        }
    }

    companion object {
        /**
         * Warn: Delete the aTalk dataBase
         * Static access from other module
         */
        fun deleteDB() {
            val ctx = aTalkApp.globalContext
            ctx.deleteDatabase(DatabaseBackend.DATABASE_NAME)
        }

        /**
         * Process to purge all debug log files in case it gets too large to handle
         * Static access from other module
         */
        fun purgeDebugLog() {
            val logDir: File?
            try {
                logDir = LibJitsi.fileAccessService.getPrivatePersistentDirectory(LogsCollector.LOGGING_DIR_NAME, FileCategory.LOG)
                if (logDir != null && logDir.exists()) {
                    val files = logDir.listFiles()!!
                    for (file in files) {
                        if (!file.delete()) Timber.w("Couldn't delete log file: %s", file.name)
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Couldn't delete log file directory.")
            }
        }
    }
}