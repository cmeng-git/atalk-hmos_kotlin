/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.translator

import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream
import javax.media.protocol.PushBufferStream
import javax.media.protocol.PushSourceStream

/**
 * Describes a `PushSourceStream` associated with an endpoint from which an
 * `RTPTranslatorImpl` is translating.
 *
 * @author Lyubomir Marinov
 */
class PushSourceStreamDesc(
        /**
         * The endpoint `RTPConnector` which owns [.stream].
         */
        val connectorDesc: RTPConnectorDesc,
        /**
         * The `PushSourceStream` associated with an endpoint from which an
         * `RTPTranslatorImpl` is translating.
         */
        val stream: PushSourceStream,
        /**
         * `true` if this instance represents a data/RTP stream or `false` if this
         * instance represents a control/RTCP stream
         */
        val data: Boolean) {
    /**
     * The `PushBufferStream` control over [.stream], if available, which may provide
     * Buffer properties other than `data`, `length` and `offset` such as
     * `flags`.
     */
    val streamAsPushBufferStream: PushBufferStream

    /**
     * Initializes a new `PushSourceStreamDesc` instance which is to describe a specific
     * endpoint `PushSourceStream` for an `RTPTranslatorImpl`.
     *
     * @param connectorDesc
     * the endpoint `RTPConnector` which owns the specified `stream`
     * @param stream
     * the endpoint `PushSourceStream` to be described by the new instance for an
     * `RTPTranslatorImpl`
     * @param data
     * `true` if the specified `stream` is a data/RTP stream or `false`
     * if the specified `stream` is a control/RTCP stream
     */
    init {
        streamAsPushBufferStream = stream.getControl(AbstractPushBufferStream.PUSH_BUFFER_STREAM_CLASS_NAME) as PushBufferStream
    }
}