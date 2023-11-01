/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chat

import net.java.sip.communicator.service.protocol.ConferenceDescription
import javax.swing.Icon

/**
 * The `ChatSessionRenderer` is the connector between the
 * `ChatSession` and the `ChatPanel`, which represents the UI
 * part of the chat.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface ChatSessionRenderer {
    /**
     * Sets the name of the given chat contact.
     *
     * @param chatContact the chat contact to be modified.
     * @param name the new name.
     */
    fun setContactName(chatContact: ChatContact<*>?, name: String?)

    /**
     * Adds the given chat transport to the UI.
     *
     * @param chatTransport the chat transport to add.
     */
    fun addChatTransport(chatTransport: ChatTransport?)

    /**
     * Removes the given chat transport from the UI.
     *
     * @param chatTransport the chat transport to remove.
     */
    fun removeChatTransport(chatTransport: ChatTransport?)

    /**
     * Adds the given chat contact to the UI.
     *
     * @param chatContact the chat contact to add.
     */
    fun addChatContact(chatContact: ChatContact<*>?)

    /**
     * Removes the given chat contact from the UI.
     *
     * @param chatContact the chat contact to remove.
     */
    fun removeChatContact(chatContact: ChatContact<*>?)

    /**
     * Removes all chat contacts from the contact list of the chat.
     */
    fun removeAllChatContacts()

    /**
     * Updates the status of the given chat transport.
     *
     * @param chatTransport the chat transport to update.
     */
    fun updateChatTransportStatus(chatTransport: ChatTransport?)

    /**
     * Sets the given `chatTransport` to be the selected chat transport.
     *
     * @param chatTransport the `ChatTransport` to select
     * @param isMessageOrFileTransferReceived Boolean telling us if this change
     * of the chat transport correspond to an effective switch to this new
     * transform (a mesaage received from this transport, or a file transfer
     * request received, or if the resource timeouted), or just a status update
     * telling us a new chatTransport is now available (i.e. another device has startup).
     */
    fun setSelectedChatTransport(chatTransport: ChatTransport?, isMessageOrFileTransferReceived: Boolean)

    /**
     * Updates the status of the given chat contact.
     *
     * @param chatContact the chat contact to update.
     * @param statusMessage the status message to show to the user.
     */
    fun updateChatContactStatus(chatContact: ChatContact<*>?, statusMessage: String?)

    /**
     * Sets the chat subject.
     *
     * @param subject the new subject to set.
     */
    fun setChatSubject(subject: String?)

    /**
     * Sets the chat icon.
     *
     * @param icon the chat icon to set
     */
    fun setChatIcon(icon: Icon?)

    /**
     * Adds the given `conferenceDescription` to the list of chat
     * conferences in this chat renderer.
     * @param conferenceDescription the conference to add.
     */
    fun addChatConferenceCall(conferenceDescription: ConferenceDescription?)

    /**
     * Removes the given `conferenceDescription` from the list of chat
     * conferences in this chat panel chat.
     * @param conferenceDescription the conference to remove.
     */
    fun removeChatConferenceCall(conferenceDescription: ConferenceDescription?)

    /**
     * Sets the visibility of conferences panel to `true` or `false`
     *
     * @param isVisible if `true` the panel is visible.
     */
    fun setConferencesPanelVisible(isVisible: Boolean)

    /**
     * This method is called when the local user publishes a  `ConferenceDescription` instance.
     *
     * @param conferenceDescription the `ConferenceDescription` instance
     * associated with the conference.
     */
    fun chatConferenceDescriptionSent(conferenceDescription: ConferenceDescription?)
}