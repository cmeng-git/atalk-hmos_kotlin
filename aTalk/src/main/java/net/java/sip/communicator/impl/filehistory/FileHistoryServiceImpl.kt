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
package net.java.sip.communicator.impl.filehistory

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import net.java.sip.communicator.impl.protocol.jabber.OutgoingFileSendEntityImpl
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.filehistory.FileHistoryService
import net.java.sip.communicator.service.filehistory.FileRecord
import net.java.sip.communicator.service.history.HistoryService
import net.java.sip.communicator.service.msghistory.MessageHistoryService
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.FileTransfer
import net.java.sip.communicator.service.protocol.IMessage
import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.FileTransferCreatedEvent
import net.java.sip.communicator.service.protocol.event.FileTransferRequestEvent
import net.java.sip.communicator.service.protocol.event.ScFileTransferListener
import net.java.sip.communicator.util.ServiceUtils
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.gui.chat.ChatMessage
import org.atalk.hmos.gui.chat.ChatSession
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.persistance.DatabaseBackend
import org.jxmpp.util.XmppStringUtils
import org.osgi.framework.BundleContext
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.io.IOException
import java.util.*

/**
 * File History Service stores info for file transfers from various protocols.
 * It handles both the outgoing and incoming file transfer events.
 * ScFileTransferListener = To handle Incoming fileTransfer request callbacks;
 * except fileTransferCreated which take care of both incoming and outgoing file creation.
 *
 * @author Eng Chong Meng
 */
class FileHistoryServiceImpl : FileHistoryService, ServiceListener, ScFileTransferListener {
    /**
     * The BundleContext that we got from the OSGI bus.
     */
    private var bundleContext: BundleContext? = null
    private lateinit var mDB: SQLiteDatabase
    private var mhs: MessageHistoryService? = null

    /**
     * Starts the service. Check the current registered protocol providers which supports
     * FileTransfer and adds a listener to them.
     *
     * @param bc BundleContext
     */
    fun start(bc: BundleContext) {
        Timber.d("Starting the file history implementation.")
        bundleContext = bc

        // start listening for newly register or removed protocol providers
        bc.addServiceListener(this)
        var ppsRefs: Array<ServiceReference<ProtocolProviderService>>? = null
        try {
            ppsRefs = bc.getServiceReferences(ProtocolProviderService::class.java.name, null) as  Array<ServiceReference<ProtocolProviderService>>?
        } catch (e: InvalidSyntaxException) {
            e.printStackTrace()
        }

        // in case we found any
        if ((ppsRefs != null) && (ppsRefs.isNotEmpty())) {
            Timber.d("Found %s installed providers.", ppsRefs.size)
            for (ppsRef: ServiceReference<ProtocolProviderService> in ppsRefs) {
                val pps = bc.getService(ppsRef)
                handleProviderAdded(pps)
            }
        }
        mDB = DatabaseBackend.writableDB
    }

    /**
     * Stops the service.
     *
     * @param bc BundleContext
     */
    fun stop(bc: BundleContext) {
        bc.removeServiceListener(this)
        var ppsRefs: Array<ServiceReference<ProtocolProviderService>>? = null
        try {
            ppsRefs = bc.getServiceReferences(ProtocolProviderService::class.java.name, null) as  Array<ServiceReference<ProtocolProviderService>>?
        } catch (e: InvalidSyntaxException) {
            e.printStackTrace()
        }

        // in case we found any
        if ((ppsRefs != null) && (ppsRefs.isNotEmpty())) {
            for (ppsRef: ServiceReference<ProtocolProviderService> in ppsRefs) {
                val pps = bc.getService(ppsRef)
                handleProviderRemoved(pps)
            }
        }
    }

    /**
     * When new protocol provider is registered we check does it supports FileTransfer and if so add a listener to it
     *
     * @param serviceEvent ServiceEvent
     */
    override fun serviceChanged(serviceEvent: ServiceEvent) {
        val pps = bundleContext!!.getService(serviceEvent.serviceReference)
        Timber.log(TimberLog.FINER, "Received a service event for: %s", pps.javaClass.name)

        // we don't care if the source service is not a protocol provider
        if (pps !is ProtocolProviderService) {
            return
        }

        Timber.d("Service is a protocol provider: %s", pps)
        if (serviceEvent.type == ServiceEvent.REGISTERED) {
            Timber.d("Handling registration of a new Protocol Provider.")
            handleProviderAdded(pps)
        } else if (serviceEvent.type == ServiceEvent.UNREGISTERING) {
            handleProviderRemoved(pps)
        }
    }

    /**
     * Used to attach the File History Service to existing or just registered protocol provider.
     * Checks if the provider has implementation of OperationSetFileTransfer
     *
     * @param provider ProtocolProviderService
     */
    private fun handleProviderAdded(provider: ProtocolProviderService) {
        Timber.d("Adding protocol provider %s", provider.protocolName)

        // check whether the provider has a file transfer operation set
        val opSetFileTransfer = provider.getOperationSet(OperationSetFileTransfer::class.java)
        if (opSetFileTransfer != null) {
            opSetFileTransfer.addFileTransferListener(this)
        } else {
            Timber.log(TimberLog.FINER, "Service did not have a file transfer opSet.")
        }
    }

