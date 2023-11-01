/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.ChatRoomInvitationListener
import net.java.sip.communicator.service.protocol.event.ChatRoomInvitationReceivedEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomInvitationRejectedEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomInvitationRejectionListener
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceChangeEvent
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceListener
import org.jxmpp.jid.EntityBareJid
import java.util.*

/**
 * Represents a default implementation of `OperationSetMultiUserChat` in order to make it
 * easier for implementers to provide complete solutions while focusing on implementation-specific details.
 *
 * @author Lubomir Marinov
 */
abstract class AbstractOperationSetMultiUserChat : OperationSetMultiUserChat {
    /**
     * The list of the currently registered `ChatRoomInvitationListener`s.
     */
    private val invitationListeners = Vector<ChatRoomInvitationListener>()

    /**
     * The list of `ChatRoomInvitationRejectionListener`s subscribed for events
     * indicating rejection of a multi user chat invitation sent by us.
     */
    private val invitationRejectionListeners = Vector<ChatRoomInvitationRejectionListener>()

    /**
     * Listeners that will be notified of changes in our status in the room such as us being kicked,
     * banned, or granted admin permissions.
     */
    private val presenceListeners = Vector<LocalUserChatRoomPresenceListener>()

    /*
     * Implements OperationSetMultiUserChat#addInvitationListener( ChatRoomInvitationListener).
     */
    override fun addInvitationListener(listener: ChatRoomInvitationListener) {
        synchronized(invitationListeners) { if (!invitationListeners.contains(listener)) invitationListeners.add(listener) }
    }

    /*
     * ImplementsOperationSetMultiUserChat
     * #addInvitationRejectionListener(ChatRoomInvitationRejectionListener).
     */
    override fun addInvitationRejectionListener(listener: ChatRoomInvitationRejectionListener) {
        synchronized(invitationRejectionListeners) { if (!invitationRejectionListeners.contains(listener)) invitationRejectionListeners.add(listener) }
    }

    /*
     * Implements OperationSetMultiUserChat#addPresenceListener( LocalUserChatRoomPresenceListener).
     */
    override fun addPresenceListener(listener: LocalUserChatRoomPresenceListener) {
        synchronized(presenceListeners) { if (!presenceListeners.contains(listener)) presenceListeners.add(listener) }
    }

    /**
     * Fires a new `ChatRoomInvitationReceivedEvent` to all currently registered
     * `ChatRoomInvitationListener`s to notify about the receipt of a specific
     * `ChatRoomInvitation`.
     *
     * @param invitation the `ChatRoomInvitation` which has been received
     */
    protected fun fireInvitationReceived(invitation: ChatRoomInvitation) {
        val evt = ChatRoomInvitationReceivedEvent(this, invitation,
                Date(System.currentTimeMillis()))
        var listeners: Array<ChatRoomInvitationListener>
        synchronized(invitationListeners) { listeners = invitationListeners.toTypedArray() }
        for (listener in listeners) listener.invitationReceived(evt)
    }

    /**
     * Delivers a `ChatRoomInvitationRejectedEvent` to all registered
     * `ChatRoomInvitationRejectionListener`s.
     *
     * @param sourceChatRoom the room that invitation refers to
     * @param invitee the name of the invitee that rejected the invitation
     * @param reason the reason of the rejection
     */
    protected fun fireInvitationRejectedEvent(sourceChatRoom: ChatRoom, invitee: EntityBareJid, reason: String) {
        val evt = ChatRoomInvitationRejectedEvent(this,
                sourceChatRoom, invitee, reason, Date(System.currentTimeMillis()))
        var listeners: Array<ChatRoomInvitationRejectionListener>
        synchronized(invitationRejectionListeners) { listeners = invitationRejectionListeners.toTypedArray() }
        for (listener in listeners) listener.invitationRejected(evt)
    }
    /**
     * Delivers a `LocalUserChatRoomPresenceChangeEvent` to all registered
     * `LocalUserChatRoomPresenceListener`s.
     *
     * chatRoom the `ChatRoom` which has been joined, left, etc.
     * eventType the type of this event; one of LOCAL_USER_JOINED, LOCAL_USER_LEFT, etc.
     * reason the reason
     * alternateAddress address of the new room, if old is destroyed.
     */
    /**
     * Delivers a `LocalUserChatRoomPresenceChangeEvent` to all registered
     * `LocalUserChatRoomPresenceListener`s.
     *
     * @param chatRoom the `ChatRoom` which has been joined, left, etc.
     * @param eventType the type of this event; one of LOCAL_USER_JOINED, LOCAL_USER_LEFT, etc.
     * @param reason the reason
     */
    @JvmOverloads
    fun fireLocalUserPresenceEvent(chatRoom: ChatRoom, eventType: String, reason: String?, alternateAddress: String? = null) {
        val evt = LocalUserChatRoomPresenceChangeEvent(this,
                chatRoom, eventType, reason, alternateAddress)
        var listeners: Array<LocalUserChatRoomPresenceListener>
        synchronized(presenceListeners) { listeners = presenceListeners.toTypedArray() }
        for (listener in listeners) listener.localUserPresenceChanged(evt)
    }

    /*
     * Implements OperationSetMultiUserChat#removeInvitationListener( ChatRoomInvitationListener).
     */
    override fun removeInvitationListener(listener: ChatRoomInvitationListener) {
        synchronized(invitationListeners) { invitationListeners.remove(listener) }
    }

    /*
     * Implements OperationSetMultiUserChat#removeInvitationRejectionListener(ChatRoomInvitationRejectionListener).
     */
    override fun removeInvitationRejectionListener(listener: ChatRoomInvitationRejectionListener) {
        synchronized(invitationRejectionListeners) { invitationRejectionListeners.remove(listener) }
    }

    /*
     * Implements OperationSetMultiUserChat#removePresenceListener(LocalUserChatRoomPresenceListener).
     */
    override fun removePresenceListener(listener: LocalUserChatRoomPresenceListener) {
        synchronized(presenceListeners) { presenceListeners.remove(listener) }
    }
}