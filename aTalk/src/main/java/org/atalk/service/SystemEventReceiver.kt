package org.atalk.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.TextUtils
import net.java.sip.communicator.impl.configuration.SQLiteConfigurationStore
import net.java.sip.communicator.util.ConfigurationUtils
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.LauncherActivity
import org.atalk.persistance.DatabaseBackend
import timber.log.Timber

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            if (isAutoStartEnable(context)) {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    val msg = "aTalk cannot support autoStart onBoot for android API: " + Build.VERSION.SDK_INT
                    Timber.w("%s", msg)
                    aTalkApp.showToastMessage(msg)
                } else {
                    val i = Intent(context, LauncherActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    i.putExtra(AUTO_START_ONBOOT, true)
                    context.startActivity(i)
                }
            } else {
                System.exit(0)
            }
        }
    }

    /**
     * Check if the aTalk auto start on reboot is enabled
     *
     * @param context Application context
     * @return true if aTalk Auto Start Option is enabled by user. false otherwise
     */
    private fun isAutoStartEnable(context: Context): Boolean {
        DatabaseBackend.getInstance(context)
        val store = SQLiteConfigurationStore(context)
        val autoStart = store.getProperty(ConfigurationUtils.pAutoStart) as String?
        return TextUtils.isEmpty(autoStart) || java.lang.Boolean.parseBoolean(autoStart)
    }

    companion object {
        const val AUTO_START_ONBOOT = "org.atalk.start_boot"
    }
}