    /**
     * Removes the specified provider from the list of currently known providers
     *
     * @param provider the ProtocolProviderService that has been unregistered.
     */
    private fun handleProviderRemoved(provider: ProtocolProviderService) {
        val opSetFileTransfer = provider.getOperationSet(OperationSetFileTransfer::class.java)
        if (opSetFileTransfer != null) {
            opSetFileTransfer.removeFileTransferListener(this)
        } else {
            Timber.log(TimberLog.FINER, "OperationSetFileTransfer service remove failed.")
        }
    }

    /**
     * Set the history service.
     *
     * @param historyService HistoryService
     */
    fun setHistoryService(historyService: HistoryService?) {}
    private fun getMHS(): MessageHistoryService? {
        if (mhs == null) mhs = ServiceUtils.getService(bundleContext, MessageHistoryService::class.java)
        return mhs
    }
    /* ============= File Transfer Handlers - ScFileTransferListener callbacks implementations ============= */
    /**
     * Receive fileTransfer requests.
     *
     * @param event FileTransferRequestEvent
     */
    override fun fileTransferRequestReceived(event: FileTransferRequestEvent) {
        val req = event.getRequest()
        val fileName = req.getFileName()
        insertRecordToDB(event, fileName!!)
    }

    /**
     * New file transfer was created; callback from both IncomingFileTransfer, OutgoingFileTransfer and
     *
     * @param event FileTransferCreatedEvent for all FileTransfers
     *
     * @see FileSendConversation.createHttpFileUploadRecord
     */
    override fun fileTransferCreated(event: FileTransferCreatedEvent?) {
        val fileTransfer = event!!.getFileTransfer()
        val contentValues = ContentValues()
        try {
            val fileName = fileTransfer.getLocalFile()!!.canonicalPath
            Timber.d("File Transfer record created in DB: %s: %s", fileTransfer.getDirection(), fileName)
            if (fileTransfer.getDirection() == FileTransfer.IN) {
                val args = arrayOf(fileTransfer.getID())
                contentValues.put(ChatMessage.FILE_PATH, fileName)
                mDB.update(ChatMessage.TABLE_NAME, contentValues, ChatMessage.UUID + "=?", args)
            } else if (fileTransfer.getDirection() == FileTransfer.OUT) {
                insertRecordToDB(event, fileName)
            }
        } catch (e: IOException) {
            Timber.e(e, "Could not add file transfer log to history")
        }
    }

    /**
     * Called when a new `IncomingFileTransferRequest` has been rejected.
     *
     * @param event the `FileTransferRequestEvent` containing the received request which was rejected.
     *
     * @see FileReceiveConversation.fileTransferRequestRejected
     */
    override fun fileTransferRequestRejected(event: FileTransferRequestEvent) {
        // Event is being handled by FileReceiveConversation; need to update both the DB and msgCache
    }

    /**
     * Called when a new `IncomingFileTransferRequest` has been cancel by the sender.
     *
     * @param event the `FileTransferRequestEvent` containing the received request which was rejected.
     *
     * @see FileReceiveConversation.fileTransferRequestCanceled
     */
    override fun fileTransferRequestCanceled(event: FileTransferRequestEvent) {
        // Event is being handled by FileReceiveConversation; need to update both the DB and msgCache
    }

    /**
     * Create new fileTransfer record in dataBase when file transfer has started
     * Also use as conversion for http file upload link message to file transfer message
     *
     * @param evt FileTransferRequestEvent or FileTransferCreatedEvent
     * @param fileName Name of the file to received or send
     */
    private fun insertRecordToDB(evt: EventObject?, fileName: String) {
        var timeStamp = 0L
        var uuid: String? = null
        val mJid: String?
        val mEntityJid: String?
        var direction = FileRecord.OUT
        var entityJid: Any? = null
        var msgType = ChatMessage.MESSAGE_FILE_TRANSFER_SEND
        val contentValues = ContentValues()
        if (evt is FileTransferRequestEvent) {
            val event = evt
            val req = event.getRequest()
            uuid = req.getID()
            entityJid = req.getSender()
            timeStamp = event.getTimestamp().time
            direction = FileRecord.IN
            msgType = ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE
            contentValues.put(ChatMessage.MSG_BODY, fileName)
        } else if (evt is FileTransferCreatedEvent) {
            val event = evt
            timeStamp = event.getTimestamp().time
            val fileTransfer = event.getFileTransfer()
            uuid = fileTransfer.getID()
            entityJid = if (fileTransfer is OutgoingFileSendEntityImpl) {
                fileTransfer.entityJid
            } else {
                fileTransfer.getContact()
            }
        }
        val sessionUuid: String?
        if (entityJid is Contact) {
            sessionUuid = getMHS()!!.getSessionUuidByJid(entityJid)
            mEntityJid = (entityJid as Contact?)!!.address
            mJid = null
        } else {
            val chatroom = entityJid as ChatRoom
            sessionUuid = getMHS()!!.getSessionUuidByJid(chatroom)
            mJid = chatroom.getParentProvider().accountID.accountJid
            mEntityJid = XmppStringUtils.parseLocalpart(mJid)
        }
        contentValues.put(ChatMessage.UUID, uuid)
        contentValues.put(ChatMessage.SESSION_UUID, sessionUuid)
        contentValues.put(ChatMessage.TIME_STAMP, timeStamp)
        contentValues.put(ChatMessage.ENTITY_JID, mEntityJid)
        contentValues.put(ChatMessage.JID, mJid)
        contentValues.put(ChatMessage.ENC_TYPE, IMessage.ENCODE_PLAIN)
        contentValues.put(ChatMessage.MSG_TYPE, msgType)
        contentValues.put(ChatMessage.DIRECTION, direction)
        contentValues.put(ChatMessage.STATUS, FileRecord.STATUS_WAITING)
        contentValues.put(ChatMessage.FILE_PATH, fileName)
        mDB.insert(ChatMessage.TABLE_NAME, null, contentValues)
    }

