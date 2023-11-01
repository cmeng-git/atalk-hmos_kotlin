/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history

import net.java.sip.communicator.service.history.records.HistoryRecordStructure

/**
 * @author Alexander Pelov
 * @author Yana Stamcheva
 */
interface History {
    /**
     * Returns an object which can be used to read and query this history.
     *
     * @return an object which can be used to read and query this history
     */
    fun getReader(): HistoryReader?

    /**
     * Returns an object that can be used to read and query this history. The `InteractiveHistoryReader` differs
     * from the `HistoryReader` in the way it manages query results. It allows to cancel a search at any time and
     * to track history results through a `HistoryQueryListener`.
     *
     * @return an object that can be used to read and query this history
     */
    fun getInteractiveReader(): InteractiveHistoryReader?

    /**
     * Returns an object which can be used to append records to this history.
     *
     * @return an object which can be used to append records to this history
     */
    fun getWriter(): HistoryWriter?

    /**
     * @return Returns the ID of this history.
     */
    fun getID(): HistoryID?

    /**
     * @return Returns the structure of the history records in this history.
     */
    fun getHistoryRecordsStructure(): HistoryRecordStructure?

    /**
     * Sets the given `structure` to be the new history records structure used in this history implementation.
     *
     * @param structure
     * the new `HistoryRecordStructure` to use
     */
    fun setHistoryRecordsStructure(structure: HistoryRecordStructure)
}