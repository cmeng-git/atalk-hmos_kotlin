/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.contactlist

import android.content.Context
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import net.java.sip.communicator.service.contactlist.MetaContactListException
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.dialogs.DialogActivity
import timber.log.Timber

/**
 * Class gathers utility methods for operations on contact list.
 */
object ContactListUtils {
    /**
     * Adds a new contact in separate `Thread`.
     *
     * @param protocolProvider parent protocol provider.
     * @param group contact group to which new contact will be added.
     * @param contactAddress new contact address.
     */
    fun addContact(protocolProvider: ProtocolProviderService?, group: MetaContactGroup?,
            contactAddress: String?) {
        object : Thread() {
            override fun run() {
                val any = try {
                    AndroidGUIActivator.contactListService.createMetaContact(protocolProvider, group, contactAddress)
                } catch (ex: MetaContactListException) {
                    Timber.e("Add Contact error: %s", ex.message)
                    val ctx = aTalkApp.globalContext
                    val title = ctx.getString(R.string.service_gui_ADD_CONTACT_ERROR_TITLE)
                    val msg: String
                    val errorCode = ex.getErrorCode()
                    msg = when (errorCode) {
                        MetaContactListException.CODE_CONTACT_ALREADY_EXISTS_ERROR -> ctx.getString(
                            R.string.service_gui_ADD_CONTACT_EXIST_ERROR, contactAddress)
                        MetaContactListException.CODE_NETWORK_ERROR -> ctx.getString(
                            R.string.service_gui_ADD_CONTACT_NETWORK_ERROR, contactAddress)
                        MetaContactListException.CODE_NOT_SUPPORTED_OPERATION -> ctx.getString(
                            R.string.service_gui_ADD_CONTACT_NOT_SUPPORTED, contactAddress)
                        else -> ctx.getString(R.string.service_gui_ADD_CONTACT_ERROR, contactAddress)
                    }
                    DialogActivity.showDialog(ctx, title, msg)
                }
            }
        }.start()
    }
}