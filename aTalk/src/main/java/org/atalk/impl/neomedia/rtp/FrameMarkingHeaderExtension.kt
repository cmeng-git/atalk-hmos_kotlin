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
package org.atalk.impl.neomedia.rtp

import org.atalk.util.ByteArrayBuffer

/**
 * Provides utility functions for the frame marking RTP header extension
 * described in https://tools.ietf.org/html/draft-ietf-avtext-framemarking-03
 *
 * Non-Scalable
 * <pre>`0                   1
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  ID=? |  L=0  |S|E|I|D|0 0 0 0|
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
`</pre> *
 *
 * Scalable
 * <pre>`0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  ID=? |  L=2  |S|E|I|D|B| TID |   LID         |    TL0PICIDX  |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
`</pre> *
 *
 * @author Boris Grozev
 * @author Sergio Garcia Murillo
 * @author Eng Chong Meng
 */
object FrameMarkingHeaderExtension {
    /**
     * The "start of frame" bit.
     */
    private const val S_BIT = 0x80.toByte()

    /**
     * The "end of frame" bit.
     */
    private const val E_BIT = 0x40.toByte()

    /**
     * The "independent frame" bit.
     */
    private const val I_BIT: Byte = 0x20

    /**
     * The bits that need to be set in order for a packet to be considered the
     * first packet of a keyframe.
     */
    private const val KF_MASK = (S_BIT.toInt() or I_BIT.toInt()).toByte()

    /**
     * The bits for the temporalId
     */
    private const val TID_MASK = 0x07.toByte()

    /**
     * @return true if the extension contained in the given buffer indicates
     * that the corresponding RTP packet is the first packet of a keyframe (i.e.
     * the S and I bits are set).
     */
    fun isKeyframe(baf: ByteArrayBuffer?): Boolean {
        if (baf == null || baf.length < 2) {
            return false
        }

        // The data follows the one-byte header.
        val b = baf.buffer!![baf.offset + 1]
        return (b.toInt() and KF_MASK.toInt()).toByte() == KF_MASK
    }

    /**
     * @return true if the extension contained in the given buffer indicates
     * that the corresponding RTP packet is the first packet of a frame (i.e.
     * the S bit is set).
     */
    fun isStartOfFrame(baf: ByteArrayBuffer?): Boolean {
        if (baf == null || baf.length < 2) {
            return false
        }

        // The data follows the one-byte header.
        val b = baf.buffer!![baf.offset + 1]
        return b.toInt() and S_BIT.toInt() != 0
    }

    /**
     * @return true if the extension contained in the given buffer indicates
     * that the corresponding RTP packet is the last packet of a frame (i.e.
     * the E bit is set).
     */
    fun isEndOfFrame(baf: ByteArrayBuffer?): Boolean {
        if (baf == null || baf.length < 2) {
            return false
        }

        // The data follows the one-byte header.
        val b = baf.buffer!![baf.offset + 1]
        return b.toInt() and E_BIT.toInt() != 0
    }

    /**
     * @param baf Header extension byte array
     * @param encoding Encoding type used to parse the LID field
     * @return the spatial layerd id present in the LID or 0 if not present.
     */
    fun getSpatialID(baf: ByteArrayBuffer?, encoding: String?): Byte {
        // Only present on scalable version
        return if (baf == null || baf.length < 4) {
            0
        } else 0
        /*
         * THIS IS STILL NOT YET CORRECTLY DEFINED ON FRAMEMARKING DRAFT!
         */
    }

    /**
     * @param baf Header extension byte array
     * @return The temporal layer id (the LID bits) or 0 if not present
     */
    fun getTemporalID(baf: ByteArrayBuffer?): Byte {
        if (baf == null || baf.length < 2) {
            return 0
        }
        // The data follows the one-byte header.
        val b = baf.buffer!![baf.offset + 1]
        return (b.toInt() and TID_MASK.toInt()).toByte()
    }
}