/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.osgi

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.util.AndroidUtils.generalNotificationInvalidated
import org.atalk.impl.androidnotification.AndroidNotifications
import org.atalk.impl.osgi.OSGiServiceImpl
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Implements an Android [Service] which (automatically) starts and stops an OSGi framework (implementation).
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class OSGiService : Service() {
    /**
     * The very implementation of this Android `Service` which is split out of the class `OSGiService` so
     * that the class `OSGiService` may remain in a `service` package and be treated as public from the
     * Android point of view and the class `OSGiServiceImpl` may reside in an `impl` package and be
     * recognized as internal from the aTalk point of view.
     */
    private val impl = OSGiServiceImpl(this)
    override fun onBind(intent: Intent): IBinder {
        return impl.onBind(intent)
    }

    override fun onCreate() {
        if (started) {
            // We are still running
            return
        }
        started = true
        impl.onCreate()
    }

    override fun onDestroy() {
        if (isShuttingDown) {
            return
        }
        isShuttingDown = true
        impl.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return impl.onStartCommand(intent, flags, startId)
    }

    /**
     * Method called by OSGi impl when start command completes.
     */
    fun onOSGiStarted() {
        if (aTalkApp.isIconEnabled) {
            showIcon()
        }
        aTalkApp.config!!.addPropertyChangeListener(aTalkApp.SHOW_ICON_PROPERTY_NAME) {
            if (aTalkApp.isIconEnabled) {
                showIcon()
            } else {
                hideIcon()
            }
        }
        serviceStarted = true
    }

    /**
     * Start the service in foreground and creates shows general notification icon.
     */
    private fun showIcon() {
        val title = resources.getString(R.string.APPLICATION_NAME)
        // The intent to launch when the user clicks the expanded notification
        val pendIntent = aTalkApp.getaTalkIconIntent()
        val nBuilder = NotificationCompat.Builder(this, AndroidNotifications.DEFAULT_GROUP)
        nBuilder.setContentTitle(title)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_notification)
                .setNumber(0)
                .setContentIntent(pendIntent)
        val notice = nBuilder.build()
        notice.flags = notice.flags or Notification.FLAG_NO_CLEAR
        this.startForeground(GENERAL_NOTIFICATION_ID, notice)
        running_foreground = true
    }

    /**
     * Stops the foreground service and hides general notification icon
     */
    fun stopForegroundService() {
        serviceStarted = false
        hideIcon()
    }

    private fun hideIcon() {
        if (running_foreground) {
            // stopForeground(STOP_FOREGROUND_REMOVE)
            stopForeground(true)
            running_foreground = false
            generalNotificationInvalidated()
        }
    }

    companion object {
        /**
         * The ID of aTalk notification icon
         */
        private const val GENERAL_NOTIFICATION_ID = R.string.APPLICATION_NAME

        /**
         * Indicates that aTalk is running in foreground mode and its icon is being displayed on android notification tray.
         * If user disable show aTalk icon, then running_foreground = false
         */
        private var running_foreground = false

        /**
         * Indicates if the service has been started and general notification icon is available
         */
        private var serviceStarted = false

        /**
         * Protects against starting next OSGi service while the previous one has not completed it's shutdown procedure.
         *
         * This field will be cleared by System.exit() called after shutdown completes.
         */
        private var started = false
        fun hasStarted(): Boolean {
            return started
        }

        /**
         * This field will be cleared by System.exit() called after shutdown completes.
         */
        var isShuttingDown = false
            private set

        /**
         * Returns general notification ID that can be used to post notification bound to our global icon
         * in android notification tray
         *
         * @return the notification ID greater than 0 or -1 if service is not running
         */
        val generalNotificationId: Int
            get() = if (serviceStarted && running_foreground) {
                GENERAL_NOTIFICATION_ID
            } else -1

        init {
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
    }
}