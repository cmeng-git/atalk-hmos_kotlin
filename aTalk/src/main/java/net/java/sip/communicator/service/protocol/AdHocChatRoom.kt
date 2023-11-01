/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageListener
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomParticipantPresenceListener
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jxmpp.jid.EntityBareJid

/**
 * Represents an ad-hoc rendezvous point where multiple chat users could communicate together. This
 * interface describes the main methods used by some protocols for multi user chat, without useless
 * methods (such as kicking a participant) which aren't supported by these protocols (MSN, ICQ etc.).
 *
 *
 * `AdHocChatRoom` acts like a simplified `ChatRoom`.
 *
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
interface AdHocChatRoom {
    /**
     * Returns the name of this `AdHocChatRoom`. The name can't be changed until the
     * `AdHocChatRoom` is ended.
     *
     * @return a `String` containing the name
     */
    fun getName(): String

    /**
     * Returns the identifier of this `AdHocChatRoom`. The identifier of the ad-hoc chat
     * room would have the following syntax: adHocChatRoomName@adHocChatRoomServer@accountID
     *
     * @return a `String` containing the identifier of this `AdHocChatRoom`.
     */
    fun getIdentifier(): String

    /**
     * Adds a listener that will be notified of changes in our participation in the ad-hoc room
     * such as us being join, left...
     *
     * @param listener a member participation listener.
     */
    fun addParticipantPresenceListener(listener: AdHocChatRoomParticipantPresenceListener?)

    /**
     * Removes a participant presence listener.
     *
     * @param listener a member participation listener.
     */
    fun removeParticipantPresenceListener(listener: AdHocChatRoomParticipantPresenceListener?)

    /**
     * Registers `listener` so that it would receive events every time a new message is
     * received on this ad-hoc chat room.
     *
     * @param listener a `MessageListener` that would be notified every time a new message is received
     * on this ad-hoc chat room.
     */
    fun addMessageListener(listener: AdHocChatRoomMessageListener?)

    /**
     * Removes `listener` so that it won't receive any further message events from this
     * ad-hoc room.
     *
     * @param listener the `MessageListener` to remove from this ad-hoc room
     */
    fun removeMessageListener(listener: AdHocChatRoomMessageListener?)

    /**
     * Invites another `Contact` to this ad-hoc chat room.
     *
     * @param userAddress the address of the `Contact` of the user to invite to the ad-hoc room.
     * @param reason a reason, subject, or welcome message that would tell users why they are being invited.
     */
    fun invite(userAddress: EntityBareJid?, reason: String?)

    /**
     * Returns a `List` of `Contact`s corresponding to all participants currently
     * participating in this room.
     *
     * @return a `List` of `Contact`s instances corresponding to all room members.
     */
    fun getParticipants(): List<Contact?>?

    /**
     * Returns the number of participants that are currently in this ad-hoc chat room.
     *
     * @return int the number of `Contact`s, currently participating in this ad-hoc room.
     */
    fun getParticipantsCount(): Int

    /**
     * Create a `IMessage` instance for sending a simple text messages with default
     * (text/plain) content type and encoding.
     *
     * @param messageText the string content of the message.
     * @return IMessage the newly created message
     */
    fun createMessage(messageText: String?): IMessage?

    /**
     * Create a IMessage instance for sending arbitrary MIME-encoding content.
     *
     * @param content content value
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     * @param subject a `String` subject or `null` for now subject.
     * @return the newly created message.
     */
    fun createMessage(content: String?, encType: Int, subject: String?): IMessage?

    /**
     * Sends the `IMessage` to this ad-hoc chat room.
     *
     * @param message the `IMessage` to send.
     */
    fun sendMessage(message: IMessage?)
    fun sendMessage(message: IMessage?, omemoManager: OmemoManager?)

    /**
     * Returns a reference to the provider that created this room.
     *
     * @return a reference to the `ProtocolProviderService` instance that created this ad-hoc room.
     */
    fun getParentProvider(): ProtocolProviderService

    /**
     * Joins this ad-hoc chat room with the nickname of the local user so that the user would start
     * receiving events and messages for it.
     *
     * @throws OperationFailedException with the corresponding code if an error occurs while joining the ad-hoc room.
     */
    @Throws(OperationFailedException::class)
    fun join()

    /**
     * Leaves this chat room. Once this method is called, the user won't be listed as a member of
     * the chat room any more and no further chat events will be delivered.
     */
    fun leave()
}