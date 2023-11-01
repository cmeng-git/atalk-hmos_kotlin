/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.call

import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.OperationSetServerStoredContactInfo
import net.java.sip.communicator.service.protocol.OperationSetServerStoredContactInfo.DetailsResponseListener
import net.java.sip.communicator.service.protocol.ServerStoredDetails.FaxDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.GenericDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.MobilePhoneDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.PagerDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.PhoneNumberDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.VideoDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.WorkPhoneDetail
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import timber.log.Timber

/**
 * Utility class used to check if there is a telephony service, video calls and
 * desktop sharing enabled for a protocol specific `Contact`.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
object ContactPhoneUtil {
    /**
     * Searches for phones for the contact.
     * Return null if we have stopped searching and a listener is available
     * and will be used to inform for results.
     *
     * @param contact the contact to check.
     * @param listener the `DetailsResponseListener` if we're interested
     * in obtaining results that came later
     * @param onlyVideo whether to include only video phones.
     * @param localized whether to localize phones.
     * @return list of phones, or null if we will use the listeners for the result.
     */
    fun getContactAdditionalPhones(contact: Contact, listener: DetailsResponseListener?,
            onlyVideo: Boolean, localized: Boolean): List<String>? {
        val infoOpSet = contact.protocolProvider.getOperationSet(OperationSetServerStoredContactInfo::class.java)
        val details: Iterator<GenericDetail?>?
        val phonesList = ArrayList<String>()
        if (infoOpSet != null) {
            try {
                if (listener != null) {
                    details = infoOpSet.requestAllDetailsForContact(contact, listener)
                    if (details == null) return null
                } else {
                    details = infoOpSet.getAllDetailsForContact(contact)
                }
                val phoneNumbers = ArrayList<String>()
                while (details!!.hasNext()) {
                    val d = details.next()
                    if (d is PhoneNumberDetail &&
                            d !is PagerDetail &&
                            d !is FaxDetail) {
                        val number = d.getNumber()
                        if (number != null && number.isNotEmpty()) {
                            if (d !is VideoDetail && onlyVideo) continue

                            // skip duplicate numbers
                            if (phoneNumbers.contains(number)) continue
                            phoneNumbers.add(number)
                            if (!localized) {
                                phonesList.add(number)
                                continue
                            }
                            phonesList.add(number + " (" + getLocalizedPhoneNumber(d) + ")")
                        }
                    }
                }
            } catch (t: Throwable) {
                Timber.e("Error obtaining server stored contact info")
            }
        }
        return phonesList
    }

    /**
     * Returns localized phone number.
     *
     * @param d the detail.
     * @return the localized phone number.
     */
    private fun getLocalizedPhoneNumber(d: GenericDetail?): String {
        return when (d) {
            is WorkPhoneDetail -> {
                aTalkApp.getResString(R.string.service_gui_WORK_PHONE)
            }
            is MobilePhoneDetail -> {
                aTalkApp.getResString(R.string.service_gui_MOBILE_PHONE)
            }
            is VideoDetail -> {
                aTalkApp.getResString(R.string.service_gui_VIDEO_PHONE)
            }
            else -> {
                aTalkApp.getResString(R.string.service_gui_HOME)
            }
        }
    }
}