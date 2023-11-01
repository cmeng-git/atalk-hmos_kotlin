/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history

import net.java.sip.communicator.service.history.event.HistorySearchProgressListener
import net.java.sip.communicator.service.history.records.HistoryRecord
import java.util.*

/**
 * Used to search over the history records
 *
 * @author Alexander Pelov
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
interface HistoryReader {
    /**
     * Searches the history for all records with timestamp after `startDate`.
     *
     * @param startDate the date after all records will be returned
     * @return the found records
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query, such as internal IO
     * error.
     */
    @Throws(RuntimeException::class)
    fun findByStartDate(startDate: Date?): QueryResultSet<HistoryRecord?>?

    /**
     * Searches the history for all records with timestamp before `endDate`.
     *
     * @param endDate the date before which all records will be returned
     * @return the found records
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query, such as internal IO
     * error.
     */
    @Throws(RuntimeException::class)
    fun findByEndDate(endDate: Date?): QueryResultSet<HistoryRecord?>?

    /**
     * Searches the history for all records with timestamp between `startDate` and
     * `endDate`.
     *
     * @param startDate start of the interval in which we search
     * @param endDate end of the interval in which we search
     * @return the found records
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query, such as internal IO
     * error.
     */
    @Throws(RuntimeException::class)
    fun findByPeriod(startDate: Date?, endDate: Date?): QueryResultSet<HistoryRecord?>?

    /**
     * Searches the history for all records containing the `keyword`.
     *
     * @param keyword the keyword to search for
     * @param field the field where to look for the keyword
     * @return the found records
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query, such as internal IO
     * error.
     */
    @Throws(RuntimeException::class)
    fun findByKeyword(keyword: String, field: String?): QueryResultSet<HistoryRecord?>?

    /**
     * Searches the history for all records containing the `keyword`.
     *
     * @param keyword the keyword to search for
     * @param field the field where to look for the keyword
     * @param caseSensitive is keywords search case sensitive
     * @return the found records
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query, such as internal IO
     * error.
     */
    @Throws(RuntimeException::class)
    fun findByKeyword(keyword: String, field: String?, caseSensitive: Boolean): QueryResultSet<HistoryRecord?>?

    /**
     * Searches the history for all records containing all `keywords`.
     *
     * @param keywords array of keywords we search for
     * @param field the field where to look for the keyword
     * @return the found records
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query, such as internal IO
     * error.
     */
    @Throws(RuntimeException::class)
    fun findByKeywords(keywords: Array<String>?, field: String?): QueryResultSet<HistoryRecord?>?

    /**
     * Searches the history for all records containing all `keywords`.
     *
     * @param keywords array of keywords we search for
     * @param field the field where to look for the keyword
     * @param caseSensitive is keywords search case sensitive
     * @return the found records
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query, such as internal IO
     * error.
     */
    @Throws(RuntimeException::class)
    fun findByKeywords(keywords: Array<String>?, field: String?, caseSensitive: Boolean): QueryResultSet<HistoryRecord?>?

    /**
     * Searches for all history records containing all `keywords`, with timestamp between
     * `startDate` and
     * `endDate`.
     *
     * @param startDate start of the interval in which we search
     * @param endDate end of the interval in which we search
     * @param keywords array of keywords we search for
     * @param field the field where to look for the keyword
     * @return the found records
     * @throws UnsupportedOperationException Thrown if an exception occurs during the execution of the query, such as internal IO
     * error.
     */
    @Throws(UnsupportedOperationException::class)
    fun findByPeriod(startDate: Date?, endDate: Date?, keywords: Array<String>?, field: String?): QueryResultSet<HistoryRecord?>?

    /**
     * Searches for all history records containing all `keywords`, with timestamp between
     * `startDate` and
     * `endDate`.
     *
     * @param startDate start of the interval in which we search
     * @param endDate end of the interval in which we search
     * @param keywords array of keywords we search for
     * @param field the field where to look for the keyword
     * @param caseSensitive is keywords search case sensitive
     * @return the found records
     * @throws UnsupportedOperationException Thrown if an exception occurs during the execution of the query, such as internal IO
     * error.
     */
    @Throws(UnsupportedOperationException::class)
    fun findByPeriod(startDate: Date?, endDate: Date?, keywords: Array<String>?, field: String?, caseSensitive: Boolean): QueryResultSet<HistoryRecord?>?

    /**
     * Returns the supplied number of recent messages
     *
     * @param count messages count
     * @return the found records
     * @throws RuntimeException
     */
    @Throws(RuntimeException::class)
    fun findLast(count: Int): QueryResultSet<HistoryRecord?>?

    /**
     * Returns the supplied number of recent messages containing all `keywords`.
     *
     * @param count messages count
     * @param keywords array of keywords we search for
     * @param field the field where to look for the keyword
     * @param caseSensitive is keywords search case sensitive
     * @return the found records
     * @throws RuntimeException
     */
    @Throws(RuntimeException::class)
    fun findLast(count: Int, keywords: Array<String>?, field: String?, caseSensitive: Boolean): QueryResultSet<HistoryRecord?>?

    /**
     * Returns the supplied number of recent messages after the given date
     *
     * @param date messages after date
     * @param count messages count
     * @return QueryResultSet the found records
     * @throws RuntimeException
     */
    @Throws(RuntimeException::class)
    fun findFirstRecordsAfter(date: Date?, count: Int): QueryResultSet<HistoryRecord?>?

    /**
     * Returns the supplied number of recent messages before the given date
     *
     * @param date messages before date
     * @param count messages count
     * @return QueryResultSet the found records
     * @throws RuntimeException
     */
    @Throws(RuntimeException::class)
    fun findLastRecordsBefore(date: Date?, count: Int): QueryResultSet<HistoryRecord?>?

    /**
     * Adding progress listener for monitoring progress of search process
     *
     * @param listener HistorySearchProgressListener
     */
    fun addSearchProgressListener(listener: HistorySearchProgressListener?)

    /**
     * Removing progress listener
     *
     * @param listener HistorySearchProgressListener
     */
    fun removeSearchProgressListener(listener: HistorySearchProgressListener?)

    /**
     * Total count of records that current history reader will read through
     *
     * @return the number of searched messages
     * @throws UnsupportedOperationException Thrown if an exception occurs during the execution of the query, such as internal IO
     * error.
     */
    @Throws(UnsupportedOperationException::class)
    fun countRecords(): Int
}