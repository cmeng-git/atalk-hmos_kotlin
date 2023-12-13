/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Point
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.jakewharton.threetenabp.AndroidThreeTen
import net.java.sip.communicator.service.protocol.AccountManager
import net.java.sip.communicator.util.ConfigurationUtils
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.LauncherActivity
import org.atalk.hmos.gui.Splash
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.account.AccountLoginActivity
import org.atalk.hmos.gui.chat.ChatSessionManager
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.settings.SettingsFragment
import org.atalk.hmos.gui.util.DrawableCache
import org.atalk.hmos.gui.util.LocaleHelper
import org.atalk.hmos.plugin.permissions.PermissionsActivity
import org.atalk.hmos.plugin.timberlog.TimberLogImpl
import org.atalk.impl.androidnotification.NotificationHelper
import org.atalk.impl.androidtray.NotificationPopupHandler
import org.atalk.persistance.DatabaseBackend
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.log.LogUploadService
import org.atalk.service.osgi.OSGiService
import timber.log.Timber
import java.awt.Dimension
import kotlin.math.abs

/**
 * `aTalkApp` is used, as a global context and utility class for global actions (like EXIT broadcast).
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class aTalkApp : Application(), LifecycleEventObserver {
    /**
     * {@inheritDoc}
     */
    override fun onCreate() {
        TimberLogImpl.init()

        // This helps to prevent WebView resets UI back to system default.
        // Must skip for < N else weired exceptions happen in Note-5
        // chromium-Monochrome.aab-stable-424011020:5 throw NPE at org.chromium.ui.base.Clipboard.<init>
        try {
            WebView(this).destroy()
        } catch (e: Exception) {
            Timber.e("WebView init exception: %s", e.message)
        }

        // Must initialize Notification channels before any notification is being issued.
        NotificationHelper(this)

        // force delete in case system locked during testing
        // ServerPersistentStoresRefreshDialog.deleteDB();  // purge sql database

        // Trigger the aTalk database upgrade or creation if none exist
        DatabaseBackend.getInstance(this)

        // Do this after WebView(this).destroy(); Set up contextWrapper to use aTalk user selected Language
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        AndroidThreeTen.init(this)
        displaySize
    }

    /**
     * setLocale for Application class to work properly with PBContext class.
     */
    override fun attachBaseContext(base: Context) {
        // instance must be initialized before getProperty() for SQLiteConfigurationStore() init.
        instance = base
        val language = ConfigurationUtils.getProperty(SettingsFragment.P_KEY_LOCALE, "")

        // showToastMessage("aTalkApp reinit locale: " + language);
        instance = LocaleHelper.setLocale(base, language)
        super.attachBaseContext(instance)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    /**
     * This method is for use in emulated process environments.  It will never be called on a production Android
     * device, where processes are removed by simply killing them; no user code (including this callback)
     * is executed when doing so.
     */
    override fun onTerminate() {
        super.onTerminate()
    }

    // ========= LifecycleEventObserver implementations ======= //
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (Lifecycle.Event.ON_START == event) {
            isForeground = true
            Timber.d("APP FOREGROUNDED")
        } else if (Lifecycle.Event.ON_STOP == event) {
            isForeground = false
            Timber.d("APP BACKGROUNDED")
        }
    }

    companion object {
        /**
         * Name of config property that indicates whether foreground icon should be displayed.
         */
        const val SHOW_ICON_PROPERTY_NAME = "org.atalk.hmos.show_icon"

        /**
         * The EXIT action name that is broadcast to all OSGiActivities
         */
        const val ACTION_EXIT = "org.atalk.hmos.exit"

        /**
         * Indicate if aTalk is in the foreground (true) or background (false)
         */
        var isForeground = false
        var permissionFirstRequest = true

        /**
         * Get aTalkApp application instance
         */
        lateinit var instance: Context

        /**
         * The currently shown activity.
         */
        private var currentActivity: AppCompatActivity? = null

        /**
         * Bitmap cache instance.
         */
        private val drawableCache = DrawableCache()

        /**
         * Used to keep the track of GUI activity.
         */
        private var lastGuiActivity = 0L
        /**
         * Returns monitor object that will be notified each time current `Activity` changes.
         *
         * @return monitor object that will be notified each time current `Activity` changes.
         */
        /**
         * Used to track current `Activity`. This monitor is notified each time current `Activity` changes.
         */
        private val currentActivityMonitor = Object()
        var isPortrait = true

        // Get android device screen display size
        lateinit var mDisplaySize: Dimension

        /**
         * Returns the size of the main application window.
         * Must support different android API else system crashes on some devices
         * e.g. UnsupportedOperationException: in Xiaomi Mi 11 Android 11 (SDK 30)
         *
         * @return the size of the main application display window.
         */
        @Suppress("DEPRECATION")
        val displaySize: Dimension
            get() {
                // Get android device screen display size
                mDisplaySize = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    val size = Point()
                    (globalContext.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getSize(size)
                    Dimension(size.x, size.y)
                } else {
                    val mBounds = (globalContext.getSystemService(WINDOW_SERVICE) as WindowManager).currentWindowMetrics.bounds
                    Dimension(abs(mBounds.width()), abs(mBounds.height()))
                }
                return mDisplaySize
            }

        /**
         * Returns true if the device is locked or screen turned off (in case password not set)
         */
        val isDeviceLocked: Boolean
            get() {
                val isLocked: Boolean

                // First we check the locked state
                val keyguardManager = instance.getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                val isKeyguardLocked = keyguardManager.isKeyguardLocked

                if (isKeyguardLocked) {
                    isLocked = true
                } else {
                    // If password is not set in the settings, the inKeyguardRestrictedInputMode() returns false,
                    // so we need to check if screen on for this case
                    val powerManager = instance.getSystemService(POWER_SERVICE) as PowerManager
                    isLocked = !powerManager.isInteractive
                }
                Timber.d("Android device is %s.", if (isLocked) "locked" else "unlocked")
                return isLocked
            }

        /**
         * Shutdowns the app by stopping `OSGiService` and broadcastingaction  [.ACTION_EXIT].
         */
        fun shutdownApplication() {
            // Shutdown the OSGi service
            instance.stopService(Intent(instance, OSGiService::class.java))
            // Broadcast the exit action
            val exitIntent = Intent()
            exitIntent.action = ACTION_EXIT
            instance.sendBroadcast(exitIntent)
        }

        /**
         * Returns global bitmap cache of the application.
         *
         * @return global bitmap cache of the application.
         */
        val imageCache: DrawableCache
            get() = drawableCache

        /**
         * Retrieves `AudioManager` instance using application context.
         *
         * @return `AudioManager` service instance.
         */
        val audioManager: AudioManager
            get() = globalContext.getSystemService(AUDIO_SERVICE) as AudioManager

        /**
         * Retrieves `CameraManager` instance using application context.
         *
         * @return `CameraManager` service instance.
         */
        val cameraManager: CameraManager
            get() = globalContext.getSystemService(CAMERA_SERVICE) as CameraManager

        /**
         * Retrieves `PowerManager` instance using application context.
         *
         * @return `PowerManager` service instance.
         */
        val powerManager: PowerManager
            get() = globalContext.getSystemService(POWER_SERVICE) as PowerManager

        /**
         * Retrieves `SensorManager` instance using application context.
         *
         * @return `SensorManager` service instance.
         */
        val sensorManager: SensorManager
            get() = globalContext.getSystemService(SENSOR_SERVICE) as SensorManager

        /**
         * Retrieves `NotificationManager` instance using application context.
         *
         * @return `NotificationManager` service instance.
         */
        val notificationManager: NotificationManager
            get() = globalContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        /**
         * Retrieves `DownloadManager` instance using application context.
         *
         * @return `DownloadManager` service instance.
         */
        val downloadManager: DownloadManager
            get() = globalContext.getSystemService(DOWNLOAD_SERVICE) as DownloadManager

        /**
         * Returns global application context.
         *
         * @return Returns global application `Context`.
         */
        @JvmStatic
        val globalContext: Context
            get() = instance.applicationContext

        /**
         * Returns application `Resources` object.
         *
         * @return application `Resources` object.
         */
        val appResources: Resources
            get() = instance.resources

        /**
         * Returns Android string resource of the user selected language for given `id`
         * and format arguments that will be used for substitution.
         *
         * @param id the string identifier.
         * @param arg the format arguments that will be used for substitution.
         *
         * @return Android string resource for given `id` and format arguments.
         */
        fun getResString(id: Int, vararg arg: Any?): String {
            return instance.getString(id, *arg)
        }

        /**
         * Returns Android string resource for given `id` and format arguments that will be used for substitution.
         *
         * @param aString the string identifier.
         *
         * @return Android string resource for given `id` and format arguments.
         */
        fun getResStringByName(aString: String?): String {
            val packageName = instance.packageName
            @SuppressLint("DiscouragedApi") val resId = instance.resources.getIdentifier(aString, "string", packageName)
            return if (resId != 0) instance.getString(resId) else ""
        }

        private var toast: Toast? = null

        /**
         * Toast show message in UI thread;
         * Cancel current toast view to allow immediate display of new toast message.
         *
         * @param message the string message to display.
         */
        fun showToastMessage(message: String?) {
            Handler(Looper.getMainLooper()).post {
                if (toast != null && toast!!.view != null) {
                    toast!!.cancel()
                }
                toast = Toast.makeText(globalContext, message, Toast.LENGTH_LONG)
                toast!!.show()
            }
        }

        @JvmStatic
        fun showToastMessage(id: Int, vararg arg: Any?) {
            showToastMessage(instance.getString(id, *arg))
        }

        fun showGenericError(id: Int, vararg arg: Any?) {
            Handler(Looper.getMainLooper()).post {
                val msg = instance.getString(id, *arg)
                DialogActivity.showDialog(instance, instance.getString(R.string.service_gui_ERROR), msg)
            }
        }

        // Start main view// If OSGI has not started show splash screen as home

        // If account manager is null means that OSGI has not started yet
        // Start new account Activity if none is found
        /**
         * Returns home `Activity` class.
         *
         * @return Returns home `Activity` class.
         */
        val homeScreenActivityClass: Class<*>
            get() {
                // If OSGI has not started show splash screen as home
                val osgiContext = AndroidGUIActivator.bundleContext
                        ?: return LauncherActivity::class.java

                // If account manager is null means that OSGI has not started yet
                val accountManager = ServiceUtils.getService(osgiContext, AccountManager::class.java)
                        ?: return LauncherActivity::class.java

                val accountCount = accountManager.storedAccounts.size
                // Start new account Activity if none is found
                return if (accountCount == 0) {
                    AccountLoginActivity::class.java
                } else {
                    // Start main view
                    aTalk::class.java
                }
            }// Home is singleTask anyway, but this way it can be started from non Activity context.

        /**
         * Creates the home `Activity` `Intent`.
         *
         * @return the home `Activity` `Intent`.
         */
        val homeIntent: Intent
            get() {
                // Home is singleTask anyway, but this way it can be started from non Activity context.
                val homeIntent = Intent(instance, homeScreenActivityClass)
                homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                return homeIntent
            }

        /**
         * Creates pending `Intent` to be started, when aTalk icon is clicked.
         *
         * @return new pending `Intent` to be started, when aTalk icon is clicked.
         */
        fun getaTalkIconIntent(): PendingIntent {
            var intent = ChatSessionManager.lastChatIntent
            if (intent == null) {
                intent = homeIntent
            }
            return PendingIntent.getActivity(globalContext, 0, intent,
                    NotificationPopupHandler.getPendingIntentFlag(isMutable = false, isUpdate = true))
        }

        /**
         * Returns `ConfigurationService` instance.
         *
         * @return `ConfigurationService` instance.
         */
        val config: ConfigurationService?
            get() = ServiceUtils.getService(AndroidGUIActivator.bundleContext, ConfigurationService::class.java)

        /**
         * Returns `true` if aTalk notification icon should be displayed.
         *
         * @return `true` if aTalk notification icon should be displayed.
         */
        val isIconEnabled: Boolean
            get() = config == null || config!!.getBoolean(SHOW_ICON_PROPERTY_NAME, false)

        /**
         * Sets the current activity.
         *
         * @param a the current activity to set
         */
        fun setCurrentActivity(a: AppCompatActivity?) {
            synchronized(currentActivityMonitor) {

                // Timber.i("Current activity set to %s", a);
                currentActivity = a
                lastGuiActivity = if (currentActivity == null) {
                    System.currentTimeMillis()
                } else {
                    -1
                }
                // Notify listening threads
                currentActivityMonitor.notifyAll()
            }
        }

        /**
         * Returns the current activity.
         *
         * @return the current activity
         */
        fun getCurrentActivity(): AppCompatActivity? {
            return currentActivity
        }// GUI is currently active

        /**
         * Returns the time elapsed since last aTalk `Activity` was open in milliseconds.
         *
         * @return the time elapsed since last aTalk `Activity` was open in milliseconds.
         */
        val lastGuiActivityInterval: Long
            get() =// GUI is currently active
                if (lastGuiActivity == -1L) {
                    0
                } else System.currentTimeMillis() - lastGuiActivity

        /**
         * Checks if current `Activity` is the home one.
         *
         * @return `true` if the home `Activity` is currently active.
         */
        val isHomeActivityActive: Boolean
            get() = currentActivity != null && currentActivity!!.javaClass == homeScreenActivityClass

        /**
         * Displays the send logs dialog.
         */
        fun showSendLogsDialog() {
            val defaultEmail = config!!.getString("org.atalk.hmos.LOG_REPORT_EMAIL")
            ServiceUtils.getService(AndroidGUIActivator.bundleContext, LogUploadService::class.java)?.sendLogs(arrayOf(defaultEmail),
                    getResString(R.string.service_gui_SEND_LOGS_SUBJECT),
                    getResString(R.string.service_gui_SEND_LOGS_TITLE))
        }

        /**
         * If OSGi has not started, then wait for the `LauncherActivity` etc to complete before
         * showing any dialog. Dialog should only be shown while `NOT in LaunchActivity` etc
         * Otherwise the dialog will be obscured by these activities; max wait = 5 waits of 1000ms each
         */
        fun waitForFocus(): Activity? {
            // if (AndroidGUIActivator.bundleContext == null) { #false on first application installation
            synchronized(currentActivityMonitor) {
                var wait = 6 // 5 waits each lasting max of 1000ms
                while (wait-- > 0) {
                    try {
                        currentActivityMonitor.wait(1000)
                    } catch (e: InterruptedException) {
                        Timber.e("%s", e.message)
                    }
                    val activity = getCurrentActivity()
                    if (activity != null) {
                        if (!(activity is LauncherActivity
                                        || activity is Splash
                                        || activity is PermissionsActivity)) {
                            return activity
                        } else {
                            Timber.d("Wait %s sec for aTalk focus on activity: %s", wait, activity)
                        }
                    }
                }
                return null
            }
        }
    }
}