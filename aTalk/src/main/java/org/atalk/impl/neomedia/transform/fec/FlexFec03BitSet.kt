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
package org.atalk.impl.neomedia.transform.fec

import java.util.*

/**
 * A bit-set class which is similar to a standard bitset, but with 2 differences:
 * 1) The size of this set is preserved.  Unlike the standard bitset, which will
 * not include leading 0's in its size (which doesn't work well for a bitmask),
 * this one will always report the size it was allocated for
 * 2) When reading (valueOf) and writing (toByteArray) it inverts the order
 * of the bits.  This is because in FlexFEC-03, the left-most bit of the mask
 * represents a delta value of '0'.
 */
class FlexFec03BitSet(numBits: Int) {
    /**
     * The underlying bitset used to store the bits
     */
    private var bitSet: BitSet

    /**
     * The size of the mask this bitset is representing
     */
    private var numBits = 0

    /**
     * Ctor
     *
     * @param numBits
     * the size, in bits, of this set
     */
    init {
        bitSet = BitSet(numBits)
        this.numBits = numBits
    }

    /**
     * Set the bit (set to 1) at the given index
     *
     * @param bitIndex
     */
    fun set(bitIndex: Int) {
        bitSet.set(bitIndex)
    }

    /**
     * Get the bit at the given index
     *
     * @param bitIndex
     * @return
     */
    operator fun get(bitIndex: Int): Boolean {
        return bitSet[bitIndex]
    }

    /**
     * Clear the bit (set to 0) at the given index
     *
     * @param bitIndex
     */
    fun clear(bitIndex: Int) {
        bitSet.clear(bitIndex)
    }

    /**
     * Add a bit with the given value in the given position.  Existing bits
     * will be moved to the right.  No loss of bits will occur.
     *
     * @param bitIndex
     * the index at which to insert the bit
     * @param bitValue
     * the value to set on the inserted bit
     */
    fun addBit(bitIndex: Int, bitValue: Boolean) {
        val newNumBits = numBits + 1
        val newBitSet = BitSet(newNumBits)
        // copy [0, bitIndex - 1] to the new set in the same position
        // copy [bitIndex, length] to the new set shifted right by 1
        for (i in 0 until numBits) {
            if (i < bitIndex) {
                newBitSet[i] = bitSet[i]
            } else {
                newBitSet[i + 1] = bitSet[i]
            }
        }
        newBitSet[bitIndex] = bitValue
        bitSet = newBitSet
        numBits = newNumBits
    }

    /**
     * Remove a bit from the given position.  Existing bits will be moved
     * to the left.
     *
     * @param bitIndex
     */
    fun removeBit(bitIndex: Int) {
        val newNumBits = numBits - 1
        val newBitSet = BitSet(newNumBits)
        for (i in 0 until numBits) {
            if (i < bitIndex) {
                newBitSet[i] = bitSet[i]
            } else if (i > bitIndex) {
                newBitSet[i - 1] = bitSet[i]
            }
        }
        bitSet = newBitSet
        numBits = newNumBits
    }

    /**
     * Get the size of this set, in bytes
     *
     * @return the size of this set, in bytes
     */
    fun sizeBytes(): Int {
        var numBytes = numBits / 8
        if (numBits % 8 != 0) {
            numBytes++
        }
        return numBytes
    }

    /**
     * Get the size of this set, in bits
     *
     * @return the size of this set, in bits
     */
    fun sizeBits(): Int {
        return numBits
    }

    /**
     * Writes this bitset to a byte array, where the rightmost bit is treated
     * as the least significant bit.
     *
     * @return
     */
    fun toByteArray(): ByteArray {
        val bytes = ByteArray(sizeBytes())
        for (currBitPos in 0 until numBits) {
            val bytePos = currBitPos / 8
            // The position of this bit relative to the current byte
            // (left to right)
            val relativeBitPos = currBitPos % 8
            if (get(currBitPos)) {
                bytes[bytePos] = (bytes[bytePos].toInt() or (0x80 shr relativeBitPos)).toByte()
            }
        }
        return bytes
    }

    /**
     * Print the bitmask in a manner where the left-most bit is the LSB
     *
     * @return
     */
    override fun toString(): String {
        val sb = StringBuilder()
        val numSpaces = Integer.toString(numBits).length + 1
        for (i in 0 until numBits) {
            sb.append(String.format("%-" + numSpaces + "s", i))
        }
        sb.append("\n")
        for (i in 0 until numBits) {
            if (get(i)) {
                sb.append(String.format("%-" + numSpaces + "s", "1"))
            } else {
                sb.append(String.format("%-" + numSpaces + "s", "0"))
            }
        }
        return sb.toString()
    }

    companion object {
        /**
         * Parse the given bytes (at the given offset and length) into a
         * FlexFec03BitSet
         *
         * @param bytes
         * the byte buffer to parse
         * @param offset
         * the offset at which to start parsing
         * @param length
         * the length (in bytes) of the chunk to parse
         * @return a FlexFec03BitSet representing the chunk of the given buffer
         * based on the given offset and length
         */
        /**
         * Parse the value of the given byte buffer into the bitset
         *
         * @param bytes
         * @return
         */
        @JvmOverloads
        fun valueOf(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): FlexFec03BitSet {
            val numBits = length * 8
            val b = FlexFec03BitSet(numBits)
            for (currBytePos in 0 until length) {
                val currByte = bytes[offset + currBytePos]
                for (currBitPos in 0..7) {
                    val absoluteBitPos = currBytePos * 8 + currBitPos
                    if (currByte.toInt() and (0x80 shr currBitPos) > 0) {
                        b.set(absoluteBitPos)
                    }
                }
            }
            return b
        }
    }
}