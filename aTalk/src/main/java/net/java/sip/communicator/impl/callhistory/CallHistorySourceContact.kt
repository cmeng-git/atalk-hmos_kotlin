/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.impl.callhistory

import net.java.sip.communicator.service.callhistory.CallRecord
import net.java.sip.communicator.service.contactsource.ContactDetail
import net.java.sip.communicator.service.contactsource.SourceContact
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.OperationNotSupportedException
import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony
import net.java.sip.communicator.service.protocol.OperationSetContactCapabilities
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolNames
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent
import net.java.sip.communicator.util.DataObject
import net.java.sip.communicator.util.GuiUtils.compareDatesOnly
import net.java.sip.communicator.util.GuiUtils.formatDateTime
import net.java.sip.communicator.util.GuiUtils.formatTime
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import java.util.*

/**
 * The `CallHistorySourceContact` is an implementation of the
 * `SourceContact` interface based on a `CallRecord`.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class CallHistorySourceContact(
        /**
         * The parent `CallHistoryContactSource`, where this contact is contained.
         */
        override val contactSource: CallHistoryContactSource,
        /**
         * The corresponding call record.
         */
        private val callRecord: CallRecord) : DataObject(), SourceContact {

    /**
     * Returns the display name of this search contact. This is a user-friendly
     * name that could be shown in the user interface.
     *
     * @return the display name of this search contact
     */
    /**
     * The display name of this contact.
     */
    override var displayName = ""
        private set

    /**
     * Returns the display details of this search contact. This could be any
     * important information that should be shown to the user.
     *
     */
    override val displayDetails: String

    /**
     * Creates an instance of `CallHistorySourceContact`
     *
     * contactSource the contact source
     * callRecord the call record
     */
    init {
        initPeerDetails()
        displayDetails = ((aTalkApp.getResString(R.string.service_gui_AT) + ": "
                + getDateString(callRecord.startTime!!.time)
                + " " + aTalkApp.getResString(R.string.service_gui_DURATION)) + ": "
                + formatTime(callRecord.startTime!!, callRecord.endTime!!))
    }

    /**
     * Initializes peer details.
     */
    private fun initPeerDetails() {
        var stripAddress = false
        val stripAddressProp = CallHistoryActivator.resources!!.getSettingsString(STRIP_ADDRESSES_TO_NUMBERS)
        if (stripAddressProp != null && java.lang.Boolean.parseBoolean(stripAddressProp)) stripAddress = true
        for (peerRecord in callRecord.peerRecords) {
            var peerAddress = peerRecord.peerAddress
            val peerSecondaryAddress = peerRecord.peerSecondaryAddress
            if (peerAddress != null) {
                if (stripAddress && !peerAddress.startsWith("@")) {
                    peerAddress = peerAddress.split("@")[0]
                }
                var peerRecordDisplayName = peerRecord.displayName
                if (peerRecordDisplayName == null || peerRecordDisplayName.isEmpty()) peerRecordDisplayName = peerAddress
                val contactDetail = ContactDetail(peerAddress, peerRecordDisplayName)
                var preferredProviders: MutableMap<Class<out OperationSet>, ProtocolProviderService>? = null
                var preferredProtocols: MutableMap<Class<out OperationSet>, String>? = null
                val preferredProvider = callRecord.protocolProvider
                if (preferredProvider != null) {
                    preferredProviders = Hashtable()
                    val opSetPres = preferredProvider.getOperationSet(OperationSetPresence::class.java)
                    var contact: Contact? = null
                    if (opSetPres != null) contact = opSetPres.findContactByID(peerAddress)
                    val opSetCaps = preferredProvider.getOperationSet(OperationSetContactCapabilities::class.java)
                    if (opSetCaps != null && opSetPres != null) {
                        if (contact != null
                                && opSetCaps.getOperationSet(contact, OperationSetBasicTelephony::class.java) != null) {
                            preferredProviders[OperationSetBasicTelephony::class.java] = preferredProvider
                        }
                    } else {
                        preferredProviders[OperationSetBasicTelephony::class.java] = preferredProvider
                    }
                    contactDetail.setPreferredProviders(preferredProviders)
                } else {
                    preferredProtocols = Hashtable()
                    preferredProtocols[OperationSetBasicTelephony::class.java] = ProtocolNames.SIP
                    contactDetail.setPreferredProtocols(preferredProtocols)
                }
                val supportedOpSets = LinkedList<Class<out OperationSet?>>()

                // if the contat supports call
                if ((preferredProviders != null
                                && preferredProviders.containsKey(OperationSetBasicTelephony::class.java)) || preferredProtocols != null) {
                    supportedOpSets.add(OperationSetBasicTelephony::class.java)
                }

                // can be added as contacts
                supportedOpSets.add(OperationSetPersistentPresence::class.java)
                contactDetail.setSupportedOpSets(supportedOpSets)
                contactDetails.add(contactDetail)
                if (peerSecondaryAddress != null) {
                    val secondaryContactDetail = ContactDetail(peerSecondaryAddress)
                    secondaryContactDetail.addSupportedOpSet(OperationSetPersistentPresence::class.java)
                    contactDetails.add(secondaryContactDetail)
                }

                // Set the displayName.
                var name = peerRecord.displayName
                if (name == null || name.isEmpty()) name = peerAddress
                if (displayName.isEmpty()) displayName = if (callRecord.peerRecords.size > 1) "Conference $name" else name
            }
        }
    }

    /**
     * A list of all contact details.
     */
    override val contactDetails = LinkedList<ContactDetail>()
        get() = LinkedList(field)

// if the call record has reason for normal call clearing
// means it was answered somewhere else, then we don't mark it as missed
    /**
     * An image (or avatar) corresponding to this search contact. If such is
     * not available this method will return null.
     *
     * @return the byte array of the image or null if no image is available
     */
    override val image: ByteArray?
        get() {
            if (callRecord.direction == CallRecord.IN) {
                // if the call record has reason for normal call clearing
                // means it was answered somewhere else, then we don't mark it as missed
                return if (callRecord.startTime == callRecord.endTime && callRecord.endReason != CallPeerChangeEvent.NORMAL_CALL_CLEARING) missedCallIcon else incomingIcon
            } else if (callRecord.direction == CallRecord.OUT) return outgoingIcon
            return null
        }

    /**
     * Returns a list of all `ContactDetail`s supporting the given `OperationSet` class.
     *
     * @param operationSet the `OperationSet` class we're looking for
     * @return a list of all `ContactDetail`s supporting the given `OperationSet` class.
     */
    override fun getContactDetails(operationSet: Class<out OperationSet?>?): List<ContactDetail>? {
        // We support only call details or persistence presence so we can add contacts.
        return if ((operationSet == OperationSetBasicTelephony::class.java || operationSet != OperationSetPersistentPresence::class.java)) null else LinkedList(contactDetails)
    }

    /**
     * Returns a list of all `ContactDetail`s corresponding to the given category.
     *
     * @param category the `OperationSet` class we're looking for
     * @return a list of all `ContactDetail`s corresponding to the given category
     */
    @Throws(OperationNotSupportedException::class)
    override fun getContactDetails(category: ContactDetail.Category): List<ContactDetail> {
        // We don't support category for call history details, so we return null.
        throw OperationNotSupportedException("Categories are not supported for call history records.")
    }

    /**
     * Returns the preferred `ContactDetail` for a given `OperationSet` class.
     *
     * @param operationSet the `OperationSet` class, for which we would
     * like to obtain a `ContactDetail`
     * @return the preferred `ContactDetail` for a given `OperationSet` class
     */
    override fun getPreferredContactDetail(operationSet: Class<out OperationSet?>?): ContactDetail? {
        // We support only call details
        // or persistence presence so we can add contacts.
        return if ((operationSet == OperationSetBasicTelephony::class.java || operationSet != OperationSetPersistentPresence::class.java)) null else contactDetails[0]
    }

    /**
     * Returns the status of the source contact. And null if such information is not available.
     *
     * @return the PresenceStatus representing the state of this source contact.
     */
    override val presenceStatus: PresenceStatus?
        get() = null

    /**
     * Returns the index of this source contact in its parent.
     *
     * @return the index of this source contact in its parent
     */
    override val index: Int
        get() = -1

    /**
     * {@inheritDoc}
     *
     * Not implemented.
     */
    override var contactAddress: String?
        get() = null
        set(contactAddress) {}

    // in this SourceContact we always show a default image based
    // on the call direction (in, out or missed)

    /**
     * Whether the current image returned by @see #getImage() is the one provided by the
     * SourceContact by default, or is a one used and obtained from external source.
     *
     * @return whether this is the default image for this SourceContact.
     */
    override val isDefaultImage: Boolean
        // in this SourceContact we always show a default image based
        // on the call direction (in, out or missed)
        get() = true

    companion object {
        /**
         * Whether we need to strip saved addresses to numbers. We strip everything
         * before '@', if it is absent nothing is changed from the saved address.
         */
        private const val STRIP_ADDRESSES_TO_NUMBERS = "callhistory.STRIP_ADDRESSES_TO_NUMBERS"

        /**
         * The incoming call icon.
         */
        private val incomingIcon = CallHistoryActivator.resources!!.getImageInBytes("gui.icons.INCOMING_CALL")

        /**
         * The outgoing call icon.
         */
        private val outgoingIcon = CallHistoryActivator.resources!!.getImageInBytes("gui.icons.OUTGOING_CALL")

        /**
         * The missed call icon.
         */
        private val missedCallIcon = CallHistoryActivator.resources!!.getImageInBytes("gui.icons.MISSED_CALL")

        /**
         * Returns the date string to show for the given date.
         *
         * @param date the date to format
         * @return the date string to show for the given date
         */
        fun getDateString(date: Long): String {
            val time = formatTime(date)

            // If the current date we don't go in there and we'll return just the time.
            return if (compareDatesOnly(date, System.currentTimeMillis()) < 0) {
                formatDateTime(Date(date))
            } else time
        }
    }
}