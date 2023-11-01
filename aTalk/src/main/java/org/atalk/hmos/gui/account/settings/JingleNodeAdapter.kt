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
import net.java.sip.communicator.service.protocol.JingleNodeDescriptor
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration

/**
 * Implements list model for Jingle Nodes list of [JabberAccountRegistration].
 *
 * @see ServerItemAdapter
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class JingleNodeAdapter
/**
 * Creates new instance of [JingleNodeAdapter]
 *
 * @param parent
 * the parent [android.app.Activity] used a a context
 * @param registration
 * the registration object that contains Jingle Nodes
 */
(parent: Activity,
    /**
     * The [JabberAccountRegistration] object that contains Jingle Nodes
     */
    private val registration: JabberAccountRegistration?) : ServerItemAdapter(parent) {
    override fun getCount(): Int {
        return registration!!.additionalJingleNodes.size
    }

    override fun getItem(i: Int): JingleNodeDescriptor? {
        return registration!!.additionalJingleNodes[i]
    }

    /**
     * Creates the dialog fragment that will allow user to edit Jingle Node
     *
     * @param position
     * the position of item to edit
     * @return the Jingle Node edit dialog
     */
    public override fun createItemEditDialogFragment(position: Int): DialogFragment {
        val dialogFragment = if (position < 0) JingleNodeDialogFragment.newInstance(this, null)
        else JingleNodeDialogFragment.newInstance(this, getItem(position) as JingleNodeDescriptor)
        return dialogFragment
    }

    override fun getView(i: Int, view: View?, viewGroup: ViewGroup): View {
        val li = parent.layoutInflater
        // View rowView = li.inflate(R.layout.server_list_row, viewGroup, false);
        val rowView = li.inflate(android.R.layout.simple_list_item_1, viewGroup, false)
        val tv = rowView.findViewById<TextView>(android.R.id.text1)
        val node = getItem(i) as JingleNodeDescriptor
        tv.text = node.getJID().toString() + if (node.isRelaySupported()) " (+Relay support)" else ""
        return rowView
    }

    /**
     * Removes the Jingle Node from the list
     *
     * @param descriptor
     * Jingle Node that shall be removed
     */
    fun removeJingleNode(descriptor: JingleNodeDescriptor?) {
        registration!!.additionalJingleNodes.remove(descriptor)
        refresh()
    }

    /**
     * Adds new Jingle node to the list
     *
     * @param descriptor
     * the [JingleNodeDescriptor] that will be included in this adapter
     */
    fun addJingleNode(descriptor: JingleNodeDescriptor) {
        registration!!.addJingleNodes(descriptor)
        refresh()
    }

    /**
     * Updates given Jingle Node
     *
     * @param descriptor
     * the JingleNode that will be updated
     */
    fun updateJingleNode(descriptor: JingleNodeDescriptor?) {
        refresh()
    }
}