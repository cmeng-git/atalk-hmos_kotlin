/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.hmos.gui.util

import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.widget.CheckBox
import net.java.sip.communicator.impl.callhistory.CallHistoryActivator
import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.call.CallHistoryFragment
import org.atalk.hmos.gui.chat.ChatPanel
import org.atalk.hmos.gui.chat.ChatSession
import org.atalk.hmos.gui.chat.ChatSessionManager
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.dialogs.DialogActivity.Companion.showConfirmDialog
import org.jxmpp.jid.DomainBareJid
import org.jxmpp.util.XmppStringUtils
import timber.log.Timber
import java.io.File
import java.util.*

/**
 * The `EntityListHelper` is the class through which we make operations with the
 * `MetaContact` or `ChatRoomWrapper` in the list. All methods in this class are static.
 *
 * @author Eng Chong Meng
 */
object EntityListHelper {
    // History erase return result ZERO_ENTITY => error
    const val ZERO_ENTITY = 0
    const val CURRENT_ENTITY = 1
    const val ALL_ENTITIES = 2

    /**
     * Removes given `metaContact` from the contact list. Asks the user for confirmation before proceed.
     * a. Remove all the chat messages and chatSession records from the database.
     * b. Remove metaContact from the roster etc in DB via MclStorageManager#fireMetaContactEvent.
     *
     * Note: DomainJid will not be removed.
     *
     * @param metaContact the contact to be removed from the list.
     */
    fun removeEntity(context: Context, metaContact: MetaContact, chatPanel: ChatPanel?) {
        val message: String
        val title = context.getString(R.string.service_gui_REMOVE_CONTACT)
        val contact = metaContact.getDefaultContact()
        val contactJid = contact.contactJid
        message = if (contactJid !is DomainBareJid) {
            val userJid = contact.protocolProvider.accountID.bareJid
            context.getString(R.string.service_gui_REMOVE_CONTACT_TEXT, userJid, contactJid)
        } else {
            aTalkApp.showToastMessage(R.string.service_gui_CONTACT_INVALID, contactJid)
            return
        }
        showConfirmDialog(context, title, message,
                context.getString(R.string.service_gui_REMOVE), object : DialogActivity.DialogListener {
            override fun onConfirmClicked(dialog: DialogActivity): Boolean {
                doRemoveContact(context, metaContact)
                if (chatPanel != null) {
                    ChatSessionManager.removeActiveChat(chatPanel)
                }
                return true
            }

            override fun onDialogCancelled(dialog: DialogActivity) {}
        })
    }

    /**
     * Routine to remove the specified metaContact
     *
     * @param metaContact the metaContact to be removed
     */
    private fun doRemoveContact(ctx: Context, metaContact: MetaContact) {
        // Prevent NetworkOnMainThreadException
        Thread {
            val metaContactListService = AndroidGUIActivator.contactListService
            try {
                metaContactListService.removeMetaContact(metaContact)
            } catch (ex: Exception) {
                DialogActivity.showDialog(ctx, ctx.getString(R.string.service_gui_REMOVE_CONTACT), ex.message)
            }
        }.start()
    }

    /**
     * Removes the given `MetaContactGroup` from the list.
     *
     * @param group the `MetaContactGroup` to remove
     */
    fun removeMetaContactGroup(group: MetaContactGroup) {
        val ctx = aTalkApp.globalContext
        val message = ctx.getString(R.string.service_gui_REMOVE_GROUP_TEXT, group.getGroupName())
        showConfirmDialog(ctx, ctx.getString(R.string.service_gui_REMOVE), message, ctx.getString(R.string.service_gui_REMOVE_GROUP),
                object : DialogActivity.DialogListener {
                    override fun onConfirmClicked(dialog: DialogActivity): Boolean {
                        doRemoveGroup(group)
                        return true
                    }

                    override fun onDialogCancelled(dialog: DialogActivity) {}
                })
    }

