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
package org.atalk.service.neomedia.rtp

import net.sf.fmj.media.rtp.RTCPSRPacket
import org.atalk.impl.neomedia.rtcp.NACKPacket
import org.atalk.impl.neomedia.rtcp.RTCPREMBPacket
import org.atalk.impl.neomedia.rtcp.RTCPTCCPacket

/**
 * A simple interface that enables listening for RTCP packets.
 *
 * @author Boris Grozev
 * @author George Politis
 * @author Eng Chong Meng
 */
interface RTCPPacketListener {
    /**
     * Notifies this listener that a [NACKPacket] has been received.
     *
     * @param nackPacket the received [NACKPacket].
     */
    fun nackReceived(nackPacket: NACKPacket?)

    /**
     * Notifies this listener that a [RTCPREMBPacket] has been received.
     *
     * @param rembPacket the received [RTCPREMBPacket].
     */
    fun rembReceived(rembPacket: RTCPREMBPacket?)

    /**
     * Notifies this listener that an [RTCPSRPacket] has been received.
     *
     * @param srPacket the received [RTCPSRPacket].
     */
    fun srReceived(srPacket: RTCPSRPacket?)

    /**
     * Notifies this listener that an [RTCPTCCPacket] has been received.
     *
     * @param tccPacket the received [RTCPTCCPacket]
     */
    fun tccReceived(tccPacket: RTCPTCCPacket?)
}