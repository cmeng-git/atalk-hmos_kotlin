/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidbrowserlauncher

import android.content.Intent
import android.net.Uri
import net.java.sip.communicator.service.browserlauncher.BrowserLauncherService
import org.atalk.hmos.aTalkApp
import timber.log.Timber

/**
 * Android implementation of `BrowserLauncherService`.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidBrowserLauncher : BrowserLauncherService {
    /**
     * {@inheritDoc}
     */
    override fun openURL(url: String) {
        try {
            val uri = Uri.parse(url)
            val launchBrowser = Intent(Intent.ACTION_VIEW, uri)
            launchBrowser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            aTalkApp.globalContext.startActivity(launchBrowser)
        } catch (e: Exception) {
            Timber.e(e, "Error opening URL")
        }
    }
}