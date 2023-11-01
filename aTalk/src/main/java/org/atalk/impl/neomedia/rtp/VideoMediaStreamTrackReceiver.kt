/*
 * Copyright @ 2018 Atlassian Pty Ltd
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
package org.atalk.impl.neomedia.rtp

import org.atalk.impl.neomedia.MediaStreamImpl
import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.RawPacket

/**
 * Extends the generic [MediaStreamTrackReceiver] with logic to update
 * its [MediaStreamTrackDesc]s with received packets.
 *
 * @author George Politis
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class VideoMediaStreamTrackReceiver
/**
 * Initializes a new [VideoMediaStreamTrackReceiver] instance.
 *
 * @param stream The [MediaStream] that this instance receives
 * [MediaStreamTrackDesc]s from.
 */
(stream: MediaStreamImpl) : MediaStreamTrackReceiver(stream) {
    /**
     * {@inheritDoc}
     */
    override fun reverseTransform(pkt: RawPacket): RawPacket {
        if (!pkt!!.isInvalid) {
            val encoding = findRTPEncodingDesc(pkt)
            encoding?.update(pkt, System.currentTimeMillis())
        }
        return pkt
    }

    override val rtpTransformer: PacketTransformer
        get() = super.rtpTransformer
    override val rtcpTransformer: PacketTransformer
        get() = TODO("Not yet implemented")
}