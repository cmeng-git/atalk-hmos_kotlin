/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.osgi

import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NavUtils
import org.atalk.hmos.BaseActivity
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.LauncherActivity
import org.atalk.hmos.gui.actionbar.ActionBarUtil
import org.atalk.hmos.gui.actionbar.ActionBarUtil.setTitle
import org.atalk.hmos.plugin.errorhandler.ExceptionHandler.Companion.checkAndAttachExceptionHandler
import org.atalk.hmos.plugin.errorhandler.ExceptionHandler.Companion.hasCrashed
import org.atalk.hmos.plugin.errorhandler.ExceptionHandler.Companion.resetCrashedStatus
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import timber.log.Timber

/**
 * Implements a base `FragmentActivity` which employs OSGi.
 *
 * @author Lyubomir Marinov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class OSGiActivity : BaseActivity() {
    private var bundleActivator: BundleActivator? = null

    /**
     * Returns OSGI `BundleContext`.
     */
    protected var bundleContext: BundleContext? = null
        private set

    private var mService: BundleContextHolder? = null

    private var serviceConnection: ServiceConnection? = null

    /**
     * EXIT action listener that triggers closes the `Activity`
     */
    private val exitListener = ExitActionListener()

    /**
     * List of attached [OSGiUiPart].
     */
    private val osgiFragments = ArrayList<OSGiUiPart>()

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     * Both setLanguage and setTheme must happen before super.onCreate() is called
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down
     * then this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Note: Otherwise it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hooks the exception handler to the UI thread
        checkAndAttachExceptionHandler()
        configureToolBar()

        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service1: IBinder) {
                if (this == serviceConnection)
                    setService(service1 as BundleContextHolder)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                if (this == serviceConnection)
                    setService(null)
            }
        }
        this.serviceConnection = serviceConnection
        var bindService = false
        try {
            bindService = bindService(Intent(this, OSGiService::class.java), serviceConnection, BIND_AUTO_CREATE)
        } finally {
            if (!bindService) this.serviceConnection = null
        }

        // Registers exit action listener
        this.registerReceiver(exitListener, IntentFilter(aTalkApp.ACTION_EXIT))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        aTalkApp.setCurrentActivity(this)
    }

    override fun onResume() {
        super.onResume()
        aTalkApp.setCurrentActivity(this)
        // If OSGi service is running check for send logs
        if (bundleContext != null) {
            checkForSendLogsDialog()
        }
    }

    override fun onPause() {
        // Clear the references to this activity.
        clearReferences()
        super.onPause()
    }

    /**
     * Called when an activity is destroyed.
     */
    override fun onDestroy() {
        // Unregisters exit action listener
        unregisterReceiver(exitListener)
        val serviceConnection = serviceConnection
        this.serviceConnection = null
        try {
            setService(null)
        } finally {
            serviceConnection?.let { unbindService(it) }
        }
        super.onDestroy()
    }

    open fun configureToolBar() {
        // Find the toolbar view inside the activity layout - aTalk cannot use ToolBar; has layout problems
        // Toolbar toolbar = findViewById(R.id.my_toolbar);
        // if (toolbar != null)
        //   setSupportActionBar(toolbar);
        val actionBar = supportActionBar
        if (actionBar != null) {
            // mActionBar.setDisplayOptions(ActionBar.DISPLAY_USE_LOGO | ActionBar.DISPLAY_SHOW_CUSTOM );
            actionBar.setDisplayShowCustomEnabled(true)
            actionBar.setDisplayUseLogoEnabled(true)
            actionBar.setDisplayShowTitleEnabled(false)
            actionBar.setCustomView(R.layout.action_bar)

            // Disable up arrow on home activity
            val homeActivity = aTalkApp.homeScreenActivityClass
            if (this.javaClass == homeActivity) {
                actionBar.setDisplayHomeAsUpEnabled(false)
                actionBar.setHomeButtonEnabled(false)
                val tv = findViewById<TextView>(R.id.actionBarStatus)
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            }
            setTitle(this, title)
            ActionBarUtil.setAvatar(this, R.drawable.ic_icon)
        }
    }

    /**
     * Checks if the crash has occurred since the aTalk was last started. If it's true asks the
     * user about eventual logs report.
     */
    private fun checkForSendLogsDialog() {
        // Checks if aTalk has previously crashed and asks the user user about log reporting
        if (!hasCrashed()) {
            return
        }
        // Clears the crash status and ask user to send debug log
        resetCrashedStatus()
        val question = AlertDialog.Builder(this)
        question.setTitle(R.string.service_gui_WARNING)
                .setMessage(getString(R.string.service_gui_SEND_LOGS_QUESTION))
                .setPositiveButton(R.string.service_gui_YES) { dialog: DialogInterface, which: Int ->
                    dialog.dismiss()
                    aTalkApp.showSendLogsDialog()
                }
                .setNegativeButton(R.string.service_gui_NO) { dialog: DialogInterface, which: Int -> dialog.dismiss() }
                .create().show()
    }

    private fun setService(service: BundleContextHolder?) {
        if (mService != service) {
            if (mService != null && bundleActivator != null) {
                bundleActivator = try {
                    mService!!.removeBundleActivator(bundleActivator)
                    null
                } finally {
                    try {
                        internalStop(null)
                    } catch (t: Throwable) {
                        if (t is ThreadDeath) throw t
                    }
                }
            }

            mService = service
            if (mService != null) {
                if (bundleActivator == null) {
                    bundleActivator = object : BundleActivator {

                        @Throws(java.lang.Exception::class)
                        override fun start(bundleContext: BundleContext) {
                            internalStart(bundleContext)
                        }

                        @Throws(java.lang.Exception::class)
                        override fun stop(bundleContext: BundleContext) {
                            internalStop(bundleContext)
                        }
                    }
                }
                mService!!.addBundleActivator(bundleActivator)
            }
        }
    }

    /**
     * Starts this osgi activity.
     *
     * @param bundleContext the osgi `BundleContext`
     *
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun internalStart(bundleContext: BundleContext) {
        this.bundleContext = bundleContext
        var start = false

        try {
            start(bundleContext)
            start = true
        } finally {
            if (!start && (this.bundleContext == bundleContext)) {
                this.bundleContext = null
            }
        }
    }

    /**
     * Stops this osgi activity.
     *
     * @param bundleContext the osgi `BundleContext`
     *
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun internalStop(bundleContext: BundleContext?) {
        var bundleContext1 = bundleContext
        if (this.bundleContext != null) {
            if (bundleContext1 == null)
                bundleContext1 = this.bundleContext
            if (this.bundleContext == bundleContext1)
                this.bundleContext = null
            stop(bundleContext1)
        }
    }

    @Throws(Exception::class)
    protected open fun start(bundleContext: BundleContext?) {
        // Starts children OSGI fragments.
        for (osGiFragment in osgiFragments) {
            osGiFragment.start(bundleContext)
        }
        // If OSGi has just started and we're on UI thread check for crash event. We must be on
        // UIThread to show the dialog and it makes no sense to show it from the background, so
        // it will be eventually displayed from onResume()
        if (Looper.getMainLooper() == Looper.myLooper()) {
            checkForSendLogsDialog()
        }
    }

    @Throws(Exception::class)
    protected open fun stop(bundleContext: BundleContext?) {
        // Stops children OSGI fragments.
        for (osGiFragment in osgiFragments) {
            osGiFragment.stop(bundleContext)
        }
    }

    /**
     * Registers child `OSGiUiPart` to be notified on startup.
     *
     * @param fragment child `OSGiUiPart` contained in this `Activity`.
     */
    open fun registerOSGiFragment(fragment: OSGiUiPart) {
        osgiFragments.add(fragment)
        if (bundleContext != null) {
            // If context exists it means we have started already, so start the fragment immediately
            try {
                fragment.start(bundleContext)
            } catch (e: Exception) {
                Timber.e(e, "Error starting OSGiFragment")
            }
        }
    }

    /**
     * Unregisters child `OSGiUiPart`.
     *
     * @param fragment the `OSGiUiPart` that will be unregistered.
     */
    open fun unregisterOSGiFragment(fragment: OSGiUiPart) {
        if (bundleContext != null) {
            try {
                fragment.stop(bundleContext)
            } catch (e: Exception) {
                Timber.e(e, "Error while trying to stop OSGiFragment")
            }
        }
        osgiFragments.remove(fragment)
    }

    /**
     * Convenience method which starts a new activity for given `activityClass` class
     *
     * @param activityClass the activity class
     */
    protected open fun startActivity(activityClass: Class<*>?) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
    }

    /**
     * Start the application notification settings page
     */
    fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        }
        else {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    /**
     * Convenience method that switches from one activity to another.
     *
     * @param activityClass the activity class
     */
    protected open fun switchActivity(activityClass: Class<*>?) {
        startActivity(activityClass)
        finish()
    }

    /**
     * Convenience method that switches from one activity to another.
     *
     * @param activityIntent the next activity `Intent`
     */
    protected open fun switchActivity(activityIntent: Intent?) {
        startActivity(activityIntent)
        finish()
    }

    /**
     * Handler for home navigator. Use upIntent if parentActivityName defined. Otherwise execute onBackKeyPressed.
     * Account setting must back to its previous menu (BackKey) to properly save changes
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            val upIntent = NavUtils.getParentActivityIntent(this)
            if (upIntent != null) {
                Timber.w("Process UpIntent for: %s", this.localClassName)
                NavUtils.navigateUpTo(this, upIntent)
            }
            else {
                Timber.w("Replace Up with BackKeyPress for: %s", this.localClassName)
                super.onBackPressed()
                // Class<?> homeActivity = aTalkApp.getHomeScreenActivityClass();
                // if (!this.getClass().equals(homeActivity)) {
                //    switchActivity(homeActivity);
                // }
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Returns the content `View`.
     *
     * @return the content `View`.
     */
    protected open fun getContentView(): View {
        return findViewById(android.R.id.content)
    }

    /**
     * Checks if the OSGi is started and if not eventually triggers `LauncherActivity`
     * that will restore current activity from its `Intent`.
     *
     * @return `true` if restore `Intent` has been posted.
     */
    protected fun postRestoreIntent(): Boolean {
        // Restore after OSGi startup
        if (AndroidGUIActivator.bundleContext == null) {
            val intent = Intent(aTalkApp.globalContext, LauncherActivity::class.java)
            intent.putExtra(LauncherActivity.ARG_RESTORE_INTENT, getIntent())
            startActivity(intent)
            finish()
            return true
        }
        return false
    }

    /**
     * Broadcast listener that listens for [aTalkApp.ACTION_EXIT] and then finishes this `Activity`
     */
    internal inner class ExitActionListener : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    private fun clearReferences() {
        val currentActivity = aTalkApp.getCurrentActivity()
        if (currentActivity != null && currentActivity == this) aTalkApp.setCurrentActivity(null)
    }

    companion object {
        /**
         * UI thread handler
         */
        val uiHandler = Handler(Looper.getMainLooper())
    }
}