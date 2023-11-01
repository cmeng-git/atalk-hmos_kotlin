package org.atalk.impl.appupdate

import android.app.AlarmManager
import android.app.IntentService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import net.java.sip.communicator.service.update.UpdateService
import net.java.sip.communicator.util.ServiceUtils.getService
import org.atalk.hmos.R
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.settings.SettingsFragment
import org.atalk.impl.androidnotification.AndroidNotifications
import org.atalk.impl.androidtray.NotificationPopupHandler.Companion.getPendingIntentFlag
import java.util.*

class OnlineUpdateService : IntentService(ONLINE_UPDATE_SERVICE) {
    private var mNotificationMgr: NotificationManager? = null
    override fun onCreate() {
        super.onCreate()
        mNotificationMgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val action = intent.action
            if (action != null) {
                when (action) {
                    ACTION_AUTO_UPDATE_APP -> checkAppUpdate()
                    ACTION_UPDATE_AVAILABLE -> {
                        val updateService = getService(AndroidGUIActivator.bundleContext, UpdateService::class.java) as UpdateServiceImpl?
                        updateService?.checkForUpdates()
                    }
                    ACTION_AUTO_UPDATE_START -> setNextAlarm(CHECK_INTERVAL_ON_LAUNCH)
                    ACTION_AUTO_UPDATE_STOP -> stopAlarm()
                }
            }
        }
    }

    private fun checkAppUpdate() {
        var isAutoUpdateCheckEnable = true
        val cfg = AndroidGUIActivator.configurationService
        if (cfg != null) isAutoUpdateCheckEnable = cfg.getBoolean(SettingsFragment.AUTO_UPDATE_CHECK_ENABLE, true)
        val updateService = getService(AndroidGUIActivator.bundleContext, UpdateService::class.java)
        if (updateService != null) {
            val isLatest = updateService.isLatestVersion
            if (!isLatest) {
                val nBuilder = NotificationCompat.Builder(this, AndroidNotifications.DEFAULT_GROUP)
                val msgString = getString(R.string.plugin_update_New_Version_Available,
                        updateService.getLatestVersion())
                nBuilder.setSmallIcon(R.drawable.ic_notification)
                nBuilder.setWhen(System.currentTimeMillis())
                nBuilder.setAutoCancel(true)
                nBuilder.setTicker(msgString)
                nBuilder.setContentTitle(getString(R.string.APPLICATION_NAME))
                nBuilder.setContentText(msgString)
                val intent = Intent(this.applicationContext, OnlineUpdateService::class.java)
                intent.action = ACTION_UPDATE_AVAILABLE
                val pending = PendingIntent.getService(this, 0, intent,
                        getPendingIntentFlag(false, true))
                nBuilder.setContentIntent(pending)
                mNotificationMgr!!.notify(UPDATE_AVAIL_TAG, UPDATE_AVAIL_NOTIFY_ID, nBuilder.build())
            }
        }
        if (isAutoUpdateCheckEnable) setNextAlarm(CHECK_NEW_VERSION_INTERVAL)
    }

    private fun setNextAlarm(nextAlarmTime: Int) {
        val alarmManager = this.getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this.applicationContext, OnlineUpdateService::class.java)
        intent.action = ACTION_AUTO_UPDATE_APP
        val pendingIntent = PendingIntent.getService(this, 0, intent,
                getPendingIntentFlag(false, true))
        val cal = Calendar.getInstance()
        cal.timeInMillis = System.currentTimeMillis()
        cal.add(Calendar.SECOND, nextAlarmTime)
        alarmManager.cancel(pendingIntent)
        alarmManager[AlarmManager.RTC_WAKEUP, cal.timeInMillis] = pendingIntent
    }

    private fun stopAlarm() {
        val alarmManager = this.getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this.applicationContext, OnlineUpdateService::class.java)
        intent.action = ACTION_AUTO_UPDATE_APP
        val pendingIntent = PendingIntent.getService(this, 0, intent,
                getPendingIntentFlag(false, true))
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        const val ACTION_AUTO_UPDATE_APP = "org.atalk.hmos.ACTION_AUTO_UPDATE_APP"
        const val ACTION_AUTO_UPDATE_START = "org.atalk.hmos.ACTION_AUTO_UPDATE_START"
        const val ACTION_AUTO_UPDATE_STOP = "org.atalk.hmos.ACTION_AUTO_UPDATE_STOP"
        private const val ACTION_UPDATE_AVAILABLE = "org.atalk.hmos.ACTION_UPDATE_AVAILABLE"
        private const val ONLINE_UPDATE_SERVICE = "OnlineUpdateService"
        private const val UPDATE_AVAIL_TAG = "aTalk Update Available"

        // in unit of seconds
        var CHECK_INTERVAL_ON_LAUNCH = 30
        var CHECK_NEW_VERSION_INTERVAL = 24 * 60 * 60
        private const val UPDATE_AVAIL_NOTIFY_ID = 1
    }
}