/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.call

import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony
import net.java.sip.communicator.service.protocol.OperationSetContactCapabilities
import net.java.sip.communicator.service.protocol.OperationSetDesktopSharingServer
import net.java.sip.communicator.service.protocol.OperationSetServerStoredContactInfo.DetailsResponseListener
import net.java.sip.communicator.service.protocol.OperationSetVideoTelephony
import net.java.sip.communicator.service.protocol.ServerStoredDetails.GenericDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.MobilePhoneDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.VideoDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.WorkPhoneDetail
import net.java.sip.communicator.util.ConfigurationUtils
import net.java.sip.communicator.util.UtilActivator
import net.java.sip.communicator.util.account.AccountUtils
import java.util.*

/**
 * Utility class used to check if there is a telephony service, video calls and
 * desktop sharing enabled for a protocol specific `MetaContact`.
 *
 * @author Damian Minkov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
open class MetaContactPhoneUtil
/**
 * Creates utility instance for `metaContact`.
 *
 * @param metaContact the metaContact checked in the utility.
 */
protected constructor(
        /**
         * The metaContact we are working on.
         */
        val metaContact: MetaContact) {
    /**
     * Returns the metaContact we work on.
     *
     * @return the metaContact we work on.
     */

    /**
     * The phones that have been discovered for metaContact child contacts.
     */
    private val phones = Hashtable<Contact, List<String?>>()

    /**
     * The video phones that have been discovered for metaContact child contacts.
     */
    private val videoPhones = Hashtable<Contact, List<String?>>()

    /**
     * True if there is any phone found for the metaContact.
     */
    private var hasPhones = false

    /**
     * True if there is any video phone found for the metaContact.
     */
    private var hasVideoDetail = false

    /**
     * Is routing for video enabled for any of the contacts of the metaContact.
     */
    private var routingForVideoEnabled = false

    /**
     * Is routing for desktop enabled for any of the contacts of the metaContact.
     */
    private var routingForDesktopEnabled = false

    /**
     * Returns localized addition phones list for contact, if any.
     * Return null if we have stopped searching and a listener is available and will be used to inform for results.
     *
     * @param contact the contact
     * @return localized addition phones list for contact, if any.
     */
    private fun getPhones(contact: Contact): List<String?>? {
        return getPhones(contact, null, true)
    }

    /**
     * Returns list of video phones for `contact`, localized.
     * Return null if we have stopped searching and a listener is available
     * and will be used to inform for results.
     *
     * @param contact the contact to check for video phones.
     * @param listener the `DetailsResponseListener` to listen for result details
     * @return list of video phones for `contact`, localized.
     */
    private fun getVideoPhones(contact: Contact, listener: DetailsResponseListener?): List<String?>? {
        if (!metaContact.containsContact(contact)) {
            return ArrayList()
        }
        if (videoPhones.containsKey(contact)) {
            return videoPhones[contact]
        }
        val phonesList = ContactPhoneUtil.getContactAdditionalPhones(contact, listener, true, true)
        if (phonesList == null) return null else if (phonesList.isNotEmpty()) hasVideoDetail = true
        videoPhones[contact] = phonesList

        // to check for routingForVideoEnabled prop
        isVideoCallEnabled(contact)
        // to check for routingForDesktopEnabled prop
        isDesktopSharingEnabled(contact)
        return phonesList
    }

    /**
     * List of phones for contact, localized if `localized` is `true`, and not otherwise.
     * Return null if we have stopped searching and a listener is available
     * and will be used to inform for results.
     *
     * @param contact the contact to check for video phones.
     * @param listener the `DetailsResponseListener` to listen for result details
     * @param localized whether to localize the phones, put a description text.
     * @return list of phones for contact.
     */
    private fun getPhones(contact: Contact, listener: DetailsResponseListener?, localized: Boolean): List<String?>? {
        if (!metaContact.containsContact(contact)) {
            return ArrayList()
        }
        if (phones.containsKey(contact)) {
            return phones[contact]
        }
        val phonesList = ContactPhoneUtil.getContactAdditionalPhones(contact, listener, false, localized)
        if (phonesList == null) return null else if (phonesList.isNotEmpty()) hasPhones = true
        phones[contact] = phonesList
        return phonesList
    }

    /**
     * Is video called is enabled for metaContact. If any of the child contacts has video enabled.
     *
     * @param listener the `DetailsResponseListener` to listen for result details
     * @return is video called is enabled for metaContact.
     */
    private fun isVideoCallEnabled(listener: DetailsResponseListener?): Boolean {
        // make sure children are checked
        return if (!checkMetaContactVideoPhones(listener)) false else (metaContact.getOpSetSupportedContact(OperationSetVideoTelephony::class.java) != null
                || routingForVideoEnabled
                || hasVideoDetail)
    }

    /**
     * Is video called is enabled for metaContact. If any of the child contacts has video enabled.
     *
     * @return is video called is enabled for metaContact.
     */
    val isVideoCallEnabled: Boolean
        get() = isVideoCallEnabled(null as DetailsResponseListener?)

    /**
     * Is video call enabled for contact.
     *
     * @param contact to check for video capabilities.
     * @return is video call enabled for contact.
     */
    private fun isVideoCallEnabled(contact: Contact): Boolean {
        if (!metaContact.containsContact(contact)) return false

        // make sure we have checked everything for the contact before continue
        if (!checkContactPhones(contact)) return false

        routingForVideoEnabled = ConfigurationUtils.isRouteVideoAndDesktopUsingPhoneNumberEnabled
                && phones.containsKey(contact)
                && phones[contact]!!.isNotEmpty()
                && AccountUtils.getOpSetRegisteredProviders(
                OperationSetVideoTelephony::class.java, null, null).isNotEmpty()

        return (contact.protocolProvider.getOperationSet(OperationSetVideoTelephony::class.java) != null
                && hasContactCapabilities(contact, OperationSetVideoTelephony::class.java)
                || routingForVideoEnabled)
    }

    /**
     * Is desktop sharing enabled for metaContact. If any of the child contacts has desktop sharing enabled.
     *
     * @param listener the `DetailsResponseListener` to listen for result details
     * @return is desktop share is enabled for metaContact.
     */
    private fun isDesktopSharingEnabled(listener: DetailsResponseListener?): Boolean {
        // make sure children are checked
        return if (!checkMetaContactVideoPhones(listener)) false else (metaContact.getDefaultContact(OperationSetDesktopSharingServer::class.java) != null || routingForDesktopEnabled
                || hasVideoDetail)
    }

    /**
     * Is desktop sharing enabled for metaContact. If any of the child contacts has desktop sharing enabled.
     *
     * @return is desktop share is enabled for metaContact.
     */
    val isDesktopSharingEnabled: Boolean
        get() = isDesktopSharingEnabled(null as DetailsResponseListener?)

    /**
     * Is desktop sharing enabled for contact.
     *
     * @param contact to check for desktop sharing capabilities.
     * @return is desktop sharing enabled for contact.
     */
    private fun isDesktopSharingEnabled(contact: Contact): Boolean {
        if (!metaContact.containsContact(contact)) return false

        // make sure we have checked everything for the contact
        // before continue
        if (!checkContactPhones(contact)) return false
        routingForDesktopEnabled = ConfigurationUtils.isRouteVideoAndDesktopUsingPhoneNumberEnabled
                && phones.containsKey(contact)
                && phones[contact]!!.isNotEmpty()
                && AccountUtils.getOpSetRegisteredProviders(
                OperationSetDesktopSharingServer::class.java, null, null).isNotEmpty()

        return (contact.protocolProvider.getOperationSet(OperationSetDesktopSharingServer::class.java) != null
                && hasContactCapabilities(contact, OperationSetDesktopSharingServer::class.java)
                || routingForDesktopEnabled)
    }

    /**
     * Is call enabled for metaContact. If any of the child contacts has call enabled.
     *
     * @param listener the `DetailsResponseListener` to listen for result details
     * @return is call enabled for metaContact.
     */
    fun isCallEnabled(listener: DetailsResponseListener?): Boolean {
        return isCallEnabled(listener, true)
    }

    /**
     * Is call enabled for metaContact. If any of the child contacts has call enabled.
     *
     * @param listener the `DetailsResponseListener` to listen for result details
     * @param checkForTelephonyOpSet whether we should check for registered
     * telephony operation sets that can be used to dial out, can be used
     * in plugins dialing out using methods outside the provider.
     * @return is call enabled for metaContact.
     */
    fun isCallEnabled(listener: DetailsResponseListener?, checkForTelephonyOpSet: Boolean): Boolean {
        // make sure children are checked
        if (!checkMetaContactPhones(listener)) return false
        var hasPhoneCheck = hasPhones
        if (checkForTelephonyOpSet) hasPhoneCheck = hasPhones && AccountUtils.getRegisteredProviders(OperationSetBasicTelephony::class.java).size > 0
        return (metaContact.getDefaultContact(OperationSetBasicTelephony::class.java) != null
                || hasPhoneCheck)
    }

    /**
     * Is call enabled for metaContact. If any of the child contacts has call enabled.
     *
     * @return is call enabled for metaContact.
     */
    val isCallEnabled: Boolean
        get() = isCallEnabled(null, true)

    /**
     * Is call enabled for metaContact. If any of the child contacts has call enabled.
     *
     * @param checkForTelephonyOpSet whether we should check for registered
     * telephony operation sets that can be used to dial out, can be used
     * in plugins dialing out using methods outside the provider.
     * @return is call enabled for metaContact.
     */
    fun isCallEnabled(checkForTelephonyOpSet: Boolean): Boolean {
        return isCallEnabled(null, checkForTelephonyOpSet)
    }

    /**
     * Is call enabled for contact.
     *
     * @param contact to check for call capabilities.
     * @return is call enabled for contact.
     */
    fun isCallEnabled(contact: Contact): Boolean {
        return if (!checkContactPhones(contact)) false else (contact.protocolProvider.getOperationSet(OperationSetBasicTelephony::class.java) != null
                && hasContactCapabilities(contact, OperationSetBasicTelephony::class.java))
    }
    /**
     * Checking all contacts for the metaContact.
     * Return `false` if there are listeners added for a contact
     * and we need to stop executions cause listener will be used to be informed for result.
     *
     * l the `DetailsResponseListener` to listen for further details
     * @return whether to continue or listeners present and will be informed for result.
     */
    /**
     * Checking all contacts for the metaContact.
     * Return `false` if there are listeners added for a contact
     * and we need to stop executions cause listener will be used to be informed for result.
     *
     * @return whether to continue or listeners present and will be informed for result.
     */
    private fun checkMetaContactPhones(l: DetailsResponseListener? = null): Boolean {
        val contactIterator = metaContact.getContacts()
        while (contactIterator.hasNext()) {
            val contact = contactIterator.next()
            if (phones.containsKey(contact)) continue
            getPhones(contact!!, l, false) ?: return false
        }
        return true
    }

    /**
     * Checking all contacts for the metaContact.
     * Return `false` if there are listeners added for a contact
     * and we need to stop executions cause listener will be used to be informed for result.
     *
     * @param l the `DetailsResponseListener` to listen for further details
     * @return whether to continue or listeners present and will be informed for result.
     */
    private fun checkMetaContactVideoPhones(l: DetailsResponseListener?): Boolean {
        val contactIterator = metaContact.getContacts()
        while (contactIterator.hasNext()) {
            val contact = contactIterator.next()
            if (videoPhones.containsKey(contact)) continue
            getVideoPhones(contact!!, l) ?: return false
        }
        return true
    }

    /**
     * Checking contact for phones.
     * Return `false` if there are listeners added for the contact
     * and we need to stop executions cause listener will be used to be informed for result.
     *
     * @return whether to continue or listeners present and will be informed for result.
     */
    private fun checkContactPhones(contact: Contact): Boolean {
        if (!phones.containsKey(contact)) {
            val phones = getPhones(contact) ?: return false

            // to check for routingForVideoEnabled prop
            isVideoCallEnabled(contact)
            // to check for routingForDesktopEnabled prop
            isDesktopSharingEnabled(contact)
        }
        return true
    }

    /**
     * Returns `true` if `Contact` supports the specified `OperationSet`, `false` otherwise.
     *
     * @param contact contact to check
     * @param opSet `OperationSet` to search for
     * @return Returns `true` if `Contact` supports the specified
     * `OperationSet`, `false` otherwise.
     */
    private fun hasContactCapabilities(contact: Contact, opSet: Class<out OperationSet>): Boolean {
        val capOpSet = contact.protocolProvider.getOperationSet(OperationSetContactCapabilities::class.java)

        // assume contact has OpSet capabilities if null
        return if (capOpSet == null) {
            true
        } else {
            capOpSet.getOperationSet(contact, opSet) != null
        }
    }

    /**
     * Returns localized phone number.
     *
     * @param d the detail.
     * @return the localized phone number.
     */
    protected fun getLocalizedPhoneNumber(d: GenericDetail?): String? {
        return when (d) {
            is WorkPhoneDetail -> {
                UtilActivator.resources.getI18NString("service.gui.WORK_PHONE")
            }
            is MobilePhoneDetail -> {
                UtilActivator.resources.getI18NString("service.gui.MOBILE_PHONE")
            }
            is VideoDetail -> {
                UtilActivator.resources.getI18NString("service.gui.VIDEO_PHONE")
            }
            else -> {
                UtilActivator.resources.getI18NString("service.gui.HOME")
            }
        }
    }

    companion object {
        /**
         * Obtains the util for `metaContact`
         *
         * @param metaContact the metaContact.
         * @return ContactPhoneUtil for the `metaContact`.
         */
        fun getPhoneUtil(metaContact: MetaContact): MetaContactPhoneUtil {
            return MetaContactPhoneUtil(metaContact)
        }
    }
}