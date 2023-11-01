/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.contactlist

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.TextView
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import net.java.sip.communicator.service.contactlist.MetaContactListException
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.service.osgi.OSGiDialogFragment
import timber.log.Timber

/**
 * Dialog that allows user to move the contact to selected group.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class MoveToGroupDialog : OSGiDialogFragment(), DialogInterface.OnClickListener {
    /**
     * The meta contact that will be moved.
     */
    private var metaContact: MetaContact? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val contentView = inflater.inflate(R.layout.move_to_group, container, false)
        dialog!!.setTitle(R.string.service_gui_MOVE_CONTACT)
        metaContact = AndroidGUIActivator.contactListService!!
                .findMetaContactByMetaUID(arguments!!.getString(META_CONTACT_UID))
        val userId = arguments!!.getString(USER_ID)
        val accountOwner = contentView.findViewById<TextView>(R.id.accountOwner)
        accountOwner.text = getString(R.string.service_gui_CONTACT_OWNER, userId)
        val groupListView = contentView.findViewById<AdapterView<*>>(R.id.selectGroupSpinner)
        val contactGroupAdapter = MetaContactGroupAdapter(activity, groupListView, true, true)
        groupListView.adapter = contactGroupAdapter
        contentView.findViewById<View>(R.id.move).setOnClickListener { v: View? ->
            val newGroup = groupListView.selectedItem as MetaContactGroup
            if (newGroup != metaContact!!.getParentMetaContactGroup()) {
                moveContact(newGroup)
            }
            dismiss()
        }
        contentView.findViewById<View>(R.id.cancel).setOnClickListener { v: View? -> dismiss() }
        return contentView
    }

    private fun moveContact(selectedItem: MetaContactGroup) {
        object : Thread() {
            override fun run() {
                try {
                    AndroidGUIActivator.contactListService!!.moveMetaContact(metaContact, selectedItem)
                } catch (e: MetaContactListException) {
                    Timber.e(e, "%s", e.message)
                    DialogActivity.showDialog(aTalkApp.globalContext,
                            aTalkApp.getResString(R.string.service_gui_ERROR), e.message)
                }
            }
        }.start()
    }

    override fun onClick(dialog: DialogInterface, which: Int) {}

    companion object {
        /**
         * Meta UID arg key.
         */
        private const val META_CONTACT_UID = "meta_uid"

        /**
         * Meta account UserID.
         */
        private const val USER_ID = "userId"

        /**
         * Creates a new instance of `MoveToGroupDialog`.
         *
         * @param metaContact the contact that will be moved.
         * @return parametrized instance of `MoveToGroupDialog`.
         */
        fun getInstance(metaContact: MetaContact?): MoveToGroupDialog {
            val dialog = MoveToGroupDialog()
            val args = Bundle()
            val userName = metaContact!!.getDefaultContact()!!.protocolProvider.accountID.mUserID
            args.putString(USER_ID, userName)
            args.putString(META_CONTACT_UID, metaContact.getMetaUID())
            dialog.arguments = args
            return dialog
        }
    }
}