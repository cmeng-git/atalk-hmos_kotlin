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
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.CallPeerAdapter
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent
import net.java.sip.communicator.service.protocol.event.RemoteControlGrantedEvent
import net.java.sip.communicator.service.protocol.event.RemoteControlListener
import net.java.sip.communicator.service.protocol.event.RemoteControlRevokedEvent
import java.lang.ref.WeakReference
import java.util.*

/**
 * Represents a default/base implementation of `OperationSetDesktopSharingClient` which
 * attempts to make it easier for implementers to provide complete solutions while focusing on
 * implementation-specific functionality.
 *
 * @param <T>
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
</T> */
abstract class AbstractOperationSetDesktopSharingClient<T : ProtocolProviderService?>
/**
 * Initializes a new `AbstractOperationSetDesktopSharing` instance which is to be
 * provided by a specific `ProtocolProviderService.
 *
 * @param parentProvider the `ProtocolProviderService` implementation which is creating the new instance
 * and for which telephony conferencing services are being provided by this instance
` */ protected constructor(
        /**
         * The `ProtocolProviderService` implementation which created this instance and for which
         * telephony conferencing services are being provided by this instance.
         */
        protected val parentProvider: T) : OperationSetDesktopSharingClient {
    /**
     * The `CallPeerListener` which listens to modifications in the properties/state of
     * `CallPeer`.
     */
    private val callPeerListener = object : CallPeerAdapter() {
        /**
         * Indicates that a change has occurred in the status of the source `CallPeer`.
         *
         * @param evt
         * the `CallPeerChangeEvent` instance containing the source event as well as
         * its previous and its new status
         */
        override fun peerStateChanged(evt: CallPeerChangeEvent) {
            val peer = evt.getSourceCallPeer()
            val state = peer.getState()
            if (state != null && (state == CallPeerState.DISCONNECTED || state == CallPeerState.FAILED)) {
                removesNullAndRevokedControlPeer(peer.getPeerID())
                removeRemoteControlListener(getListener(peer))
            }
        }
    }

    /**
     * List of the granted remote control peers for this client. Used to remember granted remote
     * control peers, when the granted event is fired before the corresponding UI listener
     * registration.
     */
    private val grantedRemoteControlPeers = Vector<CallPeer?>()

    /**
     * The list of `RemoteControlListener`s to be notified when a change in remote control
     * access occurs.
     */
    private val listeners = ArrayList<WeakReference<RemoteControlListener>>()

    /**
     * Peers who granted/revoked remote control before it's listeners was
     * added
     */
    private val deferredRemoteControlPeers = ArrayList<String?>()

    /**
     * Adds a `RemoteControlListener` to be notified when the remote peer accepts to give us
     * full control of their desktop.
     *
     *
     * The default implementation of `AbstractOperationSetDesktopSharingClient` adds a
     * `WeakReference` to the specified `RemoteControlListener` in order to avoid
     * memory leaks because of code which calls `addRemoteControlListener` and never calls
     * `removeRemoteControlListener`.
     *
     *
     * @param listener the `RemoteControlListener` to add
     */
    override fun addRemoteControlListener(listener: RemoteControlListener) {
        synchronized(listeners) {
            val i = listeners.iterator()
            var contains = false
            while (i.hasNext()) {
                val l = i.next().get()
                if (l == null) i.remove() else if (l == listener) contains = true
            }
            if (!contains) {
                listeners.add(WeakReference(listener))
                listener.getCallPeer().addCallPeerListener(callPeerListener)
            }
        }

        // Notifies the new listener if the corresponding peer has already been
        // granted to remotely control the shared desktop.
        val peer = listener.getCallPeer()
        // Removes the null peers from the granted remote control peer list.
        // If the corresponding peer was in the granted list, then this peer has
        // already been granted and we must call the remoteControlGranted
        // function for this listener.
        if (removesNullAndRevokedControlPeer(peer.getPeerID()) != -1) listener.remoteControlGranted(RemoteControlGrantedEvent(peer))
        if (deferredRemoteControlPeers.contains(peer.getAddress())) {
            fireRemoteControlGranted(peer)
            deferredRemoteControlPeers.remove(peer.getAddress())
        }
    }

    /**
     * Fires a `RemoteControlGrantedEvent` to all registered listeners.
     *
     * @param peer the `CallPeer`
     */
    private fun fireRemoteControlGranted(peer: CallPeer?) {
        val listener = getListener(peer)
        if (listener != null) {
            listener.remoteControlGranted(RemoteControlGrantedEvent(peer))
        } else {
            // Removes all previous instance of this peer.
            removesNullAndRevokedControlPeer(peer!!.getPeerID())
            // Adds the peer to the granted remote control peer list.
            synchronized(grantedRemoteControlPeers) { grantedRemoteControlPeers.add(peer) }
        }
    }

    /**
     * Fires a `RemoteControlGrantedEvent` to all registered listeners.
     *
     * @param peer the `CallPeer`
     */
    fun fireRemoteControlRevoked(peer: CallPeer) {
        val listener = getListener(peer)
        listener?.remoteControlRevoked(RemoteControlRevokedEvent(peer))

        // Removes the peer from the granted remote control peer list.
        removesNullAndRevokedControlPeer(peer.getPeerID())
    }

    fun addAddDeferredRemoteControlPeer(address: String?) {
        if (!deferredRemoteControlPeers.contains(address)) {
            deferredRemoteControlPeers.add(address)
        }
    }

    /**
     * Gets a list of `RemoteControlListener`s to be notified of remote control access changes.
     *
     * @return a list of `RemoteControlListener`s to be notifed of remote control access changes
     */
    protected fun getListeners(): List<RemoteControlListener?> {
        var listeners: MutableList<RemoteControlListener?>
        synchronized(this.listeners) {
            val i = this.listeners.iterator()
            listeners = ArrayList(this.listeners.size)
            while (i.hasNext()) {
                val l = i.next().get()
                if (l == null) i.remove() else listeners.add(l)
            }
        }
        return listeners
    }

    /**
     * Removes a `RemoteControlListener` to be notified when remote peer accept/revoke to
     * give us full control.
     *
     * @param listener `RemoteControlListener` to remove
     */
    override fun removeRemoteControlListener(listener: RemoteControlListener?) {
        synchronized(listeners) {
            val i = listeners.iterator()
            while (i.hasNext()) {
                val l = i.next().get()
                if (l == null || l == listener) i.remove()
            }
        }
    }

    /**
     * Removes null and the peer corresponding to the revokedPeerID from the granted control peer
     * list.
     *
     * @param revokedPeerID The ID of the revoked peer. May be null to only clear null instances from the granted
     * control peer list.
     * @return The index corresponding to the revokedPeerID entry. -1 if the revoked PeerID is null,
     * or if the revokedPeerID is not found and removed.
     */
    private fun removesNullAndRevokedControlPeer(revokedPeerID: String?): Int {
        var index = -1
        synchronized(grantedRemoteControlPeers) {
            var peer: CallPeer?
            var i = 0
            while (i < grantedRemoteControlPeers.size) {
                peer = grantedRemoteControlPeers[i]
                if (peer == null || peer.getPeerID() == revokedPeerID) {
                    grantedRemoteControlPeers.removeAt(i)
                    index = i
                    --i
                }
                ++i
            }
        }
        return index
    }

    /**
     * Returns the `RemoteControlListener` corresponding to the given `callPeer`, if
     * it exists.
     *
     * @param callPeer the `CallPeer` to get the corresponding `RemoteControlListener` of
     * @return the `RemoteControlListener` corresponding to the given `callPeer`, if
     * it exists; `null`, otherwise
     */
    protected fun getListener(callPeer: CallPeer?): RemoteControlListener? {
        val peerID = callPeer!!.getPeerID()
        synchronized(listeners) {
            val i = listeners.iterator()
            while (i.hasNext()) {
                val l = i.next().get()
                if (l == null) i.remove() else if (peerID == l.getCallPeer().getPeerID()) return l
            }
        }
        return null
    }
}