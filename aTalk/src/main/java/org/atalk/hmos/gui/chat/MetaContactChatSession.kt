/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chat

import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.MetaContactListService
import net.java.sip.communicator.service.contactlist.event.MetaContactAvatarUpdateEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactGroupEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactListListener
import net.java.sip.communicator.service.contactlist.event.MetaContactModifiedEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactMovedEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactRenamedEvent
import net.java.sip.communicator.service.contactlist.event.ProtoContactEvent
import net.java.sip.communicator.service.filehistory.FileRecord
import net.java.sip.communicator.service.protocol.ChatRoomMember
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ContactResource
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging
import net.java.sip.communicator.service.protocol.event.ContactResourceEvent
import net.java.sip.communicator.service.protocol.event.ContactResourceListener
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent
import net.java.sip.communicator.util.ConfigurationUtils.getChatHistorySize
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.json.JSONException
import java.util.*

/**
 * An implementation of the `ChatSession` interface that represents a user-to-user chat session.
 *
 * @author Yana Stamcheva
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
/**
 * `ChatSessionRenderer` that provides the connection between this chat session and its UI.
 */
class MetaContactChatSession(
        /**
         * The object used for rendering.
         */
        override val chatSessionRenderer: ChatPanel,

        private val metaContact: MetaContact,

        protocolContact: Contact,
        contactResource: ContactResource?,
) : ChatSession(), MetaContactListListener, ContactResourceListener {

    private val metaContactListService: MetaContactListService?


    /**
     * Creates an instance of `MetaContactChatSession` by specifying the
     * renderer, which gives the connection with the UI, the meta contact
     * corresponding to the session and the protocol contact to be used as transport.
     *
     * chatPanel the renderer, which gives the connection with the UI.
     * metaContact the meta contact corresponding to the session and the protocol contact.
     * protocolContact the protocol contact to be used as transport.
     * contactResource the specific resource to be used as transport
     */
    init {
        persistableAddress = protocolContact.getPersistableAddress()
        val chatContact = MetaContactChatContact(metaContact)
        chatParticipants.add(chatContact)
        initChatTransports(protocolContact, contactResource)

        // Obtain the MetaContactListService and add this class to it as a
        // listener of all events concerning the contact list.
        metaContactListService = AndroidGUIActivator.contactListService
        metaContactListService.addMetaContactListListener(this)
    }

    /**
     * Returns the entityBareJid of the `MetaContact`
     *
     * @return the entityBareJid of this chat
     */
    override val chatEntity: String
        get() {
            var entityJid = metaContact.getDefaultContact().address
            if (StringUtils.isEmpty(entityJid)) entityJid = aTalkApp.getResString(R.string.service_gui_UNKNOWN)
            return entityJid
        }

    /**
     * Returns a collection of the last N number of messages given by count.
     *
     * count The number of messages from history to return.
     *
     * @return a collection of the last N number of messages given by count.
     */
    override fun getHistory(count: Int): Collection<Any>? {
        val metaHistory = AndroidGUIActivator.metaHistoryService ?: return null

        // If the MetaHistoryService is not registered we have nothing to do here. The history
        // could be "disabled" from the user through one of the configuration forms.
        return metaHistory.findLast(chatHistoryFilter, metaContact, getChatHistorySize())
    }

    /**
     * Returns a collection of the last N number of messages given by count.
     *
     * date The date up to which we're looking for messages.
     * count The number of messages from history to return.
     *
     * @return a collection of the last N number of messages given by count.
     */
    override fun getHistoryBeforeDate(date: Date, count: Int): Collection<Any>? {
        val metaHistory = AndroidGUIActivator.metaHistoryService
                ?: return null

        // If the MetaHistoryService is not registered we have nothing to do here. The history
        // could be "disabled" from the user through one of the configuration forms.
        return metaHistory.findLastMessagesBefore(chatHistoryFilter, metaContact, date,
                getChatHistorySize())
    }

    /**
     * Returns a collection of the last N number of messages given by count.
     *
     * date The date from which we're looking for messages.
     * count The number of messages from history to return.
     *
     * @return a collection of the last N number of messages given by count.
     */
    override fun getHistoryAfterDate(date: Date, count: Int): Collection<Any?>? {
        val metaHistory = AndroidGUIActivator.metaHistoryService
                ?: return null

        // If the MetaHistoryService is not registered we have nothing to do here. The history
        // could be "disabled" from the user through one of the configuration forms.
        return metaHistory.findFirstMessagesAfter(chatHistoryFilter, metaContact, date, getChatHistorySize())
    }// If the MetaHistoryService is not registered we have nothing to do here. The history
    // could be "disabled" from the user through one of the configuration forms.
    /**
     * Returns the start date of the history of this chat session.
     *
     * @return the start date of the history of this chat session.
     */
    override val historyStartDate: Date
        get() {
            var startHistoryDate = Date(0)
            val metaHistory = AndroidGUIActivator.metaHistoryService
                    ?: return startHistoryDate

            // If the MetaHistoryService is not registered we have nothing to do here. The history
            // could be "disabled" from the user through one of the configuration forms.
            val firstMessage = metaHistory.findFirstMessagesAfter(chatHistoryFilter, metaContact, Date(0), 1)
            if (firstMessage.isNotEmpty()) {
                val i = firstMessage.iterator()
                when (val evt = i.next()) {
                    is MessageDeliveredEvent -> {
                        startHistoryDate = evt.getTimestamp()
                    }
                    is MessageReceivedEvent -> {
                        startHistoryDate = evt.getTimestamp()
                    }
                    is FileRecord -> {
                        startHistoryDate = evt.date
                    }
                }
            }
            return startHistoryDate
        }// If the MetaHistoryService is not registered we have nothing to do here. The history
    // could be "disabled" from the user through one of the configuration forms.
    /**
     * Returns the end date of the history of this chat session.
     *
     * @return the end date of the history of this chat session.
     */
    override val historyEndDate: Date
        get() {
            var endHistoryDate = Date(0)
            val metaHistory = AndroidGUIActivator.metaHistoryService
                    ?: return endHistoryDate

            // If the MetaHistoryService is not registered we have nothing to do here. The history
            // could be "disabled" from the user through one of the configuration forms.
            val lastMessage = metaHistory.findLastMessagesBefore(
                    chatHistoryFilter, metaContact, Date(Long.MAX_VALUE), 1)
            if (lastMessage.isNotEmpty()) {
                val i1 = lastMessage.iterator()
                when (val evt = i1.next()) {
                    is MessageDeliveredEvent -> {
                        endHistoryDate = evt.getTimestamp()
                    }
                    is MessageReceivedEvent -> {
                        endHistoryDate = evt.getTimestamp()
                    }
                    is FileRecord -> {
                        endHistoryDate = evt.date
                    }
                }
            }
            return endHistoryDate
        }
    /**
     * Returns the default mobile number used to send sms-es in this session.
     *
     * @return the default mobile number used to send sms-es in this session.
     */
    /**
     * Sets the default mobile number used to send sms-es in this session.
     *
     * smsPhoneNumber The default mobile number used to send sms-es in this session.
     */
    override var defaultSmsNumber: String?
        get() {
            val smsNumber: String
            val jsonArray = metaContact.getDetails("mobile")
            if (jsonArray.length() > 0) {
                try {
                    smsNumber = jsonArray.getString(0)
                    return smsNumber
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            return null
        }
        set(smsPhoneNumber) {
            metaContact.addDetail("mobile", smsPhoneNumber!!)
        }

    /**
     * Initializes all chat transports for this chat session.
     *
     * protocolContact the `Contact` which is to be selected into this instance as the current
     * i.e. its `ChatTransport` is to be selected as `currentChatTransport`
     * contactResource the `ContactResource`, which is to be selected into this instance
     * as the current `ChatTransport` if indicated
     */
    private fun initChatTransports(protocolContact: Contact, contactResource: ContactResource?) {
        val protocolContacts = metaContact.getContacts()
        while (protocolContacts.hasNext()) {
            val contact = protocolContacts.next()!!
            addChatTransports(contact, contactResource?.resourceName, contact == protocolContact)
        }
    }

    /**
     * The currently used transport for all operation within this chat session.
     * Note: currentChatTransport == null if pps.isRegistered is false
     */
    override var currentChatTransport: ChatTransport? = null
        set(chatTransport) {
            field = chatTransport
            fireCurrentChatTransportChange()
        }

    override fun childContactsReordered(evt: MetaContactGroupEvent) {}
    override fun metaContactAdded(evt: MetaContactEvent) {}
    override fun metaContactGroupAdded(evt: MetaContactGroupEvent) {}
    override fun metaContactGroupModified(evt: MetaContactGroupEvent) {}
    override fun metaContactGroupRemoved(evt: MetaContactGroupEvent) {}
    override fun metaContactModified(evt: MetaContactModifiedEvent) {}
    override fun metaContactMoved(evt: MetaContactMovedEvent) {}
    override fun metaContactRemoved(evt: MetaContactEvent) {}
    override fun metaContactAvatarUpdated(evt: MetaContactAvatarUpdateEvent) {}

    /**
     * Implements `MetaContactListListener.metaContactRenamed` method.
     * When a meta contact is renamed, updates all related labels in this chat panel.
     * When a meta contact is renamed, updates all related labels in this chat panel.
     *
     * evt the `MetaContactRenamedEvent` that notified us
     */
    override fun metaContactRenamed(evt: MetaContactRenamedEvent) {
        val newName = evt.getNewDisplayName()
        if (evt.getSourceMetaContact() == metaContact) {
            val chatContact = findChatContactByMetaContact(evt.getSourceMetaContact())
            chatSessionRenderer.setContactName(chatContact, newName)
        }
    }

    /**
     * Implements `MetaContactListListener.protoContactAdded` method.
     * When a proto contact is added, updates the "send via" selector box.
     */
    override fun protoContactAdded(evt: ProtoContactEvent) {
        if (evt.getNewParent() == metaContact) {
            addChatTransports(evt.getProtoContact(), null, false)
        }
    }

    /**
     * Implements `MetaContactListListener.protoContactMoved` method.
     * When a proto contact is moved, updates the "send via" selector box.
     *
     * evt the `ProtoContactEvent` that contains information about
     * the old and the new parent of the contact
     */
    override fun protoContactMoved(evt: ProtoContactEvent) {
        if (evt.getOldParent() == metaContact) {
            protoContactRemoved(evt)
        } else if (evt.getNewParent() == metaContact) {
            protoContactAdded(evt)
        }
    }

    /**
     * Implements `MetaContactListListener.protoContactRemoved` method.
     * When a proto contact is removed, updates the "send via" selector box.
     */
    override fun protoContactRemoved(evt: ProtoContactEvent) {
        if (evt.getOldParent() == metaContact) {
            val protoContact = evt.getProtoContact()
            var transports: List<ChatTransport>
            synchronized(chatTransports) { transports = ArrayList(chatTransports) }
            for (chatTransport in transports) {
                if ((chatTransport as MetaContactChatTransport).contact == protoContact) {
                    removeChatTransport(chatTransport)
                }
            }
        }
    }

    /**
     * Returns the `ChatContact` corresponding to the given `MetaContact`.
     *
     * metaContact the `MetaContact` to search for
     *
     * @return the `ChatContact` corresponding to the given `MetaContact`.
     */
    private fun findChatContactByMetaContact(metaContact: MetaContact): ChatContact<*>? {
        for (chatContact in chatParticipants) {
            val chatSourceContact = chatContact.descriptor
            if (chatSourceContact is MetaContact) {
                if (chatSourceContact == metaContact) return chatContact
            } else {
                val metaChatContact = chatSourceContact as ChatRoomMember
                val contact = metaChatContact.getContact()
                val parentMetaContact = AndroidGUIActivator.contactListService.findMetaContactByContact(contact)
                if (parentMetaContact != null && parentMetaContact == metaContact) return chatContact
            }
        }
        return null
    }

    /**
     * Disposes this chat session.
     */
    override fun dispose() {
        metaContactListService?.removeMetaContactListListener(this)
        for (chatTransport in chatTransports) {
            (chatTransport.descriptor as Contact).removeResourceListener(this)
            chatTransport.dispose()
        }
    }

    /**
     * Returns the descriptor of this chat session.
     *
     * @return the descriptor i.e. MetaContact of this chat session.
     */
    override val descriptor: Any
        get() = metaContact

    /**
     * Returns the chat identifier.
     *
     * @return the chat identifier
     */
    override val chatId: String
        get() = metaContact.getMetaUID()

    /**
     * Returns `true` if this contact is persistent, otherwise returns `false`.
     *
     * @return `true` if this contact is persistent, otherwise returns `false`.
     */
    override val isDescriptorPersistent: Boolean
        get() {
            if (metaContact == null)
                return false

            val defaultContact = metaContact.getDefaultContact(OperationSetBasicInstantMessaging::class.java)
                    ?: return false
            var isParentPersist = true
            var isParentResolved = true
            val parent = defaultContact.parentContactGroup
            if (parent != null) {
                isParentPersist = parent.isPersistent()
                isParentResolved = parent.isResolved()
            }
            return (defaultContact.isPersistent
                    || defaultContact.isResolved()
                    || isParentPersist
                    || isParentResolved)
        }

    /**
     * Implements the `ChatPanel.getChatStatusIcon` method.
     *
     * @return the status icon corresponding to this chat room
     */
    override val chatStatusIcon: ByteArray?
        get() {
            if (metaContact == null) {
                return null
            }
            val c = metaContact.getDefaultContact() ?: return null
            val status = c.presenceStatus ?: return null
            return status.statusIcon
        }

    /**
     * Returns the avatar icon of this chat session.
     *
     * @return the avatar icon of this chat session.
     */
    override val chatAvatar: ByteArray?
        get() = metaContact.getAvatar()

    override fun protoContactModified(evt: ProtoContactEvent) {}
    override fun protoContactRenamed(evt: ProtoContactEvent) {}

    /**
     * Implements ChatSession#isContactListSupported().
     */
    override val isContactListSupported: Boolean
        get() = false

    /**
     * Adds all chat transports for the given `contact`.
     *
     * contact the `Contact`, which transports to add
     * resourceName the resource to be pre-selected
     */
    private fun addChatTransports(contact: Contact, resourceName: String?, isSelectedContact: Boolean) {
        var chatTransport: MetaContactChatTransport? = null
        val contactResources = contact.getResources()
        if (contact.isSupportResources && contactResources != null && contactResources.isNotEmpty()) {
            if (contactResources.size > 1) {
                chatTransport = MetaContactChatTransport(this, contact)
                addChatTransport(chatTransport)
            }
            for (resource in contactResources) {
                val resourceTransport = MetaContactChatTransport(
                        this, contact, resource, contact.getResources()!!.size > 1)
                addChatTransport(resourceTransport)
                if (resource!!.resourceName == resourceName || contactResources.size == 1) {
                    chatTransport = resourceTransport
                }
            }
        } else {
            chatTransport = MetaContactChatTransport(this, contact)
            addChatTransport(chatTransport)
        }

        // If this is the selected contact we set it as a selected transport.
        if (isSelectedContact) {
            currentChatTransport = chatTransport
            // sessionRenderer.setSelectedChatTransport(chatTransport, false);
        }

        // If no current transport is set we choose the first online from the list.
        if (currentChatTransport == null) {
            for (ct in chatTransports) {
                if (ct.status != null && ct.status!!.isOnline) {
                    currentChatTransport = ct
                    break
                }
            }

            // if still nothing selected, choose the first one
            if (currentChatTransport == null) currentChatTransport = chatTransports[0]
            // sessionRenderer.setSelectedChatTransport(currentChatTransport, false);
        }
        if (contact.isSupportResources) {
            contact.addResourceListener(this)
        }
    }

    private fun addChatTransport(chatTransport: ChatTransport) {
        synchronized(chatTransports) { chatTransports.add(chatTransport) }
        // sessionRenderer.addChatTransport(chatTransport);
    }

    /**
     * Removes the given `ChatTransport`.
     *
     * chatTransport the `ChatTransport`.
     */
    private fun removeChatTransport(chatTransport: ChatTransport) {
        synchronized(chatTransports) { chatTransports.remove(chatTransport) }
        // sessionRenderer.removeChatTransport(chatTransport);
        chatTransport.dispose()
        if (chatTransport == currentChatTransport) currentChatTransport = null
    }

    /**
     * Removes the given `ChatTransport`.
     *
     * contact the `ChatTransport`.
     */
    private fun removeChatTransports(contact: Contact) {
        var transports: List<ChatTransport>
        synchronized(chatTransports) { transports = ArrayList(chatTransports) }
        for (transport in transports) {
            val metaTransport = transport as MetaContactChatTransport
            if (metaTransport.contact == contact) removeChatTransport(metaTransport)
        }
        contact.removeResourceListener(this)
    }

    /**
     * Updates the chat transports for the given contact.
     *
     * contact the contact, which related transports to update
     */
    private fun updateChatTransports(contact: Contact) {
        if (currentChatTransport != null) {
            val isSelectedContact = (currentChatTransport as MetaContactChatTransport).contact == contact
            val resourceName = currentChatTransport!!.resourceName
            val isResourceSelected = isSelectedContact && resourceName != null
            removeChatTransports(contact)
            if (isResourceSelected) addChatTransports(contact, resourceName, true) else addChatTransports(contact, null, isSelectedContact)
        }
    }

    /**
     * Called when a new `ContactResource` has been added to the list of available `Contact` resources.
     *
     * event the `ContactResourceEvent` that notified us
     */
    override fun contactResourceAdded(event: ContactResourceEvent?) {
        val contact = event!!.getContact()
        if (metaContact.containsContact(contact)) {
            updateChatTransports(contact)
        }
    }

    /**
     * Called when a `ContactResource` has been removed to the list of available `Contact` resources.
     *
     * event the `ContactResourceEvent` that notified us
     */
    override fun contactResourceRemoved(event: ContactResourceEvent?) {
        val contact = event!!.getContact()
        if (metaContact.containsContact(contact)) {
            updateChatTransports(contact)
        }
    }

    /**
     * Called when a `ContactResource` in the list of available `Contact` resources has been modified.
     *
     * event the `ContactResourceEvent` that notified us
     */
    override fun contactResourceModified(event: ContactResourceEvent?) {
        val contact = event!!.getContact()
        if (metaContact.containsContact(contact)) {
            val transport = findChatTransportForResource(event.getContactResource())
            if (transport != null) {
                chatSessionRenderer.updateChatTransportStatus(transport)
            }
        }
    }

    /**
     * Finds the `ChatTransport` corresponding to the given contact `resource`.
     *
     * resource the `ContactResource`, which corresponding transport we're looking for
     *
     * @return the `ChatTransport` corresponding to the given contact `resource`
     */
    private fun findChatTransportForResource(resource: ContactResource): ChatTransport? {
        var transports: List<ChatTransport>
        synchronized(chatTransports) { transports = ArrayList(chatTransports) }
        for (chatTransport in transports) {
            if (chatTransport.descriptor == resource.contact && chatTransport.resourceName != null && chatTransport.resourceName == resource.resourceName) return chatTransport
        }
        return null
    }
}