/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import android.text.TextUtils
import net.java.sip.communicator.impl.protocol.jabber.caps.UserCapsNodeListener
import net.java.sip.communicator.service.protocol.ContactResource
import net.java.sip.communicator.service.protocol.RegistrationState
import net.java.sip.communicator.service.protocol.event.ContactResourceEvent
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener
import org.jivesoftware.smackx.caps.EntityCapsManager
import org.jxmpp.jid.Jid

/**
 * Handles all the logic about mobile indicator for contacts. Has to modes, the first is searching
 * for particular string in the beginning of the contact resource and if found and this is the
 * highest priority then the contact in on mobile. The second one and the default one is searching
 * for strings in the node from the contact caps and if found and this is the most connected device
 * then the contact is a mobile one.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class MobileIndicator(
        /**
         * The parent provider.
         */
        private val parentProvider: ProtocolProviderServiceJabberImpl,
        /**
         * A reference to the ServerStoredContactListImpl instance.
         */
        private val ssclCallback: ServerStoredContactListJabberImpl,
) : RegistrationStateChangeListener, UserCapsNodeListener {

    /**
     * Whether we are using the default method for checking for mobile indicator.
     */
    private var isCapsMobileIndicator = true

    /**
     * The strings that we will check.
     */
    private var checkStrings: List<String>

    /**
     * Construct Mobile indicator.
     *
     * parentProvider the parent provider.
     * ssclCallback the callback for the contact list to obtain contacts.
     */
    init {
        val indicatorResource = parentProvider.accountID.accountProperties[MOBILE_INDICATOR_RESOURCE_ACC_PROP]
        if (!TextUtils.isEmpty(indicatorResource)) {
            isCapsMobileIndicator = false
            checkStrings = indicatorResource!!.split(",")
        }
        else {
            var indicatorCaps = parentProvider.accountID.accountProperties[MOBILE_INDICATOR_CAPS_ACC_PROP]
            if (TextUtils.isEmpty(indicatorCaps)) {
                indicatorCaps = "mobile, portable, android"
            }
            checkStrings = indicatorCaps!!.split(",")

            parentProvider.addRegistrationStateChangeListener(this)
        }
    }

    /**
     * Called when resources have been updated for a contact, on presence changed.
     *
     * @param contact the contact
     */
    fun resourcesUpdated(contact: ContactJabberImpl) {
        if (isCapsMobileIndicator) {
            // we update it also here, cause sometimes caps update comes before presence changed and
            // contacts are still offline and we dispatch wrong initial mobile indicator
            updateMobileIndicatorUsingCaps(contact.contactJid)
            return
        }

        // checks resource starts with String and is current highest priority
        var highestPriority = Int.MIN_VALUE
        val highestPriorityResources = ArrayList<ContactResource?>()
        val resources = contact.getResources() ?: return
        // sometimes volatile contacts do not have resources
        for (res in resources) {
            if (!res!!.presenceStatus.isOnline) continue
            val priority = res.priority
            if (priority >= highestPriority) {
                if (highestPriority != priority) highestPriorityResources.clear()
                highestPriority = priority
                highestPriorityResources.add(res)
            }
        }
        updateContactMobileStatus(contact, highestPriorityResources)
    }

    /**
     * Updates contact mobile status.
     *
     * @param contact the contact.
     * @param resources the list of contact resources.
     */
    private fun updateContactMobileStatus(contact: ContactJabberImpl, resources: List<ContactResource?>) {
        // check whether all are mobile
        var allMobile = false
        for (res in resources) {
            if (res!!.isMobile) allMobile = true
            else {
                allMobile = false
                break
            }
        }
        if (resources.isNotEmpty()) contact.isMobile = allMobile else contact.isMobile = false
    }

    /**
     * Checks a resource whether it is mobile or not, by checking the cache.
     *
     * @param fullJid the FullJid to check.
     * @return whether resource with that name is mobile or not.
     */
    fun isMobileResource(fullJid: Jid): Boolean {
        if (isCapsMobileIndicator) {
            val caps = EntityCapsManager.getNodeVerHashByJid(fullJid)
            return caps != null && containsStrings(caps.node, checkStrings)

//            XMPPTCPConnection xmppConnection = ssclCallback.getParentProvider().connection;
//            if (xmppConnection != null) {
//                EntityCapsManager capsManager = EntityCapsManager.getInstanceFor(xmppConnection);
//                DiscoverInfo caps = EntityCapsManager.getDiscoveryInfoByNodeVer(capsManager.getLocalNodeVer());
//                return (caps != null && containsStrings(caps.getNode(), checkStrings));
//            }
        }
        return startsWithStrings(fullJid.resourceOrEmpty.toString(), checkStrings)
    }

    /**
     * The method is called by a ProtocolProvider implementation whenever a change in the
     * registration state of the corresponding provider had occurred.
     *
     * @param evt ProviderStatusChangeEvent the event describing the status change.
     */
    override fun registrationStateChanged(evt: RegistrationStateChangeEvent) {
        if (evt.getNewState() == RegistrationState.REGISTERED) {
            parentProvider.discoveryManager!!.addUserCapsNodeListener(this)
        }
        else if (evt.getNewState() == RegistrationState.CONNECTION_FAILED
                || evt.getNewState() == RegistrationState.AUTHENTICATION_FAILED
                || evt.getNewState() == RegistrationState.UNREGISTERED) {
            parentProvider.discoveryManager?.removeUserCapsNodeListener(this)
        }
    }

    /**
     * Caps for user has been changed.
     *
     * @param userJid the user (full JID)
     * @param online indicates if the user for which we're notified is online
     */
    override fun userCapsNodeNotify(userJid: Jid?, online: Boolean) {
        updateMobileIndicatorUsingCaps(userJid)
    }

    /**
     * Update mobile indicator for contact, searching in contact caps.
     *
     * @param user the contact address with or without resource.
     */
    private fun updateMobileIndicatorUsingCaps(user: Jid?) {
        val contact = ssclCallback.findContactById(user!!.asBareJid()) ?: return

        // 1. Find most connected resources and if all are mobile
        var currentMostConnectedStatus = 0
        val mostAvailableResources = ArrayList<ContactResource>()

        for ((_, res) in contact.getResourcesMap()) {
            if (!res!!.presenceStatus.isOnline) continue

            // update the mobile indicator of connected resource, as caps have been updated
            val oldIndicator = res.isMobile
            res.isMobile = isMobileResource(res.fullJid)
            if (oldIndicator != res.isMobile) {
                contact.fireContactResourceEvent(ContactResourceEvent(contact, res,
                    ContactResourceEvent.RESOURCE_MODIFIED))
            }
            val status = res.presenceStatus.status
            if (status > currentMostConnectedStatus) {
                mostAvailableResources.clear()
                currentMostConnectedStatus = status
                mostAvailableResources.add(res)
            }
        }
        // check whether all are mobile
        updateContactMobileStatus(contact, mostAvailableResources)
    }

    companion object {
        /**
         * The account property to activate the mode for checking the resource names, the strings to
         * check whether a resource starts with can be entered separated by comas.
         */
        private const val MOBILE_INDICATOR_RESOURCE_ACC_PROP = "MOBILE_INDICATOR_RESOURCE"

        /**
         * The account property to activate the mode for checking the contact caps, the strings to check
         * whether a caps contains with can be entered separated by comas.
         */
        private const val MOBILE_INDICATOR_CAPS_ACC_PROP = "MOBILE_INDICATOR_CAPS"

        /**
         * Checks whether `value` starts one of the `checkStrs`> Strings.
         *
         * @param value the value to check
         * @param checkStrs an array of strings we are searching for.
         * @return `true` if `value` starts one of the Strings.
         */
        private fun startsWithStrings(value: String, checkStrs: List<String>): Boolean {
            for (str in checkStrs) {
                if (str.isNotEmpty() && value.startsWith(str)) return true
            }
            return false
        }

        /**
         * Checks whether `value` contains one of the `checkStrs`> Strings.
         *
         * @param value the value to check
         * @param checkStrs an array of strings we are searching for.
         * @return `true` if `value` contains one of the Strings.
         */
        private fun containsStrings(value: String, checkStrs: List<String>): Boolean {
            for (str in checkStrs) {
                if (str.isNotEmpty() && value.contains(str)) return true
            }
            return false
        }
    }
}