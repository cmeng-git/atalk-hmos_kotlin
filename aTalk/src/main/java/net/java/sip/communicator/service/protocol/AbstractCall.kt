/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.CallPeerEvent
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import java.util.*

/**
 * Provides implementations for some of the methods in the `Call` abstract class to facilitate implementations.
 *
 * <T> the peer extension class like for example `CallPeerSipImpl` or `CallPeerJabberImpl`
 * <U> the provider extension class like for example `ProtocolProviderServiceSipImpl` or
 * `ProtocolProviderServiceJabberImpl`
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class AbstractCall<T : CallPeer, U : ProtocolProviderService>
protected constructor(
        /**
         * A reference to the <code>ProtocolProviderService</code> instance that created this call.
         */
        val sourceProvider: U,

        /**
         * An identifier uniquely representing the call; set to be same as Jingle Sid if available.
         */
        sid: String,
) : Call<T>(sourceProvider, sid) {
    /**
     * The list of `CallPeer`s of this `Call`. It is implemented as a copy-on-write
     * storage in order to optimize the implementation of [Call.getCallPeers]. It represents
     * private state which is to not be exposed to outsiders. An unmodifiable view which may safely
     * be exposed to outsiders without the danger of `ConcurrentModificationException` is
     * [.unmodifiableCallPeers].
     */
    // override var callPeers: Iterator<T>? = null

    private var mCallPeers: List<T>

    /**
     * The `Object` which is used to synchronize the access to [.callPeers] and
     * [.unmodifiableCallPeers].
     */
    private val callPeersSyncRoot = Any()

    /**
     * The `PropertyChangeSupport` which helps this instance with `PropertyChangeListener`s.
     */
    private val propertyChangeSupport = PropertyChangeSupport(this)

    /**
     * An unmodifiable view of [.callPeers]. It may safely be exposed to outsiders without the
     * danger of `ConcurrentModificationException` and thus optimizes the implementation of
     * [Call.getCallPeers].
     */
    private var unmodifiableCallPeers: List<T>

    /**
     * Creates a new Call instance.
     *
     * sourceProvider the proto provider that created us.
     * sid the Jingle session-initiate id if provided.
     */
    init {
        mCallPeers = emptyList()
        unmodifiableCallPeers = Collections.unmodifiableList(mCallPeers)
    }

    /**
     * {@inheritDoc}
     *
     * Delegates to [.propertyChangeSupport].
     */
    override fun addPropertyChangeListener(listener: PropertyChangeListener?) {
        propertyChangeSupport.addPropertyChangeListener(listener)
    }

    /**
     * Adds a specific `CallPeer` to the list of `CallPeer`s of this `Call` if
     * the list does not contain it; otherwise, does nothing. Does not fire
     * [CallPeerEvent.CALL_PEER_ADDED].
     *
     *
     * The method is named `doAddCallPeer` and not `addCallPeer` because, at the time
     * of its introduction, multiple extenders have already defined an `addCallPeer` method
     * with the same argument but with no return value.
     *
     *
     * callPeer the `CallPeer` to be added to the list of `CallPeer`s of this `Call`
     * @return `true` if the list of `CallPeer`s of this `Call` was modified as
     * a result of the execution of the method; otherwise, `false`
     * @throws NullPointerException if `callPeer` is `null`
     */
    protected fun doAddCallPeer(callPeer: T?): Boolean {
        if (callPeer == null) throw NullPointerException("callPeer")
        synchronized(callPeersSyncRoot) {
            return if (mCallPeers.contains(callPeer)) false else {
                /*
                 * The List of CallPeers of this Call is implemented as a copy-on-write storage in
                 * order to optimize the implementation of the Call.getCallPeers() method.
                 */
                val newCallPeers = ArrayList(mCallPeers)
                if (newCallPeers.add(callPeer)) {
                    mCallPeers = newCallPeers
                    unmodifiableCallPeers = Collections.unmodifiableList(mCallPeers)
                    true
                } else false
            }
        }
    }

    /**
     * Removes a specific `CallPeer` from the list of `CallPeer`s of this `Call` if the list does
     * contain it; otherwise, does nothing. Does not fire [CallPeerEvent.CALL_PEER_REMOVED].
     *
     *
     * The method is named `doRemoveCallPeer` and not `removeCallPeer` because, at the
     * time of its introduction, multiple extenders have already defined a `removeCallPeer`
     * method with the same argument but with no return value.
     *
     *
     * callPeer the `CallPeer` to be removed from the list of `CallPeer`s of this `Call`
     * @return `true` if the list of `CallPeer`s of this `Call` was modified as
     * a result of the execution of the method; otherwise, `false`
     */
    protected fun doRemoveCallPeer(callPeer: T): Boolean {
        synchronized(callPeersSyncRoot) {

            /*
             * The List of CallPeers of this Call is implemented as a copy-on-write storage in order
             * to optimize the implementation of the Call.getCallPeers() method.
             */
            val newCallPeers = ArrayList(mCallPeers)
            return if (newCallPeers.remove(callPeer)) {
                mCallPeers = newCallPeers
                unmodifiableCallPeers = Collections.unmodifiableList(mCallPeers)
                true
            } else false
        }
    }

    /**
     * {@inheritDoc}
     *
     * Delegates to [.propertyChangeSupport].
     */
    override fun firePropertyChange(property: String?, oldValue: Any?, newValue: Any?) {
        propertyChangeSupport.firePropertyChange(property, oldValue, newValue)
    }

    /**
     * Returns the number of peers currently associated with this call.
     *
     * @return an `int` indicating the number of peers currently associated with this call.
     */
    override val callPeerCount: Int
        get() = getCallPeerList().size

    /**
     * Gets an unmodifiable `List` of the `CallPeer`s of this `Call`. The implementation of
     * [Call.getCallPeers] returns an `Iterator` over the same `List`.
     *
     * @return an unmodifiable `List` of the `CallPeer`s of this `Call`
     */
    fun getCallPeerList(): List<T> {
        synchronized(callPeersSyncRoot) { return unmodifiableCallPeers }
    }

    /**
     * Returns an `Iterator` over the (list of) `CallPeer`s of this `Call`. The
     * returned `Iterator` operates over the `List` returned by [.getCallPeerList].
     *
     * @return an `Iterator` over the (list of) `CallPeer`s of this `Call`
     */
    override fun getCallPeers(): Iterator<T> {
        return getCallPeerList().iterator()
    }

    /**
     * {@inheritDoc}
     *
     * Delegates to [.propertyChangeSupport].
     */
    override fun removePropertyChangeListener(listener: PropertyChangeListener?) {
        propertyChangeSupport.removePropertyChangeListener(listener)
    }
}