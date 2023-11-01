/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui

import net.java.sip.communicator.service.contactsource.ContactQuery
import net.java.sip.communicator.service.gui.event.FilterQueryListener

/**
 * The `FilterQuery` gives information about a current filtering.
 *
 * @author Yana Stamcheva
 */
abstract class FilterQuery {
    /**
     * Gets the maximum result count shown.
     *
     * @return the maximum result count shown
     */
    /**
     * Sets the maximum result count shown.
     *
     * @param resultCount the maximum result count shown
     */
    /**
     * The maximum result count for each contact source.
     */
    var maxResultShown = 10

    /**
     * A listener, which is notified when this query finishes.
     */
    private var filterQueryListener: FilterQueryListener? = null

    /**
     * Adds the given `contactQuery` to the list of filterQueries.
     * @param contactQuery the `ContactQuery` to add
     */
    abstract fun addContactQuery(contactQuery: Any?)
    /**
     * Indicates if this query has succeeded.
     * @return `true` if this query has succeeded, `false` -
     * otherwise
     */
    /**
     * Sets the `isSucceeded` property.
     * @param isSucceeded indicates if this query has succeeded
     */
    abstract var isSucceeded: Boolean

    /**
     * Indicates if this query is canceled.
     * @return `true` if this query is canceled, `false` otherwise
     */
    abstract val isCanceled: Boolean

    /**
     * Indicates if this query is canceled.
     *
     * @return `true` if this query is canceled, `false` otherwise
     */
    abstract val isRunning: Boolean

    /**
     * Cancels this filter query.
     */
    abstract fun cancel()

    /**
     * Closes this query to indicate that no more contact sub-queries would be
     * added to it.
     */
    abstract fun close()

    /**
     * Sets the given `FilterQueryListener`.
     * @param l the `FilterQueryListener` to set
     */
    fun setQueryListener(l: FilterQueryListener?) {
        filterQueryListener = l
    }

    /**
     * Removes the given query from this filter query, updates the related data
     * and notifies interested parties if this was the last query to process.
     * @param query the `ContactQuery` to remove.
     */
    abstract fun removeQuery(query: ContactQuery?)

    /**
     * Verifies if the given query is contained in this filter query.
     *
     * @param query the query we're looking for
     * @return `true` if the given `query` is contained in this
     * filter query, `false` - otherwise
     */
    abstract fun containsQuery(query: Any?): Boolean
}