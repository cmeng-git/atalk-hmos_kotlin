/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ContactResource
import net.java.sip.communicator.service.protocol.IMessage
import org.atalk.hmos.gui.chat.ChatMessage
import org.atalk.persistance.FileBackend
import java.util.*

/**
 * `MessageReceivedEvent`s indicate reception of an instant message.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class MessageReceivedEvent(source: IMessage, contact: Contact, contactResource: ContactResource?,
                           sender: String, timestamp: Date, isPrivateMessaging: Boolean, privateContactRoom: ChatRoom?) : EventObject(source) {
    /**
     * The contact that has sent this message.
     */
    private val mContact: Contact

    /**
     * The `ContactResource`, from which the message was sent.
     */
    private val mContactResource: ContactResource?

    /**
     * Message sender full jid
     */
    private val mSender: String

    /**
     * A timestamp indicating the exact date when the event occurred.
     */
    private val mTimestamp: Date

    /**
     * The type of message event that this instance represents.
     */
    private val mEventType: Int

    /**
     * The ID of the message being corrected, or null if this is a new message and not a correction.
     */
    private var correctedMessageUID: String? = null

    /**
     * Indicates whether this is private messaging event or not.
     */
    private val isPrivateMessaging: Boolean

    /**
     * The room associated with the contact which sent the message.
     */
    private val privateMessagingContactRoom: ChatRoom?

    /**
     * Creates a `MessageReceivedEvent` representing reception of the `source` message
     * received from the specified `from` contact.
     *
     * source the `IMessage` whose reception this event represents.
     * contact the `Contact` that has sent this message.
     * contactResource the `ContactResource`, from which this message was sent.
     * sender the fullJid from which this message was sent
     * timestamp the exact date when the event occurred.
     * correctedMessageUID The ID of the message being corrected, or null if this
     * is a new message and not a correction.
     */
    constructor(source: IMessage, contact: Contact, contactResource: ContactResource?,
                sender: String, timestamp: Date, correctedMessageUID: String?) : this(source, contact, contactResource, sender, timestamp, false, null) {
        this.correctedMessageUID = correctedMessageUID
    }

    /**
     * Creates a `MessageReceivedEvent` representing reception of the `source` message
     * received from the specified `from` contact.
     *
     * source the `IMessage` whose reception this event represents.
     * contact the `Contact` that has sent this message.
     * contactResource the `ContactResource`, from which this message was sent
     * sender the fullJid from which this message was sent
     * timestamp the exact date when the event occurred.
     * correctedMessageUID The ID of the message being corrected, or null if this is a new message and not a
     * correction.
     * isPrivateMessaging indicates whether the this is private messaging event or not.
     * privateContactRoom the chat room associated with the contact.
     */
    constructor(source: IMessage, contact: Contact, contactResource: ContactResource,
                sender: String, timestamp: Date, correctedMessageUID: String?, isPrivateMessaging: Boolean,
                privateContactRoom: ChatRoom?) : this(source, contact, contactResource, sender, timestamp, isPrivateMessaging, privateContactRoom) {
        this.correctedMessageUID = correctedMessageUID
    }

    /**
     * Creates a `MessageReceivedEvent` representing reception of the `source` message
     * received from the specified `from` contact.
     *
     * source the `IMessage` whose reception this event represents.
     * contact the `Contact` that has sent this message.
     * contactResource the `ContactResource`, from which this message was sent
     * sender the fullJid from which this message was sent
     * timestamp the exact date when the event occurred.
     * eventType the type of message event that this instance represents (one of the
     * XXX_MESSAGE_RECEIVED static fields).
     * isPrivateMessaging indicates whether the this is private messaging event or not.
     * privateContactRoom the chat room associated with the contact.
     */
    init {

        // Use MESSAGE_HTTP_FILE_DOWNLOAD if it is http download link
        // source.getContent() may be null (Omemo key message contains no body content)
        mEventType = if (FileBackend.isHttpFileDnLink(source.getContent())) ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD else ChatMessage.MESSAGE_IN
        mContact = contact
        mContactResource = contactResource
        mSender = sender
        mTimestamp = timestamp
        this.isPrivateMessaging = isPrivateMessaging
        privateMessagingContactRoom = privateContactRoom
    }

    /**
     * Returns a reference to the `Contact` that has sent the `IMessage` whose
     * reception this event represents.
     *
     * @return a reference to the `Contact` that has sent the `IMessage` whose
     * reception this event represents.
     */
    fun getSourceContact(): Contact {
        return mContact
    }

    /**
     * Returns a reference to the `ContactResource` that has sent the `IMessage` whose
     * reception this event represents.
     *
     * @return a reference to the `ContactResource` that has sent the `IMessage` whose
     * reception this event represents.
     */
    fun getContactResource(): ContactResource? {
        return mContactResource
    }

    /**
     * Get the message sender fullJid
     *
     * @return sender fullJid
     */
    fun getSender(): String {
        return mSender
    }

    /**
     * Returns the message that triggered this event
     *
     * @return the `IMessage` that triggered this event.
     */
    fun getSourceMessage(): IMessage {
        return getSource() as IMessage
    }

    /**
     * A timestamp indicating the exact date when the event occurred.
     *
     * @return a Date indicating when the event occurred.
     */
    fun getTimestamp(): Date {
        return mTimestamp
    }

    /**
     * Returns the type of message event represented by this event instance. IMessage event type is
     * one of the XXX_MESSAGE_RECEIVED fields of this class.
     *
     * @return one of the XXX_MESSAGE_RECEIVED fields of this class indicating the type of this event.
     */
    fun getEventType(): Int {
        return mEventType
    }

    /**
     * Returns the correctedMessageUID The ID of the message being corrected, or null if this is a
     * new message and not a correction.
     *
     * @return the correctedMessageUID The ID of the message being corrected, or null if this is a
     * new message and not a correction.
     */
    fun getCorrectedMessageUID(): String? {
        return correctedMessageUID
    }

    /**
     * Returns the chat room of the private messaging contact associated with the event and null if
     * the contact is not private messaging contact.
     *
     * @return the chat room associated with the contact or null if no chat room is associated with
     * the contact.
     */
    fun getPrivateMessagingContactRoom(): ChatRoom? {
        return privateMessagingContactRoom
    }

    /**
     * Returns `true if this is private messaging event and `false` if not.
     *
     * @return `true if this is private messaging event and `false` if not.
    `` */
    fun isPrivateMessaging(): Boolean {
        return isPrivateMessaging
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}