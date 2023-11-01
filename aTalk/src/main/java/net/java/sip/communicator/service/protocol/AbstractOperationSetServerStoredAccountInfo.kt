/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.ServerStoredDetailsChangeEvent
import net.java.sip.communicator.service.protocol.event.ServerStoredDetailsChangeListener
import timber.log.Timber

/**
 * Represents a default implementation of [OperationSetServerStoredAccountInfo] in order to
 * make it easier for implementers to provide complete solutions while focusing on
 * implementation-specific details.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
abstract class AbstractOperationSetServerStoredAccountInfo : OperationSetServerStoredAccountInfo {
    /**
     * A list of listeners registered for `ServerStoredDetailsChangeListener`s.
     */
    private val serverStoredDetailsListeners = ArrayList<ServerStoredDetailsChangeListener>()

    /**
     * Registers a ServerStoredDetailsChangeListener with this operation set so that it gets
     * notifications of details change.
     *
     * @param listener the `ServerStoredDetailsChangeListener` to register.
     */
    override fun addServerStoredDetailsChangeListener(listener: ServerStoredDetailsChangeListener) {
        synchronized(serverStoredDetailsListeners) {
            if (!serverStoredDetailsListeners.contains(listener)) serverStoredDetailsListeners.add(listener)
        }
    }

    /**
     * Unregisters `listener` so that it won't receive any further notifications upon details
     * change.
     *
     * @param listener the `ServerStoredDetailsChangeListener` to unregister.
     */
    override fun removeServerStoredDetailsChangeListener(listener: ServerStoredDetailsChangeListener) {
        synchronized(serverStoredDetailsListeners) {
            serverStoredDetailsListeners.remove(listener)
        }
    }

    /**
     * Notify all listeners of the corresponding account detail change event.
     *
     * @param source the protocol provider service source
     * @param eventID the int ID of the event to dispatch
     * @param oldValue the value that the changed property had before the change occurred.
     * @param newValue the value that the changed property currently has (after the change has occurred).
     */
    fun fireServerStoredDetailsChangeEvent(source: ProtocolProviderService?, eventID: Int,
                                           oldValue: Any?, newValue: Any) {
        val evt = ServerStoredDetailsChangeEvent(source, eventID,
                oldValue, newValue)
        var listeners: Collection<ServerStoredDetailsChangeListener>
        synchronized(serverStoredDetailsListeners) {
            listeners = ArrayList(
                    serverStoredDetailsListeners)
        }
        Timber.d("Dispatching a Contact Property Change Event to %s listeners. Evt = %s",
                listeners.size, evt)
        for (listener in listeners) listener.serverStoredDetailsChanged(evt)
    }
}