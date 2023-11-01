/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event

import net.java.sip.communicator.service.gui.FilterQuery

/**
 * The `FilterQueryListener` is notified when a filter query finishes.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface FilterQueryListener {
    /**
     * Indicates that the given `query` has finished with success, i.e. the filter has returned results.
     * @param query the `FilterQuery`, where this listener is registered
     */
    fun filterQuerySucceeded(query: FilterQuery?)

    /**
     * Indicates that the given `query` has finished with failure, i.e. no results for the filter were found.
     * @param query the `FilterQuery`, where this listener is registered
     */
    fun filterQueryFailed(query: FilterQuery?)
}