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
package net.java.sip.communicator.service.callhistory

import net.java.sip.communicator.service.callhistory.event.CallHistoryPeerRecordListener
import net.java.sip.communicator.service.callhistory.event.CallHistorySearchProgressListener
import net.java.sip.communicator.service.contactlist.MetaContact
import java.util.*

/**
 * The Call History Service stores info about calls made from various protocols
 *
 * @author Alexander Pelov
 * @author Damian Minkov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
interface CallHistoryService {
    /**
     * Returns all the calls made by all the contacts in the supplied `contact` on and after
     * the given date.
     *
     * @param metaContact MetaContact which contacts participate in the returned calls
     * @param startDate Date the start date of the calls
     * @return Collection of CallRecords with CallPeerRecord
     */
    fun findByStartDate(metaContact: MetaContact?, startDate: Date?): Collection<CallRecord?>?

    /**
     * Returns all the calls made by all the contacts in the supplied `contact` before the given date.
     *
     * @param metaContact MetaContact which contacts participate in the returned calls
     * @param endDate Date the end date of the calls
     * @return Collection of CallRecords with CallPeerRecord
     */
    fun findByEndDate(metaContact: MetaContact?, endDate: Date?): Collection<CallRecord?>?

    /**
     * Returns all the calls made by all the contacts in the supplied `contact` between
     * the given dates inclusive of startDate.
     *
     * @param metaContact MetaContact which contacts participate in the returned calls
     * @param startDate Date the start date of the calls
     * @param endDate Date the end date of the calls
     * @return Collection of CallRecords with CallPeerRecord
     */
    fun findByPeriod(metaContact: MetaContact?, startDate: Date?, endDate: Date?): Collection<CallRecord?>?

    /**
     * Returns all the calls made after the given date.
     *
     * @param startDate Date the start date of the calls
     * @return Collection of CallRecords with CallPeerRecord
     */
    fun findByStartDate(startDate: Date?): Collection<CallRecord?>?

    /**
     * Returns all the calls made before the given date.
     *
     * @param endDate Date the end date of the calls
     * @return Collection of CallRecords with CallPeerRecord
     */
    fun findByEndDate(endDate: Date?): Collection<CallRecord?>?
    fun findByEndDate(accountUuid: String?, endDate: Date?): Collection<CallRecord>?

    /**
     * Returns all the calls made between the given dates.
     *
     * @param startDate Date the start date of the calls
     * @param endDate Date the end date of the calls
     * @return Collection of CallRecords with CallPeerRecord
     */
    fun findByPeriod(startDate: Date?, endDate: Date?): Collection<CallRecord?>?

    /**
     * Returns the supplied number of recent calls made by all the contacts in the supplied `contact`.
     *
     * @param metaContact MetaContact which contacts participate in the returned calls
     * @param count calls count
     * @return Collection of CallRecords with CallPeerRecord
     */
    fun findLast(metaContact: MetaContact?, count: Int): Collection<CallRecord?>?

    /**
     * Returns the supplied number of recent calls.
     *
     * @param count calls count
     * @return Collection of CallRecords with CallPeerRecord
     */
    fun findLast(count: Int): Collection<CallRecord>

    /**
     * Find the calls made by the supplied peer address
     *
     * @param address String the address of the peer
     * @param recordCount the number of records to return
     * @return Collection of CallRecords with CallPeerRecord
     */
    fun findByPeer(address: String?, recordCount: Int): CallHistoryQuery?

    /**
     * Adding progress listener for monitoring progress of search process
     *
     * @param listener HistorySearchProgressListener
     */
    fun addSearchProgressListener(listener: CallHistorySearchProgressListener)

    /**
     * Removing progress listener
     *
     * @param listener HistorySearchProgressListener
     */
    fun removeSearchProgressListener(listener: CallHistorySearchProgressListener)

    /**
     * Updates the secondary address field of call record.
     *
     * @param date the start date of the record which will be updated.
     * @param peerAddress the peer of the record which will be updated.
     * @param address the value of the secondary address .
     */
    fun updateCallRecordPeerSecondaryAddress(date: Date, peerAddress: String, address: String)

    /**
     * Adding `CallHistoryRecordListener` listener to the list.
     *
     * @param listener CallHistoryRecordListener
     */
    fun addCallHistoryRecordListener(listener: CallHistoryPeerRecordListener)

    /**
     * Removing `CallHistoryRecordListener` listener
     *
     * @param listener CallHistoryRecordListener
     */
    fun removeCallHistoryRecordListener(listener: CallHistoryPeerRecordListener)

    /**
     * Permanently removes all locally stored call history.
     */
    fun eraseLocallyStoredHistory(callUUIDs: List<String?>?)

    /**
     * Permanently removes all locally stored call history before and on the given date
     */
    fun eraseLocallyStoredHistoryBefore(endDate: Date?): Int

    companion object {
        /* DB database column fields for call history */
        const val TABLE_NAME = "callHistory"
        const val UUID = "uuid"
        const val TIME_STAMP = "timeStamp" // TimeStamp
        const val ACCOUNT_UID = "accountUid" // account uid
        const val CALL_START = "callStart" // callStart TimeStamp
        const val CALL_END = "callEnd" // callEnd TimeStamp
        const val DIRECTION = "direction" // dir
        const val ENTITY_JID = "entityJid" // callParticipantName
        const val ENTITY_CALL_START = "entityCallStart" // callParticipantStart
        const val ENTITY_CALL_END = "entityCallEnd" // callParticipantEnd
        const val ENTITY_CALL_STATE = "entityCallState" // callParticipantStates
        const val CALL_END_REASON = "callEndReason"
        const val ENTITY_FULL_JID = "entityFullJid" // callParticipantIDs
        const val SEC_ENTITY_ID = "secEntityID" //secondaryCallParticipantIDs
    }
}