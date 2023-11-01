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
package org.atalk.impl.appupdate

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import net.java.sip.communicator.service.update.UpdateService
import net.java.sip.communicator.util.ServiceUtils.getService
import org.atalk.hmos.BuildConfig
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.aTalkApp.Companion.globalContext
import org.atalk.hmos.aTalkApp.Companion.showToastMessage
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.dialogs.DialogActivity.Companion.showDialog
import org.atalk.hmos.gui.dialogs.DialogActivity.DialogListener
import org.atalk.persistance.FileBackend
import org.atalk.persistance.FileBackend.getaTalkStore
import org.atalk.persistance.FilePathHelper.getFilePath
import org.atalk.service.version.Version
import org.atalk.service.version.VersionService
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

/**
 * aTalk update service implementation. It checks for an update and schedules .apk download using `DownloadManager`.
 * It is only activated for the debug version. Android initials the auto-update from PlayStore for release version.
 *
 * @author Eng Chong Meng
 */
class UpdateServiceImpl : UpdateService {
    /**
     * The download link for the installed application
     */
    private var downloadLink: String? = null

    /**
     * Current installed version string / version Code
     */
    private var currentVersion: String? = null
    private var currentVersionCode: Long = 0

    /**
     * The latest version string / version code
     */
    private var latestVersion: String? = null
    private var latestVersionCode: Long = 0
    private var mIsLatest = false

    /* DownloadManager Broadcast Receiver Handler */
    private var downloadReceiver: DownloadReceiver? = null
    private var mHttpConnection: HttpURLConnection? = null

    /**
     * `SharedPreferences` used to store download ids.
     */
    private var store: SharedPreferences? = null
        get() {
            if (field == null) {
                field = globalContext.getSharedPreferences("store", Context.MODE_PRIVATE)
            }
            return field
        }

    /**
     * Checks for updates and notify user of any new version, and take necessary action.
     */
    override fun checkForUpdates() {
        // cmeng: reverse the logic to !isLatestVersion() for testing
        mIsLatest = isLatestVersion
        Timber.i("Is latest: %s\nCurrent version: %s\nLatest version: %s\nDownload link: %s",
                mIsLatest, currentVersion, latestVersion, downloadLink)
        if (downloadLink != null) {
            if (!mIsLatest) {
                if (checkLastDLFileAction() < DownloadManager.ERROR_UNKNOWN) return
                DialogActivity.showConfirmDialog(globalContext,
                        R.string.plugin_update_Install_Update,
                        R.string.plugin_update_Update_Available,
                        R.string.plugin_update_Download,
                        object : DialogListener {
                            override fun onConfirmClicked(dialog: DialogActivity): Boolean {
                                downloadApk()
                                return true
                            }

                            override fun onDialogCancelled(dialog: DialogActivity) {}
                        }, latestVersion, latestVersionCode.toString(), aTalkApp.getResString(R.string.APPLICATION_NAME), currentVersion
                )
            } else {
                // Notify that running version is up to date
                DialogActivity.showConfirmDialog(globalContext,
                        R.string.plugin_update_New_Version_None,
                        R.string.plugin_update_UpToDate,
                        R.string.plugin_update_Download,
                        object : DialogListener {
                            override fun onConfirmClicked(dialog: DialogActivity): Boolean {
                                if (aTalk.hasWriteStoragePermission(aTalk.instance, true)) {
                                    if (checkLastDLFileAction() >= DownloadManager.ERROR_UNKNOWN) {
                                        downloadApk()
                                    }
                                }
                                return true
                            }

                            override fun onDialogCancelled(dialog: DialogActivity) {}
                        }, currentVersion, currentVersionCode, latestVersion
                )
            }
        } else {
            showToastMessage(R.string.plugin_update_New_Version_None)
        }
    }

    /**
     * Check for any existing downloaded file and take appropriate action;
     *
     * @return Last DownloadManager status; default to DownloadManager.ERROR_UNKNOWN if status unknown
     */
    private fun checkLastDLFileAction(): Int {
        // Check old or scheduled downloads
        var lastJobStatus = DownloadManager.ERROR_UNKNOWN
        val previousDownloads = oldDownloads
        if (previousDownloads.isNotEmpty()) {
            val lastDownload = previousDownloads[previousDownloads.size - 1]
            lastJobStatus = checkDownloadStatus(lastDownload)
            if (lastJobStatus == DownloadManager.STATUS_SUCCESSFUL) {
                val downloadManager = aTalkApp.downloadManager
                val fileUri = downloadManager.getUriForDownloadedFile(lastDownload)

                // Ask the user if he wants to install the valid apk when found
                if (isValidApkVersion(fileUri, latestVersionCode)) {
                    askInstallDownloadedApk(fileUri)
                }
            } else if (lastJobStatus != DownloadManager.STATUS_FAILED) {
                // Download is in progress or scheduled for retry
                showDialog(globalContext,
                        R.string.plugin_update_InProgress,
                        R.string.plugin_update_Download_InProgress)
            } else {
                // Download id return failed status, remove failed id and retry
                removeOldDownloads()
                showDialog(globalContext,
                        R.string.plugin_update_Install_Update, R.string.plugin_update_Download_failed)
            }
        }
        return lastJobStatus
    }

