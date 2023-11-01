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
package org.atalk.impl.neomedia.codec

import timber.log.Timber
import java.util.function.Predicate

/**
 * An `Iterator` that iterates RED blocks (primary and non-primary).
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
class REDBlockIterator(
        /**
         * The byte buffer that holds the RED payload that this instance is dissecting.
         */
        private val buffer: ByteArray?,
        /**
         * The offset in the buffer where the RED payload begin.
         */
        private val offset: Int,
        /**
         * The length of the RED payload in the buffer.
         */
        private val length: Int) : MutableIterator<REDBlock?> {
    /**
     * The number of RED blocks inside the RED payload.
     */
    private var cntRemainingBlocks = -1

    /**
     * The offset of the next RED block header inside the RED payload.
     */
    private var offNextBlockHeader = -1

    /**
     * The offset of the next RED block payload inside the RED payload.
     */
    private var offNextBlockPayload = -1

    /**
     * Ctor.
     *
     * @param buffer the byte buffer that contains the RED payload.
     * @param offset the offset in the buffer where the RED payload begins.
     * @param length the length of the RED payload.
     */
    init {
        initialize()
    }

    override fun hasNext(): Boolean {
        return cntRemainingBlocks > 0
    }

    override fun next(): REDBlock? {
        if (!hasNext()) {
            throw NoSuchElementException()
        }
        cntRemainingBlocks--
        if (buffer == null || buffer.size <= offNextBlockHeader) {
            Timber.w("Prevented an array out of bounds exception.")
            return null
        }
        val blockPT = (buffer[offNextBlockHeader].toInt() and 0x7f).toByte()
        val blockLen: Int
        if (hasNext()) {
            if (buffer.size < offNextBlockHeader + 4) {
                Timber.w("Prevented an array out of bounds exception.")
                return null
            }

            // 0                   1                   2                   3
            // 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
            //+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            //|F|   block PT  |  timestamp offset         |   block length    |
            //+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
            blockLen = buffer[offNextBlockHeader + 2].toInt() and 0x03 shl 8 or (buffer[offNextBlockHeader + 3].toInt() and 0xFF)
            offNextBlockHeader += 4 // next RED header
            offNextBlockPayload += blockLen
        } else {
            // 0 1 2 3 4 5 6 7
            // +-+-+-+-+-+-+-+-+
            // |0| Block PT |
            // +-+-+-+-+-+-+-+-+
            blockLen = length - (offNextBlockPayload + 1)
            offNextBlockHeader = -1
            offNextBlockPayload = -1
        }
        return REDBlock(buffer, offNextBlockPayload, blockLen, blockPT)
    }

    override fun remove() {
        throw UnsupportedOperationException()
    }

    /**
     * Initializes this instance.
     */
    private fun initialize() {
        if (buffer == null || buffer.size == 0) {
            return
        }

        // beginning of RTP payload
        offNextBlockHeader = offset

        // Number of packets inside RED.
        cntRemainingBlocks = 0

        // 0                   1                   2                   3
        // 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
        //+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        //|F|   block PT  |  timestamp offset         |   block length    |
        //+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        while (buffer[offNextBlockHeader].toInt() and 0x80 != 0) {
            cntRemainingBlocks++
            offNextBlockHeader += 4
        }

        // 0 1 2 3 4 5 6 7
        // +-+-+-+-+-+-+-+-+
        // |0| Block PT |
        // +-+-+-+-+-+-+-+-+
        if (buffer.size >= offNextBlockHeader + 8) {
            cntRemainingBlocks++
        }

        // back to beginning of RTP payload
        offNextBlockHeader = offset
        if (cntRemainingBlocks > 0) {
            offNextBlockPayload = offNextBlockHeader + (cntRemainingBlocks - 1) * 4 + 1
        }
    }

    companion object {
        /**
         * Matches a RED block in the RED payload.
         *
         * @param predicate the predicate that is used to match the RED block.
         * @param buffer the byte buffer that contains the RED payload.
         * @param offset the offset in the buffer where the RED payload begins.
         * @param length the length of the RED payload.
         * @return the first RED block that matches the given predicate, null otherwise.
         */
        fun matchFirst(predicate: Predicate<REDBlock?>, buffer: ByteArray?, offset: Int, length: Int): REDBlock? {
            return if (isMultiBlock(buffer, offset, length)) {
                val it = REDBlockIterator(buffer, offset, length)
                while (it.hasNext()) {
                    val b = it.next()
                    if (b != null && predicate.test(b)) {
                        return b
                    }
                }
                null
            } else {
                val b = getPrimaryBlock(buffer, offset, length)
                if (b != null && predicate.test(b)) {
                    b
                } else {
                    null
                }
            }
        }

        /**
         * Gets the first RED block in the RED payload.
         *
         * @param buffer the byte buffer that contains the RED payload.
         * @param offset the offset in the buffer where the RED payload begins.
         * @param length the length of the RED payload.
         * @return the primary RED block if it exists, null otherwise.
         */
        fun getPrimaryBlock(buffer: ByteArray?, offset: Int, length: Int): REDBlock? {
            // Chrome is typically sending RED packets with a single block carrying
            // either VP8 or FEC. This is unusual, and probably wrong as it messes
            // up the sequence numbers and packet loss computations but it's just
            // the way it is. Here we detect this situation and avoid looping
            // through the blocks if there is a single block.
            return if (isMultiBlock(buffer, offset, length)) {
                var block: REDBlock? = null
                val redBlockIterator = REDBlockIterator(buffer, offset, length)
                while (redBlockIterator.hasNext()) {
                    block = redBlockIterator.next()
                }
                if (block == null) {
                    Timber.w("No primary block found.")
                }
                block
            } else {
                if (buffer == null || offset < 0 || length < 1 || buffer.size < offset + length) {
                    Timber.w("Prevented an array out of bounds exception. offset: %s, length: %d", offset, length)
                    return null
                }
                val blockPT = (buffer[offset].toInt() and 0x7f).toByte()
                val blockOff = offset + 1 // + 1 for the primary block header.
                val blockLen = length - 1
                REDBlock(buffer, blockOff, blockLen, blockPT)
            }
        }

        /**
         * Returns `true` if a specific RED packet contains multiple blocks;
         * `false`, otherwise.
         *
         * @param buffer the byte buffer that contains the RED payload.
         * @param offset the offset in the buffer where the RED payload begins.
         * @param length the length of the RED payload.
         * @return `true if {} contains multiple RED blocks; otherwise,
         * { false}`
         */
        fun isMultiBlock(buffer: ByteArray?, offset: Int, length: Int): Boolean {
            if (buffer == null || buffer.isEmpty()) {
                Timber.w("The buffer appears to be empty.")
                return false
            }
            if (offset < 0 || buffer.size <= offset) {
                Timber.w("Prevented array out of bounds exception.")
                return false
            }
            return buffer[offset].toInt() and 0x80 != 0
        }
    }
}