/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.translator

import javax.media.rtp.OutputDataStream

/**
 * Describes an `OutputDataStream` associated with an endpoint to which an
 * `RTPTranslatorImpl` is translating.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
internal class OutputDataStreamDesc
/**
 * Initializes a new `OutputDataStreamDesc` instance which is to describe an endpoint
 * `OutputDataStream` for an `RTPTranslatorImpl`.
 *
 * @param connectorDesc the endpoint `RTPConnector` which own the specified `stream`
 * @param stream the endpoint `OutputDataStream` to be described by the new instance for an
 * `RTPTranslatorImpl`
 */
(
        /**
         * The endpoint `RTPConnector` which owns [.stream].
         */
        val connectorDesc: RTPConnectorDesc,
        /**
         * The `OutputDataStream` associated with an endpoint to which an
         * `RTPTranslatorImpl` is translating.
         */
        val stream: OutputDataStream)