/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.*
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.jxmpp.jid.Jid
import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.util.*

/**
 * Represents a default implementation of `OperationSetPersistentPresence` in order to make
 * it easier for implementers to provide complete solutions while focusing on implementation-specific details.
 *
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
abstract class AbstractOperationSetPersistentPresence<T : ProtocolProviderService?>
/**
 * Initializes a new `AbstractOperationSetPersistentPresence` instance created by a
 * specific `ProtocolProviderService` .
 *
 * pps the `ProtocolProviderService` which created the new instance
 */
protected constructor(
        /**
         * The provider that created us.
         */
        protected val mPPS: T) : OperationSetPersistentPresence {
    /**
     * A list of listeners registered for `ContactPresenceStatusChangeEvent`s.
     */
    private val contactPresenceStatusListeners = Vector<ContactPresenceStatusListener>()

    /**
     * A list of listeners registered for `ProviderPresenceStatusChangeEvent`s.
     */
    private val providerPresenceStatusListeners = Vector<ProviderPresenceStatusListener>()

    /**
     * A list of listeners registered for `ServerStoredGroupChangeEvent`s.
     */
    private val serverStoredGroupListeners = Vector<ServerStoredGroupListener>()

    /**
     * The list of listeners interested in `SubscriptionEvent`s.
     */
    private val subscriptionListeners = Vector<SubscriptionListener>()

    /**
     * Implementation of the corresponding ProtocolProviderService method.
     *
     * @param listener a presence status listener.
     */
    override fun addContactPresenceStatusListener(listener: ContactPresenceStatusListener) {
        synchronized(contactPresenceStatusListeners) { if (!contactPresenceStatusListeners.contains(listener)) contactPresenceStatusListeners.add(listener) }
    }

    /**
     * Adds a listener that would receive events upon changes of the provider presence status.
     *
     * @param listener the listener to register for changes in our PresenceStatus.
     */
    override fun addProviderPresenceStatusListener(listener: ProviderPresenceStatusListener) {
        synchronized(providerPresenceStatusListeners) { if (!providerPresenceStatusListeners.contains(listener)) providerPresenceStatusListeners.add(listener) }
    }

    /**
     * Registers a listener that would receive events upon changes in server stored groups.
     *
     * @param listener a ServerStoredGroupChangeListener impl that would receive events upon group changes.
     */
    override fun addServerStoredGroupChangeListener(listener: ServerStoredGroupListener) {
        synchronized(serverStoredGroupListeners) { if (!serverStoredGroupListeners.contains(listener)) serverStoredGroupListeners.add(listener) }
    }

    override fun addSubscriptionListener(listener: SubscriptionListener) {
        synchronized(subscriptionListeners) { if (!subscriptionListeners.contains(listener)) subscriptionListeners.add(listener) }
    }

    /**
     * Notifies all registered listeners of the new event.
     *
     * @param source the contact that has caused the event.
     * @param jid the specific contact FullJid that has caused the event.
     * @param parentGroup the group that contains the source contact.
     * @param oldValue the status that the source contact detained before changing it.
     */
    protected fun fireContactPresenceStatusChangeEvent(source: Contact, jid: Jid, parentGroup: ContactGroup,
            oldValue: PresenceStatus) {
        val newValue = source.presenceStatus
        if (oldValue == newValue) {
            Timber.d("Ignored prov status change evt. old==new: %s", oldValue)
            return
        }
        fireContactPresenceStatusChangeEvent(source, jid, parentGroup, oldValue, newValue)
    }

    @JvmOverloads
    fun fireContactPresenceStatusChangeEvent(source: Contact?, jid: Jid, parentGroup: ContactGroup,
            oldValue: PresenceStatus?, newValue: PresenceStatus?, isResourceChange: Boolean = false) {
        val evt = ContactPresenceStatusChangeEvent(source, jid,
                mPPS!!, parentGroup, oldValue, newValue, isResourceChange)
        var listeners: Collection<ContactPresenceStatusListener>
        synchronized(contactPresenceStatusListeners) { listeners = ArrayList(contactPresenceStatusListeners) }
        // Timber.d("Dispatching Contact Status Change. Listeners = %s evt = %s", listeners.size(), evt);
        for (listener in listeners) listener.contactPresenceStatusChanged(evt)
    }

    /**
     * Notify all subscription listeners of the corresponding contact property change event.
     *
     * @param source the ContactJabberImpl instance that this event is pertaining to.
     * @param eventID the String ID of the event to dispatch
     * @param oldValue the value that the changed property had before the change occurred.
     * @param newValue the value that the changed property currently has (after the change has occurred).
     */
    fun fireContactPropertyChangeEvent(source: Contact?, eventID: String?, oldValue: Any?, newValue: Any?) {
        val evt = ContactPropertyChangeEvent(source, eventID, oldValue, newValue)
        var listeners: Collection<SubscriptionListener>
        synchronized(subscriptionListeners) { listeners = ArrayList(subscriptionListeners) }
        // Timber.d("Dispatching a Contact Property Change Event to %d listeners. Evt = %S", listeners.size(), evt);
        for (listener in listeners) listener.contactModified(evt)
    }

    /**
     * Notifies all registered listeners of the new event.
     *
     * @param oldValue the presence status we were in before the change.
     */
    protected fun fireProviderStatusChangeEvent(oldValue: PresenceStatus?) {
        fireProviderStatusChangeEvent(oldValue, getPresenceStatus())
    }

    /**
     * Notify all provider presence listeners of the corresponding event change
     *
     * @param oldValue the status our stack had so far
     * @param newValue the status we have from now on
     */
    protected open fun fireProviderStatusChangeEvent(oldValue: PresenceStatus?, newValue: PresenceStatus?) {
        val evt = ProviderPresenceStatusChangeEvent(mPPS, oldValue, newValue)
        var listeners: Collection<ProviderPresenceStatusListener>
        synchronized(providerPresenceStatusListeners) { listeners = ArrayList(providerPresenceStatusListeners) }
        Timber.log(TimberLog.FINER, "Dispatching Provider Status Change. Listeners = %d evt = %s", listeners.size, evt)
        for (listener in listeners) listener.providerStatusChanged(evt)
    }

    /**
     * Notify all provider presence listeners that a new status message has been set.
     *
     * @param oldStatusMessage the status message our stack had so far
     * @param newStatusMessage the status message we have from now on
     */
    protected fun fireProviderStatusMessageChangeEvent(oldStatusMessage: String?, newStatusMessage: String?) {
        val evt = PropertyChangeEvent(mPPS,
                ProviderPresenceStatusListener.STATUS_MESSAGE, oldStatusMessage, newStatusMessage)
        var listeners: Collection<ProviderPresenceStatusListener>
        synchronized(providerPresenceStatusListeners) { listeners = ArrayList(providerPresenceStatusListeners) }
        Timber.d("Dispatching  stat. msg change. Listeners = %d evt = %s", listeners.size, evt)
        for (listener in listeners) listener.providerStatusMessageChanged(evt)
    }

    /**
     * Notifies all registered listeners of the new event.
     *
     * @param source the contact that has caused the event.
     * @param eventID an identifier of the event to dispatch.
     */
    protected fun fireServerStoredGroupEvent(source: ContactGroup, eventID: Int) {
        val evt = ServerStoredGroupEvent(source, eventID,
                source.getParentContactGroup(), mPPS, this)
        var listeners: Iterable<ServerStoredGroupListener>
        synchronized(serverStoredGroupListeners) { listeners = ArrayList(serverStoredGroupListeners) }
        for (listener in listeners) when (eventID) {
            ServerStoredGroupEvent.GROUP_CREATED_EVENT -> listener.groupCreated(evt)
            ServerStoredGroupEvent.GROUP_RENAMED_EVENT -> listener.groupNameChanged(evt)
            ServerStoredGroupEvent.GROUP_REMOVED_EVENT -> listener.groupRemoved(evt)
        }
    }

    /**
     * Notifies all registered listeners of the new event.
     *
     * @param source the contact that has caused the event.
     * @param parentGroup the group that contains the source contact.
     * @param eventID an identifier of the event to dispatch.
     */
    @JvmOverloads
    fun fireSubscriptionEvent(source: Contact?, parentGroup: ContactGroup?, eventID: Int,
            errorCode: Int = SubscriptionEvent.ERROR_UNSPECIFIED, errorReason: String? = null) {
        val evt = SubscriptionEvent(source, mPPS, parentGroup, eventID,
                errorCode, errorReason)
        var listeners: Collection<SubscriptionListener>
        synchronized(subscriptionListeners) { listeners = ArrayList(subscriptionListeners) }
        Timber.log(TimberLog.FINER, "Dispatching a Subscription Event to %d listeners. Evt = %s", listeners.size, evt)
        for (listener in listeners) when (eventID) {
            SubscriptionEvent.SUBSCRIPTION_CREATED -> listener.subscriptionCreated(evt)
            SubscriptionEvent.SUBSCRIPTION_FAILED -> listener.subscriptionFailed(evt)
            SubscriptionEvent.SUBSCRIPTION_REMOVED -> listener.subscriptionRemoved(evt)
            SubscriptionEvent.SUBSCRIPTION_RESOLVED -> listener.subscriptionResolved(evt)
        }
    }

    /**
     * Notifies all registered listeners of the new event.
     *
     * @param source the contact that has been moved..
     * @param oldParent the group where the contact was located before being moved.
     * @param newParent the group where the contact has been moved.
     */
    fun fireSubscriptionMovedEvent(source: Contact?, oldParent: ContactGroup?, newParent: ContactGroup?) {
        val evt = SubscriptionMovedEvent(source, mPPS, oldParent, newParent)
        var listeners: Collection<SubscriptionListener>
        synchronized(subscriptionListeners) { listeners = ArrayList(subscriptionListeners) }
        Timber.d("Dispatching a Subscription Event to %d listeners. Evt = %s", listeners.size, evt)
        for (listener in listeners) listener.subscriptionMoved(evt)
    }

    /**
     * Removes the specified listener so that it won't receive any further updates on contact
     * presence status changes
     *
     * @param listener the listener to remove.
     */
    override fun removeContactPresenceStatusListener(listener: ContactPresenceStatusListener) {
        synchronized(contactPresenceStatusListeners) { contactPresenceStatusListeners.remove(listener) }
    }

    /**
     * Unregisters the specified listener so that it does not receive further events upon changes in
     * local presence status.
     *
     * @param listener ProviderPresenceStatusListener
     */
    override fun removeProviderPresenceStatusListener(listener: ProviderPresenceStatusListener) {
        synchronized(providerPresenceStatusListeners) { providerPresenceStatusListeners.remove(listener) }
    }

    /**
     * Removes the specified group change listener so that it won't receive any further events.
     *
     * @param listener the ServerStoredGroupChangeListener to remove
     */
    override fun removeServerStoredGroupChangeListener(listener: ServerStoredGroupListener) {
        synchronized(serverStoredGroupListeners) { serverStoredGroupListeners.remove(listener) }
    }

    /**
     * Removes the specified subscription listener.
     *
     * @param listener the listener to remove.
     */
    override fun removeSubscriptionListener(listener: SubscriptionListener) {
        synchronized(subscriptionListeners) { subscriptionListeners.remove(listener) }
    }

    /**
     * Sets the display name for `contact` to be `newName`.
     *
     *
     *
     * @param contact the `Contact` that we are renaming
     * @param newName a `String` containing the new display name for `metaContact`.
     * @throws IllegalArgumentException if `contact` is not an instance that belongs to the underlying implementation.
     */
    @Throws(IllegalArgumentException::class)
    override fun setDisplayName(contact: Contact?, newName: String?) {
    }

    /**
     * Returns the protocol specific contact instance representing the local user or null if it is
     * not supported.
     *
     * @return the Contact that the Provider implementation is communicating on behalf of or null if
     * not supported.
     */
    open fun getLocalContact(): Contact? {
        return null
    }
}