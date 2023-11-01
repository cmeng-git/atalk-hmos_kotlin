/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.muc

import net.java.sip.communicator.service.muc.ChatRoomListChangeEvent
import net.java.sip.communicator.service.muc.ChatRoomListChangeListener
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapperListener
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.RegistrationState
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener
import net.java.sip.communicator.util.ConfigurationUtils.removeChatRoom
import net.java.sip.communicator.util.ConfigurationUtils.saveChatRoom
import org.atalk.hmos.gui.chat.ChatSession
import org.atalk.persistance.DatabaseBackend
import org.jivesoftware.smack.SmackException.NoResponseException
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smackx.bookmarks.BookmarkManager
import org.osgi.framework.Bundle
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.util.*

/**
 * The `ChatRoomsList` is the list containing all chat rooms.
 *
 * @author Yana Stamcheva
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class ChatRoomListImpl : RegistrationStateChangeListener, ServiceListener {
    /**
     * The list containing all chat servers and rooms.
     */
    private val providersList = Vector<ChatRoomProviderWrapper>()

    /**
     * All ChatRoomProviderWrapperListener change listeners registered so far.
     */
    private val providerChangeListeners = ArrayList<ChatRoomProviderWrapperListener?>()

    /**
     * A list of all `ChatRoomListChangeListener`-s.
     */
    private val listChangeListeners = Vector<ChatRoomListChangeListener>()
    private val mDB = DatabaseBackend.writableDB

    /**
     * Constructs and initializes new `ChatRoomListImpl` objects. Adds the created object
     * as service lister to the bundle context.
     */
    init {
        loadList()
        MUCActivator.bundleContext!!.addServiceListener(this)
    }

    /**
     * Initializes the list of chat rooms.
     */
    private fun loadList() {
        try {
            val serRefs = MUCActivator.bundleContext!!.getServiceReferences(ProtocolProviderService::class.java.name, null)
                    ?: return

            // If we don't have providers at this stage we just return.
            for (serRef in serRefs) {
                val pps = MUCActivator.bundleContext!!.getService(serRef as ServiceReference<Any>) as ProtocolProviderService
                val multiUserChatOpSet = pps.getOperationSet(OperationSetMultiUserChat::class.java)
                if (multiUserChatOpSet != null) {
                    addChatProvider(pps)
                }
            }
        } catch (e: InvalidSyntaxException) {
            Timber.e(e, "Failed to obtain service references.")
        }
    }

    /**
     * Adds the given `ChatRoomListChangeListener` that will listen for all changes of the
     * chat room list data model.
     *
     * @param l the listener to add.
     */
    fun addChatRoomListChangeListener(l: ChatRoomListChangeListener) {
        synchronized(listChangeListeners) { listChangeListeners.add(l) }
    }

    /**
     * Removes the given `ChatRoomListChangeListener`.
     *
     * @param l the listener to remove.
     */
    fun removeChatRoomListChangeListener(l: ChatRoomListChangeListener) {
        synchronized(listChangeListeners) { listChangeListeners.remove(l) }
    }

    /**
     * Notifies all interested listeners that a change in the chat room list model has occurred.
     *
     * @param chatRoomWrapper the chat room wrapper that identifies the chat room
     * @param eventID the identifier of the event
     */
    fun fireChatRoomListChangedEvent(chatRoomWrapper: ChatRoomWrapper?, eventID: Int) {
        val evt = ChatRoomListChangeEvent(chatRoomWrapper, eventID)
        for (l in listChangeListeners) {
            l.contentChanged(evt)
        }
    }

    /**
     * Adds a chat server which is registered and all its existing chat rooms (local & on server)
     *
     * @param pps the `ProtocolProviderService` corresponding to the chat server
     */
    fun addRegisteredChatProvider(pps: ProtocolProviderService): ChatRoomProviderWrapper {
        val chatRoomProvider = ChatRoomProviderWrapperImpl(pps)
        providersList.add(chatRoomProvider)

        // local stored chatRooms that the user have sessions before
        val chatRoomList = getExistingChatRooms(pps)

        // cmeng: should not include non-joined server chatRooms in user chatRoom window
//        MUCServiceImpl mucService = MUCActivator.getMUCService();
//        List<String> sChatRoomList = mucService.getExistingChatRooms(chatRoomProvider);
//        for (String sRoom : sChatRoomList) {
//            if (!chatRoomList.contains(sRoom))
//                chatRoomList.add(sRoom);
//        }
        for (chatRoomID in chatRoomList) {
            val chatRoomWrapper = ChatRoomWrapperImpl(chatRoomProvider, chatRoomID)
            chatRoomProvider.addChatRoom(chatRoomWrapper)
        }
        fireProviderWrapperAdded(chatRoomProvider)
        return chatRoomProvider
    }

    /**
     * Adds a listener to wait for provider to be registered or unregistered.
     * Only take action on unregistered to remove chatRoomWrapperProvider
     *
     * @param pps the `ProtocolProviderService` corresponding to the chat server
     */
    private fun addChatProvider(pps: ProtocolProviderService) {
        if (pps.isRegistered) addRegisteredChatProvider(pps) else pps.addRegistrationStateChangeListener(this)
    }

    /**
     * Removes the corresponding server and all related chat rooms from this list.
     *
     * @param pps the `ProtocolProviderService` corresponding to the server to remove
     */
    private fun removeChatProvider(pps: ProtocolProviderService) {
        val wrapper = findServerWrapperFromProvider(pps)
        if (wrapper != null) removeChatProvider(wrapper, true)
    }

    /**
     * Removes the corresponding server and all related chatRooms from this list.
     *
     * @param chatRoomProvider the `ChatRoomProviderWrapper` corresponding to the server to remove
     * @param permanently whether to remove any listener and stored configuration
     */
    private fun removeChatProvider(chatRoomProvider: ChatRoomProviderWrapper, permanently: Boolean) {
        providersList.remove(chatRoomProvider)
        if (permanently) {
            chatRoomProvider.protocolProvider.removeRegistrationStateChangeListener(this)
            val accountID = chatRoomProvider.protocolProvider.accountID
            val accountManager = MUCActivator.accountManager
            if (accountManager != null
                    && !accountManager.storedAccounts.contains(accountID)) {
                val accountUid = accountID.accountUniqueID
                val args = arrayOf(accountUid, ChatSession.MODE_MULTI.toString())
                mDB.delete(ChatSession.TABLE_NAME, ChatSession.ACCOUNT_UID + "=? AND "
                        + ChatSession.MODE + "=?", args)
            }
        }
        for (i in 0 until chatRoomProvider.countChatRooms()) {
            val wrapper = chatRoomProvider.getChatRoom(i)!!
            MUCActivator.uIService!!.closeChatRoomWindow(wrapper)

            // clears listeners added by chat room
            wrapper.removeListeners()
        }
        // clears listeners added by the system chat room
        chatRoomProvider.systemRoomWrapper!!.removeListeners()
        fireProviderWrapperRemoved(chatRoomProvider)
    }

    /**
     * Adds a chat room to this list.
     *
     * @param chatRoomWrapper the `ChatRoom` to add
     */
    fun addChatRoom(chatRoomWrapper: ChatRoomWrapper) {
        val chatRoomProvider = chatRoomWrapper.parentProvider
        if (!chatRoomProvider.containsChatRoom(chatRoomWrapper)) chatRoomProvider.addChatRoom(chatRoomWrapper)
        if (chatRoomWrapper.isPersistent!!) {
            saveChatRoom(chatRoomProvider.protocolProvider,
                    chatRoomWrapper.chatRoomID, chatRoomWrapper.chatRoomID)
        }
        fireChatRoomListChangedEvent(chatRoomWrapper, ChatRoomListChangeEvent.CHAT_ROOM_ADDED)
    }

    /**
     * Removes the given `ChatRoom` from the list of all chat rooms and bookmark on server
     *
     * @param chatRoomWrapper the `ChatRoomWrapper` to remove
     */
    fun removeChatRoom(chatRoomWrapper: ChatRoomWrapper?) {
        val chatRoomProvider = chatRoomWrapper!!.parentProvider
        if (providersList.contains(chatRoomProvider)) {
            /*
             * Remove bookmark from the server when the room is removed.
             */
            val pps = chatRoomProvider.protocolProvider
            val bookmarkManager = BookmarkManager.getBookmarkManager(pps.connection)
            val entityBareJid = chatRoomWrapper.entityBareJid
            try {
                bookmarkManager.removeBookmarkedConference(entityBareJid)
            } catch (e: NoResponseException) {
                Timber.w("Failed to remove Bookmarks: %s", e.message)
            } catch (e: NotConnectedException) {
                Timber.w("Failed to remove Bookmarks: %s", e.message)
            } catch (e: XMPPErrorException) {
                Timber.w("Failed to remove Bookmarks: %s", e.message)
            } catch (e: InterruptedException) {
                Timber.w("Failed to remove Bookmarks: %s", e.message)
            }
            chatRoomProvider.removeChatRoom(chatRoomWrapper)
            removeChatRoom(pps, chatRoomWrapper.chatRoomID)
            chatRoomWrapper.removeListeners()
            fireChatRoomListChangedEvent(chatRoomWrapper, ChatRoomListChangeEvent.CHAT_ROOM_REMOVED)
        }
    }

    /**
     * Returns the `ChatRoomWrapper` that correspond to the given `ChatRoom`. If the
     * list of chat rooms doesn't contain a corresponding wrapper - returns null.
     *
     * @param chatRoom the `ChatRoom` that we're looking for
     * @return the `ChatRoomWrapper` object corresponding to the given `ChatRoom`
     */
    fun findChatRoomWrapperFromChatRoom(chatRoom: ChatRoom?): ChatRoomWrapper? {
        for (provider in providersList) {
            // check only for the right PP
            if (chatRoom!!.getParentProvider() != provider.protocolProvider) continue
            val systemRoomWrapper = provider.systemRoomWrapper
            val systemRoom = systemRoomWrapper!!.chatRoom
            if (systemRoom != null && systemRoom == chatRoom) {
                return systemRoomWrapper
            } else {
                val chatRoomWrapper = provider.findChatRoomWrapperForChatRoom(chatRoom)
                if (chatRoomWrapper != null) {
                    // stored chatRooms has no chatRoom, but their id is the same as the chatRoom
                    // we are searching wrapper for. Also during reconnect we don't have the same
                    // chat id for another chat room object.
                    if (chatRoomWrapper.chatRoom == null
                            || chatRoomWrapper.chatRoom != chatRoom) {
                        chatRoomWrapper.chatRoom = chatRoom
                    }
                    return chatRoomWrapper
                }
            }
        }
        return null
    }

    /**
     * Returns the `ChatRoomWrapper` that correspond to the given id of chat room and
     * provider. If the list of chat rooms doesn't contain a corresponding wrapper - returns null.
     *
     * @param chatRoomID the id of `ChatRoom` that we're looking for
     * @param pps the protocol provider associated with the chat room.
     * @return the `ChatRoomWrapper` object corresponding to the given id of the chat room
     */
    fun findChatRoomWrapperFromChatRoomID(chatRoomID: String, pps: ProtocolProviderService?): ChatRoomWrapper? {
        for (provider in providersList) {
            // check all pps OR only for the right pps if provided (cmeng)
            if (pps != null && pps != provider.protocolProvider) continue
            val systemRoomWrapper = provider.systemRoomWrapper
            val systemRoom = systemRoomWrapper!!.chatRoom
            if (systemRoom != null && systemRoom.getIdentifier().equals(chatRoomID)) {
                return systemRoomWrapper
            } else {
                val chatRoomWrapper = provider.findChatRoomWrapperForChatRoomID(chatRoomID)
                if (chatRoomWrapper != null || pps != null) {
                    return chatRoomWrapper
                }
            }
        }
        return null
    }

    /**
     * Returns the `ChatRoomProviderWrapper` that correspond to the given
     * `ProtocolProviderService`. If the list doesn't contain a corresponding wrapper - returns null.
     *
     * @param protocolProvider the protocol provider that we're looking for
     * @return the `ChatRoomProvider` object corresponding to the given `ProtocolProviderService`
     */
    fun findServerWrapperFromProvider(protocolProvider: ProtocolProviderService): ChatRoomProviderWrapper? {
        for (chatRoomProvider in providersList) {
            if (chatRoomProvider != null && chatRoomProvider.protocolProvider == protocolProvider) {
                return chatRoomProvider
            }
        }
        return null
    }

    /**
     * Returns an iterator to the list of chat room providers.
     *
     * @return an iterator to the list of chat room providers.
     */
    val chatRoomProviders: List<ChatRoomProviderWrapper>
        get() = providersList

    /**
     * Adds a ChatRoomProviderWrapperListener to the listener list.
     *
     * @param listener the ChatRoomProviderWrapperListener to be added
     */
    @Synchronized
    fun addChatRoomProviderWrapperListener(listener: ChatRoomProviderWrapperListener?) {
        providerChangeListeners.add(listener)
    }

    /**
     * Removes a ChatRoomProviderWrapperListener from the listener list.
     *
     * @param listener the ChatRoomProviderWrapperListener to be removed
     */
    @Synchronized
    fun removeChatRoomProviderWrapperListener(listener: ChatRoomProviderWrapperListener?) {
        providerChangeListeners.remove(listener)
    }

    /**
     * Fire that chat room provider wrapper was added.
     *
     * @param provider which was added.
     */
    private fun fireProviderWrapperAdded(provider: ChatRoomProviderWrapper) {
        for (target in providerChangeListeners) {
            target!!.chatRoomProviderWrapperAdded(provider)
        }
    }

    /**
     * Fire that chat room provider wrapper was removed.
     *
     * @param provider which was removed.
     */
    private fun fireProviderWrapperRemoved(provider: ChatRoomProviderWrapper) {
        for (target in providerChangeListeners) {
            target!!.chatRoomProviderWrapperRemoved(provider)
        }
    }

    /**
     * Listens for changes of providers registration state, as we can use only registered providers.
     *
     * @param evt a `RegistrationStateChangeEvent` which describes the event that occurred.
     */
    override fun registrationStateChanged(evt: RegistrationStateChangeEvent) {
        val pps = evt.getProvider()
        if (evt.getNewState() === RegistrationState.REGISTERED) {
            // Must use MUCServiceImpl#synchronizeOpSetWithLocalContactList to avoid duplication entry
        } else if (evt.getNewState() === RegistrationState.UNREGISTERED || evt.getNewState() === RegistrationState.AUTHENTICATION_FAILED || evt.getNewState() === RegistrationState.CONNECTION_FAILED) {
            val wrapper = findServerWrapperFromProvider(pps)
            if (wrapper != null) {
                removeChatProvider(wrapper, false)
            }
        }
    }

    override fun serviceChanged(event: ServiceEvent) {
        // if the event is caused by a bundle being stopped, we don't want to know
        if (event.serviceReference.bundle.state == Bundle.STOPPING) return
        val service = MUCActivator.bundleContext!!.getService(event.serviceReference) as? ProtocolProviderService
                ?: return
        // we don't care if the source service is not a protocol provider
        val pps = service as ProtocolProviderService
        val multiUserChatOpSet = pps.getOperationSet(OperationSetMultiUserChat::class.java)
        if (multiUserChatOpSet != null) {
            if (event.type == ServiceEvent.REGISTERED) {
                addChatProvider(pps)
            } else if (event.type == ServiceEvent.UNREGISTERING) {
                removeChatProvider(pps)
            }
        }
    }

    /**
     * Returns existing chatRooms in store for the given `ProtocolProviderService`.
     *
     * @param pps the `ProtocolProviderService`, whom chatRooms we're looking for
     * @return existing chatRooms in store for the given `ProtocolProviderService`
     */
    fun getExistingChatRooms(pps: ProtocolProviderService?): ArrayList<String> {
        val chatRooms = ArrayList<String>(0)
        val accountUid = pps!!.accountID.accountUniqueID
        val args = arrayOf(accountUid, ChatSession.MODE_MULTI.toString())
        val columns = arrayOf(ChatSession.ENTITY_JID)
        val ORDER_ASC = ChatSession.ENTITY_JID + " ASC"
        val cursor = mDB.query(ChatSession.TABLE_NAME, columns, ChatSession.ACCOUNT_UID
                + "=? AND " + ChatSession.MODE + "=?", args, null, null, ORDER_ASC)
        while (cursor.moveToNext()) {
            chatRooms.add(cursor.getString(0))
        }
        cursor.close()
        return chatRooms
    }
}