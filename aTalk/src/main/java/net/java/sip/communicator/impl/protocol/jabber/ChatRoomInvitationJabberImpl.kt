/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.ChatRoomInvitation
import org.jxmpp.jid.EntityJid

/**
 * The Jabber implementation of the `ChatRoomInvitation` interface.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class ChatRoomInvitationJabberImpl
/**
 * Creates an invitation for the given `targetChatRoom`, from the given `inviter`.
 *
 * @param targetChatRoom the `ChatRoom` for which the invitation is
 * @param inviter the `ChatRoomMember`, which sent the invitation
 * @param reason the reason of the invitation
 * @param password the password
 */
(private val chatRoom: ChatRoom, private val inviter: EntityJid, private val reason: String, private val password: ByteArray) : ChatRoomInvitation {
    override fun getTargetChatRoom(): ChatRoom {
        return chatRoom
    }

    override fun getInviter(): EntityJid {
        return inviter
    }

    override fun getReason(): String {
        return reason
    }

    override fun getChatRoomPassword(): ByteArray {
        return password
    }
}