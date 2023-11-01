/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chatroomslist

import android.database.sqlite.SQLiteDatabase
import net.java.sip.communicator.service.protocol.AdHocChatRoom
import net.java.sip.communicator.service.protocol.OperationSetAdHocMultiUserChat
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.chat.ChatSession
import org.atalk.hmos.gui.chat.conference.AdHocChatRoomProviderWrapper
import org.atalk.hmos.gui.chat.conference.AdHocChatRoomWrapper
import org.atalk.persistance.DatabaseBackend
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceReference
import java.util.*

/**
 * The `AdHocChatRoomsList` is the list containing all ad-hoc chat rooms.
 *
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
class AdHocChatRoomList {
    /**
     * The list containing all chat servers and ad-hoc rooms.
     */
    private val providersList = Vector<AdHocChatRoomProviderWrapper>()
    private var mDB: SQLiteDatabase? = null

    /**
     * Initializes the list of ad-hoc chat rooms.
     */
    fun loadList() {
        val bundleContext = AndroidGUIActivator.bundleContext
        mDB = DatabaseBackend.writableDB
        var serRefs: Array<ServiceReference<*>?>? = null
        try {
            serRefs = bundleContext!!.getServiceReferences(
                    ProtocolProviderService::class.java.name, null)
        } catch (e: InvalidSyntaxException) {
            e.printStackTrace()
        }
        if (serRefs != null) {
            for (serRef in serRefs) {
                val protocolProvider = AndroidGUIActivator.bundleContext!!.getService(serRef as ServiceReference<ProtocolProviderService>)
                val adHocMultiUserChatOpSet = protocolProvider.getOperationSet(OperationSetAdHocMultiUserChat::class.java)
                if (adHocMultiUserChatOpSet != null) {
                    addChatProvider(protocolProvider)
                }
            }
        }
    }

    /**
     * Adds a chat server and all its existing ad-hoc chat rooms.
     *
     * @param pps the `ProtocolProviderService` corresponding to the chat server
     */
    fun addChatProvider(pps: ProtocolProviderService) {
        val chatRoomProvider = AdHocChatRoomProviderWrapper(pps)
        providersList.add(chatRoomProvider)
        val accountUid = pps.accountID.accountUniqueID
        val args = arrayOf(accountUid!!, ChatSession.MODE_MULTI.toString())
        val columns = arrayOf(ChatSession.ENTITY_JID)
        val cursor = mDB!!.query(ChatSession.TABLE_NAME, columns, ChatSession.ACCOUNT_UID
                + "=? AND " + ChatSession.MODE + "=?", args, null, null, null)
        while (cursor.moveToNext()) {
            val chatRoomID = cursor.getString(0)
            val chatRoomWrapper = AdHocChatRoomWrapper(chatRoomProvider, chatRoomID)
            chatRoomProvider.addAdHocChatRoom(chatRoomWrapper)
        }
        cursor.close()
    }

    /**
     * Removes the corresponding server and all related ad-hoc chat rooms from this list.
     *
     * @param pps the `ProtocolProviderService` corresponding to the server to remove
     */
    fun removeChatProvider(pps: ProtocolProviderService) {
        val wrapper = findServerWrapperFromProvider(pps)
        wrapper?.let { removeChatProvider(it) }
    }

    /**
     * Removes the corresponding server and all related ad-hoc chat rooms from this list.
     *
     * @param adHocChatRoomProvider the `AdHocChatRoomProviderWrapper` corresponding to the server to remove
     */
    private fun removeChatProvider(adHocChatRoomProvider: AdHocChatRoomProviderWrapper) {
        providersList.remove(adHocChatRoomProvider)
        val accountID = adHocChatRoomProvider.protocolProvider.accountID
        val accountUid = accountID.accountUniqueID
        val args = arrayOf(accountUid!!, ChatSession.MODE_MULTI.toString())
        mDB!!.delete(ChatSession.TABLE_NAME, ChatSession.ACCOUNT_UID + "=? AND "
                + ChatSession.MODE + "=?", args)
    }

    /**
     * Adds a chat room to this list.
     *
     * @param adHocChatRoomWrapper the `AdHocChatRoom` to add
     */
    fun addAdHocChatRoom(adHocChatRoomWrapper: AdHocChatRoomWrapper) {
        val adHocChatRoomProvider = adHocChatRoomWrapper.parentProvider
        if (!adHocChatRoomProvider.containsAdHocChatRoom(adHocChatRoomWrapper)) adHocChatRoomProvider.addAdHocChatRoom(adHocChatRoomWrapper)
    }

    /**
     * Removes the given `AdHocChatRoom` from the list of all ad-hoc chat rooms.
     *
     * @param adHocChatRoomWrapper the `AdHocChatRoomWrapper` to remove
     */
    fun removeChatRoom(adHocChatRoomWrapper: AdHocChatRoomWrapper) {
        val adHocChatRoomProvider = adHocChatRoomWrapper.parentProvider
        if (providersList.contains(adHocChatRoomProvider)) {
            adHocChatRoomProvider.removeChatRoom(adHocChatRoomWrapper)
        }
    }

    /**
     * Returns the `AdHocChatRoomWrapper` that correspond to the given `AdHocChatRoom
    ` * . If the list of ad-hoc chat rooms
     * doesn't contain a corresponding wrapper - returns null.
     *
     * @param adHocChatRoom the `ChatRoom` that we're looking for
     * @return the `ChatRoomWrapper` object corresponding to the given `ChatRoom`
     */
    fun findChatRoomWrapperFromAdHocChatRoom(adHocChatRoom: AdHocChatRoom): AdHocChatRoomWrapper? {
        for (provider in providersList) {
            val chatRoomWrapper = provider.findChatRoomWrapperForAdHocChatRoom(adHocChatRoom)
            if (chatRoomWrapper != null) {
                // stored chatRooms has no chatRoom, but their id is the same as the chatRoom
                // we are searching wrapper for
                if (chatRoomWrapper.adHocChatRoom == null) {
                    chatRoomWrapper.adHocChatRoom = adHocChatRoom
                }
                return chatRoomWrapper
            }
        }
        return null
    }

    /**
     * Returns the `AdHocChatRoomProviderWrapper` that correspond to the given
     * `ProtocolProviderService`. If the list doesn't
     * contain a corresponding wrapper - returns null.
     *
     * @param protocolProvider the protocol provider that we're looking for
     * @return the `AdHocChatRoomProvider` object corresponding to the given
     * `ProtocolProviderService`
     */
    fun findServerWrapperFromProvider(protocolProvider: ProtocolProviderService): AdHocChatRoomProviderWrapper? {
        for (chatRoomProvider in providersList) {
            if (chatRoomProvider.protocolProvider == protocolProvider) {
                return chatRoomProvider
            }
        }
        return null
    }
}