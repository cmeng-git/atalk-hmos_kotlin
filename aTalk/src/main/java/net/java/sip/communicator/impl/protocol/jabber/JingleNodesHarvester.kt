/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import org.ice4j.Transport
import org.ice4j.TransportAddress
import org.ice4j.ice.Component
import org.ice4j.ice.LocalCandidate
import org.ice4j.ice.harvest.AbstractCandidateHarvester
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smackx.jinglenodes.SmackServiceNode
import org.jivesoftware.smackx.jinglenodes.element.JingleChannelIQ
import timber.log.Timber

/**
 * Implements a `CandidateHarvester` which gathers `Candidate`s for a specified
 * [Component] using Jingle Nodes as defined in XEP 278 "Jingle Relay Nodes".
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class JingleNodesHarvester
/**
 * Constructor.
 *
 * @param serviceNode the `SmackServiceNode`
 */
(
        /**
         * XMPPTCPConnection
         */
        private val serviceNode: SmackServiceNode?) : AbstractCandidateHarvester() {
    /**
     * JingleNodes relay allocate two address/port couple for us. Due to the architecture of Ice4j
     * that harvest address for each component, we store the second address/port couple.
     */
    private var localAddressSecond: TransportAddress? = null

    /**
     * JingleNodes relay allocate two address/port couple for us. Due to the architecture of Ice4j
     * that harvest address for each component, we store the second address/port couple.
     */
    private var relayedAddressSecond: TransportAddress? = null

    /**
     * Gathers Jingle Nodes candidates for all host `Candidate`s that are already present in
     * the specified `component`. This method relies on the specified `component` to
     * already contain all its host candidates so that it would resolve them.
     *
     * @param component the [Component] that we'd like to gather candidate Jingle Nodes
     * `Candidate`s for
     * @return the `LocalCandidate`s gathered by this `CandidateHarvester`
     */
    @Synchronized
    override fun harvest(component: Component): Collection<LocalCandidate> {
        Timber.i("Jingle Nodes harvest start!")
        val candidates = HashSet<LocalCandidate>()
        var ip: String
        var port = -1

        /* if we have already a candidate (RTCP) allocated, get it */
        if (localAddressSecond != null && relayedAddressSecond != null) {
            val candidate = createJingleNodesCandidate(relayedAddressSecond, component, localAddressSecond)

            // try to add the candidate to the component and then only add it to the harvest not
            // redundant (not sure how it could be red. but ...)
            if (component.addLocalCandidate(candidate)) {
                candidates.add(candidate!!)
            }
            localAddressSecond = null
            relayedAddressSecond = null
            return candidates
        }
        val conn = serviceNode!!.connection
        var ciq: JingleChannelIQ? = null
        if (serviceNode != null) {
            val preferred = serviceNode.preferredRelay
            if (preferred != null) {
                try {
                    ciq = SmackServiceNode.getChannel(conn, preferred.jid)
                } catch (e: NotConnectedException) {
                    Timber.e("Could not get JingleNodes channel: %s", e.message)
                } catch (e: InterruptedException) {
                    Timber.e("Could not get JingleNodes channel: %s", e.message)
                }
            }
        }
        if (ciq != null) {
            ip = ciq.host
            port = ciq.remoteport
            Timber.i("JN relay: %s remote port: %s local port: %s", ip, port, ciq.localport)
            if (ip == null || ciq.remoteport == 0) {
                Timber.w("JN relay ignored because ip was null or port == 0")
                return candidates
            }

            // Drop the scope or interface name if the relay sends it along in its IPv6 address.
            // The scope/ifname is only valid on host that owns the IP and we don't need it here.
            val scopeIndex = ip.indexOf('%')
            if (scopeIndex > 0) {
                Timber.w("Dropping scope from assumed IPv6 address %s", ip)
                ip = ip.substring(0, scopeIndex)
            }

            /* RTP */
            val relayedAddress = TransportAddress(ip, port, Transport.UDP)
            val localAddress = TransportAddress(ip, ciq.localport, Transport.UDP)
            val local = createJingleNodesCandidate(relayedAddress, component, localAddress)

            /* RTCP */
            relayedAddressSecond = TransportAddress(ip, port + 1, Transport.UDP)
            localAddressSecond = TransportAddress(ip, ciq.localport + 1, Transport.UDP)

            // try to add the candidate to the component and then only add it to
            // the harvest not redundant (not sure how it could be red. but ...)
            if (component.addLocalCandidate(local)) {
                candidates.add(local!!)
            }
        }
        Timber.d("Jingle Nodes: %s", candidates)
        return candidates
    }

    /**
     * Creates a new `JingleNodesRelayedCandidate` instance which is to represent a specific
     * `TransportAddress`.
     *
     * @param transportAddress the `TransportAddress` allocated by the relay
     * @param component the `Component` for which the candidate will be added
     * @param localEndPoint `TransportAddress` of the Jingle Nodes relay where we will send our packet.
     * @return a new `JingleNodesRelayedCandidate` instance which represents the specified
     * `TransportAddress`
     */
    protected fun createJingleNodesCandidate(transportAddress: TransportAddress?,
            component: Component, localEndPoint: TransportAddress?): JingleNodesCandidate? {
        var candidate: JingleNodesCandidate? = null
        try {
            candidate = JingleNodesCandidate(transportAddress, component, localEndPoint)
            val stunSocket = candidate.getStunSocket(null)
            candidate.stunStack.addSocket(stunSocket)
            // cmeng ice4j-v2.0
            component.componentSocket.add(candidate.candidateIceSocketWrapper)
        } catch (e: Throwable) {
            Timber.i("Exception occurred when creating JingleNodesCandidate: %s", e.message)
        }
        return candidate
    }
}