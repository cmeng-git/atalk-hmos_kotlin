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
import android.widget.EditText
import android.widget.TextView
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.MetaContactListException
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.util.ViewUtil.toString
import org.atalk.service.osgi.OSGiDialogFragment
import timber.log.Timber

/**
 * Dialog that allows user to move the contact to selected group.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ContactRenameDialog : OSGiDialogFragment(), DialogInterface.OnClickListener {
    private lateinit var mEditName: EditText

    /**
     * The meta contact that will be moved.
     */
    private var metaContact: MetaContact? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        dialog!!.setTitle(R.string.service_gui_CONTACT_RENAME_TITLE)
        metaContact = AndroidGUIActivator.contactListService!!
                .findMetaContactByMetaUID(arguments!!.getString(META_CONTACT_UID))
        val contentView = inflater.inflate(R.layout.contact_rename, container, false)
        val userId = arguments!!.getString(USER_ID)
        val accountOwner = contentView.findViewById<TextView>(R.id.accountOwner)
        accountOwner.text = getString(R.string.service_gui_CONTACT_OWNER, userId)
        mEditName = contentView.findViewById(R.id.editName)
        val contactNick = arguments!!.getString(CONTACT_NICK)
        if (StringUtils.isNotEmpty(contactNick)) mEditName.setText(contactNick)
        contentView.findViewById<View>(R.id.rename).setOnClickListener { v: View? ->
            val displayName = toString(mEditName)
            if (displayName == null) {
                showErrorMessage(getString(R.string.service_gui_CONTACT_NAME_EMPTY))
            } else renameContact(displayName)
            dismiss()
        }
        contentView.findViewById<View>(R.id.cancel).setOnClickListener { v: View? -> dismiss() }
        return contentView
    }

    private fun renameContact(newDisplayName: String) {
        object : Thread() {
            override fun run() {
                try {
                    AndroidGUIActivator.contactListService!!.renameMetaContact(metaContact, newDisplayName)
                } catch (e: MetaContactListException) {
                    Timber.e(e, "%s", e.message)
                    showErrorMessage(e.message)
                }
            }
        }.start()
    }

    override fun onClick(dialog: DialogInterface, which: Int) {}

    /**
     * Shows given error message as an alert.
     *
     * @param errMessage the error message to show.
     */
    private fun showErrorMessage(errMessage: String?) {
        DialogActivity.showDialog(aTalkApp.globalContext,
                aTalkApp.getResString(R.string.service_gui_ERROR), errMessage)
    }

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
         * Meta account UserID.
         */
        private const val CONTACT_NICK = "contactNick"

        /**
         * Creates new instance of `MoveToGroupDialog`.
         *
         * @param metaContact the contact that will be moved.
         * @return parametrized instance of `MoveToGroupDialog`.
         */
        fun getInstance(metaContact: MetaContact?): ContactRenameDialog {
            val args = Bundle()
            val userId = metaContact!!.getDefaultContact()!!.protocolProvider.accountID.mUserID
            args.putString(USER_ID, userId)
            args.putString(META_CONTACT_UID, metaContact.getMetaUID())
            args.putString(CONTACT_NICK, metaContact.getDisplayName())
            val dialog = ContactRenameDialog()
            dialog.arguments = args
            return dialog
        }
    }
}