    /**
     * Removes given group from the contact list. Catches any exceptions and shows error alert.
     *
     * @param group the group to remove from the contact list.
     */
    private fun doRemoveGroup(group: MetaContactGroup) {
        // Prevent NetworkOnMainThreadException
        Thread {
            val ctx = aTalkApp.globalContext
            try {
                AndroidGUIActivator.contactListService.removeMetaContactGroup(group)
            } catch (ex: Exception) {
                DialogActivity.showDialog(ctx, ctx.getString(R.string.service_gui_REMOVE_GROUP), ex.message)
            }
        }.start()
    }
    // ----------------- Erase History for metaContact or ChatRoom----------------------- //
    /**
     * Erase chat history for either MetaContact or ChatRoomWrapper
     *
     * @param caller the context to callback with result
     * @param desc descriptor either MetaContact or ChatRoomWrapper
     * @param msgUUIDs list of message UID to be deleted. null to delete all for the specified desc
     */
    fun eraseEntityChatHistory(caller: Context, desc: Any, msgUUIDs: List<String>?,
            msgFiles: MutableList<File>?) {
        val entityJid = when (desc) {
            is MetaContact -> desc.getDisplayName()!!
            is ChatRoomWrapper -> XmppStringUtils.parseLocalpart(desc.chatRoomID)
            else -> return
        }
        val args = Bundle()
        args.putString(ChatMessageDeleteFragment.ARG_MESSAGE,
                aTalkApp.getResString(R.string.service_gui_HISTORY_REMOVE_PER_CONTACT_WARNING, entityJid))
        val title = aTalkApp.getResString(R.string.service_gui_HISTORY_CONTACT, entityJid)

        // Displays the history delete dialog and waits for user confirmation
        DialogActivity.showCustomDialog(aTalkApp.globalContext, title, ChatMessageDeleteFragment::class.java.name,
                args, aTalkApp.getResString(R.string.service_gui_PURGE), object : DialogActivity.DialogListener {
            override fun onConfirmClicked(dialog: DialogActivity): Boolean {
                val cbMediaDelete = dialog.findViewById<CheckBox>(R.id.cb_media_delete)
                val mediaDelete = cbMediaDelete.isChecked

                // EntityListHelper mErase = new EntityListHelper();
                EraseEntityChatHistory(caller, msgUUIDs!!, msgFiles!!, mediaDelete).execute(desc)
                return true
            }

            override fun onDialogCancelled(dialog: DialogActivity) {}
        }, null)
    }
    // ----------- Erase all the local stored chat history for all the entities (currently this is disabled) ------------- //
    /**
     * Erase all the local stored chat history for all the entities i.e. MetaContacts or ChatRoomWrappers.
     *
     * @param callback the callback.
     */
    fun eraseAllEntityHistory(callback: Context) {
        val ctx = aTalkApp.globalContext
        val title = ctx.getString(R.string.service_gui_HISTORY)
        val message = ctx.getString(R.string.service_gui_HISTORY_REMOVE_ALL_WARNING)
        showConfirmDialog(ctx, title, message, ctx.getString(R.string.service_gui_PURGE),
                object : DialogActivity.DialogListener {
                    override fun onConfirmClicked(dialog: DialogActivity): Boolean {
                        val cbMediaDelete = dialog.findViewById<CheckBox>(R.id.cb_media_delete)
                        val mediaDelete = cbMediaDelete.isChecked

                        // EntityListHelper mErase = new EntityListHelper();
                        EraseAllEntityHistory(callback, mediaDelete).execute()
                        return true
                    }

                    override fun onDialogCancelled(dialog: DialogActivity) {}
                }
        )
    }
    // ----------------- Erase Call History ----------------------- //
    /**
     * Erase local store call history
     *
     * @param caller the context
     * @param callUUIDs list of call record UID to be deleted. null to delete all for the specified desc
     */
    fun eraseEntityCallHistory(caller: CallHistoryFragment, callUUIDs: List<String>?) {
        // Displays the call history delete dialog and waits for user
        caller.context?.let {
            showConfirmDialog(it, R.string.service_gui_CALL_HISTORY_GROUP_NAME,
                    R.string.service_gui_CALL_HISTORY_REMOVE_WARNING, R.string.service_gui_PURGE,
                    object : DialogActivity.DialogListener {
                        override fun onConfirmClicked(dialog: DialogActivity): Boolean {
                            EraseEntityCallHistory(caller, callUUIDs, null).execute()
                            return true
                        }

                        override fun onDialogCancelled(dialog: DialogActivity) {}
                    }
            )
        }
    }

    fun eraseEntityCallHistory(caller: CallHistoryFragment, endDate: Date?) {
        // Displays the call history delete dialog and waits for user
        caller.context?.let {
            showConfirmDialog(it, R.string.service_gui_CALL_HISTORY_GROUP_NAME,
                    R.string.service_gui_CALL_HISTORY_REMOVE_BEFORE_DATE_WARNING, R.string.service_gui_PURGE,
                    object : DialogActivity.DialogListener {
                        override fun onConfirmClicked(dialog: DialogActivity): Boolean {
                            EraseEntityCallHistory(caller, null, endDate).execute()
                            return true
                        }

                        override fun onDialogCancelled(dialog: DialogActivity) {}
                    }, endDate
            )
        }
    }

