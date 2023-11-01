/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener
import org.atalk.hmos.plugin.timberlog.TimberLog
import timber.log.Timber
import java.util.*
import java.util.concurrent.locks.ReentrantLock

/**
 * Implements standard functionality of `ProtocolProviderService` in order to make it easier
 * for implementers to provide complete solutions while focusing on protocol-specific details.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class AbstractProtocolProviderService : ProtocolProviderService {
    /**
     * A list of all listeners registered for `RegistrationStateChangeEvent`s.
     */
    private val registrationListeners = ArrayList<RegistrationStateChangeListener>()

    /**
     * The hash table with the operation sets that we support locally.
     */
    private val supportedOperationSets = Hashtable<String, OperationSet>()

    /**
     * Return the lock of pps state
     *
     * @return the Lock for the current PPS synchronization point
     */
    /**
     * ProtocolProviderService state synchronization point for events from various threads
     */
    val loginInitLock = ReentrantLock()

    /**
     * Indicates whether or not the previous XMPP session's stream was resumed.
     *
     * @return `true` if the previous XMPP session's stream was resumed and `false` otherwise.
     */
    /**
     * isResumed true if a previous XMPP session's stream was resumed.
     */
    var isResumed = false
        protected set

    /**
     * Registers the specified listener with this provider so that it would receive notifications on
     * changes of its state or other properties such as its local address and display name.
     *
     * listener the listener to register.
     */
    override fun addRegistrationStateChangeListener(listener: RegistrationStateChangeListener?) {
        requireNotNull(listener) { "listener cannot be null" }
        synchronized(registrationListeners) { if (!registrationListeners.contains(listener)) registrationListeners.add(listener) }
    }

    /**
     * Adds a specific `OperationSet` implementation to the set of supported
     * `OperationSet`s of this instance. Serves as a type-safe wrapper around
     * [.supportedOperationSets] which works with class names instead of `Class` and
     * also shortens the code which performs such additions.
     *
     * <T> the exact type of the `OperationSet` implementation to be added
     * opsetClass the `Class` of `OperationSet` under the name of which the specified
     * implementation is to be added opset the `OperationSet` implementation to be added
    </T> */
    protected fun <T : OperationSet?> addSupportedOperationSet(opsetClass: Class<T>, opset: T) {
        supportedOperationSets[opsetClass.name] = opset as OperationSet
    }

    /**
     * Removes an `OperationSet` implementation from the set of supported
     * `OperationSet`s for this instance.
     *
     * <T> the exact type of the `OperationSet` implementation to be added
     * opsetClass the `Class` of `OperationSet` under the name of which the specified
     * implementation is to be added
    </T> */
    protected fun <T : OperationSet?> removeSupportedOperationSet(opsetClass: Class<T>) {
        supportedOperationSets.remove(opsetClass.name)
    }

    /**
     * Removes all `OperationSet` implementation from the set of supported
     * `OperationSet`s for this instance.
     */
    protected fun clearSupportedOperationSet() {
        supportedOperationSets.clear()
    }
    /**
     * Creates a RegistrationStateChange event corresponding to the specified old and new states
     * and notifies all currently registered listeners.
     *
     * oldState the state that the provider had before the change occurred
     * newState the state that the provider is currently in.
     * reasonCode a value corresponding to one of the REASON_XXX fields of the
     * RegistrationStateChangeEvent class, indicating the reason for this state transition.
     * reason a String further explaining the reason code or null if no such explanation is necessary.
     * userRequest is the event by user request.
     */
    /**
     * Creates a RegistrationStateChange event corresponding to the specified old and new states
     * and
     * notifies all currently registered listeners.
     *
     * oldState the state that the provider had before the change occurred
     * newState the state that the provider is currently in.
     * reasonCode a value corresponding to one of the REASON_XXX fields of the
     * RegistrationStateChangeEvent class, indicating the reason for this state transition.
     * reason a String further explaining the reason code or null if no such explanation is necessary.
     */
    @JvmOverloads
    fun fireRegistrationStateChanged(oldState: RegistrationState,
            newState: RegistrationState, reasonCode: Int, reason: String?, userRequest: Boolean = false) {
        // no change - throws exception to trace the root; otherwise too many unnecessary events
        if (newState === oldState) {
            val msg = "The provider state unchanged: $newState. Reason: $reason"
            Exception(msg).printStackTrace()
        }
        Timber.d("PPS state changed: %s => %s. Reason: %s", oldState, newState, reason)
        val event = RegistrationStateChangeEvent(this, oldState, newState, reasonCode, reason)
        event.setUserRequest(userRequest)
        var listeners: Array<RegistrationStateChangeListener>
        synchronized(registrationListeners) { listeners = registrationListeners.toTypedArray() }
        Timber.log(TimberLog.FINER, "Dispatching %s to %s listeners.", event, listeners.size)
        for (listener in listeners) try {
            listener.registrationStateChanged(event)
        } catch (throwable: Throwable) {
            /*
                 * The registration state has already changed and we're not using the
                 * RegistrationStateChangeListeners to veto the change so it doesn't make sense to,
                 * for example, disconnect because one of them malfunctioned.
                 *
                 * Of course, death cannot be ignored.
                 */
            if (throwable is ThreadDeath) throw throwable
            Timber.e(throwable, "Exception while sending registrationStateChanged event to: %s", listener)
        }
        Timber.log(TimberLog.FINER, "Done.")
    }

    /**
     * Returns the operation set corresponding to the specified class or null if this operation set
     * is not supported by the provider implementation.
     *
     * <T> the exact type of the `OperationSet` that we're looking for
     * opsetClass the `Class` of the operation set that we're looking for.
     * @return returns an `OperationSet` of the specified `Class` if the underlying
     * implementation supports it; `null`, otherwise.
    </T> */
    override fun <T : OperationSet?> getOperationSet(opsetClass: Class<T>): T? {
        return supportedOperationSets[opsetClass.name] as T
    }

    /**
     * Returns the protocol display name. This is the name that would be used by the GUI to display
     * the protocol name.
     *
     * @return a String containing the display name of the protocol this service is implementing
     */
    override val protocolDisplayName: String
        get() {
            val displayName = accountID.getAccountPropertyString(ProtocolProviderFactory.PROTOCOL)
            return displayName ?: protocolName
        }

    /**
     * Default implementation that always returns true.
     *
     * contactId ignored.
     * result ignored
     * @return true
     */
    override fun validateContactAddress(contactId: String?, result: MutableList<String?>?): Boolean {
        return true
    }

    /**
     * Returns an array containing all operation sets supported by the current implementation. When
     * querying this method users must be prepared to receive any subset of the OperationSet-s
     * defined by this service. They MUST ignore any OperationSet-s that they are not aware of and
     * that may be defined by future version of this service. Such "unknown" OperationSet-s though
     * not encouraged, may also be defined by service implementors.
     *
     * @return a java.util.Map containing instance of all supported operation sets mapped against
     * their class names (e.g. OperationSetPresence.class.getName()) .
     */
    override fun getSupportedOperationSets(): Map<String, OperationSet> {
        return Hashtable(supportedOperationSets)
    }

    /**
     * Returns a collection containing all operation sets classes supported by the current
     * implementation. When querying this method users must be prepared to receive any subset of
     * the OperationSet-s defined by this service. They MUST ignore any OperationSet-s that they are
     * not aware of and that may be defined by future versions of this service. Such "unknown"
     * OperationSet-s though not encouraged, may also be defined by service implementors.
     *
     * @return a [Collection] containing instances of all supported operation set classes
     * (e.g. `OperationSetPresence.class`.
     */
    override fun getSupportedOperationSetClasses(): Collection<Class<out OperationSet>> {
        val opSetClasses = ArrayList<Class<out OperationSet?>>()
        for (opSetClassName in getSupportedOperationSets().keys) {
            try {
                opSetClasses.add(getSupportedOperationSets()[opSetClassName]!!.javaClass.classLoader!!.loadClass(opSetClassName) as Class<out OperationSet?>)
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
            }
        }
        return opSetClasses
    }

    /**
     * Indicates whether or not this provider is registered
     *
     * @return `true` if the provider is currently registered and `false` otherwise.
     */
    override val isRegistered: Boolean
        get() {
            return registrationState == RegistrationState.REGISTERED
        }

    /**
     * Indicates whether or not this provider must registered when placing outgoing calls.
     *
     * @return `true` if the provider must be registered when placing a call and 'false` otherwise.
     */
    override val isRegistrationRequiredForCalling =  true

    /**
     * Removes the specified registration state change listener so that it does not receive any
     * further notifications upon changes of the RegistrationState of this provider.
     *
     * listener the listener to register for `RegistrationStateChangeEvent`s.
     */
    override fun removeRegistrationStateChangeListener(listener: RegistrationStateChangeListener) {
        synchronized(registrationListeners) { registrationListeners.remove(listener) }
    }

    /**
     * Clear all registration state change listeners.
     */
    fun clearRegistrationStateChangeListener() {
        synchronized(registrationListeners) { registrationListeners.clear() }
    }

    /**
     * A clear display for ProtocolProvider when its printed in logs.
     *
     * @return the class name and the currently handled account.
     */
    override fun toString(): String {
        return javaClass.simpleName + "(" + accountID + ")"
    }

    /**
     * Ends the registration of this protocol provider with the current registration service. The
     * default is just to call unregister. Providers that need to differentiate user requests (from
     * the UI) or automatic unregister can override this method.
     *
     * userRequest is the unregister by user request.
     * @throws OperationFailedException with the corresponding code it the registration fails for some reason
     * (e.g. a networking error or an implementation problem).
     */
    @Throws(OperationFailedException::class)
    override fun unregister(userRequest: Boolean) {
        this.unregister()
    }
}