/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history

/**
 * The `InteractiveHistoryReader` allows to search in the history in an interactive way, i.e. be able to cancel
 * the search at any time and track the results through a `HistoryQueryListener`.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface InteractiveHistoryReader {
    /**
     * Searches the history for all records containing all `keywords`.
     *
     * @param keywords array of keywords we search for
     * @param field the field where to look for the keyword
     * @param recordCount limits the result to this record count
     * @return a `HistoryQuery` object allowing to track this query
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    fun findByKeywords(keywords: Array<String>?, field: String?, recordCount: Int): HistoryQuery?

    /**
     * Searches the history for all records containing the `keyword`.
     *
     * @param keyword the keyword to search for
     * @param field the field where to look for the keyword
     * @param recordCount limits the result to this record count
     * @return a `HistoryQuery` object allowing to track this query
     * @throws RuntimeException Thrown if an exception occurs during the execution of the query, such as internal IO error.
     */
    fun findByKeyword(keyword: String, field: String?, recordCount: Int): HistoryQuery?
}