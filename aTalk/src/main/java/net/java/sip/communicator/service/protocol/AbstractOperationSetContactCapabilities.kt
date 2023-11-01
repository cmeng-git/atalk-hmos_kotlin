/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.ContactCapabilitiesEvent
import net.java.sip.communicator.service.protocol.event.ContactCapabilitiesListener
import org.jxmpp.jid.Jid
import timber.log.Timber
import java.util.*

/**
 * Represents a default implementation of `OperationSetContactCapabilities` which attempts to
 * make it easier for implementers to provide complete solutions while focusing on
 * implementation-specific functionality.
 *
 * @param <T> the type of the `ProtocolProviderService` implementation providing the
 * `AbstractOperationSetContactCapabilities` implementation
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
</T> */
abstract class AbstractOperationSetContactCapabilities<T : ProtocolProviderService?> protected constructor(parentProvider: T) : OperationSetContactCapabilities {
    /**
     * The list of `ContactCapabilitiesListener`s registered to be notified about changes in
     * the list of `OperationSet` capabilities of `Contact`s.
     */
    private val contactCapabilitiesListeners = LinkedList<ContactCapabilitiesListener>()

    /**
     * The `ProtocolProviderService` which provides this `OperationSetContactCapabilities`.
     */
    protected val parentProvider: T

    /**
     * Initializes a new `AbstractOperationSetContactCapabilities` instance which is to be
     * provided by a specific `ProtocolProviderService` implementation.
     *
     * parentProvider the `ProtocolProviderService` implementation which will provide the new instance
     */
    init {
        // if (parentProvider == null) throw NullPointerException("parentProvider")
        this.parentProvider = parentProvider
    }

    /**
     * Registers a specific `ContactCapabilitiesListener` to be notified about changes in
     * the list of `OperationSet` capabilities of `Contact`s. If the specified
     * `listener` has already been registered, adding it again has no effect.
     *
     * @param listener the `ContactCapabilitiesListener` which is to be notified about changes in the
     * list of `OperationSet` capabilities of `Contact`s
     * @see OperationSetContactCapabilities.addContactCapabilitiesListener
     */
    override fun addContactCapabilitiesListener(listener: ContactCapabilitiesListener?) {
        if (listener == null) throw NullPointerException("listener")
        synchronized(contactCapabilitiesListeners) { if (!contactCapabilitiesListeners.contains(listener)) contactCapabilitiesListeners.add(listener) }
    }

    /**
     * Fires a new `ContactCapabilitiesEvent` to notify the registered `ContactCapabilitiesListener`s
     * that a specific `Contact` has changed its list of `OperationSet` capabilities.
     *
     * @param sourceContact the `Contact` which is the source/cause of the event to be fired
     * @param jid the contact fullJid
     * @param opSets the new operation sets for the given source contact
     */
    protected fun fireContactCapabilitiesEvent(sourceContact: Contact?, jid: Jid?, opSets: Map<String, OperationSet>) {
        var listeners: Array<ContactCapabilitiesListener>
        synchronized(contactCapabilitiesListeners) { listeners = contactCapabilitiesListeners.toTypedArray() }
        if (listeners.isNotEmpty()) {
            val event = ContactCapabilitiesEvent(sourceContact, jid, opSets)
            for (listener in listeners) {
                if (jid != null) {
                    listener.supportedOperationSetsChanged(event)
                } else {
                    Timber.w(IllegalArgumentException("Cannot fire ContactCapabilitiesEvent with contact: $sourceContact"))
                }
            }
        }
    }

    /**
     * Gets the `OperationSet` corresponding to the specified `Class` and
     * supported by the specified `Contact`. If the returned value is non-`null`,
     * it indicates  that the `Contact` is considered by the associated protocol provider
     * to possess the `opsetClass` capability. Otherwise, the associated protocol provider
     * considers `contact` to not have the `opsetClass` capability.
     * `AbstractOperationSetContactCapabilities` looks for the name of the specified
     * `opsetClass` in the `Map` returned by
     * getSupportedOperationSets and returns the associated `OperationSet`.
     * Since the implementation is suboptimal due to the temporary `Map` allocations and
     * lookups, extenders are advised to override
     * [.getOperationSet].
     *
     * @param <U> the type extending `OperationSet` for which the specified `contact` is
     * to be checked whether it possesses it as a capability
     * @param contact the `Contact` for which the `opsetClass` capability is to be queried
     * @param opsetClass the `OperationSet` `Class` for which the specified `contact` is
     * to be checked whether it possesses it as a capability
     * @return the `OperationSet` corresponding to the specified `opsetClass`
     * which is considered by the associated protocol provider to be possessed as a capability by
     * the specified `contact`; otherwise, `null`
     * @see OperationSetContactCapabilities.getOperationSet
    </U> */
    override fun <U : OperationSet?> getOperationSet(contact: Contact, opsetClass: Class<U>): U? {
        return getOperationSet(contact, opsetClass, isOnline(contact))
    }

