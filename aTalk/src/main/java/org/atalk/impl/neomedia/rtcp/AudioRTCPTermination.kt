/*
 * Copyright @ 2017 Atlassian Pty Ltd
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
package org.atalk.impl.neomedia.rtcp

import org.atalk.impl.neomedia.RTCPPacketPredicate
import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.impl.neomedia.transform.SinglePacketTransformerAdapter
import org.atalk.impl.neomedia.transform.TransformEngine
import org.atalk.service.neomedia.RawPacket

/**
 * Provide RTCP termination facilities for audio
 *
 * @author Brian Baldino
 */
class AudioRTCPTermination : TransformEngine {
    override var rtcpTransformer = RTCPTransformer()

    override val rtpTransformer: PacketTransformer?
        get() = null


    class RTCPTransformer
        : SinglePacketTransformerAdapter(RTCPPacketPredicate.INSTANCE) {

        override fun transform(pkt: RawPacket): RawPacket? {
            val it = RTCPIterator(pkt)
            while (it.hasNext()) {
                val baf = it.next()
                // We want to terminate all REMB packets
                if (RTCPREMBPacket.isREMBPacket(baf)) {
                    it.remove()
                }
            }
            return if (pkt.length == 0) null else pkt
        }
    }
}