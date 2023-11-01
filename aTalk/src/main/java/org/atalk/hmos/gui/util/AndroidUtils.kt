/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.util

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.TransitionDrawable
import android.os.Build
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.core.app.NotificationCompat.*
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.androidnotification.AndroidNotifications
import org.atalk.service.osgi.OSGiService
import timber.log.Timber

/**
 * The `AndroidUtils` class provides a set of utility methods allowing an easy way to show
 * an alert dialog on android, show a general notification, etc.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
object AndroidUtils {
    /**
     * Api level constant. Change it here to simulate lower api on new devices.
     *
     * All API level decisions should be done based on [.hasAPI] call result.
     */
    private val API_LEVEL = Build.VERSION.SDK_INT

    /**
     * Var used to track last aTalk icon notification text in order to prevent from posting
     * updates that make no sense. This will happen when providers registration state changes
     * and global status is still the same(online or offline).
     */
    private var lastNotificationText: String? = null

    /**
     * Clears the general notification.
     *
     * @param appContext the `Context` that will be used to create new activity from notification `Intent`.
     */
    fun clearGeneralNotification(appContext: Context?) {
        val id = OSGiService.generalNotificationId
        if (id < 0) {
            Timber.log(TimberLog.FINER, "There's no global notification icon found")
            return
        }
        generalNotificationInvalidated()
        AndroidGUIActivator.loginRenderer?.updateaTalkIconNotification()
    }

    /**
     * Shows an alert dialog for the given context and a title given by `titleId` and
     * message given by `messageId`.
     *
     * @param context the android `Context`
     * @param notificationID the identifier of the notification to update
     * @param title the title of the message
     * @param message the message
     * @param date the date on which the event corresponding to the notification happened
     */
    fun updateGeneralNotification(context: Context, notificationID: Int, title: String?,
                                  message: String, date: Long) {
        // Filter out the same subsequent notifications
        if (lastNotificationText != null && lastNotificationText == message) {
            return
        }
        val nBuilder = Builder(context, AndroidNotifications.DEFAULT_GROUP)
        nBuilder.setContentTitle(title)
                .setContentText(message)
                .setWhen(date)
                .setSmallIcon(R.drawable.ic_notification)
        nBuilder.setContentIntent(aTalkApp.getaTalkIconIntent())
        val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = nBuilder.build()
        notification.flags = (Notification.FLAG_ONLY_ALERT_ONCE
                and Notification.FLAG_FOREGROUND_SERVICE and Notification.FLAG_NO_CLEAR)

        // mId allows you to update the notification later on.
        mNotificationManager.notify(notificationID, notification)
        lastNotificationText = message
    }

    /**
     * This method should be called when general notification is changed from the outside(like in
     * call notification for example).
     */
    fun generalNotificationInvalidated() {
        lastNotificationText = null
    }

    /**
     * Indicates if the service given by `activityClass` is currently running.
     *
     * @param context the Android context
     * @param activityClass the activity class to check
     * @return `true` if the activity given by the class is running, `false` - otherwise
     */
    fun isActivityRunning(context: Context, activityClass: Class<*>): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val services = activityManager.getRunningTasks(Int.MAX_VALUE)
        var isServiceFound = false
        for (i in services.indices) {
            if (services[i].topActivity!!.className == activityClass.name) {
                isServiceFound = true
            }
        }
        return isServiceFound
    }

    fun setOnTouchBackgroundEffect(view: View) {
        view.setOnTouchListener(object : View.OnTouchListener {
            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (v.background !is TransitionDrawable) return false
                val transition = v.background as TransitionDrawable
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> transition.startTransition(500)
                    MotionEvent.ACTION_HOVER_EXIT, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> transition.reverseTransition(500)
                }
                return false
            }
        })
    }

    /**
     * Returns `true` if we are currently running on tablet device.
     *
     * @return `true` if we are currently running on tablet device.
     */
    val isTablet: Boolean
        get() {
            val context = aTalkApp.globalContext
            return (context.resources.configuration.screenLayout
                    and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        }

    /**
     * Returns `true` if this device supports at least given API level.
     *
     * @param minApiLevel API level value to check
     * @return `true` if this device supports at least given API level.
     */
    fun hasAPI(minApiLevel: Int): Boolean {
        return API_LEVEL >= minApiLevel
    }

    /**
     * Returns `true` if current `Thread` is UI thread.
     *
     * @return `true` if current `Thread` is UI thread.
     */
    val isUIThread: Boolean
        get() = Looper.getMainLooper().thread === Thread.currentThread()

    /**
     * Converts pixels to density independent pixels.
     *
     * @param px pixels value to convert.
     * @return density independent pixels value for given pixels value.
     */
    fun pxToDp(px: Int): Int {
        return (px.toFloat() * aTalkApp.appResources.displayMetrics.density + 0.5f).toInt()
    }
}