/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.muc

import android.text.TextUtils
import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.contactsource.SourceContact
import net.java.sip.communicator.service.gui.AuthenticationWindowService
import net.java.sip.communicator.service.muc.ChatRoomListChangeEvent
import net.java.sip.communicator.service.muc.ChatRoomListChangeListener
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapperListener
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.muc.MUCService
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.ChatRoomInvitation
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.OperationNotSupportedException
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum
import net.java.sip.communicator.util.ConfigurationUtils.getChatRoomProperty
import net.java.sip.communicator.util.ConfigurationUtils.updateChatRoomStatus
import net.java.sip.communicator.util.ServiceUtils.getService
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.chat.ChatSessionManager.getChatIntent
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.dialogs.DialogActivity.DialogListener
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.XMPPException
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import timber.log.Timber

/**
 * The `MUCServiceImpl` class implements the service for the chat rooms.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class MUCServiceImpl : MUCService() {
    /**
     * The list of persistent chat rooms.
     */
    private val chatRoomList = ChatRoomListImpl()

    /**
     * Called to accept an incoming invitation. Adds the invitation chat room to the list of chat rooms and joins it.
     *
     * @param invitation the invitation to accept.
     */
    override fun acceptInvitation(invitation: ChatRoomInvitation) {
        val chatRoom = invitation.getTargetChatRoom()
        val password = invitation.getChatRoomPassword()
        var nickName = getChatRoomProperty(chatRoom.getParentProvider(),
                chatRoom.getName(), ChatRoom.USER_NICK_NAME)
        if (nickName == null) {
            // cmeng - need to add a dialog for Nickname (can also add during invite dialog ?)
            // String[] joinOptions = ChatRoomJoinOptionsDialog.getJoinOptions(true, chatRoom.getParentProvider(), chatRoom.getIdentifier(),
            // MUCActivator.getGlobalDisplayDetailsService().getDisplayName(chatRoom.getParentProvider()));
            // nickName = joinOptions[0];
            nickName = AndroidGUIActivator.globalDisplayDetailsService?.getDisplayName(chatRoom.getParentProvider())
        }
        joinChatRoom(chatRoom, nickName, password)
    }

    /**
     * Adds a change listener to the `ChatRoomList`.
     *
     * @param l the listener.
     */
    fun addChatRoomListChangeListener(l: ChatRoomListChangeListener) {
        chatRoomList.addChatRoomListChangeListener(l)
    }

    /**
     * Removes a change listener to the `ChatRoomList`.
     *
     * @param l the listener.
     */
    fun removeChatRoomListChangeListener(l: ChatRoomListChangeListener) {
        chatRoomList.removeChatRoomListChangeListener(l)
    }

    /**
     * Fires a `ChatRoomListChangedEvent` event.
     *
     * @param chatRoomWrapper the chat room.
     * @param eventID the id of the event.
     */
    override fun fireChatRoomListChangedEvent(chatRoomWrapper: ChatRoomWrapper, eventID: Int) {
        chatRoomList.fireChatRoomListChangedEvent(chatRoomWrapper, eventID)
    }

    /**
     * Joins the given chat room with the given password and manages all the exceptions that could
     * occur during the join process.
     *
     * @param chatRoomWrapper the chat room to join.
     * @param nickName the nickname we choose for the given chat room.
     * @param password the password.
     * @param rememberPassword if true the password should be saved.
     * @param isFirstAttempt is this the first attempt to join room, used to check whether to show some error messages
     * @param subject the subject which will be set to the room after the user join successful.
     */
    private fun joinChatRoom(chatRoomWrapper: ChatRoomWrapper, nickName: String?, password: ByteArray,
            rememberPassword: Boolean, isFirstAttempt: Boolean, subject: String?) {
        val chatRoom = chatRoomWrapper.chatRoom
        if (chatRoom == null) {
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_WARNING,
                    R.string.service_gui_CHATROOM_NOT_CONNECTED, chatRoomWrapper.chatRoomName)
            return
        }
        JoinChatRoomTask(chatRoomWrapper as ChatRoomWrapperImpl?, nickName, password,
                rememberPassword, isFirstAttempt, subject).start()
    }

    /**
     * Joins the given chat room with the given password and manages all the exceptions that could
     * occur during the join process.
     *
     * @param chatRoomWrapper_ the chat room to join.
     * @param nickName the nickname we choose for the given chat room.
     * @param password the password.
     */
    fun joinChatRoom(chatRoomWrapper_: ChatRoomWrapper, nickName: String?, password: ByteArray?) {
        var chatRoomWrapper = chatRoomWrapper_
        if (chatRoomWrapper.chatRoom == null) {
            chatRoomWrapper = createChatRoom(chatRoomWrapper, "", false, false, true)
        }
        val chatRoom = chatRoomWrapper.chatRoom
        if (chatRoom == null) {
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_WARNING,
                    R.string.service_gui_CHATROOM_NOT_CONNECTED, chatRoomWrapper.chatRoomName)
            return
        }
        JoinChatRoomTask(chatRoomWrapper as ChatRoomWrapperImpl?, nickName, password, null).start()
    }

    /**
     * Joins the given chat room with the given password and manages all the exceptions that could
     * occur during the join process.
     *
     * @param chatRoomWrapper the chat room to join.
     * @param nickName the nickname we choose for the given chat room.
     * @param password room password.
     * @param subject which will be set to the room after the user join successful.
     */
    override fun joinChatRoom(chatRoomWrapper: ChatRoomWrapper, nickName: String, password: ByteArray?, subject: String?) {
        var iChatRoomWrapper = chatRoomWrapper
        var chatRoom = iChatRoomWrapper.chatRoom
        if (chatRoom == null) {
            iChatRoomWrapper = createChatRoom(iChatRoomWrapper, "", false, false, true)
            if (iChatRoomWrapper != null) chatRoom = iChatRoomWrapper.chatRoom
        }
        if (chatRoom == null) {
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_WARNING,
                    R.string.service_gui_CHATROOM_NOT_CONNECTED, chatRoom)
            return
        }

        // join from add chat room dialog
        JoinChatRoomTask(iChatRoomWrapper as ChatRoomWrapperImpl?, nickName, password, subject).start()
    }

    /**
     * Join chat room.
     *
     * @param chatRoomWrapper_ the chatRoom Wrapper
     */
    fun joinChatRoom(chatRoomWrapper_: ChatRoomWrapper) {
        var chatRoomWrapper = chatRoomWrapper_
        if (chatRoomWrapper.chatRoom == null) {
            chatRoomWrapper = createChatRoom(chatRoomWrapper, "", false, false, true)
        }
        val chatRoom = chatRoomWrapper.chatRoom
        if (chatRoom == null) {
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_WARNING,
                    R.string.service_gui_CHATROOM_NOT_CONNECTED, chatRoomWrapper.chatRoomName)
            return
        }
        JoinChatRoomTask(chatRoomWrapper as ChatRoomWrapperImpl?, null, null, null).start()
    }

    /**
     * Joins the given chat room and manages all the exceptions that could occur during the join process.
     *
     * @param chatRoom the chat room to join
     * @param nickname the nickname we're using to join
     * @param password the password we're using to join
     */
    fun joinChatRoom(chatRoom: ChatRoom, nickname: String?, password: ByteArray?) {
        val chatRoomWrapper = getChatRoomWrapperByChatRoom(chatRoom, true)
        if (chatRoomWrapper != null) this.joinChatRoom(chatRoomWrapper, nickname, password)
    }

    /**
     * Joins the room with the given room name via the given chat room provider.
     *
     * @param chatRoomName the name of the room to join.
     * @param chatRoomProvider the chat room provider to join through.
     */
    override fun joinChatRoom(chatRoomName: String, chatRoomProvider: ChatRoomProviderWrapper) {
        val groupChatOpSet = chatRoomProvider.protocolProvider.getOperationSet(OperationSetMultiUserChat::class.java)
        var chatRoom: ChatRoom? = null
        try {
            /* Find chatRoom for <code>roomName</code>. If the room doesn't exists in the cache then creates it. */
            chatRoom = groupChatOpSet!!.findRoom(chatRoomName)
        } catch (e: Exception) {
            Timber.log(TimberLog.FINER, e, "Exception occurred while searching for room:%s", chatRoomName)
        }
        if (chatRoom != null) {
            var chatRoomWrapper = chatRoomList.findChatRoomWrapperFromChatRoom(chatRoom)
            if (chatRoomWrapper == null) {
                val parentProvider = chatRoomList.findServerWrapperFromProvider(chatRoom.getParentProvider())!!
                chatRoomWrapper = ChatRoomWrapperImpl(parentProvider, chatRoom)
                chatRoomList.addChatRoom(chatRoomWrapper)
                fireChatRoomListChangedEvent(chatRoomWrapper, ChatRoomListChangeEvent.CHAT_ROOM_ADDED)
            }
            joinChatRoom(chatRoomWrapper)
        } else DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_ERROR,
                R.string.service_gui_CHATROOM_NOT_EXIST,
                chatRoomName, chatRoomProvider.protocolProvider.accountID.service)
    }

    /**
     * Creates a chat room, by specifying the chatRoomWrapper and
     * eventually, the contacts invited to participate in this chat room.
     *
     * @param chatRoomWrapper the chat room to join.
     * @param reason the reason for room creation
     * @param persistent is the room persistent
     * @param isPrivate whether the room will be private or public.
     * @return the `ChatRoomWrapper` corresponding to the created room
     */
    fun createChatRoom(chatRoomWrapper: ChatRoomWrapper,
            reason: String?, join: Boolean, persistent: Boolean, isPrivate: Boolean): ChatRoomWrapper {
        val onServerRoom = chatRoomWrapper.chatRoom != null
        return createChatRoom(chatRoomWrapper.chatRoomName!!, chatRoomWrapper.protocolProvider!!,
                ArrayList(), reason, join, persistent, isPrivate, onServerRoom)
    }

    /**
     * Creates a chat room, by specifying the chat room name, the parent protocol provider and
     * eventually, the contacts invited to participate in this chat room.
     *
     * @param roomName the name of the room
     * @param protocolProvider the parent protocol provider.
     * @param contacts the contacts invited when creating the chat room.
     * @param reason the reason for room creation
     * @param join whether we should join the room after creating it.
     * @param persistent whether the newly created room will be persistent.
     * @param isPrivate whether the room will be private or public.
     * @param onServerRoom whether the room is already in the server room list.
     * @return the `ChatRoomWrapper` corresponding to the created room or `null` if
     * the protocol fails to create the chat room.
     */
    override fun createChatRoom(roomName: String?, protocolProvider: ProtocolProviderService,
            contacts: Collection<String?>?, reason: String?, join: Boolean, persistent: Boolean, isPrivate: Boolean,
            onServerRoom: Boolean): ChatRoomWrapper {
        // If there's no group chat operation set we have nothing to do here.
        val groupChatOpSet = protocolProvider.getOperationSet(OperationSetMultiUserChat::class.java)!!
        var chatRoomWrapper: ChatRoomWrapper? = null
        var chatRoom: ChatRoom? = null
        try {
            val roomProperties = HashMap<String?, Any?>()
            roomProperties[ChatRoom.IS_PRIVATE] = isPrivate
            roomProperties[ChatRoom.ON_SERVER_ROOM] = onServerRoom
            chatRoom = groupChatOpSet.createChatRoom(roomName, roomProperties)

            // server may reject chatRoom creation and timeout on reply
            if (chatRoom != null && join) {
                chatRoom.join()
                for (contact in contacts!!) {
                    chatRoom.invite(JidCreate.entityBareFrom(contact), reason)
                }
            }
        } catch (ex: OperationFailedException) {
            Timber.e(ex, "Failed to create chat room.")
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_ERROR,
                    R.string.service_gui_CHATROOM_CREATE_ERROR, protocolProvider.accountID, ex.message)
        } catch (ex: OperationNotSupportedException) {
            Timber.e(ex, "Failed to create chat room.")
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_ERROR,
                    R.string.service_gui_CHATROOM_CREATE_ERROR, protocolProvider.accountID, ex.message)
        } catch (ex: XmppStringprepException) {
            Timber.e(ex, "Failed to create chat room.")
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_ERROR,
                    R.string.service_gui_CHATROOM_CREATE_ERROR, protocolProvider.accountID, ex.message)
        } catch (ex: NotConnectedException) {
            Timber.e(ex, "Failed to create chat room.")
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_ERROR,
                    R.string.service_gui_CHATROOM_CREATE_ERROR, protocolProvider.accountID, ex.message)
        } catch (ex: InterruptedException) {
            Timber.e(ex, "Failed to create chat room.")
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_ERROR,
                    R.string.service_gui_CHATROOM_CREATE_ERROR, protocolProvider.accountID, ex.message)
        }
        if (chatRoom != null) {
            val parentProvider = chatRoomList.findServerWrapperFromProvider(protocolProvider)!!

            // if there is the same room ids don't add new wrapper as old one maybe already created
            chatRoomWrapper = chatRoomList.findChatRoomWrapperFromChatRoom(chatRoom)
            if (chatRoomWrapper == null) {
                chatRoomWrapper = ChatRoomWrapperImpl(parentProvider, chatRoom)
                chatRoomWrapper.isPersistent = persistent
                chatRoomList.addChatRoom(chatRoomWrapper)
            }
        }
        return chatRoomWrapper!!
    }

    /**
     * Creates a private chat room, by specifying the parent protocol provider and eventually, the
     * contacts invited to participate in this chat room.
     *
     * @param protocolProvider the parent protocol provider.
     * @param contacts the contacts invited when creating the chat room.
     * @param reason the reason for room creation
     * @param persistent is the room persistent
     * @return the `ChatRoomWrapper` corresponding to the created room
     */
    override fun createPrivateChatRoom(protocolProvider: ProtocolProviderService,
            contacts: Collection<String?>?, reason: String?, persistent: Boolean): ChatRoomWrapper {
        return this.createChatRoom(null, protocolProvider, contacts, reason, true, persistent, isPrivate = true, onServerRoom = false)
    }

    /**
     * Returns existing chat rooms for the given `chatRoomProvider`.
     *
     * @param chatRoomProvider the `ChatRoomProviderWrapper`, which chat rooms we're looking for
     * @return existing chat rooms for the given `chatRoomProvider`
     */
    override fun getExistingChatRooms(chatRoomProvider: ChatRoomProviderWrapper): List<String>? {
        if (chatRoomProvider == null) return null
        val protocolProvider = chatRoomProvider.protocolProvider
        val groupChatOpSet = protocolProvider.getOperationSet(OperationSetMultiUserChat::class.java)
                ?: return null
        val chatRooms: MutableList<String> = ArrayList(0)
        try {
            for (chatRoom in groupChatOpSet.getExistingChatRooms()!!) {
                chatRooms.add(chatRoom.toString())
            }
        } catch (e: OperationFailedException) {
            Timber.log(TimberLog.FINER, e, "Failed to obtain existing chat rooms for server: %s",
                    protocolProvider.accountID.service)
        } catch (e: OperationNotSupportedException) {
            Timber.log(TimberLog.FINER, e, "Failed to obtain existing chat rooms for server: %s",
                    protocolProvider.accountID.service)
        }
        return chatRooms
    }

    /**
     * Returns existing chatRooms in store for the given `ProtocolProviderService`.
     *
     * @param pps the `ProtocolProviderService`, whom chatRooms we're looking for
     * @return existing chatRooms in store for the given `ProtocolProviderService`
     */
    override fun getExistingChatRooms(pps: ProtocolProviderService): MutableList<String> {
        return chatRoomList.getExistingChatRooms(pps)
    }

    /**
     * Rejects the given invitation with the specified reason.
     *
     * @param multiUserChatOpSet the operation set to use for rejecting the invitation
     * @param invitation the invitation to reject
     * @param reason the reason for the rejection
     */
    @Throws(OperationFailedException::class)
    override fun rejectInvitation(multiUserChatOpSet: OperationSetMultiUserChat, invitation: ChatRoomInvitation, reason: String?) {
        multiUserChatOpSet.rejectInvitation(invitation, reason)
    }

    /**
     * Leaves the given chat room.
     *
     * @param chatRoomWrapper the chat room to leave.
     * @return `ChatRoomWrapper` instance associated with the chat room.
     */
    override fun leaveChatRoom(chatRoomWrapper: ChatRoomWrapper): ChatRoomWrapper? {
        val chatRoom = chatRoomWrapper.chatRoom
        if (chatRoom == null) {
            DialogActivity.showDialog(aTalkApp.globalContext, R.string.service_gui_WARNING,
                    R.string.service_gui_CHATROOM_LEAVE_NOT_CONNECTED)
            return null
        }

        if (chatRoom.isJoined()) chatRoom.leave()
        val existChatRoomWrapper = chatRoomList.findChatRoomWrapperFromChatRoom(chatRoom)
                ?: return null

        // We save the choice of the user, before the chat room is really joined, because even the
        // join fails we want the next time when we login to join this chat room automatically.
        updateChatRoomStatus(chatRoomWrapper.protocolProvider!!,
                chatRoomWrapper.chatRoomID, GlobalStatusEnum.OFFLINE_STATUS)
        return existChatRoomWrapper
    }

    /**
     * Joins a chat room in an asynchronous way.
     */
    private inner class JoinChatRoomTask(private val chatRoomWrapper: ChatRoomWrapperImpl?, nickName: String?, password: ByteArray?,
            rememberPassword: Boolean, isFirstAttempt: Boolean, subject: String?) : Thread() {
        private val chatRoomId = chatRoomWrapper!!.chatRoomName
        private val nickName: String?
        private val password: ByteArray?
        private val rememberPassword: Boolean
        private val isFirstAttempt: Boolean
        private val subject: String?

        init {
            this.nickName = nickName
            this.isFirstAttempt = isFirstAttempt
            this.subject = subject
            if (password == null) {
                val passString = chatRoomWrapper!!.loadPassword()
                if (passString != null) {
                    this.password = passString.toByteArray()
                } else {
                    this.password = null
                }
            } else {
                this.password = password
            }
            this.rememberPassword = rememberPassword
        }

        constructor(chatRoomWrapper: ChatRoomWrapperImpl?, nickName: String?, password: ByteArray?, subject: String?) : this(chatRoomWrapper, nickName, password, false, true, subject)

        /**
         * [Thread]{run()} to perform all asynchronous tasks.
         */
        override fun run() {
            // Must setup up chatRoom and ready to receive incoming messages before joining/sending presence to server
            // ChatPanel chatPanel = ChatSessionManager.getMultiChat(chatRoomWrapper, true);
            val chatRoom = chatRoomWrapper!!.chatRoom!!
            try {
                if (chatRoom.isJoined()) {
                    if (!TextUtils.isEmpty(nickName) && nickName != chatRoom.getUserNickname().toString()) {
                        chatRoom.setUserNickname(nickName)
                    }
                    if (!TextUtils.isEmpty(subject) && subject != chatRoom.getSubject()) {
                        chatRoom.setSubject(subject)
                    }
                } else {
                    startChatActivity(chatRoomWrapper)
                    /*
                     * Retry until Exception or canceled by user; join chatRoom captcha challenge from server
                     * @see ChatRoomJabberImpl#joinAs(),
                     */
                    var retry = true
                    while (retry) {
                        retry = if (password != null && password.isNotEmpty()) chatRoom.joinAs(nickName!!, password) else if (nickName != null) chatRoom.joinAs(nickName) else chatRoom.join()
                    }
                    done(ChatRoomWrapper.JOIN_SUCCESS_PROP, "")
                }
            } catch (e: OperationFailedException) {
                Timber.log(TimberLog.FINER, e, "Failed to join: %s or change chatRoom attributes: %s; %s",
                        chatRoom.getName(), nickName, subject)
                val message = e.message
                when (e.getErrorCode()) {
                    OperationFailedException.CAPTCHA_CHALLENGE -> done(ChatRoomWrapper.JOIN_CAPTCHA_VERIFICATION_PROP, message)
                    OperationFailedException.AUTHENTICATION_FAILED -> done(ChatRoomWrapper.JOIN_AUTHENTICATION_FAILED_PROP, message)
                    OperationFailedException.REGISTRATION_REQUIRED -> done(ChatRoomWrapper.JOIN_REGISTRATION_REQUIRED_PROP, message)
                    OperationFailedException.PROVIDER_NOT_REGISTERED -> done(ChatRoomWrapper.JOIN_PROVIDER_NOT_REGISTERED_PROP, message)
                    OperationFailedException.SUBSCRIPTION_ALREADY_EXISTS -> done(ChatRoomWrapper.JOIN_SUBSCRIPTION_ALREADY_EXISTS_PROP, message)
                    OperationFailedException.NOT_ENOUGH_PRIVILEGES -> done(ChatRoomWrapper.NOT_ENOUGH_PRIVILEGES, message)
                    else -> done(ChatRoomWrapper.JOIN_UNKNOWN_ERROR_PROP, message)
                }
            } catch (ex: IllegalStateException) {
                done(ChatRoomWrapper.JOIN_PROVIDER_NOT_REGISTERED_PROP, ex.message)
            }
        }

        /**
         * Starts the chat activity for the given metaContact.
         *
         * @param descriptor `MetaContact` for which chat activity will be started.
         */
        private fun startChatActivity(descriptor: Any?) {
            val chatIntent = getChatIntent(descriptor)
            if (chatIntent != null) {
                aTalkApp.globalContext.startActivity(chatIntent)
            } else {
                Timber.w("Failed to start chat with %s", descriptor)
            }
        }

        /**
         * Performs UI changes after the chat room join task has finished.
         *
         * @param returnCode the result code from the chat room join task.
         */
        private fun done(returnCode: String, msg: String?) {
            updateChatRoomStatus(chatRoomWrapper!!.protocolProvider,
                    chatRoomWrapper.chatRoomID, GlobalStatusEnum.ONLINE_STATUS)
            var errMsg: String? = null
            when (returnCode) {
                ChatRoomWrapper.JOIN_AUTHENTICATION_FAILED_PROP -> {
                    chatRoomWrapper.removePassword()
                    val authWindowsService = getService(MUCActivator.bundleContext, AuthenticationWindowService::class.java)

                    // cmeng - icon not implemented in Android
                    // AuthenticationWindow.getAuthenticationWindowIcon(chatRoomWrapper.getParentProvider().getProtocolProvider()),
                    val authWindow = authWindowsService!!.create(chatRoomWrapper.nickName,
                            null, null, false, chatRoomWrapper.isPersistent!!, null,
                            aTalkApp.getResString(R.string.service_gui_AUTHENTICATION_WINDOW_TITLE,
                                    chatRoomWrapper.parentProvider.name),
                            aTalkApp.getResString(R.string.service_gui_CHATROOM_REQUIRES_PASSWORD, chatRoomId), "", null,
                            if (isFirstAttempt) null else aTalkApp.getResString(R.string.service_gui_AUTHENTICATION_FAILED, chatRoomId), null)
                    authWindow!!.setVisible(true)
                    if (!authWindow.isCanceled) {
                        joinChatRoom(chatRoomWrapper, nickName, String(authWindow.password!!).toByteArray(),
                                authWindow.isRememberPassword, false, subject)
                    }
                }

                ChatRoomWrapper.JOIN_CAPTCHA_VERIFICATION_PROP -> errMsg = msg

                ChatRoomWrapper.JOIN_REGISTRATION_REQUIRED_PROP -> errMsg = msg + "\n" + aTalkApp.getResString(R.string.service_gui_CHATROOM_NOT_JOINED)

                ChatRoomWrapper.JOIN_PROVIDER_NOT_REGISTERED_PROP -> errMsg = aTalkApp.getResString(R.string.service_gui_CHATROOM_NOT_CONNECTED, chatRoomId)

                ChatRoomWrapper.JOIN_SUBSCRIPTION_ALREADY_EXISTS_PROP -> errMsg = aTalkApp.getResString(R.string.service_gui_CHATROOM_ALREADY_JOINED, chatRoomId)

                ChatRoomWrapper.NOT_ENOUGH_PRIVILEGES -> errMsg = msg

                ChatRoomWrapper.JOIN_UNKNOWN_ERROR_PROP -> errMsg = aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_FAILED_REASON, chatRoomId, msg)

                ChatRoomWrapper.JOIN_SUCCESS_PROP -> {
                    if (rememberPassword) {
                        chatRoomWrapper.savePassword(String(password!!))
                    }
                    if (!TextUtils.isEmpty(subject)) {
                        try {
                            chatRoomWrapper.chatRoom!!.setSubject(subject)
                        } catch (ex: OperationFailedException) {
                            Timber.w("Failed to set subject.")
                        }
                    }
                }
            }
            if (errMsg != null) {
                DialogActivity.showDialog(aTalkApp.globalContext,
                        aTalkApp.getResString(R.string.service_gui_ERROR), errMsg)
            }
            chatRoomWrapper.firePropertyChange(returnCode)
        }
    }

    /**
     * Finds the `ChatRoomWrapper` instance associated with the source contact.
     *
     * @param contact the source contact.
     * @return the `ChatRoomWrapper` instance.
     */
    override fun findChatRoomWrapperFromSourceContact(contact: SourceContact): ChatRoomWrapper? {
        if (contact !is ChatRoomSourceContact) return null
        return chatRoomList.findChatRoomWrapperFromChatRoomID(contact.chatRoomID!!, contact.provider)
    }

    /**
     * Finds the `ChatRoomWrapper` instance associated with the chat room.
     *
     * @param chatRoomID the id of the chat room.
     * @param pps the provider of the chat room.
     * @return the `ChatRoomWrapper` instance.
     */
    override fun findChatRoomWrapperFromChatRoomID(chatRoomID: String, pps: ProtocolProviderService?): ChatRoomWrapper? {
        return chatRoomList.findChatRoomWrapperFromChatRoomID(chatRoomID, pps)
    }

    /**
     * Searches for chat room wrapper in chat room list by chat room.
     *
     * @param chatRoom the chat room.
     * @param create if `true` and the chat room wrapper is not found new chatRoomWrapper is created.
     * @return found chat room wrapper or the created chat room wrapper.
     */
    override fun getChatRoomWrapperByChatRoom(chatRoom: ChatRoom, create: Boolean): ChatRoomWrapper? {
        var chatRoomWrapper = chatRoomList.findChatRoomWrapperFromChatRoom(chatRoom)
        if (chatRoomWrapper == null && create) {
            val parentProvider = chatRoomList.findServerWrapperFromProvider(chatRoom.getParentProvider())
            if (parentProvider != null) {
                chatRoomWrapper = ChatRoomWrapperImpl(parentProvider, chatRoom)
                chatRoomList.addChatRoom(chatRoomWrapper)
            }
        }
        return chatRoomWrapper
    }

    /**
     * Goes through the locally stored chat rooms list and for each [ChatRoomWrapper] tries
     * to find the corresponding server stored [ChatRoom] in the specified operation set.
     * Joins automatically all found chat rooms.
     *
     * @param protocolProvider the protocol provider for the account to synchronize
     * @param opSet the multi user chat operation set, which give us access to chat room server
     */
    override fun synchronizeOpSetWithLocalContactList(protocolProvider: ProtocolProviderService,
            opSet: OperationSetMultiUserChat) {
        var chatRoomProvider = findServerWrapperFromProvider(protocolProvider)
        if (chatRoomProvider == null) {
            chatRoomProvider = chatRoomList.addRegisteredChatProvider(protocolProvider)
        }
        chatRoomProvider.synchronizeProvider()
    }

    /**
     * Returns an iterator to the list of chat room providers.
     *
     * @return an iterator to the list of chat room providers.
     */
    override val chatRoomProviders: List<ChatRoomProviderWrapper>
        get() = chatRoomList.chatRoomProviders

    /**
     * Removes the given `ChatRoom` from the list of all chat rooms.
     *
     * @param chatRoomWrapper the `ChatRoomWrapper` to remove
     */
    override fun removeChatRoom(chatRoomWrapper: ChatRoomWrapper?) {
        chatRoomList.removeChatRoom(chatRoomWrapper)
    }

    /**
     * Destroys the given `ChatRoom` from the list of all chat rooms.
     *
     * @param chatRoomWrapper the `ChatRoomWrapper` to be destroyed.
     * @param reason the reason for destroying.
     * @param alternateAddress the alternative entityBareJid of the chatRoom to join.
     */
    override fun destroyChatRoom(chatRoomWrapper: ChatRoomWrapper, reason: String?, alternateAddress: EntityBareJid?) {
        try {
            if (chatRoomWrapper.chatRoom!!.destroy(reason, alternateAddress)) {
                MUCActivator.uIService!!.closeChatRoomWindow(chatRoomWrapper)
                chatRoomList.removeChatRoom(chatRoomWrapper)
            } else {
                // If we leave a chat room which is not persistent, the room cannot be destroyed on the server;
                // and error is returned when we try to destroy it i.e. not-authorized(401)
                if (!chatRoomWrapper.chatRoom!!.isPersistent() && !chatRoomWrapper.chatRoom!!.isJoined()) {
                    chatRoomList.removeChatRoom(chatRoomWrapper)
                }
            }
            // Allow user to purge local stored chatRoom on XMPPException
        } catch (e: XMPPException) {
            DialogActivity.showConfirmDialog(aTalkApp.globalContext,
                    R.string.service_gui_CHATROOM_DESTROY_TITLE,
                    R.string.service_gui_CHATROOM_DESTROY_ERROR,
                    R.string.service_gui_PURGE,
                    object : DialogListener {
                        override fun onConfirmClicked(dialog: DialogActivity): Boolean {
                            chatRoomList.removeChatRoom(chatRoomWrapper)
                            return true
                        }

                        override fun onDialogCancelled(dialog: DialogActivity) {}
                    }, chatRoomWrapper.entityBareJid, e.message
            )
        }
    }

    /**
     * Adds a ChatRoomProviderWrapperListener to the listener list.
     *
     * @param listener the ChatRoomProviderWrapperListener to be added
     */
    override fun addChatRoomProviderWrapperListener(listener: ChatRoomProviderWrapperListener) {
        chatRoomList.addChatRoomProviderWrapperListener(listener)
    }

    /**
     * Removes the ChatRoomProviderWrapperListener to the listener list.
     *
     * @param listener the ChatRoomProviderWrapperListener to be removed
     */
    override fun removeChatRoomProviderWrapperListener(listener: ChatRoomProviderWrapperListener) {
        chatRoomList.removeChatRoomProviderWrapperListener(listener)
    }

    /**
     * Returns the `ChatRoomProviderWrapper` that correspond to the given
     * `ProtocolProviderService`. If the list doesn't contain a corresponding wrapper - returns null.
     *
     * @param protocolProvider the protocol provider that we're looking for
     * @return the `ChatRoomProvider` object corresponding to the given `ProtocolProviderService`
     */
    override fun findServerWrapperFromProvider(protocolProvider: ProtocolProviderService): ChatRoomProviderWrapper? {
        return chatRoomList.findServerWrapperFromProvider(protocolProvider)
    }

    /**
     * Returns the `ChatRoomWrapper` that correspond to the given `ChatRoom`. If the
     * list of chat rooms doesn't contain a corresponding wrapper - returns null.
     *
     * @param chatRoom the `ChatRoom` that we're looking for
     * @return the `ChatRoomWrapper` object corresponding to the given `ChatRoom`
     */
    override fun findChatRoomWrapperFromChatRoom(chatRoom: ChatRoom): ChatRoomWrapper? {
        return chatRoomList.findChatRoomWrapperFromChatRoom(chatRoom)
    }

    /**
     * Opens a chat window for the chat room.
     *
     * @param chatRoomWrapper the chat room.
     */
    override fun openChatRoom(chatRoomWrapper: ChatRoomWrapper?) {
        var iChatRoomWrapper = chatRoomWrapper
        if (iChatRoomWrapper!!.chatRoom == null) {
            iChatRoomWrapper = createChatRoom(iChatRoomWrapper, "", false, false, true)

            // leave the chatRoom because getChatRoom().isJoined() returns true otherwise
            if (iChatRoomWrapper.chatRoom!!.isJoined()) iChatRoomWrapper.chatRoom!!.leave()
        }
        if (!iChatRoomWrapper.chatRoom!!.isJoined()) {
            val savedNick = iChatRoomWrapper.nickName
            val subject: String? = null
            if (savedNick == null) {
                // String[] joinOptions = ChatRoomJoinOptionsDialog.getJoinOptions(room.getProtocolProvider(),
                // room.getChatRoomID(), MUCActivator.getGlobalDisplayDetailsService()
                // .getDisplayName(room.getParentProvider().getProtocolProvider()));
                // savedNick = joinOptions[0];
                // subject = joinOptions[1];
            }
            if (savedNick != null) {
                joinChatRoom(iChatRoomWrapper, savedNick, null, subject)
            } else return
        }
        MUCActivator.uIService!!.openChatRoomWindow(iChatRoomWrapper)
    }

    /**
     * Returns instance of the `ServerChatRoomContactSourceService` contact source.
     *
     * @return instance of the `ServerChatRoomContactSourceService` contact source.
     */
    override fun getServerChatRoomsContactSourceForProvider(pps: ChatRoomProviderWrapper?): ContactSourceService {
        return ServerChatRoomContactSourceService(pps)
    }

    /**
     * Returns `true` if the contact is `ChatRoomSourceContact`
     *
     * @param contact the contact
     * @return `true` if the contact is `ChatRoomSourceContact`
     */
    override fun isMUCSourceContact(contact: SourceContact): Boolean {
        return contact is ChatRoomSourceContact
    }
}