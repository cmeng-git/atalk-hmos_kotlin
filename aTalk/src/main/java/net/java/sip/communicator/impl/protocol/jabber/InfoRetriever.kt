/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.ServerStoredDetails.AboutMeDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.AddressDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.BirthDateDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.CityDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.CountryDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.DisplayNameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.EmailAddressDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.FaxDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.FirstNameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.GenericDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.ImageDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.JobTitleDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.LastNameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.MiddleNameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.MobilePhoneDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.NameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.NicknameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.PagerDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.PhoneNumberDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.PostalCodeDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.ProvinceDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.URLDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.VideoDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.WorkAddressDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.WorkCityDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.WorkEmailAddressDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.WorkMobilePhoneDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.WorkOrganizationNameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.WorkPhoneDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.WorkPostalCodeDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.WorkProvinceDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.WorkVideoDetail
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.XmlEnvironment
import org.jivesoftware.smackx.avatar.vcardavatar.VCardAvatarManager
import org.jivesoftware.smackx.vcardtemp.packet.VCard
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.EntityBareJid
import timber.log.Timber
import java.net.MalformedURLException
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles and retrieves all info of our contacts or account info
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class InfoRetriever(
        /**
         * A callback to the Jabber provider that created us.
         */
        private val jabberProvider: ProtocolProviderServiceJabberImpl,
        ownerUin: EntityBareJid?,
) {

    // A linked list between contact/user and his details retrieved so far
    private val retrievedDetails = Hashtable<BareJid, MutableList<GenericDetail>>()

    /**
     * returns the user details from the specified class or its descendants the class is one from
     * the net.java.sip.communicator.service.protocol.ServerStoredDetails or implemented one in the
     * operation set for the user info
     *
     * @param uin String
     * @param detailClass Class
     * @return Iterator
     */
    fun <T : GenericDetail?> getDetailsAndDescendants(uin: BareJid, detailClass: Class<T>?): Iterator<T?> {
        val details = getUserDetails(uin)
        val result = LinkedList<T?>()
        for (item in details!!) if (detailClass!!.isInstance(item)) {
            val t = item as T
            result.add(t)
        }
        return result.iterator()
    }

    /**
     * returns the user details from the specified class exactly that class not its descendants
     *
     * @param uin String
     * @param detailClass Class
     * @return Iterator
     */
    fun getDetails(uin: BareJid, detailClass: Class<out GenericDetail?>?): Iterator<GenericDetail?> {
        val details = getUserDetails(uin)
        val result = LinkedList<GenericDetail>()

        // stop further retrieve from server if details is null or empty to prevent ANR when return from Account Settings
        if (details == null || details.isEmpty()) {
            retrievedDetails[uin] = result
        } else {
            for (item in details) if (detailClass == item.javaClass) result.add(item)
        }
        return result.iterator()
    }

    /**
     * request the full info for the given bareJid waits and return this details
     *
     * @param bareJid String
     * @return Vector the details
     */
    fun clearUserDetails(bareJid: BareJid): MutableList<GenericDetail>? {
        return retrievedDetails.remove(bareJid)
    }

    /**
     * request the full info for the given bareJid waits and return this details
     *
     * @param bareJid String
     * @return Vector the details
     */
    fun getUserDetails(bareJid: BareJid): MutableList<GenericDetail>? {
        return getCachedUserDetails(bareJid)
                ?: return retrieveDetails(bareJid)
    }

    /**
     * Retrieve details and return them return an empty list if none is found.
     * Note: Synchronized access to #retrieveDetails(BareJid bareJid) to prevent the retrieved
     * result is being overwritten by access from other Jid running on a separate thread
     * [ServerStoredContactListJabberImpl.ImageRetriever.run]
     *
     * @param bareJid the address to search for.
     * @return the details or empty list.
     */
    @Synchronized
    fun retrieveDetails(bareJid: BareJid): MutableList<GenericDetail>? {
        Timber.w(Exception("Retrieve Details (testing debug info: ignore): $bareJid"))
        val result = LinkedList<GenericDetail>()
        val connection = jabberProvider.connection
        if (connection == null || !connection.isAuthenticated) return null

        // Set the timeout to wait before considering vCard has time out - too long field ANR - use default instead
        // connection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_PACKET_REPLY_EXTENDED_TIMEOUT_30)
        Timber.d("Start loading VCard information for: %s", bareJid)
        val vCardAvatarManager = VCardAvatarManager.getInstanceFor(connection)
        val card = vCardAvatarManager.downloadVCard(bareJid)

        // Reset back to aTalk default
        // connection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_PACKET_REPLY_TIMEOUT_10)

        // cmeng - vCard can be null due to smack request response timeout (2017/11/29)
        // return an empty list if VCard fetching from server failed
        if (card === null) {
            Timber.w("Failed to download Vcard from server!")
            return result
        }
        val errMessage = "Unable to load details for contact $bareJid Exception: "
        var tmp = checkForFullName(card)
        if (tmp != null) result.add(DisplayNameDetail(tmp))
        tmp = card.firstName
        if (tmp != null) result.add(FirstNameDetail(tmp))
        tmp = card.middleName
        if (tmp != null) result.add(MiddleNameDetail(tmp))
        tmp = card.lastName
        if (tmp != null) result.add(LastNameDetail(tmp))
        tmp = card.nickName
        if (tmp != null) result.add(NicknameDetail(tmp))
        tmp = card.getField("BDAY")
        if (tmp != null) {
            val birthDateDetail: BirthDateDetail
            var birthDate: Date? = null
            try {
                val dateFormatMedium = SimpleDateFormat(BDAY_FORMAT_MEDIUM, Locale.US)
                birthDate = dateFormatMedium.parse(tmp)
            } catch (ex: ParseException) {
                try {
                    // take care of avatar date short format created by other clients i.e. 1992-01-03
                    val dateFormatShort = SimpleDateFormat(BDAY_FORMAT_SHORT, Locale.US)
                    birthDate = dateFormatShort.parse(tmp)
                } catch (e: ParseException) {
                    Timber.w("%s %s", errMessage, ex.message)
                }
            }
            if (birthDate != null) {
                val birthDateCalendar = Calendar.getInstance()
                birthDateCalendar.time = birthDate
                birthDateDetail = BirthDateDetail(birthDateCalendar)
            } else birthDateDetail = BirthDateDetail(tmp)
            result.add(birthDateDetail)
        }
        // Home Details addrField one of: POSTAL, PARCEL, (DOM | INTL), PREF, POBOX, EXTADR,
        // STREET, LOCALITY, REGION, PCODE, CTRY
        tmp = card.getAddressFieldHome("STREET")
        if (tmp != null) result.add(AddressDetail(tmp))
        tmp = card.getAddressFieldHome("LOCALITY")
        if (tmp != null) result.add(CityDetail(tmp))
        tmp = card.getAddressFieldHome("REGION")
        if (tmp != null) result.add(ProvinceDetail(tmp))
        tmp = card.getAddressFieldHome("PCODE")
        if (tmp != null) result.add(PostalCodeDetail(tmp))
        tmp = card.getAddressFieldHome("CTRY")
        if (tmp != null) result.add(CountryDetail(tmp))

        // phoneType one of
        // VOICE, FAX, PAGER, MSG, CELL, VIDEO, BBS, MODEM, ISDN, PCS, PREF
        tmp = card.getPhoneHome("VOICE")
        if (tmp != null) result.add(PhoneNumberDetail(tmp))
        tmp = card.getPhoneHome("VIDEO")
        if (tmp != null) result.add(VideoDetail(tmp))
        tmp = card.getPhoneHome("FAX")
        if (tmp != null) result.add(FaxDetail(tmp))
        tmp = card.getPhoneHome("PAGER")
        if (tmp != null) result.add(PagerDetail(tmp))
        tmp = card.getPhoneHome("CELL")
        if (tmp != null) result.add(MobilePhoneDetail(tmp))
        tmp = card.getPhoneHome("TEXT")
        if (tmp != null) result.add(MobilePhoneDetail(tmp))
        tmp = card.emailHome
        if (tmp != null) result.add(EmailAddressDetail(tmp))

        // Work Details addrField one of
        // POSTAL, PARCEL, (DOM | INTL), PREF, POBOX, EXTADR, STREET, LOCALITY, REGION, PCODE,
        // CTRY
        tmp = card.getAddressFieldWork("STREET")
        if (tmp != null) result.add(WorkAddressDetail(tmp))
        tmp = card.getAddressFieldWork("LOCALITY")
        if (tmp != null) result.add(WorkCityDetail(tmp))
        tmp = card.getAddressFieldWork("REGION")
        if (tmp != null) result.add(WorkProvinceDetail(tmp))
        tmp = card.getAddressFieldWork("PCODE")
        if (tmp != null) result.add(WorkPostalCodeDetail(tmp))

        // tmp = card.getAddressFieldWork("CTRY")
        // if(tmp != null)
        // 	result.add(new WorkCountryDetail(tmp)

        // phoneType one of
        // VOICE, FAX, PAGER, MSG, CELL, VIDEO, BBS, MODEM, ISDN, PCS, PREF
        tmp = card.getPhoneWork("VOICE")
        if (tmp != null) result.add(WorkPhoneDetail(tmp))
        tmp = card.getPhoneWork("VIDEO")
        if (tmp != null) result.add(WorkVideoDetail(tmp))
        tmp = card.getPhoneWork("FAX")
        if (tmp != null) result.add(WorkFaxDetail(tmp))
        tmp = card.getPhoneWork("PAGER")
        if (tmp != null) result.add(WorkPagerDetail(tmp))
        tmp = card.getPhoneWork("CELL")
        if (tmp != null) result.add(WorkMobilePhoneDetail(tmp))
        tmp = card.getPhoneWork("TEXT")
        if (tmp != null) result.add(WorkMobilePhoneDetail(tmp))
        tmp = card.emailWork
        if (tmp != null) result.add(WorkEmailAddressDetail(tmp))
        tmp = card.organization
        if (tmp != null) result.add(WorkOrganizationNameDetail(tmp))
        tmp = card.organizationUnit
        if (tmp != null) result.add(WorkDepartmentNameDetail(tmp))
        tmp = card.getField("TITLE")
        if (tmp != null) result.add(JobTitleDetail(tmp))
        tmp = card.getField("ABOUTME")
        if (tmp != null) result.add(AboutMeDetail(tmp))

        // cmeng: it is normal for packet.EmptyResultIQ when contact does not have avatar uploaded
        val imageBytes = card.avatar
        if (imageBytes != null && imageBytes.isNotEmpty()) {
            result.add(ImageDetail("Image", imageBytes))
        }

        // add as string context if not a valid URL
        tmp = card.getField("URL")
        try {
            if (tmp != null) result.add(URLDetail("URL", URL(tmp)))
        } catch (ex: MalformedURLException) {
            result.add(URLDetail("URL", tmp))
            Timber.w("%s %s", errMessage, ex.message)
        }
        retrievedDetails[bareJid] = result
        Timber.i("Added retrievedDetails for: %s size: %s", bareJid, result.size)
        return result
    }

    /**
     * request the full info for the given bareJid if available in cache.
     *
     * @param bareJid to search for
     * @return list of the details if any.
     */
    fun getCachedUserDetails(bareJid: BareJid): MutableList<GenericDetail>? {
        return retrievedDetails[bareJid]
    }

    /**
     * Adds a cached contact details.
     *
     * @param bareJid the contact address
     * @param details the details to add
     */
    fun addCachedUserDetails(bareJid: BareJid, details: MutableList<GenericDetail>) {
        retrievedDetails[bareJid] = details
    }

    /**
     * Checks for full name tag in the `card`.
     *
     * @param card the card to check.
     * @return the Full name if existing, null otherwise.
     */
    fun checkForFullName(card: VCard): String? {
        val vcardXml = (card as IQ).toXML(XmlEnvironment.EMPTY).toString()
        val indexOpen = vcardXml.indexOf(TAG_FN_OPEN)
        if (indexOpen == -1) return null
        val indexClose = vcardXml.indexOf(TAG_FN_CLOSE, indexOpen)

        // something is wrong!
        return if (indexClose == -1) null else vcardXml.substring(indexOpen + TAG_FN_OPEN.length, indexClose)
    }

    /**
     * Work department
     */
    /**
     * Constructor.
     *
     * @param workDepartmentName name of the work department
     */
    class WorkDepartmentNameDetail(workDepartmentName: String?) : NameDetail("Work Department Name", workDepartmentName)

    /**
     * Fax at work
     */
    class WorkFaxDetail(number: String?) : FaxDetail(number) {
        /**
         * Constructor.
         */
        init {
            super.detailDisplayName = "WorkFax"
        }
    }

    /**
     * Pager at work
     */
    class WorkPagerDetail(number: String?) : PhoneNumberDetail(number) {
        /**
         * Constructor.
         */
        init {
            super.detailDisplayName = "WorkPager"
        }
    }

    companion object {
        private const val TAG_FN_OPEN = "<FN>"
        private const val TAG_FN_CLOSE = "</FN>"
        const val BDAY_FORMAT_MEDIUM = "MMM dd, yyyy"
        const val BDAY_FORMAT_SHORT = "yyyy-mm-dd"
    }
}