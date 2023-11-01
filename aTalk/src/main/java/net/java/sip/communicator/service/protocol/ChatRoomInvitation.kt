/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import org.jxmpp.jid.EntityJid

/**
 * This interface represents an invitation, which is send from a chat room member to another user in
 * order to invite this user to join the chat room.
 *
 * @author Emil Ivov
 * @author Stephane Remy
 * @author Eng Chong Meng
 */
interface ChatRoomInvitation {
    /**
     * Returns the `ChatRoom`, which is the target of this invitation. The chat room returned
     * by this method will be the room to which the user is invited to join to.
     *
     * @return the `ChatRoom`, which is the target of this invitation
     */
    fun getTargetChatRoom(): ChatRoom

    /**
     * Returns the password to use when joining the room.
     *
     * @return the password to use when joining the room
     */
    fun getChatRoomPassword(): ByteArray?

    /**
     * Returns the `ChatRoomMember` that sent this invitation.
     *
     * @return the `ChatRoomMember` that sent this invitation.
     */
    fun getInviter(): EntityJid

    /**
     * Returns the reason of this invitation, or null if there is no reason.
     *
     * @return the reason of this invitation, or null if there is no reason
     */
    fun getReason(): String?
}