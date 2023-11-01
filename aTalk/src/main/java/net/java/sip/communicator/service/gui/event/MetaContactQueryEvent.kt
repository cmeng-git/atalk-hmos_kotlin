/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event

import net.java.sip.communicator.service.contactlist.MetaContact
import java.util.*

/**
 * The `MetaContactQueryEvent` is triggered each time a
 * `MetaContact` is received as a result of a `MetaContactQuery`.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class MetaContactQueryEvent
/**
 * Creates an instance of `MetaGroupQueryEvent` by specifying the
 * `source` query this event comes from and the `metaContact` this event is about.
 *
 * @param source the `MetaContactQuery` that triggered this event
 * @param metaContact the `MetaContact` this event is about
 */
(source: MetaContactQuery?,
        /**
         * The `MetaContact` this event is about.
         */
        val metaContact: MetaContact?) : EventObject(source) {
    /**
     * Returns the `MetaContact` this event is about.
     * @return the `MetaContact` this event is about
     */

    /**
     * Returns the `MetaContactQuery` that triggered this event.
     * @return the `MetaContactQuery` that triggered this event
     */
    val querySource: MetaContactQuery
        get() = source as MetaContactQuery
}