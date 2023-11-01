/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atalk.impl.neomedia.rtp

import net.sf.fmj.media.rtp.util.BadFormatException
import net.sf.fmj.media.rtp.util.RTPPacket
import net.sf.fmj.media.rtp.util.RTPPacketParser
import net.sf.fmj.media.rtp.util.UDPPacket
import org.atalk.service.neomedia.RawPacket
import timber.log.Timber

/**
 * Extends the FMJ `RTPPacketParser` with additional functionality.
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
class RTPPacketParserEx : RTPPacketParser() {
    @Throws(BadFormatException::class)
    fun parse(pkt: RawPacket?): RTPPacket? {
        if (pkt == null) {
            Timber.w("pkt is null.")
            return null
        }
        return parse(pkt.buffer, pkt.offset, pkt.length)
    }

    @Throws(BadFormatException::class)
    fun parse(data: ByteArray?, offset: Int, length: Int): RTPPacket {
        val udp = UDPPacket()
        udp.data = data
        udp.length = length
        udp.offset = offset
        udp.received = false
        return parse(udp)
    }
}