/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui

import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.impl.androidnotification.NotificationHelper
import org.atalk.service.SystemEventReceiver
import org.atalk.service.osgi.OSGiActivity
import org.atalk.service.osgi.OSGiService
import org.osgi.framework.BundleContext

/**
 * The splash screen fragment displays animated aTalk logo and indeterminate progress indicators.
 *
 * TODO: Eventually add exit option to the launcher Currently it's not possible to cancel OSGi
 * startup. Attempt to stop service during startup is causing immediate service restart after
 * shutdown even with synchronization of onCreate and OnDestroy commands. Maybe there is still
 * some reference to OSGI service being held at that time ?
 *
 * TODO: Prevent from recreating this Activity on startup. On startup when this Activity is
 * recreated it will also destroy OSGiService which is currently not handled properly. Options
 * specified in AndroidManifest.xml should cover most cases for now:
 * android:configChanges="keyboardHidden|orientation|screenSize"
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class LauncherActivity : OSGiActivity() {
    /**
     * Intent instance that will be called once OSGi startup is finished.
     */
    private var restoreIntent: Intent? = null
    private var startOnReboot = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setupStrictMode()
        super.onCreate(savedInstanceState)

        // Do not show actionBar in splash screen - OSGIActivity#setTitle();
        if (supportActionBar != null) supportActionBar!!.hide()
        if (OSGiService.isShuttingDown) {
            switchActivity(ShutdownActivity::class.java)
            return
        }

        // Must initialize Notification channels before any notification is being issued.
        NotificationHelper(this)

        // Get restore Intent and display "Restoring..." label
        if (intent != null) {
            restoreIntent = intent.getParcelableExtra(ARG_RESTORE_INTENT)
            startOnReboot = intent.getBooleanExtra(SystemEventReceiver.AUTO_START_ONBOOT, false)
        }

        setContentView(R.layout.splash)
        val stateText = findViewById<TextView>(R.id.stateInfo)
        if (restoreIntent != null) stateText.setText(R.string.service_gui_RESTORING)
        val mActionBarProgress = findViewById<ProgressBar>(R.id.actionbar_progress)
        mActionBarProgress.visibility = ProgressBar.VISIBLE

        // Starts fade in animation
        val myImageView = findViewById<ImageView>(R.id.loadingImage)
        val myFadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        myImageView.startAnimation(myFadeInAnimation)
    }

    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext?) {
        super.start(bundleContext)

        runOnUiThread {
            if (restoreIntent != null) {
                // Starts restore intent
                startActivity(restoreIntent)
                finish()
            }

            // Start home screen Activity
            val activityClass = aTalkApp.homeScreenActivityClass
            if (!startOnReboot || aTalk::class.java != activityClass) {
                switchActivity(activityClass)
            } else {
                startOnReboot = false
                finish()
            }
        }
    }

    private fun setupStrictMode() {
        // #TODO - change all disk access to using thread
        // cmeng - disable android.os.StrictMode$StrictModeDisk Access Violation
        val old = StrictMode.getThreadPolicy()
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder(old)
                .permitDiskReads()
                .permitDiskWrites()
                .build())
    }

    companion object {
        /**
         * Argument that holds an `Intent` that will be started once OSGi startup is finished.
         */
        const val ARG_RESTORE_INTENT = "ARG_RESTORE_INTENT"
    }
}