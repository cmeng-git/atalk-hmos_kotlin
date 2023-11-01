/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.ListView
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.ListFragment
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration
import org.atalk.hmos.R
import org.atalk.hmos.gui.widgets.ScrollingTable
import org.atalk.service.osgi.OSGiActivity
import org.osgi.framework.BundleContext

/**
 * The activity allows user to edit STUN or Jingle Nodes list of the Jabber account.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ServerListActivity : OSGiActivity() {
    /**
     * The registration object storing edited properties
     */
    private var registration: JabberAccountRegistration? = null

    /**
     * The list model for currently edited items
     */
    private var mAdapter: ServerItemAdapter? = null
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext?) {
        super.start(bundleContext)
        val intent = intent
        registration = intent.getSerializableExtra(JABBER_REGISTRATION_KEY) as JabberAccountRegistration
        val listType = intent.getIntExtra(REQUEST_CODE_KEY, -1)
        if (listType == RCODE_STUN_TURN) {
            mAdapter = StunServerAdapter(this, registration)
            setMainTitle(R.string.service_gui_STUN_TURN_SERVER)
        } else if (listType == RCODE_JINGLE_NODES) {
            mAdapter = JingleNodeAdapter(this, registration)
            setMainTitle(R.string.service_gui_JBR_JINGLE_NODES)
        } else {
            throw IllegalArgumentException()
        }
        val listFragment = ServerListFragment()
        listFragment.listAdapter = mAdapter
        // Display the fragment as the main content.
        supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, listFragment)
                .commit()
        (findViewById<ScrollingTable>(android.R.id.content)).setOnClickListener { view -> showServerEditDialog(-1) }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val onCreateOptionsMenu = super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.server_list_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.addItem) {
            showServerEditDialog(-1)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Shows the item edit dialog, created with factory method of list model
     *
     * @param listPosition the position of selected item, -1 means "create new item"
     */
    fun showServerEditDialog(listPosition: Int) {
        val securityDialog = mAdapter!!.createItemEditDialogFragment(listPosition)
        val ft = supportFragmentManager.beginTransaction()
        securityDialog.show(ft, "ServerItemDialogFragment")
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val result = Intent()
            result.putExtra(JABBER_REGISTRATION_KEY, registration)
            setResult(Activity.RESULT_OK, result)
            finish()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * The server list fragment. Required to catch events.
     */
    class ServerListFragment : ListFragment() {
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            setEmptyText(getString(R.string.service_gui_SERVERS_LIST_EMPTY))
        }

        override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
            super.onListItemClick(l, v, position, id)
            (activity as ServerListActivity?)!!.showServerEditDialog(position)
        }
    }

    companion object {
        /**
         * Request code when launched for STUN servers list edit
         */
        var RCODE_STUN_TURN = 1

        /**
         * Request code used when launched for Jingle Nodes edit
         */
        var RCODE_JINGLE_NODES = 2

        /**
         * Request code intent's extra key
         */
        var REQUEST_CODE_KEY = "requestCode"

        /**
         * Jabber account registration intent's extra key
         */
        var JABBER_REGISTRATION_KEY = "JabberReg"
    }
}