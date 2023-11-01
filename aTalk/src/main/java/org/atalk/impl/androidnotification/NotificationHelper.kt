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
package org.atalk.impl.androidnotification

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import org.atalk.hmos.R

/**
 * Helper class to manage notification channels, and create notifications.
 *
 * @author Eng Chong Meng
 */
class NotificationHelper(ctx: Context) : ContextWrapper(ctx) {
    private var notificationManager: NotificationManager? = null

    /**
     * Registers notification channels, which can be used later by individual notifications.
     *
     * @param ctx The application context
     */
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Init the system service NotificationManager
            notificationManager = ctx.getSystemService(NotificationManager::class.java)

            // Delete any unused channel IDs or force to re-init all notification channels
            deleteObsoletedChannelIds(false)
            val nCall = NotificationChannel(AndroidNotifications.Companion.CALL_GROUP,
                    getString(R.string.noti_channel_CALL_GROUP), NotificationManager.IMPORTANCE_HIGH)
            nCall.setSound(null, null)
            nCall.setShowBadge(false)
            nCall.lightColor = LED_COLOR
            nCall.enableLights(true)
            nCall.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            notificationManager!!.createNotificationChannel(nCall)
            val nMessage = NotificationChannel(AndroidNotifications.Companion.MESSAGE_GROUP,
                    getString(R.string.noti_channel_MESSAGE_GROUP), NotificationManager.IMPORTANCE_HIGH)
            nMessage.setSound(null, null)
            nMessage.setShowBadge(true)
            nMessage.lightColor = LED_COLOR
            nMessage.enableLights(true)
            // nMessage.setAllowBubbles(true);
            nMessage.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            notificationManager!!.createNotificationChannel(nMessage)
            val nFile = NotificationChannel(AndroidNotifications.Companion.FILE_GROUP,
                    getString(R.string.noti_channel_FILE_GROUP), NotificationManager.IMPORTANCE_LOW)
            nFile.setSound(null, null)
            nFile.setShowBadge(false)
            // nFile.setLightColor(Color.GREEN);
            nFile.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            notificationManager!!.createNotificationChannel(nFile)
            val nDefault = NotificationChannel(AndroidNotifications.Companion.DEFAULT_GROUP,
                    getString(R.string.noti_channel_DEFAULT_GROUP), NotificationManager.IMPORTANCE_LOW)
            nDefault.setSound(null, null)
            nDefault.setShowBadge(false)
            // nDefault.setLightColor(Color.WHITE);
            nDefault.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            notificationManager!!.createNotificationChannel(nDefault)
            val nQuietHours = NotificationChannel(AndroidNotifications.Companion.SILENT_GROUP,
                    getString(R.string.noti_channel_SILENT_GROUP), NotificationManager.IMPORTANCE_LOW)
            nQuietHours.setSound(null, null)
            nQuietHours.setShowBadge(true)
            nQuietHours.lightColor = LED_COLOR
            nQuietHours.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            notificationManager!!.createNotificationChannel(nQuietHours)
        }
    }

    /*
     * Send a notification.
     *
     * @param id The ID of the notification
     * @param notification The notification object
     */
    fun notify(id: Int, notification: Notification.Builder) {
        notificationManager!!.notify(id, notification.build())
    }

    /**
     * Send Intent to load system Notification Settings for this app.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    fun goToNotificationSettings() {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        i.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        startActivity(i)
    }

    /**
     * Send intent to load system Notification Settings UI for a particular channel.
     *
     * @param channel Name of notification channel.
     */
    @TargetApi(Build.VERSION_CODES.O)
    fun goToNotificationSettings(channel: String?) {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        intent.putExtra(Settings.EXTRA_CHANNEL_ID, channel)
        startActivity(intent)
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun deleteObsoletedChannelIds(force: Boolean) {
        val channelGroups = notificationManager!!.notificationChannels
        for (nc in channelGroups) {
            if (force || !AndroidNotifications.Companion.notificationIds.contains(nc.id)) {
                notificationManager!!.deleteNotificationChannel(nc.id)
            }
        }
    }

    companion object {
        private const val LED_COLOR = -0xff0100
    }
}