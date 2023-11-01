/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.osgi

import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.actionbar.ActionBarUtil.setTitle

/**
 * Copy of `OSGiActivity` that extends `PreferenceActivity`.
 *
 * @author Eng Chong Meng
 */
open class OSGiPreferenceActivity : OSGiActivity() {
    override fun configureToolBar() {
        val actionBar = supportActionBar
        if (actionBar != null) {
            // Disable up arrow on home activity
            val homeActivity = aTalkApp.homeScreenActivityClass
            if (this.javaClass == homeActivity) {
                actionBar.setDisplayHomeAsUpEnabled(false)
                actionBar.setHomeButtonEnabled(false)
            }
            setTitle(this, title)
        }
    }
}