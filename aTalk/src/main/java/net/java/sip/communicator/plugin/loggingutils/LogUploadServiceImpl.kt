/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.loggingutils

import android.content.Context
import android.content.Intent
import net.java.sip.communicator.impl.protocol.jabber.JabberActivator
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.persistance.FileBackend
import org.atalk.persistance.ServerPersistentStoresRefreshDialog
import org.atalk.service.fileaccess.FileCategory
import org.atalk.service.log.LogUploadService
import timber.log.Timber
import java.io.File

/**
 * Send/upload logs, to specified destination.
 *
 * @author Damian Minkov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class LogUploadServiceImpl : LogUploadService {
    /**
     * List of log files created for sending logs purpose. There is no easy way of waiting until
     * email is sent and deleting temp log file, so they are cached and removed on OSGI service stop action.
     */
    private val storedLogFiles = ArrayList<File?>()

    /**
     * Retrieve logcat file from Android and Send the log files.
     *
     * @param destinations array of destination addresses
     * @param subject the subject if available
     * @param title the title for the action, used any intermediate dialogs that need to be shown, like "Choose action:".
     */
    override fun sendLogs(destinations: Array<String?>?, subject: String?, title: String?) {
        /* The path pointing to directory used to store temporary log archives. */
        val logStorageDir = FileBackend.getaTalkStore("atalk-logs", true)
        if (logStorageDir != null) {
            val logcatFile: File?
            val externalStorageFile: File?
            val logcatFN = File("log", "atalk-current-logcat.txt").toString()
            try {
                debugPrintInfo()
                logcatFile = LoggingUtilsActivator.fileAccessService!!.getPrivatePersistentFile(logcatFN, FileCategory.LOG)
                Runtime.getRuntime().exec("logcat -v time -f " + logcatFile!!.absolutePath)
                // just wait for 100ms before collect logs - note: process redirect to file, and does not exit
                Thread.sleep(100)
                externalStorageFile = LogsCollector.collectLogs(logStorageDir, null)
            } catch (ex: Exception) {
                aTalkApp.showToastMessage("Error creating logs file archive: " + ex.message)
                return
            }
            // Stores file name to remove it on service shutdown
            val add = storedLogFiles.add(/* element = */ externalStorageFile)
            val ctx = aTalkApp.globalContext
            val logsUri = FileBackend.getUriForFile(ctx, externalStorageFile)
            val sendIntent = Intent(Intent.ACTION_SEND)
            sendIntent.putExtra(Intent.EXTRA_EMAIL, destinations)
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject)
            sendIntent.type = "application/zip"
            sendIntent.putExtra(Intent.EXTRA_STREAM, logsUri)
            val putExtra = sendIntent.putExtra(Intent.EXTRA_TEXT, ctx.getString(R.string.service_gui_SEND_LOGS_INFO))
            val chooserIntent = Intent.createChooser(sendIntent, title)
            // List<ResolveInfo> resInfoList = ctx.getPackageManager().queryIntentActivities(chooserIntent, PackageManager.MATCH_DEFAULT_ONLY);
            // for (ResolveInfo resolveInfo : resInfoList) {
            //     String packageName = resolveInfo.activityInfo.packageName;
            //     Timber.d("ResolveInfo package name: %s", packageName);
            //     ctx.grantUriPermission(packageName, logsUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // }
            ctx.grantUriPermission("android", logsUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // chooserIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); not working; need above statement

            // Starting this activity from context that is not from the current activity; this flag is needed in this situation
            chooserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.startActivity(chooserIntent)
        } else {
            Timber.e("Error sending debug log files")
        }
    }

    /**
     * Extract debug info for android device OS and aTalk installed version
     */
    private fun debugPrintInfo() {
        val property = "http.agent"
        try {
            val sysProperty = System.getProperty(property)
            Timber.i("%s = %s", property, sysProperty)
        } catch (e: Exception) {
            Timber.w(e, "An exception occurred while writing debug info")
        }
        val versionSerVice = JabberActivator.versionService
        Timber.i("Device installed with atalk-hmos version: %s, version code: %s",
                versionSerVice!!.currentVersion, versionSerVice.currentVersionCode)
    }

    /**
     * Frees resources allocated by this service.
     * Purge all files log directory and log sent.
     */
    fun dispose() {
        println("Dispose of LogFiles!!!")
        for (logFile in storedLogFiles) {
            logFile!!.delete()
        }
        storedLogFiles.clear()
        // clean debug log directory after log sent
        ServerPersistentStoresRefreshDialog.purgeDebugLog()
    }
}