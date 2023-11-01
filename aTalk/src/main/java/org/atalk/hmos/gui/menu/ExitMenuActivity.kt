/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.menu

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.About
import org.atalk.persistance.ServerPersistentStoresRefreshDialog
import org.atalk.service.osgi.OSGiActivity

/**
 * Extends this activity to handle exit options menu item.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
abstract class ExitMenuActivity : OSGiActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.exit_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_exit -> aTalkApp.shutdownApplication()
            R.id.online_help -> About.atalkUrlAccess(this, getString(R.string.FAQ_Link))
            R.id.about -> startActivity(About::class.java)
            R.id.del_database -> ServerPersistentStoresRefreshDialog.deleteDB()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}