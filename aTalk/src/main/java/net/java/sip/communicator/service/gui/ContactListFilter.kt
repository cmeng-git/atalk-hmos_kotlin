/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui

/**
 * The `ContactListFilter` is an interface meant to be implemented by
 * modules interested in filtering the contact list. An implementation of this
 * interface should be able to answer if an `UIContact` or an
 * `UIGroup` is matching the corresponding filter.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface ContactListFilter {
    /**
     * Indicates if the given `uiGroup` is matching the current filter.
     * @param uiContact the `UIContact` to check
     * @return `true` to indicate that the given `uiContact`
     * matches this filter, `false` - otherwise
     */
    fun isMatching(uiContact: UIContact?): Boolean

    /**
     * Indicates if the given `uiGroup` is matching the current filter.
     * @param uiGroup the `UIGroup` to check
     * @return `true` to indicate that the given `uiGroup`
     * matches this filter, `false` - otherwise
     */
    fun isMatching(uiGroup: UIGroup?): Boolean

    /**
     * Applies this filter to any interested sources
     * @param filterQuery the `FilterQuery` that tracks the results of this filtering
     */
    fun applyFilter(filterQuery: FilterQuery?)
}