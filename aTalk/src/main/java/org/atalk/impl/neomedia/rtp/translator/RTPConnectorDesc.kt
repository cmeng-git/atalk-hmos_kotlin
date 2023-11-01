/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.translator

import javax.media.rtp.RTPConnector

/**
 * Describes an `RTPConnector` associated with an endpoint from and to which an
 * `RTPTranslatorImpl` is translating.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class RTPConnectorDesc(val streamRTPManagerDesc: StreamRTPManagerDesc?,
        /**
         * The `RTPConnector` associated with an endpoint from and to which an
         * `RTPTranslatorImpl` is translating.
         */
        val connector: RTPConnector?)