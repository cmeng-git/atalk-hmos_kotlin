/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account.settings

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import net.java.sip.communicator.service.protocol.StunServerDescriptor
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp

/**
 * List model for STUN servers. Is used to edit STUN servers preferences of Jabber account. It's also responsible for
 * creating list row `View`s and implements [ServerItemAdapter.createItemEditDialogFragment] to
 * provide item edit dialog.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 * See [ServerListActivity]
 */
class StunServerAdapter
/**
 * Creates new instance of [StunServerAdapter]
 *
 * @param parent the parent [android.app.Activity] used as a context
 * @param registration the registration object that holds the STUN server list
 */
(parent: Activity,
        /**
         * The [JabberAccountRegistration] that contains the original list
         */
        private val registration: JabberAccountRegistration?) : ServerItemAdapter(parent) {
    override fun getCount(): Int {
        return registration!!.additionalStunServers.size
    }

    override fun getItem(i: Int): Any {
        return registration!!.additionalStunServers[i]
    }

    override fun getView(i: Int, view: View?, viewGroup: ViewGroup): View {
        val li = parent.layoutInflater
        val rowView = li.inflate(android.R.layout.simple_list_item_1, viewGroup, false)
        val tv = rowView.findViewById<TextView>(android.R.id.text1)
        val server = getItem(i) as StunServerDescriptor
        val descriptor = aTalkApp.getResString(R.string.service_gui_SERVERS_STUN_DESCRIPTOR,
                server.address, server.port, if (server.isTurnSupported) "(+TURN)" else "")
        tv.text = descriptor
        return rowView
    }

    /**
     * Removes the server from the list.
     *
     * @param descriptor the server descriptor to be removed
     */
    fun removeServer(descriptor: StunServerDescriptor) {
        registration!!.additionalStunServers.remove(descriptor)
        refresh()
    }

    /**
     * Add new STUN server descriptor to the list
     *
     * @param descriptor the server descriptor
     */
    fun addServer(descriptor: StunServerDescriptor) {
        registration!!.addStunServer(descriptor)
        refresh()
    }

    /**
     * Updates given server description
     *
     * @param descriptor the server to be updated
     */
    fun updateServer(descriptor: StunServerDescriptor?) {
        refresh()
    }

    override fun createItemEditDialogFragment(position: Int): DialogFragment {
        return if (position < 0) StunTurnDialogFragment.newInstance(this, null)
        else StunTurnDialogFragment.newInstance(this, getItem(position) as StunServerDescriptor)
    }
}