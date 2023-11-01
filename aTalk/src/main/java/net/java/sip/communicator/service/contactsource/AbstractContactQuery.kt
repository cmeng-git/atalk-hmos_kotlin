/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.service.contactsource

import java.util.*

/**
 * Provides an abstract implementation of the basic functionality of `ContactQuery` and allows
 * extenders to focus on the specifics of their implementation.
 *
 * @param <T> the very type of `ContactSourceService` which performs the `ContactQuery`
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
</T> */
abstract class AbstractContactQuery<T : ContactSourceService?>
/**
 * Initializes a new `AbstractContactQuery` which is to be performed
 * by a specific `ContactSourceService`. The status of the new
 * instance is [ContactQuery.QUERY_IN_PROGRESS].
 *
 * @param contactSource the `ContactSourceService` which is to perform the new `AbstractContactQuery`
 */
protected constructor(
        /**
         * The `ContactSourceService` which is performing this `ContactQuery`.
         */
        override val contactSource: ContactSourceService) : ContactQuery {

    /**
     * Gets the `ContactSourceService` which is performing this `ContactQuery`.
     *
     * @return the `ContactSourceService` which is performing this `ContactQuery`
     * @see ContactQuery.getContactSource
     */

    /**
     * The `List` of `ContactQueryListener`s which are to be
     * notified by this `ContactQuery` about changes in its status, the
     * receipt of new `ContactSource`s via this `ContactQuery`, etc.
     */
    private val listeners = LinkedList<ContactQueryListener>()
    /**
     * Gets the status of this `ContactQuery` which can be one of the
     * `QUERY_XXX` constants defined by `ContactQuery`.
     *
     * @return the status of this `ContactQuery` which can be one of the
     * `QUERY_XXX` constants defined by `ContactQuery`
     * @see ContactQuery.getStatus
     */
    /**
     * Sets the status of this `ContactQuery`.
     *
     * @param status [ContactQuery.QUERY_CANCELED],
     * [ContactQuery.QUERY_COMPLETED], or
     * [ContactQuery.QUERY_ERROR]
     */
    /**
     * The status of this `ContactQuery` which is one of the
     * `QUERY_XXX` constants defined by the `ContactQuery` class.
     */
    override var status = ContactQuery.QUERY_IN_PROGRESS
        set(status) {
            if (this.status != status) {
                val eventType: Int
                eventType = when (status) {
                    ContactQuery.QUERY_CANCELED -> ContactQueryStatusEvent.QUERY_CANCELED
                    ContactQuery.QUERY_COMPLETED -> ContactQueryStatusEvent.QUERY_COMPLETED
                    ContactQuery.QUERY_ERROR -> ContactQueryStatusEvent.QUERY_ERROR
                    ContactQuery.QUERY_IN_PROGRESS -> throw IllegalArgumentException("status")
                    else -> throw IllegalArgumentException("status")
                }
                field = status
                fireQueryStatusChanged(eventType)
            }
        }

    /**
     * Adds a `ContactQueryListener` to the list of listeners interested
     * in notifications about this `ContactQuery` changing its status,
     * the receipt of new `SourceContact`s via this `ContactQuery`, etc.
     *
     * @param l the `ContactQueryListener` to be added to the list of
     * listeners interested in the notifications raised by this `ContactQuery`
     * @see ContactQuery.addContactQueryListener
     */
    override fun addContactQueryListener(l: ContactQueryListener?) {
        if (l == null) throw NullPointerException("l") else {
            synchronized(listeners) { if (!listeners.contains(l)) listeners.add(l) }
        }
    }

    /**
     * Cancels this `ContactQuery`.
     *
     * @see ContactQuery.cancel
     */
    override fun cancel() {
        if (status == ContactQuery.QUERY_IN_PROGRESS) status = ContactQuery.QUERY_CANCELED
    }
    /**
     * Notifies the `ContactQueryListener`s registered with this
     * `ContactQuery` that a new `SourceContact` has been received.
     *
     * @param contact the `SourceContact` which has been received and
     * which the registered `ContactQueryListener`s are to be notified about
     * @param showMoreEnabled indicates whether show more label should be shown or not.
     */
    /**
     * Notifies the `ContactQueryListener`s registered with this
     * `ContactQuery` that a new `SourceContact` has been received.
     *
     * @param contact the `SourceContact` which has been received and
     * which the registered `ContactQueryListener`s are to be notified about
     */
    protected fun fireContactReceived(contact: SourceContact?, showMoreEnabled: Boolean = true) {
        var ls: Array<ContactQueryListener>
        synchronized(listeners) { ls = listeners.toTypedArray() }
        val ev = ContactReceivedEvent(this, contact, showMoreEnabled)
        for (l in ls) {
            l.contactReceived(ev)
        }
    }

    /**
     * Notifies the `ContactQueryListener`s registered with this
     * `ContactQuery` that a `SourceContact` has been removed.
     *
     * @param contact the `SourceContact` which has been removed and
     * which the registered `ContactQueryListener`s are to be notified about
     */
    protected fun fireContactRemoved(contact: SourceContact?) {
        var ls: Array<ContactQueryListener>
        synchronized(listeners) { ls = listeners.toTypedArray() }
        val ev = ContactRemovedEvent(this, contact)
        for (l in ls) l.contactRemoved(ev)
    }

    /**
     * Notifies the `ContactQueryListener`s registered with this
     * `ContactQuery` that a `SourceContact` has been changed.
     *
     * @param contact the `SourceContact` which has been changed and
     * which the registered `ContactQueryListener`s are to be notified about
     */
    protected fun fireContactChanged(contact: SourceContact?) {
        var ls: Array<ContactQueryListener>
        synchronized(listeners) { ls = listeners.toTypedArray() }
        val ev = ContactChangedEvent(this, contact)
        for (l in ls) l.contactChanged(ev)
    }

    /**
     * Notifies the `ContactQueryListener`s registered with this `ContactQuery` that its state has changed.
     *
     * @param eventType the type of the `ContactQueryStatusEvent` to be
     * fired which can be one of the `QUERY_XXX` constants defined by `ContactQueryStatusEvent`
     */
    protected fun fireQueryStatusChanged(eventType: Int) {
        var ls: Array<ContactQueryListener>
        synchronized(listeners) { ls = listeners.toTypedArray() }
        val ev = ContactQueryStatusEvent(this, eventType)
        for (l in ls) l.queryStatusChanged(ev)
    }

    /**
     * Removes a `ContactQueryListener` from the list of listeners
     * interested in notifications about this `ContactQuery` changing its
     * status, the receipt of new `SourceContact`s via this `ContactQuery`, etc.
     *
     * @param l the `ContactQueryListener` to be removed from the list of
     * listeners interested in notifications raised by this `ContactQuery`
     * @see ContactQuery.removeContactQueryListener
     */
    override fun removeContactQueryListener(l: ContactQueryListener?) {
        if (l != null) {
            synchronized(listeners) { listeners.remove(l) }
        }
    }
}