    /**
     * Asks the user whether to install downloaded .apk.
     *
     * @param fileUri download file uri of the apk to install.
     */
    private fun askInstallDownloadedApk(fileUri: Uri) {
        DialogActivity.showConfirmDialog(globalContext,
                R.string.plugin_update_Download_Completed,
                R.string.plugin_update_Download_Ready,
                if (mIsLatest) R.string.plugin_update_ReInstall else R.string.plugin_update_Install,
                object : DialogListener {
                    override fun onConfirmClicked(dialog: DialogActivity): Boolean {
                        // Need REQUEST_INSTALL_PACKAGES in manifest; Intent.ACTION_VIEW works for both
                        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                        intent.setDataAndType(fileUri, APK_MIME_TYPE)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        globalContext.startActivity(intent)
                        return true
                    }

                    override fun onDialogCancelled(dialog: DialogActivity) {}
                }, latestVersion)
    }

    /**
     * Queries the `DownloadManager` for the status of download job identified by given `id`.
     *
     * @param id download identifier which status will be returned.
     * @return download status of the job identified by given id. If given job is not found
     * [DownloadManager.STATUS_FAILED] will be returned.
     */
    @SuppressLint("Range")
    private fun checkDownloadStatus(id: Long): Int {
        val downloadManager: DownloadManager = aTalkApp.downloadManager
        val query = DownloadManager.Query()
        query.setFilterById(id)
        downloadManager.query(query).use { cursor -> return when {
            !cursor.moveToFirst() -> DownloadManager.STATUS_FAILED
            else -> cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
        }
        }
    }

    /**
     * Schedules .apk download.
     */
    private fun downloadApk() {
        val uri = Uri.parse(downloadLink)
        val fileName = uri.lastPathSegment
        if (downloadReceiver == null) {
            downloadReceiver = DownloadReceiver()
            globalContext.registerReceiver(downloadReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
        val request = DownloadManager.Request(uri)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setMimeType(APK_MIME_TYPE)
        val dnFile = File(getaTalkStore(FileBackend.TMP, true), fileName!!)
        request.setDestinationUri(Uri.fromFile(dnFile))
        val downloadManager: DownloadManager = aTalkApp.downloadManager
        val jobId = downloadManager.enqueue(request)
        rememberDownloadId(jobId)
    }

    private inner class DownloadReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (checkLastDLFileAction() < DownloadManager.ERROR_UNKNOWN) return

            // unregistered downloadReceiver
            if (downloadReceiver != null) {
                globalContext.unregisterReceiver(downloadReceiver)
                downloadReceiver = null
            }
        }
    }

    private fun rememberDownloadId(id: Long) {
        val store = store
        var storeStr = store!!.getString(ENTRY_NAME, "")
        storeStr += "$id,"
        store.edit().putString(ENTRY_NAME, storeStr).apply()
    }

    private val oldDownloads: List<Long>
        get() {
            val storeStr = store!!.getString(ENTRY_NAME, "")
            val idStrs = storeStr!!.split(",")
            val apkIds = ArrayList<Long>(idStrs.size)
            for (idStr in idStrs) {
                try {
                    if (idStr.isNotEmpty()) apkIds.add(idStr.toLong())
                } catch (e: NumberFormatException) {
                    Timber.e("Error parsing apk id for string: %s [%s]", idStr, storeStr)
                }
            }
            return apkIds
        }

    /**
     * Removes old downloads.
     */
    fun removeOldDownloads() {
        val apkIds = oldDownloads
        val downloadManager: DownloadManager = aTalkApp.downloadManager
        for (id in apkIds) {
            Timber.d("Removing .apk for id %s", id)
            downloadManager.remove(id)
        }
        store!!.edit().remove(ENTRY_NAME).apply()
    }

