/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.impl.protocol.jabber.caps.UserCapsNodeListener
import net.java.sip.communicator.service.protocol.OperationSetContactCapabilities
import okhttp3.internal.notifyAll
import okhttp3.internal.wait
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.jivesoftware.smack.SmackException.NoResponseException
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.StanzaListener
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smack.filter.AndFilter
import org.jivesoftware.smack.filter.StanzaExtensionFilter
import org.jivesoftware.smack.filter.StanzaTypeFilter
import org.jivesoftware.smack.packet.ExtensionElement
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smackx.caps.EntityCapsManager
import org.jivesoftware.smackx.caps.cache.EntityCapsPersistentCache
import org.jivesoftware.smackx.caps.cache.SimpleDirectoryPersistentCache
import org.jivesoftware.smackx.caps.packet.CapsExtension
import org.jivesoftware.smackx.disco.NodeInformationProvider
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager
import org.jivesoftware.smackx.disco.packet.DiscoverInfo
import org.jivesoftware.smackx.disco.packet.DiscoverItems
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.DomainBareJid
import org.jxmpp.jid.Jid
import org.jxmpp.util.cache.LruCache
import timber.log.Timber
import java.io.File
import java.util.*

/**
 * An wrapper to smack's default [ServiceDiscoveryManager] that adds support for
 * XEP-0030: Service Discovery.
 * XEP-0115: Entity Capabilities
 *
 * This work is based on Jonas Adahl's smack fork.
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
class ScServiceDiscoveryManager(
        /**
         * The parent provider
         */
        private val parentProvider: ProtocolProviderServiceJabberImpl,
        /**
         * The [XMPPConnection] that this manager is responsible for.
         */
        private val connection: XMPPConnection?,
        featuresToRemove: Array<String>?, featuresToAdd: Array<String>?, cacheNonCaps: Boolean,
) : NodeInformationProvider {
    /**
     * The flag which indicates whether we are currently storing no nodeVer caps.
     */
    private val cacheNonCaps: Boolean

    /**
     * The `EntityCapsManager` used by this instance to handle entity capabilities.
     */
    private val mEntityCapsManager: EntityCapsManager

    /**
     * The [ServiceDiscoveryManager] that we are wrapping.
     */
    private val discoveryManager: ServiceDiscoveryManager

    /**
     * The runnable responsible for retrieving discover info.
     */
    private val retriever = DiscoveryInfoRetriever()

    /**
     * A [List] of the identities we use in our disco answers.
     */
    private val identities: MutableList<DiscoverInfo.Identity>

    /**
     * The list of `UserCapsNodeListener`s interested in events notifying about
     * possible changes in the list of user caps nodes of this `EntityCapsManager`.
     */
    private val userCapsNodeListeners = LinkedList<UserCapsNodeListener>()

    /**
     * An empty array of <code>UserCapsNodeListener</code> elements explicitly defined
     * in order to reduce unnecessary allocations.
     */
    private val NO_USER_CAPS_NODE_LISTENERS = emptyArray<UserCapsNodeListener>()

    /**
     * Persistent Storage for ScServiceDiscovery, created per account. Service Discover Features
     * are defined by the account's server capability.
     */
    var discoInfoPersistentStore: File? = null
        private set

    /**
     * Creates a new `ScServiceDiscoveryManager` wrapping the default discovery manager of
     * the specified `connection`.
     *
     * parentProvider the parent provider that creates discovery manager.
     * connection Smack connection object that will be used by this instance to handle XMPPTCP connection.
     * featuresToRemove an array of `String`s representing the features to be removed from the
     * `ServiceDiscoveryManager` of the specified `connection` which is to be
     * wrapped by the new instance
     * featuresToAdd an array of `String`s representing the features to be added to the new instance
     * and to the `ServiceDiscoveryManager` of the specified `connection` which
     * is to be wrapped by the new instance
     */
    init {

        /* setup EntityCapsManager persistent store for XEP-0115: Entity Capabilities */
        // initEntityPersistentStore(); do it in ProtocolProvideServiceJabberImpl

        /* setup persistent store for XEP-0030: Service Discovery */
        initDiscoInfoPersistentStore()
        discoveryManager = ServiceDiscoveryManager.getInstanceFor(connection)
        identities = ArrayList()
        this.cacheNonCaps = cacheNonCaps

        // Sync DiscoverInfo.Identity with ServiceDiscoveryManager that has been initialized in
        // ProtocolProviderServiceImpl #ServiceDiscoveryManager.setDefaultIdentity().
        val identity = DiscoverInfo.Identity(
                "client", discoveryManager.identityName, discoveryManager.identityType)
        identities.add(identity)

        // add support for Entity Capabilities
        discoveryManager.addFeature(CapsExtension.NAMESPACE)

        /*
         * Reflect featuresToRemove and featuresToAdd before updateEntityCapsVersion() in order to
         * persist only the complete node#ver association with our own DiscoverInfo. Otherwise,
         * we'd persist all intermediate ones upon each addFeature() and removeFeature().
         */
        // featuresToRemove
        if (featuresToRemove != null) {
            for (featureToRemove in featuresToRemove) discoveryManager.removeFeature(featureToRemove)
        }
        // featuresToAdd
        if (featuresToAdd != null) {
            for (featureToAdd in featuresToAdd) if (!discoveryManager.includesFeature(featureToAdd)) discoveryManager.addFeature(featureToAdd)
        }

        // updateEntityCapsVersion(); cmeng: auto done in mEntityCapsManager init statement
        mEntityCapsManager = EntityCapsManager.getInstanceFor(connection)
        mEntityCapsManager.enableEntityCaps()

        // Listener for received cap packages and take necessary action
        connection!!.addAsyncStanzaListener(CapsStanzaListener(), PRESENCES_WITH_CAPS)
    }

    /**
     * Registers that a new feature is supported by this XMPP entity. When this client is queried
     * for its information the registered features will be answered.
     *
     * Since no packet is actually sent to the server it is safe to perform this operation before
     * logging to the server. In fact, you may want to configure the supported features before
     * logging to the server so that the information is already available if it is required upon login.
     *
     * @param feature the feature to register as supported.
     */
    fun addFeature(feature: String?) {
        discoveryManager.addFeature(feature)
    }

    /**
     * Returns `true` if the specified feature is registered in our
     * [ServiceDiscoveryManager] and `false` otherwise.
     *
     * @param feature the feature to look for.
     * @return a boolean indicating if the specified featured is registered or not.
     */
    fun includesFeature(feature: String?): Boolean {
        return discoveryManager.includesFeature(feature)
    }

    /**
     * Removes the specified feature from the supported features by the encapsulated ServiceDiscoveryManager.
     *
     *
     * Since no packet is actually sent to the server it is safe to perform this operation before
     * logging to the server.
     *
     * @param feature the feature to remove from the supported features.
     */
    fun removeFeature(feature: String?) {
        discoveryManager.removeFeature(feature)
    }

    /**
     * ============================================
     * NodeInformationProvider implementation for getNode....()
     *
     * Returns a list of the Items [org.jivesoftware.smackx.disco.packet.DiscoverItems.Item]
     * defined in the node or in other words `null` since we don't support any.
     *
     * @return always `null` since we don't support items.
     */
    override fun getNodeItems(): List<DiscoverItems.Item>? {
        return null
    }

    /**
     * Returns a list of the features defined in the node. For example, the entity caps protocol
     * specifies that an XMPP client should answer with each feature supported by the client version or extension.
     *
     * @return a list of the feature strings defined in the node.
     */
    override fun getNodeFeatures(): List<String> {
        return discoveryManager.features
    }

    /**
     * Returns a list of the identities defined in the node. For example, the x-command protocol
     * must provide an identity of category automation and type command-node for each command.
     *
     * @return a list of the Identities defined in the node.
     */
    override fun getNodeIdentities(): List<DiscoverInfo.Identity> {
        return identities
    }

    /**
     * Returns a list of the stanza(/packet) extensions defined in the node.
     *
     * @return a list of the stanza(/packet) extensions defined in the node.
     */
    override fun getNodePacketExtensions(): List<ExtensionElement>? {
        return null
    }
    /* === End of NodeInformationProvider =================== */
    /**
     * Returns the discovered information of a given XMPP entity addressed by its JID.
     *
     * @param entityJid the address of the XMPP entity. Buddy Jid should be FullJid
     * @return the discovered information.
     * @throws XMPPErrorException if the operation failed for some reason.
     * @throws NoResponseException if there was no response from the server.
     * @throws NotConnectedException if there is no connection
     * @throws InterruptedException if there is an Exception
     */
    @Throws(NoResponseException::class, XMPPErrorException::class, NotConnectedException::class, InterruptedException::class)
    fun discoverInfo(entityJid: Jid): DiscoverInfo? {
        // Check if we have it cached in the Entity Capabilities Manager
        var discoverInfo = EntityCapsManager.getDiscoverInfoByUser(entityJid)
        if (discoverInfo !== null) {
            return discoverInfo
        }

        // Try to get the nvh if it's known, otherwise null is returned e.g. for services
        val nvh = EntityCapsManager.getNodeVerHashByJid(entityJid)

        // if nvh is null; try to retrieve from local nonCapsCache
        if (cacheNonCaps && nvh == null) {
            discoverInfo = getDiscoverInfoByEntity(entityJid)
            if (discoverInfo !== null) return discoverInfo
        }

        // Not found: Then discover by requesting the information from the remote entity allowing only 10S for blocking access
        discoverInfo = getRemoteDiscoverInfo(entityJid, ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT)
        if (discoverInfo !== null) {
            // store in local nonCapsCache only if (nvh == null)
            if (nvh == null && cacheNonCaps) {
                addDiscoverInfoByEntity(entityJid, discoverInfo)
            }
            return discoverInfo
        }
        Timber.w("Failed to get DiscoverInfo for: %s", entityJid)
        return null
    }

    /**
     * Returns the discovered information of a given XMPP entity addressed by its JID if locally
     * cached, otherwise schedules for retrieval.
     *
     * @param entityJid the address of the XMPP entity. Buddy Jid should be FullJid
     * @return the discovered information.
     */
    fun discoverInfoNonBlocking(entityJid: Jid): DiscoverInfo? {
        // Check if we have it cached in the Entity Capabilities Manager
        var discoverInfo = EntityCapsManager.getDiscoverInfoByUser(entityJid)
        if (discoverInfo !== null) {
            return discoverInfo
        }

        // Try to get the nvh if it's known, otherwise null is returned  i.e. for services
        val nvh = EntityCapsManager.getNodeVerHashByJid(entityJid)

        // if nvh is null; try to retrieve from local nonCapsCache
        if (cacheNonCaps && nvh == null) {
            discoverInfo = getDiscoverInfoByEntity(entityJid)
            if (discoverInfo !== null) return discoverInfo
        }

        // add to retrieve discovery thread
        retriever.addEntityForRetrieve(entityJid)
        return null
    }

    /**
     * Returns the discovered information of a given XMPP entity addressed by its JID and note attribute.
     * Use this message only when trying to query information which is not directly addressable.
     *
     * @param entityJid the address of the XMPP entity; must be FullJid unless it is for services
     * @param timeout variable timeout to wait: default 10S for blocking and 30S for non-blocking access
     * @return the discovered information.
     * @throws XMPPErrorException if the operation failed for some reason.
     * @throws NoResponseException if there was no response from the server.
     * @throws NotConnectedException not connection exception
     * @throws InterruptedException Interrupt
     */
    @Throws(NoResponseException::class, XMPPErrorException::class, NotConnectedException::class, InterruptedException::class)
    private fun getRemoteDiscoverInfo(entityJid: Jid, timeout: Long): DiscoverInfo {
        // cmeng - "item-not-found" for request on a 5-second wait timeout. Actually server does
        // reply @ 28 seconds after disco#info is sent
        connection!!.replyTimeout = timeout.toLong()
        Timber.w("### Remote discovery for: %s", entityJid)
        val discoInfo = discoveryManager.discoverInfo(entityJid)
        connection.replyTimeout = ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT
        return discoInfo
    }

    /**
     * Returns the discovered items of a given XMPP entity addressed by its JID.
     *
     * @param entityJid the address of the XMPP entity.
     * @return the discovered information.
     * @throws XMPPErrorException if the operation failed for some reason.
     * @throws NoResponseException if there was no response from the server.
     * @throws NotConnectedException Not connection
     * @throws InterruptedException Interrupt
     */
    @Throws(NoResponseException::class, XMPPErrorException::class, NotConnectedException::class, InterruptedException::class)
    fun discoverItems(entityJid: Jid?): DiscoverItems {
        return discoveryManager.discoverItems(entityJid)
    }

    /**
     * Returns the discovered items of a given XMPP entity addressed by its JID and note attribute.
     * Use this message only when trying to query information which is not directly addressable.
     *
     * @param entityJid the address of the XMPP entity.
     * @param node the attribute that supplements the 'jid' attribute.
     * @return the discovered items.
     * @throws XMPPErrorException if the operation failed for some reason.
     * @throws NoResponseException if there was no response from the server.
     * @throws NotConnectedException Not connection
     * @throws InterruptedException Interrupt
     */
    @Throws(NoResponseException::class, XMPPErrorException::class, NotConnectedException::class, InterruptedException::class)
    fun discoverItems(entityJid: Jid?, node: String?): DiscoverItems {
        return discoveryManager.discoverItems(entityJid, node)
    }

    /**
     * Returns `true` if `jid` supports the specified `feature` and
     * `false` otherwise. The method may check the information locally if we've already
     * cached this `jid`'s disco info, or retrieve it from the network.
     *
     * @param jid the jabber ID we'd like to test for support
     * @param feature the URN feature we are interested in
     * @return true if `jid` is discovered to support `feature` and `false` otherwise.
     */
    fun supportsFeature(jid: Jid, feature: String?): Boolean {
        var info: DiscoverInfo? = null
        try {
            try {
                info = discoverInfo(jid)
            } catch (e: NoResponseException) {
                e.printStackTrace()
            } catch (e: NotConnectedException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        } catch (ex: XMPPException) {
            Timber.i(ex, "failed to retrieve disco info for %s feature %s", jid, feature)
            return false
        }
        return info !== null && info.containsFeature(feature)
    }

    /**
     * Clears/stops what's needed.
     */
    fun stop() {
        retriever.stop()
    }
    // =========================================================
    /**
     * Adds a specific `UserCapsNodeListener` to the list of `UserCapsNodeListener`s
     * interested in events notifying about changes in the list of user caps nodes of the
     * `EntityCapsManager`.
     *
     * @param listener the `UserCapsNodeListener` which is interested in events notifying about
     * changes in the list of user caps nodes of this `EntityCapsManager`
     */
    fun addUserCapsNodeListener(listener: UserCapsNodeListener?) {
        if (listener == null) throw NullPointerException("listener")
        synchronized(userCapsNodeListeners) {
            if (!userCapsNodeListeners.contains(listener)) userCapsNodeListeners.add(listener)
        }
    }

    /**
     * Removes a specific `UserCapsNodeListener` from the list of
     * `UserCapsNodeListener`s interested in events notifying about changes in the list of
     * user caps nodes of this `EntityCapsManager`.
     *
     * @param listener the `UserCapsNodeListener` which is no longer interested in events notifying
     * about changes in the list of user caps nodes of this `EntityCapsManager`
     */
    fun removeUserCapsNodeListener(listener: UserCapsNodeListener?) {
        if (listener != null) {
            synchronized(userCapsNodeListeners) { userCapsNodeListeners.remove(listener) }
        }
    }

    /**
     * Alert listener that entity caps node of a user may have changed.
     *
     * @param user the user (FullJid): Can either be account or contact
     * @param online indicates if the user is online
     */
    private fun userCapsNodeNotify(user: Jid?, online: Boolean) {
        if (user != null) {
            // Fire userCapsNodeNotify.
            var listeners: Array<UserCapsNodeListener>
            synchronized(userCapsNodeListeners) {
                listeners = userCapsNodeListeners.toArray(NO_USER_CAPS_NODE_LISTENERS)
            }
            for (listener in listeners) listener.userCapsNodeNotify(user, online)
        }
    }

    /**
     * The [StanzaListener] that will be registering incoming caps.
     */
    private inner class CapsStanzaListener : StanzaListener {
        /**
         * Handles incoming presence packets with CapsExtension and alert listeners that the specific user caps
         * node may have changed.
         *
         * @param stanza the incoming presence `Packet` to be handled
         * @see StanzaListener.processStanza
         */
        override fun processStanza(stanza: Stanza) {
            if (!mEntityCapsManager.entityCapsEnabled()) return

            // Check it the packet indicates that the user is online. We will use this
            // information to decide if we're going to send the discover info request.
            val online = stanza is Presence && stanza.isAvailable
            val capsExtension = CapsExtension.from(stanza)
            val fromJid = stanza.from
            if (capsExtension != null && online) {
                /*
                 * Before Version 1.4 of XEP-0115: Entity Capabilities, the 'ver' attribute was
                 * generated differently and the 'hash' attribute was absent. The 'ver'
                 * attribute in Version 1.3 represents the specific version of the client and
                 * thus does not provide a way to validate the DiscoverInfo sent by the client.
                 * If EntityCapsManager  'hash' attribute, it will assume the legacy format and
                 * will not cache it because the DiscoverInfo to be received from the client
                 * later on will not be trustworthy.
                 */
                userCapsNodeNotify(fromJid, true)
            } else if (!online) {
                userCapsNodeNotify(fromJid, false)
            }
        }
    }

    /**
     * Setup the SimpleDirectoryPersistentCache store to support DiscoInfo persistent
     * store for fast discoInfo retrieval and bandwidth performance.
     *
     * Note: [.discoInfoStoreDirectory] directory is setup to contain all the disco#info
     * entities for each specific account and is being setup during the account login.
     */
    fun initDiscoInfoPersistentStore() {
        val userID = parentProvider.accountID.mUserID
        discoInfoPersistentStore = File(aTalkApp.globalContext.filesDir, "/discoInfoStore_$userID")
        if (!discoInfoPersistentStore!!.exists()) {
            if (!discoInfoPersistentStore!!.mkdir()) Timber.e("DiscoInfo Store directory creation error: %s", discoInfoPersistentStore!!.absolutePath)
        }
        if (discoInfoPersistentStore!!.exists()) {
            val persistentCache = SimpleDirectoryPersistentCache(discoInfoPersistentStore)
            setDiscoInfoPersistentStore(persistentCache)
        }
    }

    fun setDiscoInfoPersistentStore(cache: SimpleDirectoryPersistentCache?) {
        discoInfoPersistentCache = cache
    }

    fun clearDiscoInfoPersistentCache() {
        nonCapsCache.clear()
    }

    /**
     * Thread that runs the discovery info.
     */
    private inner class DiscoveryInfoRetriever : Runnable {
        /**
         * start/stop.
         */
        private var stopped = true

        /**
         * The thread that runs this dispatcher.
         */
        private var retrieverThread: Thread? = null

        /**
         * Entities to be processed and their nvh. HashMap so we can store null nvh.
         */
        private val entities = ArrayList<Jid?>()

        /**
         * Our capability operation set.
         */
        private var capabilitiesOpSet: OperationSetContactCapabilitiesJabberImpl? = null

        /**
         * Runs in a different thread.
         */
        override fun run() {
            try {
                stopped = false
                while (!stopped) {
                    var entityToProcess: Jid?
                    synchronized(entities) {
                        if (entities.size == 0) {
                            try {
                                entities.wait()
                            } catch (ex: InterruptedException) {
                                ex.printStackTrace()
                            }
                        }
                        entityToProcess = entities[0]
                        entities.remove(entityToProcess)
                    }
                    if (entityToProcess != null) {
                        requestDiscoveryInfo(entityToProcess!!)
                    }
                }
            } catch (t: Throwable) {
                // May happen on aTalk shutDown, where entities array outOfBound
                Timber.w("Error requesting discovery info, thread ended: %s", t.message)
            }
        }

        /**
         * Requests the discovery info and fires the event if retrieved.
         *
         * @param entityJid the entity to request
         */
        private fun requestDiscoveryInfo(entityJid: Jid) {
            try {
                // Discover by requesting the information from the remote entity;
                // will return null if no nvh in JID_TO_NODEVER_CACHE=>CAPS_CACHE
                val discoverInfo = getRemoteDiscoverInfo(entityJid,
                        ProtocolProviderServiceJabberImpl.SMACK_REPLY_EXTENDED_TIMEOUT_30)

                // (discoverInfo = null) if iq result with "item-not-found"
                if (discoverInfo !== null) {
                    if (cacheNonCaps) {
                        // Timber.w("Add discoverInfo with null nvh for: %s", entityJid);
                        addDiscoverInfoByEntity(entityJid, discoverInfo)
                    }

                    // fire the event
                    if (capabilitiesOpSet != null) {
                        capabilitiesOpSet!!.fireContactCapabilitiesChanged(entityJid)
                    }
                }
            } catch (e: NoResponseException) {
                // print discovery info errors only when trace is enabled
                Timber.log(TimberLog.FINER, e, "Error requesting discover info for %s", entityJid)
            } catch (e: NotConnectedException) {
                Timber.log(TimberLog.FINER, e, "Error requesting discover info for %s", entityJid)
            } catch (e: XMPPException) {
                Timber.log(TimberLog.FINER, e, "Error requesting discover info for %s", entityJid)
            } catch (e: InterruptedException) {
                Timber.log(TimberLog.FINER, e, "Error requesting discover info for %s", entityJid)
            }
        }

        /**
         * Queue entities for retrieval.
         *
         * @param entityJid the entity.
         */
        fun addEntityForRetrieve(entityJid: Jid?) {
            if (entityJid is BareJid) Timber.e("Warning! discoInfo for BareJid '%s' repeated access for every call!!!", entityJid.toString())
            synchronized(entities) {
                if (!entities.contains(entityJid)) {
                    entities.add(entityJid)
                    entities.notifyAll()
                    if (retrieverThread == null) {
                        start()
                    }
                }
            }
        }

        /**
         * Start thread.
         */
        private fun start() {
            capabilitiesOpSet = parentProvider.getOperationSet(OperationSetContactCapabilities::class.java) as OperationSetContactCapabilitiesJabberImpl?
            retrieverThread = Thread(this, ScServiceDiscoveryManager::class.java.name)
            retrieverThread!!.isDaemon = true
            retrieverThread!!.start()
        }

        /**
         * Stops and clears.
         */
        fun stop() {
            synchronized(entities) {
                stopped = true
                entities.notifyAll()
                retrieverThread = null
            }
        }
    }

    companion object {
        /**
         * The cache for storing service discoInfo without nodeVer e.g, proxy.atalk.org, conference.atalk.org.
         * Used only if [.cacheNonCaps] is `true`.
         */
        private val nonCapsCache = LruCache<Jid?, DiscoverInfo>(10000)
        private val PRESENCES_WITH_CAPS = AndFilter(StanzaTypeFilter.PRESENCE,
                StanzaExtensionFilter(CapsExtension.ELEMENT, CapsExtension.NAMESPACE))

        /**
         * An empty array of `UserCapsNodeListener` elements explicitly defined
         * in order to reduce unnecessary allocations.
         */
        private val NO_USER_CAPS_NODE_LISTENERS = arrayOfNulls<UserCapsNodeListener>(0)
        /**
         * persistentAvatarCache is used only by ScServiceDiscoveryManager for the specific account entities received
         */
        private var discoInfoPersistentCache: EntityCapsPersistentCache? = null

        /**
         * A single Persistent Storage for EntityCapsManager to save caps for all accounts
         */
        var entityPersistentStore: File? = null
            private set

        /**
         * Add DiscoverInfo to the database only if the entityJid is DomainBareJid.
         *
         * @param entityJid The entity Jid
         * @param info DiscInfo for the specified entity.
         */
        private fun addDiscoverInfoByEntity(entityJid: Jid, info: DiscoverInfo) {
            if (entityJid is DomainBareJid) {
                Timber.w("### Add discInfo for: %s", entityJid)
                nonCapsCache[entityJid] = info
                if (discoInfoPersistentCache != null) discoInfoPersistentCache!!.addDiscoverInfoByNodePersistent(entityJid.toString(), info)
            }
        }

        /**
         * Retrieve DiscoverInfo for a specific entity.
         *
         * @param entityJid The entity Jid i.e. DomainJid
         * @return The corresponding DiscoverInfo or null if none is known.
         */
        private fun getDiscoverInfoByEntity(entityJid: Jid?): DiscoverInfo? {
            // If not in nonCapsCache, try to retrieve the information from discoInfoPersistentCache using entityJid
            var info = nonCapsCache.lookup(entityJid)
            if (info === null && discoInfoPersistentCache != null) {
                info = discoInfoPersistentCache!!.lookup(entityJid.toString())
                // Promote the information to nonCapsCache if one was found
                if (info !== null) {
                    nonCapsCache[entityJid] = info
                }
            }

            // If we are able to retrieve information from one of the caches, copy it before returning
            if (info !== null) info = DiscoverInfo(info)
            return info
        }
        //==================================================================
        /**
         * Setup the SimpleDirectoryPersistentCache store to support EntityCapsManager persistent
         * store for fast Entity Capabilities and bandwidth improvement.
         * First initialize in [ProtocolProviderServiceJabberImpl.initSmackDefaultSettings]
         * to ensure Persistence store is setup before being access. If necessary later in
         * ServerPersistentStoresRefreshDialog.refreshCapsStore
         *
         * Note: [.entityStoreDirectory] is a single directory for all jabber accounts to contain
         * all the caps
         */
        fun initEntityPersistentStore() {
            entityPersistentStore = File(aTalkApp.globalContext.filesDir, "/entityStore")
            if (!entityPersistentStore!!.exists()) {
                if (!entityPersistentStore!!.mkdir()) Timber.e("Entity Store directory creation error: %s", entityPersistentStore!!.absolutePath)
            }
            if (entityPersistentStore!!.exists()) {
                val entityPersistentCache = SimpleDirectoryPersistentCache(entityPersistentStore)
                EntityCapsManager.setPersistentCache(entityPersistentCache)
            }
        }
    }
}