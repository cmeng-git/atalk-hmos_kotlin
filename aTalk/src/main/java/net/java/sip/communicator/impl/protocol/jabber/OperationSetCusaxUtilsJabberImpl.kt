/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.OperationSetCusaxUtils
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.util.call.ContactPhoneUtil.getContactAdditionalPhones

/**
 * The `OperationSetCusaxUtilsJabberImpl` provides utility methods related to the Jabber CUSAX implementation.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class OperationSetCusaxUtilsJabberImpl : OperationSetCusaxUtils {
    /**
     * Checks if the given `detailAddress` exists in the given `contact` details.
     *
     * @param contact the `Contact`, which details to check
     * @param detailAddress the detail address we're looking for
     * @return `true` if the given `detailAdress` exists in the details of the given `contact`
     */
    override fun doesDetailBelong(contact: Contact?, detailAddress: String?): Boolean {
        val contactPhones = getContactAdditionalPhones(contact!!, null, false, false)
        if (contactPhones == null || contactPhones.size <= 0) return false
        val phonesIter = contactPhones.iterator()
        while (phonesIter.hasNext()) {
            val phone = phonesIter.next()
            val normalizedPhone = JabberActivator.phoneNumberI18nService!!.normalize(phone).toString()
            if (phone == detailAddress || normalizedPhone == detailAddress || detailAddress!!.contains(phone) || detailAddress.contains(normalizedPhone)) return true
        }
        return false
    }

    /**
     * Returns the linked CUSAX provider for this protocol provider.
     *
     * @return the linked CUSAX provider for this protocol provider or null if such isn't specified
     */
    override fun getLinkedCusaxProvider(): ProtocolProviderService? {
        return null
    }
}