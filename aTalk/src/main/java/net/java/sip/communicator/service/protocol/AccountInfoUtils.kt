/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.ServerStoredDetails.AddressDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.BirthDateDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.DisplayNameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.EmailAddressDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.FirstNameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.GenderDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.ImageDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.LastNameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.WorkAddressDetail

/**
 * Utility class that would give to interested parties an easy access to some of most popular
 * account details, like : first name, last name, birth date, image, etc.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
object AccountInfoUtils {
    /**
     * Returns the first name of the account, to which the given accountInfoOpSet belongs.
     *
     * @param accountInfoOpSet The account info operation set corresponding to the searched account.
     * @return the first name of the account, to which the given accountInfoOpSet belongs.
     */
    fun getFirstName(accountInfoOpSet: OperationSetServerStoredAccountInfo): String? {
        var firstName: FirstNameDetail? = null
        val firstNameDetails = accountInfoOpSet.getDetails(FirstNameDetail::class.java)
        if (firstNameDetails != null && firstNameDetails.hasNext()) firstName = firstNameDetails.next() as FirstNameDetail?
        return firstName?.toString()
    }

    /**
     * Returns the last name of the account, to which the given accountInfoOpSet belongs.
     *
     * @param accountInfoOpSet The account info operation set corresponding to the searched account.
     * @return the last name of the account, to which the given accountInfoOpSet belongs.
     */
    fun getLastName(accountInfoOpSet: OperationSetServerStoredAccountInfo): String? {
        var lastName: LastNameDetail? = null
        val lastNameDetails = accountInfoOpSet.getDetails(LastNameDetail::class.java)
        if (lastNameDetails != null && lastNameDetails.hasNext()) lastName = lastNameDetails.next() as LastNameDetail?
        return lastName?.getString()
    }

    /**
     * Returns the display name of the account, to which the given accountInfoOpSet belongs.
     *
     * @param accountInfoOpSet The account info operation set corresponding to the searched account.
     * @return the display name of the account, to which the given accountInfoOpSet belongs.
     */
    fun getDisplayName(accountInfoOpSet: OperationSetServerStoredAccountInfo): String? {
        var displayName: DisplayNameDetail? = null
        val displayNameDetails = accountInfoOpSet.getDetails(DisplayNameDetail::class.java)
        if (displayNameDetails != null && displayNameDetails.hasNext()) displayName = displayNameDetails.next() as DisplayNameDetail?
        return displayName?.getString()
    }

    /**
     * Returns the image of the account, to which the given accountInfoOpSet belongs.
     *
     * @param accountInfoOpSet The account info operation set corresponding to the searched account.
     * @return the image of the account, to which the given accountInfoOpSet belongs.
     */
    fun getImage(accountInfoOpSet: OperationSetServerStoredAccountInfo): ByteArray? {
        var image: ImageDetail? = null
        val imageDetails = accountInfoOpSet.getDetails(ImageDetail::class.java)
        if (imageDetails != null && imageDetails.hasNext()) image = imageDetails.next() as ImageDetail?
        return image?.getBytes()
    }

    /**
     * Returns the birth date of the account, to which the given accountInfoOpSet belongs.
     *
     * @param accountInfoOpSet The account info operation set corresponding to the searched account.
     * @return the birth date of the account, to which the given accountInfoOpSet belongs.
     */
    fun getBirthDate(accountInfoOpSet: OperationSetServerStoredAccountInfo): Any? {
        var date: BirthDateDetail? = null
        val dateDetails = accountInfoOpSet.getDetails(BirthDateDetail::class.java)
        if (dateDetails != null && dateDetails.hasNext()) date = dateDetails.next() as BirthDateDetail?
        return date?.getCalendar()
    }

    /**
     * Returns the gender of the account, to which the given accountInfoOpSet belongs.
     *
     * @param accountInfoOpSet The account info operation set corresponding to the searched account.
     * @return the gender of the account, to which the given accountInfoOpSet belongs.
     */
    fun getGender(accountInfoOpSet: OperationSetServerStoredAccountInfo): String? {
        var gender: GenderDetail? = null
        val genderDetails = accountInfoOpSet.getDetails(GenderDetail::class.java)
        if (genderDetails != null && genderDetails.hasNext()) {
            gender = genderDetails.next() as GenderDetail?
        }
        return gender?.getGender()
    }

    /**
     * Returns the address of the account, to which the given accountInfoOpSet belongs.
     *
     * @param accountInfoOpSet The account info operation set corresponding to the searched account.
     * @return the address of the account, to which the given accountInfoOpSet belongs.
     */
    fun getAddress(accountInfoOpSet: OperationSetServerStoredAccountInfo): String? {
        var address: AddressDetail? = null
        val addressDetails = accountInfoOpSet.getDetails(AddressDetail::class.java)
        if (addressDetails != null && addressDetails.hasNext()) address = addressDetails.next() as AddressDetail?
        return address?.getAddress()
    }

    /**
     * Returns the work address of the account, to which the given accountInfoOpSet belongs.
     *
     * @param accountInfoOpSet The account info operation set corresponding to the searched account.
     * @return the work address of the account, to which the given accountInfoOpSet belongs.
     */
    fun getWorkAddress(accountInfoOpSet: OperationSetServerStoredAccountInfo): String? {
        var address: WorkAddressDetail? = null
        val addressDetails = accountInfoOpSet.getDetails(WorkAddressDetail::class.java)
        if (addressDetails != null && addressDetails.hasNext()) address = addressDetails.next() as WorkAddressDetail?
        return address?.getAddress()
    }

    /**
     * Returns the email address of the account, to which the given accountInfoOpSet belongs.
     *
     * @param accountInfoOpSet The account info operation set corresponding to the searched account.
     * @return the email address of the account, to which the given accountInfoOpSet belongs.
     */
    fun getEmailAddress(accountInfoOpSet: OperationSetServerStoredAccountInfo): String? {
        var address: EmailAddressDetail? = null
        val addressDetails = accountInfoOpSet.getDetails(EmailAddressDetail::class.java)
        if (addressDetails != null && addressDetails.hasNext()) address = addressDetails.next() as EmailAddressDetail?
        return address?.getEMailAddress()
    }
}