    /**
     * Validate the downloaded apk file for correct versionCode and its apk name
     *
     * @param fileUri apk Uri
     * @param versionCode use the given versionCode to check against the apk versionCode
     * @return true if apkFile has the specified versionCode
     */
    private fun isValidApkVersion(fileUri: Uri, versionCode: Long): Boolean {
        // Default to valid as getPackageArchiveInfo() always return null; but sometimes OK
        var isValid = true
        val apkFile = File(getFilePath(globalContext, fileUri)!!)
        if (apkFile.exists()) {
            // Get downloaded apk actual versionCode and check its versionCode validity
            val pm = globalContext.packageManager
            val pckgInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            if (pckgInfo != null) {
                val apkVersionCode: Long = when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> pckgInfo.versionCode.toLong()
                    else -> pckgInfo.longVersionCode
                }
                isValid = versionCode == apkVersionCode
                if (!isValid) {
                    showToastMessage(R.string.plugin_update_Version_Invalid, apkVersionCode, versionCode)
                    Timber.d("Downloaded apk actual version code: %s (%s)", apkVersionCode, versionCode)
                }
            }
        }
        return isValid
    }

    /**
     * Gets the latest available (software) version online.
     *
     * @return the latest (software) version
     */
    override fun getLatestVersion(): String? {
        return latestVersion
    }

    /**
     * Determines whether we are currently running the latest version.
     *
     * @return `true` if current running application is the latest version; otherwise, `false`
     */
    override val isLatestVersion: Boolean
        get() {
            val versionService = versionService
            currentVersion = versionService!!.currentVersionName
            currentVersionCode = versionService.currentVersionCode
            for (aLink in updateLinks) {
                try {
                    if (isValidateLink(aLink)) {
                        val `in` = mHttpConnection!!.inputStream
                        val mProperties = Properties()
                        mProperties.load(`in`)
                        latestVersion = mProperties.getProperty("last_version")
                        latestVersionCode = mProperties.getProperty("last_version_code").toLong()
                        val aLinkPrefix = aLink.substring(0, aLink.lastIndexOf("/") + 1)
                        downloadLink = aLinkPrefix + fileNameApk
                        downloadLink = if (isValidateLink(downloadLink!!)) {
                            // return true is current running application is already the latest
                            return currentVersionCode >= latestVersionCode
                        } else {
                            null
                        }
                    }
                } catch (e: IOException) {
                    Timber.w("Could not retrieve version.properties for checking: %s", e.message)
                }
            }

            // return true if all failed.
            return true
        }

    /**
     * Check if the given link is accessible.
     *
     * @param link the link to check
     * @return true if link is accessible
     */
    private fun isValidateLink(link: String): Boolean {
        try {
            val mUrl = URL(link)
            mHttpConnection = mUrl.openConnection() as HttpURLConnection
            mHttpConnection!!.requestMethod = "GET"
            mHttpConnection!!.setRequestProperty("Content-length", "0")
            mHttpConnection!!.useCaches = false
            mHttpConnection!!.allowUserInteraction = false
            mHttpConnection!!.connectTimeout = 100000
            mHttpConnection!!.readTimeout = 100000
            mHttpConnection!!.connect()
            val responseCode = mHttpConnection!!.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return true
            }
        } catch (e: IOException) {
            Timber.d("Invalid url: %s", e.message)
            return false
        }
        return false
    }

    companion object {
        // Default update link; path is case-sensitive.
        private val updateLinks = arrayOf(
                "https://raw.githubusercontent.com/cmeng-git/atalk-hmos/master/aTalk/release/version.properties",
                "https://atalk.sytes.net/releases/atalk-hmos/version.properties"
        )

        /**
         * Apk mime type constant.
         */
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private val fileNameApk = String.format("aTalk-%s.apk", BuildConfig.BUILD_TYPE)

        /**
         * Name of `SharedPreferences` entry used to store old download ids. Ids are stored in
         * single string separated by ",".
         */
        private const val ENTRY_NAME = "apk_ids"

        /**
         * Gets the current (software) version.
         *
         * @return the current (software) version
         */
        fun getCurrentVersion(): Version? {
            return versionService!!.currentVersion
        }

        /**
         * Gets the current (software) version.
         *
         * @return the current (software) version
         */
        fun getCurrentVersionCode(): Long {
            return versionService!!.currentVersionCode
        }

        /**
         * Returns the currently registered instance of version service.
         *
         * @return the current version service.
         */
        private val versionService: VersionService?
            get() = getService(UpdateActivator.bundleContext, VersionService::class.java)
    }
}