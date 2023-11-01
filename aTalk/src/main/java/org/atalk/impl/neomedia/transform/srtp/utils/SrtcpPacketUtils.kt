/*
 * Copyright @ 2015 - present 8x8, Inc
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
package org.atalk.impl.neomedia.transform.srtp.utils

import org.atalk.util.ByteArrayBuffer
import org.atalk.util.ByteArrayUtils

/**
 * SrtcpPacket is the low-level utilities to get the data fields needed by SRTCP.
 */
object SrtcpPacketUtils {
    /**
     * Get the sender SSRC of an SRTCP packet.
     *
     * This is the SSRC of the first packet of the compound packet.
     *
     * @param buf The buffer holding the SRTCP packet.
     */
    fun getSenderSsrc(buf: ByteArrayBuffer): Int {
        return ByteArrayUtils.readInt(buf, 4)
    }

    /**
     * Get the SRTCP index (sequence number) from an SRTCP packet
     *
     * @param buf The buffer holding the SRTCP packet.
     * @param authTagLen authentication tag length
     * @return SRTCP sequence num from source packet
     */
    fun getIndex(buf: ByteArrayBuffer?, authTagLen: Int): Int {
        val authTagOffset = buf!!.length - (4 + authTagLen)
        return ByteArrayUtils.readInt(buf, authTagOffset)
    }

    /**
     * Validate that the contents of a ByteArrayBuffer could contain a valid SRTCP packet.
     *
     * This validates that the packet is long enough to be a valid packet, i.e. attempts to read
     * fields of the packet will not fail.
     *
     * @param buf The buffer holding the SRTCP packet.
     * @param authTagLen The length of the packet's authentication tag.
     * @return true if the packet is syntactically valid (i.e., long enough); false if not.
     */
    fun validatePacketLength(buf: ByteArrayBuffer?, authTagLen: Int): Boolean {
        val length = buf!!.length
        val neededLength = 8 /* sender SSRC */ + 4 /* index */ + authTagLen
        return length >= neededLength
    }
}