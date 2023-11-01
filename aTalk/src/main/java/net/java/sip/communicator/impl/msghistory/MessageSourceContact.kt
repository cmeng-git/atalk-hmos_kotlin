/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.msghistory

import net.java.sip.communicator.service.contactsource.ContactDetail
import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.contactsource.SourceContact
import net.java.sip.communicator.service.msghistory.MessageSourceContactPresenceStatus
import net.java.sip.communicator.service.muc.ChatRoomPresenceStatus
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.OperationNotSupportedException
import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging
import net.java.sip.communicator.service.protocol.OperationSetContactCapabilities
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageReceivedEvent
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum
import net.java.sip.communicator.util.DataObject
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Represents a contact source displaying a recent message for contact.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class MessageSourceContact internal constructor(source: EventObject?,
        /**
         * The parent service.
         */
        private val service: MessageSourceService) : DataObject(), SourceContact, Comparable<MessageSourceContact?> {
    /**
     * A list of all contact details.
     */
    override val contactDetails = LinkedList<ContactDetail>()

    /**
     * The address.
     */
    override var contactAddress: String? = null
        get() = if (field != null) field else null

    override val contactSource: ContactSourceService
        get() = service


    /**
     * The display name.
     */
    override var displayName: String? = null
        get() = if (field != null) field else aTalkApp.getResString(R.string.service_gui_UNKNOWN_USER)

    /**
     * The protocol provider.
     *
     * @return the protocol provider.
     */
    /**
     * The protocol provider.
     */
    var protocolProviderService: ProtocolProviderService? = null
        private set

    /**
     * The status. Will reuse global status offline.
     */
    override var presenceStatus: PresenceStatus = GlobalStatusEnum.OFFLINE
        private set

    /**
     * The image.
     */
    override var image: ByteArray? = null
        private set

    /**
     * The message content.
     */
    override var displayDetails: String? = null
        private set
    /**
     * The contact.
     *
     * @return the contact.
     */
    /**
     * The contact instance.
     */
    var contact: Contact? = null
        private set
    /**
     * The room.
     *
     * @return the room.
     */
    /**
     * The room instance.
     */
    var room: ChatRoom? = null
        private set
    /**
     * The timestamp of the message.
     *
     * @return the timestamp of the message.
     */
    /**
     * The timestamp.
     */
    var timestamp: Date? = null
        private set

    /**
     * Constructs `MessageSourceContact`.
     */
    init {
        update(source)
    }

    /**
     * Make sure the content of the message is not too long, as it will fill up tooltips and ui components.
     */
    private fun updateMessageContent() {
        displayDetails = if (isToday(timestamp!!)) {
            // just hour
            (SimpleDateFormat(TODAY_DATE_FORMAT, Locale.US).format(timestamp!!)
                    + displayDetails)
        } else {
            // just date
            (SimpleDateFormat(PAST_DATE_FORMAT, Locale.US).format(timestamp!!)
                    + displayDetails)
        }
        if (displayDetails!!.length > 60) {
            // do not display too long texts
            displayDetails = displayDetails!!.substring(0, 60)
            displayDetails += "..."
        }
    }

    /**
     * Checks whether `timestamp` is today.
     *
     * @param timestamp the date to check
     * @return whether `timestamp` is today.
     */
    private fun isToday(timestamp: Date): Boolean {
        val today = Calendar.getInstance()
        val tsCalendar = Calendar.getInstance()
        tsCalendar.time = timestamp
        return (today[Calendar.YEAR] == tsCalendar[Calendar.YEAR]
                && today[Calendar.DAY_OF_YEAR] == tsCalendar[Calendar.DAY_OF_YEAR])
    }

    /**
     * Updates fields.
     *
     * @param source the event object
     */
    fun update(source: EventObject?) {
        when (source) {
            is MessageDeliveredEvent -> {
                contact = source.getContact()
                contactAddress = contact!!.address
                updateDisplayName()
                protocolProviderService = contact!!.protocolProvider
                image = contact!!.image
                presenceStatus = contact!!.presenceStatus
                displayDetails = source.getSourceMessage().getContent()
                timestamp = source.getTimestamp()
            }
            is MessageReceivedEvent -> {
                val e = (source).also {
                    contact = it.getSourceContact()
                }
                contactAddress = contact!!.address
                updateDisplayName()
                protocolProviderService = contact!!.protocolProvider
                image = contact!!.image
                presenceStatus = contact!!.presenceStatus
                displayDetails = e.getSourceMessage().getContent()
                timestamp = e.getTimestamp()
            }
            is ChatRoomMessageDeliveredEvent -> {
                room = source.getSourceChatRoom()
                contactAddress = room!!.getName()
                displayName = room!!.getName()
                protocolProviderService = room!!.getParentProvider()
                image = null
                presenceStatus = if (room!!.isJoined()) ChatRoomPresenceStatus.CHAT_ROOM_ONLINE else ChatRoomPresenceStatus.CHAT_ROOM_OFFLINE
                displayDetails = source.getMessage()!!.getContent()
                timestamp = source.getTimestamp()
            }
            is ChatRoomMessageReceivedEvent -> {
                room = source.getSourceChatRoom()
                contactAddress = room!!.getName()
                displayName = room!!.getName()
                protocolProviderService = room!!.getParentProvider()
                image = null
                presenceStatus = if (room!!.isJoined()) ChatRoomPresenceStatus.CHAT_ROOM_ONLINE else ChatRoomPresenceStatus.CHAT_ROOM_OFFLINE
                displayDetails = source.getMessage().getContent()
                timestamp = source.getTimestamp()
            }
        }
        if (service.isSMSEnabled) {
            presenceStatus = MessageSourceContactPresenceStatus.MSG_SRC_CONTACT_ONLINE
        }
        updateMessageContent()
    }

    override fun toString(): String {
        return ("MessageSourceContact{" + "address='" + contactAddress
                + '\'' + ", ppService=" + protocolProviderService + '}')
    }

    /**
     * Init details. Check contact capabilities.
     *
     * @param source the source event.
     */
    fun initDetails(source: EventObject?) {
        when (source) {
            is MessageDeliveredEvent -> {
                initDetails(false, source.getContact())
            }
            is MessageReceivedEvent -> {
                initDetails(false, source.getSourceContact())
            }
            is ChatRoomMessageDeliveredEvent, is ChatRoomMessageReceivedEvent -> {
                initDetails(true, null)
            }
        }
    }

    /**
     * We will the details for this source contact. Will skip OperationSetBasicInstantMessaging
     * for chat rooms.
     *
     * @param isChatRoom is current source contact a chat room.
     */
    fun initDetails(isChatRoom: Boolean, contact: Contact?) {
        if (!isChatRoom && contact != null) updateDisplayName()
        val contactDetail = ContactDetail(contactAddress, displayName)
        val preferredProviders: MutableMap<Class<out OperationSet?>, ProtocolProviderService>

        val preferredProvider = protocolProviderService
        if (preferredProvider != null) {
            val capOpSet = preferredProvider.getOperationSet(OperationSetContactCapabilities::class.java)
            var opSetCapabilities: Map<String, OperationSet>? = null
            if (capOpSet != null && contact != null) opSetCapabilities = capOpSet.getSupportedOperationSets(contact)

            preferredProviders = Hashtable()
            val supportedOpSets = LinkedList<Class<out OperationSet?>>()
            for (opset in preferredProvider.getSupportedOperationSetClasses()) {
                // skip opset IM as we want explicitly muc support
                if (opset == OperationSetPresence::class.java || opset == OperationSetPersistentPresence::class.java || (isChatRoom || service.isSMSEnabled) && opset == OperationSetBasicInstantMessaging::class.java) {
                    continue
                }
                if (!isChatRoom && opSetCapabilities != null && !opSetCapabilities.containsKey(opset.name)) continue
                preferredProviders[opset] = preferredProvider
                supportedOpSets.add(opset)
            }
            contactDetail.setPreferredProviders(preferredProviders)
            contactDetail.setSupportedOpSets(supportedOpSets)
        }
        contactDetails.clear()
        contactDetails.add(contactDetail)
    }

    /**
     * Returns a list of all `ContactDetail`s supporting the given `OperationSet`
     * class.
     *
     * @param operationSet the `OperationSet` class we're looking for
     * @return a list of all `ContactDetail`s supporting the given `OperationSet`
     * class
     */
    override fun getContactDetails(operationSet: Class<out OperationSet?>?): List<ContactDetail> {
        val res = LinkedList<ContactDetail>()
        for (det in contactDetails) {
            if (det.getPreferredProtocolProvider(operationSet!!) != null) res.add(det)
        }
        return res
    }

    /**
     * Returns a list of all `ContactDetail`s corresponding to the given category.
     *
     * @param category the `OperationSet` class we're looking for
     * @return a list of all `ContactDetail`s corresponding to the given category
     */
    @Throws(OperationNotSupportedException::class)
    override fun getContactDetails(category: ContactDetail.Category): List<ContactDetail> {
        // We don't support category for message source history details,
        // so we return null.
        throw OperationNotSupportedException(
                "Categories are not supported for message source contact history.")
    }

    /**
     * Returns the preferred `ContactDetail` for a given `OperationSet` class.
     *
     * @param operationSet the `OperationSet` class, for which we would like to obtain a
     * `ContactDetail`
     * @return the preferred `ContactDetail` for a given `OperationSet` class
     */
    override fun getPreferredContactDetail(operationSet: Class<out OperationSet?>?): ContactDetail {
        return contactDetails[0]
    }

    override val isDefaultImage: Boolean
        get() = image == null

    /**
     * Sets current status.
     *
     * @param status
     */
    fun setStatus(status: PresenceStatus) {
        presenceStatus = status
    }

    override val index: Int
        get() = service.getIndex(this)

    /**
     * Updates display name if contact is not null.
     */
    private fun updateDisplayName() {
        if (contact == null) return
        val metaContact = MessageHistoryActivator.contactListService!!.findMetaContactByContact(contact) ?: return
        displayName = metaContact.getDisplayName()
    }

    /**
     * Compares two MessageSourceContacts.
     *
     * @param other the object to compare with
     * @return 0, less than zero, greater than zero, if equals, less or greater.
     */
    override fun compareTo(other: MessageSourceContact?): Int {
        return if (other?.timestamp == null) 1 else other.timestamp!!.compareTo(timestamp)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as MessageSourceContact
        if (contactAddress != that.contactAddress) return false
        return protocolProviderService == that.protocolProviderService
    }

    override fun hashCode(): Int {
        var result = contactAddress.hashCode()
        result = 31 * result + protocolProviderService.hashCode()
        return result
    }

    companion object {
        /**
         * Date format used to mark today messages.
         */
        const val TODAY_DATE_FORMAT = "HH:mm', '"

        /**
         * Date format used to mark past messages.
         */
        const val PAST_DATE_FORMAT = "MMM d', '"
    }
}