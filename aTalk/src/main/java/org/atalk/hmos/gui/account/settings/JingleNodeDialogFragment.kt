/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account.settings

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import net.java.sip.communicator.service.protocol.JingleNodeDescriptor
import org.atalk.hmos.R
import org.atalk.hmos.gui.util.ViewUtil
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException

/**
 * The Jingle Node edit dialog. It used to edit or create new [JingleNodeDescriptor].
 * It serves as a "create new" dialog when `null` is passed as a descriptor argument.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class JingleNodeDialogFragment : DialogFragment() {
    /**
     * Edited Jingle Node descriptor
     */
    private var descriptor: JingleNodeDescriptor? = null

    /**
     * Parent [JingleNodeAdapter] that will be notified about any change to the Jingle Node
     */
    private var listener: JingleNodeAdapter? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Get the layout inflater
        val inflater = requireActivity().layoutInflater
        val contentView = inflater.inflate(R.layout.jingle_node_dialog, null)

        // Builds the dialog
        var builder = AlertDialog.Builder(activity!!)
        builder = builder.setTitle(R.string.service_gui_JBR_JINGLE_NODES)
        builder = builder.setView(contentView)
                .setPositiveButton(R.string.service_gui_SAVE, null)
                .setNeutralButton(R.string.service_gui_SERVERS_LIST_CANCEL, null)
        if (descriptor != null) {
            // Add remove button if it''s not "create new" dialog
            builder = builder.setNegativeButton(R.string.service_gui_SERVERS_LIST_REMOVE, null)
            val jidAdrTextView = contentView.findViewById<TextView>(R.id.jidAddress)
            jidAdrTextView.text = descriptor!!.getJID()
            val useRelayCb = contentView.findViewById<View>(R.id.relaySupportCheckbox) as CompoundButton
            useRelayCb.isChecked = descriptor!!.isRelaySupported()
        }
        val dialog = builder.create()
        dialog.setOnShowListener { dialogInterface: DialogInterface? ->
            val pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            pos.setOnClickListener { view: View? -> if (saveChanges()) dismiss() }
            val neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            neg?.setOnClickListener { view: View? ->
                listener!!.removeJingleNode(descriptor)
                dismiss()
            }
        }
        return dialog
    }

    /**
     * Saves the changes if all data is correct
     *
     * @return `true` if all data is correct and changes have been stored in descriptor
     */
    fun saveChanges(): Boolean {
        val dialog = dialog
        val relaySupport = (dialog!!.findViewById<View>(R.id.relaySupportCheckbox) as CompoundButton).isChecked
        val jingleAddress = ViewUtil.toString(dialog.findViewById(R.id.jidAddress))
        if (jingleAddress == null) {
            Toast.makeText(activity, "The Jid address can not be empty", Toast.LENGTH_LONG).show()
            return false
        }
        var jidAddress: Jid? = null
        try {
            jidAddress = JidCreate.from(jingleAddress)
        } catch (e: XmppStringprepException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
        if (descriptor == null) {
            // Create new descriptor
            descriptor = JingleNodeDescriptor(jidAddress!!, relaySupport)
            listener!!.addJingleNode(descriptor!!)
        } else {
            descriptor!!.setAddress(jidAddress!!)
            descriptor!!.setRelay(relaySupport)
            listener!!.updateJingleNode(descriptor)
        }
        return true
    }

    companion object {
        /**
         * Creates new instance of [JingleNodeDialogFragment]
         *
         * @param listener parent [JingleNodeAdapter]
         * @param descriptor the [JingleNodeDescriptor] to edit or `null` if a new node shall be created
         */
        fun newInstance(listener: JingleNodeAdapter?, descriptor: JingleNodeDescriptor?): JingleNodeDialogFragment {
            if (listener == null) throw NullPointerException()
            val fragmentJnd = JingleNodeDialogFragment()
            fragmentJnd.listener = listener
            fragmentJnd.descriptor = descriptor
            return fragmentJnd
        }
    }
}