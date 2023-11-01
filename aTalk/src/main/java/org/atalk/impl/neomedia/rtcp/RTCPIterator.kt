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
package org.atalk.impl.neomedia.rtcp

import net.sf.fmj.media.rtp.RTCPHeader
import org.atalk.service.neomedia.RawPacket
import org.atalk.util.ByteArrayBuffer
import org.atalk.util.RTCPUtils.getLength

/**
 * An `Iterator` for RTCP packets contained in a compound RTCP packet.
 * For a `PacketTransformer` that splits compound RTCP packets into
 * individual RTCP packets {@see CompoundPacketEngine}.
 *
 *
 * Instances of this class are not thread-safe. If multiple threads access an
 * instance concurrently, it must be synchronized externally.
 *
 * @author George Politis
 */
class RTCPIterator(
        /**
         * The `RawPacket` that holds the RTCP packet to iterate.
         */
        private val baf: ByteArrayBuffer?) : MutableIterator<ByteArrayBuffer> {
    /**
     * The offset in the [.baf] where the next packet is to be looked for.
     */
    private var nextOff = 0

    /**
     * The remaining length in [.baf].
     */
    private var remainingLen = 0

    /**
     * The length of the last next element.
     */
    private var lastLen = 0

    /**
     * Ctor.
     *
     * @param baf The `ByteArrayBuffer` that holds the compound RTCP packet to iterate.
     */
    init {
        if (baf != null) {
            nextOff = baf.offset
            remainingLen = baf.length
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun hasNext(): Boolean {
        return getLength(baf!!.buffer, nextOff, remainingLen) >= RTCPHeader.SIZE
    }

    /**
     * {@inheritDoc}
     */
    override fun next(): ByteArrayBuffer {
        val pktLen = getLength(baf!!.buffer, nextOff, remainingLen)
        check(pktLen >= RTCPHeader.SIZE)
        val next = RawPacket(baf.buffer, nextOff, pktLen)
        lastLen = pktLen
        nextOff += pktLen
        remainingLen -= pktLen
        if (remainingLen < 0) {
            throw ArrayIndexOutOfBoundsException()
        }
        return next
    }

    /**
     * {@inheritDoc}
     */
    override fun remove() {
        check(lastLen != 0)
        System.arraycopy(baf!!.buffer, nextOff, baf.buffer, nextOff - lastLen, remainingLen)
        nextOff -= lastLen
        baf.length = baf.length - lastLen
        lastLen = 0
    }
}