/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ContactResource
import net.java.sip.communicator.service.protocol.IMessage
import org.atalk.hmos.gui.chat.ChatMessage
import java.util.*

/**
 * `MessageDeliveredEvent`s confirm successful delivery of an instant message.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class MessageDeliveredEvent(source: IMessage?, contact: Contact, contactResource: ContactResource?, sender: String?, timestamp: Date) : EventObject(source) {
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
    private val mSender: String?

    /**
     * A timestamp indicating the exact date when the event occurred.
     */
    private val mTimestamp: Date

    /**
     * The ID of the message being corrected, or null if this was a new message and not a message correction.
     */
    private var correctedMessageUID: String? = null

    /**
     * Whether the delivered message is a sms message.
     */
    private var smsMessage = false

    /**
     * Whether the delivered message is encrypted or not.
     */
    private var isMessageEncrypted = false

    /**
     * Creates a `MessageDeliveredEvent` representing delivery of the `source`
     * message to the specified `to` contact.
     *
     * @param source the `IMessage` whose delivery this event represents.
     * @param contact the `Contact` that this message was sent to.
     * @param correctedMessageUID The ID of the message being corrected.
     */
    constructor(source: IMessage?, contact: Contact, contactResource: ContactResource?,
                sender: String?, correctedMessageUID: String?) : this(source, contact, contactResource, sender, Date()) {
        this.correctedMessageUID = correctedMessageUID
    }

    /**
     * Creates a `MessageDeliveredEvent` representing delivery of the `source`
     * message to the specified `to` contact.
     *
     * @param source the `IMessage` whose delivery this event represents.
     * @param contact the `Contact` that this message was sent to.
     * @param contactResource the `Contact` resource that this message was sent to
     * @param sender the fullJid from which this message was sent
     * @param timestamp a date indicating the exact moment when the event occurred
     */
    init {
        mContact = contact
        mContactResource = contactResource
        mSender = sender
        mTimestamp = timestamp
    }

    /**
     * Returns a reference to the `Contact` that `IMessage` was sent to.
     *
     * @return a reference to the `Contact` that has send the `IMessage`
     * whose reception this event represents.
     */
    fun getContact(): Contact {
        return mContact
    }

    /**
     * Returns a reference to the `ContactResource` that has sent the `IMessage`
     * whose reception this event represents.
     *
     * @return a reference to the `ContactResource` that has sent the `IMessage`
     * whose reception this event represents.
     */
    fun getContactResource(): ContactResource? {
        return mContactResource
    }

    /**
     * Get the message sender fullJid
     *
     * @return sender fullJid
     */
    fun getSender(): String? {
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
     * Returns the type of message event represented by this event instance.
     *
     * @return one of the XXX_MESSAGE_DELIVERED fields of this class indicating the type of this event.
     */
    fun getEventType(): Int {
        return if (isSmsMessage()) ChatMessage.MESSAGE_SMS_OUT else ChatMessage.MESSAGE_OUT
    }

    /**
     * Returns the ID of the message being corrected, or null if this was a new message and not a
     * message correction.
     *
     * @return the ID of the message being corrected, or null if this was a new message and not a
     * message correction.
     */
    fun getCorrectedMessageUID(): String? {
        return correctedMessageUID
    }

    /**
     * Sets the ID of the message being corrected to the passed ID.
     *
     * @param correctedMessageUID The ID of the message being corrected.
     */
    fun setCorrectedMessageUID(correctedMessageUID: String?) {
        this.correctedMessageUID = correctedMessageUID
    }

    /**
     * Sets whether the message is a sms one.
     *
     * @param smsMessage whether it is a sms one.
     */
    fun setSmsMessage(smsMessage: Boolean) {
        this.smsMessage = smsMessage
    }

    /**
     * Returns whether the delivered message is a sms one.
     *
     * @return whether the delivered message is a sms one.
     */
    fun isSmsMessage(): Boolean {
        return smsMessage
    }

    /**
     * Returns `true` if the message is encrypted and `false` if not.
     *
     * @return `true` if the message is encrypted and `false` if not.
     */
    fun isMessageEncrypted(): Boolean {
        return isMessageEncrypted
    }

    /**
     * Sets the message encrypted flag of the event.
     *
     * @param isMessageEncrypted the value to be set.
     */
    fun setMessageEncrypted(isMessageEncrypted: Boolean) {
        this.isMessageEncrypted = isMessageEncrypted
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}