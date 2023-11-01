/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.conference

import javax.media.protocol.DataSource

/**
 * Represents a filter which determines whether a specific `DataSource` is to be selected or
 * deselected by the caller of the filter.
 *
 * @author Lyubomir Marinov
 */
interface DataSourceFilter {
    /**
     * Determines whether a specific `DataSource` is accepted by this filter i.e. whether
     * the caller of this filter should include it in its selection.
     *
     * @param dataSource
     * the `DataSource` to be checked whether it is accepted by this filter
     * @return `true` if this filter accepts the specified `DataSource` i.e. if the
     * caller of this filter should include it in its selection; otherwise, `false`
     */
    fun accept(dataSource: DataSource?): Boolean
}