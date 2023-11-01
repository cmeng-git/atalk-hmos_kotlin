/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.impl.callhistory

import android.content.ContentValues
import android.database.Cursor
import net.java.sip.communicator.impl.history.HistoryQueryImpl
import net.java.sip.communicator.service.callhistory.CallHistoryQuery
import net.java.sip.communicator.service.callhistory.CallHistoryService
import net.java.sip.communicator.service.callhistory.CallRecord
import net.java.sip.communicator.service.callhistory.event.CallHistoryPeerRecordEvent
import net.java.sip.communicator.service.callhistory.event.CallHistoryPeerRecordListener
import net.java.sip.communicator.service.callhistory.event.CallHistorySearchProgressListener
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.history.HistoryService
import net.java.sip.communicator.service.history.event.HistorySearchProgressListener
import net.java.sip.communicator.service.history.event.ProgressEvent
import net.java.sip.communicator.service.history.records.HistoryRecordStructure
import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.service.protocol.CallPeerState
import net.java.sip.communicator.service.protocol.CallState
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.CallChangeEvent
import net.java.sip.communicator.service.protocol.event.CallChangeListener
import net.java.sip.communicator.service.protocol.event.CallEvent
import net.java.sip.communicator.service.protocol.event.CallListener
import net.java.sip.communicator.service.protocol.event.CallPeerAdapter
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent
import net.java.sip.communicator.service.protocol.event.CallPeerEvent
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.persistance.DatabaseBackend
import org.osgi.framework.BundleContext
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.io.IOException
import java.io.StreamTokenizer
import java.io.StringReader
import java.util.*

