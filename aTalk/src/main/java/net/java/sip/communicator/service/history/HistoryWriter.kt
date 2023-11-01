/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history

import net.java.sip.communicator.service.history.records.HistoryRecord
import java.io.IOException
import java.util.*

/**
 * @author Alexander Pelov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
interface HistoryWriter {
    /**
     * Stores the passed record complying with the historyRecordStructure.
     *
     * @param record The record to be added.
     * @throws IOException
     */
    @Throws(IOException::class)
    fun addRecord(record: HistoryRecord)

    /**
     * Stores the passed propertyValues complying with the historyRecordStructure.
     *
     * @param propertyValues The values of the record.
     * @throws IOException
     */
    @Throws(IOException::class)
    fun addRecord(propertyValues: Array<String>)

    /**
     * Stores the passed propertyValues complying with the historyRecordStructure.
     *
     * @param propertyValues The values of the record.
     * @param maxNumberOfRecords the maximum number of records to keep or value of -1 to ignore this param.
     * @throws IOException
     */
    @Throws(IOException::class)
    fun addRecord(propertyValues: Array<String>, maxNumberOfRecords: Int)

    /**
     * Stores the passed propertyValues complying with the historyRecordStructure.
     *
     * @param propertyValues The values of the record.
     * @param timestamp The timestamp of the record.
     * @throws IOException
     */
    @Throws(IOException::class)
    fun addRecord(propertyValues: Array<String>, timestamp: Date)

    /**
     * Stores the passed propertyValues complying with the historyRecordStructure.
     *
     * @param propertyValues The values of the record.
     * @param timestamp The timestamp of the record.
     * @throws IOException
     */
    @Throws(IOException::class)
    fun insertRecord(propertyValues: Array<String>, timestamp: Date, timestampProperty: String?)

    /**
     * Updates a record by searching for record with idProperty which have idValue and updating/creating the property
     * with newValue.
     *
     * @param idProperty name of the id property
     * @param idValue value of the id property
     * @param property the property to change
     * @param newValue the value of the changed property.
     */
    @Throws(IOException::class)
    fun updateRecord(idProperty: String?, idValue: String?, property: String?, newValue: String?)

    /**
     * Updates history record using given `HistoryRecordUpdater` instance to find which is the record to be
     * updated and to get the new values for the fields
     *
     * @param updater the `HistoryRecordUpdater` instance.
     */
    @Throws(IOException::class)
    fun updateRecord(updater: HistoryRecordUpdater)

    /**
     * This interface is used to find a history record to update and to get the new values for the record.
     */
    interface HistoryRecordUpdater {
        /**
         * Sets the current history record.
         *
         * @param historyRecord the history record.
         */
        fun setHistoryRecord(historyRecord: HistoryRecord)

        /**
         * Checks if the history record should be updated or not
         *
         * @return `true` if the record should be updated.
        `` */
        fun isMatching(): Boolean

        /**
         * Returns a map with the names and new values of the fields that will be updated
         *
         * @return a map with the names and new values of the fields that will be updated
         */
        fun getUpdateChanges(): Map<String?, String?>?
    }
}