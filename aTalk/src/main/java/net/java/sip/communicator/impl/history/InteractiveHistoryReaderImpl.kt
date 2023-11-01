/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.history

import net.java.sip.communicator.service.history.HistoryQuery
import net.java.sip.communicator.service.history.HistoryService
import net.java.sip.communicator.service.history.InteractiveHistoryReader
import net.java.sip.communicator.service.history.event.HistoryQueryStatusEvent
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * The `InteractiveHistoryReaderImpl` is an implementation of the
 * `InteractiveHistoryReader` interface. It allows to search in the history in an
 * interactive way, i.e. be able to cancel the search at any time and track the results through a
 * `HistoryQueryListener`.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class InteractiveHistoryReaderImpl
/**
 * Creates an instance of `InteractiveHistoryReaderImpl` by specifying the
 * corresponding `history` implementation.
 *
 * @param history
 * the corresponding `HistoryImpl` to read from
 */
(
        /**
         * The `HistoryImpl` where this reader is registered.
         */
        private val history: HistoryImpl) : InteractiveHistoryReader {
    /**
     * Searches the history for all records containing the `keyword`.
     *
     * @param keyword
     * the keyword to search for
     * @param field
     * the field where to look for the keyword
     * @param recordCount
     * limits the result to this record count
     * @return the found records
     */
    override fun findByKeyword(keyword: String, field: String?, recordCount: Int): HistoryQuery {
        return findByKeywords(arrayOf(keyword), field, recordCount)
    }

    /**
     * Searches the history for all records containing all `keywords`.
     *
     * @param keywords
     * array of keywords we search for
     * @param field
     * the field where to look for the keyword
     * @param recordCount
     * limits the result to this record count
     * @return the found records
     */
    override fun findByKeywords(keywords: Array<String>?, field: String?, recordCount: Int): HistoryQuery {
        return find(null, null, keywords, field, false, recordCount)
    }

    /**
     * Finds the history results corresponding to the given criteria.
     *
     * @param startDate
     * the start date
     * @param endDate
     * the end date
     * @param keywords
     * an array of keywords to search for
     * @param field
     * the field, where to search the keywords
     * @param caseSensitive
     * indicates if the search should be case sensitive
     * @param resultCount
     * the desired number of results
     * @return the `HistoryQuery` that could be used to track the results or to cancel the
     * search
     */
    private fun find(startDate: Date?, endDate: Date?, keywords: Array<String>?,
            field: String?, caseSensitive: Boolean, resultCount: Int): HistoryQuery {
        val queryString = StringBuilder()
        for (s in keywords!!) {
            queryString.append(' ')
            queryString.append(s)
        }
        val query = HistoryQueryImpl(queryString.toString())
        object : Thread() {
            override fun run() {
                find(startDate, endDate, keywords, field, caseSensitive, resultCount, query)
            }
        }.start()
        return query
    }

    /**
     * Finds the history results corresponding to the given criteria.
     *
     * @param startDate
     * the start date
     * @param endDate
     * the end date
     * @param keywords
     * an array of keywords to search for
     * @param field
     * the field, where to search the keywords
     * @param caseSensitive
     * indicates if the search should be case sensitive
     * @param resultCount
     * the desired number of results
     * @param query
     * the query tracking the results
     */
    private fun find(startDate: Date?, endDate: Date?, keywords: Array<String>?, field: String?,
            caseSensitive: Boolean, resultCount: Int, query: HistoryQueryImpl) {
        var iResultCount = resultCount
        val fileList = HistoryReaderImpl.filterFilesByDate(history.fileList, startDate, endDate, true)
        val fileIterator = fileList.iterator()
        val sdf = SimpleDateFormat(HistoryService.DATE_FORMAT, Locale.US)
        while (fileIterator.hasNext() && iResultCount > 0 && !query.isCanceled) {
            val filename = fileIterator.next()
            val doc = history.getDocumentForFile(filename) ?: continue
            val nodes = doc.getElementsByTagName("record")
            var i = nodes.length - 1
            while (i >= 0 && !query.isCanceled) {
                val node = nodes.item(i)
                var timestamp: Date
                val ts = node.attributes.getNamedItem("timestamp").nodeValue
                timestamp = try {
                    sdf.parse(ts)!!
                } catch (e: ParseException) {
                    Date(ts.toLong())
                }
                if (HistoryReaderImpl.Companion.isInPeriod(timestamp, startDate, endDate)) {
                    val propertyNodes = node.childNodes
                    val record = HistoryReaderImpl.filterByKeyword(propertyNodes,
                            timestamp, keywords, field, caseSensitive)!!
                    if (record != null) {
                        query.addHistoryRecord(record!!)
                        iResultCount--
                    }
                }
                i--
            }
        }
        if (query.isCanceled) query.setStatus(HistoryQueryStatusEvent.QUERY_CANCELED) else query.setStatus(HistoryQueryStatusEvent.QUERY_COMPLETED)
    }
}