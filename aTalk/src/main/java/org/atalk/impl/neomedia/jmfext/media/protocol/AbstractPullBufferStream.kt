/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol

import javax.media.control.FormatControl
import javax.media.protocol.PullBufferDataSource
import javax.media.protocol.PullBufferStream

/**
 * Provides a base implementation of `PullBufferStream` in order to facilitate implementers
 * by taking care of boilerplate in the most common cases.
 *
 * @author Lyubomir Marinov
 */
abstract class AbstractPullBufferStream<T : PullBufferDataSource?>
/**
 * Initializes a new `AbstractPullBufferStream` instance which is to have its
 * `Format`-related information abstracted by a specific `FormatControl`.
 *
 * @param dataSource the `PullBufferDataSource` which is creating the new instance so that it
 * becomes one of its `streams`
 * @param formatControl the `FormatControl` which is to abstract the `Format`-related
 * information of the new instance
 */
protected constructor(dataSource: T, formatControl: FormatControl?) : AbstractBufferStream<T>(dataSource, formatControl), PullBufferStream {
    /**
     * Determines if [.read] will block.
     *
     * @return `true` if read block, `false` otherwise
     */
    override fun willReadBlock(): Boolean {
        return true
    }
}