    /* ============= File Transfer Handlers - Update file transfer status =============
    / **
     * Update new status and fileName to the fileTransfer record in dataBase
     * Keep file uri; for retry if not converted to MESSAGE_FILE_TRANSFER_HISTORY
     *
     * @param msgUuid message UUID
     * @param status New status for update
     * @param fileName local fileName path for http downloaded file; null => no change and keep the link in MSG_BODY
     * @param encType IMessage.ENCRYPTION_NONE, ENCRYPTION_OMEMO, ENCRYPTION_OTR
     * @param msgType File Transfer message type
     *
     * @return the number of records being updated; zero means there is no record to update historyLog disabled
     */
    fun updateFTStatusToDB(msgUuid: String, status: Int, fileName: String?, encType: Int, msgType: Int): Int {
        // Timber.w(new Exception("### File in/out transfer status changes to: " + status));
        val args = arrayOf(msgUuid)
        val contentValues = ContentValues()
        contentValues.put(ChatMessage.STATUS, status)
        if (StringUtils.isNotEmpty(fileName)) {
            contentValues.put(ChatMessage.FILE_PATH, fileName)
        }
        contentValues.put(ChatMessage.ENC_TYPE, encType)
        contentValues.put(ChatMessage.MSG_TYPE, msgType)
        return mDB.update(ChatMessage.TABLE_NAME, contentValues, ChatMessage.UUID + "=?", args)
    }

    /**
     * Permanently removes locally stored chatRoom message messages (need cleanup - not used)
     */
    override fun eraseLocallyStoredHistory() {
        val args = arrayOf(java.lang.String.valueOf(ChatSession.MODE_MULTI))
        val columns = arrayOf(ChatSession.SESSION_UUID)
        val cursor = mDB.query(ChatSession.TABLE_NAME, columns,
                ChatSession.MODE + "=?", args, null, null, null)
        while (cursor.moveToNext()) {
            purgeLocallyStoredHistory(null, cursor.getString(0))
        }
        cursor.close()
        mDB.delete(ChatMessage.TABLE_NAME, null, null)
    }

    /**
     * Permanently removes locally stored message history for the sessionUuid.
     * - Remove only chatMessages for metaContacts
     * - Remove both chatSessions and chatMessages for muc
     */
    override fun eraseLocallyStoredHistory(metaContact: MetaContact?) {
        val mhs1 = getMHS()
        val contacts = metaContact!!.getContacts()
        while (contacts.hasNext()) {
            val contact = contacts.next()
            val sessionUuid = mhs!!.getSessionUuidByJid(contact!!)
            purgeLocallyStoredHistory(contact, sessionUuid)
        }
    }

    /**
     * Permanently removes locally stored message history for the sessionUuid.
     * - Remove only chatMessages for metaContacts
     * - Remove both chatSessions and chatMessages for muc
     */
    private fun purgeLocallyStoredHistory(contact: Contact?, sessionUuid: String) {
        val args = arrayOf(sessionUuid)
        if (contact != null) {
            mDB.delete(ChatMessage.TABLE_NAME, ChatMessage.SESSION_UUID + "=?", args)
        } else {
            mDB.delete(ChatSession.TABLE_NAME, ChatSession.SESSION_UUID + "=?", args)
        }
    }

    /**
     * Used to compare FileRecords and to be ordered in TreeSet according their timestamp
     */
    private class FileRecordComparator : Comparator<FileRecord> {
        override fun compare(o1: FileRecord, o2: FileRecord): Int {
            val date1 = o1.date
            val date2 = o2.date
            return date1.compareTo(date2)
        }
    }
}