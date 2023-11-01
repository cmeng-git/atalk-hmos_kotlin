/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.contactlist

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import net.java.sip.communicator.service.contactlist.MetaContactListException
import net.java.sip.communicator.service.contactlist.MetaContactListService
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.dialogs.DialogActivity.DialogListener
import org.atalk.hmos.gui.util.ViewUtil.toString
import org.atalk.hmos.gui.util.event.EventListener
import org.atalk.service.osgi.OSGiFragment
import timber.log.Timber

/**
 * Dialog allowing user to create new contact group.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AddGroupDialog : OSGiFragment() {
    /**
     * {@inheritDoc}
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.create_group, container, false)
    }

    /**
     * Implements `DialogActivity.DialogListener` interface and handles contact group creation process.
     */
    internal class DialogListenerImpl
    /**
     * Creates new instance of `DialogListenerImpl`.
     *
     * createListener create group listener if any.
     */
    (
            /**
             * Contact created event listener.
             */
            private val listener: EventListener<MetaContactGroup?>?) : DialogListener {
        /**
         * Newly created contact group.
         */
        private var newMetaGroup: MetaContactGroup? = null

        /**
         * Thread that runs create group process.
         */
        private var createThread: Thread? = null

        // private ProgressDialog progressDialog;
        override fun onConfirmClicked(dialog: DialogActivity): Boolean {
            if (createThread != null) return false

            val view = dialog.contentFragment!!.view
            val groupName = if (view == null) null else toString(view.findViewById(R.id.editText))
            return if (groupName == null) {
                showErrorMessage(dialog.getString(R.string.service_gui_ADD_GROUP_EMPTY_NAME))
                false
            } else {
                // TODO: in progress dialog removed for simplicity
                // Add it here if operation will be taking too much time (seems to finish fast for now)
                // displayOperationInProgressDialog(dialog);
                createThread = CreateGroup(AndroidGUIActivator.contactListService!!, groupName)
                createThread!!.start()
                try {
                    // Wait for create group thread to finish
                    createThread!!.join()
                } catch (e: InterruptedException) {
                    throw RuntimeException(e)
                }
                listener?.onChangeEvent(newMetaGroup)
                true
            }
        }

        /**
         * Shows given error message as an alert.
         *
         * @param errorMessage the error message to show.
         */
        private fun showErrorMessage(errorMessage: String) {
            val ctx = aTalkApp.globalContext
            DialogActivity.showDialog(ctx, ctx.getString(R.string.service_gui_ERROR), errorMessage)
        }

        override fun onDialogCancelled(dialog: DialogActivity) {}

        /**
         * Creates a new meta contact group in a separate thread.
         */
        private inner class CreateGroup
        /**
         * Creates new instance of `AddGroupDialog`.
         *
         * @param mcl contact list service instance.
         * @param groupName name of the contact group to create.
         */
        (
                /**
                 * Contact list instance.
                 */
                var mcl: MetaContactListService,
                /**
                 * Name of the contact group to create.
                 */
                var groupName: String) : Thread() {
            override fun run() {
                try {
                    newMetaGroup = mcl.createMetaContactGroup(mcl.getRoot(), groupName)
                } catch (ex: MetaContactListException) {
                    Timber.e(ex)
                    val ctx = aTalkApp.globalContext
                    when (ex.getErrorCode()) {
                        MetaContactListException.CODE_GROUP_ALREADY_EXISTS_ERROR -> {
                            showErrorMessage(ctx.getString(R.string.service_gui_ADD_GROUP_EXIST_ERROR, groupName))
                        }
                        MetaContactListException.CODE_LOCAL_IO_ERROR -> {
                            showErrorMessage(ctx.getString(R.string.service_gui_ADD_GROUP_LOCAL_ERROR, groupName))
                        }
                        MetaContactListException.CODE_NETWORK_ERROR -> {
                            showErrorMessage(ctx.getString(R.string.service_gui_ADD_GROUP_NET_ERROR, groupName))
                        }
                        else -> {
                            showErrorMessage(ctx.getString(R.string.service_gui_ADD_GROUP_ERROR, groupName))
                        }
                    }
                }
                /*
                 * finally { hideOperationInProgressDialog(); }
                 */
            }
        }
    }

    companion object {
        /**
         * Displays create contact group dialog. If the source wants to be notified about the result
         * should pass the listener here or `null` otherwise.
         *
         * @param parent the parent `Activity`
         * @param createListener listener for contact group created event that will receive newly created instance of
         * the contact group or `null` in case user cancels the dialog.
         */
        fun showCreateGroupDialog(parent: Activity, createListener: EventListener<MetaContactGroup?>?) {
            DialogActivity.showCustomDialog(parent,
                    parent.getString(R.string.service_gui_CREATE_GROUP),
                    AddGroupDialog::class.java.name, null,
                    parent.getString(R.string.service_gui_CREATE),
                    DialogListenerImpl(createListener), null)
        }
    }
}