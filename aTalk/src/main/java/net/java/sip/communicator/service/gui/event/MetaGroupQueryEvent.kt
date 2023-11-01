/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event

import net.java.sip.communicator.service.contactlist.MetaContactGroup
import java.util.*

/**
 * The `MetaGroupQueryEvent` is triggered each time a
 * `MetaContactGroup` is received as a result of a `MetaContactQuery`.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class MetaGroupQueryEvent
/**
 * Creates an instance of `MetaGroupQueryEvent` by specifying the
 * `source` query this event comes from and the `metaGroup` this event is about.
 *
 * @param source the `MetaContactQuery` that triggered this event
 * @param metaGroup the `MetaContactGroup` this event is about
 */
(source: MetaContactQuery?,
        /**
         * The `MetaContactGroup` this event is about.
         */
        val metaGroup: MetaContactGroup?) : EventObject(source) {
    /**
     * Returns the `MetaContactGroup` this event is about.
     *
     * @return the `MetaContactGroup` this event is about
     */

    /**
     * Returns the `MetaContactQuery` that triggered this event.
     *
     * @return the `MetaContactQuery` that triggered this event
     */
    val querySource: MetaContactQuery
        get() = source as MetaContactQuery
}