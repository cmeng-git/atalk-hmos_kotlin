/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import org.apache.commons.lang3.StringUtils
import org.jivesoftware.smack.SmackConfiguration
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smackx.disco.packet.DiscoverItems
import org.jivesoftware.smackx.jinglenodes.SmackServiceNode
import org.jivesoftware.smackx.jinglenodes.SmackServiceNode.MappedNodes
import org.jivesoftware.smackx.jinglenodes.element.JingleChannelIQ
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong

/**
 * Search for jingle nodes.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class JingleNodesServiceDiscovery
/**
 * Creates discovery
 *
 * @param service the service.
 * @param connection the connected connection.
 * @param accountID our account.
 * syncRoot the synchronization object while discovering.
 */ internal constructor(
        /**
         * The service.
         */
        private val service: SmackServiceNode,
        /**
         * The connection, must be connected.
         */
        private val connection: XMPPConnection,
        /**
         * Our account.
         */
        private val accountID: JabberAccountIDImpl,
        /**
         * Synchronization object to monitor auto discovery.
         */
        private val jingleNodesSyncRoot: Any,
) : Runnable {
    /**
     * The actual discovery.
     */
    override fun run() {
        synchronized(jingleNodesSyncRoot) {
            val start = System.currentTimeMillis()
            Timber.i("Start Jingle Nodes discovery!")
            var nodes: MappedNodes? = null
            var searchNodesWithPrefix = JabberActivator.resources!!.getSettingsString(JINGLE_NODES_SEARCH_PREFIX_PROP)
            if (searchNodesWithPrefix == null || searchNodesWithPrefix.isEmpty()) searchNodesWithPrefix = JabberActivator.getConfigurationService()!!.getString(JINGLE_NODES_SEARCH_PREFIX_PROP)

            // if there are no default prefix settings or this option is turned off, just process
            // with default service discovery making list empty.
            if (searchNodesWithPrefix == null || searchNodesWithPrefix.isEmpty() || searchNodesWithPrefix.equals("off", ignoreCase = true)) {
                searchNodesWithPrefix = ""
            }
            try {
                nodes = searchServicesWithPrefix(service, connection, 6, 3, 20, JingleChannelIQ.UDP,
                        accountID.isJingleNodesSearchBuddiesEnabled(),
                        accountID.isJingleNodesAutoDiscoveryEnabled(), searchNodesWithPrefix)
            } catch (e: NotConnectedException) {
                Timber.e(e, "Search failed")
            } catch (e: InterruptedException) {
                Timber.e(e, "Search failed")
            }
            Timber.i("End of Jingle Nodes discovery!\nFound %s Jingle Nodes relay for account: %s in %s ms",
                    nodes?.relayEntries?.size ?: "0", accountID.accountJid,
                    System.currentTimeMillis() - start)
            if (nodes != null) service.addEntries(nodes)
        }
    }

    /**
     * Searches for services as the prefix list has priority. If it is set return after first found
     * service.
     *
     * @param service the service.
     * @param xmppConnection the connection.
     * @param maxEntries maximum entries to be searched.
     * @param maxDepth the depth while recursively searching.
     * @param maxSearchNodes number of nodes to query
     * @param protocol the protocol
     * @param searchBuddies should we search our buddies in contactlist.
     * @param autoDiscover is auto discover turned on
     * @param prefix the coma separated list of prefixes to be searched first.
     * @return
     */
    @Throws(NotConnectedException::class, InterruptedException::class)
    private fun searchServicesWithPrefix(
            service: SmackServiceNode,
            xmppConnection: XMPPConnection?, maxEntries: Int, maxDepth: Int, maxSearchNodes: Int,
            protocol: String, searchBuddies: Boolean, autoDiscover: Boolean, prefix: String,
    ): MappedNodes? {
        if (xmppConnection == null || !xmppConnection.isConnected) {
            return null
        }
        val mappedNodes = MappedNodes()
        val visited = ConcurrentHashMap<Jid, Jid>()

        // Request to our pre-configured trackerEntries
        for ((_, value) in service.trackerEntries) {
            SmackServiceNode.deepSearch(xmppConnection, maxEntries, value.jid,
                    mappedNodes, maxDepth - 1, maxSearchNodes, protocol, visited)
        }
        if (autoDiscover) {
            val continueSearch = searchDiscoItems(service, xmppConnection, maxEntries,
                    xmppConnection.xmppServiceDomain, mappedNodes, maxDepth - 1, maxSearchNodes,
                    protocol, visited, prefix)

            // option to stop after first found is turned on, lets exit
            if (continueSearch) {
                // Request to Server
                try {
                    SmackServiceNode.deepSearch(xmppConnection, maxEntries, JidCreate.from(xmppConnection.host),
                            mappedNodes, maxDepth - 1, maxSearchNodes, protocol, visited)
                } catch (e: XmppStringprepException) {
                    e.printStackTrace()
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                }

                // Request to Buddies
                val roster = Roster.getInstanceFor(xmppConnection)
                if (roster != null && searchBuddies) {
                    for (re in roster.entries) {
                        val i = roster.getPresences(re.jid)
                        for (presence in i) {
                            if (presence.isAvailable) {
                                SmackServiceNode.deepSearch(xmppConnection, maxEntries, presence.from,
                                        mappedNodes, maxDepth - 1, maxSearchNodes, protocol, visited)
                            }
                        }
                    }
                }
            }
        }
        return mappedNodes
    }

    companion object {
        /**
         * Property containing jingle nodes prefix to search for.
         */
        private const val JINGLE_NODES_SEARCH_PREFIX_PROP = "protocol.jabber.JINGLE_NODES_SEARCH_PREFIXES"

        /**
         * Property containing jingle nodes prefix to search for.
         */
        private const val JINGLE_NODES_SEARCH_PREFIXES_STOP_ON_FIRST_PROP = "protocol.jabber.JINGLE_NODES_SEARCH_PREFIXES_STOP_ON_FIRST"

        /**
         * Discover services and query them.
         *
         * @param service the service.
         * @param xmppConnection the connection.
         * @param maxEntries maximum entries to be searched.
         * @param startPoint the start point to search recursively
         * @param mappedNodes nodes found
         * @param maxDepth the depth while recursively searching.
         * @param maxSearchNodes number of nodes to query
         * @param protocol the protocol
         * @param visited nodes already visited
         * @param prefix the coma separated list of prefixes to be searched first.
         * @return
         */
        @Throws(InterruptedException::class, NotConnectedException::class)
        private fun searchDiscoItems(
                service: SmackServiceNode,
                xmppConnection: XMPPConnection, maxEntries: Int, startPoint: Jid,
                mappedNodes: MappedNodes, maxDepth: Int, maxSearchNodes: Int,
                protocol: String, visited: ConcurrentHashMap<Jid, Jid>, prefix: String,
        ): Boolean {
            val prefixes = prefix.split(",")

            // default is to stop when first one is found
            var stopOnFirst = true
            val stopOnFirstDefaultValue = JabberActivator.resources!!.getSettingsString(JINGLE_NODES_SEARCH_PREFIXES_STOP_ON_FIRST_PROP)
            if (stopOnFirstDefaultValue != null) {
                stopOnFirst = java.lang.Boolean.parseBoolean(stopOnFirstDefaultValue)
            }
            stopOnFirst = JabberActivator.getConfigurationService()!!
                    .getBoolean(JINGLE_NODES_SEARCH_PREFIXES_STOP_ON_FIRST_PROP, stopOnFirst)
            val items = DiscoverItems()
            (items as Stanza).to = startPoint

            val collector = xmppConnection.createStanzaCollectorAndSend(items)
            val result = try {
                collector.nextResult<DiscoverItems>((SmackConfiguration.getDefaultReplyTimeout() * 1.5).roundToLong())
            } finally {
                collector.cancel()
            }
            if (result !== null) {
                // first search priority items
                for (item in result.items) {
                    if (item != null) {
                        for (pref in prefixes) {
                            if (StringUtils.isNotEmpty(pref) && item.entityID.toString().startsWith(pref.trim { it <= ' ' })) {
                                SmackServiceNode.deepSearch(xmppConnection, maxEntries, item.entityID,
                                        mappedNodes, maxDepth, maxSearchNodes, protocol, visited)
                                if (stopOnFirst) return false // stop and don't continue
                            }
                        }
                    }
                }
                // now search rest
                for (item in result.items) {
                    if (item != null) {
                        // we may searched already this node if it starts with some of the prefixes
                        if (!visited.containsKey(item.entityID)) SmackServiceNode.deepSearch(xmppConnection, maxEntries, item.entityID,
                                mappedNodes, maxDepth, maxSearchNodes, protocol, visited)
                        if (stopOnFirst) return false // stop and don't continue
                    }
                }
            }
            // true we should continue searching
            return true
        }
    }
}