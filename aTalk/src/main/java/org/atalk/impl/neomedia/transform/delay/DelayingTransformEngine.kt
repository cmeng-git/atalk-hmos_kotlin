/*
 * Copyright @ 2015 Atlassian Pty Ltd
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
package org.atalk.impl.neomedia.transform.delay

import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.impl.neomedia.transform.SinglePacketTransformer
import org.atalk.impl.neomedia.transform.TransformEngine
import org.atalk.service.neomedia.RawPacket

/**
 * A [TransformEngine] that delays the RTP stream by specified packet
 * count by holding them in a buffer.
 *
 * @author Boris Grozev
 * @author Pawel Domas
 */
class DelayingTransformEngine(packetCount: Int) : TransformEngine {
    private val delayingTransformer: DelayingTransformer

    /**
     * Creates new instance of `DelayingTransformEngine` which will delay
     * the RTP stream by given amount of packets.
     * packetCount the delay counted in packets.
     */
    init {
        delayingTransformer = DelayingTransformer(packetCount)
    }

    /**
     * {@inheritDoc}
     */
    override val rtpTransformer: PacketTransformer
        get() = delayingTransformer

    /**
     * {@inheritDoc}
     */
    override val rtcpTransformer: PacketTransformer?
        get() = null

    private inner class DelayingTransformer(pktCount: Int) : SinglePacketTransformer() {
        private val buffer: Array<RawPacket?>
        private var idx = 0

        init {
            buffer = arrayOfNulls(pktCount)
        }

        override fun reverseTransform(pkt: RawPacket): RawPacket? {
            if (pkt != null) {
                val ret = buffer[idx]
                buffer[idx] = pkt
                idx = (idx + 1) % buffer.size
                return ret
            }
            return null
        }

        override fun transform(pkt: RawPacket): RawPacket {
            return pkt
        }
    }
}