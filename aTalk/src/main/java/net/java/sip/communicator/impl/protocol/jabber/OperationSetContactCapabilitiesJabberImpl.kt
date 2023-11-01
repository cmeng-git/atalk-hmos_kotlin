/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.impl.protocol.jabber.caps.UserCapsNodeListener
import net.java.sip.communicator.service.protocol.AbstractOperationSetContactCapabilities
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony
import net.java.sip.communicator.service.protocol.OperationSetChatStateNotifications
import net.java.sip.communicator.service.protocol.OperationSetDesktopSharingServer
import net.java.sip.communicator.service.protocol.OperationSetMessageCorrection
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.OperationSetServerStoredContactInfo
import net.java.sip.communicator.service.protocol.OperationSetVideoTelephony
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener
import net.java.sip.communicator.util.ConfigurationUtils.isSendChatStateNotifications
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smackx.chatstates.ChatStateManager
import org.jivesoftware.smackx.message_correct.element.MessageCorrectExtension
import org.jxmpp.jid.Jid
import timber.log.Timber

/**
 * Represents an `OperationSet` to query the `OperationSet`s supported for a specific
 * Jabber `Contact`. The `OperationSet`s reported as supported for a specific Jabber
 * `Contact` are considered by the associated protocol provider to be capabilities possessed
 * by the Jabber `Contact` in question.
 *
 * @author Lyubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class OperationSetContactCapabilitiesJabberImpl(parentProvider: ProtocolProviderServiceJabberImpl) : AbstractOperationSetContactCapabilities<ProtocolProviderServiceJabberImpl?>(parentProvider), UserCapsNodeListener, ContactPresenceStatusListener {
    /**
     * The `discoveryManager` of [.parentProvider].
     */
    private var discoveryManager: ScServiceDiscoveryManager? = null

    /**
     * Initializes a new `OperationSetContactCapabilitiesJabberImpl` instance which is to be
     * provided by a specific `ProtocolProviderServiceJabberImpl`.
     *
     * parentProvider the `ProtocolProviderServiceJabberImpl` which will provide the new instance
     */
    init {
        val presenceOpSet = parentProvider.getOperationSet(OperationSetPresence::class.java)
        presenceOpSet?.addContactPresenceStatusListener(this)
        setOperationSetChatStateFeatures(isSendChatStateNotifications())
    }

    /**
     * Gets the `OperationSet` corresponding to the specified `Class` and supported
     * by the specified `Contact`. If the returned value is non-`null`, it indicates
     * that the `Contact` is considered by the associated protocol provider to possess the
     * `opsetClass` capability. Otherwise, the associated protocol provider considers
     * `contact` to not have the `opsetClass` capability.
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
     * @see AbstractOperationSetContactCapabilities.getOperationSet
    </U> */
    override fun <U : OperationSet?> getOperationSet(contact: Contact, opsetClass: Class<U>, online: Boolean): U? {
        val jid = parentProvider!!.getFullJidIfPossible(contact)
        return getOperationSet(jid, opsetClass, online)
    }

    /**
     * Gets the `OperationSet`s supported by a specific `Contact`. The returned
     * `OperationSet`s are considered by the associated protocol provider to capabilities
     * possessed by the specified `contact`.
     *
     * @param contact the `Contact` for which the supported `OperationSet` capabilities are to
     * be retrieved
     * @param online `true` if `contact` is online; otherwise, `false`
     * @return a `Map` listing the `OperationSet`s considered by the associated
     * protocol provider to be supported by the specified `contact` (i.e. to be
     * possessed as capabilities). Each supported `OperationSet` capability is
     * represented by a `Map.Entry` with key equal to the `OperationSet` class
     * name and value equal to the respective `OperationSet` instance
     * @see AbstractOperationSetContactCapabilities.getSupportedOperationSets
     */
    override fun getSupportedOperationSets(contact: Contact, online: Boolean): Map<String, OperationSet> {
        val jid = parentProvider!!.getFullJidIfPossible(contact)
        return getSupportedOperationSets(jid, online)
    }

    /**
     * Gets the `OperationSet`s supported by a specific `Contact`. The returned
     * `OperationSet`s are considered by the associated protocol provider to capabilities
     * possessed by the specified `contact`.
     *
     * @param jid the `Contact` for which the supported `OperationSet` capabilities are to be retrieved
     * @param online `true` if `contact` is online; otherwise, `false`
     * @return a `Map` listing the `OperationSet`s considered by the associated
     * protocol provider to be supported by the specified `contact` (i.e. to be
     * possessed as capabilities). Each supported `OperationSet` capability is
     * represented by a `Map.Entry` with key equal to the `OperationSet` class
     * name and value equal to the respective `OperationSet` instance
     * @see AbstractOperationSetContactCapabilities.getSupportedOperationSets
     */
    private fun getSupportedOperationSets(jid: Jid?, online: Boolean): Map<String, OperationSet> {
        val supportedOperationSets = parentProvider!!.getSupportedOperationSets()
        val supportedOperationSetCount = supportedOperationSets.size
        val contactSupportedOperationSets: MutableMap<String, OperationSet> = HashMap(supportedOperationSetCount)
        if (supportedOperationSetCount != 0) {
            for ((opsetClassName) in supportedOperationSets) {
                var opsetClass: Class<out OperationSet?>?
                try {
                    opsetClass = Class.forName(opsetClassName) as Class<out OperationSet?>
                } catch (cnfex: ClassNotFoundException) {
                    opsetClass = null
                    Timber.e(cnfex, "Failed to get OperationSet class for name: %s", opsetClassName)
                }

                if (opsetClass != null) {
                    val opset = getOperationSet(jid!!, opsetClass, online)
                    if (opset != null) {
                        contactSupportedOperationSets[opsetClassName] = opset
                    }
                }
            }
        }
        return contactSupportedOperationSets
    }

    /**
     * Gets the `OperationSet` corresponding to the specified `Class` and
     * supported by the specified `Contact`. If the returned value is non-`null`,
     * it indicates that the `Contact` is considered by the associated protocol provider
     * to possess the `opsetClass` capability. Otherwise, the associated protocol provider
     * considers `contact` to not have the `opsetClass` capability.
     *
     * @param <U> the type extending `OperationSet` for which the specified `contact` is
     * to be checked whether it possesses it as a capability
     * @param jid the Jabber id for which we're checking supported operation sets
     * @param opsetClass the `OperationSet` `Class` for which the specified `contact` is
     * to be checked whether it possesses it as a capability
     * @param online `true` if `contact` is online; otherwise, `false`
     * @return the `OperationSet` corresponding to the specified `opsetClass`
     * which is considered by the associated protocol provider to be possessed as a capability by
     * the specified `contact`; otherwise, `null`
     * @see AbstractOperationSetContactCapabilities.getOperationSet
    </U> */
    private fun <U : OperationSet?> getOperationSet(jid: Jid, opsetClass: Class<U>, online: Boolean): U? {
        var opset: U? = parentProvider!!.getOperationSet(opsetClass) ?: return null
        /*
         * If the specified contact is offline, don't query its features (they should fail anyway).
         */
        if (!online) return if (OFFLINE_OPERATION_SETS.contains(opsetClass)) opset else null
        /*
         * If we know the features required for the support of opsetClass, check whether the
         * contact supports them. Otherwise, presume the contact possesses the opsetClass
         * capability in light of the fact that we miss any knowledge of the opsetClass whatsoever.
         */
        if (OPERATION_SETS_TO_FEATURES.containsKey(opsetClass)) {
            val features = OPERATION_SETS_TO_FEATURES[opsetClass]

            /*
             * Either we've completely disabled the opsetClass capability by mapping it to the null
             * list of features or we've mapped it to an actual list of features which are to be
             * checked whether the contact supports them.
             */
            if (features == null || (features.isNotEmpty()
                            && !parentProvider.isFeatureListSupported(jid, *features))) {
                opset = null
            }
        }
        return opset
    }

    /**
     * Sets the `ScServiceDiscoveryManager` which is the `discoveryManager` of [.parentProvider].
     * Remove the existing one before replaced with the new request
     *
     * @param discManager the `ScServiceDiscoveryManager` which is the `discoveryManager` of
     * [.parentProvider]
     */
    fun setDiscoveryManager(discManager: ScServiceDiscoveryManager?) {
        if (discManager != null && discManager != discoveryManager) {
            if (discoveryManager != null) discoveryManager!!.removeUserCapsNodeListener(this)
            discoveryManager = discManager
            discoveryManager!!.addUserCapsNodeListener(this)
        }
    }

    /**
     * Notifies this listener that an `EntityCapsManager` has added a record for a specific
     * user about the caps node the user has.
     *
     * @param userJid the user (contact full Jid)
     * @param online indicates if the user is currently online
     * @see UserCapsNodeListener.userCapsNodeNotify
     */
    override fun userCapsNodeNotify(userJid: Jid?, online: Boolean) {
        /*
         * It doesn't matter to us whether a caps node has been added or removed for the specified
         * user because we report all changes.
         */
        val opsetPresence = parentProvider!!.getOperationSet(OperationSetPresence::class.java)
        if (opsetPresence != null) {
            val contact = opsetPresence.findContactByJid(userJid)

            // If the contact isn't null and is online we try to discover the new set of
            // operation sets and to notify interested parties. Otherwise we ignore the event.
            if (contact != null) {
                if (online) {
                    // when going online we have received a presence and make sure we discover
                    // this particular jid for getSupportedOperationSets
                    fireContactCapabilitiesEvent(contact, userJid, getSupportedOperationSets(userJid, online))
                } else {
                    // Need to wait a while before getSupportedOperationSets(); otherwise non-updated values
                    try {
                        Thread.sleep(100)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                    // when offline, we use the contact to find the most connected jid for SupportedOperationSets
                    fireContactCapabilitiesEvent(contact, userJid, getSupportedOperationSets(contact))
                }
            }
        }
    }

    /**
     * Update self when user goes offline.
     *
     * @param evt the `ContactPresenceStatusChangeEvent` that notified us
     */
    override fun contactPresenceStatusChanged(evt: ContactPresenceStatusChangeEvent) {
        if (evt.getNewStatus().status < PresenceStatus.ONLINE_THRESHOLD) {
            userCapsNodeNotify(evt.getJid(), false)
        }
    }

    /**
     * Fires event that contact capabilities has changed.
     *
     * @param user the user Jid to search for its contact.
     */
    fun fireContactCapabilitiesChanged(user: Jid) {
        val opsetPresence = parentProvider!!.getOperationSet(OperationSetPresence::class.java)
        if (opsetPresence != null) {
            val contact = opsetPresence.findContactByJid(user)

            // this called by received discovery info for particular jid so we use its online and
            // opSets for this particular jid
            var online = false
            val presence = Roster.getInstanceFor(parentProvider.connection).getPresence(user.asBareJid())
            if (presence != null) online = presence.isAvailable
            if (contact != null) {
                fireContactCapabilitiesEvent(contact, user, getSupportedOperationSets(user, online))
            }
        }
    }

    companion object {
        /**
         * The list of `OperationSet` capabilities presumed to be supported by a
         * `Contact` when it is offline.
         */
        private val OFFLINE_OPERATION_SETS: MutableSet<Class<out OperationSet>> = HashSet()

        /**
         * The `Map` which associates specific `OperationSet` classes with the
         * features to be supported by a `Contact` in order to consider the `Contact`
         * to possess the respective `OperationSet` capability.
         */
        private val OPERATION_SETS_TO_FEATURES: MutableMap<Class<out OperationSet>, Array<String>> = HashMap()

        init {
            OFFLINE_OPERATION_SETS.add(OperationSetBasicInstantMessaging::class.java)
            OFFLINE_OPERATION_SETS.add(OperationSetMessageCorrection::class.java)
            OFFLINE_OPERATION_SETS.add(OperationSetServerStoredContactInfo::class.java)
            OPERATION_SETS_TO_FEATURES[OperationSetBasicTelephony::class.java] = arrayOf(
                    ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE,
                    ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RTP,
                    ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RTP_AUDIO)
            OPERATION_SETS_TO_FEATURES[OperationSetVideoTelephony::class.java] = arrayOf(
                    ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE,
                    ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RTP,
                    ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RTP_VIDEO)
            OPERATION_SETS_TO_FEATURES[OperationSetDesktopSharingServer::class.java] = arrayOf(
                    ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE,
                    ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RTP,
                    ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RTP_VIDEO)
            OPERATION_SETS_TO_FEATURES[OperationSetMessageCorrection::class.java] = arrayOf(MessageCorrectExtension.NAMESPACE)
        }

        fun setOperationSetChatStateFeatures(isEnable: Boolean) {
            if (OPERATION_SETS_TO_FEATURES.containsKey(OperationSetChatStateNotifications::class.java)) {
                if (!isEnable) OPERATION_SETS_TO_FEATURES.remove(OperationSetChatStateNotifications::class.java)
            } else if (isEnable) {
                OPERATION_SETS_TO_FEATURES[OperationSetChatStateNotifications::class.java] = arrayOf(ChatStateManager.NAMESPACE)
            }
        }
    }
}