    /**
     * Gets the `OperationSet` corresponding to the specified `Class` and
     * supported by the specified `Contact`. If the returned value is non-`null`,
     * it indicates that the `Contact` is considered by the associated protocol provider
     * to possess the `opsetClass` capability. Otherwise, the associated protocol provider
     * considers `contact` to not have the `opsetClass` capability.
     * `AbstractOperationSetContactCapabilities` looks for the name of the specified
     * `opsetClass` in the `Map` returned by
     * [.getSupportedOperationSets] and returns the associated `OperationSet`.
     * Since the implementation is suboptimal due to the temporary `Map` allocations and
     * lookups, extenders are advised to override.
     *
     * @param <U> the type extending `OperationSet` for which the specified `contact` is
     * to be checked whether it possesses it as a capability
     * @param contact the `Contact` for which the `opsetClass` capability is to be queried
     * @param opsetClass the `OperationSet` `Class` for which the specified `contact` is
     * to be checked whether it possesses it as a capability
     * @param online `true` if `contact` is online; otherwise, `false`
     * @return the `OperationSet` corresponding to the specified `opsetClass`
     * which is considered by the associated protocol provider to be possessed as a capability by
     * the specified `contact`; otherwise, `null`
     * @see OperationSetContactCapabilities.getOperationSet
    </U> */
    protected open fun <U : OperationSet?> getOperationSet(contact: Contact, opsetClass: Class<U>, online: Boolean): U? {
        val supportedOperationSets = getSupportedOperationSets(contact, online)

        val opset = supportedOperationSets[opsetClass.name]
        if (opsetClass.isInstance(opset)) return opset as U?
        return null
    }

    /**
     * Gets the `OperationSet`s supported by a specific `Contact`. The returned
     * `OperationSet`s are considered by the associated protocol provider to capabilities
     * possessed by the specified `contact`. The default implementation returns the
     * result of calling [ProtocolProviderService.getSupportedOperationSets] on the
     * associated `ProtocolProviderService` implementation. Extenders have to override the
     * default implementation of [.getSupportedOperationSets] in order to
     * provide actual capability detection for the specified `contact`.
     *
     * @param contact the `Contact` for which the supported `OperationSet` capabilities are to be retrieved
     * @return a `Map` listing the `OperationSet`s considered by the associated
     * protocol provider to be supported by the specified `contact` (i.e. to be
     * possessed as capabilities). Each supported `OperationSet` capability is
     * represented by a `Map.Entry` with key equal to the `OperationSet` class
     * name and value equal to the respective `OperationSet` instance
     * @see OperationSetContactCapabilities.getSupportedOperationSets
     */
    override fun getSupportedOperationSets(contact: Contact): Map<String, OperationSet> {
        return getSupportedOperationSets(contact, isOnline(contact))
    }

    /**
     * Gets the `OperationSet`s supported by a specific `Contact`. The returned
     * `OperationSet`s are considered by the associated protocol provider to capabilities
     * possessed by the specified `contact`. The default implementation returns the
     * result of calling [ProtocolProviderService.getSupportedOperationSets] on the
     * associated `ProtocolProviderService` implementation. Extenders have to override the
     * default implementation in order to provide actual capability detection for the specified `contact`.
     *
     * @param contact the `Contact` for which the supported `OperationSet` capabilities are to
     * be retrieved
     * @param online `true` if `contact` is online; otherwise, `false`
     * @return a `Map` listing the `OperationSet`s considered by the associated
     * protocol provider to be supported by the specified `contact` (i.e. to be
     * possessed as capabilities). Each supported `OperationSet` capability is
     * represented by a `Map.Entry` with key equal to the `OperationSet` class
     * name and value equal to the respective `OperationSet` instance
     * @see OperationSetContactCapabilities.getSupportedOperationSets
     */
    open fun getSupportedOperationSets(contact: Contact, online: Boolean): Map<String, OperationSet> {
        return parentProvider!!.getSupportedOperationSets()
    }

    /**
     * Determines whether a specific `Contact` is online (in contrast to offline).
     *
     * @param contact the `Contact` which is to be determines whether it is online
     * @return `true` if the specified `contact` is online; otherwise, `false`
     */
    protected fun isOnline(contact: Contact): Boolean {
        val opsetPresence = parentProvider?.getOperationSet(OperationSetPresence::class.java)
        return if (opsetPresence == null) {
            /*
             * Presence is not implemented so we cannot really know and thus we'll give it the
             * benefit of the doubt and declare it online.
             */
            true
        } else {
            var presenceStatus: PresenceStatus? = null
            var exception: Throwable? = null

            try {
                presenceStatus = opsetPresence.queryContactStatus(contact.contactJid!!.asBareJid())
            } catch (iaex: IllegalArgumentException) {
                exception = iaex
            } catch (iaex: IllegalStateException) {
                exception = iaex
            } catch (iaex: OperationFailedException) {
                exception = iaex
            }
            if (presenceStatus == null) presenceStatus = contact.presenceStatus
            if (presenceStatus == null) {
                if (exception != null) {
                    Timber.d(exception, "Failed to query PresenceStatus of Contact %s", contact)
                }
                /*
                 * For whatever reason the PresenceStatus wasn't retrieved, it's a fact that
                 * presence was advertised and the contacts wasn't reported online.
                 */
                false
            } else presenceStatus.isOnline
        }
    }

    /**
     * Unregisters a specific `ContactCapabilitiesListener` to no longer be notified about
     * changes in the list of `OperationSet` capabilities of `Contact`s. If the specified
     * `listener` has already been unregistered or has never been registered, removing it has no effect.
     *
     * @param listener the `ContactCapabilitiesListener` which is to no longer be notified about
     * changes in the list of `OperationSet` capabilities of `Contact`s
     * @see OperationSetContactCapabilities.removeContactCapabilitiesListener
     */
    override fun removeContactCapabilitiesListener(listener: ContactCapabilitiesListener?) {
        if (listener != null) {
            synchronized(contactCapabilitiesListeners) { contactCapabilitiesListeners.remove(listener) }
        }
    }
}