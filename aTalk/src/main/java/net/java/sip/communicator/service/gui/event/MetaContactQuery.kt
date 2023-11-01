/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event

import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import java.util.*

/**
 * The `MetaContactQuery` corresponds to a particular query made through
 * the `MetaContactListSource`. Each query once started could be
 * canceled. One could also register a listener in order to be notified for
 * changes in query status and query contact results.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class MetaContactQuery {
    /**
     * Returns `true` if this query has been canceled, otherwise returns `false`.
     *
     * @return `true` if this query has been canceled, otherwise returns `false`.
     */
    var isCanceled = false
        private set

    /**
     * Returns the current number of results received for this query.
     *
     * @return the current number of results received for this query
     */
    var resultCount = 0
        private set

    /**
     * A list of all registered query listeners.
     */
    private val queryListeners = LinkedList<MetaContactQueryListener>()

    /**
     * Cancels this query.
     */
    fun cancel() {
        isCanceled = true
        queryListeners.clear()
    }

    /**
     * Sets the result count of this query. This method is meant to be used to
     * set the initial result count which is before firing any events. The
     * result count would be then augmented each time the fireQueryEvent is called.
     *
     * @param resultCount the initial result count to set
     */
    fun setInitialResultCount(resultCount: Int) {
        this.resultCount = resultCount
    }

    /**
     * Adds the given `MetaContactQueryListener` to the list of
     * registered listeners. The `MetaContactQueryListener` would be
     * notified each time a new `MetaContactQuery` result has been
     * received or if the query has been completed or has been canceled by user
     * or for any other reason.
     *
     * @param l the `MetaContactQueryListener` to add
     */
    fun addContactQueryListener(l: MetaContactQueryListener) {
        synchronized(queryListeners) { queryListeners.add(l) }
    }

    /**
     * Removes the given `MetaContactQueryListener` to the list of
     * registered listeners. The `MetaContactQueryListener` would be
     * notified each time a new `MetaContactQuery` result has been
     * received or if the query has been completed or has been canceled by user
     * or for any other reason.
     *
     * @param l the `MetaContactQueryListener` to remove
     */
    fun removeContactQueryListener(l: MetaContactQueryListener) {
        synchronized(queryListeners) { queryListeners.remove(l) }
    }

    /**
     * Notifies the `MetaContactQueryListener` that a new
     * `MetaContact` has been received as a result of a search.
     *
     * @param metaContact the received `MetaContact`
     */
    fun fireQueryEvent(metaContact: MetaContact?) {
        resultCount++
        val event = MetaContactQueryEvent(this, metaContact)
        var listeners: List<MetaContactQueryListener>
        synchronized(queryListeners) { listeners = LinkedList(queryListeners) }
        for (listener in listeners) {
            listener.metaContactReceived(event)
        }
    }

    /**
     * Notifies the `MetaContactQueryListener` that a new
     * `MetaGroup` has been received as a result of a search.
     *
     * @param metaGroup the received `MetaGroup`
     */
    fun fireQueryEvent(metaGroup: MetaContactGroup?) {
        val event = MetaGroupQueryEvent(this, metaGroup)
        var listeners: List<MetaContactQueryListener>
        synchronized(queryListeners) { listeners = LinkedList(queryListeners) }
        for (listener in listeners) {
            listener.metaGroupReceived(event)
        }
    }

    /**
     * Notifies the `MetaContactQueryListener` that this query has changed its status.
     *
     * @param queryStatus the new query status
     */
    fun fireQueryEvent(queryStatus: Int) {
        val event = MetaContactQueryStatusEvent(this, queryStatus)
        var listeners: List<MetaContactQueryListener>
        synchronized(queryListeners) { listeners = LinkedList(queryListeners) }
        for (listener in listeners) {
            listener.metaContactQueryStatusChanged(event)
        }
    }
}