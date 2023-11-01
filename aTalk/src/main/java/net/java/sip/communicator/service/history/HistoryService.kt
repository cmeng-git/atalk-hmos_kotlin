/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history

import net.java.sip.communicator.service.history.records.HistoryRecordStructure
import java.io.IOException

/**
 * This service provides the functionality to store history records. The records are called `HistoryRecord`s and
 * are grouped by ID.
 *
 * The ID may be used to set hierarchical structure. In a typical usage one may set the first string to be the userID,
 * and the second - the service name.
 *
 * @author Alexander Pelov
 * @author Eng Chong Meng
 */
interface HistoryService {
    /**
     * Returns the IDs of all existing histories.
     *
     * @return An iterator to a list of IDs.
     */
    fun getExistingIDs(): Iterator<HistoryID>

    /**
     * Returns the history associated with this ID.
     *
     * @param id The ID of the history.
     * @return Returns the history with this ID.
     * @throws IllegalArgumentException Thrown if there is no such history.
     */
    @Throws(IllegalArgumentException::class)
    fun getHistory(id: HistoryID): History?

    /**
     * Enumerates existing histories.
     *
     * @param rawId the start of the HistoryID of all the histories that will be returned.
     * @return list of histories which HistoryID starts with `rawid`.
     * @throws IllegalArgumentException if the `rawid` contains ids which are missing in current history.
     */
    @Throws(IllegalArgumentException::class)
    fun getExistingHistories(rawId: Array<String>?): List<HistoryID?>?

    /**
     * Tests if a history with the given ID exists and is loaded.
     *
     * @param id The ID to test.
     * @return True if a history with this ID exists. False otherwise.
     */
    fun isHistoryExisting(id: HistoryID?): Boolean

    /**
     * Creates a new history for this ID.
     *
     * @param id The ID of the history to be created.
     * @param recordStructure The structure of the data.
     * @return Returns the history with this ID.
     * @throws IllegalArgumentException Thrown if such history already exists.
     * @throws IOException Thrown if the history could not be created due to a IO error.
     */
    @Throws(IllegalArgumentException::class, IOException::class)
    fun createHistory(id: HistoryID, recordStructure: HistoryRecordStructure): History

    /**
     * Permanently removes local stored History
     *
     * @param id HistoryID
     * @throws IOException Thrown if the history could not be removed due to a IO error.
     */
    @Throws(IOException::class)
    fun purgeLocallyStoredHistory(id: HistoryID?)

    /**
     * Clears locally(in memory) cached histories.
     */
    fun purgeLocallyCachedHistories()

    /**
     * Moves the content of oldId history to the content of the newId.
     *
     * @param oldId id of the old and existing history
     * @param newId the place where content of oldId will be moved
     * @throws java.io.IOException problem moving content to newId.
     */
    @Throws(IOException::class)
    fun moveHistory(oldId: HistoryID?, newId: HistoryID?)

    /**
     * Checks whether a history is created and stored.
     *
     * @param id the history to check
     * @return whether a history is created and stored.
     */
    fun isHistoryCreated(id: HistoryID?): Boolean

    companion object {
        /**
         * Property and values used to be set in configuration Used in implementation to cache every opened history document
         * or not to cache them and to access them on every read
         */
        const val CACHE_ENABLED_PROPERTY = "history.CACHE_ENABLED"

        /**
         * Date format used in the XML history database.
         */
        const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
    }
}