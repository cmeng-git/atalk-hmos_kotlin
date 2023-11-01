/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.metahistory

import net.java.sip.communicator.service.history.event.HistorySearchProgressListener
import java.util.*

/**
 * The Meta History Service is wrapper around the other known history services. Query them all at
 * once, sort the result and return all merged records in one collection.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
interface MetaHistoryService {
    /**
     * Returns all the records for the descriptor after the given date.
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param startDate Date the date of the first record to return
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    fun findByStartDate(services: Array<String>, descriptor: Any, startDate: Date): Collection<Any?>

    /**
     * Returns all the records before the given date
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param endDate Date the date of the last record to return
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    fun findByEndDate(services: Array<String>, descriptor: Any, endDate: Date): Collection<Any?>

    /**
     * Returns all the records between the given dates
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param startDate Date the date of the first record to return
     * @param endDate Date the date of the last record to return
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    fun findByPeriod(services: Array<String>, descriptor: Any, startDate: Date, endDate: Date): Collection<Any?>

    /**
     * Returns all the records between the given dates and having the given keywords
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param startDate Date the date of the first record to return
     * @param endDate Date the date of the last record to return
     * @param keywords array of keywords
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    fun findByPeriod(services: Array<String>, descriptor: Any, startDate: Date,
                     endDate: Date, keywords: Array<String>): Collection<Any?>

    /**
     * Returns all the records between the given dates and having the given keywords
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param startDate Date the date of the first record to return
     * @param endDate Date the date of the last record to return
     * @param keywords array of keywords
     * @param caseSensitive is keywords search case sensitive
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    fun findByPeriod(services: Array<String>, descriptor: Any, startDate: Date,
                     endDate: Date, keywords: Array<String>, caseSensitive: Boolean): Collection<Any?>

    /**
     * Returns all the records having the given keyword
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param keyword keyword
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    fun findByKeyword(services: Array<String>, descriptor: Any, keyword: String): Collection<Any?>

    /**
     * Returns all the records having the given keyword
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param keyword keyword
     * @param caseSensitive is keywords search case sensitive
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    fun findByKeyword(services: Array<String>, descriptor: Any, keyword: String, caseSensitive: Boolean): Collection<Any?>

    /**
     * Returns all the records having the given keywords
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param keywords keyword
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    fun findByKeywords(services: Array<String>, descriptor: Any, keywords: Array<String>): Collection<Any?>

    /**
     * Returns all the records having the given keywords
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param keywords keyword
     * @param caseSensitive is keywords search case sensitive
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    fun findByKeywords(services: Array<String>, descriptor: Any, keywords: Array<String>, caseSensitive: Boolean): Collection<Any?>

    /**
     * Returns the supplied number of recent records.
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param count messages count
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    fun findLast(services: Array<String>, descriptor: Any, count: Int): Collection<Any>

    /**
     * Returns the supplied number of recent records after the given date
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param startDate messages after date
     * @param count messages count
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    fun findFirstMessagesAfter(services: Array<String>, descriptor: Any, startDate: Date, count: Int): Collection<Any>

    /**
     * Returns the supplied number of recent records before the given date
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param endDate messages before date
     * @param count messages count
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    fun findLastMessagesBefore(services: Array<String>, descriptor: Any, endDate: Date, count: Int): Collection<Any>

    /**
     * Adding progress listener for monitoring progress of search process
     *
     * @param listener HistorySearchProgressListener
     */
    fun addSearchProgressListener(listener: HistorySearchProgressListener)

    /**
     * Removing progress listener
     *
     * @param listener HistorySearchProgressListener
     */
    fun removeSearchProgressListener(listener: HistorySearchProgressListener)
}