    /**
     * Perform history message deletion in background.
     * Purge all history messages for the descriptor if messageUUIDs is null
     *
     * Note: if the sender deletes the media content immediately after sending, only the tmp copy is deleted
     */
    private open class EraseEntityChatHistory(context: Context, msgUUIDs: List<String>, msgFiles: MutableList<File>, purgeMedia: Boolean) : AsyncTask<Any?, Void?, Int?>() {
        private val mCallback: TaskCompleted
        private val isPurgeMediaFile: Boolean
        private val msgUUIDs: List<String>
        private var msgFiles: MutableList<File>?

        init {
            mCallback = context as TaskCompleted
            this.msgUUIDs = msgUUIDs
            this.msgFiles = msgFiles
            isPurgeMediaFile = purgeMedia
        }

        override fun onPreExecute() {}

        override fun doInBackground(vararg mDescriptor: Any?): Int {
            val descriptor = mDescriptor[0]
            if (descriptor is MetaContact || descriptor is ChatRoomWrapper) {
                val mhs = MessageHistoryActivator.messageHistoryService
                if (isPurgeMediaFile) {
                    // null => delete all local saved files; then construct locally
                    if (msgFiles == null) {
                        msgFiles = ArrayList()
                        val filePathDel = mhs.getLocallyStoredFilePath(descriptor)
                        for (filePath in filePathDel) {
                            msgFiles!!.add(File(filePath))
                        }
                    }

                    // purge all the files of the deleted messages
                    for (file in msgFiles!!) {
                        if (file.exists() && !file.delete()) Timber.e("Failed to delete file: %s", file.name)
                    }
                }
                if (descriptor is MetaContact) {
                    mhs.eraseLocallyStoredChatHistory(descriptor, msgUUIDs)
                } else {
                    val chatRoom = (descriptor as ChatRoomWrapper).chatRoom!!
                    mhs.eraseLocallyStoredChatHistory(chatRoom, msgUUIDs)
                }
            } else {
                return ZERO_ENTITY
            }
            return CURRENT_ENTITY
        }

        override fun onPostExecute(result: Int?) {
            // Return result and deleted msgUuUIDs to caller
            mCallback.onTaskComplete(result!!, msgUUIDs)
        }

        override fun onCancelled() {}
    }

    private open class EraseAllEntityHistory(context: Context, purgeMedia: Boolean) : AsyncTask<Void?, Void?, Int?>() {
        private val isPurgeMediaFile: Boolean
        private val mCallback: TaskCompleted

        init {
            mCallback = context as TaskCompleted
            isPurgeMediaFile = purgeMedia
        }

        override fun onPreExecute() {}

        override fun doInBackground(vararg none: Void?): Int {
            val mhs = MessageHistoryActivator.messageHistoryService
            if (isPurgeMediaFile) {
                // purge all the files of the deleted messages
                val msgFiles = mhs.locallyStoredFilePath
                for (msgFile in msgFiles) {
                    val file = File(msgFile)
                    if (file.exists() && !file.delete()) Timber.w("Failed to delete the file: %s", msgFile)
                }
            }
            mhs.eraseLocallyStoredChatHistory(ChatSession.MODE_SINGLE)
            mhs.eraseLocallyStoredChatHistory(ChatSession.MODE_MULTI)
            return ALL_ENTITIES
        }

        override fun onPostExecute(result: Int?) {
            // Return result to caller
            mCallback.onTaskComplete(result!!, null)
        }

        override fun onCancelled() {}
    }

    /**
     * Perform history message delete in background.
     * Purge all history messages for the descriptor if messageUUIDs is null
     */
    private class EraseEntityCallHistory(caller: CallHistoryFragment, callUUIDs: List<String>?, endDate: Date?) : AsyncTask<Void?, Void?, Int?>() {
        private val mCallback: TaskCompleted?
        private val callUUIDs: List<String>?
        private val mEndDate: Date?

        /**
         * To delete call history based on given parameters either callUUIDs or endDate
         *
         *soundHandler caller the caller i.e. CallHistoryFragment.this
         *soundHandler callUUIDs list of callUuids to be deleted OR;
         *soundHandler endDate records on and before the given endDate toe be deleted
         */
        init {
            mCallback = caller
            this.callUUIDs = callUUIDs
            mEndDate = endDate
        }

        override fun onPreExecute() {}
        override fun doInBackground(vararg none: Void?): Int {
            val CHS = CallHistoryActivator.getCallHistoryService()!!
            return if (mEndDate == null) {
                CHS.eraseLocallyStoredHistory(callUUIDs)
                callUUIDs!!.size
            } else {
                CHS.eraseLocallyStoredHistoryBefore(mEndDate)
            }
        }

        override fun onPostExecute(result: Int?) {
            // Return result to caller
            mCallback?.onTaskComplete(result!!, callUUIDs)
        }

        override fun onCancelled() {}
    }

    interface TaskCompleted {
        // Define data you like to return from AsyncTask
        fun onTaskComplete(result: Int, deletedUUIDs: List<String>?)
    }
}