/**
 * The Call History Service stores info about the calls made. Logs calls info for all protocol
 * providers that support basic telephony (i.e. those that implement OperationSetBasicTelephony).
 *
 * @author Damian Minkov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
class CallHistoryServiceImpl : CallHistoryService, CallListener, ServiceListener {
    /**
     * The BundleContext that we got from the OSGI bus.
     */
    private var bundleContext: BundleContext? = null
    private var historyService: HistoryService? = null
    private val syncRootHistoryService = Any()
    private val progressListeners = Hashtable<CallHistorySearchProgressListener?, SearchProgressWrapper>()
    private val currentCallRecords = Vector<CallRecordImpl>()
    private val historyCallChangeListener = HistoryCallChangeListener()
    private val callHistoryRecordListeners = LinkedList<CallHistoryPeerRecordListener?>()
    private val mDB = DatabaseBackend.writableDB
    private val contentValues = ContentValues()

    /**
     * starts the service. Check the current registered protocol providers which supports
     * BasicTelephony and adds calls listener to them
     *
     * @param bc BundleContext
     */
    fun start(bc: BundleContext?) {
        Timber.d("Starting the call history implementation.")
        bundleContext = bc

        // start listening for newly register or removed protocol providers
        bc!!.addServiceListener(this)
        var ppsRefs: Array<ServiceReference<*>?>? = null
        try {
            ppsRefs = bundleContext!!.getServiceReferences(ProtocolProviderService::class.java.name,
                    null)
        } catch (e: InvalidSyntaxException) {
            e.printStackTrace()
        }
        if (ppsRefs != null && ppsRefs.isNotEmpty()) {
            for (ppsRef in ppsRefs) {
                @Suppress("UNCHECKED_CAST")
                val pps = bundleContext!!.getService(ppsRef as ServiceReference<ProtocolProviderService>)
                handleProviderAdded(pps)
            }
        }
    }

    /**
     * stops the service.
     *
     * @param bc BundleContext
     */
    fun stop(bc: BundleContext) {
        bc.removeServiceListener(this)
        var ppsRefs: Array<ServiceReference<*>?>? = null
        try {
            ppsRefs = bundleContext!!.getServiceReferences(ProtocolProviderService::class.java.name,
                    null)
        } catch (e: InvalidSyntaxException) {
            e.printStackTrace()
        }
        if (ppsRefs != null && ppsRefs.isNotEmpty()) {
            for (ppsRef in ppsRefs) {
                @Suppress("UNCHECKED_CAST")
                val pps = bundleContext!!.getService(ppsRef  as ServiceReference<ProtocolProviderService>)
                handleProviderRemoved(pps)
            }
        }
    }

    /**
     * When new protocol provider is registered we check does it supports BasicTelephony and
     * if so add a listener to it
     *
     * @param serviceEvent ServiceEvent
     */
    override fun serviceChanged(serviceEvent: ServiceEvent) {
        val sService = bundleContext!!.getService(serviceEvent.serviceReference)
        Timber.log(TimberLog.FINER, "Received a service event for: " + sService.javaClass.name)

        // we don't care if the source service is not a protocol provider
        if (sService !is ProtocolProviderService) {
            return
        }
        Timber.d("Service is a protocol provider.")
        if (serviceEvent.type == ServiceEvent.REGISTERED) {
            Timber.d("Handling registration of a new Protocol Provider.")
            handleProviderAdded(sService)
        } else if (serviceEvent.type == ServiceEvent.UNREGISTERING) {
            handleProviderRemoved(sService)
        }
    }

    /**
     * Used to attach the Call History Service to existing or just registered protocol provider.
     * Checks if the provider has implementation of OperationSetBasicTelephony
     *
     * @param provider ProtocolProviderService
     */
    private fun handleProviderAdded(provider: ProtocolProviderService) {
        Timber.d("Adding protocol provider %s", provider.protocolDisplayName)
        // check whether the provider has a basic telephony operation set
        val opSetTelephony = provider.getOperationSet(OperationSetBasicTelephony::class.java)
        if (opSetTelephony != null) {
            opSetTelephony.addCallListener(this)
        } else {
            Timber.log(TimberLog.FINER, "Service did not have a basic telephony op. set.")
        }
    }

    /**
     * Removes the specified provider from the list of currently known providers and ignores all
     * the calls made by it
     *
     * @param provider the ProtocolProviderService that has been unregistered.
     */
    private fun handleProviderRemoved(provider: ProtocolProviderService) {
        val opSetTelephony = provider.getOperationSet(OperationSetBasicTelephony::class.java)
        opSetTelephony?.removeCallListener(this)
    }

    /**
     * Set the configuration service.
     *
     * @param historyService HistoryService
     */
    fun setHistoryService(historyService: HistoryService?) {
        synchronized(syncRootHistoryService) {
            this.historyService = historyService
            Timber.d("New history service registered.")
        }
    }

    /**
     * Remove a configuration service.
     *
     * @param hService HistoryService
     */
    fun unsetHistoryService(hService: HistoryService) {
        synchronized(syncRootHistoryService) {
            if (historyService === hService) {
                historyService = null
                Timber.d("History service unregistered.")
            }
        }
    }

    /**
     * Returns all the calls made by all the contacts in the supplied `metaContact`
     * on and after and include the given date.
     *
     * @param metaContact MetaContact which contacts participate in the returned calls
     * @param startDate Date the start date of the calls
     *
     * @return the `CallHistoryQuery`, corresponding to this find
     */
    @Throws(RuntimeException::class)
    override fun findByStartDate(metaContact: MetaContact?, startDate: Date?): Collection<CallRecord?> {
        val result = TreeSet(CallRecordComparator())
        val contacts = metaContact!!.getContacts()
        while (contacts.hasNext()) {
            val contact = contacts.next()
            val startTimeStamp = startDate!!.time.toString()
            val args = arrayOf(contact.toString(), startTimeStamp)
            val cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                    CallHistoryService.ENTITY_JID + "=? AND "
                            + CallHistoryService.CALL_START + ">=?", args, null, null, ORDER_ASC)
            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToCallRecord(cursor))
            }
        }
        return result
    }

    /**
     * Returns all the calls made after and include the given date
     *
     * @param startDate Date the start date of the calls
     *
     * @return the `CallHistoryQuery`, corresponding to this find
     */
    override fun findByStartDate(startDate: Date?): Collection<CallRecord?> {
        val result = TreeSet(CallRecordComparator())
        val startTimeStamp = startDate!!.time.toString()
        val args = arrayOf(startTimeStamp)
        val cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                CallHistoryService.CALL_START + ">=?", args, null, null, ORDER_ASC)
        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToCallRecord(cursor))
        }
        return result
    }

    /**
     * Returns all the calls made by all the contacts in the supplied metaContact before
     * and include the given date
     *
     * @param metaContact MetaContact which contacts participate in the returned calls
     * @param endDate Date the end date of the calls
     *
     * @return Collection of CallRecords with CallPeerRecord
     */
    override fun findByEndDate(metaContact: MetaContact?, endDate: Date?): Collection<CallRecord?> {
        val result = TreeSet(CallRecordComparator())
        val contacts = metaContact!!.getContacts()
        while (contacts.hasNext()) {
            val contact = contacts.next()
            val endTimeStamp = endDate!!.time.toString()
            val args = arrayOf(contact.toString(), endTimeStamp)
            val cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                    CallHistoryService.ENTITY_JID + "=? AND "
                            + CallHistoryService.CALL_START + "<=?", args, null, null, ORDER_ASC)
            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToCallRecord(cursor))
            }
        }
        return result
    }

    /**
     * Returns all the calls made by the accountUuid, before and include the given date
     *
     * @param endDate Date the end date of the calls
     *
     * @return Collection of CallRecords with CallPeerRecord
     */
    override fun findByEndDate(accountUuid: String?, endDate: Date?): Collection<CallRecord> {
        val result = TreeSet(CallRecordComparator())
        val endTimeStamp = endDate!!.time.toString()
        val args = arrayOf(accountUuid, endTimeStamp)
        val cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                CallHistoryService.ACCOUNT_UID + "=? AND "
                        + CallHistoryService.CALL_START + "<=?", args, null, null, ORDER_DESC)
        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToCallRecord(cursor))
        }
        return result
    }

    /**
     * Returns all the calls made before and include the given date
     *
     * @param endDate Date the end date of the calls
     *
     * @return Collection of CallRecords with CallPeerRecord
     */
    override fun findByEndDate(endDate: Date?): Collection<CallRecord?> {
        val result = TreeSet(CallRecordComparator())
        val endTimeStamp = endDate!!.time.toString()
        val args = arrayOf(endTimeStamp)
        val cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                CallHistoryService.CALL_START + "<=?", args, null, null, ORDER_ASC)
        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToCallRecord(cursor))
        }
        return result
    }

    /**
     * Returns all the calls made by all the contacts in the supplied metaContact between the
     * given dates
     *
     * @param metaContact MetaContact
     * @param startDate Date the start date of the calls
     * @param endDate Date the end date of the conversations
     *
     * @return Collection of CallRecords with CallPeerRecord
     */
    override fun findByPeriod(metaContact: MetaContact?, startDate: Date?,
            endDate: Date?): Collection<CallRecord?> {
        val result = TreeSet(CallRecordComparator())
        val contacts = metaContact!!.getContacts()
        while (contacts.hasNext()) {
            val contact = contacts.next()
            val startTimeStamp = startDate!!.time.toString()
            val endTimeStamp = endDate!!.time.toString()
            val args = arrayOf(contact.toString(), startTimeStamp, endTimeStamp)
            val cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                    CallHistoryService.ENTITY_JID + "=? AND "
                            + CallHistoryService.CALL_START + ">=? AND "
                            + CallHistoryService.CALL_START + "<?",
                    args, null, null, ORDER_ASC)
            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToCallRecord(cursor))
            }
        }
        return result
    }

    /**
     * Returns all the calls made between the given dates
     *
     * @param startDate Date the start date of the calls
     * @param endDate Date the end date of the conversations
     *
     * @return Collection of CallRecords with CallPeerRecord
     */
    override fun findByPeriod(startDate: Date?, endDate: Date?): Collection<CallRecord?> {
        val result = TreeSet(CallRecordComparator())
        val startTimeStamp = startDate!!.time.toString()
        val endTimeStamp = endDate!!.time.toString()
        val args = arrayOf(startTimeStamp, endTimeStamp)
        val cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                CallHistoryService.CALL_START + ">=? AND "
                        + CallHistoryService.CALL_START + "<?", args, null, null, ORDER_ASC)
        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToCallRecord(cursor))
        }
        return result
    }

    /**
     * Returns the supplied number of calls by all the contacts in the supplied metaContact
     *
     * @param metaContact MetaContact which contacts participate in the returned calls
     * @param count calls count
     *
     * @return Collection of CallRecords with CallPeerRecord
     */
    override fun findLast(metaContact: MetaContact?, count: Int): Collection<CallRecord?> {
        val result = TreeSet(CallRecordComparator())
        val contacts = metaContact!!.getContacts()
        while (contacts.hasNext()) {
            val contact = contacts.next()
            val args = arrayOf(contact.toString())
            val cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                    CallHistoryService.ENTITY_JID + "=?", args, null, null, ORDER_DESC, count.toString())
            while (cursor.moveToNext()) {
                result.add(convertHistoryRecordToCallRecord(cursor))
            }
        }
        return result
    }

    /**
     * Returns the supplied number of calls made
     *
     * @param count calls count
     *
     * @return Collection of CallRecords with CallPeerRecord
     */
    override fun findLast(count: Int): Collection<CallRecord> {
        val result = TreeSet(CallRecordComparator())
        val cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                null, null, null, null, ORDER_DESC, count.toString())
        while (cursor.moveToNext()) {
            result.add(convertHistoryRecordToCallRecord(cursor))
        }
        return result
    }

    /**
     * Find the calls made by the supplied peer address
     *
     * @param address String the address of the peer (cmeng: may be null?)
     * @param recordCount the number of records to return
     *
     * @return Collection of CallRecords with CallPeerRecord
     */
    override fun findByPeer(address: String?, recordCount: Int): CallHistoryQuery {
        val hq = HistoryQueryImpl("callParticipantIDs")
        val callQuery = CallHistoryQueryImpl(hq)
        var callRecord: CallRecord
        val args = arrayOf(address)
        val cursor = mDB.query(CallHistoryService.TABLE_NAME, null,
                CallHistoryService.ENTITY_FULL_JID + " LIKE '%?%'", args,
                null, null, ORDER_DESC, recordCount.toString())
        while (cursor.moveToNext()) {
            callRecord = convertHistoryRecordToCallRecord(cursor)
            callQuery.addHistoryRecord(callRecord)
        }
        return callQuery

        //		try {
        //			// the default ones
        //			History history = this.getHistory(null, null);
        //			InteractiveHistoryReader historyReader = history.getInteractiveReader();
        //			HistoryQuery historyQuery = historyReader.findByKeyword(address, "callParticipantIDs", recordCount);
        //
        //			callQuery = new CallHistoryQueryImpl(historyQuery);
        //		}
        //		catch (IOException ex) {
        //			Timber.e("Could not read history", ex);
        //		}
        //		return callQuery;
    }
    //	/**
    //	 * Returns the history by specified local and remote contact if one of them is null the default is used
    //	 *
    //	 * @param localContact Contact
    //	 * @param remoteContact Contact
    //	 * @return History
    //	 */
    //	private History getHistory(Contact localContact, Contact remoteContact)
    //			throws IOException
    //	{
    //		String localId = localContact == null ? "default" : localContact.getAddress();
    //		String remoteId = remoteContact == null ? "default" : remoteContact.getAddress();
    //
    //		HistoryID historyId = HistoryID.createFromRawID(new String[]{"callhistory", localId, remoteId});
    //		return this.historyService.createHistory(historyId, recordStructure);
    //	}
    /**
     * Used to convert HistoryRecord in CallRecord and CallPeerRecord which are returned  in cursor
     * by the finder methods
     *
     * @param cursor HistoryRecord in cursor
     *
     * @return Object CallRecord
     */
    private fun convertHistoryRecordToCallRecord(cursor: Cursor): CallRecord {
        val mProperties = Hashtable<String, String>()
        for (i in 0 until cursor.columnCount) {
            val value = if (cursor.getString(i) == null) "" else cursor.getString(i)
            mProperties[cursor.getColumnName(i)] = value
        }
        return createCallRecordFromProperties(mProperties)
    }

    /**
     * Writes the given record to the history service
     */
    private fun writeCall(callRecord: CallRecordImpl) {
        val callPeerIDs = StringBuilder()
        val callPeerNames = StringBuilder()
        val callPeerStartTime = StringBuilder()
        val callPeerEndTime = StringBuilder()
        val callPeerStates = StringBuilder()
        val callPeerSecondaryIDs = StringBuilder()

        // Generate the delimited peerCallRecord item values
        for (item in callRecord.peerRecords) {
            if (callPeerIDs.isNotEmpty()) {
                callPeerIDs.append(DELIMITER)
                callPeerStartTime.append(DELIMITER)
                callPeerEndTime.append(DELIMITER)
                callPeerStates.append(DELIMITER)
                callPeerNames.append(DELIMITER)
                callPeerSecondaryIDs.append(DELIMITER)
            }
            callPeerIDs.append(item.peerAddress)
            callPeerStartTime.append(item.startTime?.time)
            callPeerEndTime.append(item.endTime?.time)
            callPeerStates.append(item.state.getStateString())
            callPeerNames.append(item.displayName)
            callPeerSecondaryIDs.append(if (item.peerSecondaryAddress == null) "" else item.peerSecondaryAddress)
        }

        val uuid = callRecord.callUuid
        val accountUid = callRecord.sourceCall!!.pps.accountID.accountUniqueID
        val timeStamp = Date().time
        contentValues.clear()
        contentValues.put(CallHistoryService.UUID, uuid)
        contentValues.put(CallHistoryService.TIME_STAMP, timeStamp)
        contentValues.put(CallHistoryService.ACCOUNT_UID, accountUid)
        contentValues.put(CallHistoryService.CALL_START, callRecord.startTime.time)
        contentValues.put(CallHistoryService.CALL_END, callRecord.endTime!!.time)
        contentValues.put(CallHistoryService.DIRECTION, callRecord.direction)
        contentValues.put(CallHistoryService.ENTITY_FULL_JID, callPeerIDs.toString())
        contentValues.put(CallHistoryService.ENTITY_CALL_START, callPeerStartTime.toString())
        contentValues.put(CallHistoryService.ENTITY_CALL_END, callPeerEndTime.toString())
        contentValues.put(CallHistoryService.ENTITY_CALL_STATE, callPeerStates.toString())
        contentValues.put(CallHistoryService.CALL_END_REASON, callRecord.endReason)
        contentValues.put(CallHistoryService.ENTITY_JID, callPeerNames.toString())
        contentValues.put(CallHistoryService.SEC_ENTITY_ID, callPeerSecondaryIDs.toString())
        mDB.insert(CallHistoryService.TABLE_NAME, null, contentValues)
    }

    /**
     * Adding progress listener for monitoring progress of search process
     *
     * @param listener HistorySearchProgressListener
     */
    override fun addSearchProgressListener(listener: CallHistorySearchProgressListener) {
        synchronized(progressListeners) { progressListeners.put(listener, SearchProgressWrapper(listener)) }
    }

    /**
     * Removing progress listener
     *
     * @param listener HistorySearchProgressListener
     */
    override fun removeSearchProgressListener(listener: CallHistorySearchProgressListener) {
        synchronized(progressListeners) { progressListeners.remove(listener) }
    }

    /**
     * Adding `CallHistoryRecordListener` listener to the list.
     *
     * @param listener CallHistoryRecordListener
     */
    override fun addCallHistoryRecordListener(listener: CallHistoryPeerRecordListener) {
        synchronized(callHistoryRecordListeners) { callHistoryRecordListeners.add(listener) }
    }

    /**
     * Removing `CallHistoryRecordListener` listener
     *
     * @param listener CallHistoryRecordListener
     */
    override fun removeCallHistoryRecordListener(listener: CallHistoryPeerRecordListener) {
        synchronized(callHistoryRecordListeners) { callHistoryRecordListeners.remove(listener) }
    }

    /**
     * Fires the given event to all `CallHistoryRecordListener` listeners
     *
     * @param event the `CallHistoryRecordReceivedEvent` event to be fired
     */
    private fun fireCallHistoryRecordReceivedEvent(event: CallHistoryPeerRecordEvent) {
        var tmpListeners: List<CallHistoryPeerRecordListener?>
        synchronized(callHistoryRecordListeners) { tmpListeners = LinkedList(callHistoryRecordListeners) }
        for (listener in tmpListeners) {
            listener!!.callPeerRecordReceived(event)
        }
    }

    /**
     * CallListener implementation for incoming calls
     *
     * @param event CallEvent
     */
    override fun incomingCallReceived(event: CallEvent) {
        handleNewCall(event.sourceCall, CallRecord.IN)
    }

    /**
     * CallListener implementation for outgoing calls
     *
     * @param event CallEvent
     */
    override fun outgoingCallCreated(event: CallEvent) {
        handleNewCall(event.sourceCall, CallRecord.OUT)
    }

    /**
     * CallListener implementation for call endings
     *
     * @param event CallEvent
     */
    override fun callEnded(event: CallEvent) {
        // We store the call in the callStateChangeEvent where we
        // have more information on the previous state of the call.
    }

    /**
     * Adding a record for joining peer
     *
     * @param callPeer CallPeer
     */
    private fun handlePeerAdded(callPeer: CallPeer) {
        // no such call
        val callRecord = findCallRecord(callPeer.getCall()) ?: return

        callPeer.addCallPeerListener(object : CallPeerAdapter() {
            override fun peerStateChanged(evt: CallPeerChangeEvent) {
                if (evt.newValue != CallPeerState.DISCONNECTED) {
                    val peerRecord = findPeerRecord(evt.getSourceCallPeer()) ?: return
                    val newState = evt.newValue as CallPeerState
                    if (newState == CallPeerState.CONNECTED && !CallPeerState.isOnHold(evt.oldValue as CallPeerState)) {
                        peerRecord.startTime = Date()
                    }
                    peerRecord.state = newState
                    // Disconnected / Busy
                    // Disconnected / Connecting - fail
                    // Disconnected / Connected
                }
            }
        })

        val startDate = Date()
        val newRec = CallPeerRecordImpl(callPeer.getAddress(), startDate, startDate)
        newRec.displayName = callPeer.getDisplayName()
        callRecord.peerRecords.add(newRec)
        fireCallHistoryRecordReceivedEvent(CallHistoryPeerRecordEvent(callPeer.getAddress(),
                startDate, callPeer.getProtocolProvider()))
    }

    /**
     * Adding a record for removing peer from call
     *
     * @param callPeer CallPeer
     * @param srcCall Call
     */
    private fun handlePeerRemoved(callPeer: CallPeer, srcCall: Call<*>) {
        val callRecord = findCallRecord(srcCall) ?: return
        val pAddress = callPeer.getAddress()
        val cpRecord = callRecord.findPeerRecord(pAddress) as CallPeerRecordImpl? ?: return
        // no such peer
        if (callPeer.getState() != CallPeerState.DISCONNECTED) cpRecord.state = callPeer.getState()!!
        val cpRecordState = cpRecord.state
        if (cpRecordState == CallPeerState.CONNECTED || CallPeerState.isOnHold(cpRecordState)) {
            cpRecord.endTime = Date()
        }
    }

    /**
     * Updates the secondary address field of call record.
     *
     * @param date the start date of the record which will be updated.
     * @param peerAddress the address of the peer of the record which will be updated.
     * @param address the value of the secondary address .
     */
    override fun updateCallRecordPeerSecondaryAddress(date: Date, peerAddress: String, address: String) {
        var callRecordFound = false
        synchronized(currentCallRecords) {
            for (record in currentCallRecords) {
                for (peerRecord in record.peerRecords) {
                    if (peerRecord.peerAddress == peerAddress && peerRecord.startTime == date) {
                        callRecordFound = true
                        peerRecord.peerSecondaryAddress = address
                    }
                }
            }
        }
        if (callRecordFound) return

        // update the record in db for found match record
        val columns = arrayOf(CallHistoryService.UUID, CallHistoryService.ENTITY_FULL_JID,
                CallHistoryService.ENTITY_CALL_START, CallHistoryService.SEC_ENTITY_ID)
        var args = arrayOf(peerAddress, date!!.time.toString())
        val cursor = mDB.query(CallHistoryService.TABLE_NAME, columns,
                CallHistoryService.ENTITY_FULL_JID + " LIKE '%?%' AND "
                        + CallHistoryService.ENTITY_CALL_START + " LIKE '%?%')",
                args, null, null, ORDER_ASC)

        // process only for record that have matched peerID and date and same index locations
        while (cursor.moveToNext()) {
            val uuid = cursor.getString(0)
            val peerIDs = getCSVs(cursor.getString(1))
            val i = peerIDs.indexOf(peerAddress)
            if (i == -1) continue
            val dateString = getCSVs(cursor.getString(2))[i]
            if (date.time.toString() != dateString) continue
            val secondaryID = getCSVs(cursor.getString(3))
            secondaryID[i] = address
            var secEntityID: String? = ""
            for ((j, sid) in secondaryID.withIndex()) {
                if (j != 0) secEntityID += DELIMITER
                secEntityID += sid
            }
            args = arrayOf(uuid)
            contentValues.clear()
            contentValues.put(CallHistoryService.SEC_ENTITY_ID, secEntityID)
            mDB.update(CallHistoryService.TABLE_NAME, contentValues, CallHistoryService.UUID + "=?", args)
        }
        cursor.close()
    }

    /**
     * Finding a CallRecord for the given call
     *
     * @param call Call
     *
     * @return CallRecord
     */
    private fun findCallRecord(call: Call<*>?): CallRecordImpl? {
        synchronized(currentCallRecords) {
            for (item in currentCallRecords) {
                if (item.sourceCall!! == call) return item
            }
        }
        return null
    }

    /**
     * Returns the peer record for the given peer
     *
     * @param callPeer CallPeer peer
     *
     * @return CallPeerRecordImpl the corresponding record
     */
    private fun findPeerRecord(callPeer: CallPeer): CallPeerRecordImpl? {
        val record = findCallRecord(callPeer.getCall()) ?: return null
        return record.findPeerRecord(callPeer.getAddress()) as CallPeerRecordImpl?
    }

    /**
     * Adding a record for a new call
     *
     * @param sourceCall Call
     * @param direction String
     */
    private fun handleNewCall(sourceCall: Call<*>, direction: String) {
        // if call exist. its not new
        synchronized(currentCallRecords) {
            for (currentCallRecord in currentCallRecords) {
                if (currentCallRecord.sourceCall!! == sourceCall) return
            }
        }
        val newRecord = CallRecordImpl(null, direction, Date(), null)
        newRecord.sourceCall = sourceCall
        sourceCall.addCallChangeListener(historyCallChangeListener)
        synchronized(currentCallRecords) { currentCallRecords.add(newRecord) }

        // if has already participants Dispatch them
        val callPeers = sourceCall.getCallPeers()
        while (callPeers.hasNext()) {
            handlePeerAdded(callPeers.next())
        }
    }

    /**
     * A wrapper around HistorySearchProgressListener that fires events for CallHistorySearchProgressListener
     */
    private inner class SearchProgressWrapper(private val listener: CallHistorySearchProgressListener) : HistorySearchProgressListener {
        var contactCount = 0
        var currentContactCount = 0
        var currentProgress = 0
        var lastHistoryProgress = 0
        override fun progressChanged(evt: ProgressEvent?) {
            val progress = getProgressMapping(evt!!.progress)
            listener.progressChanged(net.java.sip.communicator.service.callhistory.event.ProgressEvent(this@CallHistoryServiceImpl, evt, progress))
        }

        /**
         * Calculates the progress according the count of the contacts we will search
         *
         * @param historyProgress int
         *
         * @return int
         */
        private fun getProgressMapping(historyProgress: Int): Int {
            currentProgress += (historyProgress - lastHistoryProgress) / contactCount
            if (historyProgress == HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE) {
                currentContactCount++
                lastHistoryProgress = 0

                // this is the last one and the last event fire the max
                // there will be looses in currentProgress due to the division
                if (currentContactCount == contactCount) currentProgress = CallHistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE
            } else lastHistoryProgress = historyProgress
            return currentProgress
        }

        /**
         * clear the values
         */
        fun clear() {
            contactCount = 0
            currentProgress = 0
            lastHistoryProgress = 0
            currentContactCount = 0
        }
    }

    /**
     * Used to compare CallRecords and to be ordered in TreeSet according their timestamp
     */
    private class CallRecordComparator : Comparator<CallRecord> {
        override fun compare(o1: CallRecord, o2: CallRecord): Int {
            return o2.startTime.compareTo(o1.startTime)
        }
    }

    /**
     * Receive events for adding or removing peers from a call
     */
    private inner class HistoryCallChangeListener : CallChangeListener {
        /**
         * Indicates that a new call peer has joined the source call.
         *
         * @param evt the `CallPeerEvent` containing the source call and call peer.
         */
        override fun callPeerAdded(evt: CallPeerEvent) {
            handlePeerAdded(evt.getSourceCallPeer())
        }

        /**
         * Indicates that a call peer has left the source call.
         *
         * @param evt the `CallPeerEvent` containing the source call and call peer.
         */
        override fun callPeerRemoved(evt: CallPeerEvent) {
            handlePeerRemoved(evt.getSourceCallPeer(), evt.getSourceCall())
        }

        /**
         * A dummy implementation of this listener's callStateChanged() method.
         *
         * @param evt the `CallChangeEvent` instance containing the source calls and its old and new state.
         */
        override fun callStateChanged(evt: CallChangeEvent) {
            // no such call
            val callRecord = findCallRecord(evt.sourceCall) ?: return
            if (CallChangeEvent.CALL_STATE_CHANGE != evt.propertyName) return

            if (CallState.CALL_ENDED == evt.newValue) {
                var writeRecord = true
                if (CallState.CALL_INITIALIZATION == evt.oldValue) {
                    callRecord.endTime = callRecord.startTime

                    // if call was answered elsewhere, add its reason; so we can distinguish it from missed
                    if (evt.cause != null && evt.cause.reasonCode == CallPeerChangeEvent.NORMAL_CALL_CLEARING) {
                        callRecord.endReason = evt.cause.reasonCode
                        if ("Call completed elsewhere" == evt.cause.getReasonString()) {
                            writeRecord = false
                        }
                    }
                } else callRecord.endTime = Date()

                if (writeRecord) {
                    writeCall(callRecord)
                }
                synchronized(currentCallRecords) {
                    currentCallRecords.remove(callRecord)
                }
            }
        }
    }

    /**
     * Permanently removes all locally stored call history.
     */
    override fun eraseLocallyStoredHistory(callUUIDs: List<String?>?) {
        for (uuid in callUUIDs!!) {
            val args = arrayOf(uuid)
            mDB.delete(CallHistoryService.TABLE_NAME, CallHistoryService.UUID + "=?", args)
        }
    }

    /**
     * Permanently removes all locally stored call history on and before the given endDate.
     *
     * @return number of call records deleted
     */
    override fun eraseLocallyStoredHistoryBefore(endDate: Date?): Int {
        val endTimeStamp = endDate!!.time.toString()
        val args = arrayOf(endTimeStamp)
        return mDB.delete(CallHistoryService.TABLE_NAME, CallHistoryService.CALL_START + "<=?", args)
    }

    companion object {
        /**
         * Sort database message records by TimeStamp in ASC or DESC
         */
        private const val ORDER_ASC = CallHistoryService.CALL_START + " ASC"
        private const val ORDER_DESC = CallHistoryService.CALL_START + " DESC"
        private val STRUCTURE_NAMES = arrayOf<String?>(
                "accountUID", "callStart", "callEnd",
                "dir", "callParticipantIDs",
                "callParticipantStart", "callParticipantEnd",
                "callParticipantStates", "callEndReason",
                "callParticipantNames", "secondaryCallParticipantIDs")
        private val recordStructure = HistoryRecordStructure(STRUCTURE_NAMES)
        private const val DELIMITER = ','

        /**
         * Create from the retrieved database mProperties to chatMessages
         *
         * @param mProperties CallRecord properties converted from cursor
         *
         * @return CallRecordImpl
         */
        fun createCallRecordFromProperties(mProperties: Map<String, String>): CallRecord {
            val callPeerIDs: List<String>
            val callPeerNames: List<String>
            val callPeerStart: List<String?>
            val callPeerEnd: List<String?>
            val callPeerSecondaryIDs: List<String?>
            val result = CallRecordImpl(mProperties[CallHistoryService.UUID],
                    mProperties[CallHistoryService.DIRECTION]!!,
                    Date(mProperties[CallHistoryService.CALL_START]!!.toLong()),
                    Date(mProperties[CallHistoryService.CALL_END]!!.toLong()))
            result.protocolProvider = getProtocolProvider(mProperties[CallHistoryService.ACCOUNT_UID])
            result.endReason = mProperties[CallHistoryService.CALL_END_REASON]!!.toInt()
            callPeerIDs = getCSVs(mProperties[CallHistoryService.ENTITY_FULL_JID])
            callPeerStart = getCSVs(mProperties[CallHistoryService.ENTITY_CALL_START])
            callPeerEnd = getCSVs(mProperties[CallHistoryService.ENTITY_CALL_END])
            val callPeerStates = getStates(mProperties[CallHistoryService.ENTITY_CALL_STATE])
            callPeerNames = getCSVs(mProperties[CallHistoryService.ENTITY_JID])
            callPeerSecondaryIDs = getCSVs(mProperties[CallHistoryService.SEC_ENTITY_ID])
            val callPeerCount = callPeerIDs.size
            for (i in 0 until callPeerCount) {
                // As we iterate over the CallPeer IDs we could not be sure that for some reason the
                // start or end call list could result in different size lists, so we check this first.
                var callPeerStartValue: Date
                var callPeerEndValue: Date
                if (i < callPeerStart.size) {
                    callPeerStartValue = Date(callPeerStart[i].toLong())
                } else {
                    callPeerStartValue = result.startTime
                    Timber.i("Call history start time list different from ids list.")
                }
                if (i < callPeerEnd.size) {
                    callPeerEndValue = Date(callPeerEnd[i].toLong())
                } else {
                    callPeerEndValue = result.endTime!!
                    Timber.i("Call history end time list different from ids list.")
                }
                val cpr = CallPeerRecordImpl(callPeerIDs[i],
                        callPeerStartValue, callPeerEndValue)
                var callPeerSecondaryID: String? = null
                if (callPeerSecondaryIDs.isNotEmpty()) callPeerSecondaryID = callPeerSecondaryIDs[i]
                if (callPeerSecondaryID != null && callPeerSecondaryID != "") {
                    cpr.peerSecondaryAddress = callPeerSecondaryID
                }

                // if there is no record about the states (backward compatibility)
                if (i < callPeerStates.size) cpr.state = callPeerStates[i] else Timber.i("Call history state list different from ids list.")
                if (i < callPeerNames.size) cpr.displayName = callPeerNames[i]
                result.peerRecords.add(cpr)
            }
            return result
        }

        /**
         * Returns list of String items contained in the supplied string separated by DELIMITER
         *
         * @param str String
         *
         * @return LinkedList
         */
        private fun getCSVs(str: String?): MutableList<String> {
            val result = LinkedList<String>()
            if (str == null) return result
            val stt = StreamTokenizer(StringReader(str))
            stt.resetSyntax()
            stt.wordChars('\u0000'.code, '\uFFFF'.code)
            stt.eolIsSignificant(false)
            stt.quoteChar('"'.code)
            stt.whitespaceChars(DELIMITER.code, DELIMITER.code)
            try {
                while (stt.nextToken() != StreamTokenizer.TT_EOF) {
                    if (stt.sval != null) {
                        result.add(stt.sval.trim { it <= ' ' })
                    }
                }
            } catch (e: IOException) {
                Timber.e("failed to parse %s: %s", str, e.message)
            }
            return result
        }

        /**
         * Get the delimited strings and converts them to CallPeerState
         *
         * @param str String delimited string states
         *
         * @return LinkedList the converted values list
         */
        private fun getStates(str: String?): List<CallPeerState> {
            val result = LinkedList<CallPeerState>()
            val stateStrs = getCSVs(str)
            for (item in stateStrs) {
                result.add(convertStateStringToState(item))
            }
            return result
        }

        /**
         * Converts the state string to state
         *
         * @param state String the string
         *
         * @return CallPeerState the state
         */
        private fun convertStateStringToState(state: String?): CallPeerState {
            return when (state) {
                CallPeerState._CONNECTED -> CallPeerState.CONNECTED
                CallPeerState._BUSY -> CallPeerState.BUSY
                CallPeerState._FAILED -> CallPeerState.FAILED
                CallPeerState._DISCONNECTED -> CallPeerState.DISCONNECTED
                CallPeerState._ALERTING_REMOTE_SIDE -> CallPeerState.ALERTING_REMOTE_SIDE
                CallPeerState._CONNECTING -> CallPeerState.CONNECTING
                CallPeerState._ON_HOLD_LOCALLY -> CallPeerState.ON_HOLD_LOCALLY
                CallPeerState._ON_HOLD_MUTUALLY -> CallPeerState.ON_HOLD_MUTUALLY
                CallPeerState._ON_HOLD_REMOTELY -> CallPeerState.ON_HOLD_REMOTELY
                CallPeerState._INITIATING_CALL -> CallPeerState.INITIATING_CALL
                CallPeerState._INCOMING_CALL -> CallPeerState.INCOMING_CALL
                else -> CallPeerState.UNKNOWN
            }
        }

        /**
         * Returns the `ProtocolProviderService` corresponding to the given account identifier.
         *
         * @param accountUID the identifier of the account.
         *
         * @return the `ProtocolProviderService` corresponding to the given account identifier
         */
        private fun getProtocolProvider(accountUID: String?): ProtocolProviderService? {
            val ppsRefs = CallHistoryActivator.protocolProviderFactories
            if (ppsRefs != null) {
                for (providerFactory in ppsRefs.values) {
                    for (accountID in providerFactory!!.getRegisteredAccounts()) {
                        if (accountID.accountUniqueID == accountUID) {
                            val serRef = providerFactory.getProviderForAccount(accountID)
                            return CallHistoryActivator.bundleContext!!.getService(serRef)
                        }
                    }
                }
            }
            return null
        }
    }
}