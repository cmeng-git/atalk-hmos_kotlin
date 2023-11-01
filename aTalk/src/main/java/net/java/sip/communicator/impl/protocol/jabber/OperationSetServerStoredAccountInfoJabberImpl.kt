/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import android.graphics.BitmapFactory
import net.java.sip.communicator.service.protocol.AbstractOperationSetServerStoredAccountInfo
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.ServerStoredDetails.*
import net.java.sip.communicator.service.protocol.event.ServerStoredDetailsChangeEvent
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.jivesoftware.smack.SmackException.NoResponseException
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smackx.avatar.useravatar.UserAvatarManager
import org.jivesoftware.smackx.avatar.vcardavatar.VCardAvatarManager
import org.jivesoftware.smackx.vcardtemp.packet.VCard
import org.jxmpp.jid.BareJid
import timber.log.Timber
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * The Account Info Operation set is a means of accessing and modifying detailed information on the
 * user/account that is currently logged in through this provider.
 *
 * @author Damian Minkov
 * @author Marin Dzhigarov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class OperationSetServerStoredAccountInfoJabberImpl(
        /**
         * The jabber provider that created us.
         */
        private val jabberProvider: ProtocolProviderServiceJabberImpl?,
        /**
         * The info retriever.
         */
        private val infoRetriever: InfoRetriever,
        /**
         * Our account UIN.
         */
        private val uin: BareJid) : AbstractOperationSetServerStoredAccountInfo() {
    /**
     * Returns an iterator over all details that are instances or descendants of the specified
     * class. If for example an our account has a work address and an address detail, a call to
     * this method with AddressDetail.class would return both of them.
     *
     *
     *
     * @param detailClass one of the detail classes defined in the ServerStoredDetails class, indicating the
     * kind of details we're interested in.
     *
     *
     * @return a java.util.Iterator over all details that are instances or descendants of the
     * specified class.
     */
    override fun <T : GenericDetail?> getDetailsAndDescendants(detailClass: Class<T>?): Iterator<T?>? {
        return if (assertConnected()) infoRetriever.getDetailsAndDescendants(uin, detailClass) else null
    }

    /**
     * Returns an iterator over all details that are instances of exactly the same class as the
     * one specified. Not that, contrary to the getDetailsAndDescendants() method this one
     * would only return details that are instances of the specified class and not only its
     * descendants. If for example our account has both a work address and an address detail,
     * a call to this method with AddressDetail.class would return only the AddressDetail
     * instance and not the WorkAddressDetail instance.
     *
     *
     *
     * @param detailClass one of the detail classes defined in the ServerStoredDetails class, indicating the
     * kind of details we're interested in.
     *
     *
     * @return a java.util.Iterator over all details of specified class.
     */
    override fun getDetails(detailClass: Class<out GenericDetail?>?): Iterator<GenericDetail?>? {
        return if (assertConnected()) infoRetriever.getDetails(uin, detailClass) else null
    }

    /**
     * Returns all details currently available and set for our account.
     *
     *
     *
     * @return a java.util.Iterator over all details currently set our account.
     */
    override fun getAllAvailableDetails(): MutableIterator<GenericDetail?>? {
        return if (assertConnected()) infoRetriever.getUserDetails(uin)!!.iterator() else null
    }

    /**
     * Clear all details for account when it logoff; to allow refresh on next login
     */
    override fun clearDetails() {
        infoRetriever.clearUserDetails(uin)
    }

    /**
     * Returns all detail Class-es that the underlying implementation supports setting. Note
     * that if you call one of the modification methods (add remove or replace) with a detail not
     * contained by the iterator returned by this method, an IllegalArgumentException will be thrown.
     *
     *
     *
     * @return a java.util.Iterator over all detail classes supported by the implementation.
     */
    override fun getSupportedDetailTypes(): Iterator<Class<out GenericDetail?>?> {
        return supportedTypes.iterator()
    }

    /**
     * Determines whether a detail class represents a detail supported by the underlying
     * implementation or not. Note that if you call one of the modification methods (add remove or
     * replace) with a detail that this method has determined to be unsupported (returned false)
     * this would lead to an IllegalArgumentException being thrown.
     *
     *
     *
     * @param detailClass the class the support for which we'd like to determine.
     *
     *
     * @return true if the underlying implementation supports setting details of this type and false otherwise.
     */
    override fun isDetailClassSupported(detailClass: Class<out GenericDetail?>?): Boolean {
        return supportedTypes.contains(detailClass)
    }

    /**
     * The method returns the number of instances supported for a particular detail type. Some
     * protocols offer storing multiple values for a particular detail type. Spoken languages are a
     * good example.
     *
     * @param detailClass the class whose max instance number we'd like to find out.
     *
     *
     * @return int the maximum number of detail instances.
     */
    override fun getMaxDetailInstances(detailClass: Class<out GenericDetail?>?): Int {
        return 1
    }

    /**
     * Adds the specified detail to the list of details ready to be saved online for this account.
     * If such a detail already exists its max instance number is consulted and if it allows it - a
     * second instance is added or otherwise and illegal argument exception is thrown. An
     * IllegalArgumentException is also thrown in case the class of the specified detail is not
     * supported by the underlying implementation, i.e. its class name was not returned by the
     * getSupportedDetailTypes() method.
     *
     *
     *
     * @param detail the detail that we'd like registered on the server.
     *
     *
     * @throws IllegalArgumentException if such a detail already exists and its max instances number has been attained or if
     * the underlying implementation does not support setting details of the corresponding
     * class.
     * @throws java.lang.ArrayIndexOutOfBoundsException if the number of instances currently registered by the application is already equal
     * to the maximum number of supported instances (@see #getMaxDetailInstances())
     */
    @Throws(IllegalArgumentException::class, ArrayIndexOutOfBoundsException::class)
    override fun addDetail(detail: GenericDetail?) {
        require(isDetailClassSupported(detail!!.javaClass)) { "implementation does not support such details " + detail.javaClass }
        val iter = getDetails(detail.javaClass)
        var currentDetailsSize = 0
        while (iter!!.hasNext()) {
            currentDetailsSize++
            iter.next()
        }
        if (currentDetailsSize > getMaxDetailInstances(detail.javaClass)) {
            throw ArrayIndexOutOfBoundsException("Max count for this detail is already reached")
        }
        infoRetriever.getCachedUserDetails(uin)?.add(detail)
    }

    /**
     * Removes the specified detail from the list of details ready to be saved online this account.
     * The method returns a boolean indicating if such a detail was found (and removed) or not.
     *
     *
     *
     * @param detail the detail to remove
     * @return true if the specified detail existed and was successfully removed and false otherwise.
     */
    override fun removeDetail(detail: GenericDetail?): Boolean {
        return infoRetriever.getCachedUserDetails(uin)!!.remove(detail)
    }

    /**
     * Replaces the currentDetailValue detail with newDetailValue and returns true if the operation
     * was a success or false if currentDetailValue did not previously exist (in this case an
     * additional call to addDetail is required).
     *
     *
     *
     * @param currentDetailValue the detail value we'd like to replace.
     * @param newDetailValue the value of the detail that we'd like to replace currentDetailValue with.
     * @return true if the operation was a success or false if currentDetailValue did not
     * previously exist (in this case an additional call to addDetail is required).
     * @throws ClassCastException if newDetailValue is not an instance of the same class as currentDetailValue.
     */
    @Throws(ClassCastException::class)
    override fun replaceDetail(currentDetailValue: GenericDetail?,
            newDetailValue: GenericDetail?): Boolean {
        if (newDetailValue!!.javaClass != currentDetailValue!!.javaClass) {
            throw ClassCastException("New value to be replaced is not as the current one")
        }
        // if values are the same no change
        if (currentDetailValue == newDetailValue) {
            return true
        }
        var isFound = false
        val iter = infoRetriever.getDetails(uin, currentDetailValue.javaClass)
        while (iter.hasNext()) {
            val item = iter.next()
            if (item!! == currentDetailValue) {
                isFound = true
                break
            }
        }
        // current detail value does not exist
        if (!isFound) {
            return false
        }
        removeDetail(currentDetailValue)
        addDetail(newDetailValue)
        return true
    }

    /**
     * Saves the list of details for this account that were ready to be stored online on the
     * server. This method performs the actual saving of details online on the server and is
     * supposed to be invoked after addDetail(), replaceDetail() and/or removeDetail().
     *
     *
     *
     * @throws OperationFailedException with code Network Failure if putting the new values back online has failed.
     */
    @Throws(OperationFailedException::class)
    override fun save() {
        if (!assertConnected()) return
        val xmppConnection = jabberProvider!!.connection
        val userAvatarManager = UserAvatarManager.getInstanceFor(xmppConnection)
        val vCardAvatarMgr = VCardAvatarManager.getInstanceFor(xmppConnection)

        // modify our reply timeout because some server may send "result" IQ late (> 5 seconds).
        xmppConnection!!.replyTimeout = ProtocolProviderServiceJabberImpl.SMACK_REPLY_EXTENDED_TIMEOUT_40
        val details: List<GenericDetail?>? = infoRetriever.getUserDetails(uin)
        val vCard = VCard()
        for (detail in details!!) {
            if (detail is ImageDetail) {
                // disable XEP-0153 vcard <photo/> hash; instead publish avatar via XEP-0084
                // vCard.removeAvatar(); // cmeng - need to keep to support old client (XEP-0054)
                val avatar = detail.getBytes()
                if (avatar != null && avatar.size > 0) {
                    vCard.avatar = avatar // for XEP-0054
                    val bitmap = BitmapFactory.decodeByteArray(avatar, 0, avatar.size)
                    userAvatarManager.publishAvatar(bitmap)
                } else {
                    // vCard.removeAvatar(); cause crash in Smack 4.2.1-beta2-snapshot muc setup
                    vCard.avatar = ByteArray(0) // for XEP-0054
                    userAvatarManager.disableAvatarPublishing()
                }
                fireServerStoredDetailsChangeEvent(jabberProvider,
                        ServerStoredDetailsChangeEvent.DETAIL_ADDED, null, detail)
            } else if (detail!!.javaClass == FirstNameDetail::class.java) {
                vCard.firstName = detail.getDetailValue() as String?
            } else if (detail.javaClass == MiddleNameDetail::class.java) {
                vCard.middleName = detail.getDetailValue() as String?
            } else if (detail.javaClass == LastNameDetail::class.java) {
                vCard.lastName = detail.getDetailValue() as String?
            } else if (detail.javaClass == NicknameDetail::class.java) vCard.nickName = detail.getDetailValue() as String? else if (detail.javaClass == URLDetail::class.java) {
                if (detail.getDetailValue() != null) vCard.setField("URL", detail.getDetailValue().toString())
            } else if (detail.javaClass == BirthDateDetail::class.java) {
                if (detail.getDetailValue() != null) {
                    var strdate: String?
                    val date = (detail as BirthDateDetail?)!!.getCalendar()
                    strdate = if (date is Calendar) {
                        val dateFormat = SimpleDateFormat(InfoRetriever.BDAY_FORMAT_MEDIUM, Locale.US)
                        dateFormat.format(date.time)
                    } else {
                        date as String
                    }
                    vCard.setField("BDAY", strdate)
                }
            } else if (detail.javaClass == AddressDetail::class.java) vCard.setAddressFieldHome("STREET", detail.getDetailValue() as String?) else if (detail.javaClass == CityDetail::class.java) vCard.setAddressFieldHome("LOCALITY", detail.getDetailValue() as String?) else if (detail.javaClass == ProvinceDetail::class.java) vCard.setAddressFieldHome("REGION", detail.getDetailValue() as String?) else if (detail.javaClass == PostalCodeDetail::class.java) vCard.setAddressFieldHome("PCODE", detail.getDetailValue() as String?) else if (detail.javaClass == CountryDetail::class.java) vCard.setAddressFieldHome("CTRY", detail.getDetailValue() as String?) else if (detail.javaClass == PhoneNumberDetail::class.java) vCard.setPhoneHome("VOICE", detail.getDetailValue() as String?) else if (detail.javaClass == WorkPhoneDetail::class.java) vCard.setPhoneWork("VOICE", detail.getDetailValue() as String?) else if (detail.javaClass == MobilePhoneDetail::class.java) vCard.setPhoneHome("CELL", detail.getDetailValue() as String?) else if (detail.javaClass == VideoDetail::class.java) vCard.setPhoneHome("VIDEO", detail.getDetailValue() as String?) else if (detail.javaClass == WorkVideoDetail::class.java) vCard.setPhoneWork("VIDEO", detail.getDetailValue() as String?) else if (detail.javaClass == EmailAddressDetail::class.java) vCard.emailHome = detail.getDetailValue() as String? else if (detail.javaClass == WorkEmailAddressDetail::class.java) vCard.emailWork = detail.getDetailValue() as String? else if (detail.javaClass == WorkOrganizationNameDetail::class.java) vCard.organization = detail.getDetailValue() as String? else if (detail.javaClass == JobTitleDetail::class.java) vCard.setField("TITLE", detail.getDetailValue() as String?) else if (detail.javaClass == AboutMeDetail::class.java) vCard.setField("ABOUTME", detail.getDetailValue() as String?)
        }

        // Fix the display name detail
        val tmp: String? = infoRetriever.checkForFullName(vCard)
        if (tmp != null) {
            val displayNameDetail = DisplayNameDetail(tmp)
            val detailIt = infoRetriever.getDetails(uin, DisplayNameDetail::class.java)
            while (detailIt.hasNext()) {
                infoRetriever.getCachedUserDetails(uin)?.remove(detailIt.next())
            }
            infoRetriever.getCachedUserDetails(uin)?.add(displayNameDetail)
        }
        try {
            // saveVCard() via VCardAvatarManager to support XEP-0153
            if (vCardAvatarMgr.saveVCard(vCard)) {
                // need to send new <presence/> ?
            }
        } catch (e: XMPPException) {
            Timber.e(e, "Error loading/saving vcard")
            throw OperationFailedException("Error loading/saving vcard: ", 1, e)
        } catch (e: NoResponseException) {
            Timber.e(e, "Error loading/saving vcard")
            throw OperationFailedException("Error loading/saving vcard: ", 1, e)
        } catch (e: NotConnectedException) {
            Timber.e(e, "Error loading/saving vcard")
            throw OperationFailedException("Error loading/saving vcard: ", 1, e)
        } catch (e: InterruptedException) {
            Timber.e(e, "Error loading/saving vcard")
            throw OperationFailedException("Error loading/saving vcard: ", 1, e)
        } finally {
            // Reset to default
            xmppConnection.replyTimeout = ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT
        }
    }

    /**
     * Determines whether the underlying implementation supports edition of this detail class.
     *
     *
     *
     * @param detailClass the class whose edition we'd like to determine if it's possible
     * @return true if the underlying implementation supports edition of this type of detail and false otherwise.
     */
    override fun isDetailClassEditable(detailClass: Class<out GenericDetail?>?): Boolean {
        return isDetailClassSupported(detailClass)
    }

    /**
     * Utility method throwing an exception if the jabber stack is not properly initialized.
     *
     * cmeng - never throw exception else it crashes the application.
     * throw java.lang.IllegalStateException if the underlying jabber stack is not registered and
     * initialized.
     */
    private fun assertConnected(): Boolean {
        if (jabberProvider == null || !jabberProvider.isRegistered) {
            Timber.w(aTalkApp.getResString(R.string.service_gui_NETOWRK_ASSERTION_ERROR))
            return false
        }
        return true
    }

    companion object {
        /**
         * List of all supported `ServerStoredDetails` for this implementation.
         */
        val supportedTypes: MutableList<Class<out GenericDetail?>?> = ArrayList()

        init {
            supportedTypes.add(ImageDetail::class.java)
            supportedTypes.add(FirstNameDetail::class.java)
            supportedTypes.add(MiddleNameDetail::class.java)
            supportedTypes.add(LastNameDetail::class.java)
            supportedTypes.add(NicknameDetail::class.java)
            supportedTypes.add(AddressDetail::class.java)
            supportedTypes.add(CityDetail::class.java)
            supportedTypes.add(ProvinceDetail::class.java)
            supportedTypes.add(PostalCodeDetail::class.java)
            supportedTypes.add(CountryDetail::class.java)
            supportedTypes.add(EmailAddressDetail::class.java)
            supportedTypes.add(WorkEmailAddressDetail::class.java)
            supportedTypes.add(PhoneNumberDetail::class.java)
            supportedTypes.add(WorkPhoneDetail::class.java)
            supportedTypes.add(MobilePhoneDetail::class.java)
            supportedTypes.add(VideoDetail::class.java)
            supportedTypes.add(WorkVideoDetail::class.java)
            supportedTypes.add(WorkOrganizationNameDetail::class.java)
            supportedTypes.add(URLDetail::class.java)
            supportedTypes.add(BirthDateDetail::class.java)
            supportedTypes.add(JobTitleDetail::class.java)
            supportedTypes.add(AboutMeDetail::class.java)
        }
    }
}