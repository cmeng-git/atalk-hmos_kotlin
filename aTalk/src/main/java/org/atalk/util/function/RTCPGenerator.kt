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
package org.atalk.util.function

import net.sf.fmj.media.rtp.RTCPCompoundPacket
import org.atalk.service.neomedia.RawPacket

/**
 * A `Function` that produces `RawPacket`s from `RTCPCompoundPacket`s.
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
class RTCPGenerator : AbstractFunction<RTCPCompoundPacket?, RawPacket?>() {
    override fun apply(t: RTCPCompoundPacket?): RawPacket? {
        if (t == null) {
            return null
        }

        // Assemble the RTP packet.
        val len = t.calcLength()

        // TODO We need to be able to re-use original RawPacket buffer.
        t.assemble(len, false)
        val buf = t.data
        return RawPacket(buf, 0, len)
    }
}