/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui

import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import org.atalk.hmos.R
import org.atalk.hmos.gui.actionbar.ActionBarUtil
import org.atalk.service.osgi.OSGiActivity
import org.atalk.service.osgi.OSGiService

/**
 * Activity displayed when shutdown procedure is in progress.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ShutdownActivity : OSGiActivity() {
    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OSGiService.hasStarted()) {
            startActivity(Intent(this, LauncherActivity::class.java))
            finish()
            return
        }
        val actionBar = supportActionBar
        if (actionBar != null) {
            // Disable up arrow
            actionBar.setDisplayHomeAsUpEnabled(false)
            actionBar.setHomeButtonEnabled(false)
            ActionBarUtil.setTitle(this, getTitle())
        }
        setContentView(R.layout.splash)
        val shutDown = findViewById<TextView>(R.id.stateInfo)
        shutDown.setText(R.string.service_gui_SHUTDOWN_IN_PROGRESS)
        val mActionBarProgress = findViewById<ProgressBar>(R.id.actionbar_progress)
        mActionBarProgress.visibility = ProgressBar.VISIBLE
    }
}